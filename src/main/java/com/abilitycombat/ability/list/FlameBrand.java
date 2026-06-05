package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.effect.SharedBurn;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

@AbilityManifest(name = "화염 낙인 (FlameBrand)", species = AbilityManifest.Species.SPECIAL, explain = {
        "§e§l[패시브 - 화염 낙인]",
        "§7공격 적중 시 대상에게 §c화상 1스택§7을 부여합니다.",
        "§7화상은 공유 스택이며 스택당 매초 §c대상 최대 체력의 5% 피해§7를 줍니다.",
        "§7대상이 물에 닿거나 불이 꺼지면 화상 스택이 초기화됩니다."
}, summarize = {
        "§7패시브§f: 공격 시 화상 1스택",
        "§7화상§f: 스택당 매초 최대 체력 5% 피해"
})
public class FlameBrand extends AbilityBase {

    private static final int FIRE_TICKS = 80;

    public FlameBrand(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (!(event instanceof EntityDamageByEntityEvent damageEvent)
                || (event instanceof Cancellable cancellable && cancellable.isCancelled())) {
            return;
        }
        Player player = getPlayer();
        if (player == null || !damageEvent.getDamager().equals(player)
                || !(damageEvent.getEntity() instanceof LivingEntity target)
                || !LocationUtil.isValidTarget(player, target)) {
            return;
        }
        SharedBurn.addStack(target, player, SharedBurn.BurnProfile.FLAME_BRAND, FIRE_TICKS);
    }
}
