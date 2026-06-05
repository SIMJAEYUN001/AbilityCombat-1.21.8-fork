package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.AbilityCombat;
import com.abilitycombat.game.GameManager;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

@AbilityManifest(name = "신지드 (Singed)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 맹독의 자취]",
        "§7이동 경로에 §f4초§7 동안 유지되는 독성 자취를 남깁니다",
        "§7자취를 밟은 다른 플레이어는 §f4초§7간 §2독I§7에 걸립니다",
        "§7독을 부여할 때마다 §e광기의 물약§7 쿨타임이 §f0.4초§7 감소합니다",
        "",
        "§e§l[철괴 우클릭 - 광기의 물약]§f §8(쿨타임: 180초)",
        "§f25초§7간 §b신속 I§7, §b저항 I§7를 얻습니다",
        "§7지속 중 패시브 독이 §2독 II§7로 강화됩니다"
}, summarize = {
        "§7패시브§f: 독성 자취 + 독 부여 시 쿨감",
        "§7철괴 우클릭§f: 25초 신속1/저항1, 독 강화"
})
public class Singed extends AbilityBase implements ActiveHandler {
    private static final int COOLDOWN_SECONDS = 180;
    private static final int ACTIVE_SECONDS = 25;
    private static final int TRAIL_DURATION_TICKS = 80;
    private static final int POISON_DURATION_TICKS = 80;
    private static final int TRAIL_SPAWN_INTERVAL_TICKS = 4;
    private static final int TRAIL_VFX_INTERVAL_TICKS = 4;
    private static final int COOLDOWN_REDUCTION_TICKS = 8;
    private static final double TRAIL_RADIUS = 3;
    private static final double TRAIL_Y_RANGE = 2.0;
    private static final double TRAIL_MIN_DISTANCE_SQUARED = 0.09;
    private static final double TRAIL_PARTICLE_SPREAD_FACTOR = 0.25;
    private static final double TRAIL_PARTICLE_MAX_SPREAD = 0.9;
    private static final double TRAIL_PARTICLE_Y_SPREAD = 0.06;
    private static final int TRAIL_PARTICLE_BASE_COUNT = 1;
    private static final int TRAIL_PARTICLE_COUNT_PER_RADIUS = 3;
    private static final Particle.DustOptions TRAIL_DUST = new Particle.DustOptions(Color.fromRGB(70, 200, 70), 1.0f);
    private static final Particle.DustOptions MADNESS_TRAIL_DUST = new Particle.DustOptions(Color.fromRGB(170, 60, 220),
            1.0f);

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final Deque<TrailNode> trailNodes = new ArrayDeque<>();
    private final BossBarDuration madnessDuration = new BossBarDuration(ACTIVE_SECONDS, 7,
            net.kyori.adventure.bossbar.BossBar.Color.GREEN,
            net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS) {
        @Override
        protected Component getDurationTitle(int remaining) {
            return Component.text("광기의 물약 지속 ", net.kyori.adventure.text.format.NamedTextColor.GREEN)
                    .append(Component.text(remaining + "초", net.kyori.adventure.text.format.NamedTextColor.WHITE));
        }
    };
    private Location lastTrailLocation;
    private int cooldownReductionCarryTicks;

