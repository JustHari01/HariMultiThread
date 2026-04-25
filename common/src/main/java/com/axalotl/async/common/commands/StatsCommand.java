package com.axalotl.async.common.commands;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.platform.Permission;
import com.axalotl.async.common.utils.TickStats;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import static com.axalotl.async.common.ParallelProcessor.getPoolSize;

public class StatsCommand {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.##");

    public static LiteralArgumentBuilder<CommandSourceStack> registerStatus(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("stats")
                .requires(Permission.require("command.statistics", 0))
                .executes(cmdCtx -> {
                    showGeneralStats(cmdCtx.getSource());
                    return 1;
                })
                .then(Commands.literal("entity")
                        .executes(cmdCtx -> {
                            showEntityStats(cmdCtx.getSource(), 0, false, 0);
                            return 1;
                        })
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(cmdCtx -> {
                                    int count = IntegerArgumentType.getInteger(cmdCtx, "count");
                                    showEntityStats(cmdCtx.getSource(), count, false, 0);
                                    return 1;
                                })
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                        .executes(cmdCtx -> {
                                            int count = IntegerArgumentType.getInteger(cmdCtx, "count");
                                            int ticks = IntegerArgumentType.getInteger(cmdCtx, "ticks");
                                            startRecordingAndShow(cmdCtx.getSource(), count, ticks);
                                            return 1;
                                        })))));
    }

    private static void startRecordingAndShow(CommandSourceStack source, int topCount, int ticks) {
        TickStats.startRecording(ticks);
        source.sendSuccess(() -> AsyncCommand.prefix.copy()
                .append(Component.literal("Recording entity ticks for " + ticks + " ticks...")
                        .withStyle(ChatFormatting.YELLOW)), false);
        pollUntilDone(source, topCount, ticks);
    }

    private static void pollUntilDone(CommandSourceStack source, int topCount, int ticks) {
        source.getServer().execute(() -> {
            if (TickStats.isRecording()) {
                pollUntilDone(source, topCount, ticks);
            } else {
                showEntityStats(source, topCount, true, ticks);
            }
        });
    }

    private static void showGeneralStats(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        double mspt = server.getAverageTickTime();
        int totalEntities = 0;
        int asyncEntities = 0;
        for (ServerLevel world : server.getAllLevels()) {
            for (Entity entity : world.getAllEntities()) {
                if (!entity.isAlive()) continue;
                ++totalEntities;
                if (!ParallelProcessor.shouldTickSynchronously(entity)) {
                    ++asyncEntities;
                }
            }
        }
        double asyncRatio = totalEntities > 0 ? (double) asyncEntities * 100.0 / (double) totalEntities : 0.0;
        int threads = getPoolSize();
        boolean enabled = !AsyncConfig.disabled.getValue();
        boolean asyncSpawn = AsyncConfig.enableAsyncSpawn.getValue();
        boolean asyncRandomTicks = AsyncConfig.enableAsyncRandomTicks.getValue();

        MutableComponent message = AsyncCommand.prefix.copy()
                .append(Component.literal("Performance Statistics").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\nStatus: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(enabled ? "Enabled" : "Disabled")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal("\nAsync Spawn: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(asyncSpawn ? "Enabled" : "Disabled")
                        .withStyle(asyncSpawn ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal("\nAsync Random Ticks: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(asyncRandomTicks ? "Enabled" : "Disabled")
                        .withStyle(asyncRandomTicks ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal("\nMSPT: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(DECIMAL_FORMAT.format(mspt) + "ms")
                        .withStyle(getMsptColor(mspt)))
                .append(Component.literal("\nEntities: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(totalEntities)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(DECIMAL_FORMAT.format(asyncRatio) + "%").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" async)").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\nThreads: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(threads)).withStyle(ChatFormatting.YELLOW));

        source.sendSuccess(() -> message, false);
    }

    private static void showEntityStats(CommandSourceStack source, int topCount, boolean showTickStats, int ticks) {
        MinecraftServer server = source.getServer();
        server.execute(() -> {
            Map<EntityType<?>, Integer> entityTypeCounts = new HashMap<>();
            Map<EntityType<?>, Boolean> entityTypeAsync = new HashMap<>();
            AtomicInteger totalEntities = new AtomicInteger(0);
            AtomicInteger totalAsyncEntities = new AtomicInteger(0);

            MutableComponent message = AsyncCommand.prefix.copy()
                    .append(Component.literal("Entity Statistics").withStyle(ChatFormatting.GOLD));

            server.getAllLevels().forEach(world -> {
                String worldName = world.dimension().location().toString();
                AtomicInteger worldCount = new AtomicInteger(0);
                AtomicInteger asyncCount = new AtomicInteger(0);

                world.getAllEntities().forEach(entity -> {
                    if (entity.isAlive()) {
                        EntityType<?> entityType = entity.getType();
                        worldCount.incrementAndGet();
                        totalEntities.incrementAndGet();
                        entityTypeCounts.merge(entityType, 1, Integer::sum);

                        boolean isAsync = !ParallelProcessor.shouldTickSynchronously(entity);
                        entityTypeAsync.put(entityType, isAsync);

                        if (isAsync) {
                            asyncCount.incrementAndGet();
                            totalAsyncEntities.incrementAndGet();
                        }
                    }
                });

                message.append(Component.literal("\n" + worldName + ": ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(String.valueOf(worldCount.get())).withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" entities (").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.valueOf(asyncCount.get())).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" async)").withStyle(ChatFormatting.GRAY));
            });

            message.append(Component.literal("\nTotal Entities: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.valueOf(totalEntities.get())).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.valueOf(totalAsyncEntities.get())).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" async)").withStyle(ChatFormatting.GRAY));

            if (topCount > 0 && !entityTypeCounts.isEmpty()) {
                message.append(Component.literal("\n\nTop " + topCount + " Entity Types:").withStyle(ChatFormatting.GOLD));
                int[] rank = {1};

                entityTypeCounts.entrySet().stream()
                        .sorted(Map.Entry.<EntityType<?>, Integer>comparingByValue().reversed())
                        .limit(topCount)
                        .forEach(entry -> {
                            EntityType<?> type = entry.getKey();
                            int count = entry.getValue();
                            boolean isAsync = entityTypeAsync.getOrDefault(type, false);

                            ResourceLocation id = AsyncCommand.getEntityAccess(source).getKey(type);
                            String name = id != null ? id.getPath() : "unknown";

                            message.append(Component.literal("\n" + rank[0] + ". ").withStyle(ChatFormatting.GRAY))
                                    .append(Component.literal(name).withStyle(ChatFormatting.YELLOW))
                                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                                    .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.GREEN))
                                    .append(Component.literal(" [").withStyle(ChatFormatting.DARK_GRAY))
                                    .append(Component.literal(isAsync ? "async" : "sync")
                                            .withStyle(isAsync ? ChatFormatting.AQUA : ChatFormatting.RED))
                                    .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));

                            if (showTickStats && ticks > 0) {
                                double mspt = TickStats.getMSPTForType(type, ticks);
                                message.append(Component.literal(" "))
                                        .append(Component.literal(String.format("%.3fms avg", mspt))
                                                .withStyle(ChatFormatting.GREEN));
                            }

                            rank[0]++;
                        });
            }

            TickStats.resetEntityTickStats();
            source.sendSuccess(() -> message, false);
        });
    }

    private static ChatFormatting getMsptColor(double mspt) {
        if (mspt <= 50.0) return ChatFormatting.GREEN;
        if (mspt <= 100.0) return ChatFormatting.YELLOW;
        return ChatFormatting.RED;
    }

    public static void runStatsThread() {
    }

    public static void shutdown() {
    }
}
