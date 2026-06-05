package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import com.abilitycombat.vfx.Circle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

@AbilityManifest(name = "궤도형 레이저 (OrbitalLaser)", species = AbilityManifest.Species.SPECIAL, explain = {
        "§e§l[철괴 우클릭 - 궤도 폭격]§f §8(쿨타임: 20초)",
        "§7바라보는 지점 최대 §f20칸§7에 §c붉은 경고 원§7을 §f6초§7 표시합니다.",
        "§7이후 높이 §f15칸§7 수직 레이저가 내려오며 반경 §f12.5칸§7을 타격합니다.",
        "§7적중한 적은 §c20 + 잃은 체력의 80% 피해§7와 §f실명 2초§7를 받습니다."
}, summarize = {
        "§7철괴 우클릭§f: 20칸 지점 지정 → 6초 후 레이저",
        "§7적중§f: 반경 12.5칸, 피해 20 + 잃은 체력 80% + 실명 2초"
})
public class OrbitalLaser extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 20;
    private static final double MAX_RANGE = 20.0;
    private static final double BLAST_RADIUS = 12.5;
    private static final double BASE_DAMAGE = 20.0;
    private static final double MISSING_HEALTH_DAMAGE_RATIO = 0.8;
    private static final int BLIND_TICKS = 40;
    private static final int WARNING_TICKS = 120;
    private static final int LASER_HEIGHT = 15;
    private static final double RISING_EXPLOSION_HEIGHT = 14.0;
    private static final double RISING_EXPLOSION_STEP = 0.65;
    private static final Particle.DustOptions WARNING_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 30, 30), 1.15f);

    private final ActionbarCooldown cooldown = new ActionbarCooldown(COOLDOWN_SECONDS);
    private Location targetLocation;
    private int warningStartTick;
    private boolean warningActive;
    private Location explosionLocation;
    private int explosionLayer;
    private boolean explosionActive;

    public OrbitalLaser(Participant participant) {
        super(participant);
    }

    @Override
    protected void onDeactivate() {
        clearEffects();
    }

    @Override
    protected void onDestroy() {
        clearEffects();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
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
        Location target = resolveTargetLocation(player);
        if (target == null) {
            player.sendMessage("§c궤도형 레이저를 지정할 지면이 없습니다.");
            return false;
        }
        startWarning(target);
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void onTick(int tick) {
        if (warningActive && targetLocation != null) {
            tickWarning(tick);
        } else {
            warningActive = false;
        }

        if (explosionActive && explosionLocation != null) {
            tickRisingExplosion();
        } else {
            explosionActive = false;
        }

        if (!warningActive && !explosionActive) {
            unregisterTick();
        }
    }

    private void tickWarning(int tick) {
        int elapsed = tick - warningStartTick;
        if (elapsed <= WARNING_TICKS) {
            spawnWarningCircle(elapsed);
            return;
        }

        Location fireLocation = targetLocation.clone();
        clearWarningState();
        fireLaser(fireLocation);
    }

    private Location resolveTargetLocation(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult result = world.rayTraceBlocks(eye, direction, MAX_RANGE, FluidCollisionMode.NEVER, true);
        Location hit = result != null && result.getHitPosition() != null
                ? result.getHitPosition().toLocation(world)
                : eye.clone().add(direction.multiply(MAX_RANGE));
        int floorY = LocationUtil.getFloorY(world, hit.getBlockX(), hit.getBlockZ(),
                Math.min(hit.getBlockY() + 3, world.getMaxHeight() - 1));
        if (floorY <= world.getMinHeight()) {
            return null;
        }
        return new Location(world, hit.getBlockX() + 0.5, floorY, hit.getBlockZ() + 0.5);
    }

    private void startWarning(Location target) {
        targetLocation = target.clone();
        warningStartTick = AbilityTickManager.getGlobalTick();
        warningActive = true;
        registerTick();
        World world = target.getWorld();
        if (world != null) {
            world.playSound(target, Sound.BLOCK_BEACON_POWER_SELECT, 0.75f, 1.8f);
        }
        spawnWarningCircle(0);
    }

    private void spawnWarningCircle(int elapsed) {
        World world = targetLocation.getWorld();
        if (world == null) {
            return;
        }
        double progress = elapsed / (double) WARNING_TICKS;
        double radius = BLAST_RADIUS * (1.0 - Math.min(1.0, Math.max(0.0, progress)));
        if (radius <= 0.05) {
            ParticleUtil.spawnParticle(world, Particle.DUST, targetLocation, 18, 0.18, 0.03, 0.18, 0.0,
                    WARNING_DUST, 1, 64);
            return;
        }
        for (Vector vector : Circle.of(radius, 96)) {
            Location point = targetLocation.clone().add(vector);
            ParticleUtil.spawnParticle(world, Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0,
                    WARNING_DUST, 1, 64);
        }
        if (elapsed % 5 == 0) {
            world.playSound(targetLocation, Sound.BLOCK_NOTE_BLOCK_HAT, 0.45f, 1.2f + (float) progress);
        }
    }

    private void fireLaser(Location impactLocation) {
        Player player = getPlayer();
        World world = impactLocation.getWorld();
        if (player == null || world == null) {
            return;
        }
        for (double y = 0.0; y <= LASER_HEIGHT; y += 0.35) {
            Location point = impactLocation.clone().add(0, y, 0);
            ParticleUtil.spawnParticle(world, Particle.END_ROD, point, 2, 0.03, 0.03, 0.03, 0.0, 1, 64);
            if (((int) (y * 10)) % 7 == 0) {
                ParticleUtil.spawnParticle(world, Particle.DUST, point, 1, 0.04, 0.04, 0.04, 0.0,
                        WARNING_DUST, 1, 64);
            }
        }
        startRisingExplosionEffect(impactLocation);
        world.playSound(impactLocation, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.9f, 0.55f);
        world.playSound(impactLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.75f);

        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(impactLocation, BLAST_RADIUS, player,
                entity -> !(entity instanceof ArmorStand))) {
            target.setNoDamageTicks(0);
            target.damage(calculateDamage(target), player);
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, BLIND_TICKS, 0, true, true));
        }
    }

    private double calculateDamage(LivingEntity target) {
        AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        double maximum = maxHealth != null ? maxHealth.getValue() : target.getHealth();
        double missingHealth = Math.max(0.0, maximum - target.getHealth());
        return BASE_DAMAGE + missingHealth * MISSING_HEALTH_DAMAGE_RATIO;
    }

    private void startRisingExplosionEffect(Location base) {
        explosionLocation = base.clone();
        explosionLayer = 0;
        explosionActive = true;
        registerTick();
    }

    private void tickRisingExplosion() {
        World world = explosionLocation.getWorld();
        if (world == null) {
            clearExplosionState();
            return;
        }
        double y = explosionLayer * RISING_EXPLOSION_STEP;
        if (y > RISING_EXPLOSION_HEIGHT) {
            clearExplosionState();
            return;
        }
        spawnExplosionLayer(world, explosionLocation, y);
        explosionLayer++;
    }

    private void spawnExplosionLayer(World world, Location base, double y) {
        Location center = base.clone().add(0, y, 0);
        ParticleUtil.spawnParticle(world, Particle.EXPLOSION, center, 14, BLAST_RADIUS * 0.55, 0.18,
                BLAST_RADIUS * 0.55, 0.0, 1, 64);
        ParticleUtil.spawnParticle(world, Particle.LARGE_SMOKE, center, 42, BLAST_RADIUS * 0.7, 0.35,
                BLAST_RADIUS * 0.7, 0.05, 1, 64);
        for (Vector vector : Circle.of(BLAST_RADIUS, 96)) {
            Location edge = center.clone().add(vector);
            ParticleUtil.spawnParticle(world, Particle.FLAME, edge, 2, 0.08, 0.08, 0.08, 0.0, 1, 64);
        }
        if (explosionLayer % 2 == 0) {
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 1.0f + explosionLayer * 0.04f);
        }
    }

    private void clearWarningState() {
        warningActive = false;
        targetLocation = null;
        warningStartTick = 0;
    }

    private void clearExplosionState() {
        explosionActive = false;
        explosionLocation = null;
        explosionLayer = 0;
    }

    private void clearEffects() {
        clearWarningState();
        clearExplosionState();
        unregisterTick();
    }
}
