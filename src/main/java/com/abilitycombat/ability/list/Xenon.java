package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

@AbilityManifest(name = "제논 (Xenon)", rank = AbilityManifest.Rank.S, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 방어 시스템]",
        "§f12초§7마다 §e보호막 +10§7을 획득합니다.",
        "§7보호막은 최대 §f20§7까지 중첩됩니다.",
        "",
        "§e§l[철괴 우클릭 - 오버클럭]",
        "§f2초§7 간격으로 §f7회§7 연속 §e보호막 +5§7를 획득합니다.",
        "§7종료 후 §f40초§7간 방어 시스템이 중단됩니다.",
        "",
        "§e§l[보호막 효과]",
        "§7피해를 받으면 §e보호막§7이 먼저 감소합니다.",
        "§7피해가 보호막보다 작으면 §a넉백이 무시§7됩니다.",
        "",
        "§e§l[부작용 - 금단현상]",
        "§6보호막§7이 없으면 받는 피해가 §c30% 증가§7합니다."
}, summarize = {
        "§7패시브§f: 12초마다 보호막 +10 (최대 20)",
        "§7철괴 우클릭§f: 오버클럭 (+5 x7)",
        "§7부작용§f: 보호막 없으면 피해 +30%"
})
public class Xenon extends AbilityBase implements ActiveHandler {

    private static final int NORMAL_INTERVAL = 12;
    private static final int OVERCLOCK_INTERVAL = 2;
    private static final int OVERCLOCK_SHOTS = 7;
    private static final int OVERCLOCK_COOLDOWN = 40;
    private static final double MAX_SHIELD = 20.0;
    private static final double NORMAL_SHIELD = 10.0;
    private static final double OVERCLOCK_SHIELD = 5.0;

    private final BossBarGauge shieldGauge = new BossBarGauge("shield", 5, BossBar.Color.YELLOW,
            BossBar.Overlay.PROGRESS);

    private double shield;
    private int secondsUntilNextCharge;
    private int overclockShots;
    private int pauseSeconds;
    private boolean initialized = false;
    private boolean applyingDamage = false; // 재귀 호출 방지

    public Xenon(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        shield = 0;
        registerTick();
        subscribeEvent(EntityDamageEvent.class);
        secondsUntilNextCharge = 1;
        initialized = false;
        updateShieldBar();
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        shieldGauge.clear();
    }

    @Override
    public boolean activeSkill(Material material, ActiveHandler.ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ActiveHandler.ClickType.RIGHT_CLICK) {
            return false;
        }
        if (overclockShots > 0 || pauseSeconds > 0) {
            return false;
        }
        overclockShots = OVERCLOCK_SHOTS;
        secondsUntilNextCharge = 0;
        getPlayer().sendMessage(Component.text("오버클럭 시작!", NamedTextColor.GOLD));
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent) {
            onDamage((EntityDamageEvent) event);
        }
    }

    private void onDamage(EntityDamageEvent event) {
        if (!event.getEntity().equals(getPlayer())) {
            return;
        }
        if (applyingDamage) {
            return;
        }

        Player player = getPlayer();
        double damage = event.getFinalDamage();

        // 보호막이 없으면 +30% 피해
        if (shield <= 0) {
            event.setDamage(event.getDamage() * 1.3);
            return;
        }

        // 보호막 처리
        event.setCancelled(true);

        if (damage <= shield) {
            // 피해 < 보호막: 보호막만 감소, 넉백 무시
            shield -= damage;
            Vector currentVel = player.getVelocity().clone();
            player.getScheduler().runDelayed(com.abilitycombat.AbilityCombat.getPlugin(), task -> {
                player.setVelocity(currentVel);
            }, null, 1L);
        } else {
            // 피해 >= 보호막: 남은 피해 HP에 적용 + 넉백
            double remainingDamage = damage - shield;
            shield = 0;
            // 0.001 데미지로 넉백 트리거
            applyingDamage = true;
            try {
                player.damage(0.001);
            } finally {
                applyingDamage = false;
            }
            applyDamage(remainingDamage - 0.001); // 넉백 데미지 보정
        }

        updateShieldBar();
    }

    private void applyDamage(double damage) {
        Player player = getPlayer();
        if (player == null || player.isDead()) {
            return;
        }
        applyingDamage = true;
        try {
            double newHealth = player.getHealth() - damage;
            if (newHealth <= 0) {
                player.setHealth(0);
            } else {
                player.setHealth(newHealth);
            }
        } finally {
            applyingDamage = false;
        }
    }

    private void addShield(double amount) {
        shield = Math.min(MAX_SHIELD, shield + amount);
        updateShieldBar();
    }

    private void updateShieldBar() {
        if (shield <= 0) {
            shieldGauge.clear();
            return;
        }
        double progress = shield / MAX_SHIELD;
        Component title = Component.text("보호막 ", NamedTextColor.YELLOW)
                .append(Component.text(String.format("%.1f", shield), NamedTextColor.WHITE))
                .append(Component.text(" / " + (int) MAX_SHIELD, NamedTextColor.GRAY));
        shieldGauge.update(title, progress);
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            Player player = getPlayer();
            if (player == null || !player.isOnline()) {
                return;
            }

            // 휴식 중
            if (pauseSeconds > 0) {
                pauseSeconds--;
                return;
            }

            // 첫 초기화 시 즉시 충전
            if (!initialized) {
                initialized = true;
                addShield(NORMAL_SHIELD);
                secondsUntilNextCharge = overclockShots > 0 ? OVERCLOCK_INTERVAL : NORMAL_INTERVAL;
                return;
            }

            // 타이머 감소
            secondsUntilNextCharge--;

            // 충전 시간 도달
            if (secondsUntilNextCharge <= 0) {
                if (overclockShots > 0) {
                    addShield(OVERCLOCK_SHIELD);
                    overclockShots--;
                    if (overclockShots == 0) {
                        pauseSeconds = OVERCLOCK_COOLDOWN;
                        getPlayer().sendMessage(
                                Component.text("오버클럭 종료. " + OVERCLOCK_COOLDOWN + "초간 휴식.", NamedTextColor.RED));
                    }
                } else {
                    addShield(NORMAL_SHIELD);
                }

                secondsUntilNextCharge = overclockShots > 0 ? OVERCLOCK_INTERVAL : NORMAL_INTERVAL;
            }
        }
    }
}
