package io.mewb.andromedaGames.koth;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.game.Game;
import io.mewb.andromedaGames.game.GameState;
import io.mewb.andromedaGames.utils.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections; // For unmodifiable set
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set; // For getPlayersInGame()
import java.util.UUID;
import java.util.logging.Level;

public class KoTHGame extends Game {

    // Game parameters - to be loaded from config
    private Location hillCenter;
    private int hillRadius;
    private int hillRadiusSquared;

    private int gameDurationSeconds;
    private int timeElapsedSeconds;
    private int minPlayersToStart;
    private int countdownSeconds;

    // Arena details
    private String arenaSchematicName;
    private Location arenaPasteLocation;
    private Location lobbySpawn;
    private List<Location> gameSpawns;
    private String worldName;

    // Game state tracking (playersInGame is inherited from Game class)
    private final Map<UUID, Integer> playerScores;
    private BukkitTask gameTickTask;
    private BukkitTask countdownTask;

    private FileConfiguration gameConfig;

    public KoTHGame(AndromedaGames plugin, String gameId, String arenaId) {
        super(plugin, gameId, arenaId);
        this.playersInGame = new HashSet<>(); // Initialize inherited field
        this.playerScores = new HashMap<>();
        this.gameSpawns = new ArrayList<>();
        this.gameState = GameState.UNINITIALIZED;
    }

    public void configure(FileConfiguration config) {
        this.gameConfig = config;
        this.logger.info("Configuring KoTH game: " + gameId + " (Arena ID/Schematic: " + arenaId + ")");

        if (!config.getBoolean("enabled", false)) {
            this.logger.warning("KoTH game '" + gameId + "' is marked as disabled in its config. Aborting configuration.");
            setGameState(GameState.DISABLED);
            return;
        }

        this.worldName = config.getString("world");
        if (this.worldName == null || this.worldName.isEmpty()) {
            this.logger.severe("World name not specified for KoTH game '" + gameId + "'. Disabling game.");
            setGameState(GameState.DISABLED);
            return;
        }

        World gameWorld = Bukkit.getWorld(worldName);
        if (gameWorld == null) {
            this.logger.severe("World '" + worldName + "' not found or not loaded for KoTH game " + gameId + "! Game will be disabled.");
            setGameState(GameState.DISABLED);
            return;
        }

        this.arenaSchematicName = config.getString("arena.schematic_name", this.arenaId);
        ConfigurationSection pasteLocationSection = config.getConfigurationSection("arena.paste_location");
        this.arenaPasteLocation = LocationUtil.loadLocation(pasteLocationSection, gameWorld, this.logger);

        if (this.arenaSchematicName == null || this.arenaSchematicName.isEmpty()) {
            this.logger.warning("Arena schematic name not set for game " + gameId + ". Arena will not be loaded by schematic. Ensure it's pre-built.");
        } else if (this.arenaPasteLocation == null) {
            this.logger.severe("Arena paste location not configured correctly for game " + gameId + ". Schematic cannot be loaded. Disabling game.");
            setGameState(GameState.DISABLED);
            return;
        } else {
            this.logger.info("Attempting to load arena schematic: " + this.arenaSchematicName + " for game " + gameId);
            boolean pasted = plugin.getArenaManager().pasteSchematic(this.arenaSchematicName, this.arenaPasteLocation);
            if (!pasted) {
                this.logger.severe("Failed to paste arena schematic '" + this.arenaSchematicName + "' for game " + gameId + ". Disabling game.");
                setGameState(GameState.DISABLED);
                return;
            }
            this.logger.info("Arena '" + this.arenaSchematicName + "' loaded successfully for game " + gameId);
        }

        ConfigurationSection kothSettings = config.getConfigurationSection("koth_settings");
        if (kothSettings == null) {
            this.logger.severe("Missing 'koth_settings' section for game '" + gameId + "'. Disabling game.");
            setGameState(GameState.DISABLED);
            return;
        }
        this.hillCenter = LocationUtil.loadLocation(kothSettings.getConfigurationSection("hill_center"), gameWorld, this.logger);
        this.hillRadius = kothSettings.getInt("hill_radius", 5);
        this.hillRadiusSquared = this.hillRadius * this.hillRadius;
        this.gameDurationSeconds = kothSettings.getInt("game_duration_seconds", 300);
        this.minPlayersToStart = kothSettings.getInt("min_players_to_start", 2);
        this.countdownSeconds = kothSettings.getInt("countdown_seconds", 10);

        ConfigurationSection spawnsSection = config.getConfigurationSection("spawns");
        if (spawnsSection == null) {
            this.logger.severe("Missing 'spawns' section for game '" + gameId + "'. Disabling game.");
            setGameState(GameState.DISABLED);
            return;
        }
        this.lobbySpawn = LocationUtil.loadLocation(spawnsSection.getConfigurationSection("lobby"), gameWorld, this.logger);

        ConfigurationSection gameAreaSpawnsSection = spawnsSection.getConfigurationSection("game_area");
        if (gameAreaSpawnsSection != null) {
            this.gameSpawns = LocationUtil.loadLocationList(gameAreaSpawnsSection, gameWorld, this.logger);
        }

        if (this.hillCenter == null) {
            this.logger.severe("Hill center not configured correctly for " + gameId + ". Disabling game.");
            setGameState(GameState.DISABLED); return;
        }
        if (this.lobbySpawn == null) {
            this.logger.severe("Lobby spawn not configured correctly for " + gameId + ". Disabling game.");
            setGameState(GameState.DISABLED); return;
        }
        if (this.gameSpawns.isEmpty()) {
            this.logger.severe("No game area spawn points configured for " + gameId + ". Disabling game.");
            setGameState(GameState.DISABLED); return;
        }

        if (this.gameState != GameState.DISABLED) {
            setGameState(GameState.WAITING);
            this.logger.info("KoTH game '" + gameId + "' configured and ready. World: " + worldName);
        }
    }

