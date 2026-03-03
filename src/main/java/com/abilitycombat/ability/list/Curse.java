package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import com.abilitycombat.vfx.Circle;
import com.abilitycombat.vfx.VectorUtil;
import com.destroystokyo.paper.SkinParts;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

@AbilityManifest(name = "컬스 (Curse)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.OTHERS, explain = {
        "§e§l[철괴 우클릭 - 저주 인형]§f §8(쿨타임: 40초)",
        "§e바라보는 플레이어§7를 타겟으로 (최대 §f12칸§7)",
        "§7§5저주 인형§7을 생성합니다. (지속시간: §f10초§7)",
        "",
        "§7인형이 피해를 받으면 피해의 일부가",
        "§7타겟에게 §c전이§7됩니다.",
        "",
        "§7전이율: 기본 §c40%§7, 대상 체력이 낮을수록",
        "§7최대 §c100%§7까지 증가합니다."
}, summarize = {
        "§7철괴 우클릭§f: 바라보는 대상에게 저주 인형 (10초)",
        "§7인형 피해§f → 타겟에게 전이"
})
public class Curse extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 40;
    private static final int DURATION_SECONDS = 10;
    private static final double RANGE = 12.0;
    private static final double SPIRAL_RADIUS = 1.4;
    private static final int SPIRAL_POINTS = 12;
    private static final String NO_PLAYER_KEY = "curse:no_player";
    private static final double DOLL_FORWARD_DISTANCE = 1.2;
    private static final int TRANSFER_COOLDOWN_TICKS = 10;
    private static final double DOLL_SCALE = 0.7;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private Mannequin doll;
    private LivingEntity target;
    private int particleStep;
    private int remainingCurseSeconds = 0;
    private int lastTransferTick = -TRANSFER_COOLDOWN_TICKS;

    public Curse(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        stopCurse();
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
        if (isCurseRunning()) {
            return false;
        }
        Player player = getPlayer();
        // 바라보는 플레이어를 타겟으로 변경
        Player lookingAt = LocationUtil.getEntityLookingAt(Player.class, player, RANGE,
                entity -> !entity.equals(player) && LocationUtil.isValidTarget(entity));
        if (lookingAt == null) {
            notifyNoPlayer();
            return false;
        }
        target = lookingAt;
        startCurse();
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamage((EntityDamageByEntityEvent) event);
        }
    }

    private void onDamage(EntityDamageByEntityEvent event) {
        if (doll == null || target == null) {
            return;
        }
        if (!event.getEntity().equals(doll)) {
            return;
        }
        event.setCancelled(true);
        doll.setNoDamageTicks(0);
        if (target.isDead()) {
            stopCurse();
            return;
        }
        int currentTick = AbilityTickManager.getGlobalTick();
        if (currentTick - lastTransferTick < TRANSFER_COOLDOWN_TICKS) {
            return;
        }
        var maxAttr = target.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxAttr != null ? maxAttr.getValue() : 20.0;
        double healthRatio = Math.max(0.0, target.getHealth() / maxHealth);
        double multiplier = 0.4 + (1.0 - healthRatio) * 0.6;
        double transfer = event.getFinalDamage() * multiplier;
        int previousNoDamageTicks = target.getNoDamageTicks();
        target.setNoDamageTicks(0);
        target.damage(transfer, getPlayer());
        target.setNoDamageTicks(Math.max(previousNoDamageTicks, TRANSFER_COOLDOWN_TICKS));
        lastTransferTick = currentTick;
    }

    @Override
    protected void onDestroy() {
        stopCurse();
    }

    private void spawnDoll(Location location, LivingEntity target) {
        removeDoll();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        doll = world.spawn(location, Mannequin.class, entity -> {
            entity.setInvulnerable(false);
            entity.setImmovable(true);
            entity.setGravity(false);
            entity.setAI(false);
            entity.customName(Component.text("저주 인형", NamedTextColor.DARK_PURPLE));
            entity.setCustomNameVisible(true);
            entity.setDescription(null);
            entity.setProfile(resolveProfile(target));
            entity.setSkinParts(SkinParts.allParts());
            AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(scale.getDefaultValue() * DOLL_SCALE);
            }
            EntityEquipment equipment = entity.getEquipment();
            if (equipment != null) {
                equipment.setItemInMainHand(new ItemStack(Material.POPPY));
            }
        });
    }

    private ResolvableProfile resolveProfile(LivingEntity target) {
        if (target instanceof Player player) {
            return ResolvableProfile.resolvableProfile(player.getPlayerProfile());
        }
        return Mannequin.defaultProfile();
    }

    private void removeDoll() {
        if (doll != null && !doll.isDead()) {
            doll.remove();
        }
        doll = null;
    }

    private void stopCurse() {
        removeDoll();
        target = null;
        particleStep = 0;
        remainingCurseSeconds = 0;
        lastTransferTick = -TRANSFER_COOLDOWN_TICKS;
    }

    private void startCurse() {
        particleStep = 0;
        remainingCurseSeconds = DURATION_SECONDS;
        lastTransferTick = -TRANSFER_COOLDOWN_TICKS;
        spawnDoll(getDollSpawnLocation(getPlayer()), target);
        registerTick();
    }

    private Location getDollSpawnLocation(Player player) {
        Location base = player.getLocation().clone();
        Vector direction = player.getEyeLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 1.0E-4) {
            direction = player.getLocation().getDirection().setY(0);
        }
        direction.normalize().multiply(DOLL_FORWARD_DISTANCE);
        Location spawn = base.add(direction).add(0, 0.2, 0);
        Vector look = player.getLocation().toVector().subtract(spawn.toVector());
        look.setY(0);
        if (look.lengthSquared() > 1.0E-4) {
            spawn.setDirection(look);
        }
        return spawn;
    }

    private boolean isCurseRunning() {
        return remainingCurseSeconds > 0;
    }

    @Override
    public void onTick(int tick) {
        if (isDestroyed()) {
            unregisterTick();
            return;
        }

        if (tick % 20 == 0 && isCurseRunning()) {
            if (target == null || target.isDead()) {
                stopCurse();
                return;
            }
            spawnCurseParticles();
            remainingCurseSeconds--;
            if (remainingCurseSeconds <= 0) {
                stopCurse();
            }
        }
    }

    private void notifyNoPlayer() {
        var channel = getActionbarChannel();
        Component message = Component.text("바라보는 대상이 없습니다.", NamedTextColor.RED);
        if (channel != null) {
            channel.update(getPlayer(), NO_PLAYER_KEY, 5, message);
            AbilityCombat.getPlugin().getServer().getScheduler().runTaskLater(AbilityCombat.getPlugin(),
                    () -> channel.clear(getPlayer(), NO_PLAYER_KEY), 40L);
        } else {
            getPlayer().sendActionBar(message);
        }
    }

    private void spawnCurseParticles() {
        if (doll == null) {
            return;
        }
        World world = doll.getWorld();
        Location base = doll.getLocation().clone().add(0, 0.2, 0);
        double height = (particleStep % 20) * 0.08;
        double radians = Math.toRadians((particleStep * 18) % 360);
        for (Vector vector : Circle.of(SPIRAL_RADIUS, SPIRAL_POINTS)) {
            Vector rotated = VectorUtil.rotateAroundAxisY(vector, radians);
            Location point = base.clone().add(rotated).add(0, height, 0);
            ParticleUtil.spawnParticle(world, Particle.SOUL_FIRE_FLAME, point, 1, 0, 0, 0, 0, 2, 0);
        }
        particleStep++;
    }

}
