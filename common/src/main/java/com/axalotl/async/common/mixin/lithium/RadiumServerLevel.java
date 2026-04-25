package com.axalotl.async.common.mixin.lithium;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import java.util.Set;
import me.jellysquid.mods.lithium.common.entity.NavigatingEntity;
import me.jellysquid.mods.lithium.common.world.ServerWorldExtended;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(value = {ServerLevel.class}, priority = 1500)
public abstract class RadiumServerLevel extends Level implements WorldGenLevel, ServerWorldExtended {
    @Unique
    private final Set<PathNavigation> async$activeNavigationsOver = ConcurrentCollections.newHashSet();

    protected RadiumServerLevel(WritableLevelData properties, ResourceKey<Level> registryRef, RegistryAccess registryManager, Holder<DimensionType> dimensionEntry, Supplier<ProfilerFiller> profiler, boolean isClient, boolean debugWorld, long biomeAccess, int maxChainedNeighborUpdates) {
        super(properties, registryRef, registryManager, dimensionEntry, profiler, isClient, debugWorld, biomeAccess, maxChainedNeighborUpdates);
    }

    @Inject(
            method = {"sendBlockUpdated"},
            at = {@At(value = "INVOKE", target = "Ljava/util/Set;iterator()Ljava/util/Iterator;")}
    )
    private void async$updateActiveListeners(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci, @Local List<PathNavigation> list) {
        for (PathNavigation nav : this.async$activeNavigationsOver) {
            if (nav.shouldRecomputePath(pos)) {
                list.add(nav);
            }
        }
    }

    @Override
    public void setNavigationActive(Mob mobEntity) {
        async$activeNavigationsOver.add(((NavigatingEntity) mobEntity).getRegisteredNavigation());
    }

    @Override
    public void setNavigationInactive(Mob mobEntity) {
        async$activeNavigationsOver.remove(((NavigatingEntity) mobEntity).getRegisteredNavigation());
    }
}
