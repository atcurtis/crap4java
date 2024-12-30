package org.xiphis.util;


import java.util.concurrent.TimeUnit;

public class Time {

    private static final ThreadLocal<Anchor> ANCHOR = ThreadLocal.withInitial(Anchor::new);
    private static final double NANOS_TO_MILLIS = 1.0 / TimeUnit.MILLISECONDS.toNanos(1);
    private static final double NANOS_TO_MICROS = 1.0 / TimeUnit.MICROSECONDS.toNanos(1);
    private static final long DAY_IN_NANOS = TimeUnit.DAYS.toNanos(1);



    private static Source source = new Wallclock();

    public static long nanoTime() {
        return source.nanoTime();
    }

    public static double microTime() {
        return nanoTime() * NANOS_TO_MICROS;
    }

    public static double currentTimeMillis() {
        return source.currentTimeMillis();
    }

    public static void freeze() {
        source = source.freeze();
    }

    public static void thaw() {
        source = source.thaw();
    }

    public static void reset() {
        source = source.wallclock();
    }

    private interface Source {
        long nanoTime();
        double currentTimeMillis();

        Source freeze();
        Source thaw();
        Source wallclock();
    }

    static final class Wallclock implements Source {

        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public double currentTimeMillis() {
            return ANCHOR.get().currentTimeMillis();
        }

        @Override
        public Source freeze() {
            return new Frozen(nanoTime());
        }

        @Override
        public Source thaw() {
            return this;
        }

        @Override
        public Source wallclock() {
            return this;
        }

        final class Frozen implements Source {

            private final long frozenNanos;

            private Frozen(long frozenNanos) {
                this.frozenNanos = frozenNanos;
            }

            @Override
            public long nanoTime() {
                return frozenNanos;
            }

            @Override
            public double currentTimeMillis() {
                return ANCHOR.get().nanosToMillis(nanoTime());
            }

            @Override
            public Source freeze() {
                return this;
            }

            @Override
            public Source thaw() {
                return new Delta(nanoTime() - wallclock().nanoTime());
            }

            @Override
            public Source wallclock() {
                return Wallclock.this;
            }
        }

        final class Delta implements Source {
            private final long deltaNanos;

            private Delta(long deltaNanos) {
                this.deltaNanos = deltaNanos;
            }

            @Override
            public long nanoTime() {
                return wallclock().nanoTime() + deltaNanos;
            }

            @Override
            public double currentTimeMillis() {
                return ANCHOR.get().nanosToMillis(nanoTime());
            }

            @Override
            public Source freeze() {
                return new Frozen(nanoTime());
            }

            @Override
            public Source thaw() {
                return this;
            }

            @Override
            public Source wallclock() {
                return Wallclock.this;
            }
        }
    }

    private static class Anchor {
        final long startTimeMillis;
        final long startNanoTime;
        final long expireNanoTime;

        private Anchor() {
            long start = System.currentTimeMillis();
            long millis;
            long nanos;
            int i = 100;
            do {
                while (i > 0) {
                    i--;
                }
                millis = System.currentTimeMillis();
                nanos = System.nanoTime();
                i += 100;
            } while (start == millis);
            startTimeMillis = millis;
            startNanoTime = nanos;
            expireNanoTime = nanos + DAY_IN_NANOS;
        }

        private double currentTimeMillis() {
            long nanoTime = System.nanoTime();
            if (nanoTime > expireNanoTime) {
                ANCHOR.remove();
            }
            return nanosToMillis(nanoTime);
        }

        private double nanosToMillis(long nanoTime) {
            long deltaNanos = nanoTime - startNanoTime;
            double deltaMillis = NANOS_TO_MILLIS * deltaNanos;
            return startTimeMillis + deltaMillis;
        }
    }
}
