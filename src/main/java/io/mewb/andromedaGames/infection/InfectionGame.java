package io.mewb.andromedaGames.infection;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.arena.ArenaDefinition;
import io.mewb.andromedaGames.game.GameDefinition;
import io.mewb.andromedaGames.game.GameInstance;
import io.mewb.andromedaGames.game.GameState;
import io.mewb.andromedaGames.infection.votinghooks.InfectedSpeedBoostHook;
import io.mewb.andromedaGames.infection.votinghooks.RevealSurvivorsHook;
import io.mewb.andromedaGames.infection.votinghooks.SurvivorSpeedBoostHook;
import io.mewb.andromedaGames.utils.GameScoreboard;
import io.mewb.andromedaGames.utils.ParticleUtil;
import io.mewb.andromedaGames.voting.VoteManager;
import io.mewb.andromedaGames.voting.VotingHook;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team; // Spigot API Team

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class InfectionGame extends GameInstance implements Listener {

    // Game parameters from GameDefinition's rules
    private int gameDurationSeconds;
    private int initialInfectedCount;
    private int countdownSeconds;
    private String scoreboardTitle;
    private GameMode survivorGamemode = GameMode.SURVIVAL;
    private GameMode infectedGamemode = GameMode.SURVIVAL;
    private int minPlayersToStart;

    // Arena details - resolved in setupInstance()
    private Location absoluteLobbySpawn;
    private List<Location> absoluteGameSpawns;

    // Game state tracking specific to Infection
    private final Set<UUID> infectedPlayers = new HashSet<>();
    private final Set<UUID> survivorPlayers = new HashSet<>(); // All players in playersInGame not in infectedPlayers

    // Tasks
    private BukkitTask gameTimerTask;
    private BukkitTask countdownTask;
    private int timeRemainingSeconds;

    // Scoreboard Teams for visual differentiation (Spigot API)
    private Team infectedTeamSpigot;
    private Team survivorTeamSpigot;
    private String infectedTeamNameSpigot; // Unique name for Spigot team for this instance
    private String survivorTeamNameSpigot; // Unique name for Spigot team for this instance

    private final Random random = new Random();

    public InfectionGame(AndromedaGames plugin, UUID instanceId, GameDefinition definition, ArenaDefinition arena, Location instanceBaseWorldLocation) {
        super(plugin, instanceId, definition, arena, instanceBaseWorldLocation);
        // playersInGame is initialized in GameInstance constructor
        // availableVotingHooks is initialized in GameInstance constructor
        // logger is available from GameInstance
    }

    @Override
    public void setupInstance() {
        this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0, 8) + "] Setting up with definition '" + definition.getDefinitionId() + "' and arena '" + arena.getArenaId() + "'.");

        // Load rules from GameDefinition (inherited `this.definition`)
        this.gameDurationSeconds = definition.getRule("game_duration_seconds", 300);
        this.timeRemainingSeconds = this.gameDurationSeconds;
        this.initialInfectedCount = definition.getRule("initial_infected_count", 1);
        this.countdownSeconds = definition.getRule("countdown_seconds", 15);
        this.minPlayersToStart = definition.getRule("min_players_to_start", 2);
        this.scoreboardTitle = ChatColor.translateAlternateColorCodes('&', definition.getRule("scoreboard_title", "&c&lINFECTION: &e" + definition.getDisplayName()));

        try {
            this.survivorGamemode = GameMode.valueOf(definition.getRule("survivor_gamemode", "SURVIVAL").toString().toUpperCase());
            this.infectedGamemode = GameMode.valueOf(definition.getRule("infected_gamemode", "SURVIVAL").toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            this.logger.warning("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Invalid gamemode in definition. Defaulting to SURVIVAL. Error: " + e.getMessage());
            this.survivorGamemode = GameMode.SURVIVAL;
            this.infectedGamemode = GameMode.SURVIVAL;
        }

        // Load locations from ArenaDefinition, making them absolute using helpers from GameInstance
        this.absoluteLobbySpawn = getAbsoluteLocation("lobby_spawn");
        this.absoluteGameSpawns = getAbsoluteLocationList("game_spawns");

        // Critical checks for essential locations
        if (this.absoluteLobbySpawn == null || (this.absoluteLobbySpawn.equals(this.instanceBaseWorldLocation) && arena.getRelativeLocation("lobby_spawn") == null)) {
            this.logger.severe("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] CRITICAL: Lobby spawn could not be resolved. Arena: '" + arena.getArenaId() + "', Key: 'lobby_spawn'. Disabling instance.");
            setGameState(GameState.DISABLED);
            return;
        }
        if (this.absoluteGameSpawns.isEmpty() && arena.getRelativeLocationList("game_spawns", logger, "").isEmpty()) { // Check if the key was actually missing or just resolved to empty
            this.logger.severe("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] CRITICAL: No game spawns could be resolved. Arena: '" + arena.getArenaId() + "', Key: 'game_spawns'. Disabling instance.");
            setGameState(GameState.DISABLED);
            return;
        }
        this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Lobby Spawn: " + (absoluteLobbySpawn != null ? absoluteLobbySpawn.toString() : "NOT SET"));
        this.absoluteGameSpawns.forEach(loc -> this.logger.finer("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Game Spawn: " + loc.toString()));


        // Initialize unique Spigot team names for this instance
        // Using a shorter, more readable prefix for team names
        String instancePrefix = "ag_" + instanceId.toString().substring(0, 4);
        this.infectedTeamNameSpigot = instancePrefix + "_inf";
        this.survivorTeamNameSpigot = instancePrefix + "_surv";
        setupSpigotScoreboardTeams();

        // Voting System Configuration from GameDefinition
        ConfigurationSection votingConfigSection = definition.getVotingConfig();
        if (votingConfigSection != null) {
            this.votingEnabled = votingConfigSection.getBoolean("enabled", false); // Inherited field
            this.voteIntervalSeconds = votingConfigSection.getInt("interval_seconds", 75); // Inherited
            this.voteEventDurationSeconds = votingConfigSection.getInt("duration_seconds", 15); // Inherited
            List<String> hookIdsFromDef = votingConfigSection.getStringList("hooks_available");

            initializeVotingHooksFromDef(hookIdsFromDef); // Populates this.availableVotingHooks (inherited)

            if (this.votingEnabled && !this.availableVotingHooks.isEmpty()) {
                this.voteManager = new VoteManager(plugin, this); // Inherited field, pass this GameInstance
                this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Voting system configured: Enabled=" + votingEnabled + ", " + availableVotingHooks.size() + " hooks loaded.");
            } else {
                this.votingEnabled = false; // Ensure it's false if no hooks or not enabled in def
                this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Voting system disabled (not enabled in definition or no hooks available/loaded).");
            }
        } else {
            this.votingEnabled = false;
            this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] No 'voting' configuration section found in GameDefinition. Voting will be disabled.");
        }

        if (this.gameState != GameState.DISABLED) {
            setGameState(GameState.WAITING);
            this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] setup complete and ready (WAITING).");
        }
    }

    private void initializeVotingHooksFromDef(List<String> hookIds) {
        availableVotingHooks.clear(); // Clear inherited list first
        if (hookIds == null || hookIds.isEmpty()) {
            this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] No voting hook IDs provided in definition.");
            return;
        }
        for (String hookId : hookIds) {
            switch (hookId.toLowerCase()) {
                case "infection_reveal_survivors":
                    availableVotingHooks.add(new RevealSurvivorsHook());
                    break;
                case "infection_survivor_speed_boost":
                    availableVotingHooks.add(new SurvivorSpeedBoostHook());
                    break;
                case "infection_infected_speed_boost":
                    availableVotingHooks.add(new InfectedSpeedBoostHook());
                    break;
                // Add other Infection-specific hooks here
                default:
                    this.logger.warning("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Unknown or unimplemented voting hook ID in GameDefinition: " + hookId);
            }
        }
        this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Loaded " + availableVotingHooks.size() + " voting hooks from definition.");
    }

    private void setupSpigotScoreboardTeams() {
        org.bukkit.scoreboard.Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Cleanup existing teams with the same name first (e.g., from a server reload or unclean shutdown)
        Team existingInfectedTeam = mainScoreboard.getTeam(infectedTeamNameSpigot);
        if (existingInfectedTeam != null) existingInfectedTeam.unregister();
        infectedTeamSpigot = mainScoreboard.registerNewTeam(infectedTeamNameSpigot);
        infectedTeamSpigot.setColor(ChatColor.RED);
        infectedTeamSpigot.setPrefix(ChatColor.RED + "[INFECTED] ");
        infectedTeamSpigot.setAllowFriendlyFire(false); // Infected shouldn't hurt each other by default
        infectedTeamSpigot.setCanSeeFriendlyInvisibles(true); // Important if infected get invisibility

        Team existingSurvivorTeam = mainScoreboard.getTeam(survivorTeamNameSpigot);
        if (existingSurvivorTeam != null) existingSurvivorTeam.unregister();
        survivorTeamSpigot = mainScoreboard.registerNewTeam(survivorTeamNameSpigot);
        survivorTeamSpigot.setColor(ChatColor.GREEN);
        survivorTeamSpigot.setPrefix(ChatColor.GREEN + "[SURVIVOR] ");
        // Friendly fire for survivors is usually true in Bukkit by default, can be set if needed.
        this.logger.fine("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Spigot teams '" + infectedTeamNameSpigot + "' and '" + survivorTeamNameSpigot + "' configured.");
    }

    private void clearSpigotScoreboardTeams() {
        org.bukkit.scoreboard.Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        if (infectedTeamSpigot != null) {
            Team teamToUnregister = mainScoreboard.getTeam(infectedTeamSpigot.getName()); // Use getName() for safety
            if (teamToUnregister != null) {
                teamToUnregister.unregister();
                this.logger.fine("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Unregistered Spigot team: " + infectedTeamSpigot.getName());
            }
            infectedTeamSpigot = null;
        }
        if (survivorTeamSpigot != null) {
            Team teamToUnregister = mainScoreboard.getTeam(survivorTeamSpigot.getName());
            if (teamToUnregister != null) {
                teamToUnregister.unregister();
                this.logger.fine("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Unregistered Spigot team: " + survivorTeamSpigot.getName());
            }
            survivorTeamSpigot = null;
        }
    }

    @Override
    public void cleanupInstance() {
        this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Cleaning up...");
        cancelTasks(); // Cancels countdownTask, gameTimerTask, and any active vote

        // Destroy player-specific scoreboards
        playerScoreboards.values().forEach(GameScoreboard::destroy);
        playerScoreboards.clear();

        HandlerList.unregisterAll(this); // Unregister instance-specific listeners
        clearSpigotScoreboardTeams(); // Unregister Spigot teams associated with this instance

        // playersInGame, infectedPlayers, survivorPlayers will be cleared if stop() is called,
        // or naturally when the instance is dereferenced.
        // Arena cleanup (schematic reset) should be handled by GameManager after calling this.
        this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Cleanup complete.");
    }

    private void cancelTasks() {
        if (countdownTask != null && !countdownTask.isCancelled()) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (gameTimerTask != null && !gameTimerTask.isCancelled()) {
            gameTimerTask.cancel();
            gameTimerTask = null;
        }
        if (voteManager != null && voteManager.isVoteActive()) {
            voteManager.endVote(false); // End vote without announcing a winner if game is ending abruptly
        }
        this.logger.fine("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] All scheduled tasks cancelled.");
    }

    @Override
    public boolean start(boolean bypassMinPlayerCheck) {
        if (gameState == GameState.DISABLED) {
            this.logger.warning("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Attempted to start but it is DISABLED.");
            return false;
        }
        if (gameState != GameState.WAITING && gameState != GameState.ENDING) { // Can restart from ENDING if needed
            this.logger.warning("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Cannot start, current state: " + gameState);
            return false;
        }

        if (!bypassMinPlayerCheck && playersInGame.size() < minPlayersToStart) {
            broadcastToGamePlayers(ChatColor.RED + "Not enough players to start! Need " + minPlayersToStart + ", have " + playersInGame.size() + ".");
            if (bypassMinPlayerCheck) this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Admin bypassed min player check.");
            else return false;
        } else if (playersInGame.isEmpty() && !bypassMinPlayerCheck) { // Cannot start with 0 players unless forced
            broadcastToGamePlayers(ChatColor.RED + "Cannot start with 0 players unless forced by an admin.");
            if (bypassMinPlayerCheck) this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Admin starting with 0 players.");
            else return false;
        }

        setGameState(GameState.STARTING);
        infectedPlayers.clear();
        survivorPlayers.clear();
        survivorPlayers.addAll(playersInGame); // Initially, all are survivors

        this.timeRemainingSeconds = this.gameDurationSeconds;
        this.activeVotingHook = null; // Reset active hook (inherited from GameInstance)
        this.activeHookEndTimeMillis = 0; // Reset hook end time (inherited)

        // Prepare players: clear inventory, set gamemode, setup scoreboards
        playersInGame.forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                playerStateManager.clearPlayerForGame(p, this.survivorGamemode); // All start as survivors

                GameScoreboard sb = playerScoreboards.get(uuid);
                if (sb == null) {
                    sb = new GameScoreboard(p, scoreboardTitle);
                    playerScoreboards.put(uuid, sb);
                } else {
                    sb.updateTitle(scoreboardTitle); // Ensure title is fresh
                }
                sb.show();

                if (survivorTeamSpigot != null) { // Add to Spigot survivor team
                    survivorTeamSpigot.addEntry(p.getName());
                }
                updateScoreboard(p); // Initial scoreboard display
            }
        });

        // Teleport players to game spawns
        if (!absoluteGameSpawns.isEmpty()) {
            List<UUID> playerList = new ArrayList<>(playersInGame);
            Collections.shuffle(playerList); // Shuffle for random spawn assignment
            for (int i = 0; i < playerList.size(); i++) {
                Player player = Bukkit.getPlayer(playerList.get(i));
                if (player != null && player.isOnline()) {
                    player.teleport(absoluteGameSpawns.get(i % absoluteGameSpawns.size()));
                }
            }
        } else {
            this.logger.warning("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] No game spawns defined! Players will spawn at lobby or current location.");
            // Fallback: teleport to lobby spawn if game spawns are missing
            playersInGame.forEach(uuid -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline() && absoluteLobbySpawn != null) p.teleport(absoluteLobbySpawn);
            });
        }

        startCountdown();
        return true;
    }

    private void startCountdown() {
        cancelTasks(); // Ensure no previous countdown is running
        final int[] currentCountdownValue = {this.countdownSeconds};
        broadcastToGamePlayers(ChatColor.GOLD + "INFECTION! " + ChatColor.YELLOW + "The game will begin soon. Someone will be chosen...");

        for (UUID uuid : playersInGame) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 0.8f, 0.7f);
        }

        this.countdownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (gameState != GameState.STARTING) {
                cancelTasks();
                return;
            }

            if (currentCountdownValue[0] > 0) {
                String titleMessage = ChatColor.RED + "GET READY!";
                String subtitleMessage = ChatColor.YELLOW.toString() + currentCountdownValue[0] + "...";
                for (UUID uuid : playersInGame) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        p.sendTitle(titleMessage, subtitleMessage, 0, 25, 5);
                        if (currentCountdownValue[0] <= 5) { // Sound for last 5 seconds
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 1f, 0.8f + (0.1f * (5 - currentCountdownValue[0])) );
                        }
                    }
                }
            }

            if (currentCountdownValue[0] <= 0) {
                cancelTasks(); // Stop this countdown task
                selectInitialInfected();
                activateGame(); // Transition to ACTIVE state and start game logic
            }
            currentCountdownValue[0]--;
        }, 0L, 20L); // 0L delay, 20L (1 second) period
    }

    private void selectInitialInfected() {
        if (survivorPlayers.isEmpty() && !playersInGame.isEmpty()) { // Should not happen if playersInGame populated survivorPlayers
            this.logger.warning("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] No survivors to select initial infected from, but players exist. Forcing game end.");
            stop(true); // Force stop if something went wrong
            return;
        }
        if (playersInGame.isEmpty()) {
            this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] No players in game to select initial infected.");
            stop(false); // Game ends if no players
            return;
        }


        List<UUID> potentialInfected = new ArrayList<>(survivorPlayers); // Select from current survivors
        Collections.shuffle(potentialInfected);

        int numToInfect = Math.min(initialInfectedCount, potentialInfected.size()); // Don't try to infect more than available

        for (int i = 0; i < numToInfect; i++) {
            UUID infectedUUID = potentialInfected.get(i);
            // infectPlayer will move from survivorPlayers to infectedPlayers and handle effects/teams
            infectPlayer(infectedUUID, null, false); // No specific infector, don't announce individually yet
        }

        // Announce roles after all initial infected are chosen
        for (UUID infectedUUID : infectedPlayers) {
            Player p = Bukkit.getPlayer(infectedUUID);
            if (p != null && p.isOnline()) {
                p.sendTitle(ChatColor.DARK_RED + "" + ChatColor.BOLD + "YOU ARE INFECTED!", ChatColor.YELLOW + "Spread the plague!", 10, 80, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, SoundCategory.PLAYERS, 1f, 0.7f);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 0.3f, 0.5f);
            }
        }
        for (UUID survivorUUID : survivorPlayers) { // These are the remaining survivors
            Player p = Bukkit.getPlayer(survivorUUID);
            if (p != null && p.isOnline()) {
                p.sendTitle(ChatColor.GREEN + "SURVIVE!", ChatColor.YELLOW + "The infected are among you...", 10, 70, 20);
                p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.PLAYERS, 0.5f, 1.2f);
            }
        }
        this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Selected " + infectedPlayers.size() + " initial infected. " + survivorPlayers.size() + " survivors remaining.");
    }

    private void infectPlayer(UUID targetUUID, Player infector, boolean announcePublicly) {
        if (infectedPlayers.contains(targetUUID)) return; // Already infected

        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        if (targetPlayer == null || !targetPlayer.isOnline() || !playersInGame.contains(targetUUID)) {
            this.logger.warning("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Attempted to infect non-existent or non-game player: " + targetUUID);
            return;
        }

        survivorPlayers.remove(targetUUID);
        infectedPlayers.add(targetUUID);

        // Apply infected effects
        // targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false, false)); // Persistent glow
        targetPlayer.setGameMode(infectedGamemode); // Change gamemode if different

        // Update Spigot scoreboard teams
        if (survivorTeamSpigot != null) survivorTeamSpigot.removeEntry(targetPlayer.getName());
        if (infectedTeamSpigot != null) infectedTeamSpigot.addEntry(targetPlayer.getName());

        // Notify the player they've been infected
        String infectorName = (infector != null) ? infector.getName() : "the initial plague";
        targetPlayer.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "You have been INFECTED!", ChatColor.YELLOW + "by " + infectorName + "!", 5, 60, 15);
        targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_ZOMBIE_INFECT, SoundCategory.PLAYERS, 1.2f, 0.9f);
        targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1f, 0.8f); // Generic damage sound
        ParticleUtil.spawnExplosionEffect(targetPlayer.getLocation().add(0, 1, 0), Particle.DAMAGE_INDICATOR, 20, 0.3f);
        ParticleUtil.spawnPlayerStatusParticles(targetPlayer, Particle.SMOKE, 15, 0.4, 0.5, 0.4, 0.02); // Visual cue


        if (announcePublicly) {
            broadcastToGamePlayers(ChatColor.RED + targetPlayer.getName() + " has succumbed to the infection (tagged by " + infectorName + ")!");
            if (infector != null) {
                infector.playSound(infector.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.PLAYERS, 0.8f, 1.2f); // Sound for infector
            }
        }
        this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Player " + targetPlayer.getName() + " infected by " + infectorName + ".");

        updateAllScoreboards();
        checkGameEndConditions();
    }

    private void activateGame() {
        if (gameState != GameState.STARTING) return; // Should only activate from STARTING
        setGameState(GameState.ACTIVE);
        plugin.getServer().getPluginManager().registerEvents(this, plugin); // Register instance-specific listener
        broadcastToGamePlayers(ChatColor.RED + "" + ChatColor.BOLD + "The INFECTION has begun! RUN or HUNT!");
        this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] is now ACTIVE.");

        this.timeRemainingSeconds = this.gameDurationSeconds; // Reset timer
        if (this.votingEnabled && this.voteManager != null) {
            this.lastVoteTriggerTimeMillis = System.currentTimeMillis(); // Initialize for first vote interval
        }

        // Start main game timer task
        this.gameTimerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (gameState != GameState.ACTIVE) {
                cancelTasks(); // Stop this task if game is no longer active
                return;
            }
            timeRemainingSeconds--;

            // Handle voting triggers
            if (votingEnabled && voteManager != null && !voteManager.isVoteActive() &&
                    availableVotingHooks != null && !availableVotingHooks.isEmpty()) {
                if ((System.currentTimeMillis() - lastVoteTriggerTimeMillis) / 1000 >= voteIntervalSeconds) {
                    triggerInfectionVote();
                }
            }

            // Handle active voting hook expiration
            if (activeVotingHook != null && activeHookEndTimeMillis > 0 && System.currentTimeMillis() >= activeHookEndTimeMillis) {
                broadcastToGamePlayers(ChatColor.YELLOW + activeVotingHook.getDisplayName() + " has worn off!");
                // Specific cleanup for the hook could be done here if VotingHook had a 'cleanUp' method
                activeVotingHook = null; // Reset active hook (inherited)
                activeHookEndTimeMillis = 0; // Reset hook end time (inherited)
            }

            updateAllScoreboards(); // Update scoreboards every second

            if (timeRemainingSeconds <= 0) {
                broadcastToGamePlayers(ChatColor.GOLD + "Time's up! The survivors have held out!");
                stop(false); // Survivors win if time runs out
            }
        }, 0L, 20L); // 0L delay, 20L (1 second) period
    }

    private void triggerInfectionVote() {
        if (availableVotingHooks == null || availableVotingHooks.isEmpty() || voteManager == null) {
            this.logger.fine("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] TriggerVote called but no hooks/manager available.");
            return;
        }
        this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Attempting to trigger vote.");

        List<VotingHook> optionsForVote = new ArrayList<>();
        List<VotingHook> hooksPool = new ArrayList<>(availableVotingHooks); // Use a copy to shuffle
        Collections.shuffle(hooksPool);

        int numberOfOptionsToPresent = Math.min(hooksPool.size(), 3); // Present up to 3 options
        if (hooksPool.size() < 2 && numberOfOptionsToPresent < 2) { // Need at least 2 distinct hooks to make a vote meaningful
            this.logger.warning("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Not enough unique, applicable voting hooks available (need >= 2, have " + hooksPool.size() + " applicable). Skipping vote trigger.");
            lastVoteTriggerTimeMillis = System.currentTimeMillis(); // Reset timer to try again later
            return;
        }
        // Ensure we have at least 2 options if possible
        if (numberOfOptionsToPresent < 2 && hooksPool.size() >=2) numberOfOptionsToPresent = 2;


        for (int i = 0; i < numberOfOptionsToPresent && !hooksPool.isEmpty(); ) {
            VotingHook selectedHook = hooksPool.remove(0); // Get from shuffled pool
            if (selectedHook.canApply(this)) { // Check if hook can be applied now
                optionsForVote.add(selectedHook);
                i++;
            }
        }

        if (optionsForVote.size() < 2) { // Still not enough applicable options
            this.logger.warning("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Could not find enough applicable hooks for vote (found " + optionsForVote.size() + "). Skipping vote trigger.");
            lastVoteTriggerTimeMillis = System.currentTimeMillis(); // Reset timer
            return;
        }

        if (voteManager.startVote(optionsForVote, voteEventDurationSeconds)) {
            lastVoteTriggerTimeMillis = System.currentTimeMillis(); // Reset vote timer only if vote started
            this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Vote started with " + optionsForVote.size() + " options.");
        } else {
            this.logger.warning("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] VoteManager failed to start vote. It might be already active or game not in correct state.");
            // Don't reset lastVoteTriggerTimeMillis here, to allow it to try again soon if it was a temporary issue.
            // Or, reset it to prevent rapid re-triggering if the failure is persistent. For now, let's reset.
            lastVoteTriggerTimeMillis = System.currentTimeMillis();
        }
    }

    @Override
    public void stop(boolean force) {
        this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Stopping. Forced: " + force + ". Current state: " + gameState);
        if (gameState == GameState.ENDING || gameState == GameState.DISABLED || gameState == GameState.UNINITIALIZED) {
            this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Already stopping or in a terminal state. Ignoring stop command.");
            return;
        }

        GameState previousState = gameState;
        setGameState(GameState.ENDING);
        cancelTasks(); // Stop game timer, countdown, and vote tasks
        HandlerList.unregisterAll(this); // Unregister instance-specific listener

        String winnerMessage;
        Sound endSound = Sound.ENTITY_VILLAGER_NO; float pitch = 1f;

        if (survivorPlayers.isEmpty() && !infectedPlayers.isEmpty() && (previousState == GameState.ACTIVE || force)) {
            winnerMessage = ChatColor.RED + "" + ChatColor.BOLD + "THE INFECTED HAVE WON!";
            endSound = Sound.ENTITY_ENDER_DRAGON_DEATH; pitch = 0.8f;
        } else if (!survivorPlayers.isEmpty() && (previousState == GameState.ACTIVE || force || timeRemainingSeconds <=0)) { // Survivors win if time ran out or forced
            String survivorsString = survivorPlayers.stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && p.isOnline())
                    .map(Player::getName)
                    .collect(Collectors.joining(", "));
            if (survivorPlayers.size() == 1 && playersInGame.size() > 1 && !infectedPlayers.isEmpty()) { // Check if there were actual infected
                winnerMessage = ChatColor.GREEN + "" + ChatColor.BOLD + survivorsString + " IS THE LAST SURVIVOR AND WINS!";
            } else {
                winnerMessage = ChatColor.GREEN + "" + ChatColor.BOLD + "SURVIVORS WIN!";
                if(!survivorsString.isEmpty()) winnerMessage += " Remaining: " + ChatColor.YELLOW + survivorsString;
            }
            endSound = Sound.UI_TOAST_CHALLENGE_COMPLETE; pitch = 1.2f;
        } else {
            winnerMessage = ChatColor.YELLOW + "Infection game '" + definition.getDisplayName() + "' ended.";
            if (playersInGame.isEmpty() && previousState == GameState.WAITING) { // Game ended before it could really start due to no players
                winnerMessage = ChatColor.YELLOW + "Infection game '" + definition.getDisplayName() + "' ended as no players joined.";
            }
        }
        broadcastToGamePlayers(winnerMessage);

        final Sound finalEndSound = endSound;
        final float finalPitch = pitch;

        // Use a copy of playersInGame for safe iteration while modifying player states/teleporting
        String finalWinnerMessage = winnerMessage;
        new HashSet<>(playersInGame).forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendTitle(finalWinnerMessage.substring(0, Math.min(finalWinnerMessage.length(), 45)), // Max title length
                        (survivorPlayers.isEmpty() && !infectedPlayers.isEmpty() ? ChatColor.DARK_RED : ChatColor.DARK_GREEN) + "Thanks for playing!",
                        10, 80, 30);
                player.playSound(player.getLocation(), finalEndSound, SoundCategory.PLAYERS, 1f, finalPitch);

                GameScoreboard sb = playerScoreboards.remove(uuid); // Remove and destroy scoreboard
                if (sb != null) sb.destroy();

                playerStateManager.restorePlayerState(player); // Restore original state
                player.removePotionEffect(PotionEffectType.GLOWING); // Ensure glow is removed

                // Remove from Spigot teams
                if (infectedTeamSpigot != null) infectedTeamSpigot.removeEntry(player.getName());
                if (survivorTeamSpigot != null) survivorTeamSpigot.removeEntry(player.getName());

                if (absoluteLobbySpawn != null) { // Teleport to lobby
                    player.teleport(absoluteLobbySpawn);
                } else if (getGameWorld() != null) { // Fallback to world spawn if lobby not set
                    player.teleport(getGameWorld().getSpawnLocation());
                }
            } else { // Player logged off during game
                playerStateManager.removePlayerState(Bukkit.getOfflinePlayer(uuid).getPlayer()); // Clean up saved state
            }
        });

        // Clear all instance-specific player tracking sets
        playersInGame.clear();
        infectedPlayers.clear();
        survivorPlayers.clear();

        // Arena reset is handled by GameManager when it calls cleanupInstance() and then potentially resets arena.
        setGameState(GameState.WAITING); // Set back to WAITING for potential reuse or proper shutdown by GameManager
        this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] logic finished, set to WAITING.");
        // GameManager should call cleanupInstance() which might then trigger ArenaManager.resetArena(this.arena, this.instanceBaseWorldLocation)
    }


    @Override
    public boolean addPlayer(Player player) {
        this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Attempting to add player " + player.getName() + ". Current state: " + gameState);
        if (gameState == GameState.DISABLED) {
            player.sendMessage(ChatColor.RED + "The game '" + definition.getDisplayName() + "' is currently disabled.");
            return false;
        }
        // Allow joining in WAITING or STARTING (if game def allows late joins, not implemented here yet)
        if (gameState != GameState.WAITING) {
            player.sendMessage(ChatColor.RED + "Infection game '" + definition.getDisplayName() + "' cannot be joined at this time (State: " + gameState + ").");
            return false;
        }
        if (playersInGame.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "You are already in this game.");
            return false;
        }

        int maxPlayers = definition.getRule("max_players", 20);
        if (playersInGame.size() >= maxPlayers) {
            player.sendMessage(ChatColor.RED + "This game instance is full (" + playersInGame.size() + "/" + maxPlayers + ")!");
            return false;
        }

        playerStateManager.savePlayerState(player); // Save state before modifying

        if (absoluteLobbySpawn == null) { // Should have been caught in setupInstance if critical
            this.logger.severe("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] CRITICAL: Lobby spawn is null! Cannot add player " + player.getName() + ".");
            player.sendMessage(ChatColor.RED + "Error: Lobby spawn not set for this game. Please contact an admin.");
            playerStateManager.restorePlayerState(player); // Restore immediately if cannot proceed
            return false;
        }
        player.teleport(absoluteLobbySpawn); // Teleport to instance's lobby spawn

        playersInGame.add(player.getUniqueId()); // Add to the main set in GameInstance
        // Player will be added to survivorPlayers when game starts or if joining mid-game (not yet supported for mid-game join)

        player.sendMessage(ChatColor.GREEN + "You joined Infection: " + definition.getDisplayName());
        broadcastToGamePlayers(ChatColor.AQUA + player.getName() + ChatColor.GRAY + " joined! (" + playersInGame.size() + " players)");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.5f);

        // If game is WAITING, and now meets min players, GameManager might trigger a start, or it's manual.
        // For now, player just joins the waiting pool.
        this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Successfully added player " + player.getName() + ". Total players: " + playersInGame.size());
        return true; // Return true indicating player was added to the instance's list
    }

    @Override
    public void removePlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Attempting to remove player " + player.getName());

        GameScoreboard sb = playerScoreboards.remove(playerUUID);
        if (sb != null) sb.destroy();

        boolean wasInGame = playersInGame.remove(playerUUID); // Remove from master list in GameInstance
        boolean wasInfected = infectedPlayers.remove(playerUUID);
        boolean wasSurvivor = survivorPlayers.remove(playerUUID);

        playerStateManager.restorePlayerState(player); // Restore state regardless of role
        player.removePotionEffect(PotionEffectType.GLOWING); // Ensure effects are cleared

        // Remove from Spigot teams
        if (infectedTeamSpigot != null) infectedTeamSpigot.removeEntry(player.getName());
        if (survivorTeamSpigot != null) survivorTeamSpigot.removeEntry(player.getName());

        if (wasInGame) {
            player.sendMessage(ChatColor.GRAY + "You left Infection: " + definition.getDisplayName());
            broadcastToGamePlayers(ChatColor.AQUA + player.getName() + ChatColor.GRAY + " has left the game.");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.5f, 1.0f);

            // Teleport out if still in game world (though restorePlayerState might handle this if it includes location)
            if (absoluteLobbySpawn != null && player.isOnline()) {
                // Check if player is in the game world or a different one
                if (getGameWorld() != null && player.getWorld().equals(getGameWorld())) {
                    if (!player.getWorld().equals(absoluteLobbySpawn.getWorld()) || player.getLocation().distanceSquared(absoluteLobbySpawn) > 225) { // Heuristic distance check
                        player.teleport(absoluteLobbySpawn);
                    }
                }
            }


            if (gameState == GameState.ACTIVE || gameState == GameState.STARTING) {
                checkGameEndConditions(); // Check if game should end due to player leaving
            }
            this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Successfully removed player " + player.getName() + ". Remaining players: " + playersInGame.size());
        } else {
            this.logger.warning("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Player " + player.getName() + " was not in playersInGame set during removal attempt.");
        }
    }

    @Override
    protected void gameTick() {
        // This method is called by GameInstance.tick() if gameState is ACTIVE.
        // The main game timer and periodic updates (like scoreboard, vote checks) are handled
        // by the BukkitTask scheduled in `activateGame()`.
        // This gameTick() could be used for more frequent, per-tick logic if needed,
        // but for Infection, the 1-second timer in activateGame() handles most periodic tasks.
        // For example, if infected players had a passive particle effect, it could be spawned here.
        if (gameState != GameState.ACTIVE) return;

        for(UUID infectedUUID : infectedPlayers) {
            Player p = Bukkit.getPlayer(infectedUUID);
            if(p != null && p.isOnline()) {
                ParticleUtil.spawnPlayerStatusParticles(p, Particle.ASH, 1, 0.1, 0.1, 0.1, 0); // Subtle ash for infected
            }
        }

    }

    @EventHandler
    public void onPlayerDamageByPlayer(EntityDamageByEntityEvent event) {
        if (gameState != GameState.ACTIVE) return; // Only process during active game
        if (!(event.getEntity() instanceof Player && event.getDamager() instanceof Player)) return;

        Player damaged = (Player) event.getEntity();
        Player damager = (Player) event.getDamager();

        // CRITICAL: Ensure both players are part of THIS game instance
        if (!playersInGame.contains(damaged.getUniqueId()) || !playersInGame.contains(damager.getUniqueId())) {
            return; // Event is not relevant to this instance
        }

        // Infection logic: an infected player damages a survivor
        if (infectedPlayers.contains(damager.getUniqueId()) && survivorPlayers.contains(damaged.getUniqueId())) {
            infectPlayer(damaged.getUniqueId(), damager, true); // Infect the survivor, announce publicly
            event.setDamage(0.1); // Minimize actual damage, the tag is the important part
            // Could add custom sound/particle for successful infection tag
            damager.playSound(damager.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f);
        } else if (infectedPlayers.contains(damaged.getUniqueId()) && infectedPlayers.contains(damager.getUniqueId())) {
            event.setCancelled(true); // Infected cannot damage other infected
        }
        // Survivors damaging survivors is allowed by default (PvP) unless configured otherwise.
        // Survivors damaging infected is also allowed by default.
    }

    private void checkGameEndConditions() {
        if (gameState != GameState.ACTIVE) return; // Only check if game is active

        // Condition 1: All survivors are infected
        if (survivorPlayers.isEmpty() && !infectedPlayers.isEmpty() && !playersInGame.isEmpty()) {
            this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Game ending: All survivors infected.");
            stop(false); // Infected win
            return;
        }

        // Condition 2: Only one survivor left (and there were infected to begin with)
        // This is a common "last man standing" win condition.
        if (survivorPlayers.size() == 1 && !infectedPlayers.isEmpty() && playersInGame.size() > 1) {
            this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Game ending: One survivor remains.");
            stop(false); // Last survivor wins
            return;
        }

        // Condition 3: No players left in game (e.g. everyone quit)
        if (playersInGame.isEmpty() && (gameState == GameState.ACTIVE || gameState == GameState.STARTING)) {
            this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Game ending: No players left.");
            stop(true); // Force stop, no winner
            return;
        }

        // Condition 4: (Less common for infection, but possible) No infected left, but survivors remain.
        // This could happen if initial infected quit immediately.
        if (infectedPlayers.isEmpty() && !survivorPlayers.isEmpty() && playersInGame.size() > 0 && initialInfectedCount > 0 && gameState == GameState.ACTIVE) {
            this.logger.info("[InfectionInstance:" + instanceId.toString().substring(0,8) + "] Game ending: No infected players left, survivors win by default.");
            stop(false); // Survivors win
            return;
        }
    }

    @Override
    public void broadcastToGamePlayers(String message) {
        String prefix = ChatColor.DARK_RED + "[INFECTION-" + definition.getDisplayName() + "] " + ChatColor.RESET;
        for (UUID uuid : playersInGame) { // Iterate over playersInGame from GameInstance
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(prefix + message);
            }
        }
    }

    private void updateAllScoreboards() {
        for (UUID playerUUID : playersInGame) { // Iterate over playersInGame from GameInstance
            Player p = Bukkit.getPlayer(playerUUID);
            if (p != null && p.isOnline()) {
                updateScoreboard(p);
            }
        }
    }

    private void updateScoreboard(Player player) {
        GameScoreboard sb = playerScoreboards.get(player.getUniqueId());
        if (sb == null) return; // Should not happen if player is in game and scoreboard was set up

        sb.clearAllLines(); // Clear previous lines
        int line = 0;

        sb.setLine(line++, "&7Time Left: &e" + formatTime(this.timeRemainingSeconds));
        sb.setLine(line++, "&m--------------------"); // Separator
        sb.setLine(line++, "&aSurvivors: &f" + survivorPlayers.size());
        sb.setLine(line++, "&cInfected: &f" + infectedPlayers.size());
        sb.setLine(line++, "&m--------------------"); // Separator

        // Player's role
        if (infectedPlayers.contains(player.getUniqueId())) {
            sb.setLine(line++, "&cYOU ARE INFECTED");
            sb.setLine(line++, "&eObjective: Infect survivors!");
        } else if (survivorPlayers.contains(player.getUniqueId())) {
            sb.setLine(line++, "&aYOU ARE A SURVIVOR");
            sb.setLine(line++, "&eObjective: Survive!");
        } else {
            sb.setLine(line++, "&7Role: Spectating (or error)"); // Fallback
        }

        // Active voting hook display
        if (activeVotingHook != null) { // Check inherited field
            sb.setLine(line++, "&m--------------------");
            sb.setLine(line++, "&dEvent: &f" + activeVotingHook.getDisplayName());
            if (activeHookEndTimeMillis > 0) { // Check inherited field
                long hookTimeLeft = (activeHookEndTimeMillis - System.currentTimeMillis()) / 1000;
                if (hookTimeLeft > 0) {
                    sb.setLine(line++, "&dTime Left: &f" + formatTime((int) hookTimeLeft));
                }
            }
        }
        // Ensure scoreboard is shown (it should be, but just in case)
        // sb.show(); // GameScoreboard.show() is typically called once when player joins/game starts
    }

    private String formatTime(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // Getters for Voting Hooks that might need specific access to roles
    public Set<UUID> getModifiableSurvivorPlayers() { return survivorPlayers; }
    public Set<UUID> getModifiableInfectedPlayers() { return infectedPlayers; }

    // setActiveVotingHook is inherited from GameInstance and should be sufficient.
    // If InfectionGame needs to react specifically when a hook is set (beyond scoreboard update),
    // it can override setActiveVotingHook, call super.setActiveVotingHook(), then add its logic.
}