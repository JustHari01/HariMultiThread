/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.projectile.Projectile
 *  org.spongepowered.asm.mixin.Mixin
 */
package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value={Projectile.class})
public class ProjectileEntityMixin {
    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"shootFromRotation"})
    private void shootFromRotation(Entity shooter, float x, float y, float z, float velocity, float inaccuracy, Operation<Void> original) {
        Object object = ParallelProcessor.getEntityAddLock();
        synchronized (object) {
            original.call(new Object[]{shooter, Float.valueOf(x), Float.valueOf(y), Float.valueOf(z), Float.valueOf(velocity), Float.valueOf(inaccuracy)});
        }
    }
}

