package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityDescriptor;

public record InspiredAbilitySpec(
        AbilityDescriptor descriptor,
        Style style,
        CrowdControlType crowdControl,
        int cooldownSeconds,
        double damage,
        double range,
        double radius,
        int crowdControlTicks,
        double heal,
        double knockback) {

    static final int BLEED_DAMAGE_INTERVAL_TICKS = 10;
    static final int DASH_SPEED_TICKS = 50;
    static final int ASSASSIN_BLEED_TICKS = 80;
    static final double ASSASSIN_BLEED_DAMAGE = 0.45;
    static final int ASSASSIN_SPEED_TICKS = 60;
    static final int BLACK_HOLE_BLINDNESS_TICKS = 45;
    static final int CURSE_BLEED_TICKS = 100;
    static final double CURSE_BLEED_DAMAGE = 0.55;
    static final int CURSE_INFECTION_TICKS = 80;
    static final int GUARD_EFFECT_TICKS = 100;
    static final int ALLY_SPEED_TICKS = 120;
    static final int ALLY_DEFENSE_TICKS = 60;
    static final int SWAP_NAUSEA_TICKS = 70;
    static final int FROST_SLOWNESS_TICKS = 70;
    static final int SOUL_WITHER_TICKS = 60;
    static final double EXECUTE_THRESHOLD = 0.35;
    static final double EXECUTE_LOW_HEALTH_MULTIPLIER = 1.8;
    static final double EXECUTE_NORMAL_MULTIPLIER = 0.85;
    static final int EXECUTE_GLOWING_TICKS = 90;
    static final int GAMBLE_BUFF_TICKS = 100;
    static final int GAMBLE_REGENERATION_TICKS = 80;
    static final double PORTAL_TARGET_DAMAGE_MULTIPLIER = 0.85;
    static final double PORTAL_MAX_DISTANCE = 8.0;
    static final int PORTAL_SPEED_TICKS = 60;
    static final double TELEPORT_BEHIND_DISTANCE = 1.3;
    static final int MARK_GLOWING_TICKS = 120;
    static final int MARK_WEAKNESS_TICKS = 70;
    static final int DEFLECT_EFFECT_TICKS = 80;
    static final int SUMMON_INFECTION_TICKS = 100;
    static final int SUMMON_SLOWNESS_TICKS = 80;
    static final double INFECTION_INCOMING_DAMAGE_DELTA = 25.0;
    static final int INFECTION_ROTATION_PERIOD_TICKS = 4;
    static final double INFECTION_ROTATION_CHANCE = 65.0;

    public enum Style {
        SINGLE,
        BLAST,
        NOVA,
        DASH,
        PULL,
        GUARD,
        ALLY,
        ASSASSIN,
        BLACK_HOLE,
        CURSE,
        SWAP,
        FROST,
        SOUL,
        EXECUTE,
        GAMBLE,
        PORTAL,
        MARK,
        DEFLECT,
        SUMMON,
        GLASS_CANNON
    }

    public enum CrowdControlType {
        NONE,
        STUN,
        BIND,
        DISARM,
        FREEZE
    }
}
