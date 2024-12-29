package org.xiphis.collection;

import net.jcip.annotations.NotThreadSafe;

import java.util.*;

@NotThreadSafe
public class ListMap<K, V> extends AbstractMap<K, V> {
    private final List<Entry<K, V>> list;
    private transient Set<Entry<K, V>> entrySet;

    public ListMap(List<Entry<K, V>> list) {
        this.list = Objects.requireNonNull(list);
    }

    @Override
    public boolean containsKey(Object key) {
        return super.containsKey(key);
    }

    @Override
    public V get(Object key) {
        return super.get(key);
    }

    @Override
    public V put(K key, V value) {
        return super.put(key, value);
    }

    @Override
    public V remove(Object key) {
        return super.remove(key);
    }

    @Override
    public void clear() {
        list.clear();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        if (entrySet == null) {
            entrySet = new AbstractSet<Entry<K, V>>() {
                @Override
                public Iterator<Entry<K, V>> iterator() {
                    return list.iterator();
                }

                @Override
                public int size() {
                    return list.size();
                }
            };
        }
        return entrySet;
    }
}
