package io.mewb.andromedaGames.koth;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.game.Game;
import io.mewb.andromedaGames.game.GameState;
import io.mewb.andromedaGames.koth.votinghooks.HillZoneShrinkHook; // New Hook
import io.mewb.andromedaGames.koth.votinghooks.LaunchPadsHook;   // New Hook
import io.mewb.andromedaGames.koth.votinghooks.LowGravityHook;
import io.mewb.andromedaGames.koth.votinghooks.PlayerSwapHook;   // New Hook
import io.mewb.andromedaGames.koth.votinghooks.TntDropHook;
import io.mewb.andromedaGames.player.PlayerStateManager;
import io.mewb.andromedaGames.utils.GameScoreboard;
import io.mewb.andromedaGames.utils.LocationUtil;
import io.mewb.andromedaGames.utils.ParticleUtil;
import io.mewb.andromedaGames.voting.VoteManager;
import io.mewb.andromedaGames.voting.VotingHook;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class KoTHGame extends Game {

    // Game parameters
    private Location hillCenter;
    private int hillRadius; // Current operational hill radius
    private int originalHillRadius; // Stores the configured hill radius before any temporary changes
    private int hillRadiusSquared; // Current operational hill radius squared
    private int gameDurationSeconds;
    private int timeElapsedSeconds;
    private int minPlayersToStart;
    private int countdownSeconds;

    // Arena details
    private String arenaSchematicName;
    private Location arenaPasteLocation;
    private Location lobbySpawn;
    private List<Location> gameSpawns;
    private String worldName;

    // Game state tracking
    private final Map<UUID, Integer> playerScores;
    private BukkitTask gameTickTask;
    private BukkitTask countdownTask;
    private FileConfiguration gameConfig;
    private UUID playerCurrentlyOnHill = null;

    // Scoreboard Management
    private final Map<UUID, GameScoreboard> playerScoreboards = new HashMap<>();
    private String scoreboardTitle;

    // Voting System
    private VoteManager voteManager;
    private List<VotingHook> availableVotingHooks;
    private List<String> configuredHookIds;
    private boolean votingEnabled;
    private int voteIntervalSeconds;
    private int voteEventDurationSeconds;
    private long lastVoteTriggerTimeMillis;
    private final Random random = new Random();
    private VotingHook activeVotingHook = null;
    private long activeHookEndTimeMillis = 0;

    // Player State Management
    private final PlayerStateManager playerStateManager;
    private GameMode gamePlayGamemode = GameMode.SURVIVAL;


    public KoTHGame(AndromedaGames plugin, String gameId, String arenaId) {
        super(plugin, gameId, arenaId);
        this.playerStateManager = plugin.getPlayerStateManager();
        this.playersInGame = new HashSet<>();
        this.playerScores = new HashMap<>();
        this.gameSpawns = new ArrayList<>();
        this.availableVotingHooks = new ArrayList<>();
        this.configuredHookIds = new ArrayList<>();
        this.gameState = GameState.UNINITIALIZED;
    }

    public void configure(FileConfiguration config) {
        this.gameConfig = config;
        this.logger.info("Configuring KoTH game: " + gameId + " (Arena ID/Schematic: " + arenaId + ")");

        if (!config.getBoolean("enabled", false)) {
            this.logger.warning("KoTH game '" + gameId + "' is marked as disabled. Aborting configuration.");
            setGameState(GameState.DISABLED); return;
        }
        this.worldName = config.getString("world");
        if (this.worldName == null || this.worldName.isEmpty()) {
            this.logger.severe("World name not specified for KoTH game '" + gameId + "'. Disabling game.");
            setGameState(GameState.DISABLED); return;
        }
        World gameWorld = Bukkit.getWorld(worldName);
        if (gameWorld == null) {
            this.logger.severe("World '" + worldName + "' not found for KoTH game " + gameId + ". Disabling game.");
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

        ConfigurationSection kothSettings = config.getConfigurationSection("koth_settings");
        if (kothSettings == null) { this.logger.severe("Missing 'koth_settings' for " + gameId + ". Disabling."); setGameState(GameState.DISABLED); return; }
        this.hillCenter = LocationUtil.loadLocation(kothSettings.getConfigurationSection("hill_center"), gameWorld, this.logger);

        this.originalHillRadius = kothSettings.getInt("hill_radius", 5); // Load original radius
        this.hillRadius = this.originalHillRadius; // Set current operational radius to original
        this.hillRadiusSquared = this.hillRadius * this.hillRadius;

        this.gameDurationSeconds = kothSettings.getInt("game_duration_seconds", 300);
        this.minPlayersToStart = kothSettings.getInt("min_players_to_start", 2);
        this.countdownSeconds = kothSettings.getInt("countdown_seconds", 10);
        this.scoreboardTitle = ChatColor.translateAlternateColorCodes('&', kothSettings.getString("scoreboard_title", "&6&lKoTH: &e" + gameId));
        try {
            this.gamePlayGamemode = GameMode.valueOf(kothSettings.getString("gameplay_gamemode", "SURVIVAL").toUpperCase());
        } catch (IllegalArgumentException e) {
            this.logger.warning("Invalid gameplay_gamemode '" + kothSettings.getString("gameplay_gamemode") + "' in config for " + gameId + ". Defaulting to SURVIVAL.");
            this.gamePlayGamemode = GameMode.SURVIVAL;
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

        if (this.hillCenter == null || this.lobbySpawn == null || this.gameSpawns.isEmpty()) {
            this.logger.severe("Critical locations (hill, lobby, or game spawns) missing/invalid for " + gameId + ". Disabling.");
            setGameState(GameState.DISABLED); return;
        }

        ConfigurationSection votingConfig = config.getConfigurationSection("voting");
        if (votingConfig != null) {
            this.votingEnabled = votingConfig.getBoolean("enabled", false);
            this.voteIntervalSeconds = votingConfig.getInt("interval_seconds", 90);
            this.voteEventDurationSeconds = votingConfig.getInt("duration_seconds", 20);
            this.configuredHookIds = votingConfig.getStringList("hooks_available");
            this.voteManager = new VoteManager(plugin, this);
            initializeVotingHooks();
            this.logger.info("Voting system configured for " + gameId + ": Enabled=" + votingEnabled + ", " + availableVotingHooks.size() + " hooks loaded.");
        } else {
            this.votingEnabled = false;
            this.logger.info("No 'voting' configuration section for " + gameId + ". Voting disabled.");
        }

        if (this.gameState != GameState.DISABLED) {
            setGameState(GameState.WAITING);
            this.logger.info("KoTH game '" + gameId + "' configured and ready. World: " + worldName);
        }
    }

    private void initializeVotingHooks() {
        availableVotingHooks.clear();
        if (configuredHookIds == null) return;
        for (String hookId : configuredHookIds) {
            switch (hookId.toLowerCase()) {
                case "koth_tnt_drop": availableVotingHooks.add(new TntDropHook()); break;
                case "koth_low_gravity": availableVotingHooks.add(new LowGravityHook()); break;
                case "koth_hill_shrink": availableVotingHooks.add(new HillZoneShrinkHook()); break;
                case "koth_launch_pads": availableVotingHooks.add(new LaunchPadsHook()); break;
                case "koth_player_swap": availableVotingHooks.add(new PlayerSwapHook()); break;
                default: this.logger.warning("Unknown voting hook ID in config for " + gameId + ": " + hookId);
            }
        }
    }

    @Override
    public void load() {
        if (this.gameState == GameState.UNINITIALIZED) {
            this.logger.warning("KoTHGame.load() called but game " + gameId + " is still UNINITIALIZED.");
            if (this.gameConfig == null) {
                FileConfiguration cfg = plugin.getConfigManager().getGameConfig("koth", this.gameId);
                if (cfg != null) { this.configure(cfg); }
                else { this.logger.severe("Could not retrieve config for " + gameId + ". Disabling."); setGameState(GameState.DISABLED); }
            }
        }
    }

    @Override
    public void unload() {
        this.logger.info("Unloading KoTH game: " + gameId);
        cancelTasks();
        playerScoreboards.values().forEach(GameScoreboard::destroy);
        playerScoreboards.clear();
    }

    private void cancelTasks() {
        if (countdownTask != null && !countdownTask.isCancelled()) countdownTask.cancel();
        countdownTask = null;
        if (gameTickTask != null && !gameTickTask.isCancelled()) gameTickTask.cancel();
        gameTickTask = null;
        if (voteManager != null && voteManager.isVoteActive()) voteManager.endVote(false);
    }

    public boolean start(boolean bypassMinPlayerCheck) {
        if (gameState == GameState.DISABLED) {
            this.logger.warning("Attempted to start KoTH game '" + gameId + "' but it is DISABLED.");
            broadcastToGamePlayers(ChatColor.RED + "This game is currently disabled.");
            return false;
        }
        if (gameState != GameState.WAITING && gameState != GameState.ENDING) {
            this.logger.warning("KoTH game " + gameId + " cannot start, current state: " + gameState); return false;
        }
        if (!bypassMinPlayerCheck && playersInGame.size() < minPlayersToStart) {
            broadcastToGamePlayers(ChatColor.RED + "Not enough players to start! Need " + minPlayersToStart + ", have " + playersInGame.size() + ".");
            if (bypassMinPlayerCheck) { this.logger.info("Admin bypassed min player check for " + gameId); }
            else { return false; }
        } else if (playersInGame.isEmpty() && !bypassMinPlayerCheck) {
            broadcastToGamePlayers(ChatColor.RED + "Cannot start with 0 players unless forced by an admin.");
            if (bypassMinPlayerCheck) { this.logger.info("Admin starting game " + gameId + " with 0 players."); }
            else { return false; }
        }

        setGameState(GameState.STARTING);
        playerScores.clear();
        playerCurrentlyOnHill = null;
        activeVotingHook = null;
        activeHookEndTimeMillis = 0;
        this.hillRadius = this.originalHillRadius; // Ensure hill radius is reset to original on game start
        this.hillRadiusSquared = this.hillRadius * this.hillRadius;


        playersInGame.forEach(uuid -> {
            playerScores.put(uuid, 0);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                playerStateManager.clearPlayerForGame(p, this.gamePlayGamemode);
                GameScoreboard sb = playerScoreboards.get(uuid);
                if (sb == null) {
                    sb = new GameScoreboard(p, scoreboardTitle);
                    playerScoreboards.put(uuid, sb);
                }
                sb.show();
                updateScoreboard(p);
            }
        });
        timeElapsedSeconds = 0;
        lastVoteTriggerTimeMillis = System.currentTimeMillis();

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
        for (UUID uuid : playersInGame) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendTitle(ChatColor.GREEN + "Game Starting!", ChatColor.YELLOW + "Get ready...", 10, 70, 20);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 1f, 0.8f);
            }
        }
        this.countdownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (gameState != GameState.STARTING) { cancelTasks(); return; }
            String titleMessage = ChatColor.YELLOW.toString() + currentCountdownValue[0];
            if (currentCountdownValue[0] <= 0) { titleMessage = ChatColor.GREEN + "GO!"; }
            for (UUID uuid : playersInGame) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendTitle(titleMessage, "", 0, 25, 5);
                    if (currentCountdownValue[0] > 0 && currentCountdownValue[0] <= 3) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 1f, 1f + (0.2f * (3 - currentCountdownValue[0])) );
                    } else if (currentCountdownValue[0] == 0) {
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1f, 1.2f);
                    }
                }
            }
            if (currentCountdownValue[0] > 0) {
                broadcastToGamePlayers(ChatColor.GREEN + "Starting in " + ChatColor.YELLOW + currentCountdownValue[0] + "...");
            }
            if (currentCountdownValue[0] <= 0) {
                if (countdownTask != null && !countdownTask.isCancelled()) countdownTask.cancel();
                countdownTask = null;
                activateGame();
            }
            currentCountdownValue[0]--;
        }, 0L, 20L);
    }

    private void activateGame() {
        if (gameState != GameState.STARTING) return;
        setGameState(GameState.ACTIVE);
        broadcastToGamePlayers(ChatColor.GOLD + "" + ChatColor.BOLD + "KoTH game '" + gameId + "' has started! Capture the hill!");
        this.logger.info("KoTH game " + gameId + " is now ACTIVE.");
        this.gameTickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::gameTick, 0L, 20L);
        lastVoteTriggerTimeMillis = System.currentTimeMillis();
        updateAllScoreboards();
    }

    @Override
    public void stop(boolean force) {
        if (gameState != GameState.ACTIVE && gameState != GameState.STARTING && !force) {
            if (!force) return;
        }
        this.logger.info("KoTH game " + gameId + " is stopping. Forced: " + force);
        GameState previousState = gameState;
        setGameState(GameState.ENDING);
        cancelTasks();

        UUID winnerUUID = null;
        String winnerName = "No one";
        if (previousState == GameState.ACTIVE || (force && !playerScores.isEmpty())) {
            int maxScore = -1;
            for (Map.Entry<UUID, Integer> entry : playerScores.entrySet()) {
                if (entry.getValue() > maxScore) { maxScore = entry.getValue(); winnerUUID = entry.getKey(); }
            }
            if (winnerUUID != null) {
                Player winnerPlayer = Bukkit.getPlayer(winnerUUID);
                winnerName = (winnerPlayer != null && winnerPlayer.isOnline()) ? winnerPlayer.getName() : "An unknown player";
                broadcastToGamePlayers(ChatColor.GOLD + winnerName + " has won KoTH '" + gameId + "' with " + maxScore + " seconds on the hill!");
            } else if (!playersInGame.isEmpty()) {
                broadcastToGamePlayers(ChatColor.YELLOW + "KoTH game '" + gameId + "' ended. No winner could be determined.");
            } else if (previousState != GameState.WAITING && previousState != GameState.ENDING) {
                broadcastToGamePlayers(ChatColor.YELLOW + "KoTH game '" + gameId + "' ended as there were no players.");
            }
        }
        for (UUID uuid : new HashSet<>(playersInGame)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendTitle(ChatColor.RED + "Game Over!", ChatColor.GOLD + winnerName + " wins!", 10, 70, 20);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1f, 1f);
            }
        }

        new HashSet<>(playersInGame).forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                GameScoreboard sb = playerScoreboards.remove(uuid);
                if (sb != null) sb.destroy();
                playerStateManager.restorePlayerState(player);
                if (lobbySpawn != null) player.teleport(lobbySpawn);
            } else {
                playerStateManager.removePlayerState(Bukkit.getOfflinePlayer(uuid).getPlayer());
            }
        });
        playersInGame.clear();

        if (this.arenaSchematicName != null && !this.arenaSchematicName.isEmpty() && this.arenaPasteLocation != null && this.gameState != GameState.DISABLED) {
            this.logger.info("Resetting arena for game " + gameId + ": " + this.arenaSchematicName);
            if (!plugin.getArenaManager().pasteSchematic(this.arenaSchematicName, this.arenaPasteLocation)) {
                this.logger.severe("CRITICAL: Failed to reset arena for game " + gameId + "!");
            } else { this.logger.info("Arena for " + gameId + " reset successfully."); }
        }
        setGameState(GameState.WAITING);
        this.logger.info("KoTH game " + gameId + " is now WAITING.");
    }

    @Override
    public boolean addPlayer(Player player) {
        if (gameState == GameState.DISABLED) { player.sendMessage(ChatColor.RED + "The game '" + gameId + "' is currently disabled."); return false; }
        if (gameState != GameState.WAITING && gameState != GameState.STARTING) { player.sendMessage(ChatColor.RED + "The game '" + gameId + "' has already started or is ending."); return false; }
        if (playersInGame.contains(player.getUniqueId())) { player.sendMessage(ChatColor.YELLOW + "You are already in this game."); return false; }

        playerStateManager.savePlayerState(player);
        playersInGame.add(player.getUniqueId());
        playerScores.put(player.getUniqueId(), 0);
        if (lobbySpawn != null) { player.teleport(lobbySpawn); }
        else {
            this.logger.severe("Lobby spawn is null for game " + gameId + "! Cannot teleport player " + player.getName());
            player.sendMessage(ChatColor.RED + "Error: Lobby spawn not set for this game.");
            playersInGame.remove(player.getUniqueId()); playerScores.remove(player.getUniqueId());
            playerStateManager.restorePlayerState(player); return false;
        }

        if (gameState == GameState.STARTING || gameState == GameState.ACTIVE) {
            playerStateManager.clearPlayerForGame(player, this.gamePlayGamemode);
            GameScoreboard sb = new GameScoreboard(player, scoreboardTitle);
            playerScoreboards.put(player.getUniqueId(), sb);
            sb.show();
            updateScoreboard(player);
        }
        player.sendMessage(ChatColor.GREEN + "You have joined KoTH game: " + gameId);
        broadcastToGamePlayers(ChatColor.AQUA + player.getName() + ChatColor.GRAY + " has joined the KoTH game! (" + playersInGame.size() + " players)");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.5f);
        return true;
    }

    @Override
    public void removePlayer(Player player) {
        GameScoreboard sb = playerScoreboards.remove(player.getUniqueId());
        if (sb != null) sb.destroy();
        boolean wasInGame = playersInGame.remove(player.getUniqueId());
        playerScores.remove(player.getUniqueId());
        playerStateManager.restorePlayerState(player);

        if (wasInGame) {
            player.sendMessage(ChatColor.GRAY + "You have left KoTH game: " + gameId);
            broadcastToGamePlayers(ChatColor.AQUA + player.getName() + ChatColor.GRAY + " has left the KoTH game.");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.5f, 1.0f);

            if (lobbySpawn != null && player.isOnline()) {
                if (!player.getWorld().equals(lobbySpawn.getWorld()) || player.getLocation().distanceSquared(lobbySpawn) > 225) {
                    player.teleport(lobbySpawn);
                }
            }
            if ((gameState == GameState.ACTIVE || gameState == GameState.STARTING)) {
                if (playersInGame.isEmpty() && minPlayersToStart > 0) {
                    broadcastToGamePlayers(ChatColor.YELLOW + "The last player left. The game is ending."); stop(false);
                } else if (playersInGame.size() < minPlayersToStart && minPlayersToStart > 1) {
                    broadcastToGamePlayers(ChatColor.RED + "Not enough players to continue. The game is ending."); stop(false);
                }
            }
        }
    }

    @Override
    protected void gameTick() {
        if (gameState != GameState.ACTIVE) {
            if (gameTickTask != null && !gameTickTask.isCancelled()) gameTickTask.cancel();
            gameTickTask = null; return;
        }
        timeElapsedSeconds++;
        if (timeElapsedSeconds >= gameDurationSeconds) {
            broadcastToGamePlayers(ChatColor.GOLD + "Time's up!"); stop(false); return;
        }

        if (activeVotingHook != null && activeHookEndTimeMillis > 0 && System.currentTimeMillis() >= activeHookEndTimeMillis) {
            broadcastToGamePlayers(ChatColor.YELLOW + activeVotingHook.getDisplayName() + " has worn off!");
            if (getHillCenter() != null) { // Ensure hillCenter is not null
                ParticleUtil.spawnLocationEffect(getHillCenter(), Particle.SMOKE, 50, 0.5, 1, 0.5, 0.1);
                getHillCenter().getWorld().playSound(getHillCenter(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.AMBIENT, 0.7f, 1f);
            }
            activeVotingHook = null; activeHookEndTimeMillis = 0;
        }

        if (votingEnabled && voteManager != null && !voteManager.isVoteActive() && !availableVotingHooks.isEmpty()) {
            if ((System.currentTimeMillis() - lastVoteTriggerTimeMillis) / 1000 >= voteIntervalSeconds) {
                triggerVote();
            }
        }

        String currentHillHolderName = null;
        UUID newHillHolderUUID = null;

        for (UUID uuid : playersInGame) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (isPlayerOnHill(player)) {
                    playerScores.put(uuid, playerScores.getOrDefault(uuid, 0) + 1);
                    currentHillHolderName = player.getName();
                    newHillHolderUUID = uuid;
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.GREEN + "You are capturing the hill! Score: " + playerScores.get(uuid)));
                    ParticleUtil.spawnPlayerStatusParticles(player, Particle.HAPPY_VILLAGER, 5, 0.3, 0.5, 0.3, 0.01);
                    break;
                }
            }
        }

        if (newHillHolderUUID != null && !newHillHolderUUID.equals(playerCurrentlyOnHill)) {
            Player newCapper = Bukkit.getPlayer(newHillHolderUUID);
            if (newCapper != null) {
                broadcastToGamePlayers(ChatColor.GOLD + newCapper.getName() + " has captured the hill!");
                newCapper.playSound(newCapper.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 1.2f);
                if (getHillCenter() != null) getHillCenter().getWorld().playSound(getHillCenter(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.AMBIENT, 0.8f, 1.5f);
            }
            playerCurrentlyOnHill = newHillHolderUUID;
        } else if (newHillHolderUUID == null && playerCurrentlyOnHill != null) {
            broadcastToGamePlayers(ChatColor.YELLOW + "The hill is now neutral!");
            if (getHillCenter() != null) getHillCenter().getWorld().playSound(getHillCenter(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 0.8f, 1.0f);
            playerCurrentlyOnHill = null;
        }

        updateAllScoreboards(currentHillHolderName);

        if (timeElapsedSeconds > 0 && timeElapsedSeconds % 30 == 0) {
            broadcastToGamePlayers(ChatColor.YELLOW + "KoTH: " + ChatColor.AQUA + (gameDurationSeconds - timeElapsedSeconds) + "s" + ChatColor.YELLOW + " remaining.");
        }
    }

    private void triggerVote() {
        if (availableVotingHooks.isEmpty() || voteManager == null) {
            this.logger.fine("TriggerVote called for " + gameId + " but no hooks/manager available.");
            return;
        }
        this.logger.info("Attempting to trigger vote for " + gameId);
        List<VotingHook> optionsForVote = new ArrayList<>();
        List<VotingHook> hooksPool = new ArrayList<>(availableVotingHooks);
        Collections.shuffle(hooksPool);
        int numberOfOptionsToPresent = Math.min(hooksPool.size(), 3);
        if (numberOfOptionsToPresent < 2 && hooksPool.size() >=2) { numberOfOptionsToPresent = 2; }
        else if (hooksPool.size() < 2) {
            this.logger.warning("Not enough unique voting hooks for " + gameId + " (need >= 2, have " + hooksPool.size() + ")");
            lastVoteTriggerTimeMillis = System.currentTimeMillis(); return;
        }
        for (int i = 0; i < numberOfOptionsToPresent && !hooksPool.isEmpty(); ) {
            VotingHook selectedHook = hooksPool.remove(0);
            if (selectedHook.canApply(this)) { optionsForVote.add(selectedHook); i++; }
        }
        if (optionsForVote.size() < 2) {
            this.logger.warning("Could not find enough applicable hooks for " + gameId + " (found " + optionsForVote.size() + ").");
            lastVoteTriggerTimeMillis = System.currentTimeMillis(); return;
        }
        if (voteManager.startVote(optionsForVote, voteEventDurationSeconds)) {
            lastVoteTriggerTimeMillis = System.currentTimeMillis();
        } else {
            this.logger.warning("VoteManager failed to start vote for " + gameId);
            lastVoteTriggerTimeMillis = System.currentTimeMillis();
        }
    }

    private boolean isPlayerOnHill(Player player) {
        if (hillCenter == null || !player.getWorld().equals(hillCenter.getWorld())) return false;
        Location playerFootLoc = player.getLocation();
        if (Math.abs(playerFootLoc.getY() - hillCenter.getY()) <= 1.5) {
            double dx = playerFootLoc.getX() - hillCenter.getX();
            double dz = playerFootLoc.getZ() - hillCenter.getZ();
            return (dx * dx + dz * dz) <= hillRadiusSquared;
        }
        return false;
    }

    @Override
    public void broadcastToGamePlayers(String message) {
        String prefix = ChatColor.DARK_AQUA + "[KoTH-" + gameId + "] " + ChatColor.RESET;
        for (UUID uuid : playersInGame) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) p.sendMessage(prefix + message);
        }
    }

    private void updateAllScoreboards() { updateAllScoreboards(null); }
    private void updateAllScoreboards(String playerOnHillName) {
        for (UUID playerUUID : playersInGame) {
            Player p = Bukkit.getPlayer(playerUUID);
            if (p != null && p.isOnline()) { updateScoreboard(p, playerOnHillName); }
        }
    }

    private void updateScoreboard(Player player) { updateScoreboard(player, null); }
    private void updateScoreboard(Player player, String playerOnHillName) {
        GameScoreboard sb = playerScoreboards.get(player.getUniqueId());
        if (sb == null) return;
        sb.clearAllLines();
        int line = 0;
        sb.setLine(line++, "&7Time Left: &e" + formatTime(gameDurationSeconds - timeElapsedSeconds));
        sb.setLine(line++, "&7Your Score: &a" + playerScores.getOrDefault(player.getUniqueId(), 0));
        if (playerOnHillName != null) { sb.setLine(line++, "&7On Hill: &6" + playerOnHillName); }
        else { sb.setLine(line++, "&7On Hill: &cNone"); }
        sb.setLine(line++, "&m--------------------");
        List<Map.Entry<UUID, Integer>> sortedScores = new ArrayList<>(playerScores.entrySet());
        sortedScores.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        sb.setLine(line++, "&bTop Players:");
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sortedScores) {
            if (rank > 3) break;
            Player p = Bukkit.getPlayer(entry.getKey());
            String name = (p != null && p.isOnline()) ? p.getName() : "Player";
            sb.setLine(line++, "&7" + rank + ". &f" + name + ": &e" + entry.getValue());
            rank++;
        }
        while (rank <=3) { sb.setLine(line++, "&7" + rank + ". &8---"); rank++; }
        if (activeVotingHook != null) {
            sb.setLine(line++, "&m--------------------");
            sb.setLine(line++, "&dEvent: &f" + activeVotingHook.getDisplayName());
            if (activeHookEndTimeMillis > 0) {
                long hookTimeLeft = (activeHookEndTimeMillis - System.currentTimeMillis()) / 1000;
                if (hookTimeLeft > 0) { sb.setLine(line++, "&dTime Left: &f" + formatTime((int)hookTimeLeft)); }
            }
        }
    }

    private String formatTime(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void setActiveVotingHook(VotingHook hook) {
        this.activeVotingHook = hook;
        if (hook != null) {
            this.logger.info("Activating voting hook: " + hook.getDisplayName() + " for game " + gameId);
            if (getHillCenter() != null) {
                ParticleUtil.spawnExplosionEffect(getHillCenter().clone().add(0,1,0), Particle.LAVA, 70, 0.7f);
                ParticleUtil.spawnHelixEffect(getHillCenter().clone().add(0,0.5,0), Particle.FLAME, 1.5, 3, 20, 2);
                getHillCenter().getWorld().playSound(getHillCenter(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.AMBIENT, 0.5f, 1.2f);
            }
            if (hook.getDurationSeconds() > 0) {
                this.activeHookEndTimeMillis = System.currentTimeMillis() + (hook.getDurationSeconds() * 1000L);
            } else {
                this.activeHookEndTimeMillis = 0;
            }
        } else {
            this.activeHookEndTimeMillis = 0;
        }
        updateAllScoreboards();
    }

    /**
     * Sets the hill radius temporarily, e.g., for a voting hook.
     * Stores the original radius if this is the first shrink, and reverts to it if 'shrinking' is false.
     * @param newRadius The new radius for the hill.
     */
    public void setTemporaryHillRadius(int newRadius) {
        this.logger.info("Setting temporary hill radius for " + gameId + " to " + newRadius + ". Original was " + this.originalHillRadius);
        this.hillRadius = newRadius;
        this.hillRadiusSquared = newRadius * newRadius;
        // Scoreboard will update on next tick to reflect any changes if needed.
        // Or force an update: updateAllScoreboards(playerCurrentlyOnHill != null ? Bukkit.getPlayer(playerCurrentlyOnHill).getName() : null);
    }


    public Location getHillCenter() { return this.hillCenter; }
    public int getHillRadius() { return this.hillRadius; } // Returns current operational radius
    public VoteManager getVoteManager() { return this.voteManager; }

    public void setHillLocation(Location location) {
        if (location == null || gameConfig == null) { this.logger.warning("Attempt to set null location or gameConfig is null for " + gameId); return; }
        this.hillCenter = location.clone();
        ConfigurationSection kothSettingsSection = gameConfig.getConfigurationSection("koth_settings");
        if (kothSettingsSection == null) kothSettingsSection = gameConfig.createSection("koth_settings");
        ConfigurationSection hillSection = kothSettingsSection.getConfigurationSection("hill_center");
        if (hillSection == null) hillSection = kothSettingsSection.createSection("hill_center");
        LocationUtil.saveLocation(hillSection, this.hillCenter, false);
        plugin.getConfigManager().saveGameConfig("koth", this.gameId, gameConfig);
        this.logger.info("KoTH '" + gameId + "' hill center set and saved: " + location.toString());
        broadcastToGamePlayers(ChatColor.YELLOW + "Admin: Hill location updated.");
    }

    public void setHillRadius(int radius) { // This sets the permanent original radius
        if (radius <= 0 || gameConfig == null) { this.logger.warning("Attempt to set invalid radius ("+radius+") or gameConfig is null for " + gameId); return; }
        this.originalHillRadius = radius; // Update the original radius
        this.hillRadius = radius;         // Also update current operational radius
        this.hillRadiusSquared = radius * radius;
        gameConfig.set("koth_settings.hill_radius", radius);
        plugin.getConfigManager().saveGameConfig("koth", this.gameId, gameConfig);
        this.logger.info("KoTH '" + gameId + "' base hill radius set and saved: " + radius);
        broadcastToGamePlayers(ChatColor.YELLOW + "Admin: Hill radius updated to " + radius + ".");
    }

    @Override
    public World getGameWorld() {
        if (this.arenaPasteLocation != null && this.arenaPasteLocation.getWorld() != null) return this.arenaPasteLocation.getWorld();
        if (this.hillCenter != null && this.hillCenter.getWorld() != null) return this.hillCenter.getWorld();
        World foundWorld = Bukkit.getWorld(worldName);
        if (foundWorld == null && gameState != GameState.DISABLED) {
            this.logger.log(Level.WARNING, "getGameWorld() for " + gameId + ": world '" + worldName + "' not loaded!");
        }
        return foundWorld;
    }
}
