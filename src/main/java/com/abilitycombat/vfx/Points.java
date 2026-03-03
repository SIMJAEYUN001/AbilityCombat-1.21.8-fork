package com.abilitycombat.vfx;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class Points {

    private Points() {
    }

    public static List<Vector> of(double space, boolean[][] shape) {
        List<Vector> vectors = new ArrayList<>();
        if (shape == null || shape.length == 0 || Double.isNaN(space) || !Double.isFinite(space)) {
            return vectors;
        }
        int lines = shape.length;
        for (int i = 0; i < lines; i++) {
            boolean[] line = shape[i];
            if (line == null) {
                continue;
            }
            int lineLength = line.length;
            for (int j = 0; j < lineLength; j++) {
                if (!line[j]) {
                    continue;
                }
                double x = space * (lineLength - j) - (space * (lineLength / 2.0));
                double y = space * (lines - i);
                vectors.add(new Vector(x, y, 0));
            }
        }
        return vectors;
    }
}
