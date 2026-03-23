/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.core.BlockPos
 *  net.minecraft.world.level.redstone.CollectingNeighborUpdater
 *  net.minecraft.world.level.redstone.CollectingNeighborUpdater$NeighborUpdates
 *  net.minecraft.world.level.redstone.NeighborUpdater
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Shadow
 */
package com.axalotl.async.common.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.NeighborUpdater;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value={CollectingNeighborUpdater.class})
public abstract class CollectingNeighborUpdaterMixin
implements NeighborUpdater {
    @Shadow
    @Final
    @Mutable
    private List<CollectingNeighborUpdater.NeighborUpdates> addedThisLayer = new CopyOnWriteArrayList<CollectingNeighborUpdater.NeighborUpdates>();

    @WrapMethod(method={"addAndRun"})
    private synchronized void syncAddAndRun(BlockPos pos, CollectingNeighborUpdater.NeighborUpdates entry, Operation<Void> original) {
        original.call(new Object[]{pos, entry});
    }
}

