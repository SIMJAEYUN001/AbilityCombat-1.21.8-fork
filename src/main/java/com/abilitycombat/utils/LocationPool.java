package com.abilitycombat.utils;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Thread-local Location pool to reduce GC overhead.
 * Reuses Location objects instead of creating new ones each tick.
 */
public final class LocationPool {

    private static final ThreadLocal<Location> TEMP1 = ThreadLocal.withInitial(() -> new Location(null, 0, 0, 0));
    private static final ThreadLocal<Location> TEMP2 = ThreadLocal.withInitial(() -> new Location(null, 0, 0, 0));

    private LocationPool() {
    }

    /**
     * Get a temporary location with null world and zero coordinates.
     */
    public static Location get() {
        Location loc = TEMP1.get();
        loc.setWorld(null);
        loc.setX(0);
        loc.setY(0);
        loc.setZ(0);
        loc.setYaw(0);
        loc.setPitch(0);
        return loc;
    }

    /**
     * Get a temporary location with specified values.
     */
    public static Location get(World world, double x, double y, double z) {
        Location loc = TEMP1.get();
        loc.setWorld(world);
        loc.setX(x);
        loc.setY(y);
        loc.setZ(z);
        loc.setYaw(0);
        loc.setPitch(0);
        return loc;
    }

    /**
     * Get a temporary location with full values.
     */
    public static Location get(World world, double x, double y, double z, float yaw, float pitch) {
        Location loc = TEMP1.get();
        loc.setWorld(world);
        loc.setX(x);
        loc.setY(y);
        loc.setZ(z);
        loc.setYaw(yaw);
        loc.setPitch(pitch);
        return loc;
    }

    /**
     * Get a second temporary location (for operations needing two locations).
     */
    public static Location getSecond() {
        Location loc = TEMP2.get();
        loc.setWorld(null);
        loc.setX(0);
        loc.setY(0);
        loc.setZ(0);
        loc.setYaw(0);
        loc.setPitch(0);
        return loc;
    }

    /**
     * Get a second temporary location with specified values.
     */
    public static Location getSecond(World world, double x, double y, double z) {
        Location loc = TEMP2.get();
        loc.setWorld(world);
        loc.setX(x);
        loc.setY(y);
        loc.setZ(z);
        loc.setYaw(0);
        loc.setPitch(0);
        return loc;
    }

    /**
     * Copy values from source to a pooled location.
     */
    public static Location copy(Location source) {
        if (source == null) {
            return get();
        }
        return get(source.getWorld(), source.getX(), source.getY(), source.getZ(), source.getYaw(), source.getPitch());
    }
}
