package io.mewb.andromedaGames;

import io.mewb.andromedaGames.arena.ArenaManager;
import io.mewb.andromedaGames.command.AndromedaGamesCommand;
import io.mewb.andromedaGames.command.KoTHCommand;
import io.mewb.andromedaGames.config.ConfigManager;
import io.mewb.andromedaGames.game.GameManager;
import org.bukkit.plugin.Plugin; // Import Bukkit Plugin class
import org.bukkit.plugin.java.JavaPlugin;

public class AndromedaGames extends JavaPlugin {

    private static AndromedaGames instance;
    private GameManager gameManager;
    private FAWEProvider faweProvider;
    private ArenaManager arenaManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize FAWE Provider first as ArenaManager depends on it
        if (!checkForFAWE()) {
            // checkForFAWE() now logs the specific reason
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

        // Initialize Game Manager - GameManager will use ConfigManager
        this.gameManager = new GameManager(this);
        this.gameManager.initialize();
        getLogger().info("GameManager initialized.");

        saveDefaultConfig();

        getLogger().info("AndromedaGames is enabling!");

        // Register Commands
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

        // Register Vote Command
        // Assuming VoteCommand class exists and is structured similarly
        // VoteCommand voteExecutor = new VoteCommand(this, this.gameManager);
        // if (this.getCommand("vote") != null) {
        //     this.getCommand("vote").setExecutor(voteExecutor);
        //     // this.getCommand("vote").setTabCompleter(voteExecutor); // If it has tab completion
        //     getLogger().info("Registered 'vote' command.");
        // } else {
        //     getLogger().severe("Could not register 'vote' command! Check plugin.yml.");
        // }


        getLogger().info("AndromedaGames has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("AndromedaGames is disabling!");
        if (gameManager != null) {
            gameManager.shutdown();
        }
        instance = null;
        getLogger().info("AndromedaGames has been disabled.");
    }

    /**
     * Checks if FastAsyncWorldEdit is present and enabled on the server.
     * @return true if FAWE is found and enabled, false otherwise.
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
        // This is what FAWEProvider will try to do.
        // This step is more of a "can we get the API instance" rather than checking a specific class name.
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

    public static class FAWEProvider {
        private com.sk89q.worldedit.WorldEdit fawe;

        public FAWEProvider() {
            // The checkForFAWE() method now includes a check for WorldEdit.getInstance(),
            // so this constructor assumes it's safe to call if checkForFAWE() passed.
            try {
                this.fawe = com.sk89q.worldedit.WorldEdit.getInstance();
            } catch (Exception e) { // Catching a broader exception just in case.
                AndromedaGames.getInstance().getLogger().severe("Critical error in FAWEProvider constructor: Failed to get FAWE instance: " + e.getMessage());
                // This shouldn't happen if checkForFAWE worked, but as a safeguard:
                this.fawe = null;
            }
        }

        public com.sk89q.worldedit.WorldEdit getFAWE() {
            if (this.fawe == null) {
                AndromedaGames.getInstance().getLogger().severe("FAWE API instance is null in FAWEProvider.getFAWE(). This indicates a critical failure in FAWE initialization or our check.");
            }
            return this.fawe;
        }
    }
}
