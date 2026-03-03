package com.abilitycombat.effect;

import com.abilitycombat.AbilityCombat;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class Bleed {

    private static final long PERIOD_TICKS = 5L;
    private static final double DEFAULT_DAMAGE = 0.5;

    private static final Map<UUID, BleedEntry> BLEEDING = new HashMap<>();
    private static BukkitTask task;

    private Bleed() {
    }

    public static void start(AbilityCombat plugin) {
        if (plugin == null || task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, Bleed::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        BLEEDING.clear();
    }

    public static void apply(LivingEntity target, int ticks) {
        apply(target, ticks, DEFAULT_DAMAGE, null);
    }

    public static void apply(LivingEntity target, int ticks, double damage, LivingEntity source) {
        if (target == null || ticks <= 0) {
            return;
        }
        if (task == null) {
            start(AbilityCombat.getPlugin());
        }
        long now = System.currentTimeMillis();
        long until = now + ticks * 50L;
        UUID uuid = target.getUniqueId();
        BleedEntry entry = BLEEDING.get(uuid);
        if (entry == null) {
            entry = new BleedEntry(target);
            BLEEDING.put(uuid, entry);
        }
        entry.until = Math.max(entry.until, until);
        entry.damage = Math.max(entry.damage, Math.max(0.0, damage));
        if (source != null) {
            entry.source = source;
        }
    }

    private static void tick() {
        if (BLEEDING.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, BleedEntry>> iterator = BLEEDING.entrySet().iterator();
        while (iterator.hasNext()) {
            BleedEntry entry = iterator.next().getValue();
            LivingEntity target = entry.target;
            if (target == null || target.isDead() || !target.isValid()) {
                iterator.remove();
                continue;
            }
            if (entry.until <= now) {
                iterator.remove();
                continue;
            }
            if (!entry.toggle) {
                target.setNoDamageTicks(0);
                if (entry.source != null && entry.source.isValid() && !entry.source.isDead()) {
                    target.damage(entry.damage, entry.source);
                } else {
                    target.damage(entry.damage);
                }
            }
            entry.toggle = !entry.toggle;
        }
    }

    private static final class BleedEntry {
        private final LivingEntity target;
        private LivingEntity source;
        private double damage = DEFAULT_DAMAGE;
        private long until;
        private boolean toggle = false;

        private BleedEntry(LivingEntity target) {
            this.target = target;
            this.until = System.currentTimeMillis();
        }
    }
}
