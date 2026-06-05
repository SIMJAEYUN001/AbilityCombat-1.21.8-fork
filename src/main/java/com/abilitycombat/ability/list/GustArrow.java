package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

@AbilityManifest(name = "돌풍화살 (GustArrow)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[활 발사 - 돌풍구]",
        "§7활을 발사하면 화살 대신 §b돌풍구§7를 쏘아냅니다",
        "§7돌풍구에 휩쓸린 적은 진행 방향으로 밀려나며 §c10 피해§7를 받습니다"
}, summarize = {
        "§7활 발사§f: 돌풍구 투사체",
        "§7적중§f: 밀쳐냄 + 피해 10"
})
public class GustArrow extends AbilityBase {

    private static final double DAMAGE = 10.0;
    private static final double SPEED_MULTIPLIER = 1.15;
    private static final double KNOCKBACK = 1.65;
    private static final double HIT_RADIUS = 2.25;

    public GustArrow(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        giveBowAndArrows();
        subscribeEvent(EntityShootBowEvent.class);
        subscribeEvent(ProjectileHitEvent.class);
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityShootBowEvent shootEvent) {
            onShoot(shootEvent);
        } else if (event instanceof ProjectileHitEvent hitEvent) {
            onProjectileHit(hitEvent);
        } else if (event instanceof EntityDamageByEntityEvent damageEvent) {
            onWindChargeDamage(damageEvent);
        }
    }

    private void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (!(event.getProjectile() instanceof Arrow arrow)) {
            return;
        }
        Vector velocity = arrow.getVelocity().multiply(SPEED_MULTIPLIER);
        arrow.remove();
        event.setCancelled(true);

        WindCharge charge = player.launchProjectile(WindCharge.class, velocity);
        charge.getPersistentDataContainer().set(gustKey(), PersistentDataType.BYTE, (byte) 1);
        charge.setShooter(player);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.8f, 1.25f);
    }

    private void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractWindCharge charge) || !isGustCharge(charge)) {
            return;
        }
        Player owner = ownerOf(charge);
        if (owner == null) {
            return;
        }
        Location center = event.getHitEntity() instanceof LivingEntity living
                ? living.getLocation()
                : charge.getLocation();
        Vector direction = charge.getVelocity();
        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(center, HIT_RADIUS, owner, entity -> true)) {
            applyGustHit(owner, target, center, direction);
        }
        center.getWorld().playSound(center, Sound.ENTITY_BREEZE_WIND_BURST, 0.9f, 1.0f);
    }

    private void onWindChargeDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof AbstractWindCharge charge && isGustCharge(charge)) {
            event.setCancelled(true);
        }
    }

    private void applyGustHit(Player owner, LivingEntity target, Location center, Vector direction) {
        if (!LocationUtil.isValidTarget(owner, target)) {
            return;
        }
        Vector knock = target.getLocation().toVector().subtract(center.toVector());
        if (knock.lengthSquared() <= 1.0E-6) {
            knock = direction.clone();
        }
        if (knock.lengthSquared() > 1.0E-6) {
            knock.normalize().multiply(KNOCKBACK).setY(0.42);
            target.setVelocity(knock);
        }
        target.setNoDamageTicks(0);
        target.damage(DAMAGE, owner);
    }

    private Player ownerOf(AbstractWindCharge charge) {
        ProjectileSource shooter = charge instanceof Projectile projectile ? projectile.getShooter() : null;
        return shooter instanceof Player player ? player : getPlayer();
    }

    private boolean isGustCharge(AbstractWindCharge charge) {
        return charge.getPersistentDataContainer().has(gustKey(), PersistentDataType.BYTE);
    }

    private NamespacedKey gustKey() {
        return new NamespacedKey(AbilityCombat.getPlugin(), "gust_arrow_wind_charge");
    }
}
