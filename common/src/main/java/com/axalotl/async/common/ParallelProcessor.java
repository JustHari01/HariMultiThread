package com.axalotl.async.common;

import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.mixin.accessor.EntityAccessor;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
            thread.setDaemon(false);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.setContextClassLoader(asyncClass.getClassLoader());
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                parallelism,
                parallelism,
                0L, TimeUnit.MILLISECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(),
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
                .anyMatch(thread::equals);
    }

    public static boolean isServerExecutionThread() {
        return isThreadInPool(Thread.currentThread());
    }

    public static int getPoolSize() {
        return ((ThreadPoolExecutor) tickPool).getCorePoolSize();
    }

    @SuppressWarnings("unchecked")
    public static void callEntityTickBatch(ServerLevel world, List<Entity> entities) {
        if (entities.isEmpty())
            return;
        if (AsyncConfig.disabled.getValue() || ParallelProcessor.tickPool.isShutdown()) {
            entities.forEach(e -> tickSynchronously(world, e));
            return;
        }

        int poolSize = getPoolSize();
        int chunkSize = (entities.size() + poolSize - 1) / poolSize;

        List<java.util.concurrent.Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < entities.size(); i += chunkSize) {
            List<Entity> chunk = entities.subList(i, Math.min(i + chunkSize, entities.size()));
            java.util.concurrent.Future<Void> future = (java.util.concurrent.Future<Void>) tickPool.submit(() -> {
                for (Entity entity : chunk) {
                    if (shouldTickSynchronously(entity))
                        continue;
                    performAsyncEntityTick(world, entity);
                }
            });
            futures.add(future);
        }

        for (Entity e : entities) {
            if (shouldTickSynchronously(e)) {
                tickSynchronously(world, e);
            }
        }

        boolean allDone;
        do {
            allDone = futures.stream().allMatch(java.util.concurrent.Future::isDone);
            if (!allDone) {
                boolean pumped = false;
                for (ServerLevel lvl : server.getAllLevels()) {
                    pumped |= lvl.getChunkSource().pollTask();
                }
                if (!pumped) {
                    Thread.onSpinWait();
                }
            }
        } while (!allDone);

        for (java.util.concurrent.Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                LOGGER.error("Error in async entity tick", e);
            }
        }
    }

    public static boolean shouldTickSynchronously(Entity entity) {
        if (isShuttingDown) {
            return true;
        }
        if (entity.level().isClientSide()) {
            return true;
        }
        UUID entityId = entity.getUUID();
        boolean requiresSyncTick = AsyncConfig.disabled.getValue() ||
                entity instanceof Projectile ||
                entity instanceof AbstractMinecart ||
                entity instanceof ServerPlayer ||
                BLOCKED_ENTITIES.contains(entity.getClass()) ||
                blacklistedEntity.contains(entityId) ||
                AsyncConfig.isEntitySynchronized(EntityType.getKey(entity.getType()));
        if (requiresSyncTick) {
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

    private static boolean isPortalTickRequired(Entity entity) {
        if (entity instanceof EntityAccessor accessor) {
            return accessor.isInsidePortal();
        }
        return false;
    }

    private static void tickSynchronously(ServerLevel world, Entity entity) {
        try {
            world.tickNonPassenger(entity);
        } catch (Exception e) {
            logEntityError("Error during synchronous tick", entity, e);
        }
    }

    private static void performAsyncEntityTick(ServerLevel world, Entity entity) {
        currentEntities.incrementAndGet();
        try {
            world.tickNonPassenger(entity);
        } finally {
            currentEntities.decrementAndGet();
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
        if (!(spawnAnimals || spawnMonsters || rareSpawn)) {
            return;
        }
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
                tickPool.awaitTermination(10L, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }
        AsyncConfig.clearCaches();
        blacklistedEntity.clear();
        portalTickSyncMap.clear();
    }

    private static void logEntityError(String message, Entity entity, Throwable e) {
        LOGGER.error("{} Entity Type: {}, UUID: {}", message, entity.getType().toString(), entity.getUUID(), e);
    }

    public static MinecraftServer getServer() {
        return server;
    }

    public static void setServer(MinecraftServer newServer) {
        server = newServer;
    }
}
