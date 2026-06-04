package com.abilitycombat.npc;

import com.abilitycombat.AbilityCombat;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerReplicaManager implements Listener {

    public static final String REPLICA_TAG = "abilitycombat_replica_anchor";
    public static final String TRAINING_DUMMY_TAG = "abilitycombat_training_dummy";

    private final AbilityCombat plugin;
    private final Set<PlayerReplica> replicas = ConcurrentHashMap.newKeySet();
    private final NamespacedKey replicaKey;
    private final NamespacedKey trainingDummyKey;

    private BukkitTask refreshTask;

    public PlayerReplicaManager(AbilityCombat plugin) {
        this.plugin = plugin;
        this.replicaKey = new NamespacedKey(plugin, "replica_anchor");
        this.trainingDummyKey = new NamespacedKey(plugin, "training_dummy");
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 1L, 5L);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        for (PlayerReplica replica : new ArrayList<>(replicas)) {
            replica.remove();
        }
        replicas.clear();
    }

    public PlayerReplica createReplica(org.bukkit.Location location, ReplicaProfile profile) {
        return new PlayerReplica(plugin, this, location, profile);
    }

    public PlayerReplica createTrainingDummy(org.bukkit.Location location) {
        PlayerReplica replica = createReplica(location,
                new ReplicaProfile(UUID.randomUUID(), "AWDummy", List.of()));
        markTrainingDummyEntity(replica.getEntity());
        return replica;
    }

    NamespacedKey getReplicaKey() {
        return replicaKey;
    }

    NamespacedKey getTrainingDummyKey() {
        return trainingDummyKey;
    }

    void register(PlayerReplica replica) {
        replicas.add(replica);
    }

    void unregister(PlayerReplica replica) {
        replicas.remove(replica);
    }

    public static boolean isReplicaEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.getScoreboardTags().contains(REPLICA_TAG)) {
            return true;
        }
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null || plugin.getReplicaManager() == null) {
            return false;
        }
        return entity.getPersistentDataContainer().has(plugin.getReplicaManager().getReplicaKey(), PersistentDataType.BYTE);
    }

    public static boolean isTrainingDummy(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.getScoreboardTags().contains(TRAINING_DUMMY_TAG)) {
            return true;
        }
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null || plugin.getReplicaManager() == null) {
            return false;
        }
        return entity.getPersistentDataContainer().has(plugin.getReplicaManager().getTrainingDummyKey(),
                PersistentDataType.BYTE);
    }

    public void markReplicaEntity(Entity entity) {
        if (entity == null) {
            return;
        }
        entity.addScoreboardTag(REPLICA_TAG);
        entity.getPersistentDataContainer().set(replicaKey, PersistentDataType.BYTE, (byte) 1);
    }

    public void markTrainingDummyEntity(Entity entity) {
        if (entity == null) {
            return;
        }
        entity.addScoreboardTag(TRAINING_DUMMY_TAG);
        entity.getPersistentDataContainer().set(trainingDummyKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void refreshAll() {
        for (PlayerReplica replica : new ArrayList<>(replicas)) {
            if (replica.isDead()) {
                replica.remove();
                continue;
            }
            replica.refreshViewers();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshAll, 2L);
    }
}
