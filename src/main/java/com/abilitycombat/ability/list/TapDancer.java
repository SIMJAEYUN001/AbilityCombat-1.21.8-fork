package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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
        "§7근접 공격이 적중할 때마다 §b템포 1스택§7을 얻습니다.",
        "§7스택당 이동 속도가 §f+0.012§7 증가하며 최대 §f8스택§7까지 누적됩니다.",
        "§7§f4초§7간 전투가 없으면 스택이 §c1씩 감소§7합니다.",
        "§74스택 이상이면 지면에서 §e발소리 이펙트§7가 강조됩니다."
}, summarize = {
        "§7패시브§f: 근접 공격 시 이동 속도 +0.012 x 스택",
        "§7최대§f: 8스택, 4초 미전투마다 -1"
})
public class TapDancer extends AbilityBase {

    private static final int MAX_STACK = 8;
    private static final double SPEED_PER_STACK = 0.012;
    private static final int DECAY_IDLE_TICKS = 80;
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
            idleTicks = 0;
            stack = Math.max(0, stack - 1);
            applySpeed();
            updateGauge();
            if (stack <= 0) {
                stackGauge.clear();
                unregisterTick();
                return;
            }
        }
        if (tick % 4 == 0 && stack >= MAX_STACK / 2 && player.isOnGround()) {
            playStepEffect(player);
        }
    }

    private void addStack() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        idleTicks = 0;
        if (stack < MAX_STACK) {
            stack++;
            if (stack == MAX_STACK) {
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 2.0f);
            }
        }
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
                .append(Component.text(stack + "/" + MAX_STACK, NamedTextColor.WHITE))
                .append(Component.text("  이동속도 +" + String.format("%.3f", SPEED_PER_STACK * stack),
                        NamedTextColor.GRAY));
        stackGauge.update(title, stack / (double) MAX_STACK);
    }

    private void playStepEffect(Player player) {
        Location feet = player.getLocation().clone().add(0, 0.08, 0);
        World world = feet.getWorld();
        if (world == null) {
            return;
        }
        float volume = Math.min(0.5f, stack / 18.0f);
        world.playSound(feet, Sound.BLOCK_CHAIN_STEP, volume, 1.2f + stack * 0.04f);
        ParticleUtil.spawnParticle(world, Particle.CRIT, feet, 3, 0.22, 0.04, 0.22, 0.02, 2, 64);
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
