package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.DamageModifier;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
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
        "§7구체에 적이 닿을 때마다 §e별가루 1스택§7을 획득합니다",
        "",
        "§e§l[철괴 우클릭 - 별의 추락]§f §8(쿨타임: 15초)",
        "§7§f10칸§7 내 가장 가까운 적 1명에게 별가루를 모두 소모합니다",
        "§7별가루 1스택당 §c0.5 고정 피해§7를 입힙니다",
        "§7대상의 체력이 피해량 이하라면 §4처형§7합니다"
}, summarize = {
        "§7패시브§f: 회전 구체 3개, 접촉 시 별가루 +1",
        "§7철괴 우클릭§f: 최근접 적 1명에게 별가루 소모",
        "§7효과§f: 스택당 피해 0.5, 체력 이하 시 처형"
})
public class CenterOfUniverse extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 15;
    private static final int ORBIT_SPHERE_COUNT = 3;
    private static final double ORBIT_RADIUS = 3;
    private static final double ORBIT_HEIGHT = 0.85;
    private static final double ORBIT_ROTATION_SPEED = Math.PI / 24.0;
    private static final int ORBIT_TARGET_HIT_COOLDOWN_TICKS = 16;
    private static final double ORBIT_HIT_RADIUS = 1.1;
    private static final double ACTIVE_RANGE = 10.0;
    private static final double DAMAGE_PER_STARDUST = 0.5;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final List<OrbitSphere> orbitSpheres = new ArrayList<>();
    private final Map<UUID, Integer> orbitHitCooldowns = new HashMap<>();
    private double orbitAngle;
    private int stardust;

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
        stardust = 0;
    }

    @Override
    protected void onDestroy() {
        clearAllSpheres();
        orbitHitCooldowns.clear();
        stardust = 0;
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
        if (owner == null || stardust <= 0) {
            if (owner != null) {
                owner.sendMessage("§e별가루가 없습니다");
            }
            return false;
        }

        LivingEntity target = LocationUtil.getNearestEntity(Player.class, owner.getLocation(), ACTIVE_RANGE,
                entity -> LocationUtil.isValidTarget(owner, entity));
        if (target == null) {
            owner.sendMessage("§c10칸 내 대상이 없습니다");
            return false;
        }

        int consumed = stardust;
        stardust = 0;
        useStardust(owner, target, consumed);
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

        if (orbitSpheres.isEmpty()) {
            spawnOrbitSpheres();
        }
    }

    private void spawnOrbitSpheres() {
        if (!orbitSpheres.isEmpty()) {
            return;
        }
        Player owner = getPlayer();

        for (int i = 0; i < ORBIT_SPHERE_COUNT; i++) {
            ArmorStand stand = createSphereStand(owner.getLocation());
            orbitSpheres.add(new OrbitSphere(stand, (Math.PI * 2 / ORBIT_SPHERE_COUNT) * i));
        }
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
                stardust++;
                orbitHitCooldowns.put(target.getUniqueId(), ORBIT_TARGET_HIT_COOLDOWN_TICKS);
                owner.playSound(owner.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.45f);
                owner.sendActionBar(net.kyori.adventure.text.Component.text("별가루 " + stardust + "스택",
                        net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                ParticleUtil.spawnParticle(target.getWorld(), Particle.END_ROD,
                        target.getLocation().clone().add(0, 1, 0),
                        12, 0.25, 0.45, 0.25, 0.03, 1, 0);
            }
        }
    }

    private void useStardust(Player owner, LivingEntity target, int consumed) {
        double damage = consumed * DAMAGE_PER_STARDUST;
        if (target.getHealth() <= damage) {
            DamageModifier.applyFlatDamage(target, target.getHealth() + target.getAbsorptionAmount() + 1.0, owner);
            owner.playSound(owner.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.45f, 1.65f);
            ParticleUtil.spawnParticle(target.getWorld(), Particle.SOUL_FIRE_FLAME,
                    target.getLocation().clone().add(0, 1, 0),
                    24, 0.35, 0.55, 0.35, 0.04, 1, 0);
            return;
        }

        DamageModifier.applyFlatDamage(target, damage, owner);
        owner.playSound(owner.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.6f, 1.35f);
        ParticleUtil.spawnParticle(target.getWorld(), Particle.FIREWORK,
                target.getLocation().clone().add(0, 1, 0),
                18, 0.35, 0.45, 0.35, 0.05, 1, 0);
    }

    private LivingEntity findStandHitTarget(ArmorStand stand, Player owner, double radius) {
        if (stand == null || stand.isDead() || owner == null) {
            return null;
        }
        for (LivingEntity living : LocationUtil.getNearbyLivingEntities(stand.getLocation(), radius, owner,
                entity -> !entity.equals(owner))) {
            if (living != null && !living.isDead() && !isIgnoredOrbitHitTarget(stand, living)) {
                return living;
            }
        }
        return null;
    }

    private boolean isIgnoredOrbitHitTarget(ArmorStand stand, LivingEntity living) {
        if (living.equals(stand)) {
            return true;
        }
        if (!(living instanceof ArmorStand armorStand)) {
            return false;
        }
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin != null && armorStand.getPersistentDataContainer()
                .has(AbilityCombat.getAbilityArmorStandKey(plugin), PersistentDataType.BYTE);
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
}
