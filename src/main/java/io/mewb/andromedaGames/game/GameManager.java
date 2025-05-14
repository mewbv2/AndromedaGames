package io.mewb.andromedaGames.game;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.koth.KoTHGame; // Import specific game types
import me.lucko.helper.Events;
import me.lucko.helper.terminable.TerminableConsumer;
import me.lucko.helper.terminable.module.TerminableModule;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class GameManager implements TerminableModule {

    private final AndromedaGames plugin;
    private final Logger logger;
    private final Map<String, Game> loadedGames; // Keyed by a unique game ID or name
    private final Map<UUID, Game> playerCurrentGame; // Tracks which game a player is in

    public GameManager(AndromedaGames plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.loadedGames = new HashMap<>();
        this.playerCurrentGame = new HashMap<>();

        // Example: Load a default KoTH game instance (this would usually come from config)
        // For now, let's imagine a config defines a KoTH game named "mountain_koth"
        KoTHGame defaultKoTH = new KoTHGame(plugin, "mountain_koth", "koth_arena_1"); // Game ID, Arena ID
        loadedGames.put(defaultKoTH.getGameId(), defaultKoTH);
    }

    public void loadGamesFromConfig() {
        // TODO: Implement logic to read from a configuration file
        // that defines various game instances, their types, and settings.
        // For each game defined:
        // 1. Determine game type (KoTH, Infection, etc.)
        // 2. Create an instance of the appropriate Game class
        // 3. Call game.loadConfig(configSection);
        // 4. Add to loadedGames map
        logger.info("Game configurations would be loaded here.");

        // For now, just loading the default one if not already present.
        if (!loadedGames.containsKey("mountain_koth")) {
            KoTHGame defaultKoTH = new KoTHGame(plugin, "mountain_koth", "koth_arena_1");
            loadedGames.put(defaultKoTH.getGameId(), defaultKoTH);
            defaultKoTH.load(); // Call load on the game instance
        } else {
            loadedGames.get("mountain_koth").load();
        }
    }

    public Optional<Game> getGame(String gameId) {
        return Optional.ofNullable(loadedGames.get(gameId));
    }

    public boolean addPlayerToGame(Player player, String gameId) {
        if (isPlayerInAnyGame(player)) {
            player.sendMessage("You are already in a game!");
            return false;
        }
        Optional<Game> gameOpt = getGame(gameId);
        if (gameOpt.isPresent()) {
            Game game = gameOpt.get();
            if (game.addPlayer(player)) {
                playerCurrentGame.put(player.getUniqueId(), game);
                return true;
            } else {
                // Game-specific reason for not being able to join (e.g., full, already started)
                // The game.addPlayer() method should send the specific message.
                return false;
            }
        } else {
            player.sendMessage("Game '" + gameId + "' not found.");
            return false;
        }
    }

    public boolean removePlayerFromGame(Player player) {
        Game game = playerCurrentGame.remove(player.getUniqueId());
        if (game != null) {
            game.removePlayer(player);
            return true;
        }
        // player.sendMessage("You are not in any game."); // Or handle silently
        return false;
    }

    public Optional<Game> getPlayerGame(Player player) {
        return Optional.ofNullable(playerCurrentGame.get(player.getUniqueId()));
    }

    public boolean isPlayerInAnyGame(Player player) {
        return playerCurrentGame.containsKey(player.getUniqueId());
    }

    public void shutdown() {
        logger.info("Shutting down all games...");
        for (Game game : loadedGames.values()) {
            game.stop(true); // Force stop all games
            game.unload();   // Unload resources
        }
        loadedGames.clear();
        playerCurrentGame.clear();
    }

    @Override
    public void setup(@NotNull TerminableConsumer consumer) {
        // Listen for player quits to remove them from games automatically
        Events.subscribe(PlayerQuitEvent.class)
                .handler(event -> removePlayerFromGame(event.getPlayer()))
                .bindWith(consumer); // Auto-unregister on plugin disable

        // Potentially load game configurations here when the module is set up
        loadGamesFromConfig();
    }
}