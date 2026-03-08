package com.abilitycombat.game;

import net.kyori.adventure.text.format.NamedTextColor;

public enum CombatTeam {
    RED("레드팀", NamedTextColor.RED),
    BLUE("블루팀", NamedTextColor.BLUE);

    private final String displayName;
    private final NamedTextColor color;

    CombatTeam(String displayName, NamedTextColor color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NamedTextColor getColor() {
        return color;
    }
}
