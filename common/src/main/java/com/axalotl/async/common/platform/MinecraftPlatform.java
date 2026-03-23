/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.commands.CommandSourceStack
 */
package com.axalotl.async.common.platform;

import net.minecraft.commands.CommandSourceStack;

public interface MinecraftPlatform {
    public boolean hasPermission(CommandSourceStack var1, String var2, int var3);
}

