package io.mewb.andromedaGames.game;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration; // Not directly used here

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class GameDefinition {
    private final String definitionId;
    private final String gameType; // "KOTH", "INFECTION", "CTS"
    private final String displayName;
    private final Map<String, Object> gameRules; // e.g., duration, min_players, captures_to_win
    private final List<String> compatibleArenaTags; // Arenas must have at least one of these tags
    private final ConfigurationSection votingConfig; // Raw section for voting setup

    public GameDefinition(String definitionId, String gameType, String displayName,
                          Map<String, Object> gameRules, List<String> compatibleArenaTags,
                          ConfigurationSection votingConfig) {
        this.definitionId = definitionId;
        this.gameType = gameType;
        this.displayName = displayName;
        this.gameRules = new HashMap<>(gameRules);
        this.compatibleArenaTags = new ArrayList<>(compatibleArenaTags);
        this.votingConfig = votingConfig; // Store the whole section
    }

    // Getters
    public String getDefinitionId() { return definitionId; }
    public String getGameType() { return gameType; }
    public String getDisplayName() { return displayName; }
    public Map<String, Object> getGameRules() { return Collections.unmodifiableMap(gameRules); }
    @SuppressWarnings("unchecked")
    public <T> T getRule(String key, T defaultValue) {
        return (T) gameRules.getOrDefault(key, defaultValue);
    }
    public List<String> getCompatibleArenaTags() { return Collections.unmodifiableList(compatibleArenaTags); }
    public ConfigurationSection getVotingConfig() { return votingConfig; }


    /**
     * Factory method to load a GameDefinition from a ConfigurationSection.
     */
    public static GameDefinition loadFromConfig(String definitionId, ConfigurationSection config, Logger logger) {
        if (config == null) {
            logger.severe("Cannot load GameDefinition for '" + definitionId + "': ConfigurationSection is null.");
            return null;
        }
        String gameType = config.getString("game_type");
        if (gameType == null || gameType.isEmpty()) {
            logger.severe("Missing 'game_type' for game definition '" + definitionId + "'.");
            return null;
        }
        String displayName = config.getString("display_name", definitionId);

        Map<String, Object> rulesMap = new HashMap<>();
        ConfigurationSection rulesSection = config.getConfigurationSection("rules");
        if (rulesSection != null) {
            for (String key : rulesSection.getKeys(false)) {
                rulesMap.put(key, rulesSection.get(key));
            }
        } else {
            logger.warning("No 'rules' section found for game definition '" + definitionId + "'.");
        }

        List<String> compatibleArenaTags = config.getStringList("compatible_arena_tags");
        ConfigurationSection votingConfigSection = config.getConfigurationSection("default_voting_hooks"); // Or just "voting"

        return new GameDefinition(definitionId, gameType, displayName, rulesMap, compatibleArenaTags, votingConfigSection);
    }
}
