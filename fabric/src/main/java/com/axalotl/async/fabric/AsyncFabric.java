package com.axalotl.async.fabric;

import com.axalotl.async.common.ExplosionProcessor;
import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.commands.AsyncCommand;
import com.axalotl.async.common.commands.StatsCommand;
import com.axalotl.async.common.config.AsyncConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.axalotl.async.common.platform.PlatformUtils;
import com.axalotl.async.fabric.config.AsyncConfigFabric;

public class AsyncFabric implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger(AsyncFabric.class);
    public static boolean LITHIUM = FabricLoader.getInstance().isModLoaded("lithium");
    public static final boolean VMP = FabricLoader.getInstance().isModLoaded("vmp");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Async...");
        AsyncConfigFabric.init();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            LOGGER.info("Async Setting up thread-pool...");
            StatsCommand.runStatsThread();
            ExplosionProcessor.start();
            ParallelProcessor.setServer(server);
            PlatformUtils.initialize();
            ParallelProcessor.setupThreadPool(AsyncConfig.getParallelism(), this.getClass());
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> AsyncCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Shutting down Async thread pool...");
            ExplosionProcessor.stop();
            ParallelProcessor.stop();
            StatsCommand.shutdown();
        });

        LOGGER.info("Async Initialized successfully!");
    }
}