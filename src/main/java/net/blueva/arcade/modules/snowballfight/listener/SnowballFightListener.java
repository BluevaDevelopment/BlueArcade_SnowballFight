package net.blueva.arcade.modules.snowballfight.listener;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.visuals.VisualEffectsAPI;
import net.blueva.arcade.modules.snowballfight.game.SnowballFightGameManager;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class SnowballFightListener implements Listener {

    private final SnowballFightGameManager gameManager;

    public SnowballFightListener(SnowballFightGameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GameContext context = gameManager.getGameContext(player);

        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            if (!context.isInsideBounds(event.getTo())) {
                respawnPlayer(context, player);
            }
            return;
        }

        if (!context.isInsideBounds(event.getTo())) {
            respawnPlayer(context, player);
            return;
        }

        Material deathBlock = gameManager.getDeathBlock(context);
        Material blockBelowType = event.getTo().clone().subtract(0, 1, 0).getBlock().getType();
        if (blockBelowType == deathBlock) {
            respawnPlayer(context, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        GameContext context = gameManager.getGameContext(player);

        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof Snowball)) {
            return;
        }

        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }

        GameContext context = gameManager.getGameContext(shooter);
        if (context == null || context.getPhase() != GamePhase.PLAYING || !context.isPlayerPlaying(shooter)) {
            return;
        }

        gameManager.handleSnowballThrown(shooter);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();

        if (!(projectile instanceof Snowball)) {
            return;
        }

        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }

        GameContext context = gameManager.getGameContext(shooter);
        if (context == null || context.getPhase() != GamePhase.PLAYING || !context.isPlayerPlaying(shooter)) {
            return;
        }

        Entity hit = event.getHitEntity();
        if (!(hit instanceof Player target)) {
            return;
        }

        if (!context.isPlayerPlaying(target) || target.equals(shooter)) {
            return;
        }

        gameManager.handleSnowballHit(shooter);
    }

    @EventHandler
    public void onSnowballDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        Entity damager = event.getDamager();
        if (damager instanceof Player) {
            GameContext context = gameManager.getGameContext(target);
            if (context != null && context.isPlayerPlaying(target)) {
                event.setCancelled(true);
            }
            return;
        }

        if (!(damager instanceof Snowball snowball)) {
            return;
        }

        if (!(snowball.getShooter() instanceof Player shooter)) {
            return;
        }

        GameContext context = gameManager.getGameContext(target);
        if (context == null || context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        if (!context.isPlayerPlaying(target) || !context.isPlayerPlaying(shooter) || shooter.equals(target)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        gameManager.handleSnowballHit(shooter);
        gameManager.handleKillCredit(context, shooter);
        gameManager.handlePlayerElimination(target, shooter, true);
    }

    private void respawnPlayer(GameContext context, Player player) {
        if (context.getPhase() == GamePhase.PLAYING) {
            VisualEffectsAPI visualEffectsAPI = ModuleAPI.getVisualEffectsAPI();
            if (visualEffectsAPI != null) {
                visualEffectsAPI.playDeathEffect(player, player.getLocation());
            }
        }
        context.respawnPlayer(player);
        gameManager.getLoadoutService().applyRespawnEffects(player);
        gameManager.getMessagingService().playRespawnSound(context, player, gameManager.getCoreConfig());
    }
}
