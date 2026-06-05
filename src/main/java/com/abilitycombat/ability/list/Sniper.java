package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.entity.CustomEntity;
import com.abilitycombat.entity.Deflectable;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Set;

@AbilityManifest(name = "스나이퍼 (Sniper)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[활 발사 - 저격]§f",
        "§7활을 발사하면 §f3배§7 속도의 특수 화살을 발사합니다",
        "§7적중 시 §c12의 피해§7를 입힙니다",
        "",
        "§e§l[패시브 - 조준]",
        "§7활을 들고 있는 동안 §8이동 속도가 감소§7합니다"
}, summarize = {
        "§7활 발사§f: 고속 저격 (피해 12)"
})
public class Sniper extends AbilityBase {

    private static final double VELOCITY_MULTIPLIER = 3.0;
    private static final double DAMAGE = 12.0;
    private static final int MAX_TICKS = 100;
    private static final Set<Material> GLASS_TYPES = Set.of(
            Material.GLASS,
            Material.TINTED_GLASS,
            Material.GLASS_PANE,
            Material.WHITE_STAINED_GLASS,
            Material.ORANGE_STAINED_GLASS,
            Material.MAGENTA_STAINED_GLASS,
            Material.LIGHT_BLUE_STAINED_GLASS,
            Material.YELLOW_STAINED_GLASS,
            Material.LIME_STAINED_GLASS,
            Material.PINK_STAINED_GLASS,
            Material.GRAY_STAINED_GLASS,
            Material.LIGHT_GRAY_STAINED_GLASS,
            Material.CYAN_STAINED_GLASS,
            Material.PURPLE_STAINED_GLASS,
            Material.BLUE_STAINED_GLASS,
            Material.BROWN_STAINED_GLASS,
            Material.GREEN_STAINED_GLASS,
            Material.RED_STAINED_GLASS,
            Material.BLACK_STAINED_GLASS,
            Material.WHITE_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE,
            Material.GRAY_STAINED_GLASS_PANE,
            Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.BROWN_STAINED_GLASS_PANE,
            Material.GREEN_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE);

    public Sniper(Participant participant) {
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
        if (!(event.getProjectile() instanceof Arrow arrow)) {
            return;
        }
        Location start = arrow.getLocation();
        Vector velocity = arrow.getVelocity().multiply(VELOCITY_MULTIPLIER);
        arrow.remove();
        SniperBullet bullet = new SniperBullet(start, velocity);
        bullet.setSource(player);
        bullet.spawn();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.6f, 1.6f);
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            Player player = getPlayer();
            Material item = player.getInventory().getItemInMainHand().getType();
            if (item == Material.BOW) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, false));
            }
        }
    }

    private class SniperBullet extends CustomEntity implements Deflectable {
        private SniperBullet(Location location, Vector velocity) {
            super(location.getWorld(), location);
            setVelocity(velocity);
            setGravity(0.0);
            setDrag(0.0);
            setMaxAge(MAX_TICKS);
            resizeBoundingBox(-0.2, -0.2, -0.2, 0.2, 0.2, 0.2);
        }

        @Override
        protected void onTick() {
            Location loc = getLocation();
            ParticleUtil.spawnParticle(loc.getWorld(), Particle.CRIT, loc, 1, 0, 0, 0, 0, 2, 0);
        }

        @Override
        protected boolean onHitEntity(LivingEntity entity, Location hitLocation) {
            if (!com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), entity)) {
                return false;
            }
            entity.damage(DAMAGE, getPlayer());
            return true;
        }

        @Override
        protected boolean onHitBlock(Block block, Location hitLocation) {
            if (block == null) {
                return true;
            }
            if (GLASS_TYPES.contains(block.getType())) {
                block.breakNaturally();
                return false;
            }
            return true;
        }

        @Override
        public boolean deflect(LivingEntity deflector, Vector newVelocity) {
            if (deflector == null || newVelocity == null || newVelocity.lengthSquared() == 0) {
                return false;
            }
            setSource(deflector);
            setVelocity(newVelocity);
            return true;
        }
    }
}
