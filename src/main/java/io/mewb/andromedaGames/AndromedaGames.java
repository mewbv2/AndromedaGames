package io.mewb.andromedaGames;

import io.mewb.andromedaGames.arena.ArenaManager;
import io.mewb.andromedaGames.command.AndromedaGamesCommand;
import io.mewb.andromedaGames.command.KoTHCommand;
import io.mewb.andromedaGames.command.VoteCommand; // Assuming VoteCommand is created
import io.mewb.andromedaGames.config.ConfigManager;
import io.mewb.andromedaGames.game.GameManager;
import io.mewb.andromedaGames.player.PlayerStateManager; // Import PlayerStateManager
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public class AndromedaGames extends JavaPlugin {

    private static AndromedaGames instance;
    private GameManager gameManager;
    private FAWEProvider faweProvider;
    private ArenaManager arenaManager;
    private ConfigManager configManager;
    private PlayerStateManager playerStateManager; // Add PlayerStateManager instance

    @Override
    public void onEnable() {
        instance = this;

        // Initialize FAWE Provider first as ArenaManager depends on it
        if (!checkForFAWE()) {
            // checkForFAWE() now logs the specific reason if it fails
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.faweProvider = new FAWEProvider();
        getLogger().info("FAWE Provider initialized.");

        // Initialize Arena Manager
        this.arenaManager = new ArenaManager(this);
        getLogger().info("ArenaManager initialized.");

        // Initialize Config Manager
        this.configManager = new ConfigManager(this);
        getLogger().info("ConfigManager initialized.");

        // Initialize PlayerStateManager
        this.playerStateManager = new PlayerStateManager(getLogger());
        getLogger().info("PlayerStateManager initialized.");

        // Initialize Game Manager - GameManager will use ConfigManager and PlayerStateManager
        this.gameManager = new GameManager(this);
        this.gameManager.initialize(); // This now registers events and loads game configs
        getLogger().info("GameManager initialized.");

        // Load main plugin configuration (config.yml)
        saveDefaultConfig(); // Saves a default config.yml if one doesn't exist

        getLogger().info("AndromedaGames is enabling!");

        // Register Commands using Bukkit API
        AndromedaGamesCommand agCommandExecutor = new AndromedaGamesCommand(this);
        if (this.getCommand("andromedagames") != null) {
            this.getCommand("andromedagames").setExecutor(agCommandExecutor);
            this.getCommand("andromedagames").setTabCompleter(agCommandExecutor);
            getLogger().info("Registered 'andromedagames' command.");
        } else {
            getLogger().severe("Could not register 'andromedagames' command! Check plugin.yml.");
        }

        KoTHCommand kothCommandExecutor = new KoTHCommand(this, this.gameManager);
        if (this.getCommand("koth") != null) {
            this.getCommand("koth").setExecutor(kothCommandExecutor);
            this.getCommand("koth").setTabCompleter(kothCommandExecutor);
            getLogger().info("Registered 'koth' command.");
        } else {
            getLogger().severe("Could not register 'koth' command! Check plugin.yml.");
        }

        VoteCommand voteExecutor = new VoteCommand(this, this.gameManager);
        if (this.getCommand("vote") != null) {
            this.getCommand("vote").setExecutor(voteExecutor);
            this.getCommand("vote").setTabCompleter(voteExecutor); // Assuming VoteCommand implements TabCompleter
            getLogger().info("Registered 'vote' command.");
        } else {
            getLogger().severe("Could not register 'vote' command! Check plugin.yml.");
        }

        getLogger().info("AndromedaGames has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("AndromedaGames is disabling!");

        if (gameManager != null) {
            gameManager.shutdown(); // Shuts down all active games, should trigger player state restoration
        }

        // Any other cleanup tasks (e.g., closing database connections if added later)

        instance = null;
        getLogger().info("AndromedaGames has been disabled.");
    }

    /**
     * Checks if FastAsyncWorldEdit is present and enabled on the server,
     * and if its API is accessible.
     * @return true if FAWE is found, enabled, and API accessible, false otherwise.
     */
    private boolean checkForFAWE() {
        Plugin fawePlugin = getServer().getPluginManager().getPlugin("FastAsyncWorldEdit");

        if (fawePlugin == null) {
            getLogger().severe("FastAsyncWorldEdit plugin not found! AndromedaGames requires FAWE to function.");
            return false;
        }

        if (!fawePlugin.isEnabled()) {
            getLogger().severe("FastAsyncWorldEdit plugin is present but not enabled! AndromedaGames requires FAWE to be enabled.");
            getLogger().severe("Please check your server console for any FAWE errors during startup.");
            return false;
        }

        // As an additional check, ensure the WorldEdit API can be accessed.
        try {
            if (com.sk89q.worldedit.WorldEdit.getInstance() == null) {
                // This case should ideally be caught by fawePlugin.isEnabled() being false if FAWE failed to init its core.
                getLogger().severe("FAWE is enabled, but WorldEdit.getInstance() returned null. FAWE might not have initialized its API correctly.");
                return false;
            }
            getLogger().info("FastAsyncWorldEdit found, enabled, and API is accessible.");
            return true;
        } catch (NoClassDefFoundError | Exception e) { // Catch NoClassDefFoundError specifically, and other exceptions
            getLogger().severe("FastAsyncWorldEdit is enabled, but an error occurred trying to access its API (WorldEdit.getInstance()): " + e.getMessage());
            getLogger().severe("This usually indicates a problem with the FAWE installation or compatibility. Check FAWE version and server logs for FAWE errors.");
            return false;
        }
    }

    // --- Getters ---
    public static AndromedaGames getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public FAWEProvider getFaweProvider() {
        return faweProvider;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerStateManager getPlayerStateManager() {
        return playerStateManager;
    }

    /**
     * Inner class to provide access to the FAWE API.
     * This helps encapsulate the FAWE interaction.
     */
    public static class FAWEProvider {
        private com.sk89q.worldedit.WorldEdit fawe;

        public FAWEProvider() {
            // The checkForFAWE() method now includes a check for WorldEdit.getInstance(),
            // so this constructor assumes it's safe to call if checkForFAWE() passed.
            try {
                this.fawe = com.sk89q.worldedit.WorldEdit.getInstance();
            } catch (Exception e) { // Catching a broader exception just in case.
                // Use a temporary logger if getInstance() might be null during early init
                Logger tempLogger = (AndromedaGames.getInstance() != null) ? AndromedaGames.getInstance().getLogger() : Logger.getLogger("AndromedaGames-FAWEProvider");
                tempLogger.severe("Critical error in FAWEProvider constructor: Failed to get FAWE instance: " + e.getMessage());
                this.fawe = null; // Ensure fawe is null if initialization fails
            }
        }

        /**
         * Gets the FAWE API instance.
         * @return The WorldEdit instance, or null if it could not be initialized.
         */
        public com.sk89q.worldedit.WorldEdit getFAWE() {
            if (this.fawe == null) {
                // Use a temporary logger if getInstance() might be null during early init
                Logger tempLogger = (AndromedaGames.getInstance() != null) ? AndromedaGames.getInstance().getLogger() : Logger.getLogger("AndromedaGames-FAWEProvider");
                tempLogger.severe("FAWE API instance is null in FAWEProvider.getFAWE(). This indicates a critical failure in FAWE initialization or our check.");
            }
            return this.fawe;
        }
    }
}
