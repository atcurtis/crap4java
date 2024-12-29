package org.xiphis.util;

import net.jcip.annotations.ThreadSafe;

import java.util.Objects;

@ThreadSafe
public final class RunOnce implements Runnable {

    private final Lazy<Throwable> lazy;

    public static RunOnce of(Runnable task) {
        return new RunOnce(Objects.requireNonNull(task));
    }

    private RunOnce(Runnable task) {
        lazy = Lazy.of(() -> {
            try {
                task.run();
                return null;
            } catch (Throwable ex) {
                return ex;
            }
        });
    }

    @Override
    public void run() {
        Throwable ex = lazy.get();
        if (ex instanceof RuntimeException) {
            throw (RuntimeException) ex;
        } else if (ex instanceof Error) {
            throw (Error) ex;
        }
    }

}
