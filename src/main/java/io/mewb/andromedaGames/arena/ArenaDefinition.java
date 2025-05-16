package io.mewb.andromedaGames.arena;

import io.mewb.andromedaGames.utils.RelativeLocation;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ArenaDefinition {
    private final String arenaId;
    private final String displayName;
    private final String schematicFile; // Filename like "koth_mountain.schem"
    private final List<String> tags; // e.g., "koth", "large", "indoor"

    // This map will hold the structured relative locations.
    // The Object can be a RelativeLocation or a List<RelativeLocation>.
    private Map<String, Object> definedRelativeLocations;

    // Kept for backward compatibility or other generic properties if any.
    // However, specific game locations should primarily use definedRelativeLocations.
    private final Map<String, Object> customProperties;

    public ArenaDefinition(String arenaId, String displayName, String schematicFile, List<String> tags,
                           Map<String, Object> definedRelativeLocations, Map<String, Object> customProperties) {
        this.arenaId = Objects.requireNonNull(arenaId, "Arena ID cannot be null");
        this.displayName = displayName != null ? displayName : arenaId;
        this.schematicFile = schematicFile; // Can be null if arena is part of the main world / not schematic based
        this.tags = new ArrayList<>(tags != null ? tags : Collections.emptyList());
        this.definedRelativeLocations = new HashMap<>(definedRelativeLocations != null ? definedRelativeLocations : Collections.emptyMap());
        this.customProperties = new HashMap<>(customProperties != null ? customProperties : Collections.emptyMap());
    }

    // Getters
    public String getArenaId() { return arenaId; }
    public String getDisplayName() { return displayName; }
    public String getSchematicFile() { return schematicFile; }
    public List<String> getTags() { return Collections.unmodifiableList(tags); }
    public Map<String, Object> getCustomProperties() { return Collections.unmodifiableMap(customProperties); }

    /**
     * Gets the map of defined relative locations.
     * Keys are location identifiers (e.g., "lobby_spawn", "game_spawns").
     * Values can be a single RelativeLocation or a List<RelativeLocation>.
     * @return An unmodifiable map of the defined relative locations.
     */
    public Map<String, Object> getDefinedRelativeLocations() {
        return Collections.unmodifiableMap(definedRelativeLocations);
    }

    /**
     * Sets the entire map of defined relative locations. Used by maintenance mode when saving.
     * @param newLocations The new map of relative locations.
     */
    public void setDefinedRelativeLocations(Map<String, Object> newLocations) {
        this.definedRelativeLocations = new HashMap<>(newLocations != null ? newLocations : Collections.emptyMap());
    }

    /**
     * Gets a single RelativeLocation by its key.
     * Returns null if the key does not exist or if the value is not a single RelativeLocation.
     */
    public RelativeLocation getRelativeLocation(String key) {
        Object locData = definedRelativeLocations.get(key);
        if (locData instanceof RelativeLocation) {
            return (RelativeLocation) locData;
        }
        return null; // Or log a warning if type mismatch
    }

    /**
     * Gets a list of RelativeLocations by its key.
     * Returns an empty list if the key does not exist or if the value is not a list of RelativeLocations.
     */
    @SuppressWarnings("unchecked") // For casting List<?> to List<RelativeLocation>
    public List<RelativeLocation> getRelativeLocationList(String key, Logger logger, String contextPath) {
        Object locData = definedRelativeLocations.get(key);
        if (locData instanceof List) {
            try {
                // Ensure all elements in the list are indeed RelativeLocation objects
                List<?> rawList = (List<?>) locData;
                if (rawList.stream().allMatch(obj -> obj instanceof RelativeLocation)) {
                    return (List<RelativeLocation>) rawList;
                } else if (!rawList.isEmpty()){ // If list is not empty but contains wrong types
                    if(logger != null) logger.warning("Relative location list for key '" + key + "' in " + contextPath + " contains non-RelativeLocation objects.");
                    return Collections.emptyList();
                }
            } catch (ClassCastException e) {
                if(logger != null) logger.warning("Error casting relative location list for key '" + key + "' in " + contextPath + ". " + e.getMessage());
                return Collections.emptyList();
            }
        }
        return Collections.emptyList(); // Key not found or not a list
    }


    /**
     * Factory method to load an ArenaDefinition from a ConfigurationSection.
     */
    public static ArenaDefinition loadFromConfig(String arenaId, ConfigurationSection config, Logger logger) {
        if (config == null) {
            logger.severe("Cannot load ArenaDefinition for '" + arenaId + "': ConfigurationSection is null.");
            return null;
        }
        String displayName = config.getString("display_name", arenaId);
        String schematicFile = config.getString("schematic_file"); // Can be null
        List<String> tags = config.getStringList("tags");

        Map<String, Object> parsedRelativeLocations = new HashMap<>();
        ConfigurationSection relLocsSection = config.getConfigurationSection("relative_locations");
        if (relLocsSection != null) {
            for (String key : relLocsSection.getKeys(false)) {
                if (relLocsSection.isConfigurationSection(key)) { // Single RelativeLocation
                    RelativeLocation relLoc = RelativeLocation.loadFromConfig(relLocsSection.getConfigurationSection(key), logger);
                    if (relLoc != null) {
                        parsedRelativeLocations.put(key, relLoc);
                    }
                } else if (relLocsSection.isList(key)) { // List of RelativeLocations
                    List<Map<?, ?>> mapList = relLocsSection.getMapList(key);
                    List<RelativeLocation> relLocList = new ArrayList<>();
                    for (Map<?, ?> map : mapList) {
                        RelativeLocation relLoc = RelativeLocation.loadFromMap(map, logger, "arena '" + arenaId + "' relative_locations." + key);
                        if (relLoc != null) {
                            relLocList.add(relLoc);
                        }
                    }
                    if (!relLocList.isEmpty()) {
                        parsedRelativeLocations.put(key, relLocList);
                    }
                } else {
                    logger.warning("Unsupported data type for relative location key '" + key + "' in arena '" + arenaId + "'. Must be a section or a list of sections.");
                }
            }
        }

        Map<String, Object> customPropsMap = new HashMap<>();
        ConfigurationSection customPropsSection = config.getConfigurationSection("custom_properties");
        if (customPropsSection != null) {
            for (String key : customPropsSection.getKeys(false)) {
                customPropsMap.put(key, customPropsSection.get(key));
            }
        }

        return new ArenaDefinition(arenaId, displayName, schematicFile, tags, parsedRelativeLocations, customPropsMap);
    }
}
