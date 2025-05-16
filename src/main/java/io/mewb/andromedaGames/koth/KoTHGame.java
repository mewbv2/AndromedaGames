package io.mewb.andromedaGames.koth;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.arena.ArenaDefinition;
import io.mewb.andromedaGames.game.GameDefinition;
import io.mewb.andromedaGames.game.GameInstance;
import io.mewb.andromedaGames.game.GameState;
import io.mewb.andromedaGames.koth.votinghooks.HillZoneShrinkHook;
import io.mewb.andromedaGames.koth.votinghooks.LaunchPadsHook;
import io.mewb.andromedaGames.koth.votinghooks.LowGravityHook;
import io.mewb.andromedaGames.koth.votinghooks.PlayerSwapHook;
import io.mewb.andromedaGames.koth.votinghooks.TntDropHook;
// PlayerStateManager is inherited from GameInstance
import io.mewb.andromedaGames.utils.GameScoreboard;
// LocationUtil might not be directly needed if using RelativeLocation resolution from GameInstance
import io.mewb.andromedaGames.utils.ParticleUtil;
// RelativeLocation is used by GameInstance's getAbsoluteLocation helper
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
import org.bukkit.configuration.ConfigurationSection; // For parsing voting config from GameDefinition
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

public class KoTHGame extends GameInstance {

    // Game parameters - now mostly from GameDefinition and ArenaDefinition
    private Location absoluteHillCenter;
    private int currentHillRadius;
    private int originalHillRadius; // From definition's rules
    private int currentHillRadiusSquared;

    private int gameDurationSeconds;
    private int timeElapsedSeconds;
    private int minPlayersToStart;
    private int countdownSeconds;
    private GameMode gameplayGamemode;
    private String scoreboardTitle;


    // Arena details - from ArenaDefinition + instanceBaseWorldLocation
    private Location absoluteLobbySpawn;
    private List<Location> absoluteGameSpawns;

    // Game state tracking
    private final Map<UUID, Integer> playerScores;
    private BukkitTask gameTickTask;
    private BukkitTask countdownTask;
    private UUID playerCurrentlyOnHill = null;


    public KoTHGame(AndromedaGames plugin, UUID instanceId, GameDefinition definition, ArenaDefinition arena, Location instanceBaseWorldLocation) {
        super(plugin, instanceId, definition, arena, instanceBaseWorldLocation);
        this.playersInGame = new HashSet<>();
        this.playerScores = new HashMap<>();
        this.absoluteGameSpawns = new ArrayList<>();
        this.availableVotingHooks = new ArrayList<>(); // Inherited from GameInstance
        // Fields will be set in setupInstance()
    }

