package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.entity.CustomEntity;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
    private static final int MAX_TICKS = 80;
    private static final double HITBOX = 1.15;

    public GustArrow(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        giveBowAndArrows();
        subscribeEvent(EntityShootBowEvent.class);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityShootBowEvent shootEvent) {
            onShoot(shootEvent);
        }
    }

    private void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (!(event.getProjectile() instanceof Arrow arrow)) {
            return;
        }
        Location start = arrow.getLocation();
        Vector velocity = arrow.getVelocity().multiply(SPEED_MULTIPLIER);
        arrow.remove();
        event.setCancelled(true);

        GustProjectile projectile = new GustProjectile(start, velocity);
        projectile.setSource(player);
        projectile.spawn();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.8f, 1.25f);
    }

    private class GustProjectile extends CustomEntity {

        private final Set<UUID> hitTargets = new HashSet<>();

        private GustProjectile(Location location, Vector velocity) {
            super(location.getWorld(), location);
            setVelocity(velocity);
            setGravity(0.0);
            setDrag(0.01);
            setMaxAge(MAX_TICKS);
            resizeBoundingBox(-HITBOX, -0.45, -HITBOX, HITBOX, 0.45, HITBOX);
        }

        @Override
        protected void onTick() {
            Location loc = getLocation();
            ParticleUtil.spawnParticle(loc.getWorld(), Particle.CLOUD, loc, 10, 0.55, 0.2, 0.55, 0.02, 1, 64);
            ParticleUtil.spawnParticle(loc.getWorld(), Particle.SWEEP_ATTACK, loc, 1, 0.15, 0.05, 0.15, 0.0, 1, 64);
        }

        @Override
        protected boolean onHitEntity(LivingEntity entity, Location hitLocation) {
            if (!LocationUtil.isValidTarget(getPlayer(), entity) || !hitTargets.add(entity.getUniqueId())) {
                return false;
            }
            Vector knock = getVelocity();
            if (knock.lengthSquared() <= 1.0E-6) {
                knock = entity.getLocation().toVector().subtract(hitLocation.toVector());
            }
            if (knock.lengthSquared() > 1.0E-6) {
                knock.normalize().multiply(KNOCKBACK).setY(0.38);
                entity.setVelocity(knock);
            }
            entity.setNoDamageTicks(0);
            entity.damage(DAMAGE, getPlayer());
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.8f, 1.1f);
            return false;
        }

        @Override
        protected boolean onHitBlock(Block block, Location hitLocation) {
            ParticleUtil.spawnParticle(getWorld(), Particle.CLOUD, hitLocation, 18, 0.65, 0.3, 0.65, 0.03, 1, 64);
            getWorld().playSound(hitLocation, Sound.ENTITY_BREEZE_WIND_BURST, 0.7f, 0.85f);
            return true;
        }
    }
}
