package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Freeze;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.FastMath;
import com.abilitycombat.utils.LocationPool;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.Event;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@AbilityManifest(name = "카쟈드 (Khazhad)", species = AbilityManifest.Species.GOD, explain = {
        "§e§l[패시브 - 삼지창 충전]",
        "§7약 §f4초§7마다 §b삼지창§7이 충전됩니다",
        "§7충전된 삼지창은 플레이어 주변에 §b떠다니며§7,",
        "§7최대 §f4개§7까지 보유할 수 있습니다",
        "",
        "§e§l[철괴 우클릭 - 삼지창 발사]",
        "§7충전된 삼지창을 바라보는 방향으로 발사합니다",
        "§7적 적중 시: §c8 피해§7 + §b빙결§7 효과 (1.5초)",
        "",
        "§e§l[철괴 좌클릭 - 빙결 폭발]",
        "§7적중하지 못해 박힌 삼지창을 폭발시킵니다",
        "§7폭발력: §f1.8"
}, summarize = {
        "§7패시브§f: 7초마다 삼지창 충전 (최대 3개)",
        "§7철괴 우클릭§f: 삼지창 발사 (8 피해 + 빙결)",
        "§7철괴 좌클릭§f: 박힌 창 폭발"
})
public class Khazhad extends AbilityBase implements ActiveHandler {

    private static final int MAX_TRIDENTS = 4;
    private static final double TRIDENT_DAMAGE = 8.0;
    private static final int FREEZE_DURATION_TICKS = 30; // 2초
    private static final float EXPLOSION_POWER = 1.8f;
    private static final String PDC_KEY = "khazhad_trident";
    private static final EulerAngle DEFAULT_ARM_POSE = new EulerAngle(Math.toRadians(-10), 0, 0);
    private static final double TRIDENT_SPEED = 3;
    private static final int CHARGE_SECONDS = 4;

    private final List<FloatingTrident> tridents = new ArrayList<>();
    private final Set<Trident> stuckTridents = new HashSet<>();
    private final Set<Trident> flyingTridents = new HashSet<>();
    private final BossBarGauge tridentGauge = new BossBarGauge("tridents", 10, BossBar.Color.BLUE,
            BossBar.Overlay.NOTCHED_10);
    private int chargeProgress = 0;

    public Khazhad(Participant participant) {
        super(participant);
    }

