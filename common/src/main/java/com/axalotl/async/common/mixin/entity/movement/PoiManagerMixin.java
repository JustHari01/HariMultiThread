/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Holder
 *  net.minecraft.core.RegistryAccess$RegistryEntry
 *  net.minecraft.util.RandomSource
 *  net.minecraft.world.entity.ai.village.poi.PoiManager
 *  net.minecraft.world.entity.ai.village.poi.PoiManager$Occupancy
 *  net.minecraft.world.entity.ai.village.poi.PoiRecord
 *  net.minecraft.world.entity.ai.village.poi.PoiType
 *  net.minecraft.world.level.ChunkPos
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package com.axalotl.async.common.mixin.entity.movement;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={PoiManager.class})
public class PoiManagerMixin {
    @Unique
    private static final Object async$lock = new Object();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"getInSquare"})
    private Stream<PoiRecord> getInSquare(Predicate<RegistryAccess.RegistryEntry<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus, Operation<Stream<PoiRecord>> original) {
        Object object = async$lock;
        synchronized (object) {
            return (Stream)original.call(new Object[]{typePredicate, pos, radius, occupationStatus});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"getInRange"})
    private Stream<PoiRecord> getInRange(Predicate<RegistryAccess.RegistryEntry<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus, Operation<Stream<PoiRecord>> original) {
        Object object = async$lock;
        synchronized (object) {
            return (Stream)original.call(new Object[]{typePredicate, pos, radius, occupationStatus});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"getInChunk"})
    private Stream<PoiRecord> getInChunk(Predicate<RegistryAccess.RegistryEntry<PoiType>> typePredicate, ChunkPos chunkPos, PoiManager.Occupancy occupationStatus, Operation<Stream<PoiRecord>> original) {
        Object object = async$lock;
        synchronized (object) {
            return (Stream)original.call(new Object[]{typePredicate, chunkPos, occupationStatus});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"getCountInRange"})
    private long getInChunk(Predicate<RegistryAccess.RegistryEntry<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus, Operation<Long> original) {
        Object object = async$lock;
        synchronized (object) {
            return (Long)original.call(new Object[]{typePredicate, pos, radius, occupationStatus});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"findClosest(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/Optional;"})
    private Optional<BlockPos> getNearestPosition(Predicate<RegistryAccess.RegistryEntry<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus, Operation<Optional<BlockPos>> original) {
        Object object = async$lock;
        synchronized (object) {
            return (Optional)original.call(new Object[]{typePredicate, pos, radius, occupationStatus});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"findClosest(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/Optional;"})
    private Optional<BlockPos> getNearestPosition(Predicate<RegistryAccess.RegistryEntry<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus, Operation<Optional<BlockPos>> original) {
        Object object = async$lock;
        synchronized (object) {
            return (Optional)original.call(new Object[]{typePredicate, posPredicate, pos, radius, occupationStatus});
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @WrapMethod(method={"getRandom(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;)Ljava/util/Optional;"})
    private Optional<BlockPos> getNearestPosition(Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> positionPredicate, PoiManager.Occupancy occupationStatus, BlockPos pos, int radius, RandomSource random, Operation<Optional<BlockPos>> original) {
        Object object = async$lock;
        synchronized (object) {
            return (Optional)original.call(new Object[]{typePredicate, positionPredicate, occupationStatus, pos, radius, random});
        }
    }
}

