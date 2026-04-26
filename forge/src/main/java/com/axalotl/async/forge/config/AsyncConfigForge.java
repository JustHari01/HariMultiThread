package com.axalotl.async.forge.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.axalotl.async.common.config.AsyncConfig.*;

public class AsyncConfigForge {
        public static final ForgeConfigSpec SPEC;
        private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

        private static final ForgeConfigSpec.ConfigValue<Boolean> disabledLocal;
        private static final ForgeConfigSpec.ConfigValue<Integer> maxThreadsLocal;
        private static final ForgeConfigSpec.ConfigValue<List<? extends String>> synchronizedEntitiesLocal;
        private static final ForgeConfigSpec.ConfigValue<Boolean> enableAsyncSpawnLocal;
        private static final ForgeConfigSpec.ConfigValue<Boolean> enableAsyncRandomTicksLocal;
        private static final ForgeConfigSpec.ConfigValue<Boolean> enableAffinityRoutingLocal;
        private static final ForgeConfigSpec.ConfigValue<Boolean> enableCircuitBreakerLocal;
        private static final ForgeConfigSpec.ConfigValue<Integer> entitiesPerWorkerLocal;
        private static final ForgeConfigSpec.ConfigValue<Integer> staleTaskTimeoutMsLocal;

        static {
                BUILDER.push("Async Config");

                disabledLocal = BUILDER.comment("Disables parallel processing of entities.")
                                .define("disabled", disabled.getValue());

                maxThreadsLocal = BUILDER.comment("Maximum worker threads. -1 = auto.")
                                .defineInRange("paraMax", maxThreads.getValue(), -1, Integer.MAX_VALUE);

                synchronizedEntitiesLocal = BUILDER.comment("""
                                List of entity IDs or namespaces (*):
                                  - 'minecraft:zombie' = specific entity
                                  - 'minecraft:*'      = all entities in namespace""")
                                .defineListAllowEmpty(
                                                "synchronizedEntities",
                                                () -> new ArrayList<>(synchronizedEntities.getValue()),
                                                obj -> obj instanceof String);

                enableAsyncSpawnLocal = BUILDER.comment(
                                "Enables async entity spawning. WARNING: incompatible with Carpet's lagFreeSpawning.")
                                .define("enableAsyncSpawn", enableAsyncSpawn.getValue());

                enableAsyncRandomTicksLocal = BUILDER.comment("Experimental! Enables async random ticks.")
                                .define("enableAsyncRandomTicks", enableAsyncRandomTicks.getValue());

                enableAffinityRoutingLocal = BUILDER.comment("""
                                Enable affinity-based entity routing.
                                Routes entities in the same chunk to the same worker thread for better CPU cache locality.
                                Workers steal from other lanes when idle. Recommended: true.""")
                                .define("enableAffinityRouting", enableAffinityRouting.getValue());

                enableCircuitBreakerLocal = BUILDER.comment("""
                                Enable circuit breaker for entity tick crash isolation.
                                When an entity type crashes repeatedly during async tick, it is automatically
                                moved to synchronous ticking until it stabilizes. Prevents cascade failures.""")
                                .define("enableCircuitBreaker", enableCircuitBreaker.getValue());

                entitiesPerWorkerLocal = BUILDER.comment("""
                                Target number of entities per worker thread. Lower values = more parallelism.
                                The system dynamically scales workers based on entity count.
                                Recommended: 15-40. Default: 25.""")
                                .defineInRange("entitiesPerWorker", entitiesPerWorker.getValue(), 5, 200);

                staleTaskTimeoutMsLocal = BUILDER.comment("""
                                Timeout in milliseconds before warning about slow entity tick batches.
                                Does NOT cancel ticks (unsafe) - only logs warnings for diagnostics.
                                Default: 200ms.""")
                                .defineInRange("staleTaskTimeoutMs", staleTaskTimeoutMs.getValue(), 50, 5000);

                BUILDER.pop();
                SPEC = BUILDER.build();
                LOGGER.info("Configuration initialized.");
        }

        public static void loadConfig() {
                disabled.setValue(disabledLocal.get());
                maxThreads.setValue(maxThreadsLocal.get());
                enableAsyncSpawn.setValue(enableAsyncSpawnLocal.get());
                enableAsyncRandomTicks.setValue(enableAsyncRandomTicksLocal.get());
                enableAffinityRouting.setValue(enableAffinityRoutingLocal.get());
                enableCircuitBreaker.setValue(enableCircuitBreakerLocal.get());
                entitiesPerWorker.setValue(entitiesPerWorkerLocal.get());
                staleTaskTimeoutMs.setValue(staleTaskTimeoutMsLocal.get());

                List<? extends String> entries = synchronizedEntitiesLocal.get();
                Set<String> entities = new HashSet<>();
                if (!entries.isEmpty()) {
                        entities.addAll(entries);
                }

                synchronizedEntities.setValue(entities.isEmpty()
                                ? getDefaultSynchronizedEntities()
                                : entities);
        }

        public static void saveConfig() {
                disabledLocal.set(disabled.getValue());
                maxThreadsLocal.set(maxThreads.getValue());
                enableAsyncSpawnLocal.set(enableAsyncSpawn.getValue());
                enableAsyncRandomTicksLocal.set(enableAsyncRandomTicks.getValue());
                enableAffinityRoutingLocal.set(enableAffinityRouting.getValue());
                enableCircuitBreakerLocal.set(enableCircuitBreaker.getValue());
                entitiesPerWorkerLocal.set(entitiesPerWorker.getValue());
                staleTaskTimeoutMsLocal.set(staleTaskTimeoutMs.getValue());
                synchronizedEntitiesLocal.set(new ArrayList<>(synchronizedEntities.getValue()));
                SPEC.save();
                onConfigLoaded();
        }
}
