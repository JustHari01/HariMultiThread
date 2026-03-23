/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Holder
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.tags.BlockTags
 *  net.minecraft.util.Mth
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.effect.MobEffect
 *  net.minecraft.world.effect.MobEffectInstance
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.state.BlockState
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={LivingEntity.class}, priority=1001)
public abstract class LivingEntityMixin
extends Entity {
    @Shadow
    private final Map<Holder<MobEffect>, MobEffectInstance> activeEffects = new ConcurrentHashMap<Holder<MobEffect>, MobEffectInstance>();
    @Unique
    private static final Object async$lock = new Object();

    @Shadow
    protected abstract void onEffectUpdated(MobEffectInstance var1, boolean var2, Entity var3);

    @Shadow
    protected abstract void onEffectRemoved(MobEffectInstance var1);

    public LivingEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @WrapMethod(method={"die"})
    private synchronized void die(DamageSource damageSource, Operation<Void> original) {
        original.call(new Object[]{damageSource});
    }

    @WrapMethod(method={"dropFromLootTable(Lnet/minecraft/world/damagesource/DamageSource;Z)V"})
    private synchronized void dropFromLootTable(DamageSource damageSource, boolean playerKill, Operation<Void> original) {
        original.call(new Object[]{damageSource, playerKill});
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"knockback"})
    private void knockback(double strength, double x, double z, Operation<Void> original) {
        Object object = async$lock;
        synchronized (object) {
            original.call(new Object[]{strength, x, z});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"tickEffects"})
    private void tickStatusEffects(Operation<Void> original) {
        Object object = async$lock;
        synchronized (object) {
            Level level = this.level();
            if (level instanceof ServerLevel) {
                ServerLevel serverlevel = (ServerLevel)level;
                ArrayList<Holder<MobEffect>> effectsToTick = new ArrayList<Holder<MobEffect>>(this.activeEffects.keySet());
                for (Holder holder : effectsToTick) {
                    MobEffectInstance mobeffectinstance = this.activeEffects.get(holder);
                    if (mobeffectinstance == null) continue;
                    if (!mobeffectinstance.tick((LivingEntity)(Object)this, () -> this.onEffectUpdated(mobeffectinstance, true, null))) {
                        this.activeEffects.remove(holder);
                        this.onEffectRemoved(mobeffectinstance);
                        continue;
                    }
                    if (mobeffectinstance.getDuration() % 600 != 0) continue;
                    this.onEffectUpdated(mobeffectinstance, false, null);
                }
            } else {
                original.call(new Object[0]);
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"})
    private boolean addEffect(MobEffectInstance effect, Entity source, Operation<Boolean> original) {
        Object object = async$lock;
        synchronized (object) {
            return effect != null ? (Boolean)original.call(new Object[]{effect, source}) : false;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"removeEffect"})
    private boolean removeEffect(MobEffect effect, Operation<Boolean> original) {
        Object object = async$lock;
        synchronized (object) {
            return effect != null ? (Boolean)original.call(new Object[]{effect}) : false;
        }
    }

    @WrapMethod(method={"hasEffect"})
    public boolean hasEffect(MobEffect effect, Operation<Boolean> original) {
        return effect != null ? (Boolean)original.call(new Object[]{effect}) : false;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"removeAllEffects"})
    private boolean removeAllEffects(Operation<Boolean> original) {
        Object object = async$lock;
        synchronized (object) {
            return (Boolean)original.call(new Object[0]);
        }
    }

    @Inject(method={"causeFallDamage"}, at={@At(value="HEAD")}, cancellable=true)
    private void causeFallDamage(float fallDistance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        BlockPos pos = new BlockPos(Mth.floor((double)this.getX()), Mth.floor((double)this.getY()), Mth.floor((double)this.getZ()));
        BlockState currentBlock = this.level().getBlockState(pos);
        if (currentBlock.is(BlockTags.CLIMBABLE)) {
            cir.setReturnValue(false);
        }
    }
}

