package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@AbilityManifest(name = "대지의 각성 (EarthAwakening)", species = AbilityManifest.Species.SPECIAL, explain = {
        "§e§l[웅크리기 + 철괴 우클릭 - 대지의 흔적]§f §8(쿨타임: 12초)",
        "§7전방으로 §f최대 8칸§7 대시하며 이동 경로를 남깁니다.",
        "§7경로는 §f0.7초§7 뒤 지면 균열로 폭발합니다.",
        "§7각 균열은 반경 §f2칸§7 내 적에게 §c3 피해§7와 §e30% 슬로우 2초§7를 줍니다.",
        "§7블록은 파괴되지 않으며, 같은 적은 한 번만 피해를 받습니다."
}, summarize = {
        "§7웅크리기+철괴 우클릭§f: 8칸 대시 후 경로 균열",
        "§7균열§f: 0.7초 지연, 반경 2칸, 피해 3 + 30% 슬로우 2초"
})
public class EarthAwakening extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 12;
    private static final double DASH_SPEED = 1.8;
    private static final double DASH_Y = 0.35;
    private static final double MAX_DASH_DISTANCE = 8.0;
    private static final int MAX_DASH_TICKS = 16;
    private static final int SAMPLE_INTERVAL_TICKS = 2;
    private static final double MIN_SAMPLE_DISTANCE_SQUARED = 0.64;
    private static final int CRACK_DELAY_TICKS = 14;
    private static final double CRACK_RADIUS = 2.0;
    private static final double DAMAGE = 3.0;
    private static final int SLOW_TICKS = 40;
    private static final double SLOW_PERCENT = 30.0;
    private static final Particle.DustOptions CRACK_DUST = new Particle.DustOptions(Color.fromRGB(133, 83, 45), 1.0f);

    private final ActionbarCooldown cooldown = new ActionbarCooldown(COOLDOWN_SECONDS);
    private final List<Location> sampledPath = new ArrayList<>();
    private final List<PendingCrack> pendingCracks = new ArrayList<>();
    private final Set<UUID> hitTargets = new HashSet<>();
    private boolean dashing;
    private Location dashOrigin;
    private Location lastSample;
    private int dashStartTick;

    public EarthAwakening(Participant participant) {
        super(participant);
    }

    @Override
    protected void onDeactivate() {
        clearState();
    }

    @Override
    protected void onDestroy() {
        clearState();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        Player player = getPlayer();
        if (player == null || !player.isSneaking()) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        if (dashing) {
            return false;
        }
        startDash(player);
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void onTick(int tick) {
        if (dashing && tick % SAMPLE_INTERVAL_TICKS == 0) {
            sampleDash(tick);
        }
        processCracks(tick);
        if (!dashing && pendingCracks.isEmpty()) {
            hitTargets.clear();
            unregisterTick();
        }
    }

    private void startDash(Player player) {
        Vector direction = player.getEyeLocation().getDirection();
        direction.setY(0);
        if (direction.lengthSquared() < 1.0E-4) {
            direction = player.getLocation().getDirection().setY(0);
        }
        if (direction.lengthSquared() < 1.0E-4) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize();
        player.setVelocity(direction.multiply(DASH_SPEED).setY(DASH_Y));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 0.8f, 0.65f);

        sampledPath.clear();
        pendingCracks.clear();
        hitTargets.clear();
        dashOrigin = player.getLocation().clone();
        lastSample = dashOrigin.clone();
        sampledPath.add(lastSample.clone());
        dashStartTick = Bukkit.getCurrentTick();
        dashing = true;
        registerTick();
    }

    private void sampleDash(int tick) {
        Player player = getPlayer();
        if (player == null || dashOrigin == null) {
            finishDash(tick);
            return;
        }
        Location current = player.getLocation().clone();
        if (lastSample == null || current.distanceSquared(lastSample) >= MIN_SAMPLE_DISTANCE_SQUARED) {
            sampledPath.add(current);
            lastSample = current;
        }
        if (current.distanceSquared(dashOrigin) >= MAX_DASH_DISTANCE * MAX_DASH_DISTANCE
                || tick - dashStartTick >= MAX_DASH_TICKS) {
            finishDash(tick);
        }
    }

    private void finishDash(int tick) {
        dashing = false;
        for (Location location : sampledPath) {
            Location ground = toGround(location);
            if (ground != null) {
                pendingCracks.add(new PendingCrack(ground, tick + CRACK_DELAY_TICKS));
            }
        }
        sampledPath.clear();
        dashOrigin = null;
        lastSample = null;
    }

    private Location toGround(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int floorY = LocationUtil.getFloorY(world, x, z, Math.min(location.getBlockY() + 2, world.getMaxHeight() - 1));
        if (floorY <= world.getMinHeight()) {
            return null;
        }
        return new Location(world, x + 0.5, floorY, z + 0.5);
    }

    private void processCracks(int tick) {
        if (pendingCracks.isEmpty()) {
            return;
        }
        Iterator<PendingCrack> iterator = pendingCracks.iterator();
        while (iterator.hasNext()) {
            PendingCrack crack = iterator.next();
            if (tick >= crack.triggerTick) {
                fireCrack(crack.location);
                iterator.remove();
            }
        }
    }

    private void fireCrack(Location location) {
        Player player = getPlayer();
        World world = location.getWorld();
        if (player == null || world == null) {
            return;
        }
        Block floor = world.getBlockAt(location.getBlockX(), location.getBlockY() - 1, location.getBlockZ());
        BlockData blockData = floor.getType().isSolid() ? floor.getBlockData().clone() : Material.DIRT.createBlockData();
        ParticleUtil.spawnParticle(world, Particle.BLOCK_CRUMBLE, location.clone().add(0, 0.08, 0),
                18, 0.75, 0.08, 0.75, 0.04, blockData, 1, 64);
        ParticleUtil.spawnParticle(world, Particle.DUST, location.clone().add(0, 0.12, 0),
                10, 0.65, 0.05, 0.65, 0.0, CRACK_DUST, 1, 64);
        world.playSound(location, Sound.BLOCK_DEEPSLATE_BREAK, 0.55f, 0.75f);

        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(location, CRACK_RADIUS, player,
                entity -> !(entity instanceof ArmorStand))) {
            if (hitTargets.add(target.getUniqueId())) {
                target.setNoDamageTicks(0);
                target.damage(DAMAGE, player);
                applySlow(target, SLOW_TICKS, SLOW_PERCENT);
            }
        }
    }

    private void clearState() {
        dashing = false;
        sampledPath.clear();
        pendingCracks.clear();
        hitTargets.clear();
        dashOrigin = null;
        lastSample = null;
        unregisterTick();
    }

    private record PendingCrack(Location location, int triggerTick) {
    }
}
