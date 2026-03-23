/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
 *  net.minecraft.core.BlockPos
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.Mob
 *  net.minecraft.world.entity.MobCategory
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.LocalMobCapCalculator
 *  net.minecraft.world.level.NaturalSpawner
 *  net.minecraft.world.level.NaturalSpawner$ChunkGetter
 *  net.minecraft.world.level.NaturalSpawner$SpawnState
 *  net.minecraft.world.level.PotentialCalculator
 *  net.minecraft.world.level.biome.MobSpawnSettings$MobSpawnCost
 *  net.minecraft.world.level.chunk.ChunkAccess
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Overwrite
 *  org.spongepowered.asm.mixin.Unique
 */
package com.axalotl.async.common.mixin.spawn;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.mixin.accessor.NaturalSpawnerAccessor;
import com.axalotl.async.common.mixin.accessor.SpawnStateAccessor;
import com.axalotl.async.common.parallelised.utils.EntitySpawnData;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = { NaturalSpawner.class })
public abstract class NaturalSpawnerMixin {
    @Overwrite
    public static NaturalSpawner.SpawnState createState(int spawnableChunkCount, Iterable<Entity> entities,
            NaturalSpawner.ChunkGetter chunkGetter, LocalMobCapCalculator localMobCapCalculator) {
        if (AsyncConfig.disabled.getValue().booleanValue() || !AsyncConfig.enableAsyncSpawn.getValue().booleanValue()) {
            return NaturalSpawnerMixin.async$createStateVanilla(spawnableChunkCount, entities, chunkGetter,
                    localMobCapCalculator);
        }
        return NaturalSpawnerMixin.async$createStateParallel(spawnableChunkCount, entities, chunkGetter,
                localMobCapCalculator);
    }

    @Unique
    private static NaturalSpawner.SpawnState async$createStateVanilla(int spawnableChunkCount,
            Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter,
            LocalMobCapCalculator localMobCapCalculator) {
        PotentialCalculator potentialCalculator = new PotentialCalculator();
        Object2IntOpenHashMap mobCounts = new Object2IntOpenHashMap();
        for (Entity entity : entities) {
            MobCategory category;
            Mob mob;
            if (entity instanceof Mob
                    && ((mob = (Mob) entity).isPersistenceRequired() || mob.requiresCustomPersistence())
                    || (category = entity.getType().getCategory()) == MobCategory.MISC)
                continue;
            BlockPos blockPos = entity.blockPosition();
            chunkGetter.query(ChunkPos.asLong((BlockPos) blockPos), chunk -> {
                MobSpawnSettings.MobSpawnCost cost = NaturalSpawnerAccessor
                        .invokeGetRoughBiome(blockPos, (ChunkAccess) chunk).getMobSettings()
                        .getMobSpawnCost(entity.getType());
                if (cost != null) {
                    potentialCalculator.addCharge(blockPos, cost.charge());
                }
                if (entity instanceof Mob) {
                    localMobCapCalculator.addMob(chunk.getPos(), category);
                }
                mobCounts.addTo((Object) category, 1);
            });
        }
        return SpawnStateAccessor.create(spawnableChunkCount, (Object2IntOpenHashMap<MobCategory>) mobCounts,
                potentialCalculator, localMobCapCalculator);
    }

    @Unique
    private static NaturalSpawner.SpawnState async$createStateParallel(int spawnableChunkCount,
            Iterable<Entity> entities, NaturalSpawner.ChunkGetter chunkGetter,
            LocalMobCapCalculator localMobCapCalculator) {
        List<Entity> entityList;
        if (entities instanceof List) {
            entityList = (List<Entity>) entities;
        } else {
            entityList = new ArrayList<>();
            entities.forEach(entityList::add);
        }
        if (entityList.isEmpty()) {
            return SpawnStateAccessor.create(spawnableChunkCount, new Object2IntOpenHashMap<>(),
                    new PotentialCalculator(), localMobCapCalculator);
        }
        ConcurrentLinkedQueue<EntitySpawnData> results = new ConcurrentLinkedQueue<>();
        entityList.parallelStream().forEach(entity -> {
            Mob mob;
            if (entity instanceof Mob
                    && ((mob = (Mob) entity).isPersistenceRequired() || mob.requiresCustomPersistence())) {
                return;
            }
            MobCategory category = entity.getType().getCategory();
            if (category != MobCategory.MISC) {
                BlockPos blockPos = entity.blockPosition();
                chunkGetter.query(ChunkPos.asLong((BlockPos) blockPos), chunk -> {
                    MobSpawnSettings.MobSpawnCost cost = NaturalSpawnerAccessor
                            .invokeGetRoughBiome(blockPos, (ChunkAccess) chunk).getMobSettings()
                            .getMobSpawnCost(entity.getType());
                    results.add(new EntitySpawnData(blockPos, category, cost != null ? cost.charge() : 0.0,
                            entity instanceof Mob, chunk.getPos()));
                });
            }
        });
        PotentialCalculator potentialCalculator = new PotentialCalculator();
        Object2IntOpenHashMap mobCounts = new Object2IntOpenHashMap();
        for (EntitySpawnData data : results) {
            potentialCalculator.addCharge(data.pos(), data.charge());
            if (data.isMob()) {
                localMobCapCalculator.addMob(data.chunkPos(), data.category());
            }
            mobCounts.addTo((Object) data.category(), 1);
        }
        return SpawnStateAccessor.create(spawnableChunkCount, (Object2IntOpenHashMap<MobCategory>) mobCounts,
                potentialCalculator, localMobCapCalculator);
    }
}
