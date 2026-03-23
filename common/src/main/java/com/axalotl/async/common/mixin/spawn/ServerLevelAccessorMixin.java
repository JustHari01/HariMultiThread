/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.level.ServerLevelAccessor
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.axalotl.async.common.mixin.spawn;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ServerLevelAccessor.class})
public interface ServerLevelAccessorMixin {
    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Inject(method={"addFreshEntityWithPassengers"}, at={@At(value="HEAD")}, cancellable=true)
    default public void async$syncAddFreshEntityWithPassengers(Entity entity, CallbackInfo ci) {
        if (AsyncConfig.disabled.getValue().booleanValue() || !AsyncConfig.enableAsyncSpawn.getValue().booleanValue()) {
            return;
        }
        ci.cancel();
        Object object = ParallelProcessor.getEntityAddLock();
        synchronized (object) {
            entity.getSelfAndPassengers().forEach(e -> ((ServerLevelAccessor)this).addFreshEntity(e));
        }
    }
}

