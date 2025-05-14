package io.mewb.andromedaGames;

import io.mewb.andromedaGames.command.AndromedaGamesCommand;
import io.mewb.andromedaGames.command.KoTHCommand;
import io.mewb.andromedaGames.game.GameManager;
import me.lucko.helper.plugin.ExtendedJavaPlugin;
// No need to import CompositeTerminable here unless you are creating new instances of it
// in *this* class, which we aren't for the main plugin-level one.

import org.bukkit.Bukkit; // Keep this if used, e.g. for Bukkit.getServer() implicitly by getLogger() etc.

public class AndromedaGames extends ExtendedJavaPlugin {

    private static AndromedaGames instance;
    private GameManager gameManager;
    private FAWEProvider faweProvider;

    // The main CompositeTerminable is managed privately by ExtendedJavaPlugin.
    // We interact with it via bind(), bindModule(), or by passing 'this' (as a TerminableConsumer).

    @Override
    protected void enable() {
        instance = this;

        if (!checkForFAWE()) {
            getLogger().severe("FastAsyncWorldEdit (FAWE) not found! AndromedaGames will not enable.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.faweProvider = new FAWEProvider();

        getLogger().info("AndromedaGames is enabling!");

        this.gameManager = new GameManager(this);
        // GameManager implements TerminableModule. Its setup method will be called,
        // and it will be registered with the plugin's main CompositeTerminable.
        bindModule(this.gameManager);

        // Load Configurations (if not handled by GameManager's setup or other modules)
        // loadMainConfig();

        // Register Commands
        // These command classes, if they implement TerminableModule (or just Command from Helper),
        // will also be managed by the plugin's lifecycle when bound this way.
        bindModule(new AndromedaGamesCommand(this));
        bindModule(new KoTHCommand(this, this.gameManager)); // Pass the already created gameManager

        // Register any other listeners or schedulers directly tied to the plugin's lifecycle
        // Example:
        // Events.subscribe(SomeGlobalEvent.class)
        //       .handler(e -> { /* handle event */ })
        //       .bindWith(this); // 'this' is the TerminableConsumer

        getLogger().info("AndromedaGames has been enabled successfully!");
    }

    @Override
    protected void disable() {
        getLogger().info("AndromedaGames is disabling!");

        // All resources registered via bind(), bindModule(), or .bindWith(this)
        // will be automatically closed/terminated by ExtendedJavaPlugin's onDisable sequence.

        // If GameManager needs an explicit shutdown call for logic not covered by TerminableModule.close(),
        // it could be placed here. However, the ideal is that GameManager.close() handles all its cleanup.
        // Our current GameManager.shutdown() is called from its own TerminableModule.close() if we design it that way,
        // or explicitly if needed. For now, GameManager's PlayerQuitEvent listener is bound via its setup method,
        // which is tied to the plugin's lifecycle. The gameManager.shutdown() call is more for active game states.
        // Let's ensure GameManager's shutdown is robust.
        if (gameManager != null) {
            // This explicit shutdown might be redundant if GameManager.close() handles it,
            // but can be kept for clarity for now if it performs actions beyond
            // what its 'close' (from Terminable/AutoCloseable) might do.
            gameManager.shutdown();
        }

        instance = null;
        getLogger().info("AndromedaGames has been disabled.");
    }

    private boolean checkForFAWE() {
        if (getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            return false;
        }
        try {
            Class.forName("com.sk89q.worldedit.bukkit.FastAsyncWorldEditPlugin");
            return true;
        } catch (ClassNotFoundException e) {
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

    // The problematic getPluginTerminableModule() method has been removed.
    // If a class needs to bind to the plugin's lifecycle, it should accept
    // the AndromedaGames instance (as a TerminableConsumer) or use Helper's
    // static methods if appropriate and then .bindWith(pluginInstance).

    // --- Utility for FAWE ---
    public static class FAWEProvider {
        private com.sk89q.worldedit.WorldEdit fawe;

        public FAWEProvider() {
            // This is a common way to get the FAWE API instance
            // Ensure FAWE is loaded before this is called (which checkForFAWE() does)
            this.fawe = com.sk89q.worldedit.WorldEdit.getInstance();
        }

        public com.sk89q.worldedit.WorldEdit getFAWE() {
            return this.fawe;
        }
    }
}