package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;

import java.util.UUID;

@AbilityManifest(name = "제우스 (Zeus)", species = AbilityManifest.Species.GOD, explain = {
        "§e§l[패시브 - 하늘의 지배자]",
        "§7낙하 피해를 받지 않습니다.",
        "",
        "§e§l[철괴 우클릭 - 번개 질주]§f §8(돌진만 사용: 12초 / 재사용: 25초)",
        "§7바라보는 방향으로 §f2초§7간 돌진합니다. §8(속도: 1.5)",
        "§7돌진 중 다시 사용하면 매우 빠른 번개창을 §f30칸§7까지 던집니다.",
        "§7번개창이 적중한 구조물 위로만 순간이동하고, 경로마다 번개를 떨어뜨립니다.",
        "§7번개마다 거리 감쇠 없이 §c12 피해§7와 §e기절 1.5초§7를 적용합니다."
}, summarize = {
        "§7철괴 우클릭§f: 바라보는 방향으로 2초 돌진",
        "§7돌진 중 우클릭§f: 30칸 번개창 적중 지점 텔레포트 + 경로 번개",
        "§7쿨타임§f: 돌진만 12초 / 재사용 25초"
})
public class Zeus extends AbilityBase implements ActiveHandler {

    private static final int DASH_ONLY_COOLDOWN_SECONDS = 12;
    private static final int DASH_REUSE_COOLDOWN_SECONDS = 25;
    private static final int DASH_DURATION_TICKS = 40;
    private static final double DASH_SPEED = 1.5;
    private static final int DASH_PARTICLE_INTERVAL_TICKS = 2;
    private static final double TELEPORT_TRIDENT_RANGE = 30.0;
    private static final double TELEPORT_TRIDENT_SPEED = 6.0;
    private static final int TELEPORT_TRIDENT_MAX_TICKS = 20;
    private static final double TRIDENT_PATH_LIGHTNING_STEP = 2.0;
    private static final double LIGHTNING_HIT_RADIUS = 2.5;
    private static final double LIGHTNING_DAMAGE = 12.0;
    private static final int LIGHTNING_STUN_TICKS = 30;

    private final Cooldown cooldown = new Cooldown(DASH_REUSE_COOLDOWN_SECONDS);

    private boolean dashActive;
    private int dashTicks;
    private Vector dashDirection;
    private UUID teleportTridentId;
    private Location teleportTridentStartLocation;
    private Location teleportTridentLastLocation;
    private int teleportTridentExpireTick;

