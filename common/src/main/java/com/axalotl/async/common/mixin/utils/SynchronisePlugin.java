package com.axalotl.async.common.mixin.utils;

import com.axalotl.async.common.AsyncCommon;
import com.axalotl.async.common.platform.PlatformUtils;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class SynchronisePlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int FINAL_STATIC_PRIVATE_ABSTRACT = 0x1548; // final, static, private, abstract
    private static final int SYNCHRONIZED = 0x20; // synchronized
    private final Multimap<String, String> mixin2MethodsMap = ArrayListMultimap.create();
    private final Multimap<String, String> mixin2MethodsExcludeMap = ArrayListMultimap.create();
    private final TreeSet<String> syncAllSet = new TreeSet<>();

    @Override
    public void onLoad(String mixinPackage) {
        mixin2MethodsExcludeMap.put("com.axalotl.async.common.mixin.utils.SyncAllMixin", "net.minecraft.world.level.chunk.ChunkStatus.isOrAfter");
        syncAllSet.add("com.axalotl.async.common.mixin.utils.FastUtilsMixin");
        syncAllSet.add("com.axalotl.async.common.mixin.utils.SyncAllMixin");
    }

    @Override
    public String getRefMapperConfig() {
        return PlatformUtils.platformUsesRefmap() ? "async.refmap.json" : null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".lithium.RadiumServerLevel")) {
            return AsyncCommon.LITHIUM;
        }
        if (mixinClassName.endsWith(".vmp.VMPChunkMapMixin")) {
            return AsyncCommon.HARIPLAYER;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        Collection<String> targetMethods = mixin2MethodsMap.get(mixinClassName);
        Collection<String> excludedMethods = mixin2MethodsExcludeMap.get(mixinClassName);

        if (!targetMethods.isEmpty()) {
            applySynchronizeBit(targetClass, targetMethods, targetClassName);
        } else if (syncAllSet.contains(mixinClassName)) {
            for (MethodNode method : targetClass.methods) {
                if ((method.access & FINAL_STATIC_PRIVATE_ABSTRACT) == 0 && !method.name.equals("<init>") && !excludedMethods.contains(method.name)) {
                    // When HariChunk/C2ME is loaded, skip adding synchronized to DynamicGraphMinFixedPoint
                    // because C2ME has its own lighting thread management that handles concurrency
                    if (AsyncCommon.HARICHUNK && targetClassName.contains("DynamicGraphMinFixedPoint")) {
                        LOGGER.debug("Skipping synchronized for {} in {} - C2ME manages lighting threads", method.name, targetClassName);
                        continue;
                    }
                    // When Harium is loaded, skip adding synchronized to PalettedContainer's lock/unlock
                    // because Harium already overwrites these to no-op
                    if (AsyncCommon.LITHIUM && targetClassName.contains("PalettedContainer")
                            && (method.name.equals("lock") || method.name.equals("unlock"))) {
                        LOGGER.debug("Skipping synchronized for {} in {} - Harium already handles locking", method.name, targetClassName);
                        continue;
                    }
                    method.access |= SYNCHRONIZED;
                    logSynchronize(method.name, targetClassName, mixinClassName);
                }
            }
        }
    }

    private void applySynchronizeBit(ClassNode targetClass, Collection<String> targetMethods, String targetClassName) {
        for (MethodNode method : targetClass.methods) {
            for (String targetMethod : targetMethods) {
                if (method.name.equals(targetMethod)) {
                    method.access |= SYNCHRONIZED;
                    logSynchronize(method.name, targetClassName, null);
                }
            }
        }
    }

    private void logSynchronize(String methodName, String targetClassName, String mixinClassName) {
        if (mixinClassName == null || !mixinClassName.equals("com.axalotl.async.mixin.utils.FastUtilsMixin")) {
            String message = "Setting synchronize bit for " + methodName + " in " + targetClassName + ".";
            LOGGER.debug(message);
        }
    }
}
