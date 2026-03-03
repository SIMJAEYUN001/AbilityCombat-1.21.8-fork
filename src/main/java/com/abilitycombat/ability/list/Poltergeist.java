package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.Event;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@AbilityManifest(name = "폴터가이스트 (Poltergeist)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.SPECIAL, explain = {
        "§e§l[철괴 우클릭 - 악령의 손아귀]§f §8(쿨타임: 45초)",
        "§7§o느린 투사체§7를 던집니다.",
        "",
        "§7투사체가 적중한 지점 주변 §f5칸§7 이내의 플레이어를",
        "§d7칸 위로 띄운 후§7, §c4초 뒤 내리찍습니다§7.",
        "",
        "§7착지 시 §c15의 피해§7를 입힙니다."
}, summarize = {
        "§7철괴 우클릭§f: 느린 투사체 투척 → 적중 시 주변 적 공중 부양 → 4초 후 내리찍기"
})
public class Poltergeist extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 45;
    private static final double SNOWBALL_SPEED = 1;
    private static final double LIFT_RADIUS = 5.0;
    private static final double LIFT_HEIGHT = 7.0;
    private static final int SLAM_DELAY_TICKS = 80; // 4초
    private static final double SLAM_DAMAGE = 15.0;
    private static final int COOLDOWN_REDUCTION_PER_TARGET = 5;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private Snowball activeSnowball;

    public Poltergeist(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(ProjectileHitEvent.class);
    }

    @Override
    protected void onDeactivate() {
        if (activeSnowball != null && activeSnowball.isValid()) {
            activeSnowball.remove();
        }
        activeSnowball = null;
    }

    @Override
    public boolean activeSkill(Material material, ActiveHandler.ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ActiveHandler.ClickType.RIGHT_CLICK) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        throwSlowSnowball();
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof ProjectileHitEvent e) {
            onProjectileHit(e);
        }
    }

    private void throwSlowSnowball() {
        Player player = getPlayer();
        World world = player.getWorld();

        // 느린 눈덩이 발사
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize().multiply(SNOWBALL_SPEED);

        activeSnowball = world.spawn(eyeLoc.add(direction), Snowball.class, snowball -> {
            snowball.setVelocity(direction);
            snowball.setShooter(player);
        });

        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.6f);
    }

    private void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) {
            return;
        }
        if (activeSnowball == null || !snowball.equals(activeSnowball)) {
            return;
        }
        if (!(snowball.getShooter() instanceof Player shooter) || !shooter.equals(getPlayer())) {
            return;
        }

        Location hitLocation = snowball.getLocation();
        activeSnowball = null;
        World world = hitLocation.getWorld();

        // 적중 지점 범위 이펙트 (4칸 원형)
        if (world != null) {
            // 원형 파티클 이펙트
            for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
                double x = Math.cos(angle) * LIFT_RADIUS;
                double z = Math.sin(angle) * LIFT_RADIUS;
                Location particleLoc = hitLocation.clone().add(x, 0.5, z);
                ParticleUtil.spawnParticle(world, Particle.SOUL_FIRE_FLAME, particleLoc,
                        3, 0.1, 0.1, 0.1, 0.01, 1, 0);
            }
            // 중앙 이펙트
            ParticleUtil.spawnParticle(world, Particle.SOUL_FIRE_FLAME, hitLocation.clone().add(0, 0.5, 0),
                    30, LIFT_RADIUS / 2, 0.2, LIFT_RADIUS / 2, 0.02, 1, 0);
            ParticleUtil.spawnParticle(world, Particle.WITCH, hitLocation.clone().add(0, 1, 0),
                    20, LIFT_RADIUS / 2, 0.5, LIFT_RADIUS / 2, 0, 1, 0);
        }

        // 적중 지점 주변 플레이어 찾기 (Y축 ±1 여유)
        Set<UUID> liftedPlayers = new HashSet<>();
        for (LivingEntity entity : LocationUtil.getNearbyLivingEntities(hitLocation, LIFT_RADIUS,
                LocationUtil.withValidTarget(e -> !e.equals(shooter)))) {
            if (!(entity instanceof Player target)) {
                continue;
            }
            // Y축 차이 체크 (±1블록 여유)
            double yDiff = Math.abs(target.getLocation().getY() - hitLocation.getY());
            if (yDiff > 2.0) { // 플레이어 높이 고려하여 2블록
                continue;
            }
            liftedPlayers.add(target.getUniqueId());
            liftPlayer(target, hitLocation);
        }

        if (liftedPlayers.isEmpty()) {
            return;
        }

        reduceCooldownByTargetCount(liftedPlayers.size());

        // 4초 후 내리찍기
        Bukkit.getScheduler().runTaskLater(AbilityCombat.getPlugin(), () -> slamPlayers(hitLocation, liftedPlayers),
                SLAM_DELAY_TICKS);
    }

    private void reduceCooldownByTargetCount(int targetCount) {
        if (!cooldown.isCooldown() || targetCount <= 0) {
            return;
        }
        int reduceSeconds = Math.min(targetCount * COOLDOWN_REDUCTION_PER_TARGET, cooldown.getCount());
        cooldown.setCount(cooldown.getCount() - reduceSeconds);
    }

    private void liftPlayer(Player target, Location center) {
        World world = target.getWorld();

        // 위로 띄우기
        target.setVelocity(new Vector(0, LIFT_HEIGHT / 10.0, 0));
        target.playSound(target.getLocation(), Sound.ENTITY_VEX_CHARGE, 1.0f, 0.5f);

        // 지속적으로 위로 띄우는 효과 (1초간 상승)
        for (int i = 1; i <= 20; i++) {
            int delay = i;
            Bukkit.getScheduler().runTaskLater(AbilityCombat.getPlugin(), () -> {
                if (target.isOnline() && !target.isDead() && delay <= 10) {
                    target.setVelocity(new Vector(0, 0.5, 0));
                }
            }, i);
        }

        // 1초 후 스턴 적용 (상승 완료 후 공중에서 고정)
        Bukkit.getScheduler().runTaskLater(AbilityCombat.getPlugin(), () -> {
            if (target.isOnline() && !target.isDead()) {
                Stun.apply(target, SLAM_DELAY_TICKS - 20); // 남은 3초간 스턴
            }
        }, 20L);

        // 공중 고정 + 파티클 (1초 후부터)
        for (int i = 20; i < SLAM_DELAY_TICKS; i += 2) {
            Bukkit.getScheduler().runTaskLater(AbilityCombat.getPlugin(), () -> {
                if (target.isOnline() && !target.isDead()) {
                    // 공중에서 고정 (약간 위로 유지)
                    target.setVelocity(new Vector(0, 0.05, 0));
                    // 촘촘한 보라빛 이펙트
                    ParticleUtil.spawnParticle(world, Particle.SOUL_FIRE_FLAME, target.getLocation(),
                            8, 0.4, 0.6, 0.4, 0.02, 1, 0);
                    ParticleUtil.spawnParticle(world, Particle.WITCH, target.getLocation().add(0, 0.5, 0),
                            5, 0.3, 0.4, 0.3, 0, 1, 0);
                }
            }, i);
        }
    }

    private void slamPlayers(Location center, Set<UUID> playerIds) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Player caster = getPlayer();

        for (UUID playerId : playerIds) {
            Player target = Bukkit.getPlayer(playerId);
            if (target == null || target.isDead() || !target.isOnline()) {
                continue;
            }

            // 아래로 내리찍기
            target.setVelocity(new Vector(0, -3.0, 0));

            // 드래곤의 숨결 이펙트
            Location targetLoc = target.getLocation();
            ParticleUtil.spawnParticle(world, Particle.SOUL_FIRE_FLAME, targetLoc,
                    50, 1.5, 0.5, 1.5, 0.1, 1, 0);

            // 피해 처리 (착지 후 딜레이)
            Bukkit.getScheduler().runTaskLater(AbilityCombat.getPlugin(), () -> {
                if (target.isOnline() && !target.isDead()) {
                    // 데미지 적용 (caster가 null이면 환경 피해로 처리)
                    if (caster != null && caster.isOnline()) {
                        target.damage(SLAM_DAMAGE, caster);
                    } else {
                        target.damage(SLAM_DAMAGE);
                    }
                    world.playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);

                    // 착지 지점 드래곤 숨결 대형 이펙트
                    ParticleUtil.spawnParticle(world, Particle.SOUL_FIRE_FLAME, target.getLocation(),
                            80, 2.0, 0.1, 2.0, 0.05, 1, 0);
                }
            }, 1L); // 15틱 딜레이 (착지 시간 확보)

            // 소리 (주변 플레이어에게도 들림)
            world.playSound(target.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 0.8f, 0.7f);
        }
    }
}
