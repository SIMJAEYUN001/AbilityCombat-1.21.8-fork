package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

@AbilityManifest(name = "제우스 (Zeus)", species = AbilityManifest.Species.GOD, explain = {
        "§e§l[패시브 - 하늘의 지배자]",
        "§7낙하 피해를 받지 않습니다.",
        "",
        "§e§l[철괴 우클릭 - 번개 질주]§f §8(돌진만 사용: 5초 / 재사용: 15초)",
        "§7바라보는 방향으로 §f2.5초§7간 돌진합니다. §8(속도: 1.5)",
        "§7돌진 중 다시 사용하면 그동안 돌진한 거리 안의 구조물 위로 순간이동하고",
        "§7도착 지점에 번개를 떨어뜨립니다. 허공에는 순간이동할 수 없습니다.",
        "§7번개에 맞은 적은 §c1.5초§7간 §e기절§7합니다."
}, summarize = {
        "§7철괴 우클릭§f: 바라보는 방향으로 2.5초 돌진",
        "§7돌진 중 우클릭§f: 구조물 위로만 순간이동 + 번개 낙하",
        "§7쿨타임§f: 돌진만 5초 / 재사용 15초"
})
public class Zeus extends AbilityBase implements ActiveHandler {

    private static final int DASH_ONLY_COOLDOWN_SECONDS = 5;
    private static final int DASH_REUSE_COOLDOWN_SECONDS = 15;
    private static final int DASH_DURATION_TICKS = 50;
    private static final double DASH_SPEED = 1.5;
    private static final int DASH_PARTICLE_INTERVAL_TICKS = 2;
    private static final double LIGHTNING_HIT_RADIUS = 2.5;
    private static final int LIGHTNING_STUN_TICKS = 30;

    private final Cooldown cooldown = new Cooldown(DASH_REUSE_COOLDOWN_SECONDS);

