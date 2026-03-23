/*
 * Decompiled with CFR 0.152.
 */
package com.axalotl.async.common.parallelised.utils;

import com.axalotl.async.common.platform.PlatformUtils;
import java.util.ArrayList;
import java.util.List;

public class ModCompatibility {
    public static List<String> addUnsupportedMods() {
        ArrayList<String> mods = new ArrayList<String>();
        if (PlatformUtils.isModLoaded("create")) {
            mods.add("create:*");
        }
        if (PlatformUtils.isModLoaded("fowlplay")) {
            mods.add("fowlplay:*");
        }
        return mods;
    }
}

