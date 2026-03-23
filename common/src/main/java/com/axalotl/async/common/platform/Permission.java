/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.commands.CommandSourceStack
 */
package com.axalotl.async.common.platform;

import com.axalotl.async.common.platform.PlatformUtils;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;

public class Permission {
    public static boolean check(CommandSourceStack source, String node, int level) {
        return PlatformUtils.hasPermission(source, node, level);
    }

    public static Predicate<CommandSourceStack> require(String node, int level) {
        return source -> Permission.check(source, node, level);
    }
}

