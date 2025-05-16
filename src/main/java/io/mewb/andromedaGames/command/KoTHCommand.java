package io.mewb.andromedaGames.command;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.arena.ArenaDefinition; // For finding compatible arenas
import io.mewb.andromedaGames.game.GameDefinition;
import io.mewb.andromedaGames.game.GameInstance;
import io.mewb.andromedaGames.game.GameManager;
import io.mewb.andromedaGames.game.GameState;
import io.mewb.andromedaGames.koth.KoTHGame; // This is our KoTHGameInstance (extends GameInstance)
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

public class KoTHCommand implements CommandExecutor, TabCompleter {

    private final AndromedaGames plugin;
    private final GameManager gameManager;
    private final Logger logger;

    public KoTHCommand(AndromedaGames plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendBaseKoTHHelp(sender);
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
            case "sethill":
                return handleAdminSetHill(sender, subArgs);
            case "setradius":
                return handleAdminSetRadius(sender, subArgs);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown KoTH subcommand: " + ChatColor.YELLOW + subCommand);
                sendBaseKoTHHelp(sender);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();
        List<String> baseSubcommands = new ArrayList<>(Arrays.asList("join", "leave", "list"));
        List<String> adminSubcommands = new ArrayList<>();

        if (sender.hasPermission("andromedagames.admin.koth.manage")) { // General admin perm
            adminSubcommands.addAll(Arrays.asList("startinstance", "stopinstance"));
        }
        if (sender.hasPermission("andromedagames.admin.koth.setinstanceparams")) { // Perm for temp changes
            adminSubcommands.addAll(Arrays.asList("sethill", "setradius"));
        }

        if (args.length == 1) { // Tab completing the subcommand itself
            StringUtil.copyPartialMatches(args[0], baseSubcommands, completions);
            StringUtil.copyPartialMatches(args[0], adminSubcommands, completions); // Suggest admin commands if they have perm
        } else if (args.length >= 2) {
            String subCommand = args[0].toLowerCase();
            List<String> kothDefinitionIds = gameManager.getAllGameDefinitions().stream()
                    .filter(def -> "KOTH".equalsIgnoreCase(def.getGameType()))
                    .map(GameDefinition::getDefinitionId)
                    .collect(Collectors.toList());

            List<String> runningKothInstanceIdPrefixes = gameManager.getRunningInstances().stream()
                    .filter(inst -> inst.getDefinition().getGameType().equalsIgnoreCase("KOTH"))
                    .map(inst -> inst.getInstanceId().toString().substring(0, 8)) // Shortened UUID for display
                    .collect(Collectors.toList());

            switch (subCommand) {
                case "join":
                case "startinstance":
                    if (args.length == 2) { // Suggest KoTH definitionId
                        StringUtil.copyPartialMatches(args[1], kothDefinitionIds, completions);
                    } else if (subCommand.equals("startinstance") && args.length == 3) { // Suggest arenaId for startinstance
                        // Suggest all arena IDs that are compatible with the chosen KOTH definition
                        String chosenDefId = args[1];
                        Optional<GameDefinition> defOpt = gameManager.getGameDefinition(chosenDefId);
                        if (defOpt.isPresent()) {
                            GameDefinition chosenDef = defOpt.get();
                            List<String> compatibleArenaIds = gameManager.getAllArenaDefinitions().stream()
                                    .filter(arenaDef -> arenaDef.getTags().stream()
                                            .anyMatch(tag -> chosenDef.getCompatibleArenaTags().contains(tag)))
                                    .map(ArenaDefinition::getArenaId)
                                    .collect(Collectors.toList());
                            StringUtil.copyPartialMatches(args[2], compatibleArenaIds, completions);
                        }
                    }
                    break;
                case "stopinstance":
                    if (args.length == 2) { // Suggest running KoTH instanceId (shortened prefix)
                        StringUtil.copyPartialMatches(args[1], runningKothInstanceIdPrefixes, completions);
                    }
                    break;
                case "setradius":
                    // This command (setradius <radius>) doesn't take gameId/instanceId as arg 1
                    // It operates on the current game of the player.
                    // Arg 1 (args[1] here because args[0] is "setradius") is the radius value.
                    if (args.length == 2) { // Suggest radius value
                        StringUtil.copyPartialMatches(args[1], Arrays.asList("3", "5", "7", "10"), completions);
                    }
                    break;
                // sethill takes no arguments beyond the subcommand
            }
        }
        Collections.sort(completions);
        return completions;
    }

