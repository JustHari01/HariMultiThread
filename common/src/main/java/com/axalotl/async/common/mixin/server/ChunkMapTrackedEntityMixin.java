/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.server.level.ChunkMap$TrackedEntity
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.server.network.ServerGamePacketListenerImpl
 *  net.minecraft.world.entity.Entity
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Set;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ChunkMap.TrackedEntity.class})
public class ChunkMapTrackedEntityMixin {
    @Shadow
    private Set<ServerGamePacketListenerImpl> seenBy = ConcurrentCollections.newHashSet();
    @Shadow
    @Final
    Entity entity;

    @Inject(method={"updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V"}, at={@At(value="HEAD")}, cancellable=true)
    private void updatePlayerNull(ServerPlayer player, CallbackInfo ci) {
        if (this.entity.isRemoved()) {
            ci.cancel();
        }
    }

    @WrapMethod(method={"updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V"})
    private synchronized void updatePlayer(ServerPlayer player, Operation<Void> original) {
        original.call(new Object[]{player});
    }
}

