package com.abilitycombat.ability;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.effect.DamageModifier;
import com.abilitycombat.effect.Slow;
import com.abilitycombat.game.Participant;
import com.abilitycombat.ui.ActionbarChannel;
import com.abilitycombat.ui.BossBarManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Set;

/**
 * 모든 능력의 기반 클래스
 */
public abstract class AbilityBase implements Listener, AbilityTickManager.Tickable {

    private final Participant participant;
    private final AbilityManifest manifest;
    private boolean destroyed = false;
    private final Set<AbilityTimer> timers = new HashSet<>();
    private boolean tickRegistered = false;

    protected AbilityBase(Participant participant) {
        this.participant = participant;
        this.manifest = getClass().getAnnotation(AbilityManifest.class);
        if (this.manifest == null) {
            throw new IllegalStateException(
                    "AbilityManifest annotation is required for ability: " + getClass().getName());
        }
    }

    /**
     * 능력 소유자의 Participant 반환
     */
    public Participant getParticipant() {
        return participant;
    }

    /**
     * 능력 소유자의 Player 반환
     */
    public Player getPlayer() {
        return participant.getPlayer();
    }

    /**
     * 능력 메타데이터 반환
     */
    public AbilityManifest getManifest() {
        return manifest;
    }

    /**
     * 능력 이름 반환 (영문명 포함 전체 이름)
     */
    public String getName() {
        return manifest.name();
    }

    /**
     * 보스바 등에 표시될 깔끔한 능력 이름 반환 (한글 이름만)
     */
    public String getDisplayName() {
        String name = getName();
        if (name == null || name.isBlank()) {
            return "";
        }
        int index = name.indexOf(" (");
        if (index > 0) {
            return name.substring(0, index).trim();
        }
        return name;
    }

