package io.mewb.andromedaGames;

import io.mewb.andromedaGames.arena.ArenaManager;
import io.mewb.andromedaGames.command.AndromedaGamesCommand;
import io.mewb.andromedaGames.command.KoTHCommand;
import io.mewb.andromedaGames.config.ConfigManager; // Import ConfigManager
import io.mewb.andromedaGames.game.GameManager;
import org.bukkit.plugin.java.JavaPlugin;

public class AndromedaGames extends JavaPlugin {

    private static AndromedaGames instance;
    private GameManager gameManager;
    private FAWEProvider faweProvider;
    private ArenaManager arenaManager;
    private ConfigManager configManager; // Add ConfigManager instance

    @Override
    public void onEnable() {
        instance = this;

        // Initialize FAWE Provider first as ArenaManager depends on it
        if (!checkForFAWE()) {
            getLogger().severe("FastAsyncWorldEdit (FAWE) not found! AndromedaGames will not enable.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.faweProvider = new FAWEProvider();
        getLogger().info("FAWE Provider initialized.");

        // Initialize Arena Manager
        this.arenaManager = new ArenaManager(this);
        getLogger().info("ArenaManager initialized.");

        // Initialize Config Manager
        this.configManager = new ConfigManager(this); // Instantiate ConfigManager
        getLogger().info("ConfigManager initialized.");

        // Initialize Game Manager - GameManager will use ConfigManager
        this.gameManager = new GameManager(this);
        this.gameManager.initialize(); // This now registers events and loads game configs via ConfigManager
        getLogger().info("GameManager initialized.");

        // Load main plugin configuration (config.yml) if needed for global settings
        saveDefaultConfig(); // Saves a default config.yml if one doesn't exist
        // reloadConfig(); // Load the config

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

        getLogger().info("AndromedaGames has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("AndromedaGames is disabling!");

        if (gameManager != null) {
            gameManager.shutdown(); // Shuts down all active games
        }

        instance = null;
        getLogger().info("AndromedaGames has been disabled.");
    }

    private boolean checkForFAWE() {
        if (getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            getLogger().severe("FastAsyncWorldEdit plugin not found.");
            return false;
        }
        try {
            Class.forName("com.sk89q.worldedit.bukkit.FastAsyncWorldEditPlugin");
            getLogger().info("FastAsyncWorldEdit found and accessible.");
            return true;
        } catch (ClassNotFoundException e) {
            getLogger().severe("FastAsyncWorldEdit class not found, even though plugin is present. Check FAWE installation.");
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

    public ConfigManager getConfigManager() { // Getter for ConfigManager
        return configManager;
    }

    public static class FAWEProvider {
        private com.sk89q.worldedit.WorldEdit fawe;

        public FAWEProvider() {
            try {
                this.fawe = com.sk89q.worldedit.WorldEdit.getInstance();
            } catch (Exception e) {
                AndromedaGames.getInstance().getLogger().severe("Failed to get FAWE instance from FAWEProvider: " + e.getMessage());
                this.fawe = null;
            }
        }

        public com.sk89q.worldedit.WorldEdit getFAWE() {
            if (this.fawe == null) {
                AndromedaGames.getInstance().getLogger().severe("FAWE API instance is null in FAWEProvider.getFAWE(). FAWE might not be loaded correctly.");
            }
            return this.fawe;
        }
    }
}