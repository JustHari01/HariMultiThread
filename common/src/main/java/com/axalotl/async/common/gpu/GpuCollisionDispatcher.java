package com.axalotl.async.common.gpu;

import com.axalotl.async.common.gpu.safety.CrashGuard;
import com.axalotl.async.common.gpu.vulkan.VkBufferManager;
import com.axalotl.async.common.gpu.vulkan.VkComputePipeline;
import com.axalotl.async.common.gpu.vulkan.VkDeviceManager;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dispatches entity collision broad-phase detection to the GPU via Vulkan compute shaders.
 * <p>
 * Each tick, the dispatcher receives a list of Minecraft {@link Entity} objects, extracts
 * their AABB data (positions, widths, heights) into packed float arrays, uploads them to
 * GPU SSBOs, runs a collision broad-phase compute shader, downloads the resulting collision
 * pair indices, and maps them back to entity references.
 * <p>
 * The GPU path is wrapped by a {@link CrashGuard} circuit breaker. If the GPU fails
 * repeatedly (default: 3 consecutive failures), the circuit opens and all subsequent
 * calls fall back to a simple CPU O(n^2) AABB overlap check until the cooldown elapses.
 * <p>
 * The {@link #computeBroadPhase(List)} method never throws exceptions to callers -- it
 * always returns a list (which may be empty on error, causing the caller to skip collision
 * resolution for that tick rather than crashing the server).
 */
public final class GpuCollisionDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger("Async/GpuCollision");

    // ------------------------------------------------------------------ //
    //  Constants
    // ------------------------------------------------------------------ //

    /** Initial pre-allocation size for entity data arrays and GPU buffers. */
    private static final int INITIAL_ENTITY_CAPACITY = 512;

    /** Maximum number of collision pairs the GPU may produce in a single dispatch. */
    private static final int MAX_COLLISION_PAIRS = 16384;

    /**
     * Conservative range limit for broad-phase AABB checks (in blocks).
     * Entities farther apart than this will never be reported as colliding.
     */
    private static final float RANGE_LIMIT = 16.0f;

    // ------------------------------------------------------------------ //
    //  Collision pair record
    // ------------------------------------------------------------------ //

    /**
     * Immutable pair of entities that may be colliding, as identified by the
     * broad-phase check.  Callers must perform narrow-phase (exact AABB overlap)
     * before acting on this result.
     */
    public record CollisionPair(Entity a, Entity b) {}

    // ------------------------------------------------------------------ //
    //  State
    // ------------------------------------------------------------------ //

    private VkComputePipeline pipeline;
    private final CrashGuard crashGuard = new CrashGuard("EntityCollision", 3, 30_000);

    // Reusable CPU-side data arrays -- grown as needed, never shrunk
    private float[] posX;
    private float[] posY;
    private float[] posZ;
    private float[] halfWidths;
    private float[] heights;

    // Collision pair output arrays (indices into entityIndexMap)
    private int[] pairsA;
    private int[] pairsB;
    private int[] pairCounter; // single-element: [0]=count of pairs written by GPU

    /** Tracks last known capacity so we only re-allocate on growth. */
    private int lastEntityCapacity = 0;

    /** Maps array index back to the original Entity reference. */
    private Entity[] entityIndexMap;

    // GPU buffer handles
    private VkBufferManager.BufferHandle posXBuffer;
    private VkBufferManager.BufferHandle posYBuffer;
    private VkBufferManager.BufferHandle posZBuffer;
    private VkBufferManager.BufferHandle halfWidthsBuffer;
    private VkBufferManager.BufferHandle heightsBuffer;
    private VkBufferManager.BufferHandle pairsABuffer;
    private VkBufferManager.BufferHandle pairsBBuffer;
    private VkBufferManager.BufferHandle pairCounterBuffer;

    // Telemetry
    private volatile int gpuCollisionCount;
    private volatile int cpuFallbackCount;

    // ------------------------------------------------------------------ //
    //  Lifecycle
    // ------------------------------------------------------------------ //

    public GpuCollisionDispatcher() {
        posX = new float[INITIAL_ENTITY_CAPACITY];
        posY = new float[INITIAL_ENTITY_CAPACITY];
        posZ = new float[INITIAL_ENTITY_CAPACITY];
        halfWidths = new float[INITIAL_ENTITY_CAPACITY];
        heights = new float[INITIAL_ENTITY_CAPACITY];
        pairsA = new int[MAX_COLLISION_PAIRS];
        pairsB = new int[MAX_COLLISION_PAIRS];
        pairCounter = new int[1];
        entityIndexMap = new Entity[INITIAL_ENTITY_CAPACITY];
        lastEntityCapacity = INITIAL_ENTITY_CAPACITY;
    }

    /**
     * Initializes the Vulkan compute pipeline for collision broad-phase detection.
     * <p>
     * Loads the SPIR-V shader from {@code /assets/async/shaders/collision_broadphase.comp.spv}
     * and creates all GPU resources.  If Vulkan is unavailable or the shader cannot be loaded,
     * initialization fails silently -- subsequent calls to {@link #computeBroadPhase} will
     * use the CPU fallback path.
     */
    public void initialize() {
        try {
            if (!VkDeviceManager.isAvailable()) {
                LOGGER.info("Vulkan device not available -- GPU collision disabled, using CPU fallback");
                return;
            }

            byte[] spirvBytes = loadShaderBytes();
            if (spirvBytes == null || spirvBytes.length == 0) {
                LOGGER.warn("Collision broad-phase shader not found -- GPU collision disabled");
                return;
            }

            ByteBuffer spirvCode = ByteBuffer.allocateDirect(spirvBytes.length);
            spirvCode.put(spirvBytes);
            spirvCode.flip();

            pipeline = new VkComputePipeline();
            pipeline.initialize(spirvCode);

            allocateGpuBuffers(INITIAL_ENTITY_CAPACITY);

            LOGGER.info("GPU collision dispatcher initialized (capacity={})", INITIAL_ENTITY_CAPACITY);
        } catch (Throwable t) {
            LOGGER.warn("GPU collision dispatcher init failed: {} -- will use CPU fallback", t.getMessage());
            LOGGER.debug("GPU collision init error details:", t);
            shutdownPipeline();
        }
    }

    /**
     * Shuts down the dispatcher, releasing all GPU resources.
     * Safe to call multiple times; safe to call if never initialized.
     */
    public void shutdown() {
        shutdownPipeline();
        freeGpuBuffers();
    }

    // ------------------------------------------------------------------ //
    //  Main dispatch entry point
    // ------------------------------------------------------------------ //

    /**
     * Runs broad-phase collision detection on the given entity list.
     * <p>
     * This method <b>never throws</b>.  On any GPU or internal error it returns
     * an empty list (or falls back to CPU).  The caller should treat an empty
     * result as "no collisions detected this tick" rather than an error signal.
     *
     * @param entities the entities to test (may be empty, never null)
     * @return list of potential collision pairs; never null
     */
    public List<CollisionPair> computeBroadPhase(List<Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        final int entityCount = entities.size();

        // Fast path for very small entity counts -- GPU overhead not worth it
        if (entityCount < 16 || !isGpuReady()) {
            return cpuFallback(entities);
        }

        // Wrap the entire GPU path in CrashGuard -- on any failure, fall back to CPU
        List<CollisionPair> result = crashGuard.execute(() -> {
            ensureBuffers(entityCount);
            extractEntityData(entities);
            uploadAndDispatch(entityCount);
            return downloadPairs(entityCount);
        }, null);

        if (result != null) {
            gpuCollisionCount++;
            return result;
        }

        // CrashGuard caught a failure -- use CPU fallback
        return cpuFallback(entities);
    }

    // ------------------------------------------------------------------ //
    //  GPU pipeline
    // ------------------------------------------------------------------ //

    private boolean isGpuReady() {
        return pipeline != null && pipeline.isInitialized() && !pipeline.isDeviceLost();
    }

    private void uploadAndDispatch(int entityCount) {
        // Upload entity position and dimension data
        VkBufferManager.uploadFloats(posXBuffer, posX, 0, entityCount);
        VkBufferManager.uploadFloats(posYBuffer, posY, 0, entityCount);
        VkBufferManager.uploadFloats(posZBuffer, posZ, 0, entityCount);
        VkBufferManager.uploadFloats(halfWidthsBuffer, halfWidths, 0, entityCount);
        VkBufferManager.uploadFloats(heightsBuffer, heights, 0, entityCount);

        // Reset pair counter on CPU side before GPU write
        pairCounter[0] = 0;
        VkBufferManager.uploadInts(pairCounterBuffer, pairCounter, 0, 1);

        // Dispatch compute shader with push constants:
        //   entityCount, maxPairs, rangeLimit (packed as float bits for push constants)
        // Push constant layout: [0]=entityCount(int-as-float), [1]=maxPairs(int-as-float),
        //                        [2]=rangeLimit(float), padded to 7 ints (28 bytes)
        int[] pushData = new int[]{
                entityCount,
                MAX_COLLISION_PAIRS,
                Float.floatToIntBits(RANGE_LIMIT),
                0, 0, 0, 0 // padding to fill 28 bytes (7 ints)
        };

        // Workgroup dispatch: one workgroup per entity in the X dimension
        int wgX = (entityCount + 63) / 64; // local_size_x = 64 in shader
        pipeline.dispatch(pushData, wgX, 1, 1);
    }

    private List<CollisionPair> downloadPairs(int entityCount) {
        // Download pair counter first
        VkBufferManager.downloadInts(pairCounterBuffer, pairCounter, 0, 1);

        int pairCount = pairCounter[0];
        if (pairCount <= 0) {
            return Collections.emptyList();
        }

        // Clamp to maximum to prevent out-of-bounds reads
        if (pairCount > MAX_COLLISION_PAIRS) {
            LOGGER.warn("GPU collision pair count ({}) exceeded max ({}) -- clamping",
                    pairCount, MAX_COLLISION_PAIRS);
            pairCount = MAX_COLLISION_PAIRS;
        }

        // Download pair index arrays
        VkBufferManager.downloadInts(pairsABuffer, pairsA, 0, pairCount);
        VkBufferManager.downloadInts(pairsBBuffer, pairsB, 0, pairCount);

        // Map indices back to Entity references
        List<CollisionPair> results = new ArrayList<>(pairCount);
        for (int i = 0; i < pairCount; i++) {
            int idxA = pairsA[i];
            int idxB = pairsB[i];

            // Bounds check to guard against corrupted GPU output
            if (idxA < 0 || idxA >= entityCount || idxB < 0 || idxB >= entityCount) {
                LOGGER.debug("Skipping invalid GPU collision pair: ({}, {})", idxA, idxB);
                continue;
            }
            // Avoid self-pairs and duplicates (a < b ordering)
            if (idxA >= idxB) {
                continue;
            }

            Entity entityA = entityIndexMap[idxA];
            Entity entityB = entityIndexMap[idxB];

            if (entityA != null && entityB != null && entityA.isAlive() && entityB.isAlive()) {
                results.add(new CollisionPair(entityA, entityB));
            }
        }

        if (!results.isEmpty()) {
            LOGGER.debug("GPU broad-phase: {} entities -> {} potential pairs", entityCount, results.size());
        }

        return results;
    }

    // ------------------------------------------------------------------ //
    //  Entity data extraction (CPU side, fast)
    // ------------------------------------------------------------------ //

    /**
     * Extracts AABB data from each entity into flat arrays for GPU upload.
     * Also builds the index-to-entity mapping for later reference.
     */
    private void extractEntityData(List<Entity> entities) {
        int size = entities.size();
        for (int i = 0; i < size; i++) {
            Entity entity = entities.get(i);

            posX[i] = (float) entity.getX();
            posY[i] = (float) entity.getY();
            posZ[i] = (float) entity.getZ();
            halfWidths[i] = entity.getBbWidth() * 0.5f;
            heights[i] = entity.getBbHeight();
            entityIndexMap[i] = entity;
        }
    }

    // ------------------------------------------------------------------ //
    //  Buffer management
    // ------------------------------------------------------------------ //

    /**
     * Ensures CPU arrays and GPU buffers are large enough for the given entity count.
     * Uses a doubling strategy to amortize reallocation cost.
     */
    private void ensureBuffers(int entityCount) {
        if (entityCount <= lastEntityCapacity) {
            return;
        }

        // Grow CPU arrays (double until large enough)
        int newCapacity = lastEntityCapacity;
        while (newCapacity < entityCount) {
            newCapacity = newCapacity * 2;
        }

        posX = new float[newCapacity];
        posY = new float[newCapacity];
        posZ = new float[newCapacity];
        halfWidths = new float[newCapacity];
        heights = new float[newCapacity];
        entityIndexMap = new Entity[newCapacity];

        // Reallocate GPU buffers
        freeGpuBuffers();
        allocateGpuBuffers(newCapacity);

        lastEntityCapacity = newCapacity;
        LOGGER.debug("Grew collision buffers to capacity {}", newCapacity);
    }

    /**
     * Allocates GPU SSBOs for entity data and collision pair output.
     * All buffers are host-visible and host-coherent for CPU-side read/write.
     */
    private void allocateGpuBuffers(int capacity) {
        long entityFloatSize = (long) capacity * Float.BYTES;
        long pairIntSize = (long) MAX_COLLISION_PAIRS * Integer.BYTES;
        long counterSize = Integer.BYTES;

        posXBuffer = VkBufferManager.createHostVisibleSSBO(entityFloatSize);
        posYBuffer = VkBufferManager.createHostVisibleSSBO(entityFloatSize);
        posZBuffer = VkBufferManager.createHostVisibleSSBO(entityFloatSize);
        halfWidthsBuffer = VkBufferManager.createHostVisibleSSBO(entityFloatSize);
        heightsBuffer = VkBufferManager.createHostVisibleSSBO(entityFloatSize);
        pairsABuffer = VkBufferManager.createHostVisibleSSBO(pairIntSize);
        pairsBBuffer = VkBufferManager.createHostVisibleSSBO(pairIntSize);
        pairCounterBuffer = VkBufferManager.createHostVisibleSSBO(counterSize);
    }

    /**
     * Frees all GPU buffers.  Null-safe -- skips buffers that were never allocated
     * or have already been freed.
     */
    private void freeGpuBuffers() {
        VkBufferManager.destroyBuffer(posXBuffer);
        VkBufferManager.destroyBuffer(posYBuffer);
        VkBufferManager.destroyBuffer(posZBuffer);
        VkBufferManager.destroyBuffer(halfWidthsBuffer);
        VkBufferManager.destroyBuffer(heightsBuffer);
        VkBufferManager.destroyBuffer(pairsABuffer);
        VkBufferManager.destroyBuffer(pairsBBuffer);
        VkBufferManager.destroyBuffer(pairCounterBuffer);

        posXBuffer = null;
        posYBuffer = null;
        posZBuffer = null;
        halfWidthsBuffer = null;
        heightsBuffer = null;
        pairsABuffer = null;
        pairsBBuffer = null;
        pairCounterBuffer = null;
    }

    // ------------------------------------------------------------------ //
    //  CPU fallback
    // ------------------------------------------------------------------ //

    /**
     * Simple O(n^2) AABB overlap check on the CPU.
     * Used when the GPU is unavailable, the CrashGuard has tripped,
     * or the entity count is too small to justify GPU overhead.
     * <p>
     * Two entities are considered potentially colliding when their AABBs overlap
     * on all three axes.
     *
     * @param entities the entities to test
     * @return list of collision pairs; never null
     */
    private List<CollisionPair> cpuFallback(List<Entity> entities) {
        cpuFallbackCount++;

        int size = entities.size();
        if (size < 2) {
            return Collections.emptyList();
        }

        // Pre-extract AABB data to avoid repeated method calls in the inner loop
        float[] ax = new float[size];
        float[] ay = new float[size];
        float[] az = new float[size];
        float[] ahw = new float[size];
        float[] ah = new float[size];

        for (int i = 0; i < size; i++) {
            Entity e = entities.get(i);
            ax[i] = (float) e.getX();
            ay[i] = (float) e.getY();
            az[i] = (float) e.getZ();
            ahw[i] = e.getBbWidth() * 0.5f;
            ah[i] = e.getBbHeight();
        }

        // Upper-bound estimate: start with a reasonable capacity
        List<CollisionPair> results = new ArrayList<>(Math.min(size * 2, 256));

        for (int i = 0; i < size; i++) {
            // AABB mins/maxs for entity i
            float iMinX = ax[i] - ahw[i];
            float iMaxX = ax[i] + ahw[i];
            float iMinY = ay[i];
            float iMaxY = ay[i] + ah[i];
            float iMinZ = az[i] - ahw[i];
            float iMaxZ = az[i] + ahw[i];

            for (int j = i + 1; j < size; j++) {
                // AABB mins/maxs for entity j
                float jMinX = ax[j] - ahw[j];
                float jMaxX = ax[j] + ahw[j];
                float jMinY = ay[j];
                float jMaxY = ay[j] + ah[j];
                float jMinZ = az[j] - ahw[j];
                float jMaxZ = az[j] + ahw[j];

                // Standard AABB overlap test: all axes must overlap
                if (iMinX < jMaxX && iMaxX > jMinX &&
                    iMinY < jMaxY && iMaxY > jMinY &&
                    iMinZ < jMaxZ && iMaxZ > jMinZ) {

                    Entity ea = entities.get(i);
                    Entity eb = entities.get(j);

                    if (ea.isAlive() && eb.isAlive()) {
                        results.add(new CollisionPair(ea, eb));
                    }
                }
            }
        }

        return results;
    }

    // ------------------------------------------------------------------ //
    //  Shader loading
    // ------------------------------------------------------------------ //

    /**
     * Loads the pre-compiled SPIR-V shader bytes from the classpath.
     * Tries the {@code .spv} (pre-compiled) path first, then the raw {@code .comp}
     * path as a fallback (though runtime GLSL compilation is not supported -- the
     * pipeline expects SPIR-V).
     *
     * @return shader bytes, or null if the shader could not be loaded
     */
    private byte[] loadShaderBytes() {
        // Try pre-compiled SPIR-V first
        String[] paths = {
                "/assets/async/shaders/collision_broadphase.comp.spv",
                "/assets/async/shaders/collision_broadphase.spv",
                "/assets/async/shaders/collision_broadphase.comp"
        };

        for (String path : paths) {
            try (InputStream is = GpuCollisionDispatcher.class.getResourceAsStream(path)) {
                if (is != null) {
                    byte[] bytes = is.readAllBytes();
                    if (bytes.length > 0) {
                        LOGGER.debug("Loaded collision shader from {} ({} bytes)", path, bytes.length);
                        return bytes;
                    }
                }
            } catch (IOException e) {
                LOGGER.debug("Failed to load shader from {}: {}", path, e.getMessage());
            }
        }

        LOGGER.warn("Could not load collision broad-phase shader from any classpath location");
        return null;
    }

    // ------------------------------------------------------------------ //
    //  Shutdown helpers
    // ------------------------------------------------------------------ //

    private void shutdownPipeline() {
        if (pipeline != null) {
            try {
                pipeline.shutdown();
            } catch (Throwable t) {
                LOGGER.debug("Pipeline shutdown error (non-fatal): {}", t.getMessage());
            }
            pipeline = null;
        }
    }

    // ------------------------------------------------------------------ //
    //  Telemetry
    // ------------------------------------------------------------------ //

    /** Returns the total number of ticks where GPU collision was used successfully. */
    public int getGpuCollisionCount() {
        return gpuCollisionCount;
    }

    /** Returns the total number of ticks where CPU fallback was used. */
    public int getCpuFallbackCount() {
        return cpuFallbackCount;
    }

    /**
     * Returns the CrashGuard statistics for monitoring GPU health.
     *
     * @return crash guard stats snapshot, never null
     */
    public CrashGuard.CrashGuardStats getCrashGuardStats() {
        return crashGuard.getStats();
    }
}
