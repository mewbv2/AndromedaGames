package io.mewb.andromedaGames.command;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.arena.ArenaDefinition;
import io.mewb.andromedaGames.game.GameManager;
import io.mewb.andromedaGames.utils.RelativeLocation;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
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
import java.util.Map;
import java.util.stream.Collectors;

public class ArenaAdminCommand implements CommandExecutor, TabCompleter {

    private final AndromedaGames plugin;
    private final GameManager gameManager;

    public ArenaAdminCommand(AndromedaGames plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendArenaAdminHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        // args for the sub-subcommand
        String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        switch (subCommand) {
            case "setup":
                return handleSetup(sender, subArgs);
            case "setrelloc":
                return handleSetRelLoc(sender, subArgs);
            case "listrellocs":
                return handleListRelLocs(sender, subArgs);
            case "delrelloc":
                return handleDeleteRelLoc(sender, subArgs);
            case "tptorelloc":
                return handleTpToRelLoc(sender, subArgs);
            case "save":
                return handleSave(sender, subArgs);
            case "finish":
                return handleFinish(sender, subArgs);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown /ag arena subcommand: " + subCommand);
                sendArenaAdminHelp(sender);
                return true;
        }
    }

    private void sendArenaAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- AndromedaGames Arena Admin ---");
        sender.sendMessage(ChatColor.AQUA + "/ag arena setup <arena_id> [world_name]" + ChatColor.GRAY + " - Start setup session for an arena.");
        if (sender instanceof Player && gameManager.isAdminInSetupMode((Player) sender)) {
            sender.sendMessage(ChatColor.YELLOW + "--- Current Setup Session Commands ---");
            sender.sendMessage(ChatColor.AQUA + "/ag arena setrelloc <key> [index]" + ChatColor.GRAY + " - Set a relative location.");
            sender.sendMessage(ChatColor.AQUA + "/ag arena listrellocs" + ChatColor.GRAY + " - List currently set locations.");
            sender.sendMessage(ChatColor.AQUA + "/ag arena delrelloc <key> [index]" + ChatColor.GRAY + " - Delete a location.");
            sender.sendMessage(ChatColor.AQUA + "/ag arena tptorelloc <key> [index]" + ChatColor.GRAY + " - Teleport to a set location.");
            sender.sendMessage(ChatColor.AQUA + "/ag arena save" + ChatColor.GRAY + " - Save current locations to arena file.");
            sender.sendMessage(ChatColor.AQUA + "/ag arena finish" + ChatColor.GRAY + " - Finish setup session (clears temp arena).");
        } else {
            sender.sendMessage(ChatColor.GRAY + "(More commands available once in setup mode)");
        }
    }

    private boolean handleSetup(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
            return true;
        }
        if (!sender.hasPermission("andromedagames.admin.arena.setup")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission for arena setup.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /ag arena setup <arena_id> [world_name]");
            return true;
        }
        String arenaId = args[0];
        String worldNameOverride = (args.length > 1) ? args[1] : null;

        if (gameManager.startArenaSetupSession((Player) sender, arenaId, worldNameOverride)) {
            // Message handled by GameManager
        } else {
            // Message handled by GameManager
        }
        return true;
    }

    private boolean handleSetRelLoc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("andromedagames.admin.arena.setlocation")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to set locations.");
            return true;
        }
        if (!gameManager.isAdminInSetupMode(player)) {
            player.sendMessage(ChatColor.RED + "You are not in arena setup mode. Use '/ag arena setup <arena_id>'.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /ag arena setrelloc <location_key> [index]");
            return true;
        }
        String key = args[0];
        Integer index = null;
        if (args.length > 1) {
            try {
                index = Integer.parseInt(args[1]);
                if (index < 0) {
                    player.sendMessage(ChatColor.RED + "Index must be a non-negative number.");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid index: '" + args[1] + "'. Must be a number.");
                return true;
            }
        }

        RelativeLocation relLoc = gameManager.calculateRelativeLocationForAdmin(player);
        if (relLoc == null) {
            player.sendMessage(ChatColor.RED + "Error calculating relative location. Are you in setup mode correctly?");
            return true;
        }

        gameManager.setRelativeLocationInSetup(player, key, relLoc, index);
        // Feedback message handled by GameManager method
        return true;
    }

    private boolean handleListRelLocs(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("andromedagames.admin.arena.setlocation")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to list locations.");
            return true;
        }
        if (!gameManager.isAdminInSetupMode(player)) {
            player.sendMessage(ChatColor.RED + "You are not in arena setup mode.");
            return true;
        }

        Map<String, Object> locations = gameManager.getSetupRelativeLocations(player);
        if (locations.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No relative locations defined in this session yet for arena '" + gameManager.getCurrentSetupArenaId(player) + "'.");
            return true;
        }

        player.sendMessage(ChatColor.GOLD + "--- Current Relative Locations for Arena: " + gameManager.getCurrentSetupArenaId(player) + " ---");
        for (Map.Entry<String, Object> entry : locations.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof RelativeLocation) {
                player.sendMessage(ChatColor.AQUA + key + ": " + ChatColor.WHITE + ((RelativeLocation) value).toString());
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                player.sendMessage(ChatColor.AQUA + key + " (List - " + list.size() + " entries):");
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) instanceof RelativeLocation) {
                        player.sendMessage(ChatColor.GRAY + "  [" + i + "] " + ChatColor.WHITE + ((RelativeLocation) list.get(i)).toString());
                    } else {
                        player.sendMessage(ChatColor.GRAY + "  [" + i + "] " + ChatColor.RED + "Invalid data");
                    }
                }
            }
        }
        return true;
    }
    private boolean handleDeleteRelLoc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("andromedagames.admin.arena.setlocation")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to delete locations.");
            return true;
        }
        if (!gameManager.isAdminInSetupMode(player)) {
            player.sendMessage(ChatColor.RED + "You are not in arena setup mode.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /ag arena delrelloc <location_key> [index]");
            return true;
        }
        String key = args[0];
        Integer index = null;
        if (args.length > 1) {
            try {
                index = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid index: '" + args[1] + "'. Must be a number.");
                return true;
            }
        }
        gameManager.deleteRelativeLocationInSetup(player, key, index);
        return true;
    }

    private boolean handleTpToRelLoc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("andromedagames.admin.arena.setlocation")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to teleport to locations.");
            return true;
        }
        if (!gameManager.isAdminInSetupMode(player)) {
            player.sendMessage(ChatColor.RED + "You are not in arena setup mode.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /ag arena tptorelloc <location_key> [index]");
            return true;
        }
        String key = args[0];
        Integer index = null;
        if (args.length > 1) {
            try {
                index = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid index: '" + args[1] + "'. Must be a number.");
                return true;
            }
        }
        Location targetLoc = gameManager.getAbsoluteFromSetupRelLoc(player, key, index);
        if (targetLoc != null) {
            player.teleport(targetLoc);
            player.sendMessage(ChatColor.GREEN + "Teleported to " + key + (index != null ? "["+index+"]" : "") + ".");
        } else {
            player.sendMessage(ChatColor.RED + "Location " + key + (index != null ? "["+index+"]" : "") + " not found or invalid.");
        }
        return true;
    }


    private boolean handleSave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("andromedagames.admin.arena.save")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to save arena configurations.");
            return true;
        }
        if (!gameManager.isAdminInSetupMode(player)) {
            player.sendMessage(ChatColor.RED + "You are not in arena setup mode.");
            return true;
        }
        gameManager.saveSetupLocationsToArenaFile(player);
        // Message handled by GameManager
        return true;
    }

    private boolean handleFinish(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("andromedagames.admin.arena.setup")) { // Same perm as setup to finish
            player.sendMessage(ChatColor.RED + "You don't have permission to finish arena setup.");
            return true;
        }
        // GameManager handles if not in setup mode
        gameManager.finishArenaSetupSession(player, false); // false means not a forced finish (e.g. by quit event)
        // Message handled by GameManager
        return true;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player)) return completions;
        Player player = (Player) sender;

        List<String> subcommands = new ArrayList<>(Arrays.asList("setup"));
        if (gameManager.isAdminInSetupMode(player)) {
            subcommands.addAll(Arrays.asList("setrelloc", "listrellocs", "delrelloc", "tptorelloc", "save", "finish"));
        }

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], subcommands, completions);
        } else if (args.length >= 2) {
            String subCmd = args[0].toLowerCase();
            if (subCmd.equals("setup") && args.length == 2) {
                if (sender.hasPermission("andromedagames.admin.arena.setup")) {
                    // Suggest arena IDs
                    StringUtil.copyPartialMatches(args[1],
                            gameManager.getAllArenaDefinitions().stream().map(ArenaDefinition::getArenaId).collect(Collectors.toList()),
                            completions);
                }
            } else if (subCmd.equals("setup") && args.length == 3) {
                if (sender.hasPermission("andromedagames.admin.arena.setup")) {
                    // Suggest world names
                    StringUtil.copyPartialMatches(args[2],
                            plugin.getServer().getWorlds().stream().map(World::getName).collect(Collectors.toList()),
                            completions);
                }
            } else if (gameManager.isAdminInSetupMode(player) && (subCmd.equals("setrelloc") || subCmd.equals("delrelloc") || subCmd.equals("tptorelloc"))) {
                if (args.length == 2) { // Suggest existing location keys
                    if (sender.hasPermission("andromedagames.admin.arena.setlocation")) {
                        StringUtil.copyPartialMatches(args[1],
                                gameManager.getSetupRelativeLocations(player).keySet(),
                                completions);
                    }
                } else if (args.length == 3) { // Suggest index for lists
                    Object locData = gameManager.getSetupRelativeLocations(player).get(args[1]);
                    if (locData instanceof List) {
                        List<?> list = (List<?>) locData;
                        List<String> indices = new ArrayList<>();
                        for (int i = 0; i < list.size(); i++) indices.add(String.valueOf(i));
                        if (subCmd.equals("setrelloc")) indices.add(String.valueOf(list.size())); // Allow appending
                        StringUtil.copyPartialMatches(args[2], indices, completions);
                    }
                }
            }
        }
        Collections.sort(completions);
        return completions;
    }
}