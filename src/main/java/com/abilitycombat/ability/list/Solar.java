package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Bind;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.FakeGlow;
import com.abilitycombat.utils.LocationUtil;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@AbilityManifest(name = "솔라 (Solar)", species = AbilityManifest.Species.SPECIAL, explain = {
        "§e§l[패시브 - 빛 표식]",
        "§7타격 시 대상에게 §f10초§7 유지되는 빛 표식 1스택을 남깁니다",
        "§7표식 대상은 자신에게만 §e노란색 발광§7으로 보입니다",
        "§74스택이 되면 표식을 소모해 §e속박 1초§7를 부여합니다",
        "§7주변 §f5칸§7 플레이어에게 달빛 표식이 있으면 달빛 표식을 제거합니다",
        "",
        "§e§l[철괴 우클릭 - 태양 보호막]§f §8(쿨타임: 35초)",
        "§710초§7간 받는 피해가 §b25% 감소§7하고 자신에게 §e노란색 발광§7을 부여합니다"
}, summarize = {
        "§7패시브§f: 타격으로 10초 빛 표식, 4스택 속박 1초",
        "§7표식§f: 사용자 전용 노란색 발광",
        "§7철괴 우클릭§f: 10초간 피해 25% 감소"
})
public class Solar extends AbilityBase implements ActiveHandler {

    private static final int MARK_TICKS = 200;
    private static final int BIND_STACKS = 4;
    private static final int BIND_TICKS = 20;
    private static final int SHIELD_TICKS = 200;
    private static final int COOLDOWN_SECONDS = 35;
    private static final double MOON_PURGE_RADIUS = 5.0;
    private static final String GLOW_TEAM_NAME = "aw_solar_mark";
    private static final String SHIELD_TEAM_NAME = "aw_solar_shield";

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final Map<UUID, Integer> lightStacks = new HashMap<>();
    private final Map<UUID, Integer> lightExpireTicks = new HashMap<>();
    private final Map<UUID, String> highlightedTargets = new HashMap<>();
    private int shieldEndTick;

    public Solar(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageEvent.class);
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        clearLightStacks();
        clearShieldGlow();
        shieldEndTick = 0;
        unregisterTick();
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (!(event instanceof EntityDamageEvent damageEvent) || damageEvent.isCancelled()) {
            return;
        }
        if (damageEvent instanceof EntityDamageByEntityEvent damageByEntityEvent) {
            onDamageByEntity(damageByEntityEvent);
        }
        if (damageEvent.getEntity().equals(getPlayer()) && shieldEndTick > AbilityTickManager.getGlobalTick()) {
            modifyDamage(damageEvent, INCOMING_DAMAGE, -25.0, 0.0);
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
        UUID targetId = target.getUniqueId();
        int currentTick = AbilityTickManager.getGlobalTick();
        if (lightExpireTicks.getOrDefault(targetId, 0) <= currentTick) {
            lightStacks.remove(targetId);
            lightExpireTicks.remove(targetId);
            hideHighlight(target);
        }
        int next = lightStacks.getOrDefault(targetId, 0) + 1;
        if (next >= BIND_STACKS) {
            lightStacks.remove(targetId);
            lightExpireTicks.remove(targetId);
            hideHighlight(target);
            Bind.apply(target, BIND_TICKS);
        } else {
            lightStacks.put(targetId, next);
            lightExpireTicks.put(targetId, currentTick + MARK_TICKS);
            showHighlight(target);
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
        applyShieldGlow(player);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.7f);
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void onTick(int tick) {
        refreshHighlights(tick);
        if (tick % 10 == 0) {
            if (shieldEndTick > tick) {
                syncShieldGlowTeam();
            }
        }
        if (shieldEndTick > 0 && tick >= shieldEndTick) {
            clearShieldGlow();
            shieldEndTick = 0;
        }
    }

    private void purgeNearbyMoonMarks(Player player) {
        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(player.getLocation(), MOON_PURGE_RADIUS, null,
                entity -> true)) {
            Luna.removeMoonMark(target);
        }
    }

    private void showHighlight(LivingEntity target) {
        Player owner = getPlayer();
        if (owner == null || target == null) {
            return;
        }
        String entry = FakeGlow.scoreboardEntry(target);
        FakeGlow.show(owner, target, GLOW_TEAM_NAME, NamedTextColor.YELLOW);
        highlightedTargets.put(target.getUniqueId(), entry);
    }

    private void hideHighlight(LivingEntity target) {
        if (target != null) {
            hideHighlight(target.getUniqueId(), target);
        }
    }

    private void hideHighlight(UUID targetId, LivingEntity target) {
        Player owner = getPlayer();
        String entry = highlightedTargets.remove(targetId);
        if (owner != null) {
            FakeGlow.hide(owner, target, GLOW_TEAM_NAME, entry);
        }
    }

    private void refreshHighlights(int tick) {
        Set<UUID> desired = new HashSet<>(lightStacks.keySet());
        for (UUID targetId : desired) {
            if (lightExpireTicks.getOrDefault(targetId, 0) <= tick) {
                lightStacks.remove(targetId);
                lightExpireTicks.remove(targetId);
                hideHighlight(targetId, resolve(targetId));
                continue;
            }
            LivingEntity target = resolve(targetId);
            if (target == null || target.isDead()) {
                lightStacks.remove(targetId);
                lightExpireTicks.remove(targetId);
                continue;
            }
            showHighlight(target);
        }
        for (UUID targetId : new HashSet<>(highlightedTargets.keySet())) {
            if (!lightStacks.containsKey(targetId)) {
                hideHighlight(targetId, resolve(targetId));
            }
        }
    }

    private void clearLightStacks() {
        for (UUID targetId : new HashSet<>(highlightedTargets.keySet())) {
            hideHighlight(targetId, resolve(targetId));
        }
        lightStacks.clear();
        lightExpireTicks.clear();
    }

    private void applyShieldGlow(Player player) {
        syncShieldGlowTeam();
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, SHIELD_TICKS, 0, false, false));
    }

    private void clearShieldGlow() {
        Player player = getPlayer();
        if (player != null) {
            player.removePotionEffect(PotionEffectType.GLOWING);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                Team team = viewer.getScoreboard().getTeam(SHIELD_TEAM_NAME);
                if (team == null) {
                    continue;
                }
                team.removeEntry(player.getName());
                if (team.getSize() == 0) {
                    team.unregister();
                }
            }
        }
    }

    private void syncShieldGlowTeam() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Team team = getOrCreateTeam(viewer.getScoreboard(), SHIELD_TEAM_NAME, NamedTextColor.YELLOW);
            if (team != null) {
                team.addEntry(player.getName());
            }
        }
    }

    private Team getOrCreateTeam(Scoreboard scoreboard, String name, NamedTextColor color) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
            team.color(color);
            team.setAllowFriendlyFire(false);
        }
        return team;
    }

    private LivingEntity resolve(UUID id) {
        org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(id);
        return entity instanceof LivingEntity living ? living : null;
    }
}
