package com.axalotl.async.common.gpu.vulkan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Instance-based Vulkan compute pipeline. Each instance manages one compute shader.
 * All Vulkan access via reflection to avoid hard LWJGL dependency.
 * Lifecycle: initialize -> dispatch -> shutdown.
 */
public class VkComputePipeline {

    private static final Logger LOGGER = LoggerFactory.getLogger("Async/VkPipeline");

    private static final int VK_SUCCESS = 0, VK_TIMEOUT = 2, VK_ERROR_DEVICE_LOST = -4;
    private static final int VK_DESCRIPTOR_TYPE_STORAGE_BUFFER = 7;
    private static final int VK_SHADER_STAGE_COMPUTE_BIT = 0x20;
    private static final int VK_PIPELINE_BIND_POINT_COMPUTE = 1;
    private static final int VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT = 1;
    private static final int VK_COMMAND_BUFFER_LEVEL_PRIMARY = 0;
    private static final int VK_FENCE_CREATE_SIGNALED_BIT = 1;
    private static final int MAX_RETRIES = 2;
    private static final long FENCE_TIMEOUT_NS = 5_000_000_000L;
    private static final int PUSH_SIZE = 28, BINDING_COUNT = 4;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile boolean deviceLost;
    private final ReentrantLock dispatchLock = new ReentrantLock();

    private long shaderModule, descriptorSetLayout, pipelineLayout, pipeline;
    private long descriptorPool, descriptorSet, commandPool, commandBuffer, fence;
    private int consecutiveTimeouts, totalDispatches;
    private long lastDispatchTimeNs;

    public VkComputePipeline() {}

    // =====================================================================
    //  Public API
    // =====================================================================

    public synchronized void initialize(ByteBuffer spirvCode) {
        if (initialized.get()) return;
        if (!VkDeviceManager.isAvailable()) throw new IllegalStateException("Vulkan device not available");
        try {
            createShaderModule(spirvCode);
            createDescriptorSetLayout();
            createPipelineLayout();
            createComputePipeline();
            createDescriptorPool();
            allocateDescriptorSet();
            createCommandPool();
            allocateCommandBuffer();
            createFence();
            initialized.set(true);
            LOGGER.info("Compute pipeline initialised");
        } catch (Throwable t) {
            LOGGER.error("Pipeline init failed: {}", t.getMessage());
            shutdown();
            throw new RuntimeException("Pipeline init failed", t);
        }
    }

    public void dispatch(int[] pushData, int wgX, int wgY, int wgZ) {
        if (!initialized.get()) throw new IllegalStateException("Pipeline not initialised");
        if (deviceLost) throw new RuntimeException("GPU device lost - requires restart");
        dispatchLock.lock();
        try { dispatchInternal(pushData, wgX, wgY, wgZ); }
        finally { dispatchLock.unlock(); }
    }

    public void updateBufferBinding(int bindingIndex, VkBufferManager.BufferHandle buffer) {
        if (!initialized.get()) throw new IllegalStateException("Pipeline not initialised");
        try { updateSingleDescriptor(bindingIndex, buffer); }
        catch (Throwable t) { throw new RuntimeException("Descriptor update failed", t); }
    }

    public synchronized void shutdown() {
        safeWaitIdle();
        try {
            if (fence != 0) nvoid("vkDestroyFence", 3, dev(), fence, 0L);
            if (commandPool != 0) nvoid("vkDestroyCommandPool", 3, dev(), commandPool, 0L);
            if (descriptorPool != 0) nvoid("vkDestroyDescriptorPool", 3, dev(), descriptorPool, 0L);
            if (pipeline != 0) nvoid("vkDestroyPipeline", 3, dev(), pipeline, 0L);
            if (pipelineLayout != 0) nvoid("vkDestroyPipelineLayout", 3, dev(), pipelineLayout, 0L);
            if (descriptorSetLayout != 0) nvoid("vkDestroyDescriptorSetLayout", 3, dev(), descriptorSetLayout, 0L);
            if (shaderModule != 0) nvoid("vkDestroyShaderModule", 3, dev(), shaderModule, 0L);
        } catch (Throwable t) { LOGGER.debug("Cleanup error: {}", t.getMessage()); }
        shaderModule = descriptorSetLayout = pipelineLayout = pipeline = 0;
        descriptorPool = descriptorSet = commandPool = commandBuffer = fence = 0;
        initialized.set(false); deviceLost = false;
        LOGGER.info("Compute pipeline shut down");
    }

