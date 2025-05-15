package io.mewb.andromedaGames.infection;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.game.Game;
import io.mewb.andromedaGames.game.GameState;
import io.mewb.andromedaGames.player.PlayerStateManager;
import io.mewb.andromedaGames.utils.GameScoreboard;
import io.mewb.andromedaGames.utils.LocationUtil;
// import io.mewb.andromedaGames.utils.ParticleUtil; // Will add later for effects
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class InfectionGame extends Game implements Listener {

    // Game parameters from config
    private int gameDurationSeconds;
    private int initialInfectedCount;
    private int countdownSeconds;
    private String scoreboardTitle;
    private GameMode survivorGamemode = GameMode.SURVIVAL;
    private GameMode infectedGamemode = GameMode.SURVIVAL;

    // Arena details
    private String arenaSchematicName;
    private Location arenaPasteLocation;
    private Location lobbySpawn;
    private List<Location> gameSpawns;
    private String worldName;

    // Game state tracking
    private final Set<UUID> infectedPlayers = new HashSet<>();
    private final Set<UUID> survivorPlayers = new HashSet<>();
    private BukkitTask gameTimerTask;
    private BukkitTask countdownTask;
    private FileConfiguration gameConfig;
    private int timeRemainingSeconds; // Added to track game time for scoreboard

    // Scoreboard Management
    private final Map<UUID, GameScoreboard> playerScoreboards = new HashMap<>();

    // Player State Management
    private final PlayerStateManager playerStateManager;
    private final Random random = new Random();

    // Scoreboard Teams for visual differentiation
    private Team infectedTeamSpigot;
    private Team survivorTeamSpigot;


    public InfectionGame(AndromedaGames plugin, String gameId, String arenaId) {
        super(plugin, gameId, arenaId);
        this.playerStateManager = plugin.getPlayerStateManager();
        this.playersInGame = new HashSet<>();
        this.gameSpawns = new ArrayList<>();
        this.gameState = GameState.UNINITIALIZED;
    }

    @Override
    public void configure(FileConfiguration config) {
        this.gameConfig = config;
        this.logger.info("Configuring Infection game: " + gameId + " (Arena ID/Schematic: " + arenaId + ")");

        if (!config.getBoolean("enabled", false)) {
            this.logger.warning("Infection game '" + gameId + "' is marked as disabled. Aborting configuration.");
            setGameState(GameState.DISABLED); return;
        }

        this.worldName = config.getString("world");
        if (this.worldName == null || this.worldName.isEmpty()) {
            this.logger.severe("World name not specified for Infection game '" + gameId + "'. Disabling game.");
            setGameState(GameState.DISABLED); return;
        }
        World gameWorld = Bukkit.getWorld(worldName);
        if (gameWorld == null) {
            this.logger.severe("World '" + worldName + "' not found for Infection game " + gameId + ". Disabling game.");
            setGameState(GameState.DISABLED); return;
        }

        this.arenaSchematicName = config.getString("arena.schematic_name", this.arenaId);
        this.arenaPasteLocation = LocationUtil.loadLocation(config.getConfigurationSection("arena.paste_location"), gameWorld, this.logger);
        if (this.arenaSchematicName != null && !this.arenaSchematicName.isEmpty() && this.arenaPasteLocation != null) {
            if (!plugin.getArenaManager().pasteSchematic(this.arenaSchematicName, this.arenaPasteLocation)) {
                this.logger.severe("Failed to paste arena schematic '" + this.arenaSchematicName + "'. Disabling game.");
                setGameState(GameState.DISABLED); return;
            }
            this.logger.info("Arena '" + this.arenaSchematicName + "' loaded for game " + gameId);
        } else if (this.arenaSchematicName != null && !this.arenaSchematicName.isEmpty()) {
            this.logger.severe("Arena schematic name is set for " + gameId + " but paste location is not. Disabling game.");
            setGameState(GameState.DISABLED); return;
        } else {
            this.logger.warning("Arena schematic not specified or paste location missing for " + gameId + ". Assuming pre-built arena.");
        }

        ConfigurationSection infectionSettings = config.getConfigurationSection("infection_settings");
        if (infectionSettings == null) { this.logger.severe("Missing 'infection_settings' for " + gameId + ". Disabling."); setGameState(GameState.DISABLED); return; }
        this.initialInfectedCount = infectionSettings.getInt("initial_infected_count", 1);
        this.gameDurationSeconds = infectionSettings.getInt("game_duration_seconds", 300);
        this.timeRemainingSeconds = this.gameDurationSeconds; // Initialize time remaining
        this.countdownSeconds = infectionSettings.getInt("countdown_seconds", 15);
        this.scoreboardTitle = ChatColor.translateAlternateColorCodes('&', infectionSettings.getString("scoreboard_title", "&c&lINFECTION: &e" + gameId));
        try {
            this.survivorGamemode = GameMode.valueOf(infectionSettings.getString("survivor_gamemode", "SURVIVAL").toUpperCase());
            this.infectedGamemode = GameMode.valueOf(infectionSettings.getString("infected_gamemode", "SURVIVAL").toUpperCase());
        } catch (IllegalArgumentException e) {
            this.logger.warning("Invalid gamemode in config for " + gameId + ". Defaulting to SURVIVAL.");
            this.survivorGamemode = GameMode.SURVIVAL;
            this.infectedGamemode = GameMode.SURVIVAL;
        }

        ConfigurationSection spawnsSection = config.getConfigurationSection("spawns");
        if (spawnsSection == null) { this.logger.severe("Missing 'spawns' for " + gameId + ". Disabling."); setGameState(GameState.DISABLED); return; }
        this.lobbySpawn = LocationUtil.loadLocation(spawnsSection.getConfigurationSection("lobby"), gameWorld, this.logger);
        if (spawnsSection.contains("game_area")) {
            this.gameSpawns = LocationUtil.loadLocationList(spawnsSection, "game_area", gameWorld, this.logger);
        } else {
            this.logger.warning("Spawn list 'spawns.game_area' is missing for game '" + gameId + "'. No game spawns loaded.");
            this.gameSpawns = new ArrayList<>();
        }

        if (this.lobbySpawn == null || this.gameSpawns.isEmpty()) {
            this.logger.severe("Lobby or game spawns missing/invalid for " + gameId + ". Disabling.");
            setGameState(GameState.DISABLED); return;
        }

        setupScoreboardTeams();

        if (this.gameState != GameState.DISABLED) {
            setGameState(GameState.WAITING);
            this.logger.info("Infection game '" + gameId + "' configured and ready. World: " + worldName);
        }
    }

    private void setupScoreboardTeams() {
        org.bukkit.scoreboard.Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        infectedTeamSpigot = mainScoreboard.getTeam("InfectedTeam_" + gameId);
        if (infectedTeamSpigot == null) {
            infectedTeamSpigot = mainScoreboard.registerNewTeam("InfectedTeam_" + gameId);
        }
        infectedTeamSpigot.setColor(ChatColor.RED);
        infectedTeamSpigot.setPrefix(ChatColor.RED + "[INFECTED] ");
        infectedTeamSpigot.setAllowFriendlyFire(false);
        infectedTeamSpigot.setCanSeeFriendlyInvisibles(true);

        survivorTeamSpigot = mainScoreboard.getTeam("SurvivorTeam_" + gameId);
        if (survivorTeamSpigot == null) {
            survivorTeamSpigot = mainScoreboard.registerNewTeam("SurvivorTeam_" + gameId);
        }
        survivorTeamSpigot.setColor(ChatColor.GREEN);
        survivorTeamSpigot.setPrefix(ChatColor.GREEN + "[SURVIVOR] ");
    }

    private void clearScoreboardTeams() {
        if (infectedTeamSpigot != null) {
            new ArrayList<>(infectedTeamSpigot.getEntries()).forEach(infectedTeamSpigot::removeEntry); // Iterate copy
        }
        if (survivorTeamSpigot != null) {
            new ArrayList<>(survivorTeamSpigot.getEntries()).forEach(survivorTeamSpigot::removeEntry); // Iterate copy
        }
    }

    @Override
    public void load() {
        if (this.gameState == GameState.UNINITIALIZED) {
            this.logger.warning("InfectionGame.load() called but game " + gameId + " is still UNINITIALIZED.");
            if (this.gameConfig == null) {
                FileConfiguration cfg = plugin.getConfigManager().getGameConfig("infection", this.gameId);
                if (cfg != null) { this.configure(cfg); }
                else { this.logger.severe("Could not retrieve config for " + gameId + ". Disabling."); setGameState(GameState.DISABLED); }
            }
        }
    }

    @Override
    public void unload() {
        this.logger.info("Unloading Infection game: " + gameId);
        cancelTasks();
        playerScoreboards.values().forEach(GameScoreboard::destroy);
        playerScoreboards.clear();
        HandlerList.unregisterAll(this);
        clearScoreboardTeams();
    }

    private void cancelTasks() {
        if (countdownTask != null && !countdownTask.isCancelled()) countdownTask.cancel();
        countdownTask = null;
        if (gameTimerTask != null && !gameTimerTask.isCancelled()) gameTimerTask.cancel();
        gameTimerTask = null;
    }

    public boolean start(boolean bypassMinPlayerCheck) {
        if (gameState == GameState.DISABLED) {
            this.logger.warning("Attempted to start Infection game '" + gameId + "' but it is DISABLED.");
            return false;
        }
        if (gameState != GameState.WAITING && gameState != GameState.ENDING) {
            this.logger.warning("Infection game " + gameId + " cannot start, current state: " + gameState); return false;
        }

        int minPlayers = gameConfig.getInt("infection_settings.min_players_to_start", 2);
        if (!bypassMinPlayerCheck && playersInGame.size() < minPlayers) {
            broadcastToGamePlayers(ChatColor.RED + "Not enough players to start! Need " + minPlayers + ", have " + playersInGame.size() + ".");
            if (bypassMinPlayerCheck) { this.logger.info("Admin bypassed min player check for " + gameId); }
            else { return false; }
        } else if (playersInGame.isEmpty() && !bypassMinPlayerCheck) {
            broadcastToGamePlayers(ChatColor.RED + "Cannot start with 0 players unless forced by an admin.");
            if (bypassMinPlayerCheck) { this.logger.info("Admin starting game " + gameId + " with 0 players."); }
            else { return false; }
        }

        setGameState(GameState.STARTING);
        infectedPlayers.clear();
        survivorPlayers.clear();
        survivorPlayers.addAll(playersInGame);
        this.timeRemainingSeconds = this.gameDurationSeconds; // Reset time remaining

        playersInGame.forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                playerStateManager.clearPlayerForGame(p, this.survivorGamemode);
                GameScoreboard sb = playerScoreboards.get(uuid);
                if (sb == null) {
                    sb = new GameScoreboard(p, scoreboardTitle);
                    playerScoreboards.put(uuid, sb);
                }
                sb.show();
                updateScoreboard(p);
            }
        });

        int spawnIndex = 0;
        for (UUID uuid : playersInGame) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (!gameSpawns.isEmpty()) {
                    player.teleport(gameSpawns.get(spawnIndex % gameSpawns.size())); spawnIndex++;
                } else { this.logger.warning("No game spawns for " + gameId + "!"); player.teleport(lobbySpawn); }
            }
        }
        startCountdown();
        return true;
    }

    @Override
    public boolean start() { return this.start(false); }

    private void startCountdown() {
        cancelTasks();
        final int[] currentCountdownValue = {countdownSeconds};
        broadcastToGamePlayers(ChatColor.GOLD + "INFECTION! " + ChatColor.YELLOW + "The game will begin soon. One of you is...");

        this.countdownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (gameState != GameState.STARTING) { cancelTasks(); return; }

            if (currentCountdownValue[0] > 0) {
                String titleMessage = ChatColor.RED + "GET READY!";
                String subtitleMessage = ChatColor.YELLOW.toString() + currentCountdownValue[0] + "...";
                for (UUID uuid : playersInGame) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.sendTitle(titleMessage, subtitleMessage, 0, 25, 5);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 1f, 0.8f + (0.1f * (countdownSeconds - currentCountdownValue[0])) );
                    }
                }
            }

            if (currentCountdownValue[0] <= 0) {
                if (countdownTask != null && !countdownTask.isCancelled()) countdownTask.cancel();
                countdownTask = null;
                selectInitialInfected();
                activateGame();
            }
            currentCountdownValue[0]--;
        }, 0L, 20L);
    }

    private void selectInitialInfected() {
        if (survivorPlayers.isEmpty()) {
            this.logger.warning("No survivors to select initial infected from in game " + gameId);
            stop(true); return;
        }
        List<UUID> potentialInfected = new ArrayList<>(survivorPlayers);
        Collections.shuffle(potentialInfected);
        for (int i = 0; i < initialInfectedCount && !potentialInfected.isEmpty(); i++) {
            UUID infectedUUID = potentialInfected.remove(0);
            infectPlayer(infectedUUID, null, false);
        }
        for(UUID infectedUUID : infectedPlayers) {
            Player p = Bukkit.getPlayer(infectedUUID);
            if(p != null) {
                p.sendTitle(ChatColor.RED + "YOU ARE INFECTED!", ChatColor.YELLOW + "Spread the infection!", 10, 70, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, SoundCategory.PLAYERS, 1f, 0.8f);
            }
        }
    }

    private void infectPlayer(UUID targetUUID, Player infector, boolean announce) {
        if (infectedPlayers.contains(targetUUID)) return;
        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        if (targetPlayer == null || !targetPlayer.isOnline() || !playersInGame.contains(targetUUID)) return;

        survivorPlayers.remove(targetUUID);
        infectedPlayers.add(targetUUID);

        targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false, false));
        targetPlayer.setGameMode(infectedGamemode);

        if (survivorTeamSpigot != null) survivorTeamSpigot.removeEntry(targetPlayer.getName());
        if (infectedTeamSpigot != null) infectedTeamSpigot.addEntry(targetPlayer.getName());

        targetPlayer.sendTitle(ChatColor.RED + "You have been INFECTED!", ChatColor.YELLOW + (infector != null ? "by " + infector.getName() : ""), 5, 40, 10);
        targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_ZOMBIE_INFECT, SoundCategory.PLAYERS, 1f, 1f);

        if (announce) {
            broadcastToGamePlayers(ChatColor.RED + targetPlayer.getName() + " has been infected" + (infector != null ? " by " + infector.getName() : "") + "!");
        }
        updateAllScoreboards();
        checkGameEnd();
    }

    private void activateGame() {
        if (gameState != GameState.STARTING) return;
        setGameState(GameState.ACTIVE);
        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
        broadcastToGamePlayers(ChatColor.RED + "" + ChatColor.BOLD + "The INFECTION has begun! RUN!");
        this.logger.info("Infection game " + gameId + " is now ACTIVE.");

        this.timeRemainingSeconds = this.gameDurationSeconds; // Ensure time is reset
        this.gameTimerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (gameState != GameState.ACTIVE) {
                if (gameTimerTask != null && !gameTimerTask.isCancelled()) gameTimerTask.cancel();
                gameTimerTask = null;
                return;
            }
            timeRemainingSeconds--; // Decrement time remaining
            updateAllScoreboards();

            if (timeRemainingSeconds <= 0) {
                broadcastToGamePlayers(ChatColor.GOLD + "Time's up! The infection could not be contained!");
                endGame(true); // Infected win if time runs out for survivors
            }
        }, 0L, 20L);
    }

    @Override
    public void stop(boolean force) {
        this.logger.info("Infection game " + gameId + " is stopping. Forced: " + force);
        GameState previousState = gameState;
        setGameState(GameState.ENDING);
        cancelTasks();
        HandlerList.unregisterAll(this);

        if (survivorPlayers.isEmpty() && !infectedPlayers.isEmpty() && previousState == GameState.ACTIVE) {
            broadcastToGamePlayers(ChatColor.RED + "" + ChatColor.BOLD + "THE INFECTED HAVE WON!");
        } else if (!survivorPlayers.isEmpty() && previousState == GameState.ACTIVE) {
            String survivorsString = survivorPlayers.stream().map(Bukkit::getPlayer).filter(p -> p != null && p.isOnline()).map(Player::getName).collect(Collectors.joining(", "));
            broadcastToGamePlayers(ChatColor.GREEN + "" + ChatColor.BOLD + "SURVIVORS WIN! Remaining: " + ChatColor.YELLOW + survivorsString);
        } else {
            broadcastToGamePlayers(ChatColor.YELLOW + "Infection game '" + gameId + "' ended.");
        }

        new HashSet<>(playersInGame).forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                GameScoreboard sb = playerScoreboards.remove(uuid);
                if (sb != null) sb.destroy();
                playerStateManager.restorePlayerState(player);
                if (lobbySpawn != null) player.teleport(lobbySpawn);
                player.removePotionEffect(PotionEffectType.GLOWING);
                if (infectedTeamSpigot != null) infectedTeamSpigot.removeEntry(player.getName());
                if (survivorTeamSpigot != null) survivorTeamSpigot.removeEntry(player.getName());
            } else {
                playerStateManager.removePlayerState(Bukkit.getOfflinePlayer(uuid).getPlayer());
            }
        });
        playersInGame.clear();
        infectedPlayers.clear();
        survivorPlayers.clear();

        if (this.arenaSchematicName != null && !this.arenaSchematicName.isEmpty() && this.arenaPasteLocation != null && this.gameState != GameState.DISABLED) {
            this.logger.info("Resetting arena for game " + gameId + ": " + this.arenaSchematicName);
            if (!plugin.getArenaManager().pasteSchematic(this.arenaSchematicName, this.arenaPasteLocation)) {
                this.logger.severe("CRITICAL: Failed to reset arena for game " + gameId + "!");
            } else { this.logger.info("Arena for " + gameId + " reset successfully."); }
        }
        setGameState(GameState.WAITING);
        this.logger.info("Infection game " + gameId + " is now WAITING.");
    }

    private void endGame(boolean infectedWonIfTimeUp) {
        if (gameState != GameState.ACTIVE) return;
        // This method is called by checkGameEnd or the timer.
        // The main stop(true) handles the full cleanup.
        // For now, just calling stop directly.
        stop(true);
    }

    @Override
    public boolean addPlayer(Player player) {
        if (gameState == GameState.DISABLED) { player.sendMessage(ChatColor.RED + "Game '" + gameId + "' is disabled."); return false; }
        if (gameState != GameState.WAITING) {
            player.sendMessage(ChatColor.RED + "Infection game '" + gameId + "' cannot be joined at this time."); return false;
        }
        if (playersInGame.contains(player.getUniqueId())) { player.sendMessage(ChatColor.YELLOW + "You are already in this game."); return false; }

        playerStateManager.savePlayerState(player);
        playersInGame.add(player.getUniqueId());

        if (lobbySpawn != null) { player.teleport(lobbySpawn); }
        else {
            this.logger.severe("Lobby spawn is null for game " + gameId + "! Cannot teleport player " + player.getName());
            player.sendMessage(ChatColor.RED + "Error: Lobby spawn not set for this game.");
            playersInGame.remove(player.getUniqueId());
            playerStateManager.restorePlayerState(player); return false;
        }
        player.sendMessage(ChatColor.GREEN + "You joined Infection game: " + gameId);
        broadcastToGamePlayers(ChatColor.AQUA + player.getName() + ChatColor.GRAY + " joined! (" + playersInGame.size() + " players)");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.5f);
        return true;
    }

    @Override
    public void removePlayer(Player player) {
        GameScoreboard sb = playerScoreboards.remove(player.getUniqueId());
        if (sb != null) sb.destroy();

        boolean wasInGame = playersInGame.remove(player.getUniqueId());
        infectedPlayers.remove(player.getUniqueId());
        survivorPlayers.remove(player.getUniqueId());

        playerStateManager.restorePlayerState(player);
        player.removePotionEffect(PotionEffectType.GLOWING);
        if (infectedTeamSpigot != null) infectedTeamSpigot.removeEntry(player.getName());
        if (survivorTeamSpigot != null) survivorTeamSpigot.removeEntry(player.getName());

        if (wasInGame) {
            player.sendMessage(ChatColor.GRAY + "You left Infection game: " + gameId);
            broadcastToGamePlayers(ChatColor.AQUA + player.getName() + ChatColor.GRAY + " has left the game.");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.5f, 1.0f);
            if (lobbySpawn != null && player.isOnline()) {
                if (!player.getWorld().equals(lobbySpawn.getWorld()) || player.getLocation().distanceSquared(lobbySpawn) > 225) {
                    player.teleport(lobbySpawn);
                }
            }
            if (gameState == GameState.ACTIVE || gameState == GameState.STARTING) {
                checkGameEnd();
            }
        }
    }

    @Override
    protected void gameTick() {
        // Main game timer is handled in activateGame's BukkitTask.
        // Scoreboard updates are also triggered by that task.
        // This method could be used for other periodic logic if needed in the future.
    }

    @EventHandler
    public void onPlayerDamageByPlayer(EntityDamageByEntityEvent event) {
        if (gameState != GameState.ACTIVE) return;
        if (!(event.getEntity() instanceof Player && event.getDamager() instanceof Player)) return;

        Player damaged = (Player) event.getEntity();
        Player damager = (Player) event.getDamager();

        if (!playersInGame.contains(damaged.getUniqueId()) || !playersInGame.contains(damager.getUniqueId())) {
            return;
        }

        if (infectedPlayers.contains(damager.getUniqueId()) && survivorPlayers.contains(damaged.getUniqueId())) {
            infectPlayer(damaged.getUniqueId(), damager, true);
            event.setDamage(0); // Prevent actual damage, just infect. Set to 0 instead of cancelling to allow knockback.
            // event.setCancelled(true); // Alternatively, cancel entirely if no knockback is desired.
        }
    }

    private void checkGameEnd() {
        if (gameState != GameState.ACTIVE) return;

        if (survivorPlayers.isEmpty() && !infectedPlayers.isEmpty() && !playersInGame.isEmpty()) {
            endGame(true); // Infected win
        } else if (survivorPlayers.size() == 1 && playersInGame.size() > 1 && !infectedPlayers.isEmpty()) {
            endGame(false); // Last survivor wins
        } else if (survivorPlayers.size() > 0 && infectedPlayers.isEmpty() && playersInGame.size() > 0 && initialInfectedCount > 0) {
            // This means all infected left or were never chosen, and game started.
            this.logger.info("Game " + gameId + " ending: No infected left, survivors win by default.");
            endGame(false); // Survivors win
        }
        // Timer will also call endGame if time runs out.
    }

    @Override
    public void broadcastToGamePlayers(String message) {
        String prefix = ChatColor.DARK_RED + "[INFECTION-" + gameId + "] " + ChatColor.RESET;
        for (UUID uuid : playersInGame) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) p.sendMessage(prefix + message);
        }
    }

    private void updateAllScoreboards() {
        for (UUID playerUUID : playersInGame) {
            Player p = Bukkit.getPlayer(playerUUID);
            if (p != null && p.isOnline()) { updateScoreboard(p); }
        }
    }

    private void updateScoreboard(Player player) {
        GameScoreboard sb = playerScoreboards.get(player.getUniqueId());
        if (sb == null) return;
        sb.clearAllLines();
        int line = 0;

        sb.setLine(line++, "&7Time Left: &e" + formatTime(this.timeRemainingSeconds));
        sb.setLine(line++, "&m--------------------");
        sb.setLine(line++, "&aSurvivors: &f" + survivorPlayers.size());
        sb.setLine(line++, "&cInfected: &f" + infectedPlayers.size());
        sb.setLine(line++, "&m--------------------");

        if (infectedPlayers.contains(player.getUniqueId())) {
            sb.setLine(line++, "&cYOU ARE INFECTED");
            sb.setLine(line++, "&eObjective: Infect survivors!");
        } else {
            sb.setLine(line++, "&aYOU ARE A SURVIVOR");
            sb.setLine(line++, "&eObjective: Survive!");
        }
    }

    private String formatTime(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    public World getGameWorld() {
        if (this.arenaPasteLocation != null && this.arenaPasteLocation.getWorld() != null) return this.arenaPasteLocation.getWorld();
        World foundWorld = Bukkit.getWorld(worldName);
        if (foundWorld == null && gameState != GameState.DISABLED) {
            this.logger.log(Level.WARNING, "getGameWorld() for " + gameId + ": world '" + worldName + "' not loaded!");
        }
        return foundWorld;
    }
}
