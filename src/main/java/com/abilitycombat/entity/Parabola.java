package com.abilitycombat.entity;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public final class Parabola {

    private final Vector origin;
    private final Vector initialVelocity;
    private final double gravity;

    public Parabola(Location origin, Vector initialVelocity, double gravity) {
        this.origin = origin.toVector();
        this.initialVelocity = initialVelocity.clone();
        this.gravity = gravity;
    }

    public Vector pointAt(double timeSeconds) {
        double x = origin.getX() + (initialVelocity.getX() * timeSeconds);
        double y = origin.getY() + (initialVelocity.getY() * timeSeconds) - (0.5 * gravity * timeSeconds * timeSeconds);
        double z = origin.getZ() + (initialVelocity.getZ() * timeSeconds);
        return new Vector(x, y, z);
    }

    public Location locationAt(Location worldRef, double timeSeconds) {
        Vector point = pointAt(timeSeconds);
        return new Location(worldRef.getWorld(), point.getX(), point.getY(), point.getZ(), worldRef.getYaw(), worldRef.getPitch());
    }

    public Vector velocityAt(double timeSeconds) {
        return initialVelocity.clone().add(new Vector(0, -gravity * timeSeconds, 0));
    }

    public Vector pointAtTicks(int ticks) {
        return pointAt(ticks / 20.0);
    }

    public Vector getOrigin() {
        return origin.clone();
    }

    public Vector getInitialVelocity() {
        return initialVelocity.clone();
    }
}
