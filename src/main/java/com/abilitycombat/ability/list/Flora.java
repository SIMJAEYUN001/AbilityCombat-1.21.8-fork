package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@AbilityManifest(name = "플로라 (Flora)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.GOD, explain = {
        "§e§l[패시브 - 자연의 축복]",
        "§7주변 플레이어에게 지속적으로 버프를 부여합니다.",
        "",
        "§e§l[철괴 좌클릭 - 범위 변경]§f §8(쿨타임: 3초)",
        "§7버프 범위를 §f5칸 / 10칸 / 15칸§7 중 순환합니다.",
        "",
        "§e§l[철괴 우클릭 - 효과 변경]§f §8(쿨타임: 3초)",
        "§7버프 효과를 §b신속§7 또는 §a재생§7으로 변경합니다."
}, summarize = {
        "§7패시브§f: 주변 버프 (신속/재생)",
        "§7철괴§f: 범위/효과 변경"
})
public class Flora extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 3;
    private static final int[] RANGES = { 5, 10, 15 };

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private int rangeIndex = 1;
    private PotionEffectType effectType = PotionEffectType.SPEED;

    public Flora(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
    }

    @Override
    public boolean activeSkill(Material material, ActiveHandler.ClickType clickType) {
        if (material != Material.IRON_INGOT) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        if (clickType == ActiveHandler.ClickType.LEFT_CLICK) {
            rangeIndex = (rangeIndex + 1) % RANGES.length;
            getPlayer().sendMessage("§a플로라 범위: §f" + RANGES[rangeIndex]);
            cooldown.start();
            applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
            return true;
        }
        if (clickType == ActiveHandler.ClickType.RIGHT_CLICK) {
            effectType = (effectType == PotionEffectType.SPEED) ? PotionEffectType.REGENERATION
                    : PotionEffectType.SPEED;
            getPlayer().sendMessage("§a플로라 효과: §f" + (effectType == PotionEffectType.SPEED ? "신속" : "재생"));
            cooldown.start();
            applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
            return true;
        }
        return false;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            Player owner = getPlayer();
            int range = RANGES[rangeIndex];
            for (Player player : owner.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(owner.getLocation()) <= range * range) {
                    player.addPotionEffect(new PotionEffect(effectType, 40, 0, true, false));
                }
            }
        }
    }
}
