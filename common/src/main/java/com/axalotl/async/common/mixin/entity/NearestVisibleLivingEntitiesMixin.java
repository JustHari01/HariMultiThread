/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities
 *  net.minecraft.world.entity.ai.sensing.Sensor
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.axalotl.async.common.mixin.entity;

import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={NearestVisibleLivingEntities.class})
public class NearestVisibleLivingEntitiesMixin {
    @Mutable
    @Shadow
    @Final
    private Predicate<LivingEntity> lineOfSightTest;

    @Inject(method={"<init>(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/List;)V"}, at={@At(value="RETURN")})
    private void init(LivingEntity owner, List<LivingEntity> entities, CallbackInfo ci) {
        Object2BooleanOpenHashMap object2BooleanOpenHashMap = new Object2BooleanOpenHashMap(entities.size());
        Predicate<LivingEntity> predicate = entity -> Sensor.isEntityTargetable((LivingEntity)owner, (LivingEntity)entity);
        this.lineOfSightTest = entity -> {
            Object2BooleanOpenHashMap object2BooleanOpenHashMap2 = object2BooleanOpenHashMap;
            synchronized (object2BooleanOpenHashMap2) {
                return object2BooleanOpenHashMap.computeIfAbsent(entity, predicate);
            }
        };
    }
}

