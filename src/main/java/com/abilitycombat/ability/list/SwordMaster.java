package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.combat.SweepEffectAllowance;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.FastMath;
import com.abilitycombat.utils.LocationPool;
import com.abilitycombat.utils.VectorPool;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@AbilityManifest(name = "소드 마스터 (SwordMaster)", rank = AbilityManifest.Rank.S, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 검기 충전]",
        "§7검을 들고 있으면 약 §f0.75초§7마다 §6검기§7가 충전됩니다.",
        "§7충전된 검기는 플레이어 주변에 §b떠다니며§7,",
        "§7최대 §f10개§7까지 보유할 수 있습니다.",
        "",
        "§e§l[검 우클릭 - 검기 발사]",
        "§7충전된 검기 하나를 바라보는 방향으로 발사합니다.",
        "§7피해량은 §c들고 있는 검의 공격력§7에 비례합니다.",
        "",
        "§e§l[점프 중 웅크리기 - 삼연참]",
        "§7후방으로 빠르게 이동하며 검기 §f3개§7를 연속 발사합니다.",
        "",
        "§e§l[웅크리기 + 하향 시선 - 궁극기]§f §8(쿨타임: 50초)",
        "§7검기가 §f10개§7 모두 충전되었을 때만 발동 가능합니다.",
        "§7모든 검기가 바닥에 꽂히며 §e번개§7가 떨어집니다."
}, summarize = {
        "§7패시브§f: 검 주변에 떠다니는 검기",
        "§7검 우클릭§f: 검기 발사",
        "§7궁극기§f: 10개 모두 충전 시 번개"
})
public class SwordMaster extends AbilityBase implements ActiveHandler {

    private static final int MAX_SWORDS = 10;
    private static final int ULT_COOLDOWN_SECONDS = 50;
    private static final String PDC_KEY = "swordmaster_sword";
    private static final EulerAngle DEFAULT_ARM_POSE = new EulerAngle(Math.toRadians(-10), 0, 0);
    private static final int SHOOT_MAX_TICKS = 40;
    private static final double SHOOT_SPEED = 2.0;

    private final ActionbarCooldown ultimateCooldown = new ActionbarCooldown(ULT_COOLDOWN_SECONDS, 20);
    private final List<FloatingSword> swords = new ArrayList<>();
    private final List<ShootingProjectile> projectiles = new ArrayList<>();
    private final BossBarGauge swordGauge = new BossBarGauge("swords", 10, BossBar.Color.YELLOW,
            BossBar.Overlay.NOTCHED_10);
    private int chargeProgress = 0;
    private Material lastHeldSword = null;

    public SwordMaster(Participant participant) {
        super(participant);
    }

