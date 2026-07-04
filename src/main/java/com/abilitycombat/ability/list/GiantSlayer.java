package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.GameManager;
import com.abilitycombat.game.GameState;
import com.abilitycombat.game.Participant;
import com.abilitycombat.ui.SprintHudService;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@AbilityManifest(name = "거인 학살자 (GiantSlayer)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 거인 사냥꾼]",
        "§7플레이어 크기가 §f20%§7 감소하며,",
        "§b신속 I§7 효과를 무제한으로 얻습니다",
        "",
        "§7상대와의 크기 차이 §f10%§7당",
        "§c+15%§7의 추가 피해를 입힙니다",
        "",
        "§8예시: 거인(150%) vs 거인 학살자(80%)",
        "§8크기 차이 70% → 추가 피해 105%"
}, summarize = {
        "§7패시브§f: 크기 -20%, 신속 1, 크기 차이당 추가 피해"
})
public class GiantSlayer extends AbilityBase {

    // 크기 0.8배 (80%)
    private static final double SCALE_MULTIPLIER = 0.8;
    // 크기 차이 10%당 15% 추가 데미지
    private static final double DAMAGE_PER_SIZE_DIFF = 0.15;
    private static final double SIZE_DIFF_THRESHOLD = 0.1;
    private boolean pendingScale = false;
    private boolean scaleApplied = false;

    public GiantSlayer(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        Player player = getPlayer();
        applySlayerStats(player);
        if (shouldDelayScale()) {
            pendingScale = true;
            registerTick();
        } else {
            applyScale(player);
        }
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        Player player = getPlayer();
        pendingScale = false;
        unregisterTick();
        removeScale(player);
        removeSlayerStats(player);
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

    private void applySlayerStats(Player player) {
        if (player == null)
            return;

        // 신속 1 버프 (무제한)
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                PotionEffect.INFINITE_DURATION, 0, false, false, true));
    }

    private void removeSlayerStats(Player player) {
        if (player == null)
            return;

        // 신속 버프 제거
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!attacker.equals(getPlayer())) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        // 자신의 크기
        double myScale = getEntityScale(attacker);
        // 상대의 크기
        double targetScale = getEntityScale(target);

        // 크기 차이 계산 (상대가 더 클 때만 보너스)
        double sizeDiff = targetScale - myScale;
        if (sizeDiff <= 0) {
            return; // 상대가 같거나 작으면 보너스 없음
        }

        // 추가 데미지 계산: 크기 차이 10%당 증가치 반영
        double bonusMultiplier = (sizeDiff / SIZE_DIFF_THRESHOLD) * DAMAGE_PER_SIZE_DIFF;
        modifyDamage(event, OUTGOING_DAMAGE, bonusMultiplier * 100.0, 0.0);
    }

    private double getEntityScale(LivingEntity entity) {
        if (entity == null) {
            return 1.0;
        }
        AbilityCombat plugin = AbilityCombat.getPlugin();
        SprintHudService sprintHudService = plugin != null ? plugin.getSprintHudService() : null;
        if (sprintHudService != null) {
            return sprintHudService.getScaleWithoutDash(entity);
        }
        AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
        if (scale != null) {
            return scale.getValue();
        }
        return 1.0;
    }

    private void applyScale(Player player) {
        if (player == null || scaleApplied) {
            return;
        }
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(scale.getDefaultValue() * SCALE_MULTIPLIER);
            scaleApplied = true;
        }
    }

    private void removeScale(Player player) {
        if (player == null || !scaleApplied) {
            return;
        }
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(scale.getDefaultValue());
        }
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
