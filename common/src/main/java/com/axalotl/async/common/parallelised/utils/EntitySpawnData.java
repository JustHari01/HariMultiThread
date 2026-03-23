/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.world.entity.MobCategory
 *  net.minecraft.world.level.ChunkPos
 */
package com.axalotl.async.common.parallelised.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;

public record EntitySpawnData(BlockPos pos, MobCategory category, double charge, boolean isMob, ChunkPos chunkPos) {
}

