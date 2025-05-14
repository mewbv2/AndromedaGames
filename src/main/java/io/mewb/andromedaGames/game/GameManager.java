package io.mewb.andromedaGames.game;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.config.ConfigManager;
import io.mewb.andromedaGames.koth.KoTHGame;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class GameManager implements Listener {

    private final AndromedaGames plugin;
    private final Logger logger;
    private final ConfigManager configManager; // Use ConfigManager
    private final Map<String, Game> loadedGames;
    private final Map<UUID, Game> playerCurrentGame;

    public GameManager(AndromedaGames plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = plugin.getConfigManager(); // Get ConfigManager instance
        this.loadedGames = new HashMap<>();
        this.playerCurrentGame = new HashMap<>();
    }

    public void initialize() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        logger.info("GameManager initialized and registered as event listener.");
        loadGamesFromConfig();
    }

    public void loadGamesFromConfig() {
        logger.info("Loading all game configurations...");
        loadedGames.clear(); // Clear existing games before loading new ones

        // --- Load KoTH Games ---
        Map<String, FileConfiguration> kothConfigs = configManager.loadAllGamesOfType("koth");
        for (Map.Entry<String, FileConfiguration> entry : kothConfigs.entrySet()) {
            String gameId = entry.getKey();
            FileConfiguration gameConfig = entry.getValue();

            if (!gameConfig.getBoolean("enabled", false)) {
                logger.info("KoTH game '" + gameId + "' is disabled in config, skipping.");
                continue;
            }

            // The arenaId could be the gameId itself if schematics are named that way,
            // or a specific value from the config. Let's assume gameId can serve as arenaId for now.
            // Or, more robustly, get it from config: gameConfig.getString("arena.schematic_name", gameId)
            String arenaId = gameConfig.getString("arena.schematic_name", gameId); // Use schematic_name as arenaId

            KoTHGame kothGame = new KoTHGame(plugin, gameId, arenaId); // Pass gameConfig to KoTHGame
            kothGame.configure(gameConfig); // New method in KoTHGame to load its specific settings

            if (kothGame.getGameState() != GameState.DISABLED) { // Only add if not disabled during its own load
                loadedGames.put(gameId, kothGame);
                logger.info("Successfully loaded and configured KoTH game: " + gameId);
            } else {
                logger.warning("KoTH game '" + gameId + "' was disabled during its load process (e.g., world/arena issue).");
            }
        }

        // --- Load Other Game Types (Future) ---
        // Example:
        // Map<String, FileConfiguration> infectionConfigs = configManager.loadAllGamesOfType("infection");
        // for (Map.Entry<String, FileConfiguration> entry : infectionConfigs.entrySet()) { ... }

        logger.info(loadedGames.size() + " game(s) are now loaded and enabled.");
    }

    public Optional<Game> getGame(String gameId) {
        return Optional.ofNullable(loadedGames.get(gameId));
    }

    public Collection<Game> getAllGames() {
        return loadedGames.values();
    }

    public List<String> getKoTHGameIds() {
        return loadedGames.values().stream()
                .filter(KoTHGame.class::isInstance)
                .map(Game::getGameId)
                .collect(Collectors.toList());
    }

    public boolean addPlayerToGame(Player player, String gameId) {
        if (isPlayerInAnyGame(player)) {
            player.sendMessage(ChatColor.RED + "You are already in a game!");
            return false;
        }
        Optional<Game> gameOpt = getGame(gameId);
        if (gameOpt.isPresent()) {
            Game game = gameOpt.get();
            if (game.addPlayer(player)) {
                playerCurrentGame.put(player.getUniqueId(), game);
                return true;
            }
            return false;
        } else {
            player.sendMessage(ChatColor.RED + "Game '" + gameId + "' not found.");
            return false;
        }
    }

    public boolean removePlayerFromGame(Player player) {
        Game game = playerCurrentGame.remove(player.getUniqueId());
        if (game != null) {
            game.removePlayer(player);
            return true;
        }
        return false;
    }

    public Optional<Game> getPlayerGame(Player player) {
        return Optional.ofNullable(playerCurrentGame.get(player.getUniqueId()));
    }

    public boolean isPlayerInAnyGame(Player player) {
        return playerCurrentGame.containsKey(player.getUniqueId());
    }

    public void shutdown() {
        logger.info("Shutting down all games...");
        for (Game game : new ArrayList<>(loadedGames.values())) {
            try {
                game.stop(true);
                game.unload();
            } catch (Exception e) {
                logger.severe("Error during shutdown of game " + game.getGameId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        loadedGames.clear();
        playerCurrentGame.clear();
        logger.info("All games have been shut down and player tracking cleared.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isPlayerInAnyGame(player)) {
            logger.info("Player " + player.getName() + " quit, removing from their current game.");
            removePlayerFromGame(player);
        }
    }
}
