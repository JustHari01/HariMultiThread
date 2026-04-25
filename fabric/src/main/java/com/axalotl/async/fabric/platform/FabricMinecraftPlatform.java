package com.axalotl.async.fabric.platform;

import com.axalotl.async.common.platform.MinecraftPlatform;
import net.minecraft.commands.CommandSourceStack;

public class FabricMinecraftPlatform implements MinecraftPlatform {

    @Override
    public boolean hasPermission(CommandSourceStack source, String node, int level) {
        return source.hasPermission(level);
    }
}
