/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.longs.Long2ObjectMap
 *  it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet
 *  net.minecraft.world.level.chunk.storage.SectionStorage
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 */
package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.parallelised.fastutil.ConcurrentLongLinkedOpenHashSet;
import com.axalotl.async.common.parallelised.fastutil.Long2ObjectConcurrentHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.Optional;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value={SectionStorage.class})
public abstract class SectionStorageMixin<R>
implements AutoCloseable {
    @Shadow
    private final Long2ObjectMap<Optional<R>> storage = new Long2ObjectConcurrentHashMap<Optional<R>>();
    @Shadow
    private final LongLinkedOpenHashSet dirty = new ConcurrentLongLinkedOpenHashSet();
}

