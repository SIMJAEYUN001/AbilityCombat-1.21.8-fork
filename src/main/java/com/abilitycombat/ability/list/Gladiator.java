package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.ability.handler.TargetHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "글래디에이터 (Gladiator)", rank = AbilityManifest.Rank.S, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 결투장]§f §8(쿨타임: 60초)",
        "§7대상을 우클릭하여 §e1:1 결투장§7을 생성합니다.",
        "§7결투장은 §f20초§7간 지속됩니다.",
        "",
        "§7공중에 결투장이 생성되며 두 플레이어가",
        "§7텔레포트됩니다. 결투 종료 시 원래 위치로 복귀합니다.",
        "",
        "§7결투장 생성 시 §e흡수 II§7와 §3저항 I§7 효과를 얻습니다.",
        "",
        "§7결투 중에는 당사자 외의 플레이어가",
        "§7결투 참가자에게 피해를 줄 수 없습니다."
}, summarize = {
        "§7철괴 우클릭§f: 1:1 결투 (20초)"
})
public class Gladiator extends AbilityBase implements TargetHandler, ActiveHandler {

    private static final int COOLDOWN_SECONDS = 60;
    private static final int DUEL_SECONDS = 20;
    private static final double LOOK_RANGE = 10.0;
    private static final int ARENA_RADIUS = 8;
    private static final int ARENA_HEIGHT = 7;
    private static final int ARENA_Y = 200; // 공중에 결투장 생성
    private static final Material ARENA_FLOOR = Material.STONE_BRICKS;
    private static final Material ARENA_WALL = Material.BARRIER; // 탈출 불가

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private int remainingDuelSeconds = 0;
    private final Map<Block, BlockState> arenaSnapshots = new HashMap<>();
    private UUID duelTarget;
    private Location arenaCenter;
    private Location ownerOriginalLocation;
    private Location targetOriginalLocation;

    public Gladiator(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageByEntityEvent.class);
        subscribeEvent(BlockBreakEvent.class);
        subscribeEvent(EntityExplodeEvent.class);
        subscribeEvent(BlockExplodeEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        endDuel();
    }

    @Override
    protected void onDestroy() {
        endDuel();
    }

