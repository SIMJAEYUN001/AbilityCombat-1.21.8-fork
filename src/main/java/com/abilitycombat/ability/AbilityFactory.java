package com.abilitycombat.ability;

import com.abilitycombat.game.Participant;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 능력 등록 및 생성 팩토리
 */
public final class AbilityFactory {

    private static final Map<String, AbilityRegistration> registeredAbilities = new LinkedHashMap<>();
    private static final Map<Class<? extends AbilityBase>, AbilityManifest> manifestCache = new HashMap<>();

    private AbilityFactory() {
    }

    /**
     * 능력 등록
     */
    public static void register(Class<? extends AbilityBase> abilityClass) {
        AbilityManifest manifest = abilityClass.getAnnotation(AbilityManifest.class);
        if (manifest == null) {
            throw new IllegalArgumentException("AbilityManifest annotation is required: " + abilityClass.getName());
        }
        AbilityDescriptor descriptor = AbilityDescriptor.fromManifest(manifest);
        registeredAbilities.put(manifest.name(), new AbilityRegistration(abilityClass, descriptor,
                participant -> create(abilityClass, participant)));
        manifestCache.put(abilityClass, manifest);
    }

    public static void register(AbilityDescriptor descriptor, Class<? extends AbilityBase> abilityClass,
            Function<Participant, AbilityBase> creator) {
        if (descriptor == null || descriptor.name() == null || descriptor.name().isBlank()) {
            throw new IllegalArgumentException("AbilityDescriptor name is required");
        }
        if (abilityClass == null) {
            throw new IllegalArgumentException("Ability class is required: " + descriptor.name());
        }
        if (creator == null) {
            throw new IllegalArgumentException("Ability creator is required: " + descriptor.name());
        }
        registeredAbilities.put(descriptor.name(), new AbilityRegistration(abilityClass, descriptor, creator));
    }

    /**
     * 능력 등록 해제
     */
    public static void unregister(Class<? extends AbilityBase> abilityClass) {
        AbilityManifest manifest = manifestCache.remove(abilityClass);
        if (manifest != null) {
            registeredAbilities.remove(manifest.name());
        }
        registeredAbilities.entrySet().removeIf(entry -> entry.getValue().abilityClass().equals(abilityClass));
    }

    /**
     * 능력 생성 (클래스 기반)
     */
    public static AbilityBase create(Class<? extends AbilityBase> abilityClass, Participant participant) {
        try {
            Constructor<? extends AbilityBase> constructor = abilityClass.getConstructor(Participant.class);
            return constructor.newInstance(participant);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ability: " + abilityClass.getName(), e);
        }
    }

    /**
     * 이름으로 능력 생성
     */
    public static AbilityBase create(String name, Participant participant) {
        AbilityRegistration registration = registeredAbilities.get(name);
        if (registration == null) {
            throw new IllegalArgumentException("Unknown ability: " + name);
        }
        return registration.creator().apply(participant);
    }

    /**
     * 이름으로 능력 클래스 조회
     */
    public static Class<? extends AbilityBase> getAbilityClass(String name) {
        AbilityRegistration registration = registeredAbilities.get(name);
        return registration != null ? registration.abilityClass() : null;
    }

    /**
     * 등록된 능력 이름 목록
     */
    public static Set<String> getRegisteredNames() {
        return Collections.unmodifiableSet(registeredAbilities.keySet());
    }

    /**
     * 등록된 능력 클래스 목록
     */
    public static Collection<Class<? extends AbilityBase>> getRegisteredClasses() {
        java.util.LinkedHashSet<Class<? extends AbilityBase>> classes = new java.util.LinkedHashSet<>();
        classes.addAll(manifestCache.keySet());
        for (AbilityRegistration registration : registeredAbilities.values()) {
            classes.add(registration.abilityClass());
        }
        return Collections.unmodifiableCollection(classes);
    }

    /**
     * 능력 등록 여부 (이름)
     */
    public static boolean isRegistered(String name) {
        return registeredAbilities.containsKey(name);
    }

    /**
     * 능력 등록 여부 (클래스)
     */
    public static boolean isRegistered(Class<? extends AbilityBase> abilityClass) {
        return manifestCache.containsKey(abilityClass);
    }

    /**
     * 능력 메타데이터 반환
     */
    public static AbilityManifest getManifest(Class<? extends AbilityBase> abilityClass) {
        return manifestCache.get(abilityClass);
    }

    public static AbilityDescriptor getDescriptor(String name) {
        AbilityRegistration registration = registeredAbilities.get(name);
        return registration != null ? registration.descriptor() : null;
    }

    public static Collection<AbilityDescriptor> getRegisteredDescriptors() {
        java.util.List<AbilityDescriptor> descriptors = new java.util.ArrayList<>();
        for (AbilityRegistration registration : registeredAbilities.values()) {
            descriptors.add(registration.descriptor());
        }
        return Collections.unmodifiableCollection(descriptors);
    }

    /**
     * 등록된 능력 수
     */
    public static int getRegisteredCount() {
        return registeredAbilities.size();
    }

    /**
     * 모든 능력 등록 해제
     */
    public static void clear() {
        registeredAbilities.clear();
        manifestCache.clear();
    }

    private record AbilityRegistration(
            Class<? extends AbilityBase> abilityClass,
            AbilityDescriptor descriptor,
            Function<Participant, AbilityBase> creator) {
    }
}
