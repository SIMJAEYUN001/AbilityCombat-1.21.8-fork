package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerItemHeldEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@AbilityManifest(name = "스왑 (Swap)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 스왑]§f §8(쿨타임: 35초)",
        "§f2초§7간 주변 §f8칸§7 이내 플레이어들의",
        "§7핫바 슬롯을 마구잡이로 변경합니다",
        "",
        "§e§l[슬롯 고정]",
        "§7지속시간 종료 시, 자신을 제외한 대상들의",
        "§7슬롯을 마지막으로 변경된 위치에 §f3초§7간 고정시킵니다"
}, summarize = {
        "§7철괴 우클릭§f: 2초 핫바 스왑 → 3초 고정"
})
public class Swap extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 35;
    private static final int SWAP_SECONDS = 2;
    private static final int LOCK_SECONDS = 3;
    private static final double RANGE = 8.0;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private int remainingSwapTicks = 0;
    private int remainingLockTicks = 0;
    private final Map<Player, Integer> lockedSlots = new HashMap<>();

    public Swap(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(PlayerItemHeldEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        clearSwap();
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
        if (isSwapping()) {
            return false;
        }
        startSwap();
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof PlayerItemHeldEvent) {
            onItemHeld((PlayerItemHeldEvent) event);
        }
    }

    private void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Integer locked = lockedSlots.get(player);
        if (locked == null) {
            return;
        }
        if (event.getNewSlot() != locked) {
            event.setCancelled(true);
        }
    }

    private void startSwap() {
        remainingSwapTicks = SWAP_SECONDS * (20 / 4);
        remainingLockTicks = 0;
        lockedSlots.clear();
        registerTick();
    }

    private void stopSwap() {
        remainingSwapTicks = 0;
        remainingLockTicks = LOCK_SECONDS * 20;
    }

    private void clearSwap() {
        remainingSwapTicks = 0;
        remainingLockTicks = 0;
        lockedSlots.clear();
    }

    private boolean isSwapping() {
        return remainingSwapTicks > 0;
    }

    private boolean isLocked() {
        return remainingLockTicks > 0;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 4 == 0) {
            if (isSwapping()) {
                Player owner = getPlayer();
                for (Player target : owner.getWorld().getPlayers()) {
                    if (target.equals(owner)) {
                        continue;
                    }
                    if (target.getLocation().distanceSquared(owner.getLocation()) > RANGE * RANGE) {
                        continue;
                    }
                    int slot = ThreadLocalRandom.current().nextInt(9);
                    target.getInventory().setHeldItemSlot(slot);
                    lockedSlots.put(target, slot);
                }
                remainingSwapTicks--;
                if (remainingSwapTicks <= 0) {
                    stopSwap();
                }
            }
        }

        if (isLocked()) {
            remainingLockTicks--;
            if (remainingLockTicks <= 0) {
                lockedSlots.clear();
            }
        }
    }
}
