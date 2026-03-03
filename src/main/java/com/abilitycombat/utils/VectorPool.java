package com.abilitycombat.utils;

import org.bukkit.util.Vector;

/**
 * Thread-local Vector pool to reduce GC overhead.
 * Reuses Vector objects instead of creating new ones each tick.
 */
public final class VectorPool {

    private static final ThreadLocal<Vector> TEMP1 = ThreadLocal.withInitial(Vector::new);
    private static final ThreadLocal<Vector> TEMP2 = ThreadLocal.withInitial(Vector::new);
    private static final ThreadLocal<Vector> TEMP3 = ThreadLocal.withInitial(Vector::new);

    private VectorPool() {
    }

    /**
     * Get a zeroed temporary vector.
     */
    public static Vector get() {
        return TEMP1.get().zero();
    }

    /**
     * Get a temporary vector with specified values.
     */
    public static Vector get(double x, double y, double z) {
        Vector v = TEMP1.get();
        v.setX(x);
        v.setY(y);
        v.setZ(z);
        return v;
    }

    /**
     * Get a second temporary vector (for operations needing two vectors).
     */
    public static Vector getSecond() {
        return TEMP2.get().zero();
    }

    /**
     * Get a second temporary vector with specified values.
     */
    public static Vector getSecond(double x, double y, double z) {
        Vector v = TEMP2.get();
        v.setX(x);
        v.setY(y);
        v.setZ(z);
        return v;
    }

    /**
     * Get a third temporary vector (for complex operations).
     */
    public static Vector getThird() {
        return TEMP3.get().zero();
    }

    /**
     * Copy values from source to a pooled vector.
     */
    public static Vector copy(Vector source) {
        if (source == null) {
            return get();
        }
        return get(source.getX(), source.getY(), source.getZ());
    }
}
