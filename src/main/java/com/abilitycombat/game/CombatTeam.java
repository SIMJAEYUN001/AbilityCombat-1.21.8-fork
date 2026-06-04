package com.abilitycombat.game;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CombatTeam {

    public static final int MAX_TEAMS = 50;
    private static final String[] HEX_COLORS = {
            "#ff4b4b", "#4b7bff", "#34d399", "#f59e0b", "#ec4899",
            "#22d3ee", "#a855f7", "#84cc16", "#fb7185", "#38bdf8",
            "#f97316", "#14b8a6", "#eab308", "#8b5cf6", "#10b981",
            "#ef4444", "#3b82f6", "#d946ef", "#06b6d4", "#65a30d",
            "#f43f5e", "#0ea5e9", "#a3e635", "#facc15", "#c084fc",
            "#2dd4bf", "#fb923c", "#60a5fa", "#f472b6", "#4ade80",
            "#f87171", "#818cf8", "#c026d3", "#0891b2", "#ca8a04",
            "#16a34a", "#dc2626", "#2563eb", "#7c3aed", "#db2777",
            "#ea580c", "#0d9488", "#9333ea", "#0284c7", "#be123c",
            "#15803d", "#b45309", "#6d28d9", "#0f766e", "#1d4ed8"
    };

    public static final CombatTeam RED = new CombatTeam("team_01", "레드팀", "#ff4b4b");
    public static final CombatTeam BLUE = new CombatTeam("team_02", "블루팀", "#4b7bff");

    private final String id;
    private final String displayName;
    private final TextColor color;
    private final NamedTextColor namedColor;
    private final String legacyColor;

    private CombatTeam(String id, String displayName, String hexColor) {
        this.id = id;
        this.displayName = displayName;
        TextColor parsed = TextColor.fromHexString(hexColor);
        this.color = parsed == null ? NamedTextColor.WHITE : parsed;
        this.namedColor = NamedTextColor.nearestTo(this.color);
        this.legacyColor = toLegacyColor(this.namedColor);
    }

    public static List<CombatTeam> createTeams(int requestedCount) {
        int count = Math.max(1, Math.min(MAX_TEAMS, requestedCount));
        List<CombatTeam> teams = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            teams.add(new CombatTeam(
                    "team_" + String.format("%02d", index + 1),
                    "팀 " + String.format("%02d", index + 1),
                    HEX_COLORS[index % HEX_COLORS.length]));
        }
        return teams;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TextColor getColor() {
        return color;
    }

    public NamedTextColor getNamedColor() {
        return namedColor;
    }

    public String getLegacyColor() {
        return legacyColor;
    }

    public String getScoreboardName() {
        return "aw_" + id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CombatTeam team)) {
            return false;
        }
        return Objects.equals(id, team.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    private static String toLegacyColor(NamedTextColor color) {
        if (color == NamedTextColor.BLACK) return "§0";
        if (color == NamedTextColor.DARK_BLUE) return "§1";
        if (color == NamedTextColor.DARK_GREEN) return "§2";
        if (color == NamedTextColor.DARK_AQUA) return "§3";
        if (color == NamedTextColor.DARK_RED) return "§4";
        if (color == NamedTextColor.DARK_PURPLE) return "§5";
        if (color == NamedTextColor.GOLD) return "§6";
        if (color == NamedTextColor.GRAY) return "§7";
        if (color == NamedTextColor.DARK_GRAY) return "§8";
        if (color == NamedTextColor.BLUE) return "§9";
        if (color == NamedTextColor.GREEN) return "§a";
        if (color == NamedTextColor.AQUA) return "§b";
        if (color == NamedTextColor.RED) return "§c";
        if (color == NamedTextColor.LIGHT_PURPLE) return "§d";
        if (color == NamedTextColor.YELLOW) return "§e";
        return "§f";
    }
}
