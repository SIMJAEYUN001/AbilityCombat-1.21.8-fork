package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityDefinition;
import com.abilitycombat.ability.AbilityFactory;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.GameManager;
import com.abilitycombat.game.Participant;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.PlayerDeathEvent;

@AbilityManifest(name = "바이러스 (Virus)", species = AbilityManifest.Species.OTHERS, explain = {
        "§e§l[패시브]",
        "§7이 능력은 당신을 처치한 사람에게 감염됩니다."
}, summarize = {
        "§7사망 시 처치자에게 전염"
})
public class Virus extends AbilityBase {

    public Virus(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(PlayerDeathEvent.class);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof PlayerDeathEvent death) {
            onDeath(death);
        }
    }

    private void onDeath(PlayerDeathEvent event) {
        if (!event.getEntity().equals(getPlayer())) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null) {
            return;
        }
        GameManager gameManager = plugin.getGameManager();
        if (gameManager == null) {
            return;
        }
        Participant killerParticipant = gameManager.getParticipant(killer.getUniqueId());
        if (killerParticipant == null) {
            return;
        }
        AbilityDefinition definition = plugin.getAbilityRegistry().getByName(getManifest().name());
        if (definition == null) {
            return;
        }
        killerParticipant.setAbilityDefinition(definition);
        killerParticipant.setAbility(AbilityFactory.create(definition.getName(), killerParticipant));
        killer.sendMessage("§c바이러스에 감염되었습니다.");
    }
}
