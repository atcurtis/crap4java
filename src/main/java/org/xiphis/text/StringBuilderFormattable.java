package org.xiphis.text;

import java.util.function.Supplier;

public interface StringBuilderFormattable extends Supplier<StringBuilder> {
    void formatTo(StringBuilder builder);

    default StringBuilder get() {
        StringBuilder sb = Msg.stringBuilder();
        formatTo(sb);
        return sb;
    }

    default String format() {
        return get().toString();
    }
}
