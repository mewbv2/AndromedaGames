package io.mewb.andromedaGames.koth.votinghooks;

import io.mewb.andromedaGames.game.GameInstance;
import io.mewb.andromedaGames.koth.KoTHGame;
import io.mewb.andromedaGames.voting.VotingHook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;

public class LowGravityHook implements VotingHook {

    @Override
    public String getId() {
        return "koth_low_gravity";
    }

    @Override
    public String getDisplayName() {
        return "Low Gravity";
    }

    @Override
    public String getDescription() {
        return "Everyone gets Jump Boost and Slow Falling!";
    }

    @Override
    public int getDurationSeconds() {
        return 30; // Effect lasts for 30 seconds
    }

    @Override
    public void apply(GameInstance game, List<Player> voters) {
        if (!(game instanceof KoTHGame)) {
            return;
        }
        KoTHGame kothGame = (KoTHGame) game;
        kothGame.broadcastToGamePlayers(ChatColor.AQUA + getDisplayName() + " activated for " + getDurationSeconds() + " seconds!");

        PotionEffect jumpBoost = new PotionEffect(PotionEffectType.JUMP_BOOST, getDurationSeconds() * 20, 3, true, false); // Jump Boost IV
        PotionEffect slowFalling = new PotionEffect(PotionEffectType.SLOW_FALLING, getDurationSeconds() * 20, 0, true, false); // Slow Falling I

        for (UUID playerUUID : kothGame.getPlayersInGame()) { // Assuming KoTHGame has getPlayersInGame() returning Set<UUID>
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                player.addPotionEffect(jumpBoost);
                player.addPotionEffect(slowFalling);
            }
        }
    }
}
