package io.mewb.andromedaGames.command;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.game.Game;
import io.mewb.andromedaGames.game.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AndromedaGamesCommand implements CommandExecutor, TabCompleter {

    private final AndromedaGames plugin;
    private final GameManager gameManager;

    public AndromedaGamesCommand(AndromedaGames plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
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

        String subCommand = args[0].toLowerCase();
        String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        switch (subCommand) {
            case "reload":
                return handleReload(sender, subArgs);
            case "listgames":
                return handleListGames(sender, subArgs);
            // case "enablegame":
            // return handleEnableGame(sender, subArgs);
            // case "disablegame":
            // return handleDisableGame(sender, subArgs);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown admin subcommand: " + ChatColor.YELLOW + subCommand);
                sendBaseAdminHelp(sender);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("andromedagames.admin")) {
            return completions; // Empty list
        }

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0],
                    Arrays.asList("reload", "listgames"/*, "enablegame", "disablegame"*/),
                    completions);
        } else if (args.length >= 2) {
            // String sub = args[0].toLowerCase();
            // if ((sub.equals("enablegame") || sub.equals("disablegame")) && args.length == 2) {
            // StringUtil.copyPartialMatches(args[1],
            // gameManager.getAllGames().stream().map(Game::getGameId).collect(Collectors.toList()),
            // completions);
            // }
        }
        Collections.sort(completions);
        return completions;
    }

    private void sendBaseAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- AndromedaGames Admin ---");
        sender.sendMessage(ChatColor.YELLOW + "/ag reload" + ChatColor.GRAY + " - Reloads plugin configurations.");
        sender.sendMessage(ChatColor.YELLOW + "/ag listgames" + ChatColor.GRAY + " - Lists all loaded games and statuses.");
        // sender.sendMessage(ChatColor.YELLOW + "/ag enablegame <gameId>" + ChatColor.GRAY + " - Enables a game.");
        // sender.sendMessage(ChatColor.YELLOW + "/ag disablegame <gameId>" + ChatColor.GRAY + " - Disables a game.");
    }

    private boolean handleReload(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading AndromedaGames configurations...");
        this.plugin.reloadConfig(); // Reloads Bukkit's config.yml
        // TODO: Implement robust config reloading for gameManager and other custom configs
        // gameManager.loadGamesFromConfig(); // This needs careful implementation
        sender.sendMessage(ChatColor.GREEN + "Configurations reloaded (basic reload for now).");
        sender.sendMessage(ChatColor.GOLD + "Note: Full dynamic game config reloading is complex and not fully implemented.");
        return true;
    }

    private boolean handleListGames(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.GOLD + "--- Loaded Games ---");
        if (gameManager.getAllGames().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No games are currently loaded.");
            return true;
        }
        for (Game game : gameManager.getAllGames()) {
            sender.sendMessage(String.format("%s%s %s(%s%s%s) - State: %s%s %s- Players: %s%d",
                    ChatColor.AQUA, game.getGameId(), ChatColor.GRAY,
                    ChatColor.GREEN, game.getClass().getSimpleName().replace("Game", ""), ChatColor.GRAY,
                    ChatColor.YELLOW, game.getGameState().name(), ChatColor.GRAY,
                    ChatColor.LIGHT_PURPLE, game.getPlayerCount()));
        }
        return true;
    }
}