    public Zeus(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageEvent.class);
        subscribeEvent(EntityDamageByEntityEvent.class);
        subscribeEvent(ProjectileHitEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        stopDash(getPlayer(), true);
        clearTeleportTrident(true);
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
        if (event instanceof EntityDamageByEntityEvent damageByEntityEvent) {
            onTridentDamage(damageByEntityEvent);
        }
        if (event instanceof ProjectileHitEvent projectileHitEvent) {
            onProjectileHit(projectileHitEvent);
        }
        if (event instanceof EntityDamageEvent damageEvent) {
            onFallDamage(damageEvent);
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

    private void onTridentDamage(EntityDamageByEntityEvent event) {
        if (teleportTridentId != null && event.getDamager().getUniqueId().equals(teleportTridentId)) {
            event.setCancelled(true);
        }
    }

    private void onProjectileHit(ProjectileHitEvent event) {
        if (teleportTridentId == null || !event.getEntity().getUniqueId().equals(teleportTridentId)) {
            return;
        }
        Player player = getPlayer();
        if (player == null || !player.isOnline() || player.isDead()) {
            clearTeleportTrident(true);
            return;
        }
        Location hitLocation = event.getEntity().getLocation().clone();
        strikeAlongTridentPath(player, teleportTridentLastLocation, hitLocation);
        Location destination = resolveTridentDestination(event, player);
        clearTeleportTrident(true);
        if (destination == null) {
            player.sendMessage("§c번개창이 적중한 위치에 순간이동할 안전한 구조물이 없습니다.");
            return;
        }
        player.teleport(destination);
        strikeDashLightning(player, destination);
    }

    @Override
    public void onTick(int tick) {
        tickTeleportTrident(tick);

        if (!dashActive) {
            return;
        }

        Player player = getPlayer();
        if (player == null || !player.isOnline() || player.isDead()) {
            stopDash(player, false);
            return;
        }

        dashTicks++;
        if (dashTicks >= DASH_DURATION_TICKS) {
            finishDashOnly(player);
            return;
        }

        player.setFallDistance(0f);
        updateDashDirection(player);
        player.setVelocity(dashDirection.clone().multiply(DASH_SPEED));
        if (tick % DASH_PARTICLE_INTERVAL_TICKS == 0) {
            ParticleUtil.spawnParticle(player.getWorld(), Particle.ELECTRIC_SPARK,
                    player.getLocation().clone().add(0, 1.0, 0), 8, 0.35, 0.35, 0.35, 0.02, 2, 0);
        }
    }

    private void startDash(Player player) {
        dashActive = true;
        dashTicks = 0;
        updateDashDirection(player);
        player.setFallDistance(0f);
        player.setVelocity(dashDirection.clone().multiply(DASH_SPEED));
    }

    private void finishDashOnly(Player player) {
        stopDash(player, true);
        startCooldown(DASH_ONLY_COOLDOWN_SECONDS);
    }

    private void finishDashWithReuse(Player player) {
        if (!launchTeleportTrident(player)) {
            return;
        }
        stopDash(player, true);
        startCooldown(DASH_REUSE_COOLDOWN_SECONDS);
    }

    private void stopDash(Player player, boolean clearVelocity) {
        dashActive = false;
        dashTicks = 0;
        dashDirection = null;
        if (clearVelocity && player != null && player.isOnline()) {
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0f);
        }
    }

    private void updateDashDirection(Player player) {
        dashDirection = player.getEyeLocation().getDirection();
        if (dashDirection.lengthSquared() <= 0.0) {
            dashDirection = new Vector(0, 0, 1);
        }
        dashDirection.normalize();
    }

    private Location toGround(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        World world = location.getWorld();
        int floorY = LocationUtil.getFloorY(world, location.getBlockX(), location.getBlockZ(),
                Math.min(location.getBlockY() + 2, world.getMaxHeight() - 1));
        if (floorY <= world.getMinHeight()) {
            return null;
        }
        return new Location(world, location.getBlockX() + 0.5, floorY, location.getBlockZ() + 0.5);
    }

    private boolean launchTeleportTrident(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        clearTeleportTrident(true);
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        if (direction.lengthSquared() <= 0.0) {
            player.sendMessage("§c번개창을 던질 방향이 없습니다.");
            return false;
        }
        direction.normalize();
        Location spawnLocation = eye.clone().add(direction.clone().multiply(0.9));
        Trident trident = player.getWorld().spawn(spawnLocation, Trident.class, spawned -> {
            spawned.setShooter(player);
            spawned.setGravity(false);
            spawned.setDamage(0.0);
            spawned.setCritical(false);
            spawned.setPersistent(false);
            spawned.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            spawned.setVelocity(direction.clone().multiply(TELEPORT_TRIDENT_SPEED));
        });
        teleportTridentId = trident.getUniqueId();
        teleportTridentStartLocation = spawnLocation.clone();
        teleportTridentLastLocation = spawnLocation.clone();
        teleportTridentExpireTick = AbilityTickManager.getGlobalTick() + TELEPORT_TRIDENT_MAX_TICKS;
        return true;
    }

    private void tickTeleportTrident(int tick) {
        if (teleportTridentId == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(teleportTridentId);
        Player player = getPlayer();
        if (!(entity instanceof Trident trident) || player == null || !player.isOnline() || player.isDead()) {
            clearTeleportTrident(false);
            return;
        }
        Location current = trident.getLocation().clone();
        strikeAlongTridentPath(player, teleportTridentLastLocation, current);
        teleportTridentLastLocation = current;
        if (tick > teleportTridentExpireTick || isTridentOutOfRange(current)) {
            clearTeleportTrident(true);
            player.sendMessage("§c번개창이 구조물에 적중하지 못했습니다.");
        }
    }

    private boolean isTridentOutOfRange(Location current) {
        return teleportTridentStartLocation == null || current == null
                || current.getWorld() != teleportTridentStartLocation.getWorld()
                || current.distanceSquared(teleportTridentStartLocation) > TELEPORT_TRIDENT_RANGE * TELEPORT_TRIDENT_RANGE;
    }

    private void clearTeleportTrident(boolean removeProjectile) {
        if (removeProjectile && teleportTridentId != null) {
            Entity entity = Bukkit.getEntity(teleportTridentId);
            if (entity != null) {
                entity.remove();
            }
        }
        teleportTridentId = null;
        teleportTridentStartLocation = null;
        teleportTridentLastLocation = null;
        teleportTridentExpireTick = 0;
    }

    private Location resolveTridentDestination(ProjectileHitEvent event, Player player) {
        Location destination = null;
        if (event.getHitBlock() != null) {
            destination = findSafeLocationOnStructure(event.getHitBlock(), event.getHitBlockFace(), player);
        } else if (event.getHitEntity() != null) {
            destination = findSafeLocationNear(event.getHitEntity().getLocation(), player);
        }
        if (destination == null) {
            destination = findSafeLocationNear(event.getEntity().getLocation(), player);
        }
        if (destination != null) {
            destination.setYaw(player.getLocation().getYaw());
            destination.setPitch(player.getLocation().getPitch());
        }
        return destination;
    }

    private Location findSafeLocationNear(Location location, Player player) {
        Location base = toGround(location);
        if (base == null) {
            return null;
        }
        int[][] offsets = {
                {0, 0},
                {1, 0},
                {-1, 0},
                {0, 1},
                {0, -1},
                {1, 1},
                {1, -1},
                {-1, 1},
                {-1, -1}
        };
        for (int[] offset : offsets) {
            Location candidate = base.clone().add(offset[0], 0, offset[1]);
            if (isSupportedSafeLocation(candidate)) {
                return candidate;
            }
        }
        return null;
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
        damageAndStunNearby(player, strikeLocation, LIGHTNING_HIT_RADIUS, LIGHTNING_STUN_TICKS);
    }

    private void strikeAlongTridentPath(Player player, Location from, Location to) {
        if (player == null || from == null || to == null || from.getWorld() == null || from.getWorld() != to.getWorld()) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length <= 0.01) {
            return;
        }
        Vector step = delta.normalize().multiply(TRIDENT_PATH_LIGHTNING_STEP);
        Location point = from.clone();
        for (double traveled = 0.0; traveled <= length; traveled += TRIDENT_PATH_LIGHTNING_STEP) {
            Location strikeLocation = toGround(point);
            strikeDashLightning(player, strikeLocation != null ? strikeLocation : point);
            point.add(step);
        }
    }

    private void damageAndStunNearby(Player player, Location center, double radius, int ticks) {
        for (LivingEntity entity : LocationUtil.getNearbyLivingEntities(center, radius, player,
                e -> !e.equals(player))) {
            entity.setNoDamageTicks(0);
            entity.damage(LIGHTNING_DAMAGE, player);
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
