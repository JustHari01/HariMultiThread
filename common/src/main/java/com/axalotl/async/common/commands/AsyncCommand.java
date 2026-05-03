/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.commands.Commands
 *  net.minecraft.core.Registry
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.entity.EntityType
 */
package com.axalotl.async.common.commands;

import com.axalotl.async.common.commands.ConfigCommand;
import com.axalotl.async.common.commands.GpuCommand;
import com.axalotl.async.common.commands.StatsCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

public class AsyncCommand {
    public static final Component prefix = Component.literal((String)"\u00a78[\u00a7f\ud83c\udf00\u00a78]\u00a77 ");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> main = Commands.literal((String)"async");
        main = ConfigCommand.registerConfig(main);
        main = StatsCommand.registerStatus(main);
        main = GpuCommand.registerGpu(main);
        dispatcher.register(main);
    }

    public static Registry<EntityType<?>> getEntityAccess(CommandSourceStack source) {
        return source.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
    }
}

