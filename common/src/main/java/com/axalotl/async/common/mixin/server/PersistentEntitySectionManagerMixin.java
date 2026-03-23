/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.world.level.entity.EntityAccess
 *  net.minecraft.world.level.entity.PersistentEntitySectionManager
 *  net.minecraft.world.level.entity.Visibility
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package com.axalotl.async.common.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={PersistentEntitySectionManager.class})
public abstract class PersistentEntitySectionManagerMixin
implements AutoCloseable {
    @Unique
    private static final Object async$lock = new Object();

    @WrapMethod(method={"getEffectiveStatus"})
    private static <T extends EntityAccess> Visibility getEffectiveStatus(T entity, Visibility visibility, Operation<Visibility> original) {
        Visibility result = (Visibility)original.call(new Object[]{entity, visibility});
        if (result == null) {
            return entity.isAlwaysTicking() ? Visibility.TICKING : Visibility.TRACKED;
        }
        return result;
    }
}