    @Override
    public void load() {
        if (this.gameState == GameState.UNINITIALIZED) {
            this.logger.warning("KoTHGame.load() called but game " + gameId + " is still UNINITIALIZED. It should be configured by GameManager.");
            if (this.gameConfig == null) {
                FileConfiguration cfg = plugin.getConfigManager().getGameConfig("koth", this.gameId);
                if (cfg != null) {
                    this.configure(cfg);
                } else {
                    this.logger.severe("Could not retrieve config for " + gameId + " during fallback load(). Disabling.");
                    setGameState(GameState.DISABLED);
                }
            }
        }
    }

    @Override
    public void unload() {
        this.logger.info("Unloading KoTH game: " + gameId);
        cancelTasks();
    }

    private void cancelTasks() {
        if (countdownTask != null && !countdownTask.isCancelled()) {
            countdownTask.cancel();
        }
        countdownTask = null;
        if (gameTickTask != null && !gameTickTask.isCancelled()) {
            gameTickTask.cancel();
        }
        gameTickTask = null;
    }

    @Override
    public boolean start() {
        if (gameState != GameState.WAITING && gameState != GameState.ENDING) {
            this.logger.warning("KoTH game " + gameId + " cannot start, current state: " + gameState);
            return false;
        }
        if (playersInGame.size() < minPlayersToStart) {
            broadcastToGamePlayers(ChatColor.RED + "Not enough players to start KoTH! Need " + minPlayersToStart + ", have " + playersInGame.size() + ".");
            return false;
        }

        setGameState(GameState.STARTING);
        playerScores.clear();
        playersInGame.forEach(uuid -> playerScores.put(uuid, 0));
        timeElapsedSeconds = 0;

        int spawnIndex = 0;
        for (UUID uuid : playersInGame) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (!gameSpawns.isEmpty()) {
                    player.teleport(gameSpawns.get(spawnIndex % gameSpawns.size()));
                    spawnIndex++;
                } else {
                    this.logger.warning("No game spawns available for " + gameId + " when starting game!");
                    player.teleport(lobbySpawn);
                }
            }
        }
        startCountdown();
        return true;
    }

    private void startCountdown() {
        cancelTasks();
        final int[] currentCountdownValue = {countdownSeconds};
        broadcastToGamePlayers(ChatColor.GREEN + "Game starting in " + ChatColor.YELLOW + currentCountdownValue[0] + ChatColor.GREEN + " seconds!");

        this.countdownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (gameState != GameState.STARTING) {
                cancelTasks();
                return;
            }
            currentCountdownValue[0]--;
            if (currentCountdownValue[0] > 0) {
                broadcastToGamePlayers(ChatColor.GREEN + "Starting in " + ChatColor.YELLOW + currentCountdownValue[0] + "...");
            } else {
                if (countdownTask != null && !countdownTask.isCancelled()) {
                    countdownTask.cancel();
                }
                countdownTask = null;
                activateGame();
            }
        }, 20L, 20L);
    }

    private void activateGame() {
        if (gameState != GameState.STARTING) {
            return;
        }
        setGameState(GameState.ACTIVE);
        broadcastToGamePlayers(ChatColor.GOLD + "KoTH game '" + gameId + "' has started! Capture the hill!");
        this.logger.info("KoTH game " + gameId + " is now ACTIVE.");
        this.gameTickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::gameTick, 0L, 20L);
    }

    @Override
    public void stop(boolean force) {
        if (gameState != GameState.ACTIVE && gameState != GameState.STARTING && !force) {
            if (!force) return;
        }
        this.logger.info("KoTH game " + gameId + " is stopping. Forced: " + force);
        GameState previousState = gameState;
        setGameState(GameState.ENDING);
        cancelTasks();

        if (previousState == GameState.ACTIVE || (force && !playerScores.isEmpty())) {
            UUID winnerUUID = null;
            int maxScore = -1;
            for (Map.Entry<UUID, Integer> entry : playerScores.entrySet()) {
                if (entry.getValue() > maxScore) {
                    maxScore = entry.getValue();
                    winnerUUID = entry.getKey();
                }
            }
            if (winnerUUID != null) {
                Player winnerPlayer = Bukkit.getPlayer(winnerUUID);
                String winnerName = (winnerPlayer != null) ? winnerPlayer.getName() : "An unknown player";
                broadcastToGamePlayers(ChatColor.GOLD + winnerName + " has won KoTH '" + gameId + "' with " + maxScore + " seconds on the hill!");
            } else if (!playersInGame.isEmpty()) {
                broadcastToGamePlayers(ChatColor.YELLOW + "KoTH game '" + gameId + "' ended. No winner could be determined.");
            } else if (previousState != GameState.WAITING && previousState != GameState.ENDING) {
                broadcastToGamePlayers(ChatColor.YELLOW + "KoTH game '" + gameId + "' ended as there were no players.");
            }
        }

        for (UUID uuid : new HashSet<>(playersInGame)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.teleport(lobbySpawn);
            }
        }

        if (this.arenaSchematicName != null && !this.arenaSchematicName.isEmpty() && this.arenaPasteLocation != null && gameState != GameState.DISABLED) {
            this.logger.info("Resetting arena for game " + gameId + " by re-pasting schematic: " + this.arenaSchematicName);
            boolean reset = plugin.getArenaManager().pasteSchematic(this.arenaSchematicName, this.arenaPasteLocation);
            if (!reset) {
                this.logger.severe("CRITICAL: Failed to reset arena for game " + gameId + "!");
            } else {
                this.logger.info("Arena for " + gameId + " reset successfully.");
            }
        }
        setGameState(GameState.WAITING);
        this.logger.info("KoTH game " + gameId + " is now WAITING for players.");
    }

    @Override
    public boolean addPlayer(Player player) {
        if (gameState == GameState.DISABLED) {
            player.sendMessage(ChatColor.RED + "KoTH game '" + gameId + "' is currently disabled.");
            return false;
        }
        if (gameState != GameState.WAITING && gameState != GameState.STARTING) {
            player.sendMessage(ChatColor.RED + "KoTH game '" + gameId + "' has already started or is ending.");
            return false;
        }
        if (playersInGame.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "You are already in this KoTH game.");
            return false;
        }

        playersInGame.add(player.getUniqueId());
        playerScores.put(player.getUniqueId(), 0);
        player.teleport(lobbySpawn);
        player.sendMessage(ChatColor.GREEN + "You have joined KoTH game: " + gameId);
        broadcastToGamePlayers(ChatColor.AQUA + player.getName() + ChatColor.GRAY + " has joined the KoTH game! (" + playersInGame.size() + " players)");
        return true;
    }

    @Override
    public void removePlayer(Player player) {
        boolean wasInGame = playersInGame.remove(player.getUniqueId());
        playerScores.remove(player.getUniqueId());

        if (wasInGame) {
            player.sendMessage(ChatColor.GRAY + "You have left KoTH game: " + gameId);
            broadcastToGamePlayers(ChatColor.AQUA + player.getName() + ChatColor.GRAY + " has left the KoTH game.");
            if (lobbySpawn != null && (!player.getWorld().equals(lobbySpawn.getWorld()) || player.getLocation().distanceSquared(lobbySpawn) > 100)) {
                player.teleport(lobbySpawn);
            }

            if ((gameState == GameState.ACTIVE || gameState == GameState.STARTING)) {
                if (playersInGame.isEmpty() && minPlayersToStart > 0) {
                    broadcastToGamePlayers(ChatColor.YELLOW + "The last player left. Game ending.");
                    stop(false);
                } else if (playersInGame.size() < minPlayersToStart && minPlayersToStart > 1) {
                    broadcastToGamePlayers(ChatColor.RED + "Not enough players to continue. Game ending.");
                    stop(false);
                }
            }
        }
    }

    @Override
    protected void gameTick() {
        if (gameState != GameState.ACTIVE) {
            if (gameTickTask != null && !gameTickTask.isCancelled()){
                gameTickTask.cancel();
            }
            gameTickTask = null;
            return;
        }
        timeElapsedSeconds++;
        if (timeElapsedSeconds >= gameDurationSeconds) {
            broadcastToGamePlayers(ChatColor.GOLD + "Time's up!");
            stop(false);
            return;
        }
        for (UUID uuid : playersInGame) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (isPlayerOnHill(player)) {
                    int currentScore = playerScores.getOrDefault(uuid, 0);
                    playerScores.put(uuid, currentScore + 1);
                }
            }
        }
        if (timeElapsedSeconds > 0 && timeElapsedSeconds % 30 == 0) {
            int timeRemaining = gameDurationSeconds - timeElapsedSeconds;
            broadcastToGamePlayers(ChatColor.YELLOW + "KoTH: " + ChatColor.AQUA + timeRemaining + "s" + ChatColor.YELLOW + " remaining.");
        }
    }

    private boolean isPlayerOnHill(Player player) {
        if (hillCenter == null || !player.getWorld().equals(hillCenter.getWorld())) {
            return false;
        }
        Location playerFootLoc = player.getLocation();
        if (Math.abs(playerFootLoc.getY() - hillCenter.getY()) <= 1.5) {
            double dx = playerFootLoc.getX() - hillCenter.getX();
            double dz = playerFootLoc.getZ() - hillCenter.getZ();
            return (dx * dx + dz * dz) <= hillRadiusSquared;
        }
        return false;
    }

    public void broadcastToGamePlayers(String message) {
        String prefix = ChatColor.DARK_AQUA + "[KoTH-" + gameId + "] " + ChatColor.RESET;
        for (UUID uuid : playersInGame) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(prefix + message);
            }
        }
    }

    // --- Getters for VotingHooks ---
    /**
     * Gets the center location of the King of the Hill point.
     * @return The Location of the hill center, or null if not set.
     */
    public Location getHillCenter() {
        return this.hillCenter;
    }

    /**
     * Gets the radius of the King of the Hill capture zone.
     * @return The radius in blocks.
     */
    public int getHillRadius() {
        return this.hillRadius;
    }

    /**
     * Gets an unmodifiable set of UUIDs for players currently in this game instance.
     * The `playersInGame` field is inherited from the `Game` superclass.
     * @return An unmodifiable Set of player UUIDs.
     */
    public Set<UUID> getPlayersInGame() {
        return Collections.unmodifiableSet(this.playersInGame);
    }

    // --- Admin methods to set game parameters (should also save to config) ---
    public void setHillLocation(Location location) {
        if (location == null || gameConfig == null) return;
        this.hillCenter = location.clone();

        ConfigurationSection hillSection = gameConfig.getConfigurationSection("koth_settings.hill_center");
        if (hillSection == null) {
            hillSection = gameConfig.createSection("koth_settings.hill_center");
        }
        LocationUtil.saveLocation(hillSection, this.hillCenter, false);
        plugin.getConfigManager().saveGameConfig("koth", this.gameId, gameConfig);

        this.logger.info("KoTH '" + gameId + "' hill center administratively set and saved to: " + location.toString());
        broadcastToGamePlayers(ChatColor.YELLOW + "Admin: Hill location has been updated.");
    }

    public void setHillRadius(int radius) {
        if (radius <= 0 || gameConfig == null) return;
        this.hillRadius = radius;
        this.hillRadiusSquared = radius * radius;

        gameConfig.set("koth_settings.hill_radius", radius);
        plugin.getConfigManager().saveGameConfig("koth", this.gameId, gameConfig);

        this.logger.info("KoTH '" + gameId + "' hill radius administratively set and saved to: " + radius);
        broadcastToGamePlayers(ChatColor.YELLOW + "Admin: Hill radius has been updated to " + radius + ".");
    }

    @Override
    public World getGameWorld() {
        if (this.arenaPasteLocation != null && this.arenaPasteLocation.getWorld() != null) {
            return this.arenaPasteLocation.getWorld();
        }
        if (this.hillCenter != null && this.hillCenter.getWorld() != null) {
            return this.hillCenter.getWorld();
        }
        World foundWorld = Bukkit.getWorld(worldName);
        if (foundWorld == null && gameState != GameState.DISABLED) {
            this.logger.log(Level.WARNING, "getGameWorld() called for KoTH " + gameId + " but world '" + worldName + "' is not loaded!");
        }
        return foundWorld;
    }
}
