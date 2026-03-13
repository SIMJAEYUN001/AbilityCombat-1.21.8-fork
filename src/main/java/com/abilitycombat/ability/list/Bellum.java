package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.GameManager;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.NearbyEntityCache;
import com.abilitycombat.utils.ParticleUtil;
import com.abilitycombat.vfx.VectorUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@AbilityManifest(name = "벨리움 (Bellum)", rank = AbilityManifest.Rank.S, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 기력]",
        "§7모든 능력이 쿨타임 대신 §6기력§7을 소모합니다.",
        "§7기력은 최대 §f6§7까지 차오르며,",
        "§7매 초 §f0.5§7씩 자동으로 회복되며",
        "§7기력 소모 후 §f3초§7간 회복되지 않습니다.",
        "",
        "§e§l[철괴 우클릭 - 정면 돌파]§f §8(기력 2 소모)",
        "§7타게팅이 되지 않는 상태로 짧게 돌진합니다.",
        "§7닿은 적에게 §c4의 피해§7를 입히고 §f1초§7간 §e기절§7시킵니다.",
        "§7돌진 중 벽을 만나면 벽을 파괴하고 주변을 기절시킵니다.",
        "",
        "§e§l[패시브 - 일방적 구타]",
        "§7기절 상태인 적에게 §c125%§7 피해를 입힙니다. (§f기력 0.5 소모§7)"
}, summarize = {
        "§7기력 시스템§f: 쿨타임 대신 기력 소모",
        "§7철괴 우클릭§f: 돌진 + 기절"
})
public class Bellum extends AbilityBase implements ActiveHandler {

    private static final double MAX_STAMINA = 6.0;
    private static final double REGEN_PER_SECOND = 0.5;
    private static final double DASH_COST = 2.0;
    private static final double BEAT_COST = 0.5;
    private static final double BEAT_DAMAGE_MULTIPLIER = 1.25;
    private static final double DASH_POWER = 2.0;
    private static final int STUN_TICKS = 20;
    private static final int WALL_STUN_TICKS = 50;
    private static final int REGEN_DELAY_SECONDS = 3;
    private static final int DASH_DURATION_TICKS = 12;
    private static final Set<Material> UNBREAKABLE = Set.of(
            Material.BEDROCK,
            Material.BARRIER,
            Material.END_PORTAL_FRAME,
            Material.END_PORTAL,
            Material.END_GATEWAY,
            Material.NETHER_PORTAL,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.STRUCTURE_BLOCK,
            Material.JIGSAW);

    private double stamina = MAX_STAMINA;
    private int regenDelaySeconds;
    private final BossBarGauge staminaBar = new BossBarGauge("stamina", 5, BossBar.Color.YELLOW,
            BossBar.Overlay.NOTCHED_6);
    private final NearbyEntityCache nearbyCache = new NearbyEntityCache();
    private int dashRemainingRuns = 0;
    private final Set<UUID> hitTargets = new HashSet<>();
    private boolean storedInvulnerable;
    private boolean storedCollidable;

    public Bellum(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        updateStaminaBar();
        subscribeEvent(EntityDamageByEntityEvent.class);
        subscribeEvent(BlockBreakEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        if (isDashing()) {
            stopDash();
        }
        staminaBar.clear();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (isDashing()) {
            getPlayer().sendMessage("§c이미 돌진 사용 중입니다.");
            return false;
        }
        if (!consumeStamina(DASH_COST)) {
            getPlayer().sendMessage("§c기력이 부족합니다.");
            return false;
        }
        startDash();
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        } else if (event instanceof BlockBreakEvent) {
            onBlockBreak((BlockBreakEvent) event);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager().equals(getPlayer()) && event.getEntity() instanceof LivingEntity target) {
            if (Stun.isStunned(target)) {
                if (consumeStamina(BEAT_COST)) {
                    event.setDamage(event.getDamage() * BEAT_DAMAGE_MULTIPLIER);
                }
            }
        }
    }

