package com.abilitycombat.ui;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.npc.PlayerReplica;
import com.abilitycombat.npc.ReplicaProfile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
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
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.time.Duration;
import java.util.function.Supplier;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public final class SprintHudService implements Listener {

    private static final String RESOURCE_PACK_FILE_NAME = "abilitycombat-sprint-hud.zip";
    private static final String DEFAULT_DROPBOX_APP_KEY = "5jcck7diasz0rqy";
    private static final String DEFAULT_DROPBOX_APP_SECRET = "1n9m04y2zx7bf26";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Key FONT_KEY = Key.key("abilitycombat", "sprint_hud");
    private static final char ARROW_GLYPH = '\uE000';
    private static final char EMPTY_ARROW_GLYPH = '\uE001';
    private static final int ARROW_RENDER_HEIGHT = 7;
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
    private static final int MIN_DASH_ARROWS = 7;
    private static final int SLOW_CHARGE_ARROWS = 3;
    private static final int SLOW_CHARGE_TICKS_PER_ARROW = 2;
    private static final int FAST_CHARGE_TICKS_PER_ARROW = 1;
    private static final int SLOW_CHARGE_TICKS = SLOW_CHARGE_ARROWS * SLOW_CHARGE_TICKS_PER_ARROW;
    private static final int MAX_SPRINT_TICKS = SLOW_CHARGE_TICKS
            + ((MAX_ARROWS - SLOW_CHARGE_ARROWS) * FAST_CHARGE_TICKS_PER_ARROW);
    private static final int MIN_DASH_TICKS = SLOW_CHARGE_TICKS
            + ((MIN_DASH_ARROWS - SLOW_CHARGE_ARROWS) * FAST_CHARGE_TICKS_PER_ARROW);
    private static final int LEDGE_GRACE_TICKS = 0;
    private static final int JUMP_GRACE_TICKS = 6;
    private static final double CHARGE_GROUND_SNAP_DISTANCE = 0.6D;
    private static final int MAX_DASH_HOLD_TICKS = 30;
    private static final int POST_LAND_HOLD_TICKS = 4;
    private static final long CHARGE_TASK_PERIOD = 1L;
    private static final long DASH_TASK_PERIOD = 1L;
    private static final long FAILSAFE_TASK_PERIOD = 20L;
    private static final double MIN_CHARGE_MOVE_DELTA_SQ = 0.00025D;
    private static final double MIN_CHARGE_VELOCITY_SQ = 0.0025D;
    private static final int PACK_FORMAT = 75;
    private static final String DEFAULT_PACK_PATH = "/abilitycombat-sprint-hud.zip";
    private static final int HUD_DEFAULT_BIT = 13;
    private static final int HUD_MAX_BIT = 23 - HUD_DEFAULT_BIT;
    private static final int HUD_ADD_HEIGHT = (1 << (HUD_DEFAULT_BIT - 1)) - 1;
    private static final int SHADER_ID = 1;
    private static final int DEFAULT_BOSSBAR_OFFSET = 10;
    private static final double HEALTH_BAR_ABOVE_Y = -59.0;
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
    private final Map<String, DropboxAuthSession> dropboxAuthSessions = new ConcurrentHashMap<>();

    private BukkitTask chargeTask;
    private BukkitTask dashTask;
    private BukkitTask failsafeTask;
    private HttpServer httpServer;
    private byte[] packBytes;
    private byte[] packHash;
    private String packHashHex;
    private UUID packId;
    private volatile String packUrl;
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
        horizontalOffset = plugin.getConfig().getInt("hud.sprint.horizontal-offset", -100);
        verticalOffset = plugin.getConfig().getInt("hud.sprint.vertical-offset", 0);

        try {
            packBytes = buildPack();
            packHash = MessageDigest.getInstance("SHA-1").digest(packBytes);
            packHashHex = toHex(packHash);
            packId = UUID.nameUUIDFromBytes(packHash);
            writePackSnapshot(packBytes);
            String externalUrl = normalizeExternalPackUrl(plugin.getConfig().getString("hud.sprint.external-url", ""));
            if (externalUrl != null && !externalUrl.isBlank()) {
                packUrl = externalUrl;
                plugin.getLogger().info("Sprint HUD resource pack URL: " + packUrl + " (external)");
            } else if (plugin.getConfig().getBoolean("hud.sprint.dropbox.enabled", false)) {
                publishDropboxPackAsync(true).exceptionally(throwable -> {
                    plugin.getLogger().warning("Failed to publish sprint HUD resource pack to Dropbox: "
                            + rootMessage(throwable));
                    Bukkit.getScheduler().runTask(plugin, () -> startSelfHostedFallback());
                    return null;
                });
            } else {
                startPackServer();
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to initialize sprint HUD resource pack: " + exception.getMessage());
            packBytes = null;
            packHash = null;
            packHashHex = null;
            packId = null;
            packUrl = null;
        }

        chargeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runChargeTick, 1L, CHARGE_TASK_PERIOD);
        dashTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runDashTick, 1L, DASH_TASK_PERIOD);
        failsafeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runFailsafeTick, FAILSAFE_TASK_PERIOD,
                FAILSAFE_TASK_PERIOD);
        for (Player player : Bukkit.getOnlinePlayers()) {
            showBar(player, 0, 0f);
            sendPack(player);
        }
    }

    public String beginDropboxAuthorization(CommandSender sender) {
        String appKey = readDropboxAppKey();
        String appSecret = readDropboxAppSecret();
        String redirectUri = readConfigString("hud.sprint.dropbox.redirect-uri");
        String state = generateDropboxState();
        dropboxAuthSessions.put(getDropboxAuthSessionKey(sender),
                new DropboxAuthSession(state, redirectUri, System.currentTimeMillis()));
        StringBuilder url = new StringBuilder("https://www.dropbox.com/oauth2/authorize")
                .append("?client_id=").append(urlEncode(appKey))
                .append("&response_type=code")
                .append("&token_access_type=offline")
                .append("&force_reapprove=true")
                .append("&state=").append(urlEncode(state));
        if (!redirectUri.isBlank()) {
            url.append("&redirect_uri=").append(urlEncode(redirectUri));
        }
        return url.toString();
    }

    public CompletableFuture<DropboxAuthorizationResult> completeDropboxAuthorizationAsync(CommandSender sender,
            String pastedValue) {
        String sessionKey = getDropboxAuthSessionKey(sender);
        DropboxAuthSession session = dropboxAuthSessions.get(sessionKey);
        if (session == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("먼저 /aw dropbox auth 를 실행해야 합니다."));
        }
        String appKey = readDropboxAppKey();
        String appSecret = readDropboxAppSecret();

        return CompletableFuture
                .supplyAsync(() -> exchangeDropboxAuthorizationCode(appKey, appSecret, session, pastedValue))
                .thenCompose(tokenResult -> runSync(() -> {
                    plugin.getConfig().set("hud.sprint.dropbox.enabled", true);
                    plugin.getConfig().set("hud.sprint.dropbox.refresh-token", tokenResult.refreshToken());
                    plugin.saveConfig();
                }).thenCompose(ignored -> publishDropboxPackAsync(true)
                        .thenApply(packUrl -> new DropboxAuthorizationResult(tokenResult.accountId(), tokenResult.refreshToken(), packUrl))))
                .whenComplete((result, throwable) -> dropboxAuthSessions.remove(sessionKey));
    }

    public CompletableFuture<String> publishDropboxPackAsync(boolean resendOnlinePlayers) {
        return CompletableFuture.supplyAsync(() -> {
            String packUrl = prepareDropboxPackUrl();
            if (packUrl == null || packUrl.isBlank()) {
                throw new IllegalStateException("Dropbox 공유 링크를 가져오지 못했습니다.");
            }
            return packUrl;
        }).thenCompose(url -> runSync(() -> {
            this.packUrl = url;
            plugin.getLogger().info("Sprint HUD resource pack URL: " + url + " (dropbox)");
            if (resendOnlinePlayers) {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    loadedPackPlayers.remove(onlinePlayer.getUniqueId());
                    sendPack(onlinePlayer);
                }
            }
            return url;
        }));
    }

    public void stop() {
        cancelTask(chargeTask);
        chargeTask = null;
        cancelTask(dashTask);
        dashTask = null;
        cancelTask(failsafeTask);
        failsafeTask = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            stopDash(player);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            BossBar bar = bars.remove(player.getUniqueId());
            if (bar != null) {
                player.hideBossBar(bar);
            }
        }
        sprintTicks.clear();
        jumpGraceTicks.clear();
        storedJumpCharge.clear();
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
            UUID uuid = player.getUniqueId();
            boolean loaded = isPackLoaded(player);
            if (dashStates.containsKey(uuid)) {
                showBar(player, 0, 0f);
                continue;
            }
            int ticks = updateSprintTicks(player);
            int arrows = getChargeArrows(ticks);
            showBar(player, loaded ? arrows : Math.max(0, arrows), getChargeProgress(ticks));
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
        showBar(event.getPlayer(), 0, 0f);
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
        if (packId == null || !packId.equals(event.getID())) {
            return;
        }
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> loadedPackPlayers.add(event.getPlayer().getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                plugin.getLogger().warning("Sprint HUD resource pack status for " + event.getPlayer().getName() + ": "
                        + event.getStatus());
                loadedPackPlayers.remove(event.getPlayer().getUniqueId());
                showBar(event.getPlayer(), 0, 0f);
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
        if (isChargeGrounded(player)) {
            ticks = sprintTicks.getOrDefault(uuid, 0);
            if (ticks < MIN_DASH_TICKS) {
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
        showBar(player, 0, 0f);
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
        if (source != null && plugin.getGameManager() != null) {
            plugin.getGameManager().allowReplicaDamageTransfer(source, player);
        }
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
        ChargeActivity activity = analyzeChargeActivity(player);
        if (activity.active) {
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
        if (!isChargeGrounded(player)) {
            if (charged > 0) {
                storedJumpCharge.put(uuid, charged);
                sprintTicks.remove(uuid);
            }
            int initialGrace = getAirGraceTicks(player);
            int grace = jumpGraceTicks.getOrDefault(uuid, initialGrace);
            grace--;
            if (grace > 0) {
                jumpGraceTicks.put(uuid, grace);
                return storedJumpCharge.getOrDefault(uuid, charged);
            }
        }
        sprintTicks.remove(uuid);
        jumpGraceTicks.remove(uuid);
        storedJumpCharge.remove(uuid);
        return 0;
    }

    private int getAirGraceTicks(Player player) {
        return player.getVelocity().getY() > 0.08 ? JUMP_GRACE_TICKS : LEDGE_GRACE_TICKS;
    }

    private boolean isChargeGrounded(Player player) {
        if (player == null) {
            return false;
        }
        if (isOnGround(player)) {
            return true;
        }
        if (player.getVelocity().getY() > 0.08D) {
            return false;
        }
        BoundingBox box = player.getBoundingBox();
        double checkY = box.getMinY() - CHARGE_GROUND_SNAP_DISTANCE;
        double inset = 0.05D;
        double centerX = (box.getMinX() + box.getMaxX()) * 0.5D;
        double centerZ = (box.getMinZ() + box.getMaxZ()) * 0.5D;
        double[] xs = { box.getMinX() + inset, centerX, box.getMaxX() - inset };
        double[] zs = { box.getMinZ() + inset, centerZ, box.getMaxZ() - inset };
        for (double x : xs) {
            for (double z : zs) {
                if (player.getWorld()
                        .getBlockAt((int) Math.floor(x), (int) Math.floor(checkY), (int) Math.floor(z))
                        .getType().isSolid()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isActivelySprinting(Player player) {
        return analyzeChargeActivity(player).active;
    }

    private ChargeActivity analyzeChargeActivity(Player player) {
        UUID uuid = player.getUniqueId();
        boolean onlineAlive = player.isOnline() && !player.isDead();
        boolean sprintingFlag = sprintingPlayers.contains(uuid);
        boolean grounded = isChargeGrounded(player);
        boolean onGround = isOnGround(player);
        boolean blocked = player.isSneaking()
                || player.isFlying()
                || player.isGliding()
                || player.isSwimming()
                || player.isInsideVehicle();
        org.bukkit.Location current = player.getLocation();
        org.bukkit.Location previous = lastLocations.put(uuid, current.clone());
        if (previous == null || previous.getWorld() != current.getWorld()) {
            return new ChargeActivity(false, grounded, onGround, sprintingFlag, blocked, true, false, 0.0, 0.0);
        }
        double dx = current.getX() - previous.getX();
        double dz = current.getZ() - previous.getZ();
        double horizontalDeltaSq = (dx * dx + dz * dz);
        Vector velocity = player.getVelocity();
        double horizontalVelocitySq = (velocity.getX() * velocity.getX()) + (velocity.getZ() * velocity.getZ());
        boolean moving = horizontalDeltaSq > MIN_CHARGE_MOVE_DELTA_SQ || horizontalVelocitySq > MIN_CHARGE_VELOCITY_SQ;
        boolean active = onlineAlive && sprintingFlag && grounded && !blocked && moving;
        return new ChargeActivity(active, grounded, onGround, sprintingFlag, blocked, false, true,
                horizontalDeltaSq, horizontalVelocitySq);
    }

    private void showBar(Player player, int arrows, float progress) {
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), ignored -> {
            BossBar created = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_10);
            player.showBossBar(created);
            return created;
        });
        Component title = buildTitle(player, arrows);
        bar.name(title);
        bar.progress(isPackLoaded(player) ? Math.min(1f, Math.max(0f, progress)) : Math.min(1f, Math.max(0f, progress)));
        bar.color(BossBar.Color.WHITE);
        bar.overlay(BossBar.Overlay.PROGRESS);
    }

    private int getChargeArrows(int ticks) {
        int clamped = Math.max(0, Math.min(MAX_SPRINT_TICKS, ticks));
        if (clamped <= 0) {
            return 0;
        }
        if (clamped <= SLOW_CHARGE_TICKS) {
            return clamped / SLOW_CHARGE_TICKS_PER_ARROW;
        }
        return Math.min(MAX_ARROWS, SLOW_CHARGE_ARROWS + ((clamped - SLOW_CHARGE_TICKS) / FAST_CHARGE_TICKS_PER_ARROW));
    }

    private float getChargeProgress(int ticks) {
        return Math.max(0f, Math.min(1f, ticks / (float) MAX_SPRINT_TICKS));
    }

    private void clearBar(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private Component buildTitle(Player player, int arrows) {
        boolean loaded = isPackLoaded(player);
        String offsetPrefix = loaded ? buildSpaceOffset(horizontalOffset) : "";
        String rendered = loaded ? buildPackedArrowLine(arrows) : ">".repeat(Math.max(0, arrows));
        Component text = Component.text(offsetPrefix + rendered, NamedTextColor.AQUA);
        if (loaded) {
            return text.font(FONT_KEY);
        }
        return text;
    }

    private boolean isPackLoaded(Player player) {
        return packUrl != null && loadedPackPlayers.contains(player.getUniqueId());
    }

    private String buildPackedArrowLine(int arrows) {
        int clamped = Math.max(0, Math.min(MAX_ARROWS, arrows));
        StringBuilder builder = new StringBuilder(MAX_ARROWS);
        for (int i = 0; i < MAX_ARROWS; i++) {
            builder.append(i < clamped ? ARROW_GLYPH : EMPTY_ARROW_GLYPH);
        }
        return builder.toString();
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
        if (chargedTicks < MIN_DASH_TICKS) {
            return;
        }
        double ratio = Math.max(0.0, Math.min(1.0, chargedTicks / (double) MAX_SPRINT_TICKS));
        if (ratio <= 0.0) {
            return;
        }
        Vector dashVelocity = buildDashVelocity(player, ratio);
        boolean preserveViewerHide = isHermitHidden(player);
        DashState state = new DashState(player.getUniqueId(), player.isInvisible(), player.isCollidable(),
                preserveViewerHide);
        dashStates.put(player.getUniqueId(), state);
        if (!preserveViewerHide) {
            hideRealPlayer(player);
        }
        player.setInvisible(true);
        player.setCollidable(false);
        player.addPotionEffect(
                new PotionEffect(PotionEffectType.INVISIBILITY, MAX_DASH_HOLD_TICKS + 10, 0, true, false));
        player.setVelocity(dashVelocity);
        player.setFallDistance(0f);
        if (!preserveViewerHide) {
            state.mannequin = spawnMannequin(player, dashVelocity);
        }
    }

    private void tickDash(Player player, DashState state) {
        if (!player.isOnline() || player.isDead()) {
            stopDash(player);
            return;
        }
        state.dashTicks++;
        player.setFallDistance(0f);
        syncMannequin(player, state);

        boolean onGround = isOnGround(player);
        if (!onGround) {
            state.leftGround = true;
        }
        if (state.leftGround && onGround && state.recoveryTicks < 0) {
            state.recoveryTicks = POST_LAND_HOLD_TICKS;
        }
        if (state.recoveryTicks >= 0) {
            state.recoveryTicks--;
            if (state.recoveryTicks <= 0) {
                stopDash(player);
                return;
            }
        }
        if (state.dashTicks >= MAX_DASH_HOLD_TICKS) {
            stopDash(player);
        }
    }

    private void stopDash(Player player) {
        DashState state = dashStates.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        restoreDashVisuals(player, state.storedInvisible, state.storedCollidable, state.preserveViewerHide);
        if (state.mannequin != null && !state.mannequin.isDead()) {
            state.mannequin.remove();
        }
    }

    private PlayerReplica spawnMannequin(Player player, Vector dashVelocity) {
        PlayerReplica mannequin = plugin.getReplicaManager().createReplica(getMannequinLocation(player),
                ReplicaProfile.fromPlayer(player));
        mannequin.setInvulnerable(false);
        mannequin.setImmovable(false);
        mannequin.setGravity(true);
        mannequin.setAI(false);
        mannequin.setCollidable(true);
        mannequin.setSwimming(true);
        mannequin.setNoDamageTicks(0);
        if (mannequin.getAttribute(Attribute.MAX_HEALTH) != null) {
            mannequin.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100.0);
        }
        mannequin.setHealth(100.0);
        mannequin.customName(player.displayName());
        mannequin.setCustomNameVisible(false);
        EntityEquipment equipment = mannequin.getEquipment();
        if (equipment != null) {
            equipment.setArmorContents(cloneItems(player.getInventory().getArmorContents()));
            equipment.setItemInMainHand(cloneItem(player.getInventory().getItemInMainHand()));
            equipment.setItemInOffHand(cloneItem(player.getInventory().getItemInOffHand()));
        }
        mannequin.setPose(Pose.SWIMMING, true);
        mannequin.setVelocity(dashVelocity.clone());
        syncMannequinScale(player, mannequin);
        mannequin.syncEquipment();
        mannequin.spawn();
        if (!plugin.getConfig().getBoolean("hud.sprint.show-own-dash-replica", false)) {
            mannequin.hideFrom(player);
        }
        return mannequin;
    }

    private void syncMannequin(Player player, DashState state) {
        PlayerReplica mannequin = state.mannequin;
        if (mannequin == null || mannequin.isDead()) {
            return;
        }
        mannequin.setPose(Pose.SWIMMING, true);
        mannequin.setSwimming(true);
        mannequin.setGliding(false);
        mannequin.setFallDistance(0f);
        mannequin.setNoDamageTicks(0);
        syncMannequinScale(player, mannequin);
        org.bukkit.Location location = getMannequinLocation(player);
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(player.getLocation().getPitch());
        mannequin.teleport(location);
        mannequin.setVelocity(player.getVelocity().clone());
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
        double scale = getEntityScale(player);
        forward.normalize().multiply(FORWARD_OFFSET * scale);
        location.add(forward);
        location.add(0, HEIGHT_OFFSET * scale, 0);
        return location;
    }

    private void syncMannequinScale(Player player, PlayerReplica mannequin) {
        AttributeInstance mannequinScale = mannequin.getAttribute(Attribute.SCALE);
        if (mannequinScale == null) {
            return;
        }
        double playerScale = getEntityScale(player);
        if (Math.abs(mannequinScale.getBaseValue() - playerScale) > 0.0001D) {
            mannequinScale.setBaseValue(playerScale);
        }
    }

    private double getEntityScale(Player player) {
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale == null) {
            return 1.0D;
        }
        double value = scale.getValue();
        return value > 0.0D ? value : 1.0D;
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

    private void restoreDashVisuals(Player player, boolean invisible, boolean collidable, boolean preserveViewerHide) {
        if (!preserveViewerHide) {
            showRealPlayer(player);
        }
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
        restoreDashVisuals(player, false, stored, false);
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

    public void cancelDashState(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!dashStates.containsKey(playerId) && !hiddenByDash.containsKey(playerId)) {
            return;
        }
        forceVisible(player);
    }

    public void cancelDashForAbilityUse(Player player) {
        cancelDashState(player);
    }

    private boolean isHermitHidden(Player player) {
        if (player == null || plugin.getGameManager() == null) {
            return false;
        }
        com.abilitycombat.game.Participant participant = plugin.getGameManager().getParticipant(player.getUniqueId());
        if (participant == null || !(participant.getAbility() instanceof com.abilitycombat.ability.list.Hermit hermit)) {
            return false;
        }
        return hermit.isHidden();
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
        for (DashState state : dashStates.values()) {
            if (state.mannequin != null && state.mannequin.matches(entity)) {
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
        if (packUrl == null || packHash == null || packId == null) {
            return;
        }
        player.setResourcePack(
                packId,
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
        byte[] glyphAtlasBytes = createGlyphAtlas(iconBytes);

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
                      "height": %d,
                      "chars": ["\\uE000\\uE001"]
                    }
                  ]
                }
                """.formatted(createBitmapAscent(verticalOffset), ARROW_RENDER_HEIGHT);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            addZipEntry(zip, "pack.mcmeta", mcmeta.getBytes(StandardCharsets.UTF_8));
            addZipEntry(zip, "assets/abilitycombat/font/sprint_hud.json", fontJson.getBytes(StandardCharsets.UTF_8));
            addZipEntry(zip, "assets/abilitycombat/textures/font/icons.png", glyphAtlasBytes);
            addZipEntry(zip, "assets/minecraft/textures/gui/sprites/boss_bar/white_background.png",
                    createTransparentPng(182, 5));
            addZipEntry(zip, "assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png",
                    createTransparentPng(182, 5));
            addRootShaderEntries(zip);
            addOverlayShaderEntries(zip, OVERLAY_1_21_2, 1);
            addOverlayShaderEntries(zip, OVERLAY_1_21_4, 2);
            addOverlayShaderEntries(zip, OVERLAY_1_21_6, 3);
        }
        return outputStream.toByteArray();
    }

    private int createBitmapAscent(int y) {
        return -((((1 << HUD_MAX_BIT) + SHADER_ID) << HUD_DEFAULT_BIT) + HUD_ADD_HEIGHT + y);
    }

    private byte[] createTransparentPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", outputStream);
        return outputStream.toByteArray();
    }

    private byte[] createGlyphAtlas(byte[] iconBytes) throws IOException {
        BufferedImage icon = ImageIO.read(new java.io.ByteArrayInputStream(iconBytes));
        if (icon == null) {
            throw new IOException("Failed to read sprint HUD icon texture");
        }
        BufferedImage atlas = new BufferedImage(icon.getWidth() * 2, icon.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        try {
            graphics.drawImage(icon, 0, 0, null);
            graphics.drawImage(createFixedWidthBlankGlyph(icon.getWidth(), icon.getHeight()), icon.getWidth(), 0, null);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(atlas, "PNG", outputStream);
        return outputStream.toByteArray();
    }

    private BufferedImage createFixedWidthBlankGlyph(int width, int height) {
        BufferedImage blank = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int invisibleMarker = (1 << 24) | 0xFFFFFF;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                blank.setRGB(x, y, invisibleMarker);
            }
        }
        return blank;
    }

    private void addOverlayShaderEntries(ZipOutputStream zip, String overlayName, int shaderVersion)
            throws IOException {
        String prefix = overlayName + "/assets/minecraft/shaders/";
        if (shaderVersion < 3) {
            addZipEntry(zip, prefix + "core/rendertype_text.json",
                    buildOverlayShaderJson().getBytes(StandardCharsets.UTF_8));
        }
        addZipEntry(zip, prefix + "core/rendertype_text.vsh",
                buildBetterHudVertexShader(shaderVersion).getBytes(StandardCharsets.UTF_8));
        addZipEntry(zip, prefix + "core/rendertype_text.fsh",
                buildBetterHudFragmentShader(shaderVersion).getBytes(StandardCharsets.UTF_8));
        // BetterHud compatibility: for <=1.21.4 these includes may not exist, so
        // provide an empty stub.
        // For 1.21.6+ the client ships real include files that must not be overridden.
        if (shaderVersion < 3) {
            addZipEntry(zip, prefix + "include/dynamictransforms.glsl",
                    "#version 150\n".getBytes(StandardCharsets.UTF_8));
            addZipEntry(zip, prefix + "include/globals.glsl", "#version 150\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    private void addRootShaderEntries(ZipOutputStream zip) throws IOException {
        String prefix = "assets/minecraft/shaders/";
        addZipEntry(zip, prefix + "core/rendertype_text.vsh",
                buildBetterHudVertexShader(0).getBytes(StandardCharsets.UTF_8));
        addZipEntry(zip, prefix + "core/rendertype_text.fsh",
                buildBetterHudFragmentShader(0).getBytes(StandardCharsets.UTF_8));
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
		                                pos.x += 0.5 * ui.x;
		                                pos.y += ui.y + %.1f;
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
                .formatted(shaderVersion, HUD_DEFAULT_BIT, HUD_MAX_BIT, HUD_ADD_HEIGHT, DEFAULT_BOSSBAR_OFFSET,
                        SHADER_ID, HEALTH_BAR_ABOVE_Y);
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
                """
                .formatted(shaderVersion);
    }

    private void addZipEntry(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private String prepareHostedPackUrl() {
        String dropboxUrl = prepareDropboxPackUrl();
        if (dropboxUrl != null && !dropboxUrl.isBlank()) {
            plugin.getLogger().info("Sprint HUD resource pack URL: " + dropboxUrl + " (dropbox)");
            return dropboxUrl;
        }
        String externalUrl = normalizeExternalPackUrl(plugin.getConfig().getString("hud.sprint.external-url", ""));
        if (externalUrl != null && !externalUrl.isBlank()) {
            plugin.getLogger().info("Sprint HUD resource pack URL: " + externalUrl + " (external)");
            return externalUrl;
        }
        return null;
    }

    private void startSelfHostedFallback() {
        if (packUrl != null && !packUrl.isBlank()) {
            return;
        }
        if (httpServer != null) {
            return;
        }
        try {
            startPackServer();
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                loadedPackPlayers.remove(onlinePlayer.getUniqueId());
                sendPack(onlinePlayer);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to start sprint HUD self-host fallback: " + exception.getMessage());
        }
    }

    private String prepareDropboxPackUrl() {
        if (!plugin.getConfig().getBoolean("hud.sprint.dropbox.enabled", false)) {
            return null;
        }
        String appKey = readDropboxAppKey();
        String appSecret = readDropboxAppSecret();
        String refreshToken = readConfigString("hud.sprint.dropbox.refresh-token");
        String filePath = normalizeDropboxPath(plugin.getConfig().getString("hud.sprint.dropbox.file-path",
                "/" + RESOURCE_PACK_FILE_NAME));
        if (refreshToken.isBlank()) {
            plugin.getLogger().warning("Sprint HUD Dropbox mode is enabled, but refresh-token is missing.");
            return null;
        }
        try {
            String accessToken = requestDropboxAccessToken(appKey, appSecret, refreshToken);
            uploadPackToDropbox(accessToken, filePath);
            String shareLink = getDropboxSharedLink(accessToken, filePath);
            if (shareLink == null || shareLink.isBlank()) {
                throw new IOException("Dropbox shared link was not returned");
            }
            return normalizeExternalPackUrl(shareLink);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to publish sprint HUD resource pack to Dropbox: "
                    + exception.getMessage());
            return null;
        }
    }

    private DropboxTokenExchangeResult exchangeDropboxAuthorizationCode(String appKey, String appSecret,
            DropboxAuthSession session, String pastedValue) {
        if (System.currentTimeMillis() - session.createdAtMillis() > Duration.ofMinutes(10).toMillis()) {
            throw new IllegalStateException("Dropbox 인증 세션이 만료되었습니다. /aw dropbox auth 를 다시 실행하세요.");
        }
        DropboxAuthorizationCodeResponse response = parseDropboxAuthorizationResponse(pastedValue);
        if (response.error() != null && !response.error().isBlank()) {
            throw new IllegalStateException("Dropbox 인증이 거부되었습니다: " + response.error());
        }
        if (response.state() != null && !response.state().isBlank() && !session.state().equals(response.state())) {
            throw new IllegalStateException("Dropbox state 검증에 실패했습니다. 다시 인증을 시작하세요.");
        }
        if (response.code() == null || response.code().isBlank()) {
            throw new IllegalStateException("Dropbox authorization code 를 찾지 못했습니다.");
        }
        try {
            return requestDropboxRefreshTokenFromCode(appKey, appSecret, session.redirectUri(), response.code());
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private DropboxTokenExchangeResult requestDropboxRefreshTokenFromCode(String appKey, String appSecret,
            String redirectUri, String authorizationCode) throws IOException, InterruptedException {
        StringBuilder form = new StringBuilder("grant_type=authorization_code")
                .append("&code=").append(urlEncode(authorizationCode))
                .append("&client_id=").append(urlEncode(appKey))
                .append("&client_secret=").append(urlEncode(appSecret));
        if (redirectUri != null && !redirectUri.isBlank()) {
            form.append("&redirect_uri=").append(urlEncode(redirectUri));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.dropboxapi.com/oauth2/token"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureDropboxSuccess(response.statusCode(), response.body(), "oauth2/token authorization_code");
        String refreshToken = extractJsonString(response.body(), "refresh_token");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IOException("Dropbox refresh_token missing from authorization_code response");
        }
        String accountId = extractJsonString(response.body(), "account_id");
        return new DropboxTokenExchangeResult(accountId == null ? "" : accountId, refreshToken);
    }

    private DropboxAuthorizationCodeResponse parseDropboxAuthorizationResponse(String pastedValue) {
        String value = pastedValue == null ? "" : pastedValue.trim();
        if (value.isBlank()) {
            throw new IllegalStateException("붙여넣은 값이 비어 있습니다.");
        }
        if (!value.contains("code=") && !value.contains("error=") && !value.contains("state=")) {
            return new DropboxAuthorizationCodeResponse(stripWrappingQuotes(value), "", "");
        }
        String query = extractQueryString(stripWrappingQuotes(value));
        Map<String, String> params = parseQueryString(query);
        String error = params.getOrDefault("error", "");
        String errorDescription = params.getOrDefault("error_description", "");
        if (!errorDescription.isBlank()) {
            error = error.isBlank() ? errorDescription : error + " (" + errorDescription + ")";
        }
        return new DropboxAuthorizationCodeResponse(params.getOrDefault("code", ""),
                params.getOrDefault("state", ""),
                error);
    }

    private String extractQueryString(String input) {
        if (input.startsWith("http://") || input.startsWith("https://")) {
            URI uri = URI.create(input);
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                throw new IllegalStateException("redirect URL 에 query string 이 없습니다.");
            }
            return query;
        }
        int questionMarkIndex = input.indexOf('?');
        if (questionMarkIndex >= 0 && questionMarkIndex + 1 < input.length()) {
            return input.substring(questionMarkIndex + 1);
        }
        return input;
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> values = new HashMap<>();
        for (String part : query.split("&")) {
            if (part.isBlank()) {
                continue;
            }
            int separatorIndex = part.indexOf('=');
            String key = separatorIndex >= 0 ? part.substring(0, separatorIndex) : part;
            String value = separatorIndex >= 0 ? part.substring(separatorIndex + 1) : "";
            values.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    private String stripWrappingQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private String requestDropboxAccessToken(String appKey, String appSecret, String refreshToken)
            throws IOException, InterruptedException {
        String form = "grant_type=refresh_token"
                + "&refresh_token=" + urlEncode(refreshToken)
                + "&client_id=" + urlEncode(appKey)
                + "&client_secret=" + urlEncode(appSecret);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.dropboxapi.com/oauth2/token"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureDropboxSuccess(response.statusCode(), response.body(), "oauth2/token");
        String accessToken = extractJsonString(response.body(), "access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new IOException("Dropbox access_token missing from token response");
        }
        return accessToken;
    }

    private void uploadPackToDropbox(String accessToken, String filePath) throws IOException, InterruptedException {
        String argument = ("{\"path\":\"%s\",\"mode\":\"overwrite\",\"autorename\":false,\"mute\":true,"
                + "\"strict_conflict\":false}").formatted(escapeJson(filePath));
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://content.dropboxapi.com/2/files/upload"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + accessToken)
                .header("Dropbox-API-Arg", argument)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(packBytes))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureDropboxSuccess(response.statusCode(), response.body(), "files/upload");
    }

    private String getDropboxSharedLink(String accessToken, String filePath) throws IOException, InterruptedException {
        String listBody = """
                {"path":"%s","direct_only":true}
                """.formatted(escapeJson(filePath));
        String responseBody = postDropboxJson("https://api.dropboxapi.com/2/sharing/list_shared_links",
                accessToken, listBody, "sharing/list_shared_links");
        String existingLink = extractFirstJsonString(responseBody, "url");
        if (existingLink != null && !existingLink.isBlank()) {
            return existingLink;
        }

        String createBody = """
                {"path":"%s","settings":{"requested_visibility":"public"}}
                """.formatted(escapeJson(filePath));
        try {
            responseBody = postDropboxJson("https://api.dropboxapi.com/2/sharing/create_shared_link_with_settings",
                    accessToken, createBody, "sharing/create_shared_link_with_settings");
        } catch (IOException exception) {
            String fallbackBody = """
                    {"path":"%s"}
                    """.formatted(escapeJson(filePath));
            responseBody = postDropboxJson("https://api.dropboxapi.com/2/sharing/create_shared_link_with_settings",
                    accessToken, fallbackBody, "sharing/create_shared_link_with_settings");
        }
        return extractJsonString(responseBody, "url");
    }

    private String postDropboxJson(String url, String accessToken, String body, String operation)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureDropboxSuccess(response.statusCode(), response.body(), operation);
        return response.body();
    }

    private void ensureDropboxSuccess(int statusCode, String body, String operation) throws IOException {
        if (statusCode / 100 == 2) {
            return;
        }
        String trimmedBody = body == null ? "" : body.replace('\n', ' ').replace('\r', ' ').trim();
        if (trimmedBody.length() > 240) {
            trimmedBody = trimmedBody.substring(0, 240);
        }
        throw new IOException(operation + " failed with HTTP " + statusCode + ": " + trimmedBody);
    }

    private void startPackServer() throws IOException {
        String bindHost = plugin.getConfig().getString("hud.sprint.bind-host", "");
        bindHost = bindHost == null ? "" : bindHost.trim();
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

        httpServer = bindHost.isEmpty()
                ? HttpServer.create(new InetSocketAddress(port), 0)
                : HttpServer.create(new InetSocketAddress(bindHost, port), 0);
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.createContext(path, this::handlePackRequest);
        httpServer.start();

        String host = configuredHost.isEmpty() ? autoDetectHost() : configuredHost;
        if (host == null || host.isBlank()) {
            plugin.getLogger().warning("Sprint HUD resource pack host could not be detected automatically.");
            packUrl = null;
            return;
        }
        String versionQuery = packHashHex == null || packHashHex.isBlank() ? "" : "?v=" + packHashHex;
        packUrl = URI.create("http://" + host + ":" + port + path + versionQuery).toString();
        plugin.getLogger().info("Sprint HUD resource pack URL: " + packUrl);
    }

    private void handlePackRequest(HttpExchange exchange) throws IOException {
        if (packBytes == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Allow", "GET, HEAD");
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + RESOURCE_PACK_FILE_NAME + "\"");
        exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        exchange.getResponseHeaders().set("Pragma", "no-cache");
        exchange.getResponseHeaders().set("Expires", "0");
        exchange.getResponseHeaders().set("Content-Length", Integer.toString(packBytes.length));
        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
        exchange.getResponseHeaders().set("Connection", "close");
        if (packHashHex != null && !packHashHex.isBlank()) {
            exchange.getResponseHeaders().set("ETag", "\"" + packHashHex + "\"");
        }
        if ("HEAD".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(200, packBytes.length);
        exchange.getResponseBody().write(packBytes);
        exchange.getResponseBody().flush();
        exchange.close();
    }

    private void writePackSnapshot(byte[] bytes) throws IOException {
        Path out = plugin.getDataFolder().toPath().resolve("generated").resolve(RESOURCE_PACK_FILE_NAME);
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

    private String getDropboxAuthSessionKey(CommandSender sender) {
        if (sender instanceof Player player) {
            return "player:" + player.getUniqueId();
        }
        return "sender:" + sender.getName().toLowerCase();
    }

    private String generateDropboxState() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private String readConfigString(String path) {
        String value = plugin.getConfig().getString(path, "");
        return value == null ? "" : value.trim();
    }

    private String readDropboxAppKey() {
        String configured = readConfigString("hud.sprint.dropbox.app-key");
        return configured.isBlank() ? DEFAULT_DROPBOX_APP_KEY : configured;
    }

    private String readDropboxAppSecret() {
        String configured = readConfigString("hud.sprint.dropbox.app-secret");
        return configured.isBlank() ? DEFAULT_DROPBOX_APP_SECRET : configured;
    }

    private String normalizeDropboxPath(String rawPath) {
        String normalized = rawPath == null ? "" : rawPath.trim();
        if (normalized.isEmpty()) {
            normalized = "/" + RESOURCE_PACK_FILE_NAME;
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    private String normalizeExternalPackUrl(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        String normalized = rawUrl.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!normalized.contains("dropbox.com")) {
            return normalized;
        }
        if (normalized.contains("raw=1") || normalized.contains("dl=1")) {
            return normalized;
        }
        if (normalized.contains("dl=0")) {
            return normalized.replace("dl=0", "dl=1");
        }
        return normalized + (normalized.contains("?") ? "&dl=1" : "?dl=1");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String extractJsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return unescapeJsonString(matcher.group(1));
    }

    private String extractFirstJsonString(String json, String key) {
        return extractJsonString(json, key);
    }

    private String unescapeJsonString(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\' || i + 1 >= value.length()) {
                builder.append(current);
                continue;
            }
            char escaped = value.charAt(++i);
            switch (escaped) {
                case '"', '\\', '/' -> builder.append(escaped);
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> {
                    if (i + 4 >= value.length()) {
                        builder.append('u');
                        break;
                    }
                    String hex = value.substring(i + 1, i + 5);
                    builder.append((char) Integer.parseInt(hex, 16));
                    i += 4;
                }
                default -> builder.append(escaped);
            }
        }
        return builder.toString();
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >>> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private CompletableFuture<Void> runSync(Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    private <T> CompletableFuture<T> runSync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    public record DropboxAuthorizationResult(String accountId, String refreshToken, String packUrl) {
    }

    private record DropboxTokenExchangeResult(String accountId, String refreshToken) {
    }

    private record DropboxAuthorizationCodeResponse(String code, String state, String error) {
    }

    private record DropboxAuthSession(String state, String redirectUri, long createdAtMillis) {
    }

    private static final class DashState {
        private final UUID playerId;
        private final boolean storedInvisible;
        private final boolean storedCollidable;
        private final boolean preserveViewerHide;
        private int dashTicks;
        private boolean leftGround;
        private int recoveryTicks = -1;
        private PlayerReplica mannequin;

        private DashState(UUID playerId, boolean storedInvisible, boolean storedCollidable,
                boolean preserveViewerHide) {
            this.playerId = playerId;
            this.storedInvisible = storedInvisible;
            this.storedCollidable = storedCollidable;
            this.preserveViewerHide = preserveViewerHide;
        }
    }

    private static final class ChargeActivity {
        private final boolean active;
        private final boolean grounded;
        private final boolean onGround;
        private final boolean sprintingFlag;
        private final boolean blocked;
        private final boolean previousMissing;
        private final boolean sameWorld;
        private final double horizontalDeltaSq;
        private final double horizontalVelocitySq;

        private ChargeActivity(boolean active, boolean grounded, boolean onGround, boolean sprintingFlag,
                boolean blocked, boolean previousMissing, boolean sameWorld, double horizontalDeltaSq,
                double horizontalVelocitySq) {
            this.active = active;
            this.grounded = grounded;
            this.onGround = onGround;
            this.sprintingFlag = sprintingFlag;
            this.blocked = blocked;
            this.previousMissing = previousMissing;
            this.sameWorld = sameWorld;
            this.horizontalDeltaSq = horizontalDeltaSq;
            this.horizontalVelocitySq = horizontalVelocitySq;
        }
    }
}
