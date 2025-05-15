package io.mewb.andromedaGames.koth.votinghooks;

import io.mewb.andromedaGames.game.Game;
import io.mewb.andromedaGames.game.GameState;
import io.mewb.andromedaGames.koth.KoTHGame;
import io.mewb.andromedaGames.utils.ParticleUtil;
import io.mewb.andromedaGames.voting.VotingHook;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class HillZoneShrinkHook implements VotingHook {

    private static final int SHRINK_DURATION_SECONDS = 20;
    private static final int MIN_RADIUS_AFTER_SHRINK = 2; // Minimum radius the hill can shrink to

    @Override
    public String getId() {
        return "koth_hill_shrink";
    }

    @Override
    public String getDisplayName() {
        return "Shrinking Hill!";
    }

    @Override
    public String getDescription() {
        return "The capture zone temporarily shrinks!";
    }

    @Override
    public int getDurationSeconds() {
        return SHRINK_DURATION_SECONDS;
    }

    @Override
    public boolean canApply(Game game) {
        if (!(game instanceof KoTHGame)) {
            return false;
        }
        KoTHGame kothGame = (KoTHGame) game;
        // Don't apply if the hill is already very small
        return kothGame.getHillRadius() > MIN_RADIUS_AFTER_SHRINK + 1; // Ensure it can shrink meaningfully
    }

    @Override
    public void apply(Game game, List<Player> voters) {
        if (!(game instanceof KoTHGame)) {
            game.plugin.getLogger().warning(getId() + " can only be applied to KoTHGame instances.");
            return;
        }
        KoTHGame kothGame = (KoTHGame) game;
        kothGame.broadcastToGamePlayers(ChatColor.RED + "" + ChatColor.BOLD + getDisplayName() + " The hill is shrinking!");

        final int originalRadius = kothGame.getHillRadius();
        int newRadius = Math.max(MIN_RADIUS_AFTER_SHRINK, originalRadius / 2); // Shrink by half, but not below min
        if (newRadius >= originalRadius) {
            kothGame.broadcastToGamePlayers(ChatColor.YELLOW + "The hill is too small to shrink further!");
            return; // Already too small or calculation didn't shrink it
        }

        // Apply visual/sound effect for shrinking
        Location hillCenter = kothGame.getHillCenter();
        if (hillCenter != null && hillCenter.getWorld() != null) {
            ParticleUtil.spawnHelixEffect(hillCenter, Particle.CRIT, originalRadius, 2, 30, 2);
            hillCenter.getWorld().playSound(hillCenter, Sound.BLOCK_CONDUIT_DEACTIVATE, SoundCategory.AMBIENT, 1f, 0.8f);
        }

        kothGame.setTemporaryHillRadius(newRadius); // KoTHGame needs this new method

        // Schedule task to revert the radius
        new BukkitRunnable() {
            @Override
            public void run() {
                if (kothGame.getGameState() == GameState.ACTIVE || kothGame.getGameState() == GameState.ENDING) { // Check if game still relevant
                    // Only revert if current radius is the shrunk one, to avoid issues if another hook changed it
                    if (kothGame.getHillRadius() == newRadius) {
                        kothGame.setTemporaryHillRadius(originalRadius); // Revert to original
                        kothGame.broadcastToGamePlayers(ChatColor.GREEN + "The hill has returned to its normal size!");
                        if (hillCenter != null && hillCenter.getWorld() != null) {
                            ParticleUtil.spawnHelixEffect(hillCenter, Particle.HAPPY_VILLAGER, newRadius, 2, 30, 2);
                            hillCenter.getWorld().playSound(hillCenter, Sound.BLOCK_CONDUIT_ACTIVATE, SoundCategory.AMBIENT, 1f, 1.2f);
                        }
                    } else {
                        game.plugin.getLogger().info("Hill radius was not reverted by " + getId() + " for " + game.getGameId() + " as it was already changed.");
                    }
                }
            }
        }.runTaskLater(game.plugin, SHRINK_DURATION_SECONDS * 20L);
    }
}
