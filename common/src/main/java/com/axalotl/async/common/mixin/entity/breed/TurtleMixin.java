/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.ai.goal.BreedGoal
 *  net.minecraft.world.entity.animal.Animal
 *  net.minecraft.world.entity.animal.Turtle
 *  net.minecraft.world.entity.animal.Turtle$TurtleBreedGoal
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.axalotl.async.common.mixin.entity.breed;

import com.axalotl.async.common.mixin.accessor.TurtleAccessor;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Turtle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={Turtle.TurtleBreedGoal.class})
public abstract class TurtleMixin
extends BreedGoal {
    @Shadow
    @Final
    private Turtle turtle;

    public TurtleMixin(Animal animal, double speed) {
        super(animal, speed);
    }

    @Redirect(method={"breed()V"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/animal/Turtle;setHasEgg(Z)V"))
    private void redirectSetHasEgg(Turtle instance, boolean value) {
        if (this.partner != null && !this.turtle.hasEgg() && !((Turtle)this.partner).hasEgg()) {
            if (this.turtle.getRandom().nextBoolean()) {
                ((TurtleAccessor)this.turtle).invokeSetHasEgg(true);
            } else {
                ((TurtleAccessor)this.partner).invokeSetHasEgg(true);
            }
        }
    }
}