    public boolean isInitialized() { return initialized.get(); }
    public boolean isDeviceLost() { return deviceLost; }
    public int getTotalDispatches() { return totalDispatches; }

    // =====================================================================
    //  Dispatch
    // =====================================================================

    private void dispatchInternal(int[] pushData, int wgX, int wgY, int wgZ) {
        Throwable lastError = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                Object stack = push(); try {
                    Object pF = mallocLong(stack, 1); putLong(pF, 0, fence);
                    check(ncall("nvkResetFences", 3, dev(), 1, addr(pF)), "vkResetFences");

                    Object cmdBuf = VkDeviceManager.wrapCommandBuffer(commandBuffer);
                    Object bi = calloc("VkCommandBufferBeginInfo", stack);
                    sfield(bi, "sType", 41); sfield(bi, "flags", VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
                    nvoid("nvkBeginCommandBuffer", 2, cmdBuf, addr(bi));
                    nvoid("nvkCmdBindPipeline", 3, cmdBuf, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
                    Object pDS = mallocLong(stack, 1); putLong(pDS, 0, descriptorSet);
                    nvoid("nvkCmdBindDescriptorSets", 8, cmdBuf, VK_PIPELINE_BIND_POINT_COMPUTE,
                            pipelineLayout, 0, 1, addr(pDS), 0, 0L);

                    Object pPush = mallocInt(stack, pushData.length);
                    for (int i = 0; i < pushData.length; i++) ((IntBuffer) pPush).put(i, pushData[i]);
                    nvoid("nvkCmdPushConstants", 6, cmdBuf, pipelineLayout,
                            VK_SHADER_STAGE_COMPUTE_BIT, 0, PUSH_SIZE, addr(pPush));

                    vkMethod("vkCmdDispatch", 4).invoke(null, cmdBuf, wgX, wgY, wgZ);
                    check((int) vkMethod("vkEndCommandBuffer", 1).invoke(null, cmdBuf), "vkEndCommandBuffer");

                    Object pCB = mallocPtr(stack, 1); putLong(pCB, 0, commandBuffer);
                    Object si = calloc("VkSubmitInfo", stack);
                    sfield(si, "sType", 4);
                    writePtrArr(si, "COMMANDBUFFERCOUNT", "PCOMMANDBUFFERS", 1, pCB);
                    int sr = ncall("nvkQueueSubmit", 4, VkDeviceManager.getComputeQueue(), 1, addr(si), fence);
                    if (sr == VK_ERROR_DEVICE_LOST) deviceLost = true;
                    check(sr, "vkQueueSubmit");

                    Object pF2 = mallocLong(stack, 1); putLong(pF2, 0, fence);
                    int wr = ncall("nvkWaitForFences", 5, dev(), 1, addr(pF2), 1, FENCE_TIMEOUT_NS);
                    if (wr == VK_TIMEOUT) throw new RuntimeException("vkWaitForFences timeout");
                    if (wr == VK_ERROR_DEVICE_LOST) { deviceLost = true; throw new RuntimeException("device lost"); }
                    check(wr, "vkWaitForFences");
                } finally { pop(stack); }
                consecutiveTimeouts = 0; totalDispatches++; lastDispatchTimeNs = System.nanoTime();
                return;
            } catch (Throwable t) {
                lastError = t;
                String msg = t.getMessage() != null ? t.getMessage() : "";
                if (msg.contains("device lost")) { deviceLost = true; throw new RuntimeException("GPU device lost", t); }
                if (msg.contains("timeout")) { consecutiveTimeouts++; safeWaitIdle(); continue; }
                safeWaitIdle();
            }
        }
        throw new RuntimeException("GPU dispatch failed after " + (MAX_RETRIES + 1) + " attempts", lastError);
    }

    // =====================================================================
    //  Init helpers
    // =====================================================================

