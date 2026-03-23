/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.longs.Long2ObjectMap
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.LocalMobCapCalculator
 *  net.minecraft.world.level.LocalMobCapCalculator$MobCounts
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.axalotl.async.common.mixin.entity.spawn;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.axalotl.async.common.parallelised.fastutil.Long2ObjectConcurrentHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={LocalMobCapCalculator.class})
public class LocalMobCapCalculatorMixin {
    @Shadow
    private final Map<ServerPlayer, LocalMobCapCalculator.MobCounts> playerMobCounts = ConcurrentCollections.newHashMap();
    @Shadow
    private final Long2ObjectMap<List<ServerPlayer>> playersNearChunk = new Long2ObjectConcurrentHashMap<List<ServerPlayer>>();

    @Inject(method={"getPlayersNear"}, at={@At(value="RETURN")}, cancellable=true)
    private void getPlayersNear(ChunkPos pPos, CallbackInfoReturnable<List<ServerPlayer>> cir) {
        if (cir.getReturnValue() == null) {
            cir.setReturnValue(List.of());
        }
    }
}

