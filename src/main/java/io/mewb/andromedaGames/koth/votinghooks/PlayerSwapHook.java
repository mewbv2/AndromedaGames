package io.mewb.andromedaGames.koth.votinghooks;

import io.mewb.andromedaGames.game.Game;
import io.mewb.andromedaGames.koth.KoTHGame;
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

public class PlayerSwapHook implements VotingHook {

    @Override
    public String getId() {
        return "koth_player_swap";
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
    public boolean canApply(Game game) {
        return game.getPlayerCount() >= 2; // Need at least two players to swap
    }

    @Override
    public void apply(Game game, List<Player> voters) {
        if (!(game instanceof KoTHGame)) {
            return;
        }
        KoTHGame kothGame = (KoTHGame) game;
        if (kothGame.getPlayerCount() < 2) {
            kothGame.broadcastToGamePlayers(ChatColor.YELLOW + "Not enough players to scramble!");
            return;
        }

        kothGame.broadcastToGamePlayers(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + getDisplayName() + " Positions shuffling!");

        List<Player> onlinePlayersInGame = new ArrayList<>();
        for (UUID uuid : kothGame.getPlayersInGame()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                onlinePlayersInGame.add(p);
            }
        }

        if (onlinePlayersInGame.size() < 2) {
            kothGame.broadcastToGamePlayers(ChatColor.YELLOW + "Not enough online players to scramble!");
            return;
        }

        Collections.shuffle(onlinePlayersInGame);
        Player player1 = onlinePlayersInGame.get(0);
        Player player2 = onlinePlayersInGame.get(1);

        Location loc1 = player1.getLocation().clone();
        Location loc2 = player2.getLocation().clone();

        // Effects at original locations
        ParticleUtil.spawnLocationEffect(loc1, Particle.PORTAL, 50, 0.5, 1, 0.5, 0.2);
        loc1.getWorld().playSound(loc1, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 0.8f);
        ParticleUtil.spawnLocationEffect(loc2, Particle.PORTAL, 50, 0.5, 1, 0.5, 0.2);
        loc2.getWorld().playSound(loc2, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 0.8f);


        player1.teleport(loc2);
        player2.teleport(loc1);

        // Effects at new locations
        ParticleUtil.spawnLocationEffect(player1.getLocation(), Particle.WITCH, 40, 0.5, 1, 0.5, 0.1);
        player1.playSound(player1.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1.2f);
        ParticleUtil.spawnLocationEffect(player2.getLocation(), Particle.WITCH, 40, 0.5, 1, 0.5, 0.1);
        player2.playSound(player2.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1.2f);


        kothGame.broadcastToGamePlayers(ChatColor.YELLOW + player1.getName() + " and " + player2.getName() + " have been scrambled!");
        game.plugin.getLogger().info("Swapped " + player1.getName() + " with " + player2.getName() + " in game " + game.getGameId());
    }
}
