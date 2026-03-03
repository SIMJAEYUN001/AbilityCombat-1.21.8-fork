package com.abilitycombat.utils;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.combat.SweepEffectAllowance;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class ParticleUtil {

    private static final String CONFIG_ENABLED = "visual-effects.enabled";
    private static final String CONFIG_MIN_INTERVAL = "visual-effects.min-interval-ticks";
    private static final String CONFIG_MAX_DISTANCE = "visual-effects.max-distance";

    private ParticleUtil() {
    }

    public static void spawnParticle(World world, Particle particle, Location location, int count, double offsetX,
            double offsetY, double offsetZ, double extra) {
        spawnParticle(world, particle, location, count, offsetX, offsetY, offsetZ, extra, null, -1, -1);
    }

    public static <T> void spawnParticle(World world, Particle particle, Location location, int count, double offsetX,
            double offsetY, double offsetZ, double extra, T data) {
        spawnParticle(world, particle, location, count, offsetX, offsetY, offsetZ, extra, data, -1, -1);
    }

    public static void spawnParticle(World world, Particle particle, Location location, int count, double offsetX,
            double offsetY, double offsetZ, double extra, int minIntervalTicks, double maxDistance) {
        spawnParticle(world, particle, location, count, offsetX, offsetY, offsetZ, extra, null, minIntervalTicks,
                maxDistance);
    }

    public static <T> void spawnParticle(World world, Particle particle, Location location, int count, double offsetX,
            double offsetY, double offsetZ, double extra, T data, int minIntervalTicks, double maxDistance) {
        if (world == null || location == null || particle == null || count <= 0) {
            return;
        }
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean(CONFIG_ENABLED, true)) {
            return;
        }
        int interval = minIntervalTicks > 0 ? minIntervalTicks
                : Math.max(1, plugin.getConfig().getInt(CONFIG_MIN_INTERVAL, 2));
        if (interval > 1 && AbilityTickManager.getGlobalTick() % interval != 0) {
            return;
        }
        double distance = maxDistance > 0 ? maxDistance : plugin.getConfig().getDouble(CONFIG_MAX_DISTANCE, 64.0);
        if (distance > 0 && !hasNearbyViewer(world, location, distance)) {
            return;
        }
        if (particle == Particle.SWEEP_ATTACK) {
            SweepEffectAllowance.markAbilitySweepParticle();
        }
        if (data == null) {
            world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
        } else {
            world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
        }
    }

    private static boolean hasNearbyViewer(World world, Location location, double maxDistance) {
        double distanceSquared = maxDistance * maxDistance;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= distanceSquared) {
                return true;
            }
        }
        return false;
    }
}
