package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

@AbilityManifest(name = "최상급 조준경 (ApexScope)", species = AbilityManifest.Species.SPECIAL, explain = {
        "§e§l[패시브 - 최상급 조준경]",
        "§7입히는 피해가 §c50% 감소§7합니다",
        "§7대신 공격 사거리가 §b50% 증가§7합니다"
}, summarize = {
        "§7패시브§f: 피해 -50%, 공격 사거리 +50%"
})
public class ApexScope extends AbilityBase {

    private static final double DAMAGE_MULTIPLIER = 0.5;
    private static final double RANGE_MULTIPLIER = 1.5;
    private double originalRange = -1.0;

    public ApexScope(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        applyRange();
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        restoreRange();
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (!(event instanceof EntityDamageByEntityEvent damageEvent)
                || (event instanceof Cancellable cancellable && cancellable.isCancelled())) {
            return;
        }
        Player player = getPlayer();
        if (player != null && damageEvent.getDamager().equals(player)) {
            scaleOutgoingDamage(damageEvent, DAMAGE_MULTIPLIER);
        }
    }

    private void applyRange() {
        Player player = getPlayer();
        AttributeInstance range = player != null ? player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE) : null;
        if (range == null || originalRange >= 0.0) {
            return;
        }
        originalRange = range.getBaseValue();
        range.setBaseValue(originalRange * RANGE_MULTIPLIER);
    }

    private void restoreRange() {
        Player player = getPlayer();
        AttributeInstance range = player != null ? player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE) : null;
        if (range != null && originalRange >= 0.0) {
            range.setBaseValue(originalRange);
        }
        originalRange = -1.0;
    }
}
