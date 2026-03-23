/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  it.unimi.dsi.fastutil.longs.Long2ObjectMap
 *  it.unimi.dsi.fastutil.longs.LongSortedSet
 *  net.minecraft.world.level.entity.EntityAccess
 *  net.minecraft.world.level.entity.EntitySection
 *  net.minecraft.world.level.entity.EntitySectionStorage
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.axalotl.async.common.mixin.entity.movement;

import com.axalotl.async.common.parallelised.fastutil.ConcurrentLongSortedSet;
import com.axalotl.async.common.parallelised.fastutil.Long2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import java.util.Objects;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={EntitySectionStorage.class})
public abstract class EntitySectionStorageMixin<T extends EntityAccess> {
    @Shadow
    @Final
    @Mutable
    private Long2ObjectMap<EntitySection<T>> sections;
    @Shadow
    @Final
    @Mutable
    private LongSortedSet sectionIds;
    @Unique
    private final Object async$createLock = new Object();

    @Shadow
    public abstract LongStream getExistingSectionPositionsInChunk(long var1);

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"getOrCreateSection"})
    private EntitySection<T> getOrCreateSection(long pos, Operation<EntitySection<T>> original) {
        EntitySection existing = (EntitySection)this.sections.get(pos);
        if (existing != null) {
            return existing;
        }
        Object object = this.async$createLock;
        synchronized (object) {
            existing = (EntitySection)this.sections.get(pos);
            if (existing != null) {
                return existing;
            }
            return (EntitySection)original.call(new Object[]{pos});
        }
    }

    @Inject(method={"<init>"}, at={@At(value="TAIL")})
    private void replaceCollections(CallbackInfo ci) {
        this.sections = new Long2ObjectConcurrentHashMap<EntitySection<T>>();
        this.sectionIds = new ConcurrentLongSortedSet();
    }

    @WrapMethod(method={"getExistingSectionsInChunk"})
    private Stream<EntitySection<T>> getExistingSections(long pos, Operation<Stream<EntitySection<T>>> original) {
        return this.getExistingSectionPositionsInChunk(pos).mapToObj(arg_0 -> this.sections.get(arg_0)).filter(Objects::nonNull).toList().stream();
    }
}

