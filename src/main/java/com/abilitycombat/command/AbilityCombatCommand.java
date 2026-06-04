package com.abilitycombat.command;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityDefinition;
import com.abilitycombat.ability.AbilityRegistry;
import com.abilitycombat.game.GameManager;
import com.abilitycombat.npc.PlayerReplica;
import com.abilitycombat.ui.SprintHudService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AbilityCombatCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_ADMIN = "abilitycombat.admin";

    private final AbilityCombat plugin;
    private final GameManager gameManager;
    private final AbilityRegistry abilityRegistry;

    public AbilityCombatCommand(AbilityCombat plugin, GameManager gameManager, AbilityRegistry abilityRegistry) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.abilityRegistry = abilityRegistry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "start" -> {
                if (!hasAdminPermission(sender)) {
                    sender.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                gameManager.startGame(sender);
                sender.sendMessage("§a게임을 시작합니다.");
                return true;
            }
            case "stop" -> {
                if (!hasAdminPermission(sender)) {
                    sender.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                gameManager.stopGame();
                sender.sendMessage("§c게임을 종료합니다.");
                return true;
            }
            case "debug" -> {
                if (!hasAdminPermission(sender)) {
                    sender.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                if (sender instanceof Player player) {
                    com.abilitycombat.game.GameState state = gameManager.getState();
                    boolean allowDuringGame = plugin.getConfig().getBoolean("game.allow-debug-during-game", false);
                    if (state != com.abilitycombat.game.GameState.IDLE && !allowDuringGame) {
                        sender.sendMessage("§c게임 진행 중에는 디버그 GUI를 열 수 없습니다.");
                        return true;
                    }
                    gameManager.openDebugGui(player);
                } else {
                    sender.sendMessage("플레이어만 사용할 수 있습니다.");
                }
                return true;
            }
            case "info" -> {
                // 일반 유저 허용
                if (sender instanceof Player player) {
                    gameManager.sendAbilityInfo(player);
                } else {
                    sender.sendMessage("플레이어만 사용할 수 있습니다.");
                }
                return true;
            }
            case "config" -> {
                if (!hasAdminPermission(sender)) {
                    sender.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                if (args.length < 2) {
                    if (sender instanceof Player player) {
                        gameManager.openConfigGui(player);
                    } else {
                        sender.sendMessage("플레이어만 사용할 수 있습니다.");
                    }
                    return true;
                }
                if (args.length >= 2) {
                    String action = args[1].toLowerCase();
                    switch (action) {
                        case "reload" -> {
                            plugin.reloadConfig();
                            abilityRegistry.load();
                            sender.sendMessage("§a설정을 다시 불러왔습니다.");
                            return true;
                        }
                        case "setspawn", "setstart" -> {
                            if (!(sender instanceof Player player)) {
                                sender.sendMessage("플레이어만 사용할 수 있습니다.");
                                return true;
                            }
                            gameManager.saveStartLocation(player.getLocation(), sender);
                            sender.sendMessage("§a게임 시작 위치를 설정했습니다.");
                            return true;
                        }
                        case "gui" -> {
                            if (sender instanceof Player player) {
                                gameManager.openConfigGui(player);
                            } else {
                                sender.sendMessage("플레이어만 사용할 수 있습니다.");
                            }
                            return true;
                        }
                        default -> {
                            sender.sendMessage("사용법: /" + label + " config | /" + label + " config reload | /" + label
                                    + " config setspawn");
                            return true;
                        }
                    }
                }
                sender.sendMessage(
                        "사용법: /" + label + " config | /" + label + " config reload | /" + label + " config setspawn");
                return true;
            }
            case "abilities", "ability" -> {
                // 일반 유저 허용 - 조회 전용 GUI
                if (sender instanceof Player player) {
                    gameManager.openDebugGui(player, 0, true);
                } else {
                    List<String> names = new ArrayList<>();
                    for (AbilityDefinition ability : abilityRegistry.getAll()) {
                        names.add(ability.getName());
                    }
                    sender.sendMessage("§e능력 목록: §f" + String.join(", ", names));
                }
                return true;
            }
            case "toolkit" -> {
                if (!hasAdminPermission(sender)) {
                    sender.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                if (sender instanceof Player player) {
                    gameManager.openToolkitGui(player);
                } else {
                    sender.sendMessage("플레이어만 사용할 수 있습니다.");
                }
                return true;
            }
            case "dummy" -> {
                if (!hasAdminPermission(sender)) {
                    sender.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("플레이어만 사용할 수 있습니다.");
                    return true;
                }
                spawnTrainingDummy(player);
                return true;
            }
            case "visible" -> {
                if (!hasAdminPermission(sender)) {
                    sender.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("사용법: /" + label + " visible <player>");
                    return true;
                }
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§c해당 플레이어를 찾을 수 없습니다.");
                    return true;
                }
                SprintHudService sprintHudService = plugin.getSprintHudService();
                if (sprintHudService == null) {
                    sender.sendMessage("§c스프린트 HUD 서비스가 비활성화되어 있습니다.");
                    return true;
                }
                sprintHudService.forceVisible(target);
                sender.sendMessage("§a" + target.getName() + "의 투명화/숨김 상태를 초기화했습니다.");
                return true;
            }
            case "repack" -> {
                if (!hasAdminPermission(sender)) {
                    sender.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("사용법: /" + label + " repack <player>");
                    return true;
                }
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§c해당 플레이어를 찾을 수 없습니다.");
                    return true;
                }
                SprintHudService sprintHudService = plugin.getSprintHudService();
                if (sprintHudService == null) {
                    sender.sendMessage("§c스프린트 HUD 서비스가 비활성화되어 있습니다.");
                    return true;
                }
                sprintHudService.resendPack(target);
                sender.sendMessage("§a" + target.getName() + "에게 스프린트 HUD 리소스팩을 다시 전송했습니다.");
                return true;
            }
            case "dropbox" -> {
                if (!hasAdminPermission(sender)) {
                    sender.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                SprintHudService sprintHudService = plugin.getSprintHudService();
                if (sprintHudService == null) {
                    sender.sendMessage("§c스프린트 HUD 서비스가 비활성화되어 있습니다.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("사용법: /" + label + " dropbox <auth|finish|sync>");
                    return true;
                }
                String action = args[1].toLowerCase();
                switch (action) {
                    case "auth" -> {
                        try {
                            String authUrl = sprintHudService.beginDropboxAuthorization(sender);
                            sender.sendMessage("§aDropbox OAuth URL을 생성했습니다.");
                            sender.sendMessage("§f" + authUrl);
                            sender.sendMessage("§7로그인 후 최종 redirect URL 또는 code를 /" + label
                                    + " dropbox finish <값> 으로 붙여넣으세요.");
                        } catch (IllegalStateException exception) {
                            sender.sendMessage("§c" + exception.getMessage());
                        }
                        return true;
                    }
                    case "finish" -> {
                        if (args.length < 3) {
                            sender.sendMessage("사용법: /" + label + " dropbox finish <redirect-url|code>");
                            return true;
                        }
                        String pastedValue = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                        sender.sendMessage("§7Dropbox 토큰 교환 및 리소스팩 업로드를 비동기로 진행합니다...");
                        sprintHudService.completeDropboxAuthorizationAsync(sender, pastedValue)
                                .whenComplete((result, throwable) -> plugin.getServer().getScheduler().runTask(plugin,
                                        () -> {
                                            if (throwable != null) {
                                                sender.sendMessage("§cDropbox OAuth 처리 실패: "
                                                        + rootMessage(throwable));
                                                return;
                                            }
                                            sender.sendMessage("§aDropbox refresh token을 저장했습니다.");
                                            if (!result.accountId().isBlank()) {
                                                sender.sendMessage("§7계정: §f" + result.accountId());
                                            }
                                            sender.sendMessage("§7리소스팩 URL: §f" + result.packUrl());
                                        }));
                        return true;
                    }
                    case "sync" -> {
                        sender.sendMessage("§7Dropbox 리소스팩 업로드를 비동기로 진행합니다...");
                        sprintHudService.publishDropboxPackAsync(true)
                                .whenComplete((packUrl, throwable) -> plugin.getServer().getScheduler().runTask(plugin,
                                        () -> {
                                            if (throwable != null) {
                                                sender.sendMessage("§cDropbox 업로드 실패: "
                                                        + rootMessage(throwable));
                                                return;
                                            }
                                            sender.sendMessage("§aDropbox 리소스팩을 갱신했습니다.");
                                            sender.sendMessage("§7리소스팩 URL: §f" + packUrl);
                                        }));
                        return true;
                    }
                    default -> {
                        sender.sendMessage("사용법: /" + label + " dropbox <auth|finish|sync>");
                        return true;
                    }
                }
            }
            case "test" -> {
                if (!hasAdminPermission(sender)) {
                    sender.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("플레이어만 사용할 수 있습니다.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("사용법: /" + label + " test <횟수>");
                    return true;
                }
                int count;
                try {
                    count = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c올바른 숫자를 입력하세요.");
                    return true;
                }
                if (count <= 0 || count > 1000) {
                    sender.sendMessage("§c1~1000 사이의 숫자를 입력하세요.");
                    return true;
                }
                runAbilityDrawTest(player, count);
                return true;
            }
            default -> {
                sendUsage(sender, label);
                return true;
            }
        }
    }

    private boolean hasAdminPermission(CommandSender sender) {
        return sender.isOp() || sender.hasPermission(PERM_ADMIN);
    }

    private void sendUsage(CommandSender sender, String label) {
        boolean isAdmin = hasAdminPermission(sender);

        sender.sendMessage("§6=== AbilityCombat 명령어 ===");

        // 관리자 전용 명령어
        if (isAdmin) {
            sender.sendMessage("§e/" + label + " start §7- 게임 시작");
            sender.sendMessage("§e/" + label + " stop §7- 게임 종료");
            sender.sendMessage("§e/" + label + " debug §7- 능력 디버그 GUI");
        }

        // 일반 유저 허용 명령어
        sender.sendMessage("§e/" + label + " info §7- 내 능력 정보");
        sender.sendMessage("§e/" + label + " abilities §7- 능력 목록");

        // 관리자 전용 명령어
        if (isAdmin) {
            sender.sendMessage("§e/" + label + " toolkit §7- 기본 지급템 설정");
            sender.sendMessage("§e/" + label + " dummy §7- 체력 20 훈련 더미 생성");
            sender.sendMessage("§e/" + label + " config §7- 게임 설정 GUI");
            sender.sendMessage("§e/" + label + " config reload §7- 설정 리로드");
            sender.sendMessage("§e/" + label + " config setspawn §7- 게임 시작 위치 지정");
            sender.sendMessage("§e/" + label + " visible <player> §7- 투명화/숨김 상태 초기화");
            sender.sendMessage("§e/" + label + " repack <player> §7- 스프린트 HUD 리소스팩 재전송");
            sender.sendMessage("§e/" + label + " dropbox <auth|finish|sync> §7- Dropbox 리소스팩 OAuth/동기화");
            sender.sendMessage("§e/" + label + " test <횟수> §7- 능력 추첨 테스트");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean isAdmin = hasAdminPermission(sender);

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            // 일반 유저 허용
            completions.add("info");
            completions.add("abilities");
            completions.add("ability");
            // 관리자 전용
            if (isAdmin) {
                completions.add("start");
                completions.add("stop");
                completions.add("debug");
                completions.add("toolkit");
                completions.add("dummy");
                completions.add("config");
                completions.add("visible");
                completions.add("repack");
                completions.add("dropbox");
                completions.add("test");
            }
            return completions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("config") && isAdmin) {
            return List.of("reload", "setspawn", "setstart", "gui");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("dropbox") && isAdmin) {
            return List.of("auth", "finish", "sync");
        }
        if (args.length == 2
                && (args[0].equalsIgnoreCase("visible") || args[0].equalsIgnoreCase("repack"))
                && isAdmin) {
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }

    private void spawnTrainingDummy(Player player) {
        if (plugin.getReplicaManager() == null) {
            player.sendMessage("§c더미 관리자가 비활성화되어 있습니다.");
            return;
        }
        Location location = player.getLocation().clone();
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 1.0E-4) {
            direction = new Vector(0, 0, 1);
        }
        location.add(direction.normalize().multiply(2.0));
        PlayerReplica dummy = plugin.getReplicaManager().createTrainingDummy(location);
        dummy.setInvulnerable(false);
        dummy.setImmovable(true);
        dummy.setGravity(true);
        dummy.setAI(false);
        dummy.customName(Component.text("훈련 더미", NamedTextColor.RED));
        dummy.setCustomNameVisible(true);
        AttributeInstance maxHealth = dummy.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0);
        }
        dummy.setHealth(20.0);
        dummy.spawn();
        player.sendMessage("§a체력 20 훈련 더미를 생성했습니다.");
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private void runAbilityDrawTest(Player player, int drawCount) {
        Map<String, Integer> stats = new HashMap<>();
        int optionsPerDraw = 3;

        player.sendMessage("§6=== 능력 추첨 테스트 시작 ===");
        player.sendMessage("§7총 " + drawCount + "회 추첨, 회당 " + optionsPerDraw + "개 능력 등장");

        for (int i = 0; i < drawCount; i++) {
            List<AbilityDefinition> options = abilityRegistry.getRandomOptions(optionsPerDraw);
            for (AbilityDefinition ability : options) {
                String name = ability.getName();
                stats.put(name, stats.getOrDefault(name, 0) + 1);
            }
        }

        // 통계 정렬 및 출력
        player.sendMessage("");
        player.sendMessage("§6=== 능력 등장 통계 ===");
        player.sendMessage("§7(총 " + (drawCount * optionsPerDraw) + "회 등장 중)");
        player.sendMessage("");

        // 등장 횟수 내림차순 정렬
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(stats.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        for (Map.Entry<String, Integer> entry : sorted) {
            String name = entry.getKey();
            int count = entry.getValue();
            double percentage = (double) count / (drawCount * optionsPerDraw) * 100;
            player.sendMessage(String.format("§e%s §7- §f%d회 §7(%.1f%%)", name, count, percentage));
        }

        player.sendMessage("");
        player.sendMessage("§6=== 테스트 완료 ===");
        player.sendMessage("§7등록된 능력 수: §f" + abilityRegistry.getAll().size());
        player.sendMessage("§7등장한 능력 수: §f" + stats.size());
    }
}
