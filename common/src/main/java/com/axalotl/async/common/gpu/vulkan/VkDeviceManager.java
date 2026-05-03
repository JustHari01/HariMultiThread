package com.axalotl.async.common.gpu.vulkan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton Vulkan device manager for entity GPU compute workloads.
 * All Vulkan/LWJGL types accessed via reflection to avoid hard dependency on native Vulkan.
 * When unavailable, {@link #isAvailable()} returns false and callers fall back to CPU.
 */
public final class VkDeviceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Async/VkDevice");
    private static final String[] VK_CLASSES = {
            "org.lwjgl.vulkan.VK10", "org.lwjgl.vulkan.VK11",
            "org.lwjgl.vulkan.VK12", "org.lwjgl.vulkan.VK13"
    };

    // ---- singleton state ------------------------------------------------

    private static volatile Object vkInstanceHandle;
    private static volatile Object vkPhysicalDeviceHandle;
    private static volatile Object vkDeviceHandle;
    private static volatile Object vkComputeQueueHandle;

    private static volatile String deviceName = "unknown";
    private static volatile int deviceType;
    private static volatile int computeQueueFamilyIndex = -1;
    private static volatile long maxWorkGroupInvocations;
    private static volatile long maxSharedMemoryBytes;
    private static volatile int[] maxWorkGroupSize = {0, 0, 0};

    private static final AtomicBoolean available = new AtomicBoolean(false);
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private VkDeviceManager() { /* singleton */ }

    // =====================================================================
    //  Public API
    // =====================================================================

    /** Initialises Vulkan. Safe to call multiple times; only the first invocation does work. */
    public static synchronized void initialize() {
        if (initialized.get()) return;

        try {
            if (!findAndLoadVulkanLibrary()) {
                LOGGER.info("Vulkan native library not found - GPU compute disabled");
                return;
            }

            Object instance = createInstance();
            if (instance == null) {
                LOGGER.info("Failed to create Vulkan instance - GPU compute disabled");
                return;
            }
            long instanceHandle = getAddress(instance);
            vkInstanceHandle = instanceHandle;

            long physicalDevice = selectPhysicalDevice(instanceHandle);
            if (physicalDevice == 0L) {
                LOGGER.info("No suitable Vulkan GPU found - GPU compute disabled");
                destroyInstance(instanceHandle);
                return;
            }
            vkPhysicalDeviceHandle = physicalDevice;

            int queueFamily = findComputeQueueFamily(instanceHandle, physicalDevice);
            if (queueFamily < 0) {
                LOGGER.info("No compute queue family found on selected GPU");
                destroyInstance(instanceHandle);
                return;
            }
            computeQueueFamilyIndex = queueFamily;

            long device = createLogicalDevice(physicalDevice, queueFamily);
            if (device == 0L) {
                LOGGER.info("Failed to create Vulkan logical device");
                destroyInstance(instanceHandle);
                return;
            }
            vkDeviceHandle = device;
            vkComputeQueueHandle = getDeviceQueue(device, queueFamily, 0);

            queryDeviceProperties(physicalDevice);

            available.set(true);
            LOGGER.info("Vulkan compute initialised on {} (queue family {})", deviceName, queueFamily);

        } catch (UnsatisfiedLinkError e) {
            LOGGER.info("Vulkan native library not linkable - GPU compute disabled");
        } catch (NoClassDefFoundError e) {
            LOGGER.info("LWJGL Vulkan classes not found - GPU compute disabled");
        } catch (Throwable t) {
            LOGGER.info("Vulkan initialisation failed: {} - GPU compute disabled", t.getMessage());
        } finally {
            initialized.set(true);
        }
    }

    /** Destroys the logical device and Vulkan instance. */
    public static synchronized void shutdown() {
        if (!initialized.get()) return;
        try {
            Long device = asLong(vkDeviceHandle);
            Long instance = asLong(vkInstanceHandle);
            if (device != null) {
                invokeVk("vkDeviceWaitIdle", device);
                invokeVk("vkDestroyDevice", device, 0L);
            }
            if (instance != null) destroyInstance(instance);
        } catch (Throwable t) {
            LOGGER.debug("Error during Vulkan shutdown", t);
        } finally {
            vkDeviceHandle = null;
            vkInstanceHandle = null;
            vkPhysicalDeviceHandle = null;
            vkComputeQueueHandle = null;
            computeQueueFamilyIndex = -1;
            available.set(false);
            initialized.set(false);
        }
        LOGGER.info("Vulkan device manager shut down");
    }

    public static boolean isAvailable() { return available.get(); }

    public static long getDevice() {
        long v = asLong(vkDeviceHandle);
        if (v == 0L) throw new IllegalStateException("Vulkan device not initialised");
        return v;
    }

    public static long getPhysicalDevice() {
        long v = asLong(vkPhysicalDeviceHandle);
        if (v == 0L) throw new IllegalStateException("Vulkan physical device not initialised");
        return v;
    }

    public static long getInstance() {
        long v = asLong(vkInstanceHandle);
        if (v == 0L) throw new IllegalStateException("Vulkan instance not initialised");
        return v;
    }

    public static long getComputeQueue() {
        long v = asLong(vkComputeQueueHandle);
        if (v == 0L) throw new IllegalStateException("Vulkan compute queue not initialised");
        return v;
    }

    public static int getComputeQueueFamilyIndex() { return computeQueueFamilyIndex; }
    public static String getDeviceName() { return deviceName; }
    public static long getMaxWorkGroupInvocations() { return maxWorkGroupInvocations; }
    public static long getMaxSharedMemoryBytes() { return maxSharedMemoryBytes; }
    public static int[] getMaxWorkGroupSize() { return maxWorkGroupSize.clone(); }

    // =====================================================================
    //  Initialisation flow
    // =====================================================================

    private static boolean findAndLoadVulkanLibrary() {
        try {
            Class.forName("org.lwjgl.vulkan.VK10");
            return true; // LWJGL Vulkan already loadable
        } catch (ClassNotFoundException ignored) { }

        String os = System.getProperty("os.name", "").toLowerCase();
        String libName;
        if (os.contains("win")) libName = "vulkan-1.dll";
        else if (os.contains("linux")) libName = "libvulkan.so.1";
        else if (os.contains("mac")) libName = "libvulkan.1.dylib";
        else return false;

        for (String base : new String[]{ System.getenv("VULKAN_SDK"), System.getenv("VK_SDK_PATH"), "C:/VulkanSDK" }) {
            if (base == null || base.isEmpty()) continue;
            File found = searchForFile(base, libName);
            if (found != null) {
                System.setProperty("org.lwjgl.vulkan.library.name", found.getAbsolutePath());
                LOGGER.debug("Using Vulkan library at {}", found.getAbsolutePath());
                return true;
            }
        }
        // Let LWJGL try default lookup
        try { Class.forName("org.lwjgl.vulkan.VK10"); return true; } catch (Throwable ignored) { }
        return false;
    }

    private static Object createInstance() throws Exception {
        Class<?> appInfoClass = Class.forName("org.lwjgl.vulkan.VkApplicationInfo");
        Object appInfo = appInfoClass.getMethod("calloc").invoke(null);
        setStructInt(appInfo, "sType", vkConstant("VK_STRUCTURE_TYPE_APPLICATION_INFO", 0));
        setStructInt(appInfo, "apiVersion", makeApiVersion(1, 2, 0));

        Class<?> ciClass = Class.forName("org.lwjgl.vulkan.VkInstanceCreateInfo");
        Object createInfo = ciClass.getMethod("calloc").invoke(null);
        setStructInt(createInfo, "sType", vkConstant("VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO", 1));
        setStructField(createInfo, "pApplicationInfo", appInfo);

        Object pInstance = memAllocLong(1);
        try {
            Method vkCreate = findVkMethod("vkCreateInstance", 3);
            int result = (int) vkCreate.invoke(null, createInfo, 0L, pInstance);
            if (result != 0) { LOGGER.info("vkCreateInstance returned {}", result); return null; }
            long handle = readLong(pInstance, 0);
            Class<?> vkInstClass = Class.forName("org.lwjgl.vulkan.VkInstance");
            return vkInstClass.getMethod("create", long.class).invoke(null, handle);
        } finally {
            memFree(pInstance);
            tryFree(createInfo); tryFree(appInfo);
        }
    }

    private static long selectPhysicalDevice(long instance) throws Exception {
        Object pCount = memAllocInt(1);
        try {
            Method enumDevs = findVkMethod("vkEnumeratePhysicalDevices", 3);
            int result = (int) enumDevs.invoke(null, instance, pCount, 0L);
            if (result != 0) return 0L;
            int count = readInt(pCount, 0);
            if (count == 0) return 0L;

            Object pDevices = memAllocLong(count);
            try {
                result = (int) enumDevs.invoke(null, instance, pCount, pDevices);
                if (result != 0) return 0L;

                long bestDevice = 0L;
                int bestScore = -1;
                Class<?> propsClass = Class.forName("org.lwjgl.vulkan.VkPhysicalDeviceProperties");
                Object props = propsClass.getMethod("calloc").invoke(null);

                for (int i = 0; i < count; i++) {
                    long dev = readLong(pDevices, i);
                    if (findComputeQueueFamily(instance, dev) < 0) continue;
                    invokeVk("vkGetPhysicalDeviceProperties", dev, props);
                    int type = getIntField(props, "deviceType");
                    int score = scoreDevice(type);
                    if (score > bestScore) { bestScore = score; bestDevice = dev; }
                }
                tryFree(props);
                return bestDevice;
            } finally { memFree(pDevices); }
        } finally { memFree(pCount); }
    }

    private static int scoreDevice(int type) {
        if (type == vkConstant("VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU", 1)) return 100;
        if (type == vkConstant("VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU", 2)) return 50;
        return 10;
    }

    private static int findComputeQueueFamily(long instance, long physicalDevice) throws Exception {
        Object pCount = memAllocInt(1);
        try {
            invokeVk("vkGetPhysicalDeviceQueueFamilyProperties", physicalDevice, pCount, 0L);
            int count = readInt(pCount, 0);
            if (count == 0) return -1;

            Class<?> qfpClass = Class.forName("org.lwjgl.vulkan.VkQueueFamilyProperties");
            Object qfpBuffer = qfpClass.getMethod("calloc", int.class).invoke(null, count);
            try {
                invokeVk("vkGetPhysicalDeviceQueueFamilyProperties", physicalDevice, pCount, qfpBuffer);
                int COMPUTE_BIT = vkConstant("VK_QUEUE_COMPUTE_BIT", 0x00000002);
                Method getMethod = qfpBuffer.getClass().getMethod("get", int.class);

                for (int i = 0; i < count; i++) {
                    Object props = getMethod.invoke(qfpBuffer, i);
                    int flags = getIntField(props, "queueFlags");
                    int qCount = getIntField(props, "queueCount");
                    if ((flags & COMPUTE_BIT) != 0 && qCount > 0) return i;
                }
                return -1;
            } finally { tryFree(qfpBuffer); }
        } finally { memFree(pCount); }
    }

    private static long createLogicalDevice(long physicalDevice, int queueFamily) throws Exception {
        Class<?> qciClass = Class.forName("org.lwjgl.vulkan.VkDeviceQueueCreateInfo");
        Object qci = qciClass.getMethod("calloc").invoke(null);
        setStructInt(qci, "sType", vkConstant("VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO", 2));
        setStructInt(qci, "queueFamilyIndex", queueFamily);
        Object pPriority = memAllocFloat(1);
        putFloat(pPriority, 0, 1.0f);
        setStructField(qci, "pQueuePriorities", pPriority);

        Class<?> dciClass = Class.forName("org.lwjgl.vulkan.VkDeviceCreateInfo");
        Object dci = dciClass.getMethod("calloc").invoke(null);
        setStructInt(dci, "sType", vkConstant("VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO", 3));
        setStructField(dci, "pQueueCreateInfos", qci);
        setStructInt(dci, "queueCreateInfoCount", 1);

        Object pDevice = memAllocLong(1);
        try {
            int result = (int) findVkMethod("vkCreateDevice", 4).invoke(null, physicalDevice, dci, 0L, pDevice);
            if (result != 0) { LOGGER.info("vkCreateDevice returned {}", result); return 0L; }
            return readLong(pDevice, 0);
        } finally {
            memFree(pDevice); memFree(pPriority);
            tryFree(dci); tryFree(qci);
        }
    }

    private static Object getDeviceQueue(long device, int queueFamily, int index) throws Exception {
        Object pQueue = memAllocLong(1);
        try {
            invokeVk("vkGetDeviceQueue", device, queueFamily, index, pQueue);
            return readLong(pQueue, 0);
        } finally { memFree(pQueue); }
    }

    private static void queryDeviceProperties(long physicalDevice) throws Exception {
        Class<?> propsClass = Class.forName("org.lwjgl.vulkan.VkPhysicalDeviceProperties");
        Object props = propsClass.getMethod("calloc").invoke(null);
        try {
            invokeVk("vkGetPhysicalDeviceProperties", physicalDevice, props);
            deviceName = getStringField(props, "deviceName");
            deviceType = getIntField(props, "deviceType");
            Object limits = propsClass.getMethod("limits").invoke(props);
            maxWorkGroupInvocations = getLongField(limits, "maxComputeWorkGroupInvocations");
            maxSharedMemoryBytes = getLongField(limits, "maxComputeSharedMemorySize");
            Object wgSize = getFieldObject(limits, "maxComputeWorkGroupSize");
            if (wgSize != null) {
                maxWorkGroupSize = new int[]{
                        getIntField(wgSize, "width"), getIntField(wgSize, "height"), getIntField(wgSize, "depth")
                };
            }
        } finally { tryFree(props); }
    }

    private static void destroyInstance(long handle) {
        try { invokeVk("vkDestroyInstance", handle, 0L); } catch (Exception e) { LOGGER.debug("Error destroying instance", e); }
    }

    // =====================================================================
    //  Reflection helpers
    // =====================================================================

    /** Finds a method in VK10..VK13 by name and parameter count. */
    static Method findVkMethod(String name, int paramCount) throws NoSuchMethodException {
        for (String cls : VK_CLASSES) {
            try {
                Class<?> vkClass = Class.forName(cls);
                for (Method m : vkClass.getMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == paramCount) return m;
                }
            } catch (ClassNotFoundException ignored) { }
        }
        throw new NoSuchMethodException("Vulkan method " + name + " with " + paramCount + " params not found");
    }

    /** Finds a native (n-prefix) method in VK10..VK13. */
    static Method findNativeVkMethod(String name, int paramCount) throws NoSuchMethodException {
        return findVkMethod("n" + name, paramCount);
    }

    private static Object invokeVk(String name, Object... args) throws Exception {
        return findVkMethod(name, args.length).invoke(null, args);
    }

    static long getAddress(Object struct) throws Exception {
        return (long) struct.getClass().getMethod("address").invoke(struct);
    }

    static Object wrapCommandBuffer(long address) throws Exception {
        Class<?> cls = Class.forName("org.lwjgl.vulkan.VkCommandBuffer");
        return cls.getMethod("create", long.class).invoke(null, address);
    }

    static long memAddress(Object buffer) throws Exception {
        Class<?> mu = Class.forName("org.lwjgl.system.MemoryUtil");
        return (long) mu.getMethod("memAddress", buffer.getClass()).invoke(null, buffer);
    }

    static void safeSetAccessible(AccessibleObject obj, String context) {
        try { obj.setAccessible(true); } catch (Exception e) { LOGGER.debug("Could not setAccessible on {}", context); }
    }

    // ---- MemoryUtil wrappers -------------------------------------------

    private static Object memAllocLong(int n) throws Exception { return invokeMemUtil("memAllocLong", n); }
    private static Object memAllocInt(int n) throws Exception { return invokeMemUtil("memAllocInt", n); }
    private static Object memAllocFloat(int n) throws Exception { return invokeMemUtil("memAllocFloat", n); }
    private static void memFree(Object buf) throws Exception { invokeMemUtil("memFree", buf); }

    private static long readLong(Object buf, int i) throws Exception {
        return (long) buf.getClass().getMethod("get", int.class).invoke(buf, i);
    }

    private static int readInt(Object buf, int i) throws Exception {
        return (int) buf.getClass().getMethod("get", int.class).invoke(buf, i);
    }

    private static void putFloat(Object buf, int i, float v) throws Exception {
        buf.getClass().getMethod("put", int.class, float.class).invoke(buf, i, v);
    }

    private static Object invokeMemUtil(String name, Object... args) throws Exception {
        Class<?> mu = Class.forName("org.lwjgl.system.MemoryUtil");
        for (Method m : mu.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) return m.invoke(null, args);
        }
        throw new NoSuchMethodException("MemoryUtil." + name + " not found");
    }

    private static void tryFree(Object obj) {
        try { obj.getClass().getMethod("free").invoke(obj); } catch (Exception ignored) { }
    }

    // ---- Struct field access -------------------------------------------

    private static void setStructInt(Object struct, String field, int value) throws Exception {
        try {
            struct.getClass().getMethod(field, int.class).invoke(struct, value);
        } catch (NoSuchMethodException e) {
            Field f = findField(struct.getClass(), field);
            safeSetAccessible(f, field);
            f.setInt(struct, value);
        }
    }

    static void setStructField(Object struct, String field, Object value) throws Exception {
        try {
            struct.getClass().getMethod(field, value.getClass()).invoke(struct, value);
        } catch (NoSuchMethodException e) {
            Field f = findField(struct.getClass(), field);
            safeSetAccessible(f, field);
            f.set(struct, value);
        }
    }

    private static int getIntField(Object obj, String name) throws Exception {
        try {
            String g = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            return (int) obj.getClass().getMethod(g).invoke(obj);
        } catch (NoSuchMethodException e) {
            Field f = findField(obj.getClass(), name);
            safeSetAccessible(f, name);
            return f.getInt(obj);
        }
    }

    private static long getLongField(Object obj, String name) throws Exception {
        try {
            String g = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            return (long) obj.getClass().getMethod(g).invoke(obj);
        } catch (NoSuchMethodException e) {
            Field f = findField(obj.getClass(), name);
            safeSetAccessible(f, name);
            return f.getLong(obj);
        }
    }

    private static String getStringField(Object obj, String name) throws Exception {
        try { return (String) obj.getClass().getMethod(name).invoke(obj); }
        catch (NoSuchMethodException e) { return (String) obj.getClass().getMethod(name + "String").invoke(obj); }
    }

    private static Object getFieldObject(Object obj, String name) throws Exception {
        try { return obj.getClass().getMethod(name).invoke(obj); }
        catch (NoSuchMethodException e) { Field f = findField(obj.getClass(), name); safeSetAccessible(f, name); return f.get(obj); }
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(name + " not found in " + clazz.getName());
    }

    // ---- Vulkan constants via reflection --------------------------------

    private static int vkConstant(String name, int defaultValue) {
        for (String cls : VK_CLASSES) {
            try { return Class.forName(cls).getField(name).getInt(null); }
            catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException ignored) { }
        }
        LOGGER.debug("Vulkan constant {} not found, using default {}", name, defaultValue);
        return defaultValue;
    }

    private static int makeApiVersion(int major, int minor, int patch) {
        int version = (major << 22) | (minor << 12) | patch;
        try {
            Class<?> vk10 = Class.forName("org.lwjgl.vulkan.VK10");
            return (int) vk10.getMethod("VK_API_VERSION", int.class).invoke(null, version);
        } catch (Exception ignored) { return version; }
    }

    // ---- Misc utilities ------------------------------------------------

    private static File searchForFile(String baseDir, String fileName) {
        File root = new File(baseDir);
        if (!root.exists()) return null;
        File[] children = root.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isFile() && child.getName().equals(fileName)) return child;
            if (child.isDirectory()) { File f = searchForFile(child.getAbsolutePath(), fileName); if (f != null) return f; }
        }
        return null;
    }

    private static Long asLong(Object obj) {
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Number) return ((Number) obj).longValue();
        return null;
    }
}
