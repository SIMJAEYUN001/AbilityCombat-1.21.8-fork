package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@AbilityManifest(name = "도박꾼 (Gambler)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 판돈 재분배]§f §8(쿨타임: 30초)",
        "§7이동속도, 사거리, 최대체력, 받는 피해, 주는 피해가 무작위로 변합니다",
        "§7각 스탯은 §f0~99%§7 범위에서 증가하거나 감소합니다",
        "§7다섯 스탯의 증감 합계는 항상 §f0%§7입니다",
        "§7사용 후 액션바에 현재 스탯 변동량이 표시됩니다"
}, summarize = {
        "§7철괴 우클릭§f: 5개 전투 스탯 0~99% 무작위 조정",
        "§7조건§f: 모든 증감 합계 0%"
})
public class Gambler extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 30;
    private static final int MAX_MAGNITUDE = 99;
    private static final int ACTIONBAR_PRIORITY = 6;
    private static final String ACTIONBAR_KEY = "gambler:stats";

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private int speedPercent;
    private int rangePercent;
    private int healthPercent;
    private int incomingPercent;
    private int outgoingPercent;
    private boolean statsApplied;

    public Gambler(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageEvent.class);
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        clearAttributeModifiers();
        if (getActionbarChannel() != null) {
            getActionbarChannel().clear(getPlayer(), ACTIONBAR_KEY);
        }
        unregisterTick();
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
        rerollStats();
        Player player = getPlayer();
        if (player != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.7f);
        }
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        Player player = getPlayer();
        if (player == null || (event instanceof Cancellable cancellable && cancellable.isCancelled())) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent damageEvent && damageEvent.getDamager().equals(player)) {
            scaleOutgoingDamage(damageEvent, 1.0 + outgoingPercent / 100.0);
        }
        if (event instanceof EntityDamageEvent damageEvent && damageEvent.getEntity().equals(player)) {
            scaleIncomingDamage(damageEvent, Math.max(0.01, 1.0 - incomingPercent / 100.0));
        }
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            if (!statsApplied) {
                rerollStats();
                return;
            }
            updateActionbar();
        }
    }

    private void rerollStats() {
        int[] values = rollZeroSumStats();
        speedPercent = values[0];
        rangePercent = values[1];
        healthPercent = values[2];
        incomingPercent = values[3];
        outgoingPercent = values[4];
        statsApplied = true;
        applyAttributeModifiers();
        updateActionbar();
    }

    private int[] rollZeroSumStats() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 10_000; attempt++) {
            int[] values = new int[5];
            int sum = 0;
            for (int i = 0; i < 4; i++) {
                values[i] = rollPercent(random);
                sum += values[i];
            }
            values[4] = -sum;
            if (isValidPercent(values[4])) {
                return values;
            }
        }
        return new int[] { 99, -99, 50, -50, 0 };
    }

    private int rollPercent(ThreadLocalRandom random) {
        int magnitude = random.nextInt(MAX_MAGNITUDE + 1);
        return random.nextBoolean() ? magnitude : -magnitude;
    }

    private boolean isValidPercent(int value) {
        return Math.abs(value) <= MAX_MAGNITUDE;
    }

    private void applyAttributeModifiers() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        applyScalar(player, Attribute.MOVEMENT_SPEED, "gambler_speed", speedPercent / 100.0);
        applyScalar(player, Attribute.ENTITY_INTERACTION_RANGE, "gambler_range", rangePercent / 100.0);
        applyScalar(player, Attribute.MAX_HEALTH, "gambler_health", healthPercent / 100.0);
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            player.setHealth(Math.min(player.getHealth(), health.getValue()));
        }
    }

    private void clearAttributeModifiers() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        removeModifier(player, Attribute.MOVEMENT_SPEED, "gambler_speed");
        removeModifier(player, Attribute.ENTITY_INTERACTION_RANGE, "gambler_range");
        removeModifier(player, Attribute.MAX_HEALTH, "gambler_health");
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            player.setHealth(Math.min(player.getHealth(), health.getValue()));
        }
    }

    private void applyScalar(Player player, Attribute attribute, String keyName, double scalar) {
        AttributeInstance instance = player.getAttribute(attribute);
        NamespacedKey key = key(keyName);
        if (instance == null || key == null) {
            return;
        }
        instance.removeModifier(key);
        if (Math.abs(scalar) > 1.0E-6) {
            instance.addTransientModifier(new AttributeModifier(key, scalar,
                    AttributeModifier.Operation.ADD_SCALAR));
        }
    }

    private void removeModifier(Player player, Attribute attribute, String keyName) {
        AttributeInstance instance = player.getAttribute(attribute);
        NamespacedKey key = key(keyName);
        if (instance != null && key != null) {
            instance.removeModifier(key);
        }
    }

    private NamespacedKey key(String name) {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin != null ? new NamespacedKey(plugin, name) : null;
    }

    private void updateActionbar() {
        if (getActionbarChannel() == null || getPlayer() == null) {
            return;
        }
        List<Component> parts = new ArrayList<>();
        parts.add(Component.text("이속 " + format(speedPercent), color(speedPercent)));
        parts.add(Component.text(" 사거리 " + format(rangePercent), color(rangePercent)));
        parts.add(Component.text(" 체력 " + format(healthPercent), color(healthPercent)));
        parts.add(Component.text(" 받피감 " + format(incomingPercent), color(incomingPercent)));
        parts.add(Component.text(" 주피 " + format(outgoingPercent), color(outgoingPercent)));
        Component message = Component.text("도박꾼 ", NamedTextColor.GOLD);
        for (Component part : parts) {
            message = message.append(part);
        }
        getActionbarChannel().update(getPlayer(), ACTIONBAR_KEY, ACTIONBAR_PRIORITY, message);
    }

    private String format(int value) {
        return (value >= 0 ? "+" : "") + value + "%";
    }

    private NamedTextColor color(int value) {
        return value >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
    }
}
