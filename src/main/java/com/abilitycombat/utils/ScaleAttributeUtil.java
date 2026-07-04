package com.abilitycombat.utils;

import com.abilitycombat.AbilityCombat;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;

import java.util.Locale;

public final class ScaleAttributeUtil {

    private static final String DASH_SCALE_MODIFIER_KEY = "sprint_dash_scale";
    private static final double DASH_SCALE_SCALAR = -0.5D;

    private ScaleAttributeUtil() {
    }

    public static void applyDashScale(LivingEntity entity) {
        applyBaseScalar(entity, DASH_SCALE_MODIFIER_KEY, DASH_SCALE_SCALAR);
    }

    public static void clearDashScale(LivingEntity entity) {
        removeScaleModifier(entity, DASH_SCALE_MODIFIER_KEY);
    }

    public static boolean applyBaseScalar(LivingEntity entity, String keyName, double scalar) {
        return applyScaleModifier(entity, keyName, scalar, AttributeModifier.Operation.ADD_SCALAR);
    }

    public static boolean applyTotalScalar(LivingEntity entity, String keyName, double scalar) {
        return applyScaleModifier(entity, keyName, scalar, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    public static boolean removeScaleModifier(LivingEntity entity, String keyName) {
        AttributeInstance scale = getScaleAttribute(entity);
        NamespacedKey key = scaleKey(keyName);
        if (scale == null || key == null) {
            return false;
        }
        scale.removeModifier(key);
        return true;
    }

    public static double getScaleWithoutDash(LivingEntity entity) {
        AttributeInstance scale = getScaleAttribute(entity);
        if (scale == null) {
            return 1.0D;
        }
        NamespacedKey dashKey = scaleKey(DASH_SCALE_MODIFIER_KEY);
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

    private static boolean applyScaleModifier(LivingEntity entity, String keyName, double amount,
            AttributeModifier.Operation operation) {
        AttributeInstance scale = getScaleAttribute(entity);
        NamespacedKey key = scaleKey(keyName);
        if (scale == null || key == null || operation == null || !Double.isFinite(amount)) {
            return false;
        }
        scale.removeModifier(key);
        if (Math.abs(amount) > 1.0E-6) {
            scale.addTransientModifier(new AttributeModifier(key, amount, operation));
        }
        return true;
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

    private static NamespacedKey scaleKey(String keyName) {
        if (keyName == null || keyName.isBlank()) {
            return null;
        }
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin != null ? new NamespacedKey(plugin, keyName.toLowerCase(Locale.ROOT)) : null;
    }
}
