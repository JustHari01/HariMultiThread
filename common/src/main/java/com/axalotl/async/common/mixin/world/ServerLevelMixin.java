package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.storage.WritableLevelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = { ServerLevel.class }, priority = 1500)
public abstract class ServerLevelMixin
        extends Level
        implements WorldGenLevel {
    @Shadow
    @Final
    EntityTickList entityTickList;
    @Unique
    ConcurrentLinkedQueue<BlockEventData> async$syncedBlockEventQueue;
    @Shadow
    @Final
    @Mutable
    Set<Mob> navigatingMobs;
    @Shadow
    @Final
    private ServerChunkCache chunkSource;
    @Shadow
    @Mutable
    @Final
    List<ServerPlayer> players;
    @Unique
    private static final Object lock = new Object();
    @Unique
    private final Object async$explosionLock = new Object();
    @Unique
    private final Object async$entityAddLock = new Object();

    protected ServerLevelMixin(WritableLevelData properties, ResourceKey<Level> registryRef,
            RegistryAccess registryManager, Holder<DimensionType> dimensionEntry, Supplier<ProfilerFiller> profiler,
            boolean isClient, boolean debugWorld, long biomeAccess, int maxChainedNeighborUpdates) {
        super(properties, registryRef, registryManager, dimensionEntry, profiler, isClient, debugWorld, biomeAccess,
                maxChainedNeighborUpdates);
    }

    @Shadow
    @NotNull
    public abstract ServerLevel getLevel();

    @Inject(method = { "<init>" }, at = { @At(value = "RETURN") })
    private void init(CallbackInfo ci) {
        this.navigatingMobs = ConcurrentCollections.newHashSet();
        this.async$syncedBlockEventQueue = new ConcurrentLinkedQueue<>();
        this.players = new CopyOnWriteArrayList<>();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"))
    private void overwriteEntityTicking(EntityTickList entityList, Consumer<Entity> action) {
        ProfilerFiller profiler = this.getProfiler();
        List<Entity> toTick = new ArrayList<>();
        List<Entity> toDespawnCheck = new ArrayList<>();

        this.entityTickList.forEach(entity -> {
            if (entity == null || entity.isRemoved())
                return;

            if (!AsyncConfig.disabled.getValue() && AsyncConfig.enableAsyncSpawn.getValue()) {
                toDespawnCheck.add(entity);
            } else {
                profiler.push("checkDespawn");
                entity.checkDespawn();
                profiler.pop();
            }

            if (!this.chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(entity.chunkPosition().toLong())) {
                return;
            }

            Entity vehicle = entity.getVehicle();
            if (vehicle != null) {
                if (!vehicle.isRemoved() && vehicle.hasPassenger(entity))
                    return;
                entity.stopRiding();
            }

            toTick.add(entity);
        });

        if (!toDespawnCheck.isEmpty()) {
            int poolSize = ParallelProcessor.getPoolSize();
            if (poolSize <= 0)
                poolSize = 1;
            int chunkSize = Math.max(1, toDespawnCheck.size() / poolSize);
            List<Callable<Void>> despawnTasks = new ArrayList<>();
            for (int i = 0; i < toDespawnCheck.size(); i += chunkSize) {
                List<Entity> chunk = toDespawnCheck.subList(i, Math.min(i + chunkSize, toDespawnCheck.size()));
                despawnTasks.add(() -> {
                    for (Entity e : chunk)
                        e.checkDespawn();
                    return null;
                });
            }
            try {
                if (ParallelProcessor.tickPool instanceof ThreadPoolExecutor) {
                    ((ThreadPoolExecutor) ParallelProcessor.tickPool).invokeAll(despawnTasks);
                }
            } catch (InterruptedException ignored) {
            }
        }

        profiler.push("tick");
        ParallelProcessor.callEntityTickBatch(this.getLevel(), toTick);
        ParallelProcessor.postEntityTick();
        profiler.pop();
    }

    @Redirect(method = {
            "blockEvent" }, at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;add(Ljava/lang/Object;)Z", remap = false))
    private boolean overwriteQueueAdd(ObjectLinkedOpenHashSet<BlockEventData> objectLinkedOpenHashSet, Object object) {
        return this.async$syncedBlockEventQueue.add((BlockEventData) object);
    }

    @Redirect(method = {
            "clearBlockEvents" }, at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;removeIf(Ljava/util/function/Predicate;)Z", remap = false))
    private boolean overwriteQueueRemoveIf(ObjectLinkedOpenHashSet<BlockEventData> objectLinkedOpenHashSet,
            Predicate<BlockEventData> filter) {
        return this.async$syncedBlockEventQueue.removeIf(filter);
    }

    @Redirect(method = {
            "runBlockEvents" }, at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;isEmpty()Z", remap = false))
    private boolean overwriteEmptyCheck(ObjectLinkedOpenHashSet<BlockEventData> objectLinkedOpenHashSet) {
        return this.async$syncedBlockEventQueue.isEmpty();
    }

    @Redirect(method = {
            "runBlockEvents" }, at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;removeFirst()Ljava/lang/Object;", remap = false))
    private Object overwriteQueueRemoveFirst(ObjectLinkedOpenHashSet<BlockEventData> objectLinkedOpenHashSet) {
        return this.async$syncedBlockEventQueue.poll();
    }

    @Redirect(method = {
            "runBlockEvents" }, at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;addAll(Ljava/util/Collection;)Z", remap = false))
    private boolean overwriteQueueAddAll(ObjectLinkedOpenHashSet<BlockEventData> instance,
            Collection<? extends BlockEventData> c) {
        return this.async$syncedBlockEventQueue.addAll(c);
    }

    @Redirect(method = {
            "sendBlockUpdated" }, at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerLevel;isUpdatingNavigations:Z", opcode = 181))
    private void skipSendBlockUpdatedCheck(ServerLevel instance, boolean value) {
    }

    @WrapMethod(method = { "addFreshEntity" })
    private boolean wrapAddFreshEntity(Entity entity, Operation<Boolean> original) {
        if (AsyncConfig.disabled.getValue() || !AsyncConfig.enableAsyncSpawn.getValue()) {
            return original.call(entity);
        }
        // IMPROVED: Per-dimension lock instead of global lock.
        // Entities spawning in different dimensions no longer block each other.
        synchronized (this.async$entityAddLock) {
            return original.call(entity);
        }
    }

    @WrapMethod(method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;")
    private Explosion wrapExplode(@Nullable Entity entity, @Nullable DamageSource damageSource,
            @Nullable ExplosionDamageCalculator explosionDamageCalculator, double d, double e, double f, float g,
            boolean bl, Level.ExplosionInteraction explosionInteraction, Operation<Explosion> original) {
        synchronized (async$explosionLock) {
            return original.call(entity, damageSource, explosionDamageCalculator, d, e, f, g, bl, explosionInteraction);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void async$clearPortalCache(java.util.function.BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        com.axalotl.async.common.parallelised.utils.PortalCreationCache.clear();
    }
}
