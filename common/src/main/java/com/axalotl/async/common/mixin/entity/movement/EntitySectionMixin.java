/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.util.ClassInstanceMultiMap
 *  net.minecraft.world.level.entity.EntityAccess
 *  net.minecraft.world.level.entity.EntitySection
 *  net.minecraft.world.level.entity.Visibility
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Overwrite
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.axalotl.async.common.mixin.entity.movement;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={EntitySection.class})
public class EntitySectionMixin<T extends EntityAccess> {
    @Shadow
    @Final
    private ClassInstanceMultiMap<T> storage;
    @Shadow
    private Visibility chunkStatus;
    @Unique
    private final AtomicReference<Visibility> async$atomicStatus = new AtomicReference<Visibility>(Visibility.HIDDEN);
    @Unique
    private final Object async$storageLock = new Object();

    @Inject(method={"<init>"}, at={@At(value="TAIL")})
    private void async$init(Class<?> clazz, Visibility status, CallbackInfo ci) {
        this.async$atomicStatus.set(status != null ? status : Visibility.HIDDEN);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Overwrite
    public void add(T entity) {
        Object object = this.async$storageLock;
        synchronized (object) {
            this.storage.add(entity);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Overwrite
    public boolean remove(T entity) {
        Object object = this.async$storageLock;
        synchronized (object) {
            return this.storage.remove(entity);
        }
    }

    @Overwrite
    public Visibility getStatus() {
        return this.async$atomicStatus.get();
    }

    @Overwrite
    public Visibility updateChunkStatus(Visibility status) {
        Visibility safeStatus = status != null ? status : Visibility.HIDDEN;
        Visibility old = this.async$atomicStatus.getAndSet(safeStatus);
        this.chunkStatus = safeStatus;
        return old != null ? old : Visibility.HIDDEN;
    }

    @WrapMethod(method={"getEntities()Ljava/util/stream/Stream;"})
    private Stream<T> getEntities(Operation<Stream<T>> original) {
        return this.storage.stream().filter(Objects::nonNull).toList().stream();
    }
}

