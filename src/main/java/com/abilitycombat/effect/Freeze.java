package com.abilitycombat.effect;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.game.GameManager;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Freeze {

    private static final Map<UUID, Long> FREEZE_ENDS = new HashMap<>();

    private Freeze() {
    }

    public static void apply(LivingEntity target, int ticks) {
        if (target == null || ticks <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long until = now + ticks * 50L;
        UUID uuid = target.getUniqueId();
        Long current = FREEZE_ENDS.get(uuid);
        if (current == null || current < until) {
            FREEZE_ENDS.put(uuid, until);
        }
        refreshMovementLock(target);
    }

    public static boolean isFrozen(LivingEntity target) {
        if (target == null) {
            return false;
        }
        return getEndTime(target.getUniqueId()) > 0L;
    }

    public static void remove(LivingEntity target) {
        if (target == null) {
            return;
        }
        FREEZE_ENDS.remove(target.getUniqueId());
        refreshMovementLock(target);
    }

    static long getEndTime(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }
        Long until = FREEZE_ENDS.get(uuid);
        if (until == null) {
            return 0L;
        }
        if (until <= System.currentTimeMillis()) {
            FREEZE_ENDS.remove(uuid);
            return 0L;
        }
        return until;
    }

    private static void refreshMovementLock(LivingEntity target) {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null) {
            return;
        }
        GameManager gameManager = plugin.getGameManager();
        if (gameManager == null) {
            return;
        }
        UUID uuid = target.getUniqueId();
        long now = System.currentTimeMillis();
        long maxUntil = Math.max(getEndTime(uuid), Stun.getEndTime(uuid));
        if (maxUntil <= now) {
            gameManager.unlockMovement(target);
        } else {
            gameManager.setMovementLockUntil(target, maxUntil);
        }
    }
}
