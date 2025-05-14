package io.mewb.andromedaGames.game;

import io.mewb.andromedaGames.AndromedaGames;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger; // Ensure Logger is imported

public abstract class Game {

    public final AndromedaGames plugin;
    protected final Logger logger; // Logger is declared here
    protected final String gameId;
    protected final String arenaId;
    protected GameState gameState;
    protected Set<UUID> playersInGame;

    public Game(AndromedaGames plugin, String gameId, String arenaId) {
        this.plugin = plugin;
        this.logger = plugin.getLogger(); // Logger is initialized here from the main plugin instance
        this.gameId = gameId;
        this.arenaId = arenaId;
        this.gameState = GameState.WAITING;
    }

    public abstract void load();
    public abstract void unload();
    public abstract boolean start();
    public abstract void stop(boolean force);
    public abstract boolean addPlayer(Player player);
    public abstract void removePlayer(Player player);
    protected abstract void gameTick();

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
            // Use the initialized logger instance
            this.logger.info("[" + gameId + "] State changing from " + this.gameState + " to " + newGameState);
            this.gameState = newGameState;
        }
    }

    public boolean isPlayerInGame(Player player) {
        return playersInGame.contains(player.getUniqueId());
    }

    public boolean isPlayerInGame(UUID playerUuid) {
        return playersInGame.contains(playerUuid);
    }

    public int getPlayerCount() {
        return playersInGame.size();
    }

    public abstract World getGameWorld();

    public final void tick() {
        if (gameState == GameState.ACTIVE) {
            try {
                gameTick();
            } catch (Exception e) {
                // Use the initialized logger instance
                this.logger.severe("Exception during gameTick for " + gameId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
