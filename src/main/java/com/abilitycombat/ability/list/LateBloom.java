package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

    @AbilityManifest(name = "대기만성 (LateBloom)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.HUMAN, explain = {
            "§e§l[패시브 - 수행]",
            "§7받는 피해가 §c+10%§7 증가합니다.",
            "§7주는 피해가 §c-10%§7 감소합니다. (기본)",
            "",
            "§7자기장 페이즈당 §6(페이즈 - 1) × 5§7 수행 스택을 획득합니다.",
            "§7최대 공격 게이지로 타격 시 §6+1 스택§7 획득",
            "§7수행 1스택당 추가 피해 §a+0.5%§7 증가",
            "§8(50스택 시: -10% + 25% = +15% 추가 피해)",
        "",
        "§e§l[철괴 좌클릭 - 잠력폭발]",
        "§7수행 스택을 모두 소모하고 주변 §f4칸§7 적에게",
        "§c스택 × 20%§7의 피해를 입힙니다.",
        "",
        "§7소모한 스택에 비례해 §f15초§7간",
        "§c스택 × 2%§7 추가 피해 버프를 얻습니다.",
        "§4버프 종료 시 사망합니다."
}, summarize = {
        "§7패시브§f: 받는 피해↑, 스택당 추가 피해↑",
        "§7철괴 좌클릭§f: 스택 폭발 → 버프 → 즉사"
})
public class LateBloom extends AbilityBase implements ActiveHandler {

    private static final double DAMAGE_TAKEN_MULTIPLIER = 1.1; // 받는 피해 +10%
    private static final double BASE_DAMAGE_MODIFIER = -0.1; // 기본 추가 피해 -10%
    private static final double STACK_DAMAGE_PER = 0.005; // 스택당 +0.5%
    private static final int PHASE_STACK_MULTIPLIER = 5; // (페이즈 - 1) × 5 스택
    private static final float ATTACK_COOLDOWN_THRESHOLD = 0.99f; // 최대 공격 게이지 기준
    private static final double EXPLOSION_RANGE = 4.0;
    private static final double EXPLOSION_DAMAGE_PER_STACK = 0.2; // 스택 × 20% 피해
    private static final int BUFF_DURATION_SECONDS = 15;
    private static final double BUFF_DAMAGE_PER_STACK = 0.02; // 스택 × 2% 추가 피해
    private static final String HUD_KEY = "latebloom:stacks";
    private static final int HUD_PRIORITY = 2;

    private int stacks = 0;
    private int lastPhaseIndex = -1;
    private int buffRemainingTicks = 0;
    private double buffDamageMultiplier = 0;
    private final BossBarGauge buffGauge = new BossBarGauge("buff", 10, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);

