package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.entity.CustomEntity;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@AbilityManifest(name = "정조준 일격 (AimedStrike)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 정조준 일격]",
        "§f10칸§7 이상 떨어진 대상에게 활을 적중시키면",
        "§7대상을 향해 §6정조준 일격§7을 추가로 발사합니다.",
        "",
        "§7정조준 일격은 §c15의 피해§7를 입힙니다."
}, summarize = {
        "§7활 원거리 적중§f: 정조준 일격 발사 (피해 15)"
})
public class AimedStrike extends AbilityBase {

    private static final double MIN_DISTANCE = 10.0;
    private static final double SLASH_DAMAGE = 15.0;
    private static final double SLASH_SPEED = 0.6;
    private static final int SLASH_LIFETIME_TICKS = 100;
    private static final double SLASH_HITBOX_SIZE = 1.6;

    public AimedStrike(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        giveBowAndArrows();
        subscribeEvent(ProjectileHitEvent.class);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof ProjectileHitEvent) {
            onArrowHit((ProjectileHitEvent) event);
        }
    }

    private void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        if (!(arrow.getShooter() instanceof Player shooter) || !shooter.equals(getPlayer())) {
            return;
        }
        if (event.getHitEntity() == null) {
            return;
        }
        if (!(event.getHitEntity() instanceof LivingEntity target)) {
            return;
        }
        if (target.equals(shooter)) {
            return;
        }

        double distance = shooter.getLocation().distance(target.getLocation());
        if (distance < MIN_DISTANCE) {
            return;
        }

        // 검기 발사
        Location start = shooter.getEyeLocation();
        Vector direction = target.getEyeLocation().toVector().subtract(start.toVector()).normalize();
        Vector velocity = direction.multiply(SLASH_SPEED);

        Slash slash = new Slash(start, velocity);
        slash.setSource(shooter);
        slash.spawn();

    }

    private class Slash extends CustomEntity {

        private final Set<UUID> hitEntities = new HashSet<>();

        public Slash(Location start, Vector velocity) {
            super(start.getWorld(), start);
            setVelocity(velocity);
            setGravity(0.0);
            setDrag(0.0);
            setMaxAge(SLASH_LIFETIME_TICKS);
            setCollideEntities(false); // 엔티티 충돌 비활성화 (직접 처리)
            setCollideBlocks(false); // 블록 충돌 비활성화 (관통)
            resizeBoundingBox(-SLASH_HITBOX_SIZE, -0.3, -SLASH_HITBOX_SIZE, SLASH_HITBOX_SIZE,
                    0.3, SLASH_HITBOX_SIZE);
        }

        @Override
        protected void onTick() {
            // 파티클 이펙트
            int count = Math.max(1, (int) (SLASH_HITBOX_SIZE * 2));
            double spread = SLASH_HITBOX_SIZE * 0.5;
            ParticleUtil.spawnParticle(getWorld(), Particle.SWEEP_ATTACK, getLocation(), count, spread, 0, spread, 0, 1,
                    0);

            // 직접 엔티티 충돌 검사 (거리 기반)
            for (LivingEntity entity : com.abilitycombat.utils.LocationUtil.getNearbyLivingEntities(
                    getLocation(), SLASH_HITBOX_SIZE, getPlayer(), e -> !e.equals(getSource()))) {
                if (hitEntities.contains(entity.getUniqueId())) {
                    continue; // 이미 맞은 대상 제외
                }
                hitEntities.add(entity.getUniqueId());
                entity.setNoDamageTicks(0);
                entity.damage(SLASH_DAMAGE, getPlayer());
            }
        }
    }
}
