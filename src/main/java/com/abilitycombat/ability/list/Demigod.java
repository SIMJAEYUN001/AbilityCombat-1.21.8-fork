package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

@AbilityManifest(name = "데미갓 (Demigod)", rank = AbilityManifest.Rank.S, species = AbilityManifest.Species.DEMIGOD, explain = {
        "§e§l[패시브 - 신성의 가호]",
        "§7공격을 받으면 §f40%§7 확률로 §f5초§7간",
        "§7다음 효과 중 하나가 적용됩니다:",
        "",
        "§7- §e흡수§7: 추가 체력 획득",
        "§7- §a재생§7: 지속 체력 회복",
        "§7- §3저항§7: 받는 피해 감소",
        "§7- §c힘§7: 공격력 증가"
}, summarize = {
        "§7패시브§f: 40% 확률로 버프 (5초)"
})
public class Demigod extends AbilityBase {

    private static final int DURATION_TICKS = 100;

    public Demigod(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageEvent.class);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent) {
            onDamage((EntityDamageEvent) event);
        }
    }

    private void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (ThreadLocalRandom.current().nextInt(100) >= 40) {
            return;
        }
        int roll = ThreadLocalRandom.current().nextInt(4);
        PotionEffectType type = switch (roll) {
            case 0 -> PotionEffectType.ABSORPTION;
            case 1 -> PotionEffectType.REGENERATION;
            case 2 -> PotionEffectType.RESISTANCE;
            default -> PotionEffectType.STRENGTH;
        };
        player.addPotionEffect(new PotionEffect(type, DURATION_TICKS, 0, true, false));
    }
}
