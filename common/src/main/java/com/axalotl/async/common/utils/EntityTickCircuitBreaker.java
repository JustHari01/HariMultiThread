package com.axalotl.async.common.utils;

import net.minecraft.world.entity.EntityType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-entity-type circuit breaker for async tick crash isolation.
 * When an entity type crashes repeatedly during async tick, the circuit opens
 * and forces that entity type back to synchronous ticking for safety.
 *
 * States: CLOSED (normal) -> OPEN (blocked) -> HALF_OPEN (testing) -> CLOSED
 */
public class EntityTickCircuitBreaker {

    private static final Logger LOGGER = LogManager.getLogger(EntityTickCircuitBreaker.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private static final int FAILURE_THRESHOLD = 3;
    private static final long RECOVERY_TIMEOUT_MS = 30_000L;
    private static final int HALF_OPEN_SUCCESS_THRESHOLD = 5;

    private final ConcurrentHashMap<EntityType<?>, CircuitState> states = new ConcurrentHashMap<>();

    private static final class CircuitState {
        final AtomicInteger failureCount = new AtomicInteger(0);
        final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);
        final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
        final AtomicLong openedAt = new AtomicLong(0);
    }

    private CircuitState getOrCreate(EntityType<?> type) {
        return states.computeIfAbsent(type, k -> new CircuitState());
    }

    /**
     * Returns true if the entity type is allowed to tick asynchronously.
     * Returns false if the circuit is OPEN (should fall back to sync).
     */
    public boolean shouldTickAsync(EntityType<?> type) {
        CircuitState cs = getOrCreate(type);
        State current = cs.state.get();

        if (current == State.CLOSED) {
            return true;
        }

        if (current == State.OPEN) {
            long elapsed = System.currentTimeMillis() - cs.openedAt.get();
            if (elapsed >= RECOVERY_TIMEOUT_MS) {
                if (cs.state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    cs.halfOpenSuccessCount.set(0);
                    LOGGER.info("Circuit breaker for {} entering HALF_OPEN (testing async tick)",
                            EntityType.getKey(type));
                    return true;
                }
                return false;
            }
            return false;
        }

        if (current == State.HALF_OPEN) {
            return true;
        }

        return true;
    }

    public void recordSuccess(EntityType<?> type) {
        CircuitState cs = getOrCreate(type);
        if (cs.state.get() == State.HALF_OPEN) {
            int successes = cs.halfOpenSuccessCount.incrementAndGet();
            if (successes >= HALF_OPEN_SUCCESS_THRESHOLD) {
                if (cs.state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    cs.failureCount.set(0);
                    LOGGER.info("Circuit breaker for {} CLOSED (async tick recovered)",
                            EntityType.getKey(type));
                }
            }
        }
    }

    public void recordFailure(EntityType<?> type, Throwable cause) {
        CircuitState cs = getOrCreate(type);
        State current = cs.state.get();

        if (current == State.HALF_OPEN) {
            if (cs.state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                cs.openedAt.set(System.currentTimeMillis());
                LOGGER.warn("Circuit breaker for {} back to OPEN (failed during HALF_OPEN): {}",
                        EntityType.getKey(type), cause.getMessage());
            }
        } else if (current == State.CLOSED) {
            int failures = cs.failureCount.incrementAndGet();
            if (failures >= FAILURE_THRESHOLD) {
                if (cs.state.compareAndSet(State.CLOSED, State.OPEN)) {
                    cs.openedAt.set(System.currentTimeMillis());
                    LOGGER.warn("Circuit breaker for {} OPENED after {} failures. " +
                            "This entity type will tick synchronously until recovered. Last error: {}",
                            EntityType.getKey(type), failures, cause.getMessage());
                }
            }
        }
    }

    public State getState(EntityType<?> type) {
        return getOrCreate(type).state.get();
    }

    public int getOpenCircuitCount() {
        int count = 0;
        for (CircuitState cs : states.values()) {
            if (cs.state.get() == State.OPEN) count++;
        }
        return count;
    }

    public int getTrackedTypeCount() {
        return states.size();
    }

    public void reset() {
        states.clear();
    }
}
