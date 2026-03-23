/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.resources.ResourceLocation
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.axalotl.async.common.config;

import com.axalotl.async.common.commands.AsyncCommand;
import com.axalotl.async.common.parallelised.utils.ModCompatibility;
import com.axalotl.async.common.platform.PlatformUtils;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncConfig {
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"Async Config");
    public static Map.Entry<String, Boolean> disabled = new AbstractMap.SimpleEntry<String, Boolean>("disabled", false);
    public static Map.Entry<String, Integer> maxThreads = new AbstractMap.SimpleEntry<String, Integer>("paraMax", -1);
    public static Map.Entry<String, Boolean> enableAsyncSpawn = new AbstractMap.SimpleEntry<String, Boolean>("enableAsyncSpawn", true);
    public static Map.Entry<String, Boolean> enableAsyncRandomTicks = new AbstractMap.SimpleEntry<String, Boolean>("enableAsyncRandomTicks", false);
    public static Map.Entry<String, Set<String>> synchronizedEntities = new AbstractMap.SimpleEntry<String, Set<String>>("synchronizedEntities", AsyncConfig.getDefaultSynchronizedEntities());
    private static final Map<ResourceLocation, Boolean> syncCache = new ConcurrentHashMap<ResourceLocation, Boolean>();
    private static final Set<String> exactEntities = new HashSet<String>();
    private static final Set<String> namespaceWildcards = new HashSet<String>();

    public static Set<String> getDefaultSynchronizedEntities() {
        HashSet<String> defaultSynchronizedEntities = new HashSet<String>(ModCompatibility.addUnsupportedMods());
        defaultSynchronizedEntities.addAll(Set.of("minecraft:tnt", "minecraft:item", "minecraft:experience_orb", "minecraft:creeper", "minecraft:wither", "minecraft:end_crystal", "minecraft:ghast"));
        return defaultSynchronizedEntities;
    }

    public static int getParallelism() {
        if (maxThreads.getValue() <= 0) {
            return Runtime.getRuntime().availableProcessors();
        }
        return Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), maxThreads.getValue()));
    }

    public static boolean isNamespaceWildcard(String input) {
        if (input == null) {
            return false;
        }
        int colon = input.indexOf(58);
        if (colon <= 0) {
            return false;
        }
        return input.substring(colon + 1).equals("*");
    }

    public static boolean existsNamespace(String namespace, CommandSourceStack source) {
        for (ResourceLocation id : AsyncCommand.getEntityAccess(source).keySet()) {
            if (!id.getNamespace().equals(namespace)) continue;
            return true;
        }
        return false;
    }

    public static boolean matchesExistingNamespaceWildcard(String input, CommandSourceStack source) {
        if (!AsyncConfig.isNamespaceWildcard(input)) {
            return false;
        }
        String ns = input.substring(0, input.indexOf(58));
        return AsyncConfig.existsNamespace(ns, source);
    }

    public static void syncEntity(String entity) {
        if (synchronizedEntities.getValue().add(entity)) {
            AsyncConfig.rebuildCaches();
            PlatformUtils.saveConfig();
            LOGGER.info("Added sync entity: {}", (Object)entity);
        } else {
            LOGGER.warn("Entity already synchronized: {}", (Object)entity);
        }
    }

    public static void removeEntity(String entity) {
        if (synchronizedEntities.getValue().remove(entity)) {
            AsyncConfig.rebuildCaches();
            PlatformUtils.saveConfig();
            LOGGER.info("Removed sync entity: {}", (Object)entity);
        } else {
            LOGGER.warn("Entity not found: {}", (Object)entity);
        }
    }

    private static void rebuildCaches() {
        syncCache.clear();
        exactEntities.clear();
        namespaceWildcards.clear();
        for (String entry : synchronizedEntities.getValue()) {
            if (AsyncConfig.isNamespaceWildcard(entry)) {
                String ns = entry.substring(0, entry.indexOf(58));
                namespaceWildcards.add(ns);
                continue;
            }
            exactEntities.add(entry);
        }
    }

    public static boolean isEntitySynchronized(ResourceLocation entityId) {
        Boolean cached = syncCache.get(entityId);
        if (cached != null) {
            return cached;
        }
        String idString = entityId.toString();
        if (exactEntities.contains(idString)) {
            syncCache.put(entityId, true);
            return true;
        }
        if (namespaceWildcards.contains(entityId.getNamespace())) {
            syncCache.put(entityId, true);
            return true;
        }
        syncCache.put(entityId, false);
        return false;
    }

    public static void onConfigLoaded() {
        AsyncConfig.rebuildCaches();
        LOGGER.info("Configuration loaded.");
    }

    public static void clearCaches() {
        syncCache.clear();
    }
}

