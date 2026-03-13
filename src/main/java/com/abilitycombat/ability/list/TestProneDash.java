package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.destroystokyo.paper.SkinParts;
import com.abilitycombat.game.Participant;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

@AbilityManifest(name = "테스트 돌진 (TestProneDash)", rank = AbilityManifest.Rank.B, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 테스트 돌진]§f §8(쿨타임 없음)",
        "§7실제 플레이어를 잠시 숨기고",
        "§7마네킹이 수영 자세로 짧게 돌진합니다."
}, summarize = {
        "§7철괴 우클릭§f: 마네킹 돌진 테스트",
        "§7수영 포즈§f: 짧은 연출"
})
public class TestProneDash extends AbilityBase implements ActiveHandler {

    private static final int MAX_DASH_HOLD_TICKS = 30;
    private static final int POST_LAND_HOLD_TICKS = 4;
    private static final double DASH_SPEED = 1.2;
    private static final double DASH_Y = 0.25;
    private static final double FORWARD_OFFSET = 1.25;
    private static final double HEIGHT_OFFSET = 1.2;

    private boolean dashing;
    private int dashTicks;
    private boolean leftGround;
    private int landedTicks;
    private Mannequin mannequin;
    private boolean storedInvisible;
    private boolean storedCollidable;

    public TestProneDash(Participant participant) {
        super(participant);
    }

    @Override
    protected void onDeactivate() {
        stopDash();
    }

    @Override
    protected void onDestroy() {
        stopDash();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (dashing) {
            return false;
        }
        return startDash();
    }

    private boolean startDash() {
        Player player = getPlayer();
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }

        dashing = true;
        dashTicks = 0;
        leftGround = false;
        landedTicks = 0;
        storedInvisible = player.isInvisible();
        storedCollidable = player.isCollidable();
        hideRealPlayer(player);
        player.setInvisible(true);
        player.setCollidable(false);
        player.addPotionEffect(
                new PotionEffect(PotionEffectType.INVISIBILITY, MAX_DASH_HOLD_TICKS + 10, 0, true, false));
        Vector dashVelocity = buildDashVelocity(player);
        player.setVelocity(dashVelocity);
        spawnMannequin(player, dashVelocity);
        player.setFallDistance(0f);
        registerTick();
        return true;
    }

    private void stopDash() {
        if (!dashing) {
            return;
        }
        dashing = false;
        dashTicks = 0;
        leftGround = false;
        landedTicks = 0;
        unregisterTick();

        Player player = getPlayer();
        if (player != null) {
            showRealPlayer(player);
            player.setInvisible(storedInvisible);
            player.setCollidable(storedCollidable);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.setFallDistance(0f);
        }
        removeMannequin();
    }

    @Override
    public void onTick(int tick) {
        if (!dashing) {
            return;
        }
        Player player = getPlayer();
        if (player == null || !player.isOnline() || player.isDead()) {
            stopDash();
            return;
        }

        dashTicks++;
        player.setFallDistance(0f);
        syncMannequin(player);

        if (!player.isOnGround()) {
            leftGround = true;
        }

        if (leftGround && player.isOnGround()) {
            landedTicks++;
        } else {
            landedTicks = 0;
        }

        if (landedTicks >= POST_LAND_HOLD_TICKS || dashTicks >= MAX_DASH_HOLD_TICKS) {
            stopDash();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMannequinDamageByEntity(EntityDamageByEntityEvent event) {
        if (!isDashMannequin(event.getEntity())) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean vanillaMelee = cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;
        boolean vanillaArrow = cause == EntityDamageEvent.DamageCause.PROJECTILE
                && (event.getDamager() instanceof Arrow || event.getDamager() instanceof SpectralArrow);
        if (!vanillaMelee && !vanillaArrow) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getDamager() instanceof Player || event.getDamager() instanceof org.bukkit.entity.Mob)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        Player player = getPlayer();
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        player.setNoDamageTicks(0);
        player.damage(event.getFinalDamage(), event.getDamager());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMannequinDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        if (isDashMannequin(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private void spawnMannequin(Player player, Vector dashVelocity) {
        removeMannequin();
        mannequin = player.getWorld().spawn(player.getLocation(), Mannequin.class, entity -> {
            entity.setInvulnerable(true);
            entity.setImmovable(false);
            entity.setGravity(true);
            entity.setAI(false);
            entity.customName(player.displayName());
            entity.setCustomNameVisible(true);
            entity.setDescription(null);
            entity.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            entity.setSkinParts(SkinParts.allParts());
            EntityEquipment equipment = entity.getEquipment();
            if (equipment != null) {
                equipment.setArmorContents(cloneItems(player.getInventory().getArmorContents()));
                equipment.setItemInMainHand(cloneItem(player.getInventory().getItemInMainHand()));
                equipment.setItemInOffHand(cloneItem(player.getInventory().getItemInOffHand()));
            }
            entity.setPose(Pose.SWIMMING, true);
            entity.setVelocity(dashVelocity.clone());
        });
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin != null) {
            player.hideEntity(plugin, mannequin);
        }
        syncMannequin(player);
    }

    private void syncMannequin(Player player) {
        if (mannequin == null || mannequin.isDead()) {
            return;
        }
        mannequin.setPose(Pose.SWIMMING, true);
        mannequin.setSwimming(true);
        mannequin.setGliding(false);
        mannequin.setFallDistance(0f);
        if (!leftGround) {
            mannequin.teleport(getMannequinLocation(player));
            mannequin.setVelocity(player.getVelocity().clone());
        }
    }

    private void removeMannequin() {
        if (mannequin != null && !mannequin.isDead()) {
            mannequin.remove();
        }
        mannequin = null;
    }

    private void hideRealPlayer(Player source) {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.hidePlayer(plugin, source);
        }
    }

    private void showRealPlayer(Player source) {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.showPlayer(plugin, source);
        }
    }

    private ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private ItemStack[] cloneItems(ItemStack[] items) {
        if (items == null) {
            return new ItemStack[0];
        }
        ItemStack[] clones = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            clones[i] = cloneItem(items[i]);
        }
        return clones;
    }

    private Vector buildDashVelocity(Player player) {
        Vector direction = player.getLocation().getDirection();
        if (direction.lengthSquared() <= 0.0001) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize().multiply(DASH_SPEED);
        direction.setY(Math.max(DASH_Y, direction.getY() * 0.35));
        return direction;
    }

    private boolean isDashMannequin(Entity entity) {
        return dashing && mannequin != null && mannequin.equals(entity);
    }

    private org.bukkit.Location getMannequinLocation(Player player) {
        org.bukkit.Location location = player.getLocation().clone();
        Vector forward = location.getDirection();
        if (forward.lengthSquared() <= 0.0001) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize().multiply(FORWARD_OFFSET);
        location.add(forward);
        location.add(0, HEIGHT_OFFSET, 0);
        return location;
    }
}
