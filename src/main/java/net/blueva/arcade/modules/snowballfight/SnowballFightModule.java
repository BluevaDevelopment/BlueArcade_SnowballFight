package net.blueva.arcade.modules.snowballfight;

import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.achievements.AchievementsAPI;
import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.events.CustomEventRegistry;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GameModule;
import net.blueva.arcade.api.game.GameResult;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.ui.VoteMenuAPI;
import net.blueva.arcade.modules.snowballfight.game.SnowballFightGameManager;
import net.blueva.arcade.modules.snowballfight.listener.SnowballFightListener;
import net.blueva.arcade.modules.snowballfight.setup.SnowballFightSetup;
import net.blueva.arcade.modules.snowballfight.support.SnowballFightLoadoutService;
import net.blueva.arcade.modules.snowballfight.support.SnowballFightMessagingService;
import net.blueva.arcade.modules.snowballfight.support.SnowballFightStatsService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class SnowballFightModule implements GameModule<Player, Location, World, Material, ItemStack, Sound, Block, Entity, Listener, EventPriority> {

    private static final String MODULE_ID = "snowball_fight";

    private ModuleConfigAPI moduleConfig;
    private CoreConfigAPI coreConfig;
    private ModuleInfo moduleInfo;
    private SnowballFightGameManager gameManager;

    @Override
    public void onLoad() {
        moduleInfo = ModuleAPI.getModuleInfo(MODULE_ID);

        if (moduleInfo == null) {
            throw new IllegalStateException("ModuleInfo not available for snowball_fight module");
        }

        moduleConfig = ModuleAPI.getModuleConfig(moduleInfo.getId());
        coreConfig = ModuleAPI.getCoreConfig();

        StatsAPI statsAPI = ModuleAPI.getStatsAPI();
        VoteMenuAPI voteMenu = ModuleAPI.getVoteMenuAPI();
        AchievementsAPI achievementsAPI = ModuleAPI.getAchievementsAPI();

        moduleConfig.register("language.yml", 1);
        moduleConfig.register("settings.yml", 1);
        moduleConfig.register("achievements.yml", 1);

        SnowballFightStatsService statsService = new SnowballFightStatsService(statsAPI, moduleInfo, moduleConfig);
        statsService.registerStats();

        SnowballFightLoadoutService loadoutService = new SnowballFightLoadoutService(moduleConfig);
        SnowballFightMessagingService messagingService = new SnowballFightMessagingService(moduleConfig, coreConfig);

        gameManager = new SnowballFightGameManager(
                moduleInfo,
                moduleConfig,
                coreConfig,
                statsService,
                loadoutService,
                messagingService
        );

        if (achievementsAPI != null) {
            achievementsAPI.registerModuleAchievements(moduleInfo.getId(), "achievements.yml");
        }

        ModuleAPI.getSetupAPI().registerHandler(moduleInfo.getId(), new SnowballFightSetup(moduleConfig, coreConfig));

        if (moduleConfig != null && voteMenu != null) {
            voteMenu.registerGame(
                    moduleInfo.getId(),
                    Material.valueOf(moduleConfig.getString("menus.vote.item")),
                    moduleConfig.getStringFrom("language.yml", "vote_menu.name"),
                    moduleConfig.getStringListFrom("language.yml", "vote_menu.lore")
            );
        }
    }

    @Override
    public void onStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        gameManager.handleStart(context);
    }

    @Override
    public void onCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                int secondsLeft) {
        gameManager.handleCountdownTick(context, secondsLeft);
    }

    @Override
    public void onCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        gameManager.handleCountdownFinish(context);
    }

    @Override
    public boolean freezePlayersOnCountdown() {
        return false;
    }

    @Override
    public void onGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        gameManager.handleGameStart(context);
    }

    @Override
    public void onEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                      GameResult<Player> result) {
        gameManager.handleEnd(context);
    }

    @Override
    public void onDisable() {
        gameManager.handleDisable();
    }

    @Override
    public void registerEvents(CustomEventRegistry<Listener, EventPriority> registry) {
        registry.register(new SnowballFightListener(gameManager));
    }

    @Override
    public Map<String, String> getCustomPlaceholders(Player player) {
        return gameManager.getCustomPlaceholders(player);
    }
}