    public static NamespacedKey getTridentKey(AbilityCombat plugin) {
        return new NamespacedKey(plugin, PDC_KEY);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(PlayerMoveEvent.class);
        subscribeEvent(PlayerArmorStandManipulateEvent.class);
        subscribeEvent(ProjectileHitEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        cleanup();
    }

    @Override
    protected void onDestroy() {
        cleanup();
    }

    private void cleanup() {
        // 떠다니는 삼지창 제거
        removeAllTridents();
        // 발사된 삼지창 제거
        removeAllProjectiles();
        // 보스바 정리 (항상 시도)
        try {
            tridentGauge.clear();
        } catch (Exception ignored) {
            // 플레이어가 없어도 무시
        }
        // 월드에 남은 PDC 엔티티 정리
        cleanupWorldEntities();
    }

    private void cleanupWorldEntities() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        NamespacedKey key = getTridentKey(AbilityCombat.getPlugin());
        // 아머스탠드 정리
        for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
            if (stand.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                stand.remove();
            }
        }
        // Trident 정리
        for (Trident trident : world.getEntitiesByClass(Trident.class)) {
            if (trident.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                trident.remove();
            }
        }
    }

    @Override
    public boolean activeSkill(Material material, ActiveHandler.ClickType clickType) {
        if (material != Material.IRON_INGOT) {
            return false;
        }
        if (clickType == ActiveHandler.ClickType.RIGHT_CLICK) {
            return shootTrident();
        }
        if (clickType == ActiveHandler.ClickType.LEFT_CLICK) {
            return explodeStuckTridents();
        }
        return false;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof PlayerMoveEvent) {
            onMove((PlayerMoveEvent) event);
        } else if (event instanceof PlayerArmorStandManipulateEvent) {
            onArmorStandManipulate((PlayerArmorStandManipulateEvent) event);
        } else if (event instanceof ProjectileHitEvent) {
            onProjectileHit((ProjectileHitEvent) event);
        }
    }

    private void onMove(PlayerMoveEvent event) {
        if (!event.getPlayer().equals(getPlayer())) {
            return;
        }
    }

    private void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (event.getRightClicked().getPersistentDataContainer().has(getTridentKey(AbilityCombat.getPlugin()),
                PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    private void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) {
            return;
        }
        if (!flyingTridents.contains(trident)) {
            return;
        }

        flyingTridents.remove(trident);

        // 엔티티 적중
        if (event.getHitEntity() instanceof LivingEntity target) {
            if (!com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), target)) {
                trident.remove();
                return;
            }
            target.setNoDamageTicks(0);
            target.damage(TRIDENT_DAMAGE, getPlayer());
            Freeze.apply(target, FREEZE_DURATION_TICKS);
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.5f);
            target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5,
                    0.1);
            trident.remove();
            return;
        }

        // 블록 적중 - 박힌 상태로 전환
        if (event.getHitBlock() != null) {
            stuckTridents.add(trident);
            trident.getWorld().spawnParticle(Particle.SNOWFLAKE, trident.getLocation(), 10, 0.3, 0.3, 0.3, 0);
        }
    }

    @Override
    public void onTick(int tick) {
        if (isDestroyed()) {
            return;
        }

        // 1 충전 로직 (1초마다)
        if (tick % 20 == 0) {
            processCharge();
        }

        // 2 삼지창 애니메이션 & 게이지 로직 (8틱마다)
        if (tick % 8 == 0) {
            processTridentManagement();
        }

        // 3 박힌 삼지창 수명 관리 (20틱마다)
        if (tick % 20 == 0) {
            cleanupDeadTridents();
        }
    }

    private void processCharge() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        if (tridents.size() >= MAX_TRIDENTS) {
            return;
        }

        chargeProgress++;
        if (chargeProgress >= CHARGE_SECONDS) {
            chargeProgress = 0;
            addTrident(new FloatingTrident());
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.3f, 2.0f);
        }
    }

    private void processTridentManagement() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }

        // HUD 업데이트
        int tridentCount = tridents.size();
        double progress = (double) tridentCount / MAX_TRIDENTS;
        Component title = Component.text("삼지창 ", NamedTextColor.AQUA)
                .append(Component.text(tridentCount + "/" + MAX_TRIDENTS, NamedTextColor.WHITE));
        tridentGauge.update(title, progress);

        if (tridents.isEmpty()) {
            return;
        }

        Location playerLoc = player.getLocation();
        float yaw = playerLoc.getYaw();
        double radius = 2.0;

        for (int i = 0; i < tridentCount; i++) {
            double angle = (2 * Math.PI / tridentCount) * i - Math.toRadians(yaw);
            double x = FastMath.cos(angle) * radius;
            double z = FastMath.sin(angle) * radius;

            Location tridentLoc = LocationPool.get(playerLoc.getWorld(), playerLoc.getX() + x, playerLoc.getY() + 0.5,
                    playerLoc.getZ() + z, yaw, playerLoc.getPitch());

            FloatingTrident trident = tridents.get(i);
            trident.updatePosition(tridentLoc, playerLoc.getPitch());
        }
    }

    private void cleanupDeadTridents() {
        stuckTridents.removeIf(trident -> trident == null || trident.isDead() || !trident.isValid());
        flyingTridents.removeIf(trident -> trident == null || trident.isDead() || !trident.isValid());
    }

    private boolean shootTrident() {
        if (tridents.isEmpty()) {
            return false;
        }
        FloatingTrident floatingTrident = tridents.remove(0);
        Player player = getPlayer();
        if (player == null) {
            floatingTrident.remove();
            return false;
        }

        // 아머스탠드 제거
        floatingTrident.remove();

        // 실제 Trident 엔티티 발사
        Vector direction = player.getLocation().getDirection().normalize().multiply(TRIDENT_SPEED);
        Location eyeLoc = player.getEyeLocation();

        Trident trident = player.getWorld().spawn(eyeLoc, Trident.class, t -> {
            t.setShooter(player);
            t.setVelocity(direction);
            t.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
            t.getPersistentDataContainer().set(getTridentKey(AbilityCombat.getPlugin()),
                    PersistentDataType.BYTE, (byte) 1);
        });

        flyingTridents.add(trident);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 1.0f);
        return true;
    }

    private boolean explodeStuckTridents() {
        if (stuckTridents.isEmpty()) {
            return false;
        }

        Player player = getPlayer();
        if (player == null) {
            return false;
        }

        for (Trident trident : stuckTridents) {
            if (trident != null && !trident.isDead()) {
                Location loc = trident.getLocation();
                World world = loc.getWorld();
                if (world != null) {
                    world.createExplosion(loc, EXPLOSION_POWER, false, false, player);
                }
                trident.remove();
            }
        }
        stuckTridents.clear();
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        return true;
    }

    public void addTrident(FloatingTrident trident) {
        if (tridents.size() < MAX_TRIDENTS) {
            tridents.add(trident);
        }
    }

    private void removeAllTridents() {
        for (FloatingTrident trident : tridents) {
            trident.remove();
        }
        tridents.clear();
    }

    private void removeAllProjectiles() {
        for (Trident trident : flyingTridents) {
            if (trident != null && !trident.isDead()) {
                trident.remove();
            }
        }
        flyingTridents.clear();
        for (Trident trident : stuckTridents) {
            if (trident != null && !trident.isDead()) {
                trident.remove();
            }
        }
        stuckTridents.clear();
    }

    /**
     * 떠다니는 삼지창 (공전용 아머스탠드)
     */
    private class FloatingTrident {
        private ArmorStand armorStand;

        public FloatingTrident() {
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
            armorStand.getPersistentDataContainer().set(getTridentKey(AbilityCombat.getPlugin()),
                    PersistentDataType.BYTE, (byte) 1);
            AbilityCombat.markAbilityArmorStand(armorStand);
            armorStand.getEquipment().setItemInMainHand(new ItemStack(Material.TRIDENT));
            armorStand.setRightArmPose(DEFAULT_ARM_POSE);
        }

        public void updatePosition(Location loc, float pitch) {
            if (armorStand == null || armorStand.isDead()) {
                return;
            }
            armorStand.teleport(loc);
            armorStand.setRightArmPose(new EulerAngle(Math.toRadians(pitch - 10), 0, 0));
        }

        public void remove() {
            if (armorStand != null && !armorStand.isDead()) {
                armorStand.remove();
            }
        }
    }
}
