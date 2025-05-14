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
            case "start": // Admin start command
                return handleAdminStart(sender, subArgs);
            case "stop":
                return handleAdminStop(sender, subArgs);
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
        List<String> subcommands = new ArrayList<>(Arrays.asList("join", "leave", "list"));
        if (sender.hasPermission("andromedagames.admin.koth.manage")) {
            subcommands.addAll(Arrays.asList("start", "stop", "sethill", "setradius"));
        }

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], subcommands, completions);
        } else if (args.length >= 2) {
            String subCommand = args[0].toLowerCase();
            if (Arrays.asList("join", "start", "stop", "sethill", "setradius").contains(subCommand)) {
                if (args.length == 2) {
                    StringUtil.copyPartialMatches(args[1], gameManager.getKoTHGameIds(), completions);
                }
            }
            if (subCommand.equals("setradius") && args.length == 3) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("3", "5", "7", "10", "15"), completions);
            }
        }
        Collections.sort(completions);
        return completions;
    }

    private void sendBaseKoTHHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- King of the Hill (KoTH) Commands ---");
        if (sender.hasPermission("andromedagames.player.koth.join")) {
            sender.sendMessage(ChatColor.YELLOW + "/koth join <gameId>" + ChatColor.GRAY + " - Joins a KoTH game.");
        }
        if (sender.hasPermission("andromedagames.player.koth.leave")) {
            sender.sendMessage(ChatColor.YELLOW + "/koth leave" + ChatColor.GRAY + " - Leaves your current KoTH game.");
        }
        sender.sendMessage(ChatColor.YELLOW + "/koth list" + ChatColor.GRAY + " - Lists available KoTH games.");
        if (sender.hasPermission("andromedagames.admin.koth.manage")) {
            sender.sendMessage(ChatColor.RED + "Admin KoTH Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/koth start <gameId>" + ChatColor.GRAY + " - Force starts a KoTH game (allows solo).");
            sender.sendMessage(ChatColor.YELLOW + "/koth stop <gameId>" + ChatColor.GRAY + " - Force stops a KoTH game.");
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
            player.sendMessage(ChatColor.RED + "You are already in a game. Type " + ChatColor.YELLOW + "/koth leave" + ChatColor.RED + " first.");
            return true;
        }
        Optional<Game> gameOpt = gameManager.getGame(gameId);
        if (gameOpt.isPresent() && gameOpt.get() instanceof KoTHGame) {
            KoTHGame kothGame = (KoTHGame) gameOpt.get();
            kothGame.addPlayer(player); // Messages handled by addPlayer
        } else {
            player.sendMessage(ChatColor.RED + "KoTH game '" + gameId + "' not found or is not a KoTH game type.");
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
            player.sendMessage(ChatColor.RED + "You do not have permission to leave KoTH games.");
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
            sender.sendMessage(ChatColor.YELLOW + "No KoTH games are currently available or defined.");
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

    private boolean handleAdminStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("andromedagames.admin.koth.start")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to force start KoTH games.");
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
                // Call the new start method, bypassing player check for admins
                if (kothGame.start(true)) {
                    sender.sendMessage(ChatColor.GREEN + "KoTH game '" + gameId + "' has been force-started (bypassing player check).");
                } else {
                    sender.sendMessage(ChatColor.RED + "Could not start KoTH game '" + gameId + "'. Check console for details.");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "KoTH game '" + gameId + "' is already " + kothGame.getGameState().name().toLowerCase() + " or cannot be started from this state.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "KoTH game '" + gameId + "' not found.");
        }
        return true;
    }

    private boolean handleAdminStop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("andromedagames.admin.koth.stop")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to force stop KoTH games.");
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
                sender.sendMessage(ChatColor.RED + "KoTH game '" + gameId + "' is not currently running or starting. Current state: " + kothGame.getGameState().name());
            }
        } else {
            sender.sendMessage(ChatColor.RED + "KoTH game '" + gameId + "' not found.");
        }
        return true;
    }

    private boolean handleAdminSetHill(CommandSender sender, String[] args) {
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
            player.sendMessage(String.format("%sHill center for KoTH game '%s%s%s' set to (%.1f, %.1f, %.1f in %s).",
                    ChatColor.GREEN, ChatColor.AQUA, gameId, ChatColor.GREEN,
                    newHillCenter.getX(), newHillCenter.getY(), newHillCenter.getZ(), newHillCenter.getWorld().getName()));
            player.sendMessage(ChatColor.YELLOW + "Configuration saved.");
        } else {
            player.sendMessage(ChatColor.RED + "KoTH game '" + gameId + "' not found.");
        }
        return true;
    }

    private boolean handleAdminSetRadius(CommandSender sender, String[] args) {
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
            sender.sendMessage(ChatColor.RED + "Invalid radius: '" + args[1] + "'. Must be a whole number.");
            return true;
        }
        if (radius <= 0) {
            sender.sendMessage(ChatColor.RED + "Radius must be a positive integer > 0.");
            return true;
        }
        Optional<Game> gameOpt = gameManager.getGame(gameId);
        if (gameOpt.isPresent() && gameOpt.get() instanceof KoTHGame) {
            KoTHGame kothGame = (KoTHGame) gameOpt.get();
            kothGame.setHillRadius(radius);
            sender.sendMessage(String.format("%sHill radius for KoTH game '%s%s%s' set to %s%d%s.",
                    ChatColor.GREEN, ChatColor.AQUA, gameId, ChatColor.GREEN,
                    ChatColor.YELLOW, radius, ChatColor.GREEN));
            sender.sendMessage(ChatColor.YELLOW + "Configuration saved.");
        } else {
            sender.sendMessage(ChatColor.RED + "KoTH game '" + gameId + "' not found.");
        }
        return true;
    }
}
