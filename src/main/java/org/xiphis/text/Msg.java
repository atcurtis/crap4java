package org.xiphis.text;

import java.lang.ref.SoftReference;
import java.util.Objects;
import java.util.function.Supplier;

public final class Msg implements StringBuilderFormattable {

    private static final ThreadLocal<SoftReference<StringBuilder>> SB
            = ThreadLocal.withInitial(() -> new SoftReference<>(new StringBuilder()));

    private Msg(StringBuilderFormattable src) {
        this.src = src;
    }

    public static StringBuilder stringBuilder() {
        StringBuilder sb = SB.get().get();
        if (sb == null) {
            sb = new StringBuilder();
            SB.set(new SoftReference<>(sb));
        } else {
            sb.setLength(0);
        }
        return sb;
    }

    public static Msg of(StringBuilderFormattable fmt) {
        return new Msg(Objects.requireNonNull(fmt));
    }

    public static Msg of(Supplier<?> supplier) {
        Objects.requireNonNull(supplier);
        return of(sb -> {
            sb.append(supplier.get());
        });
    }

    private final StringBuilderFormattable src;
    private transient String string;

    @Override
    public String toString() {
        if (string == null) {
            string = format();
        }
        return string;
    }

    @Override
    public void formatTo(StringBuilder builder) {
        src.formatTo(builder);
    }
}
