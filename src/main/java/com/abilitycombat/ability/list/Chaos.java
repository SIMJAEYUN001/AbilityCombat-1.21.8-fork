package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityDescriptor;
import com.abilitycombat.ability.AbilityDefinition;
import com.abilitycombat.ability.AbilityFactory;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@AbilityManifest(name = "혼돈 (Chaos)", species = AbilityManifest.Species.SPECIAL, explain = {
        "§e§l[패시브 - 이중 능력]",
        "§7선택 시 무작위 능력 §f2개§7를 얻습니다",
        "§71번 능력은 원래 방식으로, 2번 능력은 §6금괴§7로 발동합니다",
        "§72번 능력에는 위험 능력군이 등장하지 않습니다"
}, summarize = {
        "§7패시브§f: 능력 2개 획득",
        "§7발동§f: 1번 원래 방식 / 2번 금괴"
})
public class Chaos extends AbilityBase implements ActiveHandler {

    private static final List<String> NON_IRON_TRIGGER_KEYWORDS = List.of(
            "활발사",
            "검우클릭",
            "낚시대",
            "낚싯대",
            "더블점프",
            "웅크리기",
            "화살발사",
            "화살원거리"
    );
    private static final List<String> DANGEROUS_SECOND_KEYWORDS = List.of(
            "능력복제",
            "능력2개",
            "복제",
            "사망기록",
            "부활",
            "영구소모",
            "사망시",
            "더미",
            "npc",
            "인형",
            "소환",
            "아레나",
            "결투장",
            "팀원사망",
            "팀전전용",
            "2인전전용",
            "최대체력10소모"
    );
    private static final Map<UUID, Pair> PENDING = new ConcurrentHashMap<>();

    private AbilityDefinition firstDefinition;
    private AbilityDefinition secondDefinition;
    private AbilityBase firstAbility;
    private AbilityBase secondAbility;

    public Chaos(Participant participant) {
        super(participant);
        Pair pair = PENDING.remove(participant.getUniqueId());
        if (pair != null) {
            this.firstDefinition = pair.first;
            this.secondDefinition = pair.second;
        }
    }

    public static boolean isChaosAbility(String name) {
        return "혼돈 (Chaos)".equals(name);
    }

    public static boolean isCompatibleInner(AbilityDefinition definition) {
        return isSecondCompatibleInner(definition);
    }

    public static boolean isFirstCompatibleInner(AbilityDefinition definition) {
        return definition != null
                && AbilityFactory.isRegistered(definition.getName())
                && !isChaosAbility(definition.getName());
    }

    public static boolean isSecondCompatibleInner(AbilityDefinition definition) {
        return definition != null
                && AbilityFactory.isRegistered(definition.getName())
                && !isChaosAbility(definition.getName())
                && isSafeSecondAbility(definition);
    }

    private static boolean isSafeSecondAbility(AbilityDefinition definition) {
        Class<? extends AbilityBase> abilityClass = AbilityFactory.getAbilityClass(definition.getName());
        AbilityDescriptor descriptor = AbilityFactory.getDescriptor(definition.getName());
        if (abilityClass == null || descriptor == null) {
            return false;
        }
        String text = normalizeText(definition, descriptor);
        if (ActiveHandler.class.isAssignableFrom(abilityClass) && !text.contains("철괴")) {
            return false;
        }
        if (containsAny(text, NON_IRON_TRIGGER_KEYWORDS)) {
            return false;
        }
        return !containsAny(text, DANGEROUS_SECOND_KEYWORDS);
    }

    private static String normalizeText(AbilityDefinition definition, AbilityDescriptor descriptor) {
        StringBuilder builder = new StringBuilder();
        builder.append(definition.getName()).append(' ');
        descriptor.explain().forEach(line -> builder.append(line).append(' '));
        descriptor.summarize().forEach(line -> builder.append(line).append(' '));
        definition.getSummary().forEach(line -> builder.append(line).append(' '));
        return builder.toString()
                .replaceAll("§.", "")
                .replaceAll("\\s+", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static void prepare(UUID playerId, AbilityDefinition first, AbilityDefinition second) {
        if (playerId != null && first != null && second != null) {
            PENDING.put(playerId, new Pair(first, second));
        }
    }

    public static void clearPending(UUID playerId) {
        if (playerId != null) {
            PENDING.remove(playerId);
        }
    }

    public List<AbilityBase> getInnerAbilities() {
        return java.util.stream.Stream.of(firstAbility, secondAbility)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<AbilityDefinition> getInnerDefinitions() {
        return java.util.stream.Stream.of(firstDefinition, secondDefinition)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    protected void onActivate() {
        if (firstDefinition == null || secondDefinition == null) {
            chooseFallbackPair();
        }
        firstAbility = createInner(firstDefinition);
        secondAbility = createInner(secondDefinition);
        if (firstAbility != null) {
            firstAbility.activate();
        }
        if (secondAbility != null) {
            secondAbility.activate();
        }
        ensureGoldIngot();
    }

    @Override
    protected void onDeactivate() {
        destroyInner(firstAbility);
        destroyInner(secondAbility);
        firstAbility = null;
        secondAbility = null;
    }

    @Override
    public void handleBridgeEvent(Event event) {
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material == Material.GOLD_INGOT && secondAbility instanceof ActiveHandler handler) {
            return triggerSecond(handler, clickType);
        }
        if (firstAbility instanceof ActiveHandler handler) {
            return handler.activeSkill(material, clickType);
        }
        return false;
    }

    private boolean triggerSecond(ActiveHandler handler, ClickType clickType) {
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        if (player.hasCooldown(Material.GOLD_INGOT)) {
            return false;
        }
        int previousIronCooldown = player.getCooldown(Material.IRON_INGOT);
        boolean used = handler.activeSkill(Material.IRON_INGOT, clickType);
        int newIronCooldown = player.getCooldown(Material.IRON_INGOT);
        if (used && newIronCooldown > previousIronCooldown) {
            player.setCooldown(Material.GOLD_INGOT, newIronCooldown);
            player.setCooldown(Material.IRON_INGOT, previousIronCooldown);
        }
        return used;
    }

    private AbilityBase createInner(AbilityDefinition definition) {
        if (definition == null || !AbilityFactory.isRegistered(definition.getName())) {
            return null;
        }
        return AbilityFactory.create(definition.getName(), getParticipant());
    }

    private void destroyInner(AbilityBase ability) {
        if (ability != null && !ability.isDestroyed()) {
            ability.destroy();
        }
    }

    private void chooseFallbackPair() {
        java.util.List<AbilityDefinition> candidates = new java.util.ArrayList<>();
        for (var descriptor : AbilityFactory.getRegisteredDescriptors()) {
            AbilityDefinition definition = new AbilityDefinition(descriptor.name(), descriptor.summarize(),
                    descriptor.icon());
            if (isSecondCompatibleInner(definition)) {
                candidates.add(definition);
            }
        }
        if (candidates.size() >= 2) {
            java.util.Collections.shuffle(candidates);
            firstDefinition = candidates.get(0);
            secondDefinition = candidates.get(1);
        }
    }

    private void ensureGoldIngot() {
        Player player = getPlayer();
        if (player != null && !player.getInventory().contains(Material.GOLD_INGOT)) {
            player.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 1));
        }
    }

    private record Pair(AbilityDefinition first, AbilityDefinition second) {
    }
}
