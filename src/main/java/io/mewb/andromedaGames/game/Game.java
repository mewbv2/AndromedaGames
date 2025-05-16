package io.mewb.andromedaGames.game;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.voting.VoteManager; // Import VoteManager
import io.mewb.andromedaGames.voting.VotingHook;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public abstract class Game {

    public final AndromedaGames plugin;
    protected final Logger logger;
    protected final String gameId;
    protected final String arenaId;
    protected GameState gameState;
    protected Set<UUID> playersInGame;

    public Game(AndromedaGames plugin, String gameId, String arenaId) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.gameId = gameId;
        this.arenaId = arenaId;
        this.gameState = GameState.WAITING;
    }

    public abstract void configure(FileConfiguration config);

    // --- Abstract methods ---
    public abstract void load();
    public abstract void unload();
    public abstract boolean start(); // Standard start
    public abstract boolean start(boolean bypassMinPlayerCheck); // Overload for admin/test starts
    public abstract void stop(boolean force);
    public abstract boolean addPlayer(Player player);
    public abstract void removePlayer(Player player);
    protected abstract void gameTick();
    public abstract void broadcastToGamePlayers(String message);
    public abstract World getGameWorld();

    /**
     * Gets the VoteManager for this game, if one exists.
     * Games that do not support voting should return null.
     * @return The VoteManager instance or null.
     */
    public abstract VoteManager getVoteManager();

    /**
     * Called by VoteManager when a voting hook wins and is applied.
     * Allows the game to react to the hook being activated (e.g., update scoreboards).
     * @param hook The VotingHook that was applied.
     */
    public abstract void setActiveVotingHook(VotingHook hook);


    // --- Common methods ---
    public String getGameId() {
        return gameId;
    }
    public String getArenaId() {
        return arenaId;
    }
    public GameState getGameState() {
        return gameState;
    }

    public void setGameState(GameState newGameState) {
        if (this.gameState != newGameState) {
            this.logger.info("[" + gameId + "] State changing from " + this.gameState + " to " + newGameState);
            this.gameState = newGameState;
        }
    }

    public boolean isPlayerInGame(Player player) {
        if (playersInGame == null) return false;
        return playersInGame.contains(player.getUniqueId());
    }

    public boolean isPlayerInGame(UUID playerUuid) {
        if (playersInGame == null) return false;
        return playersInGame.contains(playerUuid);
    }

    public int getPlayerCount() {
        if (playersInGame == null) return 0;
        return playersInGame.size();
    }

    public Set<UUID> getPlayersInGame() {
        if (this.playersInGame == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(this.playersInGame);
    }

    public final void tick() {
        if (gameState == GameState.ACTIVE) {
            try {
                gameTick();
            } catch (Exception e) {
                this.logger.severe("Exception during gameTick for " + gameId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}