    public Singed(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(PlayerMoveEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        madnessDuration.stop(true);
        clearTrail();
    }

    @Override
    protected void onDestroy() {
        madnessDuration.stop(true);
        clearTrail();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (isInvincible()) {
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
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ACTIVE_SECONDS * 20, 0, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ACTIVE_SECONDS * 20, 1, true, false));
        madnessDuration.start();
        cooldownReductionCarryTicks = 0;
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof PlayerMoveEvent) {
            onMove((PlayerMoveEvent) event);
        }
    }

    private void onMove(PlayerMoveEvent event) {
        if (isInvincible()) {
            return;
        }
        if (trailNodes.isEmpty()) {
            return;
        }
        Player mover = event.getPlayer();
        Player owner = getPlayer();
        if (owner == null || mover.equals(owner)) {
            return;
        }
        if (!LocationUtil.isValidTarget(getPlayer(), mover)) {
            return;
        }
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        int currentTick = AbilityTickManager.getGlobalTick();
        UUID moverId = mover.getUniqueId();
        for (TrailNode node : trailNodes) {
            if (node.expireTick <= currentTick) {
                continue;
            }
            if (node.hit.contains(moverId)) {
                continue;
            }
            if (Math.abs(to.getY() - node.location.getY()) > TRAIL_Y_RANGE) {
                continue;
            }
            if (!LocationUtil.isInCircle(node.location, to, TRAIL_RADIUS)) {
                continue;
            }
            node.hit.add(moverId);
            applyPoison(mover);
            break;
        }
    }

    private void applyPoison(Player target) {
        if (isInvincible()) {
            return;
        }
        int amplifier = isMadnessActive() ? 1 : 0;
        target.addPotionEffect(
                new PotionEffect(PotionEffectType.POISON, POISON_DURATION_TICKS, amplifier, true, false));
        reduceCooldown();
    }

    private void reduceCooldown() {
        if (!cooldown.isCooldown()) {
            return;
        }
        cooldownReductionCarryTicks += COOLDOWN_REDUCTION_TICKS;
        int reduceSeconds = cooldownReductionCarryTicks / 20;
        if (reduceSeconds <= 0) {
            return;
        }
        cooldownReductionCarryTicks %= 20;
        cooldown.setCount(Math.max(0, cooldown.getCount() - reduceSeconds));
        if (cooldown.getCount() <= 0) {
            cooldownReductionCarryTicks = 0;
        }
    }

    private boolean isMadnessActive() {
        return madnessDuration.isDuration();
    }

    @Override
    public void onTick(int tick) {
        if (isDestroyed()) {
            unregisterTick();
            return;
        }
        if (isInvincible()) {
            return;
        }
        if (tick % TRAIL_SPAWN_INTERVAL_TICKS == 0) {
            addTrailNode(tick);
        }
        if (tick % TRAIL_VFX_INTERVAL_TICKS == 0) {
            updateTrailNodes(tick);
        }
    }

    private void addTrailNode(int tick) {
        if (isInvincible()) {
            return;
        }
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        Location current = player.getLocation();
        if (current.getWorld() == null) {
            return;
        }
        if (lastTrailLocation != null) {
            if (!current.getWorld().equals(lastTrailLocation.getWorld())) {
                lastTrailLocation = null;
            } else if (current.distanceSquared(lastTrailLocation) < TRAIL_MIN_DISTANCE_SQUARED) {
                return;
            }
        }
        Location nodeLocation = new Location(current.getWorld(), current.getX(), current.getY() + 0.1, current.getZ());
        TrailNode node = new TrailNode(nodeLocation, tick + TRAIL_DURATION_TICKS);
        trailNodes.addLast(node);
        lastTrailLocation = nodeLocation;
        applyPoisonOnNode(node);
    }

    private void updateTrailNodes(int tick) {
        if (trailNodes.isEmpty()) {
            return;
        }
        Iterator<TrailNode> iterator = trailNodes.iterator();
        while (iterator.hasNext()) {
            TrailNode node = iterator.next();
            if (tick >= node.expireTick) {
                iterator.remove();
                continue;
            }
            spawnTrailParticle(node.location);
        }
    }

    private void spawnTrailParticle(Location location) {
        if (isInvincible()) {
            return;
        }
        if (location == null || location.getWorld() == null) {
            return;
        }
        Particle.DustOptions dust = isMadnessActive() ? MADNESS_TRAIL_DUST : TRAIL_DUST;
        double spread = Math.min(TRAIL_PARTICLE_MAX_SPREAD, TRAIL_RADIUS * TRAIL_PARTICLE_SPREAD_FACTOR);
        int count = Math.max(1,
                TRAIL_PARTICLE_BASE_COUNT + (int) Math.round(TRAIL_RADIUS * TRAIL_PARTICLE_COUNT_PER_RADIUS));
        ParticleUtil.spawnParticle(location.getWorld(), Particle.DUST, location, count, spread, TRAIL_PARTICLE_Y_SPREAD,
                spread, 0, dust, 2, 0);
    }

    private void applyPoisonOnNode(TrailNode node) {
        if (isInvincible()) {
            return;
        }
        Player owner = getPlayer();
        if (owner == null) {
            return;
        }
        for (Player target : LocationUtil.getNearbyPlayers(node.location, TRAIL_RADIUS, owner,
                player -> !player.equals(owner))) {
            if (Math.abs(target.getLocation().getY() - node.location.getY()) > TRAIL_Y_RANGE) {
                continue;
            }
            UUID targetId = target.getUniqueId();
            if (node.hit.add(targetId)) {
                applyPoison(target);
            }
        }
    }

    private void clearTrail() {
        trailNodes.clear();
        lastTrailLocation = null;
    }

    private static final class TrailNode {
        private final Location location;
        private final int expireTick;
        private final Set<UUID> hit = new HashSet<>();

        private TrailNode(Location location, int expireTick) {
            this.location = location;
            this.expireTick = expireTick;
        }
    }

    private boolean isInvincible() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null) {
            return false;
        }
        GameManager gameManager = plugin.getGameManager();
        if (gameManager == null) {
            return false;
        }
        return gameManager.isInvincible();
    }
}
