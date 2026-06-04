package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@AbilityManifest(name = "정원사 (Gardener)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 좌클릭 - 개화]§f §8(쿨타임: 25초)",
        "§7주변 §f10칸§7 지면을 §a잔디 블록§7으로 바꾸고,",
        "§7범위 내 자신과 팀원에게 §e흡수 III§7를 §f6초§7간 부여합니다.",
        "§7이후 §f1.5초§7마다 잔디 §a10개§7, 민들레 §e10개§7, 양귀비 §c10개§7를",
        "§7순서대로 피워낸 뒤, 범위 내 적을 §e5초 기절§7시키고 §c15 피해§7를 입힙니다.",
        "",
        "§e§l[철괴 우클릭 - 생명의 나무]§f §8(쿨타임: 45초)",
        "§7바라보는 방향 §f앞 2칸§7에 파괴 가능한 §2나무 엔티티§7를 설치합니다. (체력: §f50§7)",
        "§7나무는 실제 무기 피해와 인챈트 피해를 반영해 피해를 받습니다.",
        "§7나무는 §f20초§7간 유지되며, 주변 §f5칸§7 내 자신과 팀원을",
        "§f0.5초§7마다 §a1 체력§7 회복시키고, 회복 범위를 이펙트로 표시합니다."
}, summarize = {
        "§7철괴 좌클릭§f: 10칸 잔디화 + 팀 흡수 III + 순차 개화 후 적 기절/15피해",
        "§7철괴 우클릭§f: 체력 50 참나무 설치, 실제 무기/인챈트 피해 반영",
        "§7생명의 나무§f: 주변 5칸 아군 0.5초마다 1 회복",
        "§7쿨타임§f: 개화 25초 / 생명의 나무 45초"
})
public class Gardener extends AbilityBase implements ActiveHandler {

    private static final int BLOOM_COOLDOWN_SECONDS = 25;
    private static final int TREE_COOLDOWN_SECONDS = 45;

    private static final double BLOOM_RADIUS = 10.0;
    private static final int ABSORPTION_TICKS = 6 * 20;
    private static final int ABSORPTION_AMPLIFIER = 2;
    private static final int BLOOM_WAVE_INTERVAL_TICKS = 30;
    private static final int BLOOM_WAVE_COUNT = 3;
    private static final int BLOOM_STUN_TICKS = 5 * 20;
    private static final double BLOOM_DAMAGE = 15.0;
    private static final int FLOWERS_PER_TYPE = 10;

    private static final double TREE_FORWARD_DISTANCE = 2.0;
    private static final double TREE_HEAL_RADIUS = 5.0;
    private static final double TREE_HEAL_AMOUNT = 1.0;
    private static final int TREE_HEAL_INTERVAL_TICKS = 10;
    private static final int TREE_DURATION_TICKS = 20 * 20;
    private static final double TREE_MAX_HEALTH = 50.0;
    private static final int TREE_DAMAGE_INVULNERABLE_TICKS = 15;
    private static final float TREE_TRUNK_RADIUS = 0.5f;
    private static final float TREE_HITBOX_EXTRA_RADIUS = 0.2f;
    private static final float TREE_HITBOX_WIDTH = (TREE_TRUNK_RADIUS + TREE_HITBOX_EXTRA_RADIUS) * 2.0f;
    private static final float TREE_HITBOX_HEIGHT = 3.2f;
    private static final int TREE_RANGE_EFFECT_INTERVAL_TICKS = 6;
    private static final int TREE_RANGE_PARTICLE_POINTS = 36;
    private static final double TREE_RANGE_PARTICLE_Y = 0.15;
    private static final double TREE_RANGE_INNER_RADIUS = TREE_HEAL_RADIUS - 0.35;
    private static final double TREE_RANGE_UPPER_PARTICLE_Y = 1.1;
    private static final Particle.DustOptions TREE_RANGE_DUST = new Particle.DustOptions(Color.fromRGB(90, 220, 120),
            1.25f);

    private final Cooldown cooldown = new Cooldown(TREE_COOLDOWN_SECONDS);

    private BloomField activeBloom;
    private TreeData activeTree;

    public Gardener(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageByEntityEvent.class);
        subscribeEvent(BlockBreakEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        clearTree();
        activeBloom = null;
    }

