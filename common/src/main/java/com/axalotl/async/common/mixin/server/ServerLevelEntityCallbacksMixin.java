/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.server.level.ServerLevel$EntityCallbacks
 *  net.minecraft.world.entity.Entity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package com.axalotl.async.common.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={ServerLevel.EntityCallbacks.class})
public class ServerLevelEntityCallbacksMixin {
    @Unique
    private static final Object async$lock = new Object();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"onTickingStart(Lnet/minecraft/world/entity/Entity;)V"})
    private synchronized void onTickingStart(Entity entity, Operation<Void> original) {
        Object object = async$lock;
        synchronized (object) {
            original.call(new Object[]{entity});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"onTickingEnd(Lnet/minecraft/world/entity/Entity;)V"})
    private synchronized void onTickingEnd(Entity entity, Operation<Void> original) {
        Object object = async$lock;
        synchronized (object) {
            original.call(new Object[]{entity});
        }
    }
}

