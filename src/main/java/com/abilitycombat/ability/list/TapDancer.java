package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

@AbilityManifest(name = "탭 댄서 (TapDancer)", species = AbilityManifest.Species.SPECIAL, explain = {
        "§e§l[패시브 - 템포]",
        "§7근접 공격이 적중할 때마다 §b템포 1스택§7을 얻습니다",
        "§7스택 제한 없이 누적되며 스택당 이동 속도가 §f+0.012§7 증가합니다",
        "§7§f3초§7간 전투가 없으면 모든 템포 스택이 사라집니다"
}, summarize = {
        "§7패시브§f: 근접 공격 시 이동 속도 +0.012 x 스택",
        "§7제한 없음§f: 3초 미전투 시 전체 소멸"
})
public class TapDancer extends AbilityBase {

    private static final double SPEED_PER_STACK = 0.012;
    private static final int DECAY_IDLE_TICKS = 60;
    private static final int GAUGE_PRIORITY = 5;

    private final BossBarGauge stackGauge = new BossBarGauge("tempo", GAUGE_PRIORITY, BossBar.Color.YELLOW,
            BossBar.Overlay.NOTCHED_10);
    private int stack;
    private int idleTicks;

    public TapDancer(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unsubscribeEvent(EntityDamageByEntityEvent.class);
        clearStacks();
    }

    @Override
    protected void onDestroy() {
        clearStacks();
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (!(event instanceof EntityDamageByEntityEvent damageEvent)
                || (event instanceof Cancellable cancellable && cancellable.isCancelled())) {
            return;
        }
        Player player = getPlayer();
        if (player == null || !damageEvent.getDamager().equals(player)
                || !(damageEvent.getEntity() instanceof LivingEntity target)
                || !LocationUtil.isValidTarget(player, target)) {
            return;
        }
        addStack();
    }

    @Override
    public void onTick(int tick) {
        Player player = getPlayer();
        if (player == null || stack <= 0) {
            clearStacks();
            return;
        }
        idleTicks++;
        if (idleTicks >= DECAY_IDLE_TICKS) {
            clearStacks();
            return;
        }
        updateGauge();
    }

    private void addStack() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        idleTicks = 0;
        stack++;
        applySpeed();
        updateGauge();
        registerTick();
    }

    private void applySpeed() {
        Player player = getPlayer();
        NamespacedKey key = tempoKey();
        if (player == null || key == null) {
            return;
        }
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(key);
        if (stack > 0) {
            speed.addTransientModifier(new AttributeModifier(key, SPEED_PER_STACK * stack,
                    AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void updateGauge() {
        if (stack <= 0) {
            stackGauge.clear();
            return;
        }
        Component title = Component.text("템포 ", NamedTextColor.YELLOW)
                .append(Component.text(stack + "스택", NamedTextColor.WHITE))
                .append(Component.text("  이동속도 +" + String.format("%.3f", SPEED_PER_STACK * stack),
                        NamedTextColor.GRAY));
        double remainingRatio = 1.0 - ((double) idleTicks / DECAY_IDLE_TICKS);
        stackGauge.update(title, Math.max(0.0, Math.min(1.0, remainingRatio)));
    }

    private void clearStacks() {
        stack = 0;
        idleTicks = 0;
        applySpeed();
        stackGauge.clear();
        unregisterTick();
    }

    private NamespacedKey tempoKey() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin == null ? null : new NamespacedKey(plugin, "tapdancer_tempo_speed");
    }
}
