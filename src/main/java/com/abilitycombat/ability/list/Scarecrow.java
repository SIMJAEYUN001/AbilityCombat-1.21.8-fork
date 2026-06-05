package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.npc.PlayerReplica;
import com.abilitycombat.npc.ReplicaProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@AbilityManifest(name = "허수아비 (Scarecrow)", species = AbilityManifest.Species.OTHERS, explain = {
        "§e§l[철괴 우클릭 - 허수아비]§f §8(쿨타임: 25초)",
        "§7§f3초§7간 은신(갑옷 비노출)이 됩니다",
        "§7현재 위치에 허수아비를 소환합니다",
        "",
        "§7허수아비를 때린 플레이어에게 달려가",
        "§7§c폭발 강도 2§7로 폭발합니다"
}, summarize = {
        "§7철괴 우클릭§f: 3초 은신 + 허수아비 소환",
        "§7허수아비 피격§f: 공격자 추격 후 폭발"
})
public class Scarecrow extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 25;
    private static final int HIDE_SECONDS = 3;
    private static final int SCARECROW_DURATION_TICKS = 20 * 12;
    private static final float SCARECROW_EXPLOSION_POWER = 2.0f;
    private static final double CHASE_SPEED = 0.45;
    private static final double EXPLODE_DISTANCE = 1.5;
    private static final double EXPLOSION_RADIUS = 4.0;
    private static final double EXPLOSION_DAMAGE = 6.0;
    private static final double EXPLOSION_KNOCKBACK = 1.1;
    private static final double HEIGHT_DIFF_THRESHOLD = 0.6;
    private static final double UPWARD_SPEED = 0.35;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final List<ScarecrowData> scarecrows = new ArrayList<>();

    private int hideRemainingSeconds;
    private boolean hidden;

    public Scarecrow(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        clearScarecrows();
        disableHide();
        unregisterTick();
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
        startHide(HIDE_SECONDS);
        spawnScarecrow();
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent damageByEntity) {
            onDamageByEntity(damageByEntity);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }
        ScarecrowData data = getScarecrowData(event.getEntity().getUniqueId());
        if (data == null) {
            return;
        }
        data.lastLocation = data.mannequin.getLocation();
        LivingEntity attacker = resolveAttacker(event.getDamager());
        if (!(attacker instanceof Player) || data.mannequin.matches(attacker)) {
            return;
        }
        if (!AbilityCombat.getPlugin().getGameManager().canApplyNegativeEffect(getPlayer(), attacker)) {
            return;
        }
        data.setTarget(attacker.getUniqueId());
    }

    private void spawnScarecrow() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        Location location = player.getLocation().clone();
        PlayerReplica mannequin = AbilityCombat.getPlugin().getReplicaManager()
                .createReplica(location, ReplicaProfile.fromPlayer(player));
        mannequin.setInvulnerable(false);
        mannequin.setImmovable(false);
        mannequin.setGravity(true);
        mannequin.setAI(false);
        mannequin.customName(stripAfterNewline(player.displayName()));
        mannequin.setCustomNameVisible(true);
        EntityEquipment equipment = mannequin.getEquipment();
        if (equipment != null) {
            equipment.setArmorContents(cloneItems(player.getInventory().getArmorContents()));
            equipment.setItemInMainHand(cloneItem(player.getInventory().getItemInMainHand()));
            equipment.setItemInOffHand(cloneItem(player.getInventory().getItemInOffHand()));
        }
        mannequin.syncEquipment();
        mannequin.spawn();
        scarecrows.add(new ScarecrowData(mannequin, SCARECROW_DURATION_TICKS));
    }

    private LivingEntity resolveAttacker(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private Component stripAfterNewline(Component component) {
        String legacy = LEGACY_SERIALIZER.serialize(component);
        int newlineIndex = legacy.indexOf('\n');
        if (newlineIndex < 0) {
            newlineIndex = legacy.indexOf('\r');
        }
        if (newlineIndex >= 0) {
            legacy = legacy.substring(0, newlineIndex);
        }
        return LEGACY_SERIALIZER.deserialize(legacy);
    }

    private ScarecrowData getScarecrowData(UUID uuid) {
        for (ScarecrowData data : scarecrows) {
            if (data.mannequin.getUniqueId().equals(uuid)) {
                return data;
            }
        }
        return null;
    }

    private void clearScarecrows() {
        for (ScarecrowData data : scarecrows) {
            if (data.mannequin != null && !data.mannequin.isDead()) {
                data.mannequin.remove();
            }
        }
        scarecrows.clear();
    }

    private void startHide(int seconds) {
        hideRemainingSeconds = Math.max(hideRemainingSeconds, seconds);
        if (!hidden) {
            enableHide();
        }
    }

    private void enableHide() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        player.setInvisible(true);
        player.setCollidable(false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, HIDE_SECONDS * 20, 0, true, false));
        Bukkit.getOnlinePlayers().forEach(other -> {
            if (!other.equals(player)) {
                other.hidePlayer(com.abilitycombat.AbilityCombat.getPlugin(), player);
            }
        });
        hidden = true;
    }

    private void disableHide() {
        if (!hidden) {
            return;
        }
        Player player = getPlayer();
        if (player == null) {
            hidden = false;
            return;
        }
        player.setInvisible(false);
        player.setCollidable(true);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        Bukkit.getOnlinePlayers().forEach(other -> {
            other.showPlayer(com.abilitycombat.AbilityCombat.getPlugin(), player);
        });
        hidden = false;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0 && hideRemainingSeconds > 0) {
            hideRemainingSeconds--;
            if (hideRemainingSeconds <= 0) {
                disableHide();
            }
        }
        if (scarecrows.isEmpty()) {
            return;
        }
        Iterator<ScarecrowData> iterator = scarecrows.iterator();
        while (iterator.hasNext()) {
            ScarecrowData data = iterator.next();
            if (data.mannequin == null) {
                iterator.remove();
                continue;
            }
            if (data.mannequin.isDead()) {
                if (data.lastLocation != null) {
                    explodeScarecrow(data.lastLocation);
                }
                iterator.remove();
                continue;
            }
            data.lastLocation = data.mannequin.getLocation();
            data.remainingTicks--;
            if (data.remainingTicks <= 0) {
                data.mannequin.remove();
                iterator.remove();
                continue;
            }
            if (data.targetId != null) {
                LivingEntity target = resolveTarget(data.targetId);
                if (target == null || target.isDead()) {
                    data.targetId = null;
                    continue;
                }
                Location current = data.mannequin.getLocation();
                Location targetLoc = target.getLocation();
                double distSq = current.distanceSquared(targetLoc);
                if (distSq <= EXPLODE_DISTANCE * EXPLODE_DISTANCE) {
                    explodeScarecrow(current);
                    data.mannequin.remove();
                    iterator.remove();
                    continue;
                }
                Vector toTarget = targetLoc.toVector().subtract(current.toVector());
                Vector flatDirection = toTarget.clone().setY(0);
                if (flatDirection.lengthSquared() > 1.0E-4) {
                    double yDiff = targetLoc.getY() - current.getY();
                    double currentY = data.mannequin.getVelocity().getY();
                    double desiredY = currentY;
                    if (yDiff > HEIGHT_DIFF_THRESHOLD) {
                        desiredY = Math.max(currentY, UPWARD_SPEED);
                    }
                    Vector velocity = flatDirection.normalize().multiply(CHASE_SPEED).setY(desiredY);
                    Location look = data.mannequin.getLocation();
                    if (toTarget.lengthSquared() > 1.0E-4) {
                        look.setDirection(toTarget);
                    }
                    data.mannequin.setRotation(look.getYaw(), look.getPitch());
                    data.mannequin.setVelocity(velocity);
                }
            }
        }
    }

    private void explodeScarecrow(Location location) {
        if (location.getWorld() == null) {
            return;
        }
        Player owner = getPlayer();
        if (owner == null) {
            return;
        }
        if (!AbilityCombat.getPlugin().getGameManager().isTeamMode()) {
            location.getWorld().createExplosion(location, SCARECROW_EXPLOSION_POWER, false, false, owner);
            return;
        }
        location.getWorld().spawnParticle(Particle.EXPLOSION, location, 1);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        for (LivingEntity target : com.abilitycombat.utils.LocationUtil.getNearbyLivingEntities(location,
                EXPLOSION_RADIUS, owner, entity -> !entity.equals(owner))) {
            if (!AbilityCombat.getPlugin().getGameManager().canApplyNegativeEffect(owner, target)) {
                continue;
            }
            double distance = target.getLocation().distance(location);
            double falloff = Math.max(0.0, 1.0 - (distance / EXPLOSION_RADIUS));
            if (falloff <= 0.0) {
                continue;
            }
            target.damage(EXPLOSION_DAMAGE * falloff, owner);
            Vector knockback = target.getLocation().toVector().subtract(location.toVector());
            if (knockback.lengthSquared() <= 1.0E-4) {
                knockback = new Vector(0, 0.4, 0);
            } else {
                knockback.normalize().multiply(EXPLOSION_KNOCKBACK * falloff).setY(0.35 + (0.2 * falloff));
            }
            target.setVelocity(knockback);
        }
    }

    private LivingEntity resolveTarget(UUID targetId) {
        Entity entity = Bukkit.getEntity(targetId);
        if (entity instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private static class ScarecrowData {
        private final PlayerReplica mannequin;
        private int remainingTicks;
        private UUID targetId;
        private Location lastLocation;

        private ScarecrowData(PlayerReplica mannequin, int remainingTicks) {
            this.mannequin = mannequin;
            this.remainingTicks = remainingTicks;
            this.lastLocation = mannequin.getLocation();
        }

        private void setTarget(UUID targetId) {
            this.targetId = targetId;
        }
    }

    private ItemStack cloneItem(ItemStack item) {
        if (item == null) {
            return null;
        }
        return item.clone();
    }

    private ItemStack[] cloneItems(ItemStack[] items) {
        if (items == null) {
            return null;
        }
        ItemStack[] cloned = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            cloned[i] = cloneItem(items[i]);
        }
        return cloned;
    }
}
