package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

@AbilityManifest(name = "납치 (Kidnap)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 납치]§f §8(쿨타임: 30초)",
        "§f6칸§7 이내의 대상(플레이어/몬스터)을 납치하여 업습니다",
        "§7업힌 대상은 §8실명§7 상태가 되며",
        "§7행동할 수 없습니다 §f8초§7 후 자동으로 풀려납니다",
        "",
        "§7자신은 §b신속I§7 효과를 얻습니다",
        "",
        "§e§l[철괴 좌클릭 - 투척]",
        "§7납치 중인 대상을 바라보는 방향으로 §6던져버립니다§7"
}, summarize = {
        "§7철괴 우클릭§f: 대상 납치 (8초)",
        "§7철괴 좌클릭§f: 던지기"
})
public class Kidnap extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 30;
    private static final int CARRY_SECONDS = 8;
    private static final double RANGE = 6.0;
    private static final double THROW_POWER = 3.5;
    private static final double THROW_HEIGHT = 1.5;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private int remainingCarrySeconds = 0;
    private LivingEntity carried;

    public Kidnap(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(PlayerMoveEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        release(false);
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT) {
            return false;
        }
        if (clickType == ClickType.RIGHT_CLICK) {
            return startCarry();
        }
        if (clickType == ClickType.LEFT_CLICK) {
            return throwTarget();
        }
        return false;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof PlayerMoveEvent) {
            onMove((PlayerMoveEvent) event);
        }
    }

    private void onMove(PlayerMoveEvent event) {
        if (carried == null) {
            return;
        }
        if (event.getPlayer().equals(carried)) {
            event.setTo(event.getFrom());
        }
    }

    @Override
    protected void onDestroy() {
        release(false);
    }

    private boolean startCarry() {
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        if (isCarrying()) {
            return false;
        }
        LivingEntity target = LocationUtil.getEntityLookingAt(LivingEntity.class, getPlayer(), RANGE,
                LocationUtil.withValidTarget(getPlayer(), entity -> !entity.equals(getPlayer())));
        if (target == null) {
            return false;
        }
        carried = target;
        getPlayer().addPassenger(target);
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, CARRY_SECONDS * 20, 0, true, false));
        getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, CARRY_SECONDS * 20, 0, true, false));
        remainingCarrySeconds = CARRY_SECONDS;
        registerTick();
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    private boolean throwTarget() {
        if (carried == null) {
            return false;
        }
        LivingEntity target = carried;
        release(true);
        Vector velocity = getPlayer().getLocation().getDirection().normalize().multiply(THROW_POWER);
        velocity.setY(THROW_HEIGHT);
        target.setVelocity(velocity);
        return true;
    }

    private void release(boolean throwRelease) {
        if (carried == null) {
            return;
        }
        LivingEntity target = carried;
        getPlayer().removePassenger(target);
        if (!throwRelease && target instanceof Player player) {
            player.teleport(getPlayer().getLocation().clone().add(1, 0, 0));
        }
        carried = null;
    }

    private boolean isCarrying() {
        return remainingCarrySeconds > 0;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            if (isCarrying()) {
                remainingCarrySeconds--;
                if (remainingCarrySeconds <= 0) {
                    release(false);
                }
            }
        }
    }
}
