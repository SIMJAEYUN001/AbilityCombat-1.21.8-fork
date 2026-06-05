package com.abilitycombat.utils;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

public final class FakeGlow {

    private static final int GLOW_FALLBACK_TICKS = 24;
    private static final byte GLOWING_FLAG = 0x40;
    private static final NmsBridge NMS = NmsBridge.create();

    private FakeGlow() {
    }

    public static void show(Player viewer, LivingEntity target, String teamName, NamedTextColor color) {
        if (viewer == null || target == null) {
            return;
        }
        Team team = getOrCreateTeam(viewer.getScoreboard(), teamName, color);
        String entry = scoreboardEntry(target);
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
        if (!sendMetadataGlow(viewer, target, true)) {
            viewer.sendPotionEffectChange(target,
                    new PotionEffect(PotionEffectType.GLOWING, GLOW_FALLBACK_TICKS, 0, false, false));
        }
    }

    public static void hide(Player viewer, LivingEntity target, String teamName, String entryName) {
        if (viewer == null) {
            return;
        }
        if (target != null) {
            if (!sendMetadataGlow(viewer, target, target.isGlowing() || target.hasPotionEffect(PotionEffectType.GLOWING))) {
                viewer.sendPotionEffectChangeRemove(target, PotionEffectType.GLOWING);
            }
        }
        Team team = viewer.getScoreboard().getTeam(teamName);
        if (team != null) {
            if (entryName != null) {
                team.removeEntry(entryName);
            } else if (target != null) {
                team.removeEntry(scoreboardEntry(target));
            }
            if (team.getSize() == 0) {
                team.unregister();
            }
        }
    }

    public static String scoreboardEntry(LivingEntity target) {
        return target instanceof Player player ? player.getName() : target.getUniqueId().toString();
    }

    private static Team getOrCreateTeam(Scoreboard scoreboard, String name, NamedTextColor color) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
            team.color(color);
            team.setAllowFriendlyFire(false);
        } else {
            team.color(color);
        }
        return team;
    }

    private static boolean sendMetadataGlow(Player viewer, LivingEntity target, boolean glowing) {
        if (NMS == null) {
            return false;
        }
        try {
            NMS.sendGlow(viewer, target, glowing);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static final class NmsBridge {

        private final Method craftPlayerGetHandle;
        private final Method craftEntityGetHandle;
        private final Method getEntityData;
        private final Method dataGet;
        private final Method sendPacket;
        private final Field serverPlayerConnectionField;
        private final Field sharedFlagsField;
        private final Method dataValueCreate;
        private final Constructor<?> entityDataPacketConstructor;

        private NmsBridge() throws ReflectiveOperationException {
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Class<?> craftEntityClass = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity");
            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
            Class<?> synchedEntityDataClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
            Class<?> entityDataAccessorClass = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
            Class<?> dataValueClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
            Class<?> setEntityDataPacketClass = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");

            this.craftPlayerGetHandle = craftPlayerClass.getMethod("getHandle");
            this.craftEntityGetHandle = craftEntityClass.getMethod("getHandle");
            this.getEntityData = entityClass.getMethod("getEntityData");
            this.dataGet = synchedEntityDataClass.getMethod("get", entityDataAccessorClass);
            this.sendPacket = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl")
                    .getMethod("send", packetClass);
            this.serverPlayerConnectionField = serverPlayerClass.getField("connection");
            this.sharedFlagsField = findSharedFlagsField(entityClass, entityDataAccessorClass);
            this.sharedFlagsField.setAccessible(true);
            this.dataValueCreate = dataValueClass.getMethod("create", entityDataAccessorClass, Object.class);
            this.entityDataPacketConstructor = setEntityDataPacketClass.getConstructor(int.class, List.class);
        }

        private static NmsBridge create() {
            try {
                return new NmsBridge();
            } catch (ReflectiveOperationException exception) {
                return null;
            }
        }

        private void sendGlow(Player viewer, LivingEntity target, boolean glowing) throws ReflectiveOperationException {
            Object targetHandle = craftEntityGetHandle.invoke(target);
            Object sharedFlagsAccessor = sharedFlagsField.get(null);
            byte flags = ((Number) dataGet.invoke(getEntityData.invoke(targetHandle), sharedFlagsAccessor)).byteValue();
            byte nextFlags = glowing ? (byte) (flags | GLOWING_FLAG) : (byte) (flags & ~GLOWING_FLAG);
            Object dataValue = dataValueCreate.invoke(null, sharedFlagsAccessor, nextFlags);
            Object packet = entityDataPacketConstructor.newInstance(target.getEntityId(), List.of(dataValue));
            Object viewerHandle = craftPlayerGetHandle.invoke(viewer);
            sendPacket.invoke(serverPlayerConnectionField.get(viewerHandle), packet);
        }

        private static Field findSharedFlagsField(Class<?> entityClass, Class<?> entityDataAccessorClass)
                throws NoSuchFieldException {
            try {
                return entityClass.getDeclaredField("DATA_SHARED_FLAGS_ID");
            } catch (NoSuchFieldException ignored) {
                for (Field field : entityClass.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())
                            && entityDataAccessorClass.isAssignableFrom(field.getType())) {
                        return field;
                    }
                }
                throw ignored;
            }
        }
    }
}
