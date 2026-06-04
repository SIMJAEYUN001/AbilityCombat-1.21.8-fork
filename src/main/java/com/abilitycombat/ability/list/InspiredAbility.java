package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Bind;
import com.abilitycombat.effect.Bleed;
import com.abilitycombat.effect.Disarm;
import com.abilitycombat.effect.Freeze;
import com.abilitycombat.effect.Infection;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.GameManager;
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
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

public class InspiredAbility extends AbilityBase implements ActiveHandler {

    private final InspiredAbilitySpec spec;
    private final Cooldown cooldown;

    public InspiredAbility(Participant participant, InspiredAbilitySpec spec) {
        super(participant, spec.descriptor());
        this.spec = spec;
        this.cooldown = new Cooldown(spec.cooldownSeconds());
    }

    @Override
    protected void onActivate() {
        if (spec.style() == InspiredAbilitySpec.Style.GLASS_CANNON) {
            subscribeEvent(EntityDamageEvent.class);
        }
    }

    @Override
    protected void onDeactivate() {
        if (spec.style() == InspiredAbilitySpec.Style.GLASS_CANNON) {
            unsubscribeEvent(EntityDamageEvent.class);
        }
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (spec.style() == InspiredAbilitySpec.Style.GLASS_CANNON) {
            Player player = getPlayer();
            if (player != null) {
                player.sendMessage("§e" + getDisplayName() + "§7는 패시브 능력입니다.");
            }
            return true;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        boolean used = switch (spec.style()) {
            case SINGLE -> castSingle(player);
            case BLAST -> castBlast(player);
            case NOVA -> castNova(player);
            case DASH -> castDash(player);
            case PULL -> castPull(player);
            case GUARD -> castGuard(player);
            case ALLY -> castAlly(player);
            case ASSASSIN -> castAssassin(player);
            case BLACK_HOLE -> castBlackHole(player);
            case CURSE -> castCurse(player);
            case SWAP -> castSwap(player);
            case FROST -> castFrost(player);
            case SOUL -> castSoul(player);
            case EXECUTE -> castExecute(player);
            case GAMBLE -> castGamble(player);
            case PORTAL -> castPortal(player);
            case MARK -> castMark(player);
            case DEFLECT -> castDeflect(player);
            case SUMMON -> castSummon(player);
            case GLASS_CANNON -> false;
        };
        if (!used) {
            player.sendMessage("§c대상이 없습니다.");
            return false;
        }
        cooldown.start();
        applyIronCooldownIfEmpty(spec.cooldownSeconds());
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (spec.style() != InspiredAbilitySpec.Style.GLASS_CANNON
                || (event instanceof Cancellable cancellable && cancellable.isCancelled())) {
            return;
        }
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent damageByEntityEvent
                && isDamageSource(player, damageByEntityEvent)) {
            increaseOutgoingDamage(damageByEntityEvent, 25.0);
        }
        if (event instanceof EntityDamageEvent damageEvent && damageEvent.getEntity().equals(player)) {
            increaseIncomingDamage(damageEvent, 15.0);
        }
    }

    private boolean castSingle(Player player) {
        LivingEntity target = findLookTarget(player);
        if (target == null) {
            return false;
        }
        applyEffect(player, target, spec.damage());
        knockAway(player, target, spec.knockback() * 0.6);
        playImpact(target.getLocation());
        return true;
    }

    private boolean castBlast(Player player) {
        Location center = getAimedCenter(player);
        boolean hit = applyArea(player, center, spec.radius(), spec.damage(), true);
        playImpact(center);
        return hit;
    }

    private boolean castNova(Player player) {
        boolean hit = applyArea(player, player.getLocation(), spec.radius(), spec.damage(), true);
        heal(player, spec.heal());
        playImpact(player.getLocation());
        return hit || spec.heal() > 0.0;
    }

