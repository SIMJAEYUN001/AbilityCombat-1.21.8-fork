package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@AbilityManifest(name = "구속 (Imprison)", rank = AbilityManifest.Rank.B, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 강도]",
        "§f15초§7마다 유리구의 강도가 §f1§7씩 증가합니다. (최대 §f4§7)",
        "§7사용 후 강도가 초기화됩니다.",
        "",
        "§e§l[철괴 좌클릭 - 감금]§f §8(쿨타임: 30초)",
        "§7바라보는 대상(§f8칸§7 이내)을 유리구 안에 가둡니다.",
        "§7유리구는 §f6초§7간 지속되며 대상은 §8구속1§7에 §f8초§7간 걸립니다.",
        "",
        "§e§l[철괴 우클릭 - 자가 보호]§f §8(쿨타임: 30초)",
        "§7자신을 보호하는 유리구를 생성하고",
        "§f체력 4를 즉시 회복§7하고 자신을 보호합니다.",
        "",
        "§7유리구는 강도만큼 공격받으면 깨집니다."
}, summarize = {
        "§7철괴 좌클릭§f: 바라보는 대상 감금 (8칸)",
        "§7철괴 우클릭§f: 자신 보호 + 체력 4 즉시회복"
})
public class Imprison extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 30;
    private static final int DURATION_SECONDS = 6;
    private static final int STRENGTH_INTERVAL = 15;
    private static final int MAX_STRENGTH = 4;
    private static final int RADIUS = 3;
    private static final double TARGET_RANGE = 8.0;
    private static final double HEAL_AMOUNT = 4.0;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private int remainingCageSeconds = 0;
    private int strength = 1;
    private int remainingHits = 1;
    private final Set<Block> cageBlocks = new HashSet<>();

    public Imprison(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(BlockBreakEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        removeCage();
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

        if (clickType == ClickType.RIGHT_CLICK) {
            createCage(getPlayer().getLocation());
            healSelf();
            cooldown.start();
            applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
            return true;
        } else if (clickType == ClickType.LEFT_CLICK) {
            Player target = LocationUtil.getEntityLookingAt(Player.class, getPlayer(), TARGET_RANGE,
                    entity -> !entity.equals(getPlayer()) && LocationUtil.isValidTarget(entity));

            if (target == null) {
                return false;
            }

            createCage(target.getLocation());
            // 감금된 적에게 구속 1 효과 8초 부여
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 8 * 20, 0));
            cooldown.start();
            applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
            return true;
        }
        return false;
    }

    private void healSelf() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        player.setHealth(Math.min(maxHealth.getValue(), player.getHealth() + HEAL_AMOUNT));
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof BlockBreakEvent) {
            onBlockBreak((BlockBreakEvent) event);
        }
    }

    private void onBlockBreak(BlockBreakEvent event) {
        if (!cageBlocks.contains(event.getBlock())) {
            return;
        }
        event.setCancelled(true);
        remainingHits--;
        if (remainingHits <= 0) {
            removeCage();
        }
    }

    @Override
    protected void onDestroy() {
        removeCage();
    }

    private void createCage(Location center) {
        removeCage();
        remainingHits = strength;
        strength = 1;
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -RADIUS; y <= RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    int distance = x * x + y * y + z * z;
                    if (distance > RADIUS * RADIUS || distance < (RADIUS - 1) * (RADIUS - 1)) {
                        continue;
                    }
                    Block block = world.getBlockAt(cx + x, cy + y, cz + z);
                    if (!isReplaceable(block)) {
                        continue;
                    }
                    block.setType(Material.GLASS);
                    cageBlocks.add(block);
                }
            }
        }
        startCage();
    }

    private void removeCage() {
        for (Block block : cageBlocks) {
            if (block.getType() == Material.GLASS) {
                block.setType(Material.AIR);
            }
        }
        cageBlocks.clear();
    }

    private boolean isReplaceable(Block block) {
        Material type = block.getType();
        return type.isAir() || type == Material.SHORT_GRASS || type == Material.TALL_GRASS ||
                type == Material.FERN || type == Material.LARGE_FERN ||
                type == Material.SNOW || type.name().contains("FLOWER") ||
                type == Material.DEAD_BUSH || type == Material.VINE;
    }

    private void startCage() {
        remainingCageSeconds = DURATION_SECONDS;
        registerTick();
    }

    private void stopCage() {
        removeCage();
        remainingCageSeconds = 0;
    }

    private boolean isCageActive() {
        return remainingCageSeconds > 0;
    }

    @Override
    public void onTick(int tick) {
        // Strength increases every 15 seconds
        if (tick % (STRENGTH_INTERVAL * 20) == 0) {
            if (strength < MAX_STRENGTH) {
                strength++;
            }
        }

        // Cage duration management
        if (tick % 20 == 0) {
            if (isCageActive()) {
                remainingCageSeconds--;
                if (remainingCageSeconds <= 0) {
                    stopCage();
                }
            }
        }
    }
}
