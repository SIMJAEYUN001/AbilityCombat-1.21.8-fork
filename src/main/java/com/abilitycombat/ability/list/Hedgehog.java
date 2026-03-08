package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.NearbyEntityCache;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

@AbilityManifest(name = "고슴도치 (Hedgehog)", rank = AbilityManifest.Rank.B, species = AbilityManifest.Species.ANIMAL, explain = {
        "§e§l[패시브 - 가시]",
        "§7자신에게 §f3칸§7 이내로 근접한 적에게",
        "§71.5초마다 §c1의 고정 피해§7를 지속적으로 입힙니다."
}, summarize = {
        "§7패시브§f: 접촉 시 1.5초당 1 고정 피해"
})
public class Hedgehog extends AbilityBase {

    private static final double RANGE = 3.0;
    private static final int DAMAGE_INTERVAL_TICKS = 30; // 1.5 seconds
    private static final double DAMAGE_AMOUNT = 1.0;
    private final NearbyEntityCache nearbyCache = new NearbyEntityCache();

    public Hedgehog(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
    }

    @Override
    public void handleBridgeEvent(Event event) {
        // No events used
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
    }

    @Override
    public void onTick(int tick) {
        if (tick % DAMAGE_INTERVAL_TICKS == 0) {
            Player player = getPlayer();
            for (LivingEntity entity : nearbyCache.getNearby(player.getLocation(), RANGE,
                    e -> !e.equals(player) && com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), e), 10)) {
                // 고정 데미지 1 (방어력/보호 무시) + 피격 판정(애니메이션)
                double health = entity.getHealth();
                entity.damage(0.0001, player); // 피격 판정 및 애니메이션 발생
                if (!entity.isDead()) {
                    entity.setHealth(Math.max(0, health - DAMAGE_AMOUNT));
                }
            }
        }
    }
}
