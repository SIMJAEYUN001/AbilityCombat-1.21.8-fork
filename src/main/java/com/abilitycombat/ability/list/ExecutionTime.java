package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "처형 시간 (ExecutionTime)", species = AbilityManifest.Species.SPECIAL, explain = {
        "§e§l[패시브 - 처형 표식]",
        "§7주변 §f8칸§7 내 적에게 §c처형 표식§7을 남깁니다.",
        "§7표식은 §f10초§7 동안 대상이 받은 원본 피해를 기록합니다.",
        "§710초 후 대상은 §c2 + 기록 피해의 40% 고정 피해§7를 받습니다."
}, summarize = {
        "§7패시브§f: 8칸 적에게 10초 처형 표식",
        "§7종료§f: 2 + 기록 피해 40% 고정 피해"
})
public class ExecutionTime extends AbilityBase {

    private static final double MARK_RADIUS = 8.0;
    private static final int MARK_TICKS = 200;
    private static final double BASE_DAMAGE = 2.0;
    private static final double STORED_DAMAGE_RATIO = 0.4;

    private final Map<UUID, Mark> marks = new HashMap<>();

    public ExecutionTime(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageEvent.class);
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        clearMarks();
        unregisterTick();
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (!(event instanceof EntityDamageEvent damageEvent) || damageEvent.isCancelled()
                || !(damageEvent.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Mark mark = marks.get(target.getUniqueId());
        if (mark != null) {
            mark.rawDamage += Math.max(0.0, damageEvent.getDamage());
        }
    }

    @Override
    public void onTick(int tick) {
        Player player = getPlayer();
        if (player == null || player.isDead()) {
            return;
        }
        if (tick % 10 == 0) {
            for (LivingEntity target : LocationUtil.getNearbyLivingEntities(player.getLocation(), MARK_RADIUS, player,
                    entity -> true)) {
                marks.computeIfAbsent(target.getUniqueId(), id -> createMark(target, tick));
            }
        }
        Iterator<Map.Entry<UUID, Mark>> iterator = marks.entrySet().iterator();
        while (iterator.hasNext()) {
            Mark mark = iterator.next().getValue();
            LivingEntity target = resolve(mark.targetId);
            if (target == null || target.isDead()) {
                removeMark(mark);
                iterator.remove();
                continue;
            }
            updateMarker(mark, target);
            if (tick >= mark.triggerTick) {
                dealFixedDamage(target, BASE_DAMAGE + mark.rawDamage * STORED_DAMAGE_RATIO);
                removeMark(mark);
                iterator.remove();
            }
        }
    }

    private Mark createMark(LivingEntity target, int tick) {
        ArmorStand stand = target.getWorld().spawn(markerLocation(target), ArmorStand.class, armorStand -> {
            armorStand.setInvisible(true);
            armorStand.setMarker(true);
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);
            armorStand.getEquipment().setHelmet(new ItemStack(Material.TOTEM_OF_UNDYING));
            armorStand.customName(net.kyori.adventure.text.Component.text("처형 표식",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            armorStand.setCustomNameVisible(false);
        });
        AbilityCombat.markAbilityArmorStand(stand);
        return new Mark(target.getUniqueId(), tick + MARK_TICKS, stand);
    }

    private void updateMarker(Mark mark, LivingEntity target) {
        if (mark.marker != null && mark.marker.isValid()) {
            mark.marker.teleport(markerLocation(target));
        }
    }

    private Location markerLocation(LivingEntity target) {
        return target.getLocation().clone().add(0, target.getHeight() + 0.55, 0);
    }

    private void dealFixedDamage(LivingEntity target, double amount) {
        if (amount <= 0.0 || target.isDead()) {
            return;
        }
        double nextHealth = target.getHealth() - amount;
        target.setHealth(nextHealth <= 0.0 ? 0.0 : nextHealth);
    }

    private LivingEntity resolve(UUID id) {
        org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(id);
        return entity instanceof LivingEntity living ? living : null;
    }

    private void removeMark(Mark mark) {
        if (mark.marker != null && mark.marker.isValid()) {
            mark.marker.remove();
        }
    }

    private void clearMarks() {
        for (Mark mark : marks.values()) {
            removeMark(mark);
        }
        marks.clear();
    }

    private static final class Mark {
        private final UUID targetId;
        private final int triggerTick;
        private final ArmorStand marker;
        private double rawDamage;

        private Mark(UUID targetId, int triggerTick, ArmorStand marker) {
            this.targetId = targetId;
            this.triggerTick = triggerTick;
            this.marker = marker;
        }
    }
}
