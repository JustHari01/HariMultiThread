package com.axalotl.async.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ExplosionProcessor {
    public static final Logger LOGGER = LogManager.getLogger(ExplosionProcessor.class);
    private static ExecutorService explosionPool;

    public static void start() {
        explosionPool = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "Async-Explosion-Thread");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
    }

    public static void stop() {
        if (explosionPool != null) {
            LOGGER.info("Shutting down Async explosion pool...");
            explosionPool.shutdown();
            try {
                if (!explosionPool.awaitTermination(10L, TimeUnit.SECONDS)) {
                    explosionPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                explosionPool.shutdownNow();
            }
        }
    }
}
