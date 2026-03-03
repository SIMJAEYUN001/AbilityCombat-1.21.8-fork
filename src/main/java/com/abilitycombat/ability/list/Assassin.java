package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Bleed;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationPool;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.ArrayList;
import java.util.List;

@AbilityManifest(name = "암살자 (Assassin)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 암살]§f §8(쿨타임: 35초)",
        "§f8칸§7 이내에 있는 모든 생명체에게 연속으로 이동하며",
        "§7각각 §c8의 피해§7를 입힙니다.",
        "",
        "§7대미지를 받은 대상은 §c3초§7간 추가로 §4출혈§7 피해를 받습니다.",
        "§7암살 도중에는 §b무적§7 상태가 됩니다."
}, summarize = {
        "§7연속 순간이동 타격 + 출혈§f"
})
public class Assassin extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 35;
    private static final double RANGE = 8.0;
    private static final double DAMAGE = 8.0;
    private static final int BLEED_TICKS = 60;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private int remainingDashTicks = 0;
    private List<LivingEntity> targets = new ArrayList<>();
    private int index;
    private boolean storedInvulnerable;

    public Assassin(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        stopDash();
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
        Player player = getPlayer();
        List<LivingEntity> nearby = new ArrayList<>(com.abilitycombat.utils.LocationUtil.getNearbyLivingEntities(
                player.getLocation(), RANGE,
                com.abilitycombat.utils.LocationUtil.withValidTarget(e -> !e.equals(player))));
        if (nearby.isEmpty()) {
            return false;
        }
        targets = nearby;
        index = 0;
        storedInvulnerable = player.isInvulnerable();
        player.setInvulnerable(true);
        startDash();
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        // No specific events to handle yet
    }

    private void strike(LivingEntity target) {
        Player player = getPlayer();
        player.teleport(LocationPool.copy(target.getLocation()));
        target.damage(DAMAGE, player);
        Bleed.apply(target, BLEED_TICKS, 0.5, player);
    }

    private void startDash() {
        remainingDashTicks = 40;
        registerTick();
    }

    private void stopDash() {
        if (isDashing()) {
            Player player = getPlayer();
            player.setInvulnerable(storedInvulnerable);
            remainingDashTicks = 0;
        }
    }

    private boolean isDashing() {
        return remainingDashTicks > 0;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 2 == 0) {
            if (isDashing()) {
                if (index >= targets.size()) {
                    stopDash();
                    return;
                }
                LivingEntity target = targets.get(index++);
                if (target == null || target.isDead()) {
                    // Skip dead targets but keep ticking if we have more
                    if (index >= targets.size()) {
                        stopDash();
                    }
                    return;
                }
                strike(target);
                remainingDashTicks--;
                if (remainingDashTicks <= 0 || index >= targets.size()) {
                    stopDash();
                }
            }
        }
    }
}
