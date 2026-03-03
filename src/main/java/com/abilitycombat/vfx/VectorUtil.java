package com.abilitycombat.vfx;

import com.abilitycombat.utils.FastMath;
import org.bukkit.util.Vector;

public final class VectorUtil {

    private VectorUtil() {
    }

    public static Vector rotateAroundAxisY(Vector vector, double radians) {
        if (vector == null) {
            return new Vector();
        }
        double cos = FastMath.cos(radians);
        double sin = FastMath.sin(radians);
        double x = vector.getX() * cos + vector.getZ() * sin;
        double z = vector.getZ() * cos - vector.getX() * sin;
        return new Vector(x, vector.getY(), z);
    }
}