    @Override
    public void setupInstance() {
        this.logger.info("[KoTHInstance:" + instanceId.toString().substring(0,8) + "] Setting up with definition '" + definition.getDefinitionId() + "' and arena '" + arena.getArenaId() + "'.");

        // Load rules from GameDefinition
        this.gameDurationSeconds = definition.getRule("game_duration_seconds", 300);
        this.minPlayersToStart = definition.getRule("min_players_to_start", 2);
        this.countdownSeconds = definition.getRule("countdown_seconds", 10);
        this.originalHillRadius = definition.getRule("hill_radius", 5);
        this.currentHillRadius = this.originalHillRadius;
        this.currentHillRadiusSquared = this.currentHillRadius * this.currentHillRadius;
        this.scoreboardTitle = ChatColor.translateAlternateColorCodes('&', definition.getRule("scoreboard_title", "&6&lKoTH: &e" + definition.getDisplayName()));
        try {
            this.gameplayGamemode = GameMode.valueOf(definition.getRule("gameplay_gamemode", "SURVIVAL").toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            this.logger.warning("Invalid gameplay_gamemode in definition for " + definition.getDefinitionId() + ". Defaulting to SURVIVAL.");
            this.gameplayGamemode = GameMode.SURVIVAL;
        }

        // Load locations from ArenaDefinition, making them absolute using helpers from GameInstance
        this.absoluteHillCenter = getAbsoluteLocation("hill_center");
        this.absoluteLobbySpawn = getAbsoluteLocation("lobby_spawn");
        this.absoluteGameSpawns = getAbsoluteLocationList("game_spawns"); // Key "game_spawns" must be a list of relative locations

        if (this.absoluteHillCenter == null || this.absoluteHillCenter.equals(this.instanceBaseWorldLocation) && !"hill_center".equals(this.arena.getRelativeLocation("hill_center"))) {
            this.logger.severe("Critical: Hill center could not be resolved for instance " + instanceId + " using arena " + arena.getArenaId() + ". Key 'hill_center' might be missing in arena definition. Disabling instance.");
            setGameState(GameState.DISABLED); return;
        }
        if (this.absoluteLobbySpawn == null || this.absoluteLobbySpawn.equals(this.instanceBaseWorldLocation) && !"lobby_spawn".equals(this.arena.getRelativeLocation("lobby_spawn"))) {
            this.logger.severe("Critical: Lobby spawn could not be resolved for instance " + instanceId + ". Key 'lobby_spawn' might be missing. Disabling instance.");
            setGameState(GameState.DISABLED); return;
        }
        if (this.absoluteGameSpawns.isEmpty()) {
            this.logger.severe("Critical: No game spawns could be resolved for instance " + instanceId + ". Key 'game_spawns' might be missing or empty in arena definition. Disabling instance.");
            setGameState(GameState.DISABLED); return;
        }

        this.logger.info("[KoTHInstance:" + instanceId.toString().substring(0,8) + "] Hill Center: " + absoluteHillCenter.toString());
        this.logger.info("[KoTHInstance:" + instanceId.toString().substring(0,8) + "] Lobby Spawn: " + absoluteLobbySpawn.toString());
        for(Location l : absoluteGameSpawns) this.logger.finer("[KoTHInstance:" + instanceId.toString().substring(0,8) + "] Game Spawn: " + l.toString());

        // Voting System Configuration from GameDefinition
        ConfigurationSection votingConfigSection = definition.getVotingConfig();
        if (votingConfigSection != null) {
            this.votingEnabled = votingConfigSection.getBoolean("enabled", false); // Inherited field
            this.voteIntervalSeconds = votingConfigSection.getInt("interval_seconds", 90); // Inherited
            this.voteEventDurationSeconds = votingConfigSection.getInt("duration_seconds", 20); // Inherited
            List<String> hookIdsFromDef = votingConfigSection.getStringList("hooks_available");
            if (hookIdsFromDef != null) {
                initializeVotingHooksFromDef(hookIdsFromDef); // Populates this.availableVotingHooks
            }
            if (this.votingEnabled && !this.availableVotingHooks.isEmpty()) {
                this.voteManager = new VoteManager(plugin, this); // Inherited field, pass this GameInstance
                this.logger.info("Voting system configured for KoTH instance " + instanceId.toString().substring(0,8) + ": Enabled=" + votingEnabled + ", " + availableVotingHooks.size() + " hooks loaded.");
            } else {
                this.votingEnabled = false;
                this.logger.info("Voting system disabled for KoTH instance " + instanceId.toString().substring(0,8) + " (not enabled in def or no hooks).");
            }
        } else {
            this.votingEnabled = false;
            this.logger.info("No 'default_voting_hooks' configuration section found for KoTH definition " + definition.getDefinitionId() + ". Voting will be disabled for instance " + instanceId.toString().substring(0,8));
        }

        if (this.gameState != GameState.DISABLED) {
            setGameState(GameState.WAITING);
            this.logger.info("KoTH instance '" + instanceId.toString().substring(0,8) + "' (Def: " + definition.getDefinitionId() + ") setup complete and ready.");
        }
    }

    private void initializeVotingHooksFromDef(List<String> hookIds) {
        availableVotingHooks.clear(); // inherited from GameInstance
        if (hookIds == null) return;
        for (String hookId : hookIds) {
            switch (hookId.toLowerCase()) {
                case "koth_tnt_drop": availableVotingHooks.add(new TntDropHook()); break;
                case "koth_low_gravity": availableVotingHooks.add(new LowGravityHook()); break;
                case "koth_hill_shrink": availableVotingHooks.add(new HillZoneShrinkHook()); break;
                case "koth_launch_pads": availableVotingHooks.add(new LaunchPadsHook()); break;
                case "koth_player_swap": availableVotingHooks.add(new PlayerSwapHook()); break;
                default: this.logger.warning("Unknown voting hook ID in GameDefinition " + definition.getDefinitionId() + ": " + hookId);
            }
        }
    }

    @Override
    public void cleanupInstance() {
        this.logger.info("Cleaning up KoTH instance: " + instanceId.toString().substring(0,8));
        cancelTasks();
        playerScoreboards.values().forEach(GameScoreboard::destroy);
        playerScoreboards.clear();
        // ArenaManager will handle arena reset/deletion based on instanceBaseWorldLocation
    }

    private void cancelTasks() {
        if (countdownTask != null && !countdownTask.isCancelled()) countdownTask.cancel();
        countdownTask = null;
        if (gameTickTask != null && !gameTickTask.isCancelled()) gameTickTask.cancel();
        gameTickTask = null;
        if (voteManager != null && voteManager.isVoteActive()) voteManager.endVote(false);
    }

    @Override
    public boolean start(boolean bypassMinPlayerCheck) {
        if (gameState == GameState.DISABLED) {
            this.logger.warning("Attempted to start KoTH instance '" + instanceId.toString().substring(0,8) + "' but it is DISABLED.");
            return false;
        }
        if (gameState != GameState.WAITING && gameState != GameState.ENDING) {
            this.logger.warning("KoTH instance " + instanceId.toString().substring(0,8) + " cannot start, current state: " + gameState); return false;
        }
        // Get minPlayers from definition
        int minPlayersRequired = definition.getRule("min_players_to_start", 2);

        if (!bypassMinPlayerCheck && playersInGame.size() < minPlayersRequired) {
            broadcastToGamePlayers(ChatColor.RED + "Not enough players to start! Need " + minPlayersRequired + ", have " + playersInGame.size() + ".");
            if (bypassMinPlayerCheck) { this.logger.info("Admin bypassed min player check for instance " + instanceId.toString().substring(0,8)); }
            else { return false; }
        } else if (playersInGame.isEmpty() && !bypassMinPlayerCheck) {
            broadcastToGamePlayers(ChatColor.RED + "Cannot start with 0 players unless forced by an admin.");
            if (bypassMinPlayerCheck) { this.logger.info("Admin starting instance " + instanceId.toString().substring(0,8) + " with 0 players."); }
            else { return false; }
        }

        setGameState(GameState.STARTING);
        playerScores.clear();
        playerCurrentlyOnHill = null;
        activeVotingHook = null; // Inherited from GameInstance
        activeHookEndTimeMillis = 0; // Inherited from GameInstance
        this.currentHillRadius = this.originalHillRadius;
        this.currentHillRadiusSquared = this.currentHillRadius * this.currentHillRadius;

        playersInGame.forEach(uuid -> {
            playerScores.put(uuid, 0);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                playerStateManager.clearPlayerForGame(p, this.gameplayGamemode);
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
        if (this.votingEnabled && this.voteManager != null) { // Check inherited fields
            lastVoteTriggerTimeMillis = System.currentTimeMillis();
        }

        int spawnIndex = 0;
        for (UUID uuid : playersInGame) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (!absoluteGameSpawns.isEmpty()) {
                    player.teleport(absoluteGameSpawns.get(spawnIndex % absoluteGameSpawns.size())); spawnIndex++;
                } else { this.logger.warning("No game spawns for instance " + instanceId.toString().substring(0,8) + "!"); player.teleport(absoluteLobbySpawn); }
            }
        }
        startCountdown();
        return true;
    }

    private void startCountdown() {
        cancelTasks();
        final int[] currentCountdownValue = {countdownSeconds}; // Use countdownSeconds from definition
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
        broadcastToGamePlayers(ChatColor.GOLD + "" + ChatColor.BOLD + "KoTH Game '" + definition.getDisplayName() + "' has started! Capture the hill!");
        this.logger.info("KoTH instance " + instanceId.toString().substring(0,8) + " is now ACTIVE.");
        this.gameTickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::gameTick, 0L, 20L);
        if (this.votingEnabled && this.voteManager != null) {
            lastVoteTriggerTimeMillis = System.currentTimeMillis();
        }
        updateAllScoreboards();
    }

