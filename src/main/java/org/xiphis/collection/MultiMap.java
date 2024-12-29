package org.xiphis.collection;

import java.util.Collection;
import java.util.Map;

public interface MultiMap<K, V> extends Map<K, V> {
    void add(K key, V value);
    Collection<V> getAll(K key);
}
