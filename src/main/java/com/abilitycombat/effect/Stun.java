package com.abilitycombat.effect;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.game.GameManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

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
        long now = System.currentTimeMillis();
        long until = now + ticks * 50L;
        UUID uuid = target.getUniqueId();
        Long current = STUN_ENDS.get(uuid);
        if (current == null || current < until) {
            STUN_ENDS.put(uuid, until);
        }
        if (target instanceof Player player) {
            if (AbilityCombat.getPlugin() != null && AbilityCombat.getPlugin().getSprintHudService() != null) {
                AbilityCombat.getPlugin().getSprintHudService().cancelDashState(player);
            }
        }
        refreshMovementLock(target);
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
        refreshMovementLock(target);
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
        long maxUntil = Math.max(getEndTime(uuid), Freeze.getEndTime(uuid));
        if (maxUntil <= now) {
            gameManager.unlockMovement(target);
        } else {
            gameManager.setMovementLockUntil(target, maxUntil);
        }
    }
}
