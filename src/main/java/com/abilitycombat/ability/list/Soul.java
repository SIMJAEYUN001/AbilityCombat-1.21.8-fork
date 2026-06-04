package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@AbilityManifest(name = "소울 (Soul)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 어둑서니]",
        "§7두려움을 최대 §f6§7까지 모을 수 있습니다.",
        "§7두려움을 획득한 후 §f15초§7가 지나면 초당 §f1§7씩 감소합니다.",
        "",
        "§e§l[검 우클릭 - 오싹한 힘]§f §8(쿨타임: 8초)",
        "§f15칸§7 이내 바라보는 대상에게 최대 체력의 §f20%§7 피해를 주고",
        "§7대상의 §b두려움을 흡수§7해 §f두려움 1§7을 획득합니다.",
        "§7대상이 나를 바라보면 §5밀어§7내고, 등지면 §5끌어§7옵니다.",
        "",
        "§e§l[철괴 우클릭 - 공포 그 자체]§f §8(두려움 4 소모)",
        "§7주변 §f10칸§7 이내의 모든 적을 §f3초§7간 기절시킵니다.",
        "§f4초§7 뒤 범위 내 적에게 §c1.5 고정 피해§7를 줍니다.",
        "",
        "§e§l[더블 점프 - 유령화]§f §8(쿨타임: 2초, 두려움 1 소모)",
        "§f3초§7간 무적 및 투명 상태로 돌진합니다.",
        "§7지속시간이 끝나면 §f6칸§7 이내 적을 §f1.5초§7간 기절시킵니다.",
        "",
        "§e§l[패시브 - 유령의 몸]",
        "§7낙하 피해를 입지 않습니다."
}, summarize = {
        "§7검 우클릭§f: 최대 체력 §f20%§7 피해 + §b두려움 1 획득§7 + 밀침/당김",
        "§7철괴 우클릭§f: §8두려움 4 소모§f, 10칸 광역 기절 + 1.5 고정 피해",
        "§7더블 점프§f: §8두려움 1 소모§f, 유령화 돌진 후 6칸 기절",
        "§7낙하 피해를 받지 않습니다."
})
public class Soul extends AbilityBase implements ActiveHandler {

    private static final int MAX_FEAR = 6;
    private static final int FEAR_DECAY_DELAY = 15;
    private static final int GRASP_COOLDOWN_SECONDS = 8;
    private static final int GHOST_COOLDOWN_SECONDS = 2;
    private static final int GHOST_SECONDS = 3;
    private static final int FEAR_BURST_COST = 4;
    private static final int FEAR_BURST_STUN_TICKS = 60;
    private static final int FEAR_BURST_DELAY_TICKS = 80;
    private static final double FEAR_BURST_DAMAGE = 1.5;
    private static final double GRASP_RANGE = 15.0;
    private static final double GRASP_DAMAGE_RATIO = 0.2;
    private static final int GHOST_STUN_TICKS = 30;
    private static final double GHOST_STUN_RANGE = 6.0;

    private final Cooldown graspCooldown = new Cooldown(GRASP_COOLDOWN_SECONDS);
    private final Cooldown ghostCooldown = new Cooldown(GHOST_COOLDOWN_SECONDS);

    private int fearStacks;
    private int decayDelaySeconds;
    private int ghostRemainingSeconds;
    private boolean ghosting;
    private final List<PendingBurst> pendingBursts = new ArrayList<>();

    private boolean storedInvulnerable;
    private boolean storedAllowFlight;
    private boolean storedFlying;
    private boolean storedTargetable;

    public Soul(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        Player player = getPlayer();
        if (player != null) {
            storedAllowFlight = player.getAllowFlight();
            storedFlying = player.isFlying();
            if (shouldUseFlight(player)) {
                player.setAllowFlight(true);
                player.setFlying(false);
            }
        }
        registerTick();
        subscribeEvent(PlayerToggleFlightEvent.class);
        subscribeEvent(PlayerMoveEvent.class);
        subscribeEvent(EntityDamageEvent.class);
    }

