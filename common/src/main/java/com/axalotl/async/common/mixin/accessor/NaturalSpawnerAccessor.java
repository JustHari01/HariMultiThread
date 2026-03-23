/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.world.level.NaturalSpawner
 *  net.minecraft.world.level.biome.Biome
 *  net.minecraft.world.level.chunk.ChunkAccess
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package com.axalotl.async.common.mixin.accessor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={NaturalSpawner.class})
public interface NaturalSpawnerAccessor {
    @Invoker(value="getRoughBiome")
    public static Biome invokeGetRoughBiome(BlockPos pos, ChunkAccess chunk) {
        throw new AssertionError();
    }
}

