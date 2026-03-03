package com.abilitycombat.game;

public class WorldBorderPhase {
    /**
     * Time spent in this phase before the next shrink starts (in seconds).
     * Shrink duration itself is handled separately.
     */
    private final int durationSeconds;
    private final int radius;

    public WorldBorderPhase(int durationSeconds, int radius) {
        this.durationSeconds = durationSeconds;
        this.radius = radius;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public int getRadius() {
        return radius;
    }
}
