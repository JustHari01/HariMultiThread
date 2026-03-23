/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.ai.goal.BreedGoal
 *  net.minecraft.world.entity.animal.Animal
 *  net.minecraft.world.entity.animal.Fox
 *  net.minecraft.world.entity.animal.Fox$FoxBreedGoal
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.axalotl.async.common.mixin.entity.breed;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Fox.FoxBreedGoal.class})
public abstract class FoxBreedGoalMixin
extends BreedGoal {
    @Unique
    private static final ConcurrentHashMap<String, Boolean> async$breedingPairs = new ConcurrentHashMap();

    public FoxBreedGoalMixin(Fox fox, double speedModifier) {
        super((Animal)fox, speedModifier);
    }

    @Inject(method={"start"}, at={@At(value="HEAD")})
    private void resetBreedingFlag(CallbackInfo ci) {
        async$breedingPairs.remove(this.async$getPairKey());
    }

    @Inject(method={"breed"}, at={@At(value="HEAD")}, cancellable=true)
    private void preventDoubleBreed(CallbackInfo ci) {
        String pairKey = this.async$getPairKey();
        if (async$breedingPairs.putIfAbsent(pairKey, Boolean.TRUE) != null) {
            ci.cancel();
        }
    }

    @Unique
    private String async$getPairKey() {
        UUID id1 = this.animal.getUUID();
        UUID id2 = null;
        if (this.partner != null) {
            id2 = this.partner.getUUID();
        }
        String s1 = id1.toString();
        String s2 = null;
        if (id2 != null) {
            s2 = id2.toString();
        }
        if (s2 != null) {
            return s1.compareTo(s2) <= 0 ? s1 + "|" + s2 : s2 + "|" + s1;
        }
        return s1;
    }
}

