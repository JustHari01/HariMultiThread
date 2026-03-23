/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.util.ThreadingDetector
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Shadow
 */
package com.axalotl.async.common.mixin.utils;

import java.util.concurrent.Semaphore;
import net.minecraft.util.ThreadingDetector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value={ThreadingDetector.class})
public abstract class ThreadingDetectorMixin {
    @Shadow
    @Final
    @Mutable
    private Semaphore lock = new Semaphore(255);
}

