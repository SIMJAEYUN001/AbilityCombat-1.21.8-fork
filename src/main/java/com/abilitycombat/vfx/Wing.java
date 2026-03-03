package com.abilitycombat.vfx;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class Wing {

    private static final double DEFAULT_SPACE = 0.2;

    private final List<Vector> left;
    private final List<Vector> right;

    private Wing(List<Vector> left, List<Vector> right) {
        this.left = left;
        this.right = right;
    }

    public static Wing of(boolean[][] shape) {
        return of(shape, DEFAULT_SPACE);
    }

    public static Wing of(boolean[][] shape, double space) {
        return new Wing(buildWing(shape, space, true), buildWing(shape, space, false));
    }

    public List<Vector> getLeft() {
        return new ArrayList<>(left);
    }

    public List<Vector> getRight() {
        return new ArrayList<>(right);
    }

    private static List<Vector> buildWing(boolean[][] shape, double space, boolean inverted) {
        List<Vector> vectors = new ArrayList<>();
        if (shape == null || shape.length == 0) {
            return vectors;
        }
        int lines = shape.length;
        for (int i = 0; i < lines; i++) {
            boolean[] line = shape[i];
            if (line == null) {
                continue;
            }
            int points = line.length;
            for (int j = 0; j < points; j++) {
                if (!line[j]) {
                    continue;
                }
                double x = space * (inverted ? -(points - j) : (points - j));
                double y = space * (lines - i);
                vectors.add(new Vector(x, y, 0));
            }
        }
        return vectors;
    }
}
