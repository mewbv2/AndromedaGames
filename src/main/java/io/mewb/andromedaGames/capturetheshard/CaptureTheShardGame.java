package io.mewb.andromedaGames.capturetheshard;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.arena.ArenaDefinition;
import io.mewb.andromedaGames.game.GameDefinition;
import io.mewb.andromedaGames.game.GameInstance;
import io.mewb.andromedaGames.game.GameState;
import io.mewb.andromedaGames.utils.GameScoreboard;
import io.mewb.andromedaGames.utils.ParticleUtil;
import io.mewb.andromedaGames.utils.RelativeLocation;
import io.mewb.andromedaGames.voting.VoteManager;
import io.mewb.andromedaGames.voting.VotingHook;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class CaptureTheShardGame extends GameInstance {

    // Game Rules from Definition
    private int capturesToWin;
    private int gameDurationSeconds;
    private int countdownSeconds;
    private String scoreboardTitle;
    private GameMode gameplayGamemode;
    private int maxPlayersPerTeam;
    private int minPlayersToStart; // Combined from both teams

    // Arena Locations (Absolute)
    private Location redTeamLobbySpawn; // Optional, if teams have separate lobbies
    private Location blueTeamLobbySpawn; // Optional
    private Location neutralLobbySpawn; // Main lobby spawn if not team-specific

    private Map<TeamColor, Location> teamShardPedestals = new HashMap<>();
    private Map<TeamColor, Location> teamCapturePoints = new HashMap<>();
    private Map<TeamColor, List<Location>> teamPlayerSpawns = new HashMap<>();

    // Instance State
    private final Map<TeamColor, Set<UUID>> teamPlayers = new HashMap<>();
    private final Map<UUID, TeamColor> playerTeams = new HashMap<>();
    private final Map<TeamColor, Integer> teamScores = new HashMap<>();

    private final Map<TeamColor, ShardState> teamShardStates = new HashMap<>();
    private final Map<TeamColor, UUID> shardCarriers = new HashMap<>(); // TeamColor of shard -> Player UUID carrying it

    private BukkitTask gameTimerTask;
    private BukkitTask countdownTask;
    private int timeRemainingSeconds;

    // Spigot Scoreboard Teams
    private Map<TeamColor, org.bukkit.scoreboard.Team> spigotTeams = new HashMap<>();
    private final Random random = new Random();

    // Constants for Shard Item
    private static final Material SHARD_MATERIAL = Material.NETHER_STAR; // Example material
    private static final String SHARD_ITEM_NAME_PREFIX = ChatColor.GOLD + "Ancient Shard ";


    public CaptureTheShardGame(AndromedaGames plugin, UUID instanceId, GameDefinition definition, ArenaDefinition arena, Location instanceBaseWorldLocation) {
        super(plugin, instanceId, definition, arena, instanceBaseWorldLocation);
        for (TeamColor color : TeamColor.values()) {
            teamPlayers.put(color, new HashSet<>());
            teamScores.put(color, 0);
            teamShardStates.put(color, ShardState.AT_PEDESTAL); // Initial state
            teamPlayerSpawns.put(color, new ArrayList<>());
        }
    }

    @Override
    public void setupInstance() {
        this.logger.info("[CTSInstance:" + instanceId.toString().substring(0, 8) + "] Setting up with definition '" + definition.getDefinitionId() + "' and arena '" + arena.getArenaId() + "'.");

        // Load Game Rules
        this.capturesToWin = definition.getRule("captures_to_win", 3);
        this.gameDurationSeconds = definition.getRule("game_duration_seconds", 600);
        this.timeRemainingSeconds = this.gameDurationSeconds;
        this.countdownSeconds = definition.getRule("countdown_seconds", 20);
        this.maxPlayersPerTeam = definition.getRule("max_players_per_team", 8);
        this.minPlayersToStart = definition.getRule("min_players_to_start", 2); // Min total players for the game
        this.scoreboardTitle = ChatColor.translateAlternateColorCodes('&', definition.getRule("scoreboard_title", "&b&lCapture The Shard: &e" + definition.getDisplayName()));
        try {
            this.gameplayGamemode = GameMode.valueOf(definition.getRule("gameplay_gamemode", "SURVIVAL").toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            this.logger.warning("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Invalid gameplay_gamemode. Defaulting to SURVIVAL.");
            this.gameplayGamemode = GameMode.SURVIVAL;
        }

        // Load Locations
        this.neutralLobbySpawn = getAbsoluteLocation("lobby_spawn"); // General lobby
        // Team-specific lobbies (optional, fallback to neutralLobbySpawn if not defined)
        this.redTeamLobbySpawn = getOptionalAbsoluteLocation("red_lobby_spawn").orElse(neutralLobbySpawn);
        this.blueTeamLobbySpawn = getOptionalAbsoluteLocation("blue_lobby_spawn").orElse(neutralLobbySpawn);


        boolean criticalLocationsMissing = false;
        for (TeamColor teamColor : TeamColor.values()) {
            String colorPrefix = teamColor.name().toLowerCase(); // "red" or "blue"

            // Shard Pedestals
            Optional<Location> pedestalOpt = getOptionalAbsoluteLocation(colorPrefix + "_shard_pedestal");
            if (pedestalOpt.isPresent()) {
                teamShardPedestals.put(teamColor, pedestalOpt.get());
                this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] " + teamColor + " Pedestal: " + pedestalOpt.get().toString());
            } else {
                this.logger.severe("[CTSInstance:" + instanceId.toString().substring(0,8) + "] CRITICAL: " + teamColor + " shard pedestal location ('" + colorPrefix + "_shard_pedestal') not found in arena '" + arena.getArenaId() + "'.");
                criticalLocationsMissing = true;
            }

            // Capture Points
            Optional<Location> captureOpt = getOptionalAbsoluteLocation(colorPrefix + "_capture_point");
            if (captureOpt.isPresent()) {
                teamCapturePoints.put(teamColor, captureOpt.get());
                this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] " + teamColor + " Capture Point: " + captureOpt.get().toString());
            } else {
                this.logger.severe("[CTSInstance:" + instanceId.toString().substring(0,8) + "] CRITICAL: " + teamColor + " capture point location ('" + colorPrefix + "_capture_point') not found.");
                criticalLocationsMissing = true;
            }

            // Player Spawns (List)
            List<Location> spawns = getAbsoluteLocationList(colorPrefix + "_player_spawns");
            if (!spawns.isEmpty()) {
                teamPlayerSpawns.put(teamColor, spawns);
                spawns.forEach(loc -> this.logger.finer("[CTSInstance:" + instanceId.toString().substring(0,8) + "] " + teamColor + " Spawn: " + loc.toString()));
            } else {
                this.logger.severe("[CTSInstance:" + instanceId.toString().substring(0,8) + "] CRITICAL: No player spawns ('" + colorPrefix + "_player_spawns') found for " + teamColor + " team.");
                criticalLocationsMissing = true;
            }
        }

        if (neutralLobbySpawn == null && (redTeamLobbySpawn == null || blueTeamLobbySpawn == null)) {
            this.logger.severe("[CTSInstance:" + instanceId.toString().substring(0,8) + "] CRITICAL: No general or complete team lobby spawns defined.");
            criticalLocationsMissing = true;
        }


        if (criticalLocationsMissing) {
            setGameState(GameState.DISABLED);
            this.logger.severe("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Disabled due to missing critical locations.");
            return;
        }

        setupSpigotTeams();

        // Voting System (similar to KoTH/Infection)
        ConfigurationSection votingConfigSection = definition.getVotingConfig();
        if (votingConfigSection != null) {
            this.votingEnabled = votingConfigSection.getBoolean("enabled", false);
            this.voteIntervalSeconds = votingConfigSection.getInt("interval_seconds", 120);
            this.voteEventDurationSeconds = votingConfigSection.getInt("duration_seconds", 20);
            List<String> hookIdsFromDef = votingConfigSection.getStringList("hooks_available");
            initializeVotingHooksFromDef(hookIdsFromDef); // Populates this.availableVotingHooks
            if (this.votingEnabled && !this.availableVotingHooks.isEmpty()) {
                this.voteManager = new VoteManager(plugin, this);
                this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Voting system configured: Enabled=" + votingEnabled + ", " + availableVotingHooks.size() + " hooks loaded.");
            } else {
                this.votingEnabled = false;
                this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Voting system disabled.");
            }
        } else {
            this.votingEnabled = false;
            this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] No voting configuration found. Voting disabled.");
        }

        if (this.gameState != GameState.DISABLED) {
            setGameState(GameState.WAITING);
            this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] setup complete and ready (WAITING).");
        }
    }

    /** Helper to get an optional absolute location. */
    private Optional<Location> getOptionalAbsoluteLocation(String key) {
        RelativeLocation relLoc = arena.getRelativeLocation(key);
        if (relLoc == null) return Optional.empty();
        Location absLoc = relLoc.toAbsolute(instanceBaseWorldLocation);
        return Optional.ofNullable(absLoc);
    }


    private void initializeVotingHooksFromDef(List<String> hookIds) {
        availableVotingHooks.clear();
        if (hookIds == null || hookIds.isEmpty()) return;
        for (String hookId : hookIds) {
            // Example:
            // switch (hookId.toLowerCase()) {
            //     case "cts_speed_boost": availableVotingHooks.add(new CTSSpeedBoostHook()); break;
            //     default: this.logger.warning("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Unknown CTS voting hook ID: " + hookId);
            // }
            this.logger.warning("[CTSInstance:" + instanceId.toString().substring(0,8) + "] CTS Voting Hook '" + hookId + "' not implemented yet.");
        }
    }

    private void setupSpigotTeams() {
        org.bukkit.scoreboard.Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String instancePrefix = "cts_" + instanceId.toString().substring(0, 4);

        for (TeamColor teamColor : TeamColor.values()) {
            String teamName = instancePrefix + "_" + teamColor.name().toLowerCase();
            org.bukkit.scoreboard.Team spigotTeam = mainScoreboard.getTeam(teamName);
            if (spigotTeam != null) spigotTeam.unregister(); // Clear old team if exists

            spigotTeam = mainScoreboard.registerNewTeam(teamName);
            spigotTeam.setColor(teamColor.getChatColor());
            spigotTeam.setPrefix(teamColor.getChatColor() + "[" + teamColor.name() + "] ");
            spigotTeam.setAllowFriendlyFire(false); // Typically false in team games
            spigotTeam.setCanSeeFriendlyInvisibles(true);
            // Set collision rule (requires Paper/Spigot API supporting it)
            // spigotTeam.setOption(Option.COLLISION_RULE, OptionStatus.FOR_OTHER_TEAMS);
            spigotTeams.put(teamColor, spigotTeam);
            this.logger.fine("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Registered Spigot team: " + teamName);
        }
    }

    private void clearSpigotTeams() {
        for (org.bukkit.scoreboard.Team spigotTeam : spigotTeams.values()) {
            if (spigotTeam != null) {
                try {
                    spigotTeam.unregister();
                    this.logger.fine("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Unregistered Spigot team: " + spigotTeam.getName());
                } catch (IllegalStateException e) { /* Already unregistered */ }
            }
        }
        spigotTeams.clear();
    }


    @Override
    public void cleanupInstance() {
        this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Cleaning up...");
        cancelTasks();
        playerScoreboards.values().forEach(GameScoreboard::destroy);
        playerScoreboards.clear();
        clearSpigotTeams();
        // Any other CTS specific cleanup
        this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Cleanup complete.");
    }

    private void cancelTasks() {
        if (countdownTask != null && !countdownTask.isCancelled()) countdownTask.cancel();
        if (gameTimerTask != null && !gameTimerTask.isCancelled()) gameTimerTask.cancel();
        if (voteManager != null && voteManager.isVoteActive()) voteManager.endVote(false);
        this.logger.fine("[CTSInstance:" + instanceId.toString().substring(0,8) + "] All scheduled tasks cancelled.");
    }

    @Override
    public boolean start(boolean bypassMinPlayerCheck) {
        if (gameState == GameState.DISABLED) {
            this.logger.warning("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Attempted to start but it is DISABLED.");
            return false;
        }
        if (gameState != GameState.WAITING) {
            this.logger.warning("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Cannot start, current state: " + gameState);
            return false;
        }

        if (!bypassMinPlayerCheck && playersInGame.size() < minPlayersToStart) {
            broadcastToGamePlayers(ChatColor.RED + "Not enough players! Need " + minPlayersToStart + ", have " + playersInGame.size() + ".");
            return false;
        }

        setGameState(GameState.STARTING);
        // Reset scores and shard states
        for (TeamColor color : TeamColor.values()) {
            teamScores.put(color, 0);
            resetShard(color, false); // Reset shard to pedestal, don't announce yet
        }
        shardCarriers.clear();
        this.timeRemainingSeconds = this.gameDurationSeconds;
        this.activeVotingHook = null;
        this.activeHookEndTimeMillis = 0;


        // Prepare players (clear inventory, set gamemode, scoreboards, add to Spigot teams)
        teamPlayers.forEach((teamColor, playerUUIDs) -> {
            playerUUIDs.forEach(uuid -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    playerStateManager.clearPlayerForGame(p, this.gameplayGamemode);
                    GameScoreboard sb = playerScoreboards.computeIfAbsent(uuid, k -> new GameScoreboard(p, scoreboardTitle));
                    sb.updateTitle(scoreboardTitle);
                    sb.show();
                    spigotTeams.get(teamColor).addEntry(p.getName());
                    updateScoreboard(p);
                }
            });
        });

        // Teleport players to their team spawns
        teleportAllPlayersToSpawns();
        placeInitialShardsOnPedestals(); // Visually place shards
        startCountdown();
        return true;
    }

    private void placeInitialShardsOnPedestals() {
        for (TeamColor tc : TeamColor.values()) {
            Location pedestalLoc = teamShardPedestals.get(tc);
            if (pedestalLoc != null) {
                pedestalLoc.getBlock().setType(SHARD_MATERIAL); // Or a specific block representing the shard
                // Could add particle effects here too
                ParticleUtil.spawnLocationEffect(pedestalLoc.clone().add(0.5, 1, 0.5), Particle.END_ROD, 20, 0.1, 0.5, 0.1, 0);
            }
        }
    }

    private void teleportAllPlayersToSpawns() {
        teamPlayers.forEach((teamColor, playerUUIDs) -> {
            List<Location> spawns = teamPlayerSpawns.get(teamColor);
            if (spawns == null || spawns.isEmpty()) {
                this.logger.warning("[CTSInstance:" + instanceId.toString().substring(0,8) + "] No spawns defined for team " + teamColor + "! Players may not be teleported correctly.");
                return;
            }
            int spawnIndex = 0;
            for (UUID uuid : playerUUIDs) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.teleport(spawns.get(spawnIndex % spawns.size()));
                    spawnIndex++;
                }
            }
        });
    }

    private void startCountdown() {
        cancelTasks();
        final int[] currentCountdownValue = {this.countdownSeconds};
        broadcastToGamePlayers(ChatColor.GOLD + "Capture The Shard: " + definition.getDisplayName() + ChatColor.YELLOW + " is starting soon!");

        this.countdownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (gameState != GameState.STARTING) {
                cancelTasks();
                return;
            }
            if (currentCountdownValue[0] > 0) {
                String title = ChatColor.AQUA + "Starting in: " + ChatColor.GOLD + currentCountdownValue[0];
                for (UUID uuid : playersInGame) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        p.sendTitle(title, "", 0, 25, 5);
                        if (currentCountdownValue[0] <= 5) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f + (0.2f * (5 - currentCountdownValue[0])));
                        }
                    }
                }
            } else {
                cancelTasks();
                activateGame();
            }
            currentCountdownValue[0]--;
        }, 0L, 20L);
    }

    private void activateGame() {
        if (gameState != GameState.STARTING) return;
        setGameState(GameState.ACTIVE);
        // Register listeners if needed (e.g., PlayerInteractEvent for shard pickup) - currently handled by commands/direct calls
        broadcastToGamePlayers(ChatColor.GREEN + "" + ChatColor.BOLD + "GO! Capture the enemy shards!");
        this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] is now ACTIVE.");
        this.timeRemainingSeconds = this.gameDurationSeconds;
        if (this.votingEnabled) this.lastVoteTriggerTimeMillis = System.currentTimeMillis();

        this.gameTimerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::gameTick, 0L, 20L);
    }


    @Override
    public void stop(boolean force) {
        this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Stopping. Forced: " + force);
        if (gameState == GameState.ENDING || gameState == GameState.DISABLED || gameState == GameState.UNINITIALIZED) {
            this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Already stopping or in a terminal state.");
            return;
        }

        GameState previousState = gameState;
        setGameState(GameState.ENDING);
        cancelTasks();

        // Determine winner
        TeamColor winningTeam = null;
        int redScore = teamScores.getOrDefault(TeamColor.RED, 0);
        int blueScore = teamScores.getOrDefault(TeamColor.BLUE, 0);

        if (redScore > blueScore) winningTeam = TeamColor.RED;
        else if (blueScore > redScore) winningTeam = TeamColor.BLUE;

        String winnerMessage;
        if (winningTeam != null) {
            winnerMessage = winningTeam.getChatColor() + "" + ChatColor.BOLD + winningTeam.name() + " TEAM WINS with " + teamScores.get(winningTeam) + " captures!";
        } else {
            winnerMessage = ChatColor.YELLOW + "" + ChatColor.BOLD + "IT'S A DRAW!";
        }
        if (force && previousState != GameState.ACTIVE) {
            winnerMessage = ChatColor.YELLOW + "Game " + definition.getDisplayName() + " forcefully ended.";
        }

        broadcastToGamePlayers(winnerMessage);

        String finalWinnerMessage = winnerMessage;
        new HashSet<>(playersInGame).forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendTitle(finalWinnerMessage.substring(0, Math.min(finalWinnerMessage.length(), 45)), ChatColor.GOLD + "Thanks for playing!", 10, 80, 30);
                playerScoreboards.get(uuid).destroy();
                playerScoreboards.remove(uuid);
                playerStateManager.restorePlayerState(player);
                removePlayerFromSpigotTeam(player); // Remove from Spigot team
                clearShardFromInventory(player); // Ensure shard is removed

                TeamColor playerTeamColor = playerTeams.get(uuid);
                Location lobby = (playerTeamColor == TeamColor.RED) ? redTeamLobbySpawn : (playerTeamColor == TeamColor.BLUE) ? blueTeamLobbySpawn : neutralLobbySpawn;
                if (lobby != null) player.teleport(lobby);
                else if (getGameWorld() != null) player.teleport(getGameWorld().getSpawnLocation());
            } else {
                playerStateManager.removePlayerState(Bukkit.getOfflinePlayer(uuid).getPlayer());
            }
        });

        playersInGame.clear();
        playerTeams.clear();
        teamPlayers.values().forEach(Set::clear);
        teamScores.replaceAll((c, v) -> 0);
        shardCarriers.clear();
        teamShardStates.replaceAll((c,v) -> ShardState.AT_PEDESTAL);
        // Remove shard blocks from pedestals
        teamShardPedestals.values().forEach(loc -> { if(loc != null) loc.getBlock().setType(Material.AIR); });


        setGameState(GameState.WAITING); // Ready for potential reuse or full cleanup by GameManager
        this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Game logic finished, set to WAITING.");
    }

    private void removePlayerFromSpigotTeam(Player player) {
        TeamColor teamColor = playerTeams.get(player.getUniqueId());
        if (teamColor != null && spigotTeams.containsKey(teamColor)) {
            spigotTeams.get(teamColor).removeEntry(player.getName());
        }
    }


    public boolean addPlayer(Player player, TeamColor preferredTeam) {
        this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Attempting to add player " + player.getName() + " (Preferred: " + preferredTeam + "). State: " + gameState);
        if (gameState != GameState.WAITING) {
            player.sendMessage(ChatColor.RED + "This CTS game cannot be joined at this time (State: " + gameState + ").");
            return false;
        }
        if (playersInGame.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "You are already in this game.");
            return false;
        }
        if (playersInGame.size() >= maxPlayersPerTeam * TeamColor.values().length) {
            player.sendMessage(ChatColor.RED + "This game instance is full.");
            return false;
        }

        TeamColor assignedTeam = assignTeam(player.getUniqueId(), preferredTeam);
        if (assignedTeam == null) {
            player.sendMessage(ChatColor.RED + "Could not assign you to a team. The preferred team might be full or an error occurred.");
            return false;
        }

        playerStateManager.savePlayerState(player);
        playersInGame.add(player.getUniqueId());
        teamPlayers.get(assignedTeam).add(player.getUniqueId());
        playerTeams.put(player.getUniqueId(), assignedTeam);

        // Teleport to team-specific lobby or general lobby
        Location lobbySpawn = (assignedTeam == TeamColor.RED) ? redTeamLobbySpawn : blueTeamLobbySpawn;
        if (lobbySpawn == null) lobbySpawn = neutralLobbySpawn; // Fallback
        if (lobbySpawn != null) player.teleport(lobbySpawn);
        else { // Critical fallback if no lobby spawns are set at all
            this.logger.severe("[CTSInstance:" + instanceId.toString().substring(0,8) + "] No valid lobby spawn for player " + player.getName() + " on team " + assignedTeam);
            if(getGameWorld() != null) player.teleport(getGameWorld().getSpawnLocation());
        }


        player.sendMessage(ChatColor.GREEN + "You joined " + definition.getDisplayName() + " on " + assignedTeam.getChatColor() + assignedTeam.name() + " team!");
        broadcastToGamePlayers(assignedTeam.getChatColor() + player.getName() + ChatColor.GRAY + " joined " + assignedTeam.getChatColor() + assignedTeam.name() + " team! (" + playersInGame.size() + " total)");

        // If game is WAITING and now meets min players, GameManager might trigger a start, or it's manual.
        if (playersInGame.size() >= minPlayersToStart && gameState == GameState.WAITING) {
            // Optional: Automatically start if min players reached and a config allows it.
            // For now, admin usually starts via command or GameManager handles auto-start logic.
            broadcastToGamePlayers(ChatColor.GREEN + "Minimum player count reached! Game can now start.");
        }

        return true;
    }

    // Override addPlayer from GameInstance to ensure our specific logic is called by GameManager
    @Override
    public boolean addPlayer(Player player) {
        return addPlayer(player, null); // Call our specific method with no preferred team
    }


    private TeamColor assignTeam(UUID playerUuid, TeamColor preferredTeam) {
        int redTeamSize = teamPlayers.get(TeamColor.RED).size();
        int blueTeamSize = teamPlayers.get(TeamColor.BLUE).size();

        if (preferredTeam != null) {
            if (teamPlayers.get(preferredTeam).size() < maxPlayersPerTeam) {
                return preferredTeam;
            } else {
                // Preferred team is full, try to assign to the other team or balance
                TeamColor otherTeam = (preferredTeam == TeamColor.RED) ? TeamColor.BLUE : TeamColor.RED;
                if (teamPlayers.get(otherTeam).size() < maxPlayersPerTeam) {
                    Bukkit.getPlayer(playerUuid).sendMessage(ChatColor.YELLOW + "Your preferred team (" + preferredTeam.name() + ") was full. You've been assigned to " + otherTeam.name() + ".");
                    return otherTeam;
                }
                return null; // Both teams full or preferred is full and other is also full
            }
        }

        // Auto-balance if no preference
        if (redTeamSize < maxPlayersPerTeam && redTeamSize <= blueTeamSize) {
            return TeamColor.RED;
        } else if (blueTeamSize < maxPlayersPerTeam && blueTeamSize < redTeamSize) {
            return TeamColor.BLUE;
        } else if (redTeamSize < maxPlayersPerTeam) { // If blue is full but red is not
            return TeamColor.RED;
        } else if (blueTeamSize < maxPlayersPerTeam) { // If red is full but blue is not
            return TeamColor.BLUE;
        }
        return null; // Both teams are full
    }


    @Override
    public void removePlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Attempting to remove player " + player.getName());

        TeamColor team = playerTeams.remove(playerUUID);
        if (team != null) {
            teamPlayers.get(team).remove(playerUUID);
            if (spigotTeams.get(team) != null) {
                spigotTeams.get(team).removeEntry(player.getName());
            }
        }

        // If player was carrying a shard, drop it or return it
        for (TeamColor shardTeamColor : TeamColor.values()) {
            if (playerUUID.equals(shardCarriers.get(shardTeamColor))) {
                dropShard(player, shardTeamColor); // Or resetShard(shardTeamColor, true);
                break;
            }
        }
        clearShardFromInventory(player);


        GameScoreboard sb = playerScoreboards.remove(playerUUID);
        if (sb != null) sb.destroy();

        boolean wasInGame = playersInGame.remove(playerUUID); // From GameInstance set
        playerStateManager.restorePlayerState(player);

        if (wasInGame) {
            player.sendMessage(ChatColor.GRAY + "You left " + definition.getDisplayName() + ".");
            broadcastToGamePlayers(ChatColor.AQUA + player.getName() + ChatColor.GRAY + " has left the game.");
            if (gameState == GameState.ACTIVE || gameState == GameState.STARTING) {
                checkGameEndConditions();
            }
        }
        this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Successfully removed player " + player.getName() + ". Remaining players: " + playersInGame.size());
    }

    @Override
    protected void gameTick() {
        if (gameState != GameState.ACTIVE) return;
        timeRemainingSeconds--;

        // Voting logic
        if (votingEnabled && voteManager != null && !voteManager.isVoteActive() &&
                availableVotingHooks != null && !availableVotingHooks.isEmpty()) {
            if ((System.currentTimeMillis() - lastVoteTriggerTimeMillis) / 1000 >= voteIntervalSeconds) {
                triggerCTSvote();
            }
        }
        if (activeVotingHook != null && activeHookEndTimeMillis > 0 && System.currentTimeMillis() >= activeHookEndTimeMillis) {
            broadcastToGamePlayers(ChatColor.YELLOW + activeVotingHook.getDisplayName() + " has worn off!");
            activeVotingHook = null; activeHookEndTimeMillis = 0;
        }

        // Shard carrier effects (e.g., glowing, slowness)
        shardCarriers.forEach((shardTeam, carrierUUID) -> {
            Player carrier = Bukkit.getPlayer(carrierUUID);
            if (carrier != null && carrier.isOnline()) {
                carrier.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, true, false));
                carrier.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, false)); // Example: slight slowness
                // Particle trail for carrier
                ParticleUtil.spawnPlayerStatusParticles(carrier, Particle.TOTEM_OF_UNDYING, 1, 0.1, 0.1, 0.1, 0.01);
            }
        });

        // Check for shard returns (if dropped shards automatically return after a timeout)
        // TODO: Implement logic for dropped shards on ground returning to pedestal after a timer.

        updateAllScoreboards();
        if (timeRemainingSeconds <= 0) {
            broadcastToGamePlayers(ChatColor.GOLD + "Time's up!");
            stop(false); // Game ends, determine winner by score
        }
    }

    private void triggerCTSvote() {
        // Similar to KoTH/Infection triggerVote methods
        if (availableVotingHooks.isEmpty() || voteManager == null) return;
        this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Attempting to trigger vote.");
        List<VotingHook> options = new ArrayList<>(availableVotingHooks);
        Collections.shuffle(options);
        List<VotingHook> actualOptions = options.stream().filter(h -> h.canApply(this)).limit(3).collect(Collectors.toList());
        if (actualOptions.size() >= 2) {
            if(voteManager.startVote(actualOptions, voteEventDurationSeconds)) {
                lastVoteTriggerTimeMillis = System.currentTimeMillis();
            }
        } else {
            this.logger.warning("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Not enough applicable voting hooks for CTS (need >=2, found " + actualOptions.size() + ").");
            lastVoteTriggerTimeMillis = System.currentTimeMillis(); // Reset to try later
        }
    }

    public void playerAttemptPickupShard(Player player, TeamColor shardTeamColor) {
        if (gameState != GameState.ACTIVE) return;
        TeamColor playerTeam = playerTeams.get(player.getUniqueId());
        if (playerTeam == null) return; // Not on a team

        // Cannot pick up own team's shard if it's at pedestal or dropped by teammate (unless rules allow)
        // Can always pick up ENEMY team's shard
        if (shardTeamColor == playerTeam && (teamShardStates.get(shardTeamColor) == ShardState.AT_PEDESTAL)) {
            player.sendMessage(ChatColor.YELLOW + "You cannot pick up your own team's shard from its pedestal!");
            return;
        }
        if (shardCarriers.containsValue(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "You are already carrying a shard!");
            return;
        }
        if (teamShardStates.get(shardTeamColor) == ShardState.CARRIED_BY_ENEMY && shardCarriers.containsKey(shardTeamColor)) {
            // This means the enemy shard is already carried by someone (could be teammate or another enemy)
            // This check might be redundant if the shard block is removed when picked up.
            Player currentCarrier = Bukkit.getPlayer(shardCarriers.get(shardTeamColor));
            player.sendMessage(ChatColor.RED + "The " + shardTeamColor.name() + " shard is already being carried by " + (currentCarrier != null ? currentCarrier.getName() : "someone") + "!");
            return;
        }
        if (teamShardStates.get(shardTeamColor) == ShardState.CARRIED_BY_OWN_TEAM && shardCarriers.containsKey(shardTeamColor)) {
            player.sendMessage(ChatColor.RED + "The " + shardTeamColor.name() + " shard is already being carried by one of your teammates!");
            return;
        }


        // Successful pickup
        Location pedestalLoc = teamShardPedestals.get(shardTeamColor);
        if (pedestalLoc != null) { // Remove from pedestal
            Block pedestalBlock = pedestalLoc.getBlock();
            if(pedestalBlock.getType() == SHARD_MATERIAL) { // Check if it's actually the shard material
                pedestalBlock.setType(Material.AIR);
            } else {
                // If shard was "dropped" and is now a conceptual pickup rather than block break
                this.logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Player " + player.getName() + " picked up " + shardTeamColor + " shard (not from block).");
            }
        }

        shardCarriers.put(shardTeamColor, player.getUniqueId());
        if (playerTeam == shardTeamColor) { // Own team picked up their (e.g. dropped) shard
            teamShardStates.put(shardTeamColor, ShardState.CARRIED_BY_OWN_TEAM);
            broadcastToGamePlayers(playerTeam.getChatColor() + player.getName() + ChatColor.GREEN + " has recovered their team's " + shardTeamColor.getChatColor() + shardTeamColor.name() + " Shard!");
        } else { // Enemy team picked up shard
            teamShardStates.put(shardTeamColor, ShardState.CARRIED_BY_ENEMY);
            broadcastToGamePlayers(playerTeam.getChatColor() + player.getName() + ChatColor.GREEN + " has stolen the " + shardTeamColor.getChatColor() + shardTeamColor.name() + " Shard!");
        }

        giveShardToPlayer(player, shardTeamColor);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        updateAllScoreboards();
    }

    private void giveShardToPlayer(Player player, TeamColor shardTeamColor) {
        ItemStack shardItem = new ItemStack(SHARD_MATERIAL); // Or a custom item
        ItemMeta meta = shardItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(shardTeamColor.getChatColor() + shardTeamColor.name() + " Shard");
            meta.setLore(List.of(ChatColor.GRAY + "Return this to your capture point!", ChatColor.DARK_PURPLE + "Property of " + shardTeamColor.name() + " Team"));
            // Could add Unbreakable, Enchantments (visual glow), CustomModelData etc.
            shardItem.setItemMeta(meta);
        }
        player.getInventory().addItem(shardItem); // Consider a specific slot or offhand
        // player.getInventory().setItemInOffHand(shardItem); // Example for offhand
    }

    private void clearShardFromInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == SHARD_MATERIAL && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.getDisplayName() != null && meta.getDisplayName().contains("Shard")) {
                    player.getInventory().remove(item);
                }
            }
        }
        // Also check offhand
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        if (offHandItem != null && offHandItem.getType() == SHARD_MATERIAL && offHandItem.hasItemMeta()) {
            ItemMeta meta = offHandItem.getItemMeta();
            if (meta != null && meta.getDisplayName() != null && meta.getDisplayName().contains("Shard")) {
                player.getInventory().setItemInOffHand(null);
            }
        }
    }


    public void playerAttemptCapture(Player player) {
        if (gameState != GameState.ACTIVE) return;
        TeamColor playerTeam = playerTeams.get(player.getUniqueId());
        if (playerTeam == null) return;

        TeamColor enemyTeamColor = (playerTeam == TeamColor.RED) ? TeamColor.BLUE : TeamColor.RED;

        if (shardCarriers.get(enemyTeamColor) != null && shardCarriers.get(enemyTeamColor).equals(player.getUniqueId())) {
            // Player is carrying the enemy shard
            Location capturePoint = teamCapturePoints.get(playerTeam);
            if (capturePoint != null && player.getLocation().getWorld().equals(capturePoint.getWorld()) &&
                    player.getLocation().distanceSquared(capturePoint) < 9) { // Within 3 blocks (distance squared 9)

                teamScores.put(playerTeam, teamScores.get(playerTeam) + 1);
                broadcastToGamePlayers(playerTeam.getChatColor() + player.getName() + " captured the " + enemyTeamColor.getChatColor() + enemyTeamColor.name() + " Shard for " + playerTeam.name() + " team!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 2f, 1.5f); // Capture sound
                ParticleUtil.spawnLocationEffect(capturePoint.clone().add(0.5,1,0.5), Particle.FIREWORK, 30, 0.5,0.5,0.5,0.1);


                clearShardFromInventory(player); // Remove from carrier's inventory
                resetShard(enemyTeamColor, true); // Reset the captured shard

                if (teamScores.get(playerTeam) >= capturesToWin) {
                    stop(false); // Winning team, end game
                } else {
                    updateAllScoreboards();
                }
            } else {
                player.sendMessage(ChatColor.YELLOW + "You need to be at your team's capture point to score!");
            }
        } else {
            player.sendMessage(ChatColor.YELLOW + "You are not carrying the enemy shard!");
        }
    }

    private void dropShard(Player carrier, TeamColor shardTeamColor) {
        // Called when a shard carrier dies or disconnects
        if (!shardCarriers.containsKey(shardTeamColor) || !shardCarriers.get(shardTeamColor).equals(carrier.getUniqueId())) {
            return; // Player wasn't carrying this shard or no one was.
        }

        clearShardFromInventory(carrier);
        shardCarriers.remove(shardTeamColor); // No longer carried by this player

        // For simplicity, immediately return to pedestal.
        // A more complex system could drop it as an item on the ground for a short time.
        broadcastToGamePlayers(ChatColor.YELLOW + "The " + shardTeamColor.getChatColor() + shardTeamColor.name() + " Shard " + ChatColor.YELLOW + "was dropped and has returned to its pedestal!");
        resetShard(shardTeamColor, true);
        updateAllScoreboards();
    }

    private void resetShard(TeamColor shardTeamColor, boolean announce) {
        shardCarriers.remove(shardTeamColor); // Remove any carrier mapping
        teamShardStates.put(shardTeamColor, ShardState.AT_PEDESTAL);

        Location pedestalLoc = teamShardPedestals.get(shardTeamColor);
        if (pedestalLoc != null) {
            pedestalLoc.getBlock().setType(SHARD_MATERIAL); // Place shard block back
            ParticleUtil.spawnLocationEffect(pedestalLoc.clone().add(0.5,1,0.5), Particle.REVERSE_PORTAL, 30, 0.3,0.5,0.3,0.05);
        }

        if (announce) {
            broadcastToGamePlayers(shardTeamColor.getChatColor() + shardTeamColor.name() + " Shard" + ChatColor.GRAY + " has been returned to its pedestal!");
        }
        updateAllScoreboards();
    }


    private void checkGameEndConditions() {
        if (gameState != GameState.ACTIVE) return;
        for (TeamColor team : TeamColor.values()) {
            if (teamScores.getOrDefault(team, 0) >= capturesToWin) {
                stop(false);
                return;
            }
        }
        // Time limit is checked in gameTick()
    }

    @Override
    public void broadcastToGamePlayers(String message) {
        String prefix = ChatColor.AQUA + "[CTS-" + definition.getDisplayName() + "] " + ChatColor.RESET;
        for (UUID uuid : playersInGame) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) p.sendMessage(prefix + message);
        }
    }

    private void updateAllScoreboards() {
        for (UUID uuid : playersInGame) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) updateScoreboard(p);
        }
    }

    private void updateScoreboard(Player player) {
        GameScoreboard sb = playerScoreboards.get(player.getUniqueId());
        if (sb == null) return;
        sb.clearAllLines();
        int line = 0;

        sb.setLine(line++, "&7Time: &e" + formatTime(timeRemainingSeconds));
        sb.setLine(line++, "&m--------------------");

        for (TeamColor team : TeamColor.values()) {
            String teamNameDisplay = team.getChatColor() + team.name();
            String scoreDisplay = "&fScore: " + teamScores.getOrDefault(team, 0) + "/" + capturesToWin;
            String shardStatusDisplay;
            ShardState state = teamShardStates.get(team);
            switch (state) {
                case AT_PEDESTAL: shardStatusDisplay = "&a(Safe)"; break;
                case CARRIED_BY_ENEMY:
                    Player carrier = shardCarriers.containsKey(team) ? Bukkit.getPlayer(shardCarriers.get(team)) : null;
                    shardStatusDisplay = "&c(Stolen" + (carrier != null ? " by " + carrier.getName() : "") + ")";
                    break;
                case CARRIED_BY_OWN_TEAM: // Recovered, being returned
                    Player ownCarrier = shardCarriers.containsKey(team) ? Bukkit.getPlayer(shardCarriers.get(team)) : null;
                    shardStatusDisplay = "&e(Recovered" + (ownCarrier != null ? " by " + ownCarrier.getName() : "") + ")";
                    break;
                case DROPPED: shardStatusDisplay = "&6(Dropped!)"; break; // If implementing timed drop
                default: shardStatusDisplay = "";
            }
            sb.setLine(line++, teamNameDisplay + " " + scoreDisplay + " " + shardStatusDisplay);
        }
        sb.setLine(line++, "&m--------------------");

        // Player's current status (e.g., carrying a shard)
        Optional<Map.Entry<TeamColor, UUID>> carryingEntry = shardCarriers.entrySet().stream()
                .filter(entry -> entry.getValue().equals(player.getUniqueId()))
                .findFirst();
        if (carryingEntry.isPresent()) {
            TeamColor carriedShardTeam = carryingEntry.get().getKey();
            sb.setLine(line++, "&6Carrying: " + carriedShardTeam.getChatColor() + carriedShardTeam.name() + " Shard");
        }

        if (activeVotingHook != null) {
            sb.setLine(line++, "&m--------------------");
            sb.setLine(line++, "&dEvent: &f" + activeVotingHook.getDisplayName());
            if (activeHookEndTimeMillis > 0) {
                long hookTimeLeft = (activeHookEndTimeMillis - System.currentTimeMillis()) / 1000;
                if (hookTimeLeft > 0) sb.setLine(line++, "&dTime Left: &f" + formatTime((int)hookTimeLeft));
            }
        }
    }

    private String formatTime(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // Admin methods for temporary instance modification (called by CaptureTheShardCommand)
    public void adminSetTeamShardPedestalLocation(TeamColor teamColor, Location location) {
        if (teamColor == null || location == null) return;
        teamShardPedestals.put(teamColor, location.clone());
        resetShard(teamColor, false); // Place shard block at new location
        logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Admin set " + teamColor.name() + " pedestal to " + location.toString());
        broadcastToGamePlayers(ChatColor.YELLOW + "Admin: " + teamColor.name() + " shard pedestal location updated for this match.");
    }

    public void adminSetTeamCapturePointLocation(TeamColor teamColor, Location location) {
        if (teamColor == null || location == null) return;
        teamCapturePoints.put(teamColor, location.clone());
        logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Admin set " + teamColor.name() + " capture point to " + location.toString());
        broadcastToGamePlayers(ChatColor.YELLOW + "Admin: " + teamColor.name() + " capture point location updated for this match.");
    }

    public void adminAddTeamPlayerSpawn(TeamColor teamColor, Location location) {
        if (teamColor == null || location == null) return;
        teamPlayerSpawns.computeIfAbsent(teamColor, k -> new ArrayList<>()).add(location.clone());
        logger.info("[CTSInstance:" + instanceId.toString().substring(0,8) + "] Admin added " + teamColor.name() + " player spawn at " + location.toString());
        broadcastToGamePlayers(ChatColor.YELLOW + "Admin: Added a player spawn for " + teamColor.name() + " team for this match.");
    }
}

enum ShardState {
    AT_PEDESTAL,
    CARRIED_BY_ENEMY,
    CARRIED_BY_OWN_TEAM, // If own team recovers their dropped shard
    DROPPED // If implementing timed ground drop
}