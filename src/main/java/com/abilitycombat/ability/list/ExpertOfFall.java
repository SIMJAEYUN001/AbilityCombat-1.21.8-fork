package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import net.kyori.adventure.text.Component;

@AbilityManifest(name = "낙법의 달인 (ExpertOfFall)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 낙법]",
        "§7낙하 피해를 받지 않으며,",
        "§7받았어야 할 피해를 §e저장§7합니다",
        "",
        "§e§l[패시브 - 충격 전달]",
        "§7저장된 피해는 §f10초§7간 유지되며,",
        "§7다음 공격 시 대상에게 저장된 피해를 §c추가§7로 입힙니다",
        "§7(최대 피해량: §f20 HP§7)"
}, summarize = {
        "§7패시브§f: 낙하 피해 면역 및 피해 저장(최대 20) 후 공격 시 반사"
})
public class ExpertOfFall extends AbilityBase {

    private static final double MAX_STORED_DAMAGE = 20.0;
    private double storedDamage = 0;
    private int remainingTicks = 0;

    public ExpertOfFall(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        storedDamage = 0;
        remainingTicks = 0;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent e && !(event instanceof EntityDamageByEntityEvent)) {
            onFall(e);
        } else if (event instanceof EntityDamageByEntityEvent e) {
            onDamageByEntity(e);
        }
    }

    private void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        storedDamage = Math.min(event.getDamage(), MAX_STORED_DAMAGE);
        remainingTicks = 200; // 10 seconds
        event.setCancelled(true);
        player.setFallDistance(0);

        getActionbarChannel().updateForTicks(player, "expert:stored", 10,
                Component.text("§e낙하 피해 §c" + String.format("%.1f", storedDamage) + "§e 저장됨"), 40);
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!event.getDamager().equals(getPlayer())) {
            return;
        }

        if (storedDamage > 0 && remainingTicks > 0) {
            addOutgoingDamage(event, storedDamage);
            getActionbarChannel().updateForTicks(getPlayer(), "expert:stored", 10,
                    Component.text("§c피해 " + String.format("%.1f", storedDamage) + " 전달!"), 40);
            storedDamage = 0;
            remainingTicks = 0;
        }
    }

    @Override
    public void onTick(int tick) {
        if (remainingTicks > 0) {
            double seconds = remainingTicks / 20.0;
            getActionbarChannel().updateForTicks(getPlayer(), "expert:stored", 10,
                    Component.text("§e낙하 충격 저장 중.. (")
                            .append(Component.text(String.format("%.1f", seconds) + "s",
                                    net.kyori.adventure.text.format.NamedTextColor.RED))
                            .append(Component.text(") §f| §c" + String.format("%.1f", storedDamage) + "§e HP")),
                    10); // Refresh frequently

            remainingTicks--;
            if (remainingTicks <= 0) {
                if (storedDamage > 0) {
                    getActionbarChannel().updateForTicks(getPlayer(), "expert:stored", 10,
                            Component.text("§7저장된 낙하 피해 소멸"), 40);
                }
                storedDamage = 0;
            }
        }
    }
}
