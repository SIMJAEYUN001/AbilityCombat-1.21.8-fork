package com.abilitycombat;

import com.abilitycombat.ability.AbilityFactory;
import com.abilitycombat.ability.AbilityRegistry;
import com.abilitycombat.ability.list.AimedStrike;
import com.abilitycombat.ability.list.Ares;
import com.abilitycombat.ability.list.Assassin;
import com.abilitycombat.ability.list.BloodFiend;
import com.abilitycombat.ability.list.Bellum;
import com.abilitycombat.ability.list.Berserker;
import com.abilitycombat.ability.list.BloodLord;
import com.abilitycombat.ability.list.BulletBarrage;
import com.abilitycombat.ability.list.CenterOfUniverse;
import com.abilitycombat.ability.list.ChainLightning;
import com.abilitycombat.ability.list.Clown;
import com.abilitycombat.ability.list.Curse;
import com.abilitycombat.ability.list.DarkVision;
import com.abilitycombat.ability.list.DecayRay;
import com.abilitycombat.ability.list.DevilBoots;
import com.abilitycombat.ability.list.Demigod;
import com.abilitycombat.ability.list.Doppelganger;
import com.abilitycombat.ability.list.EarthAwakening;
import com.abilitycombat.ability.list.EarthquakeStrike;
import com.abilitycombat.ability.list.Emperor;
import com.abilitycombat.ability.list.ExpertOfFall;
import com.abilitycombat.ability.list.FireFightWithFire;
import com.abilitycombat.ability.list.Flora;
import com.abilitycombat.ability.list.Glacier;
import com.abilitycombat.ability.list.Gladiator;
import com.abilitycombat.ability.list.Giant;
import com.abilitycombat.ability.list.GiantSlayer;
import com.abilitycombat.ability.list.Ghost;
import com.abilitycombat.ability.list.Gardener;
import com.abilitycombat.ability.list.GravityField;
import com.abilitycombat.ability.list.GrapplingHook;
import com.abilitycombat.ability.list.Hacker;
import com.abilitycombat.ability.list.Hedgehog;
import com.abilitycombat.ability.list.Hermit;
import com.abilitycombat.ability.list.HigherBeing;
import com.abilitycombat.ability.list.Hunter;
import com.abilitycombat.ability.list.Imprison;
import com.abilitycombat.ability.list.Ira;
import com.abilitycombat.ability.list.JellyFish;
import com.abilitycombat.ability.list.Khazhad;
import com.abilitycombat.ability.list.Kidnap;
import com.abilitycombat.ability.list.Lazyness;
import com.abilitycombat.ability.list.LateBloom;
import com.abilitycombat.ability.list.Liberator;
import com.abilitycombat.ability.list.Loki;
import com.abilitycombat.ability.list.Lorem;
import com.abilitycombat.ability.list.MachineArm;
import com.abilitycombat.ability.list.Magnet;
import com.abilitycombat.ability.list.Magician;
import com.abilitycombat.ability.list.MakeshiftAnvil;
import com.abilitycombat.ability.list.Morpheus;
import com.abilitycombat.ability.list.Nex;
import com.abilitycombat.ability.list.ODMGear;
import com.abilitycombat.ability.list.OrbitalLaser;
import com.abilitycombat.ability.list.PenetrationArrow;
import com.abilitycombat.ability.list.Poltergeist;
import com.abilitycombat.ability.list.Pumpkin;
import com.abilitycombat.ability.list.RedBeard;
import com.abilitycombat.ability.list.Reverse;
import com.abilitycombat.ability.list.Ruber;
import com.abilitycombat.ability.list.Scarecrow;
import com.abilitycombat.ability.list.ShowmanShip;
import com.abilitycombat.ability.list.Singed;
import com.abilitycombat.ability.list.Sniper;
import com.abilitycombat.ability.list.Soul;
import com.abilitycombat.ability.list.SoulEncroach;
import com.abilitycombat.ability.list.Stalker;
import com.abilitycombat.ability.list.StrategicSymbiosis;
import com.abilitycombat.ability.list.SuperNova;
import com.abilitycombat.ability.list.SurvivalInstinct;
import com.abilitycombat.ability.list.Swap;
import com.abilitycombat.ability.list.SwordDance;
import com.abilitycombat.ability.list.SwordMaster;
import com.abilitycombat.ability.list.TapDancer;
import com.abilitycombat.ability.list.Terrorist;
import com.abilitycombat.ability.list.Themis;
import com.abilitycombat.ability.list.TimeRewind;
import com.abilitycombat.ability.list.Vampire;
import com.abilitycombat.ability.list.Virus;
import com.abilitycombat.ability.list.Void;
import com.abilitycombat.ability.list.Virtus;
import com.abilitycombat.ability.list.WraithForm;
import com.abilitycombat.ability.list.Xenon;
import com.abilitycombat.ability.list.Yeti;
import com.abilitycombat.ability.list.Zeus;
import com.abilitycombat.ability.list.Zombie;
import com.abilitycombat.command.AbilityCombatCommand;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.combat.SweepPacketSuppressor;
import com.abilitycombat.entity.CustomEntityManager;
import com.abilitycombat.effect.Bleed;
import com.abilitycombat.effect.CrowdControl;
import com.abilitycombat.effect.DamageModifier;
import com.abilitycombat.effect.Infection;
import com.abilitycombat.effect.Slow;
import com.abilitycombat.game.GameManager;
import com.abilitycombat.game.MapManager;
import com.abilitycombat.npc.PlayerReplicaManager;
import com.abilitycombat.ui.ActionbarChannel;
import com.abilitycombat.ui.BossBarManager;
import com.abilitycombat.ui.SprintHudService;
import com.abilitycombat.event.EventBridge;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class AbilityCombat extends JavaPlugin {

    private static AbilityCombat instance;
    private static final String KEY_ABILITY_ARMOR_STAND = "ability_armor_stand";
    private AbilityRegistry abilityRegistry;
    private GameManager gameManager;
    private MapManager mapManager;
    private ActionbarChannel actionbarChannel;
    private BossBarManager bossBarManager;
    private SprintHudService sprintHudService;
    private PlayerReplicaManager replicaManager;
    private EventBridge eventBridge;
    private SweepPacketSuppressor sweepPacketSuppressor;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ensureConfigDefaults();
        saveResource("abilities.yml", false);
        abilityRegistry = new AbilityRegistry(this);
        registerAbilities();
        abilityRegistry.load();
        mapManager = new MapManager(getDataFolder(), getLogger());
        mapManager.load();
        gameManager = new GameManager(this, abilityRegistry);
        getServer().getPluginManager().registerEvents(gameManager, this);
        cleanupGlobalEntities();
        actionbarChannel = new ActionbarChannel(this);
        actionbarChannel.start();
        bossBarManager = new BossBarManager(this);
        getServer().getPluginManager().registerEvents(bossBarManager, this);
        replicaManager = new PlayerReplicaManager(this);
        replicaManager.start();
        sprintHudService = new SprintHudService(this);
        getServer().getPluginManager().registerEvents(sprintHudService, this);
        sprintHudService.start();
        DamageModifier.start(this);
        Bleed.start(this);
        Infection.start(this);
        Slow.start(this);
        CrowdControl.start(this);
        CustomEntityManager.start(this);
        AbilityTickManager.start(this);
        sweepPacketSuppressor = new SweepPacketSuppressor(this);
        sweepPacketSuppressor.start();
        eventBridge = new EventBridge();
        getServer().getPluginManager().registerEvents(eventBridge, this);
        cleanupGlobalEntities();
        AbilityCombatCommand command = new AbilityCombatCommand(this, gameManager, abilityRegistry);
        if (getCommand("aw") != null) {
            getCommand("aw").setExecutor(command);
            getCommand("aw").setTabCompleter(command);
        }
        getLogger().info("AbilityCombat has been enabled!");
    }

    private void ensureConfigDefaults() {
        FileConfiguration config = getConfig();
        boolean updated = false;

        updated |= ensureConfigDefault(config, "game.fixed-daytime", true);
        updated |= ensureConfigDefault(config, "hud.sprint.external-url", "");
        updated |= ensureConfigDefault(config, "hud.sprint.bind-host", "");
        updated |= ensureConfigDefault(config, "hud.sprint.public-host", "");
        updated |= ensureConfigDefault(config, "hud.sprint.http-port", 24891);
        updated |= ensureConfigDefault(config, "hud.sprint.http-path", "/abilitycombat-sprint-hud.zip");
        updated |= ensureConfigDefault(config, "hud.sprint.require-resource-pack", false);
        updated |= ensureConfigDefault(config, "hud.sprint.show-own-dash-replica", false);
        updated |= ensureConfigDefault(config, "hud.sprint.horizontal-offset", -100);
        updated |= ensureConfigDefault(config, "hud.sprint.vertical-offset", 0);
        updated |= ensureConfigDefault(config, "hud.sprint.dropbox.enabled", false);
        updated |= ensureConfigDefault(config, "hud.sprint.dropbox.app-key", "");
        updated |= ensureConfigDefault(config, "hud.sprint.dropbox.app-secret", "");
        updated |= ensureConfigDefault(config, "hud.sprint.dropbox.redirect-uri", "");
        updated |= ensureConfigDefault(config, "hud.sprint.dropbox.refresh-token", "");
        updated |= ensureConfigDefault(config, "hud.sprint.dropbox.file-path", "/abilitycombat-sprint-hud.zip");

        if (updated) {
            saveConfig();
        }
    }

    private boolean ensureConfigDefault(FileConfiguration config, String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    @Override
    public void onDisable() {
        cleanupGlobalEntities();
        if (gameManager != null) {
            gameManager.stopGame();
            gameManager.shutdown();
        }
        if (actionbarChannel != null) {
            actionbarChannel.stop();
        }
        if (bossBarManager != null) {
            bossBarManager.shutdown();
        }
        if (replicaManager != null) {
            replicaManager.stop();
            replicaManager = null;
        }
        if (sprintHudService != null) {
            sprintHudService.stop();
            sprintHudService = null;
        }
        DamageModifier.stop();
        Bleed.stop();
        Infection.stop();
        Slow.stop();
        CrowdControl.stop();
        CustomEntityManager.stop();
        AbilityTickManager.stop();
        if (sweepPacketSuppressor != null) {
            sweepPacketSuppressor.stop();
            sweepPacketSuppressor = null;
        }
        if (eventBridge != null) {
            eventBridge.clear();
        }
        getLogger().info("AbilityCombat has been disabled!");
    }

    private void cleanupGlobalEntities() {
        NamespacedKey abilityStandKey = getAbilityArmorStandKey(this);
        NamespacedKey smKey = SwordMaster.getSwordKey(this);
        NamespacedKey emperorKey = Emperor.getGuardKey(this);
        NamespacedKey khazhadKey = Khazhad.getTridentKey(this);
        NamespacedKey voidKey = Void.getPortalKey(this);
        NamespacedKey replicaKey = new NamespacedKey(this, "replica_anchor");

        for (World world : getServer().getWorlds()) {
            // ArmorStand 정리 (검기, 삼지창, 포탈 등)
            for (org.bukkit.entity.ArmorStand stand : world.getEntitiesByClass(org.bukkit.entity.ArmorStand.class)) {
                if (stand.getPersistentDataContainer().has(abilityStandKey, PersistentDataType.BYTE)
                        || stand.getPersistentDataContainer().has(replicaKey, PersistentDataType.BYTE)
                        || stand.getPersistentDataContainer().has(smKey, PersistentDataType.BYTE)
                        || stand.getPersistentDataContainer().has(khazhadKey, PersistentDataType.BYTE)
                        || stand.getPersistentDataContainer().has(voidKey, PersistentDataType.BYTE)) {
                    stand.remove();
                }
            }
            // Trident 정리 (박힌 삼지창)
            for (org.bukkit.entity.Trident trident : world.getEntitiesByClass(org.bukkit.entity.Trident.class)) {
                if (trident.getPersistentDataContainer().has(khazhadKey, PersistentDataType.BYTE)) {
                    trident.remove();
                }
            }
            // Skeleton 정리 (근위병)
            for (org.bukkit.entity.Skeleton skeleton : world.getEntitiesByClass(org.bukkit.entity.Skeleton.class)) {
                if (skeleton.getPersistentDataContainer().has(emperorKey, PersistentDataType.BYTE)) {
                    skeleton.remove();
                }
            }
        }
    }

    public static AbilityCombat getPlugin() {
        return instance;
    }

    public static NamespacedKey getAbilityArmorStandKey(AbilityCombat plugin) {
        return new NamespacedKey(plugin, KEY_ABILITY_ARMOR_STAND);
    }

    public static void markAbilityArmorStand(ArmorStand stand) {
        if (stand == null) {
            return;
        }
        AbilityCombat plugin = getPlugin();
        if (plugin == null) {
            return;
        }
        stand.getPersistentDataContainer().set(getAbilityArmorStandKey(plugin), PersistentDataType.BYTE, (byte) 1);
    }

    public static void markPiercingAbilityArmorStand(ArmorStand stand) {
        if (stand == null) {
            return;
        }
        markAbilityArmorStand(stand);
        stand.setMarker(true);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
    }

    public AbilityRegistry getAbilityRegistry() {
        return abilityRegistry;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public ActionbarChannel getActionbarChannel() {
        return actionbarChannel;
    }

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public SprintHudService getSprintHudService() {
        return sprintHudService;
    }

    public PlayerReplicaManager getReplicaManager() {
        return replicaManager;
    }

    public EventBridge getEventBridge() {
        return eventBridge;
    }

    public MapManager getMapManager() {
        return mapManager;
    }

    private void registerAbilities() {
        AbilityFactory.clear();
        AbilityFactory.register(Zeus.class);
        AbilityFactory.register(Berserker.class);
        AbilityFactory.register(Bellum.class);
        AbilityFactory.register(CenterOfUniverse.class);
        AbilityFactory.register(Gladiator.class);
        AbilityFactory.register(Glacier.class);
        AbilityFactory.register(Sniper.class);
        AbilityFactory.register(SwordMaster.class);
        AbilityFactory.register(Ruber.class);
        AbilityFactory.register(Lorem.class);
        AbilityFactory.register(MachineArm.class);
        AbilityFactory.register(Magnet.class);
        AbilityFactory.register(MakeshiftAnvil.class);
        AbilityFactory.register(SoulEncroach.class);
        AbilityFactory.register(Soul.class);
        AbilityFactory.register(PenetrationArrow.class);
        AbilityFactory.register(TimeRewind.class);
        AbilityFactory.register(Morpheus.class);
        AbilityFactory.register(Yeti.class);
        AbilityFactory.register(Xenon.class);
        AbilityFactory.register(Demigod.class);
        AbilityFactory.register(Ares.class);
        AbilityFactory.register(Assassin.class);
        AbilityFactory.register(Curse.class);
        AbilityFactory.register(Ghost.class);
        AbilityFactory.register(Loki.class);
        AbilityFactory.register(Themis.class);
        AbilityFactory.register(Nex.class);
        AbilityFactory.register(EarthquakeStrike.class);
        AbilityFactory.register(EarthAwakening.class);
        AbilityFactory.register(Khazhad.class);
        AbilityFactory.register(Vampire.class);
        AbilityFactory.register(Zombie.class);
        AbilityFactory.register(JellyFish.class);
        AbilityFactory.register(Hacker.class);
        AbilityFactory.register(Magician.class);
        AbilityFactory.register(Stalker.class);
        AbilityFactory.register(Terrorist.class);
        AbilityFactory.register(Ira.class);
        AbilityFactory.register(Virtus.class);
        AbilityFactory.register(WraithForm.class);
        AbilityFactory.register(SwordDance.class);
        AbilityFactory.register(ChainLightning.class);
        AbilityFactory.register(OrbitalLaser.class);
        AbilityFactory.register(TapDancer.class);
        AbilityFactory.register(StrategicSymbiosis.class);
        AbilityFactory.register(Liberator.class);
        AbilityFactory.register(Hermit.class);
        AbilityFactory.register(Clown.class);
        AbilityFactory.register(GrapplingHook.class);
        AbilityFactory.register(RedBeard.class);
        AbilityFactory.register(Scarecrow.class);
        AbilityFactory.register(Virus.class);
        AbilityFactory.register(Void.class);
        AbilityFactory.register(Lazyness.class);
        AbilityFactory.register(Flora.class);
        AbilityFactory.register(DarkVision.class);
        AbilityFactory.register(SurvivalInstinct.class);
        AbilityFactory.register(Swap.class);
        AbilityFactory.register(Reverse.class);
        AbilityFactory.register(ShowmanShip.class);
        AbilityFactory.register(Singed.class);
        AbilityFactory.register(Kidnap.class);
        AbilityFactory.register(Imprison.class);
        AbilityFactory.register(SuperNova.class);
        AbilityFactory.register(HigherBeing.class);
        AbilityFactory.register(Emperor.class);
        AbilityFactory.register(FireFightWithFire.class);
        AbilityFactory.register(DevilBoots.class);
        AbilityFactory.register(Hedgehog.class);
        AbilityFactory.register(Pumpkin.class);
        AbilityFactory.register(ExpertOfFall.class);
        AbilityFactory.register(BloodFiend.class);
        AbilityFactory.register(BloodLord.class);
        AbilityFactory.register(Gardener.class);
        AbilityFactory.register(Giant.class);
        AbilityFactory.register(GiantSlayer.class);
        AbilityFactory.register(GravityField.class);
        AbilityFactory.register(DecayRay.class);
        AbilityFactory.register(BulletBarrage.class);
        AbilityFactory.register(LateBloom.class);
        AbilityFactory.register(AimedStrike.class);
        AbilityFactory.register(Poltergeist.class);
        AbilityFactory.register(ODMGear.class);
        AbilityFactory.register(Hunter.class);
        AbilityFactory.register(Doppelganger.class);
    }
}
