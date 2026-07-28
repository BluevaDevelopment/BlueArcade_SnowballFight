package net.blueva.arcade.modules.snowballfight.support;

import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatDefinition;
import net.blueva.arcade.api.stats.StatScope;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import org.bukkit.entity.Player;

public class SnowballFightStatsService {

    private final StatsAPI statsAPI;
    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;

    public SnowballFightStatsService(StatsAPI statsAPI, ModuleInfo moduleInfo, ModuleConfigAPI moduleConfig) {
        this.statsAPI = statsAPI;
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
    }

    public void registerStats() {
        if (!hasStatsApi()) {
            return;
        }

        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("wins", moduleConfig.getTranslation(null, "stats.labels.wins"), moduleConfig.getTranslation(null, "stats.descriptions.wins"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("games_played", moduleConfig.getTranslation(null, "stats.labels.games_played"), moduleConfig.getTranslation(null, "stats.descriptions.games_played"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("snowballs_thrown", moduleConfig.getTranslation(null, "stats.labels.snowballs_thrown"), moduleConfig.getTranslation(null, "stats.descriptions.snowballs_thrown"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("snowball_kills", moduleConfig.getTranslation(null, "stats.labels.snowball_kills"), moduleConfig.getTranslation(null, "stats.descriptions.snowball_kills"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("snowball_hits", moduleConfig.getTranslation(null, "stats.labels.snowball_hits"), moduleConfig.getTranslation(null, "stats.descriptions.snowball_hits"), StatScope.MODULE));
    }

    public boolean hasStatsApi() {
        return statsAPI != null;
    }

    public void recordWin(Player player) {
        if (!hasStatsApi()) {
            return;
        }

        statsAPI.addModuleStat(player, moduleInfo.getId(), "wins", 1);
    }

    public void recordGamePlayed(Player player) {
        if (hasStatsApi()) {
            statsAPI.addModuleStat(player, moduleInfo.getId(), "games_played", 1);
        }
    }

    public void recordSnowballThrown(Player player) {
        if (hasStatsApi()) {
            statsAPI.addModuleStat(player, moduleInfo.getId(), "snowballs_thrown", 1);
        }
    }

    public void recordSnowballHit(Player player) {
        if (hasStatsApi()) {
            statsAPI.addModuleStat(player, moduleInfo.getId(), "snowball_hits", 1);
        }
    }

    public void recordSnowballKill(Player player) {
        if (hasStatsApi()) {
            statsAPI.addModuleStat(player, moduleInfo.getId(), "snowball_kills", 1);
        }
    }
}
