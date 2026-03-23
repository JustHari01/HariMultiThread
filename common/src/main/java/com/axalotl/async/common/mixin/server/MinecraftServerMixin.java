/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.commands.CommandSource
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.TickTask
 *  net.minecraft.util.thread.ReentrantBlockableEventLoop
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import net.minecraft.commands.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={MinecraftServer.class}, priority=0x7FFFFFFF)
public abstract class MinecraftServerMixin
extends ReentrantBlockableEventLoop<TickTask>
implements CommandSource,
AutoCloseable {
    public MinecraftServerMixin(String string) {
        super(string);
    }

    @Redirect(method={"reloadResources"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/MinecraftServer;isSameThread()Z"))
    private boolean onServerExecutionThreadPatch(MinecraftServer minecraftServer) {
        return ParallelProcessor.isServerExecutionThread();
    }

    @Inject(method={"stopServer"}, at={@At(value="HEAD")})
    private void beforeStopServer(CallbackInfo ci) {
        ParallelProcessor.stop();
    }
}

