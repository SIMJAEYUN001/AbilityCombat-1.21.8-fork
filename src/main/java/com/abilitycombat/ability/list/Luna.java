package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.FakeGlow;
import com.abilitycombat.utils.LocationUtil;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@AbilityManifest(name = "루나 (Luna)", species = AbilityManifest.Species.SPECIAL, explain = {
        "§e§l[패시브 - 월광 표식]",
        "§7타격 시 대상에게 §f달빛 표식 1스택§7을 남깁니다",
        "§7표식 대상은 자신에게만 §f흰색 발광§7으로 보입니다",
        "§75스택이 되면 표식을 소모해 §c추가 피해 8§7을 주고 주변에 표식을 전이합니다",
        "",
        "§e§l[철괴 우클릭 - 월광 돌진]§f §8(대상별 쿨타임: 12초)",
        "§7바라본 표식 대상에게 빠르게 돌진해 §e기절 1초§7를 부여합니다"
}, summarize = {
        "§7패시브§f: 타격으로 달빛 표식, 5스택 추가 피해 8",
        "§7표식 소모§f: 주변 적에게 달빛 표식 전이",
        "§7철괴 우클릭§f: 표식 대상 돌진/기절 1초"
})
public class Luna extends AbilityBase implements ActiveHandler {

    private static final int DETONATE_STACKS = 5;
    private static final double BONUS_DAMAGE = 8.0;
    private static final double ACTIVE_RANGE = 8.0;
    private static final double TRANSFER_RADIUS = 5.0;
    private static final int TARGET_COOLDOWN_TICKS = 240;
    private static final int STUN_TICKS = 20;
    private static final int DASH_TICKS = 12;
    private static final double DASH_SPEED = 1.65;
    private static final String GLOW_TEAM_NAME = "aw_luna_mark";

    private static final Map<UUID, Map<UUID, Integer>> MOON_STACKS = new HashMap<>();
    private static final Map<UUID, Luna> ACTIVE_LUNAS = new HashMap<>();

    private final Map<UUID, Integer> targetCooldowns = new HashMap<>();
    private final Map<UUID, String> highlightedTargets = new HashMap<>();
    private AbilityTimer dashTimer;