    @Override
    public void stop(boolean force) {
        this.logger.info("KoTH instance " + instanceId.toString().substring(0,8) + " is stopping. Forced: " + force);
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
                broadcastToGamePlayers(ChatColor.GOLD + winnerName + " has won KoTH with " + maxScore + " seconds on the hill!");
            } else if (!playersInGame.isEmpty()) {
                broadcastToGamePlayers(ChatColor.YELLOW + "KoTH game ended. No winner could be determined.");
            } else if (previousState != GameState.WAITING && previousState != GameState.ENDING) {
                broadcastToGamePlayers(ChatColor.YELLOW + "KoTH game ended as there were no players.");
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
                if (absoluteLobbySpawn != null) player.teleport(absoluteLobbySpawn);
                else player.teleport(getGameWorld().getSpawnLocation());
            } else {
                playerStateManager.removePlayerState(Bukkit.getOfflinePlayer(uuid).getPlayer());
            }
        });
        playersInGame.clear();

        // Actual arena reset will be handled by GameManager when it fully discards this instance
        setGameState(GameState.WAITING);
        this.logger.info("KoTH instance " + instanceId.toString().substring(0,8) + " logic finished. Set to WAITING (or should be FINISHED).");
        // GameManager should call cleanupInstance() which might then trigger ArenaManager.resetArena(this.arena, this.instanceBaseWorldLocation)
    }

    @Override
    public boolean addPlayer(Player player) {
        this.logger.info("[KoTHInstance-" + instanceId.toString().substring(0,8) + "] Attempting to add player " + player.getName());
        if (gameState == GameState.DISABLED) {
            player.sendMessage(ChatColor.RED + "The game '" + definition.getDisplayName() + "' is currently disabled.");
            return false;
        }
        if (gameState != GameState.WAITING && gameState != GameState.STARTING) {
            player.sendMessage(ChatColor.RED + "The game '" + definition.getDisplayName() + "' has already started or is ending.");
            return false;
        }
        if (playersInGame.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "You are already in this game.");
            return false;
        }

        int maxPlayers = definition.getRule("max_players", 16); // Example: get max_players rule
        if (playersInGame.size() >= maxPlayers) {
            player.sendMessage(ChatColor.RED + "This game instance is full (" + playersInGame.size() + "/" + maxPlayers + ")!");
            return false;
        }

        playerStateManager.savePlayerState(player);

        if (absoluteLobbySpawn == null) {
            this.logger.severe("[KoTHInstance-" + instanceId.toString().substring(0,8) + "] CRITICAL: Lobby spawn is null! Cannot add player " + player.getName() + ".");
            player.sendMessage(ChatColor.RED + "Error: Lobby spawn not set for this game. Please contact an admin.");
            playerStateManager.restorePlayerState(player);
            return false;
        }
        player.teleport(absoluteLobbySpawn);

        playersInGame.add(player.getUniqueId());
        playerScores.put(player.getUniqueId(), 0);

        if (gameState == GameState.STARTING || gameState == GameState.ACTIVE) {
            playerStateManager.clearPlayerForGame(player, this.gameplayGamemode);
            GameScoreboard sb = new GameScoreboard(player, scoreboardTitle);
            playerScoreboards.put(player.getUniqueId(), sb);
            sb.show();
            updateScoreboard(player);
        }

        player.sendMessage(ChatColor.GREEN + "You have joined KoTH: " + definition.getDisplayName());
        broadcastToGamePlayers(ChatColor.AQUA + player.getName() + ChatColor.GRAY + " has joined the KoTH game! (" + playersInGame.size() + " players)");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.5f);
        this.logger.info("[KoTHInstance-" + instanceId.toString().substring(0,8) + "] Successfully added player " + player.getName() + ". Total players: " + playersInGame.size());
        return true;
    }

    @Override
    public void removePlayer(Player player) {
        this.logger.info("[KoTHInstance-" + instanceId.toString().substring(0,8) + "] Attempting to remove player " + player.getName());
        GameScoreboard sb = playerScoreboards.remove(player.getUniqueId());
        if (sb != null) sb.destroy();

        boolean wasInGame = playersInGame.remove(player.getUniqueId());
        playerScores.remove(player.getUniqueId());

        playerStateManager.restorePlayerState(player);

        if (wasInGame) {
            player.sendMessage(ChatColor.GRAY + "You have left KoTH: " + definition.getDisplayName());
            broadcastToGamePlayers(ChatColor.AQUA + player.getName() + ChatColor.GRAY + " has left the KoTH game.");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.5f, 1.0f);

            if (absoluteLobbySpawn != null && player.isOnline()) {
                if (!player.getWorld().equals(absoluteLobbySpawn.getWorld()) || player.getLocation().distanceSquared(absoluteLobbySpawn) > 225) {
                    player.teleport(absoluteLobbySpawn);
                }
            }
            if ((gameState == GameState.ACTIVE || gameState == GameState.STARTING)) {
                int minPlayersRequired = definition.getRule("min_players_to_start", 2);
                if (playersInGame.isEmpty() && minPlayersRequired > 0) {
                    broadcastToGamePlayers(ChatColor.YELLOW + "The last player left. The game is ending."); stop(false);
                } else if (playersInGame.size() < minPlayersRequired && minPlayersRequired > 1) {
                    broadcastToGamePlayers(ChatColor.RED + "Not enough players to continue. The game is ending."); stop(false);
                }
            }
            this.logger.info("[KoTHInstance-" + instanceId.toString().substring(0,8) + "] Successfully removed player " + player.getName() + ". Remaining players: " + playersInGame.size());
        } else {
            this.logger.warning("[KoTHInstance-" + instanceId.toString().substring(0,8) + "] Player " + player.getName() + " was not in playersInGame set during removal attempt.");
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
            if (getAbsoluteHillCenter() != null) {
                ParticleUtil.spawnLocationEffect(getAbsoluteHillCenter(), Particle.SMOKE, 50, 0.5, 1, 0.5, 0.1);
                getAbsoluteHillCenter().getWorld().playSound(getAbsoluteHillCenter(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.AMBIENT, 0.7f, 1f);
            }
            activeVotingHook = null; activeHookEndTimeMillis = 0;
        }

        if (votingEnabled && voteManager != null && !voteManager.isVoteActive() && availableVotingHooks != null && !availableVotingHooks.isEmpty()) {
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
                if (getAbsoluteHillCenter() != null) getAbsoluteHillCenter().getWorld().playSound(getAbsoluteHillCenter(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.AMBIENT, 0.8f, 1.5f);
            }
            playerCurrentlyOnHill = newHillHolderUUID;
        } else if (newHillHolderUUID == null && playerCurrentlyOnHill != null) {
            broadcastToGamePlayers(ChatColor.YELLOW + "The hill is now neutral!");
            if (getAbsoluteHillCenter() != null) getAbsoluteHillCenter().getWorld().playSound(getAbsoluteHillCenter(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 0.8f, 1.0f);
            playerCurrentlyOnHill = null;
        }

        updateAllScoreboards(currentHillHolderName);

        if (timeElapsedSeconds > 0 && timeElapsedSeconds % 30 == 0) {
            broadcastToGamePlayers(ChatColor.YELLOW + "KoTH: " + ChatColor.AQUA + (gameDurationSeconds - timeElapsedSeconds) + "s" + ChatColor.YELLOW + " remaining.");
        }
    }

    private void triggerVote() {
        if (availableVotingHooks == null || availableVotingHooks.isEmpty() || voteManager == null) {
            this.logger.fine("TriggerVote called for instance " + instanceId.toString().substring(0,8) + " but no hooks/manager available.");
            return;
        }
        this.logger.info("Attempting to trigger vote for instance " + instanceId.toString().substring(0,8));
        List<VotingHook> optionsForVote = new ArrayList<>();
        List<VotingHook> hooksPool = new ArrayList<>(availableVotingHooks);
        Collections.shuffle(hooksPool);
        int numberOfOptionsToPresent = Math.min(hooksPool.size(), 3);
        if (numberOfOptionsToPresent < 2 && hooksPool.size() >=2) { numberOfOptionsToPresent = 2; }
        else if (hooksPool.size() < 2) {
            this.logger.warning("Not enough unique voting hooks for instance " + instanceId.toString().substring(0,8) + " (need >= 2, have " + hooksPool.size() + ")");
            lastVoteTriggerTimeMillis = System.currentTimeMillis(); return;
        }
        for (int i = 0; i < numberOfOptionsToPresent && !hooksPool.isEmpty(); ) {
            VotingHook selectedHook = hooksPool.remove(0);
            if (selectedHook.canApply(this)) { optionsForVote.add(selectedHook); i++; }
        }
        if (optionsForVote.size() < 2) {
            this.logger.warning("Could not find enough applicable hooks for instance " + instanceId.toString().substring(0,8) + " (found " + optionsForVote.size() + ").");
            lastVoteTriggerTimeMillis = System.currentTimeMillis(); return;
        }
        if (voteManager.startVote(optionsForVote, voteEventDurationSeconds)) {
            lastVoteTriggerTimeMillis = System.currentTimeMillis();
        } else {
            this.logger.warning("VoteManager failed to start vote for instance " + instanceId.toString().substring(0,8));
            lastVoteTriggerTimeMillis = System.currentTimeMillis();
        }
    }

    private boolean isPlayerOnHill(Player player) {
        if (absoluteHillCenter == null || !player.getWorld().equals(absoluteHillCenter.getWorld())) return false;
        Location playerFootLoc = player.getLocation();
        if (Math.abs(playerFootLoc.getY() - absoluteHillCenter.getY()) <= 1.5) {
            double dx = playerFootLoc.getX() - absoluteHillCenter.getX();
            double dz = playerFootLoc.getZ() - absoluteHillCenter.getZ();
            return (dx * dx + dz * dz) <= currentHillRadiusSquared;
        }
        return false;
    }

    @Override
    public void broadcastToGamePlayers(String message) {
        String prefix = ChatColor.DARK_AQUA + "[KoTH-" + definition.getDisplayName() + "] " + ChatColor.RESET;
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

    // setActiveVotingHook is inherited from GameInstance and calls super.setActiveVotingHook()
    // then updates scoreboards. We can add specific particles/sounds here if needed.
    @Override
    public void setActiveVotingHook(VotingHook hook) {
        super.setActiveVotingHook(hook); // Sets activeVotingHook and activeHookEndTimeMillis
        if (hook != null) {
            this.logger.info("Activating voting hook: " + hook.getDisplayName() + " for KoTH instance " + instanceId.toString().substring(0,8));
            if (getAbsoluteHillCenter() != null) {
                ParticleUtil.spawnExplosionEffect(getAbsoluteHillCenter().clone().add(0,1,0), Particle.LAVA, 70, 0.7f);
                ParticleUtil.spawnHelixEffect(getAbsoluteHillCenter().clone().add(0,0.5,0), Particle.FLAME, 1.5, 3, 20, 2);
                getAbsoluteHillCenter().getWorld().playSound(getAbsoluteHillCenter(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.AMBIENT, 0.5f, 1.2f);
            }
        } else { // Hook ended
            if (getAbsoluteHillCenter() != null) {
                getAbsoluteHillCenter().getWorld().playSound(getAbsoluteHillCenter(), Sound.BLOCK_CONDUIT_DEACTIVATE, SoundCategory.PLAYERS, 0.6f, 1f);
            }
        }
        updateAllScoreboards();
    }

    public void setTemporaryHillRadius(int newRadius) {
        this.logger.info("Setting temporary hill radius for instance " + instanceId.toString().substring(0,8) + " to " + newRadius + ". Original was " + this.originalHillRadius);
        this.currentHillRadius = newRadius;
        this.currentHillRadiusSquared = newRadius * newRadius;
        broadcastToGamePlayers(ChatColor.YELLOW + "The hill's capture zone has changed size!");
    }

    public Location getAbsoluteHillCenter() { return this.absoluteHillCenter; }
    public int getCurrentHillRadius() { return this.currentHillRadius; }

    // Admin commands for temporary instance modification (not saved to definition)
    public void adminSetHillLocation(Location location) {
        if (location == null) { this.logger.warning("Admin attempt to set null hill location for instance " + instanceId.toString().substring(0,8)); return; }
        this.absoluteHillCenter = location.clone();
        this.logger.info("KoTH instance " + instanceId.toString().substring(0,8) + " hill center administratively set to: " + location.toString() + " (Current match only).");
        broadcastToGamePlayers(ChatColor.YELLOW + "Admin: Hill location has been updated for this match.");
    }

    public void adminSetHillRadius(int radius) {
        if (radius <= 0) { this.logger.warning("Admin attempt to set invalid hill radius ("+radius+") for instance " + instanceId.toString().substring(0,8)); return; }
        this.currentHillRadius = radius; // Modifies current operational radius
        this.currentHillRadiusSquared = radius * radius;
        this.logger.info("KoTH instance " + instanceId.toString().substring(0,8) + " hill radius administratively set to: " + radius + " (Current match only).");
        broadcastToGamePlayers(ChatColor.YELLOW + "Admin: Hill radius has been updated to " + radius + " for this match.");
    }
}
