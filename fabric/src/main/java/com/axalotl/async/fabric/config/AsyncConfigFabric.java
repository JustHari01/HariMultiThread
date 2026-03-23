package com.axalotl.async.fabric.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.ArrayList;

import static com.axalotl.async.common.config.AsyncConfig.*;
import static com.mojang.text2speech.Narrator.LOGGER;

public class AsyncConfigFabric {
    private static final Supplier<CommentedFileConfig> configSupplier = () -> CommentedFileConfig
            .builder(FabricLoader.getInstance().getConfigDir().resolve("harimt.toml"))
            .preserveInsertionOrder()
            .sync()
            .build();

    private static CommentedFileConfig CONFIG;

    public static void init() {
        LOGGER.info("Initializing Async Config...");
        CONFIG = configSupplier.get();
        try {
            if (!CONFIG.getFile().exists()) {
                LOGGER.warn("Configuration file not found, creating default configuration.");
                setDefaultValues();
                saveConfig();
            } else {
                CONFIG.load();
                loadConfigValues();
                LOGGER.info("Configuration successfully loaded.");
            }
        } catch (Throwable t) {
            LOGGER.error("Error loading configuration, resetting to default values.", t);
            setDefaultValues();
            saveConfig();
        }
    }

    public static void saveConfig() {
        CONFIG.set("disabled", disabled);
        CONFIG.setComment("disabled",
                "Globally disable all toggleable functionality within the async system. Set to true to stop all asynchronous operations.");

        CONFIG.set("paraMax", maxThreads);
        CONFIG.setComment("paraMax",
                "Maximum number of threads to use for parallel processing. Set to -1 to use default value. Note: If 'virtualThreads' is enabled, this setting will be ignored.");

        CONFIG.set("synchronizedEntities", new ArrayList<>(synchronizedEntities));
        CONFIG.setComment("synchronizedEntities", "List of entity class for sync processing.");

        CONFIG.set("enableAsyncSpawn", enableAsyncSpawn);
        CONFIG.setComment("enableAsyncSpawn",
                "Enables parallel processing of entity spawns. Warning, incompatible with Carpet mod lagFreeSpawning rule.");

        CONFIG.set("enableAsyncRandomTicks", enableAsyncRandomTicks);
        CONFIG.setComment("enableAsyncRandomTicks",
                "Experimental! Enables async random ticks.");

        CONFIG.save();
        LOGGER.info("Configuration saved successfully.");
    }

    private static void loadConfigValues() {
        Set<String> processedKeys = new HashSet<>(List.of(
                "disabled",
                "paraMax",
                "synchronizedEntities",
                "enableAsyncSpawn",
                "enableAsyncRandomTicks"));

        disabled = CONFIG.getOrElse("disabled", disabled);
        maxThreads = CONFIG.getOrElse("paraMax", maxThreads);
        enableAsyncSpawn = CONFIG.getOrElse("enableAsyncSpawn", enableAsyncSpawn);
        enableAsyncRandomTicks = CONFIG.getOrElse("enableAsyncRandomTicks", enableAsyncRandomTicks);

        synchronizedEntities = new HashSet<>();
        CONFIG.<List<String>>getOptional("synchronizedEntities").ifPresentOrElse(ids -> {
            for (String id : ids) {
                synchronizedEntities.add(id);
            }
        }, () -> synchronizedEntities = getDefaultSynchronizedEntities());

        Set<String> keysToRemove = new HashSet<>();
        for (CommentedConfig.Entry entry : CONFIG.entrySet()) {
            String key = entry.getKey();
            if (!processedKeys.contains(key)) {
                keysToRemove.add(key);
            }
        }

        for (String key : keysToRemove) {
            LOGGER.warn("Removing unused configuration key: {}", key);
            CONFIG.remove(key);
        }

        CONFIG.save();
    }

    private static void setDefaultValues() {
        disabled = false;
        maxThreads = -1;
        enableAsyncSpawn = false;
        enableAsyncRandomTicks = false;
        synchronizedEntities = getDefaultSynchronizedEntities();
    }
}
