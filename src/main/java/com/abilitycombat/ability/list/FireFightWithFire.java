package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;

@AbilityManifest(name = "이열치열 (FireFightWithFire)", rank = AbilityManifest.Rank.B, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 화염 흡수]",
        "§7모든 화염 피해를 받지 않고,",
        "§7대신 해당 피해만큼 §a체력을 회복§7합니다.",
        "",
        "§e§l[철괴 우클릭 - 발화]§f §8(쿨타임: 20초)",
        "§7자신에게 §f8초§7간 §c발화§7 상태를 부여합니다.",
        "§7(발화 피해를 체력으로 흡수)"
}, summarize = {
        "§7패시브§f: 화염 피해 → 체력 회복",
        "§7철괴 우클릭§f: 8초 발화"
})
public class FireFightWithFire extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 20;
    private static final int FIRE_SECONDS = 8;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);

    public FireFightWithFire(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageEvent.class);
    }

    @Override
    public boolean activeSkill(Material material, ActiveHandler.ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ActiveHandler.ClickType.RIGHT_CLICK) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        getPlayer().setFireTicks(FIRE_SECONDS * 20);
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent) {
            onDamage((EntityDamageEvent) event);
        }
    }

    private void onDamage(EntityDamageEvent event) {
        if (!event.getEntity().equals(getPlayer())) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FIRE
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                || event.getCause() == EntityDamageEvent.DamageCause.LAVA
                || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            event.setCancelled(true);
            Player player = getPlayer();
            double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.min(maxHealth, player.getHealth() + event.getDamage()));
        }
    }
}
