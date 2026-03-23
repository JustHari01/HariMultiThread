/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 */
package com.axalotl.async.common.parallelised.utils;

import net.minecraft.core.BlockPos;

public interface AsyncSafeNavigation {
    public boolean async$shouldRecomputePathSafe(BlockPos var1);

    public record PathSnapshot(double centerX, double centerY, double centerZ, double maxDistanceSq) {
        public boolean shouldRecompute(BlockPos pos) {
            double dz;
            double dy;
            double dx = (double)pos.getX() + 0.5 - this.centerX;
            return dx * dx + (dy = (double)pos.getY() + 0.5 - this.centerY) * dy + (dz = (double)pos.getZ() + 0.5 - this.centerZ) * dz <= this.maxDistanceSq;
        }
    }
}

