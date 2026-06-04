package com.abilitycombat.ability;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

public class AbilityDefinition {

    private final String name;
    private final AbilityRank rank;
    private final List<String> summary;
    private final Material icon;

    public AbilityDefinition(String name, AbilityRank rank, List<String> summary, Material icon) {
        this.name = name;
        this.rank = rank == null ? AbilityRank.A : rank;
        this.summary = summary == null ? Collections.emptyList() : List.copyOf(summary);
        this.icon = icon == null ? this.rank.getDefaultIcon() : icon;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        if (name == null || name.isBlank()) {
            return "";
        }
        int index = name.lastIndexOf(" (");
        if (index >= 0 && name.endsWith(")")) {
            String inside = name.substring(index + 2, name.length() - 1);
            if (inside.matches("[A-Za-z0-9 _-]+")) {
                return name.substring(0, index).trim();
            }
        }
        return name;
    }

    public AbilityRank getRank() {
        return rank;
    }

    public List<String> getSummary() {
        return summary;
    }

    public Material getIcon() {
        return icon;
    }
}
