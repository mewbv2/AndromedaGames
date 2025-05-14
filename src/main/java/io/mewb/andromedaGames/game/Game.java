package io.mewb.andromedaGames.game;

import io.mewb.andromedaGames.AndromedaGames;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collections; // Added for Collections.emptySet()
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public abstract class Game {

    public final AndromedaGames plugin;
    protected final Logger logger;
    protected final String gameId;
    protected final String arenaId;
    protected GameState gameState;
    protected Set<UUID> playersInGame; // This should be initialized by subclasses

    public Game(AndromedaGames plugin, String gameId, String arenaId) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.gameId = gameId;
        this.arenaId = arenaId;
        this.gameState = GameState.WAITING;
        // playersInGame MUST be initialized in the constructor of the concrete game class
        // e.g., this.playersInGame = new HashSet<>();
    }

    // --- Abstract methods to be implemented by each specific game type ---
    public abstract void load();
    public abstract void unload();
    public abstract boolean start();
    public abstract void stop(boolean force);
    public abstract boolean addPlayer(Player player);
    public abstract void removePlayer(Player player);
    protected abstract void gameTick();

    /**
     * Broadcasts a message to all players currently participating in this game instance.
     * Each game type will implement how messages are formatted and sent.
     * @param message The message to send.
     */
    public abstract void broadcastToGamePlayers(String message);


    // --- Common methods that can be used by all game types ---
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
        if (playersInGame == null) return false; // Safety check
        return playersInGame.contains(player.getUniqueId());
    }

    public boolean isPlayerInGame(UUID playerUuid) {
        if (playersInGame == null) return false; // Safety check
        return playersInGame.contains(playerUuid);
    }

    public int getPlayerCount() {
        if (playersInGame == null) return 0; // Safety check
        return playersInGame.size();
    }

    /**
     * Gets an unmodifiable set of UUIDs for players currently in this game instance.
     * @return An unmodifiable Set of player UUIDs. Returns an empty set if playersInGame is null.
     */
    public Set<UUID> getPlayersInGame() {
        if (this.playersInGame == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(this.playersInGame);
    }


    public abstract World getGameWorld();

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