    private boolean castDash(Player player) {
        Vector direction = player.getEyeLocation().getDirection().normalize();
        player.setVelocity(direction.clone().multiply(1.45).setY(Math.max(0.08, direction.getY() * 0.35)));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 50, 1, true, false));
        Location center = player.getLocation().clone().add(direction.multiply(Math.min(4.0, spec.range() * 0.35)));
        applyArea(player, center, spec.radius(), spec.damage(), true);
        playImpact(center);
        return true;
    }

    private boolean castPull(Player player) {
        Collection<LivingEntity> targets = LocationUtil.getNearbyLivingEntities(
                player.getLocation(), spec.radius(), player, null);
        boolean hit = false;
        for (LivingEntity target : targets) {
            pull(target, player.getLocation(), spec.knockback());
            applyEffect(player, target, spec.damage());
            hit = true;
        }
        playImpact(player.getLocation());
        return hit;
    }

    private boolean castGuard(Player player) {
        cleanseCrowdControl(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1, true, false));
        heal(player, spec.heal());
        applyArea(player, player.getLocation(), Math.max(3.0, spec.radius() * 0.65), spec.damage() * 0.35, false);
        playImpact(player.getLocation());
        return true;
    }

    private boolean castAlly(Player player) {
        int duration = 120;
        applyAllyBuff(player, duration);
        for (Player ally : LocationUtil.getNearbyEntities(Player.class, player.getLocation(), spec.radius(),
                candidate -> isAlly(player, candidate))) {
            applyAllyBuff(ally, duration);
        }
        applyArea(player, player.getLocation(), Math.max(3.0, spec.radius() * 0.65), spec.damage() * 0.45, false);
        playImpact(player.getLocation());
        return true;
    }

    private boolean castAssassin(Player player) {
        LivingEntity target = findLookTarget(player);
        if (target == null) {
            return false;
        }
        teleportBehind(player, target);
        applyEffect(player, target, spec.damage() + 1.2);
        Bleed.apply(target, 80, 0.45, player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, true, false));
        playImpact(target.getLocation());
        return true;
    }

    private boolean castBlackHole(Player player) {
        Location center = getAimedCenter(player);
        Collection<LivingEntity> targets = LocationUtil.getNearbyLivingEntities(center, spec.radius(), player, null);
        boolean hit = false;
        for (LivingEntity target : targets) {
            pull(target, center, 0.82);
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 45, 0, true, false));
            applyEffect(player, target, spec.damage());
            hit = true;
        }
        playImpact(center);
        return hit;
    }

    private boolean castCurse(Player player) {
        LivingEntity target = findLookTarget(player);
        if (target == null) {
            return false;
        }
        applyEffect(player, target, spec.damage());
        Bleed.apply(target, 100, 0.55, player);
        Infection.apply(target, 80);
        heal(player, spec.heal());
        playImpact(target.getLocation());
        return true;
    }

    private boolean castSwap(Player player) {
        LivingEntity target = findLookTarget(player);
        if (target == null) {
            return false;
        }
        Location playerLocation = player.getLocation().clone();
        Location targetLocation = target.getLocation().clone();
        target.teleport(playerLocation);
        player.teleport(targetLocation);
        applyEffect(player, target, spec.damage() * 0.7);
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 70, 0, true, false));
        playImpact(targetLocation);
        return true;
    }

    private boolean castFrost(Player player) {
        Location center = getAimedCenter(player);
        boolean hit = applyArea(player, center, spec.radius(), spec.damage(), false);
        playImpact(center);
        return hit;
    }

    private boolean castSoul(Player player) {
        LivingEntity target = findLookTarget(player);
        if (target == null) {
            return false;
        }
        teleportBehind(player, target);
        applyEffect(player, target, spec.damage());
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, true, false));
        heal(player, spec.heal());
        playImpact(target.getLocation());
        return true;
    }

    private boolean castExecute(Player player) {
        LivingEntity target = findLookTarget(player);
        if (target == null) {
            return false;
        }
        AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0;
        double damage = target.getHealth() <= max * 0.35 ? spec.damage() * 1.8 : spec.damage() * 0.85;
        applyEffect(player, target, damage);
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 90, 0, true, false));
        playImpact(target.getLocation());
        return true;
    }

    private boolean castGamble(Player player) {
        int roll = ThreadLocalRandom.current().nextInt(4);
        if (roll == 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, true, false));
            heal(player, spec.heal());
            playImpact(player.getLocation());
            return true;
        }
        if (roll == 1) {
            return castBlast(player);
        }
        if (roll == 2) {
            return castPull(player);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 0, true, false));
        playImpact(player.getLocation());
        return true;
    }

    private boolean castPortal(Player player) {
        LivingEntity target = findLookTarget(player);
        if (target != null) {
            teleportBehind(player, target);
            applyEffect(player, target, spec.damage() * 0.85);
            playImpact(target.getLocation());
            return true;
        }
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location destination = player.getLocation().clone().add(direction.multiply(Math.min(8.0, spec.range())));
        player.teleport(destination);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, true, false));
        playImpact(destination);
        return true;
    }

    private boolean castMark(Player player) {
        LivingEntity target = findLookTarget(player);
        if (target == null) {
            return false;
        }
        applyEffect(player, target, spec.damage());
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 120, 0, true, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 70, 0, true, false));
        playImpact(target.getLocation());
        return true;
    }

    private boolean castDeflect(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 1, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 80, 0, true, false));
        applyArea(player, player.getLocation(), spec.radius(), spec.damage() * 0.5, true);
        playImpact(player.getLocation());
        return true;
    }

    private boolean castSummon(Player player) {
        Location center = getAimedCenter(player);
        Collection<LivingEntity> targets = LocationUtil.getNearbyLivingEntities(center, spec.radius(), player, null);
        boolean hit = false;
        for (LivingEntity target : targets) {
            Infection.apply(target, 100);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0, true, false));
            applyEffect(player, target, spec.damage() * 0.8);
            hit = true;
        }
        playImpact(center);
        return hit;
    }

    private boolean applyArea(Player source, Location center, double radius, double damage, boolean knockback) {
        Collection<LivingEntity> targets = LocationUtil.getNearbyLivingEntities(center, radius, source, null);
        boolean hit = false;
        for (LivingEntity target : targets) {
            applyEffect(source, target, damage);
            if (knockback) {
                knockAway(source, target, spec.knockback());
            }
            hit = true;
        }
        return hit;
    }

    private void applyEffect(Player source, LivingEntity target, double damage) {
        if (!LocationUtil.isValidTarget(source, target)) {
            return;
        }
        boolean wasFrozen = Freeze.isFrozen(target);
        if (damage > 0.0) {
            target.damage(damage, source);
        }
        applyCrowdControl(target, spec.crowdControl(), spec.crowdControlTicks());
        applyStyleDebuff(source, target);
        if (wasFrozen && (spec.crowdControl() == InspiredAbilitySpec.CrowdControlType.FREEZE
                || spec.style() == InspiredAbilitySpec.Style.FROST)) {
            heal(source, 1.0);
        }
    }

    private void applyStyleDebuff(Player source, LivingEntity target) {
        switch (spec.style()) {
            case CURSE -> Bleed.apply(target, 80, 0.45, source);
            case SUMMON -> Infection.apply(target, 80);
            case BLACK_HOLE -> target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true, false));
            case FROST -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 1, true, false));
            case SOUL -> target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 45, 0, true, false));
            case MARK -> target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, true, false));
            default -> {
            }
        }
    }

    private void applyCrowdControl(LivingEntity target, InspiredAbilitySpec.CrowdControlType type, int ticks) {
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

    private LivingEntity findLookTarget(Player player) {
        return LocationUtil.getEntityLookingAt(LivingEntity.class, player, spec.range(),
                target -> LocationUtil.isValidTarget(player, target));
    }

    private Location getAimedCenter(Player player) {
        LivingEntity target = findLookTarget(player);
        if (target != null) {
            return target.getLocation();
        }
        return player.getEyeLocation().clone()
                .add(player.getEyeLocation().getDirection().normalize().multiply(spec.range()));
    }

    private void pull(LivingEntity target, Location center, double force) {
        Vector pull = center.toVector().subtract(target.getLocation().toVector());
        if (pull.lengthSquared() <= 0.001) {
            return;
        }
        target.setVelocity(pull.normalize().multiply(Math.max(0.35, force)).setY(0.18));
    }

    private void knockAway(Player source, LivingEntity target, double force) {
        if (force <= 0.0) {
            return;
        }
        Vector direction = target.getLocation().toVector().subtract(source.getLocation().toVector());
        if (direction.lengthSquared() <= 0.001) {
            return;
        }
        target.setVelocity(direction.normalize().multiply(force).setY(0.22));
    }

    private void teleportBehind(Player player, LivingEntity target) {
        Vector back = target.getLocation().getDirection();
        if (back.lengthSquared() <= 0.001) {
            back = player.getLocation().toVector().subtract(target.getLocation().toVector());
        }
        if (back.lengthSquared() <= 0.001) {
            back = new Vector(0, 0, 1);
        }
        Location destination = target.getLocation().clone().subtract(back.normalize().multiply(1.3));
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());
        player.teleport(destination);
    }

    private void applyAllyBuff(Player player, int duration) {
        if (player == null || player.isDead()) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Math.max(60, duration / 2), 0, true,
                false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Math.max(60, duration / 2), 0, true,
                false));
        heal(player, spec.heal() * 0.5);
    }

    private boolean isAlly(Player source, Player candidate) {
        if (source == null || candidate == null || candidate.equals(source) || !LocationUtil.isValidTarget(candidate)) {
            return false;
        }
        AbilityCombat plugin = AbilityCombat.getPlugin();
        GameManager gameManager = plugin != null ? plugin.getGameManager() : null;
        return gameManager != null && gameManager.areTeammates(source, candidate);
    }

    private boolean isDamageSource(Player player, EntityDamageByEntityEvent event) {
        if (event.getDamager().equals(player)) {
            return true;
        }
        return event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter
                && shooter.equals(player);
    }

    private void cleanseCrowdControl(LivingEntity target) {
        Stun.remove(target);
        Bind.remove(target);
        Disarm.remove(target);
        Freeze.remove(target);
    }

    private void heal(Player player, double amount) {
        if (player == null || amount <= 0.0) {
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
        Particle primary = switch (spec.style()) {
            case BLACK_HOLE, PORTAL, SOUL -> Particle.PORTAL;
            case CURSE, SUMMON -> Particle.SOUL_FIRE_FLAME;
            case FROST -> Particle.SNOWFLAKE;
            case GUARD, ALLY, DEFLECT -> Particle.ENCHANT;
            case GLASS_CANNON -> Particle.DAMAGE_INDICATOR;
            case DASH, ASSASSIN -> Particle.SWEEP_ATTACK;
            default -> Particle.CRIT;
        };
        ParticleUtil.spawnParticle(world, primary, location, 16, 0.45, 0.45, 0.45, 0.02, 2, 64);
        ParticleUtil.spawnParticle(world, Particle.ENCHANTED_HIT, location, 8, 0.35, 0.35, 0.35, 0.01, 2, 64);
        world.playSound(location, soundForStyle(), 0.75f, 1.1f);
    }

    private Sound soundForStyle() {
        return switch (spec.style()) {
            case BLACK_HOLE, PORTAL -> Sound.ENTITY_ENDERMAN_TELEPORT;
            case FROST -> Sound.BLOCK_GLASS_BREAK;
            case GUARD, ALLY, DEFLECT -> Sound.ITEM_SHIELD_BLOCK;
            case GLASS_CANNON -> Sound.ENTITY_PLAYER_ATTACK_CRIT;
            case CURSE, SOUL, SUMMON -> Sound.ENTITY_WITHER_HURT;
            default -> Sound.ENTITY_PLAYER_ATTACK_SWEEP;
        };
    }
}
