package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@AbilityManifest(name = "헤르밋 (Hermit)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 은둔]",
        "§7비전투 상태로 §f20초§7간 있으면 §e은신§7합니다.",
        "§7(피해를 주거나 받으면 해제/초기화)",
        "",
        "§e§l[은신 효과]",
        "§7투명해지며 타게팅되지 않습니다.",
        "§7장비가 보이지 않게 됩니다."
}, summarize = {
        "§7패시브§f: 비전투 20초 후 은신"
})
public class Hermit extends AbilityBase {

    private static final int HIDE_SECONDS = 20;

    private long lastCombatTime;
    private boolean hidden;

    public Hermit(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        lastCombatTime = System.currentTimeMillis();
        registerTick();
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        if (hidden) {
            disableHide();
        }
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamage((EntityDamageByEntityEvent) event);
        }
    }

    private void onDamage(EntityDamageByEntityEvent event) {
        Player player = getPlayer();
        if (event.getEntity().equals(player) || event.getDamager().equals(player)) {
            lastCombatTime = System.currentTimeMillis();
            if (hidden) {
                disableHide();
            }
        }
    }

    private void enableHide() {
        Player player = getPlayer();
        player.setInvisible(true);
        player.setCollidable(false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, true, false));

        // Hide player (and armor) from everyone else
        org.bukkit.Bukkit.getOnlinePlayers().forEach(other -> {
            if (!other.equals(player)) {
                other.hidePlayer(com.abilitycombat.AbilityCombat.getPlugin(), player);
            }
        });

        hidden = true;
    }

    private void disableHide() {
        Player player = getPlayer();
        player.setInvisible(false);
        player.setCollidable(true);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);

        // Show player to everyone
        org.bukkit.Bukkit.getOnlinePlayers().forEach(other -> {
            other.showPlayer(com.abilitycombat.AbilityCombat.getPlugin(), player);
        });

        hidden = false;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            if (hidden) {
                return;
            }
            long elapsed = System.currentTimeMillis() - lastCombatTime;
            if (elapsed >= HIDE_SECONDS * 1000L) {
                enableHide();
            }
        }
    }

    public boolean isHidden() {
        return hidden;
    }
}
