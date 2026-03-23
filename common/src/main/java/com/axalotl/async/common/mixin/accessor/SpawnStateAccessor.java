/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
 *  net.minecraft.world.entity.MobCategory
 *  net.minecraft.world.level.LocalMobCapCalculator
 *  net.minecraft.world.level.NaturalSpawner$SpawnState
 *  net.minecraft.world.level.PotentialCalculator
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package com.axalotl.async.common.mixin.accessor;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={NaturalSpawner.SpawnState.class})
public interface SpawnStateAccessor {
    @Invoker(value="<init>")
    public static NaturalSpawner.SpawnState create(int spawnableChunkCount, Object2IntOpenHashMap<MobCategory> mobCategoryCounts, PotentialCalculator spawnPotential, LocalMobCapCalculator localMobCapCalculator) {
        throw new AssertionError();
    }
}

