package com.abilitycombat.effect;

import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Disarm {

    private static final Map<UUID, Long> DISARM_ENDS = new HashMap<>();

    private Disarm() {
    }

    public static void apply(LivingEntity target, int ticks) {
        if (target == null || ticks <= 0) {
            return;
        }
        long until = System.currentTimeMillis() + ticks * 50L;
        UUID uuid = target.getUniqueId();
        Long current = DISARM_ENDS.get(uuid);
        if (current == null || current < until) {
            DISARM_ENDS.put(uuid, until);
        }
    }

    public static boolean isDisarmed(LivingEntity target) {
        if (target == null) {
            return false;
        }
        return getEndTime(target.getUniqueId()) > 0L;
    }

    public static void remove(LivingEntity target) {
        if (target == null) {
            return;
        }
        DISARM_ENDS.remove(target.getUniqueId());
    }

    static long getEndTime(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }
        Long until = DISARM_ENDS.get(uuid);
        if (until == null) {
            return 0L;
        }
        if (until <= System.currentTimeMillis()) {
            DISARM_ENDS.remove(uuid);
            return 0L;
        }
        return until;
    }
}
