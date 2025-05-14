package io.mewb.andromedaGames.config;

import io.mewb.andromedaGames.AndromedaGames;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfigManager {

    private final AndromedaGames plugin;
    private final Logger logger;
    private final File gamesDirectory; // Directory to store game-specific YAML files

    // Cache for loaded game configurations
    private final Map<String, FileConfiguration> gameConfigs = new HashMap<>();

    public ConfigManager(AndromedaGames plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.gamesDirectory = new File(plugin.getDataFolder(), "games");

        if (!gamesDirectory.exists()) {
            if (gamesDirectory.mkdirs()) {
                logger.info("Created games configuration directory: " + gamesDirectory.getAbsolutePath());
                // Optionally, create default subdirectories for game types like 'koth'
                createDefaultGameTypeDirectories();
            } else {
                logger.severe("Could not create games configuration directory: " + gamesDirectory.getAbsolutePath());
            }
        }
    }

    private void createDefaultGameTypeDirectories() {
        File kothDir = new File(gamesDirectory, "koth");
        if (!kothDir.exists() && kothDir.mkdirs()) {
            logger.info("Created default KoTH games directory: " + kothDir.getAbsolutePath());
            // You could also save a default example koth game file here.
            // saveDefaultKoTHExample(kothDir);
        }
        // Add other game type directories as needed
    }

    /**
     * Gets the FileConfiguration for a specific game.
     * Loads it from file if not already cached.
     *
     * @param gameType The type of game (e.g., "koth", "infection") - used for subdirectories.
     * @param gameId   The unique ID of the game (typically the filename without .yml).
     * @return The FileConfiguration for the game, or null if it cannot be loaded/found.
     */
    public FileConfiguration getGameConfig(String gameType, String gameId) {
        String configKey = gameType.toLowerCase() + File.separator + gameId.toLowerCase();
        if (gameConfigs.containsKey(configKey)) {
            return gameConfigs.get(configKey);
        }

        File gameConfigFile = new File(new File(gamesDirectory, gameType.toLowerCase()), gameId.toLowerCase() + ".yml");

        if (!gameConfigFile.exists()) {
            logger.warning("Game configuration file not found: " + gameConfigFile.getAbsolutePath());
            // Optionally, create a default one here if desired:
            // try {
            //     plugin.saveResource("default_configs/" + gameType.toLowerCase() + "/" + gameId.toLowerCase() + ".yml", false); // This copies from JAR
            // } catch (IllegalArgumentException e) { // If resource not in JAR
            //     logger.info("No default config resource found in JAR for " + gameConfigFile.getName());
            // }
            return null; // Or an empty YamlConfiguration if you want to proceed with defaults
        }

        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(gameConfigFile);
            gameConfigs.put(configKey, config);
            logger.info("Loaded game configuration: " + gameConfigFile.getAbsolutePath());
            return config;
        } catch (IOException | InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Could not load game configuration from " + gameConfigFile.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Saves a game's FileConfiguration back to its file.
     *
     * @param gameType The type of game.
     * @param gameId   The ID of the game.
     * @param config   The FileConfiguration to save.
     * @return True if successful, false otherwise.
     */
    public boolean saveGameConfig(String gameType, String gameId, FileConfiguration config) {
        String configKey = gameType.toLowerCase() + File.separator + gameId.toLowerCase();
        File gameConfigFile = new File(new File(gamesDirectory, gameType.toLowerCase()), gameId.toLowerCase() + ".yml");
        try {
            config.save(gameConfigFile);
            gameConfigs.put(configKey, config); // Update cache
            logger.info("Saved game configuration: " + gameConfigFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save game configuration to " + gameConfigFile.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Reloads all cached game configurations or a specific one.
     * For simplicity, this example clears the cache, forcing a reload on next get.
     * A more sophisticated reload might update existing Game objects.
     * @param gameType Optional: specific game type to reload.
     * @param gameId Optional: specific game ID to reload.
     */
    public void reloadGameConfigs(String gameType, String gameId) {
        if (gameType != null && gameId != null) {
            String configKey = gameType.toLowerCase() + File.separator + gameId.toLowerCase();
            gameConfigs.remove(configKey);
            logger.info("Marked game config for reload: " + configKey);
            // Force reload by getting it again (optional, depends on how GameManager uses this)
            // getGameConfig(gameType, gameId);
        } else {
            gameConfigs.clear();
            logger.info("All game configurations marked for reload (cache cleared).");
        }
    }

    /**
     * Retrieves all game configuration files for a given game type.
     * @param gameType The type of game (e.g., "koth").
     * @return A Map of gameId to FileConfiguration.
     */
    public Map<String, FileConfiguration> loadAllGamesOfType(String gameType) {
        Map<String, FileConfiguration> configs = new HashMap<>();
        File gameTypeDir = new File(gamesDirectory, gameType.toLowerCase());
        if (!gameTypeDir.exists() || !gameTypeDir.isDirectory()) {
            logger.warning("Game type directory not found for: " + gameType);
            return configs;
        }

        File[] gameFiles = gameTypeDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (gameFiles != null) {
            for (File gameFile : gameFiles) {
                String gameId = gameFile.getName().substring(0, gameFile.getName().length() - 4); // Remove .yml
                FileConfiguration config = getGameConfig(gameType, gameId); // Use existing method to load/cache
                if (config != null) {
                    configs.put(gameId, config);
                }
            }
        }
        logger.info("Loaded " + configs.size() + " configurations for game type: " + gameType);
        return configs;
    }
}
