/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  it.unimi.dsi.fastutil.longs.Long2LongMap
 *  it.unimi.dsi.fastutil.longs.Long2ObjectMap
 *  net.minecraft.util.profiling.ProfilerFiller
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.ticks.LevelChunkTicks
 *  net.minecraft.world.ticks.LevelTickAccess
 *  net.minecraft.world.ticks.LevelTicks
 *  net.minecraft.world.ticks.ScheduledTick
 *  org.jetbrains.annotations.NotNull
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.parallelised.fastutil.Long2LongConcurrentHashMap;
import com.axalotl.async.common.parallelised.fastutil.Long2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.function.LongPredicate;
import java.util.function.Supplier;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={LevelTicks.class})
public abstract class LevelTicksMixin<T>
implements LevelTickAccess<T> {
    @Shadow
    @Final
    @Mutable
    private Long2ObjectMap<LevelChunkTicks<@NotNull T>> allContainers;
    @Shadow
    @Final
    @Mutable
    private Long2LongMap nextTickForContainer;
    @Unique
    private static final Object async$lock = new Object();

    @Inject(method={"<init>"}, at={@At(value="TAIL")})
    private void replaceConcurrentCollections(LongPredicate par1, Supplier<ProfilerFiller> par2, CallbackInfo ci) {
        this.allContainers = new Long2ObjectConcurrentHashMap<LevelChunkTicks<T>>();
        Long2LongConcurrentHashMap newMap = new Long2LongConcurrentHashMap();
        newMap.defaultReturnValue(Long.MAX_VALUE);
        this.nextTickForContainer = newMap;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"sortContainersToTick"})
    private void wrapSortContainersToTick(long gameTime, Operation<Void> original) {
        Object object = async$lock;
        synchronized (object) {
            original.call(new Object[]{gameTime});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"collectTicks"})
    private void wrapCollectTicks(long p_193222_, int p_193223_, ProfilerFiller p_193224_, Operation<Void> original) {
        Object object = async$lock;
        synchronized (object) {
            original.call(new Object[]{p_193222_, p_193223_, p_193224_});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"schedule"})
    private void wrapSchedule(ScheduledTick<@NotNull T> tick, Operation<Void> original) {
        Object object = async$lock;
        synchronized (object) {
            original.call(new Object[]{tick});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"addContainer"})
    private void wrapAddContainer(ChunkPos pos, LevelChunkTicks<@NotNull T> ticks, Operation<Void> original) {
        Object object = async$lock;
        synchronized (object) {
            original.call(new Object[]{pos, ticks});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"removeContainer"})
    private void wrapRemoveContainer(ChunkPos pos, Operation<Void> original) {
        Object object = async$lock;
        synchronized (object) {
            original.call(new Object[]{pos});
        }
    }
}

