package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.Participant;
import com.abilitycombat.npc.PlayerReplica;
import com.abilitycombat.npc.ReplicaProfile;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.UUID;

@AbilityManifest(name = "인형사 (PuppetMaster)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 전투 인형]",
        "§7자신을 따라다니는 §f60% 크기§7의 체력 §f30§7 인형을 소환합니다",
        "§7인형은 장비 없이 사용자 스킨을 복제하고 일반 사람의 §b120% 속도§7로 이동합니다",
        "§7사용자가 공격한 대상을 함께 추적해 타격마다 §c6 피해§7를 줍니다",
        "§7인형이 사용자와 §f7칸§7 이상 멀어지면 사용자 위치로 돌아오고 전투를 멈춥니다",
        "§7인형 사망 시 §f50초§7 후 자동 재소환되며 체력과 재소환 시간은 보스바로 표시됩니다",
        "",
        "§e§l[철괴 우클릭 - 인형 투척]",
        "§7인형을 바라보는 방향으로 발사하고 §f5초§7간 복귀를 막습니다",
        "§7착지 지점 주변 §f5칸§7 적을 §e1.5초 기절§7시키고 전투를 시작합니다"
}, summarize = {
        "§7패시브§f: 체력 30 인형이 따라다니며 대상 추격",
        "§7인형 공격§f: 타격마다 피해 6",
        "§7철괴 우클릭§f: 인형 발사, 착지 주변 5칸 기절 1.5초"
})
public class PuppetMaster extends AbilityBase implements ActiveHandler {

    private static final double PUPPET_SCALE = 0.6;
    private static final double PUPPET_MAX_HEALTH = 30.0;
    private static final double PUPPET_DAMAGE = 6.0;
    private static final double PUPPET_SPEED = 0.36;
    private static final double PUPPET_GRAVITY = 0.08;
    private static final double PUPPET_DRAG = 0.02;
    private static final double FOLLOW_DISTANCE = 2.4;
    private static final double RETURN_DISTANCE = 7.0;
    private static final double ATTACK_RANGE = 2.2;
    private static final int ATTACK_INTERVAL_TICKS = 16;
    private static final int RESPAWN_TICKS = 1000;
    private static final int RETURN_DISABLE_TICKS = 100;
    private static final double LAUNCH_SPEED = 1.8;
    private static final double LAUNCH_RADIUS = 5.0;
    private static final int LAUNCH_STUN_TICKS = 30;
    private static final int GAUGE_PRIORITY = 7;

    private final BossBarGauge puppetGauge = new BossBarGauge("puppet", GAUGE_PRIORITY, BossBar.Color.PURPLE,
            BossBar.Overlay.PROGRESS);

    private PlayerReplica puppet;
    private UUID combatTargetId;
    private int respawnTicks;
    private int attackCooldown;
    private int returnDisabledUntilTick;
    private boolean launched;

