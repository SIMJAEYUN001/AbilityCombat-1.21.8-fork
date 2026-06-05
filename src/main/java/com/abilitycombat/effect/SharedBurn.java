package com.abilitycombat.effect;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.utils.ParticleUtil;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.EnumMap;
import java.util.EnumSet;

public final class SharedBurn implements AbilityTickManager.Tickable, Listener {

    private static final int DAMAGE_INTERVAL_TICKS = 20;
    private static final double DEVIL_BOOTS_DAMAGE_PER_STACK = 0.5;
    private static final double FLAME_BRAND_DAMAGE_PER_STACK_RATIO = 0.05;
    private static final double FIRE_FIGHT_FIXED_DAMAGE_PER_STACK_RATIO = 0.005;

    private static final Map<UUID, BurnState> BURNS = new HashMap<>();
    private static final Map<UUID, SuppressedKnockback> SUPPRESSED_KNOCKBACKS = new HashMap<>();
    private static final Set<UUID> ABSORBERS = new HashSet<>();
    private static SharedBurn instance;

    private SharedBurn() {
    }

    public static void start(AbilityCombat plugin) {
        if (plugin == null || instance != null) {
            return;
        }
        instance = new SharedBurn();
        Bukkit.getPluginManager().registerEvents(instance, plugin);
        AbilityTickManager.register(instance);
    }

    public static void stop() {
        BURNS.clear();
        SUPPRESSED_KNOCKBACKS.clear();
        ABSORBERS.clear();
        if (instance != null) {
            AbilityTickManager.unregister(instance);
            HandlerList.unregisterAll(instance);
            instance = null;
        }
    }

    public enum BurnProfile {
        DEVIL_BOOTS,
        FLAME_BRAND,
        FIRE_FIGHT_WITH_FIRE
    }

    public static void addStack(LivingEntity target, Player source, BurnProfile profile, int fireTicks) {
        addStacks(target, source, profile, 1, fireTicks);
    }

    public static void addStacks(LivingEntity target, Player source, BurnProfile profile, int stacks, int fireTicks) {
        updateBurn(target, source, profile, stacks, fireTicks);
    }

    private static void updateBurn(LivingEntity target, Player source, BurnProfile profile, int stackDelta, int fireTicks) {
        if (target == null || target.isDead() || source == null || fireTicks <= 0) {
            return;
        }
        if (profile == null || stackDelta < 0) {
            return;
        }
        BurnState state = BURNS.computeIfAbsent(target.getUniqueId(), ignored -> new BurnState());
        state.stacks += stackDelta;
        state.profiles.add(profile);
        state.sources.put(profile, source.getUniqueId());
        state.lastSeenName = target.getName();
        target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
    }

    public static void registerAbsorber(Player player) {
        if (player != null) {
            ABSORBERS.add(player.getUniqueId());
        }
    }

    public static void unregisterAbsorber(Player player) {
        if (player != null) {
            ABSORBERS.remove(player.getUniqueId());
        }
    }

    public static int getStacks(LivingEntity target) {
        if (target == null) {
            return 0;
        }
        BurnState state = BURNS.get(target.getUniqueId());
        return state != null ? state.stacks : 0;
    }

    public static void clear(LivingEntity target) {
        if (target != null) {
            BURNS.remove(target.getUniqueId());
            SUPPRESSED_KNOCKBACKS.remove(target.getUniqueId());
            ABSORBERS.remove(target.getUniqueId());
        }
    }

    public static void clearAll() {
        BURNS.clear();
        SUPPRESSED_KNOCKBACKS.clear();
        ABSORBERS.clear();
    }

    @Override
    public void onTick(int tick) {
        if (tick % DAMAGE_INTERVAL_TICKS == 0) {
            applyBurnDamage(tick);
        }
        SUPPRESSED_KNOCKBACKS.entrySet().removeIf(entry -> entry.getValue().expireTick < tick);
    }

