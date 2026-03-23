/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.level.block.entity.BeehiveBlockEntity
 *  net.minecraft.world.level.block.entity.BeehiveBlockEntity$BeeData
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 */
package com.axalotl.async.common.mixin.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value={BeehiveBlockEntity.class})
public class BeehiveBlockEntityMixin {
    @Shadow
    private final List<BeehiveBlockEntity.BeeData> stored = Collections.synchronizedList(new ArrayList());
}

