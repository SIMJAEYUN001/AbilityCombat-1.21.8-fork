package com.abilitycombat.effect;

import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Stun {

    private static final Map<UUID, Long> STUN_ENDS = new HashMap<>();

    private Stun() {
    }

    public static void apply(LivingEntity target, int ticks) {
        if (target == null || ticks <= 0) {
            return;
        }
        CrowdControl.ensureRunning();
        long now = System.currentTimeMillis();
        long until = now + ticks * 50L;
        UUID uuid = target.getUniqueId();
        Long current = STUN_ENDS.get(uuid);
        if (current == null || current < until) {
            STUN_ENDS.put(uuid, until);
        }
        CrowdControl.cancelDashState(target);
        CrowdControl.refreshMovementLock(target);
    }

    public static boolean isStunned(LivingEntity target) {
        if (target == null) {
            return false;
        }
        return getEndTime(target.getUniqueId()) > 0L;
    }

    public static void remove(LivingEntity target) {
        if (target == null) {
            return;
        }
        STUN_ENDS.remove(target.getUniqueId());
        CrowdControl.refreshMovementLock(target);
    }

    static long getEndTime(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }
        Long until = STUN_ENDS.get(uuid);
        if (until == null) {
            return 0L;
        }
        if (until <= System.currentTimeMillis()) {
            STUN_ENDS.remove(uuid);
            return 0L;
        }
        return until;
    }

    static void cleanup(long now) {
        STUN_ENDS.values().removeIf(until -> until <= now);
    }

    static void clear() {
        STUN_ENDS.clear();
    }

}
