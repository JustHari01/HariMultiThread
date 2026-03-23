/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  it.unimi.dsi.fastutil.ints.Int2ObjectMap
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.level.entity.EntityTickList
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 */
package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.fastutil.Int2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={EntityTickList.class})
public class EntityTickListMixin {
    @Shadow
    private Int2ObjectMap<Entity> passive = new Int2ObjectConcurrentHashMap<Entity>();
    @Shadow
    private Int2ObjectMap<Entity> active;
    @Shadow
    private Int2ObjectMap<Entity> iterated;
    @Unique
    private final Object async$lock = new Object();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"add"})
    private void add(Entity entity, Operation<Void> original) {
        Object object = this.async$lock;
        synchronized (object) {
            original.call(new Object[]{entity});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"remove"})
    private void remove(Entity entity, Operation<Void> original) {
        Object object = this.async$lock;
        synchronized (object) {
            original.call(new Object[]{entity});
        }
    }
}

