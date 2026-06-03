package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "베르투스 (Virtus)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 응보]§f §8(쿨타임: 40초)",
        "§f3초§7간 받는 모든 피해가 §b90% 감소§7합니다.",
        "§7능력 사용 시 §8회색 발광§7 효과가 적용됩니다.",
        "",
        "§e§l[반격]",
        "§7피해 감소 시간 동안 다른 플레이어에게 받은 피해를 축적하고,",
        "§7종료 시 축적 피해의 §c40%§7를 공격자에게 한 번에 반사합니다.",
        "§7반사 성공 시 쿨타임이 §e4초§7로 감소합니다.",
        "§7반사하지 못하고 끝나면 쿨타임이 §e25초§7로 감소합니다."
}, summarize = {
        "§7철괴 우클릭§f: 3초간 피해 90% 감소 + 누적 반격"
})
public class Virtus extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 40;
    private static final int REFLECT_COOLDOWN_SECONDS = 4;
    private static final int NO_REFLECT_COOLDOWN_SECONDS = 25;
    private static final int DURATION_TICKS = 60; // 3초
    private static final double DAMAGE_MULTIPLIER = 0.1;
    private static final double REFLECT_RATIO = 0.4;

    private Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private boolean guarding;
    private int guardEndTick = -1;
    private final Map<UUID, Double> accumulatedDamageByAttacker = new HashMap<>();

    public Virtus(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        guarding = false;
        guardEndTick = -1;
        accumulatedDamageByAttacker.clear();
        removeGlowEffect();
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
        if (guarding) {
            return false;
        }
        startGuard();
        startCooldown(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent e) {
            onDamage(e);
        }
    }

    private void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (!guarding) {
            return;
        }
        double originalFinalDamage = getCalculatedFinalDamage(event);
        if (originalFinalDamage <= 0.0) {
            return;
        }

        // 피해 90% 감소 적용
        scaleIncomingDamage(event, DAMAGE_MULTIPLIER);

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Bukkit.getScheduler().runTaskLater(AbilityCombat.getPlugin(), () -> {
                if (player.isOnline() && !player.isDead()) {
                    player.setVelocity(player.getVelocity().setX(0).setZ(0));
                }
            }, 1L);

            if (byEntity.getDamager() instanceof Player attacker && !attacker.equals(player)
                    && AbilityCombat.getPlugin().getGameManager().canApplyNegativeEffect(player, attacker)) {
                accumulatedDamageByAttacker.merge(attacker.getUniqueId(), originalFinalDamage, Double::sum);
            }
        }
    }

    private void startGuard() {
        guarding = true;
        guardEndTick = getCurrentTick() + DURATION_TICKS;
        accumulatedDamageByAttacker.clear();

        Player player = getPlayer();
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1.0f, 1.2f);

        // 회색 발광 효과
        applyGlowEffect();
    }

    private void stopGuard(boolean naturalEnd) {
        boolean reflected = false;
        Player player = getPlayer();
        if (naturalEnd && guarding && player != null && player.isOnline() && !player.isDead()) {
            reflected = reflectAccumulatedDamage(player);
            startCooldown(reflected ? REFLECT_COOLDOWN_SECONDS : NO_REFLECT_COOLDOWN_SECONDS);
        }
        guarding = false;
        guardEndTick = -1;
        accumulatedDamageByAttacker.clear();
        removeGlowEffect();
    }

    private boolean reflectAccumulatedDamage(Player player) {
        boolean reflected = false;
        for (Map.Entry<UUID, Double> entry : accumulatedDamageByAttacker.entrySet()) {
            Player attacker = Bukkit.getPlayer(entry.getKey());
            double reflectedDamage = entry.getValue() * REFLECT_RATIO;
            if (attacker == null || !attacker.isOnline() || attacker.isDead() || reflectedDamage <= 0.0) {
                continue;
            }
            attacker.damage(reflectedDamage, player);
            attacker.playSound(attacker.getLocation(), Sound.ENCHANT_THORNS_HIT, 1.0f, 0.8f);
            reflected = true;
        }
        if (reflected) {
            player.playSound(player.getLocation(), Sound.ENCHANT_THORNS_HIT, 1.0f, 0.8f);
        }
        return reflected;
    }

    private void startCooldown(int seconds) {
        if (cooldown != null && cooldown.isCooldown()) {
            cooldown.stop(true);
        }
        cooldown = new Cooldown(seconds);
        cooldown.start();
        applyIronCooldownIfEmpty(seconds);
    }

    private void applyGlowEffect() {
        Player player = getPlayer();
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, DURATION_TICKS, 0, false, false));

        // 회색 팀으로 발광 색상 설정
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team grayTeam = scoreboard.getTeam("virtus_gray");
        if (grayTeam == null) {
            grayTeam = scoreboard.registerNewTeam("virtus_gray");
            grayTeam.color(net.kyori.adventure.text.format.NamedTextColor.GRAY);
        }
        grayTeam.addEntry(player.getName());
    }

    private void removeGlowEffect() {
        Player player = getPlayer();
        if (player == null)
            return;

        player.removePotionEffect(PotionEffectType.GLOWING);

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team grayTeam = scoreboard.getTeam("virtus_gray");
        if (grayTeam != null) {
            grayTeam.removeEntry(player.getName());
        }
    }

    private int currentTick = 0;

    private int getCurrentTick() {
        return currentTick;
    }

    @Override
    public void onTick(int tick) {
        currentTick = tick;

        // 가드 시간 체크
        if (guarding && guardEndTick > 0 && tick >= guardEndTick) {
            stopGuard(true);
        }
    }
}
