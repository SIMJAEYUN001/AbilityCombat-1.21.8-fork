package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

@AbilityManifest(name = "처형 시간 (ExecutionTime)", species = AbilityManifest.Species.SPECIAL, explain = {
        "§e§l[철괴 우클릭 - 처형 표식]§f §8(쿨타임: 35초)",
        "§7바라본 §f8칸§7 내 적 1명에게 §c처형 표식§7을 남깁니다.",
        "§7표식은 §f10초§7 동안 대상이 받은 원본 피해를 기록합니다.",
        "§710초 후 대상은 §c2 + 기록 피해의 40% 고정 피해§7를 받습니다.",
        "§7표식은 한 번에 §f1개§7만 유지됩니다."
}, summarize = {
        "§7철괴 우클릭§f: 8칸 적 1명에게 10초 처형 표식",
        "§7종료§f: 2 + 기록 피해 40% 고정 피해",
        "§7제한§f: 표식 1개, 쿨타임 35초"
})
public class ExecutionTime extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 35;
    private static final double MARK_RANGE = 8.0;
    private static final int MARK_TICKS = 200;
    private static final double BASE_DAMAGE = 2.0;
    private static final double STORED_DAMAGE_RATIO = 0.4;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private Mark mark;

    public ExecutionTime(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageEvent.class);
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        clearMark();
        unregisterTick();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        LivingEntity target = LocationUtil.getEntityLookingAt(LivingEntity.class, player, MARK_RANGE,
                entity -> LocationUtil.isValidTarget(player, entity));
        if (target == null) {
            player.sendMessage("§c8칸 내 바라본 적이 없습니다.");
            return false;
        }
        clearMark();
        mark = createMark(target);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.65f, 0.8f);
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (!(event instanceof EntityDamageEvent damageEvent) || damageEvent.isCancelled()
                || mark == null || !(damageEvent.getEntity() instanceof LivingEntity target)
                || !mark.targetId.equals(target.getUniqueId())) {
            return;
        }
        mark.rawDamage += Math.max(0.0, damageEvent.getDamage());
    }

    @Override
    public void onTick(int tick) {
        if (mark == null) {
            return;
        }
        LivingEntity target = resolve(mark.targetId);
        if (target == null || target.isDead()) {
            clearMark();
            return;
        }
        updateMarker(mark, target);
        if (tick >= mark.triggerTick) {
            dealFixedDamage(target, BASE_DAMAGE + mark.rawDamage * STORED_DAMAGE_RATIO);
            clearMark();
        }
    }

    private Mark createMark(LivingEntity target) {
        ItemDisplay display = target.getWorld().spawn(markerLocation(target), ItemDisplay.class, itemDisplay -> {
            itemDisplay.setItemStack(createRedTotem());
            itemDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            itemDisplay.setBillboard(Display.Billboard.CENTER);
            itemDisplay.setViewRange(32f);
            itemDisplay.setInterpolationDuration(2);
            itemDisplay.setTeleportDuration(2);
            itemDisplay.setBrightness(new Display.Brightness(15, 15));
            itemDisplay.setGlowing(true);
            itemDisplay.setGlowColorOverride(Color.RED);
        });
        AbilityCombat.markAbilityEntity(display);
        return new Mark(target.getUniqueId(), com.abilitycombat.ability.AbilityTickManager.getGlobalTick() + MARK_TICKS,
                display);
    }

    private ItemStack createRedTotem() {
        ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
        item.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(Color.fromRGB(0xff2020)));
        item.setData(DataComponentTypes.CUSTOM_NAME, Component.text("처형 표식", NamedTextColor.RED));
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return item;
    }

    private void updateMarker(Mark mark, LivingEntity target) {
        if (mark.marker != null && mark.marker.isValid()) {
            mark.marker.teleport(markerLocation(target));
        }
    }

    private Location markerLocation(LivingEntity target) {
        return target.getLocation().clone().add(0, target.getHeight() + 0.18, 0);
    }

    private void dealFixedDamage(LivingEntity target, double amount) {
        if (amount <= 0.0 || target.isDead()) {
            return;
        }
        double nextHealth = target.getHealth() - amount;
        target.setHealth(nextHealth <= 0.0 ? 0.0 : nextHealth);
    }

    private LivingEntity resolve(UUID id) {
        org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(id);
        return entity instanceof LivingEntity living ? living : null;
    }

    private void clearMark() {
        if (mark != null && mark.marker != null && mark.marker.isValid()) {
            mark.marker.remove();
        }
        mark = null;
    }

    private static final class Mark {
        private final UUID targetId;
        private final int triggerTick;
        private final ItemDisplay marker;
        private double rawDamage;

        private Mark(UUID targetId, int triggerTick, ItemDisplay marker) {
            this.targetId = targetId;
            this.triggerTick = triggerTick;
            this.marker = marker;
        }
    }
}
