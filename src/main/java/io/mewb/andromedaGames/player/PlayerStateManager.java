package io.mewb.andromedaGames.player;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class PlayerStateManager {

    private static final Map<UUID, PlayerState> savedStates = new HashMap<>();
    private final Logger logger;

    public PlayerStateManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * Saves the current state of a player.
     * @param player The player whose state is to be saved.
     */
    public void savePlayerState(Player player) {
        if (player == null) return;
        UUID playerUUID = player.getUniqueId();

        if (savedStates.containsKey(playerUUID)) {
            // This might happen if a player joins another game before their state from a previous one was cleared,
            // or if save is called multiple times. For minigames, usually, we want the state *before* joining the first game.
            // logger.warning("Player " + player.getName() + " already has a saved state. Overwriting is not typical for minigame joins.");
            // For now, we'll allow overwrite, but a more robust system might prevent this or handle it differently.
        }

        ItemStack[] inventory = player.getInventory().getContents();
        ItemStack[] armor = player.getInventory().getArmorContents();
        Collection<PotionEffect> effects = player.getActivePotionEffects();
        double health = player.getHealth();
        int foodLevel = player.getFoodLevel();
        float saturation = player.getSaturation();
        float exhaustion = player.getExhaustion();
        int level = player.getLevel();
        float exp = player.getExp();
        GameMode gameMode = player.getGameMode();
        boolean isFlying = player.isFlying();
        boolean allowFlight = player.getAllowFlight();
        // Max health can also be stored if it's modified by plugins/attributes
        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();


        PlayerState state = new PlayerState(inventory, armor, effects, health, maxHealth, foodLevel, saturation, exhaustion, level, exp, gameMode, isFlying, allowFlight);
        savedStates.put(playerUUID, state);
        logger.fine("Saved state for player " + player.getName());
    }

    /**
     * Restores the saved state to a player.
     * @param player The player whose state is to be restored.
     * @return True if a state was found and restored, false otherwise.
     */
    public boolean restorePlayerState(Player player) {
        if (player == null) return false;
        UUID playerUUID = player.getUniqueId();
        PlayerState state = savedStates.remove(playerUUID); // Remove after restoring

        if (state == null) {
            logger.warning("No saved state found for player " + player.getName() + " to restore.");
            return false;
        }

        // Clear current inventory and effects before restoring
        player.getInventory().clear();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        player.getInventory().setContents(state.getInventory());
        player.getInventory().setArmorContents(state.getArmor());
        player.addPotionEffects(state.getEffects());

        // Restore health carefully, considering max health
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(state.getMaxHealth()); // Restore max health first
        if (state.getHealth() > state.getMaxHealth()) { // Cap health at their restored max health
            player.setHealth(state.getMaxHealth());
        } else {
            player.setHealth(state.getHealth());
        }

        player.setFoodLevel(state.getFoodLevel());
        player.setSaturation(state.getSaturation());
        player.setExhaustion(state.getExhaustion());
        player.setLevel(state.getLevel());
        player.setExp(state.getExp());
        player.setGameMode(state.getGameMode());
        player.setAllowFlight(state.isAllowFlight()); // Restore allow flight before setting flying state
        player.setFlying(state.isFlying()); // Restore flying state

        logger.info("Restored state for player " + player.getName());
        return true;
    }

    /**
     * Clears a player's inventory, potion effects, sets gamemode, health, and hunger
     * in preparation for a game.
     * @param player The player to prepare.
     * @param gameMode The GameMode to set for the game (e.g., GameMode.SURVIVAL, GameMode.ADVENTURE).
     */
    public void clearPlayerForGame(Player player, GameMode gameMode) {
        if (player == null) return;

        player.getInventory().clear();
        player.getInventory().setArmorContents(null); // Clear armor slots

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Reset health to default max (20) or a game-specific max if needed
        // For now, let's assume default max health unless game modifies it
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0); // Reset to default
        player.setHealth(20.0); // Full health
        player.setFoodLevel(20); // Full hunger
        player.setSaturation(20f); // Full saturation (important for natural regeneration)
        player.setExhaustion(0f);
        player.setLevel(0);
        player.setExp(0f);
        player.setFireTicks(0);

        player.setGameMode(gameMode);
        player.setAllowFlight(gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR); // Default flight allowance
        player.setFlying(false); // Ensure not flying unless gamemode allows and intended

        logger.fine("Cleared player " + player.getName() + " for game, set gamemode to " + gameMode.name());
    }

    /**
     * Removes a player's saved state from memory without restoring it.
     * Useful if a player logs out and their state is handled by other means,
     * or if a game ends and restoration isn't desired/possible.
     * @param player The player whose state is to be removed.
     */
    public void removePlayerState(Player player) {
        if (player == null) return;
        if (savedStates.remove(player.getUniqueId()) != null) {
            logger.fine("Removed saved state for player " + player.getName());
        }
    }

    /**
     * Checks if a player has a state saved.
     * @param player The player to check.
     * @return True if a state is saved, false otherwise.
     */
    public boolean hasSavedState(Player player) {
        if (player == null) return false;
        return savedStates.containsKey(player.getUniqueId());
    }
}