package com.abilitycombat.utils;

/**
 * Pre-computed sin/cos lookup table for performance optimization.
 * Based on AbilityWar's FastMath implementation.
 */
public final class FastMath {

    private static final int ACCURACY = 100000;
    private static final double TWO_PI = 6.283185307179586;
    private static final double HALF_PI = 1.5707963267948966;
    private static final int TABLE_SIZE = (int) (TWO_PI * ACCURACY);
    private static final double[] SIN_TABLE;

    static {
        SIN_TABLE = new double[TABLE_SIZE];
        for (int i = 0; i < TABLE_SIZE; i++) {
            SIN_TABLE[i] = Math.sin((i + 1.0) / ACCURACY);
        }
    }

    private FastMath() {
    }

    public static double sin(double radians) {
        double normalized = Math.abs(radians) % TWO_PI;
        int index = Math.max(0, (int) (normalized * ACCURACY) - 1);
        if (index >= TABLE_SIZE) {
            index = TABLE_SIZE - 1;
        }
        return radians >= 0 ? SIN_TABLE[index] : -SIN_TABLE[index];
    }

    public static double cos(double radians) {
        return sin(radians + HALF_PI);
    }

    public static double tan(double radians) {
        double cosValue = cos(radians);
        if (cosValue == 0) {
            return Double.NaN;
        }
        return sin(radians) / cosValue;
    }

    public static double square(double value) {
        return value * value;
    }
}
