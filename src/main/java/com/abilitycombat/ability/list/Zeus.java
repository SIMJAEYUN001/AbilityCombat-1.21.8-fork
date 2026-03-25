package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

@AbilityManifest(name = "제우스 (Zeus)", rank = AbilityManifest.Rank.S, species = AbilityManifest.Species.GOD, explain = {
        "§e§l[패시브 - 하늘의 지배자]",
        "§7낙하 피해를 받지 않습니다, 두 능력의 쿨타임을 공유합니다.",
        "",
        "§e§l[철괴 우클릭 - 번개 강림]§f §8(쿨타임: 10초)",
        "§7바라보는 지점(최대 §f40칸§7)에 번개를 떨어뜨리고",
        "§7그 위치로 즉시 순간이동합니다.",
        "§7번개에 맞은 적은 §c1.5초§7간 §e기절§7합니다.",
        "",
        "§e§l[철괴 좌클릭 - 천둥의 심판]§f §8(쿨타임: 50초)",
        "§7번개가 떨어지는 동안 공중에 부양합니다. (높이 §f10칸§7)",
        "§7부양 중 받는 피해가 §f90%§7 감소합니다. (약 §f7초§7)",
        "§7주변에 번개를 §f2틱마다 2개씩§7, 총 §f140번§7 떨어뜨립니다.",
        "§7번개에 §f3회§7 이상 적중한 대상은 §f3초§7 기절하고 §c15의 피해§7를 받습니다."
}, summarize = {
        "§7철괴 우클릭§f: 번개 낙뢰 + 순간이동",
        "§7철괴 좌클릭§f: 부양 + 번개 난사"
})
public class Zeus extends AbilityBase implements ActiveHandler {

    private static final int LIGHTNING_DESCENT_COOLDOWN_SECONDS = 10;
    private static final int THUNDER_JUDGMENT_COOLDOWN_SECONDS = 50;
    private static final int LIGHTNING_RANGE = 40;
    private static final int CHAIN_COUNT = 3;
    private static final double CHAIN_RANGE = 8.0;
    private static final double CHAIN_DAMAGE = 4.0;
    private static final int CHAIN_STUN_TICKS = 20;
    private static final double STORM_FLOAT_HEIGHT = 10.0;
    private static final double STORM_DAMAGE_REDUCTION_MULTIPLIER = 0.1; // 90% 감소
    private static final int STORM_STRIKE_INTERVAL_TICKS = 2;
    private static final int STORM_STRIKES_PER_INTERVAL = 2;
    private static final int STORM_STRIKE_COUNT = 70;
    private static final int STORM_DURATION_TICKS = STORM_STRIKE_INTERVAL_TICKS * STORM_STRIKE_COUNT;
    // 주변 번개 범위 (요청대로 변수로 쉽게 수정 가능)
    private static final double STORM_RADIUS = 10.0;
    private static final double STORM_HIT_RADIUS = 2.0;
    private static final int STORM_HITS_TO_TRIGGER = 3;
    private static final int STORM_STUN_TICKS = 3 * 20;
    private static final double STORM_TRIGGER_DAMAGE = 15.0;

    private final Cooldown cooldown = new Cooldown(THUNDER_JUDGMENT_COOLDOWN_SECONDS);

    private boolean stormActive;
    private int stormTicks;
    private int stormStrikesRemaining;
    private double stormTargetY;
    private final Map<UUID, Integer> stormHitCounts = new HashMap<>();
    private final Set<UUID> stormTriggered = new HashSet<>();

    public Zeus(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        stopStorm();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT) {
            return false;
        }
        Player player = getPlayer();
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        if (clickType == ClickType.LEFT_CLICK) {
            startStorm(player);
            cooldown.start();
            cooldown.setCount(THUNDER_JUDGMENT_COOLDOWN_SECONDS);
            applyIronCooldownIfEmpty(THUNDER_JUDGMENT_COOLDOWN_SECONDS);
            return true;
        }
        if (clickType != ClickType.RIGHT_CLICK) {
            return false;
        }

