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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "검무 (SwordDance)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 검무]§f §8(쿨타임: 18초)",
        "§7주변 §f5칸§7 내 적을 거리순으로 골라 총 §f4회§7 베어냅니다",
        "§7베기마다 §f0.2초§7 간격으로 대상 주변 §f1.5칸§7에 이동해 §c10 피해§7를 줍니다",
        "§7대상이 부족하면 같은 대상을 동서남북 방향에서 반복 베어냅니다",
        "§7시전 중 §e1.2초간 무적§7 상태가 됩니다"
}, summarize = {
        "§7철괴 우클릭§f: 5칸 내 적을 총 4회 순차 이동 타격",
        "§7시전 중§f: 1.2초 무적, 타격당 피해 10"
})
public class SwordDance extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 18;
    private static final int TOTAL_STRIKES = 4;
    private static final double SCAN_RADIUS = 5.0;
    private static final int STEP_DELAY_TICKS = 4;
    private static final int INVINCIBLE_TICKS = 24;
    private static final double DAMAGE_PER_HIT = 10.0;
    private static final double SLASH_DISTANCE = 1.5;
    private static final Vector[] SLASH_DIRECTIONS = {
            new Vector(1, 0, 0),
            new Vector(-1, 0, 0),
            new Vector(0, 0, 1),
            new Vector(0, 0, -1)
    };

    private final ActionbarCooldown cooldown = new ActionbarCooldown(COOLDOWN_SECONDS);
    private final List<LivingEntity> danceTargets = new ArrayList<>();
    private final Map<UUID, Integer> targetSlashCounts = new HashMap<>();
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
                .toList();
        if (targets.isEmpty()) {
            player.sendMessage("§c검무 대상이 없습니다");
            return false;
        }

        startDance(buildStrikeSequence(targets));
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
        targetSlashCounts.clear();
        targetIndex = 0;
        int now = AbilityTickManager.getGlobalTick();
        invincibleUntilTick = now + INVINCIBLE_TICKS;
        dancing = true;
        registerTick();
        Player player = getPlayer();
        if (player != null) {
            playStartEffect(player);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.75f, 1.55f);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.25f, 1.85f);
        }
        strikeNextTarget();
        nextStepTick = now + STEP_DELAY_TICKS;
    }

    private void stopDance() {
        dancing = false;
        danceTargets.clear();
        targetSlashCounts.clear();
        targetIndex = 0;
        nextStepTick = 0;
        invincibleUntilTick = 0;
        unregisterTick();
    }

    private List<LivingEntity> buildStrikeSequence(List<LivingEntity> targets) {
        List<LivingEntity> sequence = new ArrayList<>(TOTAL_STRIKES);
        for (int i = 0; i < TOTAL_STRIKES; i++) {
            sequence.add(targets.get(i % targets.size()));
        }
        return sequence;
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
                    || target instanceof ArmorStand) {
                continue;
            }
            Location before = player.getLocation().clone();
            Location destination = getSafeSlashLocation(target, before);
            player.teleport(destination);
            target.setNoDamageTicks(0);
            target.damage(DAMAGE_PER_HIT, player);
            playStrikeEffect(player, target);
            return;
        }
    }

    private Location getSafeSlashLocation(LivingEntity target, Location fallback) {
        int slashIndex = targetSlashCounts.merge(target.getUniqueId(), 1, Integer::sum) - 1;
        for (int i = 0; i < SLASH_DIRECTIONS.length; i++) {
            Vector direction = SLASH_DIRECTIONS[(slashIndex + i) % SLASH_DIRECTIONS.length].clone();
            Location destination = target.getLocation().clone().add(direction.multiply(SLASH_DISTANCE));
            destination.setDirection(target.getLocation().toVector().subtract(destination.toVector()));
            if (isSafeTeleportLocation(destination)) {
                return destination;
            }
        }
        return fallback;
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
        world.playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.85f, 1.75f);
        world.playSound(target.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.28f, 2.0f);
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
