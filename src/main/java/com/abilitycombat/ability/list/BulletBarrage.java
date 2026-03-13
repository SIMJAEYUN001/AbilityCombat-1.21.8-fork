package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationPool;
import com.abilitycombat.utils.VectorPool;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@AbilityManifest(name = "탄환세례 (BulletBarrage)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 탄환세례]§f §8(쿨타임: 15초)",
        "§7활을 쏴서 적중 시 대상을 공격하는",
        "§c유도 탄환 4개§7를 추가로 발사합니다.",
        "",
        "§7탄환 1개당 §c1의 고정 피해§7를 입힙니다.",
        "§8미적중 시 쿨타임이 적용되지 않습니다."
}, summarize = {
        "§7패시브§f: 화살 적중 시 유도 탄환 4발"
})
public class BulletBarrage extends AbilityBase {

    private static final int COOLDOWN_SECONDS = 15;
    private static final int BULLET_COUNT = 4;
    private static final double BULLET_DAMAGE = 1.0;
    private static final double BULLET_SPEED = 2.5;
    private static final int BULLET_MAX_TICKS = 60;
    private static final double HOMING_STRENGTH = 0.2;
    private static final double HIT_DISTANCE = 1.2;

    private final ActionbarCooldown cooldown = new ActionbarCooldown(COOLDOWN_SECONDS);
    private final List<BulletData> activeBullets = new ArrayList<>();

    public BulletBarrage(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        giveBowAndArrows();
        subscribeEvent(ProjectileHitEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        for (BulletData data : activeBullets) {
            if (data.bullet != null && !data.bullet.isDead()) {
                data.bullet.remove();
            }
        }
        activeBullets.clear();
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
        if (event.getHitEntity().equals(shooter)) {
            return;
        }
        if (cooldown.isCooldown()) {
            return;
        }

        spawnHomingBullets(shooter, target);
        cooldown.start();
    }

    private void spawnHomingBullets(Player shooter, LivingEntity target) {
        Location origin = shooter.getEyeLocation();
        shooter.getWorld().playSound(origin, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.5f);

        for (int i = 0; i < BULLET_COUNT; i++) {
            final int delay = i * 4;
            Bukkit.getScheduler().runTaskLater(AbilityCombat.getPlugin(), () -> {
                if (!isDestroyed() && target != null && !target.isDead()) {
                    spawnBullet(shooter.getEyeLocation(), target, shooter);
                }
            }, delay);
        }

        // 매니저 등록
        registerTick();
    }

    private void spawnBullet(Location startLoc, LivingEntity target, Player shooter) {
        Location targetLoc = target.getLocation();
        Vector initialDir = VectorPool.get(targetLoc.getX(), targetLoc.getY() + 1, targetLoc.getZ())
                .subtract(startLoc.toVector()).normalize();

        ArmorStand bullet = startLoc.getWorld().spawn(startLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setBasePlate(false);
            stand.setArms(true);
            stand.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_BUTTON));
            stand.setRightArmPose(new EulerAngle(Math.toRadians(-90), 0, 0));
        });
        AbilityCombat.markPiercingAbilityArmorStand(bullet);

        activeBullets.add(new BulletData(bullet, target, shooter, initialDir.clone()));

        // 매니저 등록
        registerTick();
    }

    private void processBullet(BulletData data) {
        ArmorStand bullet = data.bullet;
        LivingEntity target = data.target;
        Player shooter = data.shooter;

        if (bullet == null || bullet.isDead() || target == null || target.isDead()) {
            data.dead = true;
            return;
        }

        data.ticks++;
        if (data.ticks > BULLET_MAX_TICKS) {
            data.dead = true;
            return;
        }

        Location bulletLoc = bullet.getLocation();
        Vector toTarget = target.getLocation().add(0, 1, 0).toVector()
                .subtract(bulletLoc.toVector());

        if (toTarget.lengthSquared() > 0) {
            toTarget.normalize();
            Vector homing = VectorPool.copy(toTarget).multiply(HOMING_STRENGTH);
            data.direction.multiply(1 - HOMING_STRENGTH).add(homing);
            if (data.direction.lengthSquared() > 0) {
                data.direction.normalize();
            }
        }

        Vector delta = VectorPool.get().copy(data.direction).multiply(BULLET_SPEED);
        Location next = LocationPool.get(bulletLoc.getWorld(), bulletLoc.getX() + delta.getX(),
                bulletLoc.getY() + delta.getY(), bulletLoc.getZ() + delta.getZ(), bulletLoc.getYaw(),
                bulletLoc.getPitch());

        // 블록 충돌
        if (next.getBlock().getType().isSolid()) {
            data.dead = true;
            return;
        }

        next.setDirection(data.direction);
        bullet.teleport(next);

        // 엔티티 충돌
        for (LivingEntity entity : com.abilitycombat.utils.LocationUtil.getNearbyLivingEntities(bullet.getLocation(),
                HIT_DISTANCE, com.abilitycombat.utils.LocationUtil.withValidTarget(getPlayer(), null))) {
            if (entity.equals(shooter) || entity instanceof ArmorStand) {
                continue;
            }

            double newHealth = entity.getHealth() - BULLET_DAMAGE;
            if (newHealth <= 0) {
                entity.setHealth(0.01);
                entity.damage(0.01, shooter);
            } else {
                entity.setHealth(newHealth);
                entity.damage(0.01, shooter);
                entity.setNoDamageTicks(0);
            }

            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            data.dead = true;
            return;
        }
    }

    private static class BulletData {
        final ArmorStand bullet;
        final LivingEntity target;
        final Player shooter;
        Vector direction;
        int ticks;
        boolean dead;

        BulletData(ArmorStand bullet, LivingEntity target, Player shooter, Vector direction) {
            this.bullet = bullet;
            this.target = target;
            this.shooter = shooter;
            this.direction = direction;
            this.ticks = 0;
            this.dead = false;
        }
    }

    @Override
    public void onTick(int tick) {
        if (isDestroyed() || activeBullets.isEmpty()) {
            unregisterTick();
            return;
        }

        // 탄환 처리 (2틱마다 실행)
        if (tick % 2 == 0) {
            Iterator<BulletData> iter = activeBullets.iterator();
            while (iter.hasNext()) {
                BulletData data = iter.next();
                processBullet(data);
                if (data.dead) {
                    if (data.bullet != null && !data.bullet.isDead()) {
                        data.bullet.remove();
                    }
                    iter.remove();
                }
            }
        }
    }
}
