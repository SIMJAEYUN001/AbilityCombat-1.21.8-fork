package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Disarm;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "평화주의자 (Pacifist)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 정전]§f §8(쿨타임: 60초)",
        "§7주변 §f8칸§7 내 모든 플레이어에게 §e무장해제 10초§7를 부여합니다.",
        "§7무장해제된 플레이어가 §f10초§7 동안 회복한 만큼 자신이 흡수를 얻습니다."
}, summarize = {
        "§7철괴 우클릭§f: 8칸 무장해제 10초",
        "§7정전 중 회복§f: 회복량만큼 흡수 획득"
})
public class Pacifist extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 60;
    private static final int DISARM_TICKS = 200;
    private static final double RADIUS = 8.0;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final Map<UUID, Integer> trackedPlayers = new HashMap<>();

    public Pacifist(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityRegainHealthEvent.class);
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        trackedPlayers.clear();
        unregisterTick();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        int expireTick = AbilityTickManager.getGlobalTick() + DISARM_TICKS;
        trackedPlayers.put(player.getUniqueId(), expireTick);
        Disarm.apply(player, DISARM_TICKS);
        for (Player target : LocationUtil.getNearbyPlayers(player.getLocation(), RADIUS, p -> true)) {
            trackedPlayers.put(target.getUniqueId(), expireTick);
            Disarm.apply(target, DISARM_TICKS);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.1f);
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (!(event instanceof EntityRegainHealthEvent regainEvent)
                || !(regainEvent.getEntity() instanceof Player target)) {
            return;
        }
        if (!trackedPlayers.containsKey(target.getUniqueId()) || regainEvent.getAmount() <= 0.0) {
            return;
        }
        Player owner = getPlayer();
        if (owner != null && owner.isOnline() && !owner.isDead()) {
            owner.setAbsorptionAmount(owner.getAbsorptionAmount() + regainEvent.getAmount());
        }
    }

    @Override
    public void onTick(int tick) {
        Iterator<Map.Entry<UUID, Integer>> iterator = trackedPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= tick) {
                iterator.remove();
            }
        }
    }
}
