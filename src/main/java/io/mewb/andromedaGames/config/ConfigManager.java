package io.mewb.andromedaGames.config;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.arena.ArenaDefinition;
import io.mewb.andromedaGames.game.GameDefinition;
import io.mewb.andromedaGames.utils.RelativeLocation; // Added import
import org.bukkit.configuration.ConfigurationSection; // Added import
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException; // For save method
import java.util.ArrayList; // Added import
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors; // Added import

public class ConfigManager {

    private final AndromedaGames plugin;
    private final Logger logger;
    private final File pluginDataFolder;
    private final File arenasDirectory;
    private final File definitionsDirectory;

    // Arena Setup Configuration
    private String arenaSetupWorldName = "world_arena_setup"; // Default value
    private double arenaSetupOriginX = 0.0;
    private double arenaSetupOriginY = 100.0;
    private double arenaSetupOriginZ = 0.0;


    private static final List<String> GAME_TYPES = Collections.unmodifiableList(Arrays.asList(
            "koth", "infection", "capturetheshard",
            "anvilrain", "colorcollapse", "chickenspleef"
    ));

    private final Map<String, ArenaDefinition> loadedArenaDefinitions = new HashMap<>();
    private final Map<String, GameDefinition> loadedGameDefinitions = new HashMap<>();

    public ConfigManager(AndromedaGames plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.pluginDataFolder = plugin.getDataFolder();

        if (!pluginDataFolder.exists() && !pluginDataFolder.mkdirs()) {
            logger.severe("CRITICAL: Could not create plugin data folder: " + pluginDataFolder.getAbsolutePath());
        }

        this.arenasDirectory = new File(pluginDataFolder, "arenas");
        if (!arenasDirectory.exists() && !arenasDirectory.mkdirs()) {
            logger.severe("Could not create arenas directory: " + arenasDirectory.getAbsolutePath());
        }

        this.definitionsDirectory = new File(pluginDataFolder, "definitions");
        if (!definitionsDirectory.exists() && !definitionsDirectory.mkdirs()) {
            logger.severe("Could not create definitions directory: " + definitionsDirectory.getAbsolutePath());
        }

        loadMainPluginConfig(); // Load settings like arena setup world/origin
        setupDefaultConfigs();
    }

    /**
     * Loads settings from the main config.yml (e.g., arena setup parameters).
     */
    private void loadMainPluginConfig() {
        plugin.saveDefaultConfig(); // Ensures config.yml exists
        FileConfiguration mainConfig = plugin.getConfig(); // Gets the already loaded config

        this.arenaSetupWorldName = mainConfig.getString("arena_setup.setup_world_name", "world_arena_setup");
        this.arenaSetupOriginX = mainConfig.getDouble("arena_setup.origin_x", 0.0);
        this.arenaSetupOriginY = mainConfig.getDouble("arena_setup.origin_y", 100.0);
        this.arenaSetupOriginZ = mainConfig.getDouble("arena_setup.origin_z", 0.0);
        logger.info("Arena Setup Config: World='" + arenaSetupWorldName + "', Origin=(" + arenaSetupOriginX + "," + arenaSetupOriginY + "," + arenaSetupOriginZ + ")");
    }

    // Getters for Arena Setup Config
    public String getArenaSetupWorldName() { return arenaSetupWorldName; }
    public double getArenaSetupOriginX() { return arenaSetupOriginX; }
    public double getArenaSetupOriginY() { return arenaSetupOriginY; }
    public double getArenaSetupOriginZ() { return arenaSetupOriginZ; }


    private void setupDefaultConfigs() {
        logger.info("Initializing default configuration files and directories...");
        saveDefaultArenaExamples();
        createGameTypeDefinitionDirectoriesAndExamples();
        logger.info("Default configuration setup process complete.");
    }

    private void saveDefaultArenaExamples() {
        logger.fine("Saving default arena example files...");
        for (String gameType : GAME_TYPES) {
            String exampleArenaFileName = "example_arena_" + gameType.toLowerCase() + ".yml";
            String resourcePath = "arenas/" + exampleArenaFileName;
            File destinationFile = new File(arenasDirectory, exampleArenaFileName);
            saveDefaultResource(resourcePath, destinationFile);
        }
    }

