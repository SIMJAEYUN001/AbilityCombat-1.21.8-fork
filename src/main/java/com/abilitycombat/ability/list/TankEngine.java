package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "탱크 엔진 (TankEngine)", species = AbilityManifest.Species.OTHERS, explain = {
        "§e§l[패시브 - 장갑 증설]",
        "§7피해를 입힌 적이 §f10초§7 안에 사망하면 처치 관여로 인정됩니다",
        "§7처치 관여 시 최대 체력이 §a4§7 증가합니다"
}, summarize = {
        "§7패시브§f: 10초 내 처치 관여 시 최대 체력 +4"
})
public class TankEngine extends AbilityBase {

    private static final int ASSIST_WINDOW_TICKS = 200;
    private static final double MAX_HEALTH_GAIN = 4.0;

    private final Map<UUID, Integer> damagedTargets = new HashMap<>();

    public TankEngine(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
        subscribeEvent(PlayerDeathEvent.class);
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        damagedTargets.clear();
        unregisterTick();
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent damageEvent) {
            onDamage(damageEvent);
        } else if (event instanceof PlayerDeathEvent deathEvent) {
            onDeath(deathEvent);
        }
    }

    private void onDamage(EntityDamageByEntityEvent event) {
        if ((event instanceof Cancellable cancellable && cancellable.isCancelled())
                || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Player player = getPlayer();
        if (player == null || !event.getDamager().equals(player) || !LocationUtil.isValidTarget(player, target)) {
            return;
        }
        damagedTargets.put(target.getUniqueId(), AbilityTickManager.getGlobalTick() + ASSIST_WINDOW_TICKS);
    }

    private void onDeath(PlayerDeathEvent event) {
        if (damagedTargets.remove(event.getEntity().getUniqueId()) == null) {
            return;
        }
        Player player = getPlayer();
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        double nextMax = maxHealth.getBaseValue() + MAX_HEALTH_GAIN;
        maxHealth.setBaseValue(nextMax);
        player.setHealth(Math.min(nextMax, player.getHealth() + MAX_HEALTH_GAIN));
    }

    @Override
    public void onTick(int tick) {
        Iterator<Map.Entry<UUID, Integer>> iterator = damagedTargets.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= tick) {
                iterator.remove();
            }
        }
    }
}
