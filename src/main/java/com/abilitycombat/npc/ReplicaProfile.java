package com.abilitycombat.npc;

import com.destroystokyo.paper.ClientOption;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.destroystokyo.paper.SkinParts;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ReplicaProfile(UUID uuid, String name, List<PropertyData> properties, int skinParts) {

    private static final String DEFAULT_NAME = "Replica";
    private static final int ALL_SKIN_PARTS = 0x7F;

    public static ReplicaProfile fromPlayer(Player player) {
        if (player == null) {
            return defaultProfile();
        }
        UUID uuid = UUID.randomUUID();
        String name = sanitizeName(player.getName());
        org.bukkit.profile.PlayerProfile rawProfile = player.getPlayerProfile();
        List<PropertyData> properties = new ArrayList<>();
        if (rawProfile instanceof PlayerProfile paperProfile) {
            for (ProfileProperty property : paperProfile.getProperties()) {
                properties.add(new PropertyData(property.getName(), property.getValue(), property.getSignature()));
            }
        }
        SkinParts parts = player.getClientOption(ClientOption.SKIN_PARTS);
        int rawSkinParts = parts != null ? parts.getRaw() : ALL_SKIN_PARTS;
        return new ReplicaProfile(uuid, name, List.copyOf(properties), rawSkinParts);
    }

    public static ReplicaProfile defaultProfile() {
        return new ReplicaProfile(UUID.randomUUID(), DEFAULT_NAME, List.of(), ALL_SKIN_PARTS);
    }

    public static ReplicaProfile named(String name) {
        return new ReplicaProfile(UUID.randomUUID(), sanitizeName(name), List.of(), ALL_SKIN_PARTS);
    }

    private static String sanitizeName(String input) {
        if (input == null || input.isBlank()) {
            return DEFAULT_NAME;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < input.length() && builder.length() < 16; i++) {
            char ch = input.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                builder.append(ch);
            }
        }
        if (builder.isEmpty()) {
            return DEFAULT_NAME;
        }
        return builder.toString();
    }

    public record PropertyData(String name, String value, String signature) {
    }
}
