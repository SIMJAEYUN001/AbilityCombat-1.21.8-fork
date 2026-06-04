package com.abilitycombat.effect;

import com.abilitycombat.AbilityCombat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class Infection {

    public static final double INCOMING_DAMAGE_INCREASE_PERCENT = 25.0;
    public static final long ROTATION_PERIOD_TICKS = 4L;
    public static final double ROTATION_CHANCE = 0.65;

    private static final Map<UUID, InfectionEntry> INFECTED = new HashMap<>();
    private static final String DAMAGE_SOURCE_KEY = "infection";
    private static BukkitTask task;

    private Infection() {
    }

    public static void start(AbilityCombat plugin) {
        if (plugin != null && task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, Infection::tick, ROTATION_PERIOD_TICKS,
                    ROTATION_PERIOD_TICKS);
        }
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (InfectionEntry entry : INFECTED.values()) {
            DamageModifier.removeIncoming(entry.target, DAMAGE_SOURCE_KEY);
        }
        INFECTED.clear();
    }

    public static void apply(LivingEntity target, int ticks) {
        if (target == null || ticks <= 0) {
            return;
        }
        if (task == null) {
            start(AbilityCombat.getPlugin());
        }
        long now = System.currentTimeMillis();
        long until = now + ticks * 50L;
        UUID uuid = target.getUniqueId();
        InfectionEntry entry = INFECTED.get(uuid);
        if (entry == null) {
            entry = new InfectionEntry(target);
            INFECTED.put(uuid, entry);
        }
        entry.until = Math.max(entry.until, until);
        DamageModifier.applyIncoming(target, ticks, DAMAGE_SOURCE_KEY, INCOMING_DAMAGE_INCREASE_PERCENT);
    }

    public static boolean isInfected(LivingEntity target) {
        if (target == null) {
            return false;
        }
        return getEndTime(target.getUniqueId()) > 0L;
    }

    private static long getEndTime(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }
        InfectionEntry entry = INFECTED.get(uuid);
        if (entry == null) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        if (entry.until <= now) {
            INFECTED.remove(uuid);
            return 0L;
        }
        return entry.until;
    }

    private static void tick() {
        if (INFECTED.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, InfectionEntry>> iterator = INFECTED.entrySet().iterator();
        while (iterator.hasNext()) {
            InfectionEntry entry = iterator.next().getValue();
            LivingEntity target = entry.target;
            if (target == null || target.isDead() || !target.isValid()) {
                DamageModifier.removeIncoming(target, DAMAGE_SOURCE_KEY);
                iterator.remove();
                continue;
            }
            if (entry.until <= now) {
                DamageModifier.removeIncoming(target, DAMAGE_SOURCE_KEY);
                iterator.remove();
                continue;
            }
            if (target instanceof Player player && ThreadLocalRandom.current().nextDouble() <= ROTATION_CHANCE) {
                Location location = player.getLocation();
                float yaw = location.getYaw() + ThreadLocalRandom.current().nextInt(130) - 65;
                if (yaw > 180f) {
                    yaw -= 360f;
                } else if (yaw < -180f) {
                    yaw += 360f;
                }
                float pitch = location.getPitch() + ThreadLocalRandom.current().nextInt(90) - 45;
                if (pitch > 90f) {
                    pitch = 90f;
                } else if (pitch < -90f) {
                    pitch = -90f;
                }
                player.setRotation(yaw, pitch);
            }
        }
    }

    private static final class InfectionEntry {
        private final LivingEntity target;
        private long until;

        private InfectionEntry(LivingEntity target) {
            this.target = target;
            this.until = System.currentTimeMillis();
        }
    }
}
