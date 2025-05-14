package io.mewb.andromedaGames.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

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
            logger.warning("Cannot load location: configuration section is null.");
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

        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw", 0.0); // Default to 0 if not present
        float pitch = (float) section.getDouble("pitch", 0.0); // Default to 0 if not present

        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Loads a list of Locations from a configuration section representing a list of location maps.
     *
     * @param section The ConfigurationSection containing the list.
     * @param world The world for these locations.
     * @param logger A logger for reporting errors.
     * @return A List of Locations. The list may be empty if no valid locations are found.
     */
    public static List<Location> loadLocationList(ConfigurationSection section, World world, Logger logger) {
        List<Location> locations = new ArrayList<>();
        if (section == null) {
            logger.warning("Cannot load location list: configuration section is null.");
            return locations;
        }
        if (world == null) {
            logger.warning("Cannot load location list from section '" + section.getCurrentPath() + "': world is null.");
            return locations;
        }

        List<Map<?, ?>> locationMaps = section.getMapList(""); // Assumes the section itself is a list of maps
        if (locationMaps.isEmpty() && section.isList("")) { // Fallback if getMapList is empty but it is a list
            try {
                List<?> rawList = section.getList("");
                if (rawList != null) {
                    for (Object item : rawList) {
                        if (item instanceof ConfigurationSection) {
                            // This case is unlikely if getMapList didn't work, but for completeness
                            Location loc = loadLocation((ConfigurationSection) item, world, logger);
                            if (loc != null) {
                                locations.add(loc);
                            }
                        } else if (item instanceof Map) {
                            // Manually create a temporary ConfigurationSection-like structure or adapt
                            // This part is tricky as Bukkit's API expects ConfigurationSection for easy parsing
                            // For simplicity, we'll assume getMapList works for lists of location objects.
                            // If not, manual parsing of the Map is needed.
                            logger.finer("Found a raw map in location list, manual parsing would be needed if not a ConfigurationSection.");
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Error trying to parse location list from section '" + section.getCurrentPath() + "': " + e.getMessage());
            }
        }


        for (Map<?, ?> map : locationMaps) {
            // Bukkit's getMapList converts list items into Maps. We need to adapt them or use a sub-section approach.
            // A common way is to have named subsections for each location in a list,
            // or ensure the list items are directly parsable as ConfigurationSections (which getMapList might not do).

            // Let's assume the map can be treated as a source for a temporary ConfigurationSection
            // or that the structure is like:
            // spawns:
            //   spawn1: {x,y,z}
            //   spawn2: {x,y,z}
            // If it's truly a list of anonymous maps, parsing is more direct:
            if (map.containsKey("x") && map.containsKey("y") && map.containsKey("z")) {
                double x = ((Number) map.get("x")).doubleValue();
                double y = ((Number) map.get("y")).doubleValue();
                double z = ((Number) map.get("z")).doubleValue();
                float yaw = map.containsKey("yaw") ? ((Number) map.get("yaw")).floatValue() : 0.0f;
                float pitch = map.containsKey("pitch") ? ((Number) map.get("pitch")).floatValue() : 0.0f;
                locations.add(new Location(world, x, y, z, yaw, pitch));
            } else {
                logger.warning("Skipping invalid location entry in list under section '" + section.getCurrentPath() + "': missing x, y, or z.");
            }
        }
        if (locations.isEmpty() && !locationMaps.isEmpty()) {
            logger.info("Parsed location list for " + section.getCurrentPath() + " but no valid locations were constructed. Check map structure.");
        }


        return locations;
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
