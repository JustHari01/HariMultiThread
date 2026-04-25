package com.axalotl.async.common.parallelised.utils;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SensorUtils {

    /**
     * Wraps a sensor with custom tick logic for async-safe processing.
     */
    public static <T extends LivingEntity> Sensor<T> wrapSensor(Sensor<T> originalSensor, TickWrapper<T> wrapperLogic) {
        return new Sensor<T>() {
            @Override
            public Set<MemoryModuleType<?>> requires() {
                return originalSensor.requires();
            }

            @Override
            protected void doTick(ServerLevel level, T entity) {
                wrapperLogic.tick(level, entity);
            }
        };
    }

    /**
     * Utility for sorting entities by distance with caching to avoid repeated distance calculations.
     */
    public static <T extends Entity> Comparator<T> distanceComparator(Entity source) {
        Map<Entity, Double> cache = new HashMap<>();
        return (a, b) -> {
            double d1 = cache.computeIfAbsent(a, e -> e.distanceToSqr(source));
            double d2 = cache.computeIfAbsent(b, e -> e.distanceToSqr(source));
            return Double.compare(d1, d2);
        };
    }

    @FunctionalInterface
    public interface TickWrapper<T extends LivingEntity> {
        void tick(ServerLevel level, T entity);
    }
}
