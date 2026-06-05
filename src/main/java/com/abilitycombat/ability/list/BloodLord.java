package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@AbilityManifest(name = "피의 군주 (BloodLord)", species = AbilityManifest.Species.UNDEAD, explain = {
        "§e§l[패시브 - 핏빛 탐식]",
        "§7주변 §f15칸§7 이내의 §f플레이어§7가 체력을 잃을 때마다",
        "§7잃은 체력 §f1§7당 §4피 중첩 1§7을 얻습니다",
        "",
        "§e§l[철괴 좌클릭 - 혈흔 폭발]§f §8(쿨타임: 1초)",
        "§7§4피 중첩§7을 모두 소모(최소 15)해서 주변 §f10칸§7 내 적에게 피해를 입힙니다",
        "§7소모한 피 중첩 §f1§7당 §c0.5 + 잃은 체력 50%§7의 피해를 입힙니다",
        "",
        "§e§l[철괴 우클릭 - 피의 속박]§f §8(쿨타임: 1초)",
        "§4피 중첩 20§7을 소모해 §e5초간 기절§7하는 투사체를 발사합니다",
        "§8좌클릭/우클릭 쿨타임은 서로 공유됩니다"
}, summarize = {
        "§7패시브§f: 주변 15칸 플레이어가 체력 1 잃을 때마다 피 중첩 1 획득",
        "§7철괴 좌클릭§f: 최소 15 중첩, 주변 10칸에 중첩당 0.25 + 잃은 체력 30% 피해",
        "§7철괴 우클릭§f: 중첩 20 소모, 적중 시 5초 기절 투사체"
})
public class BloodLord extends AbilityBase implements ActiveHandler {

    private static final double PASSIVE_RANGE = 15.0;
    private static final double PASSIVE_RANGE_SQUARED = PASSIVE_RANGE * PASSIVE_RANGE;
    private static final double BLOOD_EXPLOSION_RANGE = 10.0;
    private static final double BLOOD_EXPLOSION_MIN_STACKS = 15.0;
    private static final double BLOOD_EXPLOSION_DAMAGE_PER_STACK = 0.5;
    private static final double BLOOD_EXPLOSION_MISSING_HEALTH_RATIO = 0.50;
    private static final double BLOOD_BIND_COST = 20.0;
    private static final int BLOOD_BIND_STUN_TICKS = 100;
    private static final double PROJECTILE_SPEED = 1.35;
    private static final int PROJECTILE_MAX_TICKS = 28;
    private static final double PROJECTILE_HIT_RADIUS = 0.9;
    private static final int SKILL_COOLDOWN_SECONDS = 1;
    private static final String HUD_KEY = "bloodlord:stacks";
    private static final int HUD_PRIORITY = 3;
    private static final BlockData BLOOD_BLOCK_DATA = Material.REDSTONE_BLOCK.createBlockData();
    private static final DecimalFormat STACK_FORMAT = new DecimalFormat("0.0");

    private final List<BloodBindProjectile> projectiles = new ArrayList<>();
    private final Cooldown skillCooldown = new Cooldown(SKILL_COOLDOWN_SECONDS);
    private double bloodStacks;

