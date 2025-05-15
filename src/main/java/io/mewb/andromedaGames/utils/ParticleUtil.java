package io.mewb.andromedaGames.utils;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.Random;

public class ParticleUtil {

    private static final Random random = new Random();

    /**
     * Spawns a persistent (or repeating) particle effect around a player.
     * Good for indicating a status, like being "on the hill".
     * This method itself doesn't repeat; it spawns one batch. Call it in a repeating task.
     *
     * @param player   The player to spawn particles around.
     * @param particle The type of particle to spawn.
     * @param count    The number of particles in this batch.
     * @param offsetX  The random X offset.
     * @param offsetY  The random Y offset.
     * @param offsetZ  The random Z offset.
     * @param speed    The speed/extra data of the particle.
     */
    public static void spawnPlayerStatusParticles(Player player, Particle particle, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        if (player == null || !player.isOnline()) return;
        Location loc = player.getLocation().add(0, 1, 0); // Centered around player's mid-section
        player.getWorld().spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, speed);
    }

    /**
     * Spawns an effect at a specific location, often used for an event happening.
     *
     * @param location The location to spawn the particles.
     * @param particle The type of particle.
     * @param count    The number of particles.
     * @param offsetX  Random X offset.
     * @param offsetY  Random Y offset.
     * @param offsetZ  Random Z offset.
     * @param speed    Particle speed/extra data.
     */
    public static void spawnLocationEffect(Location location, Particle particle, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        if (location == null || location.getWorld() == null) return;
        location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed);
    }

    /**
     * Example: Creates a rising helix effect around a location (e.g., hill center for an event).
     *
     * @param centerLocation The center of the helix.
     * @param particle       The particle type.
     * @param radius         The radius of the helix.
     * @param height         The total height of the helix.
     * @param density        How many particles per rotation segment.
     * @param rotations      How many full rotations.
     */
    public static void spawnHelixEffect(Location centerLocation, Particle particle, double radius, double height, int density, double rotations) {
        if (centerLocation == null || centerLocation.getWorld() == null) return;
        World world = centerLocation.getWorld();
        double points = density * rotations; // Total points to plot
        double yIncrement = height / points;

        for (int i = 0; i < points; i++) {
            double angle = (i / (double) density) * 2 * Math.PI; // Current angle for this point
            double x = centerLocation.getX() + radius * Math.cos(angle);
            double z = centerLocation.getZ() + radius * Math.sin(angle);
            double y = centerLocation.getY() + i * yIncrement;
            world.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0); // Spawn 1 particle, no offset, no speed
        }
    }

    /**
     * Spawns a particle explosion effect at a location.
     * @param location The center of the explosion.
     * @param particle The particle type.
     * @param count The number of particles.
     * @param intensity The spread/speed of the explosion.
     */
    public static void spawnExplosionEffect(Location location, Particle particle, int count, float intensity) {
        if (location == null || location.getWorld() == null) return;
        location.getWorld().spawnParticle(particle, location, count, intensity, intensity, intensity, intensity * 0.5); // Higher speed for explosion
    }
}