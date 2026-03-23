/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.world.entity.Mob
 *  net.minecraft.world.entity.ai.navigation.PathNavigation
 *  net.minecraft.world.level.pathfinder.Node
 *  net.minecraft.world.level.pathfinder.Path
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.axalotl.async.common.mixin.entity.movement;

import com.axalotl.async.common.parallelised.utils.AsyncSafeNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={PathNavigation.class})
public abstract class AsyncPathNavigationMixin
implements AsyncSafeNavigation {
    @Shadow
    protected Path path;
    @Shadow
    protected boolean hasDelayedRecomputation;
    @Shadow
    @Final
    protected Mob mob;
    @Unique
    private volatile AsyncSafeNavigation.PathSnapshot async$snapshot = null;
    @Unique
    private final Object async$lock = new Object();

    @Inject(method={"moveTo(Lnet/minecraft/world/level/pathfinder/Path;D)Z"}, at={@At(value="RETURN")})
    private void async$onMoveTo(Path path, double speed, CallbackInfoReturnable<Boolean> cir) {
        this.async$updateSnapshot();
    }

    @Inject(method={"recomputePath"}, at={@At(value="RETURN")})
    private void async$onRecompute(CallbackInfo ci) {
        this.async$updateSnapshot();
    }

    @Inject(method={"stop"}, at={@At(value="RETURN")})
    private void async$onStop(CallbackInfo ci) {
        this.async$snapshot = null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Inject(method={"tick"}, at={@At(value="RETURN")})
    private void async$onTick(CallbackInfo ci) {
        Object object = this.async$lock;
        synchronized (object) {
            int nextIndex;
            Path currentPath = this.path;
            if (currentPath == null || currentPath.isDone()) {
                return;
            }
            AsyncSafeNavigation.PathSnapshot currentSnapshot = this.async$snapshot;
            if (currentSnapshot == null) {
                return;
            }
            int nodeCount = currentPath.getNodeCount();
            int remainingNodes = nodeCount - (nextIndex = currentPath.getNextNodeIndex());
            if (remainingNodes <= 0) {
                return;
            }
            Node endNode = currentPath.getEndNode();
            if (endNode == null) {
                return;
            }
            double mobX = this.mob.getX();
            double mobY = this.mob.getY();
            double mobZ = this.mob.getZ();
            double newCenterX = ((double)endNode.x + mobX) / 2.0;
            double newCenterY = ((double)endNode.y + mobY) / 2.0;
            double newCenterZ = ((double)endNode.z + mobZ) / 2.0;
            double dx = newCenterX - currentSnapshot.centerX();
            double dy = newCenterY - currentSnapshot.centerY();
            double dz = newCenterZ - currentSnapshot.centerZ();
            double newMaxDistSq = (double)remainingNodes * (double)remainingNodes;
            if (dx * dx + dy * dy + dz * dz > 1.0 || Math.abs(newMaxDistSq - currentSnapshot.maxDistanceSq()) > (double)remainingNodes) {
                this.async$snapshot = new AsyncSafeNavigation.PathSnapshot(newCenterX, newCenterY, newCenterZ, newMaxDistSq);
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Unique
    private void async$updateSnapshot() {
        Object object = this.async$lock;
        synchronized (object) {
            Path currentPath = this.path;
            if (currentPath == null || currentPath.isDone() || currentPath.getNodeCount() == 0) {
                this.async$snapshot = null;
                return;
            }
            Node endNode = currentPath.getEndNode();
            if (endNode == null) {
                this.async$snapshot = null;
                return;
            }
            int remainingNodes = currentPath.getNodeCount() - currentPath.getNextNodeIndex();
            if (remainingNodes <= 0) {
                this.async$snapshot = null;
                return;
            }
            double centerX = ((double)endNode.x + this.mob.getX()) / 2.0;
            double centerY = ((double)endNode.y + this.mob.getY()) / 2.0;
            double centerZ = ((double)endNode.z + this.mob.getZ()) / 2.0;
            double maxDistanceSq = (double)remainingNodes * (double)remainingNodes;
            this.async$snapshot = new AsyncSafeNavigation.PathSnapshot(centerX, centerY, centerZ, maxDistanceSq);
        }
    }

    @Override
    public boolean async$shouldRecomputePathSafe(BlockPos pos) {
        if (this.hasDelayedRecomputation) {
            return false;
        }
        AsyncSafeNavigation.PathSnapshot snapshot = this.async$snapshot;
        if (snapshot == null) {
            return false;
        }
        return snapshot.shouldRecompute(pos);
    }
}