    private void onBlockBreak(BlockBreakEvent event) {
        if (isDashing()) {
            Block block = event.getBlock();
            if (!UNBREAKABLE.contains(block.getType())) {
                block.breakNaturally();
                nearbyCache
                        .getNearby(block.getLocation(), 3.0,
                                target -> !target.equals(getPlayer())
                                        && com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), target),
                                1)
                        .forEach(target -> Stun.apply(target, WALL_STUN_TICKS));
            }
        }
    }

    private void startDash() {
        Player player = getPlayer();
        storedInvulnerable = player.isInvulnerable();
        storedCollidable = player.isCollidable();
        player.setInvulnerable(true);
        player.setCollidable(false);
        hitTargets.clear();
        dashRemainingRuns = DASH_DURATION_TICKS;
        applyDashVelocity(player);
    }

    private void stopDash() {
        Player player = getPlayer();
        if (player != null) {
            player.setInvulnerable(storedInvulnerable);
            player.setCollidable(storedCollidable);
        }
        dashRemainingRuns = 0;
        hitTargets.clear();
    }

    private boolean isDashing() {
        return dashRemainingRuns > 0;
    }

    @Override
    public void onTick(int tick) {
        if (isDestroyed()) {
            unregisterTick();
            return;
        }

        // 1. Stamina Regen (Every 20 ticks = 1 second)
        if (tick % 20 == 0) {
            if (stamina < MAX_STAMINA) {
                if (regenDelaySeconds > 0) {
                    regenDelaySeconds--;
                } else {
                    stamina = Math.min(MAX_STAMINA, stamina + REGEN_PER_SECOND);
                }
            }
            updateStaminaBar();
        }

        // 2. Dash Logic (Every 1 tick for smooth movement)
        if (isDashing()) {
            Player player = getPlayer();
            if (player == null) {
                stopDash();
                return;
            }
            applyDashVelocity(player);
            hitNearbyTargets(player);
            if (isBlockObstructing(player)) {
                breakThrough(player);
                stopDash();
            } else {
                dashRemainingRuns--;
                if (dashRemainingRuns <= 0) {
                    stopDash();
                }
            }
        }
    }

    private void applyDashVelocity(Player player) {
        Vector direction = player.getLocation().getDirection().setY(0).normalize().multiply(DASH_POWER);
        direction.setY(0.05);
        player.setVelocity(direction);
    }

    private void hitNearbyTargets(Player player) {
        List<LivingEntity> targets = nearbyCache.getNearby(player.getLocation(), 2.0,
                target -> com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), target), 4);
        for (LivingEntity entity : targets) {
            if (entity.equals(player)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(player.getLocation()) <= 4.0) {
                if (hitTargets.add(entity.getUniqueId())) {
                    entity.damage(4.0, player);
                    Stun.apply(entity, STUN_TICKS);
                }
            }
        }
    }

    private boolean consumeStamina(double amount) {
        if (stamina < amount) {
            return false;
        }
        stamina = Math.max(0.0, stamina - amount);
        regenDelaySeconds = REGEN_DELAY_SECONDS;
        updateStaminaBar();
        return true;
    }

    private void updateStaminaBar() {
        Component title = Component.text("기력 " + String.format("%.1f", stamina) + "/" + MAX_STAMINA,
                NamedTextColor.YELLOW);
        staminaBar.update(title, stamina / MAX_STAMINA);
    }

    private boolean isBlockObstructing(Player player) {
        Location location = player.getLocation();
        Vector direction = location.getDirection().setY(0).normalize().multiply(0.75);
        return isBlockObstructing(location, direction)
                || isBlockObstructing(location, VectorUtil.rotateAroundAxisY(direction.clone(), Math.toRadians(45)))
                || isBlockObstructing(location, VectorUtil.rotateAroundAxisY(direction.clone(), Math.toRadians(-45)));
    }

    private boolean isBlockObstructing(Location location, Vector direction) {
        Location front = location.clone().add(direction);
        AbilityCombat plugin = AbilityCombat.getPlugin();
        GameManager gameManager = plugin != null ? plugin.getGameManager() : null;
        boolean crossesBorder = gameManager != null
                ? gameManager.isInsideGameBorder(location) && !gameManager.isInsideGameBorder(front)
                : location.getWorld().getWorldBorder().isInside(location)
                        && !location.getWorld().getWorldBorder().isInside(front);
        return isSolid(front.getBlock())
                || isSolid(front.clone().add(0, 1, 0).getBlock())
                || crossesBorder;
    }

    private boolean isSolid(Block block) {
        return block != null && !block.isEmpty() && block.getType().isSolid();
    }

    private void breakThrough(Player player) {
        Location location = player.getLocation();
        Vector forward = location.getDirection().setY(0).normalize();
        double yawRadians = Math.toRadians(-location.getYaw());
        Set<Block> broken = new HashSet<>();
        Set<UUID> damaged = new HashSet<>();
        for (double side = -2.0; side <= 2.0; side += 0.5) {
            for (int up = 0; up <= 3; up++) {
                Vector offset = VectorUtil.rotateAroundAxisY(new Vector(side, up, 0), yawRadians);
                Location base = location.clone().add(offset);
                for (int i = 0; i <= 4; i++) {
                    Block block = base.clone().add(forward.clone().multiply(i)).getBlock();
                    if (!shouldBreak(block) || !broken.add(block)) {
                        continue;
                    }
                    spawnBreakParticle(block);
                    BlockBreakEvent event = new BlockBreakEvent(block, player);
                    Bukkit.getPluginManager().callEvent(event);
                    if (!event.isCancelled()) {
                        block.breakNaturally();
                    }
                    for (Player target : block.getWorld().getNearbyEntitiesByType(Player.class, block.getLocation(), 2,
                            2, 2)) {
                        if (target.equals(player) || !com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), target)
                                || !damaged.add(target.getUniqueId())) {
                            continue;
                        }
                        target.damage(10.0, player);
                        Stun.apply(target, WALL_STUN_TICKS);
                    }
                }
            }
        }
    }

    private boolean shouldBreak(Block block) {
        if (block == null || block.isEmpty()) {
            return false;
        }
        Material type = block.getType();
        if (!type.isSolid() || type.isAir() || block.isLiquid()) {
            return false;
        }
        return !UNBREAKABLE.contains(type);
    }

    private void spawnBreakParticle(Block block) {
        ParticleUtil.spawnParticle(
                block.getWorld(),
                Particle.BLOCK,
                block.getLocation().add(0.5, 0.5, 0.5),
                10,
                0.3,
                0.3,
                0.3,
                0,
                block.getBlockData(),
                1,
                0);
    }
}
