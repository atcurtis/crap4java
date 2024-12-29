package org.xiphis.util;

public abstract class Builder<T extends Builder<T, V>, V> extends CRTP<T> {

    public abstract V build();

}
