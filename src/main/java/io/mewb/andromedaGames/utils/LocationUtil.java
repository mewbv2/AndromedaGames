package io.mewb.andromedaGames.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class LocationUtil {

    /**
     * Loads a single Location from a configuration section.
     * Assumes x, y, z are present. Yaw and pitch are optional.
     *
     * @param section The ConfigurationSection to load from.
     * @param world The world for this location.
     * @param logger A logger for reporting errors.
     * @return The Location, or null if essential coordinates are missing or the world is null.
     */
    public static Location loadLocation(ConfigurationSection section, World world, Logger logger) {
        if (section == null) {
            // Logger call is removed here as the caller should log if the section itself is missing.
            // This method expects a non-null section representing the location's data.
            return null;
        }
        if (world == null) {
            logger.warning("Cannot load location from section '" + section.getCurrentPath() + "': world is null.");
            return null;
        }

        if (!section.contains("x") || !section.contains("y") || !section.contains("z")) {
            logger.warning("Cannot load location from section '" + section.getCurrentPath() + "': x, y, or z coordinates are missing.");
            return null;
        }

        try {
            double x = section.getDouble("x");
            double y = section.getDouble("y");
            double z = section.getDouble("z");
            float yaw = (float) section.getDouble("yaw", 0.0); // Default to 0 if not present
            float pitch = (float) section.getDouble("pitch", 0.0); // Default to 0 if not present
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception e) {
            logger.warning("Error parsing coordinates for location in section '" + section.getCurrentPath() + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Loads a list of Locations from a list of maps.
     * This is a helper method for loadLocationList.
     * @param locationMaps The List of Maps, where each map represents a location.
     * @param world The world for these locations.
     * @param configPathForLogging The configuration path string for logging purposes.
     * @param logger A logger for reporting errors.
     * @return A List of Locations.
     */
    private static List<Location> loadLocationListFromMaps(List<Map<?, ?>> locationMaps, World world, String configPathForLogging, Logger logger) {
        List<Location> locations = new ArrayList<>();
        if (world == null) {
            logger.warning("Cannot load location list from maps for path '" + configPathForLogging + "': world is null.");
            return locations;
        }
        if (locationMaps == null) { // This check is important
            logger.warning("Cannot load location list from maps for path '" + configPathForLogging + "': provided map list is null (path might be incorrect or not a list).");
            return locations;
        }

        int entryIndex = 0;
        for (Map<?, ?> map : locationMaps) {
            entryIndex++;
            if (map != null && map.containsKey("x") && map.containsKey("y") && map.containsKey("z")) {
                try {
                    double x = ((Number) map.get("x")).doubleValue();
                    double y = ((Number) map.get("y")).doubleValue();
                    double z = ((Number) map.get("z")).doubleValue();
                    float yaw = map.containsKey("yaw") ? ((Number) map.get("yaw")).floatValue() : 0.0f;
                    float pitch = map.containsKey("pitch") ? ((Number) map.get("pitch")).floatValue() : 0.0f;
                    locations.add(new Location(world, x, y, z, yaw, pitch));
                } catch (Exception e) {
                    logger.warning("Error parsing location entry #" + entryIndex + " in list for path '" + configPathForLogging + "': " + e.getMessage() + " - Entry: " + map.toString());
                }
            } else {
                logger.warning("Skipping invalid location entry #" + entryIndex + " in list for path '" + configPathForLogging + "': missing x, y, or z, or map is null. Entry: " + (map == null ? "null" : map.toString()));
            }
        }
        if (locations.isEmpty() && !locationMaps.isEmpty()) {
            logger.warning("Parsed location list for '" + configPathForLogging + "' but no valid locations were constructed. Check map structures within the list.");
        }
        return locations;
    }


    /**
     * Loads a list of Locations from a configuration section using a given key that points to a list of location maps.
     *
     * @param parentSection The ConfigurationSection containing the list.
     * @param listKey The key within parentSection that holds the list of location maps (e.g., "game_area").
     * @param world The world for these locations.
     * @param logger A logger for reporting errors.
     * @return A List of Locations. The list may be empty if no valid locations are found or key doesn't exist/isn't a list.
     */
    public static List<Location> loadLocationList(ConfigurationSection parentSection, String listKey, World world, Logger logger) {
        if (parentSection == null) {
            logger.warning("Cannot load location list for key '" + listKey + "': parent configuration section is null.");
            return new ArrayList<>();
        }

        if (!parentSection.contains(listKey)) {
            logger.warning("Location list key '" + listKey + "' not found in section '" + parentSection.getCurrentPath() + "'. Returning empty list.");
            return new ArrayList<>();
        }
        // Bukkit's getMapList will return an empty list if the path is not a list or doesn't exist,
        // so we don't strictly need parentSection.isList(listKey) if we trust getMapList's behavior.
        // However, adding an isList check can provide more specific logging.
        if (!parentSection.isList(listKey)){
            logger.warning("Path '" + parentSection.getCurrentPath() + "." + listKey + "' is not a list in the configuration. Expected a list of location objects.");
            return new ArrayList<>();
        }

        List<Map<?, ?>> locationMaps = parentSection.getMapList(listKey);
        // getMapList returns an empty list if the path is not a list or doesn't exist.
        // It will throw an exception if the list contains non-map elements.
        if (locationMaps.isEmpty() && parentSection.get(listKey) != null) { // Check if the key exists but getMapList returned empty (e.g. list of strings)
            logger.warning("Location list for key '" + listKey + "' in section '" + parentSection.getCurrentPath() + "' was found but could not be parsed as a list of maps (is it a list of strings or numbers instead of location objects?).");
        }

        return loadLocationListFromMaps(locationMaps, world, parentSection.getCurrentPath() + "." + listKey, logger);
    }

    /**
     * Saves a Location to a ConfigurationSection.
     *
     * @param section The ConfigurationSection to save to.
     * @param location The Location to save.
     * @param saveWorld If true, saves the world name (not typically done for game configs where world is parent).
     */
    public static void saveLocation(ConfigurationSection section, Location location, boolean saveWorld) {
        if (section == null || location == null) {
            return;
        }
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
        if (saveWorld && location.getWorld() != null) {
            section.set("world", location.getWorld().getName());
        }
    }
}