    private void applyBurnDamage(int tick) {
        Iterator<Map.Entry<UUID, BurnState>> iterator = BURNS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BurnState> entry = iterator.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity target) || target.isDead()
                    || target.isInWater() || target.getFireTicks() <= 0) {
                iterator.remove();
                continue;
            }
            BurnState state = entry.getValue();
            double maxHealth = getMaxHealth(target);
            double totalHealingOrDamage = calculateTotalDamage(state, maxHealth);
            if (ABSORBERS.contains(target.getUniqueId())) {
                heal(target, totalHealingOrDamage);
            } else {
                applyProfileDamage(target, state, maxHealth, tick);
            }
            ParticleUtil.spawnParticle(target.getWorld(), Particle.FLAME, target.getLocation().clone().add(0, 1.0, 0),
                    Math.min(48, 4 + state.stacks * 2), 0.35, 0.45, 0.35, 0.03, 2, 64);
        }
    }

    private double calculateTotalDamage(BurnState state, double maxHealth) {
        double total = 0.0;
        if (state.profiles.contains(BurnProfile.DEVIL_BOOTS)) {
            total += DEVIL_BOOTS_DAMAGE_PER_STACK * state.stacks;
        }
        if (state.profiles.contains(BurnProfile.FLAME_BRAND)) {
            total += maxHealth * FLAME_BRAND_DAMAGE_PER_STACK_RATIO * state.stacks;
        }
        if (state.profiles.contains(BurnProfile.FIRE_FIGHT_WITH_FIRE)) {
            total += maxHealth * FIRE_FIGHT_FIXED_DAMAGE_PER_STACK_RATIO * state.stacks;
        }
        return total;
    }

    private void applyProfileDamage(LivingEntity target, BurnState state, double maxHealth, int tick) {
        double totalDamage = calculateTotalDamage(state, maxHealth);
        if (totalDamage <= 0.0) {
            return;
        }
        suppressKnockback(target, tick);
        if (state.profiles.contains(BurnProfile.DEVIL_BOOTS)) {
            applyEventDamageNoKnockback(target, Bukkit.getPlayer(state.sources.get(BurnProfile.DEVIL_BOOTS)),
                    DEVIL_BOOTS_DAMAGE_PER_STACK * state.stacks);
        }
        if (state.profiles.contains(BurnProfile.FLAME_BRAND)) {
            applyEventDamageNoKnockback(target, Bukkit.getPlayer(state.sources.get(BurnProfile.FLAME_BRAND)),
                    maxHealth * FLAME_BRAND_DAMAGE_PER_STACK_RATIO * state.stacks);
        }
        if (state.profiles.contains(BurnProfile.FIRE_FIGHT_WITH_FIRE)) {
            applyFixedDamage(target, maxHealth * FIRE_FIGHT_FIXED_DAMAGE_PER_STACK_RATIO * state.stacks);
        }
    }

    private void suppressKnockback(LivingEntity target, int tick) {
        SUPPRESSED_KNOCKBACKS.put(target.getUniqueId(),
                new SuppressedKnockback(target.getVelocity().clone(), tick + 2));
    }

    private void applyEventDamageNoKnockback(LivingEntity target, Player source, double damage) {
        if (damage <= 0.0) {
            return;
        }
        target.setNoDamageTicks(0);
        if (source != null && source.isOnline()) {
            target.damage(damage, source);
        } else {
            target.damage(damage);
        }
    }

    private void applyFixedDamage(LivingEntity target, double damage) {
        if (damage <= 0.0) {
            return;
        }
        double nextHealth = target.getHealth() - damage;
        if (nextHealth <= 0.0) {
            target.setHealth(0.0);
        } else {
            target.setHealth(nextHealth);
        }
    }

    private void heal(LivingEntity target, double amount) {
        if (amount <= 0.0 || target.isDead()) {
            return;
        }
        double max = getMaxHealth(target);
        target.setHealth(Math.min(max, target.getHealth() + amount));
    }

    private double getMaxHealth(LivingEntity target) {
        AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        return maxHealth != null ? maxHealth.getValue() : Math.max(1.0, target.getHealth());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityKnockback(EntityKnockbackEvent event) {
        SuppressedKnockback suppressed = SUPPRESSED_KNOCKBACKS.get(event.getEntity().getUniqueId());
        if (suppressed == null || AbilityTickManager.getGlobalTick() > suppressed.expireTick) {
            return;
        }
        event.setCancelled(true);
        event.getEntity().setVelocity(suppressed.velocity);
    }

    private static final class BurnState {
        private int stacks;
        private final EnumSet<BurnProfile> profiles = EnumSet.noneOf(BurnProfile.class);
        private final EnumMap<BurnProfile, UUID> sources = new EnumMap<>(BurnProfile.class);
        @SuppressWarnings("unused")
        private String lastSeenName;
    }

    private record SuppressedKnockback(Vector velocity, int expireTick) {
    }
}
