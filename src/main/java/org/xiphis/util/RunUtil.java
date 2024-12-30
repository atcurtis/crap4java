package org.xiphis.util;

import java.util.function.Supplier;

public final class RunUtil {
    private RunUtil() {
    }

    public static void runQuietly(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception ex) {
            // sink it.
        }
    }

    public static void runQuietly(Supplier<?> supplier) {
        try {
            supplier.get();
        } catch (Exception ex) {
            // sink it.
        }
    }

}
