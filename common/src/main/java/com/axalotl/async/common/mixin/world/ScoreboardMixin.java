/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.scores.Objective
 *  net.minecraft.world.scores.Score
 *  net.minecraft.world.scores.Scoreboard
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 */
package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import java.util.Map;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value={Scoreboard.class})
public class ScoreboardMixin {
    @Shadow
    private final Map<String, Map<Objective, Score>> playerScores = ConcurrentCollections.newHashMap();
}

