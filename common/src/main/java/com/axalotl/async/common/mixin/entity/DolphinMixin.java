/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.animal.Dolphin
 *  net.minecraft.world.entity.animal.WaterAnimal
 *  net.minecraft.world.entity.item.ItemEntity
 *  net.minecraft.world.level.Level
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={Dolphin.class})
public abstract class DolphinMixin
extends WaterAnimal {
    @Unique
    private static final Object async$lock = new Object();

    protected DolphinMixin(EntityType<? extends WaterAnimal> entityType, Level world) {
        super(entityType, world);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"pickUpItem"})
    private void pickUpItem(ItemEntity item, Operation<Void> original) {
        Object object = async$lock;
        synchronized (object) {
            if (!item.isRemoved()) {
                original.call(new Object[]{item});
            }
        }
    }
}