    public static NamespacedKey getSwordKey(AbilityCombat plugin) {
        return new NamespacedKey(plugin, PDC_KEY);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(PlayerMoveEvent.class);
        subscribeEvent(PlayerArmorStandManipulateEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        removeAllSwords();
        removeAllProjectiles();
        swordGauge.clear();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (!isSword(material) || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        return shootOne();
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof PlayerMoveEvent) {
            onMove((PlayerMoveEvent) event);
        } else if (event instanceof PlayerArmorStandManipulateEvent) {
            onArmorStandManipulate((PlayerArmorStandManipulateEvent) event);
        }
    }

    private void onMove(PlayerMoveEvent event) {
        if (!event.getPlayer().equals(getPlayer())) {
            return;
        }
        Player player = getPlayer();
        // 점프 중 웅크리기 = 삼연참
        if (player.getVelocity().getY() > 0.4 && player.isSneaking() && getSwordCount() >= 3) {
            Vector backward = VectorPool.get();
            backward.copy(player.getLocation().getDirection()).normalize().multiply(-2).setY(0.35);
            player.setVelocity(backward);
            shootOne();
            shootOne();
            shootOne();
        }
        // 웅크리기 + 아래 시선 = 궁극기
        if (player.isSneaking() && player.getLocation().getPitch() >= 80) {
            if (!ultimateCooldown.isCooldown() && swords.size() >= MAX_SWORDS) {
                ultimate();
                ultimateCooldown.start();
            }
        }
    }

    private void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (event.getRightClicked().getPersistentDataContainer().has(getSwordKey(AbilityCombat.getPlugin()),
                PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    private boolean isSword(Material material) {
        return material != null && material.name().endsWith("_SWORD");
    }

    private double getSwordDamage() {
        Player player = getPlayer();
        double baseDamage = player.getAttribute(Attribute.ATTACK_DAMAGE).getValue();
        return baseDamage / 1.5;
    }

    @Override
    public void onTick(int tick) {
        if (isDestroyed()) {
            return;
        }

        // 1. Charge Logic (Every 5 ticks)
        if (tick % 5 == 0) {
            processCharge();
        }

        // 2. Sword Animation & Gauge Logic (Every 8 ticks)
        if (tick % 8 == 0) {
            processSwordManagement();
        }

        // 3. Projectile Logic (Every 2 ticks for smoothness)
        if (tick % 2 == 0 && !projectiles.isEmpty()) {
            processProjectiles();
        }
    }

    private void processCharge() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        Material held = player.getInventory().getItemInMainHand().getType();

        if (!isSword(held)) {
            chargeProgress = 0;
            lastHeldSword = null;
            return;
        }

        if (held != lastHeldSword) {
            chargeProgress = 0;
            lastHeldSword = held;
        }

        if (swords.size() >= MAX_SWORDS) {
            return;
        }

        chargeProgress++;
        if (chargeProgress >= 3) {
            chargeProgress = 0;
            ItemStack swordItem = player.getInventory().getItemInMainHand().clone();
            addSword(new FloatingSword(swordItem, getSwordDamage()));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.3f, 2.0f);
        }
    }

    private void processSwordManagement() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        // HUD Update
        int swordCount = swords.size();
        double progress = (double) swordCount / MAX_SWORDS;
        Component title = Component.text("검기 ", NamedTextColor.GOLD)
                .append(Component.text(swordCount + "/" + MAX_SWORDS, NamedTextColor.WHITE));
        swordGauge.update(title, progress);

        if (swords.isEmpty()) {
            return;
        }

        Location playerLoc = player.getLocation();
        float yaw = playerLoc.getYaw();
        double radius = 2.5;

        for (int i = 0; i < swordCount; i++) {
            double angle = (2 * Math.PI / swordCount) * i - Math.toRadians(yaw);
            double x = FastMath.cos(angle) * radius;
            double z = FastMath.sin(angle) * radius;

            Location swordLoc = LocationPool.get(playerLoc.getWorld(), playerLoc.getX() + x, playerLoc.getY() + 0.5,
                    playerLoc.getZ() + z, yaw, playerLoc.getPitch());

            FloatingSword sword = swords.get(i);
            sword.updatePosition(swordLoc, playerLoc.getPitch());
        }
    }

    private void processProjectiles() {
        Iterator<ShootingProjectile> iter = projectiles.iterator();
        while (iter.hasNext()) {
            ShootingProjectile proj = iter.next();
            if (processProjectile(proj)) {
                if (proj.armorStand != null && !proj.armorStand.isDead()) {
                    proj.armorStand.remove();
                }
                iter.remove();
            }
        }
    }

    private boolean processProjectile(ShootingProjectile proj) {
        if (proj.armorStand == null || proj.armorStand.isDead()) {
            return true;
        }

        proj.ticks++;
        if (proj.ticks > SHOOT_MAX_TICKS) {
            return true;
        }

        Location current = proj.armorStand.getLocation();
        Vector delta = VectorPool.get().copy(proj.direction).multiply(SHOOT_SPEED);
        Location next = LocationPool.get(current.getWorld(), current.getX() + delta.getX(),
                current.getY() + delta.getY(), current.getZ() + delta.getZ(), current.getYaw(), current.getPitch());

        // 블록 충돌
        if (next.getBlock().getType().isSolid()) {
            return true;
        }

        proj.armorStand.teleport(next);

        // 엔티티 충돌
        for (LivingEntity entity : com.abilitycombat.utils.LocationUtil
                .getNearbyLivingEntities(proj.armorStand.getLocation(), 1.2, null)) {
            if (entity.equals(proj.shooter) || entity.equals(proj.armorStand)) {
                continue;
            }
            if (entity.getPersistentDataContainer().has(getSwordKey(AbilityCombat.getPlugin()),
                    PersistentDataType.BYTE)) {
                continue;
            }
            entity.setNoDamageTicks(0);
            entity.damage(proj.damage, proj.shooter);
            return true;
        }

        return false;
    }

    public int getSwordCount() {
        return swords.size();
    }

