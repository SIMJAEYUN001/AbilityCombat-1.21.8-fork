package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@AbilityManifest(name = "지진강타 (EarthquakeStrike)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 지진강타]§f §8(쿨타임: 30초)",
        "§7바라보는 방향으로 §f지진파§7가 전진합니다.",
        "§7지진파는 바닥 블럭을 §f순차적으로 솟구치게§7 합니다.",
        "§7(최대 §f15칸§7, 폭 §f3§7)",
        "",
        "§7적중 시 §c5의 피해§7와 함께",
        "§7§e6초 기절§7, §e50% 슬로우§7, §e멀미§7를 부여합니다."
}, summarize = {
        "§7철괴 우클릭§f: 전방 지진파 (15칸)",
        "§7적중§f: 피해 5 + 기절/50% 슬로우/멀미 (4초)"
})
public class EarthquakeStrike extends AbilityBase implements ActiveHandler {

    private static final int MAX_DISTANCE = 30;
    private static final int WAVE_WIDTH = 8;
    private static final int TICK_PER_BLOCK = 2;
    private static final int BLOCK_LIFETIME = 25;
    private static final double HIT_RADIUS = 1.5;
    private static final int COOLDOWN_SECONDS = 30;
    private static final double DAMAGE = 5.0;
    private static final int STUN_TICKS = 80;
    private static final int DEBUFF_TICKS = 80;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final List<FallingBlockEntry> activeBlocks = new ArrayList<>();
    private final Set<UUID> hitTargets = new HashSet<>();

    private boolean waveActive = false;
    private int waveStep = 0;
    private Vector waveDirection;
    private Vector wavePerpendicular;
    private Location waveOrigin;
    private int floorStartY = 0;

    public EarthquakeStrike(Participant participant) {
        super(participant);
    }

    @Override
    protected void onDeactivate() {
        stopWave(true);
        unregisterTick();
    }

