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

@AbilityManifest(name = "베르투스 (Virtus)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 응보]§f §8(쿨타임: 40초)",
        "§f2.5초§7간 받는 모든 피해가 §b90% 감소§7합니다.",
        "§7능력 사용 시 §8회색 발광§7 효과가 적용됩니다.",
        "",
        "§e§l[반격]",
        "§7피해 감소 효과 중 다른 플레이어에게 피해를 받으면,",
        "§c원래 피해의 100%§7를 공격자에게 §c반사§7하고",
        "§7쿨타임이 §e4초§7로 감소합니다."
}, summarize = {
        "§7철괴 우클릭§f: 2.5초간 피해 90% 감소 + 반격"
})
public class Virtus extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 40;
    private static final int REDUCED_COOLDOWN_SECONDS = 4;
    private static final int DURATION_TICKS = 50; // 2.5초
    private static final double DAMAGE_MULTIPLIER = 0.1;

    private Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private boolean guarding;
    private int guardEndTick = -1;

    public Virtus(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageEvent.class);
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        guarding = false;
        guardEndTick = -1;
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
        // 항상 40초 쿨다운으로 시작
        cooldown = new Cooldown(COOLDOWN_SECONDS);
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent e) {
            onDamageByEntity(e);
        } else if (event instanceof EntityDamageEvent e) {
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
        // 모든 피해 90% 감소
        event.setDamage(event.getDamage() * DAMAGE_MULTIPLIER);
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (!guarding) {
            return;
        }

        // 원래 피해량 저장 (감소 적용 전)
        double originalDamage = event.getDamage();

        // 피해 90% 감소 적용
        event.setDamage(originalDamage * DAMAGE_MULTIPLIER);

        // 넉백 무효화
        Bukkit.getScheduler().runTaskLater(AbilityCombat.getPlugin(), () -> {
            if (player.isOnline() && !player.isDead()) {
                player.setVelocity(player.getVelocity().setX(0).setZ(0));
            }
        }, 1L);

        // 공격자가 플레이어인 경우 반사
        if (event.getDamager() instanceof Player attacker && !attacker.equals(player)) {
            // 원래 피해량 100% 반사
            Bukkit.getScheduler().runTaskLater(AbilityCombat.getPlugin(), () -> {
                if (attacker.isOnline() && !attacker.isDead()) {
                    attacker.damage(originalDamage, player);
                    attacker.playSound(attacker.getLocation(), Sound.ENCHANT_THORNS_HIT, 1.0f, 0.8f);
                    player.playSound(player.getLocation(), Sound.ENCHANT_THORNS_HIT, 1.0f, 0.8f);
                }
            }, 1L);

            // 쿨타임 8초로 감소 - 새 쿨다운 생성
            cooldown.stop(true);
            cooldown = new Cooldown(REDUCED_COOLDOWN_SECONDS);
            cooldown.start();
            applyIronCooldownIfEmpty(REDUCED_COOLDOWN_SECONDS);

            // 가드 종료
            stopGuard();
        }
    }

    private void startGuard() {
        guarding = true;
        guardEndTick = getCurrentTick() + DURATION_TICKS;

        Player player = getPlayer();
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1.0f, 1.2f);

        // 회색 발광 효과
        applyGlowEffect();
    }

    private void stopGuard() {
        guarding = false;
        guardEndTick = -1;
        removeGlowEffect();
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
            stopGuard();
        }
    }
}
