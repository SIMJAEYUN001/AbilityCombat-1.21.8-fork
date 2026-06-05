package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.SharedBurn;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

@AbilityManifest(name = "악마의 부츠 (DevilBoots)", species = AbilityManifest.Species.OTHERS, explain = {
        "§e§l[패시브 - 지옥불 자취]",
        "§7이동 경로에 §f4초§7간 유지되는 §c화염 자취§7를 남깁니다.",
        "§7자취에 닿은 적은 매초 §c화상 1스택§7을 얻고 §c4초§7간 불탑니다.",
        "§7화상은 공유 스택이며 이 능력은 스택당 매초 §c0.5 피해§7를 줍니다.",
        "§7대상이 물에 닿거나 불이 꺼지면 화상 스택이 초기화됩니다.",
        "",
        "§e§l[철괴 우클릭 - 악마의 질주]§f §8(쿨타임: 30초)",
        "§7자신에게 §b신속 II 10초§7를 부여합니다."
}, summarize = {
        "§7패시브§f: 4초 화염 자취, 닿은 적 매초 화상 +1",
        "§7화상§f: 스택당 초당 0.5 피해, 물/소화 시 초기화",
        "§7철괴 우클릭§f: 신속 II 10초 (30초)"
})
public class DevilBoots extends AbilityBase implements ActiveHandler {

    private static final int ACTIVE_COOLDOWN_SECONDS = 30;
    private static final int SPEED_DURATION_TICKS = 200;
    private static final int TRAIL_DURATION_TICKS = 80;
    private static final int TRAIL_SPAWN_INTERVAL_TICKS = 4;
    private static final int TRAIL_VFX_INTERVAL_TICKS = 4;
    private static final int BURN_TICK_INTERVAL = 20;
    private static final int FIRE_TICKS = 80;
    private static final double TRAIL_RADIUS = 1.35;
    private static final double TRAIL_Y_RANGE = 1.6;
    private static final double TRAIL_MIN_DISTANCE_SQUARED = 0.16;

    private final Cooldown cooldown = new Cooldown(ACTIVE_COOLDOWN_SECONDS);
    private final BossBarDuration speedDuration = new BossBarDuration(10, 7,
            net.kyori.adventure.bossbar.BossBar.Color.RED,
            net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS) {
        @Override
        protected Component getDurationTitle(int remaining) {
            return Component.text("악마의 질주 " + remaining + "초",
                    net.kyori.adventure.text.format.NamedTextColor.RED);
        }
    };
    private final Deque<TrailNode> trailNodes = new ArrayDeque<>();
    private Location lastTrailLocation;

    public DevilBoots(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        unsubscribeEvent(EntityDamageEvent.class);
        speedDuration.stop(true);
        clearTrail();
    }

    @Override
    protected void onDestroy() {
        speedDuration.stop(true);
        clearTrail();
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
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, SPEED_DURATION_TICKS, 1, true, false));
        player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.7f, 1.35f);
        speedDuration.start();
        cooldown.start();
        applyIronCooldownIfEmpty(ACTIVE_COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent damageEvent) {
            onDamage(damageEvent);
        }
    }

    private void onDamage(EntityDamageEvent event) {
        if (!event.getEntity().equals(getPlayer())) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FIRE
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                || event.getCause() == EntityDamageEvent.DamageCause.LAVA
                || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onTick(int tick) {
        if (tick % TRAIL_SPAWN_INTERVAL_TICKS == 0) {
            addTrailNode(tick);
        }
        if (tick % TRAIL_VFX_INTERVAL_TICKS == 0) {
            updateTrailNodes(tick);
        }
        if (tick % BURN_TICK_INTERVAL == 0) {
            applyBurnStacks();
        }
    }

    private void addTrailNode(int tick) {
        Player player = getPlayer();
        if (player == null || player.isDead()) {
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
        Location nodeLocation = current.clone().add(0, 0.12, 0);
        trailNodes.addLast(new TrailNode(nodeLocation, tick + TRAIL_DURATION_TICKS));
        lastTrailLocation = nodeLocation;
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
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        ParticleUtil.spawnParticle(world, Particle.FLAME, location, 8, 0.35, 0.08, 0.35, 0.01, 2, 64);
        ParticleUtil.spawnParticle(world, Particle.LAVA, location, 1, 0.22, 0.04, 0.22, 0.0, 4, 64);
    }

    private void applyBurnStacks() {
        Player owner = getPlayer();
        if (owner == null) {
            return;
        }
        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(owner.getLocation(), 24.0, owner,
                entity -> entity instanceof Player)) {
            if (!isTouchingTrail(target)) {
                continue;
            }
            SharedBurn.refreshProfile(target, owner, SharedBurn.BurnProfile.DEVIL_BOOTS, FIRE_TICKS);
        }
    }

    private boolean isTouchingTrail(LivingEntity target) {
        Location targetLocation = target.getLocation();
        for (TrailNode node : trailNodes) {
            if (!targetLocation.getWorld().equals(node.location.getWorld())) {
                continue;
            }
            if (Math.abs(targetLocation.getY() - node.location.getY()) > TRAIL_Y_RANGE) {
                continue;
            }
            if (LocationUtil.isInCircle(node.location, targetLocation, TRAIL_RADIUS)) {
                return true;
            }
        }
        return false;
    }

    private void clearTrail() {
        trailNodes.clear();
        lastTrailLocation = null;
    }

    private record TrailNode(Location location, int expireTick) {
    }

}
