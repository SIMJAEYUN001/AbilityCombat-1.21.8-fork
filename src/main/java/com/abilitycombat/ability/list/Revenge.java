package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.GameManager;
import com.abilitycombat.game.MatchMode;
import com.abilitycombat.game.Participant;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

@AbilityManifest(name = "복수 (Revenge)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 복수]",
        "§72인전 전용 능력입니다",
        "§7팀원이 사망하면 영구적으로 §c주는 피해 +20%§7를 얻습니다",
        "§7이후 입힌 피해의 §a15%§7만큼 체력을 회복합니다"
}, summarize = {
        "§72인전 전용§f: 팀원 사망 후 피해 +20%",
        "§7복수 상태§f: 입힌 피해 15% 회복"
})
public class Revenge extends AbilityBase {

    private static final double DAMAGE_MULTIPLIER = 1.2;
    private static final double HEAL_RATIO = 0.15;

    private boolean empowered;

    public Revenge(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(PlayerDeathEvent.class);
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof PlayerDeathEvent deathEvent) {
            onPlayerDeath(deathEvent);
        } else if (event instanceof EntityDamageByEntityEvent damageEvent) {
            onDamage(damageEvent);
        }
    }

    private void onPlayerDeath(PlayerDeathEvent event) {
        Player player = getPlayer();
        GameManager gameManager = getGameManager();
        if (empowered || player == null || gameManager == null || gameManager.getSelectedMatchMode() != MatchMode.DUO) {
            return;
        }
        Player dead = event.getEntity();
        if (!dead.equals(player) && gameManager.areTeammates(player, dead)) {
            empowered = true;
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.45f, 1.4f);
            player.sendMessage("§c복수가 시작되었습니다");
        }
    }

    private void onDamage(EntityDamageByEntityEvent event) {
        if (!empowered || (event instanceof Cancellable cancellable && cancellable.isCancelled())) {
            return;
        }
        Player player = getPlayer();
        if (player == null || !event.getDamager().equals(player)) {
            return;
        }
        scaleOutgoingDamage(event, DAMAGE_MULTIPLIER);
        double heal = getCalculatedFinalDamage(event) * HEAL_RATIO;
        if (heal > 0.0) {
            var maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                player.setHealth(Math.min(maxHealth.getValue(), player.getHealth() + heal));
            }
        }
    }

    private GameManager getGameManager() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin != null ? plugin.getGameManager() : null;
    }
}
