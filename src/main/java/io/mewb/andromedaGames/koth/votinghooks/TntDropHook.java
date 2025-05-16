package io.mewb.andromedaGames.koth.votinghooks;

import io.mewb.andromedaGames.game.GameInstance;
import io.mewb.andromedaGames.game.GameState;
import io.mewb.andromedaGames.koth.KoTHGame;
import io.mewb.andromedaGames.voting.VotingHook;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;
import java.util.UUID; // Added for storing instance ID in task

public class TntDropHook implements VotingHook {

    private final Random random = new Random();

    @Override
    public String getId() {
        return "koth_tnt_drop";
    }

    @Override
    public String getDisplayName() {
        return "TNT Drop!";
    }

    @Override
    public String getDescription() {
        return "TNT rains down around the hill!";
    }

    @Override
    public int getDurationSeconds() {
        return 15; // The event (TNT falling) lasts for about this long
    }

    @Override
    public boolean canApply(GameInstance game) {
        if (!(game instanceof KoTHGame)) {
            return false;
        }
        KoTHGame kothGame = (KoTHGame) game;
        // Can apply if hill center is defined
        return kothGame.getAbsoluteHillCenter() != null && kothGame.getAbsoluteHillCenter().getWorld() != null;
    }

    @Override
    public void apply(GameInstance game, List<Player> voters) {
        if (!(game instanceof KoTHGame)) {
            game.plugin.getLogger().warning(getId() + " applied to non-KoTHGame instance: " + game.getInstanceId().toString().substring(0,8));
            return;
        }
        KoTHGame kothGame = (KoTHGame) game;

        Location hillCenter = kothGame.getAbsoluteHillCenter();
        if (hillCenter == null || hillCenter.getWorld() == null) {
            game.plugin.getLogger().warning(getId() + " cannot apply for instance " + game.getInstanceId().toString().substring(0,8) + ": Hill center or world is not defined.");
            kothGame.broadcastToGamePlayers(ChatColor.RED + getDisplayName() + " failed to activate (configuration error).");
            return;
        }

        kothGame.broadcastToGamePlayers(ChatColor.RED + "" + ChatColor.BOLD + "INCOMING! " + getDisplayName());
        game.plugin.getLogger().info(getId() + " applied to instance " + game.getInstanceId().toString().substring(0,8));


        World world = hillCenter.getWorld();
        // Use current hill radius for drop zone, not original, if it can change
        int dropRadius = kothGame.getCurrentHillRadius() + 10;
        int dropHeight = hillCenter.getBlockY() + 20;
        int tntCount = 10 + random.nextInt(11); // 10-20 TNT blocks
        final UUID instanceUUID = game.getInstanceId(); // Store for use in BukkitRunnable

        new BukkitRunnable() {
            int tntDropped = 0;
            @Override
            public void run() {
                // Re-fetch the game instance to ensure it's still valid and get its current state
                GameInstance currentInstance = game.plugin.getGameManager().getRunningGameInstance(instanceUUID).orElse(null);

                if (currentInstance == null || !(currentInstance instanceof KoTHGame) ||
                        currentInstance.getGameState() != GameState.ACTIVE || tntDropped >= tntCount) {
                    this.cancel();
                    if (currentInstance == null) {
                        game.plugin.getLogger().info(getId() + " task for instance " + instanceUUID.toString().substring(0,8) + " cancelled: instance no longer exists.");
                    } else {
                        game.plugin.getLogger().info(getId() + " task for instance " + instanceUUID.toString().substring(0,8) + " finished or cancelled. Dropped: " + tntDropped + "/" + tntCount + ". State: " + currentInstance.getGameState());
                    }
                    return;
                }

                KoTHGame currentKothGame = (KoTHGame) currentInstance; // Safe cast after instanceof check
                Location currentHillCenter = currentKothGame.getAbsoluteHillCenter(); // Use current hill center
                if (currentHillCenter == null || !world.equals(currentHillCenter.getWorld())) { // Check if world changed or center became null
                    this.cancel();
                    game.plugin.getLogger().warning(getId() + " task for instance " + instanceUUID.toString().substring(0,8) + " cancelled: hill center invalid or world changed.");
                    return;
                }


                double angle = random.nextDouble() * 2 * Math.PI;
                double x = currentHillCenter.getX() + (random.nextDouble() * dropRadius * Math.cos(angle));
                double z = currentHillCenter.getZ() + (random.nextDouble() * dropRadius * Math.sin(angle));
                // Ensure Y is relative to current hill center's Y, not a fixed initial height if hill can move vertically.
                Location dropLocation = new Location(world, x, currentHillCenter.getY() + 15 + random.nextInt(10), z); // Drop from 15-25 blocks above current hill Y


                if (dropLocation.getBlock().getType() == Material.AIR) { // Simple check
                    TNTPrimed tnt = (TNTPrimed) world.spawnEntity(dropLocation, EntityType.TNT);
                    tnt.setFuseTicks(40 + random.nextInt(41)); // 2-4 seconds fuse
                    tntDropped++;
                }
            }
        }.runTaskTimer(game.plugin, 0L, 15L + random.nextInt(10)); // Drop TNT every 0.75-1.25 seconds
    }
}
