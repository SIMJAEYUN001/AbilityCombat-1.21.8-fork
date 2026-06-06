package com.abilitycombat.effect;

import com.abilitycombat.AbilityCombat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class DamageModifier implements Listener {

    public enum DamageChannel {
        INCOMING,
        OUTGOING
    }

    private static final long CLEANUP_PERIOD_TICKS = 20L;
    private static final double MIN_PERCENT = -95.0;
    private static final double MAX_PERCENT = 500.0;
    private static final double FLAT_DAMAGE_TRIGGER = 0.001;
    private static final double DAMAGE_SEARCH_EPSILON = 1.0E-4;
    private static final double MAX_RAW_DAMAGE = 1_000_000.0;
    private static final int DAMAGE_SEARCH_ITERATIONS = 28;

    private static final Map<UUID, Map<String, ModifierEntry>> INCOMING = new HashMap<>();
    private static final Map<UUID, Map<String, ModifierEntry>> OUTGOING = new HashMap<>();
    private static final Map<EntityDamageEvent, PendingAdjustment> PENDING = new IdentityHashMap<>();
    private static final Map<UUID, OneShotFlatDamage> ONE_SHOT_FLAT = new HashMap<>();

    private static BukkitTask task;
    private static DamageModifier listener;

    private DamageModifier() {
    }

    public static void start(AbilityCombat plugin) {
        if (plugin == null) {
            return;
        }
        if (listener == null) {
            listener = new DamageModifier();
            Bukkit.getPluginManager().registerEvents(listener, plugin);
        }
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, DamageModifier::cleanup, CLEANUP_PERIOD_TICKS,
                    CLEANUP_PERIOD_TICKS);
        }
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        INCOMING.clear();
        OUTGOING.clear();
        PENDING.clear();
        ONE_SHOT_FLAT.clear();
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }

    public static void apply(LivingEntity target, DamageChannel channel, int ticks, String sourceKey,
            double percentDelta) {
        if (channel == null) {
            return;
        }
        apply(container(channel), target, ticks, sourceKey, percentDelta);
    }

    public static void remove(LivingEntity target, DamageChannel channel, String sourceKey) {
        if (channel == null) {
            return;
        }
        remove(container(channel), target, sourceKey);
    }

    public static void add(EntityDamageEvent event, DamageChannel channel, double percentDelta, double flatDelta) {
        if (event == null || channel == null) {
            return;
        }
        PendingAdjustment adjustment = PENDING.computeIfAbsent(event, ignored -> new PendingAdjustment());
        if (Double.isFinite(percentDelta)) {
            adjustment.percentDelta += percentDelta;
        }
        if (Double.isFinite(flatDelta)) {
            adjustment.flatDelta += flatDelta;
        }
    }

    public static void applyFlatDamage(LivingEntity target, double amount, Entity source) {
        if (target == null || target.isDead() || amount <= 0.0 || !Double.isFinite(amount)) {
            return;
        }
        UUID sourceId = source != null ? source.getUniqueId() : null;
        ONE_SHOT_FLAT.put(target.getUniqueId(),
                new OneShotFlatDamage(sourceId, amount, Bukkit.getCurrentTick() + 1));
        target.setNoDamageTicks(0);
        if (source != null && source.isValid()) {
            target.damage(FLAT_DAMAGE_TRIGGER, source);
        } else {
            target.damage(FLAT_DAMAGE_TRIGGER);
        }
        ONE_SHOT_FLAT.remove(target.getUniqueId());
    }

    public static double previewFinalDamage(EntityDamageEvent event) {
        return previewFinalDamage(event, PENDING.get(event));
    }

    private static double previewFinalDamage(EntityDamageEvent event, PendingAdjustment pending) {
        if (event == null) {
            return 0.0;
        }
        double finalDamage = event.getFinalDamage();
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return Math.max(0.0, finalDamage);
        }
        long now = System.currentTimeMillis();
        double totalPercent = resolveTotal(INCOMING, target.getUniqueId(), now);
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity) {
            LivingEntity attacker = resolveAttacker(byEntity.getDamager());
            if (attacker != null) {
                totalPercent += resolveTotal(OUTGOING, attacker.getUniqueId(), now);
            }
        }
        double flatAmount = 0.0;
        if (pending != null) {
            totalPercent += pending.percentDelta;
            flatAmount += pending.flatDelta;
        }
        return Math.max(0.0, (finalDamage * percentToMultiplier(totalPercent)) + flatAmount);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        PendingAdjustment pending = PENDING.remove(event);
        OneShotFlatDamage oneShotFlatDamage = consumeOneShotFlatDamage(event);
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        if (oneShotFlatDamage != null) {
            applyFinalDamage(event, oneShotFlatDamage.amount);
            return;
        }
        double previewFinalDamage = previewFinalDamage(event, pending);
        double currentFinalDamage = Math.max(0.0, event.getFinalDamage());
        if (pending == null && Math.abs(previewFinalDamage - currentFinalDamage) < 1.0E-6) {
            return;
        }
        applyFinalDamage(event, previewFinalDamage);
    }

    private static void apply(Map<UUID, Map<String, ModifierEntry>> container, LivingEntity target, int ticks,
            String sourceKey, double percentDelta) {
        if (target == null || ticks <= 0 || sourceKey == null || sourceKey.isBlank()) {
            return;
        }
        double clampedPercent = clampPercent(percentDelta);
        if (Math.abs(clampedPercent) < 1.0E-6) {
            remove(container, target, sourceKey);
            return;
        }
        long now = System.currentTimeMillis();
        long until = now + ticks * 50L;
        Map<String, ModifierEntry> entries = container.computeIfAbsent(target.getUniqueId(), ignored -> new HashMap<>());
        ModifierEntry current = entries.get(sourceKey);
        if (current == null) {
            entries.put(sourceKey, new ModifierEntry(until, clampedPercent));
            return;
        }
        current.until = Math.max(current.until, until);
        current.percentDelta = clampedPercent;
    }

    private static void remove(Map<UUID, Map<String, ModifierEntry>> container, LivingEntity target, String sourceKey) {
        if (target == null || sourceKey == null || sourceKey.isBlank()) {
            return;
        }
        remove(container, target.getUniqueId(), sourceKey);
    }

    private static void remove(Map<UUID, Map<String, ModifierEntry>> container, UUID uuid, String sourceKey) {
        Map<String, ModifierEntry> entries = container.get(uuid);
        if (entries == null) {
            return;
        }
        entries.remove(sourceKey);
        if (entries.isEmpty()) {
            container.remove(uuid);
        }
    }

    private static LivingEntity resolveAttacker(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return null;
    }

    private static OneShotFlatDamage consumeOneShotFlatDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return null;
        }
        OneShotFlatDamage damage = ONE_SHOT_FLAT.get(target.getUniqueId());
        if (damage == null) {
            return null;
        }
        if (damage.expiresAtTick < Bukkit.getCurrentTick()) {
            ONE_SHOT_FLAT.remove(target.getUniqueId());
            return null;
        }
        if (damage.sourceId != null && !damage.sourceId.equals(resolveEventSourceId(event))) {
            return null;
        }
        ONE_SHOT_FLAT.remove(target.getUniqueId());
        return damage;
    }

    private static UUID resolveEventSourceId(EntityDamageEvent event) {
        if (!(event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter.getUniqueId();
        }
        return damager.getUniqueId();
    }

    private static void cleanup() {
        long now = System.currentTimeMillis();
        cleanup(INCOMING, now);
        cleanup(OUTGOING, now);
        int currentTick = Bukkit.getCurrentTick();
        ONE_SHOT_FLAT.entrySet().removeIf(entry -> entry.getValue().expiresAtTick < currentTick);
    }

    private static void cleanup(Map<UUID, Map<String, ModifierEntry>> container, long now) {
        Iterator<Map.Entry<UUID, Map<String, ModifierEntry>>> outer = container.entrySet().iterator();
        while (outer.hasNext()) {
            Map<String, ModifierEntry> entries = outer.next().getValue();
            entries.values().removeIf(entry -> entry.until <= now);
            if (entries.isEmpty()) {
                outer.remove();
            }
        }
    }

    private static double resolveTotal(Map<UUID, Map<String, ModifierEntry>> container, UUID uuid, long now) {
        Map<String, ModifierEntry> entries = container.get(uuid);
        if (entries == null || entries.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        Iterator<Map.Entry<String, ModifierEntry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            ModifierEntry entry = iterator.next().getValue();
            if (entry.until <= now) {
                iterator.remove();
                continue;
            }
            total += entry.percentDelta;
        }
        if (entries.isEmpty()) {
            container.remove(uuid);
            return 0.0;
        }
        return clampPercent(total);
    }

    private static void applyFinalDamage(EntityDamageEvent event, double targetFinalDamage) {
        if (event == null) {
            return;
        }
        double clampedTargetFinal = Math.max(0.0, targetFinalDamage);
        if (clampedTargetFinal <= DAMAGE_SEARCH_EPSILON) {
            event.setDamage(0.0);
            return;
        }

        double high = Math.min(MAX_RAW_DAMAGE, Math.max(1.0, Math.max(event.getDamage(), clampedTargetFinal)));
        while (high < MAX_RAW_DAMAGE) {
            event.setDamage(high);
            if (Math.max(0.0, event.getFinalDamage()) >= clampedTargetFinal - DAMAGE_SEARCH_EPSILON) {
                break;
            }
            high = Math.min(MAX_RAW_DAMAGE, high * 2.0);
        }

        event.setDamage(high);
        if (Math.max(0.0, event.getFinalDamage()) < clampedTargetFinal - DAMAGE_SEARCH_EPSILON) {
            return;
        }

        double low = 0.0;
        for (int i = 0; i < DAMAGE_SEARCH_ITERATIONS; i++) {
            double mid = (low + high) * 0.5;
            event.setDamage(mid);
            double finalDamage = Math.max(0.0, event.getFinalDamage());
            if (finalDamage < clampedTargetFinal) {
                low = mid;
            } else {
                high = mid;
            }
        }
        event.setDamage(high);
    }

    private static double percentToMultiplier(double percentDelta) {
        return Math.max(0.05, 1.0 + (clampPercent(percentDelta) / 100.0));
    }

    private static double clampPercent(double percentDelta) {
        if (!Double.isFinite(percentDelta)) {
            return 0.0;
        }
        return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percentDelta));
    }

    private static Map<UUID, Map<String, ModifierEntry>> container(DamageChannel channel) {
        return channel == DamageChannel.OUTGOING ? OUTGOING : INCOMING;
    }

    private static final class ModifierEntry {
        private long until;
        private double percentDelta;

        private ModifierEntry(long until, double percentDelta) {
            this.until = until;
            this.percentDelta = percentDelta;
        }
    }

    private static final class PendingAdjustment {
        private double percentDelta;
        private double flatDelta;
    }

    private record OneShotFlatDamage(UUID sourceId, double amount, int expiresAtTick) {
    }
}
