package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.Participant;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

@AbilityManifest(name = "해파리 (JellyFish)", species = AbilityManifest.Species.ANIMAL, explain = {
        "§e§l[패시브 - 신경독]§f §8(쿨타임: 8초)",
        "§7플레이어를 §f근접 공격§7하면 대상을",
        "§f3초§7간 §e기절§7시켜 움직이지 못하게 합니다.",
        "§7(이동 및 점프 불가)",
        ""
}, summarize = {
        "§7근접 공격§f: 3초 기절 (§c8초 쿨§f)"
})
public class JellyFish extends AbilityBase {

    private static final int STUN_SECONDS = 3;
    private static final int COOLDOWN_SECONDS = 8;

    private final ActionbarCooldown cooldown = new ActionbarCooldown(COOLDOWN_SECONDS);

    public JellyFish(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (cooldown.isCooldown()) {
            return;
        }
        Stun.apply(target, STUN_SECONDS * 20);
        cooldown.start();
    }
}
