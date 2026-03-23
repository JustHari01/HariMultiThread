/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.shorts.ShortSet
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.SectionPos
 *  net.minecraft.server.level.ChunkHolder
 *  net.minecraft.server.level.ChunkHolder$LevelChangeListener
 *  net.minecraft.server.level.ChunkHolder$PlayerProvider
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.LevelHeightAccessor
 *  net.minecraft.world.level.chunk.LevelChunk
 *  net.minecraft.world.level.lighting.LevelLightEngine
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.parallelised.fastutil.ConcurrentShortHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ChunkHolder.class})
public abstract class ChunkHolderMixin {
    @Mutable
    @Shadow
    @Final
    private ShortSet[] changedBlocksPerSection;
    @Shadow
    private boolean hasChangedSections;
    @Shadow
    @Final
    private LevelHeightAccessor levelHeightAccessor;

    @Shadow
    public abstract LevelChunk getTickingChunk();

    @Inject(method={"<init>"}, at={@At(value="TAIL", target="Lnet/minecraft/server/level/ChunkHolder;changedBlocksPerSection:[Lit/unimi/dsi/fastutil/shorts/ShortSet;", opcode=181)})
    private void overwriteShortSet(ChunkPos pos, int level, LevelHeightAccessor world, LevelLightEngine lightingProvider, ChunkHolder.LevelChangeListener levelUpdateListener, ChunkHolder.PlayerProvider playersWatchingChunkProvider, CallbackInfo ci) {
        this.changedBlocksPerSection = new ConcurrentShortHashSet[world.getSectionsCount()];
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     * Converted monitor instructions to comments
     * Lifted jumps to return sites
     */
    @Inject(method={"blockChanged"}, at={@At(value="HEAD")})
    private void replaceBlockChanged(BlockPos pos, CallbackInfo ci) {
        LevelChunk levelchunk = this.getTickingChunk();
        if (levelchunk == null) {
            return;
        }
        boolean flag = this.hasChangedSections;
        int i = this.levelHeightAccessor.getSectionIndex(pos.getY());
        ShortSet shortset = this.changedBlocksPerSection[i];
        if (shortset == null) {
            ShortSet[] shortSetArray = this.changedBlocksPerSection;
            // MONITORENTER : this.changedBlocksPerSection
            shortset = this.changedBlocksPerSection[i];
            if (shortset == null) {
                this.hasChangedSections = true;
                this.changedBlocksPerSection[i] = shortset = new ConcurrentShortHashSet();
            }
            // MONITOREXIT : shortSetArray
        }
        shortset.add(SectionPos.sectionRelativePos((BlockPos)pos));
    }
}

