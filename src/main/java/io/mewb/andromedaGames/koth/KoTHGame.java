package io.mewb.andromedaGames.koth;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.game.GameState;
import io.mewb.andromedaGames.game.Game;
import me.lucko.helper.terminable.composite.CompositeTerminable;
import me.lucko.helper.Schedulers; // Ensure this is imported

// ... other imports ...
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
// No need for import java.util.concurrent.TimeUnit; if using Schedulers directly with ticks

public class KoTHGame extends Game {

    // ... (fields remain the same) ...
    private Location hillCenter;
    private int hillRadius;
    private int hillRadiusSquared;

    private int gameDurationSeconds;
    private int timeElapsedSeconds;
    private int minPlayersToStart;

    private final Map<UUID, Integer> playerScores;
    private CompositeTerminable gameTaskTerminable; // Manages tasks specific to this game instance

    private Location lobbySpawn;
    private Location gameWorldSpawn;
    private String worldName;


    public KoTHGame(AndromedaGames plugin, String gameId, String arenaId) {
        super(plugin, gameId, arenaId);
        this.playersInGame = new HashSet<>();
        this.playerScores = new HashMap<>();
        this.gameState = GameState.WAITING;
    }

    @Override
    public void load() {
        plugin.getLogger().info("Loading KoTH game: " + gameId + " using arena: " + arenaId);
        // ... (config loading logic) ...
        this.worldName = plugin.getConfig().getString("koth." + gameId + ".world", "world"); // Example config access
        // Ensure world is loaded if using Bukkit.getWorld(worldName) directly after.
        World gameWorld = Bukkit.getWorld(worldName);
        if (gameWorld == null) {
            plugin.getLogger().severe("World " + worldName + " not found for KoTH game " + gameId + "!");
            setGameState(GameState.DISABLED);
            return;
        }

        this.hillCenter = new Location(gameWorld, 0, 100, 0); // Placeholder - load from config
        this.hillRadius = 5; // Placeholder - load from config
        this.hillRadiusSquared = hillRadius * hillRadius;
        this.gameDurationSeconds = 300; // Placeholder
        this.minPlayersToStart = 1; // Placeholder
        this.lobbySpawn = new Location(gameWorld, 0, 64, 0); // Placeholder
        this.gameWorldSpawn = new Location(gameWorld, 10, 100, 10); // Placeholder

        setGameState(GameState.WAITING);
    }

    @Override
    public void unload() {
        plugin.getLogger().info("Unloading KoTH game: " + gameId);
        if (gameTaskTerminable != null && !gameTaskTerminable.isClosed()) {
            // Use closeAndReportException() to avoid unhandled checked exception
            // and to automatically log any errors during the closing of tasks.
            gameTaskTerminable.closeAndReportException();
            gameTaskTerminable = null; // Good practice to nullify after closing
        }
        // ArenaManager.getInstance().resetArena(arenaId);
    }

