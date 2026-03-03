package com.abilitycombat.ability.handler;

import org.bukkit.Material;

/**
 * 아이템 클릭으로 발동하는 능력 핸들러
 */
public interface ActiveHandler {

    enum ClickType {
        LEFT_CLICK,
        RIGHT_CLICK
    }

    /**
     * 액티브 스킬 발동
     * @param material 사용한 아이템 재질
     * @param clickType 클릭 유형
     * @return 스킬 발동 성공 여부
     */
    boolean activeSkill(Material material, ClickType clickType);
}
