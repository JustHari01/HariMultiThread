/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.core.BlockPos
 *  net.minecraft.server.level.ChunkHolder
 *  net.minecraft.world.level.chunk.LevelChunk
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package com.axalotl.async.common.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={ChunkHolder.class})
public class ChunkHolderMixin {
    @Unique
    private final Object async$lock = new Object();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"broadcastChanges"})
    private void wrapBroadcastChanges(LevelChunk chunk, Operation<Void> original) {
        Object object = this.async$lock;
        synchronized (object) {
            original.call(new Object[]{chunk});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"blockChanged"})
    private void wrapBlockChanged(BlockPos pos, Operation<Boolean> original) {
        Object object = this.async$lock;
        synchronized (object) {
            original.call(new Object[]{pos});
        }
    }
}

