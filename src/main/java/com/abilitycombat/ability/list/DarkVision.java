package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@AbilityManifest(name = "심안 (DarkVision)", rank = AbilityManifest.Rank.B, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 심안]",
        "§7영구적으로 §8실명§7 상태가 되지만,",
        "§7§b신속 III§7 효과를 항상 받습니다.",
        "",
        "§7주변 §f30칸§7 이내의 모든 생명체에게",
        "§e발광§7 효과가 적용되어 위치를 파악할 수 있습니다."
}, summarize = {
        "§7패시브§f: 실명 + 신속 III + 적 발광"
})
public class DarkVision extends AbilityBase {

    private static final double RANGE = 30.0;

    public DarkVision(Participant participant) {
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
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            Player player = getPlayer();
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2, true, false));
            for (LivingEntity entity : com.abilitycombat.utils.LocationUtil.getNearbyLivingEntities(
                    player.getLocation(), RANGE, com.abilitycombat.utils.LocationUtil
                            .withValidTarget(e -> !e.equals(player) && !(e instanceof ArmorStand)))) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, true, false));
            }
        }
    }
}
