/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.ai.Brain
 *  net.minecraft.world.entity.ai.memory.ExpirableValue
 *  net.minecraft.world.entity.ai.memory.MemoryModuleType
 *  net.minecraft.world.entity.ai.memory.MemoryStatus
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = { Brain.class })
public class BrainMixin<E extends LivingEntity> {
    @Shadow
    @Final
    private Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories;
    @Unique
    private volatile Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> async$cachedSnapshot;
    @Unique
    private volatile boolean async$needsRebuild = true;
    @Unique
    private volatile boolean async$inTick = false;
    @Unique
    private final Object async$writeLock = new Object();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Inject(method = { "tick" }, at = { @At(value = "HEAD") })
    private void async$takeSnapshot(ServerLevel level, E entity, CallbackInfo ci) {
        if (AsyncConfig.disabled.getValue().booleanValue()) {
            return;
        }
        if (this.async$needsRebuild || this.async$cachedSnapshot == null) {
            Object object = this.async$writeLock;
            synchronized (object) {
                if (this.async$needsRebuild || this.async$cachedSnapshot == null) {
                    this.async$cachedSnapshot = new ConcurrentHashMap(this.memories);
                    this.async$needsRebuild = false;
                }
            }
        }
        this.async$inTick = true;
    }

    @Inject(method = { "tick" }, at = { @At(value = "RETURN") })
    private void async$clearSnapshot(ServerLevel level, E entity, CallbackInfo ci) {
        this.async$inTick = false;
    }

    @Inject(method = { "getMemory" }, at = { @At(value = "HEAD") }, cancellable = true)
    private <U> void async$getMemoryFromSnapshot(MemoryModuleType<U> type, CallbackInfoReturnable<Optional<U>> cir) {
        if (AsyncConfig.disabled.getValue().booleanValue()) {
            return;
        }
        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = this.async$cachedSnapshot;
        if (this.async$inTick && snapshot != null) {
            Optional<? extends ExpirableValue<?>> value = snapshot.get(type);
            if (value == null) {
                cir.setReturnValue(Optional.empty());
                return;
            }
            Optional result = value.map(ExpirableValue::getValue);
            cir.setReturnValue(result);
        }
    }

    @Inject(method = { "hasMemoryValue" }, at = { @At(value = "HEAD") }, cancellable = true)
    private void async$hasMemoryValueFromSnapshot(MemoryModuleType<?> type, CallbackInfoReturnable<Boolean> cir) {
        if (AsyncConfig.disabled.getValue().booleanValue()) {
            return;
        }
        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = this.async$cachedSnapshot;
        if (this.async$inTick && snapshot != null) {
            Optional<? extends ExpirableValue<?>> value = snapshot.get(type);
            if (value == null) {
                cir.setReturnValue(false);
                return;
            }
            cir.setReturnValue(value.isPresent());
        }
    }

    @Inject(method = { "checkMemory" }, at = { @At(value = "HEAD") }, cancellable = true)
    private void async$checkMemoryFromSnapshot(MemoryModuleType<?> type, MemoryStatus status,
            CallbackInfoReturnable<Boolean> cir) {
        if (AsyncConfig.disabled.getValue().booleanValue()) {
            return;
        }
        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = this.async$cachedSnapshot;
        if (this.async$inTick && snapshot != null) {
            Optional<? extends ExpirableValue<?>> value = snapshot.get(type);
            if (value == null) {
                cir.setReturnValue(false);
                return;
            }
            boolean result = switch (status) {
                default -> throw new IncompatibleClassChangeError();
                case REGISTERED -> true;
                case VALUE_PRESENT -> value.isPresent();
                case VALUE_ABSENT -> value.isEmpty();
            };
            cir.setReturnValue(result);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method = { "setMemoryInternal" })
    private <U> void async$setMemoryInternal(MemoryModuleType<U> memoryType,
            Optional<? extends ExpirableValue<?>> memory, Operation<Void> original) {
        if (AsyncConfig.disabled.getValue().booleanValue()) {
            original.call(new Object[] { memoryType, memory });
            return;
        }
        Object object = this.async$writeLock;
        synchronized (object) {
            original.call(new Object[] { memoryType, memory });
            Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> snapshot = this.async$cachedSnapshot;
            if (snapshot != null && snapshot.containsKey(memoryType)) {
                snapshot.put(memoryType, memory);
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method = { "clearMemories" })
    private void async$clearMemories(Operation<Void> original) {
        if (AsyncConfig.disabled.getValue().booleanValue()) {
            original.call(new Object[0]);
            return;
        }
        Object object = this.async$writeLock;
        synchronized (object) {
            original.call(new Object[0]);
            this.async$needsRebuild = true;
        }
    }
}