    private boolean dashActive;
    private int dashTicks;
    private Vector dashDirection;
    private Location dashLastLocation;
    private double dashDistance;

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
        stopDash(getPlayer(), true);
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        Player player = getPlayer();
        if (dashActive) {
            finishDashWithReuse(player);
            return true;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        startDash(player);
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
        }
    }

    @Override
    public void onTick(int tick) {
        if (!dashActive) {
            return;
        }

        Player player = getPlayer();
        if (player == null || !player.isOnline() || player.isDead()) {
            stopDash(player, false);
            return;
        }

        accumulateDashDistance(player.getLocation());
        dashTicks++;
        if (dashTicks >= DASH_DURATION_TICKS) {
            finishDashOnly(player);
            return;
        }

        player.setFallDistance(0f);
        if (dashDirection != null) {
            player.setVelocity(dashDirection.clone().multiply(DASH_SPEED));
        }
        if (tick % DASH_PARTICLE_INTERVAL_TICKS == 0) {
            ParticleUtil.spawnParticle(player.getWorld(), Particle.ELECTRIC_SPARK,
                    player.getLocation().clone().add(0, 1.0, 0), 8, 0.35, 0.35, 0.35, 0.02, 2, 0);
        }
    }

    private void startDash(Player player) {
        dashDirection = player.getLocation().getDirection();
        if (dashDirection.lengthSquared() <= 0.0) {
            dashDirection = new Vector(0, 0, 1);
        }
        dashDirection.normalize();
        dashActive = true;
        dashTicks = 0;
        dashDistance = 0.0;
        dashLastLocation = player.getLocation().clone();
        player.setFallDistance(0f);
        player.setVelocity(dashDirection.clone().multiply(DASH_SPEED));
    }

    private void finishDashOnly(Player player) {
        accumulateDashDistance(player.getLocation());
        stopDash(player, true);
        startCooldown(DASH_ONLY_COOLDOWN_SECONDS);
    }

    private void finishDashWithReuse(Player player) {
        accumulateDashDistance(player.getLocation());
        Location from = player.getLocation().clone();
        Vector direction = dashDirection == null ? player.getLocation().getDirection().normalize() : dashDirection.clone();
        double distance = Math.max(0.0, dashDistance);

        Location destination = findReuseDestination(player, from, direction, distance);
        if (destination == null) {
            player.sendMessage("§c바라보는 방향의 돌진 거리 안에 순간이동 가능한 구조물이 없습니다.");
            return;
        }

        stopDash(player, true);
        player.teleport(destination);
        spawnLightningTrail(from, destination);
        strikeDashLightning(player, destination);
        startCooldown(DASH_REUSE_COOLDOWN_SECONDS);
    }

    private void stopDash(Player player, boolean clearVelocity) {
        dashActive = false;
        dashTicks = 0;
        dashDirection = null;
        dashLastLocation = null;
        dashDistance = 0.0;
        if (clearVelocity && player != null && player.isOnline()) {
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0f);
        }
    }

    private void accumulateDashDistance(Location current) {
        if (current == null) {
            return;
        }
        if (dashLastLocation != null && current.getWorld() == dashLastLocation.getWorld()) {
            double distance = current.distance(dashLastLocation);
            if (Double.isFinite(distance)) {
                dashDistance += distance;
            }
        }
        dashLastLocation = current.clone();
    }

    private Location findReuseDestination(Player player, Location from, Vector direction, double distance) {
        if (player == null || from == null || direction == null || distance < 0.5 || from.getWorld() == null) {
            return null;
        }
        Vector normalized = direction.clone();
        if (normalized.lengthSquared() <= 0.0) {
            return null;
        }
        normalized.normalize();

        Location rayStart = from.clone().add(0, 1.0, 0);
        RayTraceResult trace = from.getWorld().rayTraceBlocks(rayStart, normalized, distance,
                FluidCollisionMode.NEVER, true);
        if (trace == null || trace.getHitBlock() == null) {
            return null;
        }

        Block hitBlock = trace.getHitBlock();
        BlockFace hitFace = trace.getHitBlockFace();
        Location destination = findSafeLocationOnStructure(hitBlock, hitFace, player);
        if (destination == null) {
            return null;
        }
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());
        return destination;
    }

    private Location findSafeLocationOnStructure(Block hitBlock, BlockFace hitFace, Player player) {
        if (hitBlock == null || player == null) {
            return null;
        }
        Location onHitBlock = toPlayerLocation(hitBlock.getRelative(BlockFace.UP));
        if (isSupportedSafeLocation(onHitBlock)) {
            return onHitBlock;
        }
        if (hitFace != null) {
            Block adjacent = hitBlock.getRelative(hitFace);
            Location adjacentLocation = toPlayerLocation(adjacent);
            if (isSupportedSafeLocation(adjacentLocation)) {
                return adjacentLocation;
            }
            Location onAdjacent = toPlayerLocation(adjacent.getRelative(BlockFace.UP));
            if (isSupportedSafeLocation(onAdjacent)) {
                return onAdjacent;
            }
        }
        return null;
    }

    private Location toPlayerLocation(Block feetBlock) {
        if (feetBlock == null || feetBlock.getWorld() == null) {
            return null;
        }
        return feetBlock.getLocation().add(0.5, 0.0, 0.5);
    }

    private boolean isSupportedSafeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Block feet = location.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block ground = feet.getRelative(BlockFace.DOWN);
        return isPassableSpace(feet) && isPassableSpace(head) && isStructureBlock(ground);
    }

    private boolean isPassableSpace(Block block) {
        return block != null && !block.isLiquid() && (block.isPassable() || block.getType().isAir());
    }

    private boolean isStructureBlock(Block block) {
        return block != null && !block.isLiquid() && !block.getType().isAir() && block.getType().isSolid();
    }

    private void startCooldown(int seconds) {
        cooldown.start();
        cooldown.setCount(seconds);
        applyIronCooldownIfEmpty(seconds);
    }

    private void strikeDashLightning(Player player, Location strikeLocation) {
        World world = strikeLocation.getWorld();
        if (world == null) {
            return;
        }
        world.strikeLightningEffect(strikeLocation);
        ParticleUtil.spawnParticle(world, Particle.ELECTRIC_SPARK, strikeLocation,
                25, 0.7, 0.7, 0.7, 0.05, 1, 0);
        stunNearby(player, strikeLocation, LIGHTNING_HIT_RADIUS, LIGHTNING_STUN_TICKS);
    }

    private void stunNearby(Player player, Location center, double radius, int ticks) {
        for (LivingEntity entity : LocationUtil.getNearbyLivingEntities(center, radius, player,
                e -> !e.equals(player))) {
            Stun.apply(entity, ticks);
        }
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
