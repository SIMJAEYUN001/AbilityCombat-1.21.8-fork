package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.Comparator;

@AbilityManifest(name = "연쇄 번개 (ChainLightning)", species = AbilityManifest.Species.SPECIAL, explain = {
        "§e§l[패시브 - 연쇄]",
        "§7근접 공격이 적중하면 피격자 주변 §f5칸§7 내 추가 적을 찾습니다",
        "§7최대 §f3명§7에게 원본 최종 피해의 §e35%§7 전이 피해를 줍니다"
}, summarize = {
        "§7패시브§f: 근접 적중 시 5칸 내 3명에게 35% 전이 피해"
})
public class ChainLightning extends AbilityBase {

    private static final double CHAIN_RADIUS = 5.0;
    private static final int MAX_CHAIN_TARGETS = 3;
    private static final double CHAIN_DAMAGE_RATIO = 0.35;
    private static final Particle.DustOptions LIGHTNING_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 230, 90), 1.0f);

    private boolean applyingChainDamage;

    public ChainLightning(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unsubscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (!(event instanceof EntityDamageByEntityEvent damageEvent)
                || (event instanceof Cancellable cancellable && cancellable.isCancelled())) {
            return;
        }
        if (applyingChainDamage) {
            return;
        }
        Player player = getPlayer();
        if (player == null || !damageEvent.getDamager().equals(player)
                || !(damageEvent.getEntity() instanceof LivingEntity victim)
                || !LocationUtil.isValidTarget(player, victim)) {
            return;
        }
        double originalDamage = getCalculatedFinalDamage(damageEvent);
        if (originalDamage <= 0.0) {
            return;
        }
        double chainDamage = originalDamage * CHAIN_DAMAGE_RATIO;
        Location origin = victim.getLocation().clone().add(0, 1.0, 0);

        LocationUtil.getNearbyLivingEntities(victim.getLocation(), CHAIN_RADIUS, player,
                target -> !target.equals(victim) && !(target instanceof ArmorStand))
                .stream()
                .sorted(Comparator.comparingDouble(target -> target.getLocation().distanceSquared(victim.getLocation())))
                .limit(MAX_CHAIN_TARGETS)
                .forEach(target -> chainTo(player, origin, target, chainDamage));
    }

    private void chainTo(Player player, Location origin, LivingEntity target, double damage) {
        spawnChainEffect(origin, target.getLocation().clone().add(0, 1.0, 0));
        target.setNoDamageTicks(0);
        applyingChainDamage = true;
        try {
            target.damage(damage, player);
        } finally {
            applyingChainDamage = false;
        }
    }

    private void spawnChainEffect(Location from, Location to) {
        World world = from.getWorld();
        if (world == null || to.getWorld() != world) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length <= 0.01) {
            return;
        }
        Vector step = delta.normalize().multiply(0.45);
        Location point = from.clone();
        for (double traveled = 0.0; traveled <= length; traveled += 0.45) {
            ParticleUtil.spawnParticle(world, Particle.ELECTRIC_SPARK, point, 1, 0.04, 0.04, 0.04, 0.0, 1, 64);
            ParticleUtil.spawnParticle(world, Particle.DUST, point, 1, 0.02, 0.02, 0.02, 0.0,
                    LIGHTNING_DUST, 1, 64);
            point.add(step);
        }
        world.playSound(to, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f, 1.8f);
    }
}
