package io.mewb.andromedaGames.command;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.game.Game;
import io.mewb.andromedaGames.game.GameManager;
import io.mewb.andromedaGames.game.GameState;
import io.mewb.andromedaGames.infection.InfectionGame; // Import InfectionGame
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
import java.util.stream.Collectors;

public class InfectionCommand implements CommandExecutor, TabCompleter {

    private final AndromedaGames plugin;
    private final GameManager gameManager;

    public InfectionCommand(AndromedaGames plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
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
            // Player commands
            case "join":
                return handleJoin(sender, subArgs);
            case "leave":
                return handleLeave(sender, subArgs);
            case "list":
                return handleList(sender, subArgs);
            // Admin commands
            case "start":
                return handleAdminStart(sender, subArgs);
            case "stop":
                return handleAdminStop(sender, subArgs);
            // Add more admin commands like setspawn, setlobby etc. later if needed
            default:
                sender.sendMessage(ChatColor.RED + "Unknown Infection subcommand: " + ChatColor.YELLOW + subCommand);
                sendBaseInfectionHelp(sender);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();
        List<String> subcommands = new ArrayList<>(Arrays.asList("join", "leave", "list"));

        // Check for a general admin permission for infection games
        if (sender.hasPermission("andromedagames.admin.infection.manage")) {
            subcommands.addAll(Arrays.asList("start", "stop"));
        }

        if (args.length == 1) { // Tab completing the subcommand itself
            StringUtil.copyPartialMatches(args[0], subcommands, completions);
        } else if (args.length >= 2) { // Tab completing arguments for a subcommand
            String subCommand = args[0].toLowerCase();
            // Arguments for game-specific commands (join, start, stop)
            if (Arrays.asList("join", "start", "stop").contains(subCommand)) {
                if (args.length == 2) { // Expecting gameId as the first argument
                    // Filter for InfectionGame IDs
                    List<String> infectionGameIds = gameManager.getAllGames().stream()
                            .filter(InfectionGame.class::isInstance)
                            .map(Game::getGameId)
                            .collect(Collectors.toList());
                    StringUtil.copyPartialMatches(args[1], infectionGameIds, completions);
                }
            }
        }
        Collections.sort(completions);
        return completions;
    }

    private void sendBaseInfectionHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "--- Infection Commands ---");
        if (sender.hasPermission("andromedagames.player.infection.join")) {
            sender.sendMessage(ChatColor.YELLOW + "/infection join <gameId>" + ChatColor.GRAY + " - Joins an Infection game.");
        }
        if (sender.hasPermission("andromedagames.player.infection.leave")) {
            sender.sendMessage(ChatColor.YELLOW + "/infection leave" + ChatColor.GRAY + " - Leaves your current Infection game.");
        }
        sender.sendMessage(ChatColor.YELLOW + "/infection list" + ChatColor.GRAY + " - Lists available Infection games.");

        if (sender.hasPermission("andromedagames.admin.infection.manage")) {
            sender.sendMessage(ChatColor.RED + "Admin Infection Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/infection start <gameId>" + ChatColor.GRAY + " - Force starts an Infection game.");
            sender.sendMessage(ChatColor.YELLOW + "/infection stop <gameId>" + ChatColor.GRAY + " - Force stops an Infection game.");
        }
    }

    // --- Player Command Handlers ---

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
            player.sendMessage(ChatColor.RED + "Usage: /infection join <gameId>");
            return true;
        }
        String gameId = args[0];

        if (gameManager.isPlayerInAnyGame(player)) {
            player.sendMessage(ChatColor.RED + "You are already in a game. Type " + ChatColor.YELLOW + "/infection leave" + ChatColor.RED + " or the equivalent for your current game first.");
            return true;
        }

        Optional<Game> gameOpt = gameManager.getGame(gameId);
        if (gameOpt.isPresent() && gameOpt.get() instanceof InfectionGame) {
            InfectionGame infectionGame = (InfectionGame) gameOpt.get();
            if (!infectionGame.addPlayer(player)) {
                // Failure message should be sent by InfectionGame.addPlayer()
            }
            // Success message is also sent by InfectionGame.addPlayer()
        } else {
            player.sendMessage(ChatColor.RED + "Infection game '" + gameId + "' not found or is not an Infection game type.");
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
            player.sendMessage(ChatColor.RED + "You do not have permission to leave Infection games.");
            return true;
        }
        // GameManager.removePlayerFromGame will call the specific game's removePlayer method.
        if (!gameManager.removePlayerFromGame(player)) {
            player.sendMessage(ChatColor.RED + "You are not currently in an Infection game.");
        }
        // Success/failure message is typically sent by InfectionGame.removePlayer()
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        List<InfectionGame> infectionGames = gameManager.getAllGames().stream()
                .filter(InfectionGame.class::isInstance)
                .map(InfectionGame.class::cast)
                .collect(Collectors.toList());

        if (infectionGames.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No Infection games are currently available or defined.");
            return true;
        }
        sender.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "--- Available Infection Games ---");
        for (InfectionGame game : infectionGames) {
            sender.sendMessage(String.format("%s%s %s- State: %s%s %s- Players: %s%d",
                    ChatColor.AQUA, game.getGameId(), ChatColor.GRAY,
                    ChatColor.YELLOW, game.getGameState().name(), ChatColor.GRAY,
                    ChatColor.LIGHT_PURPLE, game.getPlayerCount()));
        }
        return true;
    }

    // --- Admin Command Handlers ---

    private boolean handleAdminStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("andromedagames.admin.infection.start")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to force start Infection games.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /infection start <gameId>");
            return true;
        }
        String gameId = args[0];
        Optional<Game> gameOpt = gameManager.getGame(gameId);

        if (gameOpt.isPresent() && gameOpt.get() instanceof InfectionGame) {
            InfectionGame infectionGame = (InfectionGame) gameOpt.get();
            if (infectionGame.getGameState() == GameState.WAITING || infectionGame.getGameState() == GameState.ENDING) {
                // Call the start method that allows bypassing player check
                if (infectionGame.start(true)) { // true to bypass min player check
                    sender.sendMessage(ChatColor.GREEN + "Infection game '" + gameId + "' has been force-started (bypassing player check).");
                } else {
                    sender.sendMessage(ChatColor.RED + "Could not start Infection game '" + gameId + "'. It might need more players (even if forced) or have other issues (check console).");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Infection game '" + gameId + "' is already " + infectionGame.getGameState().name().toLowerCase() + " or cannot be started from this state.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Infection game '" + gameId + "' not found.");
        }
        return true;
    }

    private boolean handleAdminStop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("andromedagames.admin.infection.stop")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to force stop Infection games.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /infection stop <gameId>");
            return true;
        }
        String gameId = args[0];
        Optional<Game> gameOpt = gameManager.getGame(gameId);

        if (gameOpt.isPresent() && gameOpt.get() instanceof InfectionGame) {
            InfectionGame infectionGame = (InfectionGame) gameOpt.get();
            if (infectionGame.getGameState() == GameState.ACTIVE || infectionGame.getGameState() == GameState.STARTING) {
                infectionGame.stop(true); // Force stop
                sender.sendMessage(ChatColor.GREEN + "Infection game '" + gameId + "' has been force-stopped.");
            } else {
                sender.sendMessage(ChatColor.RED + "Infection game '" + gameId + "' is not currently running or starting. Current state: " + infectionGame.getGameState().name());
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Infection game '" + gameId + "' not found.");
        }
        return true;
    }
}
