package com.abilitycombat.effect;

import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Bind {

    private static final Map<UUID, Long> BIND_ENDS = new HashMap<>();

    private Bind() {
    }

    public static void apply(LivingEntity target, int ticks) {
        if (target == null || ticks <= 0) {
            return;
        }
        CrowdControl.ensureRunning();
        long until = System.currentTimeMillis() + ticks * 50L;
        UUID uuid = target.getUniqueId();
        Long current = BIND_ENDS.get(uuid);
        if (current == null || current < until) {
            BIND_ENDS.put(uuid, until);
        }
        CrowdControl.cancelDashState(target);
        CrowdControl.refreshMovementLock(target);
    }

    public static boolean isBound(LivingEntity target) {
        if (target == null) {
            return false;
        }
        return getEndTime(target.getUniqueId()) > 0L;
    }

    public static void remove(LivingEntity target) {
        if (target == null) {
            return;
        }
        BIND_ENDS.remove(target.getUniqueId());
        CrowdControl.refreshMovementLock(target);
    }

    static long getEndTime(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }
        Long until = BIND_ENDS.get(uuid);
        if (until == null) {
            return 0L;
        }
        if (until <= System.currentTimeMillis()) {
            BIND_ENDS.remove(uuid);
            return 0L;
        }
        return until;
    }

    static void cleanup(long now) {
        BIND_ENDS.values().removeIf(until -> until <= now);
    }

    static void clear() {
        BIND_ENDS.clear();
    }
}
