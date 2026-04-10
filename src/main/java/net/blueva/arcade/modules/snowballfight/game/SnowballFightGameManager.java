package net.blueva.arcade.modules.snowballfight.game;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GameResult;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.visuals.VisualEffectsAPI;
import net.blueva.arcade.modules.snowballfight.support.SnowballFightLoadoutService;
import net.blueva.arcade.modules.snowballfight.support.SnowballFightMessagingService;
import net.blueva.arcade.modules.snowballfight.support.SnowballFightStatsService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SnowballFightGameManager {

    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final SnowballFightStatsService statsService;
    private final SnowballFightLoadoutService loadoutService;
    private final SnowballFightMessagingService messagingService;

    private final Map<Integer, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity>> activeGames = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerArenas = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> gameEnded = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> arenaWinners = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> snowballTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerKills = new ConcurrentHashMap<>();

    public SnowballFightGameManager(ModuleInfo moduleInfo,
                                    ModuleConfigAPI moduleConfig,
                                    CoreConfigAPI coreConfig,
                                    SnowballFightStatsService statsService,
                                    SnowballFightLoadoutService loadoutService,
                                    SnowballFightMessagingService messagingService) {
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.statsService = statsService;
        this.loadoutService = loadoutService;
        this.messagingService = messagingService;
    }

    public void handleStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        activeGames.put(arenaId, context);
        gameEnded.put(arenaId, false);
        arenaWinners.remove(arenaId);
        snowballTicks.put(arenaId, 0);

        for (Player player : context.getPlayers()) {
            playerArenas.put(player, arenaId);
            playerKills.put(player.getUniqueId(), 0);
        }

        messagingService.sendDescription(context, getWinMode(context));
    }

    public void handleCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, int secondsLeft) {
        messagingService.sendCountdownTick(context, coreConfig, moduleInfo, secondsLeft);
    }

    public void handleCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        messagingService.sendCountdownFinish(context, coreConfig, moduleInfo);
    }

    public void handleGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        startGameTimer(context);

        for (Player player : context.getPlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
            loadoutService.giveStartingItems(player);
            loadoutService.applyStartingEffects(player);
            loadoutService.applyRespawnEffects(player);
            context.getScoreboardAPI().showScoreboard(player, getScoreboardPath(context));
        }
    }

    private void startGameTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        Integer gameTime = context.getDataAccess().getGameData("basic.time", Integer.class);
        if (gameTime == null || gameTime == 0) {
            gameTime = 180;
        }

        final int[] timeLeft = {gameTime};

        String taskId = "arena_" + arenaId + "_snowball_fight_timer";

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (gameEnded.getOrDefault(arenaId, false)) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            timeLeft[0]--;

            List<Player> alivePlayers = context.getAlivePlayers();
            List<Player> allPlayers = context.getPlayers();

            if (alivePlayers.size() <= 1 || timeLeft[0] <= 0) {
                endGameOnce(context);
                return;
            }

            giveTimedItems(context);

            String actionBarTemplate = coreConfig.getLanguage("action_bar.in_game.global");
            for (Player player : allPlayers) {
                if (!player.isOnline()) continue;

                Map<String, String> customPlaceholders = getCustomPlaceholders(player);
                customPlaceholders.put("time", String.valueOf(timeLeft[0]));
                customPlaceholders.put("alive", String.valueOf(alivePlayers.size()));
                customPlaceholders.put("spectators", String.valueOf(context.getSpectators().size()));

                if (actionBarTemplate != null) {
                    String actionBarMessage = actionBarTemplate
                            .replace("{time}", String.valueOf(timeLeft[0]))
                            .replace("{round}", String.valueOf(context.getCurrentRound()))
                            .replace("{round_max}", String.valueOf(context.getMaxRounds()));
                    context.getMessagesAPI().sendActionBar(player, actionBarMessage);
                }

                context.getScoreboardAPI().update(player, getScoreboardPath(context), customPlaceholders);
            }
        }, 0L, 20L);
    }

    private void endGameOnce(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        Boolean wasEnded = gameEnded.put(arenaId, true);
        if (wasEnded != null && wasEnded) {
            return;
        }

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        List<Player> alivePlayers = new ArrayList<>(context.getAlivePlayers());
        String winMode = getWinMode(context);
        if (alivePlayers.size() == 1 && "last_standing".equals(winMode)) {
            context.setWinner(alivePlayers.getFirst());
            handleWin(alivePlayers.getFirst());
        }

        if ("most_kills".equals(winMode)) {
            handleMostKillsOutcome(context);
        }

        context.endGame();
    }

    public void handleEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        activeGames.remove(arenaId);
        gameEnded.remove(arenaId);
        arenaWinners.remove(arenaId);
        snowballTicks.remove(arenaId);

        if (statsService.hasStatsApi()) {
            for (Player player : context.getPlayers()) {
                statsService.recordGamePlayed(player);
            }
        }

        for (Player player : context.getPlayers()) {
            playerKills.remove(player.getUniqueId());
        }

        playerArenas.entrySet().removeIf(entry -> entry.getValue().equals(arenaId));
    }

    public void handleDisable() {
        if (!activeGames.isEmpty()) {
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> anyContext = activeGames.values().iterator().next();
            anyContext.getSchedulerAPI().cancelModuleTasks(moduleInfo.getId());
        }

        activeGames.clear();
        playerArenas.clear();
        gameEnded.clear();
        snowballTicks.clear();
        playerKills.clear();
    }

    public void handleWin(Player player) {
        if (!statsService.hasStatsApi()) {
            return;
        }

        Integer arenaId = playerArenas.get(player);
        if (arenaId == null) {
            return;
        }

        if (!arenaWinners.containsKey(arenaId)) {
            arenaWinners.put(arenaId, player.getUniqueId());
            statsService.recordWin(player);
        }
    }

    public Map<String, String> getCustomPlaceholders(Player player) {
        Map<String, String> placeholders = new HashMap<>();

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context != null) {
            placeholders.put("alive", String.valueOf(context.getAlivePlayers().size()));
            placeholders.put("spectators", String.valueOf(context.getSpectators().size()));
            placeholders.put("kills", String.valueOf(getPlayerKills(context, player)));
            placeholders.put("mode", getModeLabel(getWinMode(context)));
            if ("most_kills".equals(getWinMode(context))) {
                List<Player> topPlayers = getTopPlayersByKills(context);
                for (int i = 0; i < 5; i++) {
                    String placeKey = "place_" + (i + 1);
                    String killsKey = "kills_" + (i + 1);
                    if (topPlayers.size() > i) {
                        Player topPlayer = topPlayers.get(i);
                        placeholders.put(placeKey, topPlayer.getName());
                        placeholders.put(killsKey, String.valueOf(getPlayerKills(context, topPlayer)));
                    } else {
                        placeholders.put(placeKey, "-");
                        placeholders.put(killsKey, "0");
                    }
                }
            }
        }

        return placeholders;
    }

    public void handlePlayerElimination(Player player, Player killer, boolean killedBySnowball) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context == null) {
            return;
        }

        // Don't eliminate spectators
        if (context.getSpectators().contains(player)) {
            return;
        }

        Location deathLocation = player.getLocation();
        playVisualEffects(player, killer, deathLocation);

        String winMode = getWinMode(context);
        if ("most_kills".equals(winMode)) {
            player.setGameMode(GameMode.SPECTATOR);
            player.getInventory().clear();
            if (killedBySnowball) {
                messagingService.sendDeathTitle(context, player);
            }
            messagingService.broadcastDeathMessage(context, player, killer);

            int respawnDelayTicks = Math.max(0, moduleConfig.getInt("respawn.most_kills_delay_ticks", 60));
            int arenaId = context.getArenaId();
            context.getSchedulerAPI().runLater(
                    "snowball_fight_respawn_" + arenaId + "_" + player.getUniqueId(),
                    () -> {
                        if (gameEnded.getOrDefault(arenaId, false) || !context.isPlayerPlaying(player)) {
                            return;
                        }
                        context.respawnPlayer(player);
                        player.setGameMode(GameMode.SURVIVAL);
                        loadoutService.giveStartingItems(player);
                        loadoutService.applyStartingEffects(player);
                        loadoutService.applyRespawnEffects(player);
                        messagingService.playRespawnSound(context, player, coreConfig);
                    },
                    respawnDelayTicks
            );
            return;
        }

        messagingService.broadcastDeathMessage(context, player, killer);
        context.eliminatePlayer(player, moduleConfig.getStringFrom("language.yml", "messages.eliminated"));
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        if (killedBySnowball) {
            messagingService.playDeathSound(context, player, coreConfig);
            messagingService.sendDeathTitle(context, player);
        } else {
            messagingService.playClassifiedSound(context, player, coreConfig);
            messagingService.sendClassifiedTitle(context, player);
        }
    }

    private void playVisualEffects(Player target, Player killer, Location deathLocation) {
        VisualEffectsAPI visualEffectsAPI = ModuleAPI.getVisualEffectsAPI();
        if (visualEffectsAPI == null) {
            return;
        }
        if (deathLocation != null) {
            visualEffectsAPI.playDeathEffect(target, deathLocation);
        } else {
            visualEffectsAPI.playDeathEffect(target);
        }
        if (killer != null) {
            visualEffectsAPI.playKillEffect(killer);
        }
    }

    public void handleSnowballThrown(Player player) {
        statsService.recordSnowballThrown(player);
    }

    public void handleSnowballHit(Player player) {
        statsService.recordSnowballHit(player);
    }

    public void handleKillCredit(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player killer) {
        if (context == null || killer == null) {
            return;
        }

        statsService.recordSnowballKill(killer);
        playerKills.merge(killer.getUniqueId(), 1, Integer::sum);
    }

    public String getWinMode(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        String mode = context.getDataAccess().getGameData("basic.win_mode", String.class);
        if (mode == null) {
            return "last_standing";
        }
        mode = mode.toLowerCase();
        if (!mode.equals("last_standing") && !mode.equals("most_kills")) {
            return "last_standing";
        }
        return mode;
    }

    public String getModeLabel(String mode) {
        if ("most_kills".equals(mode)) {
            return moduleConfig.getStringFrom("language.yml", "scoreboard.mode_labels.most_kills");
        }
        return moduleConfig.getStringFrom("language.yml", "scoreboard.mode_labels.last_standing");
    }

    public Player getTopKiller(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<Player> topPlayers = getTopPlayersByKills(context);
        if (topPlayers.isEmpty()) {
            return null;
        }

        return topPlayers.getFirst();
    }

    private void handleMostKillsOutcome(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<Player> topPlayers = getTopPlayersByKills(context);
        if (topPlayers.isEmpty()) {
            return;
        }

        Player winner = topPlayers.getFirst();
        context.setWinner(winner);
        handleWin(winner);

        for (int i = 1; i < topPlayers.size(); i++) {
            Player player = topPlayers.get(i);
            if (!context.isPlayerPlaying(player)) {
                continue;
            }
            context.finishPlayer(player);
        }
    }

    private List<Player> getTopPlayersByKills(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        Map<Player, Integer> killCounts = new HashMap<>();
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }
            killCounts.put(player, getPlayerKills(context, player));
        }

        List<Map.Entry<Player, Integer>> sorted = new ArrayList<>(killCounts.entrySet());
        sorted.sort((a, b) -> {
            int compare = Integer.compare(b.getValue(), a.getValue());
            if (compare != 0) {
                return compare;
            }
            return a.getKey().getName().compareToIgnoreCase(b.getKey().getName());
        });

        List<Player> topPlayers = new ArrayList<>();
        for (Map.Entry<Player, Integer> entry : sorted) {
            topPlayers.add(entry.getKey());
            if (topPlayers.size() >= 5) {
                break;
            }
        }

        return topPlayers;
    }

    public int getPlayerKills(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) {
        return playerKills.getOrDefault(player.getUniqueId(), 0);
    }

    private void giveTimedItems(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();
        int interval = moduleConfig.getInt("items.snowball_interval_ticks", 20);
        int amount = moduleConfig.getInt("items.snowballs_per_interval", 4);

        if (interval <= 0 || amount <= 0) {
            return;
        }

        int ticks = snowballTicks.merge(arenaId, 20, Integer::sum);
        if (ticks % interval != 0) {
            return;
        }

        for (Player player : context.getPlayers()) {
            if (!context.isPlayerPlaying(player)) {
                continue;
            }
            player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.SNOWBALL, amount));
        }
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getGameContext(Player player) {
        Integer arenaId = playerArenas.get(player);
        if (arenaId == null) {
            return null;
        }
        return activeGames.get(arenaId);
    }

    private String getScoreboardPath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        return "scoreboard." + getWinMode(context);
    }

    public Material getDeathBlock(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        try {
            String deathBlockName = context.getDataAccess().getGameData("basic.death_block", String.class);
            if (deathBlockName != null) {
                return Material.valueOf(deathBlockName.toUpperCase());
            }
        } catch (Exception e) {
            // fallback below
        }
        return Material.BARRIER;
    }

    public SnowballFightLoadoutService getLoadoutService() {
        return loadoutService;
    }

    public SnowballFightMessagingService getMessagingService() {
        return messagingService;
    }

    public CoreConfigAPI getCoreConfig() {
        return coreConfig;
    }
}
