package io.mewb.andromedaGames.command;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.arena.ArenaDefinition; // For finding compatible arenas
import io.mewb.andromedaGames.game.GameDefinition;
import io.mewb.andromedaGames.game.GameInstance;
import io.mewb.andromedaGames.game.GameManager;
import io.mewb.andromedaGames.game.GameState;
import io.mewb.andromedaGames.infection.InfectionGame; // This is our InfectionGameInstance (extends GameInstance)
import org.bukkit.ChatColor;
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

public class InfectionCommand implements CommandExecutor, TabCompleter {

    private final AndromedaGames plugin;
    private final GameManager gameManager;
    private final Logger logger;

    public InfectionCommand(AndromedaGames plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendBaseInfectionHelp(sender);
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
            default:
                sender.sendMessage(ChatColor.RED + "Unknown Infection subcommand: " + ChatColor.YELLOW + subCommand);
                sendBaseInfectionHelp(sender);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();
        List<String> baseSubcommands = new ArrayList<>(Arrays.asList("join", "leave", "list"));
        List<String> adminSubcommands = new ArrayList<>();

        if (sender.hasPermission("andromedagames.admin.infection.manage")) {
            adminSubcommands.addAll(Arrays.asList("startinstance", "stopinstance"));
        }

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], baseSubcommands, completions);
            StringUtil.copyPartialMatches(args[0], adminSubcommands, completions);
        } else if (args.length >= 2) {
            String subCommand = args[0].toLowerCase();
            List<String> infectionDefinitionIds = gameManager.getAllGameDefinitions().stream()
                    .filter(def -> "INFECTION".equalsIgnoreCase(def.getGameType()))
                    .map(GameDefinition::getDefinitionId)
                    .collect(Collectors.toList());

            List<String> runningInfectionInstanceIdPrefixes = gameManager.getRunningInstances().stream()
                    .filter(inst -> inst.getDefinition().getGameType().equalsIgnoreCase("INFECTION"))
                    .map(inst -> inst.getInstanceId().toString().substring(0, 8)) // Shortened UUID
                    .collect(Collectors.toList());

            switch (subCommand) {
                case "join":
                case "startinstance":
                    if (args.length == 2) { // Suggest Infection definitionId
                        StringUtil.copyPartialMatches(args[1], infectionDefinitionIds, completions);
                    } else if (subCommand.equals("startinstance") && args.length == 3) { // Suggest arenaId for startinstance
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
                    if (args.length == 2) { // Suggest running Infection instanceId (shortened prefix)
                        StringUtil.copyPartialMatches(args[1], runningInfectionInstanceIdPrefixes, completions);
                    }
                    break;
            }
        }
        Collections.sort(completions);
        return completions;
    }

    private void sendBaseInfectionHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "--- Infection Commands ---");
        if (sender.hasPermission("andromedagames.player.infection.join")) {
            sender.sendMessage(ChatColor.YELLOW + "/infection join <definitionId>" + ChatColor.GRAY + " - Joins an available Infection game match.");
        }
        if (sender.hasPermission("andromedagames.player.infection.leave")) {
            sender.sendMessage(ChatColor.YELLOW + "/infection leave" + ChatColor.GRAY + " - Leaves your current game match.");
        }
        sender.sendMessage(ChatColor.YELLOW + "/infection list" + ChatColor.GRAY + " - Lists Infection game types and active matches.");

        if (sender.hasPermission("andromedagames.admin.infection.manage")) {
            sender.sendMessage(ChatColor.RED + "Admin Infection Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/infection startinstance <definitionId> [arenaId]" + ChatColor.GRAY + " - Starts a new Infection match.");
            sender.sendMessage(ChatColor.YELLOW + "/infection stopinstance <instanceId_prefix>" + ChatColor.GRAY + " - Stops a running Infection match.");
        }
    }

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("andromedagames.player.infection.join")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to join Infection games.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /infection join <definitionId>");
            return true;
        }
        String definitionId = args[0];
        logger.finer("[InfectionCommand] Player " + player.getName() + " attempting to join Infection definition: " + definitionId);

        if (gameManager.isPlayerInAnyInstance(player)) {
            player.sendMessage(ChatColor.RED + "You are already in a game.");
            return true;
        }

        Optional<GameDefinition> defOpt = gameManager.getGameDefinition(definitionId);
        if (defOpt.isEmpty() || !defOpt.get().getGameType().equalsIgnoreCase("INFECTION")) {
            player.sendMessage(ChatColor.RED + "Infection game definition '" + definitionId + "' not found.");
            return true;
        }
        GameDefinition definition = defOpt.get();

        Optional<GameInstance> targetInstanceOpt = gameManager.getRunningInstances().stream()
                .filter(inst -> inst.getDefinition().getDefinitionId().equalsIgnoreCase(definitionId) &&
                        inst.getGameState() == GameState.WAITING &&
                        inst.getPlayerCount() < definition.getRule("max_players", 20)) // Example max_players rule
                .findFirst();

        GameInstance targetInstance;
        if (targetInstanceOpt.isPresent()) {
            targetInstance = targetInstanceOpt.get();
            logger.info("Found existing waiting Infection instance " + targetInstance.getInstanceId().toString().substring(0,8) + " for definition " + definitionId);
        } else {
            logger.info("No waiting Infection instance found for " + definitionId + ". Attempting to create a new one.");
            Optional<ArenaDefinition> arenaOpt = gameManager.getAllArenaDefinitions().stream()
                    .filter(ad -> ad.getTags().stream().anyMatch(tag -> definition.getCompatibleArenaTags().contains(tag) || definition.getCompatibleArenaTags().isEmpty()))
                    .findFirst();

            if (arenaOpt.isEmpty()) {
                player.sendMessage(ChatColor.RED + "No compatible arenas available to start a new '" + definition.getDisplayName() + "' match.");
                logger.warning("Could not create instance for Infection definition '" + definitionId + "': No compatible arenas found.");
                return true;
            }
            String arenaIdToUse = arenaOpt.get().getArenaId();
            logger.info("Selected arena '" + arenaIdToUse + "' for new Infection instance of definition '" + definitionId + "'.");

            Optional<GameInstance> newInstanceOpt = gameManager.createGameInstance(definitionId, arenaIdToUse);
            if (newInstanceOpt.isPresent()) {
                targetInstance = newInstanceOpt.get();
            } else {
                player.sendMessage(ChatColor.RED + "Failed to create a new Infection match for '" + definition.getDisplayName() + "'.");
                logger.severe("Failed to create game instance for Infection definition '" + definitionId + "' with arena '" + arenaIdToUse + "'.");
                return true;
            }
        }

        if (targetInstance != null) {
            if (!gameManager.addPlayerToInstance(player, targetInstance.getInstanceId())) {
                logger.warning("[InfectionCommand] Failed to add " + player.getName() + " to instance " + targetInstance.getInstanceId().toString().substring(0,8));
            }
        } else {
            player.sendMessage(ChatColor.RED + "Could not find or create a suitable Infection match for '" + definition.getDisplayName() + "'.");
        }
        return true;
    }

    private boolean handleLeave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("andromedagames.player.infection.leave")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to leave games.");
            return true;
        }
        if (!gameManager.removePlayerFromInstance(player)) {
            player.sendMessage(ChatColor.RED + "You are not currently in an Infection game match.");
        }
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "--- Available Infection Game Definitions ---");
        List<GameDefinition> infectionDefinitions = gameManager.getAllGameDefinitions().stream()
                .filter(def -> "INFECTION".equalsIgnoreCase(def.getGameType()))
                .collect(Collectors.toList());
        if (infectionDefinitions.isEmpty()){
            sender.sendMessage(ChatColor.GRAY+"No Infection game types defined.");
        } else {
            infectionDefinitions.forEach(def -> sender.sendMessage(ChatColor.YELLOW + def.getDefinitionId() + ChatColor.GRAY + " - " + def.getDisplayName()));
        }

        sender.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "--- Running Infection Matches ---");
        List<GameInstance> runningInfectionInstances = gameManager.getRunningInstances().stream()
                .filter(inst -> inst.getDefinition().getGameType().equalsIgnoreCase("INFECTION"))
                .collect(Collectors.toList());

        if (runningInfectionInstances.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No Infection matches currently running.");
        } else {
            for (GameInstance instance : runningInfectionInstances) {
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
        if (!sender.hasPermission("andromedagames.admin.infection.manage")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to start Infection instances.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /infection startinstance <definitionId> [arenaId]");
            return true;
        }
        String definitionId = args[0];
        String arenaId = (args.length > 1) ? args[1] : null;

        Optional<GameDefinition> defOpt = gameManager.getGameDefinition(definitionId);
        if(defOpt.isEmpty() || !defOpt.get().getGameType().equalsIgnoreCase("INFECTION")){
            sender.sendMessage(ChatColor.RED + "Infection Game Definition '" + definitionId + "' not found.");
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
                    sender.sendMessage(ChatColor.GREEN + "New Infection instance of '" + definition.getDisplayName() + "' on arena '" + arenaId + "' created and force-started (ID: " + instance.getInstanceId().toString().substring(0,8) + ").");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Infection instance '" + definition.getDisplayName() + "' created (ID: " + instance.getInstanceId().toString().substring(0,8) + ") but failed to start. It is in " + instance.getGameState() + " state.");
                }
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Infection instance '" + definition.getDisplayName() + "' created (ID: " + instance.getInstanceId().toString().substring(0,8) + ") but is already in state " + instance.getGameState() + ". Not attempting to start.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to create Infection instance for definition '" + definitionId + "' on arena '" + arenaId + "'. Check console for errors.");
        }
        return true;
    }

    private boolean handleAdminStopInstance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("andromedagames.admin.infection.manage")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to stop Infection instances.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /infection stopinstance <instanceId_prefix>");
            return true;
        }
        String instanceIdPrefix = args[0].toLowerCase();
        Optional<GameInstance> instanceOpt = gameManager.getRunningInstances().stream()
                .filter(inst -> inst.getDefinition().getGameType().equalsIgnoreCase("INFECTION") &&
                        inst.getInstanceId().toString().toLowerCase().startsWith(instanceIdPrefix))
                .findFirst();

        if (instanceOpt.isPresent()) {
            GameInstance instance = instanceOpt.get();
            gameManager.endGameInstance(instance.getInstanceId());
            sender.sendMessage(ChatColor.GREEN + "Infection instance " + instance.getDefinition().getDisplayName() + " (ID starting with " + instanceIdPrefix + ") has been stopped.");
        } else {
            sender.sendMessage(ChatColor.RED + "No running Infection instance found with ID starting with '" + instanceIdPrefix + "'.");
        }
        return true;
    }
}
