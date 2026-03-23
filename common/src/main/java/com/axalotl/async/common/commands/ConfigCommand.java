/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.arguments.ArgumentType
 *  com.mojang.brigadier.arguments.BoolArgumentType
 *  com.mojang.brigadier.arguments.StringArgumentType
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  com.mojang.brigadier.context.CommandContext
 *  com.mojang.brigadier.suggestion.SuggestionsBuilder
 *  net.minecraft.ChatFormatting
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.commands.Commands
 *  net.minecraft.commands.arguments.ResourceLocationArgument
 *  net.minecraft.core.Registry
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.world.entity.EntityType
 */
package com.axalotl.async.common.commands;

import com.axalotl.async.common.commands.AsyncCommand;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.platform.Permission;
import com.axalotl.async.common.platform.PlatformUtils;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public class ConfigCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> registerConfig(LiteralArgumentBuilder<CommandSourceStack> root) {
        return (LiteralArgumentBuilder)root.then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"config").requires(Permission.require("command.config", 4))).then(ConfigCommand.buildToggleCommand())).then(ConfigCommand.buildReloadCommand())).then(ConfigCommand.buildSynchronizedEntitiesCommand())).then(ConfigCommand.buildAsyncEntitySpawnCommand())).then(ConfigCommand.buildAsyncRandomTicksCommand()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildReloadCommand() {
        return (LiteralArgumentBuilder)Commands.literal((String)"reload").executes(ctx -> {
            try {
                PlatformUtils.reloadConfig();
                MutableComponent msg = AsyncCommand.prefix.copy().append((Component)Component.literal((String)"Configuration reloaded successfully.").withStyle(style -> style.withColor(ChatFormatting.GREEN)));
                ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> msg, true);
            }
            catch (Exception e) {
                MutableComponent msg = AsyncCommand.prefix.copy().append((Component)Component.literal((String)("Failed to reload config: " + e.getMessage())).withStyle(style -> style.withColor(ChatFormatting.RED)));
                ((CommandSourceStack)ctx.getSource()).sendFailure((Component)msg);
            }
            return 1;
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildToggleCommand() {
        return (LiteralArgumentBuilder)Commands.literal((String)"toggle").executes(ctx -> {
            AsyncConfig.disabled.setValue(AsyncConfig.disabled.getValue() == false);
            PlatformUtils.saveConfig();
            ConfigCommand.sendMessage((CommandContext<CommandSourceStack>)ctx, "Async is now ", AsyncConfig.disabled.getValue() != false ? "disabled" : "enabled", true);
            return 1;
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSynchronizedEntitiesCommand() {
        return (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"synchronizedEntities").executes(ctx -> {
            ConfigCommand.displaySynchronizedEntities((CommandContext<CommandSourceStack>)ctx);
            return 1;
        })).then(ConfigCommand.buildAddEntityCommand())).then(ConfigCommand.buildRemoveEntityCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAddEntityCommand() {
        return (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"add").then(Commands.argument((String)"entity", (ArgumentType)ResourceLocationArgument.id()).suggests((context, builder) -> {
            Registry<EntityType<?>> entityAccess = AsyncCommand.getEntityAccess((CommandSourceStack)context.getSource());
            entityAccess.keySet().forEach(id -> builder.suggest(id.toString()));
            entityAccess.keySet().stream().map(ResourceLocation::getNamespace).distinct().forEach(ns -> builder.suggest(ns + ":*"));
            return builder.buildFuture();
        }).executes(ctx -> {
            ConfigCommand.addEntity((CommandContext<CommandSourceStack>)ctx);
            return 1;
        }))).then(Commands.argument((String)"namespace", (ArgumentType)StringArgumentType.greedyString()).executes(ctx -> {
            ConfigCommand.addNamespace((CommandContext<CommandSourceStack>)ctx);
            return 1;
        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRemoveEntityCommand() {
        return (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"remove").then(Commands.argument((String)"entity", (ArgumentType)ResourceLocationArgument.id()).suggests((context, builder) -> {
            AsyncConfig.synchronizedEntities.getValue().forEach(arg_0 -> ((SuggestionsBuilder)builder).suggest(arg_0));
            return builder.buildFuture();
        }).executes(ctx -> {
            ConfigCommand.removeEntity((CommandContext<CommandSourceStack>)ctx);
            return 1;
        }))).then(Commands.argument((String)"namespace", (ArgumentType)StringArgumentType.greedyString()).executes(ctx -> {
            ConfigCommand.removeNamespace((CommandContext<CommandSourceStack>)ctx);
            return 1;
        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAsyncEntitySpawnCommand() {
        return (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"setAsyncEntitySpawn").executes(ctx -> {
            ConfigCommand.sendMessage((CommandContext<CommandSourceStack>)ctx, "Current value of async entity spawn: ", String.valueOf(AsyncConfig.enableAsyncSpawn), false);
            return 1;
        })).then(Commands.argument((String)"value", (ArgumentType)BoolArgumentType.bool()).executes(ctx -> {
            boolean value = BoolArgumentType.getBool((CommandContext)ctx, (String)"value");
            AsyncConfig.enableAsyncSpawn.setValue(value);
            PlatformUtils.saveConfig();
            ConfigCommand.sendMessage((CommandContext<CommandSourceStack>)ctx, "Async Entity Spawn set to ", String.valueOf(value), true);
            return 1;
        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAsyncRandomTicksCommand() {
        return (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"setAsyncRandomTicks").executes(ctx -> {
            ConfigCommand.sendMessage((CommandContext<CommandSourceStack>)ctx, "Current value of async random ticks: ", String.valueOf(AsyncConfig.enableAsyncRandomTicks), false);
            return 1;
        })).then(Commands.argument((String)"value", (ArgumentType)BoolArgumentType.bool()).executes(ctx -> {
            boolean value = BoolArgumentType.getBool((CommandContext)ctx, (String)"value");
            AsyncConfig.enableAsyncRandomTicks.setValue(value);
            PlatformUtils.saveConfig();
            ConfigCommand.sendMessage((CommandContext<CommandSourceStack>)ctx, "Async Random Ticks set to ", String.valueOf(value), true);
            return 1;
        }));
    }

    private static void displaySynchronizedEntities(CommandContext<CommandSourceStack> ctx) {
        Set<String> entities = AsyncConfig.synchronizedEntities.getValue();
        MutableComponent message = AsyncCommand.prefix.copy().append((Component)Component.literal((String)"Synchronized Entities: ").withStyle(style -> style.withColor(ChatFormatting.WHITE)));
        if (entities.isEmpty()) {
            message.append((Component)Component.literal((String)"No entities synchronized.").withStyle(style -> style.withColor(ChatFormatting.RED)));
        } else {
            message.append((Component)Component.literal((String)"\n"));
            entities.forEach(entity -> message.append((Component)Component.literal((String)"- ").withStyle(style -> style.withColor(ChatFormatting.GREEN))).append((Component)Component.literal((String)entity).withStyle(style -> style.withColor(ChatFormatting.YELLOW))).append((Component)Component.literal((String)"\n")));
        }
        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> message, false);
    }

    private static void addEntity(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, (String)"entity");
        Registry<EntityType<?>> entityAccess = AsyncCommand.getEntityAccess((CommandSourceStack)ctx.getSource());
        if (!entityAccess.containsKey(id)) {
            ConfigCommand.sendErrorMessage(ctx, "Error entity class ", id.toString(), " does not exist.");
            return;
        }
        if (AsyncConfig.isEntitySynchronized(id)) {
            ConfigCommand.sendErrorMessage(ctx, "Error entity class ", id.toString(), " is already synchronized.");
            return;
        }
        AsyncConfig.syncEntity(id.toString());
        ConfigCommand.sendMessage(ctx, "Entity class ", id.toString(), " has been added to the synchronized list.");
    }

    private static void addNamespace(CommandContext<CommandSourceStack> ctx) {
        String namespace = StringArgumentType.getString(ctx, (String)"namespace");
        if (AsyncConfig.matchesExistingNamespaceWildcard(namespace, (CommandSourceStack)ctx.getSource())) {
            AsyncConfig.syncEntity(namespace);
            ConfigCommand.sendMessage(ctx, "All entities with namespace ", namespace, " has been added to the synchronized list.");
        } else {
            ConfigCommand.sendErrorMessage(ctx, "Error namespace ", namespace, " does not exist.");
        }
    }

    private static void removeEntity(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, (String)"entity");
        if (!AsyncConfig.isEntitySynchronized(id)) {
            ConfigCommand.sendErrorMessage(ctx, "Error entity class ", id.toString(), " is not in the synchronized list.");
            return;
        }
        AsyncConfig.removeEntity(id.toString());
        ConfigCommand.sendMessage(ctx, "Entity class ", id.toString(), " has been removed from synchronized list.");
    }

    private static void removeNamespace(CommandContext<CommandSourceStack> ctx) {
        String namespace = StringArgumentType.getString(ctx, (String)"namespace");
        ResourceLocation id = ResourceLocation.tryParse((String)namespace);
        if (id != null) {
            return;
        }
        if (!AsyncConfig.synchronizedEntities.getValue().contains(namespace)) {
            ConfigCommand.sendErrorMessage(ctx, "Error namespace ", namespace, " is not in the synchronized list.");
            return;
        }
        AsyncConfig.removeEntity(namespace);
        ConfigCommand.sendMessage(ctx, "All entities with namespace ", namespace, " has been removed from synchronized list.");
    }

    private static void sendMessage(CommandContext<CommandSourceStack> ctx, String prefix, String highlight, boolean broadcast) {
        MutableComponent message = AsyncCommand.prefix.copy().append((Component)Component.literal((String)prefix).withStyle(style -> style.withColor(ChatFormatting.WHITE))).append((Component)Component.literal((String)highlight).withStyle(style -> style.withColor(ChatFormatting.GREEN)));
        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> message, broadcast);
    }

    private static void sendMessage(CommandContext<CommandSourceStack> ctx, String prefix, String highlight, String suffix) {
        MutableComponent message = AsyncCommand.prefix.copy().append((Component)Component.literal((String)prefix).withStyle(style -> style.withColor(ChatFormatting.WHITE))).append((Component)Component.literal((String)highlight).withStyle(style -> style.withColor(ChatFormatting.GREEN))).append((Component)Component.literal((String)suffix).withStyle(style -> style.withColor(ChatFormatting.WHITE)));
        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> message, true);
    }

    private static void sendErrorMessage(CommandContext<CommandSourceStack> ctx, String prefix, String error, String suffix) {
        MutableComponent message = AsyncCommand.prefix.copy().append((Component)Component.literal((String)prefix).withStyle(style -> style.withColor(ChatFormatting.RED))).append((Component)Component.literal((String)error).withStyle(style -> style.withColor(ChatFormatting.RED))).append((Component)Component.literal((String)suffix).withStyle(style -> style.withColor(ChatFormatting.RED)));
        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> message, true);
    }
}

