package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "우주의 중심 (CenterOfUniverse)", species = AbilityManifest.Species.GOD, explain = {
        "§e§l[패시브 - 공전하는 핵]",
        "§7사용자 주변을 회전하며 따라다니는 §53개의 구체§7를 소환합니다",
        "§7구체에 닿은 적은 §c5의 피해§7를 받고 §82초간 구속§7됩니다",
        "",
        "§e§l[철괴 우클릭 - 별의 추락]§f §8(쿨타임: 15초)",
        "§7§f10칸§7 내 가장 가까운 적에게 구체 §53개를 모두 발사§7합니다",
        "§7구체 1개당 §c7의 피해§7를 입힙니다",
        "§7쿨타임이 끝나면 주변 구체가 다시 생성됩니다"
}, summarize = {
        "§7패시브§f: 회전 구체 3개, 접촉 시 피해 5 + 2초 구속",
        "§7철괴 우클릭§f: 최근접 적에게 구체 3개 발사 (개당 피해 7)",
        "§7쿨타임 종료 후§f: 구체 자동 재생성"
})
public class CenterOfUniverse extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 15;
    private static final int ORBIT_SPHERE_COUNT = 3;
    private static final double ORBIT_RADIUS = 3;
    private static final double ORBIT_HEIGHT = 0.85;
    private static final double ORBIT_ROTATION_SPEED = Math.PI / 24.0;
    private static final double ORBIT_DAMAGE = 5.0;
    private static final int ORBIT_BIND_TICKS = 40;
    private static final int ORBIT_BIND_AMPLIFIER = 0;
    private static final int ORBIT_TARGET_HIT_COOLDOWN_TICKS = 16;
    private static final double ORBIT_HIT_RADIUS = 1.1;

    private static final double ACTIVE_RANGE = 10.0;
    private static final double PROJECTILE_SPEED = 1.5;
    private static final double HOMING_STRENGTH = 0.22;
    private static final int PROJECTILE_MAX_TICKS = 50;
    private static final double PROJECTILE_DAMAGE = 7.0;
    private static final double PROJECTILE_HIT_RADIUS = 1.0;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final List<OrbitSphere> orbitSpheres = new ArrayList<>();
    private final List<ProjectileSphere> projectileSpheres = new ArrayList<>();
    private final Map<UUID, Integer> orbitHitCooldowns = new HashMap<>();
    private double orbitAngle;

    public CenterOfUniverse(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        spawnOrbitSpheres();
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        clearAllSpheres();
        orbitHitCooldowns.clear();
    }

    @Override
    protected void onDestroy() {
        clearAllSpheres();
        orbitHitCooldowns.clear();
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
        Player owner = getPlayer();
        LivingEntity target = LocationUtil.getNearestEntity(Player.class, owner.getLocation(), ACTIVE_RANGE,
                entity -> !entity.equals(owner) && LocationUtil.isValidTarget(owner, entity));
        if (target == null || orbitSpheres.isEmpty()) {
            return false;
        }

        launchSpheres(target);
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        // no direct events
    }

    @Override
    public void onTick(int tick) {
        tickOrbitCooldowns();
        tickOrbitSpheres();
        tickProjectileSpheres();

        if (!cooldown.isCooldown() && orbitSpheres.isEmpty() && projectileSpheres.isEmpty()) {
            spawnOrbitSpheres();
        }
    }

    private void spawnOrbitSpheres() {
        if (!orbitSpheres.isEmpty()) {
            return;
        }
        Player owner = getPlayer();
        World world = owner.getWorld();

        for (int i = 0; i < ORBIT_SPHERE_COUNT; i++) {
            ArmorStand stand = createSphereStand(owner.getLocation());
            orbitSpheres.add(new OrbitSphere(stand, (Math.PI * 2 / ORBIT_SPHERE_COUNT) * i));
        }
    }

    private void launchSpheres(LivingEntity target) {
        for (OrbitSphere sphere : orbitSpheres) {
            if (sphere.stand == null || sphere.stand.isDead()) {
                continue;
            }
            Location location = sphere.stand.getLocation().clone();
            Vector direction = target.getEyeLocation().toVector().subtract(location.toVector());
            if (direction.lengthSquared() <= 1.0E-6) {
                direction = getPlayer().getLocation().getDirection();
            }
            projectileSpheres.add(new ProjectileSphere(sphere.stand, target, direction.normalize()));
        }
        orbitSpheres.clear();
    }

    private void tickOrbitCooldowns() {
        if (orbitHitCooldowns.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> iterator = orbitHitCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int next = entry.getValue() - 1;
            if (next <= 0) {
                iterator.remove();
            } else {
                entry.setValue(next);
            }
        }
    }

    private void tickOrbitSpheres() {
        if (orbitSpheres.isEmpty()) {
            return;
        }
        Player owner = getPlayer();
        orbitAngle += ORBIT_ROTATION_SPEED;

        Iterator<OrbitSphere> iterator = orbitSpheres.iterator();
        while (iterator.hasNext()) {
            OrbitSphere sphere = iterator.next();
            if (sphere.stand == null || sphere.stand.isDead()) {
                iterator.remove();
                continue;
            }

            Location location = getOrbitLocation(owner, orbitAngle + sphere.angleOffset);
            sphere.stand.teleport(location);

            LivingEntity target = findStandHitTarget(sphere.stand, owner, ORBIT_HIT_RADIUS);
            if (target != null && !orbitHitCooldowns.containsKey(target.getUniqueId())) {
                target.damage(ORBIT_DAMAGE, owner);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                        ORBIT_BIND_TICKS, ORBIT_BIND_AMPLIFIER, true, true, true));
                orbitHitCooldowns.put(target.getUniqueId(), ORBIT_TARGET_HIT_COOLDOWN_TICKS);
                ParticleUtil.spawnParticle(target.getWorld(), Particle.PORTAL,
                        target.getLocation().clone().add(0, 1, 0),
                        12, 0.25, 0.45, 0.25, 0.15, 1, 0);
            }
        }
    }

    private void tickProjectileSpheres() {
        if (projectileSpheres.isEmpty()) {
            return;
        }

        Iterator<ProjectileSphere> iterator = projectileSpheres.iterator();
        while (iterator.hasNext()) {
            ProjectileSphere sphere = iterator.next();
            if (sphere.stand == null || sphere.stand.isDead()) {
                iterator.remove();
                continue;
            }
            if (sphere.target == null || sphere.target.isDead()) {
                removeSphereStand(sphere.stand);
                iterator.remove();
                continue;
            }

            sphere.ticks++;
            if (sphere.ticks > PROJECTILE_MAX_TICKS) {
                removeSphereStand(sphere.stand);
                iterator.remove();
                continue;
            }

            Location current = sphere.stand.getLocation();
            Vector toTarget = sphere.target.getEyeLocation().toVector().subtract(current.toVector());
            if (toTarget.lengthSquared() > 1.0E-6) {
                toTarget.normalize();
                sphere.direction.multiply(1.0 - HOMING_STRENGTH).add(toTarget.multiply(HOMING_STRENGTH));
                if (sphere.direction.lengthSquared() > 1.0E-6) {
                    sphere.direction.normalize();
                }
            }

            Vector delta = sphere.direction.clone().multiply(PROJECTILE_SPEED);
            Location next = current.clone().add(delta);
            if (next.getBlock().getType().isSolid()) {
                removeSphereStand(sphere.stand);
                iterator.remove();
                continue;
            }

            next.setDirection(sphere.direction);
            sphere.stand.teleport(next);

            LivingEntity entity = findStandHitTarget(sphere.stand, getPlayer(), PROJECTILE_HIT_RADIUS);
            if (entity != null) {
                entity.damage(PROJECTILE_DAMAGE, getPlayer());
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_HURT, 0.7f, 1.5f);
                ParticleUtil.spawnParticle(entity.getWorld(), Particle.DRAGON_BREATH,
                        entity.getLocation().clone().add(0, 1, 0),
                        10, 0.3, 0.4, 0.3, 0.02, 1, 0);
                removeSphereStand(sphere.stand);
                iterator.remove();
            }
        }
    }

    private LivingEntity findStandHitTarget(ArmorStand stand, Player owner, double radius) {
        if (stand == null || stand.isDead() || owner == null) {
            return null;
        }
        for (LivingEntity living : LocationUtil.getNearbyLivingEntities(stand.getLocation(), radius, owner,
                entity -> !entity.equals(owner))) {
            if (living != null && !living.isDead()) {
                return living;
            }
        }
        return null;
    }

    private Location getOrbitLocation(Player owner, double angle) {
        Location base = owner.getLocation().clone();
        double x = Math.cos(angle) * ORBIT_RADIUS;
        double z = Math.sin(angle) * ORBIT_RADIUS;
        Location location = base.add(x, ORBIT_HEIGHT, z);
        Vector facing = owner.getLocation().toVector().subtract(location.toVector());
        if (facing.lengthSquared() > 1.0E-6) {
            location.setDirection(facing);
        }
        return location;
    }

    private ArmorStand createSphereStand(Location location) {
        ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class, entity -> {
            entity.setVisible(false);
            entity.setMarker(false);
            entity.setGravity(false);
            entity.setSmall(true);
            entity.setBasePlate(false);
            entity.setArms(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.getEquipment().setHelmet(new ItemStack(Material.DRAGON_EGG));
            entity.setHeadPose(new EulerAngle(0, 0, 0));
        });
        AbilityCombat.markAbilityArmorStand(stand);
        return stand;
    }

    private void clearAllSpheres() {
        for (OrbitSphere sphere : orbitSpheres) {
            removeSphereStand(sphere.stand);
        }
        orbitSpheres.clear();
        for (ProjectileSphere sphere : projectileSpheres) {
            removeSphereStand(sphere.stand);
        }
        projectileSpheres.clear();
    }

    private void removeSphereStand(ArmorStand stand) {
        if (stand != null && !stand.isDead()) {
            stand.remove();
        }
    }

    private static final class OrbitSphere {
        private final ArmorStand stand;
        private final double angleOffset;

        private OrbitSphere(ArmorStand stand, double angleOffset) {
            this.stand = stand;
            this.angleOffset = angleOffset;
        }
    }

    private static final class ProjectileSphere {
        private final ArmorStand stand;
        private final LivingEntity target;
        private final Vector direction;
        private int ticks;

        private ProjectileSphere(ArmorStand stand, LivingEntity target, Vector direction) {
            this.stand = stand;
            this.target = target;
            this.direction = direction;
        }
    }
}
