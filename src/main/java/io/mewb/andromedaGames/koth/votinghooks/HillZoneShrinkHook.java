package io.mewb.andromedaGames.koth.votinghooks;

import io.mewb.andromedaGames.game.GameInstance;
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
    public boolean canApply(GameInstance game) {
        if (!(game instanceof KoTHGame)) {
            return false;
        }
        KoTHGame kothGame = (KoTHGame) game;
        // Don't apply if the hill is already very small or smaller than the intended shrink target
        return kothGame.getCurrentHillRadius() > MIN_RADIUS_AFTER_SHRINK + 1; // Ensure it can shrink meaningfully
    }

    @Override
    public void apply(GameInstance game, List<Player> voters) {
        if (!(game instanceof KoTHGame)) {
            // This should ideally not happen if canApply is checked by VoteManager
            game.plugin.getLogger().warning(getId() + " was applied to a non-KoTHGame instance: " + game.getInstanceId());
            return;
        }
        KoTHGame kothGame = (KoTHGame) game;
        kothGame.broadcastToGamePlayers(ChatColor.RED + "" + ChatColor.BOLD + getDisplayName() + ChatColor.YELLOW + " The hill is shrinking!");

        final int originalRadius = kothGame.getCurrentHillRadius(); // Use getCurrentHillRadius
        int newRadius = Math.max(MIN_RADIUS_AFTER_SHRINK, originalRadius / 2);
        if (newRadius >= originalRadius) {
            kothGame.broadcastToGamePlayers(ChatColor.YELLOW + "The hill is too small to shrink further!");
            // If it can't shrink, ensure the hook doesn't incorrectly stay "active"
            // GameInstance.setActiveVotingHook(null) might be called by VoteManager or game logic after apply finishes.
            // For now, we just log and return. The duration implies it will wear off.
            game.plugin.getLogger().info(getId() + " for instance " + game.getInstanceId().toString().substring(0,8) + " did not shrink hill further from radius " + originalRadius);
            return;
        }

        Location hillCenter = kothGame.getAbsoluteHillCenter(); // Use getAbsoluteHillCenter
        if (hillCenter != null && hillCenter.getWorld() != null) {
            ParticleUtil.spawnHelixEffect(hillCenter, Particle.CRIT, originalRadius, 2, 30, 2);
            hillCenter.getWorld().playSound(hillCenter, Sound.BLOCK_CONDUIT_DEACTIVATE, SoundCategory.AMBIENT, 1f, 0.8f);
        }

        kothGame.adminSetHillRadius(newRadius); // Use adminSetHillRadius for temporary change

        new BukkitRunnable() {
            @Override
            public void run() {
                // Check if the game instance still exists and is in a relevant state
                GameInstance currentInstance = game.plugin.getGameManager().getRunningGameInstance(game.getInstanceId()).orElse(null);
                if (currentInstance == null || !(currentInstance instanceof KoTHGame)) {
                    this.cancel();
                    return;
                }
                KoTHGame currentKothGame = (KoTHGame) currentInstance;

                if (currentKothGame.getGameState() == GameState.ACTIVE || currentKothGame.getGameState() == GameState.ENDING) {
                    if (currentKothGame.getCurrentHillRadius() == newRadius) { // Check if radius is still the shrunk one
                        currentKothGame.adminSetHillRadius(originalRadius); // Revert to original
                        currentKothGame.broadcastToGamePlayers(ChatColor.GREEN + "The hill has returned to its normal size!");
                        Location currentHillCenter = currentKothGame.getAbsoluteHillCenter();
                        if (currentHillCenter != null && currentHillCenter.getWorld() != null) {
                            ParticleUtil.spawnHelixEffect(currentHillCenter, Particle.HAPPY_VILLAGER, newRadius, 2, 30, 2); // Particles at newRadius before it visually expands
                            currentHillCenter.getWorld().playSound(currentHillCenter, Sound.BLOCK_CONDUIT_ACTIVATE, SoundCategory.AMBIENT, 1f, 1.2f);
                        }
                    } else {
                        // Log using the instanceId for clarity
                        game.plugin.getLogger().info("Hill radius for instance " + game.getInstanceId().toString().substring(0,8) +
                                " was not reverted by " + getId() + " as it was already changed from the shrunk radius (" + newRadius + ") to " + currentKothGame.getCurrentHillRadius() + ".");
                    }
                }
            }
        }.runTaskLater(game.plugin, SHRINK_DURATION_SECONDS * 20L);
    }
}