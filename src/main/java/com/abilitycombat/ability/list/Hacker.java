package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

@AbilityManifest(name = "해커 (Hacker)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 해킹]§f §8(쿨타임: 90초)",
        "§7주변 §f25칸§7 내 가장 가까운 플레이어를 해킹합니다.",
        "",
        "§7해킹이 진행되는 §f5초§7 동안 대상은",
        "§e기절§7(이동 불가) 상태가 됩니다.",
        "",
        "§7해킹 진행 상황은 자신과 대상에게 표시됩니다.",
        "",
        "§7해킹 완료 시 대상의 §e정확한 좌표§7를 알아냅니다."
}, summarize = {
        "§7철괴 우클릭§f: 5초 해킹 + 기절",
        "§7완료 시§f: 좌표 파악"
})
public class Hacker extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 90;
    private static final int HACK_SECONDS = 5;
    private static final double RANGE = 25.0;
    private static final DustOptions HACK_DUST = new DustOptions(Color.fromRGB(160, 80, 255), 1.0f);

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final String barKey = "hacker:" + Integer.toHexString(System.identityHashCode(this));
    private int remainingHackSeconds = 0;
    private Player target;

    public Hacker(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
    }

    @Override
    public void handleBridgeEvent(Event event) {
        // No events used
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        stopHack(false);
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
        if (isHacking()) {
            return false;
        }
        Player nearest = LocationUtil.getNearestEntity(Player.class, getPlayer().getLocation(), RANGE,
                player -> !player.equals(getPlayer()));
        if (nearest == null) {
            return false;
        }
        target = nearest;
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        startHack();
        return true;
    }

    private void startHack() {
        remainingHackSeconds = HACK_SECONDS;
        if (target != null) {
            target.sendMessage("§e해킹이 시작되었습니다.");
            Stun.apply(target, HACK_SECONDS * 20);
        }
        getPlayer().sendMessage("§e해킹 시작.");
        updateBossBars(HACK_SECONDS);
        registerTick();
    }

    private void stopHack(boolean completed) {
        if (completed && target != null && !target.isDead()) {
            Location location = target.getLocation();
            getPlayer().sendMessage("§e해킹 완료: §f" + location.getBlockX() + ", " + location.getBlockY() + ", "
                    + location.getBlockZ());
            target.sendMessage("§e해킹이 종료되었습니다.");
            Stun.remove(target);
        } else if (target != null) {
            Stun.remove(target);
        }
        clearUi();
        remainingHackSeconds = 0;
        target = null;
    }

    private boolean isHacking() {
        return remainingHackSeconds > 0;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            if (isHacking()) {
                if (target == null || target.isDead()) {
                    stopHack(false);
                    return;
                }
                updateBossBars(remainingHackSeconds);
                updateActionbars(remainingHackSeconds);
                spawnHackParticles(target.getLocation());
                remainingHackSeconds--;
                if (remainingHackSeconds <= 0) {
                    stopHack(true);
                }
            }
        }
    }

    private void updateBossBars(int remaining) {
        var manager = getBossBarManager();
        if (manager == null) {
            return;
        }
        float progress = remaining / (float) HACK_SECONDS;
        Component title = Component.text("해킹 진행 " + remaining + "초", NamedTextColor.LIGHT_PURPLE);
        manager.update(getPlayer(), barKey, 8, title, progress, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        if (target != null) {
            manager.update(target, barKey, 8, title, progress, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        }
    }

    private void updateActionbars(int remaining) {
        var channel = getActionbarChannel();
        if (channel == null) {
            return;
        }
        Component message = Component.text("해킹 진행 " + remaining + "초", NamedTextColor.LIGHT_PURPLE);
        channel.update(getPlayer(), barKey, 5, message);
        if (target != null) {
            channel.update(target, barKey, 5, message);
        }
    }

    private void spawnHackParticles(Location center) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        ParticleUtil.spawnParticle(center.getWorld(), Particle.DUST, center.clone().add(0, 1.0, 0), 12, 0.6, 0.6,
                0.6, 0, HACK_DUST, 2, 0);
    }

    private void clearUi() {
        var manager = getBossBarManager();
        if (manager != null) {
            manager.clear(getPlayer(), barKey);
            if (target != null) {
                manager.clear(target, barKey);
            }
        }
        var channel = getActionbarChannel();
        if (channel != null) {
            channel.clear(getPlayer(), barKey);
            if (target != null) {
                channel.clear(target, barKey);
            }
        }
    }
}
