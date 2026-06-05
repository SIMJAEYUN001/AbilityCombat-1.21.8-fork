package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.game.Participant;
import com.abilitycombat.ui.SprintHudService;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
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
        "§e§l[패시브 - 대지의 흔적]",
        "§7스프린트 게이지 대시가 끝나면 이동 경로의 지면이 순차적으로 폭발합니다",
        "§7폭발 전 균열이 표시되고, §f0.15초§7 간격으로 터집니다",
        "§7반경 §f2칸§7 내 적에게 균열당 §c10 피해§7를 줍니다",
        "§7적중한 적은 §e30% 슬로우 2초§7를 받고, 같은 대시에서는 한 번만 맞습니다"
}, summarize = {
        "§7패시브§f: 스프린트 대시 경로가 0.15초 간격으로 순차 폭발",
        "§7균열§f: 예고 표시, 반경 2칸, 피해 10 + 30% 슬로우 2초"
})
public class EarthAwakening extends AbilityBase implements SprintHudService.DashListener {

    private static final int SAMPLE_INTERVAL_TICKS = 2;
    private static final double MIN_SAMPLE_DISTANCE_SQUARED = 0.64;
    private static final int CRACK_START_DELAY_TICKS = 2;
    private static final int CRACK_STEP_DELAY_TICKS = 3;
    private static final int MAX_PATH_SAMPLES = 18;
    private static final double CRACK_RADIUS = 2.0;
    private static final double DAMAGE = 10.0;
    private static final int SLOW_TICKS = 40;
    private static final double SLOW_PERCENT = 30.0;
    private static final Particle.DustOptions WARNING_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 170, 35), 1.1f);

    private final List<Location> sampledPath = new ArrayList<>();
    private final List<PendingCrack> pendingCracks = new ArrayList<>();
    private boolean trackingDash;
    private Location lastSample;
    private int lastSampleTick;

    public EarthAwakening(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        SprintHudService sprintHudService = getSprintHudService();
        if (sprintHudService != null) {
            sprintHudService.addDashListener(this);
        }
    }

    @Override
    protected void onDeactivate() {
        SprintHudService sprintHudService = getSprintHudService();
        if (sprintHudService != null) {
            sprintHudService.removeDashListener(this);
        }
        clearState();
    }

    @Override
    protected void onDestroy() {
        SprintHudService sprintHudService = getSprintHudService();
        if (sprintHudService != null) {
            sprintHudService.removeDashListener(this);
        }
        clearState();
    }

    @Override
    public void onDashStart(Player player) {
        if (!isOwner(player)) {
            return;
        }
        sampledPath.clear();
        trackingDash = true;
        lastSample = null;
        lastSampleTick = AbilityTickManager.getGlobalTick() - SAMPLE_INTERVAL_TICKS;
        samplePath(player.getLocation().clone(), true);
    }

    @Override
    public void onDashTick(Player player, Location location) {
        if (!trackingDash || !isOwner(player)) {
            return;
        }
        int tick = AbilityTickManager.getGlobalTick();
        if (tick - lastSampleTick < SAMPLE_INTERVAL_TICKS) {
            return;
        }
        samplePath(location, false);
        lastSampleTick = tick;
    }

    @Override
    public void onDashEnd(Player player) {
        if (!trackingDash || !isOwner(player)) {
            return;
        }
        samplePath(player.getLocation().clone(), true);
        queueCracks(AbilityTickManager.getGlobalTick());
        trackingDash = false;
        sampledPath.clear();
        lastSample = null;
        registerTick();
    }

    @Override
    public void onTick(int tick) {
        processCracks(tick);
        if (!trackingDash && pendingCracks.isEmpty()) {
            unregisterTick();
        }
    }

    private void samplePath(Location location, boolean force) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (!force && lastSample != null && location.getWorld().equals(lastSample.getWorld())
                && location.distanceSquared(lastSample) < MIN_SAMPLE_DISTANCE_SQUARED) {
            return;
        }
        if (sampledPath.size() >= MAX_PATH_SAMPLES) {
            return;
        }
        sampledPath.add(location.clone());
        lastSample = location.clone();
    }

    private void queueCracks(int tick) {
        Set<UUID> hitTargets = new HashSet<>();
        int index = 0;
        for (Location sample : sampledPath) {
            Location ground = toGround(sample);
            if (ground == null) {
                continue;
            }
            PendingCrack crack = new PendingCrack(ground, tick + CRACK_START_DELAY_TICKS
                    + (index * CRACK_STEP_DELAY_TICKS), hitTargets);
            pendingCracks.add(crack);
            index++;
        }
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

    private void markCrack(PendingCrack crack, int tick) {
        World world = crack.location.getWorld();
        if (world == null) {
            return;
        }
        double pulse = 0.85 + (Math.sin(tick * 0.45) * 0.15);
        for (int i = 0; i < 6; i++) {
            double angle = (Math.PI * 2.0 * i) / 6.0;
            Vector direction = new Vector(Math.cos(angle), 0.0, Math.sin(angle));
            Location start = crack.location.clone().add(0, 0.08, 0);
            for (double distance = 0.35; distance <= CRACK_RADIUS; distance += 0.35) {
                Location point = start.clone().add(direction.clone().multiply(distance));
                ParticleUtil.spawnParticle(world, Particle.DUST, point,
                        1, 0.025 * pulse, 0.01, 0.025 * pulse, 0.0, WARNING_DUST, 1, 64);
            }
        }
        ParticleUtil.spawnParticle(world, Particle.ELECTRIC_SPARK, crack.location.clone().add(0, 0.12, 0),
                2, CRACK_RADIUS * 0.25, 0.02, CRACK_RADIUS * 0.25, 0.0, 1, 64);
    }

    private void processCracks(int tick) {
        if (pendingCracks.isEmpty()) {
            return;
        }
        Iterator<PendingCrack> iterator = pendingCracks.iterator();
        while (iterator.hasNext()) {
            PendingCrack crack = iterator.next();
            if (tick >= crack.triggerTick) {
                fireCrack(crack);
                iterator.remove();
            } else {
                markCrack(crack, tick);
            }
        }
    }

    private void fireCrack(PendingCrack crack) {
        Player player = getPlayer();
        World world = crack.location.getWorld();
        if (player == null || world == null) {
            return;
        }
        BlockData blockData = getFloorBlockData(world, crack.location);
        ParticleUtil.spawnParticle(world, Particle.BLOCK_CRUMBLE, crack.location.clone().add(0, 0.08, 0),
                32, 0.8, 0.1, 0.8, 0.08, blockData, 1, 64);
        world.playSound(crack.location, Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 1.55f);

        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(crack.location, CRACK_RADIUS, player,
                entity -> !(entity instanceof ArmorStand))) {
            if (crack.hitTargets.add(target.getUniqueId())) {
                target.setNoDamageTicks(0);
                target.damage(DAMAGE, player);
                applySlow(target, SLOW_TICKS, SLOW_PERCENT);
            }
        }
    }

    private BlockData getFloorBlockData(World world, Location location) {
        Block floor = world.getBlockAt(location.getBlockX(), location.getBlockY() - 1, location.getBlockZ());
        return floor.getType().isSolid() ? floor.getBlockData().clone() : Material.DIRT.createBlockData();
    }

    private boolean isOwner(Player player) {
        Player owner = getPlayer();
        return owner != null && player != null && owner.getUniqueId().equals(player.getUniqueId());
    }

    private SprintHudService getSprintHudService() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin == null ? null : plugin.getSprintHudService();
    }

    private void clearState() {
        trackingDash = false;
        sampledPath.clear();
        pendingCracks.clear();
        lastSample = null;
        lastSampleTick = 0;
        unregisterTick();
    }

    private record PendingCrack(Location location, int triggerTick, Set<UUID> hitTargets) {
    }
}
