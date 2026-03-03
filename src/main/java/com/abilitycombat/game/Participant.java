package com.abilitycombat.game;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityDefinition;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 게임 참가자 클래스
 */
public class Participant {

    private final UUID uuid;
    private final String name;
    private AbilityBase ability;
    private AbilityDefinition abilityDefinition;
    private boolean targetable = true;

    public Participant(Player player) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
    }

    /**
     * 참가자 UUID 반환
     */
    public UUID getUniqueId() {
        return uuid;
    }

    /**
     * 참가자 이름 반환
     */
    public String getName() {
        return name;
    }

    /**
     * 참가자 Player 반환
     */
    public Player getPlayer() {
        return org.bukkit.Bukkit.getPlayer(uuid);
    }

    /**
     * 현재 능력 반환
     */
    public AbilityBase getAbility() {
        return ability;
    }

    public AbilityDefinition getAbilityDefinition() {
        return abilityDefinition;
    }

    public void setAbilityDefinition(AbilityDefinition abilityDefinition) {
        this.abilityDefinition = abilityDefinition;
    }

    /**
     * 능력 설정
     */
    public void setAbility(AbilityBase ability) {
        if (this.ability != null) {
            this.ability.destroy();
        }
        this.ability = ability;
        if (ability != null) {
            ability.activate();
        }
    }

    /**
     * 능력 제거
     */
    public void removeAbility() {
        if (ability != null) {
            ability.destroy();
            ability = null;
        }
    }

    public void clearAbility() {
        removeAbility();
        abilityDefinition = null;
    }

    /**
     * 능력 보유 여부
     */
    public boolean hasAbility() {
        return ability != null;
    }

    /**
     * 타게팅 가능 여부
     */
    public boolean isTargetable() {
        return targetable;
    }

    /**
     * 타게팅 가능 여부 설정
     */
    public void setTargetable(boolean targetable) {
        this.targetable = targetable;
    }
}
