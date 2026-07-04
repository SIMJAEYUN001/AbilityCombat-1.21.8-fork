package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.GameManager;
import com.abilitycombat.game.GameState;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.ScaleAttributeUtil;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

@AbilityManifest(name = "거인 (Giant)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 거인의 힘]",
        "§7플레이어 크기가 §f50%§7 증가합니다",
        "§7최대 체력이 §c50%§7 증가합니다 (30 HP)",
        "§7모든 공격의 데미지가 §c25%§7 증가합니다",
        "§7공격 사거리가 §e25%§7 증가합니다"
}, summarize = {
        "§7패시브§f: 크기/체력/데미지/사거리 증가"
})
public class Giant extends AbilityBase {

    // 크기 1.5배 (150%)
    private static final double SCALE_MULTIPLIER = 1.5;
    private static final String SCALE_MODIFIER_KEY = "giant_scale";
    // 체력 1.5배 (30 HP)
    private static final double HEALTH_MULTIPLIER = 1.5;
    // 데미지 1.25배
    private static final double DAMAGE_MULTIPLIER = 1.25;
    // 사거리 1.25배
    private static final double RANGE_MULTIPLIER = 1.25;
    private boolean pendingScale = false;
    private boolean scaleApplied = false;

    public Giant(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        Player player = getPlayer();
        applyGiantStats(player);
        if (shouldDelayScale()) {
            pendingScale = true;
            registerTick();
        } else {
            applyScale(player);
        }
    }

    @Override
    protected void onDeactivate() {
        Player player = getPlayer();
        pendingScale = false;
        unregisterTick();
        removeScale(player);
        removeGiantStats(player);
    }

    @Override
    public void onTick(int tick) {
        if (isDestroyed()) {
            pendingScale = false;
            unregisterTick();
            return;
        }
        if (!pendingScale) {
            unregisterTick();
            return;
        }
        if (isScaleReady()) {
            applyScale(getPlayer());
            pendingScale = false;
            unregisterTick();
        }
    }

    private void applyGiantStats(Player player) {
        if (player == null)
            return;

        // 최대 체력 증가
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            double newMax = health.getDefaultValue() * HEALTH_MULTIPLIER;
            health.setBaseValue(newMax);
            // 체력도 비례해서 증가
            player.setHealth(Math.min(player.getHealth() * HEALTH_MULTIPLIER, newMax));
        }

        // 공격력 증가
        AttributeInstance damage = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getDefaultValue() * DAMAGE_MULTIPLIER);
        }

        // 공격 사거리 증가
        AttributeInstance range = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (range != null) {
            range.setBaseValue(range.getDefaultValue() * RANGE_MULTIPLIER);
        }
    }

    private void removeGiantStats(Player player) {
        if (player == null)
            return;

        // 최대 체력 복구
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getDefaultValue());
            player.setHealth(Math.min(player.getHealth(), health.getValue()));
        }

        // 공격력 복구
        AttributeInstance damage = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getDefaultValue());
        }

        // 공격 사거리 복구
        AttributeInstance range = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (range != null) {
            range.setBaseValue(range.getDefaultValue());
        }
    }

    private void applyScale(Player player) {
        if (player == null || scaleApplied) {
            return;
        }
        if (ScaleAttributeUtil.applyBaseScalar(player, SCALE_MODIFIER_KEY, SCALE_MULTIPLIER - 1.0D)) {
            scaleApplied = true;
        }
    }

    private void removeScale(Player player) {
        if (player == null || !scaleApplied) {
            return;
        }
        ScaleAttributeUtil.removeScaleModifier(player, SCALE_MODIFIER_KEY);
        scaleApplied = false;
    }

    private boolean shouldDelayScale() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null) {
            return false;
        }
        GameManager gameManager = plugin.getGameManager();
        if (gameManager == null) {
            return false;
        }
        GameState state = gameManager.getState();
        return state == GameState.SELECTING || (state == GameState.RUNNING && gameManager.isInvincible());
    }

    private boolean isScaleReady() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null) {
            return true;
        }
        GameManager gameManager = plugin.getGameManager();
        if (gameManager == null) {
            return true;
        }
        return gameManager.getState() == GameState.RUNNING && !gameManager.isInvincible();
    }
}
