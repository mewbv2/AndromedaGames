package io.mewb.andromedaGames.command;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.game.GameInstance; // Changed from Game to GameInstance
import io.mewb.andromedaGames.game.GameManager;
import io.mewb.andromedaGames.voting.VoteManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class VoteCommand implements CommandExecutor, TabCompleter {

    // private final AndromedaGames plugin; // Not directly used in this command's logic anymore
    private final GameManager gameManager;

    public VoteCommand(AndromedaGames plugin, GameManager gameManager) {
        // this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("andromedagames.player.vote")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to vote.");
            return true;
        }

        // Use getPlayerGameInstance which returns Optional<GameInstance>
        Optional<GameInstance> currentGameInstanceOpt = gameManager.getPlayerGameInstance(player);

        if (currentGameInstanceOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "You are not currently in a game where you can vote.");
            return true;
        }

        GameInstance currentGameInstance = currentGameInstanceOpt.get();
        // GameInstance itself should provide access to its VoteManager
        VoteManager voteManager = currentGameInstance.getVoteManager();

        if (voteManager == null) {
            player.sendMessage(ChatColor.RED + "Voting is not enabled for your current game type or instance.");
            return true;
        }

        if (!voteManager.isVoteActive()) {
            player.sendMessage(ChatColor.RED + "There is no active vote in your game right now.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /vote <option_number>");
            // Consider having voteManager.displayVoteOptionsToPlayer(player); if you want to resend options
            return true;
        }

        int optionNumber;
        try {
            optionNumber = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "'" + args[0] + "' is not a valid number. Usage: /vote <option_number>");
            return true;
        }

        // castVote should handle messages for success/failure/invalid option
        if (!voteManager.castVote(player, optionNumber)) {
            // Player already received a message from castVote
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player) {
            Player player = (Player) sender;
            // Use getPlayerGameInstance here as well
            Optional<GameInstance> currentGameInstanceOpt = gameManager.getPlayerGameInstance(player);
            if (currentGameInstanceOpt.isPresent()) {
                GameInstance currentGameInstance = currentGameInstanceOpt.get();
                VoteManager vm = currentGameInstance.getVoteManager();
                if (vm != null && vm.isVoteActive()) {
                    // A more dynamic way would be for VoteManager to expose the number of current options.
                    // For now, suggesting 1-5 is a placeholder.
                    // Example: int numOptions = vm.getNumberOfCurrentVoteOptions();
                    // return IntStream.rangeClosed(1, numOptions).mapToObj(String::valueOf).collect(Collectors.toList());
                    return List.of("1", "2", "3", "4", "5"); // Placeholder
                }
            }
        }
        return Collections.emptyList();
    }
}