    private void createShaderModule(ByteBuffer spirvCode) throws Throwable {
        Object s = push(); try {
            Object ci = calloc("VkShaderModuleCreateInfo", s); sfield(ci, "sType", 14);
            int[] code = new int[spirvCode.remaining() / 4]; spirvCode.asIntBuffer().get(code);
            Object pCode = mallocInt(s, code.length); ((IntBuffer) pCode).put(code);
            long sa = saddr(ci); Method mpl = memPutLong();
            mpl.invoke(null, sa + off(ci, "CODESIZE", 24), (long) spirvCode.remaining());
            mpl.invoke(null, sa + off(ci, "PCODE", 32), VkDeviceManager.memAddress(pCode));
            Object pM = mallocLong(s, 1);
            check(ncall("nvkCreateShaderModule", 4, dev(), sa, 0L, addr(pM)), "vkCreateShaderModule");
            shaderModule = getLong(pM, 0);
        } finally { pop(s); }
    }

    private void createDescriptorSetLayout() throws Throwable {
        Object s = push(); try {
            Object pb = callocBuf("VkDescriptorSetLayoutBinding", BINDING_COUNT, s);
            for (int i = 0; i < BINDING_COUNT; i++) {
                pos(pb, i); sfield(pb, "binding", i); sfield(pb, "descriptorType", VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
                sfield(pb, "descriptorCount", 1); sfield(pb, "stageFlags", VK_SHADER_STAGE_COMPUTE_BIT);
            }
            pos(pb, 0);
            Object ci = calloc("VkDescriptorSetLayoutCreateInfo", s); sfield(ci, "sType", 16);
            safeSetter(ci, "pBindings", pb);
            Object pL = mallocLong(s, 1);
            check(ncall("nvkCreateDescriptorSetLayout", 4, dev(), addr(ci), 0L, addr(pL)), "DescriptorSetLayout");
            descriptorSetLayout = getLong(pL, 0);
        } finally { pop(s); }
    }

    private void createPipelineLayout() throws Throwable {
        Object s = push(); try {
            Object ppr = callocBuf("VkPushConstantRange", 1, s);
            sfield(ppr, "stageFlags", VK_SHADER_STAGE_COMPUTE_BIT);
            sfield(ppr, "offset", 0); sfield(ppr, "size", PUSH_SIZE);
            Object psl = mallocLong(s, 1); putLong(psl, 0, descriptorSetLayout);
            Object ci = calloc("VkPipelineLayoutCreateInfo", s); sfield(ci, "sType", 28);
            writePtrArr(ci, "SETLAYOUTCOUNT", "PSETLAYOUTS", 1, psl);
            safeSetter(ci, "pPushConstantRanges", ppr);
            Object ppl = mallocLong(s, 1);
            check(ncall("nvkCreatePipelineLayout", 4, dev(), addr(ci), 0L, addr(ppl)), "PipelineLayout");
            pipelineLayout = getLong(ppl, 0);
        } finally { pop(s); }
    }

    private void createComputePipeline() throws Throwable {
        Object s = push(); try {
            Object stage = calloc("VkPipelineShaderStageCreateInfo", s);
            sfield(stage, "sType", 19); sfield(stage, "stage", VK_SHADER_STAGE_COMPUTE_BIT);
            sfield(stage, "module", shaderModule);
            Object pN = malloc(s, 5); ((ByteBuffer) pN).put(new byte[]{'m','a','i','n',0}).flip();
            writePtr(stage, "PNAME", pN);
            Object ci = calloc("VkComputePipelineCreateInfo", s); sfield(ci, "sType", 43);
            sfield(ci, "layout", pipelineLayout);
            ci.getClass().getMethod("stage", Class.forName("org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo")).invoke(ci, stage);
            Object pp = mallocLong(s, 1);
            check(ncall("nvkCreateComputePipelines", 6, dev(), 0L, 1, addr(ci), 0L, addr(pp)), "ComputePipeline");
            pipeline = getLong(pp, 0);
        } finally { pop(s); }
    }

    private void createDescriptorPool() throws Throwable {
        Object s = push(); try {
            Object pps = callocBuf("VkDescriptorPoolSize", 1, s);
            sfield(pps, "type", VK_DESCRIPTOR_TYPE_STORAGE_BUFFER); sfield(pps, "descriptorCount", BINDING_COUNT);
            Object ci = calloc("VkDescriptorPoolCreateInfo", s); sfield(ci, "sType", 15);
            sfield(ci, "flags", 1); sfield(ci, "maxSets", 1); safeSetter(ci, "pPoolSizes", pps);
            Object pp = mallocLong(s, 1);
            check(ncall("nvkCreateDescriptorPool", 4, dev(), addr(ci), 0L, addr(pp)), "DescriptorPool");
            descriptorPool = getLong(pp, 0);
        } finally { pop(s); }
    }

    private void allocateDescriptorSet() throws Throwable {
        Object s = push(); try {
            Object psl = mallocLong(s, 1); putLong(psl, 0, descriptorSetLayout);
            Object ai = calloc("VkDescriptorSetAllocateInfo", s); sfield(ai, "sType", 17);
            sfield(ai, "descriptorPool", descriptorPool);
            writePtrArr(ai, "DESCRIPTORSETCOUNT", "PSETLAYOUTS", 1, psl);
            Object ps = mallocLong(s, 1);
            check(ncall("nvkAllocateDescriptorSets", 3, dev(), addr(ai), addr(ps)), "AllocateDescriptorSets");
            descriptorSet = getLong(ps, 0);
        } finally { pop(s); }
    }

    private void createCommandPool() throws Throwable {
        Object s = push(); try {
            Object ci = calloc("VkCommandPoolCreateInfo", s); sfield(ci, "sType", 39);
            sfield(ci, "flags", 2); sfield(ci, "queueFamilyIndex", VkDeviceManager.getComputeQueueFamilyIndex());
            Object pp = mallocLong(s, 1);
            check(ncall("nvkCreateCommandPool", 4, dev(), addr(ci), 0L, addr(pp)), "CommandPool");
            commandPool = getLong(pp, 0);
        } finally { pop(s); }
    }

    private void allocateCommandBuffer() throws Throwable {
        Object s = push(); try {
            Object ai = calloc("VkCommandBufferAllocateInfo", s); sfield(ai, "sType", 40);
            sfield(ai, "commandPool", commandPool);
            sfield(ai, "level", VK_COMMAND_BUFFER_LEVEL_PRIMARY); sfield(ai, "commandBufferCount", 1);
            Object pc = mallocPtr(s, 1);
            check(ncall("nvkAllocateCommandBuffers", 3, dev(), addr(ai), addr(pc)), "AllocateCommandBuffers");
            commandBuffer = getLong(pc, 0);
        } finally { pop(s); }
    }

    private void createFence() throws Throwable {
        Object s = push(); try {
            Object ci = calloc("VkFenceCreateInfo", s); sfield(ci, "sType", 13);
            sfield(ci, "flags", VK_FENCE_CREATE_SIGNALED_BIT);
            Object pf = mallocLong(s, 1);
            check(ncall("nvkCreateFence", 4, dev(), addr(ci), 0L, addr(pf)), "vkCreateFence");
            fence = getLong(pf, 0);
        } finally { pop(s); }
    }

    // =====================================================================
    //  Descriptor update
    // =====================================================================

    private void updateSingleDescriptor(int binding, VkBufferManager.BufferHandle buf) throws Throwable {
        Object s = push(); try {
            Object pw = callocBuf("VkWriteDescriptorSet", 1, s);
            sfield(pw, "sType", 23); sfield(pw, "dstSet", descriptorSet);
            sfield(pw, "dstBinding", binding); sfield(pw, "descriptorCount", 1);
            sfield(pw, "descriptorType", VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
            Object pbi = callocBuf("VkDescriptorBufferInfo", 1, s);
            sfield(pbi, "buffer", buf.buffer()); sfield(pbi, "offset", 0L); sfield(pbi, "range", buf.size());
            safeSetter(pw, "pBufferInfo", pbi); pos(pw, 0);
            nvoid("nvkUpdateDescriptorSets", 5, dev(), 1, addr(pw), 0, 0L);
        } finally { pop(s); }
    }

    // =====================================================================
    //  Reflection helpers
    // =====================================================================

    private static Object dev() { return VkDeviceManager.getDevice(); }
    private static long addr(Object o) throws Exception { return VkDeviceManager.memAddress(o); }
    private static void sfield(Object s, String f, Object v) throws Exception { VkDeviceManager.setStructField(s, f, v); }
    private static void check(int r, String ctx) { if (r != VK_SUCCESS) throw new RuntimeException(ctx + " failed: " + r); }

    private static int ncall(String name, int n, Object... a) throws Exception {
        return (int) VkDeviceManager.findNativeVkMethod(name, n).invoke(null, a);
    }
    private static void nvoid(String name, int n, Object... a) throws Exception {
        VkDeviceManager.findNativeVkMethod(name, n).invoke(null, a);
    }
    private static Method vkMethod(String name, int n) throws Exception { return VkDeviceManager.findVkMethod(name, n); }

    // -- MemoryStack --------------------------------------------------------

    private static Object push() throws Exception {
        Method m = Class.forName("org.lwjgl.system.MemoryStack").getMethod("stackPush");
        m.setAccessible(true); return m.invoke(null);
    }
    private static void pop(Object s) throws Exception { s.getClass().getMethod("pop").invoke(s); }

    private static Object calloc(String name, Object s) throws Exception {
        return Class.forName("org.lwjgl.vulkan." + name).getMethod("calloc", s.getClass()).invoke(null, s);
    }
    private static Object callocBuf(String name, int n, Object s) throws Exception {
        return Class.forName("org.lwjgl.vulkan." + name).getMethod("calloc", int.class, s.getClass()).invoke(null, n, s);
    }
    private static Object mallocLong(Object s, int n) throws Exception { return s.getClass().getMethod("mallocLong", int.class).invoke(s, n); }
    private static Object mallocInt(Object s, int n) throws Exception { return s.getClass().getMethod("mallocInt", int.class).invoke(s, n); }
    private static Object mallocPtr(Object s, int n) throws Exception { return s.getClass().getMethod("mallocPointer", int.class).invoke(s, n); }
    private static Object malloc(Object s, int n) throws Exception { return s.getClass().getMethod("malloc", int.class).invoke(s, n); }

    // -- Buffer accessors ---------------------------------------------------

    private static long getLong(Object b, int i) throws Exception { return (long) b.getClass().getMethod("get", int.class).invoke(b, i); }
    private static void putLong(Object b, int i, long v) throws Exception { b.getClass().getMethod("put", int.class, long.class).invoke(b, i, v); }
    private static void pos(Object b, int p) throws Exception { b.getClass().getMethod("position", int.class).invoke(b, p); }

    // -- Struct pointer writes ----------------------------------------------

    private static long saddr(Object s) throws Exception { return (long) s.getClass().getMethod("address").invoke(s); }
    private static Method memPutLong() throws Exception {
        return Class.forName("org.lwjgl.system.MemoryUtil").getMethod("memPutLong", long.class, long.class);
    }
    private static int off(Object s, String f, int d) {
        try { return s.getClass().getField(f).getInt(null); } catch (Exception e) { return d; }
    }
    private static void writePtrArr(Object s, String cf, String pf, int cnt, Object buf) throws Throwable {
        long sa = saddr(s); int co = off(s, cf, -1), po = off(s, pf, -1);
        Method mpi = Class.forName("org.lwjgl.system.MemoryUtil").getMethod("memPutInt", long.class, int.class);
        if (co >= 0) mpi.invoke(null, sa + co, cnt);
        if (po >= 0) memPutLong().invoke(null, sa + po, addr(buf));
    }
    private static void writePtr(Object s, String f, Object buf) throws Throwable {
        int o = off(s, f, -1); if (o >= 0) memPutLong().invoke(null, saddr(s) + o, addr(buf));
    }
    private static void safeSetter(Object s, String setter, Object buf) throws Throwable {
        Class<?> bc = buf.getClass(), c = bc;
        while (c != null && c.getName().startsWith("org.lwjgl")) { bc = c; c = c.getSuperclass(); }
        try { Method m = s.getClass().getMethod(setter, bc); m.setAccessible(true); m.invoke(s, buf); }
        catch (NoSuchMethodException e) { writePtr(s, setter.toUpperCase(), buf); }
    }
    private static void safeWaitIdle() {
        try { vkMethod("vkDeviceWaitIdle", 1).invoke(null, dev()); } catch (Throwable t) { LOGGER.debug("WaitIdle failed"); }
    }
}
