package com.axalotl.async.common.gpu.safety;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Circuit-breaker pattern adapted from HariChunk.
 * Wraps GPU operations with consecutive-failure tracking and automatic fallback.
 * When failures reach the configured threshold the circuit opens, blocking the GPU
 * path. After a cooldown period since the last failure the circuit auto-resets,
 * allowing the GPU path to be retried.
 */
public final class CrashGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger("Async/CrashGuard");

    private final String name;
    private final int failureThreshold;
    private final long cooldownMs;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);

    private final AtomicLong totalSuccesses = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicLong totalCircuitTrips = new AtomicLong(0);

    public CrashGuard(String name, int failureThreshold, long cooldownMs) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.cooldownMs = cooldownMs;
    }

    /**
     * Execute a callable operation with a fallback value.
     * If the circuit is open the fallback is returned immediately without
     * invoking the operation. On success the consecutive-failure counter
     * resets; on failure the counter advances and may trip the circuit.
     */
    public <T> T execute(Callable<T> operation, T fallbackValue) {
        if (isCircuitOpen()) {
            return fallbackValue;
        }

        try {
            T result = operation.call();
            onSuccess();
            return result;
        } catch (Throwable t) {
            onFailure(t);
            return fallbackValue;
        }
    }

    /**
     * Execute a runnable operation with a runnable fallback.
     * If the circuit is open only the fallback is invoked. On success the
     * consecutive-failure counter resets; on failure the counter advances
     * and may trip the circuit.
     */
    public void execute(Runnable operation, Runnable fallback) {
        if (isCircuitOpen()) {
            if (fallback != null) {
                fallback.run();
            }
            return;
        }

        try {
            operation.run();
            onSuccess();
        } catch (Throwable t) {
            onFailure(t);
            if (fallback != null) {
                fallback.run();
            }
        }
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
        totalSuccesses.incrementAndGet();
    }

    private void onFailure(Throwable cause) {
        int failures = consecutiveFailures.incrementAndGet();
        totalFailures.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());

        LOGGER.warn("[{}] Operation failed ({}/{}): {}",
                name, failures, failureThreshold, cause.getMessage());

        if (failures >= failureThreshold && circuitOpen.compareAndSet(false, true)) {
            totalCircuitTrips.incrementAndGet();
            LOGGER.error("[{}] Circuit breaker TRIPPED after {} consecutive failures. " +
                    "GPU path disabled for {}ms. Fallback active.",
                    name, failures, cooldownMs);
        }
    }

    private boolean isCircuitOpen() {
        if (!circuitOpen.get()) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - lastFailureTime.get();
        if (elapsed >= cooldownMs) {
            if (circuitOpen.compareAndSet(true, false)) {
                consecutiveFailures.set(0);
                LOGGER.info("[{}] Circuit breaker RESET after {}ms cooldown. GPU path re-enabled.",
                        name, elapsed);
                return false;
            }
        }

        return true;
    }

    /**
     * Returns true when the circuit is closed (GPU path is allowed).
     */
    public boolean isHealthy() {
        return !circuitOpen.get();
    }

    /**
     * Record a failure from an external source without executing an operation.
     * Advances the consecutive-failure counter and may trip the circuit.
     */
    public void recordFailure(Throwable cause) {
        onFailure(cause);
    }

    /**
     * Snapshot of current guard statistics.
     */
    public CrashGuardStats getStats() {
        return new CrashGuardStats(
                name,
                totalSuccesses.get(),
                totalFailures.get(),
                totalCircuitTrips.get(),
                circuitOpen.get(),
                consecutiveFailures.get());
    }

    public record CrashGuardStats(
            String name,
            long totalSuccesses,
            long totalFailures,
            long totalCircuitTrips,
            boolean circuitOpen,
            int consecutiveFailures) {

        @Override
        public String toString() {
            return String.format("[%s] ok=%d fail=%d trips=%d open=%s streak=%d",
                    name, totalSuccesses, totalFailures, totalCircuitTrips, circuitOpen, consecutiveFailures);
        }
    }
}
