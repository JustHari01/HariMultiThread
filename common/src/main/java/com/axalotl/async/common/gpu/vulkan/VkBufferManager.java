package com.axalotl.async.common.gpu.vulkan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Manages GPU buffers for Vulkan compute shader data transfer.
 * <p>
 * All Vulkan API calls are performed via reflection to avoid a hard dependency
 * on LWJGL Vulkan, preventing {@code NoClassDefFoundError} when the native
 * library is absent.
 */
public final class VkBufferManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(VkBufferManager.class);

    // Cached LWJGL Vulkan class references (null when Vulkan unavailable)
    private static volatile boolean vulkanClassesLoaded = false;
    private static Class<?> vkBufferClass;
    private static Class<?> vkMemoryAllocateInfoClass;
    private static Class<?> vkBufferCreateInfoClass;
    private static Class<?> vkMemoryRequirementsClass;
    private static Class<?> vkMemoryPropertyFlagBitsClass;
    private static Class<?> vkBufferUsageFlagBitsClass;
    private static Class<?> vkMemoryTypeClass;
    private static Class<?> vkPhysicalDeviceMemoryPropertiesClass;
    private static Class<?> longBufferClass;

    private VkBufferManager() {
        // Utility class -- not instantiated
    }

    // ------------------------------------------------------------------ //
    //  Inner record
    // ------------------------------------------------------------------ //

    /**
     * Immutable handle representing a Vulkan buffer together with its
     * backing memory allocation and an optional CPU-side mapped pointer.
     *
     * @param buffer     native VkBuffer handle
     * @param allocation native VkDeviceMemory handle
     * @param size       buffer size in bytes
     * @param mappedPtr  host-visible mapped pointer (0 if unmapped)
     */
    public record BufferHandle(long buffer, long allocation, long size, long mappedPtr) {
    }

    // ------------------------------------------------------------------ //
    //  Class loading helpers
    // ------------------------------------------------------------------ //

    /**
     * Eagerly attempt to resolve the LWJGL Vulkan classes.  Returns
     * {@code true} if all required classes were found.
     */
    public static synchronized boolean ensureClassesLoaded() {
        if (vulkanClassesLoaded) return true;

        try {
            vkBufferClass = Class.forName("org.lwjgl.vulkan.VkBuffer");
            vkMemoryAllocateInfoClass = Class.forName("org.lwjgl.vulkan.VkMemoryAllocateInfo");
            vkBufferCreateInfoClass = Class.forName("org.lwjgl.vulkan.VkBufferCreateInfo");
            vkMemoryRequirementsClass = Class.forName("org.lwjgl.vulkan.VkMemoryRequirements");
            vkMemoryPropertyFlagBitsClass = Class.forName("org.lwjgl.vulkan.VkMemoryPropertyFlagBits");
            vkBufferUsageFlagBitsClass = Class.forName("org.lwjgl.vulkan.VkBufferUsageFlagBits");
            vkMemoryTypeClass = Class.forName("org.lwjgl.vulkan.VkMemoryType");
            vkPhysicalDeviceMemoryPropertiesClass = Class.forName("org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties");
            longBufferClass = Class.forName("org.lwjgl.system.MemoryUtil"); // for memPutFloat etc.

            vulkanClassesLoaded = true;
            return true;
        } catch (ClassNotFoundException e) {
            LOGGER.warn("LWJGL Vulkan classes not available - GPU buffer operations disabled", e);
            return false;
        }
    }

    // ------------------------------------------------------------------ //
    //  Buffer creation
    // ------------------------------------------------------------------ //

    /**
     * Creates a host-visible Storage Buffer (SSBO) of the requested size.
     * <p>
     * The buffer is created with {@code VK_BUFFER_USAGE_STORAGE_BUFFER_BIT},
     * allocated from device memory that is host-visible and host-coherent, and
     * then persistently mapped so that CPU-side reads/writes can proceed
     * without explicit map/unmap calls.
     *
     * @param sizeBytes required buffer size in bytes
     * @return a {@link BufferHandle} containing all native handles
     * @throws IllegalStateException if Vulkan is unavailable or creation fails
     */
    public static BufferHandle createHostVisibleSSBO(long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive, got: " + sizeBytes);
        }
        if (!ensureClassesLoaded()) {
            throw new IllegalStateException("Vulkan classes not available; cannot create SSBO");
        }

        try {
            long device = VkDeviceManager.getDevice();
            long physicalDevice = VkDeviceManager.getPhysicalDevice();

            // -- Create VkBuffer --------------------------------------------------
            Object bufferCreateInfo = createBufferCreateInfo(sizeBytes);
            long bufferHandle = createBuffer(device, bufferCreateInfo);

            // -- Query memory requirements -----------------------------------------
            Object memReqs = getMemoryRequirements(device, bufferHandle);

            // -- Find suitable memory type -----------------------------------------
            int memoryTypeIndex = findMemoryType(
                    physicalDevice,
                    memReqs,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT() | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT()
            );

            // -- Allocate memory ---------------------------------------------------
            long allocSize = getMemoryRequirementsSize(memReqs);
            long allocation = allocateMemory(device, allocSize, memoryTypeIndex);

            // -- Bind memory to buffer ---------------------------------------------
            bindBufferMemory(device, bufferHandle, allocation);

            // -- Map memory for CPU access -----------------------------------------
            long mappedPtr = mapMemory(device, allocation, allocSize);

            LOGGER.debug("Created host-visible SSBO: buffer={}, allocation={}, size={}, mapped={}",
                    bufferHandle, allocation, sizeBytes, mappedPtr);

            return new BufferHandle(bufferHandle, allocation, sizeBytes, mappedPtr);

        } catch (Exception e) {
            LOGGER.error("Failed to create host-visible SSBO of {} bytes", sizeBytes, e);
            throw new IllegalStateException("SSBO creation failed", e);
        }
    }

    // ------------------------------------------------------------------ //
    //  Upload / Download -- floats
    // ------------------------------------------------------------------ //

    /**
     * Copies {@code count} floats from {@code data} starting at {@code offset}
     * into the mapped region of the given buffer handle.
     *
     * @param handle the buffer handle returned by {@link #createHostVisibleSSBO}
     * @param data   source float array
     * @param offset starting index in {@code data}
     * @param count  number of floats to copy
     */
    public static void uploadFloats(BufferHandle handle, float[] data, int offset, int count) {
        if (handle == null || handle.mappedPtr() == 0) {
            LOGGER.warn("uploadFloats called with null or unmapped handle");
            return;
        }
        validateArrayBounds(data, offset, count);
        validateBufferSize(handle, (long) count * Float.BYTES);

        try {
            ByteBuffer bb = newDirectByteBuffer(handle.mappedPtr(), (long) count * Float.BYTES);
            FloatBuffer fb = bb.order(ByteOrder.nativeOrder()).asFloatBuffer();
            fb.put(data, offset, count);
        } catch (Exception e) {
            LOGGER.error("Failed to upload {} floats to buffer {}", count, handle.buffer(), e);
        }
    }

    /**
     * Reads {@code count} floats from the mapped region of the given buffer
     * into {@code output} starting at {@code outputOffset}.
     *
     * @param handle       the buffer handle
     * @param output       destination float array
     * @param outputOffset starting index in {@code output}
     * @param count        number of floats to read
     */
    public static void downloadFloats(BufferHandle handle, float[] output, int outputOffset, int count) {
        if (handle == null || handle.mappedPtr() == 0) {
            LOGGER.warn("downloadFloats called with null or unmapped handle");
            return;
        }
        validateArrayBounds(output, outputOffset, count);
        validateBufferSize(handle, (long) count * Float.BYTES);

        try {
            ByteBuffer bb = newDirectByteBuffer(handle.mappedPtr(), (long) count * Float.BYTES);
            FloatBuffer fb = bb.order(ByteOrder.nativeOrder()).asFloatBuffer();
            fb.get(output, outputOffset, count);
        } catch (Exception e) {
            LOGGER.error("Failed to download {} floats from buffer {}", count, handle.buffer(), e);
        }
    }

    // ------------------------------------------------------------------ //
    //  Upload / Download -- ints
    // ------------------------------------------------------------------ //

    /**
     * Copies {@code count} ints from {@code data} starting at {@code offset}
     * into the mapped region of the given buffer handle.
     *
     * @param handle the buffer handle
     * @param data   source int array
     * @param offset starting index in {@code data}
     * @param count  number of ints to copy
     */
    public static void uploadInts(BufferHandle handle, int[] data, int offset, int count) {
        if (handle == null || handle.mappedPtr() == 0) {
            LOGGER.warn("uploadInts called with null or unmapped handle");
            return;
        }
        validateArrayBounds(data, offset, count);
        validateBufferSize(handle, (long) count * Integer.BYTES);

        try {
            ByteBuffer bb = newDirectByteBuffer(handle.mappedPtr(), (long) count * Integer.BYTES);
            IntBuffer ib = bb.order(ByteOrder.nativeOrder()).asIntBuffer();
            ib.put(data, offset, count);
        } catch (Exception e) {
            LOGGER.error("Failed to upload {} ints to buffer {}", count, handle.buffer(), e);
        }
    }

    /**
     * Reads {@code count} ints from the mapped region of the given buffer
     * into {@code output} starting at {@code outputOffset}.
     *
     * @param handle       the buffer handle
     * @param output       destination int array
     * @param outputOffset starting index in {@code output}
     * @param count        number of ints to read
     */
    public static void downloadInts(BufferHandle handle, int[] output, int outputOffset, int count) {
        if (handle == null || handle.mappedPtr() == 0) {
            LOGGER.warn("downloadInts called with null or unmapped handle");
            return;
        }
        validateArrayBounds(output, outputOffset, count);
        validateBufferSize(handle, (long) count * Integer.BYTES);

        try {
            ByteBuffer bb = newDirectByteBuffer(handle.mappedPtr(), (long) count * Integer.BYTES);
            IntBuffer ib = bb.order(ByteOrder.nativeOrder()).asIntBuffer();
            ib.get(output, outputOffset, count);
        } catch (Exception e) {
            LOGGER.error("Failed to download {} ints from buffer {}", count, handle.buffer(), e);
        }
    }

    // ------------------------------------------------------------------ //
    //  Buffer destruction
    // ------------------------------------------------------------------ //

    /**
     * Destroys a previously created buffer, freeing its device memory and
     * unmapping any mapped pointer.  Null-safe; passing {@code null} is a
     * no-op.
     *
     * @param handle the buffer handle to destroy (may be {@code null})
     */
    public static void destroyBuffer(BufferHandle handle) {
        if (handle == null) {
            return;
        }

        try {
            long device = VkDeviceManager.getDevice();

            // Unmap if mapped
            if (handle.mappedPtr() != 0) {
                invokeVulkanDeviceMethod("vkUnmapMemory", device, handle.allocation());
            }

            // Free device memory
            invokeVulkanDeviceMethod("vkFreeMemory", device, handle.allocation(), 0L);

            // Destroy buffer
            invokeVulkanDeviceMethod("vkDestroyBuffer", device, handle.buffer(), 0L);

            LOGGER.debug("Destroyed buffer: buffer={}, allocation={}", handle.buffer(), handle.allocation());
        } catch (Exception e) {
            LOGGER.error("Failed to destroy buffer handle {}", handle, e);
        }
    }

    // ================================================================== //
    //  Private helpers -- reflection wrappers
    // ================================================================== //

    /**
     * Creates a {@code VkBufferCreateInfo} via reflection.
     */
    private static Object createBufferCreateInfo(long sizeBytes) throws Exception {
        // VkBufferCreateInfo.calloc()
        Method callocMethod = vkBufferCreateInfoClass.getMethod("calloc");
        Object createInfo = callocMethod.invoke(null);

        // set sType -> VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO
        setIntField(createInfo, "sType", VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO());

        // set size
        setLongField(createInfo, "size", sizeBytes);

        // set usage -> VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
        setIntField(createInfo, "usage", VK_BUFFER_USAGE_STORAGE_BUFFER_BIT());

        // set sharingMode -> VK_SHARING_MODE_EXCLUSIVE (0)
        setIntField(createInfo, "sharingMode", 0);

        return createInfo;
    }

    /**
     * Calls {@code vkCreateBuffer} via reflection on the {@link VkDeviceManager}.
     */
    private static long createBuffer(long device, Object createInfo) throws Exception {
        // Allocate a LongBuffer to receive the buffer handle
        Object pBuffer = invokeMemoryUtil("memAllocLong", 1);
        try {
            Method vkCreateBuffer = findVulkanMethod("vkCreateBuffer", long.class, createInfo.getClass(), pBuffer.getClass());
            int result = (int) vkCreateBuffer.invoke(null, device, createInfo, 0L, pBuffer);
            if (result != 0) { // VK_SUCCESS = 0
                throw new RuntimeException("vkCreateBuffer returned " + result);
            }
            // Read the long value from the LongBuffer
            Method getMethod = pBuffer.getClass().getMethod("get", int.class);
            return (long) getMethod.invoke(pBuffer, 0);
        } finally {
            invokeMemoryUtil("memFree", pBuffer);
        }
    }

    /**
     * Calls {@code vkGetBufferMemoryRequirements} via reflection.
     */
    private static Object getMemoryRequirements(long device, long buffer) throws Exception {
        Object memReqs = vkMemoryRequirementsClass.getMethod("calloc").invoke(null);
        Method method = findVulkanMethod("vkGetBufferMemoryRequirements", long.class, long.class, memReqs.getClass());
        method.invoke(null, device, buffer, memReqs);
        return memReqs;
    }

    /**
     * Reads the {@code size} field from a {@code VkMemoryRequirements} object.
     */
    private static long getMemoryRequirementsSize(Object memReqs) throws Exception {
        return getLongField(memReqs, "size");
    }

    /**
     * Finds a suitable memory type index that satisfies the requested property flags.
     */
    private static int findMemoryType(long physicalDevice, Object memReqs, int requiredProperties) throws Exception {
        Object memProps = vkPhysicalDeviceMemoryPropertiesClass.getMethod("calloc").invoke(null);
        try {
            Method getMemProps = findVulkanMethod("vkGetPhysicalDeviceMemoryProperties", long.class, memProps.getClass());
            getMemProps.invoke(null, physicalDevice, memProps);

            int memoryTypeBits = getIntField(memReqs, "memoryTypeBits");
            int memoryTypeCount = getIntField(memProps, "memoryTypeCount");

            // Access the memoryTypes array -- represented as a VkMemoryType.Buffer in LWJGL
            Object memoryTypesBuffer = memProps.getClass().getMethod("memoryTypes").invoke(memProps);

            for (int i = 0; i < memoryTypeCount; i++) {
                if ((memoryTypeBits & (1 << i)) != 0) {
                    // Get VkMemoryType at index i
                    Method getMethod = memoryTypesBuffer.getClass().getMethod("get", int.class);
                    Object memoryType = getMethod.invoke(memoryTypesBuffer, i);

                    int propertyFlags = getIntField(memoryType, "propertyFlags");
                    if ((propertyFlags & requiredProperties) == requiredProperties) {
                        return i;
                    }
                }
            }

            throw new RuntimeException("Failed to find suitable memory type with requiredProperties=" + requiredProperties);
        } finally {
            try {
                memProps.getClass().getMethod("free").invoke(memProps);
            } catch (Exception ignored) {
                // Best-effort cleanup
            }
        }
    }

    /**
     * Allocates device memory via {@code vkAllocateMemory}.
     */
    private static long allocateMemory(long device, long allocSize, int memoryTypeIndex) throws Exception {
        // VkMemoryAllocateInfo.calloc()
        Object allocInfo = vkMemoryAllocateInfoClass.getMethod("calloc").invoke(null);

        // set sType
        setIntField(allocInfo, "sType", VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO());
        // set allocationSize
        setLongField(allocInfo, "allocationSize", allocSize);
        // set memoryTypeIndex
        setIntField(allocInfo, "memoryTypeIndex", memoryTypeIndex);

        Object pMemory = invokeMemoryUtil("memAllocLong", 1);
        try {
            Method vkAllocateMemory = findVulkanMethod("vkAllocateMemory", long.class, allocInfo.getClass(), pMemory.getClass());
            int result = (int) vkAllocateMemory.invoke(null, device, allocInfo, 0L, pMemory);
            if (result != 0) {
                throw new RuntimeException("vkAllocateMemory returned " + result);
            }
            Method getMethod = pMemory.getClass().getMethod("get", int.class);
            return (long) getMethod.invoke(pMemory, 0);
        } finally {
            invokeMemoryUtil("memFree", pMemory);
            try {
                allocInfo.getClass().getMethod("free").invoke(allocInfo);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Binds allocated memory to a buffer via {@code vkBindBufferMemory}.
     */
    private static void bindBufferMemory(long device, long buffer, long allocation) throws Exception {
        Method method = findVulkanMethod("vkBindBufferMemory", long.class, long.class, long.class, long.class);
        int result = (int) method.invoke(null, device, buffer, allocation, 0L);
        if (result != 0) {
            throw new RuntimeException("vkBindBufferMemory returned " + result);
        }
    }

    /**
     * Maps device memory for CPU access via {@code vkMapMemory}.
     */
    private static long mapMemory(long device, long allocation, long size) throws Exception {
        Object ppData = invokeMemoryUtil("memAllocLong", 1);
        try {
            Method vkMapMemory = findVulkanMethod("vkMapMemory", long.class, long.class, long.class, long.class, int.class, ppData.getClass());
            int result = (int) vkMapMemory.invoke(null, device, allocation, 0L, size, 0, ppData);
            if (result != 0) {
                throw new RuntimeException("vkMapMemory returned " + result);
            }
            Method getMethod = ppData.getClass().getMethod("get", int.class);
            return (long) getMethod.invoke(ppData, 0);
        } finally {
            invokeMemoryUtil("memFree", ppData);
        }
    }

    /**
     * Invokes a {@code VK10} / {@code VK11} static method that takes (device, handle, pAllocator) parameters.
     * Used for destroy/free operations that require a third allocator parameter (usually 0L).
     */
    private static void invokeVulkanDeviceMethod(String methodName, long device, long handle, long allocator) throws Exception {
        try {
            // Three-arg variant (device, handle, pAllocator)
            Method method = tryFindVulkanMethod(methodName, long.class, long.class, long.class);
            if (method != null) {
                method.invoke(null, device, handle, allocator);
                return;
            }
            LOGGER.warn("Could not find Vulkan method {} for device cleanup", methodName);
        } catch (Exception e) {
            LOGGER.debug("Vulkan method {} invocation failed (non-fatal during cleanup)", methodName, e);
        }
    }

    /**
     * Invokes a {@code VK10} / {@code VK11} static method that takes (device, ...) parameters.
     * Used for destroy/free/unmap operations where we only need a void return.
     */
    private static void invokeVulkanDeviceMethod(String methodName, long device, long handle) throws Exception {
        try {
            // Try VK10 first, then VK11, then VK12
            Method method = tryFindVulkanMethod(methodName, long.class, long.class, long.class);
            if (method != null) {
                method.invoke(null, device, handle, 0L);
                return;
            }
            // Two-arg variant (e.g. vkUnmapMemory)
            method = tryFindVulkanMethod(methodName, long.class, long.class);
            if (method != null) {
                method.invoke(null, device, handle);
                return;
            }
            LOGGER.warn("Could not find Vulkan method {} for device cleanup", methodName);
        } catch (Exception e) {
            LOGGER.debug("Vulkan method {} invocation failed (non-fatal during cleanup)", methodName, e);
        }
    }

    /**
     * Searches VK10, VK11, VK12 classes for a static method.
     */
    private static Method findVulkanMethod(String name, Class<?>... paramTypes) throws NoSuchMethodException {
        Method m = tryFindVulkanMethod(name, paramTypes);
        if (m == null) {
            throw new NoSuchMethodException("Vulkan method " + name + " not found in VK10/VK11/VK12");
        }
        return m;
    }

    /**
     * Tries to locate a Vulkan static method across VK10, VK11, VK12.
     */
    private static Method tryFindVulkanMethod(String name, Class<?>... paramTypes) {
        for (String className : new String[]{
                "org.lwjgl.vulkan.VK10",
                "org.lwjgl.vulkan.VK11",
                "org.lwjgl.vulkan.VK12",
                "org.lwjgl.vulkan.VK13"
        }) {
            try {
                Class<?> vkClass = Class.forName(className);
                return vkClass.getMethod(name, paramTypes);
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                // try next
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    //  Vulkan constant accessors (via reflection on VK10)
    // ------------------------------------------------------------------ //

    private static int VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO() {
        return getVulkanIntConstant("VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO", 17);
    }

    private static int VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO() {
        return getVulkanIntConstant("VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO", 5);
    }

    private static int VK_BUFFER_USAGE_STORAGE_BUFFER_BIT() {
        return getVulkanIntConstant("VK_BUFFER_USAGE_STORAGE_BUFFER_BIT", 0x00000020);
    }

    private static int VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT() {
        return getVulkanIntConstant("VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT", 0x00000002);
    }

    private static int VK_MEMORY_PROPERTY_HOST_COHERENT_BIT() {
        return getVulkanIntConstant("VK_MEMORY_PROPERTY_HOST_COHERENT_BIT", 0x00000004);
    }

    /**
     * Reads an int constant from VK10 (or any VK version class).  Falls back
     * to {@code defaultValue} when the class or field is unavailable.
     */
    private static int getVulkanIntConstant(String name, int defaultValue) {
        for (String className : new String[]{
                "org.lwjgl.vulkan.VK10",
                "org.lwjgl.vulkan.VK11",
                "org.lwjgl.vulkan.VK12",
                "org.lwjgl.vulkan.VK13"
        }) {
            try {
                Class<?> vkClass = Class.forName(className);
                java.lang.reflect.Field field = vkClass.getField(name);
                return field.getInt(null);
            } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException ignored) {
                // try next
            }
        }
        LOGGER.debug("Vulkan constant {} not found via reflection, using default {}", name, defaultValue);
        return defaultValue;
    }

    // ------------------------------------------------------------------ //
    //  MemoryUtil helpers
    // ------------------------------------------------------------------ //

    /**
     * Calls a static method on {@code org.lwjgl.system.MemoryUtil} by name.
     */
    private static Object invokeMemoryUtil(String methodName, Object... args) throws Exception {
        Class<?> memUtilClass = Class.forName("org.lwjgl.system.MemoryUtil");
        for (Method m : memUtilClass.getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                return m.invoke(null, args);
            }
        }
        throw new NoSuchMethodException("MemoryUtil." + methodName + " with " + args.length + " args not found");
    }

    /**
     * Wraps a native pointer as a direct {@link ByteBuffer} using
     * LWJGL's {@code MemoryUtil.memByteBuffer} via reflection.
     */
    private static ByteBuffer newDirectByteBuffer(long address, long capacity) throws Exception {
        Class<?> memUtilClass = Class.forName("org.lwjgl.system.MemoryUtil");
        Method method = memUtilClass.getMethod("memByteBuffer", long.class, int.class);
        return (ByteBuffer) method.invoke(null, address, (int) capacity);
    }

    // ------------------------------------------------------------------ //
    //  Field access helpers
    // ------------------------------------------------------------------ //

    private static void setIntField(Object obj, String fieldName, int value) throws Exception {
        try {
            java.lang.reflect.Field field = findField(obj.getClass(), fieldName);
            field.setAccessible(true);
            field.setInt(obj, value);
        } catch (NoSuchFieldException e) {
            // LWJGL structs expose setter methods; try set<Name>(int)
            String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            try {
                Method setter = obj.getClass().getMethod(setterName, int.class);
                setter.invoke(obj, value);
            } catch (NoSuchMethodException ex) {
                throw new RuntimeException("Cannot set field " + fieldName + " on " + obj.getClass().getName(), ex);
            }
        }
    }

    private static void setLongField(Object obj, String fieldName, long value) throws Exception {
        try {
            java.lang.reflect.Field field = findField(obj.getClass(), fieldName);
            field.setAccessible(true);
            field.setLong(obj, value);
        } catch (NoSuchFieldException e) {
            String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            try {
                Method setter = obj.getClass().getMethod(setterName, long.class);
                setter.invoke(obj, value);
            } catch (NoSuchMethodException ex) {
                throw new RuntimeException("Cannot set field " + fieldName + " on " + obj.getClass().getName(), ex);
            }
        }
    }

    private static long getLongField(Object obj, String fieldName) throws Exception {
        try {
            java.lang.reflect.Field field = findField(obj.getClass(), fieldName);
            field.setAccessible(true);
            return field.getLong(obj);
        } catch (NoSuchFieldException e) {
            // Try getter method
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            try {
                Method getter = obj.getClass().getMethod(getterName);
                return (long) getter.invoke(obj);
            } catch (NoSuchMethodException ex) {
                // Try field access with underscore prefix (LWJGL convention)
                java.lang.reflect.Field field = findField(obj.getClass(), "__" + fieldName);
                field.setAccessible(true);
                return field.getLong(obj);
            }
        }
    }

    private static int getIntField(Object obj, String fieldName) throws Exception {
        try {
            java.lang.reflect.Field field = findField(obj.getClass(), fieldName);
            field.setAccessible(true);
            return field.getInt(obj);
        } catch (NoSuchFieldException e) {
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            try {
                Method getter = obj.getClass().getMethod(getterName);
                return (int) getter.invoke(obj);
            } catch (NoSuchMethodException ex) {
                java.lang.reflect.Field field = findField(obj.getClass(), "__" + fieldName);
                field.setAccessible(true);
                return field.getInt(obj);
            }
        }
    }

    /**
     * Searches the class hierarchy for a declared field with the given name.
     */
    private static java.lang.reflect.Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName + " not found in " + clazz.getName() + " hierarchy");
    }

    // ------------------------------------------------------------------ //
    //  Validation helpers
    // ------------------------------------------------------------------ //

    private static void validateArrayBounds(Object array, int offset, int count) {
        int length = java.lang.reflect.Array.getLength(array);
        if (offset < 0 || count < 0 || offset + count > length) {
            throw new IndexOutOfBoundsException(
                    String.format("offset=%d, count=%d, arrayLength=%d", offset, count, length));
        }
    }

    private static void validateBufferSize(BufferHandle handle, long requiredBytes) {
        if (requiredBytes > handle.size()) {
            throw new IllegalArgumentException(
                    String.format("Requested %d bytes but buffer only has %d bytes", requiredBytes, handle.size()));
        }
    }
}
