package org.xiphis.collection;

import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class WeakStringHashMap<V> extends WeakHashMap<String, V> {

    @Override
    public V computeIfAbsent(String key, Function<? super String, ? extends V> mappingFunction) {
        return super.computeIfAbsent(key, keyMapping(Objects.requireNonNull(mappingFunction)));
    }

    private static <V> Function<? super String, ? extends V> keyMapping(Function<? super String, ? extends V> mappingFunction) {
        //noinspection StringOperationCanBeSimplified
        return key -> mappingFunction.apply(new String(key));
    }

    @Override
    public V computeIfPresent(String key, BiFunction<? super String, ? super V, ? extends V> remappingFunction) {
        return super.computeIfPresent(key, keyMapping(Objects.requireNonNull(remappingFunction)));
    }

    private static <V> BiFunction<? super String, ? super V, ? extends V> keyMapping(BiFunction<? super String, ? super V, ? extends V> remappingFunction) {
        //noinspection StringOperationCanBeSimplified
        return (key, oldValue) -> remappingFunction.apply(new String(key), oldValue);
    }

    @Override
    public V compute(String key, BiFunction<? super String, ? super V, ? extends V> remappingFunction) {
        return super.compute(key, keyMapping(Objects.requireNonNull(remappingFunction)));
    }
}
