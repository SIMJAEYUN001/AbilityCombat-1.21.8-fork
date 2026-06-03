package com.abilitycombat.effect;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.game.GameManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public final class CrowdControl {

    private static final long CLEANUP_PERIOD_TICKS = 20L;

    private static BukkitTask cleanupTask;

    private CrowdControl() {
    }

    public static void start(AbilityCombat plugin) {
        if (plugin == null || cleanupTask != null) {
            return;
        }
        cleanupTask = plugin.getServer().getScheduler().runTaskTimer(plugin, CrowdControl::cleanup,
                CLEANUP_PERIOD_TICKS, CLEANUP_PERIOD_TICKS);
    }

    public static void stop() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        clearAll();
    }

    public static void clearAll() {
        Stun.clear();
        Freeze.clear();
        Bind.clear();
        Disarm.clear();
    }

    public static void handleDamageByEntity(EntityDamageByEntityEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }
        LivingEntity source = resolveDamageSource(event.getDamager());
        if (source != null && blocksOutgoingDamage(source)) {
            event.setCancelled(true);
        }
    }

    public static boolean blocksOutgoingDamage(LivingEntity source) {
        if (source == null) {
            return false;
        }
        return Stun.isStunned(source) || Disarm.isDisarmed(source);
    }

    public static LivingEntity resolveDamageSource(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return null;
    }

    static void cancelDashState(LivingEntity target) {
        if (!(target instanceof Player player)) {
            return;
        }
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin != null && plugin.getSprintHudService() != null) {
            plugin.getSprintHudService().cancelDashState(player);
        }
    }

    static void ensureRunning() {
        if (cleanupTask == null) {
            start(AbilityCombat.getPlugin());
        }
    }

    static void refreshMovementLock(LivingEntity target) {
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
        long maxUntil = Math.max(Stun.getEndTime(uuid), Math.max(Freeze.getEndTime(uuid), Bind.getEndTime(uuid)));
        if (maxUntil <= now) {
            gameManager.unlockMovement(target);
        } else {
            gameManager.setMovementLockUntil(target, maxUntil);
        }
    }

    private static void cleanup() {
        long now = System.currentTimeMillis();
        Stun.cleanup(now);
        Freeze.cleanup(now);
        Bind.cleanup(now);
        Disarm.cleanup(now);
    }
}
