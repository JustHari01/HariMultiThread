/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.ai.behavior.PlayTagWithOtherKids
 *  net.minecraft.world.entity.ai.memory.MemoryModuleType
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.axalotl.async.common.mixin.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.PlayTagWithOtherKids;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={PlayTagWithOtherKids.class})
public class PlayTagWithOtherKidsMixin {
    @Inject(method={"whoAreYouChasing"}, at={@At(value="HEAD")}, cancellable=true)
    private static void onWhoAreYouChasing(LivingEntity baby, CallbackInfoReturnable<LivingEntity> cir) {
        cir.setReturnValue(baby.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).orElse(null));
    }
}

