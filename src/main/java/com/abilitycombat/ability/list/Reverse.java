package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.handler.TargetHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@AbilityManifest(name = "리버스 (Reverse)", species = AbilityManifest.Species.OTHERS, explain = {
        "§e§l[패시브 - 리버스 지대]",
        "§7자신 주변 §f8칸§7 이내의 모든 적에게",
        "§7넉백 및 끌어당김 효과를 §a반전§7시킵니다.",
        "",
        "§e§l[철괴 우클릭 - 체력 대전환]§f §8(쿨타임: 90초)",
        "§7대상을 §e우클릭§7하여 자신과 대상의 §c체력 비율§7을 교환합니다. (5칸)",
        "§7교환 시 자신의 체력이 §f20%§7 이하일 경우에만 발동 가능합니다.",
        "",
        "§7교환당한 대상은 §e흡수1 버프§7를 §f10초§7간 획득합니다."
}, summarize = {
        "§7패시브§f: 넉백 반전 지대",
        "§7철괴 우클릭§f: 대상과 체력 교환 (상대 흡수1 10초)"
})
public class Reverse extends AbilityBase implements TargetHandler {

    private static final int COOLDOWN_SECONDS = 90;
    private static final double ACTIVE_RANGE = 5.0;
    private static final double ZONE_RADIUS = 8.0;
    private static final String NO_TARGET_KEY = "reverse:no_player";
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
        Player player = getPlayer();
        Player resolvedTarget = targetPlayer;
        if (resolvedTarget.getLocation().distanceSquared(player.getLocation()) > ACTIVE_RANGE * ACTIVE_RANGE) {
            Player lookingTarget = LocationUtil.getEntityLookingAt(Player.class, player, ACTIVE_RANGE,
                    entity -> LocationUtil.isValidTarget(player, entity));
            if (lookingTarget == null) {
                notifyNoTargetInRange();
                return;
            }
            resolvedTarget = lookingTarget;
        }

        AttributeInstance myMaxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (myMaxHealthAttr == null) {
            return;
        }

        double myMaxHealth = myMaxHealthAttr.getValue();
        double myHealthPercent = player.getHealth() / myMaxHealth;
        if (myHealthPercent > 0.2) {
            player.sendMessage("§c체력이 20% 이하일 때만 사용할 수 있습니다.");
            return;
        }

        AttributeInstance targetMaxHealthAttr = resolvedTarget.getAttribute(Attribute.MAX_HEALTH);
        if (targetMaxHealthAttr == null) {
            return;
        }
        double targetMaxHealth = targetMaxHealthAttr.getValue();
        double targetHealthPercent = resolvedTarget.getHealth() / targetMaxHealth;

        player.setHealth(Math.max(1.0, targetHealthPercent * myMaxHealth));
        resolvedTarget.setHealth(Math.max(1.0, myHealthPercent * targetMaxHealth));

        // 교환당한 상대에게 흡수1 버프 10초 지급
        resolvedTarget.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 10 * 20, 0));

        player.sendMessage("§a" + resolvedTarget.getName() + "님과 체력을 교환했습니다!");
        resolvedTarget.sendMessage("§c" + player.getName() + "의 능력으로 인해 체력이 교환되었습니다! §e(흡수 효과 10초)");

        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
    }

    private void notifyNoTargetInRange() {
        var channel = getActionbarChannel();
        Component message = Component.text("바라보는 대상이 범위 내에 없습니다.", NamedTextColor.RED);
        if (channel != null) {
            channel.update(getPlayer(), NO_TARGET_KEY, 5, message);
            AbilityCombat.getPlugin().getServer().getScheduler().runTaskLater(AbilityCombat.getPlugin(),
                    () -> channel.clear(getPlayer(), NO_TARGET_KEY), 40L);
        } else {
            getPlayer().sendActionBar(message);
        }
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
        if (!com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), victim)) {
            return;
        }

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