        Block target = player.getTargetBlockExact(LIGHTNING_RANGE);
        if (target == null) {
            return false;
        }
        Location strikeLocation = target.getLocation().add(0.5, 1.0, 0.5);
        World world = player.getWorld();
        spawnLightningTrail(player.getLocation(), strikeLocation);
        // 실제 번개(불 생성)를 쓰지 않고, 이펙트 + 커스텀 로직으로 대체하여 화염 피해를 방지합니다.
        world.strikeLightningEffect(strikeLocation);
        player.teleport(strikeLocation);
        stunNearby(strikeLocation, 2.5, 30);
        Set<UUID> hit = new HashSet<>();
        hit.add(player.getUniqueId());
        chainLightning(strikeLocation, hit, CHAIN_COUNT);
        cooldown.start();
        cooldown.setCount(LIGHTNING_DESCENT_COOLDOWN_SECONDS);
        applyIronCooldownIfEmpty(LIGHTNING_DESCENT_COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent) {
            onFallDamage((EntityDamageEvent) event);
        }
    }

    private void onFallDamage(EntityDamageEvent event) {
        if (!event.getEntity().equals(getPlayer())) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }
        if (isStormFloating()) {
            scaleIncomingDamage(event, STORM_DAMAGE_REDUCTION_MULTIPLIER);
        }
    }

    @Override
    public void onTick(int tick) {
        if (!stormActive) {
            return;
        }

        Player player = getPlayer();
        if (player == null || !player.isOnline() || player.isDead()) {
            stopStorm();
            return;
        }

        stormTicks++;

        if (isStormFloating()) {
            maintainFloating(player);
        }

        if (stormStrikesRemaining > 0 && stormTicks % STORM_STRIKE_INTERVAL_TICKS == 0) {
            for (int i = 0; i < STORM_STRIKES_PER_INTERVAL; i++) {
                dropRandomLightning(player);
            }
            stormStrikesRemaining--;
            if (stormStrikesRemaining <= 0) {
                stopStorm();
            }
        }
    }

    private void startStorm(Player player) {
        stopStorm();
        stormActive = true;
        stormTicks = 0;
        stormStrikesRemaining = STORM_STRIKE_COUNT;
        stormHitCounts.clear();
        stormTriggered.clear();
        stormTargetY = player.getLocation().getY() + STORM_FLOAT_HEIGHT;

        // 즉시 부양 시작 (수평 속도는 유지)
        Vector vel = player.getVelocity();
        vel.setY(0.9);
        player.setVelocity(vel);
    }

    private void stopStorm() {
        stormActive = false;
        stormTicks = 0;
        stormStrikesRemaining = 0;
        stormTargetY = 0;
        stormHitCounts.clear();
        stormTriggered.clear();
    }

    private boolean isStormFloating() {
        return stormActive && stormTicks <= STORM_DURATION_TICKS;
    }

    private void maintainFloating(Player player) {
        if (player == null) {
            return;
        }
        player.setFallDistance(0f);

        double currentY = player.getLocation().getY();
        double dy = stormTargetY - currentY;

        // 목표 높이에 자연스럽게 수렴시키기 (폴터가이스트의 '공중 고정' 느낌)
        double yVelocity;
        if (dy > 0.6) {
            yVelocity = 0.6;
        } else if (dy < -0.6) {
            yVelocity = -0.2;
        } else {
            // 중력 상쇄용 미세 상승
            yVelocity = 0.08;
        }
        Vector vel = player.getVelocity();
        vel.setY(yVelocity);
        player.setVelocity(vel);
    }

    private void dropRandomLightning(Player player) {
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dist = Math.sqrt(random.nextDouble()) * STORM_RADIUS;
        double x = center.getX() + Math.cos(angle) * dist;
        double z = center.getZ() + Math.sin(angle) * dist;
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int floorY = LocationUtil.getFloorY(world, blockX, blockZ, center.getBlockY());

        Location strike = new Location(world, blockX + 0.5, floorY, blockZ + 0.5);
        world.strikeLightningEffect(strike);

        // 이펙트 보강
        ParticleUtil.spawnParticle(world, Particle.ELECTRIC_SPARK, strike, 15, 0.6, 0.6, 0.6, 0.05, 1, 0);

        // 번개 적중 판정(커스텀) 및 누적
        for (LivingEntity entity : LocationUtil.getNearbyLivingEntities(strike, STORM_HIT_RADIUS, player,
                e -> !e.equals(player))) {
            UUID uuid = entity.getUniqueId();
            int next = stormHitCounts.getOrDefault(uuid, 0) + 1;
            stormHitCounts.put(uuid, next);

            if (next >= STORM_HITS_TO_TRIGGER && stormTriggered.add(uuid)) {
                entity.damage(STORM_TRIGGER_DAMAGE, player);
                Stun.apply(entity, STORM_STUN_TICKS);
            }
        }
    }

    private void stunNearby(Location center, double radius, int ticks) {
        for (LivingEntity entity : LocationUtil.getNearbyLivingEntities(center, radius, getPlayer(),
                e -> !e.equals(getPlayer()))) {
            Stun.apply(entity, ticks);
        }
    }

    private void chainLightning(Location origin, Set<UUID> hit, int remaining) {
        if (remaining <= 0 || origin == null || origin.getWorld() == null) {
            return;
        }
        LivingEntity target = findChainTarget(origin, hit);
        if (target == null) {
            return;
        }
        hit.add(target.getUniqueId());
        Location strike = target.getLocation().add(0, 1.0, 0);
        origin.getWorld().strikeLightningEffect(strike);
        spawnLightningTrail(origin, strike);
        target.damage(CHAIN_DAMAGE, getPlayer());
        Stun.apply(target, CHAIN_STUN_TICKS);
        chainLightning(strike, hit, remaining - 1);
    }

    private LivingEntity findChainTarget(Location origin, Set<UUID> hit) {
        LivingEntity nearest = null;
        double minDistance = Double.MAX_VALUE;
        for (LivingEntity entity : LocationUtil.getNearbyLivingEntities(origin, CHAIN_RANGE, getPlayer(),
                e -> !e.equals(getPlayer()) && !hit.contains(e.getUniqueId()))) {
            double distance = entity.getLocation().distanceSquared(origin);
            if (distance < minDistance) {
                nearest = entity;
                minDistance = distance;
            }
        }
        return nearest;
    }

    private void spawnLightningTrail(Location from, Location to) {
        if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        Vector delta = com.abilitycombat.utils.VectorPool.get();
        delta.copy(to.toVector()).subtract(from.toVector());
        double length = delta.length();
        if (length <= 0.0) {
            return;
        }
        int points = Math.max(8, (int) (length * 4));
        Vector step = com.abilitycombat.utils.VectorPool.get();
        step.copy(delta).multiply(1.0 / points);

        Location point = com.abilitycombat.utils.LocationPool.get(from.getWorld(), 0, 0, 0);
        point.setX(from.getX());
        point.setY(from.getY());
        point.setZ(from.getZ());

        for (int i = 0; i <= points; i++) {
            ParticleUtil.spawnParticle(from.getWorld(), Particle.ELECTRIC_SPARK, point, 2, 0, 0, 0, 0, 2, 0);
            point.add(step);
        }
    }

}
