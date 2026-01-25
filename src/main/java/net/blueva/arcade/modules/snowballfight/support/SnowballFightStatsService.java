package net.blueva.arcade.modules.snowballfight.support;

import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatDefinition;
import net.blueva.arcade.api.stats.StatScope;
import net.blueva.arcade.api.stats.StatsAPI;
import org.bukkit.entity.Player;

public class SnowballFightStatsService {

    private final StatsAPI statsAPI;
    private final ModuleInfo moduleInfo;

    public SnowballFightStatsService(StatsAPI statsAPI, ModuleInfo moduleInfo) {
        this.statsAPI = statsAPI;
        this.moduleInfo = moduleInfo;
    }

    public void registerStats() {
        if (!hasStatsApi()) {
            return;
        }

        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("wins", "Wins", "Snowball Fight wins", StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("games_played", "Games Played", "Snowball Fight games played", StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("snowballs_thrown", "Snowballs thrown", "Snowballs thrown in Snowball Fight", StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("snowball_kills", "Snowball kills", "Players eliminated with snowballs", StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("snowball_hits", "Snowball hits", "Snowball hits landed", StatScope.MODULE));
    }

    public boolean hasStatsApi() {
        return statsAPI != null;
    }

    public void recordWin(Player player) {
        if (!hasStatsApi()) {
            return;
        }

        statsAPI.addModuleStat(player, moduleInfo.getId(), "wins", 1);
        statsAPI.addGlobalStat(player, "wins", 1);
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