    public void addSword(FloatingSword sword) {
        if (swords.size() < MAX_SWORDS) {
            swords.add(sword);
        }
    }

    public boolean shootOne() {
        if (swords.isEmpty()) {
            return false;
        }
        FloatingSword sword = swords.remove(0);
        Player player = getPlayer();
        if (player == null) {
            sword.remove();
            return false;
        }

        // 발사 준비
        Vector dir = player.getEyeLocation().getDirection().normalize();
        Location eyeLoc = player.getEyeLocation();
        sword.armorStand.teleport(eyeLoc);
        sword.armorStand
                .setRightArmPose(new EulerAngle(Math.toRadians(player.getLocation().getPitch() - 10), 0, 0));

        // 발사체 등록
        projectiles.add(new ShootingProjectile(sword.armorStand, sword.damage, dir, player));
        SweepEffectAllowance.markAbilitySweepSound();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
        return true;
    }

    public void ultimate() {
        if (swords.size() < MAX_SWORDS) {
            return;
        }
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        for (Iterator<FloatingSword> it = swords.iterator(); it.hasNext();) {
            FloatingSword sword = it.next();
            sword.ultimateDrop(player);
            it.remove();
        }
    }

    private void removeAllSwords() {
        for (FloatingSword sword : swords) {
            sword.remove();
        }
        swords.clear();
    }

    private void removeAllProjectiles() {
        for (ShootingProjectile proj : projectiles) {
            if (proj.armorStand != null && !proj.armorStand.isDead()) {
                proj.armorStand.remove();
            }
        }
        projectiles.clear();
    }

    /**
     * 발사 중인 검 데이터
     */
    private static class ShootingProjectile {
        final ArmorStand armorStand;
        final double damage;
        final Vector direction;
        final Player shooter;
        int ticks;

        ShootingProjectile(ArmorStand armorStand, double damage, Vector direction, Player shooter) {
            this.armorStand = armorStand;
            this.damage = damage;
            this.direction = direction;
            this.shooter = shooter;
            this.ticks = 0;
        }
    }

    /**
     * 개별 떠다니는 검
     */
    private class FloatingSword {
        private ArmorStand armorStand;
        private final ItemStack swordItem;
        private final double damage;

        public FloatingSword(ItemStack swordItem, double damage) {
            this.swordItem = swordItem;
            this.damage = damage;
            spawnArmorStand();
        }

        private void spawnArmorStand() {
            Player player = getPlayer();
            if (player == null) {
                return;
            }
            armorStand = player.getWorld().spawn(player.getLocation(), ArmorStand.class);
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);
            armorStand.setSmall(true);
            armorStand.setBasePlate(false);
            armorStand.setArms(true);
            armorStand.getPersistentDataContainer().set(getSwordKey(AbilityCombat.getPlugin()),
                    PersistentDataType.BYTE, (byte) 1);
            AbilityCombat.markAbilityArmorStand(armorStand);
            armorStand.getEquipment().setItemInMainHand(swordItem);
            armorStand.setRightArmPose(DEFAULT_ARM_POSE);
        }

        public void updatePosition(Location loc, float pitch) {
            if (armorStand == null || armorStand.isDead()) {
                return;
            }
            armorStand.teleport(loc);
            armorStand.setRightArmPose(new EulerAngle(Math.toRadians(pitch - 10), 0, 0));
        }

        public void ultimateDrop(Player player) {
            if (armorStand == null || armorStand.isDead()) {
                return;
            }

            armorStand.setRightArmPose(new EulerAngle(Math.toRadians(80), 0, 0));

            Location loc = armorStand.getLocation();
            loc.setY(player.getLocation().getY() - 0.5);
            armorStand.teleport(loc);

            loc.getWorld().strikeLightningEffect(loc);

            for (LivingEntity entity : com.abilitycombat.utils.LocationUtil.getNearbyLivingEntities(loc, 3.0, null)) {
                if (entity.equals(player) || entity.getPersistentDataContainer()
                        .has(getSwordKey(AbilityCombat.getPlugin()), PersistentDataType.BYTE)) {
                    continue;
                }
                entity.damage(damage * 1.5, player);
            }

            Bukkit.getScheduler().runTaskLater(AbilityCombat.getPlugin(), this::remove, 40L);
        }

        public void remove() {
            if (armorStand != null && !armorStand.isDead()) {
                armorStand.remove();
            }
        }
    }
}