    @Override
    public boolean activeSkill(Material material, ActiveHandler.ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ActiveHandler.ClickType.RIGHT_CLICK) {
            return false;
        }
        LivingEntity target = LocationUtil.getEntityLookingAt(LivingEntity.class, getPlayer(), LOOK_RANGE,
                LocationUtil.withValidTarget(entity -> !entity.equals(getPlayer())));
        return startDuel(target);
    }

    @Override
    public void targetSkill(Material material, LivingEntity target) {
        if (material != Material.IRON_INGOT) {
            return;
        }
        startDuel(target);
    }

    private boolean startDuel(LivingEntity target) {
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        if (isDuelActive()) {
            return false;
        }
        if (!(target instanceof Player targetPlayer) || target.equals(getPlayer())) {
            return false;
        }
        Player player = getPlayer();
        if (!player.getWorld().equals(targetPlayer.getWorld())) {
            return false;
        }

        // 원래 위치 저장
        ownerOriginalLocation = player.getLocation().clone();
        targetOriginalLocation = targetPlayer.getLocation().clone();

        duelTarget = targetPlayer.getUniqueId();
        arenaCenter = buildArenaCenter(player);
        buildArena(arenaCenter);
        teleportDuelists(player, targetPlayer, arenaCenter);
        startDuelTimer();
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, DUEL_SECONDS * 20, 1, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, DUEL_SECONDS * 20, 0, true, false));
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    private Location buildArenaCenter(Player player) {
        World world = player.getWorld();
        Location borderCenter = world.getWorldBorder().getCenter();
        // 자기장(월드 보더) 중심 상공에 결투장 생성
        return new Location(world, borderCenter.getX(), ARENA_Y, borderCenter.getZ());
    }

    private void teleportDuelists(Player player, Player target, Location center) {
        if (center == null) {
            return;
        }
        // 결투장 바닥 위로 텔레포트
        Location playerSpawn = center.clone().add(2, 1, 0);
        Location targetSpawn = center.clone().add(-2, 1, 0);
        playerSpawn.setYaw(90); // 서로를 바라보도록
        targetSpawn.setYaw(-90);
        player.teleport(playerSpawn);
        target.teleport(targetSpawn);
    }

    private void buildArena(Location center) {
        if (center == null) {
            return;
        }
        arenaSnapshots.clear();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int baseX = center.getBlockX();
        int baseY = center.getBlockY();
        int baseZ = center.getBlockZ();
        int radiusSq = ARENA_RADIUS * ARENA_RADIUS;
        int innerSq = (ARENA_RADIUS - 1) * (ARENA_RADIUS - 1);

        // 바닥
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                int distSq = x * x + z * z;
                if (distSq <= radiusSq) {
                    setArenaBlock(world.getBlockAt(baseX + x, baseY, baseZ + z), ARENA_FLOOR);
                }
            }
        }

        // 벽 (배리어 블록)
        for (int y = 1; y <= ARENA_HEIGHT; y++) {
            for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
                for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                    int distSq = x * x + z * z;
                    if (distSq <= radiusSq && distSq >= innerSq) {
                        setArenaBlock(world.getBlockAt(baseX + x, baseY + y, baseZ + z), ARENA_WALL);
                    }
                }
            }
        }

        // 천장
        int roofY = baseY + ARENA_HEIGHT + 1;
        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                int distSq = x * x + z * z;
                if (distSq <= radiusSq) {
                    setArenaBlock(world.getBlockAt(baseX + x, roofY, baseZ + z), ARENA_FLOOR);
                }
            }
        }
    }

    private void setArenaBlock(Block block, Material material) {
        arenaSnapshots.putIfAbsent(block, block.getState());
        if (block.getType() != material) {
            block.setType(material, false);
        }
    }

    private void restoreArena() {
        for (BlockState state : arenaSnapshots.values()) {
            state.update(true, false);
        }
        arenaSnapshots.clear();
    }

    private void returnPlayers() {
        Player owner = getPlayer();
        if (owner != null && ownerOriginalLocation != null) {
            owner.teleport(ownerOriginalLocation);
        }
        if (duelTarget != null) {
            Player target = Bukkit.getPlayer(duelTarget);
            if (target != null && targetOriginalLocation != null) {
                target.teleport(targetOriginalLocation);
            }
        }
        ownerOriginalLocation = null;
        targetOriginalLocation = null;
    }

    private void endDuel() {
        returnPlayers();
        restoreArena();
        duelTarget = null;
        arenaCenter = null;
        remainingDuelSeconds = 0;
    }

    private void startDuelTimer() {
        remainingDuelSeconds = DUEL_SECONDS;
        registerTick();
    }

    private boolean isDuelActive() {
        return remainingDuelSeconds > 0;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            if (isDuelActive()) {
                remainingDuelSeconds--;
                if (remainingDuelSeconds <= 0) {
                    endDuel();
                }
            }
        }
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        } else if (event instanceof BlockBreakEvent) {
            onBlockBreak((BlockBreakEvent) event);
        } else if (event instanceof BlockExplodeEvent) {
            onBlockExplode((BlockExplodeEvent) event);
        } else if (event instanceof EntityExplodeEvent) {
            onEntityExplode((EntityExplodeEvent) event);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!isDuelActive()) {
            return;
        }
        UUID ownerId = getPlayer().getUniqueId();
        UUID targetId = duelTarget;
        if (targetId == null) {
            return;
        }
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        boolean damagerIsDuelist = damager instanceof Player
                && (damager.getUniqueId().equals(ownerId) || damager.getUniqueId().equals(targetId));
        boolean victimIsDuelist = victim instanceof Player
                && (victim.getUniqueId().equals(ownerId) || victim.getUniqueId().equals(targetId));
        if (damagerIsDuelist && victimIsDuelist) {
            return;
        }
        if (damagerIsDuelist || victimIsDuelist) {
            event.setCancelled(true);
        }
    }

    private void onBlockBreak(BlockBreakEvent event) {
        if (arenaSnapshots.containsKey(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(arenaSnapshots::containsKey);
    }

    private void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(arenaSnapshots::containsKey);
    }

}
