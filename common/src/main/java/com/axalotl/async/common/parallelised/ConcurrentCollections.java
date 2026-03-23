/*
 * Decompiled with CFR 0.152.
 */
package com.axalotl.async.common.parallelised;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class ConcurrentCollections {
    public static <T> Set<T> newHashSet() {
        return Collections.newSetFromMap(new ConcurrentHashMap());
    }

    public static <T, U> Map<T, U> newHashMap() {
        return new ConcurrentHashMap();
    }

    public static <T> Collector<T, ?, List<T>> toList() {
        return Collectors.toCollection(CopyOnWriteArrayList::new);
    }
}

