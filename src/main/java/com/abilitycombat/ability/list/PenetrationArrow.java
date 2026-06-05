package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.entity.CustomEntity;
import com.abilitycombat.entity.Parabola;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@AbilityManifest(name = "관통화살 (PenetrationArrow)", species = AbilityManifest.Species.OTHERS, explain = {
        "§e§l[활 발사 - 관통화살]",
        "§7특수한 관통 화살을 발사합니다 (탄창: §f4발§7, 재장전: §f3초§7)",
        "§7화살은 블록을 §f2회§7까지 관통하며, 5명까지 관통합니다",
        "",
        "§e§l[탄환 종류]§f §8(재장전 시 무작위)",
        "§7- §c절단§7: 적중 시 §c+2 추가 피해§7",
        "§7- §5중력§7: 적 주변 §f4칸§7 끌어당김 + 기절",
        "§7- §e풍월§7: 적을 화살 방향으로 §6밀쳐냄§7"
}, summarize = {
        "§7활 발사§f: 관통화살 (4발, 재장전 3초)",
        "§7효과§f: 절단/중력/풍월"
})
public class PenetrationArrow extends AbilityBase {

    private static final int MAGAZINE = 4;
    private static final int RELOAD_SECONDS = 3;
    private static final int BLOCK_PIERCE = 2;
    private static final int ENTITY_PIERCE = 5;
    private static final double BASE_DAMAGE = 4.0;
    private static final double GRAVITY_PER_TICK = 0.05;
    private static final int MAX_TICKS = 80;

    private int remainingReloadSeconds = 0;
    private AmmoType currentAmmo = AmmoType.random();
    private int ammoRemaining = MAGAZINE;

    public PenetrationArrow(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        giveBowAndArrows();
        registerTick();
        subscribeEvent(EntityShootBowEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        remainingReloadSeconds = 0;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityShootBowEvent) {
            onShoot((EntityShootBowEvent) event);
        }
    }

    private void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (isReloading()) {
            event.setCancelled(true);
            return;
        }
        if (ammoRemaining <= 0) {
            event.setCancelled(true);
            startReload();
            return;
        }
        Location start = event.getProjectile().getLocation();
        Vector velocity = event.getProjectile().getVelocity();
        event.getProjectile().remove();
        event.setCancelled(true);
        PenetrationShot shot = new PenetrationShot(start, velocity, currentAmmo, BLOCK_PIERCE, ENTITY_PIERCE);
        shot.setSource(player);
        shot.spawn();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.4f);
        ammoRemaining--;
        if (ammoRemaining <= 0) {
            startReload();
        }
    }

    private void applyAmmoEffect(AmmoType type, LivingEntity target, Vector projectileVelocity) {
        if (type == null) {
            return;
        }
        switch (type) {
            case GRAVITY -> applyGravity(target);
            case WIND -> applyWind(target, projectileVelocity);
            case CUT -> {
                // Damage bonus handled separately
            }
        }
    }

    private void applyGravity(LivingEntity target) {
        Stun.apply(target, 40);
        Collection<LivingEntity> nearby = com.abilitycombat.utils.LocationUtil.getNearbyLivingEntities(
                target.getLocation(), 4, getPlayer(), entity -> !entity.equals(target));
        for (LivingEntity entity : nearby) {
            Vector pull = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize()
                    .multiply(0.6);
            entity.setVelocity(pull);
        }
    }

    private void applyWind(LivingEntity target, Vector projectileVelocity) {
        Vector knock = projectileVelocity.clone();
        if (knock.lengthSquared() == 0) {
            return;
        }
        target.setVelocity(knock.normalize().multiply(1.5).setY(0.4));
    }

    private class PenetrationShot extends CustomEntity {
        private final AmmoType ammoType;
        private final Parabola parabola;
        private final Set<UUID> hitEntities = new HashSet<>();
        private int remainingBlockPierce;
        private int remainingEntityPierce;
        private int ticksLived;

        private PenetrationShot(Location start, Vector velocity, AmmoType ammoType, int blockPierce, int entityPierce) {
            super(start.getWorld(), start);
            this.ammoType = ammoType;
            this.remainingBlockPierce = blockPierce;
            this.remainingEntityPierce = entityPierce;
            setVelocity(velocity);
            setGravity(GRAVITY_PER_TICK);
            setDrag(0.0);
            setMaxAge(MAX_TICKS);
            resizeBoundingBox(-0.3, -0.3, -0.3, 0.3, 0.3, 0.3);
            Vector perSecond = velocity.clone().multiply(20.0);
            double gravityPerSecond = GRAVITY_PER_TICK * 400.0;
            this.parabola = new Parabola(start, perSecond, gravityPerSecond);
        }

        @Override
        protected void onTick() {
            ticksLived++;
            spawnTrail();
        }

        private void spawnTrail() {
            if (parabola == null || getWorld() == null) {
                return;
            }
            Vector prev = parabola.pointAtTicks(Math.max(0, ticksLived - 1));
            Vector current = parabola.pointAtTicks(ticksLived);
            Location from = new Location(getWorld(), prev.getX(), prev.getY(), prev.getZ());
            Location to = new Location(getWorld(), current.getX(), current.getY(), current.getZ());
            Vector delta = to.toVector().subtract(from.toVector());
            double length = delta.length();
            int points = Math.max(1, (int) (length * 3));
            Vector step = length > 0 ? delta.multiply(1.0 / points) : new Vector();
            Location point = from.clone();
            for (int i = 0; i <= points; i++) {
                ParticleUtil.spawnParticle(getWorld(), Particle.CRIT, point, 1, 0, 0, 0, 0, 2, 0);
                point.add(step);
            }
        }

        @Override
        protected boolean onHitEntity(LivingEntity entity, Location hitLocation) {
            if (!com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), entity)) {
                return false;
            }
            if (!hitEntities.add(entity.getUniqueId())) {
                return false;
            }
            applyAmmoEffect(ammoType, entity, getVelocity());
            entity.setNoDamageTicks(0);
            double damage = BASE_DAMAGE + (ammoType == AmmoType.CUT ? 2.0 : 0.0);
            entity.damage(damage, getPlayer());
            remainingEntityPierce--;
            return remainingEntityPierce <= 0;
        }

        @Override
        protected boolean onHitBlock(Block block, Location hitLocation) {
            remainingBlockPierce--;
            if (remainingBlockPierce <= 0) {
                return true;
            }
            Vector velocity = getVelocity();
            if (velocity.lengthSquared() > 0) {
                setLocation(getLocation().add(velocity.clone().normalize().multiply(0.6)));
            }
            return false;
        }
    }

    private void startReload() {
        remainingReloadSeconds = RELOAD_SECONDS;
        registerTick();
    }

    private boolean isReloading() {
        return remainingReloadSeconds > 0;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            if (isReloading()) {
                remainingReloadSeconds--;
                if (remainingReloadSeconds <= 0) {
                    ammoRemaining = MAGAZINE;
                    currentAmmo = AmmoType.random();
                    getPlayer().sendMessage("§a관통화살 재장전 완료: §f" + currentAmmo.display);
                }
            }
        }
    }

    private enum AmmoType {
        CUT("§c절단"),
        GRAVITY("§5중력"),
        WIND("§e풍월");

        private final String display;

        AmmoType(String display) {
            this.display = display;
        }

        private static AmmoType random() {
            AmmoType[] values = values();
            return values[ThreadLocalRandom.current().nextInt(values.length)];
        }

    }
}
