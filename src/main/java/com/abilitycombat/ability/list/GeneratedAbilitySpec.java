package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityDescriptor;

public record GeneratedAbilitySpec(
        AbilityDescriptor descriptor,
        Role role,
        Pattern pattern,
        CrowdControlType crowdControl,
        int cooldownSeconds,
        double damage,
        double range,
        double radius,
        int crowdControlTicks,
        double heal,
        double knockback) {

    public enum Role {
        ATTACK,
        DEFENSE,
        SUPPORT
    }

    public enum Pattern {
        STRIKE,
        BLAST,
        NOVA,
        DASH,
        GUARD,
        PULL
    }

    public enum CrowdControlType {
        NONE,
        STUN,
        BIND,
        DISARM,
        FREEZE
    }
}
