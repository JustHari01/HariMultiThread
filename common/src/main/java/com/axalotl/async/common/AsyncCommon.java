/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package com.axalotl.async.common;

import com.axalotl.async.common.platform.PlatformUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AsyncCommon {
    public static final String MODID = "harimt";
    private static final Logger LOGGER = LogManager.getLogger((String)"HariMultiThread");
    public static boolean LITHIUM = PlatformUtils.isModLoaded("lithium") || PlatformUtils.isModLoaded("harium");
    public static boolean HARIPLAYER = PlatformUtils.isModLoaded("hariplayer") || PlatformUtils.isModLoaded("vmp");
    public static boolean HARICHUNK = PlatformUtils.isModLoaded("harichunk") || PlatformUtils.isModLoaded("c2me");

    public final void initialize() {
        PlatformUtils.initialize();
        AsyncCommon.logCompatibilityStatus();
    }

    private static void logCompatibilityStatus() {
        LOGGER.info("=== HariMultiThread Mod Compatibility ===");
        if (LITHIUM) {
            LOGGER.info("Detected: Harium/Lithium - Adjusted entity AI optimizations");
        }
        if (HARIPLAYER) {
            LOGGER.info("Detected: HariPlayer/VMP - Async chunk operations coordinated");
        }
        if (HARICHUNK) {
            LOGGER.info("Detected: HariChunk/C2ME - Threading synchronized");
        }
        if (!(LITHIUM || HARIPLAYER || HARICHUNK)) {
            LOGGER.info("No conflicting optimization mods detected - Full async mode enabled");
        }
        LOGGER.info("=========================================");
    }
}

