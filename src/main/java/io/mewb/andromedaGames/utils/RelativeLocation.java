package io.mewb.andromedaGames.utils;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class RelativeLocation {
    public double relX;
    public double relY;
    public double relZ;
    public float yaw;
    public float pitch;

    public RelativeLocation(double relX, double relY, double relZ, float yaw, float pitch) {
        this.relX = relX;
        this.relY = relY;
        this.relZ = relZ;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public RelativeLocation(double relX, double relY, double relZ) {
        this(relX, relY, relZ, 0.0f, 0.0f);
    }

    /**
     * Calculates the absolute world location based on a base paste location.
     * @param basePasteLocation The absolute world location where the parent structure (e.g., schematic) was pasted.
     * The yaw and pitch of this base location are also used as the reference.
     * @return The absolute world Location.
     */
    public Location toAbsolute(Location basePasteLocation) {
        if (basePasteLocation == null || basePasteLocation.getWorld() == null) {
            // Consider logging a warning here or throwing an IllegalArgumentException
            // For now, returning null if base is invalid.
            return null;
        }
        // Calculate absolute world coordinates
        double absoluteX = basePasteLocation.getX() + relX;
        double absoluteY = basePasteLocation.getY() + relY;
        double absoluteZ = basePasteLocation.getZ() + relZ;

        // Calculate absolute world orientation
        // The relative yaw/pitch are added to the basePasteLocation's yaw/pitch
        float absoluteYaw = basePasteLocation.getYaw() + this.yaw;
        float absolutePitch = basePasteLocation.getPitch() + this.pitch;

        // Normalize yaw to be within -180 to 180 (Minecraft standard)
        while (absoluteYaw > 180.0f) absoluteYaw -= 360.0f;
        while (absoluteYaw <= -180.0f) absoluteYaw += 360.0f;

        // Pitch is typically clamped between -90 and 90 by clients, but raw values can exceed this.
        // No normalization needed here unless specifically required.

        return new Location(
                basePasteLocation.getWorld(),
                absoluteX,
                absoluteY,
                absoluteZ,
                absoluteYaw,
                absolutePitch
        );
    }

    /**
     * Calculates a RelativeLocation based on an absolute point and a base origin location.
     * This is used in Maintenance Mode to determine the relative offsets from the schematic's paste point.
     * @param absolutePoint The current absolute location (e.g., admin's position).
     * @param baseOrigin The absolute location where the schematic was pasted (its 0,0,0 point in the world).
     * The yaw/pitch of baseOrigin are the reference for relative yaw/pitch.
     * @return A new RelativeLocation object.
     */
    public static RelativeLocation calculateFrom(Location absolutePoint, Location baseOrigin) {
        if (absolutePoint == null || baseOrigin == null || absolutePoint.getWorld() == null || baseOrigin.getWorld() == null) {
            throw new IllegalArgumentException("Absolute point and base origin must be valid locations with worlds.");
        }
        if (!absolutePoint.getWorld().equals(baseOrigin.getWorld())) {
            // This should ideally not happen in maintenance mode as admin is in the same world as pasted schematic.
            // Or, if baseOrigin is conceptual (0,0,0) and world is just for reference.
            // For now, let's assume they are in the same world or baseOrigin's world is the reference.
            // Log a warning if different, but proceed using absolutePoint's world for the calculation context if needed.
            // However, spatial relativity only makes sense within the same coordinate system (world).
            // For simplicity, we'll assume they are intended to be in the same world.
            // If not, the concept of "relative" becomes ambiguous without further rules.
        }

        double relX = absolutePoint.getX() - baseOrigin.getX();
        double relY = absolutePoint.getY() - baseOrigin.getY();
        double relZ = absolutePoint.getZ() - baseOrigin.getZ();

        // Calculate relative yaw. The result should be the difference needed to add to baseOrigin.getYaw()
        // to get absolutePoint.getYaw().
        float relYaw = absolutePoint.getYaw() - baseOrigin.getYaw();
        // Normalize yaw to the range (-180, 180]
        while (relYaw <= -180.0f) relYaw += 360.0f;
        while (relYaw > 180.0f) relYaw -= 360.0f;

        // Relative pitch.
        float relPitch = absolutePoint.getPitch() - baseOrigin.getPitch();
        // Pitch is naturally clamped by Minecraft to -90 to 90. No complex normalization needed here usually.

        return new RelativeLocation(relX, relY, relZ, relYaw, relPitch);
    }


    /**
     * Loads a RelativeLocation from a ConfigurationSection.
     * Expects "rel_x", "rel_y", "rel_z", and optionally "yaw", "pitch".
     */
    public static RelativeLocation loadFromConfig(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return null;
        }
        if (!section.contains("rel_x") || !section.contains("rel_y") || !section.contains("rel_z")) {
            if (logger != null) {
                logger.warning("Cannot load RelativeLocation from section '" + section.getCurrentPath() + "': rel_x, rel_y, or rel_z coordinates are missing.");
            }
            return null;
        }
        try {
            double x = section.getDouble("rel_x");
            double y = section.getDouble("rel_y");
            double z = section.getDouble("rel_z");
            float yaw = (float) section.getDouble("yaw", 0.0); // Default to 0.0 if not present
            float pitch = (float) section.getDouble("pitch", 0.0); // Default to 0.0 if not present
            return new RelativeLocation(x, y, z, yaw, pitch);
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Error parsing coordinates for RelativeLocation in section '" + section.getCurrentPath() + "': " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * Loads a RelativeLocation from a Map (often from config.getMapList).
     * Keys in map should be "rel_x", "rel_y", "rel_z", "yaw", "pitch".
     */
    public static RelativeLocation loadFromMap(Map<?, ?> map, Logger logger, String pathForLogging) {
        if (map == null) return null;
        if (map.containsKey("rel_x") && map.containsKey("rel_y") && map.containsKey("rel_z")) {
            try {
                double x = ((Number) map.get("rel_x")).doubleValue();
                double y = ((Number) map.get("rel_y")).doubleValue();
                double z = ((Number) map.get("rel_z")).doubleValue();
                float yaw = map.containsKey("yaw") ? ((Number) map.get("yaw")).floatValue() : 0.0f;
                float pitch = map.containsKey("pitch") ? ((Number) map.get("pitch")).floatValue() : 0.0f;
                return new RelativeLocation(x, y, z, yaw, pitch);
            } catch (ClassCastException | NullPointerException e) { // Catch specific errors from map access
                if (logger != null) logger.warning("Error parsing RelativeLocation from map for path '" + pathForLogging + "': Invalid data type in map. " + e.getMessage() + " - Entry: " + map.toString());
                return null;
            }
        } else {
            if (logger != null) logger.warning("Skipping invalid RelativeLocation map entry for path '" + pathForLogging + "': missing rel_x, rel_y, or rel_z. Entry: " + map.toString());
            return null;
        }
    }

    /**
     * Converts this RelativeLocation object to a Map suitable for serialization in YAML.
     * @return A Map representing this RelativeLocation.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("rel_x", relX);
        map.put("rel_y", relY);
        map.put("rel_z", relZ);
        // Only include yaw and pitch if they are not default (0.0) to keep YAML cleaner, optional.
        if (this.yaw != 0.0f) {
            map.put("yaw", yaw);
        }
        if (this.pitch != 0.0f) {
            map.put("pitch", pitch);
        }
        return map;
    }

    @Override
    public String toString() {
        return "RelativeLocation{" +
                "relX=" + String.format("%.2f", relX) +
                ", relY=" + String.format("%.2f", relY) +
                ", relZ=" + String.format("%.2f", relZ) +
                ", yaw=" + String.format("%.2f", yaw) +
                ", pitch=" + String.format("%.2f", pitch) +
                '}';
    }
}
