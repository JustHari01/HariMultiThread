/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.datafixers.util.Either
 *  net.minecraft.server.level.ChunkHolder
 *  net.minecraft.server.level.ChunkHolder$ChunkLoadingFailure
 *  net.minecraft.server.level.ChunkMap
 *  net.minecraft.server.level.ServerChunkCache
 *  net.minecraft.server.level.ServerChunkCache$MainThreadExecutor
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.GameRules
 *  net.minecraft.world.level.NaturalSpawner$SpawnState
 *  net.minecraft.world.level.chunk.ChunkAccess
 *  net.minecraft.world.level.chunk.ChunkSource
 *  net.minecraft.world.level.chunk.ChunkStatus
 *  net.minecraft.world.level.chunk.ImposterProtoChunk
 *  net.minecraft.world.level.chunk.LevelChunk
 *  org.jetbrains.annotations.Nullable
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = { ServerChunkCache.class }, priority = 1500)
public abstract class ServerChunkCacheMixin
        extends ChunkSource {
    @Final
    @Shadow
    public ServerLevel level;
    @Shadow
    @Final
    Thread mainThread;
    @Shadow
    @Final
    public ChunkMap chunkMap;
    @Shadow
    @Final
    private ServerChunkCache.MainThreadExecutor mainThreadProcessor;
    @Unique
    private final List<LevelChunk> async$chunksToTick = new ArrayList<LevelChunk>();

    @Shadow
    @Nullable
    protected abstract ChunkHolder getVisibleChunkIfPresent(long var1);

    @Shadow
    protected abstract CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> getChunkFutureMainThread(
            int var1, int var2, ChunkStatus var3, boolean var4);

    @Inject(method = {
            "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;" }, at = {
                    @At(value = "HEAD") }, cancellable = true)
    private void shortcutGetChunk(int x, int z, ChunkStatus leastStatus, boolean create,
            CallbackInfoReturnable<ChunkAccess> cir) {
        if (Thread.currentThread() == this.mainThread) {
            return;
        }
        ChunkAccess fast = this.async$tryGetChunkFast(x, z, leastStatus);
        if (fast != null) {
            cir.setReturnValue(fast);
            return;
        }
        CompletionStage future = CompletableFuture
                .supplyAsync(() -> this.getChunkFutureMainThread(x, z, leastStatus, create),
                        (Executor) this.mainThreadProcessor)
                .thenCompose(f -> f);
        Either resultEither = (Either) ((CompletableFuture) future).join();
        if (resultEither != null) {
            resultEither.ifLeft(result -> {
                if (result instanceof ImposterProtoChunk) {
                    ImposterProtoChunk readOnlyChunk = (ImposterProtoChunk) result;
                    result = readOnlyChunk.getWrapped();
                }
                cir.setReturnValue((ChunkAccess) result);
            });
        }
    }

    @Unique
    @Nullable
    private ChunkAccess async$tryGetChunkFast(int x, int z, ChunkStatus leastStatus) {
        ChunkAccess result;
        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong((int) x, (int) z));
        if (holder == null) {
            return null;
        }
        ChunkAccess chunk = holder.getLastAvailable();
        if (chunk != null && chunk.getStatus().isOrAfter(leastStatus)) {
            if (chunk instanceof ImposterProtoChunk) {
                ImposterProtoChunk imposter = (ImposterProtoChunk) chunk;
                return imposter.getWrapped();
            }
            return chunk;
        }
        CompletableFuture future = holder.getOrScheduleFuture(leastStatus, this.chunkMap);
        if (future.isDone() && (result = (ChunkAccess) ((Either) future.join()).left().orElse(null)) != null) {
            if (result instanceof ImposterProtoChunk) {
                ImposterProtoChunk imposter = (ImposterProtoChunk) result;
                return imposter.getWrapped();
            }
            return result;
        }
        return null;
    }

    @Inject(method = { "getChunkNow" }, at = { @At(value = "HEAD") }, cancellable = true)
    private void shortcutGetChunkNow(int chunkX, int chunkZ, CallbackInfoReturnable<LevelChunk> cir) {
        ChunkAccess chunk;
        if (Thread.currentThread() == this.mainThread) {
            return;
        }
        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong((int) chunkX, (int) chunkZ));
        if (holder != null && (chunk = holder.getLastAvailable()) != null
                && chunk.getStatus().isOrAfter(ChunkStatus.FULL) && chunk instanceof LevelChunk) {
            LevelChunk levelChunk = (LevelChunk) chunk;
            cir.setReturnValue(levelChunk);
            return;
        }
        cir.setReturnValue(null);
    }

    @Redirect(method = {
            "tickChunks" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V"))
    private void collectChunksToTick(ServerLevel level, LevelChunk chunk, int randomTickSpeed) {
        if (!AsyncConfig.disabled.getValue().booleanValue()
                && AsyncConfig.enableAsyncRandomTicks.getValue().booleanValue()) {
            this.async$chunksToTick.add(chunk);
        } else {
            level.tickChunk(chunk, randomTickSpeed);
        }
    }

    @Inject(method = { "tickChunks" }, at = { @At(value = "TAIL") })
    private void processCollectedChunks(CallbackInfo ci) {
        if (!AsyncConfig.disabled.getValue().booleanValue()
                && AsyncConfig.enableAsyncRandomTicks.getValue().booleanValue() && !this.async$chunksToTick.isEmpty()) {
            int randomTickSpeed = this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            List<LevelChunk> chunksSnapshot = new ArrayList<>(this.async$chunksToTick);
            this.async$chunksToTick.clear();
            CompletableFuture.runAsync(() -> {
                for (LevelChunk chunk : chunksSnapshot) {
                    if (chunk.getLevel() != null
                            && chunk.getLevel().getChunkSource().hasChunk(chunk.getPos().x, chunk.getPos().z)) {
                        this.level.tickChunk(chunk, randomTickSpeed);
                    }
                }
            }, ParallelProcessor.tickPool).exceptionally(e -> {
                ParallelProcessor.LOGGER.error("Error in async random tick, falling back to sync", e);
                for (LevelChunk chunk : chunksSnapshot) {
                    try {
                        if (chunk.getLevel() != null
                                && chunk.getLevel().getChunkSource().hasChunk(chunk.getPos().x, chunk.getPos().z)) {
                            this.level.tickChunk(chunk, randomTickSpeed);
                        }
                    } catch (Exception ex) {
                        ParallelProcessor.LOGGER.error("Error in sync fallback tick for chunk {}", chunk.getPos(), ex);
                    }
                }
                return null;
            });
        }
    }

    @Redirect(method = {
            "tickChunks" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;ZZZ)V"))
    private void onSpawnForChunk(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState,
            boolean spawnAnimals, boolean spawnMonsters, boolean rareSpawn) {
        ParallelProcessor.asyncSpawnForChunk(level, chunk, spawnState, spawnAnimals, spawnMonsters, rareSpawn);
    }
}
