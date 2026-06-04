package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@AbilityManifest(name = "검무 (SwordDance)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 검무]§f §8(쿨타임: 18초)",
        "§7주변 §f5칸§7 내 적을 거리순으로 최대 §f4명§7까지 베어냅니다.",
        "§7대상마다 §f0.2초§7 간격으로 등 뒤 §f1.5칸§7에 이동해 §c4 피해§7를 줍니다.",
        "§7시전 중 §e1.2초간 무적§7 상태가 되며, 동일 대상은 한 번만 적중합니다."
}, summarize = {
        "§7철괴 우클릭§f: 5칸 내 최대 4명 순차 이동 타격",
        "§7시전 중§f: 1.2초 무적, 타격당 피해 4"
})
public class SwordDance extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 18;
    private static final int MAX_TARGETS = 4;
    private static final double SCAN_RADIUS = 5.0;
    private static final int STEP_DELAY_TICKS = 4;
    private static final int INVINCIBLE_TICKS = 24;
    private static final double DAMAGE_PER_HIT = 4.0;
    private static final double BACK_DISTANCE = 1.5;

    private final ActionbarCooldown cooldown = new ActionbarCooldown(COOLDOWN_SECONDS);
    private final List<LivingEntity> danceTargets = new ArrayList<>();
    private final Set<UUID> hitTargets = new HashSet<>();
    private boolean dancing;
    private int nextStepTick;
    private int invincibleUntilTick;
    private int targetIndex;

    public SwordDance(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unsubscribeEvent(EntityDamageEvent.class);
        stopDance();
    }

    @Override
    protected void onDestroy() {
        stopDance();
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

        List<LivingEntity> targets = LocationUtil.getNearbyLivingEntities(player.getLocation(), SCAN_RADIUS, player,
                target -> !(target instanceof ArmorStand))
                .stream()
                .sorted(Comparator.comparingDouble(target -> target.getLocation().distanceSquared(player.getLocation())))
                .limit(MAX_TARGETS)
                .toList();
        if (targets.isEmpty()) {
            player.sendMessage("§c검무 대상이 없습니다.");
            return false;
        }

        startDance(targets);
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (!(event instanceof EntityDamageEvent damageEvent) || !dancing) {
            return;
        }
        Player player = getPlayer();
        if (player != null && damageEvent.getEntity().getUniqueId().equals(player.getUniqueId())
                && AbilityTickManager.getGlobalTick() <= invincibleUntilTick) {
            damageEvent.setCancelled(true);
        }
    }

    @Override
    public void onTick(int tick) {
        if (!dancing) {
            unregisterTick();
            return;
        }
        if (targetIndex < danceTargets.size() && tick >= nextStepTick) {
            strikeNextTarget();
            nextStepTick = tick + STEP_DELAY_TICKS;
        }
        if (targetIndex >= danceTargets.size() && tick > invincibleUntilTick) {
            stopDance();
        }
    }

    private void startDance(List<LivingEntity> targets) {
        danceTargets.clear();
        danceTargets.addAll(targets);
        hitTargets.clear();
        targetIndex = 0;
        int now = AbilityTickManager.getGlobalTick();
        invincibleUntilTick = now + INVINCIBLE_TICKS;
        dancing = true;
        registerTick();
        Player player = getPlayer();
        if (player != null) {
            playStartEffect(player);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.35f);
        }
        strikeNextTarget();
        nextStepTick = now + STEP_DELAY_TICKS;
    }

    private void stopDance() {
        dancing = false;
        danceTargets.clear();
        hitTargets.clear();
        targetIndex = 0;
        nextStepTick = 0;
        invincibleUntilTick = 0;
        unregisterTick();
    }

    private void strikeNextTarget() {
        Player player = getPlayer();
        if (player == null) {
            stopDance();
            return;
        }
        while (targetIndex < danceTargets.size()) {
            LivingEntity target = danceTargets.get(targetIndex++);
            if (target == null || target.isDead() || !LocationUtil.isValidTarget(player, target)
                    || target instanceof ArmorStand || !hitTargets.add(target.getUniqueId())) {
                continue;
            }
            Location before = player.getLocation().clone();
            Location destination = getSafeBackLocation(player, target, before);
            player.teleport(destination);
            target.setNoDamageTicks(0);
            target.damage(DAMAGE_PER_HIT, player);
            playStrikeEffect(player, target);
            return;
        }
    }

    private Location getSafeBackLocation(Player player, LivingEntity target, Location fallback) {
        Vector back = target.getLocation().getDirection();
        back.setY(0);
        if (back.lengthSquared() < 1.0E-4) {
            back = player.getLocation().toVector().subtract(target.getLocation().toVector());
            back.setY(0);
        }
        if (back.lengthSquared() < 1.0E-4) {
            return fallback;
        }
        Location destination = target.getLocation().clone().subtract(back.normalize().multiply(BACK_DISTANCE));
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());
        return isSafeTeleportLocation(destination) ? destination : fallback;
    }

    private boolean isSafeTeleportLocation(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        Block feet = location.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block below = feet.getRelative(BlockFace.DOWN);
        return !feet.getType().isSolid() && !head.getType().isSolid() && below.getType().isSolid();
    }

    private void playStrikeEffect(Player player, LivingEntity target) {
        Location center = target.getLocation().clone().add(0, 1.0, 0);
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        ParticleUtil.spawnParticle(world, Particle.SWEEP_ATTACK, center, 3, 0.35, 0.35, 0.35, 0.0, 1, 64);
        ParticleUtil.spawnParticle(world, Particle.CRIT, center, 16, 0.45, 0.45, 0.45, 0.08, 1, 64);
        world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.85f, 1.55f);
        world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.35f, 1.8f);
    }

    private void playStartEffect(Player player) {
        Location center = player.getLocation().clone().add(0, 1.0, 0);
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        ParticleUtil.spawnParticle(world, Particle.SWEEP_ATTACK, center, 5, 0.8, 0.35, 0.8, 0.0, 1, 64);
        ParticleUtil.spawnParticle(world, Particle.ENCHANTED_HIT, center, 24, 0.7, 0.45, 0.7, 0.04, 1, 64);
    }
}
