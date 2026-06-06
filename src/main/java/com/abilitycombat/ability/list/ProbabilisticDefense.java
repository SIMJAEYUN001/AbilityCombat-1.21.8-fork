package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.concurrent.ThreadLocalRandom;

@AbilityManifest(name = "확률적 방어 (ProbabilisticDefense)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 확률적 방어]",
        "§7피해를 받을 때 §f50% 확률§7로 피해를 §b45% 감소§7시킵니다"
}, summarize = {
        "§7패시브§f: 피격 시 50% 확률로 피해 45% 감소"
})
public class ProbabilisticDefense extends AbilityBase {

    private static final double CHANCE = 0.5;
    private static final double DAMAGE_MULTIPLIER = 0.55;

    public ProbabilisticDefense(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageEvent.class);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (!(event instanceof EntityDamageEvent damageEvent) || damageEvent.isCancelled()) {
            return;
        }
        Player player = getPlayer();
        if (player == null || !damageEvent.getEntity().equals(player)) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= CHANCE) {
            return;
        }
        modifyDamage(damageEvent, INCOMING_DAMAGE, (DAMAGE_MULTIPLIER - 1.0) * 100.0, 0.0);
        player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.55f, 1.45f);
    }
}
