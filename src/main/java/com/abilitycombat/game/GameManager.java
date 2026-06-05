package com.abilitycombat.game;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityDescriptor;
import com.abilitycombat.ability.AbilityDefinition;
import com.abilitycombat.ability.AbilityFactory;
import com.abilitycombat.ability.AbilityRegistry;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.ability.handler.TargetHandler;
import com.abilitycombat.ability.list.Chaos;
import com.abilitycombat.ability.list.ForYouOnly;
import com.abilitycombat.ability.list.Luna;
import com.abilitycombat.effect.CrowdControl;
import com.abilitycombat.effect.SharedBurn;
import com.abilitycombat.gui.AbilityDebugGui;
import com.abilitycombat.gui.AbilitySelectGui;
import com.abilitycombat.gui.ChaosPreviewGui;
import com.abilitycombat.gui.ConfigGui;
import com.abilitycombat.gui.ToolkitGui;
import com.abilitycombat.npc.PlayerReplicaManager;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.Color;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.FireworkEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Display;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class GameManager implements Listener {
    private static final double START_SPAWN_RADIUS = 20.0;
    private static final int START_SPAWN_ATTEMPTS = 12;
    private static final int SWORD_PRIMARY_TARGET_CHECK_RANGE = 8;

    private final AbilityCombat plugin;
    private final AbilityRegistry abilityRegistry;
    private final Map<UUID, Participant> participants = new HashMap<>();
    private final Set<UUID> alivePlayers = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Map<UUID, SelectionSession> selectionSessions = new HashMap<>();
    private final Set<UUID> debugAbilityUsers = new HashSet<>();
    private final Map<UUID, Long> movementLocks = new HashMap<>();
    private final Map<UUID, Boolean> storedAi = new HashMap<>();
    private final Map<UUID, Integer> lastInteractTick = new HashMap<>();
    private final Random random = new Random();
    private long randomSelectionSeed = 0;
    private final RegionSnapshot regionSnapshot;
    private final NamespacedKey victoryFireworkKey;
    private final NamespacedKey abilityArmorStandKey;
    private final NamespacedKey fixedAxeDamageKey;

    private static final int FIXED_FOOD_LEVEL = 20;
    private static final float FIXED_SATURATION = 20.0f;
    private static final int OLD_COMBAT_TASK_PERIOD_TICKS = 20;
    private static final int OLD_NATURAL_REGEN_INTERVAL_TICKS = 80;
    private static final double OLD_NATURAL_REGEN_AMOUNT = 1.0;
    private static final double OLD_PLAYER_KNOCKBACK_HORIZONTAL = 0.4;
    private static final double OLD_PLAYER_KNOCKBACK_VERTICAL = 0.4;
    private static final double OLD_PLAYER_KNOCKBACK_VERTICAL_LIMIT = 0.4;
    private static final double OLD_PLAYER_EXTRA_KNOCKBACK = 0.5;
    private static final double OLD_PLAYER_EXTRA_VERTICAL_KNOCKBACK = 0.1;
    private static final double NO_ATTACK_COOLDOWN_ATTACK_SPEED = 1024.0;
    private static final double NO_SWEEPING_DAMAGE_RATIO = 0.0;
    private static final double FIXED_AXE_ATTACK_DAMAGE = 5.0;
    private static final double FIXED_AXE_DAMAGE_MODIFIER = FIXED_AXE_ATTACK_DAMAGE - 1.0;
    private static final int LEGACY_PLAYER_NO_DAMAGE_TICKS = 15;
    private static final String FIXED_AXE_DAMAGE_KEY = "fixed_axe_damage";
    private static final int MAP_SCAN_CHUNKS_PER_TICK = 2;
    private static final int MAP_RESTORE_CHUNKS_PER_TICK = 2;
    private static final String SELECTION_HUD_KEY = "aw:selection";
    private static final int SELECTION_HUD_PRIORITY = 1;
    private static final String VICTORY_FIREWORK_KEY = "victory_firework";
    private static final int BORDER_DAMAGE_INTERVAL_SECONDS = 1;
    private static final double STATIONARY_BORDER_RADIUS_SHRINK_PER_SECOND = 0.001;
    private static final long STATIONARY_BORDER_SHRINK_DURATION_SECONDS = 1L;
    private static final double MIN_SAFE_ZONE_RADIUS = 0.01;
    private GameState state = GameState.IDLE;
    private BukkitTask selectionTask;
    private BukkitTask gameTask;
    private BukkitTask visualTask;
    private BukkitTask mapScanTask;
    private BukkitTask mapRestoreTask;
    private BukkitTask victoryTask;
    private BukkitTask attackSpeedSyncTask;
    private final Set<BukkitTask> trackedTasks = new HashSet<>();
    private final Set<AbilityBase.AbilityTimer> runningTimers = new java.util.HashSet<>();
    private BukkitTask fixedDaytimeTask;
    private final Map<UUID, FixedWorldState> fixedWorldStates = new HashMap<>();
    private final Map<UUID, SwordSwingRecord> lastSwordSwings = new HashMap<>();
    private final Map<UUID, Integer> naturalRegenCounters = new HashMap<>();
    private final Set<UUID> manualNaturalRegen = new HashSet<>();
    private final Map<UUID, PendingKnockback> pendingKnockbacks = new HashMap<>();
    private final Map<UUID, ReplicaDamageBypass> replicaDamageBypasses = new HashMap<>();
    private int selectionSeconds;
    private int selectionRemaining;
    private int invincibilitySeconds;
    private int gameSeconds;
    private int invincibilityRemaining;
    private int gameRemaining;
    private boolean invincible;
    private boolean fixedDaytimeEnabled;
    private double borderShrinkSpeed;
    private int initialBorderRadius;
    private MatchMode selectedMatchMode = MatchMode.SOLO;
	    private boolean hideSpectators;
	    private int borderShrinkRemaining;
	    // Remaining time in the current phase before the next shrink starts (seconds).
	    private int phaseRemaining;
	    private int borderDamageIntervalRemaining = BORDER_DAMAGE_INTERVAL_SECONDS;
	    private int startAliveCount;
	    private boolean startedSolo;
	    private boolean mapRestoreEnabled;
	    private boolean blockNaturalMobSpawn;
	    private boolean infiniteDurability;
	    private boolean craftingEnabled;
	    private boolean attackCooldownEnabled;
	    private boolean idleBlockBreakAllowed;
	    private boolean idleBlockPlaceAllowed;
	    private boolean idleInvincible;

    private static final double WORLD_BORDER_MIN_SIZE = MIN_SAFE_ZONE_RADIUS * 2.0;
    private static final double WORLD_BORDER_MAX_SIZE = 5.9999968E7;

    private WorldBorder worldBorder;
    private World gameBorderWorld;
    private double originalBorderSize;
    private Location originalBorderCenter;
    private boolean noSafeZonePhaseActive;
	    private World originalBorderWorld;
	    private List<WorldBorderPhase> borderPhases = new ArrayList<>();
	    // 1-based phase number (e.g., 1..N). During shrinking, this stays as the previous stable phase.
	    private int currentPhaseIndex;
    private Location startLocation;
    private Location lobbyLocation;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private final Map<UUID, List<String>> playerSidebarEntries = new HashMap<>();
    private final Map<UUID, TextDisplay> teamHealthDisplays = new HashMap<>();

	    public GameManager(AbilityCombat plugin, AbilityRegistry abilityRegistry) {
	        this.plugin = plugin;
	        this.abilityRegistry = abilityRegistry;
	        this.blockNaturalMobSpawn = plugin.getConfig().getBoolean("mob-spawn.block-natural", true);
	        this.infiniteDurability = plugin.getConfig().getBoolean("durability.infinite", true);
	        this.craftingEnabled = plugin.getConfig().getBoolean("crafting.enabled", true);
	        this.attackCooldownEnabled = plugin.getConfig().getBoolean("combat.attack-cooldown", true);
	        this.fixedDaytimeEnabled = plugin.getConfig().getBoolean("game.fixed-daytime", true);
	        this.idleBlockBreakAllowed = plugin.getConfig().getBoolean("lobby.allow-block-break", true);
	        this.idleBlockPlaceAllowed = plugin.getConfig().getBoolean("lobby.allow-block-place", true);
	        this.idleInvincible = plugin.getConfig().getBoolean("lobby.invincible", false);
	        this.lobbyLocation = loadLobbyLocation();
        this.regionSnapshot = new RegionSnapshot(plugin.getDataFolder().toPath(), plugin.getLogger());
        this.regionSnapshot.loadFromDisk();
        this.victoryFireworkKey = new NamespacedKey(plugin, VICTORY_FIREWORK_KEY);
        this.abilityArmorStandKey = AbilityCombat.getAbilityArmorStandKey(plugin);
        this.fixedAxeDamageKey = new NamespacedKey(plugin, FIXED_AXE_DAMAGE_KEY);
        startAttackSpeedSyncTask();
        if (fixedDaytimeEnabled) {
            startFixedDaytime();
        }
    }

    public GameState getState() {
        return state;
    }

    public boolean isInvincible() {
        return invincible;
    }

    public boolean areAbilityEffectsEnabled() {
        return state == GameState.RUNNING && !invincible;
    }

    public boolean canTriggerAbilityEffects(Player player) {
        if (state == GameState.IDLE) {
            return player != null && debugAbilityUsers.contains(player.getUniqueId());
        }
        return player != null && areAbilityEffectsEnabled() && isAlive(player);
    }

    public MatchMode getSelectedMatchMode() {
        return selectedMatchMode;
    }

    public void shutdown() {
        stopTasks();
        clearMovementLocks();
        CrowdControl.clearAll();
        SharedBurn.clearAll();
        ForYouOnly.clearReducedHealth();
        Luna.clearMoonMarks();
        restoreFixedDaytime();
    }

    public boolean isTeamMode() {
        return selectedMatchMode.isTeamBased();
    }

    public boolean areTeammates(Player first, Player second) {
        if (!isTeamMode() || first == null || second == null || first.equals(second)) {
            return false;
        }
        Participant firstParticipant = participants.get(first.getUniqueId());
        Participant secondParticipant = participants.get(second.getUniqueId());
        if (firstParticipant == null || secondParticipant == null) {
            return false;
        }
        CombatTeam firstTeam = firstParticipant.getTeam();
        CombatTeam secondTeam = secondParticipant.getTeam();
        return firstTeam != null && firstTeam.equals(secondTeam);
    }

    public List<Player> getTeammates(Player player, boolean aliveOnly) {
        List<Player> result = new ArrayList<>();
        if (!isTeamMode() || player == null) {
            return result;
        }
        CombatTeam team = getPlayerTeam(player);
        if (team == null) {
            return result;
        }
        for (Participant participant : participants.values()) {
            Player teammate = participant.getPlayer();
            if (teammate == null || teammate.equals(player) || !team.equals(participant.getTeam())) {
                continue;
            }
            if (aliveOnly && !alivePlayers.contains(teammate.getUniqueId())) {
                continue;
            }
            result.add(teammate);
        }
        return result;
    }

    public boolean reviveParticipant(Player player, Location location, double maxHealthValue, double healthValue) {
        if (state != GameState.RUNNING || player == null || !player.isOnline()) {
            return false;
        }
        Participant participant = participants.get(player.getUniqueId());
        if (participant == null || alivePlayers.contains(player.getUniqueId())) {
            return false;
        }
        alivePlayers.add(player.getUniqueId());
        spectators.remove(player.getUniqueId());
        participant.setTargetable(true);
        player.spigot().respawn();
        Bukkit.getScheduler().runTask(plugin, () -> restoreRevivedParticipant(player, participant, location,
                maxHealthValue, healthValue));
        return true;
    }

    public boolean canApplyNegativeEffect(LivingEntity source, LivingEntity target) {
        if (source == null || target == null) {
            return false;
        }
        if (!(source instanceof Player sourcePlayer) || !(target instanceof Player targetPlayer)) {
            return true;
        }
        return !areTeammates(sourcePlayer, targetPlayer);
    }

    private void restoreRevivedParticipant(Player player, Participant participant, Location location,
            double maxHealthValue, double healthValue) {
        if (player == null || !player.isOnline() || participant == null) {
            return;
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(false);
        player.setCollidable(true);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double targetMax = Math.max(1.0, maxHealthValue);
        if (maxHealth != null) {
            maxHealth.setBaseValue(targetMax);
        }
        double actualMax = maxHealth != null ? maxHealth.getValue() : targetMax;
        player.setHealth(Math.max(1.0, Math.min(actualMax, healthValue)));
        if (location != null && location.getWorld() != null) {
            player.teleport(location);
        }
        applyHungerLock(player);
        giveToolkit(player);
        if (participant.getAbility() == null && participant.getAbilityDefinition() != null
                && AbilityFactory.isRegistered(participant.getAbilityDefinition().getName())) {
            participant.setAbility(AbilityFactory.create(participant.getAbilityDefinition().getName(), participant));
        }
        restoreRevivedVisibility(player);
        updateVisibility();
        syncTeamHealthDisplayVisibility();
    }

    private void restoreRevivedVisibility(Player player) {
        player.setInvisible(false);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.showPlayer(plugin, player);
            player.showPlayer(plugin, viewer);
        }
    }

    public void allowReplicaDamageTransfer(Entity source, Player target) {
        if (target == null) {
            return;
        }
        Player sourcePlayer = resolveCombatSourcePlayer(source);
        if (sourcePlayer == null) {
            return;
        }
        replicaDamageBypasses.put(target.getUniqueId(),
                new ReplicaDamageBypass(sourcePlayer.getUniqueId(), Bukkit.getCurrentTick() + 1));
    }

    public int getCurrentPhaseIndex() {
        return currentPhaseIndex;
    }

    /**
     * UUID로 Participant 조회
     */
    public Participant getParticipant(java.util.UUID uuid) {
        return participants.get(uuid);
    }

    // =============== Timer Management ===============

    public void registerTimer(AbilityBase.AbilityTimer timer) {
        runningTimers.add(timer);
    }

    public void unregisterTimer(AbilityBase.AbilityTimer timer) {
        runningTimers.remove(timer);
    }

    public void stopAllTimers() {
        for (AbilityBase.AbilityTimer timer : runningTimers) {
            timer.stop(true);
        }
    }

    public void pauseAllTimers() {
        for (AbilityBase.AbilityTimer timer : runningTimers) {
            timer.pause();
        }
    }

    public void resumeAllTimers() {
        for (AbilityBase.AbilityTimer timer : runningTimers) {
            timer.resume();
        }
    }

    public void pauseGame() {
        if (state == GameState.RUNNING) {
            state = GameState.PAUSED;
            pauseAllTimers();
            if (gameTask != null) {
                gameTask.cancel();
                untrackTask(gameTask);
                gameTask = null;
            }
            plugin.getServer().broadcast(net.kyori.adventure.text.Component.text("§e게임이 일시정지되었습니다."));
        }
    }

    public void resumeGame() {
        if (state == GameState.PAUSED) {
            state = GameState.RUNNING;
            resumeAllTimers();
            startGameTimerInternal();
            plugin.getServer().broadcast(net.kyori.adventure.text.Component.text("§a게임이 재개되었습니다."));
        }
    }

    public void startGame() {
        startGame(null);
    }

    public void startGame(CommandSender sender) {
        if (state != GameState.IDLE) {
            return;
        }
        loadConfigValues();

        // 맵이 등록되어 있으면 맵 선택 GUI 표시
        MapManager mapManager = plugin.getMapManager();
        if (mapManager != null && mapManager.getMapCount() > 0) {
            // 맵이 1개면 바로 시작, 여러 개면 선택 GUI
            if (mapManager.getMapCount() == 1) {
                MapData onlyMap = mapManager.getAllMaps().iterator().next();
                selectedMatchMode = MatchMode.SOLO;
                startGameWithMap(onlyMap);
            } else {
                // 명령어 입력자가 플레이어면 그 사람에게 GUI 표시
                if (sender instanceof Player player) {
                    selectedMatchMode = MatchMode.SOLO;
                    player.openInventory(new com.abilitycombat.gui.MapSelectGui(plugin, selectedMatchMode).getInventory());
                    return;
                }
                // sender가 없거나 콘솔이면 랜덤 맵으로 시작
                MapData randomMap = mapManager.getRandomMap();
                if (randomMap != null) {
                    selectedMatchMode = MatchMode.SOLO;
                    startGameWithMap(randomMap);
                }
            }
            return;
        }

        // 맵이 없으면 기존 방식 (config의 start-location 또는 월드 스폰)
        continueGameStart();
    }

    public void stopGame() {
        if (state == GameState.IDLE) {
            return;
        }
        Set<UUID> winners = resolveWinningPlayers();
        CombatTeam winningTeam = resolveWinningTeam();
        stopTasks();
        clearSelectionHud();
        resetWorldBorder();
        clearDroppedItems();
        announceResult(winningTeam, winners);
        clearWinnerInventory(winners);
        playVictoryEffect(winners);
        startMapRestore();
        resetPlayers();
        gatherPlayersToLobby();
        healAllOnlinePlayersWithRetries(40);
        selectionSessions.clear();
        participants.clear();
        alivePlayers.clear();
        spectators.clear();
        debugAbilityUsers.clear();
        clearMovementLocks();
        CrowdControl.clearAll();
        SharedBurn.clearAll();
        ForYouOnly.clearReducedHealth();
        Luna.clearMoonMarks();
        lastSwordSwings.clear();
        naturalRegenCounters.clear();
        manualNaturalRegen.clear();
        pendingKnockbacks.clear();
        clearScoreboard();
        clearTeamHealthDisplays();
        selectedMatchMode = MatchMode.SOLO;
        state = GameState.IDLE;
        if (fixedDaytimeEnabled) {
            startFixedDaytime();
        } else {
            restoreFixedDaytime();
        }
    }

    private Set<UUID> resolveWinningPlayers() {
        if (!isTeamMode()) {
            if (alivePlayers.size() != 1) {
                return Set.of();
            }
            return Set.of(alivePlayers.iterator().next());
        }
        CombatTeam winningTeam = resolveWinningTeam();
        if (winningTeam == null) {
            return Set.of();
        }
        Set<UUID> winners = new HashSet<>();
        for (UUID uuid : alivePlayers) {
            Participant participant = participants.get(uuid);
            if (participant != null && winningTeam.equals(participant.getTeam())) {
                winners.add(uuid);
            }
        }
        return winners;
    }

    private void clearDroppedItems() {
        World world = startLocation != null ? startLocation.getWorld()
                : (originalBorderWorld != null ? originalBorderWorld
                        : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0)));
        if (world == null) {
            return;
        }
        for (Item item : world.getEntitiesByClass(Item.class)) {
            item.remove();
        }
    }

    private CombatTeam resolveWinningTeam() {
        if (!isTeamMode()) {
            return null;
        }
        Set<CombatTeam> aliveTeams = new HashSet<>();
        for (UUID uuid : alivePlayers) {
            Participant participant = participants.get(uuid);
            if (participant != null && participant.getTeam() != null) {
                aliveTeams.add(participant.getTeam());
            }
        }
        return aliveTeams.size() == 1 ? aliveTeams.iterator().next() : null;
    }

    private void announceResult(CombatTeam winningTeam, Set<UUID> winners) {
        if (winners.isEmpty()) {
            return;
        }
        Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(500));
        if (!isTeamMode()) {
            Player winner = Bukkit.getPlayer(winners.iterator().next());
            if (winner == null) {
                return;
            }
            Title titleObj = Title.title(
                    Component.text("승리!").color(NamedTextColor.GOLD),
                    Component.text(winner.getName()).color(NamedTextColor.WHITE),
                    times);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showTitle(titleObj);
            }
            return;
        }
        if (winningTeam == null) {
            return;
        }
        Component teamSubtitle = buildWinningTeamSubtitle(winningTeam, winners);
        plugin.getServer().broadcast(Component.text(winningTeam.getDisplayName() + " 승리!", winningTeam.getColor())
                .append(Component.text(selectedMatchMode == MatchMode.DUO ? " - " + getWinnerNames(winners) : "",
                        NamedTextColor.WHITE)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean winner = winners.contains(player.getUniqueId());
            Title titleObj = Title.title(
                    Component.text(winner ? "승리!" : "패배...", winner ? NamedTextColor.GOLD : NamedTextColor.RED),
                    teamSubtitle,
                    times);
            player.showTitle(titleObj);
            player.sendMessage(winner ? "§6승리했습니다!" : "§c패배했습니다.");
        }
    }

    private Component buildWinningTeamSubtitle(CombatTeam winningTeam, Set<UUID> winners) {
        Component team = Component.text(winningTeam.getDisplayName(), winningTeam.getColor());
        if (selectedMatchMode != MatchMode.DUO) {
            return team;
        }
        return team.append(Component.text(" - " + getWinnerNames(winners), NamedTextColor.WHITE));
    }

    private String getWinnerNames(Set<UUID> winners) {
        List<String> names = new ArrayList<>();
        for (UUID uuid : winners) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                names.add(player.getName());
            }
        }
        Collections.sort(names);
        return String.join(", ", names);
    }

    private void clearWinnerInventory(Set<UUID> winners) {
        for (UUID uuid : winners) {
            Player winner = Bukkit.getPlayer(uuid);
            if (winner == null) {
                continue;
            }
            winner.getInventory().clear();
            winner.getInventory().setArmorContents(new ItemStack[4]);
            winner.getInventory().setItemInOffHand(null);
        }
    }

    private void playVictoryEffect(Set<UUID> winners) {
        if (winners.isEmpty()) {
            return;
        }
        victoryTask = trackTask(new BukkitRunnable() {
            private int bursts = 6;

            @Override
            public void run() {
                if (bursts <= 0) {
                    untrackTask(victoryTask);
                    cancel();
                    return;
                }
                for (UUID uuid : winners) {
                    Player winner = Bukkit.getPlayer(uuid);
                    if (winner == null || !winner.isOnline()) {
                        continue;
                    }
                    Location base = winner.getLocation().clone();
                    for (int i = 0; i < 3; i++) {
                        double dx = (random.nextDouble() - 0.5) * 4.0;
                        double dz = (random.nextDouble() - 0.5) * 4.0;
                        Location spawn = base.clone().add(dx, 0.5, dz);
                        spawnVictoryFirework(spawn);
                    }
                }
                bursts--;
            }
        }.runTaskTimer(plugin, 0L, 10L));
    }

    private void spawnVictoryFirework(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Firework firework = location.getWorld().spawn(location, Firework.class, spawned -> {
            FireworkMeta meta = spawned.getFireworkMeta();
            meta.setPower(1);
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.RED, Color.ORANGE, Color.YELLOW)
                    .withFade(Color.WHITE)
                    .trail(true)
                    .flicker(true)
                    .build());
            spawned.setFireworkMeta(meta);
            spawned.getPersistentDataContainer().set(victoryFireworkKey, PersistentDataType.BYTE, (byte) 1);
        });
        Bukkit.getScheduler().runTaskLater(plugin, firework::detonate, 1L);
    }

    public void openDebugGui(Player player) {
        openDebugGui(player, 0, false);
    }

    public void openDebugGui(Player player, int page, boolean viewOnly) {
        AbilityDebugGui gui = new AbilityDebugGui(getDebugDefinitions(), abilityRegistry, page, viewOnly);
        player.openInventory(gui.getInventory());
    }

    public void sendAbilityInfo(Player player) {
        Participant participant = participants.get(player.getUniqueId());
        if (participant == null || participant.getAbilityDefinition() == null) {
            player.sendMessage("§c아직 능력이 선택되지 않았습니다.");
            return;
        }
        AbilityDefinition definition = participant.getAbilityDefinition();
        AbilityBase ability = participant.getAbility();
        if (ability != null) {
            // 도플갱어는 /aw info 출력만 '도플갱어'로 표기하고, 본문은 복제한 능력 설명을 보여줍니다.
            if (ability instanceof com.abilitycombat.ability.list.Doppelganger doppelganger
                    && doppelganger.getCopiedAbility() != null) {
                player.sendMessage("§6[능력정보] §f" + ability.getDisplayName());
                List<String> copiedExplain = doppelganger.getCopiedAbility().getExplain();
                if (!copiedExplain.isEmpty()) {
                    for (String line : copiedExplain) {
                        player.sendMessage("§f- " + line);
                    }
                } else {
                    player.sendMessage("§7설명이 등록되어 있지 않습니다.");
                }
                return;
            }
            if (ability instanceof Chaos chaos
                    && (!chaos.getInnerAbilities().isEmpty() || !chaos.getInnerDefinitions().isEmpty())) {
                player.sendMessage("§6[능력 정보] §f" + ability.getName());
                List<AbilityBase> inners = chaos.getInnerAbilities();
                if (!inners.isEmpty()) {
                    for (int i = 0; i < inners.size(); i++) {
                        AbilityBase inner = inners.get(i);
                        player.sendMessage((i == 0 ? "§f[1번] " : "§6[2번 금괴] ") + inner.getName());
                        if (!inner.getExplain().isEmpty()) {
                            for (String line : inner.getExplain()) {
                                player.sendMessage("§f- " + line);
                            }
                        } else {
                            player.sendMessage("§7설명이 등록되어 있지 않습니다.");
                        }
                    }
                } else {
                    List<AbilityDefinition> definitions = chaos.getInnerDefinitions();
                    for (int i = 0; i < definitions.size(); i++) {
                        AbilityDefinition inner = definitions.get(i);
                        player.sendMessage((i == 0 ? "§f[1번] " : "§6[2번 금괴] ") + inner.getName());
                        AbilityDescriptor descriptor = AbilityFactory.getDescriptor(inner.getName());
                        List<String> explain = descriptor != null ? descriptor.explain() : List.of();
                        List<String> lines = !explain.isEmpty() ? explain : inner.getSummary();
                        if (!lines.isEmpty()) {
                            for (String line : lines) {
                                player.sendMessage("§f- " + line);
                            }
                        } else {
                            player.sendMessage("§7설명이 등록되어 있지 않습니다.");
                        }
                    }
                }
                return;
            }
            if (ability instanceof com.abilitycombat.ability.list.AkashicRecord akashicRecord
                    && akashicRecord.getCopiedAbility() != null) {
                player.sendMessage("§6[능력 정보] §f" + ability.getName());
                AbilityBase copied = akashicRecord.getCopiedAbility();
                player.sendMessage("§b[복제 중] §f" + copied.getName());
                if (!copied.getExplain().isEmpty()) {
                    for (String line : copied.getExplain()) {
                        player.sendMessage("§f- " + line);
                    }
                }
                return;
            }

            player.sendMessage("§6[능력 정보] §f" + ability.getName());
            if (!ability.getExplain().isEmpty()) {
                for (String line : ability.getExplain()) {
                    player.sendMessage("§f- " + line);
                }
                return;
            }
        }
        player.sendMessage("§6[능력 정보] §f" + definition.getName());
        if (!definition.getSummary().isEmpty()) {
            for (String line : definition.getSummary()) {
                player.sendMessage("§f- " + line);
            }
        } else {
            player.sendMessage("§7설명이 등록되어 있지 않습니다.");
        }
    }

    public void openConfigGui(Player player) {
        ConfigGui gui = new ConfigGui(plugin);
        player.openInventory(gui.getInventory());
    }

    public void openToolkitGui(Player player) {
        if (!player.isOp()) {
            player.sendMessage("§c권한이 없습니다.");
            return;
        }
        ToolkitGui gui = new ToolkitGui(plugin);
        player.openInventory(gui.getInventory());
    }

    private void giveToolkit(Player player) {
        player.getInventory().clear();
        var inventory = player.getInventory();
        for (ItemStack item : ToolkitGui.getToolkitItems(plugin)) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (!equipToolkitArmor(inventory, item.clone())) {
                inventory.addItem(item);
            }
        }
        int level = ToolkitGui.getToolkitLevel(plugin);
        player.setLevel(level);
        player.setExp(0f);
        player.setTotalExperience(0);
    }

    private boolean equipToolkitArmor(org.bukkit.inventory.PlayerInventory inventory, ItemStack item) {
        if (inventory == null || item == null) {
            return false;
        }
        Material type = item.getType();
        if (type.name().endsWith("_HELMET")) {
            inventory.setHelmet(item);
            return true;
        }
        if (type.name().endsWith("_CHESTPLATE")) {
            inventory.setChestplate(item);
            return true;
        }
        if (type.name().endsWith("_LEGGINGS")) {
            inventory.setLeggings(item);
            return true;
        }
        if (type.name().endsWith("_BOOTS")) {
            inventory.setBoots(item);
            return true;
        }
        return false;
    }

    public void saveStartLocation(Location location) {
        saveStartLocation(location, null);
    }

    public void saveStartLocation(Location location, CommandSender sender) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        plugin.getConfig().set("game.start-location.world", location.getWorld().getName());
        plugin.getConfig().set("game.start-location.x", location.getBlockX());
        plugin.getConfig().set("game.start-location.y", location.getY());
        plugin.getConfig().set("game.start-location.z", location.getBlockZ());
        plugin.getConfig().set("game.start-location.yaw", location.getYaw());
        plugin.getConfig().set("game.start-location.pitch", location.getPitch());
        plugin.saveConfig();
        startLocation = loadStartLocation();
        mapRestoreEnabled = plugin.getConfig().getBoolean("map-restore.enabled", true);
        initialBorderRadius = plugin.getConfig().getInt("world-border.initial-radius", 200);
        startMapScan(location, sender);
    }

    public void saveLobbyLocation(Location location, CommandSender sender) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        plugin.getConfig().set("lobby.location.world", location.getWorld().getName());
        plugin.getConfig().set("lobby.location.x", location.getX());
        plugin.getConfig().set("lobby.location.y", location.getY());
        plugin.getConfig().set("lobby.location.z", location.getZ());
        plugin.getConfig().set("lobby.location.yaw", location.getYaw());
        plugin.getConfig().set("lobby.location.pitch", location.getPitch());
        plugin.saveConfig();
        lobbyLocation = loadLobbyLocation();
        if (sender != null) {
            sender.sendMessage("§a로비 위치를 설정했습니다.");
        }
    }

    private void loadConfigValues() {
        invincibilitySeconds = plugin.getConfig().getInt("game.invincibility-seconds", 180);
        gameSeconds = plugin.getConfig().getInt("game.duration-seconds", 720);
        selectionSeconds = plugin.getConfig().getInt("ability.selection-seconds", 15);
        initialBorderRadius = plugin.getConfig().getInt("world-border.initial-radius", 200);
        borderShrinkSpeed = plugin.getConfig().getDouble("world-border.shrink-seconds", 3.0);
        fixedDaytimeEnabled = plugin.getConfig().getBoolean("game.fixed-daytime", true);
        hideSpectators = plugin.getConfig().getBoolean("spectator.hide-from-alive", true);
	        mapRestoreEnabled = plugin.getConfig().getBoolean("map-restore.enabled", true);
	        blockNaturalMobSpawn = plugin.getConfig().getBoolean("mob-spawn.block-natural", true);
	        infiniteDurability = plugin.getConfig().getBoolean("durability.infinite", true);
	        craftingEnabled = plugin.getConfig().getBoolean("crafting.enabled", true);
	        attackCooldownEnabled = plugin.getConfig().getBoolean("combat.attack-cooldown", true);
	        idleBlockBreakAllowed = plugin.getConfig().getBoolean("lobby.allow-block-break", true);
	        idleBlockPlaceAllowed = plugin.getConfig().getBoolean("lobby.allow-block-place", true);
	        idleInvincible = plugin.getConfig().getBoolean("lobby.invincible", false);
        startLocation = loadStartLocation();
        lobbyLocation = loadLobbyLocation();

	        borderPhases = new ArrayList<>();
	        List<Map<?, ?>> phaseList = plugin.getConfig().getMapList("world-border.phases");
	        for (Map<?, ?> entry : phaseList) {
	            Object timeObj = entry.get("time");
	            Object radiusObj = entry.get("radius");
	            if (timeObj == null || radiusObj == null) {
	                continue;
	            }
	            int time = Integer.parseInt(String.valueOf(timeObj));
	            int radius = Integer.parseInt(String.valueOf(radiusObj));
	            borderPhases.add(new WorldBorderPhase(Math.max(0, time), Math.max(0, radius)));
	        }

	        if (borderPhases.isEmpty()) {
	            int r1 = Math.max(1, initialBorderRadius);
	            int r2 = Math.max(1, (int) Math.round(r1 * 0.75));
	            int r3 = Math.max(1, (int) Math.round(r1 * 0.5));
	            int r4 = Math.max(1, (int) Math.round(r1 * 0.2));
	            int r5 = Math.max(1, (int) Math.round(r1 * 0.05));
	            borderPhases.add(new WorldBorderPhase(60, r1));
	            borderPhases.add(new WorldBorderPhase(60, r2));
	            borderPhases.add(new WorldBorderPhase(60, r3));
	            borderPhases.add(new WorldBorderPhase(60, r4));
	            borderPhases.add(new WorldBorderPhase(60, r5));
	        }
	
	        // Ensure at least 5 phases exist (requested default).
	        while (borderPhases.size() < 5) {
	            WorldBorderPhase last = borderPhases.get(borderPhases.size() - 1);
	            borderPhases.add(new WorldBorderPhase(last.getDurationSeconds(), last.getRadius()));
	        }
	    }

    private void startMapScan(Location location, CommandSender sender) {
        cancelMapScan();
        cancelMapRestore();
        if (!mapRestoreEnabled) {
            regionSnapshot.clear();
            if (sender != null) {
                sender.sendMessage("§e맵 복원이 비활성화되어 스캔을 진행하지 않습니다.");
            }
            return;
        }
        if (location == null || location.getWorld() == null) {
            regionSnapshot.clear();
            return;
        }
        World world = location.getWorld();
        int radius = initialBorderRadius;
        regionSnapshot.prepare(location, radius);

        int centerX = location.getBlockX();
        int centerZ = location.getBlockZ();
        int minChunkX = Math.floorDiv(centerX - radius, 16);
        int maxChunkX = Math.floorDiv(centerX + radius, 16);
        int minChunkZ = Math.floorDiv(centerZ - radius, 16);
        int maxChunkZ = Math.floorDiv(centerZ + radius, 16);

        List<Long> targets = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!regionSnapshot.isChunkWithinRadius(chunkX, chunkZ)) {
                    continue;
                }
                targets.add(RegionSnapshot.toKey(chunkX, chunkZ));
            }
        }

        if (targets.isEmpty()) {
            regionSnapshot.clear();
            if (sender != null) {
                sender.sendMessage(Component.text("§c스캔된 청크가 없습니다."));
            }
            return;
        }

        if (sender != null) {
            sender.sendMessage("§e맵 스캔 시작: 청크 " + targets.size() + "개");
        }
        java.util.Iterator<Long> iterator = targets.iterator();
        mapScanTask = trackTask(new BukkitRunnable() {
            private int scanned;

            @Override
            public void run() {
                if (!mapRestoreEnabled) {
                    regionSnapshot.clear();
                    if (sender != null) {
                        sender.sendMessage("§c맵 복원이 비활성화되어 스캔을 중단했습니다.");
                    }
                    cancel();
                    untrackTask(mapScanTask);
                    mapScanTask = null;
                    return;
                }
                int processed = 0;
                while (iterator.hasNext() && processed < MAP_SCAN_CHUNKS_PER_TICK) {
                    long key = iterator.next();
                    int chunkX = RegionSnapshot.keyToChunkX(key);
                    int chunkZ = RegionSnapshot.keyToChunkZ(key);
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    ChunkSnapshot snapshot = chunk.getChunkSnapshot();
                    regionSnapshot.storeSnapshot(chunkX, chunkZ, snapshot);
                    scanned++;
                    processed++;
                }
                if (!iterator.hasNext()) {
                    regionSnapshot.finishCapture();
                    if (sender != null) {
                        sender.sendMessage("§a맵 스캔 완료: 청크 " + scanned + "개");
                    }
                    cancel();
                    untrackTask(mapScanTask);
                    mapScanTask = null;
                }
            }
        }.runTaskTimer(plugin, 1L, 1L));
    }

    private void startMapRestore() {
        startMapRestore(null);
    }

    private void startMapRestore(CommandSender sender) {
        cancelMapScan();
        cancelMapRestore();
        if (!mapRestoreEnabled) {
            return;
        }
        if (!regionSnapshot.hasCaptured()) {
            regionSnapshot.loadFromDisk();
            if (!regionSnapshot.hasCaptured()) {
                return;
            }
        }
        World world = regionSnapshot.getWorld();
        if (world == null) {
            return;
        }
        Set<Long> targets = regionSnapshot.snapshotKeys();
        if (targets.isEmpty()) {
            return;
        }
        java.util.Iterator<Long> iterator = targets.iterator();
        int total = targets.size();
        mapRestoreTask = trackTask(new BukkitRunnable() {
            private int completed;
            private int ticks;

            @Override
            public void run() {
                if (!mapRestoreEnabled) {
                    regionSnapshot.clear();
                    cancel();
                    untrackTask(mapRestoreTask);
                    mapRestoreTask = null;
                    return;
                }
                int processed = 0;
                while (iterator.hasNext() && processed < MAP_RESTORE_CHUNKS_PER_TICK) {
                    long key = iterator.next();
                    RegionSnapshot.SnapshotData snapshot = regionSnapshot.loadSnapshot(key);
                    if (snapshot == null) {
                        processed++;
                        continue;
                    }
                    int chunkX = RegionSnapshot.keyToChunkX(key);
                    int chunkZ = RegionSnapshot.keyToChunkZ(key);
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    clearNonPlayerEntities(chunk);
                    int baseX = chunk.getX() << 4;
                    int baseZ = chunk.getZ() << 4;
                    int minY = snapshot.getMinY();
                    int maxY = snapshot.getMaxY();
                    BlockData[] palette = snapshot.getPalette();
                    short[] indices = snapshot.getIndices();
                    int cursor = 0;
                    for (int y = minY; y < maxY; y++) {
                        for (int dx = 0; dx < 16; dx++) {
                            int worldX = baseX + dx;
                            for (int dz = 0; dz < 16; dz++) {
                                int worldZ = baseZ + dz;
                                int paletteIndex = indices[cursor++] & 0xffff;
                                BlockData snapshotData = palette[paletteIndex];
                                Block block = world.getBlockAt(worldX, y, worldZ);
                                if (!block.getBlockData().equals(snapshotData)) {
                                    block.setBlockData(snapshotData, false);
                                }
                            }
                        }
                    }
                    processed++;
                    completed++;
                }
                ticks++;
                if (sender != null && (ticks % 20 == 0 || !iterator.hasNext())) {
                    int percent = total == 0 ? 100 : (int) Math.round((completed * 100.0) / total);
                    sender.sendMessage("§e맵 복원 진행: §f" + completed + " / " + total + " (" + percent + "%)");
                }
                if (!iterator.hasNext()) {
                    if (sender != null) {
                        sender.sendMessage("§a맵 복원 완료.");
                    }
                    cancel();
                    untrackTask(mapRestoreTask);
                    mapRestoreTask = null;
                }
            }
        }.runTaskTimer(plugin, 1L, 1L));
    }

    private void clearNonPlayerEntities(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player) {
                continue;
            }
            entity.remove();
        }
    }

    private void requestMapRestore(CommandSender sender) {
        if (!mapRestoreEnabled) {
            if (sender != null) {
                sender.sendMessage("§c맵 복원이 비활성화되어 있습니다.");
            }
            return;
        }
        if (!regionSnapshot.hasCaptured()) {
            regionSnapshot.loadFromDisk();
        }
        if (mapScanTask != null) {
            if (sender != null) {
                sender.sendMessage("§e맵 스캔이 진행 중입니다. 완료 후 다시 시도하세요.");
            }
            return;
        }
        if (mapRestoreTask != null) {
            if (sender != null) {
                sender.sendMessage("§e맵 복원이 이미 진행 중입니다.");
            }
            return;
        }
        if (!regionSnapshot.hasCaptured()) {
            if (sender != null) {
                sender.sendMessage(Component.text("§c저장된 스냅샷이 없습니다. 스폰을 저장해 주세요."));
            }
            return;
        }
        startMapRestore(sender);
        if (sender != null) {
            sender.sendMessage("§a맵 복원을 시작합니다.");
        }
    }

    private void cancelMapScan() {
        if (mapScanTask != null) {
            mapScanTask.cancel();
            untrackTask(mapScanTask);
            mapScanTask = null;
        }
    }

    private void cancelMapRestore() {
        if (mapRestoreTask != null) {
            mapRestoreTask.cancel();
            untrackTask(mapRestoreTask);
            mapRestoreTask = null;
        }
    }

    private void prepareParticipants() {
        // 게임 시작 전에 디버그로 설정된 능력 제거
        for (Participant existing : participants.values()) {
            existing.clearAbility();
        }
        participants.clear();
        alivePlayers.clear();
        spectators.clear();
        debugAbilityUsers.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Participant participant = new Participant(player);
            participants.put(player.getUniqueId(), participant);
            alivePlayers.add(player.getUniqueId());
            participant.setTeam(null);
            participant.setTargetable(true);
            participant.clearAbility();
            resetPlayerAttributes(player);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setInvulnerable(false);
            player.setCollidable(true);
            applyHungerLock(player);
            giveToolkit(player);
        }
        assignTeamsIfNeeded();
        Map<CombatTeam, Location> teamSpawnBases = new HashMap<>();
        for (UUID uuid : alivePlayers) {
            Participant participant = participants.get(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (participant != null && player != null) {
                player.teleport(getStartLocation(player, participant, teamSpawnBases));
            }
        }
        updateVisibility();
    }

    private void assignTeamsIfNeeded() {
        if (!isTeamMode()) {
            for (Participant participant : participants.values()) {
                participant.setTeam(null);
            }
            return;
        }
        List<UUID> shuffled = new ArrayList<>(alivePlayers);
        Collections.shuffle(shuffled, random);
        List<CombatTeam> teams;
        int splitIndex = (shuffled.size() + 1) / 2;
        if (selectedMatchMode == MatchMode.DUO) {
            int teamCount = Math.min(CombatTeam.MAX_TEAMS, Math.max(1, (shuffled.size() + 1) / 2));
            teams = CombatTeam.createTeams(teamCount);
        } else {
            teams = CombatTeam.createTeams(2);
        }
        for (int i = 0; i < shuffled.size(); i++) {
            Participant participant = participants.get(shuffled.get(i));
            if (participant == null) {
                continue;
            }
            CombatTeam team = selectedMatchMode == MatchMode.DUO
                    ? teams.get((i / 2) % teams.size())
                    : teams.get(i < splitIndex ? 0 : 1);
            participant.setTeam(team);
            Player player = participant.getPlayer();
            if (player != null) {
                player.sendMessage(Component.text("당신의 팀: ", NamedTextColor.YELLOW)
                        .append(Component.text(team.getDisplayName(), team.getColor())));
            }
        }
    }

    private Location getStartLocation(Player player, Participant participant, Map<CombatTeam, Location> teamSpawnBases) {
        if (!isTeamMode() || participant == null || participant.getTeam() == null) {
            return getRandomStartLocation(player);
        }
        CombatTeam team = participant.getTeam();
        Location base = teamSpawnBases.get(team);
        if (base == null) {
            Location selected = getRandomStartLocation(player);
            teamSpawnBases.put(team, selected);
            return selected;
        }
        return getNearbyTeamStartLocation(player, base);
    }

    private Location getNearbyTeamStartLocation(Player player, Location teamBase) {
        World world = teamBase.getWorld();
        if (world == null) {
            return getRandomStartLocation(player);
        }
        for (int attempt = 0; attempt < START_SPAWN_ATTEMPTS; attempt++) {
            double radius = 1.5 + random.nextDouble() * 3.5;
            double angle = random.nextDouble() * Math.PI * 2.0;
            double x = teamBase.getX() + Math.cos(angle) * radius;
            double z = teamBase.getZ() + Math.sin(angle) * radius;
            int floorY = com.abilitycombat.utils.LocationUtil.getFloorY(world,
                    (int) Math.floor(x), (int) Math.floor(z), teamBase.getBlockY());
            Location candidate = new Location(world, x + 0.5, floorY, z + 0.5, teamBase.getYaw(), teamBase.getPitch());
            if (isSpawnPassable(candidate)) {
                return candidate;
            }
        }
        return teamBase;
    }

    private Location getRandomStartLocation(Player player) {
        Location base = startLocation != null ? startLocation : player.getWorld().getSpawnLocation();
        if (base == null) {
            return player.getWorld().getSpawnLocation();
        }
        World world = base.getWorld();
        if (world == null) {
            return base;
        }
        for (int attempt = 0; attempt < START_SPAWN_ATTEMPTS; attempt++) {
            double radius = random.nextDouble() * START_SPAWN_RADIUS;
            double angle = random.nextDouble() * Math.PI * 2.0;
            double x = base.getX() + Math.cos(angle) * radius;
            double z = base.getZ() + Math.sin(angle) * radius;
            int floorY = com.abilitycombat.utils.LocationUtil.getFloorY(world,
                    (int) Math.floor(x), (int) Math.floor(z), base.getBlockY());
            Location candidate = new Location(world, x + 0.5, floorY, z + 0.5, base.getYaw(), base.getPitch());
            if (isSpawnPassable(candidate)) {
                return candidate;
            }
        }
        return base;
    }

    private boolean isSpawnPassable(Location location) {
        Block block = location.getBlock();
        Block above = block.getRelative(BlockFace.UP);
        return block.isPassable() && above.isPassable();
    }

    private void openSelectionGuis() {
        List<AbilityDefinition> implemented = getImplementedDefinitions();
        for (UUID uuid : alivePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            List<AbilityDefinition> options = getRandomOptions(implemented, 3);
            int maxRerolls = plugin.getConfig().getInt("ability.reroll-count", 1);
            selectionSessions.put(uuid, new SelectionSession(options, maxRerolls));
            player.openInventory(new AbilitySelectGui(uuid, options, maxRerolls).getInventory());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.6f);
        }
    }

    private List<AbilityDefinition> getImplementedDefinitions() {
        List<AbilityDefinition> result = new ArrayList<>();
        for (AbilityDefinition definition : abilityRegistry.getAll()) {
            if (AbilityFactory.isRegistered(definition.getName()) && isAbilityAvailableForCurrentMode(definition)) {
                result.add(definition);
            }
        }
        return result;
    }

    private boolean isAbilityAvailableForCurrentMode(AbilityDefinition definition) {
        if (definition == null) {
            return false;
        }
        String name = definition.getName();
        if ("너만을 위해 (ForYouOnly)".equals(name)) {
            return selectedMatchMode.isTeamBased();
        }
        if ("복수 (Revenge)".equals(name)) {
            return selectedMatchMode == MatchMode.DUO;
        }
        return true;
    }

    private List<AbilityDefinition> getChaosFirstCandidateDefinitions() {
        List<AbilityDefinition> result = new ArrayList<>();
        for (AbilityDefinition definition : getImplementedDefinitions()) {
            if (Chaos.isFirstCompatibleInner(definition)) {
                result.add(definition);
            }
        }
        return result;
    }

    private List<AbilityDefinition> getChaosSecondCandidateDefinitions() {
        List<AbilityDefinition> result = new ArrayList<>();
        for (AbilityDefinition definition : getImplementedDefinitions()) {
            if (Chaos.isSecondCompatibleInner(definition)) {
                result.add(definition);
            }
        }
        return result;
    }

    private List<AbilityDefinition> getDebugDefinitions() {
        Map<String, AbilityDefinition> merged = new HashMap<>();
        for (AbilityDefinition definition : abilityRegistry.getAll()) {
            if (AbilityFactory.isRegistered(definition.getName())) {
                merged.put(definition.getName(), definition);
            }
        }
        for (AbilityDescriptor descriptor : AbilityFactory.getRegisteredDescriptors()) {
            if (descriptor == null) {
                continue;
            }
            String name = descriptor.name();
            if (merged.containsKey(name)) {
                continue;
            }
            AbilityDefinition definition = new AbilityDefinition(name, descriptor.summarize(), descriptor.icon());
            merged.put(name, definition);
        }
        return new ArrayList<>(merged.values());
    }

    private List<AbilityDefinition> getRandomOptions(List<AbilityDefinition> pool, int count) {
        if (pool.isEmpty()) {
            return new ArrayList<>();
        }
        List<AbilityDefinition> options = new ArrayList<>(pool);
        sortBySelectionHash(options);
        if (options.size() <= count) {
            return options;
        }
        return new ArrayList<>(options.subList(0, count));
    }

    private AbilityDefinition getRandomOptionExcluding(List<AbilityDefinition> pool, Set<String> excluded) {
        List<AbilityDefinition> filtered = new ArrayList<>();
        for (AbilityDefinition ability : pool) {
            if (!excluded.contains(ability.getName())) {
                filtered.add(ability);
            }
        }
        if (filtered.isEmpty()) {
            return null;
        }
        sortBySelectionHash(filtered);
        return filtered.get(0);
    }

    private void sortBySelectionHash(List<AbilityDefinition> options) {
        long seed = nextSelectionSeed();
        options.sort((a, b) -> {
            long hashA = hashSelection(a.getName(), seed);
            long hashB = hashSelection(b.getName(), seed);
            return Long.compare(hashA, hashB);
        });
    }

    private long nextSelectionSeed() {
        randomSelectionSeed += 0x9e3779b97f4a7c15L;
        return randomSelectionSeed ^ System.nanoTime();
    }

    private long hashSelection(String input, long seed) {
        long x = seed;
        if (input != null) {
            for (int i = 0; i < input.length(); i++) {
                x ^= (0x9e3779b97f4a7c15L * (input.charAt(i) + 1L));
                x = Long.rotateLeft(x, 17) + 0x9e3779b97f4a7c15L;
            }
        }
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return x;
    }

    private void startSelectionTimer() {
        stopSelectionTask();
        selectionRemaining = selectionSeconds;
        invincibilityRemaining = invincibilitySeconds;
        invincible = invincibilityRemaining > 0;
        if (selectionRemaining <= 0) {
            finalizeSelection();
            return;
        }
        updateSelectionHud();
        selectionTask = trackTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            selectionRemaining--;
            updateSelectionHud();
            if (selectionRemaining <= 0) {
                finalizeSelection();
            }
        }, 20L, 20L));
    }

    private void finalizeSelection() {
        stopSelectionTask();
        clearSelectionHud();
        selectionRemaining = 0;
        for (Map.Entry<UUID, SelectionSession> entry : selectionSessions.entrySet()) {
            UUID uuid = entry.getKey();
            SelectionSession session = entry.getValue();
            if (session.selected == null) {
                if (session.awaitingChaosPreview && session.pendingChaosDefinition != null
                        && session.pendingChaosFirst != null && session.pendingChaosSecond != null) {
                    session.awaitingChaosPreview = false;
                    Chaos.prepare(uuid, session.pendingChaosFirst, session.pendingChaosSecond);
                    session.selected = session.pendingChaosDefinition;
                }
                List<AbilityDefinition> options = session.options;
                if (session.selected == null && !options.isEmpty()) {
                    session.selected = options.get(0);
                }
            }
            if (session.selected != null) {
                Player player = Bukkit.getPlayer(uuid);
                Participant participant = participants.get(uuid);
                AbilityDefinition current = participant != null ? participant.getAbilityDefinition() : null;
                if (current != null && current.getName().equals(session.selected.getName())) {
                    if (player != null) {
                        // 1틱 뒤에 닫아서 클릭 이벤트와 충돌 방지
                        Bukkit.getScheduler().runTask(plugin, (Runnable) player::closeInventory);
                    }
                    continue;
                }
                if (player != null) {
                    assignAbility(player, session.selected, true);
                    // 1틱 뒤에 닫아서 클릭 이벤트와 충돌 방지
                    Bukkit.getScheduler().runTask(plugin, (Runnable) player::closeInventory);
                    // 능력 선택 완료 후 자동으로 능력 정보 출력
                    sendAbilityInfo(player);
                }
            }
        }
        selectionSessions.clear();
        state = GameState.RUNNING;
        startGameTimer();
    }

	    private void startGameTimer() {
	        gameRemaining = gameSeconds;
	        invincible = invincibilityRemaining > 0;
	        borderShrinkRemaining = 0;
	        phaseRemaining = 0;
	        borderDamageIntervalRemaining = BORDER_DAMAGE_INTERVAL_SECONDS;
	        startAliveCount = alivePlayers.size();
	        startedSolo = startAliveCount <= 1;
            resetAllPlayerBossBars();
	        setupWorldBorder();
	        initBorderPhases();
	        stopGameTask();
	        updateScoreboard();
            startVisualTask();
	        startGameTimerInternal();
            if (!invincible) {
                activateDeferredAbilities();
            }
	    }

    private void activateDeferredAbilities() {
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Participant participant = participants.get(uuid);
            if (participant != null && participant.getAbility() != null) {
                participant.getAbility().startDeferredActivation();
            }
        }
    }

    private void resetAllPlayerBossBars() {
        if (plugin.getBossBarManager() != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.getBossBarManager().clearAll(player);
            }
        }
        if (plugin.getSprintHudService() != null) {
            plugin.getSprintHudService().resetAllBossBars();
        }
    }

    private void startVisualTask() {
        if (visualTask != null) {
            visualTask.cancel();
            untrackTask(visualTask);
        }
        visualTask = trackTask(Bukkit.getScheduler().runTaskTimer(plugin, this::updateTeamHealthDisplays, 1L, 5L));
    }

    private void startGameTimerInternal() {
        stopGameTask();
	        gameTask = trackTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
	            if (invincible) {
	                invincibilityRemaining = Math.max(0, invincibilityRemaining - 1);
	                if (invincibilityRemaining <= 0) {
	                    invincible = false;
                        activateDeferredAbilities();
	                }
	            } else {
	                gameRemaining = Math.max(0, gameRemaining - 1);
	                tickBorderPhases();

	                if (gameRemaining <= 0) {
	                    stopGame();
	                    return;
	                }
                applyPhaseBasedBorderDamage();
	            }
            updateScoreboard();
        }, 20L, 20L));
    }

	    private void setupWorldBorder() {
	        World world = startLocation != null ? startLocation.getWorld()
	                : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
	        if (world == null) {
	            return;
	        }
	        WorldBorder actualBorder = world.getWorldBorder();
        gameBorderWorld = world;

	        if (originalBorderWorld == null) {
	            originalBorderWorld = world;
	            originalBorderCenter = actualBorder.getCenter();
	            originalBorderSize = actualBorder.getSize();
	        }

	        int startRadius = !borderPhases.isEmpty() ? borderPhases.get(0).getRadius() : initialBorderRadius;
	        double gameSize = Math.max(WORLD_BORDER_MIN_SIZE, resolveSafeZoneRadius(startRadius) * 2.0);
	        Location center = startLocation != null ? startLocation : world.getSpawnLocation();
	        worldBorder = Bukkit.createWorldBorder();
	        worldBorder.setCenter(center);
	        worldBorder.setSize(gameSize);
	        worldBorder.setDamageBuffer(0.0);
	        worldBorder.setDamageAmount(0.0);
	        worldBorder.setWarningDistance(5);
        worldBorder.setWarningTime(15);
        noSafeZonePhaseActive = false;

	        actualBorder.setSize(WORLD_BORDER_MAX_SIZE);
	        actualBorder.setDamageBuffer(5.0);
	        actualBorder.setDamageAmount(0.2);
	        actualBorder.setWarningDistance(5);
        actualBorder.setWarningTime(15);
	        syncWorldBorderForAllPlayers();
	    }

	    private void initBorderPhases() {
	        // Phase timings start when invincibility ends (same as gameRemaining decrement).
	        if (worldBorder == null || borderPhases.isEmpty()) {
	            currentPhaseIndex = 1;
	            phaseRemaining = 0;
                noSafeZonePhaseActive = false;
	            return;
	        }
	        currentPhaseIndex = 1;
	        phaseRemaining = Math.max(0, borderPhases.get(0).getDurationSeconds());
	        borderShrinkRemaining = 0;
	        applyBorderPhaseState(currentPhaseIndex);
	    }

	    private void tickBorderPhases() {
	        if (borderPhases.isEmpty()) {
	            return;
	        }

	        // Shrinking time is NOT part of phase time.
	        if (borderShrinkRemaining > 0) {
	            borderShrinkRemaining = Math.max(0, borderShrinkRemaining - 1);
	            if (borderShrinkRemaining <= 0 && currentPhaseIndex < borderPhases.size()) {
	                currentPhaseIndex++;
	                phaseRemaining = Math.max(0, borderPhases.get(currentPhaseIndex - 1).getDurationSeconds());
	                applyBorderPhaseState(currentPhaseIndex);
	            }
	            return;
	        }

	        // Last phase: no further shrink.
	        if (currentPhaseIndex >= borderPhases.size()) {
                refreshStationaryBorderMotion();
	            return;
	        }

	        phaseRemaining = Math.max(0, phaseRemaining - 1);
	        if (phaseRemaining > 0) {
                refreshStationaryBorderMotion();
	            return;
	        }

	        int targetRadius = borderPhases.get(currentPhaseIndex).getRadius();
	        int duration = startBorderShrink(targetRadius);
	        if (duration <= 0) {
	            // No actual shrink needed; immediately enter the next phase.
	            currentPhaseIndex++;
	            if (currentPhaseIndex <= borderPhases.size()) {
	                phaseRemaining = Math.max(0, borderPhases.get(currentPhaseIndex - 1).getDurationSeconds());
	                applyBorderPhaseState(currentPhaseIndex);
	            }
	        }
	    }

	    private int startBorderShrink(int targetRadius) {
	        ensureGameWorldBorderExists();
	        if (worldBorder == null) {
	            return 0;
	        }
	        double speed = Math.max(0.1, borderShrinkSpeed);
	        double currentRadius = worldBorder.getSize() / 2.0;
	        double safeTargetRadius = resolveSafeZoneRadius(targetRadius);
	        double delta = Math.abs(currentRadius - safeTargetRadius);
	        if (delta <= 0.01) {
	            return 0;
	        }
	        int duration = (int) Math.ceil(delta / speed);
	        duration = Math.max(1, duration);
	        worldBorder.setSize(safeTargetRadius * 2.0, duration);
	        borderShrinkRemaining = duration;
	        return duration;
	    }

	    private void applyBorderPhaseState(int phase) {
	        int radius = getPhaseRadius(phase);
            if (radius <= 0) {
                noSafeZonePhaseActive = true;
                syncWorldBorderForAllPlayers();
                broadcastPhaseDamage(phase, radius);
                return;
            }
	        noSafeZonePhaseActive = false;
	        ensureGameWorldBorder(resolveSafeZoneRadius(radius));
	        syncWorldBorderForAllPlayers();
	        applyBorderDamageBufferForPhase(phase);
	        broadcastPhaseDamage(phase, radius);
	    }

        private void refreshStationaryBorderMotion() {
            if (worldBorder == null || noSafeZonePhaseActive || borderShrinkRemaining > 0) {
                return;
            }
            double currentSize = worldBorder.getSize();
            double nextSize = clampWorldBorderSize(currentSize - (STATIONARY_BORDER_RADIUS_SHRINK_PER_SECOND * 2.0));
            if (nextSize >= currentSize - 1.0E-6) {
                return;
            }
            worldBorder.setSize(nextSize, STATIONARY_BORDER_SHRINK_DURATION_SECONDS);
        }

	    private void applyBorderDamageBufferForPhase(int phase) {
	        if (worldBorder == null) {
	            return;
	        }
	        // 바닐라 자기장 데미지는 사용하지 않음.
	        worldBorder.setDamageBuffer(0.0);
	    }

	    private int getPhaseRadius(int phase) {
	        if (phase <= 0 || phase > borderPhases.size()) {
	            return 0;
	        }
	        return Math.max(0, borderPhases.get(phase - 1).getRadius());
	    }

	    private void ensureGameWorldBorder(double radius) {
	        ensureGameWorldBorderExists();
	        if (worldBorder == null) {
	            return;
	        }
	        worldBorder.setSize(Math.max(WORLD_BORDER_MIN_SIZE, radius * 2.0));
	    }

	    private void ensureGameWorldBorderExists() {
	        World world = gameBorderWorld != null ? gameBorderWorld : getGameWorld();
	        if (world == null) {
	            return;
	        }
	        Location center = startLocation != null ? startLocation : world.getSpawnLocation();
	        if (worldBorder == null) {
	            worldBorder = Bukkit.createWorldBorder();
	            worldBorder.setCenter(center);
	            worldBorder.setDamageBuffer(0.0);
	            worldBorder.setDamageAmount(0.0);
	            worldBorder.setWarningDistance(5);
	            worldBorder.setWarningTime(15);
	        } else {
	            worldBorder.setCenter(center);
	        }
	    }

	    private void broadcastPhaseDamage(int phase, int radius) {
	        double damage = Math.max(1, phase);
	        String message = radius <= 0
	                ? "§c[자기장] §f페이즈 " + phase + " 시작: §c안전지대 소멸§f, 모든 생존자에게 자기장 데미지 §c"
	                        + formatBorderDamage(damage) + "§f/초"
	                : "§c[자기장] §f페이즈 " + phase + " 시작: 자기장 데미지 §c"
	                        + formatBorderDamage(damage) + "§f/초";
	        plugin.getServer().broadcast(Component.text(message));
	    }

	    private double resolveSafeZoneRadius(int radius) {
	        return radius <= 0 ? MIN_SAFE_ZONE_RADIUS : radius;
	    }

	    private String formatBorderDamage(double damage) {
	        if (Math.abs(damage - Math.rint(damage)) < 1.0E-6) {
	            return String.valueOf((int) Math.rint(damage));
	        }
	        return String.format(java.util.Locale.ROOT, "%.1f", damage);
	    }

    private void applyPhaseBasedBorderDamage() {
        if (worldBorder == null || alivePlayers.isEmpty()) {
            return;
        }
        borderDamageIntervalRemaining = Math.max(0, borderDamageIntervalRemaining - 1);
        if (borderDamageIntervalRemaining > 0) {
            return;
        }
        borderDamageIntervalRemaining = BORDER_DAMAGE_INTERVAL_SECONDS;
        int phase = Math.max(1, currentPhaseIndex);
        double damagePerTick = phase;
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || player.isDead()) {
                continue;
            }
            if (gameBorderWorld != null && !gameBorderWorld.equals(player.getWorld())) {
                continue;
            }
            if (!noSafeZonePhaseActive && worldBorder != null && worldBorder.isInside(player.getLocation())) {
                continue;
            }
            player.damage(damagePerTick);
        }
    }

    private void resetWorldBorder() {
        // 게임이 진행된 월드의 보더를 찾아서 리셋
        World world = gameBorderWorld;
        if (world == null && startLocation != null && startLocation.getWorld() != null) {
            world = startLocation.getWorld();
        } else if (world == null && originalBorderWorld != null) {
            world = originalBorderWorld;
        } else if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }

        if (world == null) {
            return;
        }

        WorldBorder border = world.getWorldBorder();

        // 원본 정보 복원 또는 바닐라 기본값으로 리셋
        if (originalBorderCenter != null) {
            border.setCenter(originalBorderCenter);
        } else {
            border.setCenter(world.getSpawnLocation());
        }

        // 진행 중인 축소 애니메이션을 즉시 중지하려면 setSize를 두 번 호출
        double targetSize = originalBorderSize > 0 ? originalBorderSize : WORLD_BORDER_MAX_SIZE;
        border.setSize(clampWorldBorderSize(targetSize)); // 즉시 적용

        // 데미지/경고 설정을 바닐라 기본값으로 복원
        border.setDamageBuffer(5.0); // 바닐라 기본값
        border.setDamageAmount(0.2); // 바닐라 기본값
        border.setWarningDistance(5); // 바닐라 기본값
        border.setWarningTime(15); // 바닐라 기본값

        // 상태 초기화
        borderShrinkRemaining = 0;
        worldBorder = null;
        gameBorderWorld = null;
        noSafeZonePhaseActive = false;
        originalBorderWorld = null;
        originalBorderCenter = null;
        originalBorderSize = 0;
        syncWorldBorderForAllPlayers();
    }

    public boolean isInsideGameBorder(Location location) {
        if (location == null || location.getWorld() == null) {
            return true;
        }
        if (noSafeZonePhaseActive && gameBorderWorld != null && gameBorderWorld.equals(location.getWorld())) {
            return false;
        }
        if (worldBorder != null && gameBorderWorld != null && gameBorderWorld.equals(location.getWorld())) {
            return worldBorder.isInside(location);
        }
        return location.getWorld().getWorldBorder().isInside(location);
    }

    public Location getGameBorderCenter(World world) {
        if (world == null) {
            return null;
        }
        if (noSafeZonePhaseActive && gameBorderWorld != null && gameBorderWorld.equals(world)) {
            return startLocation != null ? startLocation : world.getSpawnLocation();
        }
        if (worldBorder != null && gameBorderWorld != null && gameBorderWorld.equals(world)) {
            return worldBorder.getCenter();
        }
        return world.getWorldBorder().getCenter();
    }

    private void syncWorldBorderForAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncPlayerWorldBorder(player);
        }
    }

    private void syncPlayerWorldBorder(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!noSafeZonePhaseActive && worldBorder != null && gameBorderWorld != null
                && gameBorderWorld.equals(player.getWorld())) {
            player.setWorldBorder(worldBorder);
            return;
        }
        player.setWorldBorder(player.getWorld().getWorldBorder());
    }

    private void startFixedDaytime() {
        if (fixedDaytimeTask != null) {
            fixedDaytimeTask.cancel();
            untrackTask(fixedDaytimeTask);
            fixedDaytimeTask = null;
        }
        fixedDaytimeTask = trackTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            applyFixedDaytimeToWorlds();
        }, 1L, 1L));
        applyFixedDaytimeToWorlds();
    }

    private void restoreFixedDaytime() {
        if (fixedDaytimeTask != null) {
            fixedDaytimeTask.cancel();
            untrackTask(fixedDaytimeTask);
            fixedDaytimeTask = null;
        }
        for (Map.Entry<UUID, FixedWorldState> entry : new ArrayList<>(fixedWorldStates.entrySet())) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            FixedWorldState snapshot = entry.getValue();
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, snapshot.doDaylightCycle());
            world.setFullTime(snapshot.fullTime());
            world.setStorm(snapshot.storm());
            world.setThundering(snapshot.thundering());
            world.setClearWeatherDuration(snapshot.clearWeatherDuration());
            world.setWeatherDuration(snapshot.weatherDuration());
            world.setThunderDuration(snapshot.thunderDuration());
        }
        fixedWorldStates.clear();
    }

    private void applyFixedDaytimeToWorlds() {
        for (World world : Bukkit.getWorlds()) {
            fixedWorldStates.computeIfAbsent(world.getUID(), ignored -> new FixedWorldState(
                    Boolean.TRUE.equals(world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE)),
                    world.getFullTime(),
                    world.hasStorm(),
                    world.isThundering(),
                    world.getClearWeatherDuration(),
                    world.getWeatherDuration(),
                    world.getThunderDuration()));
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setTime(4000L);
            world.setStorm(false);
            world.setThundering(false);
            world.setClearWeatherDuration(Integer.MAX_VALUE);
            world.setWeatherDuration(0);
            world.setThunderDuration(0);
        }
    }

    private World getGameWorld() {
        if (startLocation != null && startLocation.getWorld() != null) {
            return startLocation.getWorld();
        }
        if (originalBorderWorld != null) {
            return originalBorderWorld;
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    private double clampWorldBorderSize(double size) {
        if (!Double.isFinite(size)) {
            return WORLD_BORDER_MAX_SIZE;
        }
        if (size < WORLD_BORDER_MIN_SIZE) {
            return WORLD_BORDER_MIN_SIZE;
        }
        if (size > WORLD_BORDER_MAX_SIZE) {
            return WORLD_BORDER_MAX_SIZE;
        }
        return size;
    }

    private void resetPlayers() {
        for (UUID uuid : participants.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            Participant participant = participants.get(uuid);
            if (participant != null) {
                participant.clearAbility();
                participant.setTargetable(true);
            }
            resetPlayerAttributes(player);
            player.setInvulnerable(false);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setCollidable(true);
        }
        clearMovementLocks();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                viewer.showPlayer(plugin, target);
            }
        }
    }

    private void teleportToLobby(Player player) {
        if (player == null) {
            return;
        }
        Location lobby = lobbyLocation != null ? lobbyLocation : player.getWorld().getSpawnLocation();
        player.teleport(lobby);
    }

    private void gatherPlayersToLobby() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            teleportToLobby(player);
        }
    }

    private void healPlayer(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0;
        if (!Double.isFinite(max) || max <= 0) {
            max = 20.0;
        }
        player.setHealth(max);
    }

    private boolean healAllOnlinePlayersOnce() {
        boolean pending = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (player.isDead()) {
                pending = true;
                continue;
            }
            healPlayer(player);
        }
        return pending;
    }

    private void healAllOnlinePlayersWithRetries(int retries) {
        boolean pending = healAllOnlinePlayersOnce();
        if (!pending || retries <= 0) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> healAllOnlinePlayersWithRetries(retries - 1), 1L);
    }

    private void resetPlayerAttributes(Player player) {
        if (player == null)
            return;

        // 크기 초기화
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale != null)
            scale.setBaseValue(scale.getDefaultValue());

        // 공격 사거리 초기화
        AttributeInstance range = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (range != null)
            range.setBaseValue(range.getDefaultValue());

        // 최대 체력 초기화
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getDefaultValue());
            double current = player.getHealth();
            if (current > 0 && !player.isDead()) {
                player.setHealth(Math.min(current, health.getValue()));
            }
        }

        // 공격력 초기화
        AttributeInstance damage = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null)
            damage.setBaseValue(damage.getDefaultValue());

        // 전투 속성 초기화
        syncCombatSettings(player);

        // 포션 효과 초기화
        for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    private void applyLegacyDamageImmunity(Player player) {
        if (player == null) {
            return;
        }
        player.setMaximumNoDamageTicks(LEGACY_PLAYER_NO_DAMAGE_TICKS);
        if (player.getNoDamageTicks() > LEGACY_PLAYER_NO_DAMAGE_TICKS) {
            player.setNoDamageTicks(LEGACY_PLAYER_NO_DAMAGE_TICKS);
        }
    }

    private void applyAttackCooldownSetting(Player player) {
        if (player == null) {
            return;
        }
        AttributeInstance attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed == null) {
            return;
        }
        double targetAttackSpeed = shouldUseDefaultAttackSpeed(player)
                ? attackSpeed.getDefaultValue()
                : NO_ATTACK_COOLDOWN_ATTACK_SPEED;
        if (Double.compare(attackSpeed.getBaseValue(), targetAttackSpeed) != 0) {
            attackSpeed.setBaseValue(targetAttackSpeed);
        }
        applySweepingDamageSetting(player);
    }

    private void applySweepingDamageSetting(Player player) {
        AttributeInstance sweepingDamage = player.getAttribute(Attribute.SWEEPING_DAMAGE_RATIO);
        if (sweepingDamage == null) {
            return;
        }
        double targetSweepingDamage = attackCooldownEnabled ? sweepingDamage.getDefaultValue() : NO_SWEEPING_DAMAGE_RATIO;
        if (Double.compare(sweepingDamage.getBaseValue(), targetSweepingDamage) != 0) {
            sweepingDamage.setBaseValue(targetSweepingDamage);
        }
    }

    private void syncCombatSettings(Player player) {
        if (player == null) {
            return;
        }
        applyAttackCooldownSetting(player);
        applyFixedAxeDamageComponents(player);
        applyLegacyDamageImmunity(player);
    }

    private void applyAttackCooldownSettingToOnlinePlayers() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            applyAttackCooldownSetting(online);
        }
    }

    private boolean shouldUseDefaultAttackSpeed(Player player) {
        if (attackCooldownEnabled) {
            return true;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand == null || mainHand.getType() == Material.AIR) {
            return false;
        }
        String weaponName = mainHand.getType().name();
        return "TRIDENT".equals(weaponName) || weaponName.contains("SPEAR");
    }

    private void startAttackSpeedSyncTask() {
        if (attackSpeedSyncTask != null) {
            return;
        }
        attackSpeedSyncTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tickOldCombatAdjustments(OLD_COMBAT_TASK_PERIOD_TICKS);
        }, OLD_COMBAT_TASK_PERIOD_TICKS, OLD_COMBAT_TASK_PERIOD_TICKS);
    }

    private void applyFixedAxeDamageComponents(Player player) {
        if (player == null) {
            return;
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!isAxeWeapon(item)) {
                continue;
            }
            if (!applyFixedAxeDamageComponent(item)) {
                continue;
            }
            player.getInventory().setItem(slot, item);
        }
    }

    private boolean applyFixedAxeDamageComponent(ItemStack item) {
        if (!isAxeWeapon(item)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.getPersistentDataContainer().has(fixedAxeDamageKey, PersistentDataType.BYTE)) {
            return false;
        }
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(fixedAxeDamageKey, FIXED_AXE_DAMAGE_MODIFIER,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.getPersistentDataContainer().set(fixedAxeDamageKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return true;
    }

    @SuppressWarnings("unused")
    private void assignAbility(Player player, AbilityDefinition definition) {
        assignAbility(player, definition, false);
    }

    private void assignAbility(Player player, AbilityDefinition definition, boolean countPick) {
        Participant participant = participants.computeIfAbsent(player.getUniqueId(), key -> new Participant(player));
        boolean shouldRecordPick = countPick
                && (participant.getAbilityDefinition() == null
                        || !participant.getAbilityDefinition().getName().equals(definition.getName()));
        participant.setAbilityDefinition(definition);
        participant.removeAbility();
        if (shouldRecordPick) {
            abilityRegistry.recordPick(definition);
        }
        if (AbilityFactory.isRegistered(definition.getName())) {
            AbilityBase ability = AbilityFactory.create(definition.getName(), participant);
            participant.setAbility(ability);
        } else {
            player.sendMessage("§c아직 구현되지 않은 능력입니다. §f" + definition.getName());
        }
    }

    private void stopTasks() {
        stopSelectionTask();
        stopGameTask();
        cancelMapScan();
        cancelMapRestore();
        cancelVictoryTask();
        cancelTrackedTasks();
    }

    private void stopSelectionTask() {
        if (selectionTask != null) {
            selectionTask.cancel();
            untrackTask(selectionTask);
            selectionTask = null;
        }
    }

    private void stopGameTask() {
        if (gameTask != null) {
            gameTask.cancel();
            untrackTask(gameTask);
            gameTask = null;
        }
        if (visualTask != null) {
            visualTask.cancel();
            untrackTask(visualTask);
            visualTask = null;
        }
    }

    private void cancelVictoryTask() {
        if (victoryTask != null) {
            victoryTask.cancel();
            untrackTask(victoryTask);
            victoryTask = null;
        }
    }

    private BukkitTask trackTask(BukkitTask task) {
        if (task != null) {
            trackedTasks.add(task);
        }
        return task;
    }

    private void untrackTask(BukkitTask task) {
        if (task != null) {
            trackedTasks.remove(task);
        }
    }

    private void cancelTrackedTasks() {
        for (BukkitTask task : new ArrayList<>(trackedTasks)) {
            task.cancel();
        }
        trackedTasks.clear();
    }

    private boolean isSpectator(Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public boolean isAlive(Player player) {
        return alivePlayers.contains(player.getUniqueId());
    }

    private boolean canUseAbility(Player player) {
        return canTriggerAbilityEffects(player);
    }

    public void lockMovement(LivingEntity target, int ticks) {
        if (target == null || ticks <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long until = now + ticks * 50L;
        Long current = movementLocks.get(target.getUniqueId());
        if (current != null && current > until) {
            until = current;
        }
        setMovementLockUntil(target, until);
    }

    public void setMovementLockUntil(LivingEntity target, long untilMillis) {
        if (target == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (untilMillis <= now) {
            unlockMovement(target);
            return;
        }
        movementLocks.put(target.getUniqueId(), untilMillis);
        applyMovementLock(target);
        long ticks = Math.max(1L, (untilMillis - now + 49L) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> tryUnlockMovement(target.getUniqueId()), ticks);
    }

    public void unlockMovement(LivingEntity target) {
        if (target == null) {
            return;
        }
        clearMovementLock(target.getUniqueId());
    }

    public void clearMovementLocks() {
        Set<UUID> lockedIds = new HashSet<>(movementLocks.keySet());
        lockedIds.addAll(storedAi.keySet());
        for (UUID uuid : lockedIds) {
            clearMovementLock(uuid);
        }
        movementLocks.clear();
        storedAi.clear();
    }

    public boolean isMovementLocked(LivingEntity target) {
        if (target == null) {
            return false;
        }
        Long until = movementLocks.get(target.getUniqueId());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            tryUnlockMovement(target.getUniqueId());
            return false;
        }
        return true;
    }

    private void tryUnlockMovement(UUID uuid) {
        Long until = movementLocks.get(uuid);
        if (until == null || System.currentTimeMillis() < until) {
            return;
        }
        clearMovementLock(uuid);
    }

    private void applyMovementLock(LivingEntity target) {
        if (target instanceof Player player) {
            player.setVelocity(player.getVelocity().multiply(0));
            return;
        }
        if (target instanceof Mob mob) {
            storedAi.putIfAbsent(target.getUniqueId(), mob.hasAI());
            mob.setAI(false);
            mob.setVelocity(mob.getVelocity().multiply(0));
        }
    }

    private void clearMovementLock(UUID uuid) {
        movementLocks.remove(uuid);
        Boolean ai = storedAi.remove(uuid);
        if (ai == null) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity instanceof Mob mob) {
                mob.setAI(ai);
                break;
            }
        }
    }

    private void applyHungerLock(Player player) {
        player.setFoodLevel(FIXED_FOOD_LEVEL);
        player.setSaturation(Math.min(FIXED_FOOD_LEVEL, FIXED_SATURATION));
        player.setExhaustion(0.0f);
    }

    private void tickOldCombatAdjustments(int elapsedTicks) {
        int currentTick = Bukkit.getCurrentTick();
        pendingKnockbacks.entrySet().removeIf(entry -> entry.getValue().expiresAtTick < currentTick);
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyHungerLock(player);
            tickOldNaturalRegen(player, elapsedTicks);
        }
    }

    private void tickOldNaturalRegen(Player player, int elapsedTicks) {
        UUID uuid = player.getUniqueId();
        if (!canUseOldNaturalRegen(player)) {
            naturalRegenCounters.remove(uuid);
            return;
        }
        int counter = naturalRegenCounters.getOrDefault(uuid, 0) + Math.max(1, elapsedTicks);
        if (counter < OLD_NATURAL_REGEN_INTERVAL_TICKS) {
            naturalRegenCounters.put(uuid, counter);
            return;
        }
        naturalRegenCounters.put(uuid, 0);
        applyOldNaturalRegen(player);
    }

    private boolean canUseOldNaturalRegen(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
            return false;
        }
        return player.getHealth() < getMaxHealth(player);
    }

    private void applyOldNaturalRegen(Player player) {
        UUID uuid = player.getUniqueId();
        manualNaturalRegen.add(uuid);
        try {
            EntityRegainHealthEvent event = new EntityRegainHealthEvent(player, OLD_NATURAL_REGEN_AMOUNT,
                    EntityRegainHealthEvent.RegainReason.SATIATED, false);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            double maxHealth = getMaxHealth(player);
            player.setHealth(Math.min(maxHealth, player.getHealth() + event.getAmount()));
        } finally {
            manualNaturalRegen.remove(uuid);
        }
    }

    private double getMaxHealth(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        return maxHealth != null ? maxHealth.getValue() : 20.0;
    }

    private Vector createOldPlayerKnockback(Player attacker, Player target) {
        Vector direction = target.getLocation().toVector().subtract(attacker.getLocation().toVector()).setY(0);
        if (direction.lengthSquared() <= 1.0E-6) {
            direction = attacker.getLocation().getDirection().clone().setY(0);
        }
        if (direction.lengthSquared() <= 1.0E-6) {
            return null;
        }
        direction.normalize();
        double horizontal = OLD_PLAYER_KNOCKBACK_HORIZONTAL;
        double vertical = OLD_PLAYER_KNOCKBACK_VERTICAL;
        if (attacker.isSprinting()) {
            horizontal += OLD_PLAYER_EXTRA_KNOCKBACK;
            vertical += OLD_PLAYER_EXTRA_VERTICAL_KNOCKBACK;
        }
        Vector velocity = target.getVelocity().clone().multiply(0.5);
        velocity.setX(velocity.getX() + direction.getX() * horizontal);
        velocity.setZ(velocity.getZ() + direction.getZ() * horizontal);
        velocity.setY(Math.min(OLD_PLAYER_KNOCKBACK_VERTICAL_LIMIT, velocity.getY() / 2.0 + vertical));
        return velocity;
    }

    private void updateScoreboard() {
        if (state != GameState.RUNNING) {
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateScoreboard(player, manager);
        }
    }

    private void clearScoreboard() {
        playerScoreboards.clear();
        playerSidebarEntries.clear();
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard main = manager.getMainScoreboard();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(main);
        }
    }

	    private List<String> buildScoreboardLines(Player viewer) {
	        List<String> lines = new ArrayList<>();
	        if (invincible) {
	            lines.add("§e무적 해제까지: §f" + formatTime(invincibilityRemaining));
	            return lines;
	        }
	        if (borderShrinkRemaining > 0) {
	            lines.add("§e페이즈: §f자기장 축소 중.");
	            lines.add("§e다음 페이즈까지 남은 시간: §f" + formatTime(borderShrinkRemaining));
	            return lines;
	        }
	        int currentPhase = Math.max(1, currentPhaseIndex);
	        lines.add("§e페이즈: §f" + currentPhase);
	        if (borderPhases.isEmpty() || currentPhaseIndex >= borderPhases.size()) {
	            lines.add("§e다음 페이즈까지 남은 시간: §f-");
	        } else {
	            lines.add("§e다음 페이즈까지 남은 시간: §f" + formatTime(phaseRemaining));
	        }
            if (isTeamMode() && viewer != null && isAlive(viewer)) {
                CombatTeam team = getPlayerTeam(viewer);
                if (team != null) {
                    lines.add(" ");
                    lines.add(team.getLegacyColor() + "내 팀");
                    for (String teammate : getAliveTeamMemberNames(team)) {
                        lines.add(team.getLegacyColor() + teammate);
                    }
                }
            }
	        return lines;
	    }

    private void updateScoreboard(Player viewer, ScoreboardManager manager) {
        UUID viewerId = viewer.getUniqueId();
        Scoreboard board = playerScoreboards.computeIfAbsent(viewerId, id -> createScoreboard(manager));
        Objective sidebar = board.getObjective("aw_status");
        if (sidebar == null) {
            board = createScoreboard(manager);
            playerScoreboards.put(viewerId, board);
            sidebar = board.getObjective("aw_status");
        }
        configureTeams(board);
        List<String> lines = buildScoreboardLines(viewer);
        Set<String> used = new HashSet<>();
        List<String> entries = new ArrayList<>(lines.size());
        for (String line : lines) {
            entries.add(makeUniqueLine(line, used));
        }
        List<String> oldEntries = playerSidebarEntries.getOrDefault(viewerId, List.of());
        for (String oldEntry : oldEntries) {
            if (!entries.contains(oldEntry)) {
                board.resetScores(oldEntry);
            }
        }
        int score = entries.size();
        for (String entry : entries) {
            sidebar.getScore(entry).setScore(score--);
        }
        playerSidebarEntries.put(viewerId, entries);
        if (viewer.getScoreboard() != board) {
            viewer.setScoreboard(board);
        }
    }

    private Scoreboard createScoreboard(ScoreboardManager manager) {
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective sidebar = scoreboard.registerNewObjective("aw_status", Criteria.DUMMY,
                Component.text("AbilityCombat").color(NamedTextColor.GOLD), RenderType.INTEGER);
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        sidebar.numberFormat(NumberFormat.blank());
        return scoreboard;
    }

    private void configureTeams(Scoreboard scoreboard) {
        Set<CombatTeam> activeTeams = getActiveTeams();
        Set<String> activeTeamNames = new HashSet<>();
        for (CombatTeam team : activeTeams) {
            activeTeamNames.add(team.getScoreboardName());
        }
        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            if (team.getName().startsWith("aw_team_") && !activeTeamNames.contains(team.getName())) {
                team.unregister();
            }
        }
        for (Participant participant : participants.values()) {
            Player player = participant.getPlayer();
            if (player == null) {
                continue;
            }
            for (Team team : scoreboard.getTeams()) {
                if (team.getName().startsWith("aw_team_")) {
                    team.removeEntry(player.getName());
                }
            }
            CombatTeam combatTeam = participant.getTeam();
            if (combatTeam != null) {
                Team scoreboardTeam = getOrCreateScoreboardTeam(scoreboard, combatTeam);
                scoreboardTeam.addEntry(player.getName());
            }
        }
    }

    private Team getOrCreateScoreboardTeam(Scoreboard scoreboard, CombatTeam combatTeam) {
        Team team = scoreboard.getTeam(combatTeam.getScoreboardName());
        if (team == null) {
            team = scoreboard.registerNewTeam(combatTeam.getScoreboardName());
        }
        team.color(combatTeam.getNamedColor());
        team.setAllowFriendlyFire(false);
        return team;
    }

    private Set<CombatTeam> getActiveTeams() {
        Set<CombatTeam> teams = new HashSet<>();
        for (Participant participant : participants.values()) {
            if (participant.getTeam() != null) {
                teams.add(participant.getTeam());
            }
        }
        return teams;
    }

    private CombatTeam getPlayerTeam(Player player) {
        Participant participant = player == null ? null : participants.get(player.getUniqueId());
        return participant != null ? participant.getTeam() : null;
    }

    private List<String> getAliveTeamMemberNames(CombatTeam team) {
        List<String> names = new ArrayList<>();
        for (UUID uuid : alivePlayers) {
            Participant participant = participants.get(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (participant != null && player != null && team.equals(participant.getTeam())) {
                names.add(player.getName());
            }
        }
        Collections.sort(names);
        return names;
    }

    private int countAlivePlayers(CombatTeam team) {
        int count = 0;
        for (UUID uuid : alivePlayers) {
            Participant participant = participants.get(uuid);
            if (participant != null && team.equals(participant.getTeam())) {
                count++;
            }
        }
        return count;
    }

    private void updateTeamHealthDisplays() {
        if (state != GameState.RUNNING || !isTeamMode()) {
            clearTeamHealthDisplays();
            return;
        }
        Set<UUID> aliveIds = new HashSet<>(alivePlayers);
        teamHealthDisplays.entrySet().removeIf(entry -> {
            TextDisplay display = entry.getValue();
            boolean remove = !aliveIds.contains(entry.getKey()) || display == null || display.isDead();
            if (remove && display != null && !display.isDead()) {
                display.remove();
            }
            return remove;
        });
        for (UUID uuid : alivePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            Participant participant = participants.get(uuid);
            if (player == null || participant == null || participant.getTeam() == null) {
                continue;
            }
            TextDisplay display = teamHealthDisplays.get(uuid);
            if (display == null || display.isDead() || display.getWorld() != player.getWorld()) {
                if (display != null && !display.isDead()) {
                    display.remove();
                }
                display = player.getWorld().spawn(getTeamHealthDisplayLocation(player), TextDisplay.class, entity -> {
                    entity.setBillboard(Display.Billboard.CENTER);
                    entity.setSeeThrough(true);
                    entity.setShadowed(false);
                    entity.setViewRange(32f);
                    entity.setInterpolationDuration(2);
                    entity.setTeleportDuration(2);
                });
                teamHealthDisplays.put(uuid, display);
            }
            display.text(buildTeamHealthText(player));
            display.teleport(getTeamHealthDisplayLocation(player));
        }
        syncTeamHealthDisplayVisibility();
    }

    private void syncTeamHealthDisplayVisibility() {
        for (Map.Entry<UUID, TextDisplay> entry : teamHealthDisplays.entrySet()) {
            UUID ownerId = entry.getKey();
            TextDisplay display = entry.getValue();
            Player owner = Bukkit.getPlayer(ownerId);
            if (display == null || display.isDead() || owner == null) {
                continue;
            }
            CombatTeam ownerTeam = getPlayerTeam(owner);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                boolean visible = state == GameState.RUNNING
                        && isTeamMode()
                        && ownerTeam != null
                        && isAlive(owner)
                        && isAlive(viewer)
                        && areTeammates(owner, viewer);
                if (visible) {
                    viewer.showEntity(plugin, display);
                } else {
                    viewer.hideEntity(plugin, display);
                }
            }
        }
    }

    private void clearTeamHealthDisplays() {
        for (TextDisplay display : teamHealthDisplays.values()) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
        teamHealthDisplays.clear();
    }

    private Component buildTeamHealthText(Player player) {
        double health = Math.ceil(Math.max(0.0, player.getHealth()));
        double maxHealth = 20.0;
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            maxHealth = attribute.getValue();
        }
        NamedTextColor color = health <= Math.max(4.0, maxHealth * 0.25) ? NamedTextColor.RED : NamedTextColor.GREEN;
        return Component.text((int) health + " HP", color);
    }

    private Location getTeamHealthDisplayLocation(Player player) {
        return player.getLocation().clone().add(0, player.getHeight() + 0.55, 0);
    }

    private String formatTime(int seconds) {
        int minutes = Math.max(0, seconds) / 60;
        int remain = Math.max(0, seconds) % 60;
        return String.format("%02d:%02d", minutes, remain);
    }

    private void updateSelectionHud() {
        var channel = plugin.getActionbarChannel();
        Component message = Component.text("능력 선택까지: " + formatTime(Math.max(0, selectionRemaining)),
                NamedTextColor.GOLD);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (channel != null) {
                channel.update(player, SELECTION_HUD_KEY, SELECTION_HUD_PRIORITY, message);
            } else {
                player.sendActionBar(message);
            }
        }
    }

    private void clearSelectionHud() {
        var channel = plugin.getActionbarChannel();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (channel != null) {
                channel.clear(player, SELECTION_HUD_KEY);
            } else {
                player.sendActionBar(Component.empty());
            }
        }
    }

    private String makeUniqueLine(String line, Set<String> used) {
        String result = line == null ? "" : line;
        StringBuilder sb = new StringBuilder(result);
        while (used.contains(sb.toString())) {
            sb.append(" ");
        }
        result = sb.toString();
        used.add(result);
        return result;
    }

    private void checkAutoEnd() {
        if (state != GameState.RUNNING || startedSolo) {
            return;
        }
        if (!isTeamMode() && alivePlayers.size() <= 1) {
            plugin.getServer().broadcast(Component.text("§e남은 플레이어가 1명이 되어 게임이 종료됩니다."));
            stopGame();
            return;
        }
        if (isTeamMode() && resolveWinningTeam() != null) {
            plugin.getServer().broadcast(Component.text("§e한 팀만 남아 게임이 종료됩니다."));
            stopGame();
        }
    }

    private void setSpectator(Player player) {
        UUID uuid = player.getUniqueId();
        alivePlayers.remove(uuid);
        spectators.add(uuid);
        Participant participant = participants.get(uuid);
        if (participant != null) {
            participant.setTargetable(false);
            participant.removeAbility();
        }
        resetPlayerAttributes(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
        player.setInvulnerable(true);
        updateVisibility();
        syncTeamHealthDisplayVisibility();
        checkAutoEnd();
    }

    private void updateVisibility() {
        if (!hideSpectators) {
            syncTeamHealthDisplayVisibility();
            return;
        }
        List<Player> alive = new ArrayList<>();
        List<Player> spec = new ArrayList<>();
        for (UUID uuid : alivePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                alive.add(player);
            }
        }
        for (UUID uuid : spectators) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                spec.add(player);
            }
        }
        for (Player alivePlayer : alive) {
            for (Player spectator : spec) {
                alivePlayer.hidePlayer(plugin, spectator);
            }
        }
        for (Player spectator : spec) {
            for (Player other : spec) {
                spectator.showPlayer(plugin, other);
            }
            for (Player alivePlayer : alive) {
                spectator.showPlayer(plugin, alivePlayer);
            }
        }
        syncTeamHealthDisplayVisibility();
    }

    private boolean allSelected() {
        for (SelectionSession session : selectionSessions.values()) {
            if (session.selected == null) {
                return false;
            }
        }
        return true;
    }

    private void selectAbility(Player player, SelectionSession session, AbilityDefinition ability) {
        if (player == null || session == null || ability == null || session.selected != null) {
            return;
        }
        session.selected = ability;
        session.awaitingChaosPreview = false;
        session.pendingChaosDefinition = null;
        session.pendingChaosFirst = null;
        session.pendingChaosSecond = null;
        assignAbility(player, ability, true);
        sendAbilityInfo(player);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.2f);
        player.closeInventory();
        if (allSelected()) {
            finalizeSelection();
        }
    }

    private void beginChaosPreview(Player player, SelectionSession session, AbilityDefinition chaosDefinition) {
        if (player == null || session == null || chaosDefinition == null) {
            return;
        }
        List<AbilityDefinition> firstOptions = getRandomOptions(getChaosFirstCandidateDefinitions(), 1);
        if (firstOptions.isEmpty()) {
            player.sendMessage("§c혼돈으로 선택할 수 있는 능력이 부족합니다");
            return;
        }
        AbilityDefinition first = firstOptions.get(0);
        List<AbilityDefinition> secondCandidates = new ArrayList<>();
        for (AbilityDefinition definition : getChaosSecondCandidateDefinitions()) {
            if (!definition.getName().equals(first.getName())) {
                secondCandidates.add(definition);
            }
        }
        List<AbilityDefinition> secondOptions = getRandomOptions(secondCandidates, 1);
        if (secondOptions.isEmpty()) {
            player.sendMessage("§c혼돈 2번 능력으로 선택할 수 있는 능력이 부족합니다");
            return;
        }
        session.awaitingChaosPreview = true;
        session.pendingChaosDefinition = chaosDefinition;
        session.pendingChaosFirst = first;
        session.pendingChaosSecond = secondOptions.get(0);
        Chaos.prepare(player.getUniqueId(), session.pendingChaosFirst, session.pendingChaosSecond);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
        player.openInventory(new ChaosPreviewGui(player.getUniqueId(), session.pendingChaosFirst,
                session.pendingChaosSecond).getInventory());
    }

    private void confirmChaosPreview(Player player, ChaosPreviewGui previewGui) {
        if (player == null || previewGui == null || state != GameState.SELECTING) {
            return;
        }
        if (!player.getUniqueId().equals(previewGui.getPlayerId())) {
            return;
        }
        SelectionSession session = selectionSessions.get(player.getUniqueId());
        if (session == null || session.selected != null || !session.awaitingChaosPreview
                || session.pendingChaosDefinition == null
                || session.pendingChaosFirst == null || session.pendingChaosSecond == null) {
            return;
        }
        Chaos.prepare(player.getUniqueId(), session.pendingChaosFirst, session.pendingChaosSecond);
        session.awaitingChaosPreview = false;
        selectAbility(player, session, session.pendingChaosDefinition);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof AbilitySelectGui selectGui) {
            event.setCancelled(true);
            if (state != GameState.SELECTING) {
                // 이미 게임이 시작되었으면 강제로 닫기
                player.closeInventory();
                return;
            }
            if (!player.getUniqueId().equals(selectGui.getPlayerId())) {
                return;
            }

            // 재설정 버튼 클릭 처리
            if (selectGui.isRerollSlot(event.getRawSlot())) {
                SelectionSession session = selectionSessions.get(player.getUniqueId());
                if (session != null && session.canReroll()) {
                    AbilityDefinition toReplace = selectGui.getAbilityForReroll(event.getRawSlot());
                    if (toReplace != null) {
                        // 새로운 능력 선택 (제외 목록 적용)
                        AbilityDefinition replacement = getRandomOptionExcluding(getImplementedDefinitions(),
                                session.excludedAbilities);
                        if (replacement != null) {
                            session.excludedAbilities.add(replacement.getName());
                            // 기존 옵션에서 교체
                            int index = session.options.indexOf(toReplace);
                            if (index >= 0) {
                                session.options.set(index, replacement);
                            }
                            session.useReroll();
                            int abilitySlot = selectGui.getAbilitySlotForReroll(event.getRawSlot());
                            selectGui.updateAbility(abilitySlot, replacement);
                            selectGui.updateRerollButtons(session.getRerollsRemaining());
                            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
                            player.sendMessage("§e능력이 재설정되었습니다. (남은 횟수: " + session.getRerollsRemaining() + "회)");
                        } else {
                            player.sendMessage("§c대체할 능력이 없습니다.");
                        }
                    }
                }
                return;
            }

            AbilityDefinition ability = selectGui.getAbilityAt(event.getRawSlot());
            if (ability != null) {
                SelectionSession session = selectionSessions.get(player.getUniqueId());
                if (session != null && session.selected == null) {
                    if (Chaos.isChaosAbility(ability.getName())) {
                        beginChaosPreview(player, session, ability);
                    } else {
                        selectAbility(player, session, ability);
                    }
                }
            }
            return;
        }
        if (holder instanceof ChaosPreviewGui previewGui) {
            event.setCancelled(true);
            if (!player.getUniqueId().equals(previewGui.getPlayerId())) {
                return;
            }
            return;
        }
        if (holder instanceof AbilityDebugGui debugGui) {
            event.setCancelled(true);
            AbilityDefinition ability = debugGui.getAbilityAt(event.getRawSlot());
            if (ability != null && !debugGui.isViewOnly()) {
                debugAbilityUsers.add(player.getUniqueId());
                assignAbility(player, ability, false);
                player.closeInventory();
                return;
            }
            if (debugGui.isPrevSlot(event.getRawSlot()) && debugGui.getPage() > 0) {
                openDebugGui(player, debugGui.getPage() - 1, debugGui.isViewOnly());
                return;
            }
            if (debugGui.isNextSlot(event.getRawSlot()) && debugGui.getPage() + 1 < debugGui.getPageCount()) {
                openDebugGui(player, debugGui.getPage() + 1, debugGui.isViewOnly());
                return;
            }
        }
        if (holder instanceof ToolkitGui toolkitGui) {
            handleToolkitClick(player, toolkitGui, event);
            return;
        }
        if (holder instanceof com.abilitycombat.gui.MapConfigGui mapConfigGui) {
            event.setCancelled(true);
            handleMapConfigClick(player, mapConfigGui, event);
            return;
        }
        if (holder instanceof com.abilitycombat.gui.MapSelectGui mapSelectGui) {
            event.setCancelled(true);
            handleMapSelectClick(player, mapSelectGui, event);
            return;
        }
        if (holder instanceof ConfigGui configGui) {
            event.setCancelled(true);
            handleConfigClick(player, configGui, event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof AbilitySelectGui) {
            // SELECTING 모드일 때만 재오픈 로직 작동
            if (state != GameState.SELECTING) {
                return;
            }
            SelectionSession session = selectionSessions.get(player.getUniqueId());
            if (session != null && session.selected == null && !session.awaitingChaosPreview) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (state == GameState.SELECTING && player.isOnline()) {
                        player.openInventory(
                                new AbilitySelectGui(player.getUniqueId(), session.options,
                                        session.getRerollsRemaining()).getInventory());
                    }
                }, 1L);
            }
        }
        if (holder instanceof ChaosPreviewGui previewGui) {
            Bukkit.getScheduler().runTask(plugin, () -> confirmChaosPreview(player, previewGui));
            return;
        }
        if (holder instanceof ToolkitGui toolkitGui) {
            toolkitGui.saveItems();
            player.sendMessage(Component.text("§a기본 지급템이 저장되었습니다."));
        }
    }

    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        if ((state == GameState.RUNNING || state == GameState.SELECTING)
                && isShieldCombination(event.getInventory().getResult(), event.getInventory().getMatrix())) {
            event.getInventory().setResult(new ItemStack(Material.AIR));
            return;
        }
        if (craftingEnabled) {
            return;
        }
        // Only care about player crafting; ignore non-player viewers (if any).
        boolean hasPlayerViewer = false;
        for (HumanEntity viewer : event.getViewers()) {
            if (viewer instanceof Player) {
                hasPlayerViewer = true;
                break;
            }
        }
        if (!hasPlayerViewer) {
            return;
        }
        event.getInventory().setResult(new ItemStack(Material.AIR));
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (state != GameState.RUNNING && state != GameState.SELECTING) {
            return;
        }
        ItemStack left = event.getInventory().getItem(0);
        ItemStack right = event.getInventory().getItem(1);
        ItemStack result = event.getResult();
        if (isShieldItem(left) || isShieldItem(right) || isShieldItem(result)) {
            event.setResult(new ItemStack(Material.AIR));
        }
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if ((state == GameState.RUNNING || state == GameState.SELECTING)
                && isShieldCombination(event.getInventory().getResult(), event.getInventory().getContents())) {
            event.setCancelled(true);
            return;
        }
        if (craftingEnabled) {
            return;
        }
        if (event.getWhoClicked() instanceof Player player && isSpectator(player)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
    }

    private void handleConfigClick(Player player, ConfigGui gui, InventoryClickEvent event) {
        if (!player.isOp()) {
            player.sendMessage("§c권한이 없습니다.");
            player.closeInventory();
            return;
        }
        ConfigGui.Entry entry = gui.getEntryAt(event.getRawSlot());
        if (entry == null) {
            return;
        }
        boolean left = event.isLeftClick();
        boolean right = event.isRightClick();
        boolean shift = event.isShiftClick();
        if (!left && !right) {
            return;
        }
        switch (entry.getType()) {
            case ATTACK_COOLDOWN -> {
                boolean enabled = plugin.getConfig().getBoolean("combat.attack-cooldown", true);
                boolean next = !enabled;
                plugin.getConfig().set("combat.attack-cooldown", next);
                plugin.saveConfig();
                attackCooldownEnabled = next;
                applyAttackCooldownSettingToOnlinePlayers();
                gui.refresh(entry);
            }
            case LOBBY_BLOCK_BREAK -> {
                if (state != GameState.IDLE) {
                    player.sendMessage("§c게임 진행 중에는 변경할 수 없습니다.");
                    return;
                }
                boolean allow = plugin.getConfig().getBoolean("lobby.allow-block-break", true);
                boolean next = !allow;
                plugin.getConfig().set("lobby.allow-block-break", next);
                plugin.saveConfig();
                idleBlockBreakAllowed = next;
                gui.refresh(entry);
            }
            case LOBBY_BLOCK_PLACE -> {
                if (state != GameState.IDLE) {
                    player.sendMessage("§c게임 진행 중에는 변경할 수 없습니다.");
                    return;
                }
                boolean allow = plugin.getConfig().getBoolean("lobby.allow-block-place", true);
                boolean next = !allow;
                plugin.getConfig().set("lobby.allow-block-place", next);
                plugin.saveConfig();
                idleBlockPlaceAllowed = next;
                gui.refresh(entry);
            }
            case LOBBY_INVINCIBILITY -> {
                if (state != GameState.IDLE) {
                    player.sendMessage("§c게임 진행 중에는 변경할 수 없습니다.");
                    return;
                }
                boolean enabled = plugin.getConfig().getBoolean("lobby.invincible", false);
                boolean next = !enabled;
                plugin.getConfig().set("lobby.invincible", next);
                plugin.saveConfig();
                idleInvincible = next;
                gui.refresh(entry);
            }
            case LOBBY_LOCATION -> {
                if (state != GameState.IDLE) {
                    player.sendMessage("§c게임 진행 중에는 변경할 수 없습니다.");
                    return;
                }
                saveLobbyLocation(player.getLocation(), player);
                gatherPlayersToLobby();
                gui.refresh(entry);
            }
            case MAP_MANAGE -> {
                player.closeInventory();
                player.openInventory(new com.abilitycombat.gui.MapConfigGui(plugin).getInventory());
            }
            case RELOAD -> {
                plugin.reloadConfig();
                abilityRegistry.load();
                startLocation = loadStartLocation();
                lobbyLocation = loadLobbyLocation();
                idleBlockBreakAllowed = plugin.getConfig().getBoolean("lobby.allow-block-break", true);
                idleBlockPlaceAllowed = plugin.getConfig().getBoolean("lobby.allow-block-place", true);
                idleInvincible = plugin.getConfig().getBoolean("lobby.invincible", false);
                attackCooldownEnabled = plugin.getConfig().getBoolean("combat.attack-cooldown", true);
                fixedDaytimeEnabled = plugin.getConfig().getBoolean("game.fixed-daytime", true);
                applyAttackCooldownSettingToOnlinePlayers();
                if (fixedDaytimeEnabled) {
                    startFixedDaytime();
                } else {
                    restoreFixedDaytime();
                }
                gui.refreshAll();
            }
            case FIXED_DAYTIME -> {
                boolean enabled = plugin.getConfig().getBoolean("game.fixed-daytime", true);
                boolean next = !enabled;
                plugin.getConfig().set("game.fixed-daytime", next);
                plugin.saveConfig();
                fixedDaytimeEnabled = next;
                if (fixedDaytimeEnabled) {
                    startFixedDaytime();
                } else {
                    restoreFixedDaytime();
                }
                gui.refresh(entry);
            }
	            case SPECTATOR_HIDE -> {
	                boolean hide = plugin.getConfig().getBoolean("spectator.hide-from-alive", true);
	                plugin.getConfig().set("spectator.hide-from-alive", !hide);
	                plugin.saveConfig();
	                gui.refresh(entry);
	            }
            case DASH_SELF_PREVIEW -> {
                boolean enabled = plugin.getConfig().getBoolean("hud.sprint.show-own-dash-replica", false);
                plugin.getConfig().set("hud.sprint.show-own-dash-replica", !enabled);
                plugin.saveConfig();
                gui.refresh(entry);
            }
	            case CRAFTING -> {
	                boolean enabled = plugin.getConfig().getBoolean("crafting.enabled", true);
	                boolean next = !enabled;
	                plugin.getConfig().set("crafting.enabled", next);
	                plugin.saveConfig();
	                craftingEnabled = next;
	                gui.refresh(entry);
	            }
	            case MAP_RESTORE -> {
	                boolean enabled = plugin.getConfig().getBoolean("map-restore.enabled", true);
	                boolean next = !enabled;
	                plugin.getConfig().set("map-restore.enabled", next);
	                plugin.saveConfig();
                mapRestoreEnabled = next;
                if (!mapRestoreEnabled) {
                    cancelMapScan();
                    cancelMapRestore();
                    regionSnapshot.clear();
                }
                gui.refresh(entry);
            }
            case MAP_RESTORE_RUN -> requestMapRestore(player);
            case MOB_SPAWN_BLOCK -> {
                boolean blocked = plugin.getConfig().getBoolean("mob-spawn.block-natural", true);
                boolean next = !blocked;
                plugin.getConfig().set("mob-spawn.block-natural", next);
                plugin.saveConfig();
                blockNaturalMobSpawn = next;
                gui.refresh(entry);
            }
            case INFINITE_DURABILITY -> {
                boolean enabled = plugin.getConfig().getBoolean("durability.infinite", true);
                boolean next = !enabled;
                plugin.getConfig().set("durability.infinite", next);
                plugin.saveConfig();
                infiniteDurability = next;
                gui.refresh(entry);
            }
            case INVINCIBILITY -> {
                int delta = shift ? 60 : 10;
                updateConfigInt("game.invincibility-seconds", left ? delta : -delta, 0);
                gui.refresh(entry);
            }
            case GAME_DURATION -> {
                int delta = shift ? 300 : 60;
                updateConfigInt("game.duration-seconds", left ? delta : -delta, 0);
                gui.refresh(entry);
            }
            case SELECTION_TIME -> {
                int delta = shift ? 10 : 5;
                updateConfigInt("ability.selection-seconds", left ? delta : -delta, 0);
                gui.refresh(entry);
            }
	            case BORDER_INITIAL_RADIUS -> {
	                int delta = shift ? 50 : 10;
	                updateConfigInt("world-border.initial-radius", left ? delta : -delta, 0);
	                syncPhase0RadiusWithInitialRadius();
	                gui.refreshAll();
	            }
            case BORDER_SHRINK_SECONDS -> {
                int delta = shift ? 5 : 1;
                updateConfigInt("world-border.shrink-seconds", left ? delta : -delta, 1);
                gui.refresh(entry);
            }
	            case PHASE -> {
	                int timeDelta = 0;
	                int radiusDelta = 0;
	                if (shift) {
	                    timeDelta = left ? 60 : -60;
	                } else {
	                    radiusDelta = left ? 10 : -10;
	                }
	                updatePhase(entry.getIndex(), timeDelta, radiusDelta);
	                if (entry.getIndex() == 0 && radiusDelta != 0) {
	                    gui.refreshAll();
	                } else {
	                    gui.refresh(entry);
	                }
	            }
            case REROLL_COUNT -> {
                int delta = left ? 1 : -1;
                updateConfigInt("ability.reroll-count", delta, 0);
                gui.refresh(entry);
            }
        }
    }

    private void handleMapConfigClick(Player player, com.abilitycombat.gui.MapConfigGui gui,
            InventoryClickEvent event) {
        if (!player.isOp()) {
            player.sendMessage("§c권한이 없습니다.");
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();

        // 뒤로 가기 버튼
        if (gui.isBackSlot(slot)) {
            player.closeInventory();
            openConfigGui(player);
            return;
        }

        // 현재 위치 추가 버튼
        if (gui.isAddMapSlot(slot)) {
            MapManager mapManager = plugin.getMapManager();
            if (mapManager != null) {
                String mapName = "맵 " + (mapManager.getMapCount() + 1);
                MapData newMap = mapManager.addMap(mapName, player.getLocation());
                player.sendMessage("§a맵 '" + newMap.getName() + "'이(가) 추가되었습니다.");

                // config에서 맵 복원 설정 직접 읽기
                mapRestoreEnabled = plugin.getConfig().getBoolean("map-restore.enabled", true);
                initialBorderRadius = plugin.getConfig().getInt("world-border.initial-radius", 200);

                // 해당 맵의 청크 스캔 시작
                regionSnapshot.setMapId(newMap.getSafeId());
                startMapScan(player.getLocation(), player);

                gui.refresh();
            }
            return;
        }

        // 맵 클릭
        MapData map = gui.getMapAt(slot);
        if (map == null) {
            return;
        }

        // Shift+클릭: 이름 변경 (채팅으로 입력 안내)
        if (event.isShiftClick()) {
            player.closeInventory();
            player.sendMessage("§e채팅으로 새 맵 이름을 입력하세요. (취소하려면 'cancel' 입력)");
            player.sendMessage("§7현재 맵: " + map.getName());
            // 이름 변경은 별도 채팅 이벤트로 처리 필요 - 현재는 간단히 안내만
            return;
        }

        // 우클릭: 삭제
        if (event.isRightClick()) {
            MapManager mapManager = plugin.getMapManager();
            if (mapManager != null) {
                mapManager.removeMap(map.getId());
                player.sendMessage("§c맵 '" + map.getName() + "'이(가) 삭제되었습니다.");
                gui.refresh();
            }
            return;
        }

        // 좌클릭: 해당 위치로 텔레포트
        if (event.isLeftClick()) {
            org.bukkit.Location loc = map.getLocation();
            if (loc != null) {
                player.teleport(loc);
                player.sendMessage("§a'" + map.getName() + "' 위치로 이동했습니다.");
            } else {
                player.sendMessage("§c월드를 찾을 수 없습니다: " + map.getWorldName());
            }
        }
    }

    private void handleMapSelectClick(Player player, com.abilitycombat.gui.MapSelectGui gui,
            InventoryClickEvent event) {
        int slot = event.getRawSlot();

        // 맵이 없으면 닫기만
        if (!gui.hasMaps()) {
            player.closeInventory();
            player.sendMessage("§c등록된 맵이 없습니다. /aw config에서 맵을 추가해주세요.");
            return;
        }

        if (gui.isModeToggleSlot(slot)) {
            gui.toggleMode();
            selectedMatchMode = gui.getSelectedMode();
            return;
        }

        // 랜덤 맵 선택
        if (gui.isRandomSlot(slot)) {
            MapManager mapManager = plugin.getMapManager();
            if (mapManager != null) {
                MapData randomMap = mapManager.getRandomMap();
                if (randomMap != null) {
                    player.closeInventory();
                    startGameWithMap(randomMap);
                }
            }
            return;
        }

        // 특정 맵 선택
        MapData map = gui.getMapAt(slot);
        if (map != null) {
            player.closeInventory();
            startGameWithMap(map);
        }
    }

	    private void startGameWithMap(MapData map) {
        if (map == null) {
            return;
        }
        org.bukkit.Location loc = map.getLocation();
        if (loc == null || loc.getWorld() == null) {
            plugin.getServer().broadcast(Component.text("§c맵 '" + map.getName() + "'의 월드를 찾을 수 없습니다."));
            return;
        }
        startLocation = loc;

        // 선택된 맵 이름 전체 메시지 출력
        plugin.getServer().broadcast(Component.text("§e§l[맵] §f" + map.getName()));

        // 맵에 해당하는 스냅샷 로드
        regionSnapshot.setMapId(map.getSafeId());
        regionSnapshot.loadFromDisk();

        // 게임 시작 계속
        continueGameStart();
    }

    private void continueGameStart() {
        if (fixedDaytimeEnabled) {
            startFixedDaytime();
        } else {
            restoreFixedDaytime();
        }
        prepareParticipants();
        healAllOnlinePlayersWithRetries(20);
        state = GameState.SELECTING;
	        // Attribution for the original project this plugin is based on.
	        plugin.getServer().broadcast(Component.text("§8§m------------------------------"));
	        plugin.getServer().broadcast(Component.text("§e§lAbilityWar 기반"));
	        plugin.getServer().broadcast(Component.text("§7원작자: §fDaybreak 새벽 §8(Copyright © 2020)"));
	        plugin.getServer().broadcast(Component.text("§7출처: §fhttps://github.com/Daybreak365/AbilityWar"));
	        plugin.getServer().broadcast(Component.text("§8§m------------------------------"));
	        openSelectionGuis();
	        plugin.getServer().broadcast(Component.text("§e능력 선택 후 §f/aw info §e로 능력을 확인할 수 있습니다."));
	        startSelectionTimer();
	    }

    private void handleToolkitClick(Player player, ToolkitGui gui, InventoryClickEvent event) {
        if (!player.isOp()) {
            event.setCancelled(true);
            player.sendMessage("§c권한이 없습니다.");
            player.closeInventory();
            return;
        }
        int rawSlot = event.getRawSlot();
        if (gui.isControlSlot(rawSlot)) {
            event.setCancelled(true);
            if (!event.isLeftClick() && !event.isRightClick()) {
                return;
            }
            if (!gui.isLevelSlot(rawSlot)) {
                return;
            }
            int delta = event.isShiftClick() ? 5 : 1;
            if (event.isRightClick()) {
                delta *= -1;
            }
            gui.adjustLevel(delta);
            return;
        }
        if (event.isShiftClick() && event.getClickedInventory() == player.getInventory()) {
            event.setCancelled(true);
        }
    }

    private void updateConfigInt(String path, int delta, int min) {
        int value = plugin.getConfig().getInt(path, min);
        int updated = Math.max(min, value + delta);
        plugin.getConfig().set(path, updated);
        plugin.saveConfig();
    }

	    private void updatePhase(int index, int timeDelta, int radiusDelta) {
	        List<Map<?, ?>> rawList = plugin.getConfig().getMapList("world-border.phases");
	        List<Map<String, Object>> list = new ArrayList<>();
	        int radiusDefault = plugin.getConfig().getInt("world-border.initial-radius", 200);
        for (Map<?, ?> raw : rawList) {
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("time", toInt(raw.get("time"), 0));
            entry.put("radius", toInt(raw.get("radius"), radiusDefault));
            list.add(entry);
        }
        while (list.size() <= index) {
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("time", 0);
            entry.put("radius", radiusDefault);
            list.add(entry);
        }
        Map<String, Object> entry = list.get(index);
        int time = toInt(entry.get("time"), 0);
        int radius = toInt(entry.get("radius"), radiusDefault);
	        time = Math.max(0, time + timeDelta);
	        radius = Math.max(0, radius + radiusDelta);
	        entry.put("time", time);
	        entry.put("radius", radius);
	        plugin.getConfig().set("world-border.phases", list);
	        if (index == 0 && radiusDelta != 0) {
	            // Keep phase 1 radius and initial-radius consistent.
	            plugin.getConfig().set("world-border.initial-radius", radius);
	        }
	        plugin.saveConfig();
	    }

	    private void syncPhase0RadiusWithInitialRadius() {
	        int initialRadius = plugin.getConfig().getInt("world-border.initial-radius", 200);
	        List<Map<?, ?>> rawList = plugin.getConfig().getMapList("world-border.phases");
	        List<Map<String, Object>> list = new ArrayList<>();
	        for (Map<?, ?> raw : rawList) {
	            Map<String, Object> entry = new java.util.LinkedHashMap<>();
	            entry.put("time", toInt(raw.get("time"), 0));
	            entry.put("radius", toInt(raw.get("radius"), initialRadius));
	            list.add(entry);
	        }
	        if (list.isEmpty()) {
	            Map<String, Object> entry = new java.util.LinkedHashMap<>();
	            entry.put("time", 0);
	            entry.put("radius", initialRadius);
	            list.add(entry);
	        }
	        list.get(0).put("radius", Math.max(1, initialRadius));
	        plugin.getConfig().set("world-border.phases", list);
	        plugin.saveConfig();
	    }

	private int toInt(Object value, int defaultValue) {
	    if (value == null) {
	        return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (PlayerReplicaManager.isReplicaEntity(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        if (state == GameState.IDLE) {
            handleLobbyVoidDeath(event);
            return;
        }
        if (state != GameState.RUNNING) {
            return;
        }
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        // 킬로그 출력
        broadcastKillLog(player);

        boolean endingGame = !startedSolo && alivePlayers.contains(uuid) && alivePlayers.size() <= 2;
        if (isTeamMode()) {
            CombatTeam team = getPlayerTeam(player);
            endingGame = !startedSolo && team != null && countAlivePlayers(team) <= 1;
        }
        if (endingGame) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        setSpectator(player);
        Bukkit.getScheduler().runTask(plugin, () -> player.spigot().respawn());
    }

    private void handleLobbyVoidDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage == null || lastDamage.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }

        // 로비에서 떨어져 죽었을 때는 아이템 손실/대기시간 없이 즉시 로비로 복귀시킵니다.
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);

        Bukkit.getScheduler().runTask(plugin, () -> player.spigot().respawn());
    }

    private void broadcastKillLog(Player victim) {
        Player killer = victim.getKiller();
        Participant victimParticipant = participants.get(victim.getUniqueId());
        String victimAbility = "없음";
        if (victimParticipant != null && victimParticipant.getAbilityDefinition() != null) {
            victimAbility = victimParticipant.getAbilityDefinition().getName();
            // 영문명 괄호 제거 (예: "리바이 병장 (LeviAckerman)" -> "리바이 병장")
            int idx = victimAbility.indexOf(" (");
            if (idx > 0) {
                victimAbility = victimAbility.substring(0, idx);
            }
        }

        Component message;
        if (killer != null && !killer.equals(victim)) {
            // 킬러는 닉네임만, 피해자는 능력도 표시
            message = Component.text()
                    .append(Component.text("☠ ", NamedTextColor.RED))
                    .append(Component.text(killer.getName(), NamedTextColor.GREEN))
                    .append(Component.text(" → ", NamedTextColor.WHITE))
                    .append(Component.text(victim.getName(), NamedTextColor.RED))
                    .append(Component.text(" [", NamedTextColor.GRAY))
                    .append(Component.text(victimAbility, NamedTextColor.GOLD))
                    .append(Component.text("]", NamedTextColor.GRAY))
                    .build();
        } else {
            message = Component.text()
                    .append(Component.text("☠ ", NamedTextColor.RED))
                    .append(Component.text(victim.getName(), NamedTextColor.RED))
                    .append(Component.text(" [", NamedTextColor.GRAY))
                    .append(Component.text(victimAbility, NamedTextColor.GOLD))
                    .append(Component.text("]", NamedTextColor.GRAY))
                    .append(Component.text(" 사망", NamedTextColor.WHITE))
                    .build();
        }
        plugin.getServer().broadcast(message);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (state == GameState.RUNNING) {
            if (startLocation != null) {
                event.setRespawnLocation(startLocation);
            } else {
                event.setRespawnLocation(player.getWorld().getSpawnLocation());
            }
            setSpectator(player);
        } else if (state == GameState.IDLE) {
            Location lobby = lobbyLocation != null ? lobbyLocation : player.getWorld().getSpawnLocation();
            event.setRespawnLocation(lobby);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncCombatSettings(player);
            syncPlayerWorldBorder(player);
        });
    }

    private Location loadStartLocation() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("game.start-location");
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world", "").trim();
        if (worldName.isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Configured start location world not found: " + worldName);
            return null;
        }
        double x = section.getDouble("x", world.getSpawnLocation().getX());
        double y = section.getDouble("y", world.getSpawnLocation().getY());
        double z = section.getDouble("z", world.getSpawnLocation().getZ());
        float yaw = (float) section.getDouble("yaw", world.getSpawnLocation().getYaw());
        float pitch = (float) section.getDouble("pitch", world.getSpawnLocation().getPitch());
        return new Location(world, x, y, z, yaw, pitch);
    }

    private Location loadLobbyLocation() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("lobby.location");
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world", "").trim();
        if (worldName.isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Configured lobby location world not found: " + worldName);
            return null;
        }
        double x = section.getDouble("x", world.getSpawnLocation().getX());
        double y = section.getDouble("y", world.getSpawnLocation().getY());
        double z = section.getDouble("z", world.getSpawnLocation().getZ());
        float yaw = (float) section.getDouble("yaw", world.getSpawnLocation().getYaw());
        float pitch = (float) section.getDouble("pitch", world.getSpawnLocation().getPitch());
        return new Location(world, x, y, z, yaw, pitch);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        resetPlayerAttributes(player);
        applyHungerLock(player);
        if (state == GameState.IDLE) {
            teleportToLobby(player);
        } else {
            participants.putIfAbsent(player.getUniqueId(), new Participant(player));
            setSpectator(player);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncCombatSettings(player);
            syncPlayerWorldBorder(player);
        });
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> syncPlayerWorldBorder(player));
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> syncCombatSettings(player));
    }

    @EventHandler
    public void onPlayerInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> syncCombatSettings(player));
    }

    @EventHandler
    public void onPlayerInventoryMutation(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> syncCombatSettings(player));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isMovementLocked(player)) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setTo(
                    new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (state != GameState.IDLE && alivePlayers.contains(uuid)) {
            alivePlayers.remove(uuid);
            spectators.add(uuid);
            Participant participant = participants.get(uuid);
            if (participant != null) {
                participant.setTargetable(false);
                participant.removeAbility();
            }
            checkAutoEnd();
        } else {
            alivePlayers.remove(uuid);
            spectators.remove(uuid);
        }
        selectionSessions.remove(uuid);
        debugAbilityUsers.remove(uuid);
        movementLocks.remove(uuid);
        storedAi.remove(uuid);
        naturalRegenCounters.remove(uuid);
        manualNaturalRegen.remove(uuid);
        pendingKnockbacks.remove(uuid);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (PlayerReplicaManager.isTrainingDummy(player)) {
            return;
        }
        if (isSpectator(player)) {
            event.setCancelled(true);
            return;
        }
        // 로비(IDLE) 무적 중이라도, 세계 밖(VOID)으로 떨어지는 경우는 "사망 -> 로비 복귀" 흐름이 필요합니다.
        if (state == GameState.IDLE) {
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                // VOID에서 빠르게 사망 처리되도록 큰 피해를 줍니다.
                event.setCancelled(false);
                event.setDamage(Math.max(event.getDamage(), 1000.0));
                return;
            }
            if (idleInvincible) {
                event.setCancelled(true);
                return;
            }
        }
        if ((state == GameState.RUNNING || state == GameState.SELECTING) && invincible && isAlive(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        applyHungerLock(player);
    }

    @EventHandler
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) {
            return;
        }
        if (manualNaturalRegen.contains(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        if (!infiniteDurability) {
            return;
        }
        event.setCancelled(true);
        event.setDamage(0);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework && isVictoryFirework(firework)) {
            event.setCancelled(true);
            return;
        }
        boolean replicaTransfer = consumeReplicaDamageBypass(event);
        if (!attackCooldownEnabled && !replicaTransfer) {
            suppressSwordSweep(event);
        }
        if (event.getDamager() instanceof Player player && isSpectator(player)) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player player && isSpectator(player)) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player targetPlayer) {
            Player sourcePlayer = resolveCombatSourcePlayer(event.getDamager());
            if (sourcePlayer != null && areTeammates(sourcePlayer, targetPlayer)) {
                event.setCancelled(true);
                return;
            }
            if (sourcePlayer != null && !event.isCancelled()) {
                applyLegacyDamageImmunity(targetPlayer);
            }
            if (!event.isCancelled() && sourcePlayer != null
                    && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                Vector knockback = createOldPlayerKnockback(sourcePlayer, targetPlayer);
                if (knockback != null) {
                    pendingKnockbacks.put(targetPlayer.getUniqueId(),
                            new PendingKnockback(knockback, Bukkit.getCurrentTick() + 2));
                }
            }
        }
    }

    @EventHandler
    public void onEntityKnockback(EntityKnockbackEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() != EntityKnockbackEvent.Cause.ENTITY_ATTACK) {
            return;
        }
        PendingKnockback pending = pendingKnockbacks.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        player.setVelocity(pending.velocity);
    }

    private Player resolveCombatSourcePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof org.bukkit.entity.TNTPrimed tnt
                && tnt.getSource() != null) {
            Entity sourceEntity = tnt.getSource();
            return resolveCombatSourcePlayer(sourceEntity);
        }
        if (entity instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean consumeReplicaDamageBypass(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player targetPlayer)) {
            return false;
        }
        ReplicaDamageBypass bypass = replicaDamageBypasses.get(targetPlayer.getUniqueId());
        if (bypass == null) {
            return false;
        }
        int currentTick = Bukkit.getCurrentTick();
        if (bypass.expiresAtTick < currentTick) {
            replicaDamageBypasses.remove(targetPlayer.getUniqueId());
            return false;
        }
        Player sourcePlayer = resolveCombatSourcePlayer(event.getDamager());
        if (sourcePlayer == null || !bypass.sourcePlayerId.equals(sourcePlayer.getUniqueId())) {
            return false;
        }
        replicaDamageBypasses.remove(targetPlayer.getUniqueId());
        return true;
    }

    private boolean isAxeWeapon(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        return item.getType().name().endsWith("_AXE");
    }

    private boolean isSwordWeapon(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        String name = item.getType().name();
        return name.endsWith("_SWORD");
    }

    private void suppressSwordSweep(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!isSwordWeapon(mainHand)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
            return;
        }
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }

        UUID attackerId = player.getUniqueId();
        UUID targetId = event.getEntity().getUniqueId();
        int currentTick = Bukkit.getCurrentTick();

        Entity aimedTarget = player.getTargetEntity(SWORD_PRIMARY_TARGET_CHECK_RANGE);
        if (aimedTarget instanceof LivingEntity && !aimedTarget.getUniqueId().equals(targetId)) {
            event.setCancelled(true);
            return;
        }

        SwordSwingRecord record = lastSwordSwings.get(attackerId);
        if (record == null || record.tick != currentTick) {
            lastSwordSwings.put(attackerId, new SwordSwingRecord(currentTick, targetId));
            return;
        }

        if (!record.firstTarget.equals(targetId)) {
            event.setCancelled(true);
        }
    }

    private boolean isShieldItem(ItemStack item) {
        return item != null && item.getType() == Material.SHIELD;
    }

    private boolean isShieldCombination(ItemStack result, ItemStack... ingredients) {
        if (isShieldItem(result)) {
            return true;
        }
        if (ingredients == null) {
            return false;
        }
        for (ItemStack ingredient : ingredients) {
            if (isShieldItem(ingredient)) {
                return true;
            }
        }
        return false;
    }

    private boolean isVictoryFirework(Firework firework) {
        if (firework == null) {
            return false;
        }
        return firework.getPersistentDataContainer().has(victoryFireworkKey, PersistentDataType.BYTE);
    }

    private static final class SwordSwingRecord {
        private final int tick;
        private final UUID firstTarget;

        private SwordSwingRecord(int tick, UUID firstTarget) {
            this.tick = tick;
            this.firstTarget = firstTarget;
        }
    }

    private static final class PendingKnockback {
        private final Vector velocity;
        private final int expiresAtTick;

        private PendingKnockback(Vector velocity, int expiresAtTick) {
            this.velocity = velocity;
            this.expiresAtTick = expiresAtTick;
        }
    }

    private static final class ReplicaDamageBypass {
        private final UUID sourcePlayerId;
        private final int expiresAtTick;

        private ReplicaDamageBypass(UUID sourcePlayerId, int expiresAtTick) {
            this.sourcePlayerId = sourcePlayerId;
            this.expiresAtTick = expiresAtTick;
        }
    }

    private record FixedWorldState(
            boolean doDaylightCycle,
            long fullTime,
            boolean storm,
            boolean thundering,
            int clearWeatherDuration,
            int weatherDuration,
            int thunderDuration) {
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }
        Player player = event.getPlayer();

        // 동일 틱 중복 호출 방지 (바닥 클릭 시 등)
        int currentTick = Bukkit.getCurrentTick();
        if (lastInteractTick.getOrDefault(player.getUniqueId(), -1) == currentTick) {
            return;
        }
        lastInteractTick.put(player.getUniqueId(), currentTick);

        if (isSpectator(player)) {
            event.setCancelled(true);
            return;
        }
        if (!canUseAbility(player)) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.PHYSICAL) {
            return;
        }
        Participant participant = participants.get(player.getUniqueId());
        if (participant == null) {
            return;
        }
        AbilityBase ability = participant.getAbility();
        if (ability instanceof ActiveHandler activeHandler) {
            ActiveHandler.ClickType clickType = (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)
                    ? ActiveHandler.ClickType.LEFT_CLICK
                    : ActiveHandler.ClickType.RIGHT_CLICK;
            Material mainHand = player.getInventory().getItemInMainHand().getType();

            // 철괴 사용 시 방패 방어 해제하여 능력 발동 보장
            if (mainHand == Material.IRON_INGOT && clickType == ActiveHandler.ClickType.RIGHT_CLICK) {
                Material offHand = player.getInventory().getItemInOffHand().getType();
                if (offHand == Material.SHIELD) {
                    event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                }
            }

            cancelDashForAbilityUse(player);
            if (activeHandler.activeSkill(mainHand, clickType)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getRightClicked() instanceof org.bukkit.entity.ArmorStand stand
                && isAbilityArmorStand(stand)) {
            event.setCancelled(true);
            return;
        }
        if (isSpectator(player)) {
            event.setCancelled(true);
            return;
        }
        if (!canUseAbility(player)) {
            return;
        }
        Participant participant = participants.get(player.getUniqueId());
        if (participant == null) {
            return;
        }
        AbilityBase ability = participant.getAbility();
        if (ability instanceof TargetHandler targetHandler
                && event.getRightClicked() instanceof org.bukkit.entity.LivingEntity target) {
            cancelDashForAbilityUse(player);
            targetHandler.targetSkill(player.getInventory().getItemInMainHand().getType(), target);
        }
    }

    private void cancelDashForAbilityUse(Player player) {
        if (player == null || plugin.getSprintHudService() == null) {
            return;
        }
        plugin.getSprintHudService().cancelDashForAbilityUse(player);
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        org.bukkit.entity.ArmorStand stand = event.getRightClicked();
        if (isAbilityArmorStand(stand)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isSpectator(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (state == GameState.IDLE && !idleBlockBreakAllowed) {
            event.setCancelled(true);
            return;
        }
        if (event.isCancelled()) {
            return;
        }
    }

    private boolean isAbilityArmorStand(org.bukkit.entity.ArmorStand stand) {
        if (stand == null) {
            return false;
        }
        return stand.getPersistentDataContainer().has(abilityArmorStandKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isSpectator(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (state == GameState.IDLE && !idleBlockPlaceAllowed) {
            event.setCancelled(true);
            return;
        }
        if (event.isCancelled()) {
            return;
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.isCancelled()) {
            return;
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) {
            return;
        }
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.isCancelled()) {
            return;
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!blockNaturalMobSpawn) {
            return;
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        switch (reason) {
            case CUSTOM, SPAWNER, SPAWNER_EGG, DISPENSE_EGG, COMMAND -> {
                return;
            }
            default -> event.setCancelled(true);
        }
    }

    private static class SelectionSession {
        private final List<AbilityDefinition> options;
        private final Set<String> excludedAbilities = new HashSet<>();
        private AbilityDefinition selected;
        private boolean awaitingChaosPreview;
        private AbilityDefinition pendingChaosDefinition;
        private AbilityDefinition pendingChaosFirst;
        private AbilityDefinition pendingChaosSecond;
        private int rerollsRemaining;

        private SelectionSession(List<AbilityDefinition> options, int maxRerolls) {
            this.options = new ArrayList<>(options);
            this.rerollsRemaining = maxRerolls;
            for (AbilityDefinition ability : options) {
                excludedAbilities.add(ability.getName());
            }
        }

        private boolean canReroll() {
            return rerollsRemaining > 0 && selected == null;
        }

        private void useReroll() {
            if (rerollsRemaining > 0) {
                rerollsRemaining--;
            }
        }

        private int getRerollsRemaining() {
            return rerollsRemaining;
        }
    }
}
