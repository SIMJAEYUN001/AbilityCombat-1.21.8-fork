package com.abilitycombat.ability;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum AbilityRank {
    SPECIAL(NamedTextColor.RED, Material.NETHER_STAR),
    L(NamedTextColor.GOLD, Material.NETHER_STAR),
    S(NamedTextColor.LIGHT_PURPLE, Material.NETHER_STAR),
    A(NamedTextColor.GREEN, Material.DIAMOND_SWORD),
    B(NamedTextColor.AQUA, Material.IRON_SWORD),
    C(NamedTextColor.YELLOW, Material.STONE_SWORD);

    private final NamedTextColor color;
    private final Material defaultIcon;

    AbilityRank(NamedTextColor color, Material defaultIcon) {
        this.color = color;
        this.defaultIcon = defaultIcon;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public Material getDefaultIcon() {
        return defaultIcon;
    }

    public static AbilityRank fromString(String value) {
        if (value == null) {
            return AbilityRank.A;
        }
        try {
            return AbilityRank.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return AbilityRank.A;
        }
    }
}
