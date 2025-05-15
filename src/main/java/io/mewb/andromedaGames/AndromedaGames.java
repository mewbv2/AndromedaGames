package io.mewb.andromedaGames;

import io.mewb.andromedaGames.arena.ArenaManager;
import io.mewb.andromedaGames.command.AndromedaGamesCommand;
import io.mewb.andromedaGames.command.InfectionCommand; // Import InfectionCommand
import io.mewb.andromedaGames.command.KoTHCommand;
import io.mewb.andromedaGames.command.VoteCommand;
import io.mewb.andromedaGames.config.ConfigManager;
import io.mewb.andromedaGames.game.GameManager;
import io.mewb.andromedaGames.player.PlayerStateManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger; // Explicit import for FAWEProvider logger

public class AndromedaGames extends JavaPlugin {

    private static AndromedaGames instance;
    private GameManager gameManager;
    private FAWEProvider faweProvider;
    private ArenaManager arenaManager;
    private ConfigManager configManager;
    private PlayerStateManager playerStateManager;

    @Override
    public void onEnable() {
        instance = this;

        if (!checkForFAWE()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.faweProvider = new FAWEProvider();
        getLogger().info("FAWE Provider initialized.");

        this.arenaManager = new ArenaManager(this);
        getLogger().info("ArenaManager initialized.");

        this.configManager = new ConfigManager(this);
        getLogger().info("ConfigManager initialized.");

        this.playerStateManager = new PlayerStateManager(getLogger());
        getLogger().info("PlayerStateManager initialized.");

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

        VoteCommand voteExecutor = new VoteCommand(this, this.gameManager);
        if (this.getCommand("vote") != null) {
            this.getCommand("vote").setExecutor(voteExecutor);
            this.getCommand("vote").setTabCompleter(voteExecutor);
            getLogger().info("Registered 'vote' command.");
        } else {
            getLogger().severe("Could not register 'vote' command! Check plugin.yml.");
        }

        InfectionCommand infectionExecutor = new InfectionCommand(this, this.gameManager); // Create InfectionCommand executor
        if (this.getCommand("infection") != null) {
            this.getCommand("infection").setExecutor(infectionExecutor);
            this.getCommand("infection").setTabCompleter(infectionExecutor);
            getLogger().info("Registered 'infection' command.");
        } else {
            getLogger().severe("Could not register 'infection' command! Check plugin.yml.");
        }


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
        try {
            if (com.sk89q.worldedit.WorldEdit.getInstance() == null) {
                getLogger().severe("FAWE is enabled, but WorldEdit.getInstance() returned null. FAWE might not have initialized its API correctly.");
                return false;
            }
            getLogger().info("FastAsyncWorldEdit found, enabled, and API is accessible.");
            return true;
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().severe("FastAsyncWorldEdit is enabled, but an error occurred trying to access its API (WorldEdit.getInstance()): " + e.getMessage());
            getLogger().severe("This usually indicates a problem with the FAWE installation or compatibility. Check FAWE version and server logs for FAWE errors.");
            return false;
        }
    }

    public static AndromedaGames getInstance() { return instance; }
    public GameManager getGameManager() { return gameManager; }
    public FAWEProvider getFaweProvider() { return faweProvider; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public PlayerStateManager getPlayerStateManager() { return playerStateManager; }

    public static class FAWEProvider {
        private com.sk89q.worldedit.WorldEdit fawe;
        public FAWEProvider() {
            try { this.fawe = com.sk89q.worldedit.WorldEdit.getInstance(); }
            catch (Exception e) {
                Logger tempLogger = (AndromedaGames.getInstance() != null) ? AndromedaGames.getInstance().getLogger() : Logger.getLogger("AndromedaGames-FAWEProvider");
                tempLogger.severe("Critical error in FAWEProvider constructor: Failed to get FAWE instance: " + e.getMessage());
                this.fawe = null;
            }
        }
        public com.sk89q.worldedit.WorldEdit getFAWE() {
            if (this.fawe == null) {
                Logger tempLogger = (AndromedaGames.getInstance() != null) ? AndromedaGames.getInstance().getLogger() : Logger.getLogger("AndromedaGames-FAWEProvider");
                tempLogger.severe("FAWE API instance is null in FAWEProvider.getFAWE(). This indicates a critical failure in FAWE initialization or our check.");
            }
            return this.fawe;
        }
    }
}
