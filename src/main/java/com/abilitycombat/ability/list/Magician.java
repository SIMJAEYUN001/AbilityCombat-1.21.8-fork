package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@AbilityManifest(name = "마술사 (Magician)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 트릭]§f §8(쿨타임: 8초)",
        "§7활을 쏘면 적중 위치 반경 §f5칸§7 내의",
        "§7모든 생명체에게 §c최대 체력의 30%§7 피해를 입히고,",
        "§7위치를 무작위로 §e뒤섞습니다§7.",
        "",
        "§7트릭 화살이 아닌 일반 화살도 발사할 수 있으며,",
        "§7쿨타임 중에는 일반 화살만 발사됩니다."
}, summarize = {
        "§7활 발사§f: 범위 피해 + 위치 뒤섞기"
})
public class Magician extends AbilityBase {

    private static final int COOLDOWN_SECONDS = 8;
    private static final double RANGE = 5.0;
    private static final double DAMAGE_RATE = 0.3;
    private static final String KEY_MAGIC = "magician_arrow";

    private final ActionbarCooldown cooldown = new ActionbarCooldown(COOLDOWN_SECONDS);

    public Magician(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        giveBowAndArrows();
        subscribeEvent(EntityShootBowEvent.class);
        subscribeEvent(ProjectileHitEvent.class);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityShootBowEvent) {
            onShoot((EntityShootBowEvent) event);
        } else if (event instanceof ProjectileHitEvent) {
            onHit((ProjectileHitEvent) event);
        }
    }

    private void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (cooldown.isCooldown()) {
            return;
        }
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        arrow.getPersistentDataContainer().set(new NamespacedKey(AbilityCombat.getPlugin(), KEY_MAGIC),
                PersistentDataType.BYTE, (byte) 1);
        cooldown.start();
    }

    private void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        if (!arrow.getPersistentDataContainer().has(new NamespacedKey(AbilityCombat.getPlugin(), KEY_MAGIC),
                PersistentDataType.BYTE)) {
            return;
        }
        if (!(arrow.getShooter() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        Location center = arrow.getLocation();
        applyTrick(center);
        arrow.remove();
    }

    private void applyTrick(Location center) {
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity entity : center.getWorld().getLivingEntities()) {
            if (entity.getLocation().distanceSquared(center) <= RANGE * RANGE
                    && com.abilitycombat.utils.LocationUtil.isValidTarget(entity)) {
                targets.add(entity);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        List<Location> locations = new ArrayList<>();
        for (LivingEntity entity : targets) {
            locations.add(entity.getLocation());
        }
        Collections.shuffle(locations);
        for (int i = 0; i < targets.size(); i++) {
            LivingEntity target = targets.get(i);
            double maxHealth = target.getAttribute(Attribute.MAX_HEALTH).getValue();
            target.damage(maxHealth * DAMAGE_RATE, getPlayer());
            target.teleport(locations.get(i));
        }
    }
}
