package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.TargetHandler;
import com.abilitycombat.game.Participant;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@AbilityManifest(name = "리버스 (Reverse)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.OTHERS, explain = {
        "§e§l[패시브 - 리버스 지대]",
        "§7자신 주변 §f8칸§7 이내의 모든 적에게",
        "§7넉백 및 끌어당김 효과를 §a반전§7시킵니다.",
        "",
        "§e§l[철괴 우클릭 - 체력 대전환]§f §8(쿨타임: 90초)",
        "§7대상을 §e우클릭§7하여 자신과 대상의 §c체력 비율§7을 교환합니다.",
        "§7교환 시 자신의 체력이 §f20%§7 이하일 경우에만 발동 가능합니다.",
        "",
        "§7교환당한 대상은 §e흡수1 버프§7를 §f10초§7간 획득합니다."
}, summarize = {
        "§7패시브§f: 넉백 반전 지대",
        "§7철괴 우클릭§f: 대상과 체력 교환 (상대 흡수1 10초)"
})
public class Reverse extends AbilityBase implements TargetHandler {

    private static final int COOLDOWN_SECONDS = 90;
    private static final double ZONE_RADIUS = 8.0;
    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final List<LivingEntity> pendingReversals = new ArrayList<>();

    public Reverse(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        pendingReversals.clear();
    }

    @Override
    public void targetSkill(Material material, LivingEntity target) {
        if (material != Material.IRON_INGOT) {
            return;
        }

        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return;
        }

        if (!(target instanceof Player targetPlayer)) {
            getPlayer().sendMessage("§c대상은 플레이어여야 합니다.");
            return;
        }

        double myMaxHealth = getPlayer().getAttribute(Attribute.MAX_HEALTH).getValue();
        double myHealthPercent = getPlayer().getHealth() / myMaxHealth;
        if (myHealthPercent > 0.2) {
            getPlayer().sendMessage("§c체력이 20% 이하일 때만 사용할 수 있습니다.");
            return;
        }

        double targetMaxHealth = targetPlayer.getAttribute(Attribute.MAX_HEALTH).getValue();
        double targetHealthPercent = targetPlayer.getHealth() / targetMaxHealth;

        getPlayer().setHealth(Math.max(1.0, targetHealthPercent * myMaxHealth));
        targetPlayer.setHealth(Math.max(1.0, myHealthPercent * targetMaxHealth));

        // 교환당한 상대에게 흡수1 버프 10초 지급
        targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 10 * 20, 0));

        getPlayer().sendMessage("§a" + targetPlayer.getName() + "님과 체력을 교환했습니다!");
        targetPlayer.sendMessage("§c" + getPlayer().getName() + "의 능력으로 인해 체력이 교환되었습니다! §e(흡수 효과 10초)");

        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        Location center = getPlayer().getLocation();
        if (victim.getLocation().distanceSquared(center) > ZONE_RADIUS * ZONE_RADIUS) {
            return;
        }

        // Check if the victim is an enemy (not self or ally, if applicable)
        // For simplicity, assuming all LivingEntities in range are subject to reversal
        // If specific targeting is needed, add checks here (e.g., isEnemy(victim))

        pendingReversals.add(victim);
    }

    @Override
    public void onTick(int tick) {
        if (pendingReversals.isEmpty()) {
            return;
        }
        for (LivingEntity target : pendingReversals) {
            target.setVelocity(target.getVelocity().multiply(-1));
        }
        pendingReversals.clear();
    }
}
