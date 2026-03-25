package com.abilitycombat.effect;

import com.abilitycombat.AbilityCombat;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class Slow {

    private static final long PERIOD_TICKS = 2L;
    private static final Map<UUID, SlowEntry> SLOWED = new HashMap<>();
    private static final String OUTGOING_DAMAGE_SOURCE_KEY = "slow";

    private static BukkitTask task;

    private static NamespacedKey movementSpeedKey;
    private static NamespacedKey flyingSpeedKey;
    private static NamespacedKey sneakingSpeedKey;
    private static NamespacedKey waterMovementKey;
    private static NamespacedKey terrainMovementKey;
    private static NamespacedKey gravityKey;
    private static NamespacedKey jumpStrengthKey;
    private static NamespacedKey blockBreakSpeedKey;
    private static NamespacedKey miningEfficiencyKey;
    private static NamespacedKey submergedMiningSpeedKey;
    private static NamespacedKey attackSpeedKey;

    private Slow() {
    }

    public static void start(AbilityCombat plugin) {
        if (plugin == null) {
            return;
        }
        initKeys(plugin);
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, Slow::tick, 1L, PERIOD_TICKS);
        }
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (SlowEntry entry : SLOWED.values()) {
            clearModifiers(entry.target);
            DamageModifier.removeOutgoing(entry.target, OUTGOING_DAMAGE_SOURCE_KEY);
        }
        SLOWED.clear();
    }

    public static void apply(LivingEntity target, int ticks) {
        apply(target, ticks, SlowProfile.uniform(20.0));
    }

    public static void apply(LivingEntity target, int ticks, int amplifier) {
        apply(target, ticks, SlowProfile.fromAmplifier(amplifier));
    }

    public static void apply(LivingEntity target, int ticks, double percent) {
        apply(target, ticks, SlowProfile.uniform(percent));
    }

    public static void apply(LivingEntity target, int ticks, SlowProfile profile) {
        if (target == null || ticks <= 0) {
            return;
        }
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null) {
            return;
        }
        if (task == null) {
            start(plugin);
        }
        long now = System.currentTimeMillis();
        long until = now + ticks * 50L;
        SlowProfile resolvedProfile = profile != null ? profile : SlowProfile.uniform(20.0);
        UUID uuid = target.getUniqueId();
        SlowEntry entry = SLOWED.get(uuid);
        if (entry == null) {
            entry = new SlowEntry(target);
            SLOWED.put(uuid, entry);
        }
        entry.until = Math.max(entry.until, until);
        entry.profile = entry.profile.merge(resolvedProfile);
        applyModifiers(target, entry.profile);
        DamageModifier.applyOutgoing(target, ticks, OUTGOING_DAMAGE_SOURCE_KEY, -entry.profile.outgoingDamagePercent);
    }

    public static boolean isSlowed(LivingEntity target) {
        if (target == null) {
            return false;
        }
        SlowEntry entry = SLOWED.get(target.getUniqueId());
        return entry != null && entry.until > System.currentTimeMillis();
    }

    public static void remove(LivingEntity target) {
        if (target == null) {
            return;
        }
        SLOWED.remove(target.getUniqueId());
        clearModifiers(target);
        DamageModifier.removeOutgoing(target, OUTGOING_DAMAGE_SOURCE_KEY);
    }

    private static void tick() {
        if (SLOWED.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, SlowEntry>> iterator = SLOWED.entrySet().iterator();
        while (iterator.hasNext()) {
            SlowEntry entry = iterator.next().getValue();
            LivingEntity target = entry.target;
            if (target == null || !target.isValid() || target.isDead()) {
                if (target != null) {
                    clearModifiers(target);
                    DamageModifier.removeOutgoing(target, OUTGOING_DAMAGE_SOURCE_KEY);
                }
                iterator.remove();
                continue;
            }
            if (entry.until <= now) {
                clearModifiers(target);
                DamageModifier.removeOutgoing(target, OUTGOING_DAMAGE_SOURCE_KEY);
                iterator.remove();
                continue;
            }
            applyModifiers(target, entry.profile);
        }
    }

    private static void applyModifiers(LivingEntity target, SlowProfile profile) {
        applyScalar(target, Attribute.MOVEMENT_SPEED, movementSpeedKey, negativePercent(profile.movementSpeedPercent));
        applyScalar(target, Attribute.FLYING_SPEED, flyingSpeedKey, negativePercent(profile.flyingSpeedPercent));
        applyScalar(target, Attribute.SNEAKING_SPEED, sneakingSpeedKey, negativePercent(profile.sneakingSpeedPercent));
        applyScalar(target, Attribute.WATER_MOVEMENT_EFFICIENCY, waterMovementKey,
                negativePercent(profile.waterMovementPercent));
        applyScalar(target, Attribute.MOVEMENT_EFFICIENCY, terrainMovementKey,
                negativePercent(profile.terrainMovementPercent));
        applyScalar(target, Attribute.JUMP_STRENGTH, jumpStrengthKey, negativePercent(profile.jumpStrengthPercent));
        applyScalar(target, Attribute.BLOCK_BREAK_SPEED, blockBreakSpeedKey, negativePercent(profile.blockBreakPercent));
        applyScalar(target, Attribute.MINING_EFFICIENCY, miningEfficiencyKey,
                negativePercent(profile.miningEfficiencyPercent));
        applyScalar(target, Attribute.SUBMERGED_MINING_SPEED, submergedMiningSpeedKey,
                negativePercent(profile.submergedMiningPercent));
        applyScalar(target, Attribute.ATTACK_SPEED, attackSpeedKey, negativePercent(profile.attackSpeedPercent));
        applyScalar(target, Attribute.GRAVITY, gravityKey, positivePercent(profile.gravityPercent));
    }

    private static void clearModifiers(LivingEntity target) {
        removeModifier(target, Attribute.MOVEMENT_SPEED, movementSpeedKey);
        removeModifier(target, Attribute.FLYING_SPEED, flyingSpeedKey);
        removeModifier(target, Attribute.SNEAKING_SPEED, sneakingSpeedKey);
        removeModifier(target, Attribute.WATER_MOVEMENT_EFFICIENCY, waterMovementKey);
        removeModifier(target, Attribute.MOVEMENT_EFFICIENCY, terrainMovementKey);
        removeModifier(target, Attribute.JUMP_STRENGTH, jumpStrengthKey);
        removeModifier(target, Attribute.BLOCK_BREAK_SPEED, blockBreakSpeedKey);
        removeModifier(target, Attribute.MINING_EFFICIENCY, miningEfficiencyKey);
        removeModifier(target, Attribute.SUBMERGED_MINING_SPEED, submergedMiningSpeedKey);
        removeModifier(target, Attribute.ATTACK_SPEED, attackSpeedKey);
        removeModifier(target, Attribute.GRAVITY, gravityKey);
    }

    private static void applyScalar(LivingEntity target, Attribute attribute, NamespacedKey key, double amount) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance == null || key == null) {
            return;
        }
        instance.removeModifier(key);
        if (Math.abs(amount) < 1.0E-6) {
            return;
        }
        instance.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_SCALAR));
    }

    private static void removeModifier(LivingEntity target, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance == null || key == null) {
            return;
        }
        instance.removeModifier(key);
    }

    private static double negativePercent(double percent) {
        return -clampPercent(percent, 95.0) / 100.0;
    }

    private static double positivePercent(double percent) {
        return clampPercent(percent, 500.0) / 100.0;
    }

    private static double clampPercent(double percent, double max) {
        if (!Double.isFinite(percent)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(max, percent));
    }

    private static void initKeys(AbilityCombat plugin) {
        if (movementSpeedKey != null) {
            return;
        }
        movementSpeedKey = new NamespacedKey(plugin, "slow_movement_speed");
        flyingSpeedKey = new NamespacedKey(plugin, "slow_flying_speed");
        sneakingSpeedKey = new NamespacedKey(plugin, "slow_sneaking_speed");
        waterMovementKey = new NamespacedKey(plugin, "slow_water_movement");
        terrainMovementKey = new NamespacedKey(plugin, "slow_terrain_movement");
        gravityKey = new NamespacedKey(plugin, "slow_gravity");
        jumpStrengthKey = new NamespacedKey(plugin, "slow_jump_strength");
        blockBreakSpeedKey = new NamespacedKey(plugin, "slow_block_break_speed");
        miningEfficiencyKey = new NamespacedKey(plugin, "slow_mining_efficiency");
        submergedMiningSpeedKey = new NamespacedKey(plugin, "slow_submerged_mining_speed");
        attackSpeedKey = new NamespacedKey(plugin, "slow_attack_speed");
    }

    private static final class SlowEntry {
        private final LivingEntity target;
        private long until;
        private SlowProfile profile;

        private SlowEntry(LivingEntity target) {
            this.target = target;
            this.until = System.currentTimeMillis();
            this.profile = SlowProfile.uniform(0.0);
        }
    }

    public static final class SlowProfile {
        private final double movementSpeedPercent;
        private final double flyingSpeedPercent;
        private final double sneakingSpeedPercent;
        private final double waterMovementPercent;
        private final double terrainMovementPercent;
        private final double jumpStrengthPercent;
        private final double gravityPercent;
        private final double blockBreakPercent;
        private final double miningEfficiencyPercent;
        private final double submergedMiningPercent;
        private final double attackSpeedPercent;
        private final double outgoingDamagePercent;

        private SlowProfile(double movementSpeedPercent, double flyingSpeedPercent, double sneakingSpeedPercent,
                double waterMovementPercent, double terrainMovementPercent, double jumpStrengthPercent,
                double gravityPercent, double blockBreakPercent, double miningEfficiencyPercent,
                double submergedMiningPercent, double attackSpeedPercent, double outgoingDamagePercent) {
            this.movementSpeedPercent = movementSpeedPercent;
            this.flyingSpeedPercent = flyingSpeedPercent;
            this.sneakingSpeedPercent = sneakingSpeedPercent;
            this.waterMovementPercent = waterMovementPercent;
            this.terrainMovementPercent = terrainMovementPercent;
            this.jumpStrengthPercent = jumpStrengthPercent;
            this.gravityPercent = gravityPercent;
            this.blockBreakPercent = blockBreakPercent;
            this.miningEfficiencyPercent = miningEfficiencyPercent;
            this.submergedMiningPercent = submergedMiningPercent;
            this.attackSpeedPercent = attackSpeedPercent;
            this.outgoingDamagePercent = outgoingDamagePercent;
        }

        public static SlowProfile uniform(double percent) {
            double value = clampPercent(percent, 95.0);
            return new SlowProfile(value, value, value, value, value, value, value, value, value, value, value,
                    value);
        }

        public static SlowProfile fromAmplifier(int amplifier) {
            int clampedAmplifier = Math.max(0, Math.min(4, amplifier));
            return builder()
                    .movementSpeed(18.0 * (clampedAmplifier + 1))
                    .flyingSpeed(12.0 * (clampedAmplifier + 1))
                    .sneakingSpeed(18.0 * (clampedAmplifier + 1))
                    .waterMovement(18.0 * (clampedAmplifier + 1))
                    .terrainMovement(16.0 * (clampedAmplifier + 1))
                    .jumpStrength(12.0 * (clampedAmplifier + 1))
                    .gravity(18.0 * (clampedAmplifier + 1))
                    .blockBreak(25.0 * (clampedAmplifier + 1))
                    .miningEfficiency(25.0 * (clampedAmplifier + 1))
                    .submergedMining(25.0 * (clampedAmplifier + 1))
                    .attackSpeed(20.0 * (clampedAmplifier + 1))
                    .outgoingDamage(12.0 * (clampedAmplifier + 1))
                    .build();
        }

        public static Builder builder() {
            return new Builder();
        }

        private SlowProfile merge(SlowProfile other) {
            return new SlowProfile(
                    Math.max(this.movementSpeedPercent, other.movementSpeedPercent),
                    Math.max(this.flyingSpeedPercent, other.flyingSpeedPercent),
                    Math.max(this.sneakingSpeedPercent, other.sneakingSpeedPercent),
                    Math.max(this.waterMovementPercent, other.waterMovementPercent),
                    Math.max(this.terrainMovementPercent, other.terrainMovementPercent),
                    Math.max(this.jumpStrengthPercent, other.jumpStrengthPercent),
                    Math.max(this.gravityPercent, other.gravityPercent),
                    Math.max(this.blockBreakPercent, other.blockBreakPercent),
                    Math.max(this.miningEfficiencyPercent, other.miningEfficiencyPercent),
                    Math.max(this.submergedMiningPercent, other.submergedMiningPercent),
                    Math.max(this.attackSpeedPercent, other.attackSpeedPercent),
                    Math.max(this.outgoingDamagePercent, other.outgoingDamagePercent));
        }

        public static final class Builder {
            private double movementSpeedPercent;
            private double flyingSpeedPercent;
            private double sneakingSpeedPercent;
            private double waterMovementPercent;
            private double terrainMovementPercent;
            private double jumpStrengthPercent;
            private double gravityPercent;
            private double blockBreakPercent;
            private double miningEfficiencyPercent;
            private double submergedMiningPercent;
            private double attackSpeedPercent;
            private double outgoingDamagePercent;

            private Builder() {
            }

            public Builder movementSpeed(double percent) {
                this.movementSpeedPercent = percent;
                return this;
            }

            public Builder flyingSpeed(double percent) {
                this.flyingSpeedPercent = percent;
                return this;
            }

            public Builder sneakingSpeed(double percent) {
                this.sneakingSpeedPercent = percent;
                return this;
            }

            public Builder waterMovement(double percent) {
                this.waterMovementPercent = percent;
                return this;
            }

            public Builder terrainMovement(double percent) {
                this.terrainMovementPercent = percent;
                return this;
            }

            public Builder jumpStrength(double percent) {
                this.jumpStrengthPercent = percent;
                return this;
            }

            public Builder gravity(double percent) {
                this.gravityPercent = percent;
                return this;
            }

            public Builder blockBreak(double percent) {
                this.blockBreakPercent = percent;
                return this;
            }

            public Builder miningEfficiency(double percent) {
                this.miningEfficiencyPercent = percent;
                return this;
            }

            public Builder submergedMining(double percent) {
                this.submergedMiningPercent = percent;
                return this;
            }

            public Builder attackSpeed(double percent) {
                this.attackSpeedPercent = percent;
                return this;
            }

            public Builder outgoingDamage(double percent) {
                this.outgoingDamagePercent = percent;
                return this;
            }

            public SlowProfile build() {
                return new SlowProfile(
                        clampPercent(movementSpeedPercent, 95.0),
                        clampPercent(flyingSpeedPercent, 95.0),
                        clampPercent(sneakingSpeedPercent, 95.0),
                        clampPercent(waterMovementPercent, 95.0),
                        clampPercent(terrainMovementPercent, 95.0),
                        clampPercent(jumpStrengthPercent, 95.0),
                        clampPercent(gravityPercent, 500.0),
                        clampPercent(blockBreakPercent, 95.0),
                        clampPercent(miningEfficiencyPercent, 95.0),
                        clampPercent(submergedMiningPercent, 95.0),
                        clampPercent(attackSpeedPercent, 95.0),
                        clampPercent(outgoingDamagePercent, 95.0));
            }
        }
    }
}
