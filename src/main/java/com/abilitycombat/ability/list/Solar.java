package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Bind;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "솔라 (Solar)", species = AbilityManifest.Species.SPECIAL, explain = {
        "§e§l[패시브 - 빛 표식]",
        "§7타격 시 대상에게 §e빛 표식 1스택§7을 남깁니다.",
        "§74스택이 되면 표식을 소모해 §e속박 1초§7를 부여합니다.",
        "§7주변 §f5칸§7 플레이어에게 달빛 표식이 있으면 달빛 표식을 제거합니다.",
        "",
        "§e§l[철괴 우클릭 - 태양 보호막]§f §8(쿨타임: 35초)",
        "§710초§7간 받는 피해가 §b25% 감소§7합니다."
}, summarize = {
        "§7패시브§f: 타격으로 빛 표식, 4스택 속박 1초",
        "§7철괴 우클릭§f: 10초간 피해 25% 감소"
})
public class Solar extends AbilityBase implements ActiveHandler {

    private static final int BIND_STACKS = 4;
    private static final int BIND_TICKS = 20;
    private static final int SHIELD_TICKS = 200;
    private static final int COOLDOWN_SECONDS = 35;
    private static final double MOON_PURGE_RADIUS = 5.0;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final Map<UUID, Integer> lightStacks = new HashMap<>();
    private int shieldEndTick;

    public Solar(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
        subscribeEvent(org.bukkit.event.entity.EntityDamageEvent.class);
    }

    @Override
    protected void onDeactivate() {
        lightStacks.clear();
        shieldEndTick = 0;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent damageEvent) {
            onDamageByEntity(damageEvent);
        } else if (event instanceof org.bukkit.event.entity.EntityDamageEvent damageEvent) {
            if (!damageEvent.isCancelled() && damageEvent.getEntity().equals(getPlayer())
                    && shieldEndTick > AbilityTickManager.getGlobalTick()) {
                scaleIncomingDamage(damageEvent, 0.75);
            }
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if ((event instanceof Cancellable cancellable && cancellable.isCancelled())
                || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Player player = getPlayer();
        if (player == null || !event.getDamager().equals(player) || !LocationUtil.isValidTarget(player, target)) {
            return;
        }
        int next = lightStacks.getOrDefault(target.getUniqueId(), 0) + 1;
        if (next >= BIND_STACKS) {
            lightStacks.remove(target.getUniqueId());
            Bind.apply(target, BIND_TICKS);
        } else {
            lightStacks.put(target.getUniqueId(), next);
        }
        purgeNearbyMoonMarks(player);
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
        shieldEndTick = AbilityTickManager.getGlobalTick() + SHIELD_TICKS;
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.7f);
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    private void purgeNearbyMoonMarks(Player player) {
        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(player.getLocation(), MOON_PURGE_RADIUS, null,
                entity -> true)) {
            Luna.removeMoonMark(target);
        }
    }
}
