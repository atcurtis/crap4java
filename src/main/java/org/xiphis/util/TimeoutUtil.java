package org.xiphis.util;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public abstract class TimeoutUtil<V> implements Callable<V> {

    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);;

    private long now = Time.nanoTime();
    private long timeout;
    private TimeUnit unit;
    private long elapsedNanos;

    public TimeoutUtil(long timeout, TimeUnit unit) {
        this.timeout = timeout;
        this.unit = Objects.requireNonNull(unit);
    }

    protected final long getElapsedNanos() {
        return elapsedNanos;
    }

    protected abstract V action(long now, long timeout, TimeUnit unit) throws InterruptedException;

    public final V call() throws InterruptedException {
        V result;
        do {
            long prev = now;
            now = Time.nanoTime();
            elapsedNanos += now - prev;
            long diff = unit.convert(elapsedNanos, TimeUnit.NANOSECONDS);
            if (diff > 0) {
                elapsedNanos -= unit.toNanos(diff);
                timeout -= diff;
            } else if (elapsedNanos > NANOS_PER_SECOND && timeout < Long.MAX_VALUE && unit.ordinal() > 0) {
                TimeUnit newUnit = TimeUnit.values()[unit.ordinal()-1];
                long conv = TimeUnit.values()[unit.ordinal()-1].convert(timeout, unit);
                if (conv > 0 && conv < Long.MAX_VALUE) {
                    timeout = conv;
                    unit = newUnit;
                }
            }
            result = action(now, timeout, unit);
        } while (result == null && timeout > 0);
        return result;
    }
}
