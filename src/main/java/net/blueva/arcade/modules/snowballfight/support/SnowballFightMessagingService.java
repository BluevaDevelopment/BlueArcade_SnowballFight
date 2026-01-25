package net.blueva.arcade.modules.snowballfight.support;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.module.ModuleInfo;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SnowballFightMessagingService {

    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;

    public SnowballFightMessagingService(ModuleConfigAPI moduleConfig, CoreConfigAPI coreConfig) {
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
    }

    public void sendDescription(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                String winMode) {
        String descriptionKey = "description." + winMode;
        List<String> description = moduleConfig.getStringListFrom("language.yml", descriptionKey);
        if (description == null || description.isEmpty()) {
            description = moduleConfig.getStringListFrom("language.yml", "description");
        }

        for (Player player : context.getPlayers()) {
            for (String line : description) {
                context.getMessagesAPI().sendRaw(player, line);
            }
        }
    }

    public void sendCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                  CoreConfigAPI coreConfig,
                                  ModuleInfo moduleInfo,
                                  int secondsLeft) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) continue;

            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.countdown"));

            String title = coreConfig.getLanguage("titles.starting_game.title")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            String subtitle = coreConfig.getLanguage("titles.starting_game.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 5);
        }
    }

    public void sendCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    CoreConfigAPI coreConfig,
                                    ModuleInfo moduleInfo) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) continue;

            String title = coreConfig.getLanguage("titles.game_started.title")
                    .replace("{game_display_name}", moduleInfo.getName());

            String subtitle = coreConfig.getLanguage("titles.game_started.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName());

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 20);
            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.start"));
        }
    }

    public void broadcastDeathMessage(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                      Player victim,
                                      Player killer) {
        String path = killer != null ? "messages.deaths.killed_by_player" : "messages.deaths.generic";
        String message = getRandomMessage(path);

        if (message == null) {
            return;
        }

        message = message
                .replace("{victim}", victim.getName())
                .replace("{killer}", killer != null ? killer.getName() : "");

        for (Player player : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(player, message);
        }
    }

    public void sendDeathTitle(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                               Player player) {
        context.getTitlesAPI().sendRaw(player,
                moduleConfig.getStringFrom("language.yml", "titles.you_died.title"),
                moduleConfig.getStringFrom("language.yml", "titles.you_died.subtitle"),
                0, 80, 20);
    }

    public void sendClassifiedTitle(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    Player player) {
        context.getTitlesAPI().sendRaw(player,
                moduleConfig.getStringFrom("language.yml", "titles.classified.title"),
                moduleConfig.getStringFrom("language.yml", "titles.classified.subtitle"),
                0, 80, 20);
    }

    public void playRespawnSound(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 Player player,
                                 CoreConfigAPI coreConfig) {
        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.respawn"));
    }

    public void playDeathSound(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                               Player player,
                               CoreConfigAPI coreConfig) {
        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.dead"));
    }

    public void playClassifiedSound(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    Player player,
                                    CoreConfigAPI coreConfig) {
        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.classified"));
    }

    private String getRandomMessage(String path) {
        List<String> messages = moduleConfig.getStringListFrom("language.yml", path);
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        int index = ThreadLocalRandom.current().nextInt(messages.size());
        return messages.get(index);
    }
}
