package org.xiphis.util;

import net.jcip.annotations.ThreadSafe;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

@ThreadSafe
public final class Lazy<T> implements Supplier<T> {

    private Supplier<T> supplier;

    private Lazy(Supplier<? extends T> source) {
        supplier = new Supplier<T>() {
            @Override
            public synchronized T get() {
                if (this == supplier) {
                    supplier = of(source);
                }
                return supplier.get();
            }
        };
    }

    public static <T> Lazy<T> of(Supplier<? extends T> supplier) {
        return new Lazy<>(Objects.requireNonNull(supplier));
    }

    public static <K, T> Lazy<T> of(K arg, Function<? super K, ? extends T> function) {
        return of(of(arg), function);
    }

    public static <K, T> Lazy<T> of(Supplier<K> arg, Function<? super K, ? extends T> function) {
        Objects.requireNonNull(arg);
        Objects.requireNonNull(function);
        return of(() -> function.apply(arg.get()));
    }

    public static <T> Supplier<T> of(T value) {
        return () -> value;
    }

    @Override
    public T get() {
        return supplier.get();
    }
}
