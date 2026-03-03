package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.combat.SweepEffectAllowance;
import com.abilitycombat.game.Participant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

@AbilityManifest(name = "리바이 병장 (LeviAckerman)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[입체기동장치]",
        "§7게임 시작 시 §f입체기동장치§7(낚시대)를 지급받습니다.",
        "",
        "§7낚시대를 던져 §f블록§7이나 §f플레이어§7에 닿으면",
        "§b해당 위치로 빠르게 돌진§7합니다.",
        "",
        "§7낚시대가 허공에서 회수되면 돌진하지 않습니다.",
        "",
        "§e§l[패시브 - 거인 슬레이어]",
        "§7'§c거인§7' 능력을 가진 플레이어를 공격 시",
        "§c100%§7의 추가 데미지를 입힙니다."
}, summarize = {
        "§7낚시대 적중 시 돌진",
        "§7거인 능력자에게 +100% 데미지"
})
public class ODMGear extends AbilityBase {

    private static final String ITEM_NAME = "입체기동장치";

    public ODMGear(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(PlayerFishEvent.class);
        subscribeEvent(EntityDamageByEntityEvent.class);
        giveODMGear();
    }

    @Override
    protected void onDeactivate() {
        removeODMGear();
    }

    private void giveODMGear() {
        Player player = getPlayer();
        if (player == null)
            return;

        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(ITEM_NAME, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            rod.setItemMeta(meta);
        }
        player.getInventory().addItem(rod);
    }

    private void removeODMGear() {
        Player player = getPlayer();
        if (player == null)
            return;

        for (ItemStack item : player.getInventory().getContents()) {
            if (isODMGear(item)) {
                player.getInventory().remove(item);
            }
        }
    }

    private boolean isODMGear(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        Component displayName = meta.displayName();
        if (displayName == null) {
            return false;
        }
        // Component를 문자열로 변환하여 비교
        return displayName.toString().contains(ITEM_NAME);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof PlayerFishEvent e) {
            onFish(e);
        } else if (event instanceof EntityDamageByEntityEvent e) {
            onDamage(e);
        }
    }

    private void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        // 대상이 '거인' 능력을 가지고 있는지 확인
        Participant targetParticipant = com.abilitycombat.AbilityCombat.getPlugin().getGameManager()
                .getParticipant(target.getUniqueId());
        if (targetParticipant != null && targetParticipant.getAbility() instanceof Giant) {
            // 100% 추가 데미지 (즉 2배)
            event.setDamage(event.getDamage() * 2.0);
        }
    }

    private void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (!player.equals(getPlayer())) {
            return;
        }

        PlayerFishEvent.State state = event.getState();
        FishHook hook = event.getHook();
        Location hookLoc = hook.getLocation();

        // REEL_IN, CAUGHT_FISH, CAUGHT_ENTITY, IN_GROUND 등 다양한 상태 체크
        if (state == PlayerFishEvent.State.REEL_IN ||
                state == PlayerFishEvent.State.CAUGHT_ENTITY ||
                state == PlayerFishEvent.State.IN_GROUND ||
                state == PlayerFishEvent.State.CAUGHT_FISH) {

            // 1. 엔티티(플레이어)가 주변에 있는지 확인
            for (org.bukkit.entity.Entity entity : hookLoc.getWorld().getNearbyEntities(hookLoc, 2.0, 2.0, 2.0)) {
                if (entity instanceof Player target && !target.equals(player)) {
                    dashTowards(player, target.getLocation());
                    return;
                }
            }

            // 2. 주변에 솔리드 블록이 있는지 확인
            if (hasNearbyBlock(hookLoc)) {
                dashTowards(player, hookLoc);
            }
        }
    }

    private boolean hasNearbyBlock(Location loc) {
        // 훅 주변 블록 확인 (1블록 범위)
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (!loc.clone().add(x, y, z).getBlock().isPassable()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void dashTowards(Player player, Location target) {
        Location playerLoc = player.getLocation();
        Vector direction = target.toVector().subtract(playerLoc.toVector());
        double distance = direction.length();

        // 거리가 너무 가까우면 무시
        if (distance < 1) {
            return;
        }

        // 거리에 비례한 속도 계산 (최소 0.8, 최대 2.5)
        double speed = Math.min(2.5, Math.max(0.8, distance * 0.2));

        direction.normalize().multiply(speed);

        // Y축 보정 (상승 시 추가 부스트)
        double yDiff = target.getY() - playerLoc.getY();
        if (yDiff > 0) {
            direction.setY(Math.min(direction.getY() + 0.4, 1.8));
        }

        player.setVelocity(direction);
        player.playSound(playerLoc, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.5f);
        SweepEffectAllowance.markAbilitySweepSound();
        player.getWorld().playSound(playerLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
    }
}
