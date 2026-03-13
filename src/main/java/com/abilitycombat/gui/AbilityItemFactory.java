package com.abilitycombat.gui;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityDefinition;
import com.abilitycombat.ability.AbilityFactory;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.AbilityRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AbilityItemFactory {

    private static final int LORE_LINE_LENGTH = 28;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private AbilityItemFactory() {
    }

    public static ItemStack create(AbilityDefinition definition) {
        return create(definition, List.of());
    }

    public static ItemStack createForDebug(AbilityDefinition definition, AbilityRegistry abilityRegistry) {
        if (abilityRegistry == null) {
            return create(definition);
        }
        long count = abilityRegistry.getPickCount(definition);
        double rate = abilityRegistry.getPickRatePercent(definition);
        String pickLine = String.format(Locale.ROOT, "§6픽률: §e%.2f%% §7(§f%d회§7)", rate, count);
        return create(definition, List.of(pickLine));
    }

    private static ItemStack create(AbilityDefinition definition, List<String> extraLore) {
        ItemStack item = new ItemStack(definition.getIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(definition.getDisplayName(), definition.getRank().getColor())
                    .decoration(TextDecoration.ITALIC, false));
            List<String> lore = new ArrayList<>();
            lore.add(buildRankLine(definition));
            lore.add(buildCooldownLine(definition.getName()));
            if (extraLore != null && !extraLore.isEmpty()) {
                lore.addAll(extraLore);
            }
            lore.add("");
            List<String> explain = resolveExplain(definition);
            if (explain.isEmpty()) {
                lore.add("§8설명이 없습니다.");
            } else {
                for (String line : explain) {
                    addWrappedLore(lore, line);
                }
            }
            meta.lore(toComponents(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static List<String> resolveExplain(AbilityDefinition definition) {
        AbilityManifest manifest = getManifest(definition.getName());
        if (manifest != null && manifest.explain().length > 0) {
            return Arrays.asList(manifest.explain());
        }
        return definition.getSummary();
    }

    private static String buildRankLine(AbilityDefinition definition) {
        if (definition == null || definition.getRank() == null) {
            return "§6등급: §fA";
        }
        return "§6등급: " + LEGACY_SERIALIZER
                .serialize(Component.text(definition.getRank().name(), definition.getRank().getColor()));
    }

    private static AbilityManifest getManifest(String name) {
        Class<? extends AbilityBase> abilityClass = AbilityFactory.getAbilityClass(name);
        if (abilityClass == null) {
            return null;
        }
        return AbilityFactory.getManifest(abilityClass);
    }

    private static String buildCooldownLine(String name) {
        List<Integer> cooldowns = extractCooldowns(AbilityFactory.getAbilityClass(name));
        if (cooldowns.isEmpty()) {
            return "§6쿨타임: §e없음";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < cooldowns.size(); i++) {
            if (i > 0) {
                text.append(" / ");
            }
            text.append(cooldowns.get(i)).append("초");
        }
        return "§6쿨타임: §e" + text;
    }

    private static List<Integer> extractCooldowns(Class<? extends AbilityBase> abilityClass) {
        if (abilityClass == null) {
            return List.of();
        }
        Set<Integer> values = new LinkedHashSet<>();
        for (Field field : abilityClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.getType() != int.class && field.getType() != Integer.TYPE) {
                continue;
            }
            if (!field.getName().toUpperCase(Locale.ROOT).contains("COOLDOWN")) {
                continue;
            }
            try {
                field.setAccessible(true);
                int value = field.getInt(null);
                if (value > 0) {
                    values.add(value);
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return new ArrayList<>(values);
    }

    private static void addWrappedLore(List<String> lore, String line) {
        String colored = applyDefaultColor(line);
        for (String part : wrapLine(colored, LORE_LINE_LENGTH)) {
            lore.add(part);
        }
    }

    private static String applyDefaultColor(String line) {
        if (line == null || line.isBlank()) {
            return "§8";
        }
        if (line.indexOf('§') >= 0) {
            return line;
        }
        return "§8" + line;
    }

    private static List<String> wrapLine(String line, int maxLength) {
        List<String> result = new ArrayList<>();
        if (line == null) {
            return result;
        }
        StringBuilder current = new StringBuilder();
        int visible = 0;
        int index = 0;
        while (index < line.length()) {
            char ch = line.charAt(index);
            if (ch == '§' && index + 1 < line.length()) {
                current.append(ch).append(line.charAt(index + 1));
                index += 2;
                continue;
            }
            if (visible >= maxLength) {
                String part = current.toString();
                result.add(part);
                String lastColors = getLastColors(part);
                current.setLength(0);
                if (!lastColors.isEmpty()) {
                    current.append(lastColors);
                }
                visible = 0;
            }
            current.append(ch);
            visible++;
            index++;
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private static String getLastColors(String input) {
        StringBuilder result = new StringBuilder();
        int length = input.length();
        for (int index = length - 2; index >= 0; index--) {
            char section = input.charAt(index);
            if (section == '§') {
                char c = input.charAt(index + 1);
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == 'r'
                        || c == 'R') {
                    result.insert(0, "§" + c);
                    break;
                } else if ((c >= 'k' && c <= 'o') || (c >= 'K' && c <= 'O')) {
                    result.insert(0, "§" + c);
                }
            }
        }
        return result.toString();
    }

    private static Component toComponent(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return LEGACY_SERIALIZER.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    private static List<Component> toComponents(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(toComponent(line));
        }
        return components;
    }
}
