/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.animal.Animal
 *  net.minecraft.world.entity.animal.frog.Frog
 *  net.minecraft.world.level.Level
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package com.axalotl.async.common.mixin.entity.breed;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={Frog.class})
public abstract class FrogMixin
extends Animal {
    @Unique
    private final AtomicBoolean async$breedingFlag = new AtomicBoolean(false);

    protected FrogMixin(EntityType<? extends Animal> entityType, Level world) {
        super(entityType, world);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"spawnChildFromBreeding"})
    private void breed(ServerLevel world, Animal other, Operation<Void> original) {
        if (this.getId() > other.getId()) {
            return;
        }
        FrogMixin otherMixin = (FrogMixin)other;
        if (this.async$breedingFlag.compareAndSet(false, true) && otherMixin.async$breedingFlag.compareAndSet(false, true)) {
            try {
                original.call(new Object[]{world, other});
            }
            finally {
                this.async$breedingFlag.set(false);
                otherMixin.async$breedingFlag.set(false);
            }
        }
    }
}

