package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

@AbilityManifest(name = "이라 (Ira)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 분노]",
        "§f3번§7 공격당할 때마다 §c자신의 위치§7에",
        "§c폭발§7을 일으킵니다. (폭발력: §f2.5§7)",
        "",
        "§7자신도 폭발 피해를 입을 수 있습니다."
}, summarize = {
        "§7패시브§f: 3회 피격마다 폭발 (파워 2.5)"
})
public class Ira extends AbilityBase {

    private static final int TRIGGER_COUNT = 3;
    private static final float EXPLOSION_POWER = 2.5f;

    private int hitCount;

    public Ira(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamage((EntityDamageByEntityEvent) event);
        }
    }

    private void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (!(event.getDamager() instanceof LivingEntity)) {
            return;
        }
        hitCount++;
        if (hitCount >= TRIGGER_COUNT) {
            hitCount = 0;
            Location location = player.getLocation();
            location.getWorld().createExplosion(location, EXPLOSION_POWER, false, false, player);
        }
    }
}
