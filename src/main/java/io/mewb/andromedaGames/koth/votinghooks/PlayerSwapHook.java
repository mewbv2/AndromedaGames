package io.mewb.andromedaGames.koth.votinghooks;

import io.mewb.andromedaGames.game.GameInstance;
import io.mewb.andromedaGames.koth.KoTHGame; // KoTH specific, but could be made generic
import io.mewb.andromedaGames.utils.ParticleUtil;
import io.mewb.andromedaGames.voting.VotingHook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlayerSwapHook implements VotingHook {

    @Override
    public String getId() {
        return "koth_player_swap"; // Could be generic "player_swap" if used by other game modes
    }

    @Override
    public String getDisplayName() {
        return "Player Scramble!";
    }

    @Override
    public String getDescription() {
        return "Randomly swaps the locations of two players!";
    }

    @Override
    public int getDurationSeconds() {
        return 0; // Instantaneous
    }

    @Override
    public boolean canApply(GameInstance game) {
        // This hook is generic enough that it doesn't strictly need to be a KoTHGame,
        // as long as the game instance has at least 2 players.
        return game.getPlayerCount() >= 2;
    }

    @Override
    public void apply(GameInstance game, List<Player> voters) {
        // No specific KoTHGame cast needed if we only use GameInstance methods like getPlayersInGame()
        if (game.getPlayerCount() < 2) {
            game.broadcastToGamePlayers(ChatColor.YELLOW + getDisplayName() + " failed: Not enough players to scramble!");
            game.plugin.getLogger().info(getId() + " for instance " + game.getInstanceId().toString().substring(0,8) + " failed: player count < 2.");
            return;
        }

        game.broadcastToGamePlayers(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + getDisplayName() + ChatColor.YELLOW + " Positions shuffling!");

        List<Player> onlinePlayersInGame = game.getPlayersInGame().stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline())
                .collect(Collectors.toList());

        if (onlinePlayersInGame.size() < 2) {
            game.broadcastToGamePlayers(ChatColor.YELLOW + getDisplayName() + " failed: Not enough online players to scramble!");
            game.plugin.getLogger().info(getId() + " for instance " + game.getInstanceId().toString().substring(0,8) + " failed: online player count < 2.");
            return;
        }

        Collections.shuffle(onlinePlayersInGame);
        Player player1 = onlinePlayersInGame.get(0);
        Player player2 = onlinePlayersInGame.get(1);

        Location loc1 = player1.getLocation().clone();
        Location loc2 = player2.getLocation().clone();

        // Effects at original locations
        ParticleUtil.spawnLocationEffect(loc1, Particle.PORTAL, 50, 0.5, 1, 0.5, 0.2);
        if (loc1.getWorld() != null) loc1.getWorld().playSound(loc1, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 0.8f);

        ParticleUtil.spawnLocationEffect(loc2, Particle.PORTAL, 50, 0.5, 1, 0.5, 0.2);
        if (loc2.getWorld() != null) loc2.getWorld().playSound(loc2, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 0.8f);


        player1.teleport(loc2);
        player2.teleport(loc1);

        // Effects at new locations
        Location newLoc1 = player1.getLocation(); // Get new location after teleport
        ParticleUtil.spawnLocationEffect(newLoc1, Particle.WITCH, 40, 0.5, 1, 0.5, 0.1);
        if (newLoc1.getWorld() != null) newLoc1.getWorld().playSound(newLoc1, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1.2f);

        Location newLoc2 = player2.getLocation(); // Get new location after teleport
        ParticleUtil.spawnLocationEffect(newLoc2, Particle.WITCH, 40, 0.5, 1, 0.5, 0.1);
        if (newLoc2.getWorld() != null) newLoc2.getWorld().playSound(newLoc2, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1.2f);


        game.broadcastToGamePlayers(ChatColor.YELLOW + player1.getName() + " and " + player2.getName() + " have been scrambled!");
        game.plugin.getLogger().info(getId() + " applied to instance " + game.getInstanceId().toString().substring(0,8) + ": Swapped " + player1.getName() + " with " + player2.getName());
    }
}