    private void createGameTypeDefinitionDirectoriesAndExamples() {
        logger.fine("Creating game type definition directories and saving example definition files...");
        for (String gameType : GAME_TYPES) {
            String normalizedGameType = gameType.toLowerCase();
            File gameTypeDir = new File(definitionsDirectory, normalizedGameType);
            if (!gameTypeDir.exists() && !gameTypeDir.mkdirs()) {
                logger.severe("Could not create directory for '" + normalizedGameType + "' game definitions.");
                continue;
            }
            String exampleDefinitionFileName = "default_" + normalizedGameType + ".yml";
            String resourcePath = "definitions/" + normalizedGameType + "/" + exampleDefinitionFileName;
            File destinationFile = new File(gameTypeDir, exampleDefinitionFileName);
            saveDefaultResource(resourcePath, destinationFile);
        }
    }

    private void saveDefaultResource(String resourcePath, File destinationFile) {
        if (!destinationFile.exists()) {
            logger.info("Default config '" + destinationFile.getName() + "' not found. Saving from JAR: " + resourcePath);
            try {
                File parentDir = destinationFile.getParentFile();
                if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                    logger.severe("Could not create parent directory: " + parentDir.getAbsolutePath());
                    return;
                }
                plugin.saveResource(resourcePath, false);
                if (!destinationFile.exists()) {
                    logger.warning("Failed to save default config: " + destinationFile.getName() + ". Resource '" + resourcePath + "' might be missing/empty in JAR.");
                } else {
                    logger.info("Successfully saved default config: " + destinationFile.getAbsolutePath());
                }
            } catch (IllegalArgumentException e) {
                logger.warning("Resource not found in JAR for '" + resourcePath + "'. " + e.getMessage());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error saving default resource '" + resourcePath + "'", e);
            }
        } else {
            logger.finer("Default config '" + destinationFile.getName() + "' already exists.");
        }
    }

    /**
     * Saves the given ArenaDefinition object to its corresponding YAML file.
     * This will overwrite the existing file.
     * @param arenaDef The ArenaDefinition to save.
     * @return true if successful, false otherwise.
     */
    public boolean saveArenaDefinition(ArenaDefinition arenaDef) {
        if (arenaDef == null) {
            logger.warning("Attempted to save a null ArenaDefinition.");
            return false;
        }
        File arenaFile = new File(arenasDirectory, arenaDef.getArenaId().toLowerCase() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        config.set("arena_id", arenaDef.getArenaId()); // Store with original casing for consistency if desired
        config.set("display_name", arenaDef.getDisplayName());
        if (arenaDef.getSchematicFile() != null && !arenaDef.getSchematicFile().isEmpty()) {
            config.set("schematic_file", arenaDef.getSchematicFile());
        } else {
            config.set("schematic_file", null); // Explicitly null if not set
        }
        config.set("tags", arenaDef.getTags());

        // Serialize definedRelativeLocations
        // The map can contain RelativeLocation or List<RelativeLocation>
        if (arenaDef.getDefinedRelativeLocations() != null && !arenaDef.getDefinedRelativeLocations().isEmpty()) {
            ConfigurationSection relLocsSection = config.createSection("relative_locations");
            for (Map.Entry<String, Object> entry : arenaDef.getDefinedRelativeLocations().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof RelativeLocation) {
                    relLocsSection.set(key, ((RelativeLocation) value).toMap());
                } else if (value instanceof List) {
                    // Check if it's a list of RelativeLocation
                    List<?> rawList = (List<?>) value;
                    if (!rawList.isEmpty() && rawList.get(0) instanceof RelativeLocation) {
                        List<Map<String, Object>> mapList = rawList.stream()
                                .map(rl -> ((RelativeLocation) rl).toMap())
                                .collect(Collectors.toList());
                        relLocsSection.set(key, mapList);
                    } else if (rawList.isEmpty()){
                        relLocsSection.set(key, new ArrayList<>()); // Save empty list
                    } else {
                        logger.warning("Cannot save relative location list for key '" + key + "' in arena '" + arenaDef.getArenaId() + "': List contains non-RelativeLocation objects.");
                    }
                } else if (value != null) {
                    logger.warning("Cannot save relative location for key '" + key + "' in arena '" + arenaDef.getArenaId() + "': Unknown object type: " + value.getClass().getName());
                }
            }
        } else {
            // If map is empty or null, ensure the section is cleared or not present
            config.set("relative_locations", null);
        }


        // Serialize customProperties
        if (arenaDef.getCustomProperties() != null && !arenaDef.getCustomProperties().isEmpty()) {
            ConfigurationSection customPropsSection = config.createSection("custom_properties");
            for (Map.Entry<String, Object> entry : arenaDef.getCustomProperties().entrySet()) {
                customPropsSection.set(entry.getKey(), entry.getValue());
            }
        } else {
            config.set("custom_properties", null);
        }

        try {
            config.save(arenaFile);
            logger.info("Saved arena definition: " + arenaFile.getAbsolutePath());
            // If this arena was already loaded, update the cache or clear it so it reloads next time.
            loadedArenaDefinitions.put(arenaDef.getArenaId().toLowerCase(), arenaDef); // Update cache
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save arena definition to " + arenaFile.getAbsolutePath(), e);
            return false;
        }
    }


    public ArenaDefinition getArenaDefinition(String arenaId) {
        String normalizedArenaId = arenaId.toLowerCase();
        if (loadedArenaDefinitions.containsKey(normalizedArenaId)) {
            return loadedArenaDefinitions.get(normalizedArenaId);
        }
        File arenaFile = new File(arenasDirectory, normalizedArenaId + ".yml");
        if (!arenaFile.exists()) return null;
        FileConfiguration config = YamlConfiguration.loadConfiguration(arenaFile);
        ArenaDefinition definition = ArenaDefinition.loadFromConfig(arenaId, config, logger);
        if (definition != null) {
            loadedArenaDefinitions.put(normalizedArenaId, definition);
        }
        return definition;
    }

    public Map<String, ArenaDefinition> loadAllArenaDefinitions() {
        loadedArenaDefinitions.clear();
        if (!arenasDirectory.exists() || !arenasDirectory.isDirectory()) {
            logger.warning("Arenas directory not found.");
            return Collections.emptyMap();
        }
        File[] arenaFiles = arenasDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (arenaFiles != null) {
            for (File arenaFile : arenaFiles) {
                String arenaId = arenaFile.getName().substring(0, arenaFile.getName().length() - 4);
                getArenaDefinition(arenaId);
            }
        }
        logger.info("Loaded " + loadedArenaDefinitions.size() + " arena definitions.");
        return Collections.unmodifiableMap(loadedArenaDefinitions);
    }

    public GameDefinition getGameDefinition(String gameType, String definitionId) {
        String key = gameType.toLowerCase() + "/" + definitionId.toLowerCase();
        if (loadedGameDefinitions.containsKey(key)) {
            return loadedGameDefinitions.get(key);
        }
        File definitionFile = new File(new File(definitionsDirectory, gameType.toLowerCase()), definitionId.toLowerCase() + ".yml");
        if (!definitionFile.exists()) return null;
        FileConfiguration config = YamlConfiguration.loadConfiguration(definitionFile);
        GameDefinition definition = GameDefinition.loadFromConfig(definitionId, config, logger);
        if (definition != null) {
            loadedGameDefinitions.put(key, definition);
        }
        return definition;
    }

    public Map<String, GameDefinition> loadAllGameDefinitionsOfType(String gameType) {
        String normalizedGameType = gameType.toLowerCase();
        Map<String, GameDefinition> definitionsOfType = new HashMap<>();
        File gameTypeDir = new File(definitionsDirectory, normalizedGameType);
        if (!gameTypeDir.exists() || !gameTypeDir.isDirectory()) {
            logger.warning("Game definition directory not found for type: " + normalizedGameType);
            return Collections.emptyMap();
        }
        File[] definitionFiles = gameTypeDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (definitionFiles != null) {
            for (File defFile : definitionFiles) {
                String definitionId = defFile.getName().substring(0, defFile.getName().length() - 4);
                GameDefinition def = getGameDefinition(normalizedGameType, definitionId);
                if (def != null) {
                    definitionsOfType.put(definitionId, def);
                }
            }
        }
        logger.info("Loaded " + definitionsOfType.size() + " game definitions for type '" + normalizedGameType + "'.");
        return Collections.unmodifiableMap(definitionsOfType);
    }

    public void reloadAllDefinitions() {
        loadedArenaDefinitions.clear();
        loadedGameDefinitions.clear();
        loadMainPluginConfig(); // Reload main config settings like arena setup world/origin
        logger.info("ConfigManager caches cleared and main config reloaded.");
    }
}
