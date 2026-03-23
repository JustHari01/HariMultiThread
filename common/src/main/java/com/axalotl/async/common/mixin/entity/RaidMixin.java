/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.world.entity.raid.Raid
 *  net.minecraft.world.entity.raid.Raider
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={Raid.class})
public class RaidMixin {
    @Unique
    private static final Object async$lock = new Object();
    @Shadow
    private final Map<Integer, Set<Raider>> groupRaiderMap = ConcurrentCollections.newHashMap();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"addWaveMob(ILnet/minecraft/world/entity/raid/Raider;)Z"})
    private boolean addWaveMob(int wave, Raider entity, Operation<Boolean> original) {
        Object object = async$lock;
        synchronized (object) {
            return (Boolean)original.call(new Object[]{wave, entity});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"addWaveMob(ILnet/minecraft/world/entity/raid/Raider;Z)Z"})
    private boolean addWaveMob(int wave, Raider entity, boolean countHealth, Operation<Boolean> original) {
        Object object = async$lock;
        synchronized (object) {
            return (Boolean)original.call(new Object[]{wave, entity, countHealth});
        }
    }

    @Redirect(method={"addWaveMob(ILnet/minecraft/world/entity/raid/Raider;Z)Z"}, at=@At(value="INVOKE", target="Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object redirectComputeIfAbsent(Map<Integer, Set<Raider>> instance, Object k, Function<?, ?> key) {
        return instance.computeIfAbsent((Integer)k, wave -> ConcurrentCollections.newHashSet());
    }
}