    private void sendBaseKoTHHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- King of the Hill (KoTH) Commands ---");
        if (sender.hasPermission("andromedagames.player.koth.join")) {
            sender.sendMessage(ChatColor.YELLOW + "/koth join <definitionId>" + ChatColor.GRAY + " - Joins an available KoTH game match.");
        }
        if (sender.hasPermission("andromedagames.player.koth.leave")) {
            sender.sendMessage(ChatColor.YELLOW + "/koth leave" + ChatColor.GRAY + " - Leaves your current game match.");
        }
        sender.sendMessage(ChatColor.YELLOW + "/koth list" + ChatColor.GRAY + " - Lists KoTH game types and active matches.");

        if (sender.hasPermission("andromedagames.admin.koth.manage")) {
            sender.sendMessage(ChatColor.RED + "Admin KoTH Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/koth startinstance <definitionId> [arenaId]" + ChatColor.GRAY + " - Starts a new KoTH match.");
            sender.sendMessage(ChatColor.YELLOW + "/koth stopinstance <instanceId_prefix>" + ChatColor.GRAY + " - Stops a running KoTH match.");
        }
        if (sender.hasPermission("andromedagames.admin.koth.setinstanceparams")) {
            sender.sendMessage(ChatColor.YELLOW + "/koth sethill" + ChatColor.GRAY + " - Sets hill for your current KoTH match (temporary).");
            sender.sendMessage(ChatColor.YELLOW + "/koth setradius <radius>" + ChatColor.GRAY + " - Sets radius for your current KoTH match (temporary).");
        }
    }

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("andromedagames.player.koth.join")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to join KoTH games.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /koth join <definitionId>");
            return true;
        }
        String definitionId = args[0];
        logger.finer("[KoTHCommand] Player " + player.getName() + " attempting to join KoTH definition: " + definitionId);

        if (gameManager.isPlayerInAnyInstance(player)) {
            player.sendMessage(ChatColor.RED + "You are already in a game.");
            return true;
        }

        Optional<GameDefinition> defOpt = gameManager.getGameDefinition(definitionId);
        if (defOpt.isEmpty() || !defOpt.get().getGameType().equalsIgnoreCase("KOTH")) {
            player.sendMessage(ChatColor.RED + "KoTH game definition '" + definitionId + "' not found.");
            return true;
        }
        GameDefinition definition = defOpt.get();

        // Find an available waiting instance of this definition, or create a new one
        Optional<GameInstance> targetInstanceOpt = gameManager.getRunningInstances().stream()
                .filter(inst -> inst.getDefinition().getDefinitionId().equalsIgnoreCase(definitionId) &&
                        inst.getGameState() == GameState.WAITING &&
                        inst.getPlayerCount() < definition.getRule("max_players", 16)) // Check max players rule
                .findFirst();

        GameInstance targetInstance;
        if (targetInstanceOpt.isPresent()) {
            targetInstance = targetInstanceOpt.get();
            logger.info("Found existing waiting KoTH instance " + targetInstance.getInstanceId().toString().substring(0,8) + " for definition " + definitionId);
        } else {
            logger.info("No waiting KoTH instance found for " + definitionId + ". Attempting to create a new one.");
            // Find a compatible arena
            Optional<ArenaDefinition> arenaOpt = gameManager.getAllArenaDefinitions().stream()
                    .filter(ad -> ad.getTags().stream().anyMatch(tag -> definition.getCompatibleArenaTags().contains(tag)))
                    .findFirst(); // For simplicity, pick the first compatible one. Could be random.

            if (arenaOpt.isEmpty()) {
                player.sendMessage(ChatColor.RED + "No compatible arenas available to start a new '" + definition.getDisplayName() + "' match.");
                logger.warning("Could not create instance for KoTH definition '" + definitionId + "': No compatible arenas found.");
                return true;
            }
            String arenaIdToUse = arenaOpt.get().getArenaId();
            logger.info("Selected arena '" + arenaIdToUse + "' for new KoTH instance of definition '" + definitionId + "'.");

            Optional<GameInstance> newInstanceOpt = gameManager.createGameInstance(definitionId, arenaIdToUse);
            if (newInstanceOpt.isPresent()) {
                targetInstance = newInstanceOpt.get();
                // New instance might need to be explicitly started if it doesn't auto-start on creation
                // For now, we assume it's WAITING and players can join.
                // An admin might need to /koth startinstance if it requires manual start.
            } else {
                player.sendMessage(ChatColor.RED + "Failed to create a new KoTH match for '" + definition.getDisplayName() + "'.");
                logger.severe("Failed to create game instance for KoTH definition '" + definitionId + "' with arena '" + arenaIdToUse + "'.");
                return true;
            }
        }

        if (targetInstance != null) {
            if (!gameManager.addPlayerToInstance(player, targetInstance.getInstanceId())) {
                // Message to player should be handled by addPlayerToInstance or the GameInstance.addPlayer
                logger.warning("[KoTHCommand] Failed to add " + player.getName() + " to instance " + targetInstance.getInstanceId().toString().substring(0,8));
            }
        } else {
            // This case should ideally be caught by the creation failure messages above.
            player.sendMessage(ChatColor.RED + "Could not find or create a suitable KoTH match for '" + definition.getDisplayName() + "'.");
        }
        return true;
    }

    private boolean handleLeave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("andromedagames.player.koth.leave")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to leave games.");
            return true;
        }
        if (!gameManager.removePlayerFromInstance(player)) {
            player.sendMessage(ChatColor.RED + "You are not currently in a KoTH game match.");
        }
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.GOLD + "--- Available KoTH Game Definitions ---");
        List<GameDefinition> kothDefinitions = gameManager.getAllGameDefinitions().stream()
                .filter(def -> "KOTH".equalsIgnoreCase(def.getGameType()))
                .collect(Collectors.toList());
        if (kothDefinitions.isEmpty()){
            sender.sendMessage(ChatColor.GRAY+"No KoTH game types defined.");
        } else {
            kothDefinitions.forEach(def -> sender.sendMessage(ChatColor.YELLOW + def.getDefinitionId() + ChatColor.GRAY + " - " + def.getDisplayName()));
        }

        sender.sendMessage(ChatColor.GOLD + "--- Running KoTH Matches ---");
        List<GameInstance> runningKothInstances = gameManager.getRunningInstances().stream()
                .filter(inst -> inst.getDefinition().getGameType().equalsIgnoreCase("KOTH"))
                .collect(Collectors.toList());

        if (runningKothInstances.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No KoTH matches currently running.");
        } else {
            for (GameInstance instance : runningKothInstances) {
                sender.sendMessage(String.format("%s%s %s(ID: %s) %s- State: %s%s %s- Players: %s%d",
                        ChatColor.AQUA, instance.getDefinition().getDisplayName(),
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
        if (!sender.hasPermission("andromedagames.admin.koth.manage")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to start KoTH instances.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /koth startinstance <definitionId> [arenaId]");
            return true;
        }
        String definitionId = args[0];
        String arenaId = (args.length > 1) ? args[1] : null;

        Optional<GameDefinition> defOpt = gameManager.getGameDefinition(definitionId);
        if(defOpt.isEmpty() || !defOpt.get().getGameType().equalsIgnoreCase("KOTH")){
            sender.sendMessage(ChatColor.RED + "KoTH Game Definition '" + definitionId + "' not found.");
            return true;
        }
        GameDefinition definition = defOpt.get();

        if (arenaId == null) {
            Optional<ArenaDefinition> compatibleArenaOpt = gameManager.getAllArenaDefinitions().stream()
                    .filter(ad -> ad.getTags().stream().anyMatch(tag -> definition.getCompatibleArenaTags().contains(tag) || definition.getCompatibleArenaTags().isEmpty()))
                    .findFirst(); // Pick first compatible, or could be random.
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
            if (instance.getGameState() == GameState.WAITING) { // Only try to start if it's waiting
                if (instance.start(true)) { // true to bypass player checks for admin start
                    sender.sendMessage(ChatColor.GREEN + "New KoTH instance of '" + definition.getDisplayName() + "' on arena '" + arenaId + "' created and force-started (ID: " + instance.getInstanceId().toString().substring(0,8) + ").");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "KoTH instance '" + definition.getDisplayName() + "' created (ID: " + instance.getInstanceId().toString().substring(0,8) + ") but failed to start (e.g. no players yet, or other issue). It is in " + instance.getGameState() + " state.");
                }
            } else {
                sender.sendMessage(ChatColor.YELLOW + "KoTH instance '" + definition.getDisplayName() + "' created (ID: " + instance.getInstanceId().toString().substring(0,8) + ") but is already in state " + instance.getGameState() + ". Not attempting to start.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to create KoTH instance for definition '" + definitionId + "' on arena '" + arenaId + "'. Check console for errors.");
        }
        return true;
    }

    private boolean handleAdminStopInstance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("andromedagames.admin.koth.manage")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to stop KoTH instances.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /koth stopinstance <instanceId_prefix>");
            return true;
        }
        String instanceIdPrefix = args[0].toLowerCase();
        Optional<GameInstance> instanceOpt = gameManager.getRunningInstances().stream()
                .filter(inst -> inst.getDefinition().getGameType().equalsIgnoreCase("KOTH") &&
                        inst.getInstanceId().toString().toLowerCase().startsWith(instanceIdPrefix))
                .findFirst();

        if (instanceOpt.isPresent()) {
            GameInstance instance = instanceOpt.get();
            gameManager.endGameInstance(instance.getInstanceId());
            sender.sendMessage(ChatColor.GREEN + "KoTH instance " + instance.getDefinition().getDisplayName() + " (ID starting with " + instanceIdPrefix + ") has been stopped.");
        } else {
            sender.sendMessage(ChatColor.RED + "No running KoTH instance found with ID starting with '" + instanceIdPrefix + "'.");
        }
        return true;
    }

    private boolean handleAdminSetHill(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("andromedagames.admin.koth.setinstanceparams")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to modify KoTH instance parameters.");
            return true;
        }

        Optional<GameInstance> instanceOpt = gameManager.getPlayerGameInstance(player);
        if (instanceOpt.isPresent() && instanceOpt.get() instanceof KoTHGame) {
            KoTHGame kothInstance = (KoTHGame) instanceOpt.get();
            if (kothInstance.getGameState() == GameState.ACTIVE || kothInstance.getGameState() == GameState.STARTING) {
                kothInstance.adminSetHillLocation(player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Hill center for your current KoTH match temporarily set to your location.");
                player.sendMessage(ChatColor.YELLOW + "This change is for the current match only and is not saved to definitions.");
            } else {
                player.sendMessage(ChatColor.RED + "Your current KoTH match is not active or starting.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "You are not currently in an active KoTH match instance.");
        }
        return true;
    }

    private boolean handleAdminSetRadius(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("andromedagames.admin.koth.setinstanceparams")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to modify KoTH instance parameters.");
            return true;
        }
        if (args.length < 1) { // Changed from < 2 to < 1 as radius is args[0] for this subcommand
            player.sendMessage(ChatColor.RED + "Usage: /koth setradius <radius>");
            return true;
        }
        int radius;
        try {
            radius = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid radius: '" + args[0] + "'. Must be a number.");
            return true;
        }
        if (radius <= 0) {
            player.sendMessage(ChatColor.RED + "Radius must be a positive number.");
            return true;
        }

        Optional<GameInstance> instanceOpt = gameManager.getPlayerGameInstance(player);
        if (instanceOpt.isPresent() && instanceOpt.get() instanceof KoTHGame) {
            KoTHGame kothInstance = (KoTHGame) instanceOpt.get();
            if (kothInstance.getGameState() == GameState.ACTIVE || kothInstance.getGameState() == GameState.STARTING) {
                kothInstance.adminSetHillRadius(radius);
                player.sendMessage(ChatColor.GREEN + "Hill radius for your current KoTH match temporarily set to " + radius + ".");
                player.sendMessage(ChatColor.YELLOW + "This change is for the current match only and is not saved to definitions.");
            } else {
                player.sendMessage(ChatColor.RED + "Your current KoTH match is not active or starting.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "You are not currently in an active KoTH match instance.");
        }
        return true;
    }
}