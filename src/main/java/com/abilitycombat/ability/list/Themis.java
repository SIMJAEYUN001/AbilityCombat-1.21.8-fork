package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "테미스 (Themis)", species = AbilityManifest.Species.GOD, explain = {
        "§e§l[패시브 - 단죄]",
        "§7정의의 여신 테미스는 살인자를 용서하지 않습니다",
        "",
        "§7적이 §c플레이어를 처치한 수§7(죄 스택)에 비례하여",
        "§7추가 피해를 입힙니다",
        "",
        "§7추가 피해: 처치당 §c+2§7 (최대 §c+16§7)"
}, summarize = {
        "§7패시브§f: 살인자에게 추가 피해"
})
public class Themis extends AbilityBase {

    private static final double DAMAGE_PER_KILL = 2.0;
    private static final double MAX_BONUS = 16.0;

    private final Map<UUID, Integer> killCounts = new HashMap<>();

    public Themis(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(PlayerDeathEvent.class);
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof PlayerDeathEvent) {
            onPlayerDeath((PlayerDeathEvent) event);
        } else if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        }
    }

    private void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            killCounts.merge(killer.getUniqueId(), 1, (a, b) -> a + b);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        int kills = killCounts.getOrDefault(target.getUniqueId(), 0);
        if (kills <= 0) {
            return;
        }
        double bonus = Math.min(MAX_BONUS, kills * DAMAGE_PER_KILL);
        addOutgoingDamage(event, bonus);
    }
}
