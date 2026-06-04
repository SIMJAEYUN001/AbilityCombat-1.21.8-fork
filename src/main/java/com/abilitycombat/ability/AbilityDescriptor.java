package com.abilitycombat.ability;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

public record AbilityDescriptor(
        String name,
        AbilityManifest.Rank rank,
        AbilityManifest.Species species,
        List<String> explain,
        List<String> summarize,
        Material icon,
        List<Integer> cooldowns) {

    public AbilityDescriptor {
        rank = rank == null ? AbilityManifest.Rank.A : rank;
        species = species == null ? AbilityManifest.Species.OTHERS : species;
        explain = explain == null ? Collections.emptyList() : List.copyOf(explain);
        summarize = summarize == null ? Collections.emptyList() : List.copyOf(summarize);
        cooldowns = cooldowns == null ? Collections.emptyList() : List.copyOf(cooldowns);
    }

    public static AbilityDescriptor fromManifest(AbilityManifest manifest) {
        if (manifest == null) {
            return null;
        }
        return new AbilityDescriptor(
                manifest.name(),
                manifest.rank(),
                manifest.species(),
                List.of(manifest.explain()),
                List.of(manifest.summarize()),
                null,
                List.of());
    }
}