    @Override
    protected void onDestroy() {
        clearTree();
        activeBloom = null;
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }

        boolean activated = false;
        int cooldownSeconds = TREE_COOLDOWN_SECONDS;
        if (clickType == ClickType.LEFT_CLICK) {
            activated = activateBloom();
            cooldownSeconds = BLOOM_COOLDOWN_SECONDS;
        } else if (clickType == ClickType.RIGHT_CLICK) {
            activated = activateTree();
            cooldownSeconds = TREE_COOLDOWN_SECONDS;
        }

        if (!activated) {
            return false;
        }

        cooldown.start();
        cooldown.setCount(cooldownSeconds);
        applyIronCooldownIfEmpty(cooldownSeconds);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent damageByEntity) {
            onDamageByEntity(damageByEntity);
        } else if (event instanceof BlockBreakEvent blockBreakEvent) {
            onBlockBreak(blockBreakEvent);
        }
    }

    private boolean activateBloom() {
        Player player = getPlayer();
        World world = player.getWorld();
        Location center = player.getLocation().clone();
        List<Block> grassBlocks = collectGroundBlocks(center, BLOOM_RADIUS);
        if (grassBlocks.isEmpty()) {
            return false;
        }

        for (Block block : grassBlocks) {
            block.setType(Material.GRASS_BLOCK, false);
        }

        giveAbsorptionToAllies(center);

        ParticleUtil.spawnParticle(world, Particle.HAPPY_VILLAGER, center.clone().add(0, 1, 0),
                40, 1.8, 0.5, 1.8, 0.2, 1, 0);
        world.playSound(center, Sound.ITEM_BONE_MEAL_USE, 1.0f, 0.8f);

        activeBloom = new BloomField(center, grassBlocks);
        return true;
    }

    private boolean activateTree() {
        Player player = getPlayer();
        Location treeLocation = getTreeSpawnLocation(player);
        if (treeLocation == null || treeLocation.getWorld() == null) {
            return false;
        }

        clearTree();
        List<TreeBlockState> treeBlocks = placeTreeStructure(treeLocation);
        if (treeBlocks.isEmpty()) {
            return false;
        }
        ArmorStand stand = treeLocation.getWorld().spawn(treeLocation, ArmorStand.class, entity -> {
            entity.setGravity(false);
            entity.setVisible(false);
            entity.setMarker(false);
            entity.setBasePlate(false);
            entity.setArms(false);
            entity.setSmall(false);
            entity.setInvulnerable(false);
            entity.setCollidable(false);
            entity.customName(Component.text(buildTreeName(TREE_MAX_HEALTH)));
            entity.setCustomNameVisible(true);
        });
        AbilityCombat.markAbilityArmorStand(stand);
        Interaction hitbox = treeLocation.getWorld().spawn(treeLocation.clone().add(0.0, 1.0, 0.0), Interaction.class,
                entity -> {
                    entity.setInteractionWidth(TREE_HITBOX_WIDTH);
                    entity.setInteractionHeight(TREE_HITBOX_HEIGHT);
                    entity.setResponsive(true);
                });

        activeTree = new TreeData(stand, hitbox, treeBlocks, TREE_MAX_HEALTH, TREE_DURATION_TICKS);
        treeLocation.getWorld().playSound(treeLocation, Sound.ITEM_BONE_MEAL_USE, 1.0f, 0.7f);
        ParticleUtil.spawnParticle(treeLocation.getWorld(), Particle.CHERRY_LEAVES, treeLocation.clone().add(0, 1, 0),
                25, 0.5, 0.8, 0.5, 0.03, 1, 0);
        return true;
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || activeTree == null || activeTree.stand == null) {
            return;
        }
        Entity damaged = event.getEntity();
        if (!damaged.getUniqueId().equals(activeTree.stand.getUniqueId())
                && (activeTree.hitbox == null || !damaged.getUniqueId().equals(activeTree.hitbox.getUniqueId()))) {
            return;
        }

        double damage = getTreeDamage(event);
        event.setCancelled(true);
        if (activeTree.damageInvulnerableTicks > 0) {
            return;
        }
        activeTree.health = Math.max(0.0, activeTree.health - damage);
        activeTree.damageInvulnerableTicks = TREE_DAMAGE_INVULNERABLE_TICKS;
        activeTree.stand.customName(Component.text(buildTreeName(activeTree.health)));
        activeTree.stand.getWorld().playSound(activeTree.stand.getLocation(), Sound.BLOCK_WOOD_HIT, 0.9f, 0.9f);

        if (activeTree.health <= 0.0) {
            removeTreeWithEffect();
        }
    }

    private double getTreeDamage(EntityDamageByEntityEvent event) {
        return Math.max(1.0, getCalculatedFinalDamage(event));
    }

    private void onBlockBreak(BlockBreakEvent event) {
        if (activeTree == null) {
            return;
        }
        for (TreeBlockState blockState : activeTree.blocks) {
            if (blockState.block.equals(event.getBlock())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @Override
    public void onTick(int tick) {
        tickBloomField();
        tickTree(tick);
    }

    private void tickBloomField() {
        if (activeBloom == null) {
            return;
        }

        activeBloom.elapsedTicks++;
        if (activeBloom.elapsedTicks % BLOOM_WAVE_INTERVAL_TICKS == 0
                && activeBloom.completedWaves < BLOOM_WAVE_COUNT) {
            spawnFlowerWave(activeBloom);
            activeBloom.completedWaves++;
            if (activeBloom.completedWaves >= BLOOM_WAVE_COUNT) {
                detonateBloom(activeBloom);
                activeBloom = null;
            }
        }
    }

    private void tickTree(int tick) {
        if (activeTree == null || activeTree.stand == null) {
            return;
        }
        if (activeTree.stand.isDead() || !activeTree.stand.isValid()) {
            clearTree();
            return;
        }
        if (activeTree.hitbox != null && (!activeTree.hitbox.isValid() || activeTree.hitbox.isDead())) {
            clearTree();
            return;
        }

        activeTree.remainingTicks--;
        if (activeTree.remainingTicks <= 0) {
            removeTreeWithEffect();
            return;
        }
        if (activeTree.damageInvulnerableTicks > 0) {
            activeTree.damageInvulnerableTicks--;
        }

        if (tick % TREE_HEAL_INTERVAL_TICKS == 0) {
            healAroundTree();
        }
        if (tick % TREE_RANGE_EFFECT_INTERVAL_TICKS == 0) {
            Location center = activeTree.stand.getLocation().clone().add(0, 1.0, 0);
            ParticleUtil.spawnParticle(center.getWorld(), Particle.COMPOSTER, center,
                    6, 0.45, 0.8, 0.45, 0.02, 1, 0);
            spawnTreeRangeEffect();
        }
    }

    private void giveAbsorptionToAllies(Location center) {
        for (Player target : LocationUtil.getNearbyPlayers(center, BLOOM_RADIUS,
                player -> canSupportTarget(getPlayer(), player))) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
                    ABSORPTION_TICKS, ABSORPTION_AMPLIFIER, true, false, true));
        }
    }

    private void spawnFlowerWave(BloomField bloomField) {
        int waveIndex = bloomField.completedWaves;
        Material plantMaterial;
        Sound waveSound;
        float pitch;

        if (waveIndex == 0) {
            plantMaterial = Material.SHORT_GRASS;
            waveSound = Sound.BLOCK_GRASS_PLACE;
            pitch = 0.9f;
        } else if (waveIndex == 1) {
            plantMaterial = Material.DANDELION;
            waveSound = Sound.BLOCK_NOTE_BLOCK_BELL;
            pitch = 1.15f;
        } else {
            plantMaterial = Material.POPPY;
            waveSound = Sound.BLOCK_NOTE_BLOCK_CHIME;
            pitch = 1.35f;
        }

        placePlants(bloomField.grassBlocks, plantMaterial, FLOWERS_PER_TYPE);

        World world = bloomField.center.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(bloomField.center, waveSound, 1.0f, pitch);
        ParticleUtil.spawnParticle(world, Particle.HAPPY_VILLAGER, bloomField.center.clone().add(0, 1, 0),
                24, 1.7, 0.4, 1.7, 0.08, 1, 0);
    }

    private void detonateBloom(BloomField bloomField) {
        World world = bloomField.center.getWorld();
        if (world == null) {
            return;
        }

        ParticleUtil.spawnParticle(world, Particle.CHERRY_LEAVES, bloomField.center.clone().add(0, 1, 0),
                90, 3.4, 0.8, 3.4, 0.05, 1, 0);
        world.playSound(bloomField.center, Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f, 0.85f);
        world.playSound(bloomField.center, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.7f);

        for (Player target : LocationUtil.getNearbyPlayers(bloomField.center, BLOOM_RADIUS, getPlayer(),
                player -> !player.equals(getPlayer()))) {
            Stun.apply(target, BLOOM_STUN_TICKS);
            target.damage(BLOOM_DAMAGE, getPlayer());
        }
    }

    private void healAroundTree() {
        if (activeTree == null || activeTree.stand == null) {
            return;
        }
        Location center = activeTree.stand.getLocation();
        for (Player target : LocationUtil.getNearbyPlayers(center, TREE_HEAL_RADIUS,
                player -> canSupportTarget(getPlayer(), player))) {
            healTarget(target, TREE_HEAL_AMOUNT);
        }

        World world = center.getWorld();
        if (world != null) {
            world.playSound(center, Sound.BLOCK_MOSS_BREAK, 0.6f, 0.9f);
        }
    }

    private void spawnTreeRangeEffect() {
        if (activeTree == null || activeTree.stand == null) {
            return;
        }
        Location base = activeTree.stand.getLocation();
        World world = base.getWorld();
        if (world == null) {
            return;
        }

        double lowerY = base.getY() + TREE_RANGE_PARTICLE_Y;
        double upperY = base.getY() + TREE_RANGE_UPPER_PARTICLE_Y;
        for (int i = 0; i < TREE_RANGE_PARTICLE_POINTS; i++) {
            double angle = (Math.PI * 2.0 * i) / TREE_RANGE_PARTICLE_POINTS;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Location outerPoint = new Location(
                    world,
                    base.getX() + (cos * TREE_HEAL_RADIUS),
                    lowerY,
                    base.getZ() + (sin * TREE_HEAL_RADIUS));
            Location innerPoint = new Location(
                    world,
                    base.getX() + (cos * TREE_RANGE_INNER_RADIUS),
                    lowerY,
                    base.getZ() + (sin * TREE_RANGE_INNER_RADIUS));
            Location upperPoint = new Location(
                    world,
                    base.getX() + (cos * TREE_HEAL_RADIUS),
                    upperY,
                    base.getZ() + (sin * TREE_HEAL_RADIUS));
            ParticleUtil.spawnParticle(world, Particle.DUST, outerPoint,
                    2, 0.03, 0.03, 0.03, 0.0, TREE_RANGE_DUST, 1, 0);
            ParticleUtil.spawnParticle(world, Particle.DUST, innerPoint,
                    1, 0.02, 0.02, 0.02, 0.0, TREE_RANGE_DUST, 1, 0);
            if (i % 3 == 0) {
                ParticleUtil.spawnParticle(world, Particle.DUST, upperPoint,
                        1, 0.02, 0.02, 0.02, 0.0, TREE_RANGE_DUST, 1, 0);
            }
        }
        for (int i = 0; i < 4; i++) {
            double angle = (Math.PI / 2.0) * i;
            Location cardinalPoint = new Location(
                    world,
                    base.getX() + (Math.cos(angle) * TREE_HEAL_RADIUS),
                    base.getY() + 0.6,
                    base.getZ() + (Math.sin(angle) * TREE_HEAL_RADIUS));
            ParticleUtil.spawnParticle(world, Particle.HAPPY_VILLAGER, cardinalPoint,
                    2, 0.05, 0.15, 0.05, 0.0, 1, 0);
        }
    }

    private void healTarget(Player target, double amount) {
        if (target == null || amount <= 0.0 || target.isDead()) {
            return;
        }
        AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0;
        double healed = Math.min(max, target.getHealth() + amount);
        if (healed <= target.getHealth()) {
            return;
        }
        target.setHealth(healed);
        ParticleUtil.spawnParticle(target.getWorld(), Particle.HEART, target.getLocation().clone().add(0, 1, 0),
                3, 0.25, 0.3, 0.25, 0.01, 1, 0);
    }

    private boolean canSupportTarget(Player owner, Player target) {
        if (owner == null || target == null) {
            return false;
        }
        if (owner.equals(target)) {
            return true;
        }
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin != null
                && plugin.getGameManager() != null
                && plugin.getGameManager().areTeammates(owner, target);
    }

    private List<Block> collectGroundBlocks(Location center, double radius) {
        List<Block> blocks = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) {
            return blocks;
        }

        int baseX = center.getBlockX();
        int baseY = center.getBlockY();
        int baseZ = center.getBlockZ();
        int intRadius = (int) Math.ceil(radius);

        for (int x = -intRadius; x <= intRadius; x++) {
            for (int z = -intRadius; z <= intRadius; z++) {
                if ((x * x) + (z * z) > radius * radius) {
                    continue;
                }
                Block block = findSurfaceBlock(world, baseX + x, baseY, baseZ + z);
                if (block == null || blocks.contains(block)) {
                    continue;
                }
                blocks.add(block);
            }
        }
        return blocks;
    }

    private Block findSurfaceBlock(World world, int x, int baseY, int z) {
        for (int y = baseY + 2; y >= baseY - 4; y--) {
            Block floor = world.getBlockAt(x, y, z);
            Block above = floor.getRelative(0, 1, 0);
            if (!floor.getType().isAir() && floor.getType().isSolid() && canPlacePlant(above)) {
                return floor;
            }
        }
        return null;
    }

    private void placePlants(List<Block> grassBlocks, Material material, int amount) {
        List<Block> candidates = new ArrayList<>(grassBlocks);
        Collections.shuffle(candidates, ThreadLocalRandom.current());

        int placed = 0;
        for (Block block : candidates) {
            if (placed >= amount) {
                break;
            }
            Block above = block.getRelative(0, 1, 0);
            if (!canPlacePlant(above)) {
                continue;
            }
            above.setType(material, false);
            placed++;
        }
    }

    private boolean canPlacePlant(Block block) {
        Material type = block.getType();
        return type.isAir() || type == Material.SHORT_GRASS || type == Material.DANDELION || type == Material.POPPY;
    }

    private Location getTreeSpawnLocation(Player player) {
        Location origin = player.getLocation().clone();
        Vector forward = origin.getDirection().clone().setY(0);
        if (forward.lengthSquared() <= 0.0) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize().multiply(TREE_FORWARD_DISTANCE);
        Location target = origin.add(forward);
        World world = target.getWorld();
        if (world == null) {
            return null;
        }

        Block ground = findSurfaceBlock(world, target.getBlockX(), target.getBlockY(), target.getBlockZ());
        if (ground == null) {
            ground = target.getBlock().getRelative(0, -1, 0);
            if (ground == null || !ground.getType().isSolid()) {
                return null;
            }
        }
        return new Location(world,
                ground.getX() + 0.5,
                ground.getY() + 0.05,
                ground.getZ() + 0.5,
                player.getLocation().getYaw(),
                0.0f);
    }

    private void removeTreeWithEffect() {
        if (activeTree == null || activeTree.stand == null) {
            activeTree = null;
            return;
        }
        Location location = activeTree.stand.getLocation().clone();
        World world = location.getWorld();
        restoreTreeBlocks(activeTree);
        activeTree.stand.remove();
        if (activeTree.hitbox != null && activeTree.hitbox.isValid()) {
            activeTree.hitbox.remove();
        }
        activeTree = null;
        if (world != null) {
            world.playSound(location, Sound.BLOCK_WOOD_BREAK, 1.0f, 0.8f);
            ParticleUtil.spawnParticle(world, Particle.BLOCK, location.clone().add(0, 1, 0),
                    18, 0.35, 0.8, 0.35, 0.0, Material.OAK_LOG.createBlockData(), 1, 0);
        }
    }

    private void clearTree() {
        if (activeTree != null) {
            restoreTreeBlocks(activeTree);
            if (activeTree.stand != null && !activeTree.stand.isDead()) {
                activeTree.stand.remove();
            }
            if (activeTree.hitbox != null && activeTree.hitbox.isValid()) {
                activeTree.hitbox.remove();
            }
        }
        activeTree = null;
    }

    private List<TreeBlockState> placeTreeStructure(Location treeLocation) {
        World world = treeLocation.getWorld();
        if (world == null) {
            return List.of();
        }

        int baseX = treeLocation.getBlockX();
        int baseY = treeLocation.getBlockY() + 1;
        int baseZ = treeLocation.getBlockZ();
        List<TreeBlockTemplate> templates = List.of(
                new TreeBlockTemplate(0, 0, 0, Material.OAK_LOG),
                new TreeBlockTemplate(0, 1, 0, Material.OAK_LOG),
                new TreeBlockTemplate(0, 2, 0, Material.OAK_LOG),
                new TreeBlockTemplate(0, 3, 0, Material.OAK_LEAVES),
                new TreeBlockTemplate(1, 2, 0, Material.OAK_LEAVES),
                new TreeBlockTemplate(-1, 2, 0, Material.OAK_LEAVES),
                new TreeBlockTemplate(0, 2, 1, Material.OAK_LEAVES),
                new TreeBlockTemplate(0, 2, -1, Material.OAK_LEAVES),
                new TreeBlockTemplate(1, 2, 1, Material.OAK_LEAVES),
                new TreeBlockTemplate(1, 2, -1, Material.OAK_LEAVES),
                new TreeBlockTemplate(-1, 2, 1, Material.OAK_LEAVES),
                new TreeBlockTemplate(-1, 2, -1, Material.OAK_LEAVES),
                new TreeBlockTemplate(1, 3, 0, Material.OAK_LEAVES),
                new TreeBlockTemplate(-1, 3, 0, Material.OAK_LEAVES),
                new TreeBlockTemplate(0, 3, 1, Material.OAK_LEAVES),
                new TreeBlockTemplate(0, 3, -1, Material.OAK_LEAVES));

        List<TreeBlockState> placed = new ArrayList<>(templates.size());
        for (TreeBlockTemplate template : templates) {
            Block block = world.getBlockAt(baseX + template.offsetX, baseY + template.offsetY, baseZ + template.offsetZ);
            if (!canReplaceTreeBlock(block)) {
                return List.of();
            }
            placed.add(new TreeBlockState(block, block.getBlockData().clone(), template.material));
        }

        for (TreeBlockState blockState : placed) {
            blockState.block.setBlockData(createTreeBlockData(blockState.material), false);
        }
        return placed;
    }

    private BlockData createTreeBlockData(Material material) {
        BlockData blockData = material.createBlockData();
        if (blockData instanceof Leaves leaves) {
            leaves.setPersistent(true);
        }
        return blockData;
    }

    private boolean canReplaceTreeBlock(Block block) {
        Material type = block.getType();
        return type.isAir() || (block.isPassable() && !block.isLiquid());
    }

    private void restoreTreeBlocks(TreeData treeData) {
        if (treeData == null) {
            return;
        }
        for (TreeBlockState blockState : treeData.blocks) {
            blockState.block.setBlockData(blockState.originalData, false);
        }
    }

    private String buildTreeName(double health) {
        return "§2생명의 나무 §7[" + String.format(Locale.US, "%.1f", health) + " / " + TREE_MAX_HEALTH + "]";
    }

    private static final class BloomField {
        private final Location center;
        private final List<Block> grassBlocks;
        private int elapsedTicks;
        private int completedWaves;

        private BloomField(Location center, List<Block> grassBlocks) {
            this.center = center;
            this.grassBlocks = grassBlocks;
        }
    }

    private static final class TreeData {
        private final ArmorStand stand;
        private final Interaction hitbox;
        private final List<TreeBlockState> blocks;
        private double health;
        private int remainingTicks;
        private int damageInvulnerableTicks;

        private TreeData(ArmorStand stand, Interaction hitbox, List<TreeBlockState> blocks, double health,
                int remainingTicks) {
            this.stand = stand;
            this.hitbox = hitbox;
            this.blocks = blocks;
            this.health = health;
            this.remainingTicks = remainingTicks;
            this.damageInvulnerableTicks = 0;
        }
    }

    private static final class TreeBlockTemplate {
        private final int offsetX;
        private final int offsetY;
        private final int offsetZ;
        private final Material material;

        private TreeBlockTemplate(int offsetX, int offsetY, int offsetZ, Material material) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.material = material;
        }
    }

    private static final class TreeBlockState {
        private final Block block;
        private final BlockData originalData;
        private final Material material;

        private TreeBlockState(Block block, BlockData originalData, Material material) {
            this.block = block;
            this.originalData = originalData;
            this.material = material;
        }
    }
}
