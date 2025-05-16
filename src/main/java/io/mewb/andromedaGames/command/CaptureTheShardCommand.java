package io.mewb.andromedaGames.command;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.arena.ArenaDefinition;
import io.mewb.andromedaGames.capturetheshard.CaptureTheShardGame; // This is our CaptureTheShardGameInstance
import io.mewb.andromedaGames.capturetheshard.TeamColor;
import io.mewb.andromedaGames.game.GameDefinition;
import io.mewb.andromedaGames.game.GameInstance;
import io.mewb.andromedaGames.game.GameManager;
import io.mewb.andromedaGames.game.GameState;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CaptureTheShardCommand implements CommandExecutor, TabCompleter {

    private final AndromedaGames plugin;
    private final GameManager gameManager;
    private final Logger logger;

    public CaptureTheShardCommand(AndromedaGames plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendBaseCTSHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        switch (subCommand) {
            case "join":
                return handleJoin(sender, subArgs);
            case "leave":
                return handleLeave(sender, subArgs);
            case "list":
                return handleList(sender, subArgs);
            case "startinstance":
                return handleAdminStartInstance(sender, subArgs);
            case "stopinstance":
                return handleAdminStopInstance(sender, subArgs);
            case "setlocation": // For admins to mark locations in a test instance
                return handleAdminSetLocation(sender, subArgs);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown CTS subcommand: " + ChatColor.YELLOW + subCommand);
                sendBaseCTSHelp(sender);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();
        List<String> baseSubcommands = new ArrayList<>(Arrays.asList("join", "leave", "list"));
        List<String> adminSubcommands = new ArrayList<>();

        if (sender.hasPermission("andromedagames.admin.cts.manage")) {
            adminSubcommands.addAll(Arrays.asList("startinstance", "stopinstance"));
        }
        if (sender.hasPermission("andromedagames.admin.cts.setinstancelocations")) { // New permission
            adminSubcommands.add("setlocation");
        }


        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], baseSubcommands, completions);
            StringUtil.copyPartialMatches(args[0], adminSubcommands, completions);
        } else if (args.length >= 2) {
            String subCommand = args[0].toLowerCase();
            List<String> ctsDefinitionIds = gameManager.getAllGameDefinitions().stream()
                    .filter(def -> "CAPTURETHESHARD".equalsIgnoreCase(def.getGameType()))
                    .map(GameDefinition::getDefinitionId)
                    .collect(Collectors.toList());

            List<String> runningCtsInstanceIdPrefixes = gameManager.getRunningInstances().stream()
                    .filter(inst -> inst.getDefinition().getGameType().equalsIgnoreCase("CAPTURETHESHARD"))
                    .map(inst -> inst.getInstanceId().toString().substring(0, 8))
                    .collect(Collectors.toList());

            switch (subCommand) {
                case "join":
                    if (args.length == 2) { // Suggest CTS definitionId
                        StringUtil.copyPartialMatches(args[1], ctsDefinitionIds, completions);
                    } else if (args.length == 3) { // Suggest team for /cts join <definitionId> <team>
                        StringUtil.copyPartialMatches(args[2], Arrays.stream(TeamColor.values()).map(Enum::name).collect(Collectors.toList()), completions);
                    }
                    break;
                case "startinstance":
                    if (args.length == 2) { // Suggest CTS definitionId
                        StringUtil.copyPartialMatches(args[1], ctsDefinitionIds, completions);
                    } else if (args.length == 3) { // Suggest arenaId
                        String chosenDefId = args[1];
                        Optional<GameDefinition> defOpt = gameManager.getGameDefinition(chosenDefId);
                        if (defOpt.isPresent()) {
                            GameDefinition chosenDef = defOpt.get();
                            List<String> compatibleArenaIds = gameManager.getAllArenaDefinitions().stream()
                                    .filter(arenaDef -> arenaDef.getTags().stream()
                                            .anyMatch(tag -> chosenDef.getCompatibleArenaTags().contains(tag) || chosenDef.getCompatibleArenaTags().isEmpty()))
                                    .map(ArenaDefinition::getArenaId)
                                    .collect(Collectors.toList());
                            StringUtil.copyPartialMatches(args[2], compatibleArenaIds, completions);
                        }
                    }
                    break;
                case "stopinstance":
                    if (args.length == 2) { // Suggest running CTS instanceId prefix
                        StringUtil.copyPartialMatches(args[1], runningCtsInstanceIdPrefixes, completions);
                    }
                    break;
                case "setlocation":
                    // /cts setlocation <teamColor> <pedestal|capture|addspawn>
                    // This command doesn't take gameId as it works on the player's current instance
                    if (args.length == 2) { // teamColor (RED/BLUE)
                        StringUtil.copyPartialMatches(args[1], Arrays.stream(TeamColor.values()).map(Enum::name).collect(Collectors.toList()), completions);
                    } else if (args.length == 3) { // locationType (pedestal, capture, addspawn)
                        StringUtil.copyPartialMatches(args[2], Arrays.asList("pedestal", "capture", "addspawn"), completions);
                    }
                    break;
            }
        }
        Collections.sort(completions);
        return completions;
    }

    private void sendBaseCTSHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "--- Capture The Shard Commands ---");
        if (sender.hasPermission("andromedagames.player.cts.join")) {
            sender.sendMessage(ChatColor.YELLOW + "/cts join <definitionId> [RED/BLUE]" + ChatColor.GRAY + " - Joins an available CTS game match.");
        }
        if (sender.hasPermission("andromedagames.player.cts.leave")) {
            sender.sendMessage(ChatColor.YELLOW + "/cts leave" + ChatColor.GRAY + " - Leaves your current game match.");
        }
        sender.sendMessage(ChatColor.YELLOW + "/cts list" + ChatColor.GRAY + " - Lists CTS game types and active matches.");

        if (sender.hasPermission("andromedagames.admin.cts.manage")) {
            sender.sendMessage(ChatColor.RED + "Admin CTS Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/cts startinstance <definitionId> [arenaId]" + ChatColor.GRAY + " - Starts a new CTS match.");
            sender.sendMessage(ChatColor.YELLOW + "/cts stopinstance <instanceId_prefix>" + ChatColor.GRAY + " - Stops a running CTS match.");
        }
        if (sender.hasPermission("andromedagames.admin.cts.setinstancelocations")) {
            sender.sendMessage(ChatColor.YELLOW + "/cts setlocation <RED/BLUE> <pedestal|capture|addspawn>" + ChatColor.GRAY + " - Sets key locations for your current CTS match instance (temporary).");
        }
    }

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("andromedagames.player.cts.join")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to join CTS games.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /cts join <definitionId> [RED/BLUE]");
            return true;
        }
        String definitionId = args[0];
        TeamColor preferredTeam = null;
        if (args.length >= 2) {
            try {
                preferredTeam = TeamColor.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage(ChatColor.RED + "Invalid team color: " + args[1] + ". Choose RED or BLUE, or omit for auto-assign.");
                return true;
            }
        }
        logger.finer("[CTSCommand] Player " + player.getName() + " attempting to join CTS definition: " + definitionId + (preferredTeam != null ? " (Preferred team: " + preferredTeam.name() + ")" : " (Auto-assign team)"));

        if (gameManager.isPlayerInAnyInstance(player)) {
            player.sendMessage(ChatColor.RED + "You are already in a game.");
            return true;
        }

        Optional<GameDefinition> defOpt = gameManager.getGameDefinition(definitionId);
        if (defOpt.isEmpty() || !defOpt.get().getGameType().equalsIgnoreCase("CAPTURETHESHARD")) {
            player.sendMessage(ChatColor.RED + "CTS game definition '" + definitionId + "' not found.");
            return true;
        }
        GameDefinition definition = defOpt.get();

        Optional<GameInstance> targetInstanceOpt = gameManager.getRunningInstances().stream()
                .filter(inst -> inst.getDefinition().getDefinitionId().equalsIgnoreCase(definitionId) &&
                        inst.getGameState() == GameState.WAITING &&
                        inst.getPlayerCount() < (definition.getRule("max_players_per_team", 8) * TeamColor.values().length)) // Approx max total players
                .findFirst();

        GameInstance targetInstance;
        if (targetInstanceOpt.isPresent()) {
            targetInstance = targetInstanceOpt.get();
            logger.info("Found existing waiting CTS instance " + targetInstance.getInstanceId().toString().substring(0,8) + " for definition " + definitionId);
        } else {
            logger.info("No waiting CTS instance found for " + definitionId + ". Attempting to create a new one.");
            Optional<ArenaDefinition> arenaOpt = gameManager.getAllArenaDefinitions().stream()
                    .filter(ad -> ad.getTags().stream().anyMatch(tag -> definition.getCompatibleArenaTags().contains(tag) || definition.getCompatibleArenaTags().isEmpty()))
                    .findFirst();

            if (arenaOpt.isEmpty()) {
                player.sendMessage(ChatColor.RED + "No compatible arenas available to start a new '" + definition.getDisplayName() + "' match.");
                logger.warning("Could not create instance for CTS definition '" + definitionId + "': No compatible arenas found.");
                return true;
            }
            String arenaIdToUse = arenaOpt.get().getArenaId();
            logger.info("Selected arena '" + arenaIdToUse + "' for new CTS instance of definition '" + definitionId + "'.");

            Optional<GameInstance> newInstanceOpt = gameManager.createGameInstance(definitionId, arenaIdToUse);
            if (newInstanceOpt.isPresent()) {
                targetInstance = newInstanceOpt.get();
            } else {
                player.sendMessage(ChatColor.RED + "Failed to create a new CTS match for '" + definition.getDisplayName() + "'.");
                logger.severe("Failed to create game instance for CTS definition '" + definitionId + "' with arena '" + arenaIdToUse + "'.");
                return true;
            }
        }

        if (targetInstance instanceof CaptureTheShardGame) { // Ensure it's the correct game type
            CaptureTheShardGame ctsInstance = (CaptureTheShardGame) targetInstance;
            // The addPlayer method in CaptureTheShardGame needs to accept preferredTeam
            if (!ctsInstance.addPlayer(player, preferredTeam)) { // Pass preferredTeam
                logger.warning("[CTSCommand] Failed to add " + player.getName() + " to instance " + targetInstance.getInstanceId().toString().substring(0,8) + " via CTSGame.addPlayer");
            }
            // GameManager.addPlayerToInstance is more generic and doesn't know about preferredTeam.
            // The logic for adding to a specific team and then GameManager tracking should be:
            // 1. CTSInstance.addPlayer(player, preferredTeam) -> adds to teamPlayers, playerTeams, playersInGame (in GameInstance)
            // 2. If successful, then gameManager.trackPlayerInInstance(player, instanceId); (hypothetical new method)
            // For now, we assume ctsInstance.addPlayer also informs GameManager or handles the global tracking part.
            // The current GameManager.addPlayerToInstance already does the global tracking if instance.addPlayer is true.
            if (!gameManager.addPlayerToInstance(player, targetInstance.getInstanceId())) {
                // This message might be redundant if ctsInstance.addPlayer already messaged.
                // player.sendMessage(ChatColor.RED + "Failed to join the match instance.");
            }
        } else if (targetInstance != null) {
            player.sendMessage(ChatColor.RED + "Selected game is not a Capture The Shard type.");
            logger.warning("Player " + player.getName() + " tried to join instance " + targetInstance.getInstanceId() + " which is not a CTS game.");
        } else {
            player.sendMessage(ChatColor.RED + "Could not find or create a suitable CTS match for '" + definition.getDisplayName() + "'.");
        }
        return true;
    }

    private boolean handleLeave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("andromedagames.player.cts.leave")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to leave games.");
            return true;
        }
        if (!gameManager.removePlayerFromInstance(player)) {
            player.sendMessage(ChatColor.RED + "You are not currently in a CTS game match.");
        }
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "--- Available CTS Game Definitions ---");
        List<GameDefinition> ctsDefinitions = gameManager.getAllGameDefinitions().stream()
                .filter(def -> "CAPTURETHESHARD".equalsIgnoreCase(def.getGameType()))
                .collect(Collectors.toList());
        if (ctsDefinitions.isEmpty()){
            sender.sendMessage(ChatColor.GRAY+"No CTS game types defined.");
        } else {
            ctsDefinitions.forEach(def -> sender.sendMessage(ChatColor.YELLOW + def.getDefinitionId() + ChatColor.GRAY + " - " + def.getDisplayName()));
        }

        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "--- Running CTS Matches ---");
        List<GameInstance> runningCtsInstances = gameManager.getRunningInstances().stream()
                .filter(inst -> inst.getDefinition().getGameType().equalsIgnoreCase("CAPTURETHESHARD"))
                .collect(Collectors.toList());

        if (runningCtsInstances.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No CTS matches currently running.");
        } else {
            for (GameInstance instance : runningCtsInstances) {
                sender.sendMessage(String.format("%s%s %s(ID: %s) %s- State: %s%s %s- Players: %s%d",
                        ChatColor.GOLD, instance.getDefinition().getDisplayName(),
                        ChatColor.DARK_AQUA, instance.getInstanceId().toString().substring(0, 8),
                        ChatColor.GRAY,
                        ChatColor.YELLOW, instance.getGameState().name(),
                        ChatColor.GRAY,
                        ChatColor.LIGHT_PURPLE, instance.getPlayerCount()
                ));
            }
        }
        return true;
    }

    private boolean handleAdminStartInstance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("andromedagames.admin.cts.manage")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to start CTS instances.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /cts startinstance <definitionId> [arenaId]");
            return true;
        }
        String definitionId = args[0];
        String arenaId = (args.length > 1) ? args[1] : null;

        Optional<GameDefinition> defOpt = gameManager.getGameDefinition(definitionId);
        if(defOpt.isEmpty() || !defOpt.get().getGameType().equalsIgnoreCase("CAPTURETHESHARD")){
            sender.sendMessage(ChatColor.RED + "CTS Game Definition '" + definitionId + "' not found.");
            return true;
        }
        GameDefinition definition = defOpt.get();

        if (arenaId == null) {
            Optional<ArenaDefinition> compatibleArenaOpt = gameManager.getAllArenaDefinitions().stream()
                    .filter(ad -> ad.getTags().stream().anyMatch(tag -> definition.getCompatibleArenaTags().contains(tag) || definition.getCompatibleArenaTags().isEmpty()))
                    .findFirst();
            if (compatibleArenaOpt.isPresent()) {
                arenaId = compatibleArenaOpt.get().getArenaId();
                sender.sendMessage(ChatColor.YELLOW + "Auto-selected arena: " + arenaId);
            } else {
                sender.sendMessage(ChatColor.RED + "No compatible arena found for definition '" + definitionId + "'. Please specify an arena ID.");
                return true;
            }
        }

        Optional<GameInstance> instanceOpt = gameManager.createGameInstance(definitionId, arenaId);
        if (instanceOpt.isPresent()) {
            GameInstance instance = instanceOpt.get();
            if (instance.getGameState() == GameState.WAITING) {
                if (instance.start(true)) {
                    sender.sendMessage(ChatColor.GREEN + "New CTS instance of '" + definition.getDisplayName() + "' on arena '" + arenaId + "' created and force-started (ID: " + instance.getInstanceId().toString().substring(0,8) + ").");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "CTS instance '" + definition.getDisplayName() + "' created (ID: " + instance.getInstanceId().toString().substring(0,8) + ") but failed to start. It is in " + instance.getGameState() + " state.");
                }
            } else {
                sender.sendMessage(ChatColor.YELLOW + "CTS instance '" + definition.getDisplayName() + "' created (ID: " + instance.getInstanceId().toString().substring(0,8) + ") but is already in state " + instance.getGameState() + ". Not attempting to start.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to create CTS instance for definition '" + definitionId + "' on arena '" + arenaId + "'. Check console for errors.");
        }
        return true;
    }

    private boolean handleAdminStopInstance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("andromedagames.admin.cts.manage")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to stop CTS instances.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /cts stopinstance <instanceId_prefix>");
            return true;
        }
        String instanceIdPrefix = args[0].toLowerCase();
        Optional<GameInstance> instanceOpt = gameManager.getRunningInstances().stream()
                .filter(inst -> inst.getDefinition().getGameType().equalsIgnoreCase("CAPTURETHESHARD") &&
                        inst.getInstanceId().toString().toLowerCase().startsWith(instanceIdPrefix))
                .findFirst();

        if (instanceOpt.isPresent()) {
            GameInstance instance = instanceOpt.get();
            gameManager.endGameInstance(instance.getInstanceId());
            sender.sendMessage(ChatColor.GREEN + "CTS instance " + instance.getDefinition().getDisplayName() + " (ID starting with " + instanceIdPrefix + ") has been stopped.");
        } else {
            sender.sendMessage(ChatColor.RED + "No running CTS instance found with ID starting with '" + instanceIdPrefix + "'.");
        }
        return true;
    }

    private boolean handleAdminSetLocation(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("andromedagames.admin.cts.setinstancelocations")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to set temporary CTS game locations.");
            return true;
        }
        // Usage: /cts setlocation <RED/BLUE> <pedestal|capture|addspawn>
        // Operates on the player's current game instance.
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /cts setlocation <RED/BLUE> <pedestal|capture|addspawn>");
            return true;
        }
        TeamColor teamColor;
        String locationType = args[1].toLowerCase();

        try {
            teamColor = TeamColor.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid team color: " + args[0] + ". Use RED or BLUE.");
            return true;
        }

        Optional<GameInstance> instanceOpt = gameManager.getPlayerGameInstance(player);
        if (instanceOpt.isPresent() && instanceOpt.get() instanceof CaptureTheShardGame) {
            CaptureTheShardGame ctsInstance = (CaptureTheShardGame) instanceOpt.get();
            if (ctsInstance.getGameState() != GameState.ACTIVE && ctsInstance.getGameState() != GameState.STARTING && ctsInstance.getGameState() != GameState.WAITING) {
                player.sendMessage(ChatColor.RED + "Your current CTS match is not in a state where locations can be set (must be WAITING, STARTING, or ACTIVE for testing).");
                return true;
            }

            Location playerLocation = player.getLocation();
            // These methods in CaptureTheShardGame should only modify the *running instance's* current locations
            // And NOT save to the definition file via this command.
            switch (locationType) {
                case "pedestal":
                    ctsInstance.adminSetTeamShardPedestalLocation(teamColor, playerLocation);
                    player.sendMessage(ChatColor.GREEN + "Shard pedestal for " + teamColor.name() + " team in your current match temporarily set to your location.");
                    break;
                case "capture":
                    ctsInstance.adminSetTeamCapturePointLocation(teamColor, playerLocation);
                    player.sendMessage(ChatColor.GREEN + "Capture point for " + teamColor.name() + " team in your current match temporarily set to your location.");
                    break;
                case "addspawn":
                    ctsInstance.adminAddTeamPlayerSpawn(teamColor, playerLocation);
                    player.sendMessage(ChatColor.GREEN + "Added player spawn for " + teamColor.name() + " team in your current match at your location.");
                    break;
                default:
                    player.sendMessage(ChatColor.RED + "Invalid location type: " + locationType + ". Use 'pedestal', 'capture', or 'addspawn'.");
                    return true;
            }
            player.sendMessage(ChatColor.YELLOW + "This change is temporary for the current match instance only.");
        } else {
            player.sendMessage(ChatColor.RED + "You are not currently in an active CTS match instance, or it's not a CTS game type.");
        }
        return true;
    }
}
