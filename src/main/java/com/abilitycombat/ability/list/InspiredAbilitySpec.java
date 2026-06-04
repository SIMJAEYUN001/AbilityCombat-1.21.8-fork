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
        REWIND,
        SWAP,
        FROST,
        SOUL,
        EXECUTE,
        GAMBLE,
        PORTAL,
        MARK,
        DEFLECT,
        SUMMON
    }

    public enum CrowdControlType {
        NONE,
        STUN,
        BIND,
        DISARM,
        FREEZE
    }
}
