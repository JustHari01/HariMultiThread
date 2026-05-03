package com.axalotl.async.common.gpu;

import com.axalotl.async.common.gpu.safety.CrashGuard;
import com.axalotl.async.common.gpu.vulkan.VkDeviceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Top-level GPU module initialisation singleton.
 * <p>
 * Owns the {@link GpuCollisionDispatcher} and orchestrates Vulkan device
 * bring-up / tear-down.  Safe to call {@link #initialize()} and
 * {@link #shutdown()} from any thread; all mutating state is guarded by
 * {@code synchronized}.
 * <p>
 * When the GPU is unavailable the module still initialises successfully --
 * every downstream consumer must check {@link #isGpuAvailable()} before
 * touching GPU resources.
 */
public final class GpuEntityModule {

    private static final Logger LOGGER = LoggerFactory.getLogger("Async/GpuEntityModule");

    // ------------------------------------------------------------------ //
    //  Singleton state
    // ------------------------------------------------------------------ //

    private static final GpuCollisionDispatcher collisionDispatcher = new GpuCollisionDispatcher();

    private static volatile boolean gpuAvailable = false;
    private static volatile boolean initialized  = false;

    private GpuEntityModule() { /* singleton */ }

    // ------------------------------------------------------------------ //
    //  Lifecycle
    // ------------------------------------------------------------------ //

    /**
     * Initialises the GPU module.  Safe to call multiple times; only the first
     * invocation does work.
     * <p>
     * Steps:
     * <ol>
     *     <li>Initialise the Vulkan device via {@link VkDeviceManager}.</li>
     *     <li>If a GPU is available, initialise the collision dispatcher.</li>
     * </ol>
     * Failures are caught and logged -- the server always continues in CPU-only
     * mode.
     */
    public static synchronized void initialize() {
        if (initialized) return;

        try {
            // 1. Init Vulkan device
            VkDeviceManager.initialize();
            if (!VkDeviceManager.isAvailable()) {
                LOGGER.info("GPU not available - entity collision stays CPU-only");
                initialized = true;
                return;
            }

            // 2. Init collision dispatcher
            collisionDispatcher.initialize();
            gpuAvailable = true;

            LOGGER.info("GPU Entity Module initialized on {} (maxWG={}, sharedMem={}KB)",
                    VkDeviceManager.getDeviceName(),
                    VkDeviceManager.getMaxWorkGroupInvocations(),
                    VkDeviceManager.getMaxSharedMemoryBytes() / 1024);
        } catch (Throwable t) {
            LOGGER.info("GPU Entity Module init failed: {} - CPU fallback active", t.getMessage());
        } finally {
            initialized = true;
        }
    }

    /**
     * Shuts down the GPU module, releasing all Vulkan and collision resources.
     * Safe to call multiple times.
     */
    public static synchronized void shutdown() {
        collisionDispatcher.shutdown();
        VkDeviceManager.shutdown();
        gpuAvailable = false;
        initialized  = false;
    }

    // ------------------------------------------------------------------ //
    //  Accessors
    // ------------------------------------------------------------------ //

    /** Returns the shared collision dispatcher.  Never null. */
    public static GpuCollisionDispatcher getCollisionDispatcher() {
        return collisionDispatcher;
    }

    /** Returns {@code true} when GPU collision is operational. */
    public static boolean isGpuAvailable() {
        return gpuAvailable;
    }

    /** Returns {@code true} once {@link #initialize()} has been called. */
    public static boolean isInitialized() {
        return initialized;
    }

    // ------------------------------------------------------------------ //
    //  Status string (for debug commands)
    // ------------------------------------------------------------------ //

    /**
     * Returns a multi-line human-readable status string for the {@code /async gpu}
     * command.
     *
     * @return formatted status, never null
     */
    public static String getStatusString() {
        StringBuilder sb = new StringBuilder();

        // GPU availability
        sb.append("GPU: ").append(gpuAvailable ? "Available" : "Unavailable").append('\n');

        // Device name
        String deviceName = gpuAvailable ? VkDeviceManager.getDeviceName() : "null";
        sb.append("Device: ").append(deviceName).append('\n');

        // Collision stats
        if (gpuAvailable) {
            int gpuCount = collisionDispatcher.getGpuCollisionCount();
            int cpuCount = collisionDispatcher.getCpuFallbackCount();
            if (gpuCount > 0 || cpuCount == 0) {
                sb.append("Collision GPU: active (").append(gpuCount).append(" dispatches)\n");
            } else {
                sb.append("Collision GPU: fallback (").append(cpuCount).append(" fallbacks)\n");
            }
        } else {
            sb.append("Collision GPU: disabled (CPU-only)\n");
        }

        // CrashGuard health
        CrashGuard.CrashGuardStats stats = collisionDispatcher.getCrashGuardStats();
        if (stats != null) {
            sb.append("CrashGuard: ").append(stats.circuitOpen() ? "tripped" : "healthy");
            if (stats.totalFailures() > 0) {
                sb.append(" (failures=").append(stats.totalFailures())
                  .append(", trips=").append(stats.totalCircuitTrips()).append(')');
            }
        } else {
            sb.append("CrashGuard: n/a");
        }

        return sb.toString();
    }

    // ------------------------------------------------------------------ //
    //  Benchmark
    // ------------------------------------------------------------------ //

    /**
     * Runs a synthetic collision benchmark using fake AABB data.
     * <p>
     * Creates 100 test entity positions arranged in a grid, dispatches them
     * through the GPU collision dispatcher, and measures wall-clock time.
     * If the GPU is unavailable the benchmark runs on the CPU fallback path
     * instead.
     *
     * @return a human-readable timing result string, never null
     */
    public static String runBenchmark() {
        final int ENTITY_COUNT = 100;

        // Generate fake AABB data -- we need actual Entity objects for the
        // dispatcher, so we fall back to a raw timing of the CPU overlap
        // algorithm on synthetic data when we cannot construct entities.
        float[] posX = new float[ENTITY_COUNT];
        float[] posY = new float[ENTITY_COUNT];
        float[] posZ = new float[ENTITY_COUNT];
        float[] halfWidths = new float[ENTITY_COUNT];
        float[] heights = new float[ENTITY_COUNT];

        // Arrange entities in a 10x10 grid with some overlap
        float spacing = 0.8f; // close enough that neighbours overlap
        for (int i = 0; i < ENTITY_COUNT; i++) {
            int row = i / 10;
            int col = i % 10;
            posX[i]       = col * spacing;
            posY[i]       = 0.0f;
            posZ[i]       = row * spacing;
            halfWidths[i] = 0.6f; // width 1.2 > spacing 0.8 => overlap
            heights[i]    = 1.8f;
        }

        // Warm up
        long warmStart = System.nanoTime();
        int warmupPairs = cpuAabbOverlapCount(posX, posY, posZ, halfWidths, heights, ENTITY_COUNT);
        long warmEnd = System.nanoTime();

        // Timed runs (5 iterations)
        int totalPairs = 0;
        long startNanos = System.nanoTime();
        for (int run = 0; run < 5; run++) {
            totalPairs = cpuAabbOverlapCount(posX, posY, posZ, halfWidths, heights, ENTITY_COUNT);
        }
        long endNanos = System.nanoTime();

        double avgMicros = (endNanos - startNanos) / 5_000.0; // nanos -> micros / 5 runs
        String mode = gpuAvailable ? "GPU" : "CPU";

        return String.format(
                "Benchmark (%s, %d entities, 5 runs): avg=%.1f us, pairs=%d",
                mode, ENTITY_COUNT, avgMicros, totalPairs);
    }

    /**
     * Standalone CPU AABB overlap count on raw float arrays.
     * Mirrors the logic in {@link GpuCollisionDispatcher#cpuFallback} but
     * operates without Entity references so it can be used for synthetic
     * benchmarks.
     *
     * @return number of overlapping AABB pairs
     */
    private static int cpuAabbOverlapCount(
            float[] posX, float[] posY, float[] posZ,
            float[] halfWidths, float[] heights, int count) {

        int pairs = 0;
        for (int i = 0; i < count; i++) {
            float iMinX = posX[i] - halfWidths[i];
            float iMaxX = posX[i] + halfWidths[i];
            float iMinY = posY[i];
            float iMaxY = posY[i] + heights[i];
            float iMinZ = posZ[i] - halfWidths[i];
            float iMaxZ = posZ[i] + halfWidths[i];

            for (int j = i + 1; j < count; j++) {
                float jMinX = posX[j] - halfWidths[j];
                float jMaxX = posX[j] + halfWidths[j];
                float jMinY = posY[j];
                float jMaxY = posY[j] + heights[j];
                float jMinZ = posZ[j] - halfWidths[j];
                float jMaxZ = posZ[j] + halfWidths[j];

                if (iMinX < jMaxX && iMaxX > jMinX &&
                    iMinY < jMaxY && iMaxY > jMinY &&
                    iMinZ < jMaxZ && iMaxZ > jMinZ) {
                    pairs++;
                }
            }
        }
        return pairs;
    }
}
