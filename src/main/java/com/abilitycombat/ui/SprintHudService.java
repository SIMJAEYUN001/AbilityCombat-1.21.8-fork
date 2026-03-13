package com.abilitycombat.ui;

import com.abilitycombat.AbilityCombat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.destroystokyo.paper.SkinParts;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public final class SprintHudService implements Listener {

    private static final UUID RESOURCE_PACK_ID = UUID.fromString("9d92f6d1-b0a5-49a7-867d-5f6341468a60");
    private static final Key FONT_KEY = Key.key("abilitycombat", "sprint_hud");
    private static final char ARROW_GLYPH = '\uE000';
    private static final char NEGATIVE_SPACE_128 = '\uE100';
    private static final char NEGATIVE_SPACE_64 = '\uE101';
    private static final char NEGATIVE_SPACE_32 = '\uE102';
    private static final char NEGATIVE_SPACE_16 = '\uE103';
    private static final char NEGATIVE_SPACE_8 = '\uE104';
    private static final char NEGATIVE_SPACE_4 = '\uE105';
    private static final char NEGATIVE_SPACE_2 = '\uE106';
    private static final char NEGATIVE_SPACE_1 = '\uE107';
    private static final char POSITIVE_SPACE_128 = '\uE108';
    private static final char POSITIVE_SPACE_64 = '\uE109';
    private static final char POSITIVE_SPACE_32 = '\uE10A';
    private static final char POSITIVE_SPACE_16 = '\uE10B';
    private static final char POSITIVE_SPACE_8 = '\uE10C';
    private static final char POSITIVE_SPACE_4 = '\uE10D';
    private static final char POSITIVE_SPACE_2 = '\uE10E';
    private static final char POSITIVE_SPACE_1 = '\uE10F';
    private static final int MAX_ARROWS = 10;
    private static final int TICKS_PER_ARROW = 2;
    private static final int MAX_SPRINT_TICKS = MAX_ARROWS * TICKS_PER_ARROW;
    private static final int JUMP_GRACE_TICKS = 6;
    private static final int MAX_DASH_HOLD_TICKS = 30;
    private static final int POST_LAND_HOLD_TICKS = 4;
    private static final long CHARGE_TASK_PERIOD = 2L;
    private static final long DASH_TASK_PERIOD = 1L;
    private static final long FAILSAFE_TASK_PERIOD = 20L;
    private static final int PACK_FORMAT = 75;
    private static final String DEFAULT_PACK_PATH = "/abilitycombat-sprint-hud.zip";
    private static final int HUD_DEFAULT_BIT = 13;
    private static final int HUD_MAX_BIT = 23 - HUD_DEFAULT_BIT;
    private static final int HUD_ADD_HEIGHT = (1 << (HUD_DEFAULT_BIT - 1)) - 1;
    private static final int SHADER_ID = 1;
    private static final int DEFAULT_BOSSBAR_OFFSET = 10;
    private static final String OVERLAY_1_21_2 = "betterhud_1_21_2";
    private static final String OVERLAY_1_21_4 = "betterhud_1_21_4";
    private static final String OVERLAY_1_21_6 = "betterhud_1_21_6";
    private static final double DASH_SPEED = 1.0;
    private static final double DASH_Y = 0.25;
    private static final double FULL_CHARGE_SPEED_BONUS = 0.15;
    private static final double FORWARD_OFFSET = 1.25;
    private static final double HEIGHT_OFFSET = 1.2;
    private static final int[] SPACE_WIDTHS = { 128, 64, 32, 16, 8, 4, 2, 1 };
    private static final char[] NEGATIVE_SPACE_GLYPHS = {
            NEGATIVE_SPACE_128, NEGATIVE_SPACE_64, NEGATIVE_SPACE_32, NEGATIVE_SPACE_16,
            NEGATIVE_SPACE_8, NEGATIVE_SPACE_4, NEGATIVE_SPACE_2, NEGATIVE_SPACE_1
    };
    private static final char[] POSITIVE_SPACE_GLYPHS = {
            POSITIVE_SPACE_128, POSITIVE_SPACE_64, POSITIVE_SPACE_32, POSITIVE_SPACE_16,
            POSITIVE_SPACE_8, POSITIVE_SPACE_4, POSITIVE_SPACE_2, POSITIVE_SPACE_1
    };

    private final AbilityCombat plugin;
    private final Map<UUID, Integer> sprintTicks = new HashMap<>();
    private final Map<UUID, Integer> jumpGraceTicks = new HashMap<>();
    private final Map<UUID, Integer> storedJumpCharge = new HashMap<>();
    private final Map<UUID, org.bukkit.Location> lastLocations = new HashMap<>();
    private final Set<UUID> sprintingPlayers = new HashSet<>();
    private final Map<UUID, Boolean> hiddenByDash = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Set<UUID> loadedPackPlayers = new HashSet<>();
    private final Map<UUID, DashState> dashStates = new HashMap<>();

    private BukkitTask chargeTask;
    private BukkitTask dashTask;
    private BukkitTask failsafeTask;
    private HttpServer httpServer;
    private byte[] packBytes;
    private byte[] packHash;
    private String packUrl;
    private boolean enabled;
    private boolean requireResourcePack;
    private int horizontalOffset;
    private int verticalOffset;

    public SprintHudService(AbilityCombat plugin) {
        this.plugin = plugin;
    }

    public void start() {
        enabled = plugin.getConfig().getBoolean("hud.sprint.enabled", true);
        if (!enabled) {
            return;
        }
        requireResourcePack = plugin.getConfig().getBoolean("hud.sprint.require-resource-pack", false);
        horizontalOffset = plugin.getConfig().getInt("hud.sprint.horizontal-offset", -120);
        verticalOffset = plugin.getConfig().getInt("hud.sprint.vertical-offset", 2);

        try {
            packBytes = buildPack();
            packHash = MessageDigest.getInstance("SHA-1").digest(packBytes);
            writePackSnapshot(packBytes);
            startPackServer();
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to initialize sprint HUD resource pack: " + exception.getMessage());
            packBytes = null;
            packHash = null;
            packUrl = null;
        }

        chargeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runChargeTick, 1L, CHARGE_TASK_PERIOD);
        dashTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runDashTick, 1L, DASH_TASK_PERIOD);
        failsafeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runFailsafeTick, FAILSAFE_TASK_PERIOD,
                FAILSAFE_TASK_PERIOD);
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendPack(player);
        }
    }

    public void stop() {
        cancelTask(chargeTask);
        chargeTask = null;
        cancelTask(dashTask);
        dashTask = null;
        cancelTask(failsafeTask);
        failsafeTask = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            BossBar bar = bars.remove(player.getUniqueId());
            if (bar != null) {
                player.hideBossBar(bar);
            }
        }
        sprintTicks.clear();
        loadedPackPlayers.clear();
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    private void runChargeTick() {
        if (!enabled) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (dashStates.containsKey(player.getUniqueId())) {
                clearBar(player);
                continue;
            }
            int ticks = updateSprintTicks(player);
            if (ticks <= 0) {
                clearBar(player);
                continue;
            }
            int arrows = Math.min(MAX_ARROWS, ticks / TICKS_PER_ARROW);
            if (arrows <= 0) {
                clearBar(player);
                continue;
            }
            showBar(player, arrows);
        }
    }

    private void runDashTick() {
        if (!enabled || dashStates.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            DashState state = dashStates.get(player.getUniqueId());
            if (state != null) {
                tickDash(player, state);
            }
        }
    }

    private void runFailsafeTick() {
        if (!enabled || hiddenByDash.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!dashStates.containsKey(player.getUniqueId())) {
                restoreDashVisualsIfNeeded(player);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendPack(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sprintTicks.remove(uuid);
        jumpGraceTicks.remove(uuid);
        storedJumpCharge.remove(uuid);
        lastLocations.remove(uuid);
        sprintingPlayers.remove(uuid);
        hiddenByDash.remove(uuid);
        loadedPackPlayers.remove(uuid);
        stopDash(event.getPlayer());
        BossBar bar = bars.remove(uuid);
        if (bar != null) {
            event.getPlayer().hideBossBar(bar);
        }
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (!RESOURCE_PACK_ID.equals(event.getID())) {
            return;
        }
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> loadedPackPlayers.add(event.getPlayer().getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                loadedPackPlayers.remove(event.getPlayer().getUniqueId());
                clearBar(event.getPlayer());
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (event.isSprinting()) {
            sprintingPlayers.add(uuid);
            lastLocations.put(uuid, event.getPlayer().getLocation().clone());
        } else {
            sprintingPlayers.remove(uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (!enabled || player.isDead() || dashStates.containsKey(player.getUniqueId())) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Integer grace = jumpGraceTicks.get(uuid);
        int ticks;
        if (isOnGround(player)) {
            ticks = sprintTicks.getOrDefault(uuid, 0);
            if (ticks < MAX_SPRINT_TICKS) {
                return;
            }
        } else {
            if (grace == null || grace <= 0) {
                return;
            }
            ticks = storedJumpCharge.getOrDefault(uuid, 0);
        }
        if (ticks <= 0) {
            return;
        }
        sprintTicks.remove(uuid);
        jumpGraceTicks.remove(uuid);
        storedJumpCharge.remove(uuid);
        clearBar(player);
        startDash(player, ticks);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDashMannequinDamageByEntity(EntityDamageByEntityEvent event) {
        DashState state = getDashState(event.getEntity());
        if (state == null) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean vanillaMelee = cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;
        boolean vanillaArrow = cause == EntityDamageEvent.DamageCause.PROJECTILE
                && (event.getDamager() instanceof Arrow || event.getDamager() instanceof SpectralArrow);
        if (!vanillaMelee && !vanillaArrow) {
            event.setCancelled(true);
            return;
        }
        Player player = Bukkit.getPlayer(state.playerId);
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        player.setNoDamageTicks(0);
        Entity source = resolveDamageSource(event.getDamager());
        if (source != null) {
            player.damage(event.getFinalDamage(), source);
        } else {
            player.damage(event.getFinalDamage());
        }
    }

    @EventHandler
    public void onDashMannequinDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        if (getDashState(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    private int updateSprintTicks(Player player) {
        UUID uuid = player.getUniqueId();
        if (isActivelySprinting(player)) {
            jumpGraceTicks.remove(uuid);
            storedJumpCharge.remove(uuid);
            int updated = Math.min(MAX_SPRINT_TICKS, sprintTicks.getOrDefault(uuid, 0) + 1);
            sprintTicks.put(uuid, updated);
            return updated;
        }
        int charged = sprintTicks.getOrDefault(uuid, 0);
        int pending = storedJumpCharge.getOrDefault(uuid, 0);
        if (charged <= 0 && pending <= 0) {
            jumpGraceTicks.remove(uuid);
            storedJumpCharge.remove(uuid);
            return 0;
        }
        if (!isOnGround(player)) {
            if (charged > 0) {
                storedJumpCharge.put(uuid, charged);
                sprintTicks.remove(uuid);
            }
            int grace = jumpGraceTicks.getOrDefault(uuid, JUMP_GRACE_TICKS);
            grace--;
            if (grace > 0) {
                jumpGraceTicks.put(uuid, grace);
                return 0;
            }
        }
        sprintTicks.remove(uuid);
        jumpGraceTicks.remove(uuid);
        storedJumpCharge.remove(uuid);
        return 0;
    }

    private boolean isActivelySprinting(Player player) {
        if (!player.isOnline() || player.isDead() || !sprintingPlayers.contains(player.getUniqueId())) {
            return false;
        }
        if (!isOnGround(player)) {
            return false;
        }
        if (player.isFlying() || player.isGliding() || player.isSwimming() || player.isInsideVehicle()) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        org.bukkit.Location current = player.getLocation();
        org.bukkit.Location previous = lastLocations.put(uuid, current.clone());
        if (previous == null || previous.getWorld() != current.getWorld()) {
            return false;
        }
        double dx = current.getX() - previous.getX();
        double dz = current.getZ() - previous.getZ();
        return (dx * dx + dz * dz) > 0.00025;
    }

    private void showBar(Player player, int arrows) {
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), ignored -> {
            BossBar created = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_10);
            player.showBossBar(created);
            return created;
        });
        Component title = buildTitle(player, arrows);
        bar.name(title);
        bar.progress(Math.min(1f, arrows / (float) MAX_ARROWS));
        bar.color(BossBar.Color.WHITE);
        bar.overlay(BossBar.Overlay.PROGRESS);
    }

    private void clearBar(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private Component buildTitle(Player player, int arrows) {
        String offsetPrefix = buildSpaceOffset(horizontalOffset);
        String rendered = String
                .valueOf(loadedPackPlayers.contains(player.getUniqueId()) && packUrl != null ? ARROW_GLYPH : '>')
                .repeat(Math.max(0, arrows));
        Component text = Component.text(offsetPrefix + rendered, NamedTextColor.AQUA);
        if (loadedPackPlayers.contains(player.getUniqueId()) && packUrl != null) {
            return text.font(FONT_KEY);
        }
        return text;
    }

    private String buildSpaceOffset(int pixels) {
        if (pixels == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int remaining = Math.abs(pixels);
        char[] glyphs = pixels < 0 ? NEGATIVE_SPACE_GLYPHS : POSITIVE_SPACE_GLYPHS;
        for (int i = 0; i < SPACE_WIDTHS.length; i++) {
            int width = SPACE_WIDTHS[i];
            while (remaining >= width) {
                builder.append(glyphs[i]);
                remaining -= width;
            }
        }
        return builder.toString();
    }

    private void startDash(Player player, int chargedTicks) {
        double ratio = Math.max(0.0, Math.min(1.0, chargedTicks / (double) MAX_SPRINT_TICKS));
        if (ratio <= 0.0) {
            return;
        }
        Vector dashVelocity = buildDashVelocity(player, ratio);
        DashState state = new DashState(player.getUniqueId(), player.isInvisible(), player.isCollidable());
        dashStates.put(player.getUniqueId(), state);
        hideRealPlayer(player);
        player.setInvisible(true);
        player.setCollidable(false);
        player.addPotionEffect(
                new PotionEffect(PotionEffectType.INVISIBILITY, MAX_DASH_HOLD_TICKS + 10, 0, true, false));
        player.setVelocity(dashVelocity);
        player.setFallDistance(0f);
        state.mannequin = spawnMannequin(player, dashVelocity);
    }

    private void tickDash(Player player, DashState state) {
        if (!player.isOnline() || player.isDead()) {
            stopDash(player);
            return;
        }
        state.dashTicks++;
        player.setFallDistance(0f);
        syncMannequin(player, state);

        if (!isOnGround(player)) {
            state.leftGround = true;
        }
        if (state.leftGround && isOnGround(player)) {
            state.landedTicks++;
        } else {
            state.landedTicks = 0;
        }
        if (state.landedTicks >= POST_LAND_HOLD_TICKS || state.dashTicks >= MAX_DASH_HOLD_TICKS) {
            stopDash(player);
        }
    }

    private void stopDash(Player player) {
        DashState state = dashStates.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        restoreDashVisuals(player, state.storedInvisible, state.storedCollidable);
        if (state.mannequin != null && !state.mannequin.isDead()) {
            state.mannequin.remove();
        }
    }

    private Mannequin spawnMannequin(Player player, Vector dashVelocity) {
        Mannequin mannequin = player.getWorld().spawn(player.getLocation(), Mannequin.class, entity -> {
            entity.setInvulnerable(false);
            entity.setImmovable(false);
            entity.setGravity(true);
            entity.setAI(false);
            if (entity.getAttribute(Attribute.MAX_HEALTH) != null) {
                entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100.0);
            }
            entity.setHealth(100.0);
            entity.customName(player.displayName());
            entity.setCustomNameVisible(false);
            entity.setDescription(null);
            entity.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            entity.setSkinParts(SkinParts.allParts());
            EntityEquipment equipment = entity.getEquipment();
            if (equipment != null) {
                equipment.setArmorContents(cloneItems(player.getInventory().getArmorContents()));
                equipment.setItemInMainHand(cloneItem(player.getInventory().getItemInMainHand()));
                equipment.setItemInOffHand(cloneItem(player.getInventory().getItemInOffHand()));
            }
            entity.setPose(Pose.SWIMMING, true);
            entity.setVelocity(dashVelocity.clone());
        });
        player.hideEntity(plugin, mannequin);
        return mannequin;
    }

    private void syncMannequin(Player player, DashState state) {
        Mannequin mannequin = state.mannequin;
        if (mannequin == null || mannequin.isDead()) {
            return;
        }
        mannequin.setPose(Pose.SWIMMING, true);
        mannequin.setGliding(false);
        mannequin.setFallDistance(0f);
        if (!state.leftGround) {
            mannequin.teleport(getMannequinLocation(player));
            mannequin.setVelocity(player.getVelocity().clone());
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isOnGround(Player player) {
        return player.isOnGround();
    }

    private Vector buildDashVelocity(Player player, double ratio) {
        Vector direction = player.getLocation().getDirection();
        if (direction.lengthSquared() <= 0.0001) {
            direction = new Vector(0, 0, 1);
        }
        double speed = DASH_SPEED * ratio;
        if (ratio >= 0.999) {
            speed += FULL_CHARGE_SPEED_BONUS;
        }
        direction.normalize().multiply(speed);
        direction.setY(DASH_Y * ratio);
        return direction;
    }

    private org.bukkit.Location getMannequinLocation(Player player) {
        org.bukkit.Location location = player.getLocation().clone();
        Vector forward = location.getDirection();
        if (forward.lengthSquared() <= 0.0001) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize().multiply(FORWARD_OFFSET);
        location.add(forward);
        location.add(0, HEIGHT_OFFSET, 0);
        return location;
    }

    private void hideRealPlayer(Player source) {
        hiddenByDash.put(source.getUniqueId(), source.isCollidable());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.hidePlayer(plugin, source);
        }
    }

    private void showRealPlayer(Player source) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.showPlayer(plugin, source);
        }
    }

    private void restoreDashVisuals(Player player, boolean invisible, boolean collidable) {
        showRealPlayer(player);
        hiddenByDash.remove(player.getUniqueId());
        player.setInvisible(invisible);
        player.setCollidable(collidable);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setFallDistance(0f);
    }

    private void restoreDashVisualsIfNeeded(Player player) {
        Boolean stored = hiddenByDash.get(player.getUniqueId());
        if (stored == null) {
            return;
        }
        restoreDashVisuals(player, false, stored);
    }

    public void forceVisible(Player player) {
        if (player == null) {
            return;
        }
        stopDash(player);
        showRealPlayer(player);
        hiddenByDash.remove(player.getUniqueId());
        if (player.isInvisible()) {
            player.setInvisible(false);
        }
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        player.setCollidable(true);
        player.setFallDistance(0f);
    }

    private ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private ItemStack[] cloneItems(ItemStack[] items) {
        if (items == null) {
            return new ItemStack[0];
        }
        ItemStack[] clones = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            clones[i] = cloneItem(items[i]);
        }
        return clones;
    }

    private DashState getDashState(Entity entity) {
        if (!(entity instanceof Mannequin)) {
            return null;
        }
        for (DashState state : dashStates.values()) {
            if (state.mannequin != null && state.mannequin.equals(entity)) {
                return state;
            }
        }
        return null;
    }

    private Entity resolveDamageSource(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        if (damager instanceof Player || damager instanceof Mob) {
            return damager;
        }
        return null;
    }

    public void resendPack(Player player) {
        loadedPackPlayers.remove(player.getUniqueId());
        sendPack(player);
    }

    private void sendPack(Player player) {
        if (packUrl == null || packHash == null) {
            return;
        }
        player.setResourcePack(
                RESOURCE_PACK_ID,
                packUrl,
                packHash,
                Component.text("스프린트 HUD 리소스팩", NamedTextColor.AQUA),
                requireResourcePack);
    }

    private byte[] buildPack() throws Exception {
        byte[] iconBytes;
        try (InputStream inputStream = plugin.getResource("hud/icons.png")) {
            if (inputStream == null) {
                throw new IOException("Resource hud/icons.png not found");
            }
            iconBytes = inputStream.readAllBytes();
        }

        String mcmeta = """
                {
                  "pack": {
                    "pack_format": %d,
                    "description": "AbilityCombat Sprint HUD",
                    "supported_formats": [9, %d],
                    "min_format": 9,
                    "max_format": %d
                  },
                  "overlays": {
                    "entries": [
                      {
                        "formats": [35, 45],
                        "directory": "%s",
                        "min_format": 35,
                        "max_format": 45
                      },
                      {
                        "formats": [46, 55],
                        "directory": "%s",
                        "min_format": 46,
                        "max_format": 55
                      },
                      {
                        "formats": [56, 99],
                        "directory": "%s",
                        "min_format": 56,
                        "max_format": 99
                      }
                    ]
                  }
                }
                """.formatted(PACK_FORMAT, PACK_FORMAT, PACK_FORMAT, OVERLAY_1_21_2, OVERLAY_1_21_4, OVERLAY_1_21_6);
        String fontJson = """
                {
                  "providers": [
                    {
                      "type": "space",
                      "advances": {
                        "\\uE100": -128,
                        "\\uE101": -64,
                        "\\uE102": -32,
                        "\\uE103": -16,
                        "\\uE104": -8,
                        "\\uE105": -4,
                        "\\uE106": -2,
                        "\\uE107": -1,
                        "\\uE108": 128,
                        "\\uE109": 64,
                        "\\uE10A": 32,
                        "\\uE10B": 16,
                        "\\uE10C": 8,
                        "\\uE10D": 4,
                        "\\uE10E": 2,
                        "\\uE10F": 1
                      }
                    },
                    {
                      "type": "bitmap",
                      "file": "abilitycombat:font/icons.png",
                      "ascent": %d,
                      "height": 9,
                      "chars": ["\\uE000"]
                    }
                  ]
                }
                """.formatted(createBitmapAscent(verticalOffset));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            addZipEntry(zip, "pack.mcmeta", mcmeta.getBytes(StandardCharsets.UTF_8));
            addZipEntry(zip, "assets/abilitycombat/font/sprint_hud.json", fontJson.getBytes(StandardCharsets.UTF_8));
            addZipEntry(zip, "assets/abilitycombat/textures/font/icons.png", iconBytes);
            addZipEntry(zip, "assets/minecraft/textures/gui/sprites/boss_bar/white_background.png",
                    createTransparentPng(182, 5));
            addZipEntry(zip, "assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png",
                    createTransparentPng(182, 5));
            addOverlayShaderEntries(zip, OVERLAY_1_21_2, 1);
            addOverlayShaderEntries(zip, OVERLAY_1_21_4, 2);
            addOverlayShaderEntries(zip, OVERLAY_1_21_6, 3);
        }
        return outputStream.toByteArray();
    }

    private int createBitmapAscent(int y) {
        if (y == 0) {
            return 8;
        }
        return -((((1 << HUD_MAX_BIT) + SHADER_ID) << HUD_DEFAULT_BIT) + HUD_ADD_HEIGHT + y);
    }

    private byte[] createTransparentPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", outputStream);
        return outputStream.toByteArray();
    }

    private void addOverlayShaderEntries(ZipOutputStream zip, String overlayName, int shaderVersion) throws IOException {
        String prefix = overlayName + "/assets/minecraft/shaders/";
        addZipEntry(zip, prefix + "core/rendertype_text.json",
                buildOverlayShaderJson().getBytes(StandardCharsets.UTF_8));
        addZipEntry(zip, prefix + "core/rendertype_text.vsh",
                buildBetterHudVertexShader(shaderVersion).getBytes(StandardCharsets.UTF_8));
        addZipEntry(zip, prefix + "core/rendertype_text.fsh",
                buildBetterHudFragmentShader(shaderVersion).getBytes(StandardCharsets.UTF_8));
        addZipEntry(zip, prefix + "include/dynamictransforms.glsl", "#version 150\n".getBytes(StandardCharsets.UTF_8));
        addZipEntry(zip, prefix + "include/globals.glsl", "#version 150\n".getBytes(StandardCharsets.UTF_8));
    }

    private String buildOverlayShaderJson() {
        return """
                {
                    "vertex": "minecraft:core/rendertype_text",
                    "fragment": "minecraft:core/rendertype_text",
                    "samplers": [
                        { "name": "Sampler0" },
                        { "name": "Sampler2" }
                    ],
                    "uniforms": [
                        { "name": "ModelViewMat", "type": "matrix4x4", "count": 16, "values": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },
                        { "name": "ProjMat", "type": "matrix4x4", "count": 16, "values": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },
                        { "name": "ColorModulator", "type": "float", "count": 4, "values": [ 1.0, 1.0, 1.0, 1.0 ] },
                        { "name": "ChunkOffset", "type": "float", "count": 3, "values": [ 0.0, 0.0, 0.0 ] },
                        { "name": "FogStart", "type": "float", "count": 1, "values": [ 0.0 ] },
                        { "name": "FogEnd", "type": "float", "count": 1, "values": [ 1.0 ] },
                        { "name": "FogColor", "type": "float", "count": 4, "values": [ 0.0, 0.0, 0.0, 0.0 ] },
                        { "name": "FogShape", "type": "int", "count": 1, "values": [ 0 ] },
                        { "name": "ScreenSize", "type": "float", "count": 2, "values": [ 1.0, 1.0 ] },
                        { "name": "GameTime", "type": "float", "count": 1, "values": [ 0.0 ] }
                    ]
                }
                """;
    }

    private String buildBetterHudVertexShader(int shaderVersion) {
        return """
                #version 150

                #define SHADER_VERSION %d
                #define HEIGHT_BIT %d
                #define MAX_BIT %d
                #define ADD_OFFSET %d
                #define DEFAULT_OFFSET %d

                #moj_import <fog.glsl>

                #if SHADER_VERSION >= 3
                #moj_import <dynamictransforms.glsl>
                #moj_import <projection.glsl>
                #moj_import <globals.glsl>
                out float sphericalVertexDistance;
                out float cylindricalVertexDistance;
                #else
                uniform mat4 ProjMat;
                uniform mat4 ModelViewMat;
                uniform int FogShape;
                out float vertexDistance;
                uniform vec2 ScreenSize;
                uniform float GameTime;
                #endif

                in vec3 Position;
                in vec4 Color;
                in vec2 UV0;
                in ivec2 UV2;

                uniform sampler2D Sampler0;
                uniform sampler2D Sampler2;
                uniform vec3 ChunkOffset;

                out vec4 vertexColor;
                out vec2 texCoord0;
                out float applyColor;

                float fogDistance(vec3 pos, int shape) {
                    if (shape == 0) {
                        return length(pos);
                    } else {
                        float distXZ = length(pos.xz);
                        float distY = abs(pos.y);
                        return max(distXZ, distY);
                    }
                }

                void main() {
                    vec3 pos = Position;
                    vec2 ui = ceil(2 / vec2(ProjMat[0][0], -ProjMat[1][1]));
                    vec2 uiScreen = ui / ScreenSize;
                    vec3 color = Color.xyz;
                    applyColor = 0;
                    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
                    if (pos.y >= ui.y && ProjMat[3].x == -1) {
                        int bit = int(pos.y) >> HEIGHT_BIT;
                        if (((bit >> MAX_BIT) & 1) == 1) {
                            int id = bit - (1 << MAX_BIT);
                            pos.x -= 0.5 * ui.x;
                            pos.y -= (bit << HEIGHT_BIT) + ADD_OFFSET + DEFAULT_OFFSET;
                            if (id == %d) {
                                pos.z += 0.0;
                            }
                        }
                    }

                #if SHADER_VERSION >= 3
                    sphericalVertexDistance = fog_spherical_distance(pos);
                    cylindricalVertexDistance = fog_cylindrical_distance(pos);
                #else
                    vertexDistance = fogDistance(pos, FogShape);
                #endif

                    texCoord0 = UV0;
                    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
                }
                """
                .formatted(shaderVersion, HUD_DEFAULT_BIT, HUD_MAX_BIT, HUD_ADD_HEIGHT, DEFAULT_BOSSBAR_OFFSET, SHADER_ID);
    }

    private String buildBetterHudFragmentShader(int shaderVersion) {
        return """
                #version 150

                #define SHADER_VERSION %d

                #moj_import <fog.glsl>

                #if SHADER_VERSION >= 3
                #moj_import <dynamictransforms.glsl>
                in float sphericalVertexDistance;
                in float cylindricalVertexDistance;
                #else
                uniform vec4 ColorModulator;
                uniform float FogStart;
                uniform float FogEnd;
                uniform vec4 FogColor;
                in float vertexDistance;
                #endif

                uniform sampler2D Sampler0;

                in vec4 vertexColor;
                in vec2 texCoord0;

                out vec4 fragColor;

                void main() {
                    vec4 texColor = texture(Sampler0, texCoord0);
                    vec4 color = texColor * vertexColor * ColorModulator;
                    if (color.a < 0.1) {
                        discard;
                    }
                #if SHADER_VERSION >= 3
                    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
                #else
                    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
                #endif
                }
                """.formatted(shaderVersion);
    }

    private void addZipEntry(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private void startPackServer() throws IOException {
        String configuredHost = plugin.getConfig().getString("hud.sprint.public-host", "");
        configuredHost = configuredHost == null ? "" : configuredHost.trim();
        int port = plugin.getConfig().getInt("hud.sprint.http-port", 24891);
        String path = plugin.getConfig().getString("hud.sprint.http-path", DEFAULT_PACK_PATH);
        path = path == null ? DEFAULT_PACK_PATH : path.trim();
        if (path.isEmpty()) {
            path = DEFAULT_PACK_PATH;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext(path, this::handlePackRequest);
        httpServer.start();

        String host = configuredHost.isEmpty() ? autoDetectHost() : configuredHost;
        if (host == null || host.isBlank()) {
            plugin.getLogger().warning("Sprint HUD resource pack host could not be detected automatically.");
            packUrl = null;
            return;
        }
        packUrl = URI.create("http://" + host + ":" + port + path).toString();
        plugin.getLogger().info("Sprint HUD resource pack URL: " + packUrl);
    }

    private void handlePackRequest(HttpExchange exchange) throws IOException {
        if (packBytes == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().add("Content-Type", "application/zip");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, packBytes.length);
        exchange.getResponseBody().write(packBytes);
        exchange.close();
    }

    private void writePackSnapshot(byte[] bytes) throws IOException {
        Path out = plugin.getDataFolder().toPath().resolve("generated").resolve("abilitycombat-sprint-hud.zip");
        Files.createDirectories(out.getParent());
        Files.write(out, bytes);
    }

    private String autoDetectHost() {
        String serverIp = plugin.getServer().getIp();
        if (serverIp != null && !serverIp.isBlank()) {
            return serverIp;
        }
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address ipv4 && !ipv4.isLoopbackAddress()) {
                        return ipv4.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    private static final class DashState {
        private final UUID playerId;
        private final boolean storedInvisible;
        private final boolean storedCollidable;
        private int dashTicks;
        private boolean leftGround;
        private int landedTicks;
        private Mannequin mannequin;

        private DashState(UUID playerId, boolean storedInvisible, boolean storedCollidable) {
            this.playerId = playerId;
            this.storedInvisible = storedInvisible;
            this.storedCollidable = storedCollidable;
        }
    }
}
