package com.abilitycombat.event;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.utils.collection.QueueOnIterateHashSet;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Central event dispatcher for abilities.
 * Reduces Bukkit listener overhead by dispatching events only to active
 * abilities.
 */
public final class EventBridge implements Listener {

    private final Map<Class<? extends Event>, QueueOnIterateHashSet<AbilityBase>> subscribers = new HashMap<>();

    public void subscribe(Class<? extends Event> eventClass, AbilityBase ability) {
        if (eventClass == null || ability == null) {
            return;
        }
        subscribers.computeIfAbsent(eventClass, k -> new QueueOnIterateHashSet<>()).add(ability);
    }

    public void unsubscribe(Class<? extends Event> eventClass, AbilityBase ability) {
        if (eventClass == null || ability == null) {
            return;
        }
        QueueOnIterateHashSet<AbilityBase> set = subscribers.get(eventClass);
        if (set != null) {
            set.remove(ability);
        }
    }

    public void unsubscribeAll(AbilityBase ability) {
        if (ability == null) {
            return;
        }
        for (QueueOnIterateHashSet<AbilityBase> set : subscribers.values()) {
            set.remove(ability);
        }
    }

    public void clear() {
        for (QueueOnIterateHashSet<AbilityBase> set : subscribers.values()) {
            set.clear();
        }
        subscribers.clear();
    }

    // =============== High-frequency event handlers ===============

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        dispatch(event, EntityDamageByEntityEvent.class);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        dispatch(event, EntityDamageEvent.class);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only dispatch if position actually changed (not just head rotation)
        if (event.hasChangedPosition()) {
            dispatch(event, PlayerMoveEvent.class);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        dispatch(event, ProjectileHitEvent.class);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        dispatch(event, EntityShootBowEvent.class);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        dispatch(event, EntityTargetLivingEntityEvent.class);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        dispatch(event, PlayerInteractEvent.class);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        dispatch(event, PlayerAnimationEvent.class);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        dispatch(event, PlayerToggleSneakEvent.class);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        dispatch(event, PlayerToggleFlightEvent.class);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        dispatch(event, PlayerItemHeldEvent.class);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        dispatch(event, BlockBreakEvent.class);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        dispatch(event, PlayerTeleportEvent.class);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        dispatch(event, PlayerDeathEvent.class);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        dispatch(event, PlayerInteractAtEntityEvent.class);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        dispatch(event, EntityRegainHealthEvent.class);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        dispatch(event, EntityExplodeEvent.class);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        dispatch(event, BlockExplodeEvent.class);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerFish(PlayerFishEvent event) {
        dispatch(event, PlayerFishEvent.class);
    }

    // =============== Dispatch logic ===============

    private <T extends Event> void dispatch(T event, Class<T> eventClass) {
        QueueOnIterateHashSet<AbilityBase> set = subscribers.get(eventClass);
        if (set == null || set.isEmpty()) {
            return;
        }
        set.forEach(ability -> {
            if (ability == null || ability.isDestroyed()) {
                return;
            }
            try {
                ability.handleBridgeEvent(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