    public BloodLord(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageEvent.class);
        updateHud();
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        projectiles.clear();
        clearHud();
    }

    @Override
    protected void onDestroy() {
        projectiles.clear();
        clearHud();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT) {
            return false;
        }
        if (skillCooldown.isCooldown()) {
            return false;
        }
        if (clickType == ClickType.LEFT_CLICK) {
            return castBloodBurst();
        }
        if (clickType == ClickType.RIGHT_CLICK) {
            return castBloodBind();
        }
        return false;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent e) {
            onEntityDamage(e);
        }
    }

    @Override
    public void onTick(int tick) {
        tickProjectiles();
        if (tick % 20 == 0) {
            updateHud();
        }
    }

    private void onEntityDamage(EntityDamageEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player target)) {
            return;
        }
        double lostHealth = Math.min(target.getHealth(), getCalculatedFinalDamage(event));
        if (lostHealth <= 0.0) {
            return;
        }
        Player owner = getPlayer();
        if (!owner.isOnline() || owner.isDead() || owner.getWorld() != target.getWorld()) {
            return;
        }
        if (owner.getLocation().distanceSquared(target.getLocation()) > PASSIVE_RANGE_SQUARED) {
            return;
        }
        bloodStacks += lostHealth;
        updateHud();
    }

    private boolean castBloodBurst() {
        if (bloodStacks < BLOOD_EXPLOSION_MIN_STACKS) {
            showStatus("피 중첩이 부족합니다 (" + STACK_FORMAT.format(bloodStacks) + "/15.0)", NamedTextColor.RED);
            return false;
        }

        Player owner = getPlayer();
        var targets = LocationUtil.getNearbyPlayers(owner.getLocation(), BLOOD_EXPLOSION_RANGE, owner,
                entity -> !entity.equals(owner));
        if (targets.isEmpty()) {
            showStatus("주변에 폭발시킬 적이 없습니다", NamedTextColor.RED);
            return false;
        }

        double consumed = bloodStacks;
        bloodStacks = 0.0;
        for (Player target : targets) {
            double missingHealth = getMissingHealth(target);
            double damage = (consumed * BLOOD_EXPLOSION_DAMAGE_PER_STACK)
                    + (missingHealth * BLOOD_EXPLOSION_MISSING_HEALTH_RATIO);
            target.damage(damage, owner);
            target.setNoDamageTicks(0);
            spawnBurstEffect(target.getLocation());
        }
        spawnBurstEffect(owner.getLocation());
        owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.35f);
        owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.9f, 0.7f);
        skillCooldown.start();
        applyIronCooldownIfEmpty(SKILL_COOLDOWN_SECONDS);
        updateHud();
        return true;
    }

    private boolean castBloodBind() {
        if (bloodStacks < BLOOD_BIND_COST) {
            showStatus("피 중첩이 부족합니다 (" + STACK_FORMAT.format(bloodStacks) + "/20.0)", NamedTextColor.RED);
            return false;
        }

        Player owner = getPlayer();
        bloodStacks -= BLOOD_BIND_COST;
        Vector direction = owner.getEyeLocation().getDirection().normalize();
        Location start = owner.getEyeLocation().clone().add(direction.clone().multiply(0.6));
        projectiles.add(new BloodBindProjectile(start, direction));
        owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.85f, 0.7f);
        skillCooldown.start();
        applyIronCooldownIfEmpty(SKILL_COOLDOWN_SECONDS);
        updateHud();
        return true;
    }

    private void tickProjectiles() {
        if (projectiles.isEmpty()) {
            return;
        }

        Iterator<BloodBindProjectile> iterator = projectiles.iterator();
        while (iterator.hasNext()) {
            BloodBindProjectile projectile = iterator.next();
            projectile.ticks++;
            if (projectile.ticks > PROJECTILE_MAX_TICKS) {
                iterator.remove();
                continue;
            }

            Location current = projectile.location;
            Vector delta = projectile.direction.clone().multiply(PROJECTILE_SPEED);
            Location next = current.clone().add(delta);
            if (next.getBlock().getType().isSolid()) {
                spawnBindBreakEffect(next);
                iterator.remove();
                continue;
            }

            spawnTrail(current, next);
            Player hit = findProjectileHitTarget(next);
            projectile.location = next;
            if (hit == null) {
                continue;
            }

            Stun.apply(hit, BLOOD_BIND_STUN_TICKS);
            spawnBindImpactEffect(hit.getLocation());
            hit.getWorld().playSound(hit.getLocation(), Sound.ENTITY_WITHER_HURT, 0.9f, 1.6f);
            iterator.remove();
        }
    }

    private Player findProjectileHitTarget(Location location) {
        for (Player target : LocationUtil.getNearbyPlayers(location, PROJECTILE_HIT_RADIUS, getPlayer(),
                entity -> !entity.equals(getPlayer()))) {
            return target;
        }
        return null;
    }

    private void spawnTrail(Location from, Location to) {
        Vector delta = to.toVector().subtract(from.toVector()).multiply(0.25);
        Location point = from.clone();
        for (int i = 0; i < 4; i++) {
            point.add(delta);
            ParticleUtil.spawnParticle(point.getWorld(), Particle.BLOCK, point,
                    4, 0.08, 0.08, 0.08, 0.02, BLOOD_BLOCK_DATA, 1, 0);
        }
    }

    private void spawnBurstEffect(Location location) {
        Location center = location.clone().add(0, 1.0, 0);
        ParticleUtil.spawnParticle(center.getWorld(), Particle.BLOCK, center,
                28, 0.45, 0.45, 0.45, 0.1, BLOOD_BLOCK_DATA, 1, 0);
        ParticleUtil.spawnParticle(center.getWorld(), Particle.DAMAGE_INDICATOR, center,
                12, 0.25, 0.3, 0.25, 0.15, null, 1, 0);
    }

    private void spawnBindImpactEffect(Location location) {
        Location center = location.clone().add(0, 1.0, 0);
        ParticleUtil.spawnParticle(center.getWorld(), Particle.BLOCK, center,
                16, 0.3, 0.35, 0.3, 0.08, BLOOD_BLOCK_DATA, 1, 0);
        ParticleUtil.spawnParticle(center.getWorld(), Particle.SMOKE, center,
                10, 0.2, 0.25, 0.2, 0.02, null, 1, 0);
    }

    private void spawnBindBreakEffect(Location location) {
        ParticleUtil.spawnParticle(location.getWorld(), Particle.BLOCK, location,
                12, 0.18, 0.18, 0.18, 0.03, BLOOD_BLOCK_DATA, 1, 0);
    }

    private double getMissingHealth(Player target) {
        AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0;
        return Math.max(0.0, max - target.getHealth());
    }

    private void updateHud() {
        Component message = Component.text("피 중첩 ", NamedTextColor.DARK_RED)
                .append(Component.text(STACK_FORMAT.format(bloodStacks), NamedTextColor.WHITE));
        if (getActionbarChannel() != null) {
            getActionbarChannel().update(getPlayer(), HUD_KEY, HUD_PRIORITY, message);
        } else {
            getPlayer().sendActionBar(message);
        }
    }

    private void clearHud() {
        if (getActionbarChannel() != null) {
            getActionbarChannel().clear(getPlayer(), HUD_KEY);
        } else {
            getPlayer().sendActionBar(Component.empty());
        }
    }

    private void showStatus(String message, NamedTextColor color) {
        Component component = Component.text(message, color);
        if (getActionbarChannel() != null) {
            getActionbarChannel().update(getPlayer(), HUD_KEY, HUD_PRIORITY + 1, component);
        } else {
            getPlayer().sendActionBar(component);
        }
    }

    private static final class BloodBindProjectile {
        private Location location;
        private final Vector direction;
        private int ticks;

        private BloodBindProjectile(Location location, Vector direction) {
            this.location = location;
            this.direction = direction.clone();
        }
    }
}