    @Override
    public boolean start() {
        if (gameState != GameState.WAITING && gameState != GameState.ENDING) {
            plugin.getLogger().warning("KoTH game " + gameId + " cannot start, current state: " + gameState);
            return false;
        }
        if (playersInGame.size() < minPlayersToStart) {
            broadcastToGamePlayers("Not enough players to start KoTH! Need " + minPlayersToStart + ", have " + playersInGame.size() + ".");
            return false;
        }

        setGameState(GameState.STARTING);
        playerScores.clear();
        playersInGame.forEach(uuid -> playerScores.put(uuid, 0));
        timeElapsedSeconds = 0;

        for (UUID uuid : playersInGame) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.teleport(gameWorldSpawn);
                player.sendMessage("The KoTH game '" + gameId + "' is starting!");
            }
        }

        plugin.getLogger().info("KoTH game " + gameId + " is starting.");
        setGameState(GameState.ACTIVE);

        // Ensure previous terminable is closed if any lingering (shouldn't happen if flow is correct)
        if (this.gameTaskTerminable != null && !this.gameTaskTerminable.isClosed()) {
            this.gameTaskTerminable.closeAndReportException();
        }
        this.gameTaskTerminable = CompositeTerminable.create(); // Create a new one for this game session

        Schedulers.sync().runRepeating(() -> {
                    if (gameState == GameState.ACTIVE) {
                        gameTick();
                    }
                }, 1L, 20L) // Run every second (20 ticks)
                .bindWith(this.gameTaskTerminable); // Auto-cancels when gameTaskTerminable is closed

        return true;
    }

    @Override
    public void stop(boolean force) {
        if (gameState != GameState.ACTIVE && gameState != GameState.STARTING && !force) {
            plugin.getLogger().warning("KoTH game " + gameId + " is not active, cannot stop normally.");
            // If forced, we might still want to attempt cleanup.
            if (!force) return;
        }
        plugin.getLogger().info("KoTH game " + gameId + " is stopping. Forced: " + force);

        // Important: Change state before closing tasks that might depend on the state.
        // However, if tasks need to run one last time (e.g. final score calculation), order might differ.
        // For now, setting state to ENDING first.
        setGameState(GameState.ENDING);

        if (this.gameTaskTerminable != null && !this.gameTaskTerminable.isClosed()) {
            // Use closeAndReportException() here as well.
            this.gameTaskTerminable.closeAndReportException();
            this.gameTaskTerminable = null; // Nullify after closing
        }

        // Announce winner
        UUID winner = null;
        int maxScore = -1;
        // ... (winner announcement logic remains the same) ...
        for (Map.Entry<UUID, Integer> entry : playerScores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                winner = entry.getKey();
            }
        }

        if (winner != null) {
            Player winnerPlayer = Bukkit.getPlayer(winner);
            String winnerName = (winnerPlayer != null) ? winnerPlayer.getName() : "An unknown player";
            broadcastToGamePlayers(winnerName + " has won KoTH '" + gameId + "' with " + maxScore + " seconds on the hill!");
        } else if (!playersInGame.isEmpty()){ // Only say no winner if there were players
            broadcastToGamePlayers("KoTH game '" + gameId + "' ended. No winner could be determined.");
        } else {
            broadcastToGamePlayers("KoTH game '" + gameId + "' ended as there were no players.");
        }


        for (UUID uuid : new HashSet<>(playersInGame)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.teleport(lobbySpawn);
            }
        }
        // playersInGame.clear(); // Game manager might handle this or a separate reset method
        // playerScores.clear();

        setGameState(GameState.WAITING); // Or RESETTING then WAITING
    }

    // ... (addPlayer, removePlayer, gameTick, isPlayerOnHill, broadcastToGamePlayers, admin commands, getGameWorld remain largely the same) ...

    @Override
    public boolean addPlayer(Player player) {
        if (gameState != GameState.WAITING && gameState != GameState.STARTING) {
            player.sendMessage("KoTH game '" + gameId + "' has already started or is ending.");
            return false;
        }
        if (playersInGame.contains(player.getUniqueId())) {
            player.sendMessage("You are already in this KoTH game.");
            return false;
        }

        playersInGame.add(player.getUniqueId());
        playerScores.put(player.getUniqueId(), 0);
        player.teleport(lobbySpawn);
        player.sendMessage("You have joined KoTH game: " + gameId);
        broadcastToGamePlayers(player.getName() + " has joined the KoTH game! (" + playersInGame.size() + " players)");
        return true;
    }

    @Override
    public void removePlayer(Player player) {
        boolean wasInGame = playersInGame.remove(player.getUniqueId());
        playerScores.remove(player.getUniqueId());

        if (wasInGame) {
            player.sendMessage("You have left KoTH game: " + gameId);
            broadcastToGamePlayers(player.getName() + " has left the KoTH game.");

            if (gameState == GameState.ACTIVE && playersInGame.size() < minPlayersToStart && minPlayersToStart > 1) {
                broadcastToGamePlayers("Not enough players to continue. Game ending.");
                stop(false);
            } else if ((gameState == GameState.ACTIVE || gameState == GameState.STARTING) && playersInGame.isEmpty()) {
                plugin.getLogger().info("Last player left KoTH game " + gameId + ". Stopping.");
                stop(false); // Stop the game if no players are left and it was active/starting
            }
        }
    }

    @Override
    protected void gameTick() {
        timeElapsedSeconds++;

        if (timeElapsedSeconds >= gameDurationSeconds) {
            broadcastToGamePlayers("Time's up!");
            stop(false);
            return;
        }

        for (UUID uuid : playersInGame) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && player.getWorld().equals(hillCenter.getWorld())) {
                if (isPlayerOnHill(player)) {
                    int currentScore = playerScores.getOrDefault(uuid, 0);
                    playerScores.put(uuid, currentScore + 1);
                }
            }
        }

        if (timeElapsedSeconds % 30 == 0) {
            broadcastToGamePlayers("KoTH: " + (gameDurationSeconds - timeElapsedSeconds) + "s remaining.");
        }
    }

    private boolean isPlayerOnHill(Player player) {
        // Ensure world check is already done by caller or here
        if (!player.getWorld().equals(hillCenter.getWorld())) return false;

        Location playerFootLoc = player.getLocation();
        if (Math.abs(playerFootLoc.getY() - hillCenter.getY()) <= 1.5) { // Y-level check
            double dx = playerFootLoc.getX() - hillCenter.getX();
            double dz = playerFootLoc.getZ() - hillCenter.getZ();
            return (dx * dx + dz * dz) <= hillRadiusSquared;
        }
        return false;
    }

    public void broadcastToGamePlayers(String message) {
        for (UUID uuid : playersInGame) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage("[KoTH-" + gameId + "] " + message);
            }
        }
    }

    public void setHillLocation(Location location) {
        this.hillCenter = location;
        // TODO: Save to config
        plugin.getLogger().info("KoTH '" + gameId + "' hill center set to: " + location.toString());
    }

    public void setHillRadius(int radius) {
        this.hillRadius = radius;
        this.hillRadiusSquared = radius * radius;
        // TODO: Save to config
        plugin.getLogger().info("KoTH '" + gameId + "' hill radius set to: " + radius);
    }

    @Override
    public World getGameWorld() {
        if (hillCenter != null && hillCenter.getWorld() != null) {
            return hillCenter.getWorld();
        }
        World foundWorld = Bukkit.getWorld(worldName);
        if (foundWorld == null) {
            plugin.getLogger().warning("getGameWorld() called for KoTH " + gameId + " but world '" + worldName + "' is not loaded!");
        }
        return foundWorld;
    }
}