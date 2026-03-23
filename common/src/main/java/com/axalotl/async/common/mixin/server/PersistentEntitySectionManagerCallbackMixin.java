/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.world.entity.Entity$RemovalReason
 *  net.minecraft.world.level.entity.EntitySection
 *  net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 */
package com.axalotl.async.common.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={PersistentEntitySectionManager.Callback.class})
public abstract class PersistentEntitySectionManagerCallbackMixin
implements AutoCloseable {
    @Shadow
    private EntitySection<?> currentSection;
    @Unique
    private final ReentrantLock async$lock = new ReentrantLock();
    @Unique
    private volatile boolean async$removed = false;

    @WrapMethod(method={"onMove"})
    private void onMove(Operation<Void> original) {
        if (this.async$removed) {
            return;
        }
        if (!this.async$lock.tryLock()) {
            return;
        }
        try {
            if (!this.async$removed && this.currentSection != null) {
                original.call(new Object[0]);
            }
        }
        finally {
            this.async$lock.unlock();
        }
    }

    @WrapMethod(method={"onRemove"})
    private void onRemove(Entity.RemovalReason reason, Operation<Void> original) {
        this.async$lock.lock();
        try {
            if (this.async$removed) {
                return;
            }
            this.async$removed = true;
            if (this.currentSection != null) {
                original.call(new Object[]{reason});
            }
        }
        finally {
            this.async$lock.unlock();
        }
    }
}