    @Override
    protected void onDestroy() {
        stopWave(true);
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
        if (waveActive) {
            return false;
        }
        if (!startWave()) {
            return false;
        }
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    private boolean startWave() {
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        World world = player.getWorld();
        Vector direction = player.getEyeLocation().getDirection();
        direction.setY(0);
        if (direction.lengthSquared() < 1.0E-4) {
            direction = player.getLocation().getDirection().setY(0);
        }
        if (direction.lengthSquared() < 1.0E-4) {
            return false;
        }
        direction.normalize();
        waveDirection = direction.clone();
        wavePerpendicular = new Vector(-direction.getZ(), 0, direction.getX());
        if (wavePerpendicular.lengthSquared() > 0) {
            wavePerpendicular.normalize();
        }

        waveOrigin = player.getLocation().clone();
        floorStartY = Math.min(player.getLocation().getBlockY() + 2, world.getMaxHeight() - 1);
        waveStep = 1;
        waveActive = true;
        hitTargets.clear();
        registerTick();
        return true;
    }

    private void stopWave(boolean clearBlocks) {
        waveActive = false;
        waveStep = 0;
        waveDirection = null;
        wavePerpendicular = null;
        waveOrigin = null;
        floorStartY = 0;
        hitTargets.clear();
        if (clearBlocks) {
            for (FallingBlockEntry entry : activeBlocks) {
                if (entry.block != null && !entry.block.isDead()) {
                    entry.block.remove();
                }
            }
            activeBlocks.clear();
        }
    }

    @Override
    public void onTick(int tick) {
        if (isDestroyed()) {
            stopWave(true);
            unregisterTick();
            return;
        }

        cleanupBlocks(tick);

        if (waveActive && tick % TICK_PER_BLOCK == 0) {
            advanceWave(tick);
        }

        if (!waveActive && activeBlocks.isEmpty()) {
            unregisterTick();
        }
    }

    private void cleanupBlocks(int tick) {
        if (activeBlocks.isEmpty()) {
            return;
        }
        Iterator<FallingBlockEntry> iterator = activeBlocks.iterator();
        while (iterator.hasNext()) {
            FallingBlockEntry entry = iterator.next();
            FallingBlock block = entry.block;
            if (block == null || block.isDead()) {
                iterator.remove();
                continue;
            }
            int age = tick - entry.spawnTick;
            if (age >= BLOCK_LIFETIME) {
                if (block != null && !block.isDead()) {
                    block.remove();
                }
                iterator.remove();
                continue;
            }
        }
    }

    private void advanceWave(int tick) {
        if (waveOrigin == null || waveDirection == null || wavePerpendicular == null) {
            stopWave(false);
            return;
        }
        Player player = getPlayer();
        if (player == null) {
            stopWave(false);
            return;
        }
        World world = waveOrigin.getWorld();
        if (world == null) {
            stopWave(false);
            return;
        }
        if (waveStep > MAX_DISTANCE) {
            stopWave(false);
            return;
        }

        double baseX = waveOrigin.getX() + waveDirection.getX() * waveStep;
        double baseZ = waveOrigin.getZ() + waveDirection.getZ() * waveStep;

        int centerX = (int) Math.floor(baseX);
        int centerZ = (int) Math.floor(baseZ);
        int centerFloorY = LocationUtil.getFloorY(world, centerX, centerZ, floorStartY);
        if (centerFloorY <= world.getMinHeight()) {
            stopWave(false);
            return;
        }
        Location soundLoc = new Location(world, centerX + 0.5, centerFloorY, centerZ + 0.5);
        world.playSound(soundLoc, Sound.ENTITY_IRON_GOLEM_HURT, 0.8f, 0.9f);

        int halfWidth = WAVE_WIDTH / 2;
        for (int w = -halfWidth; w <= halfWidth; w++) {
            double offsetX = wavePerpendicular.getX() * w;
            double offsetZ = wavePerpendicular.getZ() * w;
            int x = (int) Math.floor(baseX + offsetX);
            int z = (int) Math.floor(baseZ + offsetZ);
            spawnWaveBlock(world, x, z, tick, player);
        }

        waveStep++;
        if (waveStep > MAX_DISTANCE) {
            stopWave(false);
        }
    }

    private void spawnWaveBlock(World world, int x, int z, int tick, Player player) {
        int floorY = LocationUtil.getFloorY(world, x, z, floorStartY);
        if (floorY <= world.getMinHeight()) {
            return;
        }
        Block floorBlock = world.getBlockAt(x, floorY - 1, z);
        if (!floorBlock.getType().isSolid()) {
            return;
        }
        BlockData blockData = floorBlock.getBlockData().clone();
        Location spawnLoc = new Location(world, x + 0.5, floorY, z + 0.5);

        FallingBlock fallingBlock = world.spawn(spawnLoc, FallingBlock.class, fb -> {
            fb.setBlockData(blockData);
            fb.setDropItem(false);
            fb.setHurtEntities(false);
            fb.setVelocity(new Vector(0, 0.6, 0));
        });
        activeBlocks.add(new FallingBlockEntry(fallingBlock, tick));

        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(spawnLoc, HIT_RADIUS, player,
                entity -> !entity.equals(player))) {
            if (hitTargets.add(target.getUniqueId())) {
                applyDebuffs(player, target);
            }
        }
    }

    private void applyDebuffs(Player player, LivingEntity target) {
        target.damage(DAMAGE, player);
        Stun.apply(target, STUN_TICKS);
        applySlow(target, DEBUFF_TICKS, 50.0);
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, DEBUFF_TICKS, 0, true, true));
    }

    private static class FallingBlockEntry {
        final FallingBlock block;
        final int spawnTick;

        FallingBlockEntry(FallingBlock block, int spawnTick) {
            this.block = block;
            this.spawnTick = spawnTick;
        }
    }
}
