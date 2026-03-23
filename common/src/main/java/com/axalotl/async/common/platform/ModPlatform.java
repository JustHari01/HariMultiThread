/*
 * Decompiled with CFR 0.152.
 */
package com.axalotl.async.common.platform;

public interface ModPlatform {
    public void saveConfig();

    public void reloadConfig();

    public boolean isModLoaded(String var1);

    public boolean platformUsesRefmap();
}

