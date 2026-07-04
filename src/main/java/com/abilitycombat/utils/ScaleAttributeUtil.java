package com.abilitycombat.utils;

import com.abilitycombat.AbilityCombat;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;

public final class ScaleAttributeUtil {

    private static final String DASH_SCALE_MODIFIER_KEY = "sprint_dash_scale";
    private static final double DASH_SCALE_SCALAR = -0.5D;

    private ScaleAttributeUtil() {
    }

    public static void applyDashScale(LivingEntity entity) {
        AttributeInstance scale = getScaleAttribute(entity);
        NamespacedKey key = dashScaleKey();
        if (scale == null || key == null) {
            return;
        }
        scale.removeModifier(key);
        scale.addTransientModifier(new AttributeModifier(key, DASH_SCALE_SCALAR,
                AttributeModifier.Operation.ADD_SCALAR));
    }

    public static void clearDashScale(LivingEntity entity) {
        AttributeInstance scale = getScaleAttribute(entity);
        NamespacedKey key = dashScaleKey();
        if (scale != null && key != null) {
            scale.removeModifier(key);
        }
    }

    public static double getScaleWithoutDash(LivingEntity entity) {
        AttributeInstance scale = getScaleAttribute(entity);
        if (scale == null) {
            return 1.0D;
        }
        NamespacedKey dashKey = dashScaleKey();
        double value = calculateAttributeValue(scale, dashKey);
        return value > 0.0D ? value : 1.0D;
    }

    public static double getBaseScale(LivingEntity entity) {
        AttributeInstance scale = getScaleAttribute(entity);
        if (scale == null) {
            return 1.0D;
        }
        double value = scale.getBaseValue();
        return value > 0.0D ? value : 1.0D;
    }

    private static AttributeInstance getScaleAttribute(LivingEntity entity) {
        return entity != null ? entity.getAttribute(Attribute.SCALE) : null;
    }

    private static double calculateAttributeValue(AttributeInstance instance, NamespacedKey ignoredKey) {
        double value = instance.getBaseValue();
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!matchesKey(modifier, ignoredKey)
                    && modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                value += modifier.getAmount();
            }
        }
        double multipliedBase = value;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!matchesKey(modifier, ignoredKey)
                    && modifier.getOperation() == AttributeModifier.Operation.ADD_SCALAR) {
                value += multipliedBase * modifier.getAmount();
            }
        }
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (!matchesKey(modifier, ignoredKey)
                    && modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1) {
                value *= 1.0D + modifier.getAmount();
            }
        }
        return value;
    }

    private static boolean matchesKey(AttributeModifier modifier, NamespacedKey key) {
        return modifier != null && key != null && key.equals(modifier.getKey());
    }

    private static NamespacedKey dashScaleKey() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin != null ? new NamespacedKey(plugin, DASH_SCALE_MODIFIER_KEY) : null;
    }
}
