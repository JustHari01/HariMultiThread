/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
 *  net.minecraft.core.BlockPos
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.Mob
 *  net.minecraft.world.entity.MobCategory
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.LocalMobCapCalculator
 *  net.minecraft.world.level.NaturalSpawner$SpawnState
 *  net.minecraft.world.level.PotentialCalculator
 *  net.minecraft.world.level.biome.MobSpawnSettings$MobSpawnCost
 *  net.minecraft.world.level.chunk.ChunkAccess
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.axalotl.async.common.mixin.spawn;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.mixin.accessor.NaturalSpawnerAccessor;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={NaturalSpawner.SpawnState.class})
public abstract class SpawnStateMixin {
    @Shadow
    @Final
    private PotentialCalculator spawnPotential;
    @Shadow
    @Final
    private Object2IntOpenHashMap<MobCategory> mobCategoryCounts;
    @Shadow
    @Final
    private LocalMobCapCalculator localMobCapCalculator;
    @Shadow
    private BlockPos lastCheckedPos;
    @Shadow
    private EntityType<?> lastCheckedType;
    @Shadow
    private double lastCharge;
    @Unique
    private final Object async$lock = new Object();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Inject(method={"afterSpawn"}, at={@At(value="HEAD")}, cancellable=true)
    private void async$afterSpawn(Mob mob, ChunkAccess chunk, CallbackInfo ci) {
        MobSpawnSettings.MobSpawnCost cost;
        if (AsyncConfig.disabled.getValue().booleanValue() || !AsyncConfig.enableAsyncSpawn.getValue().booleanValue()) {
            return;
        }
        ci.cancel();
        EntityType entityType = mob.getType();
        BlockPos blockPos = mob.blockPosition();
        double charge = blockPos.equals((Object)this.lastCheckedPos) && entityType == this.lastCheckedType ? this.lastCharge : ((cost = NaturalSpawnerAccessor.invokeGetRoughBiome(blockPos, chunk).getMobSettings().getMobSpawnCost(entityType)) != null ? cost.charge() : 0.0);
        MobCategory category = entityType.getCategory();
        Object object = this.async$lock;
        synchronized (object) {
            this.spawnPotential.addCharge(blockPos, charge);
            this.mobCategoryCounts.addTo(category, 1);
            this.localMobCapCalculator.addMob(new ChunkPos(blockPos), category);
        }
    }
}

