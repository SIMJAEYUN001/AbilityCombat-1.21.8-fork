package com.abilitycombat.npc;

import com.abilitycombat.AbilityCombat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerReplica {

    private static final NmsBridge NMS = new NmsBridge();

    private final AbilityCombat plugin;
    private final PlayerReplicaManager manager;
    private final Object nmsPlayer;
    private final Player visualEntity;
    private final Set<UUID> hiddenViewers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> currentViewers = ConcurrentHashMap.newKeySet();

    private boolean spawned;
    private boolean removed;

    PlayerReplica(AbilityCombat plugin, PlayerReplicaManager manager, Location location, ReplicaProfile profile) {
        this.plugin = plugin;
        this.manager = manager;
        this.nmsPlayer = NMS.createReplicaHandle(location, profile);
        this.visualEntity = NMS.getBukkitPlayer(nmsPlayer);
        this.visualEntity.setInvulnerable(false);
        this.visualEntity.setGravity(true);
        this.visualEntity.setInvisible(false);
        this.visualEntity.setCollidable(false);
        this.visualEntity.setSilent(true);
        this.visualEntity.setPose(Pose.STANDING, true);
        manager.markReplicaEntity(visualEntity);
        NMS.addToWorld(location.getWorld(), nmsPlayer);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.hideEntity(plugin, visualEntity);
        }
        NMS.syncDirtyMetadata(nmsPlayer);
    }

    public void spawn() {
        if (spawned || removed) {
            return;
        }
        spawned = true;
        manager.register(this);
        refreshViewers();
        syncMetadata(true);
        syncEquipment();
    }

    public void hideFrom(Player viewer) {
        if (viewer == null) {
            return;
        }
        hiddenViewers.add(viewer.getUniqueId());
        despawnFor(viewer);
    }

    public void showTo(Player viewer) {
        if (viewer == null) {
            return;
        }
        hiddenViewers.remove(viewer.getUniqueId());
        if (canView(viewer)) {
            spawnFor(viewer);
        }
    }

    public boolean matches(Entity entity) {
        return entity != null && visualEntity.getUniqueId().equals(entity.getUniqueId());
    }

    public Player getEntity() {
        return visualEntity;
    }

    public boolean isDead() {
        return removed || visualEntity.isDead() || !visualEntity.isValid();
    }

    public UUID getUniqueId() {
        return visualEntity.getUniqueId();
    }

    public Location getLocation() {
        return visualEntity.getLocation();
    }

    public World getWorld() {
        return visualEntity.getWorld();
    }

    public Vector getVelocity() {
        return visualEntity.getVelocity();
    }

    public void setVelocity(Vector velocity) {
        if (removed) {
            return;
        }
        Vector applied = velocity != null ? velocity.clone() : new Vector();
        NMS.setVelocity(nmsPlayer, applied);
        broadcastTeleport();
    }

    public void teleport(Location location) {
        if (removed || location == null || location.getWorld() == null) {
            return;
        }
        NMS.setLocation(nmsPlayer, location);
        broadcastTeleport();
    }

    public void setRotation(float yaw, float pitch) {
        if (removed) {
            return;
        }
        Location location = visualEntity.getLocation();
        location.setYaw(yaw);
        location.setPitch(pitch);
        teleport(location);
    }

    public void setPose(Pose pose, boolean forced) {
        visualEntity.setPose(pose, forced);
        syncMetadata(false);
    }

    public void setGliding(boolean gliding) {
        visualEntity.setGliding(gliding);
        syncMetadata(false);
    }

    public void setSwimming(boolean swimming) {
        visualEntity.setSwimming(swimming);
        syncMetadata(false);
    }

    public void setFallDistance(float fallDistance) {
        visualEntity.setFallDistance(fallDistance);
    }

    public void setInvulnerable(boolean invulnerable) {
        visualEntity.setInvulnerable(invulnerable);
        syncMetadata(false);
    }

    public void setGravity(boolean gravity) {
        visualEntity.setGravity(gravity);
        syncMetadata(false);
    }

    public void setInvisible(boolean invisible) {
        visualEntity.setInvisible(invisible);
        syncMetadata(false);
    }

    public void setCollidable(boolean collidable) {
        visualEntity.setCollidable(collidable);
        syncMetadata(false);
    }

    public void setAI(boolean ai) {
    }

    public void setImmovable(boolean immovable) {
        if (immovable) {
            NMS.setVelocity(nmsPlayer, new Vector());
        }
    }

    public void setNoDamageTicks(int ticks) {
        visualEntity.setNoDamageTicks(ticks);
    }

    public void setHealth(double health) {
        double clamped = Math.max(0.0, health);
        AttributeInstance maxHealth = visualEntity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            clamped = Math.min(clamped, maxHealth.getValue());
        }
        visualEntity.setHealth(clamped);
        syncMetadata(false);
    }

    public void customName(Component component) {
        visualEntity.customName(component);
        syncMetadata(true);
    }

    public void setCustomNameVisible(boolean visible) {
        visualEntity.setCustomNameVisible(visible);
        syncMetadata(true);
    }

    public AttributeInstance getAttribute(Attribute attribute) {
        return visualEntity.getAttribute(attribute);
    }

    public EntityEquipment getEquipment() {
        return visualEntity.getEquipment();
    }

    public void syncEquipment() {
        if (removed) {
            return;
        }
        Map<EquipmentSlot, ItemStack> equipmentMap = collectEquipmentMap();
        for (UUID viewerId : new ArrayList<>(currentViewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) {
                currentViewers.remove(viewerId);
                continue;
            }
            viewer.sendEquipmentChange(visualEntity, equipmentMap);
        }
    }

    public void refreshViewers() {
        if (removed) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (canView(player)) {
                spawnFor(player);
            } else {
                despawnFor(player);
            }
        }
    }

    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        for (UUID viewerId : new ArrayList<>(currentViewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) {
                viewer.hideEntity(plugin, visualEntity);
            }
        }
        currentViewers.clear();
        manager.unregister(this);
        NMS.discard(nmsPlayer);
    }

    private boolean canView(Player player) {
        return player != null
                && player.isOnline()
                && player.getWorld().equals(visualEntity.getWorld())
                && !hiddenViewers.contains(player.getUniqueId());
    }

    private void spawnFor(Player viewer) {
        if (viewer == null || currentViewers.contains(viewer.getUniqueId())) {
            return;
        }
        currentViewers.add(viewer.getUniqueId());
        NMS.sendTabAdd(viewer, nmsPlayer);
        viewer.showEntity(plugin, visualEntity);
        viewer.unlistPlayer(visualEntity);
        viewer.sendEquipmentChange(visualEntity, collectEquipmentMap());
    }

    private void despawnFor(Player viewer) {
        if (viewer == null || !currentViewers.remove(viewer.getUniqueId())) {
            return;
        }
        viewer.hideEntity(plugin, visualEntity);
    }

    private void broadcastTeleport() {
        for (UUID viewerId : new ArrayList<>(currentViewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) {
                currentViewers.remove(viewerId);
                continue;
            }
            NMS.sendTeleport(viewer, nmsPlayer);
        }
    }

    private void syncMetadata(boolean full) {
        NMS.syncDirtyMetadata(nmsPlayer);
        for (UUID viewerId : new ArrayList<>(currentViewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) {
                currentViewers.remove(viewerId);
                continue;
            }
            NMS.sendMetadata(viewer, nmsPlayer, full);
        }
    }

    private Map<EquipmentSlot, ItemStack> collectEquipmentMap() {
        Map<EquipmentSlot, ItemStack> equipmentMap = new EnumMap<>(EquipmentSlot.class);
        EntityEquipment equipment = visualEntity.getEquipment();
        if (equipment == null) {
            return equipmentMap;
        }
        equipmentMap.put(EquipmentSlot.HEAD, cloneItem(equipment.getHelmet()));
        equipmentMap.put(EquipmentSlot.CHEST, cloneItem(equipment.getChestplate()));
        equipmentMap.put(EquipmentSlot.LEGS, cloneItem(equipment.getLeggings()));
        equipmentMap.put(EquipmentSlot.FEET, cloneItem(equipment.getBoots()));
        equipmentMap.put(EquipmentSlot.HAND, cloneItem(equipment.getItemInMainHand()));
        equipmentMap.put(EquipmentSlot.OFF_HAND, cloneItem(equipment.getItemInOffHand()));
        return equipmentMap;
    }

    private ItemStack cloneItem(ItemStack item) {
        return item != null ? item.clone() : null;
    }

    private static final class NmsBridge {

        private final Constructor<?> gameProfileCtor;
        private final Constructor<?> propertyCtor;
        private final Constructor<?> connectionCtor;
        private final Constructor<?> serverPlayerCtor;
        private final Constructor<?> serverGamePacketListenerCtor;
        private final Constructor<?> setEntityDataPacketCtor;
        private final Method craftServerGetServer;
        private final Method craftWorldGetHandle;
        private final Method craftPlayerGetHandle;
        private final Method commonCookieCreateInitial;
        private final Method clientInformationCreateDefault;
        private final Method getProperties;
        private final Method getLevel;
        private final Method addFreshEntity;
        private final Method getChunkSource;
        private final Method levelPlayers;
        private final Method setPos;
        private final Method setRot;
        private final Method setVelocity;
        private final Method setYHeadRot;
        private final Method setYBodyRot;
        private final Method getBukkitEntity;
        private final Method getId;
        private final Method getUuid;
        private final Method getOnGround;
        private final Method sendPacket;
        private final Method getEntityData;
        private final Method packDirty;
        private final Method getNonDefaultValues;
        private final Method clientboundTeleportFactory;
        private final Method positionMoveRotationOf;
        private final Method playerInfoInitPacket;
        private final Method discard;
        private final Method propertyMapPut;
        private final Method chunkMapUpdatePlayerStatus;
        private final Object packetFlowClientbound;

        private final Field serverPlayerConnectionField;
        private final Field chunkMapField;
        private final Field serverViewDistanceField;

        private NmsBridge() {
            try {
                Class<?> craftServerClass = Class.forName("org.bukkit.craftbukkit.CraftServer");
                Class<?> craftWorldClass = Class.forName("org.bukkit.craftbukkit.CraftWorld");
                Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
                Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
                Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
                Class<?> connectionClass = Class.forName("net.minecraft.network.Connection");
                Class<?> packetFlowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
                Class<?> commonCookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
                Class<?> clientInfoClass = Class.forName("net.minecraft.server.level.ClientInformation");
                Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
                Class<?> serverGamePacketListenerClass = Class.forName(
                        "net.minecraft.server.network.ServerGamePacketListenerImpl");
                Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
                Class<?> setEntityDataPacketClass = Class.forName(
                        "net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
                Class<?> playerInfoUpdatePacketClass = Class.forName(
                        "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
                Class<?> teleportPacketClass = Class.forName(
                        "net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket");
                Class<?> positionMoveRotationClass = Class.forName("net.minecraft.world.entity.PositionMoveRotation");
                Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
                Class<?> livingEntityClass = Class.forName("net.minecraft.world.entity.LivingEntity");
                Class<?> synchedEntityDataClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
                Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
                Class<?> serverChunkCacheClass = Class.forName("net.minecraft.server.level.ServerChunkCache");
                Class<?> chunkMapClass = Class.forName("net.minecraft.server.level.ChunkMap");
                Class<?> spawnReasonClass = Class.forName("org.bukkit.event.entity.CreatureSpawnEvent$SpawnReason");

                this.gameProfileCtor = gameProfileClass.getConstructor(UUID.class, String.class);
                Constructor<?> propertyCtor;
                try {
                    propertyCtor = propertyClass.getConstructor(String.class, String.class, String.class);
                } catch (NoSuchMethodException ignored) {
                    propertyCtor = propertyClass.getConstructor(String.class, String.class);
                }
                this.propertyCtor = propertyCtor;
                this.connectionCtor = connectionClass.getConstructor(packetFlowClass);
                this.serverPlayerCtor = serverPlayerClass.getConstructor(
                        Class.forName("net.minecraft.server.MinecraftServer"),
                        Class.forName("net.minecraft.server.level.ServerLevel"),
                        gameProfileClass,
                        clientInfoClass);
                this.serverGamePacketListenerCtor = serverGamePacketListenerClass.getConstructor(
                        Class.forName("net.minecraft.server.MinecraftServer"),
                        connectionClass,
                        serverPlayerClass,
                        commonCookieClass);
                this.setEntityDataPacketCtor = setEntityDataPacketClass.getConstructor(int.class, List.class);
                this.craftServerGetServer = craftServerClass.getMethod("getServer");
                this.craftWorldGetHandle = craftWorldClass.getMethod("getHandle");
                this.craftPlayerGetHandle = craftPlayerClass.getMethod("getHandle");
                this.commonCookieCreateInitial = commonCookieClass.getMethod("createInitial", gameProfileClass, boolean.class);
                this.clientInformationCreateDefault = clientInfoClass.getMethod("createDefault");
                this.getProperties = gameProfileClass.getMethod("getProperties");
                this.getLevel = entityClass.getMethod("level");
                this.addFreshEntity = serverLevelClass.getMethod("addFreshEntity", entityClass, spawnReasonClass);
                this.getChunkSource = serverLevelClass.getMethod("getChunkSource");
                this.levelPlayers = serverLevelClass.getMethod("players");
                this.setPos = entityClass.getMethod("setPos", double.class, double.class, double.class);
                this.setRot = entityClass.getMethod("setRot", float.class, float.class);
                this.setVelocity = entityClass.getMethod("setDeltaMovement", double.class, double.class, double.class);
                this.setYHeadRot = livingEntityClass.getMethod("setYHeadRot", float.class);
                this.setYBodyRot = livingEntityClass.getMethod("setYBodyRot", float.class);
                this.getBukkitEntity = serverPlayerClass.getMethod("getBukkitEntity");
                this.getId = entityClass.getMethod("getId");
                this.getUuid = entityClass.getMethod("getUUID");
                this.getOnGround = entityClass.getMethod("onGround");
                this.sendPacket = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl")
                        .getMethod("send", packetClass);
                this.getEntityData = entityClass.getMethod("getEntityData");
                this.packDirty = synchedEntityDataClass.getMethod("packDirty");
                this.getNonDefaultValues = synchedEntityDataClass.getMethod("getNonDefaultValues");
                this.clientboundTeleportFactory = teleportPacketClass.getMethod("teleport", int.class,
                        positionMoveRotationClass, Set.class, boolean.class);
                this.positionMoveRotationOf = positionMoveRotationClass.getMethod("of", entityClass);
                this.playerInfoInitPacket = playerInfoUpdatePacketClass.getMethod("createSinglePlayerInitializing",
                        serverPlayerClass, boolean.class);
                this.discard = entityClass.getMethod("discard");

                Object[] packetFlowValues = (Object[]) packetFlowClass.getMethod("values").invoke(null);
                this.packetFlowClientbound = packetFlowValues[0].toString().equals("CLIENTBOUND")
                        ? packetFlowValues[0]
                        : Enum.valueOf((Class<Enum>) packetFlowClass.asSubclass(Enum.class), "CLIENTBOUND");
                this.serverPlayerConnectionField = serverPlayerClass.getField("connection");
                this.chunkMapField = serverChunkCacheClass.getField("chunkMap");
                this.serverViewDistanceField = findField(chunkMapClass, "serverViewDistance");
                this.chunkMapUpdatePlayerStatus = findChunkMapUpdatePlayerStatus(chunkMapClass, serverPlayerClass);

                Object propertyMap = getProperties.invoke(gameProfileCtor.newInstance(UUID.randomUUID(), "Probe"));
                this.propertyMapPut = propertyMap.getClass().getMethod("put", Object.class, Object.class);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to initialize replica NMS bridge", exception);
            }
        }

        private Object createReplicaHandle(Location location, ReplicaProfile profile) {
            try {
                Object craftServer = Bukkit.getServer();
                Object server = craftServerGetServer.invoke(craftServer);
                Object level = craftWorldGetHandle.invoke(location.getWorld());
                Object gameProfile = gameProfileCtor.newInstance(profile.uuid(), profile.name());
                Object propertyMap = getProperties.invoke(gameProfile);
                for (ReplicaProfile.PropertyData property : profile.properties()) {
                    Object authlibProperty = property.signature() != null
                            ? propertyCtor.newInstance(property.name(), property.value(), property.signature())
                            : propertyCtor.newInstance(property.name(), property.value());
                    propertyMapPut.invoke(propertyMap, property.name(), authlibProperty);
                }
                Object clientInfo = clientInformationCreateDefault.invoke(null);
                Object handle = serverPlayerCtor.newInstance(server, level, gameProfile, clientInfo);
                Object connection = connectionCtor.newInstance(packetFlowClientbound);
                Object cookie = commonCookieCreateInitial.invoke(null, gameProfile, false);
                Object listener = serverGamePacketListenerCtor.newInstance(server, connection, handle, cookie);
                serverPlayerConnectionField.set(handle, listener);
                setLocation(handle, location);
                return handle;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to create player replica", exception);
            }
        }

        private Player getBukkitPlayer(Object handle) {
            try {
                return (Player) getBukkitEntity.invoke(handle);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to wrap replica player", exception);
            }
        }

        private void addToWorld(World world, Object handle) {
            try {
                Object level = craftWorldGetHandle.invoke(world);
                Object spawnReason = Enum.valueOf(
                        (Class<Enum>) Class.forName("org.bukkit.event.entity.CreatureSpawnEvent$SpawnReason")
                                .asSubclass(Enum.class),
                        "CUSTOM");
                Integer previousViewDistance = null;
                Object chunkMap = null;
                try {
                    Object chunkSource = getChunkSource.invoke(level);
                    chunkMap = chunkMapField.get(chunkSource);
                    if (serverViewDistanceField != null) {
                        previousViewDistance = serverViewDistanceField.getInt(chunkMap);
                        serverViewDistanceField.setInt(chunkMap, -1);
                    }
                } catch (ReflectiveOperationException ignored) {
                    chunkMap = null;
                    previousViewDistance = null;
                }
                try {
                    addFreshEntity.invoke(level, handle, spawnReason);
                } finally {
                    if (chunkMap != null && previousViewDistance != null && serverViewDistanceField != null) {
                        serverViewDistanceField.setInt(chunkMap, previousViewDistance);
                    }
                }
                addOrRemoveFromPlayerList(handle, false);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to add replica to world", exception);
            }
        }

        private void setLocation(Object handle, Location location) {
            try {
                setPos.invoke(handle, location.getX(), location.getY(), location.getZ());
                setRot.invoke(handle, location.getYaw(), location.getPitch());
                setYHeadRot.invoke(handle, location.getYaw());
                setYBodyRot.invoke(handle, location.getYaw());
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to update replica location", exception);
            }
        }

        private void setVelocity(Object handle, Vector velocity) {
            try {
                setVelocity.invoke(handle, velocity.getX(), velocity.getY(), velocity.getZ());
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to update replica velocity", exception);
            }
        }

        private void sendTabAdd(Player viewer, Object handle) {
            try {
                sendPacket(viewer, playerInfoInitPacket.invoke(null, handle, false));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to add replica tab entry", exception);
            }
        }

        private void sendTeleport(Player viewer, Object handle) {
            try {
                int entityId = (int) getId.invoke(handle);
                Object positionMoveRotation = positionMoveRotationOf.invoke(null, handle);
                boolean onGround = (boolean) getOnGround.invoke(handle);
                Object teleportPacket = clientboundTeleportFactory.invoke(null, entityId, positionMoveRotation,
                        Collections.emptySet(), onGround);
                sendPacket(viewer, teleportPacket);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to teleport replica packets", exception);
            }
        }

        private void sendMetadata(Player viewer, Object handle, boolean full) {
            try {
                Object packet = createMetadataPacket(handle, full);
                if (packet != null) {
                    sendPacket(viewer, packet);
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to send replica metadata", exception);
            }
        }

        private void syncDirtyMetadata(Object handle) {
            try {
                getDirtyMetadata(handle, false);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to sync replica metadata cache", exception);
            }
        }

        private void discard(Object handle) {
            try {
                addOrRemoveFromPlayerList(handle, true);
                discard.invoke(handle);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        private void addOrRemoveFromPlayerList(Object handle, boolean remove) throws ReflectiveOperationException {
            Object level = getLevel.invoke(handle);
            if (level == null) {
                return;
            }
            @SuppressWarnings("unchecked")
            List<Object> players = (List<Object>) levelPlayers.invoke(level);
            if (players != null) {
                if (remove) {
                    players.remove(handle);
                } else if (!players.contains(handle)) {
                    players.add(handle);
                }
            }
            if (chunkMapUpdatePlayerStatus == null) {
                return;
            }
            Object chunkSource = getChunkSource.invoke(level);
            Object chunkMap = chunkMapField.get(chunkSource);
            chunkMapUpdatePlayerStatus.invoke(chunkMap, handle, !remove);
        }

        private Method findChunkMapUpdatePlayerStatus(Class<?> chunkMapClass, Class<?> serverPlayerClass) {
            for (Method method : chunkMapClass.getDeclaredMethods()) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 2
                        && parameterTypes[1] == boolean.class
                        && (parameterTypes[0].isAssignableFrom(serverPlayerClass)
                                || serverPlayerClass.isAssignableFrom(parameterTypes[0]))) {
                    method.setAccessible(true);
                    return method;
                }
            }
            return null;
        }

        private Field findField(Class<?> owner, String name) {
            try {
                Field field = owner.getField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private Object createMetadataPacket(Object handle, boolean full) throws ReflectiveOperationException {
            List<?> values = getDirtyMetadata(handle, full);
            if (values == null || values.isEmpty()) {
                return null;
            }
            return setEntityDataPacketCtor.newInstance(getId.invoke(handle), values);
        }

        private List<?> getDirtyMetadata(Object handle, boolean full) throws ReflectiveOperationException {
            Object entityData = getEntityData.invoke(handle);
            List<?> values = (List<?>) (full ? getNonDefaultValues.invoke(entityData) : packDirty.invoke(entityData));
            return values != null ? values : List.of();
        }

        private void sendPacket(Player viewer, Object packet) throws ReflectiveOperationException {
            Object connection = serverConnection(viewer);
            sendPacket.invoke(connection, packet);
        }

        private Object serverConnection(Player viewer) throws ReflectiveOperationException {
            return serverPlayerConnectionField.get(craftPlayerGetHandle.invoke(viewer));
        }
    }
}
