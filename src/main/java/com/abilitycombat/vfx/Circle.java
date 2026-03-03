package com.abilitycombat.vfx;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class Circle {

    private Circle() {
    }

    public static List<Vector> of(double radius, int points) {
        List<Vector> vectors = new ArrayList<>();
        if (radius <= 0 || points < 1 || Double.isNaN(radius) || !Double.isFinite(radius)) {
            return vectors;
        }
        double step = (Math.PI * 2.0) / points;
        for (int i = 1; i <= points; i++) {
            double radians = step * i;
            double x = Math.cos(radians) * radius;
            double z = Math.sin(radians) * radius;
            vectors.add(new Vector(x, 0, z));
        }
        return vectors;
    }
}
