package io.mewb.andromedaGames.command;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.game.Game;
import io.mewb.andromedaGames.game.GameManager;
import io.mewb.andromedaGames.koth.KoTHGame; // To get VoteManager from KoTHGame
import io.mewb.andromedaGames.voting.VoteManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class VoteCommand implements CommandExecutor, TabCompleter {

    private final AndromedaGames plugin;
    private final GameManager gameManager;

    public VoteCommand(AndromedaGames plugin, GameManager gameManager) {
        this.plugin = plugin;
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

        Optional<Game> currentGameOpt = gameManager.getPlayerGame(player);
        if (currentGameOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "You are not currently in a game where you can vote.");
            return true;
        }

        Game currentGame = currentGameOpt.get();
        // We need a way to get the VoteManager from the Game instance.
        // For now, let's assume KoTHGame has a getVoteManager() method.
        // This might need a more generic approach if all games can have votes.
        if (!(currentGame instanceof KoTHGame)) { // Example: only KoTH games have voting for now
            player.sendMessage(ChatColor.RED + "Voting is not active in your current game type.");
            return true;
        }

        KoTHGame kothGame = (KoTHGame) currentGame;
        VoteManager voteManager = kothGame.getVoteManager(); // ASSUMPTION: KoTHGame has getVoteManager()

        if (voteManager == null || !voteManager.isVoteActive()) {
            player.sendMessage(ChatColor.RED + "There is no active vote in your game right now.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /vote <option_number>");
            // Optionally, re-display vote options if vote is active
            return true;
        }

        int optionNumber;
        try {
            optionNumber = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "'" + args[0] + "' is not a valid number. Usage: /vote <option_number>");
            return true;
        }

        voteManager.castVote(player, optionNumber); // VoteManager handles feedback messages
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player) {
            Player player = (Player) sender;
            Optional<Game> currentGameOpt = gameManager.getPlayerGame(player);
            if (currentGameOpt.isPresent() && currentGameOpt.get() instanceof KoTHGame) {
                KoTHGame kothGame = (KoTHGame) currentGameOpt.get();
                VoteManager voteManager = kothGame.getVoteManager();
                if (voteManager != null && voteManager.isVoteActive()) {
                    // Provide numbers 1, 2, 3... up to number of options
                    // This part is tricky as VoteManager doesn't directly expose option count to TabCompleter easily.
                    // For simplicity, we won't offer tab completion for vote numbers for now,
                    // or we'd need a more complex way for VoteManager to register current options.
                    // return List.of("1", "2", "3"); // Placeholder
                }
            }
        }
        return Collections.emptyList(); // No suggestions by default
    }
}
