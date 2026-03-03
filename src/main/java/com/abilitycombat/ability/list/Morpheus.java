package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.Participant;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@AbilityManifest(name = "모르페우스 (Morpheus)", rank = AbilityManifest.Rank.S, species = AbilityManifest.Species.GOD, explain = {
        "§e§l[검 우클릭 - 꿈의 연주]§f §8(쿨타임: 30초)",
        "§7주변 §f8칸§7 이내의 모든 플레이어를",
        "§f6초§7간 §e수면§7 상태로 만듭니다.",
        "",
        "§e§l[수면 상태]",
        "§7이동, 공격, 아이템 사용이 불가능합니다.",
        "§7시야가 §8실명§7됩니다.",
        "§7피해를 받으면 즉시 §a깨어납니다§7.",
        ""
}, summarize = {
        "§7검 우클릭§f: 8칸 내 6초 수면 (§c30초 쿨§f)"
})
public class Morpheus extends AbilityBase implements ActiveHandler {

    private static final int RANGE = 8;
    private static final int SLEEP_SECONDS = 6;
    private static final int COOLDOWN_SECONDS = 30;

    private final ActionbarCooldown cooldown = new ActionbarCooldown(COOLDOWN_SECONDS);
    private final Set<UUID> sleeping = new HashSet<>();
    private final Map<UUID, Integer> pendingWake = new HashMap<>();

    public Morpheus(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageEvent.class);
        subscribeEvent(PlayerInteractEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        clearSleep();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (!isSword(material) || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (cooldown.isCooldown()) {
            return false;
        }

        Player caster = getPlayer();
        boolean affected = false;

        for (org.bukkit.entity.Entity entity : caster.getNearbyEntities(RANGE, RANGE, RANGE)) {
            if (entity instanceof Player target) {
                if (target.equals(caster) || !com.abilitycombat.utils.LocationUtil.isValidTarget(target))
                    continue;

                applySleep(target);
                affected = true;
            }
        }

        if (affected) {
            cooldown.start();
            return true;
        }
        return false;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent) {
            onDamage((EntityDamageEvent) event);
        } else if (event instanceof PlayerInteractEvent) {
            onInteract((PlayerInteractEvent) event);
        }
    }

    private void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (sleeping.contains(player.getUniqueId())) {
            wake(player);
        }
    }

    private void onInteract(PlayerInteractEvent event) {
        if (sleeping.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void applySleep(Player target) {
        if (sleeping.add(target.getUniqueId())) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, SLEEP_SECONDS * 20, 0, true, false));
            Stun.apply(target, SLEEP_SECONDS * 20);
            pendingWake.put(target.getUniqueId(), SLEEP_SECONDS * 20);
            registerTick();
        }
    }

    private void clearSleep() {
        for (UUID uuid : new HashSet<>(sleeping)) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) {
                wake(player);
            }
        }
        sleeping.clear();
        pendingWake.clear();
    }

    @Override
    public void onTick(int tick) {
        if (pendingWake.isEmpty()) {
            return;
        }
        pendingWake.entrySet().removeIf(entry -> {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    wake(player);
                }
                return true;
            }
            entry.setValue(remaining);
            return false;
        });
    }

    private void wake(Player player) {
        if (!sleeping.remove(player.getUniqueId())) {
            return;
        }
        pendingWake.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        Stun.remove(player);
    }

    private boolean isSword(Material material) {
        return material.name().endsWith("_SWORD");
    }
}
