package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.Participant;
import com.abilitycombat.npc.PlayerReplicaManager;
import com.abilitycombat.utils.LocationPool;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import com.abilitycombat.utils.VectorPool;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

@AbilityManifest(name = "기계팔 (MachineArm)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 기계팔]§f §8(쿨타임: 12초)",
        "§7전방으로 기계팔을 발사해 적중한 대상을 끌어온 후 1.5초간 스턴을 적용합니다",
        "§7적중 시 데미지 §c10§7을 입힙니다",
        "§7기계팔이 블록에 닿으면 사라집니다"
}, summarize = {
        "§7철괴 우클릭§f: 그랩 발사 → 적 끌어오기"
})
public class MachineArm extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 12;
    private static final int FINISH_STUN_TICKS = 30;
    private static final double HIT_DAMAGE = 10.0;

    private static final double FIST_VISUAL_Y_OFFSET = 1.2;

    private static final double RANGE = 15.0;
    private static final double SPEED = 2.5;
    private static final double HIT_RADIUS = 1.15;
    private static final int MAX_TICKS = (int) Math.ceil(RANGE / SPEED) + 2;

    private static final int PULL_TICKS = 12;
    private static final double PULL_FINISH_DISTANCE = 1.6;
    private static final double PULL_MIN_SPEED = 0.8;
    private static final double PULL_MAX_SPEED = 2.3;
    private static final double PULL_SPEED_RATIO = 0.22;
    private static final double PULL_Y_BOOST = 0.25;
    private static final double PULL_MAX_UP_Y = 1.4;
    private static final double PULL_MAX_DOWN_Y = -1.2;
    private static final double PULL_MIN_UP_Y = 0.35;
    private static final double PULL_TARGET_FORWARD = 1.25;
    private static final double PULL_TARGET_Y_OFFSET = 0.6;

    private static final Particle.DustOptions CHAIN_DUST = new Particle.DustOptions(Color.fromRGB(160, 160, 160),
            1.0f);

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private ArmProjectile projectile;
    private LivingEntity grabbed;
    private int remainingPullTicks;

    public MachineArm(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        resetState();
    }

    @Override
    protected void onDestroy() {
        resetState();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (isBusy()) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        ArmProjectile created = spawnProjectile(player);
        if (created == null) {
            return false;
        }
        projectile = created;
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        playFireSound(player);
        return true;
    }

    private boolean isBusy() {
        return projectile != null || remainingPullTicks > 0;
    }

    private void resetState() {
        removeProjectile();
        grabbed = null;
        remainingPullTicks = 0;
    }

    private ArmProjectile spawnProjectile(Player player) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) {
            return null;
        }
        Vector direction = eye.getDirection().normalize();
        Location position = eye.clone().add(direction.clone().multiply(0.9));
        Location standSpawn = position.clone().subtract(0, FIST_VISUAL_Y_OFFSET, 0);
        ArmorStand stand = world.spawn(standSpawn, ArmorStand.class, entity -> {
            entity.setVisible(false);
            entity.setGravity(false);
            entity.setMarker(true);
            entity.setSmall(true);
            entity.setBasePlate(false);
            entity.setInvulnerable(true);
            entity.getEquipment().setHelmet(new ItemStack(Material.IRON_BLOCK));
        });
        AbilityCombat.markAbilityArmorStand(stand);
        return new ArmProjectile(stand, direction, position);
    }

    private void removeProjectile() {
        if (projectile == null) {
            return;
        }
        if (projectile.armorStand != null && !projectile.armorStand.isDead()) {
            projectile.armorStand.remove();
        }
        projectile = null;
    }

    @Override
    public void onTick(int tick) {
        processProjectile();
        processPulling();
    }

    private void processProjectile() {
        if (projectile == null) {
            return;
        }
        Player player = getPlayer();
        if (player == null || !player.isOnline() || player.isDead()) {
            removeProjectile();
            return;
        }
        if (projectile.armorStand == null || projectile.armorStand.isDead()) {
            removeProjectile();
            return;
        }

        projectile.ticks++;
        if (projectile.ticks > MAX_TICKS) {
            removeProjectile();
            return;
        }

        Vector delta = VectorPool.copy(projectile.direction).multiply(SPEED);
        projectile.position.add(delta);
        Location next = projectile.position;

        if (next.getBlock().getType().isSolid()) {
            spawnImpactEffect(next);
            removeProjectile();
            return;
        }

        Location standLocation = LocationPool.getSecond(next.getWorld(), next.getX(),
                next.getY() - FIST_VISUAL_Y_OFFSET,
                next.getZ());
        projectile.armorStand.teleport(standLocation);
        spawnProjectileEffect(next);
        spawnChainEffect(player, next);
        if (projectile.ticks % 3 == 0) {
            player.getWorld().playSound(next, Sound.BLOCK_CHAIN_STEP, 0.35f, 1.8f);
        }

        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(next, HIT_RADIUS, player,
                entity -> !entity.equals(player)
                        && !(entity instanceof ArmorStand)
                        && !PlayerReplicaManager.isReplicaEntity(entity))) {
            onHitTarget(player, target);
            removeProjectile();
            return;
        }
    }

    private void onHitTarget(Player player, LivingEntity target) {
        target.damage(HIT_DAMAGE, player);
        grabbed = target;
        remainingPullTicks = PULL_TICKS;
        playHitSound(player, target);
    }

    private void processPulling() {
        if (remainingPullTicks <= 0) {
            return;
        }
        Player player = getPlayer();
        if (player == null || !player.isOnline() || player.isDead()) {
            resetPulling();
            return;
        }
        if (grabbed == null || grabbed.isDead()) {
            resetPulling();
            return;
        }
        if (player.getWorld() != grabbed.getWorld()) {
            resetPulling();
            return;
        }
        if (!player.hasLineOfSight(grabbed)) {
            resetPulling();
            return;
        }

        Location targetLoc = grabbed.getLocation();
        Location pullTarget = getPullTarget(player);
        double dx = pullTarget.getX() - targetLoc.getX();
        double dy = pullTarget.getY() - targetLoc.getY();
        double dz = pullTarget.getZ() - targetLoc.getZ();
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared <= PULL_FINISH_DISTANCE * PULL_FINISH_DISTANCE) {
            finishPull(player);
            return;
        }

        Vector direction = VectorPool.get(dx, dy, dz);
        double distance = Math.sqrt(distanceSquared);
        if (distance <= 0.001) {
            resetPulling();
            return;
        }
        double speed = Math.min(PULL_MAX_SPEED, Math.max(PULL_MIN_SPEED, distance * PULL_SPEED_RATIO));
        Vector velocity = direction.multiply(1.0 / distance).multiply(speed);
        double vy = velocity.getY();
        if (dy > 0) {
            vy = Math.min(PULL_MAX_UP_Y, Math.max(PULL_MIN_UP_Y, vy + PULL_Y_BOOST));
        } else {
            vy = Math.max(PULL_MAX_DOWN_Y, vy);
        }
        velocity.setY(vy);
        grabbed.setVelocity(velocity);
        grabbed.setFallDistance(0f);
        spawnPullEffect(player, grabbed);

        remainingPullTicks--;
        if (remainingPullTicks <= 0) {
            finishPull(player);
        }
    }

    private Location getPullTarget(Player player) {
        Location playerLoc = player.getLocation();
        Vector forward = VectorPool.get().copy(playerLoc.getDirection());
        forward.setY(0);
        if (forward.lengthSquared() <= 0.0001) {
            forward.setX(0);
            forward.setZ(1);
        }
        forward.normalize().multiply(PULL_TARGET_FORWARD);
        Location target = playerLoc.clone().add(forward);
        target.setY(playerLoc.getY() + PULL_TARGET_Y_OFFSET);
        return target;
    }

    private void finishPull(Player player) {
        if (player == null || grabbed == null) {
            resetPulling();
            return;
        }
        Location pullTarget = getPullTarget(player);
        Location safe = adjustToSafeDestination(pullTarget);
        if (safe != null) {
            grabbed.teleport(safe);
        }
        grabbed.setVelocity(VectorPool.get());
        grabbed.setFallDistance(0f);
        Stun.apply(grabbed, FINISH_STUN_TICKS);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.9f, 1.4f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.7f, 1.6f);
        resetPulling();
    }

    private Location adjustToSafeDestination(Location destination) {
        if (destination == null || destination.getWorld() == null) {
            return destination;
        }
        Location candidate = destination.clone();
        for (int i = 0; i < 3; i++) {
            if (!candidate.getBlock().getType().isSolid()
                    && !candidate.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                return candidate;
            }
            candidate.add(0, 1, 0);
        }
        return destination;
    }

    private void resetPulling() {
        grabbed = null;
        remainingPullTicks = 0;
    }

    private void playFireSound(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(location, Sound.BLOCK_PISTON_EXTEND, 1.0f, 1.4f);
        world.playSound(location, Sound.BLOCK_CHAIN_PLACE, 0.7f, 1.8f);
        world.playSound(location, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.6f, 1.8f);
        world.playSound(location, Sound.ITEM_TRIDENT_THROW, 0.5f, 1.7f);
    }

    private void playHitSound(Player player, LivingEntity target) {
        World world = player.getWorld();
        Location hitLocation = target.getLocation();
        world.playSound(hitLocation, Sound.BLOCK_CHAIN_HIT, 0.9f, 1.4f);
        world.playSound(hitLocation, Sound.ITEM_TRIDENT_HIT, 0.7f, 1.2f);
        world.playSound(player.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.6f, 1.8f);
    }

    private void spawnProjectileEffect(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        ParticleUtil.spawnParticle(world, Particle.CRIT, location, 2, 0.15, 0.15, 0.15, 0.02, 1, 0);
    }

    private void spawnImpactEffect(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        ParticleUtil.spawnParticle(world, Particle.BLOCK_CRUMBLE, location, 8, 0.2, 0.2, 0.2, 0.02,
                Material.IRON_BLOCK.createBlockData(), 1, 0);
        world.playSound(location, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.6f);
        world.playSound(location, Sound.BLOCK_PISTON_CONTRACT, 0.5f, 1.8f);
    }

    private void spawnChainEffect(Player player, Location end) {
        if (player == null || end == null) {
            return;
        }
        Location start = player.getLocation();
        World world = start.getWorld();
        if (world == null) {
            return;
        }
        double startX = start.getX();
        double startY = start.getY() + 1.0;
        double startZ = start.getZ();

        double dx = end.getX() - startX;
        double dy = end.getY() - startY;
        double dz = end.getZ() - startZ;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared <= 0.0001) {
            return;
        }
        double length = Math.sqrt(lengthSquared);
        int points = Math.min(12, Math.max(2, (int) (length * 2.0)));
        double stepX = dx / points;
        double stepY = dy / points;
        double stepZ = dz / points;

        Location point = LocationPool.getSecond(world, startX, startY, startZ);
        for (int i = 0; i <= points; i++) {
            ParticleUtil.spawnParticle(world, Particle.DUST, point, 1, 0, 0, 0, 0, CHAIN_DUST, 2, 0);
            point.add(stepX, stepY, stepZ);
        }
    }

    private void spawnPullEffect(Player player, LivingEntity target) {
        spawnChainEffect(player, target.getLocation().clone().add(0, 1.0, 0));
        Location location = target.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        ParticleUtil.spawnParticle(world, Particle.SMOKE, location, 1, 0.2, 0.2, 0.2, 0.01, 2, 0);
    }

    private static class ArmProjectile {
        private final ArmorStand armorStand;
        private final Vector direction;
        private final Location position;
        private int ticks;

        private ArmProjectile(ArmorStand armorStand, Vector direction, Location position) {
            this.armorStand = armorStand;
            this.direction = direction;
            this.position = position;
            this.ticks = 0;
        }
    }
}
