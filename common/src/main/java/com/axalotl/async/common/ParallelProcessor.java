package com.axalotl.async.common;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.mixin.accessor.EntityAccessor;
import com.axalotl.async.common.parallelised.utils.AsyncCompatible;
import com.axalotl.async.common.utils.TickStats;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ParallelProcessor {
    public static final Logger LOGGER = LogManager.getLogger(ParallelProcessor.class);
    private static MinecraftServer server;
    public static final AtomicInteger currentEntities = new AtomicInteger();
    private static final AtomicInteger threadPoolID = new AtomicInteger();
    public static ExecutorService tickPool;
    private static final Set<UUID> blacklistedEntity = ConcurrentHashMap.newKeySet();
    private static final Map<String, Set<WeakReference<Thread>>> mcThreadTracker = new ConcurrentHashMap<>();
    private static volatile boolean isShuttingDown = false;
    private static final Object ENTITY_ADD_LOCK = new Object();
    public static final Set<Class<?>> BLOCKED_ENTITIES = Set.of(
            FallingBlockEntity.class,
            Shulker.class,
            Boat.class);
    private static final Map<UUID, Integer> portalTickSyncMap = new ConcurrentHashMap<>();

    public static void setupThreadPool(int parallelism, Class<?> asyncClass) {
        isShuttingDown = false;
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "Async-Tick-Pool-Thread-" + threadPoolID.getAndIncrement());
            registerThread("Async-Tick", thread);
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.setContextClassLoader(asyncClass.getClassLoader());
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                parallelism,
                parallelism,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.allowCoreThreadTimeOut(false);
        executor.prestartAllCoreThreads();
        tickPool = executor;
        LOGGER.info("Initialized Pool with {} threads", parallelism);
    }

    public static void registerThread(String poolName, Thread thread) {
        mcThreadTracker.computeIfAbsent(poolName, key -> ConcurrentHashMap.newKeySet())
                .add(new WeakReference<>(thread));
    }

    private static boolean isThreadInPool(Thread thread) {
        return mcThreadTracker.getOrDefault("Async-Tick", Set.of()).stream()
                .map(WeakReference::get)
                .filter(java.util.Objects::nonNull)
                .anyMatch(thread::equals);
    }

    public static boolean isServerExecutionThread() {
        return isThreadInPool(Thread.currentThread());
    }

    public static int getPoolSize() {
        if (tickPool instanceof ThreadPoolExecutor pool) {
            return pool.getCorePoolSize();
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    public static void callEntityTickBatch(ServerLevel world, List<Entity> entities) {
        if (entities.isEmpty()) return;

        if (AsyncConfig.disabled.getValue() || tickPool.isShutdown()) {
            entities.forEach(e -> tickEntity(world, e, false));
            TickStats.RECORDING_TICKS_LEFT.decrementAndGet();
            return;
        }

        int poolSize = getPoolSize();
        int chunkSize = (entities.size() + poolSize - 1) / poolSize;

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < entities.size(); i += chunkSize) {
            List<Entity> chunk = entities.subList(i, Math.min(i + chunkSize, entities.size()));
            Future<Void> future = (Future<Void>) tickPool.submit(() -> {
                for (Entity entity : chunk) {
                    if (!shouldTickSynchronously(entity)) {
                        tickEntity(world, entity, true);
                    }
                }
            });
            futures.add(future);
        }

        for (Entity e : entities) {
            if (shouldTickSynchronously(e)) {
                tickEntity(world, e, false);
            }
        }

        waitForFutures(futures);
        TickStats.RECORDING_TICKS_LEFT.decrementAndGet();
    }

    private static void waitForFutures(List<Future<Void>> futures) {
        boolean allDone;
        do {
            allDone = futures.stream().allMatch(Future::isDone);
            if (!allDone) {
                boolean pumped = false;
                for (ServerLevel lvl : server.getAllLevels()) {
                    pumped |= lvl.getChunkSource().pollTask();
                }
                if (!pumped) Thread.onSpinWait();
            }
        } while (!allDone);

        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                LOGGER.error("Error during async entity tick", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static boolean shouldTickSynchronously(Entity entity) {
        if (isShuttingDown || entity.level().isClientSide()) return true;

        UUID entityId = entity.getUUID();

        if (AsyncConfig.disabled.getValue()
                || entitySupportsAsyncApi(entity)
                || entity instanceof Projectile
                || entity instanceof AbstractMinecart
                || entity instanceof ServerPlayer
                || BLOCKED_ENTITIES.contains(entity.getClass())
                || blacklistedEntity.contains(entityId)
                || AsyncConfig.isEntitySynchronized(EntityType.getKey(entity.getType()))) {
            return true;
        }

        if (portalTickSyncMap.containsKey(entityId)) {
            int ticksLeft = portalTickSyncMap.get(entityId);
            if (ticksLeft > 0) {
                portalTickSyncMap.put(entityId, ticksLeft - 1);
                return true;
            }
            portalTickSyncMap.remove(entityId);
        }

        if (isPortalTickRequired(entity)) {
            portalTickSyncMap.put(entityId, 39);
            return true;
        }

        return false;
    }

    public static boolean entitySupportsAsyncApi(Entity entity) {
        return !"minecraft".equals(EntityType.getKey(entity.getType()).getNamespace())
                && !entity.getClass().isAnnotationPresent(AsyncCompatible.class);
    }

    private static boolean isPortalTickRequired(Entity entity) {
        if (entity instanceof EntityAccessor accessor) {
            return accessor.isInsidePortal();
        }
        return false;
    }

    private static void tickEntity(ServerLevel world, Entity entity, boolean async) {
        long start = System.nanoTime();
        currentEntities.incrementAndGet();
        try {
            world.tickNonPassenger(entity);
        } catch (Exception e) {
            LOGGER.error("Error during {} tick. Entity: {}, UUID: {}",
                    async ? "async" : "sync", entity.getType(), entity.getUUID(), e);
        } finally {
            currentEntities.decrementAndGet();
            if (TickStats.RECORDING_TICKS_LEFT.get() > 0) {
                EntityType<?> type = entity.getType();
                long elapsed = System.nanoTime() - start;

                if (async) {
                    TickStats.ASYNC_TICK_TIME_NS.computeIfAbsent(type, k -> new LongAdder()).add(elapsed);
                    TickStats.ASYNC_TICK_COUNT.computeIfAbsent(type, k -> new LongAdder()).increment();
                } else {
                    TickStats.TICK_TIME_NS.computeIfAbsent(type, k -> new LongAdder()).add(elapsed);
                    TickStats.TICK_COUNT.computeIfAbsent(type, k -> new LongAdder()).increment();
                }
            }
        }
    }

    public static Object getEntityAddLock() {
        return ENTITY_ADD_LOCK;
    }

    public static void asyncSpawnForChunk(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState,
            boolean spawnAnimals, boolean spawnMonsters, boolean rareSpawn) {
        if (isShuttingDown || AsyncConfig.disabled.getValue() || !AsyncConfig.enableAsyncSpawn.getValue()) {
            NaturalSpawner.spawnForChunk(level, chunk, spawnState, spawnAnimals, spawnMonsters, rareSpawn);
            return;
        }
        if (!(spawnAnimals || spawnMonsters || rareSpawn)) return;
        tickPool.submit(() -> {
            try {
                NaturalSpawner.spawnForChunk(level, chunk, spawnState, spawnAnimals, spawnMonsters, rareSpawn);
            } catch (Exception e) {
                LOGGER.error("Error in async spawn for chunk {}: {}", chunk.getPos(), e.getMessage());
            }
        });
    }

    public static void postEntityTick() {
        for (ServerLevel world : server.getAllLevels()) {
            world.getChunkSource().pollTask();
        }
    }

    public static void stop() {
        isShuttingDown = true;
        if (tickPool != null) {
            LOGGER.info("Waiting for Async tickPool to shutdown...");
            tickPool.shutdown();
            try {
                if (!tickPool.awaitTermination(60L, TimeUnit.SECONDS)) {
                    LOGGER.warn("Async pool did not terminate in 60 seconds");
                    tickPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Interrupted while waiting for thread pool shutdown", e);
                tickPool.shutdownNow();
            }
        }
        AsyncConfig.clearCaches();
        blacklistedEntity.clear();
        portalTickSyncMap.clear();
        TickStats.resetEntityTickStats();
    }

    public static MinecraftServer getServer() {
        return server;
    }

    public static void setServer(MinecraftServer newServer) {
        server = newServer;
    }
}
