/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.Mob
 *  net.minecraft.world.entity.item.ItemEntity
 *  net.minecraft.world.item.ItemStack
 *  org.jetbrains.annotations.Nullable
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={Mob.class})
public class MobMixin {
    @Unique
    private static final Object async$lock = new Object();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"equipItemIfPossible"})
    private ItemStack tryEquip(ItemStack stack, Operation<ItemStack> original) {
        Object object = async$lock;
        synchronized (object) {
            return (ItemStack)original.call(new Object[]{stack});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"pickUpItem"})
    private void pickUpItem(ItemEntity entity, Operation<Void> original) {
        Object object = async$lock;
        synchronized (object) {
            original.call(new Object[]{entity});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"setItemSlotAndDropWhenKilled"})
    private void equipLootStack(EquipmentSlot slot, ItemStack stack, Operation<Void> original) {
        Object object = async$lock;
        synchronized (object) {
            original.call(new Object[]{slot, stack});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"convertTo(Lnet/minecraft/world/entity/EntityType;Z)Lnet/minecraft/world/entity/Mob;"})
    @Nullable
    private <T extends Mob> T convertTo(EntityType<T> entityType, boolean mysteryBool, Operation<T> original) {
        Object object = async$lock;
        synchronized (object) {
            if (((Mob)(Object)this).isRemoved()) {
                return null;
            }
            return (T)((Mob)original.call(new Object[]{entityType, mysteryBool}));
        }
    }
}

