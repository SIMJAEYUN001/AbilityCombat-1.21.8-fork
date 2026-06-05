package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Stun;
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

@AbilityManifest(name = "루나 (Luna)", species = AbilityManifest.Species.SPECIAL, explain = {
        "§e§l[패시브 - 월광 표식]",
        "§7타격 시 대상에게 §b달빛 표식 1스택§7을 남깁니다.",
        "§75스택이 되면 표식을 소모해 §c추가 피해 8§7을 줍니다.",
        "",
        "§e§l[철괴 우클릭 - 월광 돌진]§f §8(대상별 쿨타임: 12초)",
        "§7주변 §f6칸§7 적에게 달빛 표식 1스택을 부여합니다.",
        "§7바라본 표식 대상에게 돌진해 §e기절 1초§7를 부여합니다."
}, summarize = {
        "§7패시브§f: 타격으로 달빛 표식 누적",
        "§7철괴 우클릭§f: 주변 표식 + 표식 대상 돌진/기절"
})
public class Luna extends AbilityBase implements ActiveHandler {

    private static final int DETONATE_STACKS = 5;
    private static final double BONUS_DAMAGE = 8.0;
    private static final double ACTIVE_RADIUS = 6.0;
    private static final int TARGET_COOLDOWN_TICKS = 240;
    private static final int STUN_TICKS = 20;

    private static final Map<UUID, Integer> MOON_STACKS = new HashMap<>();

    private final Map<UUID, Integer> targetCooldowns = new HashMap<>();

    public Luna(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        targetCooldowns.clear();
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (!(event instanceof EntityDamageByEntityEvent damageEvent)
                || (event instanceof Cancellable cancellable && cancellable.isCancelled())) {
            return;
        }
        Player player = getPlayer();
        if (player == null || !damageEvent.getDamager().equals(player)
                || !(damageEvent.getEntity() instanceof LivingEntity target)
                || !LocationUtil.isValidTarget(player, target)) {
            return;
        }
        addMark(target);
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(player.getLocation(), ACTIVE_RADIUS, player,
                entity -> true)) {
            addMark(target);
        }
        LivingEntity target = LocationUtil.getEntityLookingAt(LivingEntity.class, player, ACTIVE_RADIUS,
                entity -> LocationUtil.isValidTarget(player, entity) && hasMoonMark(entity));
        if (target != null && isTargetReady(target)) {
            player.teleport(target.getLocation().clone().add(target.getLocation().getDirection().multiply(-1.2)));
            Stun.apply(target, STUN_TICKS);
            targetCooldowns.put(target.getUniqueId(), org.bukkit.Bukkit.getCurrentTick() + TARGET_COOLDOWN_TICKS);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.5f);
        }
        return true;
    }

    private void addMark(LivingEntity target) {
        int next = MOON_STACKS.getOrDefault(target.getUniqueId(), 0) + 1;
        if (next >= DETONATE_STACKS) {
            MOON_STACKS.remove(target.getUniqueId());
            target.setNoDamageTicks(0);
            target.damage(BONUS_DAMAGE, getPlayer());
        } else {
            MOON_STACKS.put(target.getUniqueId(), next);
        }
    }

    public static boolean hasMoonMark(LivingEntity target) {
        return target != null && MOON_STACKS.getOrDefault(target.getUniqueId(), 0) > 0;
    }

    public static boolean removeMoonMark(LivingEntity target) {
        return target != null && MOON_STACKS.remove(target.getUniqueId()) != null;
    }

    public static void clearMoonMarks() {
        MOON_STACKS.clear();
    }

    private boolean isTargetReady(LivingEntity target) {
        return targetCooldowns.getOrDefault(target.getUniqueId(), 0) <= org.bukkit.Bukkit.getCurrentTick();
    }
}
