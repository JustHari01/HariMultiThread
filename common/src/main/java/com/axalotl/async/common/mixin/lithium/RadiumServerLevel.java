/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.sugar.Local
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Holder
 *  net.minecraft.core.RegistryAccess
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.util.profiling.ProfilerFiller
 *  net.minecraft.world.entity.Mob
 *  net.minecraft.world.entity.ai.navigation.PathNavigation
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.WorldGenLevel
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.dimension.DimensionType
 *  net.minecraft.world.level.storage.WritableLevelData
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.axalotl.async.common.mixin.lithium;

import com.llamalad7.mixinextras.sugar.Local;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ServerLevel.class}, priority=1500)
public abstract class RadiumServerLevel
extends Level
implements WorldGenLevel {
    @Unique
    private static final Logger ASYNC_LOGGER = LogManager.getLogger((String)"Async-RadiumCompat");
    @Unique
    private final Set<PathNavigation> async$activeNavigationsOver = Collections.newSetFromMap(new ConcurrentHashMap());
    @Unique
    private static volatile Method async$getRegisteredNavigation;
    @Unique
    private static volatile boolean async$reflectionInitialized;

    protected RadiumServerLevel(WritableLevelData properties, ResourceKey<Level> registryRef, RegistryAccess registryManager, Holder<DimensionType> dimensionEntry, Supplier<ProfilerFiller> profiler, boolean isClient, boolean debugWorld, long biomeAccess, int maxChainedNeighborUpdates) {
        super(properties, registryRef, registryManager, dimensionEntry, profiler, isClient, debugWorld, biomeAccess, maxChainedNeighborUpdates);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Unique
    private static void async$initReflection(Object mobEntity) {
        if (async$reflectionInitialized) {
            return;
        }
        Class<RadiumServerLevel> clazz = RadiumServerLevel.class;
        synchronized (RadiumServerLevel.class) {
            if (async$reflectionInitialized) {
                // ** MonitorExit[var1_1] (shouldn't be in output)
                return;
            }
            try {
                Method method = mobEntity.getClass().getMethod("getRegisteredNavigation", new Class[0]);
                method.setAccessible(true);
                async$getRegisteredNavigation = method;
                ASYNC_LOGGER.debug("[Async] Successfully resolved Radium NavigatingEntity.getRegisteredNavigation()");
            }
            catch (NoSuchMethodException e) {
                ASYNC_LOGGER.warn("[Async] Radium NavigatingEntity.getRegisteredNavigation() not found - Radium/Harium may not be loaded or incompatible");
            }
            catch (Throwable e) {
                ASYNC_LOGGER.warn("[Async] Failed to initialize Radium reflection: " + e.getMessage());
            }
            async$reflectionInitialized = true;
            // ** MonitorExit[var1_1] (shouldn't be in output)
            return;
        }
    }

    @Unique
    private static PathNavigation async$getNavigation(Mob mobEntity) {
        RadiumServerLevel.async$initReflection(mobEntity);
        if (async$getRegisteredNavigation == null) {
            return null;
        }
        try {
            return (PathNavigation)async$getRegisteredNavigation.invoke((Object)mobEntity, new Object[0]);
        }
        catch (Throwable e) {
            return null;
        }
    }

    @Inject(method={"sendBlockUpdated"}, at={@At(value="INVOKE", target="Ljava/util/Set;iterator()Ljava/util/Iterator;")})
    private void updateActiveListeners(BlockPos pos, BlockState oldState, BlockState newState, int arg3, CallbackInfo ci, @Local List<PathNavigation> list) {
        for (PathNavigation nav : this.async$activeNavigationsOver) {
            if (!nav.shouldRecomputePath(pos)) continue;
            list.add(nav);
        }
    }

    public void setNavigationActive(Mob mobEntity) {
        PathNavigation nav = RadiumServerLevel.async$getNavigation(mobEntity);
        if (nav != null) {
            this.async$activeNavigationsOver.add(nav);
        }
    }

    public void setNavigationInactive(Mob mobEntity) {
        PathNavigation nav = RadiumServerLevel.async$getNavigation(mobEntity);
        if (nav != null) {
            this.async$activeNavigationsOver.remove(nav);
        }
    }

    static {
        async$reflectionInitialized = false;
    }
}

