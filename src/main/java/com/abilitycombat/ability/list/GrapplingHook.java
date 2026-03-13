package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.combat.SweepEffectAllowance;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationPool;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.VectorPool;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

@AbilityManifest(name = "그래플링 훅 (GrapplingHook)", rank = AbilityManifest.Rank.S, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 후크 발사]§f",
        "§7바라보는 방향으로 §3후크§7를 발사해 적중 위치로 이동합니다.",
        "§7후크는 §f4개§7까지 보유하며 모두 소모 시 §f25초§7 후 전부 충전됩니다.",
        "§7후크가 적에게 적중하면 §3급습§f이 자동으로 발동합니다.",
        "",
        "§e§l[웅크리기 - 급습]",
        "§7후크 사용 직후 웅크리면 전방으로 강하게 도약합니다.",
        "§75칸 이내의 적을 기절시키고 최대 체력의 §f15%§7 피해를 줍니다.",
        "",
        "§e§l[패시브]",
        "§7낙하 피해를 받지 않습니다."
}, summarize = {
        "낙하 피해를 받지 않습니다.",
        "§7철괴 우클릭§f: 후크 투사체 발사",
        "§7웅크리기§f: 급습 기절"
})
public class GrapplingHook extends AbilityBase implements ActiveHandler {

    private static final int MAX_CHARGE = 4;
    private static final int RECHARGE_SECONDS = 25;
    private static final double HOOK_RANGE = 25.0;
    private static final double HOOK_SPEED = 1.6;
    private static final double HOOK_HIT_RADIUS = 1.1;
    private static final int HOOK_MAX_TICKS = (int) Math.ceil(HOOK_RANGE / HOOK_SPEED) + 2;
    private static final double MIN_TARGET_DISTANCE = 4.0;
    private static final double DASH_MIN_SPEED = 0.9;
    private static final double DASH_MAX_SPEED = 2.7;
    private static final double DASH_SPEED_RATIO = 0.18;
    private static final int AMBUSH_WINDOW_TICKS = 20;
    private static final double AMBUSH_RANGE = 5.0;
    private static final int AMBUSH_STUN_TICKS = 40;
    private static final double AMBUSH_DAMAGE_RATIO = 0.15;
    private static final double AMBUSH_LEAP_SPEED = 1.5;
    private static final double AMBUSH_LEAP_Y = 0.6;

    private int charges = MAX_CHARGE;
    private int ambushWindowTicks;
    private LivingEntity hookedTarget;
    private boolean autoAmbush;
    private HookProjectile hookProjectile;

    private final Cooldown recharge = new Cooldown(RECHARGE_SECONDS) {
        @Override
        protected void onEnd() {
            super.onEnd();
            charges = MAX_CHARGE;
        }
    };

    public GrapplingHook(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageEvent.class);
        subscribeEvent(PlayerToggleSneakEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        removeHookProjectile();
        ambushWindowTicks = 0;
        hookedTarget = null;
        autoAmbush = false;
    }

