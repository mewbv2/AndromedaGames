package io.mewb.andromedaGames.command;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.game.Game;
import io.mewb.andromedaGames.game.GameManager;
import io.mewb.andromedaGames.game.GameState;
import io.mewb.andromedaGames.koth.KoTHGame;
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
import java.util.stream.Collectors;

public class KoTHCommand implements CommandExecutor, TabCompleter {

    private final AndromedaGames plugin;
    private final GameManager gameManager;

    public KoTHCommand(AndromedaGames plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
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
            case "start":
                return handleStart(sender, subArgs);
            case "stop":
                return handleStop(sender, subArgs);
            case "sethill":
                return handleSetHill(sender, subArgs);
            case "setradius":
                return handleSetRadius(sender, subArgs);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown KoTH subcommand: " + ChatColor.YELLOW + subCommand);
                sendBaseKoTHHelp(sender);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0],
                    Arrays.asList("join", "leave", "list", "start", "stop", "sethill", "setradius"),
                    completions);
        } else if (args.length >= 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("join") || subCommand.equals("start") || subCommand.equals("stop") ||
                    subCommand.equals("sethill") || subCommand.equals("setradius")) {
                if (args.length == 2) { // Expecting gameId
                    StringUtil.copyPartialMatches(args[1], gameManager.getKoTHGameIds(), completions);
                }
            }
            if (subCommand.equals("setradius") && args.length == 3) { // Expecting radius number
                StringUtil.copyPartialMatches(args[2], Arrays.asList("5", "10", "15"), completions);
            }
        }
        Collections.sort(completions);
        return completions;
    }

    private void sendBaseKoTHHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- KoTH Commands ---");
        sender.sendMessage(ChatColor.YELLOW + "/koth join <gameId>" + ChatColor.GRAY + " - Joins a KoTH game.");
        sender.sendMessage(ChatColor.YELLOW + "/koth leave" + ChatColor.GRAY + " - Leaves your current game.");
        sender.sendMessage(ChatColor.YELLOW + "/koth list" + ChatColor.GRAY + " - Lists available KoTH games.");
        if (sender.hasPermission("andromedagames.admin.koth.manage")) { // Example broader admin perm
            sender.sendMessage(ChatColor.RED + "Admin Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/koth start <gameId>" + ChatColor.GRAY + " - Force starts a game.");
            sender.sendMessage(ChatColor.YELLOW + "/koth stop <gameId>" + ChatColor.GRAY + " - Force stops a game.");
            sender.sendMessage(ChatColor.YELLOW + "/koth sethill <gameId>" + ChatColor.GRAY + " - Sets hill center (player only).");
            sender.sendMessage(ChatColor.YELLOW + "/koth setradius <gameId> <radius>" + ChatColor.GRAY + " - Sets hill radius.");
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
            player.sendMessage(ChatColor.RED + "Usage: /koth join <gameId>");
            return true;
        }
        String gameId = args[0];

        if (gameManager.isPlayerInAnyGame(player)) {
            player.sendMessage(ChatColor.RED + "You are already in a game. Type /koth leave first.");
            return true;
        }

        Optional<Game> gameOpt = gameManager.getGame(gameId);
        if (gameOpt.isPresent() && gameOpt.get() instanceof KoTHGame) {
            KoTHGame kothGame = (KoTHGame) gameOpt.get();
            if (!kothGame.addPlayer(player)) {
                // Failure message sent by KoTHGame.addPlayer() or if it returns false without msg:
                // player.sendMessage(ChatColor.RED + "Could not join game '" + gameId + "'. It might be full or already started.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "KoTH game '" + gameId + "' not found.");
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
        if (!gameManager.removePlayerFromGame(player)) {
            player.sendMessage(ChatColor.RED + "You are not currently in a KoTH game.");
        }
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        List<KoTHGame> kothGames = gameManager.getAllGames().stream()
                .filter(KoTHGame.class::isInstance)
                .map(KoTHGame.class::cast)
                .collect(Collectors.toList());

        if (kothGames.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No KoTH games are currently available.");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "--- Available KoTH Games ---");
        for (KoTHGame game : kothGames) {
            sender.sendMessage(String.format("%s%s %s- State: %s%s %s- Players: %s%d",
                    ChatColor.AQUA, game.getGameId(), ChatColor.GRAY,
                    ChatColor.YELLOW, game.getGameState().name(), ChatColor.GRAY,
                    ChatColor.LIGHT_PURPLE, game.getPlayerCount()));
        }
        return true;
    }

    private boolean handleStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("andromedagames.admin.koth.forcestart")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to force start games.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /koth start <gameId>");
            return true;
        }
        String gameId = args[0];
        Optional<Game> gameOpt = gameManager.getGame(gameId);

        if (gameOpt.isPresent() && gameOpt.get() instanceof KoTHGame) {
            KoTHGame kothGame = (KoTHGame) gameOpt.get();
            if (kothGame.getGameState() == GameState.WAITING || kothGame.getGameState() == GameState.ENDING) {
                if (kothGame.start()) {
                    sender.sendMessage(ChatColor.GREEN + "KoTH game '" + gameId + "' has been force-started.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Could not start KoTH game '" + gameId + "'. Check player count or console.");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "KoTH game '" + gameId + "' is already active or starting.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "KoTH game '" + gameId + "' not found.");
        }
        return true;
    }

    private boolean handleStop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("andromedagames.admin.koth.forcestop")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to force stop games.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /koth stop <gameId>");
            return true;
        }
        String gameId = args[0];
        Optional<Game> gameOpt = gameManager.getGame(gameId);

        if (gameOpt.isPresent() && gameOpt.get() instanceof KoTHGame) {
            KoTHGame kothGame = (KoTHGame) gameOpt.get();
            if (kothGame.getGameState() == GameState.ACTIVE || kothGame.getGameState() == GameState.STARTING) {
                kothGame.stop(true); // Force stop
                sender.sendMessage(ChatColor.GREEN + "KoTH game '" + gameId + "' has been force-stopped.");
            } else {
                sender.sendMessage(ChatColor.RED + "KoTH game '" + gameId + "' is not currently running.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "KoTH game '" + gameId + "' not found.");
        }
        return true;
    }

    private boolean handleSetHill(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("andromedagames.admin.koth.sethill")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to set the hill location.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /koth sethill <gameId>");
            return true;
        }
        String gameId = args[0];
        Optional<Game> gameOpt = gameManager.getGame(gameId);

        if (gameOpt.isPresent() && gameOpt.get() instanceof KoTHGame) {
            KoTHGame kothGame = (KoTHGame) gameOpt.get();
            Location newHillCenter = player.getLocation();
            kothGame.setHillLocation(newHillCenter);
            player.sendMessage(String.format("%sHill center for KoTH game '%s' set to your current location (%.1f, %.1f, %.1f in %s).",
                    ChatColor.GREEN, gameId, newHillCenter.getX(), newHillCenter.getY(), newHillCenter.getZ(), newHillCenter.getWorld().getName()));
            player.sendMessage(ChatColor.YELLOW + "Remember to save this to configuration for persistence!");
        } else {
            player.sendMessage(ChatColor.RED + "KoTH game '" + gameId + "' not found.");
        }
        return true;
    }

    private boolean handleSetRadius(CommandSender sender, String[] args) {
        if (!sender.hasPermission("andromedagames.admin.koth.setradius")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to set the hill radius.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /koth setradius <gameId> <radius>");
            return true;
        }
        String gameId = args[0];
        int radius;
        try {
            radius = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid radius: '" + args[1] + "'. Must be a number.");
            return true;
        }

        if (radius <= 0) {
            sender.sendMessage(ChatColor.RED + "Radius must be a positive integer.");
            return true;
        }

        Optional<Game> gameOpt = gameManager.getGame(gameId);
        if (gameOpt.isPresent() && gameOpt.get() instanceof KoTHGame) {
            KoTHGame kothGame = (KoTHGame) gameOpt.get();
            kothGame.setHillRadius(radius);
            sender.sendMessage(String.format("%sHill radius for KoTH game '%s' set to %d.", ChatColor.GREEN, gameId, radius));
            sender.sendMessage(ChatColor.YELLOW + "Remember to save this to configuration for persistence!");
        } else {
            sender.sendMessage(ChatColor.RED + "KoTH game '" + gameId + "' not found.");
        }
        return true;
    }
}