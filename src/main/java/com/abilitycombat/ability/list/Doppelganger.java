package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityFactory;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.ability.handler.TargetHandler;
import com.abilitycombat.game.GameManager;
import com.abilitycombat.game.GameState;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@AbilityManifest(name = "도플갱어 (Doppelganger)", species = AbilityManifest.Species.HUMAN,
        chaosMode = AbilityManifest.ChaosMode.EXCLUDE_ALL, explain = {
        "§e§l[패시브 - 복제]",
        "§7능력 선택이 모두 끝나면 생존자 중 랜덤한 한 명의",
        "§7능력을 복제합니다 (복제 대상은 본인에게만 공개)",
        "",
        "§e§l[조건]",
        "§7복제 대상을 §c직접 처치§7하면 최대 체력이 §c10§7 증가합니다",
        "§7복제 대상을 처치하지 못하고 대상이 사망하면 §c즉시 사망§7합니다"
}, summarize = {
        "§7게임 시작 시§f: 생존자 1명의 능력 복제",
        "§7대상 처치§f: 최대 체력 +10",
        "§7실패§f: 즉시 사망"
})
public class Doppelganger extends AbilityBase implements ActiveHandler, TargetHandler {

    private static final double BONUS_HEALTH = 10.0;

    private boolean copied;
    private UUID targetUuid;
    private boolean resolved;

    private AbilityBase copiedAbility;

    public Doppelganger(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(PlayerDeathEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        destroyCopiedAbility();
    }

    @Override
    protected void onDestroy() {
        destroyCopiedAbility();
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof PlayerDeathEvent e) {
            onPlayerDeath(e);
        }
    }

    @Override
    public void onTick(int tick) {
        if (!copied) {
            tryCopyOnGameStart();
            return;
        }
        if (!resolved && tick % 20 == 0) {
            checkTargetStillValid();
        }
    }

    private void tryCopyOnGameStart() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        GameManager gameManager = plugin != null ? plugin.getGameManager() : null;
        if (gameManager == null || gameManager.getState() != GameState.RUNNING) {
            return;
        }

        Player self = getPlayer();
        if (self == null) {
            return;
        }

        List<Player> candidates = getCopyCandidates(self, gameManager, true);
        if (candidates.isEmpty()) {
            candidates = getCopyCandidates(self, gameManager, false);
        }
        if (candidates.isEmpty()) {
            copied = true;
            resolved = true;
            self.sendMessage("§c[도플갱어] §f복제할 대상이 없습니다");
            return;
        }

        Player target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Participant targetParticipant = gameManager.getParticipant(target.getUniqueId());
        AbilityBase targetAbility = targetParticipant != null ? targetParticipant.getAbility() : null;

        if (targetAbility == null || targetAbility instanceof Doppelganger) {
            copied = true;
            resolved = true;
            self.sendMessage("§c[도플갱어] §f복제할 대상의 능력을 찾을 수 없습니다");
            return;
        }

        copied = true;
        targetUuid = target.getUniqueId();

        self.sendMessage("§e[도플갱어] §f복제 대상: §c" + target.getName() + "§f (" + targetAbility.getName() + ")");
        target.sendMessage("§c[도플갱어] §f당신의 능력이 누군가에게 복제되었습니다");

        try {
            copiedAbility = AbilityFactory.create(targetAbility.getClass(), getParticipant());
            copiedAbility.activate();
            // 게임 시작 시 복제한 능력의 /aw info 를 바로 출력합니다
            sendCopiedAbilityInfo(self);
        } catch (Exception ex) {
            copiedAbility = null;
            resolved = true;
            self.sendMessage("§c[도플갱어] §f능력 복제에 실패했습니다");
        }
    }

    private List<Player> getCopyCandidates(Player self, GameManager gameManager, boolean excludeDoppelganger) {
        List<Player> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || player.equals(self)) {
                continue;
            }
            if (!LocationUtil.isValidTarget(getPlayer(), player)) {
                continue;
            }
            Participant participant = gameManager.getParticipant(player.getUniqueId());
            if (participant == null || participant.getAbility() == null) {
                continue;
            }
            if (excludeDoppelganger && participant.getAbility() instanceof Doppelganger) {
                continue;
            }
            result.add(player);
        }
        return result;
    }

    private void onPlayerDeath(PlayerDeathEvent event) {
        if (!copied || resolved || targetUuid == null) {
            return;
        }
        Player dead = event.getEntity();
        if (dead == null || !dead.getUniqueId().equals(targetUuid)) {
            return;
        }

        Player self = getPlayer();
        if (self == null || self.isDead()) {
            resolved = true;
            return;
        }

        Player killer = dead.getKiller();
        if (killer != null && killer.getUniqueId().equals(self.getUniqueId())) {
            resolved = true;
            grantBonusHealth(self);
            self.sendMessage("§a[도플갱어] §f대상 처치 성공! 최대 체력 §c+10§f");
        } else {
            fail(self);
        }
    }

    private void checkTargetStillValid() {
        if (targetUuid == null) {
            resolved = true;
            return;
        }
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null || !target.isOnline() || target.isDead() || !LocationUtil.isValidTarget(getPlayer(), target)) {
            Player self = getPlayer();
            if (self != null && !self.isDead()) {
                fail(self);
            } else {
                resolved = true;
            }
        }
    }

    private void fail(Player self) {
        resolved = true;
        self.sendMessage("§c[도플갱어] §f대상 처치에 실패했습니다");
        self.setHealth(0.0);
    }

    private void grantBonusHealth(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        double newMax = maxHealth.getBaseValue() + BONUS_HEALTH;
        maxHealth.setBaseValue(newMax);
        player.setHealth(Math.min(newMax, player.getHealth() + BONUS_HEALTH));
    }

    private void destroyCopiedAbility() {
        if (copiedAbility == null) {
            return;
        }
        copiedAbility.destroy();
        copiedAbility = null;
    }

    public AbilityBase getCopiedAbility() {
        return copiedAbility;
    }

    private void sendCopiedAbilityInfo(Player player) {
        if (player == null || copiedAbility == null) {
            return;
        }
        player.sendMessage("§6[능력 정보] §f" + copiedAbility.getName());
        if (!copiedAbility.getExplain().isEmpty()) {
            for (String line : copiedAbility.getExplain()) {
                player.sendMessage("§f- " + line);
            }
        } else {
            player.sendMessage("§7설명이 등록되어 있지 않습니다");
        }
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (copiedAbility instanceof ActiveHandler handler) {
            return handler.activeSkill(material, clickType);
        }
        return false;
    }

    @Override
    public void targetSkill(Material material, LivingEntity target) {
        if (copiedAbility instanceof TargetHandler handler) {
            handler.targetSkill(material, target);
        }
    }
}
