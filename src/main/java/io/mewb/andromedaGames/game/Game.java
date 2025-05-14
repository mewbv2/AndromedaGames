package io.mewb.andromedaGames.game;

import io.mewb.andromedaGames.AndromedaGames;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public abstract class Game {

    protected final AndromedaGames plugin;
    protected final String gameId; // Unique identifier for this game instance (e.g., "koth_main", "infection_lab")
    protected final String arenaId; // Identifier for the arena this game uses
    protected GameState gameState;
    protected Set<UUID> playersInGame; // Players currently participating

    public Game(AndromedaGames plugin, String gameId, String arenaId) {
        this.plugin = plugin;
        this.gameId = gameId;
        this.arenaId = arenaId;
        this.gameState = GameState.WAITING; // Default state
    }

    // --- Abstract methods to be implemented by each game type ---
    public abstract void load(); // Load configurations, arena, etc.
    public abstract void unload(); // Clean up resources
    public abstract boolean start(); // Start the game
    public abstract void stop(boolean force); // Stop the game (force for immediate shutdown)
    public abstract boolean addPlayer(Player player); // Add player to the game
    public abstract void removePlayer(Player player); // Remove player from the game
    protected abstract void gameTick(); // Called periodically if the game is active

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

    public void setGameState(GameState gameState) {
        this.plugin.getLogger().info("[" + gameId + "] Changing state from " + this.gameState + " to " + gameState);
        this.gameState = gameState;
        // Potentially broadcast state changes or handle logic based on state change
    }

    public boolean isPlayerInGame(Player player) {
        return playersInGame.contains(player.getUniqueId());
    }

    public int getPlayerCount() {
        return playersInGame.size();
    }

    // Optional: Get the world the game is primarily played in
    // This might be more complex if arenas can span worlds or are world-specific
    public abstract World getGameWorld();

    // To be called by a scheduler
    public final void tick() {
        if (gameState == GameState.ACTIVE) {
            gameTick();
        }
    }
}