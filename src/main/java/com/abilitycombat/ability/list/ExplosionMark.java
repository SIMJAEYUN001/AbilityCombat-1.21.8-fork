package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "익스플로젼 (ExplosionMark)", species = AbilityManifest.Species.SPECIAL, explain = {
        "§e§l[패시브 - 폭발 표식]",
        "§7타격마다 대상에게 §c폭발 표식 1스택§7을 남깁니다.",
        "§74스택§7이 되면 표식을 소모해 블록 파괴 없는 폭발을 일으킵니다.",
        "§7폭발은 반경 §f3칸§7 내 적에게 §c10 피해§7를 줍니다."
}, summarize = {
        "§7패시브§f: 타격 4회마다 반경 3칸 폭발 피해 10"
})
public class ExplosionMark extends AbilityBase {

    private static final int REQUIRED_STACKS = 4;
    private static final double RADIUS = 3.0;
    private static final double DAMAGE = 10.0;

    private final Map<UUID, Integer> stacks = new HashMap<>();

    public ExplosionMark(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        stacks.clear();
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (!(event instanceof EntityDamageByEntityEvent damageEvent)
                || (event instanceof Cancellable cancellable && cancellable.isCancelled())
                || !(damageEvent.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Player player = getPlayer();
        if (player == null || !damageEvent.getDamager().equals(player) || !LocationUtil.isValidTarget(player, target)) {
            return;
        }
        int next = stacks.getOrDefault(target.getUniqueId(), 0) + 1;
        if (next < REQUIRED_STACKS) {
            stacks.put(target.getUniqueId(), next);
            return;
        }
        stacks.remove(target.getUniqueId());
        explode(player, target);
    }

    private void explode(Player player, LivingEntity center) {
        ParticleUtil.spawnParticle(center.getWorld(), Particle.EXPLOSION, center.getLocation(), 2,
                0.4, 0.2, 0.4, 0.0, 1, 64);
        center.getWorld().playSound(center.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.75f, 1.1f);
        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(center.getLocation(), RADIUS, player,
                entity -> true)) {
            target.setNoDamageTicks(0);
            target.damage(DAMAGE, player);
        }
    }
}
