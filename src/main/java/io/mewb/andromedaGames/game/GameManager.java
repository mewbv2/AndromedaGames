package io.mewb.andromedaGames.game;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.arena.ArenaDefinition;
import io.mewb.andromedaGames.arena.ArenaManager; // Required for schematic operations
import io.mewb.andromedaGames.capturetheshard.CaptureTheShardGame;
import io.mewb.andromedaGames.config.ConfigManager;
import io.mewb.andromedaGames.infection.InfectionGame;
import io.mewb.andromedaGames.koth.KoTHGame;
import io.mewb.andromedaGames.utils.RelativeLocation; // Required for setup locations

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class GameManager implements Listener {

    private final AndromedaGames plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final ArenaManager arenaManager; // Added for schematic pasting/clearing

    private final Map<String, ArenaDefinition> loadedArenaDefinitions = new HashMap<>();
    private final Map<String, GameDefinition> loadedGameDefinitions = new HashMap<>();

    private final Map<UUID, GameInstance> runningGameInstances = new HashMap<>();
    private final Map<UUID, UUID> playerCurrentInstance = new HashMap<>();

    // --- Arena Setup Mode State Variables ---
    private Player adminInSetupMode = null;
    private String currentSetupArenaId = null;
    private ArenaDefinition currentSetupArenaDefinition = null; // Store the loaded definition being edited
    private Location currentSetupPasteOrigin = null; // Where the schematic was pasted for setup
    // Stores RelativeLocation or List<RelativeLocation> being defined in the current session
    private Map<String, Object> currentSessionRelativeLocations = new HashMap<>();
    private Location adminOriginalLocation = null;
    private GameMode adminOriginalGameMode = null;
    private boolean adminOriginalAllowFlight = false;
    private boolean adminOriginalFlyingState = false;
    // --- End Arena Setup Mode State Variables ---


    public GameManager(AndromedaGames plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = plugin.getConfigManager();
        this.arenaManager = plugin.getArenaManager(); // Get ArenaManager instance
        logger.info("[GM_DEBUG] GameManager instance CREATED.");
    }

    public void initialize() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        logger.info("GameManager initialized and registered as event listener.");
        loadAllDefinitionsAndArenas();

        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (GameInstance instance : new ArrayList<>(runningGameInstances.values())) {
                if (instance.getGameState() == GameState.ACTIVE) {
                    try {
                        instance.tick();
                    } catch (Exception e) {
                        logger.severe("Error during game tick for instance " + instance.getInstanceId() + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }, 0L, 1L);
    }

    public void loadAllDefinitionsAndArenas() {
        // ... (existing code for loading definitions, unchanged) ...
        logger.info("Loading all arena and game definitions... GameManager instance: " + this.hashCode());
        if (!runningGameInstances.isEmpty()) {
            logger.info("Stopping all running game instances for definition reload...");
            for (GameInstance instance : new ArrayList<>(runningGameInstances.values())) {
                logger.info("[GM_DEBUG] Stopping game instance " + instance.getInstanceId() + " during reload.");
                endGameInstance(instance.getInstanceId());
            }
            playerCurrentInstance.clear();
            logger.info("[GM_DEBUG] runningGameInstances and playerCurrentInstance maps cleared after stopping all instances.");
        }
        loadedArenaDefinitions.clear();
        loadedGameDefinitions.clear();

        loadedArenaDefinitions.putAll(configManager.loadAllArenaDefinitions());
        logger.info("Loaded " + loadedArenaDefinitions.size() + " arena definitions.");

        String[] gameTypesToLoad = {"koth", "infection", "capturetheshard", "anvilrain", "colorcollapse", "chickenspleef"};
        for (String gameType : gameTypesToLoad) {
            Map<String, GameDefinition> definitions = configManager.loadAllGameDefinitionsOfType(gameType);
            for (GameDefinition def : definitions.values()) {
                loadedGameDefinitions.put(def.getDefinitionId().toLowerCase(), def);
            }
        }
        logger.info("Loaded " + loadedGameDefinitions.size() + " total game definitions.");

        if (loadedArenaDefinitions.isEmpty()) {
            logger.warning("No arena definitions were loaded. Games may not be able to start without arenas.");
        }
        if (loadedGameDefinitions.isEmpty()) {
            logger.warning("No game definitions were loaded. No games can be created.");
        }
    }

    // ... (existing getters for definitions, instances, etc. - unchanged) ...
    public Optional<GameDefinition> getGameDefinition(String definitionId) {
        return Optional.ofNullable(loadedGameDefinitions.get(definitionId.toLowerCase()));
    }

    public Optional<ArenaDefinition> getArenaDefinition(String arenaId) {
        return Optional.ofNullable(loadedArenaDefinitions.get(arenaId.toLowerCase()));
    }

    public Collection<GameDefinition> getAllGameDefinitions() {
        return Collections.unmodifiableCollection(loadedGameDefinitions.values());
    }

    public Collection<ArenaDefinition> getAllArenaDefinitions() {
        return Collections.unmodifiableCollection(loadedArenaDefinitions.values());
    }

    public Optional<GameInstance> getRunningGameInstance(UUID instanceId) {
        return Optional.ofNullable(runningGameInstances.get(instanceId));
    }

    public Collection<GameInstance> getRunningInstances() {
        return Collections.unmodifiableCollection(runningGameInstances.values());
    }


    public Optional<GameInstance> createGameInstance(String definitionId, String arenaIdToUse) {
        // ... (existing code - unchanged, but ensure ArenaManager is used for pasting) ...
        logger.info("Attempting to create game instance from definition '" + definitionId + "' using arena '" + arenaIdToUse + "'.");
        Optional<GameDefinition> defOpt = getGameDefinition(definitionId);
        Optional<ArenaDefinition> arenaDefOpt = getArenaDefinition(arenaIdToUse);

        if (defOpt.isEmpty()) {
            logger.severe("Cannot create game instance: GameDefinition '" + definitionId + "' not found.");
            return Optional.empty();
        }
        if (arenaDefOpt.isEmpty()) {
            logger.severe("Cannot create game instance: ArenaDefinition '" + arenaIdToUse + "' not found.");
            return Optional.empty();
        }

        GameDefinition definition = defOpt.get();
        ArenaDefinition arena = arenaDefOpt.get();

        Location instanceBaseWorldLocation;
        String worldName = definition.getRule("world", "world").toString();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            logger.severe("Cannot create game instance: World '" + worldName + "' for definition '" + definitionId + "' not found or not loaded.");
            return Optional.empty();
        }

        // TODO: Implement a robust arena allocation strategy.
        double offsetX = runningGameInstances.size() * 512;
        double offsetZ = 0;
        instanceBaseWorldLocation = new Location(world, offsetX, 100, offsetZ);
        logger.warning("USING TEMPORARY/PLACEHOLDER instanceBaseWorldLocation for " + definitionId + ": " + instanceBaseWorldLocation.toString());

        if (arenaManager != null && arena.getSchematicFile() != null && !arena.getSchematicFile().isEmpty()) {
            if (!arenaManager.pasteSchematic(arena.getSchematicFile(), instanceBaseWorldLocation)) { // Use ArenaManager
                logger.severe("Failed to paste schematic '" + arena.getSchematicFile() + "' for new instance of " + definitionId + ". Instance creation failed.");
                return Optional.empty();
            }
            logger.info("Schematic '" + arena.getSchematicFile() + "' pasted for instance of " + definitionId + " at " + instanceBaseWorldLocation.toString());
        } else {
            logger.warning("ArenaManager not available, or no schematic file defined for arena '" + arena.getArenaId() + "'. Arena not pasted via schematic.");
        }

        UUID instanceId = UUID.randomUUID();
        GameInstance newInstance = null;

        switch (definition.getGameType().toUpperCase()) {
            case "KOTH":
                newInstance = new KoTHGame(plugin, instanceId, definition, arena, instanceBaseWorldLocation);
                break;
            case "INFECTION":
                newInstance = new InfectionGame(plugin, instanceId, definition, arena, instanceBaseWorldLocation);
                break;
            case "CAPTURETHESHARD":
                newInstance = new CaptureTheShardGame(plugin, instanceId, definition, arena, instanceBaseWorldLocation);
                break;
            default:
                logger.severe("Unknown game_type '" + definition.getGameType() + "' for definition '" + definitionId + "'. Cannot create instance.");
                return Optional.empty();
        }

        if (newInstance == null) {
            logger.severe("Failed to instantiate GameInstance for type: " + definition.getGameType() + " (Definition: " + definitionId + ")");
            if (arenaManager != null && arena.getSchematicFile() != null && !arena.getSchematicFile().isEmpty()) {
                // arenaManager.clearPastedArena(instanceBaseWorldLocation, arena); // Cleanup pasted schematic
                logger.warning("Schematic may have been pasted for failed instance " + instanceId + ". Cleanup needed.");
            }
            return Optional.empty();
        }

        newInstance.setupInstance();

        if (newInstance.getGameState() == GameState.DISABLED) {
            logger.warning("Game instance for definition '" + definitionId + "' (Instance ID: " + instanceId.toString().substring(0,8) + ") was disabled during its setup.");
            newInstance.cleanupInstance(); // Call its own cleanup
            if (arenaManager != null && arena.getSchematicFile() != null && !arena.getSchematicFile().isEmpty()) {
                // arenaManager.clearPastedArena(instanceBaseWorldLocation, arena); // Cleanup pasted schematic
                logger.warning("Schematic may have been pasted for disabled instance " + instanceId + ". Cleanup needed.");
            }
            return Optional.empty();
        }

        runningGameInstances.put(instanceId, newInstance);
        logger.info("Successfully created and registered new game instance '" + instanceId.toString().substring(0,8) + "' (Def: " + definition.getDefinitionId() + ", Type: " + definition.getGameType() + ", Arena: " + arena.getArenaId() + "). State: " + newInstance.getGameState());
        return Optional.of(newInstance);
    }


    public void endGameInstance(UUID instanceId) {
        // ... (existing code - unchanged, but ensure ArenaManager is used for clearing) ...
        GameInstance instance = runningGameInstances.remove(instanceId);
        if (instance != null) {
            logger.info("Ending game instance: " + instanceId.toString().substring(0,8) + " (Def: " + instance.getDefinition().getDefinitionId() + ")");
            instance.stop(true);
            instance.cleanupInstance();

            if (arenaManager != null && instance.getArena().getSchematicFile() != null && !instance.getArena().getSchematicFile().isEmpty()) {
                // arenaManager.clearPastedArena(instance.getInstanceBaseWorldLocation(), instance.getArena()); // Use ArenaManager
                logger.info("Arena cleanup for instance " + instanceId.toString().substring(0,8) + " at " + instance.getInstanceBaseWorldLocation() + " needed via ArenaManager.");
            }

            List<UUID> playersToRemoveFromTracking = new ArrayList<>();
            for (Map.Entry<UUID, UUID> entry : playerCurrentInstance.entrySet()) {
                if (entry.getValue().equals(instanceId)) {
                    playersToRemoveFromTracking.add(entry.getKey());
                }
            }
            for (UUID playerUUID : playersToRemoveFromTracking) {
                playerCurrentInstance.remove(playerUUID);
            }
            logger.info("Instance " + instanceId.toString().substring(0,8) + " fully ended and removed.");
        } else {
            logger.warning("Attempted to end non-existent game instance: " + instanceId);
        }
    }

    // ... (existing player management methods: addPlayerToInstance, removePlayerFromInstance, etc. - largely unchanged for now) ...
    public boolean addPlayerToInstance(Player player, UUID instanceId) {
        logger.fine("[PLAYER_TRACKING] Attempting to add player " + player.getName() + " to instance " + instanceId.toString().substring(0,8));
        if (isPlayerInAnyInstance(player)) {
            UUID currentInstanceId = playerCurrentInstance.get(player.getUniqueId());
            if (currentInstanceId != null && currentInstanceId.equals(instanceId)) {
                Optional<GameInstance> instOpt = getRunningGameInstance(instanceId);
                if(instOpt.isPresent() && instOpt.get().isPlayerInGame(player.getUniqueId())){
                    player.sendMessage(ChatColor.YELLOW + "You are already in this game instance.");
                    return true;
                }
            } else {
                player.sendMessage(ChatColor.RED + "You are already in a different game instance!");
                return false;
            }
        }

        Optional<GameInstance> instanceOpt = getRunningGameInstance(instanceId);
        if (instanceOpt.isPresent()) {
            GameInstance instance = instanceOpt.get();
            if (instance.getGameState() == GameState.DISABLED) {
                player.sendMessage(ChatColor.RED + "The game instance is currently disabled.");
                return false;
            }
            if (instance.addPlayer(player)) {
                playerCurrentInstance.put(player.getUniqueId(), instanceId);
                return true;
            } else {
                return false;
            }
        } else {
            player.sendMessage(ChatColor.RED + "Game instance not found.");
            return false;
        }
    }

    public boolean removePlayerFromInstance(Player player) {
        UUID instanceId = playerCurrentInstance.remove(player.getUniqueId());
        if (instanceId != null) {
            Optional<GameInstance> instanceOpt = getRunningGameInstance(instanceId);
            if (instanceOpt.isPresent()) {
                instanceOpt.get().removePlayer(player);
            } else {
                plugin.getPlayerStateManager().restorePlayerState(player);
            }
            return true;
        } else {
            return false;
        }
    }

    public Optional<GameInstance> getPlayerGameInstance(Player player) {
        UUID instanceId = playerCurrentInstance.get(player.getUniqueId());
        if (instanceId != null) {
            return getRunningGameInstance(instanceId);
        }
        return Optional.empty();
    }

    public boolean isPlayerInAnyInstance(Player player) {
        return playerCurrentInstance.containsKey(player.getUniqueId());
    }


    public void shutdown() {
        // ... (existing code - unchanged) ...
        logger.info("Shutting down all game instances...");
        for (UUID instanceId : new ArrayList<>(runningGameInstances.keySet())) {
            endGameInstance(instanceId);
        }
        runningGameInstances.clear();
        playerCurrentInstance.clear();
        logger.info("All game instances shut down and player tracking cleared.");
    }

    // --- ARENA SETUP MODE METHODS ---

    /**
     * Starts an arena setup session for an administrator.
     * @param admin The player initiating the setup.
     * @param arenaId The ID of the arena to set up.
     * @param worldNameOverride Optional: specific world to paste in, otherwise uses config default.
     * @return True if the session started successfully, false otherwise.
     */
    public boolean startArenaSetupSession(Player admin, String arenaId, String worldNameOverride) {
        if (adminInSetupMode != null) {
            admin.sendMessage(ChatColor.RED + "Another admin (" + adminInSetupMode.getName() + ") is already in arena setup mode. Please wait.");
            return false;
        }

        Optional<ArenaDefinition> arenaDefOpt = getArenaDefinition(arenaId);
        if (arenaDefOpt.isEmpty()) {
            admin.sendMessage(ChatColor.RED + "Arena definition '" + arenaId + "' not found.");
            return false;
        }
        currentSetupArenaDefinition = arenaDefOpt.get(); // Store the loaded definition
        if (currentSetupArenaDefinition.getSchematicFile() == null || currentSetupArenaDefinition.getSchematicFile().isEmpty()) {
            admin.sendMessage(ChatColor.RED + "Arena '" + arenaId + "' does not have a schematic file defined. Cannot enter setup mode.");
            currentSetupArenaDefinition = null;
            return false;
        }

        String targetWorldName = (worldNameOverride != null) ? worldNameOverride : configManager.getArenaSetupWorldName();
        World setupWorld = Bukkit.getWorld(targetWorldName);
        if (setupWorld == null) {
            admin.sendMessage(ChatColor.RED + "Setup world '" + targetWorldName + "' not found or not loaded. Check plugin config.yml.");
            currentSetupArenaDefinition = null;
            return false;
        }

        currentSetupPasteOrigin = new Location(setupWorld,
                configManager.getArenaSetupOriginX(),
                configManager.getArenaSetupOriginY(),
                configManager.getArenaSetupOriginZ());

        // TODO: Check if the setup area is clear before pasting. This is a complex step.
        // For now, we assume ArenaManager handles potential overlaps or it's a dedicated clean area.

        if (arenaManager == null) {
            admin.sendMessage(ChatColor.RED + "ArenaManager is not available. Cannot paste schematic.");
            currentSetupArenaDefinition = null;
            return false;
        }

        admin.sendMessage(ChatColor.YELLOW + "Pasting schematic '" + currentSetupArenaDefinition.getSchematicFile() + "' at " + currentSetupPasteOrigin.toVector() + " in world '" + setupWorld.getName() + "'...");
        if (!arenaManager.pasteSchematic(currentSetupArenaDefinition.getSchematicFile(), currentSetupPasteOrigin)) {
            admin.sendMessage(ChatColor.RED + "Failed to paste schematic for setup. See console for details.");
            currentSetupArenaDefinition = null;
            currentSetupPasteOrigin = null;
            return false;
        }

        adminInSetupMode = admin;
        currentSetupArenaId = arenaId;
        currentSessionRelativeLocations = new HashMap<>(currentSetupArenaDefinition.getDefinedRelativeLocations()); // Load existing locations into session

        // Store admin's current state
        adminOriginalLocation = admin.getLocation().clone();
        adminOriginalGameMode = admin.getGameMode();
        adminOriginalAllowFlight = admin.getAllowFlight();
        adminOriginalFlyingState = admin.isFlying();

        // Set admin to creative, allow flight, teleport
        admin.setGameMode(GameMode.CREATIVE);
        admin.setAllowFlight(true);
        admin.setFlying(true);
        admin.teleport(currentSetupPasteOrigin.clone().add(0, 5, 0)); // Teleport slightly above origin

        admin.sendMessage(ChatColor.GREEN + "Entered arena setup mode for '" + arenaId + "'.");
        admin.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.AQUA + "/ag arena setrelloc <key> [index]" + ChatColor.YELLOW + " to define locations.");
        admin.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.AQUA + "/ag arena save" + ChatColor.YELLOW + " to save changes.");
        admin.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.AQUA + "/ag arena finish" + ChatColor.YELLOW + " to exit.");
        return true;
    }

    /**
     * Checks if the given player is the admin currently in setup mode.
     */
    public boolean isAdminInSetupMode(Player player) {
        return adminInSetupMode != null && adminInSetupMode.getUniqueId().equals(player.getUniqueId());
    }

    /**
     * Calculates the RelativeLocation of the admin's current position based on the setup origin.
     * @param admin The admin player.
     * @return The RelativeLocation, or null if not in setup mode or error.
     */
    public RelativeLocation calculateRelativeLocationForAdmin(Player admin) {
        if (!isAdminInSetupMode(admin) || currentSetupPasteOrigin == null) {
            return null;
        }
        return RelativeLocation.calculateFrom(admin.getLocation(), currentSetupPasteOrigin);
    }

    /**
     * Sets a relative location in the current setup session.
     * @param admin The admin player.
     * @param key The key for the location (e.g., "lobby_spawn").
     * @param relLoc The RelativeLocation object.
     * @param index Optional: if the key represents a list, the index to set/add at. Null for single entries.
     * @return True if successful.
     */
    @SuppressWarnings("unchecked") // For casting Object to List<RelativeLocation>
    public boolean setRelativeLocationInSetup(Player admin, String key, RelativeLocation relLoc, Integer index) {
        if (!isAdminInSetupMode(admin)) {
            admin.sendMessage(ChatColor.RED + "You are not in arena setup mode.");
            return false;
        }
        if (key == null || key.trim().isEmpty() || relLoc == null) {
            admin.sendMessage(ChatColor.RED + "Invalid key or location data.");
            return false;
        }

        if (index != null) { // Setting/adding to a list
            Object existingObject = currentSessionRelativeLocations.computeIfAbsent(key, k -> new ArrayList<RelativeLocation>());
            if (!(existingObject instanceof List)) {
                admin.sendMessage(ChatColor.RED + "Error: Location key '" + key + "' is not a list but an index was provided.");
                return false;
            }
            List<RelativeLocation> locList = (List<RelativeLocation>) existingObject;
            if (index >= 0 && index < locList.size()) {
                locList.set(index, relLoc); // Replace existing
                admin.sendMessage(ChatColor.GREEN + "Replaced location for '" + key + "' at index " + index + ".");
            } else if (index == locList.size()) { // Append if index is next available slot
                locList.add(relLoc);
                admin.sendMessage(ChatColor.GREEN + "Added new location for '" + key + "' at index " + index + ".");
            } else { // Index out of bounds for append or set
                admin.sendMessage(ChatColor.RED + "Invalid index " + index + " for location list '" + key + "'. Size: " + locList.size() + ". Use 0 to " + locList.size() + ".");
                return false;
            }
        } else { // Setting a single location entry
            currentSessionRelativeLocations.put(key, relLoc);
            admin.sendMessage(ChatColor.GREEN + "Set location for '" + key + "'.");
        }
        logger.info("[ArenaSetup] Admin " + admin.getName() + " set location '" + key + (index != null ? "["+index+"]" : "") + "' to " + relLoc.toString() + " for arena " + currentSetupArenaId);
        return true;
    }

    /**
     * Gets the currently defined relative locations in the setup session.
     */
    public Map<String, Object> getSetupRelativeLocations(Player admin) {
        if (!isAdminInSetupMode(admin)) return Collections.emptyMap();
        return Collections.unmodifiableMap(currentSessionRelativeLocations);
    }

    public String getCurrentSetupArenaId(Player admin) {
        if (!isAdminInSetupMode(admin)) return null;
        return currentSetupArenaId;
    }


    /**
     * Deletes a relative location from the current setup session.
     */
    @SuppressWarnings("unchecked")
    public boolean deleteRelativeLocationInSetup(Player admin, String key, Integer index) {
        if (!isAdminInSetupMode(admin)) {
            admin.sendMessage(ChatColor.RED + "You are not in arena setup mode.");
            return false;
        }
        if (!currentSessionRelativeLocations.containsKey(key)) {
            admin.sendMessage(ChatColor.RED + "Location key '" + key + "' not found in current setup.");
            return false;
        }

        if (index != null) { // Deleting from a list
            Object existingObject = currentSessionRelativeLocations.get(key);
            if (!(existingObject instanceof List)) {
                admin.sendMessage(ChatColor.RED + "Error: Location key '" + key + "' is not a list but an index was provided for deletion.");
                return false;
            }
            List<RelativeLocation> locList = (List<RelativeLocation>) existingObject;
            if (index >= 0 && index < locList.size()) {
                locList.remove(index.intValue()); // remove by index
                admin.sendMessage(ChatColor.GREEN + "Removed location for '" + key + "' at index " + index + ".");
                if (locList.isEmpty()) currentSessionRelativeLocations.remove(key); // Remove key if list becomes empty
            } else {
                admin.sendMessage(ChatColor.RED + "Invalid index " + index + " for location list '" + key + "'. Size: " + locList.size() + ".");
                return false;
            }
        } else { // Deleting a single entry or entire list
            currentSessionRelativeLocations.remove(key);
            admin.sendMessage(ChatColor.GREEN + "Removed location(s) for key '" + key + "'.");
        }
        logger.info("[ArenaSetup] Admin " + admin.getName() + " deleted location '" + key + (index != null ? "["+index+"]" : "") + "' for arena " + currentSetupArenaId);
        return true;
    }

    /**
     * Calculates the absolute world location from a relative location key stored in the setup session.
     * @param admin The admin player.
     * @param key The key of the relative location.
     * @param index Optional index if the key refers to a list.
     * @return The absolute Location, or null if not found or error.
     */
    @SuppressWarnings("unchecked")
    public Location getAbsoluteFromSetupRelLoc(Player admin, String key, Integer index) {
        if (!isAdminInSetupMode(admin) || currentSetupPasteOrigin == null) return null;

        Object locData = currentSessionRelativeLocations.get(key);
        if (locData == null) return null;

        RelativeLocation relLocToTeleport = null;
        if (locData instanceof RelativeLocation && index == null) {
            relLocToTeleport = (RelativeLocation) locData;
        } else if (locData instanceof List && index != null) {
            List<RelativeLocation> locList = (List<RelativeLocation>) locData;
            if (index >= 0 && index < locList.size()) {
                relLocToTeleport = locList.get(index);
            }
        }

        if (relLocToTeleport != null) {
            return relLocToTeleport.toAbsolute(currentSetupPasteOrigin);
        }
        return null;
    }


    /**
     * Saves the currently defined relative locations from the setup session to the arena's YAML file.
     */
    public boolean saveSetupLocationsToArenaFile(Player admin) {
        if (!isAdminInSetupMode(admin) || currentSetupArenaDefinition == null) {
            admin.sendMessage(ChatColor.RED + "Not in setup mode or no arena definition loaded for setup.");
            return false;
        }

        // Update the loaded ArenaDefinition object with the session's locations
        currentSetupArenaDefinition.setDefinedRelativeLocations(new HashMap<>(currentSessionRelativeLocations)); // Use a copy

        if (configManager.saveArenaDefinition(currentSetupArenaDefinition)) {
            admin.sendMessage(ChatColor.GREEN + "Arena '" + currentSetupArenaId + "' saved successfully with " + currentSessionRelativeLocations.size() + " location entries.");
            logger.info("[ArenaSetup] Admin " + admin.getName() + " saved arena " + currentSetupArenaId);
            return true;
        } else {
            admin.sendMessage(ChatColor.RED + "Failed to save arena '" + currentSetupArenaId + "'. Check console for errors.");
            return false;
        }
    }

    /**
     * Finishes the arena setup session, cleans up, and restores the admin's state.
     */
    public boolean finishArenaSetupSession(Player admin, boolean force) {
        if (!isAdminInSetupMode(admin)) {
            // This might be called if admin disconnects, so check if adminInSetupMode is the same player.
            if (adminInSetupMode != null && !admin.getUniqueId().equals(adminInSetupMode.getUniqueId())) {
                admin.sendMessage(ChatColor.RED + "You are not the admin currently in setup mode.");
                return false;
            } else if (adminInSetupMode == null && !force) { // No one in setup mode, and not a forced cleanup
                admin.sendMessage(ChatColor.YELLOW + "No active arena setup session to finish.");
                return true; // Nothing to do
            }
        }

        // Player object for cleanup might be different if called from PlayerQuitEvent
        Player adminToRestore = (adminInSetupMode != null) ? adminInSetupMode : admin;


        if (currentSetupPasteOrigin != null && currentSetupArenaDefinition != null && arenaManager != null &&
                currentSetupArenaDefinition.getSchematicFile() != null && !currentSetupArenaDefinition.getSchematicFile().isEmpty()) {
            adminToRestore.sendMessage(ChatColor.YELLOW + "Cleaning up temporary setup arena...");
            // arenaManager.clearPastedArena(currentSetupPasteOrigin, currentSetupArenaDefinition); // TODO: Implement in ArenaManager
            logger.info("[ArenaSetup] Cleanup of pasted schematic for " + currentSetupArenaId + " at " + currentSetupPasteOrigin + " requested.");
            adminToRestore.sendMessage(ChatColor.YELLOW + "Note: Arena schematic cleanup needs to be fully implemented in ArenaManager.");
        }

        // Restore admin's original state only if they are online and were the one in setup mode
        if (adminToRestore.isOnline()) {
            if (adminOriginalLocation != null) {
                adminToRestore.teleport(adminOriginalLocation);
            }
            if (adminOriginalGameMode != null) {
                adminToRestore.setGameMode(adminOriginalGameMode);
            }
            adminToRestore.setAllowFlight(adminOriginalAllowFlight);
            adminToRestore.setFlying(adminOriginalFlyingState);
            adminToRestore.sendMessage(ChatColor.GREEN + "Exited arena setup mode.");
        }


        logger.info("[ArenaSetup] Session for arena " + currentSetupArenaId + " finished by " + adminToRestore.getName() + (force ? " (forced)" : ""));
        // Clear all session state variables
        adminInSetupMode = null;
        currentSetupArenaId = null;
        currentSetupArenaDefinition = null;
        currentSetupPasteOrigin = null;
        currentSessionRelativeLocations.clear();
        adminOriginalLocation = null;
        adminOriginalGameMode = null;
        return true;
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // If the player quitting is the admin in setup mode, force finish the session.
        if (isAdminInSetupMode(player)) {
            logger.info("[ArenaSetup] Admin " + player.getName() + " quit while in setup mode. Forcing session finish.");
            finishArenaSetupSession(player, true);
        }

        // Existing player instance removal logic
        logger.fine("[PLAYER_TRACKING] PlayerQuitEvent for " + player.getName());
        if (isPlayerInAnyInstance(player)) {
            logger.info("[PLAYER_TRACKING] Player " + player.getName() + " was in an instance upon quit. Calling removePlayerFromInstance.");
            removePlayerFromInstance(player);
        } else {
            logger.fine("[PLAYER_TRACKING] Player " + player.getName() + " was not in any instance upon quitting.");
        }
    }
}