    @Override
    protected void onDeactivate() {
        endGhost();
        Player player = getPlayer();
        if (player != null) {
            player.setAllowFlight(storedAllowFlight);
            player.setFlying(storedFlying);
        }
        pendingBursts.clear();
        unregisterTick();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (material == Material.IRON_INGOT) {
            return useFearBurst();
        }
        if (isSword(material)) {
            return useGrasp();
        }
        return false;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof PlayerToggleFlightEvent toggleFlight) {
            onToggleFlight(toggleFlight);
        } else if (event instanceof PlayerMoveEvent move) {
            onMove(move);
        } else if (event instanceof EntityDamageEvent damage) {
            onDamage(damage);
        }
    }

    private void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!player.equals(getPlayer())) {
            return;
        }
        if (!shouldUseFlight(player)) {
            return;
        }
        event.setCancelled(true);
        player.setFlying(false);
        if (isOnGround(player)) {
            return;
        }
        if (ghosting) {
            return;
        }
        if (ghostCooldown.isCooldown()) {
            notifyCooldown(ghostCooldown);
            return;
        }
        if (fearStacks <= 0) {
            return;
        }
        consumeFear(1);
        startGhost();
        ghostCooldown.start();
        player.setAllowFlight(false);
    }

    private void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.equals(getPlayer())) {
            return;
        }
        if (ghosting) {
            return;
        }
        if (shouldUseFlight(player) && isOnGround(player)) {
            player.setAllowFlight(true);
        }
    }

    private void onDamage(EntityDamageEvent event) {
        if (!event.getEntity().equals(getPlayer())) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    private boolean useGrasp() {
        if (graspCooldown.isCooldown()) {
            notifyCooldown(graspCooldown);
            return false;
        }
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        LivingEntity target = LocationUtil.getEntityLookingAt(LivingEntity.class, player, GRASP_RANGE,
                entity -> !entity.equals(player));
        if (target == null) {
            return false;
        }
        double maxHealth = target.getAttribute(Attribute.MAX_HEALTH) != null
                ? target.getAttribute(Attribute.MAX_HEALTH).getValue()
                : 20.0;
        double damage = Math.max(1.0, maxHealth * GRASP_DAMAGE_RATIO);
        target.damage(damage, player);

        Vector toPlayer = player.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        Vector targetDir = target.getLocation().getDirection().normalize();
        boolean lookingAt = targetDir.dot(toPlayer) > 0.2;
        Vector velocity = lookingAt ? toPlayer.multiply(-0.6) : toPlayer.multiply(0.6);
        velocity.setY(0.2);
        target.setVelocity(velocity);

        gainFear(1);
        graspCooldown.start();
        return true;
    }

    private boolean useFearBurst() {
        if (fearStacks < FEAR_BURST_COST) {
            return false;
        }
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        consumeFear(FEAR_BURST_COST);
        Location center = player.getLocation().clone();
        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(center, 10.0, player,
                entity -> !entity.equals(player))) {
            Stun.apply(target, FEAR_BURST_STUN_TICKS);
        }
        pendingBursts.add(new PendingBurst(center, FEAR_BURST_DELAY_TICKS));
        return true;
    }

    private void startGhost() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        if (com.abilitycombat.AbilityCombat.getPlugin() != null
                && com.abilitycombat.AbilityCombat.getPlugin().getSprintHudService() != null) {
            com.abilitycombat.AbilityCombat.getPlugin().getSprintHudService().cancelDashState(player);
        }
        storedInvulnerable = player.isInvulnerable();
        storedTargetable = getParticipant() != null && getParticipant().isTargetable();
        player.setInvulnerable(true);
        player.setInvisible(true);
        player.setCollidable(false);
        if (getParticipant() != null) {
            getParticipant().setTargetable(false);
        }
        ghosting = true;
        ghostRemainingSeconds = GHOST_SECONDS;

        Vector dash = player.getLocation().getDirection().normalize().multiply(1.2);
        dash.setY(0.4);
        player.setVelocity(dash);
    }

    private void endGhost() {
        if (!ghosting) {
            return;
        }
        Player player = getPlayer();
        if (player != null) {
            player.setInvulnerable(storedInvulnerable);
            player.setInvisible(false);
            player.setCollidable(true);
            if (getParticipant() != null) {
                getParticipant().setTargetable(storedTargetable);
            }
            for (LivingEntity target : LocationUtil.getNearbyLivingEntities(player.getLocation(), GHOST_STUN_RANGE,
                    player,
                    entity -> !entity.equals(player))) {
                Stun.apply(target, GHOST_STUN_TICKS);
            }
        }
        ghostRemainingSeconds = 0;
        ghosting = false;
    }

    private void gainFear(int amount) {
        fearStacks = Math.min(MAX_FEAR, fearStacks + amount);
        decayDelaySeconds = FEAR_DECAY_DELAY;
    }

    private void consumeFear(int amount) {
        fearStacks = Math.max(0, fearStacks - amount);
        if (fearStacks == 0) {
            decayDelaySeconds = 0;
        }
    }

    private boolean isSword(Material material) {
        return material != null && material.name().endsWith("_SWORD");
    }

    private boolean shouldUseFlight(Player player) {
        if (player == null) {
            return false;
        }
        GameMode mode = player.getGameMode();
        return mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR;
    }

    private boolean isOnGround(Player player) {
        if (player == null) {
            return false;
        }
        Block block = player.getLocation().getBlock();
        Block below = block.getRelative(BlockFace.DOWN);
        return !below.isPassable();
    }

    @Override
    public void onTick(int tick) {
        if (!pendingBursts.isEmpty()) {
            Iterator<PendingBurst> iterator = pendingBursts.iterator();
            while (iterator.hasNext()) {
                PendingBurst burst = iterator.next();
                burst.remainingTicks--;
                if (burst.remainingTicks <= 0) {
                    applyBurstDamage(burst.center);
                    iterator.remove();
                }
            }
        }
        if (tick % 20 == 0) {
            if (ghosting) {
                ghostRemainingSeconds--;
                if (ghostRemainingSeconds <= 0) {
                    endGhost();
                }
            }
            if (decayDelaySeconds > 0) {
                decayDelaySeconds--;
            } else if (fearStacks > 0) {
                fearStacks--;
            }
        }
    }

    private void applyBurstDamage(Location center) {
        Player player = getPlayer();
        if (player == null || center == null) {
            return;
        }
        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(center, 10.0, player,
                entity -> !entity.equals(player))) {
            target.damage(FEAR_BURST_DAMAGE, player);
        }
    }

    private static class PendingBurst {
        private final Location center;
        private int remainingTicks;

        private PendingBurst(Location center, int remainingTicks) {
            this.center = center;
            this.remainingTicks = remainingTicks;
        }
    }
}