    @Override
    protected void onDestroy() {
        removeHookProjectile();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (charges <= 0) {
            notifyCooldown(recharge);
            return false;
        }
        if (hookProjectile != null) {
            return false;
        }
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        LivingEntity tooClose = LocationUtil.getEntityLookingAt(LivingEntity.class, player, MIN_TARGET_DISTANCE,
                entity -> !entity.equals(player));
        if (tooClose != null) {
            return false;
        }
        hookProjectile = spawnHook(player);
        if (hookProjectile == null) {
            return false;
        }
        charges--;
        if (charges <= 0) {
            recharge.start();
            applyIronCooldownIfEmpty(RECHARGE_SECONDS);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, 1.2f);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent damage) {
            onDamage(damage);
        } else if (event instanceof PlayerToggleSneakEvent sneak) {
            onSneak(sneak);
        }
    }

    private void onDamage(EntityDamageEvent event) {
        if (!event.getEntity().equals(getPlayer())) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    private void onSneak(PlayerToggleSneakEvent event) {
        if (!event.getPlayer().equals(getPlayer())) {
            return;
        }
        if (!event.isSneaking()) {
            return;
        }
        if (ambushWindowTicks <= 0) {
            return;
        }
        ambushWindowTicks = 0;
        autoAmbush = false;
        performLeap();
        LivingEntity target = resolveAmbushTarget();
        if (target != null) {
            performAmbush(target);
        }
        hookedTarget = null;
    }

    private HookProjectile spawnHook(Player player) {
        Location eye = player.getEyeLocation();
        ArmorStand stand = eye.getWorld().spawn(eye, ArmorStand.class, entity -> {
            entity.setVisible(false);
            entity.setGravity(false);
            entity.setMarker(true);
            entity.setSmall(true);
            entity.setBasePlate(false);
            entity.setArms(true);
            entity.setInvulnerable(true);
            entity.getEquipment().setItemInMainHand(new ItemStack(Material.TRIPWIRE_HOOK));
            entity.setRightArmPose(new EulerAngle(Math.toRadians(90), 0, 0));
        });
        AbilityCombat.markAbilityArmorStand(stand);
        Vector direction = eye.getDirection().normalize();
        return new HookProjectile(stand, direction, player);
    }

    private void processHookProjectile() {
        if (hookProjectile == null) {
            return;
        }
        if (hookProjectile.armorStand == null || hookProjectile.armorStand.isDead()) {
            removeHookProjectile();
            return;
        }
        hookProjectile.ticks++;
        if (hookProjectile.ticks > HOOK_MAX_TICKS) {
            removeHookProjectile();
            return;
        }

        Location current = hookProjectile.armorStand.getLocation();
        Vector delta = VectorPool.copy(hookProjectile.direction).multiply(HOOK_SPEED);
        Location next = LocationPool.get(current.getWorld(), current.getX() + delta.getX(), current.getY() + delta.getY(),
                current.getZ() + delta.getZ(), current.getYaw(), current.getPitch());

        if (next.getBlock().getType().isSolid()) {
            onHookHitBlock(next);
            removeHookProjectile();
            return;
        }

        hookProjectile.armorStand.teleport(next);

        for (LivingEntity entity : LocationUtil.getNearbyLivingEntities(next, HOOK_HIT_RADIUS, hookProjectile.shooter,
                target ->
                !target.equals(hookProjectile.shooter)
                        && !(target instanceof ArmorStand)
                        && !(target instanceof Mannequin))) {
            onHookHitEntity(entity);
            removeHookProjectile();
            return;
        }
    }

    private void onHookHitBlock(Location location) {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        dashTowards(player, location);
        player.setFallDistance(0);
        hookedTarget = null;
        autoAmbush = false;
        ambushWindowTicks = AMBUSH_WINDOW_TICKS;
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.1f);
    }

    private void onHookHitEntity(LivingEntity target) {
        Player player = getPlayer();
        if (player == null || target == null) {
            return;
        }
        dashTowards(player, target.getLocation());
        player.setFallDistance(0);
        hookedTarget = target;
        autoAmbush = true;
        ambushWindowTicks = AMBUSH_WINDOW_TICKS;
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.1f);
    }

    private void performLeap() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        cancelSprintDashState(player);
        Vector leap = player.getLocation().getDirection().normalize().multiply(AMBUSH_LEAP_SPEED);
        leap.setY(AMBUSH_LEAP_Y);
        player.setVelocity(leap);
        SweepEffectAllowance.markAbilitySweepSound();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.2f);
    }

    private LivingEntity resolveAmbushTarget() {
        Player player = getPlayer();
        if (player == null) {
            return null;
        }
        if (hookedTarget != null && !hookedTarget.isDead()
                && hookedTarget.getLocation().distanceSquared(player.getLocation()) <= AMBUSH_RANGE * AMBUSH_RANGE) {
            return hookedTarget;
        }
        return LocationUtil.getEntityLookingAt(LivingEntity.class, player, AMBUSH_RANGE,
                entity -> !entity.equals(player));
    }

    private void performAmbush(LivingEntity target) {
        Player player = getPlayer();
        if (player == null || target == null) {
            return;
        }
        double maxHealth = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                ? target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                : 20.0;
        double damage = Math.max(1.0, maxHealth * AMBUSH_DAMAGE_RATIO);
        target.damage(damage, player);
        Stun.apply(target, AMBUSH_STUN_TICKS);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.9f);
    }

    private void dashTowards(Player player, Location target) {
        cancelSprintDashState(player);
        Location playerLoc = player.getLocation();
        Vector direction = target.toVector().subtract(playerLoc.toVector());
        double distance = direction.length();
        if (distance < 0.5) {
            return;
        }
        double speed = Math.min(DASH_MAX_SPEED, Math.max(DASH_MIN_SPEED, distance * DASH_SPEED_RATIO));
        Vector velocity = direction.normalize().multiply(speed);
        if (target.getY() > playerLoc.getY()) {
            velocity.setY(Math.min(velocity.getY() + 0.35, 1.4));
        }
        player.setVelocity(velocity);
    }

    private void cancelSprintDashState(Player player) {
        if (player == null) {
            return;
        }
        if (AbilityCombat.getPlugin() == null || AbilityCombat.getPlugin().getSprintHudService() == null) {
            return;
        }
        AbilityCombat.getPlugin().getSprintHudService().cancelDashState(player);
    }

    private void removeHookProjectile() {
        if (hookProjectile == null) {
            return;
        }
        if (hookProjectile.armorStand != null && !hookProjectile.armorStand.isDead()) {
            hookProjectile.armorStand.remove();
        }
        hookProjectile = null;
    }

    @Override
    public void onTick(int tick) {
        processHookProjectile();
        if (ambushWindowTicks > 0) {
            ambushWindowTicks--;
            if (ambushWindowTicks <= 0) {
                hookedTarget = null;
                autoAmbush = false;
            }
        }
        if (autoAmbush && hookedTarget != null) {
            Player player = getPlayer();
            if (player == null || hookedTarget.isDead()) {
                autoAmbush = false;
                hookedTarget = null;
                return;
            }
            if (hookedTarget.getLocation().distanceSquared(player.getLocation()) <= AMBUSH_RANGE * AMBUSH_RANGE) {
                performAmbush(hookedTarget);
                autoAmbush = false;
                hookedTarget = null;
                ambushWindowTicks = 0;
            }
        }
    }

    private static class HookProjectile {
        private final ArmorStand armorStand;
        private final Vector direction;
        private final Player shooter;
        private int ticks;

        private HookProjectile(ArmorStand armorStand, Vector direction, Player shooter) {
            this.armorStand = armorStand;
            this.direction = direction;
            this.shooter = shooter;
        }
    }
}
