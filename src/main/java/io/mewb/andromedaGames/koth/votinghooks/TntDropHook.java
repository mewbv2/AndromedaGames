package io.mewb.andromedaGames.koth.votinghooks;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.game.Game;
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
    public void apply(Game game, List<Player> voters) {
        if (!(game instanceof KoTHGame)) {
            game.plugin.getLogger().warning(getId() + " can only be applied to KoTHGame instances.");
            return;
        }
        KoTHGame kothGame = (KoTHGame) game;
        kothGame.broadcastToGamePlayers(ChatColor.RED + "" + ChatColor.BOLD + "INCOMING! " + getDisplayName());

        Location hillCenter = kothGame.getHillCenter(); // Assuming KoTHGame has getHillCenter()
        if (hillCenter == null || hillCenter.getWorld() == null) {
            game.plugin.getLogger().warning("Cannot apply TNT Drop: Hill center or world is not defined for game " + game.getGameId());
            return;
        }
        World world = hillCenter.getWorld();
        int dropRadius = kothGame.getHillRadius() + 10; // Drop TNT in a wider area around the hill
        int dropHeight = hillCenter.getBlockY() + 20; // Drop from above
        int tntCount = 10 + random.nextInt(11); // 10-20 TNT blocks

        new BukkitRunnable() {
            int tntDropped = 0;
            @Override
            public void run() {
                if (kothGame.getGameState() != GameState.ACTIVE || tntDropped >= tntCount) {
                    this.cancel();
                    return;
                }

                double angle = random.nextDouble() * 2 * Math.PI;
                double x = hillCenter.getX() + (random.nextDouble() * dropRadius * Math.cos(angle));
                double z = hillCenter.getZ() + (random.nextDouble() * dropRadius * Math.sin(angle));
                Location dropLocation = new Location(world, x, dropHeight, z);

                // Ensure TNT doesn't spawn inside blocks directly if possible (simple check)
                if (dropLocation.getBlock().getType() == Material.AIR) {
                    TNTPrimed tnt = (TNTPrimed) world.spawnEntity(dropLocation, EntityType.TNT);
                    tnt.setFuseTicks(40 + random.nextInt(41)); // 2-4 seconds fuse
                    tntDropped++;
                }
            }
        }.runTaskTimer(game.plugin, 0L, 20L); // Drop one TNT every second for tntCount seconds
    }
}
