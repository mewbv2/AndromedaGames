package io.mewb.andromedaGames.game;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.arena.ArenaDefinition;
import io.mewb.andromedaGames.player.PlayerStateManager;
import io.mewb.andromedaGames.utils.GameScoreboard;
import io.mewb.andromedaGames.utils.RelativeLocation;
import io.mewb.andromedaGames.voting.VoteManager;
import io.mewb.andromedaGames.voting.VotingHook;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class GameInstance {
    public final AndromedaGames plugin;
    protected final Logger logger;
    protected final UUID instanceId; // Unique ID for this specific match
    protected final GameDefinition definition; // The rules and type of game being played
    protected final ArenaDefinition arena; // The arena layout being used
    protected final Location instanceBaseWorldLocation; // Where this instance's arena (0,0,0) is in the world

    protected GameState gameState;
    protected Set<UUID> playersInGame; // Players currently in this instance
    protected final PlayerStateManager playerStateManager;
    protected final Map<UUID, GameScoreboard> playerScoreboards = new HashMap<>();

    // Voting related fields - subclasses will initialize VoteManager if they support voting
    protected VoteManager voteManager;
    protected List<VotingHook> availableVotingHooks; // Populated by subclass based on definition
    protected VotingHook activeVotingHook;
    protected long activeHookEndTimeMillis;
    protected boolean votingEnabled;
    protected int voteIntervalSeconds;
    protected int voteEventDurationSeconds;
    protected long lastVoteTriggerTimeMillis;


    public GameInstance(AndromedaGames plugin, UUID instanceId, GameDefinition definition, ArenaDefinition arena, Location instanceBaseWorldLocation) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.instanceId = instanceId;
        this.definition = definition;
        this.arena = arena;
        // Ensure instanceBaseWorldLocation is not null and has a world
        if (instanceBaseWorldLocation == null || instanceBaseWorldLocation.getWorld() == null) {
            this.logger.severe("CRITICAL: instanceBaseWorldLocation or its world is null for instance " + instanceId + "! This instance may not function correctly.");
            // Assign a dummy location to prevent NPEs, but this is a critical setup error.
            this.instanceBaseWorldLocation = new Location(plugin.getServer().getWorlds().get(0), 0, 0, 0);
        } else {
            this.instanceBaseWorldLocation = instanceBaseWorldLocation.clone(); // Clone for safety
        }
        this.playerStateManager = plugin.getPlayerStateManager();
        this.playersInGame = new HashSet<>();
        this.availableVotingHooks = new ArrayList<>(); // Initialize
        this.gameState = GameState.UNINITIALIZED; // Instances start uninitialized until fully set up
    }

    /**
     * Calculates an absolute world location from a relative location key defined in the ArenaDefinition.
     * @param relativeLocationKey The key for the relative location (e.g., "hill_center", "lobby_spawn").
     * @return The absolute Location, or the instanceBaseWorldLocation as a fallback if key not found.
     */
    protected Location getAbsoluteLocation(String relativeLocationKey) {
        RelativeLocation relLoc = arena.getRelativeLocation(relativeLocationKey);
        if (relLoc == null) {
            logger.warning("Relative location key '" + relativeLocationKey + "' not found in arena '" + arena.getArenaId() + "' for game instance " + instanceId + ". Using instance base as fallback.");
            return instanceBaseWorldLocation.clone(); // Return a clone of the base
        }
        Location absLoc = relLoc.toAbsolute(instanceBaseWorldLocation);
        if (absLoc == null) {
            logger.warning("Failed to convert relative location '" + relativeLocationKey + "' to absolute for instance " + instanceId + ". Base location might be invalid. Using instance base as fallback.");
            return instanceBaseWorldLocation.clone();
        }
        return absLoc;
    }

    /**
     * Calculates a list of absolute world locations from a list of relative locations
     * stored under a custom property key in the ArenaDefinition.
     * @param relativeLocationListKey The key in ArenaDefinition's customProperties that holds a List<RelativeLocation>.
     * @return A List of absolute Locations.
     */
    protected List<Location> getAbsoluteLocationList(String relativeLocationListKey) {
        List<RelativeLocation> relLocs = arena.getRelativeLocationList(relativeLocationListKey, logger, "arena:" + arena.getArenaId() + ".properties");
        List<Location> absLocs = new ArrayList<>();
        if (relLocs != null) {
            for (RelativeLocation rl : relLocs) {
                Location abs = rl.toAbsolute(instanceBaseWorldLocation);
                if (abs != null) {
                    absLocs.add(abs);
                } else {
                    logger.warning("Failed to convert one of the relative locations in list '" + relativeLocationListKey + "' to absolute for instance " + instanceId + ".");
                }
            }
        } else {
            logger.warning("Relative location list key '" + relativeLocationListKey + "' not found or not a list in arena '" + arena.getArenaId() + "' custom properties for game instance " + instanceId);
        }
        return absLocs;
    }


    // --- Abstract methods for subclasses to implement ---

    /**
     * Called after the GameInstance is constructed and its arena is (conceptually) ready.
     * Subclasses should use this to load game-specific rules from the GameDefinition,
     * calculate absolute locations for objectives/spawns based on the ArenaDefinition and
     * instanceBaseWorldLocation, and perform any other setup needed before players can join.
     * Should set gameState to WAITING if successful, or DISABLED if setup fails.
     */
    public abstract void setupInstance();

    /**
     * Called when this specific game instance is being shut down permanently.
     * Should handle any game-specific cleanup beyond what stop() does,
     * like unregistering instance-specific listeners if any were registered outside the main plugin.
     */
    public abstract void cleanupInstance();
    public abstract boolean start(boolean bypassMinPlayerCheck); // Start the game instance
    public abstract void stop(boolean force); // Stop the game instance
    public abstract boolean addPlayer(Player player); // Add player to this instance
    public abstract void removePlayer(Player player); // Remove player from this instance
    protected abstract void gameTick(); // Logic for each active game tick
    public abstract void broadcastToGamePlayers(String message); // Broadcast to players in this instance

    public World getGameWorld() { // Common implementation
        return instanceBaseWorldLocation.getWorld();
    }

    // Voting related methods - subclasses override if they support voting
    public VoteManager getVoteManager() { return this.voteManager; }
    public void setActiveVotingHook(VotingHook hook) {
        this.activeVotingHook = hook;
        if (hook != null && hook.getDurationSeconds() > 0) {
            this.activeHookEndTimeMillis = System.currentTimeMillis() + (hook.getDurationSeconds() * 1000L);
        } else {
            this.activeHookEndTimeMillis = 0;
        }
        // Subclasses should call updateAllScoreboards() if they have scoreboards
    }


    // --- Common Getters ---
    public UUID getInstanceId() { return instanceId; }
    public GameDefinition getDefinition() { return definition; }
    public ArenaDefinition getArena() { return arena; }
    public Location getInstanceBaseWorldLocation() { return instanceBaseWorldLocation.clone(); } // Return clone for safety
    public GameState getGameState() { return gameState; }
    public Set<UUID> getPlayersInGame() { return Collections.unmodifiableSet(playersInGame); }
    public int getPlayerCount() { return playersInGame.size(); }

    public void setGameState(GameState newGameState) {
        if (this.gameState != newGameState) {
            // Provide more context in logging for instances
            this.logger.info("[Instance:" + (instanceId != null ? instanceId.toString().substring(0,8) : "UNKNOWN") +
                    ", Def:" + (definition != null ? definition.getDefinitionId() : "UNKNOWN") +
                    "] State: " + this.gameState + " -> " + newGameState);
            this.gameState = newGameState;
        }
    }

    public boolean isPlayerInGame(UUID playerUuid) {
        if (playersInGame == null) return false;
        return playersInGame.contains(playerUuid);
    }

    public final void tick() { // Called by GameManager's main scheduler if this game is active
        if (gameState == GameState.ACTIVE) {
            try {
                gameTick();
            } catch (Exception e) {
                this.logger.log(Level.SEVERE, "Exception during gameTick for instance " + (instanceId != null ? instanceId.toString().substring(0,8) : "UNKNOWN"), e);
                // Consider error handling, e.g., stopping the instance if ticks consistently fail
                // this.stop(true);
                // this.setGameState(GameState.DISABLED);
            }
        }
    }
}
