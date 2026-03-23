/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  it.unimi.dsi.fastutil.ints.Int2ObjectMap
 *  net.minecraft.world.level.entity.EntityAccess
 *  net.minecraft.world.level.entity.EntityLookup
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.axalotl.async.common.parallelised.fastutil.Int2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = { EntityLookup.class })
public abstract class EntityLookupMixin<T extends EntityAccess> {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger((String) "Async EntityLookup");
    @Shadow
    @Final
    @Mutable
    private Map<UUID, T> byUuid;
    @Shadow
    @Final
    @Mutable
    private Int2ObjectMap<T> byId;

    @Inject(method = { "<init>" }, at = { @At(value = "TAIL") })
    private void replaceConVars(CallbackInfo ci) {
        this.byId = new Int2ObjectConcurrentHashMap<T>();
        this.byUuid = ConcurrentCollections.newHashMap();
    }

    @Inject(method = { "add" }, at = { @At(value = "HEAD") }, cancellable = true)
    private void threadSafeAdd(T entity, CallbackInfo ci) {
        ci.cancel();
        UUID uuid = entity.getUUID();
        int id = entity.getId();
        this.byUuid.compute(uuid, (k, existing) -> {
            if (existing == null) {
                this.byId.put(id, entity);
                return entity;
            }
            if (existing.getId() == id) {
                this.byId.put(id, entity);
                return entity;
            }
            LOGGER.warn("Duplicate entity UUID {}: existing={}, new={}", new Object[] { uuid, existing, entity });
            return existing;
        });
    }

    @Inject(method = { "remove" }, at = { @At(value = "HEAD") }, cancellable = true)
    private void threadSafeRemove(T entity, CallbackInfo ci) {
        ci.cancel();
        UUID uuid = entity.getUUID();
        int id = entity.getId();
        this.byUuid.computeIfPresent(uuid, (k, existing) -> {
            if (existing.getId() == id) {
                this.byId.remove(id);
                return null;
            }
            return existing;
        });
    }

    @WrapMethod(method = { "getEntity(Ljava/util/UUID;)Lnet/minecraft/world/level/entity/EntityAccess;" })
    private T getEntity(UUID uuid, Operation<T> original) {
        return (T) (uuid == null ? null : (EntityAccess) original.call(new Object[] { uuid }));
    }

    @WrapMethod(method = { "getEntity(I)Lnet/minecraft/world/level/entity/EntityAccess;" })
    private T getEntity1(int id, Operation<T> original) {
        return (T) (id == 0 ? null : (EntityAccess) original.call(new Object[] { id }));
    }
}
