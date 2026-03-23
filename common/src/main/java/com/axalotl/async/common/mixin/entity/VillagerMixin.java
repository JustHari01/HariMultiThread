/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.entity.item.ItemEntity
 *  net.minecraft.world.entity.npc.Villager
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={Villager.class})
public class VillagerMixin {
    @Unique
    private static final Object async$lock = new Object();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"pickUpItem"})
    private void pickUpItem(ItemEntity itemEntity, Operation<Void> original) {
        Object object = async$lock;
        synchronized (object) {
            if (!itemEntity.isRemoved()) {
                original.call(new Object[]{itemEntity});
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"spawnGolemIfNeeded"})
    private void spawnGolemIfNeeded(ServerLevel world, long time, int requiredCount, Operation<Void> original) {
        Object object = async$lock;
        synchronized (object) {
            original.call(new Object[]{world, time, requiredCount});
        }
    }
}

