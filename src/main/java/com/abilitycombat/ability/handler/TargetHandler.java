package com.abilitycombat.ability.handler;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;

/**
 * 대상 지정 스킬 핸들러
 */
public interface TargetHandler {

    /**
     * 타겟 스킬 발동
     * @param material 사용한 아이템 재질
     * @param target 대상 엔티티
     */
    void targetSkill(Material material, LivingEntity target);
}