    public Luna(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        Player player = getPlayer();
        if (player != null) {
            ACTIVE_LUNAS.put(player.getUniqueId(), this);
        }
        subscribeEvent(EntityDamageByEntityEvent.class);
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        if (dashTimer != null) {
            dashTimer.stop(true);
            dashTimer = null;
        }
        Player player = getPlayer();
        if (player != null) {
            ACTIVE_LUNAS.remove(player.getUniqueId());
            removeOwnerMoonMarks(player.getUniqueId());
        }
        clearHighlights();
        targetCooldowns.clear();
        unregisterTick();
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
        addMark(target, true);
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
        LivingEntity target = LocationUtil.getEntityLookingAt(LivingEntity.class, player, ACTIVE_RANGE,
                entity -> LocationUtil.isValidTarget(player, entity) && hasOwnerMoonMark(player.getUniqueId(), entity));
        if (target == null) {
            player.sendMessage("§c바라본 표식 대상이 없습니다");
            return false;
        }
        if (!isTargetReady(target)) {
            player.sendMessage("§c해당 대상에게 아직 월광 돌진을 사용할 수 없습니다");
            return false;
        }
        startDash(player, target);
        targetCooldowns.put(target.getUniqueId(), AbilityTickManager.getGlobalTick() + TARGET_COOLDOWN_TICKS);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.65f);
        return true;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 10 == 0) {
            refreshHighlights();
        }
    }

    private void addMark(LivingEntity target, boolean transferOnDetonate) {
        Player player = getPlayer();
        if (player == null || target == null) {
            return;
        }
        UUID ownerId = player.getUniqueId();
        UUID targetId = target.getUniqueId();
        Map<UUID, Integer> ownerStacks = MOON_STACKS.computeIfAbsent(targetId, ignored -> new HashMap<>());
        int next = ownerStacks.getOrDefault(ownerId, 0) + 1;
        if (next >= DETONATE_STACKS) {
            ownerStacks.remove(ownerId);
            cleanupTargetMoonMap(targetId);
            hideHighlight(target);
            target.setNoDamageTicks(0);
            target.damage(BONUS_DAMAGE, player);
            if (transferOnDetonate) {
                transferMark(target);
            }
            return;
        }
        ownerStacks.put(ownerId, next);
        showHighlight(target);
    }

    private void transferMark(LivingEntity sourceTarget) {
        Player player = getPlayer();
        if (player == null || sourceTarget == null) {
            return;
        }
        for (LivingEntity nearby : LocationUtil.getNearbyLivingEntities(sourceTarget.getLocation(), TRANSFER_RADIUS,
                player, entity -> entity != sourceTarget)) {
            addMark(nearby, false);
        }
        sourceTarget.getWorld().playSound(sourceTarget.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.6f);
    }

    private void startDash(Player player, LivingEntity target) {
        if (dashTimer != null && dashTimer.isRunning()) {
            dashTimer.stop(true);
        }
        dashTimer = new AbilityTimer(DASH_TICKS) {
            private boolean hit;

            @Override
            protected void onRun(int count) {
                Player owner = getPlayer();
                if (owner == null || owner.isDead() || target.isDead()
                        || !owner.getWorld().equals(target.getWorld())) {
                    stop(true);
                    return;
                }
                Vector direction = target.getLocation().toVector().subtract(owner.getLocation().toVector());
                double distanceSquared = direction.lengthSquared();
                if (distanceSquared <= 2.25) {
                    hit = true;
                    Stun.apply(target, STUN_TICKS);
                    owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.4f);
                    stop(false);
                    return;
                }
                if (distanceSquared > 1.0E-6) {
                    Vector velocity = direction.normalize().multiply(DASH_SPEED);
                    velocity.setY(Math.max(-0.05, Math.min(0.35, velocity.getY() * 0.25 + 0.08)));
                    owner.setVelocity(velocity);
                }
            }

            @Override
            protected void onEnd() {
                if (!hit && getPlayer() != null) {
                    getPlayer().setVelocity(getPlayer().getVelocity().multiply(0.45));
                }
            }
        }.setPeriod(1);
        dashTimer.start();
    }

    private void showHighlight(LivingEntity target) {
        Player owner = getPlayer();
        if (owner == null || target == null) {
            return;
        }
        String entry = FakeGlow.scoreboardEntry(target);
        FakeGlow.show(owner, target, GLOW_TEAM_NAME, NamedTextColor.WHITE);
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

    private void refreshHighlights() {
        Player player = getPlayer();
        if (player == null) {
            clearHighlights();
            return;
        }
        UUID ownerId = player.getUniqueId();
        Set<UUID> desired = new HashSet<>();
        for (Map.Entry<UUID, Map<UUID, Integer>> entry : MOON_STACKS.entrySet()) {
            if (!entry.getValue().containsKey(ownerId)) {
                continue;
            }
            LivingEntity target = resolve(entry.getKey());
            if (target == null || target.isDead()) {
                continue;
            }
            desired.add(entry.getKey());
            showHighlight(target);
        }
        for (UUID targetId : new HashSet<>(highlightedTargets.keySet())) {
            if (!desired.contains(targetId)) {
                hideHighlight(targetId, resolve(targetId));
            }
        }
    }

    private void clearHighlights() {
        for (UUID targetId : new HashSet<>(highlightedTargets.keySet())) {
            hideHighlight(targetId, resolve(targetId));
        }
    }

    private static LivingEntity resolve(UUID id) {
        org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(id);
        return entity instanceof LivingEntity living ? living : null;
    }

    private static boolean hasOwnerMoonMark(UUID ownerId, LivingEntity target) {
        Map<UUID, Integer> ownerStacks = target != null ? MOON_STACKS.get(target.getUniqueId()) : null;
        return ownerId != null && ownerStacks != null && ownerStacks.getOrDefault(ownerId, 0) > 0;
    }

    public static boolean hasMoonMark(LivingEntity target) {
        Map<UUID, Integer> ownerStacks = target != null ? MOON_STACKS.get(target.getUniqueId()) : null;
        return ownerStacks != null && !ownerStacks.isEmpty();
    }

    public static boolean removeMoonMark(LivingEntity target) {
        if (target == null) {
            return false;
        }
        Map<UUID, Integer> removed = MOON_STACKS.remove(target.getUniqueId());
        if (removed == null || removed.isEmpty()) {
            return false;
        }
        for (UUID ownerId : removed.keySet()) {
            Luna luna = ACTIVE_LUNAS.get(ownerId);
            if (luna != null) {
                luna.hideHighlight(target);
            }
        }
        return true;
    }

    public static void clearMoonMarks() {
        for (Luna luna : new HashSet<>(ACTIVE_LUNAS.values())) {
            luna.clearHighlights();
        }
        MOON_STACKS.clear();
    }

    private static void removeOwnerMoonMarks(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        Iterator<Map.Entry<UUID, Map<UUID, Integer>>> iterator = MOON_STACKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Map<UUID, Integer>> entry = iterator.next();
            entry.getValue().remove(ownerId);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private static void cleanupTargetMoonMap(UUID targetId) {
        Map<UUID, Integer> ownerStacks = MOON_STACKS.get(targetId);
        if (ownerStacks != null && ownerStacks.isEmpty()) {
            MOON_STACKS.remove(targetId);
        }
    }

    private boolean isTargetReady(LivingEntity target) {
        return targetCooldowns.getOrDefault(target.getUniqueId(), 0) <= AbilityTickManager.getGlobalTick();
    }

}