    public PuppetMaster(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageEvent.class);
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        removePuppet();
        puppetGauge.clear();
        unregisterTick();
    }

    @Override
    protected void onDestroy() {
        removePuppet();
        puppetGauge.clear();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        Player owner = getPlayer();
        if (owner == null || !isPuppetAlive()) {
            return false;
        }
        Vector direction = owner.getLocation().getDirection();
        if (direction.lengthSquared() <= 1.0E-6) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize().multiply(LAUNCH_SPEED).setY(Math.max(0.35, direction.getY()));
        puppet.setVelocity(direction);
        launched = true;
        combatTargetId = null;
        returnDisabledUntilTick = AbilityTickManager.getGlobalTick() + RETURN_DISABLE_TICKS;
        owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_WIND_CHARGE_THROW, 0.8f, 1.0f);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent damageEvent) {
            onDamageByEntity(damageEvent);
        }
        if (event instanceof EntityDamageEvent damageEvent) {
            onPuppetDamage(damageEvent);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player owner = getPlayer();
        if (owner == null || event.isCancelled()) {
            return;
        }
        if (isPuppetAlive() && puppet.matches(event.getEntity())) {
            Entity damager = resolveDamager(event.getDamager());
            if (damager != null && damager.getUniqueId().equals(owner.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }
        if (!event.getDamager().equals(owner) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (!LocationUtil.isValidTarget(owner, target)) {
            return;
        }
        combatTargetId = target.getUniqueId();
    }

    private void onPuppetDamage(EntityDamageEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (!isPuppetAlive() || !puppet.matches(event.getEntity())) {
            return;
        }
        if (puppet.getEntity().getHealth() - event.getFinalDamage() <= 0.0) {
            event.setCancelled(true);
            killPuppet();
            return;
        }
        Bukkit.getScheduler().runTask(AbilityCombat.getPlugin(), this::updateGauge);
    }

    @Override
    public void onTick(int tick) {
        Player owner = getPlayer();
        if (owner == null || !owner.isOnline()) {
            unregisterTick();
            return;
        }
        if (!isPuppetAlive()) {
            if (respawnTicks <= 0) {
                spawnPuppet();
                return;
            }
            tickRespawn();
            return;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        boolean puppetOnGround = puppet.tickPhysics(PUPPET_GRAVITY, PUPPET_DRAG);
        if (launched) {
            tickLaunch(owner, tick, puppetOnGround);
            updateGauge();
            return;
        }
        Location current = puppet.getLocation();
        if (current.getWorld() == null || !current.getWorld().equals(owner.getWorld())) {
            puppet.teleport(owner.getLocation());
            combatTargetId = null;
            updateGauge();
            return;
        }
        double ownerDistanceSq = current.distanceSquared(owner.getLocation());
        if (ownerDistanceSq > RETURN_DISTANCE * RETURN_DISTANCE && tick >= returnDisabledUntilTick) {
            puppet.teleport(owner.getLocation());
            combatTargetId = null;
            updateGauge();
            return;
        }
        LivingEntity target = resolveTarget(owner);
        if (target != null) {
            chaseAndAttack(owner, target);
        } else {
            followOwner(owner);
        }
        if (tick % 5 == 0) {
            updateGauge();
        }
    }

    private void tickLaunch(Player owner, int tick, boolean puppetOnGround) {
        ParticleUtil.spawnParticle(owner.getWorld(), Particle.CLOUD, puppet.getLocation().clone().add(0, 0.5, 0),
                8, 0.25, 0.2, 0.25, 0.02, 1, 64);
        if (tick < returnDisabledUntilTick && !puppetOnGround) {
            return;
        }
        launched = false;
        LivingEntity target = LocationUtil.getNearbyLivingEntities(puppet.getLocation(), LAUNCH_RADIUS, owner,
                entity -> true).stream()
                .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(puppet.getLocation())))
                .orElse(null);
        if (target != null) {
            for (LivingEntity nearby : LocationUtil.getNearbyLivingEntities(puppet.getLocation(), LAUNCH_RADIUS, owner,
                    entity -> true)) {
                Stun.apply(nearby, LAUNCH_STUN_TICKS);
            }
            combatTargetId = target.getUniqueId();
            owner.getWorld().playSound(puppet.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.85f, 0.8f);
        }
    }

    private void chaseAndAttack(Player owner, LivingEntity target) {
        Location current = puppet.getLocation();
        double distanceSq = current.distanceSquared(target.getLocation());
        face(target.getLocation());
        if (distanceSq > ATTACK_RANGE * ATTACK_RANGE) {
            moveToward(target.getLocation(), PUPPET_SPEED);
            return;
        }
        puppet.setVelocity(new Vector());
        if (attackCooldown <= 0) {
            target.setNoDamageTicks(0);
            target.damage(PUPPET_DAMAGE, owner);
            attackCooldown = ATTACK_INTERVAL_TICKS;
            owner.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 1.25f);
        }
    }

    private void followOwner(Player owner) {
        Location current = puppet.getLocation();
        double distanceSq = current.distanceSquared(owner.getLocation());
        face(owner.getLocation());
        if (distanceSq <= FOLLOW_DISTANCE * FOLLOW_DISTANCE) {
            puppet.setVelocity(new Vector());
            return;
        }
        moveToward(owner.getLocation(), PUPPET_SPEED);
    }

    private void moveToward(Location target, double speed) {
        Location current = puppet.getLocation();
        Vector delta = target.toVector().subtract(current.toVector());
        Vector flat = delta.clone().setY(0);
        if (flat.lengthSquared() <= 1.0E-6) {
            puppet.setVelocity(new Vector());
            return;
        }
        double y = puppet.getVelocity().getY();
        if (delta.getY() > 0.7) {
            y = Math.max(y, 0.35);
        }
        puppet.setVelocity(flat.normalize().multiply(speed).setY(y));
    }

    private void face(Location target) {
        Location current = puppet.getLocation();
        Vector delta = target.toVector().subtract(current.toVector());
        if (delta.lengthSquared() <= 1.0E-6) {
            return;
        }
        current.setDirection(delta);
        puppet.setRotation(current.getYaw(), current.getPitch());
    }

    private LivingEntity resolveTarget(Player owner) {
        if (combatTargetId == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(combatTargetId);
        if (!(entity instanceof LivingEntity living) || living.isDead() || !LocationUtil.isValidTarget(owner, living)) {
            combatTargetId = null;
            return null;
        }
        return living;
    }

    private Entity resolveDamager(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity entity) {
            return entity;
        }
        return damager;
    }

    private void tickRespawn() {
        if (respawnTicks > 0) {
            respawnTicks--;
            updateGauge();
            if (respawnTicks <= 0) {
                spawnPuppet();
            }
        }
    }

    private void spawnPuppet() {
        Player owner = getPlayer();
        if (owner == null) {
            return;
        }
        removePuppet();
        puppet = AbilityCombat.getPlugin().getReplicaManager()
                .createReplica(owner.getLocation(), ReplicaProfile.fromPlayer(owner));
        puppet.setInvulnerable(false);
        puppet.setImmovable(false);
        puppet.setGravity(true);
        puppet.setAI(false);
        puppet.setCollidable(true);
        puppet.customName(Component.text(owner.getName() + "의 인형", NamedTextColor.LIGHT_PURPLE));
        puppet.setCustomNameVisible(false);
        AttributeInstance scale = puppet.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(PUPPET_SCALE);
        }
        AttributeInstance health = puppet.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(PUPPET_MAX_HEALTH);
        }
        puppet.setHealth(PUPPET_MAX_HEALTH);
        EntityEquipment equipment = puppet.getEquipment();
        if (equipment != null) {
            equipment.setArmorContents(new ItemStack[] {
                    new ItemStack(Material.AIR), new ItemStack(Material.AIR),
                    new ItemStack(Material.AIR), new ItemStack(Material.AIR)
            });
            equipment.setItemInMainHand(new ItemStack(Material.AIR));
            equipment.setItemInOffHand(new ItemStack(Material.AIR));
        }
        puppet.syncEquipment();
        puppet.spawn();
        respawnTicks = 0;
        combatTargetId = null;
        launched = false;
        updateGauge();
    }

    private void killPuppet() {
        Location location = puppet.getLocation();
        removePuppet();
        if (location.getWorld() != null) {
            location.getWorld().playSound(location, Sound.ENTITY_ARMOR_STAND_BREAK, 0.8f, 0.8f);
            ParticleUtil.spawnParticle(location.getWorld(), Particle.SMOKE, location.clone().add(0, 0.8, 0),
                    20, 0.35, 0.35, 0.35, 0.03, 1, 64);
        }
        respawnTicks = RESPAWN_TICKS;
        updateGauge();
    }

    private void removePuppet() {
        if (puppet != null && !puppet.isDead()) {
            puppet.remove();
        }
        puppet = null;
        combatTargetId = null;
        launched = false;
    }

    private boolean isPuppetAlive() {
        return puppet != null && !puppet.isDead() && !puppet.getEntity().isDead();
    }

    private void updateGauge() {
        if (isPuppetAlive()) {
            double health = Math.max(0.0, puppet.getEntity().getHealth());
            puppetGauge.update(Component.text("인형 체력 ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(String.format(java.util.Locale.ROOT, "%.1f/30", health),
                            NamedTextColor.WHITE)), health / PUPPET_MAX_HEALTH);
            return;
        }
        if (respawnTicks > 0) {
            int seconds = (int) Math.ceil(respawnTicks / 20.0);
            puppetGauge.update(Component.text("인형 재소환 ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(seconds + "초", NamedTextColor.WHITE)),
                    1.0 - (respawnTicks / (double) RESPAWN_TICKS));
            return;
        }
        puppetGauge.clear();
    }
}