    protected ActionbarChannel getActionbarChannel() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin != null ? plugin.getActionbarChannel() : null;
    }

    protected BossBarManager getBossBarManager() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin != null ? plugin.getBossBarManager() : null;
    }

    protected void notifyCooldown(Cooldown cooldown) {
        if (cooldown == null || !cooldown.isCooldown()) {
            return;
        }
        int remaining = Math.max(0, cooldown.getCount());
        Player player = getPlayer();
        if (player != null) {
            player.sendMessage(Component.text("남은 쿨타임 ").color(NamedTextColor.GRAY)
                    .append(Component.text(remaining + "초").color(NamedTextColor.RED)));
        }
    }

    protected void applyMaterialCooldownIfEmpty(Material material, int seconds) {
        if (material == null || seconds <= 0) {
            return;
        }
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null) {
            return;
        }
        NamespacedKey ownerKey = new NamespacedKey(plugin, "cooldown_owner_" + material.name().toLowerCase());
        NamespacedKey expireKey = new NamespacedKey(plugin, "cooldown_expire_" + material.name().toLowerCase());
        PersistentDataContainer data = player.getPersistentDataContainer();
        String currentOwner = data.get(ownerKey, PersistentDataType.STRING);
        boolean hasCooldown = player.hasCooldown(material);
        String myOwner = getClass().getName();
        if (hasCooldown && (currentOwner == null || !currentOwner.equals(myOwner))) {
            return;
        }
        int ticks = Math.max(1, seconds * 20);
        player.setCooldown(material, ticks);
        data.set(ownerKey, PersistentDataType.STRING, myOwner);
        data.set(expireKey, PersistentDataType.LONG, (long) Bukkit.getCurrentTick() + ticks);
    }

    protected void applyIronCooldownIfEmpty(int seconds) {
        applyMaterialCooldownIfEmpty(Material.IRON_INGOT, seconds);
    }

    protected void lockMovement(LivingEntity target, int ticks) {
        if (target == null || ticks <= 0) {
            return;
        }
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null || plugin.getGameManager() == null) {
            return;
        }
        plugin.getGameManager().lockMovement(target, ticks);
    }

    protected void applySlow(LivingEntity target, int ticks) {
        if (target == null || ticks <= 0) {
            return;
        }
        Slow.apply(target, ticks);
    }

    protected void applySlow(LivingEntity target, int ticks, int amplifier) {
        if (target == null || ticks <= 0) {
            return;
        }
        Slow.apply(target, ticks, amplifier);
    }

    protected void applySlow(LivingEntity target, int ticks, double percent) {
        if (target == null || ticks <= 0) {
            return;
        }
        Slow.apply(target, ticks, percent);
    }

    protected void applySlow(LivingEntity target, int ticks, Slow.SlowProfile profile) {
        if (target == null || ticks <= 0) {
            return;
        }
        Slow.apply(target, ticks, profile);
    }

    protected void scaleIncomingDamage(EntityDamageEvent event, double multiplier) {
        applyIncomingPercentDamage(event, (multiplier - 1.0) * 100.0);
    }

    protected void scaleOutgoingDamage(EntityDamageByEntityEvent event, double multiplier) {
        applyOutgoingPercentDamage(event, (multiplier - 1.0) * 100.0);
    }

    protected void increaseIncomingDamage(EntityDamageEvent event, double percent) {
        applyIncomingPercentDamage(event, percent);
    }

    protected void decreaseIncomingDamage(EntityDamageEvent event, double percent) {
        applyIncomingPercentDamage(event, -percent);
    }

    protected void increaseOutgoingDamage(EntityDamageByEntityEvent event, double percent) {
        applyOutgoingPercentDamage(event, percent);
    }

    protected void decreaseOutgoingDamage(EntityDamageByEntityEvent event, double percent) {
        applyOutgoingPercentDamage(event, -percent);
    }

    protected void addIncomingDamage(EntityDamageEvent event, double amount) {
        addIncomingFlatDamage(event, amount);
    }

    protected void addOutgoingDamage(EntityDamageByEntityEvent event, double amount) {
        addOutgoingFlatDamage(event, amount);
    }

    protected double getCalculatedFinalDamage(EntityDamageEvent event) {
        if (event == null) {
            return 0.0;
        }
        return DamageModifier.previewFinalDamage(event);
    }

    protected boolean isMovementLocked(LivingEntity target) {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null || plugin.getGameManager() == null) {
            return false;
        }
        return plugin.getGameManager().isMovementLocked(target);
    }

    private void applyIncomingPercentDamage(EntityDamageEvent event, double percent) {
        if (event == null || !Double.isFinite(percent)) {
            return;
        }
        DamageModifier.addIncomingPercent(event, percent);
    }

    private void applyOutgoingPercentDamage(EntityDamageByEntityEvent event, double percent) {
        if (event == null || !Double.isFinite(percent)) {
            return;
        }
        DamageModifier.addOutgoingPercent(event, percent);
    }

    private void addIncomingFlatDamage(EntityDamageEvent event, double amount) {
        if (event == null || !Double.isFinite(amount)) {
            return;
        }
        DamageModifier.addIncomingFlat(event, amount);
    }

    private void addOutgoingFlatDamage(EntityDamageByEntityEvent event, double amount) {
        if (event == null || !Double.isFinite(amount)) {
            return;
        }
        DamageModifier.addOutgoingFlat(event, amount);
    }

    /**
     * 활과 화살을 지급합니다. (활 1개, 화살 64개)
     */
    protected void giveBowAndArrows() {
        Player player = getPlayer();
        if (player == null)
            return;
        org.bukkit.inventory.ItemStack bow = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BOW, 1);
        org.bukkit.inventory.meta.ItemMeta meta = bow.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.POWER, 4, true);
            bow.setItemMeta(meta);
        }
        player.getInventory().addItem(bow);
        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW, 96));
    }

    /**
     * 능력 등급 반환
     */
    public AbilityManifest.Rank getRank() {
        return manifest.rank();
    }

    /**
     * 능력 종족 반환
     */
    public AbilityManifest.Species getSpecies() {
        return manifest.species();
    }

    /**
     * 능력 파괴 여부
     */
    public boolean isDestroyed() {
        return destroyed;
    }

    /**
     * 능력 활성화 (이벤트 리스너 등록)
     */
    public void activate() {
        AbilityCombat.getPlugin().getServer().getPluginManager().registerEvents(this, AbilityCombat.getPlugin());
        onActivate();
    }

    /**
     * 능력 비활성화 (이벤트 리스너 해제)
     */
    public void deactivate() {
        HandlerList.unregisterAll(this);
        onDeactivate();
    }

    /**
     * 능력 파괴 (완전 해제)
     */
    public void destroy() {
        if (destroyed)
            return;
        destroyed = true;
        unregisterTick();

        // EventBridge에서 모든 이벤트 구독 해제
        com.abilitycombat.event.EventBridge bridge = getEventBridge();
        if (bridge != null) {
            bridge.unsubscribeAll(this);
        }

        // 모든 타이머 정지
        for (AbilityTimer timer : new java.util.ArrayList<>(timers)) {
            timer.stop(true);
        }
        timers.clear();

        deactivate();
        onDestroy();
    }

    /**
     * 타이머 등록
     */
    protected void registerTimer(AbilityTimer timer) {
        timers.add(timer);
    }

    /**
     * 타이머 해제
     */
    protected void unregisterTimer(AbilityTimer timer) {
        timers.remove(timer);
    }

    protected void registerTick() {
        if (!tickRegistered) {
            AbilityTickManager.register(this);
            tickRegistered = true;
        }
    }

    protected void unregisterTick() {
        if (tickRegistered) {
            AbilityTickManager.unregister(this);
            tickRegistered = false;
        }
    }

    @Override
    public void onTick(int tick) {
    }

    /**
     * 능력 활성화 시 호출
     */
    protected void onActivate() {
    }

    /**
     * 능력 비활성화 시 호출
     */
    protected void onDeactivate() {
    }

    /**
     * 능력 파괴 시 호출
     */
    protected void onDestroy() {
    }

    // =============== EventBridge Support ===============

    protected com.abilitycombat.event.EventBridge getEventBridge() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin != null ? plugin.getEventBridge() : null;
    }

    protected void subscribeEvent(Class<? extends org.bukkit.event.Event> eventClass) {
        com.abilitycombat.event.EventBridge bridge = getEventBridge();
        if (bridge != null) {
            bridge.subscribe(eventClass, this);
        }
    }

    protected void unsubscribeEvent(Class<? extends org.bukkit.event.Event> eventClass) {
        com.abilitycombat.event.EventBridge bridge = getEventBridge();
        if (bridge != null) {
            bridge.unsubscribe(eventClass, this);
        }
    }

    /**
     * EventBridge에서 전달받은 이벤트 처리 (서브클래스에서 오버라이드)
     */
    public void handleBridgeEvent(org.bukkit.event.Event event) {
        // 서브클래스에서 타입 체크 후 처리
    }

    // =============== Inner Classes ===============
    /**
     * 능력 타이머 기본 클래스
     */
    public abstract class AbilityTimer implements Runnable, AbilityTickManager.Tickable {
        private int count;
        private final int maxCount;
        private long periodTicks;
        private long tickAccumulator;
        private boolean running = false;
        private boolean paused = false;
        private int pausedCount = -1;

        public AbilityTimer(int maxCount) {
            this.maxCount = maxCount;
            this.count = maxCount;
            this.periodTicks = 20L; // 1초 기본
            this.tickAccumulator = 0L;
        }

        public AbilityTimer setPeriod(long ticks) {
            this.periodTicks = Math.max(1L, ticks);
            return this;
        }

        public boolean start() {
            if (running || destroyed)
                return false;
            running = true;
            paused = false;
            count = maxCount;
            tickAccumulator = 0L;
            onStart();
            registerTimer(this);
            AbilityTickManager.register(this);
            // GameManager 등록
            AbilityCombat plugin = AbilityCombat.getPlugin();
            if (plugin != null && plugin.getGameManager() != null) {
                plugin.getGameManager().registerTimer(this);
            }
            return true;
        }

        public void stop(boolean silent) {
            if (!running && !paused)
                return;
            running = false;
            paused = false;
            AbilityTickManager.unregister(this);
            unregisterTimer(this);
            // GameManager 해제
            AbilityCombat plugin = AbilityCombat.getPlugin();
            if (plugin != null && plugin.getGameManager() != null) {
                plugin.getGameManager().unregisterTimer(this);
            }
            if (silent) {
                onSilentEnd();
            } else {
                onEnd();
            }
        }

        public void pause() {
            if (!paused && running) {
                pausedCount = count;
                running = false;
                paused = true;
                AbilityTickManager.unregister(this);
            }
        }

        public void resume() {
            if (paused) {
                paused = false;
                count = pausedCount;
                running = true;
                AbilityTickManager.register(this);
            }
        }

        public boolean isPaused() {
            return paused;
        }

        @Override
        public void onTick(int tick) {
            if (!running) {
                return;
            }
            tickAccumulator += AbilityTickManager.TICK_INTERVAL;
            while (tickAccumulator >= periodTicks) {
                tickAccumulator -= periodTicks;
                run();
                if (!running) {
                    break;
                }
            }
        }

        @Override
        public void run() {
            if (AbilityBase.this.getPlayer() == null) {
                stop(true);
                return;
            }
            if (count <= 0) {
                stop(false);
                return;
            }
            onRun(count);
            count--;
        }

        public boolean isRunning() {
            return running;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public int getMaxCount() {
            return maxCount;
        }

        protected void onStart() {
        }

        protected abstract void onRun(int count);

        protected void onEnd() {
        }

        protected void onSilentEnd() {
        }
    }

    /**
     * 쿨다운 타이머
     */
    public class Cooldown extends AbilityTimer {
        public Cooldown(int seconds) {
            super(seconds);
        }

        public boolean isCooldown() {
            return isRunning();
        }

        @Override
        protected void onRun(int count) {
            // 쿨다운 진행 중
        }

        @Override
        protected void onEnd() {
            String name = getDisplayName();
            Player player = getPlayer();
            if (player != null) {
                player.sendMessage("§a" + name + " §f능력을 다시 사용할 수 있습니다.");
            }
        }
    }

    /**
     * 액션바 쿨다운 타이머
     */
    public class ActionbarCooldown extends Cooldown {
        private final String actionbarKey;
        private final int priority;

        public ActionbarCooldown(int seconds) {
            this(seconds, 10);
        }

        public ActionbarCooldown(int seconds, int priority) {
            super(seconds);
            this.priority = priority;
            this.actionbarKey = AbilityBase.this.getClass().getName()
                    + ":cooldown:" + Integer.toHexString(System.identityHashCode(this));
        }

        @Override
        protected void onRun(int count) {
            Player player = getPlayer();
            if (player == null) {
                stop(true);
                return;
            }
            Component message = Component.text("남은 쿨타임 ", NamedTextColor.GRAY)
                    .append(Component.text(count + "초", NamedTextColor.RED));
            ActionbarChannel channel = getActionbarChannel();
            if (channel != null) {
                channel.update(player, actionbarKey, priority, message);
            } else {
                player.sendActionBar(message);
            }
        }

        @Override
        protected void onEnd() {
            super.onEnd();
            clearActionbar();
        }

        @Override
        protected void onSilentEnd() {
            clearActionbar();
        }

        private void clearActionbar() {
            Player player = getPlayer();
            if (player == null) {
                return;
            }
            ActionbarChannel channel = getActionbarChannel();
            if (channel != null) {
                channel.clear(player, actionbarKey);
            } else {
                player.sendActionBar(Component.empty());
            }
        }
    }

    public class Duration extends AbilityTimer {
        private final Cooldown linkedCooldown;

        public Duration(int seconds, Cooldown linkedCooldown) {
            super(seconds);
            this.linkedCooldown = linkedCooldown;
        }

        public Duration(int seconds) {
            this(seconds, null);
        }

        public boolean isDuration() {
            return isRunning();
        }

        @Override
        protected void onRun(int count) {
            onDurationProcess(count);
        }

        @Override
        protected void onEnd() {
            onDurationEnd();
            if (linkedCooldown != null) {
                linkedCooldown.start();
            }
        }

        @Override
        protected void onSilentEnd() {
            onDurationSilentEnd();
        }

        protected void onDurationProcess(int count) {
        }

        protected void onDurationEnd() {
        }

        protected void onDurationSilentEnd() {
        }
    }

    public class BossBarCooldown extends Cooldown {
        private final String barKey;
        private final int priority;
        private final BossBar.Color color;
        private final BossBar.Overlay overlay;

        public BossBarCooldown(int seconds) {
            this(seconds, 10, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        }

        public BossBarCooldown(int seconds, int priority, BossBar.Color color, BossBar.Overlay overlay) {
            super(seconds);
            this.priority = priority;
            this.color = color;
            this.overlay = overlay;
            this.barKey = AbilityBase.this.getClass().getName()
                    + ":cooldown:" + Integer.toHexString(System.identityHashCode(this));
        }

        @Override
        protected void onStart() {
            updateBar(getMaxCount());
        }

        @Override
        protected void onRun(int count) {
            updateBar(count);
        }

        @Override
        protected void onEnd() {
            clearBar();
            super.onEnd();
        }

        @Override
        protected void onSilentEnd() {
            clearBar();
        }

        private void updateBar(int remaining) {
            BossBarManager manager = getBossBarManager();
            if (manager == null) {
                return;
            }
            Player player = getPlayer();
            if (player == null) {
                return;
            }
            float progress = getMaxCount() > 0 ? remaining / (float) getMaxCount() : 0f;
            Component title = Component.text(getDisplayName() + " 쿨타임 " + remaining + "초", NamedTextColor.YELLOW);
            manager.update(player, barKey, priority, title, progress, color, overlay);
        }

        private void clearBar() {
            BossBarManager manager = getBossBarManager();
            if (manager != null) {
                Player player = getPlayer();
                if (player != null) {
                    manager.clear(player, barKey);
                }
            }
        }
    }

    public class BossBarDuration extends Duration {
        private final String barKey;
        private final int priority;
        private final BossBar.Color color;
        private final BossBar.Overlay overlay;

        public BossBarDuration(int seconds) {
            this(seconds, 10, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        }

        public BossBarDuration(int seconds, int priority, BossBar.Color color, BossBar.Overlay overlay) {
            super(seconds);
            this.priority = priority;
            this.color = color;
            this.overlay = overlay;
            this.barKey = AbilityBase.this.getClass().getName()
                    + ":duration:" + Integer.toHexString(System.identityHashCode(this));
        }

        @Override
        protected void onStart() {
            updateBar(getMaxCount());
        }

        @Override
        protected final void onDurationProcess(int count) {
            updateBar(count);
            onBossBarProcess(count);
        }

        @Override
        protected final void onDurationEnd() {
            clearBar();
            onBossBarEnd();
        }

        @Override
        protected final void onDurationSilentEnd() {
            clearBar();
            onBossBarSilentEnd();
        }

        protected void onBossBarProcess(int count) {
        }

        protected void onBossBarEnd() {
        }

        protected void onBossBarSilentEnd() {
        }

        private void updateBar(int remaining) {
            BossBarManager manager = getBossBarManager();
            if (manager == null) {
                return;
            }
            Player player = getPlayer();
            if (player == null) {
                return;
            }
            float progress = getMaxCount() > 0 ? remaining / (float) getMaxCount() : 0f;
            Component title = getDurationTitle(remaining);
            manager.update(player, barKey, priority, title, progress, color, overlay);
        }

        protected Component getDurationTitle(int remaining) {
            return Component.text(getDisplayName() + " 지속 ", NamedTextColor.GREEN)
                    .append(Component.text(remaining + "초", NamedTextColor.WHITE));
        }

        private void clearBar() {
            BossBarManager manager = getBossBarManager();
            if (manager != null) {
                Player player = getPlayer();
                if (player != null) {
                    manager.clear(player, barKey);
                }
            }
        }
    }

    public class BossBarGauge {
        private final String barKey;
        private final int priority;
        private final BossBar.Color color;
        private final BossBar.Overlay overlay;

        public BossBarGauge(String key, int priority, BossBar.Color color, BossBar.Overlay overlay) {
            String suffix = (key == null || key.isBlank())
                    ? Integer.toHexString(System.identityHashCode(this))
                    : key;
            this.barKey = AbilityBase.this.getClass().getName() + ":gauge:" + suffix;
            this.priority = priority;
            this.color = color;
            this.overlay = overlay;
        }

        public void update(Component title, double progress) {
            BossBarManager manager = getBossBarManager();
            if (manager == null) {
                return;
            }
            Player player = getPlayer();
            if (player == null) {
                return;
            }
            float clamped = (float) Math.max(0.0, Math.min(1.0, progress));
            manager.update(player, barKey, priority, title, clamped, color, overlay);
        }

        public void clear() {
            BossBarManager manager = getBossBarManager();
            if (manager != null) {
                Player player = getPlayer();
                if (player != null) {
                    manager.clear(player, barKey);
                }
            }
        }
    }
}
