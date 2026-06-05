package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityFactory;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.GameManager;
import com.abilitycombat.game.Participant;
import com.abilitycombat.npc.PlayerReplica;
import com.abilitycombat.npc.ReplicaProfile;
import com.abilitycombat.utils.FakeGlow;
import com.abilitycombat.utils.LocationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "아카식 레코드 (AkashicRecord)", species = AbilityManifest.Species.SPECIAL, explain = {
        "§e§l[패시브 - 사망 기록]",
        "§7플레이어가 사망하면 자신에게만 보이는 기록 더미가 남습니다",
        "§7기록 더미를 우클릭하면 해당 플레이어의 능력을 §f180초§7간 복제합니다",
        "",
        "§e§l[철괴 우클릭 - 기록 열람]§f §8(쿨타임: 20초)",
        "§7능력 복제 중이 아닐 때 바라본 상대의 능력 정보를 읽습니다"
}, summarize = {
        "§7패시브§f: 사망 기록 더미 우클릭 시 180초 능력 복제",
        "§7철괴 우클릭§f: 바라본 상대 능력 정보 확인 (20초)"
})
public class AkashicRecord extends AbilityBase implements ActiveHandler {

    private static final int COPY_TICKS = 3600;
    private static final double READ_RANGE = 20.0;
    private static final int READ_COOLDOWN_SECONDS = 20;
    private static final String GLOW_TEAM_NAME = "aw_akashic";

    private final Map<UUID, RecordDummy> records = new HashMap<>();
    private final Cooldown readCooldown = new Cooldown(READ_COOLDOWN_SECONDS);
    private AbilityBase copiedAbility;
    private int copyEndTick;

    public AkashicRecord(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(PlayerDeathEvent.class);
        subscribeEvent(PlayerInteractEntityEvent.class);
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        clearRecords();
        destroyCopiedAbility();
        unregisterTick();
    }

    @Override
    protected void onDestroy() {
        clearRecords();
        destroyCopiedAbility();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (copiedAbility != null) {
            return copiedAbility instanceof ActiveHandler handler && handler.activeSkill(material, clickType);
        }
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (readCooldown.isCooldown()) {
            notifyCooldown(readCooldown);
            return false;
        }
        if (!readTargetAbility()) {
            return false;
        }
        readCooldown.start();
        applyIronCooldownIfEmpty(READ_COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof PlayerDeathEvent deathEvent) {
            onPlayerDeath(deathEvent);
        } else if (event instanceof PlayerInteractEntityEvent interactEvent) {
            onInteractEntity(interactEvent);
        } else if (copiedAbility != null) {
            copiedAbility.handleBridgeEvent(event);
        }
    }

    @Override
    public void onTick(int tick) {
        refreshRecordViewers();
        if (copiedAbility != null && tick >= copyEndTick) {
            Player player = getPlayer();
            destroyCopiedAbility();
            if (player != null) {
                player.sendMessage("§7아카식 복제가 종료되었습니다");
            }
        }
        if (records.isEmpty() && copiedAbility == null) {
            return;
        }
        if (tick % 20 == 0) {
            Player owner = getPlayer();
            if (owner != null) {
                for (RecordDummy record : records.values()) {
                    FakeGlow.show(owner, record.dummy.getEntity(), GLOW_TEAM_NAME, NamedTextColor.AQUA);
                }
            }
        }
    }

