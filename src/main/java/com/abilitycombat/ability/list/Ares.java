package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.NearbyEntityCache;
import com.abilitycombat.utils.ParticleUtil;
import com.abilitycombat.utils.VectorPool;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@AbilityManifest(name = "아레스 (Ares)", species = AbilityManifest.Species.GOD, explain = {
        "§e§l[철괴 우클릭 - 전쟁의 도약]§f §8(쿨타임: 30초)",
        "§7전방으로 강하게 도약합니다",
        "",
        "§7도약 중 주변 §f3칸§7 이내의 생명체를 §6끌어당기며§7",
        "§7최초 접촉 시 §c12의 피해§7를 입힙니다",
        "",
        "§7착지하면 주변 §f4칸§7 이내의 생명체를 §6밀쳐내고§7",
        "§c15의 피해§7를 추가로 입힙니다",
        "§7도약 중에는 낙하 피해를 받지 않습니다"
}, summarize = {
        "§7철괴 우클릭§f: 도약 + 끌어당김 + 밀쳐냄"
})
// Verified: AbilityManifest and ActiveHandler should now be resolved in the
// IDE
public class Ares extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 30;
    private static final int DASH_TICKS = 40;
    private static final double PULL_RADIUS = 3.0;
    private static final double LAND_RADIUS = 4.0;
    private static final double INITIAL_CONTACT_DAMAGE = 12.0;
    private static final double LAND_DAMAGE = 15.0;
    private static final int TICK_PERIOD = 2;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private int remainingDashTicks = 0;
    private final Set<UUID> hitTargets = new HashSet<>();
    private final NearbyEntityCache pullCache = new NearbyEntityCache();
    private final NearbyEntityCache landCache = new NearbyEntityCache();
    private boolean landed;
    private int airborneTicks;

    public Ares(Participant participant) {
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
        if (isDashing() && !landed) {
            land(getPlayer());
        }
        remainingDashTicks = 0;
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
        if (isDashing()) {
            return false;
        }
        startDash();
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent) {
            onFallDamage((EntityDamageEvent) event);
        }
    }

    private void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (isDashing() && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    private void startDash() {
        Player player = getPlayer();
        hitTargets.clear();
        landed = false;
        airborneTicks = 0;
        Vector direction = VectorPool.get().copy(player.getLocation().getDirection()).normalize().multiply(1.4);
        direction.setY(0.6);
        player.setVelocity(direction);
        remainingDashTicks = DASH_TICKS / TICK_PERIOD;
        registerTick();
    }

    private void stopDash(boolean endNaturally) {
        if (endNaturally && !landed) {
            land(getPlayer());
        }
        remainingDashTicks = 0;
    }

    private boolean isDashing() {
        return remainingDashTicks > 0;
    }

    @Override
    public void onTick(int tick) {
        if (tick % TICK_PERIOD == 0) {
            if (isDashing()) {
                Player player = getPlayer();
                airborneTicks++;
                spawnDashParticles(player);
                if (airborneTicks > 2 && ((org.bukkit.entity.Entity) player).isOnGround()) {
                    landed = true;
                    land(player);
                    stopDash(false);
                    return;
                }
                pullTargets(player);
                remainingDashTicks--;
                if (remainingDashTicks <= 0) {
                    stopDash(true);
                }
            }
        }
    }

    private void pullTargets(Player player) {
        for (LivingEntity entity : pullCache.getNearby(player.getLocation(), PULL_RADIUS,
                e -> com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), e), 4)) {
            if (entity.equals(player)) {
                continue;
            }
            Vector pull = VectorPool.get().copy(player.getLocation().toVector())
                    .subtract(entity.getLocation().toVector()).normalize()
                    .multiply(0.8);
            entity.setVelocity(pull);
            if (hitTargets.add(entity.getUniqueId())) {
                entity.setNoDamageTicks(0);
                entity.damage(INITIAL_CONTACT_DAMAGE, player);
            }
        }
    }

    private void land(Player player) {
        for (LivingEntity entity : landCache.getNearby(player.getLocation(), LAND_RADIUS,
                e -> com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), e), 4)) {
            if (entity.equals(player)) {
                continue;
            }
            Vector push = VectorPool.get().copy(entity.getLocation().toVector())
                    .subtract(player.getLocation().toVector()).normalize()
                    .multiply(1.2);
            push.setY(0.4);
            entity.setVelocity(push);
            entity.setNoDamageTicks(0);
            entity.damage(LAND_DAMAGE, player);
        }
        spawnLandingEffects(player);
    }

    private void spawnLandingEffects(Player player) {
        World world = player.getWorld();
        ParticleUtil.spawnParticle(world, Particle.LAVA, player.getLocation(), 8, 0.6, 0.2, 0.6, 0.01, 1, 0);
        ParticleUtil.spawnParticle(world, Particle.FLAME, player.getLocation(), 20, 0.8, 0.2, 0.8, 0.02, 1, 0);
        world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.1f);
    }

    private void spawnDashParticles(Player player) {
        World world = player.getWorld();
        ParticleUtil.spawnParticle(world, Particle.LAVA, player.getLocation(), 2, 0.3, 0.1, 0.3, 0.01, 2, 0);
        ParticleUtil.spawnParticle(world, Particle.FLAME, player.getLocation(), 6, 0.5, 0.2, 0.5, 0.02, 2, 0);
    }

}
