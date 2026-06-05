package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "보이드 (Void)", species = AbilityManifest.Species.OTHERS, explain = {
        "§e§l[패시브 - 공허 차원문]",
        "§7순간이동 시 그 자리에 §5차원문§7을 남깁니다 (지속: §f20초§7)",
        "§7차원문에 §b닿으면§7 도착 위치로 이동할 수 있습니다",
        "",
        "§e§l[철괴 우클릭 - 공허의 습격]§f §8(쿨타임: 50초)",
        "§7가장 가까운 플레이어(§f25칸§7 이내)에게 순간이동합니다",
        "§7순간이동 후 §f2초§7간 §b무적§7 상태가 됩니다"
}, summarize = {
        "§7패시브§f: 순간이동 시 차원문 생성",
        "§7철괴 우클릭§f: 적에게 순간이동 + 무적"
})
public class Void extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 50;
    private static final int INVULN_SECONDS = 2;
    private static final int PORTAL_SECONDS = 20;
    private static final double RANGE = 25.0;
    private static final double PORTAL_RADIUS = 1.2; // 포탈 감지 범위
    private static final String KEY_PORTAL = "void_portal";

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private int remainingInvulnSeconds = 0;
    private final Map<UUID, PortalData> portals = new HashMap<>();
    private boolean storedInvulnerable;

    public Void(Participant participant) {
        super(participant);
    }

    public static NamespacedKey getPortalKey(AbilityCombat plugin) {
        return new NamespacedKey(plugin, KEY_PORTAL);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(PlayerTeleportEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        stopInvuln();
        removeAllPortals();
    }

    @Override
    public boolean activeSkill(Material material, ActiveHandler.ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ActiveHandler.ClickType.RIGHT_CLICK) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        Player target = LocationUtil.getNearestEntity(Player.class, getPlayer().getLocation(), RANGE,
                player -> !player.equals(getPlayer()) && LocationUtil.isValidTarget(getPlayer(), player));
        if (target == null) {
            return false;
        }
        Location fromLoc = getPlayer().getLocation().clone();
        Location toLoc = target.getLocation().clone();
        getPlayer().teleport(toLoc);
        // 직접 차원문 생성
        createPortal(fromLoc, toLoc);
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        startInvuln();
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof PlayerTeleportEvent) {
            onTeleport((PlayerTeleportEvent) event);
        }
    }

    private void onTeleport(PlayerTeleportEvent event) {
        if (!event.getPlayer().equals(getPlayer())) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from == null || to == null) {
            return;
        }
        // 능력 발동에 의한 텔레포트가 아닌 경우에만 차원문 생성 (엔더펄 등)
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            createPortal(from.clone(), to.clone());
        }
    }

    private void createPortal(Location from, Location to) {
        // 투명 아머스탠드 생성
        ArmorStand stand = from.getWorld().spawn(from.clone().add(0, -0.5, 0), ArmorStand.class, entity -> {
            entity.setVisible(false);
            entity.setGravity(false);
            entity.setMarker(true);
            entity.setInvulnerable(true);
            entity.setSmall(false);
            entity.customName(Component.text("§5[ 차원문 ]", NamedTextColor.DARK_PURPLE));
            entity.setCustomNameVisible(true);
            // 머리에 엔더 포탈 프레임 장착
            entity.getEquipment().setHelmet(new ItemStack(Material.END_PORTAL_FRAME));
            entity.getPersistentDataContainer().set(
                    getPortalKey(AbilityCombat.getPlugin()),
                    PersistentDataType.BYTE, (byte) 1);
            AbilityCombat.markAbilityArmorStand(entity);
        });

        // 포탈 데이터 저장
        PortalData data = new PortalData(stand, to, PORTAL_SECONDS * 20);
        portals.put(stand.getUniqueId(), data);

        // 생성 효과
        from.getWorld().spawnParticle(Particle.PORTAL, from, 50, 0.5, 0.5, 0.5, 0.5);
        from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
    }

    private void removeAllPortals() {
        for (PortalData data : portals.values()) {
            if (data.stand != null && !data.stand.isDead()) {
                data.stand.remove();
            }
        }
        portals.clear();
    }

    private void startInvuln() {
        Player player = getPlayer();
        storedInvulnerable = player.isInvulnerable();
        player.setInvulnerable(true);
        remainingInvulnSeconds = INVULN_SECONDS;
    }

    private void stopInvuln() {
        getPlayer().setInvulnerable(storedInvulnerable);
        remainingInvulnSeconds = 0;
    }

    private boolean isVoidInvulnerable() {
        return remainingInvulnSeconds > 0;
    }

    @Override
    public void onTick(int tick) {
        // 무적 타이머 (1초마다)
        if (tick % 20 == 0) {
            if (isVoidInvulnerable()) {
                remainingInvulnSeconds--;
                if (remainingInvulnSeconds <= 0) {
                    stopInvuln();
                }
            }
        }

        // 포탈 겹침 감지 및 수명 관리 (4틱마다)
        if (tick % 4 == 0) {
            processPortals();
        }
    }

    private void processPortals() {
        Iterator<Map.Entry<UUID, PortalData>> iter = portals.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<UUID, PortalData> entry = iter.next();
            PortalData data = entry.getValue();

            // 수명 감소
            data.remainingTicks -= 4;
            if (data.remainingTicks <= 0 || data.stand == null || data.stand.isDead()) {
                if (data.stand != null && !data.stand.isDead()) {
                    data.stand.remove();
                }
                iter.remove();
                continue;
            }

            // 포탈 파티클 효과
            Location portalLoc = data.stand.getLocation().add(0, 1.0, 0);
            portalLoc.getWorld().spawnParticle(Particle.PORTAL, portalLoc, 5, 0.3, 0.3, 0.3, 0.1);

            // 재사용 쿨다운 감소
            if (data.useCooldown > 0) {
                data.useCooldown -= 4;
                continue; // 쿨다운 중에는 사용 불가
            }

            // 플레이어 겹침 감지
            for (Player player : portalLoc.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(portalLoc) <= PORTAL_RADIUS * PORTAL_RADIUS) {
                    // 포탈로 순간이동
                    player.teleport(data.destination);
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                    player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 30, 0.5, 1.0, 0.5, 0.3);

                    // 재사용 쿨다운 설정 (1초 = 20틱)
                    data.useCooldown = 20;
                    break;
                }
            }
        }
    }

    /**
     * 포탈 데이터
     */
    private static class PortalData {
        final ArmorStand stand;
        final Location destination;
        int remainingTicks;
        int useCooldown; // 재사용 쿨다운

        PortalData(ArmorStand stand, Location destination, int remainingTicks) {
            this.stand = stand;
            this.destination = destination;
            this.remainingTicks = remainingTicks;
            this.useCooldown = 0;
        }
    }
}