    private void onPlayerDeath(PlayerDeathEvent event) {
        Player owner = getPlayer();
        Player dead = event.getEntity();
        if (owner == null || dead == null || dead.equals(owner)) {
            return;
        }
        GameManager gameManager = AbilityCombat.getPlugin().getGameManager();
        Participant deadParticipant = gameManager != null ? gameManager.getParticipant(dead.getUniqueId()) : null;
        AbilityBase deadAbility = deadParticipant != null ? deadParticipant.getAbility() : null;
        if (deadAbility == null || deadAbility instanceof AkashicRecord) {
            return;
        }
        PlayerReplica dummy = AbilityCombat.getPlugin().getReplicaManager()
                .createReplica(dead.getLocation(), ReplicaProfile.fromPlayer(dead));
        dummy.setInvulnerable(true);
        dummy.setGravity(false);
        dummy.setCollidable(false);
        dummy.customName(Component.text(dead.getName() + "의 기록", NamedTextColor.AQUA));
        dummy.setCustomNameVisible(true);
        EntityEquipment equipment = dummy.getEquipment();
        if (equipment != null) {
            equipment.setArmorContents(new ItemStack[] {
                    new ItemStack(Material.AIR), new ItemStack(Material.AIR),
                    new ItemStack(Material.AIR), new ItemStack(Material.AIR)
            });
            equipment.setItemInMainHand(new ItemStack(Material.AIR));
            equipment.setItemInOffHand(new ItemStack(Material.AIR));
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(owner)) {
                dummy.hideFrom(online);
            }
        }
        dummy.syncEquipment();
        dummy.spawn();
        dummy.showTo(owner);
        FakeGlow.show(owner, dummy.getEntity(), GLOW_TEAM_NAME, NamedTextColor.AQUA);
        records.put(dummy.getUniqueId(), new RecordDummy(dummy, deadAbility.getClass(), deadAbility.getName()));
    }

    private void onInteractEntity(PlayerInteractEntityEvent event) {
        Player owner = getPlayer();
        if (owner == null || !event.getPlayer().equals(owner)) {
            return;
        }
        RecordDummy record = records.get(event.getRightClicked().getUniqueId());
        if (record == null) {
            return;
        }
        event.setCancelled(true);
        copyAbility(record);
    }

    private void copyAbility(RecordDummy record) {
        Player owner = getPlayer();
        if (owner == null || record == null) {
            return;
        }
        destroyCopiedAbility();
        copiedAbility = AbilityFactory.create(record.abilityClass, getParticipant());
        copiedAbility.activate();
        copyEndTick = com.abilitycombat.ability.AbilityTickManager.getGlobalTick() + COPY_TICKS;
        removeRecord(record.dummy.getUniqueId());
        owner.playSound(owner.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.3f);
        owner.sendMessage("§b아카식 레코드 §f복제: §e" + record.abilityName + " §7(180초)");
    }

    private boolean readTargetAbility() {
        Player owner = getPlayer();
        GameManager gameManager = AbilityCombat.getPlugin().getGameManager();
        if (owner == null || gameManager == null) {
            return false;
        }
        LivingEntity target = LocationUtil.getEntityLookingAt(LivingEntity.class, owner, READ_RANGE,
                entity -> entity instanceof Player && LocationUtil.isValidTarget(owner, entity));
        if (!(target instanceof Player targetPlayer)) {
            owner.sendMessage("§c읽을 대상이 없습니다");
            return false;
        }
        Participant targetParticipant = gameManager.getParticipant(targetPlayer.getUniqueId());
        AbilityBase targetAbility = targetParticipant != null ? targetParticipant.getAbility() : null;
        if (targetAbility == null) {
            owner.sendMessage("§c대상의 능력 정보를 찾을 수 없습니다");
            return false;
        }
        owner.sendMessage("§6[아카식 기록] §f" + targetPlayer.getName() + " §7- §e" + targetAbility.getName());
        if (!targetAbility.getExplain().isEmpty()) {
            for (String line : targetAbility.getExplain()) {
                owner.sendMessage("§f- " + line);
            }
        }
        return true;
    }

    private void refreshRecordViewers() {
        Player owner = getPlayer();
        Iterator<Map.Entry<UUID, RecordDummy>> iterator = records.entrySet().iterator();
        while (iterator.hasNext()) {
            RecordDummy record = iterator.next().getValue();
            if (record.dummy.isDead()) {
                iterator.remove();
                continue;
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (owner != null && online.equals(owner)) {
                    record.dummy.showTo(online);
                } else {
                    record.dummy.hideFrom(online);
                }
            }
        }
    }

    private void removeRecord(UUID id) {
        RecordDummy record = records.remove(id);
        if (record == null) {
            return;
        }
        Player owner = getPlayer();
        if (owner != null) {
            FakeGlow.hide(owner, record.dummy.getEntity(), GLOW_TEAM_NAME,
                    FakeGlow.scoreboardEntry(record.dummy.getEntity()));
        }
        record.dummy.remove();
    }

    private void clearRecords() {
        for (UUID id : java.util.List.copyOf(records.keySet())) {
            removeRecord(id);
        }
        records.clear();
    }

    private void destroyCopiedAbility() {
        if (copiedAbility != null && !copiedAbility.isDestroyed()) {
            copiedAbility.destroy();
        }
        copiedAbility = null;
        copyEndTick = 0;
    }

    public AbilityBase getCopiedAbility() {
        return copiedAbility;
    }

    private record RecordDummy(PlayerReplica dummy, Class<? extends AbilityBase> abilityClass, String abilityName) {
    }
}
