package com.axalotl.async.common.commands;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.gpu.GpuEntityModule;
import com.axalotl.async.common.gpu.GpuCollisionDispatcher;
import com.axalotl.async.common.gpu.safety.CrashGuard;
import com.axalotl.async.common.gpu.vulkan.VkDeviceManager;
import com.axalotl.async.common.platform.Permission;
import com.axalotl.async.common.platform.PlatformUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * GPU diagnostics command for end users.
 * <p>
 * Sub-commands:
 * <ul>
 *     <li>{@code /async gpu}        -- shows GPU status, device info, collision stats</li>
 *     <li>{@code /async gpu test}   -- runs a collision benchmark (100 test entities)</li>
 *     <li>{@code /async gpu toggle} -- toggles GPU collision on/off</li>
 * </ul>
 * All sub-commands require OP permission level 2.
 */
public class GpuCommand {

    private GpuCommand() { /* utility class */ }

    // ------------------------------------------------------------------ //
    //  Registration
    // ------------------------------------------------------------------ //

    /**
     * Registers the {@code gpu} sub-command tree under the main {@code async} root.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> registerGpu(
            LiteralArgumentBuilder<CommandSourceStack> root) {

        return root.then(Commands.literal("gpu")
                .requires(Permission.require("command.gpu", 2))

                // /async gpu  (status)
                .executes(ctx -> {
                    showStatus(ctx.getSource());
                    return 1;
                })

                // /async gpu test  (benchmark)
                .then(Commands.literal("test")
                        .requires(Permission.require("command.gpu", 2))
                        .executes(ctx -> {
                            runBenchmark(ctx.getSource());
                            return 1;
                        }))

                // /async gpu toggle
                .then(Commands.literal("toggle")
                        .requires(Permission.require("command.gpu", 2))
                        .executes(ctx -> {
                            toggleGpu(ctx.getSource());
                            return 1;
                        }))
        );
    }

    // ------------------------------------------------------------------ //
    //  /async gpu  -- status display
    // ------------------------------------------------------------------ //

    private static void showStatus(CommandSourceStack source) {
        boolean gpuAvailable = GpuEntityModule.isGpuAvailable();
        boolean gpuEnabled   = AsyncConfig.enableGpuCollision.getValue();

        // Header
        MutableComponent message = AsyncCommand.prefix.copy()
                .append(Component.literal("GPU Diagnostics").withStyle(ChatFormatting.GOLD));

        // -- GPU Status --
        ChatFormatting statusColor = gpuAvailable
                ? ChatFormatting.GREEN
                : ChatFormatting.RED;
        String statusText = gpuAvailable ? "Available" : "Unavailable";
        message.append(Component.literal("\nGPU: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(statusText).withStyle(statusColor));

        // -- Enabled / Disabled --
        ChatFormatting enabledColor = gpuEnabled
                ? ChatFormatting.GREEN
                : ChatFormatting.GRAY;
        String enabledText = gpuEnabled ? "Enabled" : "Disabled";
        message.append(Component.literal("\nGPU Collision: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(enabledText).withStyle(enabledColor));

        // -- Device info --
        if (gpuAvailable) {
            String deviceName = VkDeviceManager.getDeviceName();
            long maxWG        = VkDeviceManager.getMaxWorkGroupInvocations();
            long sharedMemKB  = VkDeviceManager.getMaxSharedMemoryBytes() / 1024;

            message.append(Component.literal("\nDevice: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(deviceName).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\nMax Work-Group: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.valueOf(maxWG)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("\nShared Memory: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(sharedMemKB + " KB").withStyle(ChatFormatting.AQUA));
        } else {
            message.append(Component.literal("\nDevice: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("N/A").withStyle(ChatFormatting.DARK_GRAY));
        }

        // -- Collision stats --
        GpuCollisionDispatcher dispatcher = GpuEntityModule.getCollisionDispatcher();
        int gpuDispatches = dispatcher.getGpuCollisionCount();
        int cpuFallbacks  = dispatcher.getCpuFallbackCount();

        if (gpuAvailable && gpuEnabled) {
            message.append(Component.literal("\nCollision GPU: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("active (" + gpuDispatches + " dispatches)")
                            .withStyle(ChatFormatting.GREEN));
        } else {
            ChatFormatting fallbackColor = cpuFallbacks > 0
                    ? ChatFormatting.YELLOW
                    : ChatFormatting.GRAY;
            message.append(Component.literal("\nCollision GPU: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("fallback (" + cpuFallbacks + " fallbacks)")
                            .withStyle(fallbackColor));
        }

        // -- CrashGuard --
        CrashGuard.CrashGuardStats guardStats = dispatcher.getCrashGuardStats();
        if (guardStats != null) {
            boolean healthy = !guardStats.circuitOpen();
            ChatFormatting guardColor = healthy ? ChatFormatting.GREEN : ChatFormatting.RED;
            String guardText = healthy ? "healthy" : "tripped";
            message.append(Component.literal("\nCrashGuard: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(guardText).withStyle(guardColor));

            if (guardStats.totalFailures() > 0) {
                message.append(Component.literal(
                        " (failures=" + guardStats.totalFailures()
                        + ", trips=" + guardStats.totalCircuitTrips() + ")")
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        source.sendSuccess(() -> message, false);
    }

    // ------------------------------------------------------------------ //
    //  /async gpu test  -- benchmark
    // ------------------------------------------------------------------ //

    private static void runBenchmark(CommandSourceStack source) {
        if (!GpuEntityModule.isInitialized()) {
            source.sendFailure(AsyncCommand.prefix.copy()
                    .append(Component.literal("GPU module not initialized.")
                            .withStyle(ChatFormatting.RED)));
            return;
        }

        // Notify the player that the benchmark is starting
        source.sendSuccess(() -> AsyncCommand.prefix.copy()
                .append(Component.literal("Running collision benchmark (100 entities, 5 iterations)...")
                        .withStyle(ChatFormatting.YELLOW)), false);

        // Run benchmark on the server thread to keep things simple
        String result = GpuEntityModule.runBenchmark();

        source.sendSuccess(() -> AsyncCommand.prefix.copy()
                .append(Component.literal("Benchmark Complete").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n" + result).withStyle(ChatFormatting.GREEN)), false);
    }

    // ------------------------------------------------------------------ //
    //  /async gpu toggle
    // ------------------------------------------------------------------ //

    private static void toggleGpu(CommandSourceStack source) {
        boolean current = AsyncConfig.enableGpuCollision.getValue();
        boolean next    = !current;
        AsyncConfig.enableGpuCollision.setValue(next);
        PlatformUtils.saveConfig();

        ChatFormatting color = next ? ChatFormatting.GREEN : ChatFormatting.RED;
        String label = next ? "Enabled" : "Disabled";

        MutableComponent message = AsyncCommand.prefix.copy()
                .append(Component.literal("GPU Collision: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(label).withStyle(color));

        // Add a warning if toggled on but GPU is not available
        if (next && !GpuEntityModule.isGpuAvailable()) {
            message.append(Component.literal("\n  Warning: ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("No GPU detected -- setting will have no effect until a GPU is available.")
                            .withStyle(ChatFormatting.YELLOW));
        }

        // Add a warning if toggled off
        if (!next && GpuEntityModule.isGpuAvailable()) {
            message.append(Component.literal("\n  GPU collision disabled -- using CPU fallback.")
                    .withStyle(ChatFormatting.GRAY));
        }

        source.sendSuccess(() -> message, true);
    }
}