    public LateBloom(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageByEntityEvent.class);
        // Note: EntityDamageEvent 구독 제거 - EntityDamageByEntityEvent가 EntityDamageEvent를
        // 상속하므로
        // 두 이벤트를 모두 구독하면 handleBridgeEvent가 두 번 호출되어 스택이 중복 증가함
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        clearHud();
        buffGauge.clear();
    }

    @Override
    protected void onDestroy() {
        clearHud();
        buffGauge.clear();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.LEFT_CLICK) {
            return false;
        }
        if (stacks <= 0) {
            return false;
        }

        Player player = getPlayer();
        int consumedStacks = stacks;
        stacks = 0;

        // 폭발 피해
        double explosionDamage = consumedStacks * EXPLOSION_DAMAGE_PER_STACK;
        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(
                player.getLocation(), EXPLOSION_RANGE,
                LocationUtil.withValidTarget(getPlayer(), e -> !e.equals(player)))) {
            target.damage(explosionDamage, player);
        }

        // 폭발 이펙트
        ParticleUtil.spawnParticle(
                player.getWorld(), Particle.EXPLOSION, player.getLocation(), 1, 0, 0, 0, 0, 1, 0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

        // 추가 피해 버프 시작
        buffDamageMultiplier = consumedStacks * BUFF_DAMAGE_PER_STACK;
        buffRemainingTicks = BUFF_DURATION_SECONDS * 20;

        player.sendMessage(Component.text("잠력폭발! ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(consumedStacks + " 스택 소모, ", NamedTextColor.WHITE))
                .append(Component.text("+" + (int) (buffDamageMultiplier * 100) + "% 추가 피해 (15초)",
                        NamedTextColor.RED)));

        updateHud();
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent damageEvent) {
            onDamageByEntity(damageEvent);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = getPlayer();

        // 공격 시: 추가 피해 적용 및 스택 획득
        if (event.getDamager() instanceof Player attacker && attacker.equals(player)) {
            if (!(event.getEntity() instanceof LivingEntity)) {
                return;
            }

            // 추가 피해 계산
            double damageModifier = getPassiveDamageModifier();
            if (buffRemainingTicks > 0) {
                damageModifier += buffDamageMultiplier;
            }
            scaleOutgoingDamage(event, 1 + damageModifier);

            // 최대 공격 게이지 타격 시 스택 획득 (정확히 1스택만)
            if (attacker.getCooledAttackStrength(0) >= ATTACK_COOLDOWN_THRESHOLD) {
                stacks++;
                updateHud();
            }
            return; // 공격 처리 완료, 아래 피해 받기 로직 실행 안 함
        }

        // 피격 시: 받는 피해 증가
        if (event.getEntity() instanceof Player victim && victim.equals(player)) {
            scaleIncomingDamage(event, DAMAGE_TAKEN_MULTIPLIER);
        }
    }

    // Note: onDamage 메서드 제거됨 - 모든 피해 처리가 onDamageByEntity로 통합됨
    // EntityDamageByEntityEvent만 구독하므로 엔티티에 의한 피해만 처리됨
    // (낙하 피해 등 비-엔티티 피해에 대한 +20% 증가는 의도적으로 제외)

    private double getPassiveDamageModifier() {
        return BASE_DAMAGE_MODIFIER + (stacks * STACK_DAMAGE_PER);
    }

    @Override
    public void onTick(int tick) {
        if (isDestroyed()) {
            unregisterTick();
            return;
        }

        // 1초마다 실행
        if (tick % 20 == 0) {
            // 페이즈 스택 획득 체크
            int currentPhase = AbilityCombat.getPlugin().getGameManager().getCurrentPhaseIndex();
            if (currentPhase > lastPhaseIndex) {
                int phasesToAdd = currentPhase - Math.max(0, lastPhaseIndex);
                for (int i = 1; i <= phasesToAdd; i++) {
                    int phaseNum = (lastPhaseIndex < 0 ? 0 : lastPhaseIndex) + i;
                    stacks += Math.max(0, (phaseNum - 1) * PHASE_STACK_MULTIPLIER);
                }
                lastPhaseIndex = currentPhase;
            }

            // 버프 타이머
            if (buffRemainingTicks > 0) {
                buffRemainingTicks -= 20;
                double progress = (double) buffRemainingTicks / (BUFF_DURATION_SECONDS * 20);
                int seconds = Math.max(0, buffRemainingTicks / 20);
                Component title = Component
                        .text("추가 피해 +" + (int) (buffDamageMultiplier * 100) + "% ", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text(seconds + "초", NamedTextColor.WHITE));
                buffGauge.update(title, progress);

                if (buffRemainingTicks <= 0) {
                    // 버프 종료 → 즉사
                    buffGauge.clear();
                    buffDamageMultiplier = 0;
                    Player player = getPlayer();
                    player.sendMessage(Component.text("잠력폭발의 대가로 사망합니다.", NamedTextColor.DARK_RED));
                    player.setHealth(0);
                }
            }

            updateHud();
        }
    }

    private void updateHud() {
        var channel = getActionbarChannel();
        double modifier = getPassiveDamageModifier();
        String modifierStr = (modifier >= 0 ? "+" : "") + (int) (modifier * 100) + "%";
        Component message = Component.text("수행 ", NamedTextColor.GOLD)
                .append(Component.text(stacks + " ", NamedTextColor.WHITE))
                .append(Component.text("(" + modifierStr + " 추가 피해)",
                        modifier >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED));

        if (channel != null) {
            channel.update(getPlayer(), HUD_KEY, HUD_PRIORITY, message);
        } else {
            getPlayer().sendActionBar(message);
        }
    }

    private void clearHud() {
        var channel = getActionbarChannel();
        if (channel != null) {
            channel.clear(getPlayer(), HUD_KEY);
        }
    }
}
