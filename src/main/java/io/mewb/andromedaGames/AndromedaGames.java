package io.mewb.andromedaGames;

import io.mewb.andromedaGames.arena.ArenaManager;
import io.mewb.andromedaGames.command.AndromedaGamesCommand;
import io.mewb.andromedaGames.command.CaptureTheShardCommand;
import io.mewb.andromedaGames.command.InfectionCommand;
import io.mewb.andromedaGames.command.KoTHCommand;
import io.mewb.andromedaGames.command.VoteCommand;
import io.mewb.andromedaGames.config.ConfigManager;
import io.mewb.andromedaGames.game.GameManager;
import io.mewb.andromedaGames.player.PlayerStateManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

public class AndromedaGames extends JavaPlugin {

    private static AndromedaGames instance;
    private ConfigManager configManager;
    private PlayerStateManager playerStateManager;
    private FAWEProvider faweProvider;
    private ArenaManager arenaManager;
    private GameManager gameManager;


    @Override
    public void onEnable() {
        instance = this;
        Logger pluginLogger = getLogger(); // Use a local variable for clarity

        pluginLogger.info("AndromedaGames is enabling!");

        // Initialize ConfigManager first as other managers might need it (e.g., for settings)
        // Also, ConfigManager handles default config.yml saving.
        this.configManager = new ConfigManager(this);
        pluginLogger.info("ConfigManager initialized.");

        // Initialize PlayerStateManager
        this.playerStateManager = new PlayerStateManager(pluginLogger); // Pass the logger
        pluginLogger.info("PlayerStateManager initialized.");

        // Initialize FAWE Provider (dependency for ArenaManager)
        if (!checkForFAWE()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.faweProvider = new FAWEProvider();
        pluginLogger.info("FAWE Provider initialized.");

        // Initialize Arena Manager (depends on FAWEProvider)
        this.arenaManager = new ArenaManager(this); // ArenaManager might need plugin instance for tasks or FAWEProvider
        pluginLogger.info("ArenaManager initialized.");

        // Initialize Game Manager (depends on ConfigManager, PlayerStateManager, ArenaManager)
        this.gameManager = new GameManager(this); // GameManager constructor now takes plugin instance
        this.gameManager.initialize(); // This registers events and loads game/arena definitions
        pluginLogger.info("GameManager initialized and definitions loaded.");


        // Register Commands using Bukkit API
        // Main Admin Command (/ag) - this will delegate to ArenaAdminCommand for /ag arena
        AndromedaGamesCommand agCommandExecutor = new AndromedaGamesCommand(this);
        if (this.getCommand("andromedagames") != null) {
            this.getCommand("andromedagames").setExecutor(agCommandExecutor);
            this.getCommand("andromedagames").setTabCompleter(agCommandExecutor);
            pluginLogger.info("Registered 'andromedagames' command (/ag).");
        } else {
            pluginLogger.severe("Could not register 'andromedagames' command! Check plugin.yml.");
        }

        // Game-Specific Commands
        KoTHCommand kothCommandExecutor = new KoTHCommand(this, this.gameManager);
        if (this.getCommand("koth") != null) {
            this.getCommand("koth").setExecutor(kothCommandExecutor);
            this.getCommand("koth").setTabCompleter(kothCommandExecutor);
            pluginLogger.info("Registered 'koth' command.");
        } else {
            pluginLogger.severe("Could not register 'koth' command! Check plugin.yml.");
        }

        VoteCommand voteExecutor = new VoteCommand(this, this.gameManager);
        if (this.getCommand("vote") != null) {
            this.getCommand("vote").setExecutor(voteExecutor);
            this.getCommand("vote").setTabCompleter(voteExecutor);
            pluginLogger.info("Registered 'vote' command.");
        } else {
            pluginLogger.severe("Could not register 'vote' command! Check plugin.yml.");
        }

        InfectionCommand infectionExecutor = new InfectionCommand(this, this.gameManager);
        if (this.getCommand("infection") != null) {
            this.getCommand("infection").setExecutor(infectionExecutor);
            this.getCommand("infection").setTabCompleter(infectionExecutor);
            pluginLogger.info("Registered 'infection' command.");
        } else {
            pluginLogger.severe("Could not register 'infection' command! Check plugin.yml.");
        }

        CaptureTheShardCommand ctsExecutor = new CaptureTheShardCommand(this, this.gameManager);
        if (this.getCommand("capturetheshard") != null) {
            this.getCommand("capturetheshard").setExecutor(ctsExecutor);
            this.getCommand("capturetheshard").setTabCompleter(ctsExecutor);
            pluginLogger.info("Registered 'capturetheshard' command (/cts).");
        } else {
            pluginLogger.severe("Could not register 'capturetheshard' command! Check plugin.yml.");
        }
        // TODO: Register commands for AnvilRain, ColorCollapse, ChickenSpleef when implemented

        pluginLogger.info("AndromedaGames has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("AndromedaGames is disabling!");
        if (gameManager != null) {
            gameManager.shutdown(); // Shuts down all active games and performs cleanup
        }
        // Any other specific cleanup for other managers if needed
        getLogger().info("AndromedaGames has been disabled.");
        instance = null;
    }

    private boolean checkForFAWE() {
        Plugin fawePlugin = getServer().getPluginManager().getPlugin("FastAsyncWorldEdit");
        if (fawePlugin == null) {
            getLogger().severe("FastAsyncWorldEdit plugin not found! AndromedaGames requires FAWE to function.");
            return false;
        }
        if (!fawePlugin.isEnabled()) {
            getLogger().severe("FastAsyncWorldEdit plugin is present but not enabled! AndromedaGames requires FAWE to be enabled.");
            return false;
        }
        try {
            // Attempt to access a core FAWE class to ensure API is available
            if (com.sk89q.worldedit.WorldEdit.getInstance() == null) {
                getLogger().severe("FAWE is enabled, but WorldEdit.getInstance() returned null. FAWE API might not be ready.");
                return false;
            }
            getLogger().info("FastAsyncWorldEdit found, enabled, and API is accessible.");
            return true;
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().severe("Error accessing FAWE API (WorldEdit.getInstance()): " + e.getMessage());
            getLogger().severe("This usually indicates a problem with FAWE installation or compatibility. Check FAWE version.");
            return false;
        }
    }

    // --- Getters for Managers and Instance ---
    public static AndromedaGames getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerStateManager getPlayerStateManager() {
        return playerStateManager;
    }

    public FAWEProvider getFaweProvider() {
        return faweProvider;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    /**
     * Inner class to provide safe access to the FAWE API.
     * This helps encapsulate the FAWE interaction and check for its availability.
     */
    public static class FAWEProvider {
        private com.sk89q.worldedit.WorldEdit fawe;
        private boolean initializedCorrectly = false;

        public FAWEProvider() {
            try {
                this.fawe = com.sk89q.worldedit.WorldEdit.getInstance();
                if (this.fawe != null) {
                    initializedCorrectly = true;
                } else {
                    // Logger might not be available if getInstance() is null during construction
                    Logger tempLogger = (AndromedaGames.getInstance() != null) ? AndromedaGames.getInstance().getLogger() : Logger.getLogger("AndromedaGames-FAWEProvider-Constructor");
                    tempLogger.severe("FAWEProvider: WorldEdit.getInstance() returned null during construction.");
                }
            } catch (Exception e) {
                Logger tempLogger = (AndromedaGames.getInstance() != null) ? AndromedaGames.getInstance().getLogger() : Logger.getLogger("AndromedaGames-FAWEProvider-Constructor");
                tempLogger.severe("Critical error in FAWEProvider constructor: Failed to get FAWE instance: " + e.getMessage());
                this.fawe = null; // Ensure fawe is null on error
            }
        }

        public com.sk89q.worldedit.WorldEdit getFAWE() {
            if (!initializedCorrectly || this.fawe == null) {
                Logger tempLogger = (AndromedaGames.getInstance() != null) ? AndromedaGames.getInstance().getLogger() : Logger.getLogger("AndromedaGames-FAWEProvider-Getter");
                tempLogger.severe("FAWE API instance is null or was not initialized correctly in FAWEProvider.getFAWE(). FAWE features will not work.");
            }
            return this.fawe;
        }

        public boolean isInitialized() {
            return initializedCorrectly && this.fawe != null;
        }
    }
}
