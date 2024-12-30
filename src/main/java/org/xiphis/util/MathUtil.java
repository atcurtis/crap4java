package org.xiphis.util;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static java.lang.Double.MIN_EXPONENT;

public final class MathUtil {
    private MathUtil() {
    }

    /**
     * The number of bits used to represent a {@code double} value.
     *
     * @since 1.5
     */
    public static final int DOUBLE_SIZE = Double.SIZE;

    /**
     * The number of bits in the significand of a {@code double} value.
     * This is the parameter N in section {@jls 4.2.3} of
     * <cite>The Java Language Specification</cite>.
     *
     * @since 19
     */
    public static final int DOUBLE_PRECISION = 53;


    /**
     * The number of logical bits in the significand of a
     * {@code double} number, including the implicit bit.
     */
    public static final int SIGNIFICAND_WIDTH = DOUBLE_PRECISION;


    /**
     * The exponent the smallest positive {@code double}
     * subnormal value would have if it could be normalized..
     */
    public static final int MIN_SUB_EXPONENT =
            MIN_EXPONENT - (SIGNIFICAND_WIDTH - 1); // -1074

    /**
     * Bias used in representing a {@code double} exponent.
     */
    public static final int EXP_BIAS =
            (1 << (DOUBLE_SIZE - SIGNIFICAND_WIDTH - 1)) - 1; // 1023

    /**
     * Bit mask to isolate the sign bit of a {@code double}.
     */
    public static final long SIGN_BIT_MASK = 1L << (DOUBLE_SIZE - 1);

    /**
     * Bit mask to isolate the exponent field of a {@code double}.
     */
    public static final long EXP_BIT_MASK =
            ((1L << (DOUBLE_SIZE - SIGNIFICAND_WIDTH)) - 1) << (SIGNIFICAND_WIDTH - 1);

    /**
     * Bit mask to isolate the significand field of a {@code double}.
     */
    public static final long SIGNIF_BIT_MASK = (1L << (SIGNIFICAND_WIDTH - 1)) - 1;

    /**
     * Bit mask to isolate the magnitude bits (combined exponent and
     * significand fields) of a {@code double}.
     */
    public static final long MAG_BIT_MASK = EXP_BIT_MASK | SIGNIF_BIT_MASK;


    public static int nextPow2(int v) {
        return (int) nextPow2((long) v);
    }

    public static long nextPow2(long v) {
        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        v |= v >> 32;
        v++;
        return v;
    }

    public static long gaussian(long centre) {
        return gaussian(centre, 0.5);
    }

    public static long gaussian(long centre, double spread) {
        return gaussian(ThreadLocalRandom.current(), centre, spread);
    }

    public static long gaussian(Random rnd, long centre, double spread) {
        long bound = Math.round(2.0 * centre * spread);
        return (centre - (bound >> 1))
                + (rnd.nextLong(bound) + rnd.nextLong(bound) + rnd.nextLong(bound)
                + rnd.nextLong(bound) + rnd.nextLong(bound) ) / 5;
    }

    static {
        // verify bit masks cover all bit positions and that the bit
        // masks are non-overlapping
        //noinspection ConstantValue
        assert(((SIGN_BIT_MASK | EXP_BIT_MASK | SIGNIF_BIT_MASK) == ~0L) &&
                (((SIGN_BIT_MASK & EXP_BIT_MASK) == 0L) &&
                        ((SIGN_BIT_MASK & SIGNIF_BIT_MASK) == 0L) &&
                        ((EXP_BIT_MASK & SIGNIF_BIT_MASK) == 0L)) &&
                ((SIGN_BIT_MASK | MAG_BIT_MASK) == ~0L));
    }
}
