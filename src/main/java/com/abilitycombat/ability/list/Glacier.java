package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Freeze;
import com.abilitycombat.game.Participant;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "글래시어 (Glacier)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[공격 - 결빙]",
        "§7빙결 상태가 아닌 적을 §f4회§7 공격하면",
        "§f3초§7간 §b빙결§7시킵니다",
        "",
        "§e§l[공격 - 쇄빙]",
        "§7빙결 상태인 적 공격 시 고정 피해 §c1§7을 입히고",
        "§7빙하기의 쿨타임이 §f1초§7 감소합니다",
        "",
        "§e§l[철괴 우클릭 - 빙하기]§f §8(쿨타임: 30초)",
        "§7주변 §f8칸§7 이내의 모든 플레이어를",
        "§f6초§7간 §b빙결§7시킵니다",
        "",
        "§e§l[패시브 - 얼어붙은 심장]",
        "§7빙결 상태에서 §2체력을 회복§7합니다"
}, summarize = {
        "§7공격 4회§f: 빙결",
        "§7철괴 우클릭§f: 광역 빙결"
})
public class Glacier extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 30;
    private static final int FREEZE_TICKS = 60;
    private static final int BLIZZARD_TICKS = 120;
    private static final double BLIZZARD_RANGE = 8.0;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final Map<UUID, Integer> hitCounts = new HashMap<>();

    public Glacier(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
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
        freezeNearby(BLIZZARD_RANGE, BLIZZARD_TICKS);
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!event.getDamager().equals(getPlayer())) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        UUID targetId = target.getUniqueId();
        if (Freeze.isFrozen(target)) {
            // 빙결 상태인 적 공격 시 추가 피해 (무한 루프 방지)
            addOutgoingDamage(event, 1.0);
            if (cooldown.isCooldown()) {
                cooldown.setCount(Math.max(0, cooldown.getCount() - 1));
            }
            return;
        }
        int count = hitCounts.getOrDefault(targetId, 0) + 1;
        if (count >= 4) {
            freezeTarget(target, FREEZE_TICKS);
            hitCounts.remove(targetId);
        } else {
            hitCounts.put(targetId, count);
        }
    }

    private void freezeNearby(double radius, int ticks) {
        Player player = getPlayer();
        for (LivingEntity entity : player.getLocation().getWorld().getLivingEntities()) {
            if (entity.equals(player) || !com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), entity)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                freezeTarget(entity, ticks);
            }
        }
    }

    private void freezeTarget(LivingEntity entity, int ticks) {
        Freeze.apply(entity, ticks);
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            if (Freeze.isFrozen(getPlayer())) {
                getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, true, false));
            }
        }
    }
}
