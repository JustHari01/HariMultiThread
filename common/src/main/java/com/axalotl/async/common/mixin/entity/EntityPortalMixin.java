package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.utils.PortalCreationCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Optional;

@Mixin(Entity.class)
public abstract class EntityPortalMixin {

    @Unique
    private static final Object async$lock = new Object();

    @WrapMethod(method = "getExitPortal")
    private Optional<BlockUtil.FoundRectangle> async_getExitPortal(
            ServerLevel level,
            BlockPos pos,
            boolean isNether,
            WorldBorder worldBorder,
            Operation<Optional<BlockUtil.FoundRectangle>> original) {
        if (AsyncConfig.disabled.getValue()) {
            return original.call(level, pos, isNether, worldBorder);
        }
        ResourceKey<Level> dimension = level.dimension();
        synchronized (async$lock) {
            BlockUtil.FoundRectangle cached = PortalCreationCache.get(dimension);
            if (cached != null) {
                return Optional.of(cached);
            }

            Optional<BlockUtil.FoundRectangle> optional = original.call(level, pos, isNether, worldBorder);
            if (optional.isPresent()) {
                PortalCreationCache.put(dimension, optional.get());
            }
            return optional;
        }
    }
}
