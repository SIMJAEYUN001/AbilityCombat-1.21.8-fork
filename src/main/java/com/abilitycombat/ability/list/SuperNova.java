package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.PlayerDeathEvent;

@AbilityManifest(name = "초신성 (SuperNova)", species = AbilityManifest.Species.OTHERS, explain = {
        "§e§l[패시브 - 최후의 섬광]",
        "§7사망 시 강력한 §c대폭발§7을 일으킵니다",
        "",
        "§7폭발 범위 §f6칸§7 내의 적들에게",
        "§c최대 체력의 50%§7에 해당하는 피해를 입힙니다"
}, summarize = {
        "§7패시브§f: 사망 시 대폭발 (최대체력 50% 피해)"
})
public class SuperNova extends AbilityBase {

    private static final float EXPLOSION_POWER = 4.0f;
    private static final double RANGE = 6.0;

    public SuperNova(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(PlayerDeathEvent.class);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof PlayerDeathEvent) {
            onDeath((PlayerDeathEvent) event);
        }
    }

    private void onDeath(PlayerDeathEvent event) {
        if (!event.getEntity().equals(getPlayer())) {
            return;
        }
        Player player = getPlayer();
        player.getWorld().createExplosion(player.getLocation(), EXPLOSION_POWER, false, false, player);
        for (LivingEntity entity : player.getWorld().getLivingEntities()) {
            if (entity.equals(player)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(player.getLocation()) <= RANGE * RANGE) {
                if (com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), entity)) {
                    double maxHealth = entity.getAttribute(Attribute.MAX_HEALTH).getValue();
                    entity.damage(maxHealth * 0.5, player);
                }
            }
        }
    }
}
