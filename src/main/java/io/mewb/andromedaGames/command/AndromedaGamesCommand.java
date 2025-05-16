package io.mewb.andromedaGames.command;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.game.GameInstance;
import io.mewb.andromedaGames.game.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player; // Added for TabCompleter context
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AndromedaGamesCommand implements CommandExecutor, TabCompleter {

    private final AndromedaGames plugin;
    private final GameManager gameManager;
    private final ArenaAdminCommand arenaAdminCommand; // Instance of the new command handler

    public AndromedaGamesCommand(AndromedaGames plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.arenaAdminCommand = new ArenaAdminCommand(plugin); // Initialize ArenaAdminCommand
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("andromedagames.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use AndromedaGames admin commands.");
            return true;
        }

        if (args.length == 0) {
            sendBaseAdminHelp(sender);
            return true;
        }

        String mainSubCommand = args[0].toLowerCase();
        String[] subCommandArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        switch (mainSubCommand) {
            case "reload":
                return handleReload(sender);
            case "listinstances":
                return handleListInstances(sender);
            case "arena": // New case for arena subcommands
                // Delegate to ArenaAdminCommand
                // We pass 'command' and 'label' from the original /ag command,
                // and the subCommandArgs which are the arguments *after* "arena"
                return arenaAdminCommand.onCommand(sender, command, label + " arena", subCommandArgs);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown admin subcommand: " + ChatColor.YELLOW + mainSubCommand);
                sendBaseAdminHelp(sender);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("andromedagames.admin")) {
            return completions;
        }

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0],
                    Arrays.asList("reload", "listinstances", "arena"), // Added "arena"
                    completions);
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("arena")) {
            // Delegate tab completion to ArenaAdminCommand
            // Create a new args array for ArenaAdminCommand, excluding the "arena" part
            String[] arenaCmdArgs = Arrays.copyOfRange(args, 1, args.length);
            return arenaAdminCommand.onTabComplete(sender, command, alias + " arena", arenaCmdArgs);
        }
        // Add more tab completions for future subcommands if necessary
        Collections.sort(completions);
        return completions;
    }

    private void sendBaseAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- AndromedaGames Admin ---");
        sender.sendMessage(ChatColor.YELLOW + "/ag reload" + ChatColor.GRAY + " - Reloads plugin configurations.");
        sender.sendMessage(ChatColor.YELLOW + "/ag listinstances" + ChatColor.GRAY + " - Lists all running game instances.");
        sender.sendMessage(ChatColor.YELLOW + "/ag arena" + ChatColor.GRAY + " - Shows arena setup & management commands.");
    }

    private boolean handleReload(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading AndromedaGames configurations...");
        plugin.reloadConfig();
        if (gameManager != null) {
            gameManager.loadAllDefinitionsAndArenas();
            sender.sendMessage(ChatColor.GREEN + "Game and Arena definitions reloaded. All running instances were stopped.");
        } else {
            sender.sendMessage(ChatColor.RED + "GameManager not available.");
        }
        return true;
    }

    private boolean handleListInstances(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- Running Game Instances ---");
        if (gameManager == null) {
            sender.sendMessage(ChatColor.RED + "GameManager is not available.");
            return true;
        }
        if (gameManager.getRunningInstances().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No game instances are currently running.");
            return true;
        }
        for (GameInstance instance : gameManager.getRunningInstances()) {
            String instanceIdShort = instance.getInstanceId().toString().substring(0, 8);
            String defId = instance.getDefinition().getDefinitionId();
            String arenaId = instance.getArena().getArenaId();
            String gameType = instance.getDefinition().getGameType();

            sender.sendMessage(String.format("%sID: %s%s %s- Type: %s%s %s(Def: %s%s, Arena: %s%s)",
                    ChatColor.AQUA, instanceIdShort, ChatColor.GRAY,
                    ChatColor.WHITE, ChatColor.GREEN, gameType, ChatColor.GRAY,
                    ChatColor.YELLOW, defId, ChatColor.GRAY,
                    ChatColor.YELLOW, arenaId, ChatColor.GRAY
            ));
            sender.sendMessage(String.format("  %sState: %s%s %s- Players: %s%d",
                    ChatColor.GRAY, ChatColor.LIGHT_PURPLE, instance.getGameState().name(),
                    ChatColor.GRAY, ChatColor.GOLD, instance.getPlayerCount()
            ));
        }
        return true;
    }
}