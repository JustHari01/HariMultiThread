/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  it.unimi.dsi.fastutil.ints.Int2ObjectMap
 *  net.minecraft.world.level.chunk.LevelChunk
 *  net.minecraft.world.level.gameevent.GameEventListenerRegistry
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.parallelised.fastutil.Int2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={LevelChunk.class})
public abstract class LevelChunkMixin {
    @Mutable
    @Shadow
    @Final
    private Int2ObjectMap<GameEventListenerRegistry> gameEventListenerRegistrySections;

    @Inject(method={"<init>*"}, at={@At(value="RETURN")})
    private void init(CallbackInfo ci) {
        this.gameEventListenerRegistrySections = new Int2ObjectConcurrentHashMap<GameEventListenerRegistry>();
    }

    @WrapMethod(method={"getListenerRegistry"})
    private synchronized GameEventListenerRegistry getListenerRegistry(int ySectionCoord, Operation<GameEventListenerRegistry> original) {
        return (GameEventListenerRegistry)original.call(new Object[]{ySectionCoord});
    }
}

