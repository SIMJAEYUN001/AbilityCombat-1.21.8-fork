package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.game.Participant;
import com.abilitycombat.ui.SprintHudService;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;

@AbilityManifest(name = "무법자의 투지 (OutlawsGrit)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 내구력]",
        "§7대시를 사용할 때마다 §f내구력 1스택§7을 얻습니다",
        "§7최대 §f4스택§7까지 쌓이고, 스택당 받는 피해가 §b12% 감소§7합니다",
        "§7내구력 스택과 유지시간은 보스바로 표시됩니다",
        "§75초§7 이상 대시를 사용하지 않으면 내구력 스택이 초기화됩니다"
}, summarize = {
        "§7패시브§f: 대시마다 내구력 +1",
        "§7내구력§f: 최대 4스택, 스택당 피해 12% 감소",
        "§7보스바§f: 스택 유지 잔여시간 표시"
})
public class OutlawsGrit extends AbilityBase implements SprintHudService.DashListener {

    private static final int MAX_STACKS = 4;
    private static final double REDUCTION_PER_STACK = 0.12;
    private static final int RESET_TICKS = 100;
    private static final int GAUGE_PRIORITY = 7;

    private final BossBarGauge stackGauge = new BossBarGauge("grit", GAUGE_PRIORITY, BossBar.Color.YELLOW,
            BossBar.Overlay.NOTCHED_10);
    private int stacks;
    private int lastDashTick;

    public OutlawsGrit(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        SprintHudService sprintHudService = getSprintHudService();
        if (sprintHudService != null) {
            sprintHudService.addDashListener(this);
        }
        subscribeEvent(EntityDamageEvent.class);
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        SprintHudService sprintHudService = getSprintHudService();
        if (sprintHudService != null) {
            sprintHudService.removeDashListener(this);
        }
        unregisterTick();
        stacks = 0;
        stackGauge.clear();
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (!(event instanceof EntityDamageEvent damageEvent) || damageEvent.isCancelled()) {
            return;
        }
        if (stacks <= 0 || !damageEvent.getEntity().equals(getPlayer())) {
            return;
        }
        scaleIncomingDamage(damageEvent, Math.max(0.0, 1.0 - stacks * REDUCTION_PER_STACK));
    }

    @Override
    public void onDashStart(Player player) {
        if (!isOwner(player)) {
            return;
        }
        stacks = Math.min(MAX_STACKS, stacks + 1);
        lastDashTick = AbilityTickManager.getGlobalTick();
        updateGauge(AbilityTickManager.getGlobalTick());
    }

    @Override
    public void onDashTick(Player player, org.bukkit.Location location) {
    }

    @Override
    public void onDashEnd(Player player) {
    }

    @Override
    public void onTick(int tick) {
        if (stacks > 0 && tick - lastDashTick >= RESET_TICKS) {
            stacks = 0;
            stackGauge.clear();
            return;
        }
        if (stacks > 0 && tick % 5 == 0) {
            updateGauge(tick);
        }
    }

    private void updateGauge(int tick) {
        int remainingTicks = Math.max(0, RESET_TICKS - (tick - lastDashTick));
        double remainingRatio = remainingTicks / (double) RESET_TICKS;
        int reduction = (int) Math.round(stacks * REDUCTION_PER_STACK * 100.0);
        Component title = Component.text("내구력 ", NamedTextColor.YELLOW)
                .append(Component.text(stacks + "/" + MAX_STACKS, NamedTextColor.WHITE))
                .append(Component.text(" 피해 감소 " + reduction + "%", NamedTextColor.AQUA));
        stackGauge.update(title, remainingRatio);
    }

    private boolean isOwner(Player player) {
        Player owner = getPlayer();
        return owner != null && player != null && owner.getUniqueId().equals(player.getUniqueId());
    }

    private SprintHudService getSprintHudService() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin == null ? null : plugin.getSprintHudService();
    }
}
