package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.Event;

@AbilityManifest(name = "예티 (Yeti)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 설원의 지배자]",
        "§7모든 종류의 눈/얼음 블록 위에 있으면 다음 버프를 획득합니다:",
        "§7- §b신속 I§7, §c힘 I§7, §3저항 I§7",
        "",
        "§e§l[철괴 우클릭 - 동토]§f §8(쿨타임: 40초)",
        "§7주변 §f15칸§7 반경의 지형을 §b꽁꽁언 얼음§7으로 변환합니다"
}, summarize = {
        "§7패시브§f: 모든 눈/얼음 위 버프",
        "§7철괴 우클릭§f: 지형 얼음화 (15칸, 꽁꽁언 얼음)"
})
public class Yeti extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 40;
    private static final int RANGE = 15;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);

    public Yeti(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
    }

    @Override
    public void handleBridgeEvent(Event event) {
        // No events used
    }

    @Override
    protected void onDeactivate() {
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
        convertTerrain(getPlayer().getLocation());
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    private void convertTerrain(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int baseX = center.getBlockX();
        int baseY = center.getBlockY();
        int baseZ = center.getBlockZ();

        for (int x = -RANGE; x <= RANGE; x++) {
            for (int z = -RANGE; z <= RANGE; z++) {
                if (x * x + z * z > RANGE * RANGE) {
                    continue;
                }
                int bx = baseX + x;
                int bz = baseZ + z;

                // 플레이어 높이 근처에서 표면을 찾음
                for (int y = baseY + 5; y >= baseY - 10; y--) {
                    Block block = world.getBlockAt(bx, y, bz);
                    Block above = block.getRelative(BlockFace.UP);

                    // 고체 블럭 위에 비고체 블럭이 있으면 표면
                    if (block.getType().isSolid() && !above.getType().isSolid()) {
                        // 표면 블럭을 직접 얼음으로 변환
                        if (isConvertibleToIce(block.getType())) {
                            // applyPhysics=false로 설정하여 강제 변환
                            block.setType(Material.PACKED_ICE, false);
                        }
                        break;
                    }
                }
            }
        }
    }

    private boolean isConvertibleToIce(Material type) {
        // 이미 꽁꽁언 얼음 계열이면 변환 불필요 (일반 얼음은 꽁꽁언 얼음으로 업그레이드 가능하도록 제외)
        if (type == Material.PACKED_ICE || type == Material.BLUE_ICE
                || type == Material.FROSTED_ICE || type == Material.SNOW_BLOCK) {
            return false;
        }
        // 특수 블럭 제외 (변환하면 안 되는 블럭들)
        if (type == Material.BEDROCK || type == Material.BARRIER
                || type == Material.COMMAND_BLOCK || type == Material.CHAIN_COMMAND_BLOCK
                || type == Material.REPEATING_COMMAND_BLOCK || type == Material.STRUCTURE_BLOCK
                || type == Material.JIGSAW || type == Material.END_PORTAL_FRAME
                || type == Material.SPAWNER || type == Material.CHEST || type == Material.ENDER_CHEST
                || type == Material.TRAPPED_CHEST || type == Material.SHULKER_BOX) {
            return false;
        }
        // 모든 고체 블럭 변환 가능 (테라코타 포함)
        return type.isSolid();
    }

    private boolean isSnowOrIce(Block block) {
        Material type = block.getType();
        return type == Material.SNOW
                || type == Material.SNOW_BLOCK
                || type == Material.ICE
                || type == Material.PACKED_ICE
                || type == Material.BLUE_ICE
                || type == Material.FROSTED_ICE
                || type == Material.POWDER_SNOW;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            Player player = getPlayer();
            Block under = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
            if (!isSnowOrIce(under)) {
                return;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, true, false));
        }
    }
}
