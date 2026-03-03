package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

@AbilityManifest(name = "상위존재 (HigherBeing)", rank = AbilityManifest.Rank.B, species = AbilityManifest.Species.OTHERS, explain = {
        "§e§l[철괴 우클릭 - 무릎 꿇어라]§f §8(쿨타임: 45초)",
        "§7주변 §f9칸§7 이내 플레이어들의 시선을",
        "§e4초§7간 강제로 §c아래로 고정§7합니다.",
        "",
        "§e§l[패시브 - 위치 선정]",
        "§7자신보다 §fY좌표(높이)§7가 낮은 적을 공격 시",
        "§7높이 차이에 비례해 추가 피해를 줍니다.",
        "",
        "§7추가 피해: 높이 차이 1칸당 §c+1§7 (최대 §c+8§7)"
}, summarize = {
        "§7철괴 우클릭§f: 주변 적 시선 아래로 고정 (4초)",
        "§7패시브§f: 높은 위치에서 추가 피해 (최대 +8)"
})
public class HigherBeing extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 45;
    private static final double LOOK_DOWN_RADIUS = 9.0;
    private static final int LOOK_DOWN_DURATION_TICKS = 80; // 4초

    private static final double BONUS_PER_HEIGHT = 1.0;
    private static final double MAX_BONUS = 8.0;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);

    public HigherBeing(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    public boolean activeSkill(Material material, ActiveHandler.ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ActiveHandler.ClickType.RIGHT_CLICK) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        forceLookDown();
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    private void forceLookDown() {
        Player player = getPlayer();
        Location center = player.getLocation();

        player.playSound(center, Sound.ENTITY_WARDEN_ROAR, 1.0f, 1.2f);

        int affectedCount = 0;
        for (LivingEntity entity : LocationUtil.getNearbyLivingEntities(center, LOOK_DOWN_RADIUS,
                LocationUtil.withValidTarget(e -> !e.equals(player)))) {
            if (!(entity instanceof Player target)) {
                continue;
            }
            affectedCount++;
            applyLookDownEffect(target);
        }

        if (affectedCount > 0) {
            player.sendMessage("§e" + affectedCount + "명의 적을 무릎 꿇렸습니다!");
        }
    }

    private void applyLookDownEffect(Player target) {
        target.playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.5f);

        // 4초간 시선 아래로 고정 (2틱마다) - setRotation 사용으로 이동에 영향 없음
        for (int i = 0; i < LOOK_DOWN_DURATION_TICKS; i += 2) {
            Bukkit.getScheduler().runTaskLater(AbilityCombat.getPlugin(), () -> {
                if (target.isOnline() && !target.isDead()) {
                    float currentYaw = target.getLocation().getYaw();
                    target.setRotation(currentYaw, 90); // yaw 유지, pitch만 90 (아래)
                }
            }, i);
        }
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamage((EntityDamageByEntityEvent) event);
        }
    }

    private void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        double heightDiff = player.getLocation().getY() - target.getLocation().getY();
        if (heightDiff <= 0) {
            return;
        }
        double bonus = Math.min(MAX_BONUS, heightDiff * BONUS_PER_HEIGHT);
        event.setDamage(event.getDamage() + bonus);
    }
}
