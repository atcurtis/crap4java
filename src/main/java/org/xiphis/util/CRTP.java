package org.xiphis.util;

public class CRTP<T extends CRTP<T>> {

    protected final T self() {
        //noinspection unchecked
        return (T) this;
    }
}
