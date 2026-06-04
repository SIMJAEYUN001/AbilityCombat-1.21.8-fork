package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Bind;
import com.abilitycombat.effect.Disarm;
import com.abilitycombat.effect.Freeze;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Collection;

public class GeneratedAbility extends AbilityBase implements ActiveHandler {

    private final GeneratedAbilitySpec spec;
    private final Cooldown cooldown;

    public GeneratedAbility(Participant participant, GeneratedAbilitySpec spec) {
        super(participant, spec.descriptor());
        this.spec = spec;
        this.cooldown = new Cooldown(spec.cooldownSeconds());
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
        boolean used = switch (spec.pattern()) {
            case STRIKE -> castStrike(player);
            case BLAST -> castBlast(player);
            case NOVA -> castNova(player);
            case DASH -> castDash(player);
            case GUARD -> castGuard(player);
            case PULL -> castPull(player);
        };
        if (!used) {
            player.sendMessage("§c대상이 없습니다.");
            return false;
        }
        cooldown.start();
        applyIronCooldownIfEmpty(spec.cooldownSeconds());
        return true;
    }

    private boolean castStrike(Player player) {
        LivingEntity target = findLookTarget(player);
        if (target == null) {
            return false;
        }
        applyEffect(player, target, spec.damage(), spec.crowdControlTicks());
        playImpact(target.getLocation());
        return true;
    }

    private boolean castBlast(Player player) {
        Location center = getAimedCenter(player);
        Collection<LivingEntity> targets = LocationUtil.getNearbyLivingEntities(center, spec.radius(), player, null);
        boolean hit = false;
        for (LivingEntity target : targets) {
            applyEffect(player, target, spec.damage(), spec.crowdControlTicks());
            hit = true;
        }
        playImpact(center);
        return hit;
    }

    private boolean castNova(Player player) {
        Collection<LivingEntity> targets = LocationUtil.getNearbyLivingEntities(
                player.getLocation(), spec.radius(), player, null);
        boolean hit = false;
        for (LivingEntity target : targets) {
            applyEffect(player, target, spec.damage(), spec.crowdControlTicks());
            knockAway(player, target);
            hit = true;
        }
        playImpact(player.getLocation());
        heal(player, spec.heal());
        return hit || spec.heal() > 0.0;
    }

    private boolean castDash(Player player) {
        Vector direction = player.getEyeLocation().getDirection().normalize();
        player.setVelocity(direction.multiply(1.35).setY(Math.max(0.1, direction.getY() * 0.35)));
        Location center = player.getLocation().add(direction.multiply(3.2));
        Collection<LivingEntity> targets = LocationUtil.getNearbyLivingEntities(center, spec.radius(), player, null);
        for (LivingEntity target : targets) {
            applyEffect(player, target, spec.damage(), spec.crowdControlTicks());
            knockAway(player, target);
        }
        playImpact(center);
        heal(player, spec.heal());
        return true;
    }

    private boolean castGuard(Player player) {
        int duration = Math.max(40, spec.crowdControlTicks());
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, 0, true, false));
        heal(player, spec.heal());
        Collection<LivingEntity> targets = LocationUtil.getNearbyLivingEntities(
                player.getLocation(), Math.max(3.0, spec.radius() * 0.7), player, null);
        for (LivingEntity target : targets) {
            applyCrowdControl(target, spec.crowdControl(), Math.max(20, spec.crowdControlTicks() / 2));
        }
        playImpact(player.getLocation());
        return true;
    }

    private boolean castPull(Player player) {
        Collection<LivingEntity> targets = LocationUtil.getNearbyLivingEntities(
                player.getLocation(), spec.radius(), player, null);
        boolean hit = false;
        for (LivingEntity target : targets) {
            Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector());
            if (pull.lengthSquared() > 0.001) {
                target.setVelocity(pull.normalize().multiply(Math.max(0.35, spec.knockback())));
            }
            applyEffect(player, target, spec.damage(), spec.crowdControlTicks());
            hit = true;
        }
        playImpact(player.getLocation());
        return hit;
    }

    private LivingEntity findLookTarget(Player player) {
        return LocationUtil.getEntityLookingAt(LivingEntity.class, player, spec.range(),
                target -> LocationUtil.isValidTarget(player, target));
    }

    private Location getAimedCenter(Player player) {
        LivingEntity target = findLookTarget(player);
        if (target != null) {
            return target.getLocation();
        }
        return player.getEyeLocation().add(player.getEyeLocation().getDirection().normalize().multiply(spec.range()));
    }

    private void applyEffect(Player source, LivingEntity target, double damage, int crowdControlTicks) {
        if (!LocationUtil.isValidTarget(source, target)) {
            return;
        }
        if (damage > 0.0) {
            target.damage(damage, source);
        }
        applyCrowdControl(target, spec.crowdControl(), crowdControlTicks);
    }

    private void applyCrowdControl(LivingEntity target, GeneratedAbilitySpec.CrowdControlType type, int ticks) {
        if (target == null || ticks <= 0) {
            return;
        }
        switch (type) {
            case STUN -> Stun.apply(target, ticks);
            case BIND -> Bind.apply(target, ticks);
            case DISARM -> Disarm.apply(target, ticks);
            case FREEZE -> Freeze.apply(target, ticks);
            case NONE -> {
            }
        }
    }

    private void knockAway(Player source, LivingEntity target) {
        if (spec.knockback() <= 0.0) {
            return;
        }
        Vector direction = target.getLocation().toVector().subtract(source.getLocation().toVector());
        if (direction.lengthSquared() <= 0.001) {
            return;
        }
        target.setVelocity(direction.normalize().multiply(spec.knockback()).setY(0.25));
    }

    private void heal(Player player, double amount) {
        if (amount <= 0.0) {
            return;
        }
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0;
        player.setHealth(Math.min(max, player.getHealth() + amount));
    }

    private void playImpact(Location location) {
        if (location == null) {
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        ParticleUtil.spawnParticle(world, Particle.CRIT, location, 18, 0.35, 0.35, 0.35, 0.02, 2, 64);
        ParticleUtil.spawnParticle(world, Particle.ENCHANTED_HIT, location, 10, 0.45, 0.45, 0.45, 0.01, 2, 64);
        world.playSound(location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.75f, 1.1f);
    }
}
