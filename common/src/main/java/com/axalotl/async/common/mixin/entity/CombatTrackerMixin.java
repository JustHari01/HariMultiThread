/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.damagesource.CombatEntry
 *  net.minecraft.world.damagesource.CombatTracker
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Shadow
 */
package com.axalotl.async.common.mixin.entity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.damagesource.CombatEntry;
import net.minecraft.world.damagesource.CombatTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value={CombatTracker.class})
public class CombatTrackerMixin {
    @Shadow
    @Final
    @Mutable
    private List<CombatEntry> entries = new CopyOnWriteArrayList<CombatEntry>();
}

