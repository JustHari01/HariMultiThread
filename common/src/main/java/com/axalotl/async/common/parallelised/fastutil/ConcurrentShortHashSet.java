/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.shorts.ShortCollection
 *  it.unimi.dsi.fastutil.shorts.ShortIterator
 *  it.unimi.dsi.fastutil.shorts.ShortSet
 *  org.jetbrains.annotations.NotNull
 */
package com.axalotl.async.common.parallelised.fastutil;

import com.axalotl.async.common.parallelised.fastutil.FastUtilHackUtil;
import it.unimi.dsi.fastutil.shorts.ShortCollection;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

public final class ConcurrentShortHashSet
        implements ShortSet {
    private final ConcurrentHashMap.KeySetView<Short, Boolean> backing = ConcurrentHashMap.newKeySet();

    public int size() {
        return this.backing.size();
    }

    public boolean isEmpty() {
        return this.backing.isEmpty();
    }

    @NotNull
    public ShortIterator iterator() {
        return FastUtilHackUtil.itrShortWrap(this.backing);
    }

    @NotNull
    public Object[] toArray() {
        return this.backing.toArray();
    }

    @NotNull
    public <T> T[] toArray(T[] array) {
        Objects.requireNonNull(array, "Array cannot be null");
        return this.backing.toArray(array);
    }

    public boolean containsAll(@NotNull Collection<?> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        return this.backing.containsAll(collection);
    }

    public boolean addAll(@NotNull Collection<? extends Short> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        return this.backing.addAll((Collection<Short>) collection);
    }

    public boolean removeAll(@NotNull Collection<?> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        return this.backing.removeAll((Collection) collection);
    }

    public boolean retainAll(@NotNull Collection<?> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        return this.backing.retainAll(collection);
    }

    public void clear() {
        this.backing.clear();
    }

    public boolean add(short key) {
        return this.backing.add(key);
    }

    public boolean contains(short key) {
        return this.backing.contains(key);
    }

    public short[] toShortArray() {
        int size = this.backing.size();
        short[] result = new short[size];
        int i = 0;
        for (Short value : this.backing) {
            result[i++] = value;
        }
        return result;
    }

    public short[] toArray(short[] array) {
        Objects.requireNonNull(array, "Array cannot be null");
        short[] result = this.toShortArray();
        if (array.length < result.length) {
            return result;
        }
        System.arraycopy(result, 0, array, 0, result.length);
        if (array.length > result.length) {
            array[result.length] = 0;
        }
        return array;
    }

    public boolean addAll(ShortCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        boolean modified = false;
        ShortIterator iterator = c.iterator();
        while (iterator.hasNext()) {
            modified |= this.add(iterator.nextShort());
        }
        return modified;
    }

    public boolean containsAll(ShortCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        ShortIterator iterator = c.iterator();
        while (iterator.hasNext()) {
            if (this.contains(iterator.nextShort()))
                continue;
            return false;
        }
        return true;
    }

    public boolean removeAll(ShortCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        boolean modified = false;
        ShortIterator iterator = c.iterator();
        while (iterator.hasNext()) {
            modified |= this.remove(iterator.nextShort());
        }
        return modified;
    }

    public boolean retainAll(ShortCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        return this.backing.retainAll((Collection<?>) c);
    }

    public boolean remove(short k) {
        return this.backing.remove(k);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ShortSet)) {
            return false;
        }
        ShortSet that = (ShortSet) o;
        if (this.size() != that.size()) {
            return false;
        }
        return this.containsAll((ShortCollection) that);
    }

    public int hashCode() {
        return this.backing.hashCode();
    }

    public String toString() {
        return this.backing.toString();
    }
}
