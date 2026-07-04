package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.ParticleUtil;
import com.abilitycombat.vfx.Circle;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "감쇠광선 (DecayRay)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 약화]",
        "§7최대 공격 쿨다운으로 플레이어를 타격하면",
        "§f8초§7간 §c약화 스택§7을 §f1§7 부여합니다 (최대 §f3§7스택)",
        "",
        "§7약화가 적용된 적은 §f크기 10%§7 감소",
        "§7스택당 입히는 데미지가 §c10%§7 감소합니다",
        "",
        "§e§l[철괴 우클릭 - 감쇠 폭발]§f §8(쿨타임: 40초)",
        "§7주변 §e12칸§7의 플레이어에게 약화 §f3스택§7 즉시 부여",
        "§7적용된 적 1명당 §c+10%§7 데미지 버프 (10초)"
}, summarize = {
        "§7패시브§f: 타격 시 약화 스택 부여",
        "§7철괴 우클릭§f: 범위 약화 + 데미지 버프"
})
public class DecayRay extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 40;
    private static final int WEAKNESS_DURATION_SECONDS = 8;
    private static final int WEAKNESS_DURATION_TICKS = WEAKNESS_DURATION_SECONDS * 20;
    private static final int MAX_STACKS = 3;
    private static final double SIZE_REDUCTION = 0.9; // 10% 감소
    private static final double DAMAGE_REDUCTION_PER_STACK = 0.1; // 스택당 10% 감소
    private static final double ACTIVE_RANGE = 12.0;
    private static final int BUFF_DURATION_SECONDS = 10;
    private static final double BUFF_DAMAGE_PER_TARGET = 0.1; // 타겟당 10% 추가 데미지

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final Map<UUID, WeaknessData> weaknessMap = new HashMap<>();
    private final BossBarGauge buffGauge = new BossBarGauge("buff", 15, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);

    private int buffRemainingTicks = 0;
    private double buffDamageMultiplier = 0;

    public DecayRay(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        // 모든 약화 대상의 크기 복구
        for (Map.Entry<UUID, WeaknessData> entry : weaknessMap.entrySet()) {
            Player target = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (target != null) {
                restoreScale(target, entry.getValue());
            }
        }
        weaknessMap.clear();
        buffGauge.clear();
        buffRemainingTicks = 0;
        buffDamageMultiplier = 0;
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

        Player player = getPlayer();
        Location center = player.getLocation();

        // 범위 이펙트
        spawnRangeEffect(center);

        // 범위 내 플레이어에게 약화 3스택 부여
        int affectedCount = 0;
        for (Player target : player.getWorld().getPlayers()) {
            if (target.equals(player))
                continue;
            if (target.getLocation().distance(center) <= ACTIVE_RANGE) {
                applyWeakness(target, MAX_STACKS);
                affectedCount++;
            }
        }

        // 데미지 버프 적용
        if (affectedCount > 0) {
            buffDamageMultiplier = affectedCount * BUFF_DAMAGE_PER_TARGET;
            buffRemainingTicks = BUFF_DURATION_SECONDS * 20;
            player.sendMessage("§d감쇠 폭발! §f" + affectedCount + "§7명에게 약화 적용, §c+" +
                    (int) (buffDamageMultiplier * 100) + "%§7 데미지 버프 (10초)");
            registerTick();
        }

        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    private void spawnRangeEffect(Location center) {
        for (org.bukkit.util.Vector offset : Circle.of(ACTIVE_RANGE, 36)) {
            Location loc = center.clone().add(offset);
            ParticleUtil.spawnParticle(
                    center.getWorld(),
                    Particle.DUST,
                    loc.clone().add(0, 0.5, 0),
                    1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.PURPLE, 1.5f),
                    2,
                    0);
        }
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) { // Removed @EventHandler, changed to private
        // 공격 시: 패시브 스택 부여
        if (event.getDamager() instanceof Player attacker && attacker.equals(getPlayer())) {
            if (event.getEntity() instanceof Player target) {
                // 최대 공격 쿨다운 체크
                if (attacker.getCooledAttackStrength(0) >= 0.99f) {
                    applyWeakness(target, 1);
                }
            }

            // 버프 데미지 적용
            if (buffRemainingTicks > 0 && buffDamageMultiplier > 0) {
                modifyDamage(event, OUTGOING_DAMAGE, buffDamageMultiplier * 100.0, 0.0);
            }
        }

        // 약화 대상이 공격할 때: 데미지 감소
        if (event.getDamager() instanceof Player attacker) {
            WeaknessData data = weaknessMap.get(attacker.getUniqueId());
            if (data != null && data.stacks > 0) {
                double reduction = data.stacks * DAMAGE_REDUCTION_PER_STACK;
                modifyDamage(event, OUTGOING_DAMAGE, -reduction * 100.0, 0.0);
            }
        }
    }

    private void applyWeakness(Player target, int stacksToAdd) {
        UUID uuid = target.getUniqueId();
        WeaknessData data = weaknessMap.get(uuid);

        if (data == null) {
            data = new WeaknessData();
            weaknessMap.put(uuid, data);
        }

        // 스택 추가 (최대 제한)
        data.stacks = Math.min(MAX_STACKS, data.stacks + stacksToAdd);
        data.remainingTicks = WEAKNESS_DURATION_TICKS;

        // 크기 감소 적용 (1회만)
        if (!data.sizeReduced) {
            applyScaleReduction(target, data);
            data.sizeReduced = true;
        }
        registerTick();
    }

    private void applyScaleReduction(Player player, WeaknessData data) {
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale != null) {
            // 대시 같은 임시 modifier가 섞이지 않도록 능력이 소유한 base 크기만 저장합니다.
            data.originalScale = scale.getBaseValue();
            scale.setBaseValue(data.originalScale * SIZE_REDUCTION);
        }
    }

    private void restoreScale(Player player, WeaknessData data) {
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale != null && data != null && data.originalScale > 0) {
            // 저장된 원래 크기로 복원
            scale.setBaseValue(data.originalScale);
        }
    }

    private class WeaknessData {
        int stacks = 0;
        int remainingTicks = 0;
        boolean sizeReduced = false;
        double originalScale = 0;
    }

    @Override
    public void onTick(int tick) {
        if (isDestroyed()) {
            unregisterTick();
            return;
        }

        // 1초마다 실행 (20틱)
        if (tick % 20 == 0) {
            boolean active = false;

            // Weakness Map Update
            Iterator<Map.Entry<UUID, WeaknessData>> iterator = weaknessMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, WeaknessData> entry = iterator.next();
                WeaknessData data = entry.getValue();
                data.remainingTicks -= 20;

                if (data.remainingTicks <= 0) {
                    Player target = org.bukkit.Bukkit.getPlayer(entry.getKey());
                    if (target != null) {
                        restoreScale(target, data);
                    }
                    iterator.remove();
                } else {
                    active = true;
                }
            }

            // Buff Timer Update
            if (buffRemainingTicks > 0) {
                buffRemainingTicks -= 20;
                double progress = (double) buffRemainingTicks / (BUFF_DURATION_SECONDS * 20);
                int seconds = Math.max(0, buffRemainingTicks / 20);
                Component title = Component
                        .text("데미지 +" + (int) (buffDamageMultiplier * 100) + "% ", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text(seconds + "초", NamedTextColor.WHITE));
                buffGauge.update(title, progress);

                if (buffRemainingTicks <= 0) {
                    buffDamageMultiplier = 0;
                    buffGauge.clear();
                } else {
                    active = true;
                }
            }

            if (!active) {
                unregisterTick();
            }
        }
    }
}
