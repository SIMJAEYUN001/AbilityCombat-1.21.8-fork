package com.abilitycombat.effect;

import com.abilitycombat.AbilityCombat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class Infection implements Listener {

    private static final Map<UUID, InfectionEntry> INFECTED = new HashMap<>();
    private static BukkitTask task;
    private static Infection listener;

    private Infection() {
    }

    public static void start(AbilityCombat plugin) {
        if (plugin == null) {
            return;
        }
        if (listener == null) {
            listener = new Infection();
            Bukkit.getPluginManager().registerEvents(listener, plugin);
        }
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, Infection::tick, 4L, 4L);
        }
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        INFECTED.clear();
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
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
                iterator.remove();
                continue;
            }
            if (entry.until <= now) {
                iterator.remove();
                continue;
            }
            if (target instanceof Player player && ThreadLocalRandom.current().nextDouble() <= 0.65) {
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

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (getEndTime(target.getUniqueId()) <= 0L) {
            return;
        }
        event.setDamage(event.getDamage() * 0.75);
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
