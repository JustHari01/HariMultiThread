package com.axalotl.async.common.commands;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.commands.AsyncCommand;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.platform.Permission;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
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

public class StatsCommand {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.##");

    public static LiteralArgumentBuilder<CommandSourceStack> registerStatus(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return (LiteralArgumentBuilder) root.then(((LiteralArgumentBuilder) ((LiteralArgumentBuilder) Commands
                .literal((String) "stats").requires(Permission.require("command.statistics", 0))).executes(cmdCtx -> {
                    StatsCommand.showGeneralStats((CommandSourceStack) cmdCtx.getSource());
                    return 1;
                })).then(((LiteralArgumentBuilder) Commands.literal((String) "entity").executes(cmdCtx -> {
                    StatsCommand.showEntityStats((CommandSourceStack) cmdCtx.getSource(), 0);
                    return 1;
                })).then(Commands
                        .argument((String) "count", (ArgumentType) IntegerArgumentType.integer((int) 1, (int) 100))
                        .executes(cmdCtx -> {
                            int count = IntegerArgumentType.getInteger((CommandContext) cmdCtx, (String) "count");
                            StatsCommand.showEntityStats((CommandSourceStack) cmdCtx.getSource(), count);
                            return 1;
                        }))));
    }

    private static void showGeneralStats(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        double mspt = server.getAverageTickTime();
        int totalEntities = 0;
        int asyncEntities = 0;
        for (ServerLevel world : server.getAllLevels()) {
            for (Entity entity : world.getAllEntities()) {
                if (!entity.isAlive())
                    continue;
                ++totalEntities;
                if (ParallelProcessor.shouldTickSynchronously(entity))
                    continue;
                ++asyncEntities;
            }
        }
        double asyncRatio = totalEntities > 0 ? (double) asyncEntities * 100.0 / (double) totalEntities : 0.0;
        int threads = 0;
        if (ParallelProcessor.tickPool instanceof ThreadPoolExecutor pool && !pool.isShutdown()) {
            threads = pool.getCorePoolSize();
        }
        boolean enabled = AsyncConfig.disabled.getValue() == false;
        boolean asyncSpawn = AsyncConfig.enableAsyncSpawn.getValue();
        boolean asyncRandomTicks = AsyncConfig.enableAsyncRandomTicks.getValue();
        MutableComponent message = AsyncCommand.prefix.copy()
                .append((Component) Component.literal((String) "Performance Statistics").withStyle(ChatFormatting.GOLD))
                .append((Component) Component.literal((String) "\nStatus: ").withStyle(ChatFormatting.WHITE))
                .append((Component) Component.literal((String) (enabled ? "Enabled" : "Disabled"))
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append((Component) Component.literal((String) "\nAsync Spawn: ").withStyle(ChatFormatting.WHITE))
                .append((Component) Component.literal((String) (asyncSpawn ? "Enabled" : "Disabled"))
                        .withStyle(asyncSpawn ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append((Component) Component.literal((String) "\nAsync Random Ticks: ")
                        .withStyle(ChatFormatting.WHITE))
                .append((Component) Component.literal((String) (asyncRandomTicks ? "Enabled" : "Disabled"))
                        .withStyle(asyncRandomTicks ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append((Component) Component.literal((String) "\nMSPT: ").withStyle(ChatFormatting.WHITE))
                .append((Component) Component.literal((String) (DECIMAL_FORMAT.format(mspt) + "ms"))
                        .withStyle(StatsCommand.getMsptColor(mspt)))
                .append((Component) Component.literal((String) "\nEntities: ").withStyle(ChatFormatting.WHITE))
                .append((Component) Component.literal((String) String.valueOf(totalEntities))
                        .withStyle(ChatFormatting.GREEN))
                .append((Component) Component.literal((String) " (").withStyle(ChatFormatting.GRAY))
                .append((Component) Component.literal((String) (DECIMAL_FORMAT.format(asyncRatio) + "%"))
                        .withStyle(ChatFormatting.AQUA))
                .append((Component) Component.literal((String) " async)").withStyle(ChatFormatting.GRAY))
                .append((Component) Component.literal((String) "\nThreads: ").withStyle(ChatFormatting.WHITE))
                .append((Component) Component.literal((String) String.valueOf(threads))
                        .withStyle(ChatFormatting.YELLOW));
        source.sendSuccess(() -> message, false);
    }

    private static void showEntityStats(CommandSourceStack source, int topCount) {
        MinecraftServer server = source.getServer();
        server.execute(() -> {
            HashMap<EntityType<?>, Integer> entityTypeCounts = new HashMap<>();
            HashMap<EntityType<?>, Boolean> entityTypeAsync = new HashMap<>();
            AtomicInteger totalEntities = new AtomicInteger(0);
            AtomicInteger totalAsyncEntities = new AtomicInteger(0);
            MutableComponent message = AsyncCommand.prefix.copy()
                    .append((Component) Component.literal((String) "Entity Statistics").withStyle(ChatFormatting.GOLD));
            server.getAllLevels().forEach(world -> {
                String worldName = world.dimension().location().toString();
                AtomicInteger worldCount = new AtomicInteger(0);
                AtomicInteger asyncCount = new AtomicInteger(0);
                world.getAllEntities().forEach(entity -> {
                    if (entity.isAlive()) {
                        EntityType entityType = entity.getType();
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
                message.append((Component) Component.literal((String) ("\n" + worldName + ": "))
                        .withStyle(ChatFormatting.YELLOW))
                        .append((Component) Component.literal((String) String.valueOf(worldCount.get()))
                                .withStyle(ChatFormatting.GREEN))
                        .append((Component) Component.literal((String) " entities (").withStyle(ChatFormatting.GRAY))
                        .append((Component) Component.literal((String) String.valueOf(asyncCount.get()))
                                .withStyle(ChatFormatting.AQUA))
                        .append((Component) Component.literal((String) " async)").withStyle(ChatFormatting.GRAY));
            });
            message.append((Component) Component.literal((String) "\nTotal Entities: ").withStyle(ChatFormatting.WHITE))
                    .append((Component) Component.literal((String) String.valueOf(totalEntities.get()))
                            .withStyle(ChatFormatting.GOLD))
                    .append((Component) Component.literal((String) " (").withStyle(ChatFormatting.GRAY))
                    .append((Component) Component.literal((String) String.valueOf(totalAsyncEntities.get()))
                            .withStyle(ChatFormatting.AQUA))
                    .append((Component) Component.literal((String) " async)").withStyle(ChatFormatting.GRAY));
            if (topCount > 0 && !entityTypeCounts.isEmpty()) {
                message.append((Component) Component.literal((String) ("\n\nTop " + topCount + " Entity Types:"))
                        .withStyle(ChatFormatting.GOLD));
                int[] rank = new int[] { 1 };

                @SuppressWarnings("unchecked")
                Map<EntityType<?>, Integer> counts = (Map<EntityType<?>, Integer>) entityTypeCounts;
                @SuppressWarnings("unchecked")
                Map<EntityType<?>, Boolean> asyncs = (Map<EntityType<?>, Boolean>) entityTypeAsync;
                counts.entrySet().stream().sorted(Map.Entry.<EntityType<?>, Integer>comparingByValue().reversed())
                        .limit(topCount).forEach(entry -> {
                            EntityType<?> type = entry.getKey();
                            int count = entry.getValue();
                            boolean isAsync = asyncs.getOrDefault(type, false);
                            ResourceLocation id = AsyncCommand.getEntityAccess(source).getKey(type);
                            String name = id.getPath();
                            message.append((Component) Component.literal((String) ("\n" + rank[0] + ". "))
                                    .withStyle(ChatFormatting.GRAY))
                                    .append((Component) Component.literal((String) name)
                                            .withStyle(ChatFormatting.YELLOW))
                                    .append((Component) Component.literal((String) ": ").withStyle(ChatFormatting.GRAY))
                                    .append((Component) Component.literal((String) String.valueOf(count))
                                            .withStyle(ChatFormatting.GREEN))
                                    .append((Component) Component.literal((String) " [")
                                            .withStyle(ChatFormatting.DARK_GRAY))
                                    .append((Component) Component.literal((String) (isAsync ? "async" : "sync"))
                                            .withStyle(isAsync ? ChatFormatting.AQUA : ChatFormatting.RED))
                                    .append((Component) Component.literal((String) "]")
                                            .withStyle(ChatFormatting.DARK_GRAY));
                            rank[0] = rank[0] + 1;
                        });
            }
            source.sendSuccess(() -> message, false);
        });
    }

    private static ChatFormatting getMsptColor(double mspt) {
        if (mspt <= 50.0) {
            return ChatFormatting.GREEN;
        }
        if (mspt <= 100.0) {
            return ChatFormatting.YELLOW;
        }
        return ChatFormatting.RED;
    }

    public static void runStatsThread() {
    }

    public static void shutdown() {
    }
}