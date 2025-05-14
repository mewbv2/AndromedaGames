package io.mewb.andromedaGames.voting;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.game.Game;
import io.mewb.andromedaGames.game.GameState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class VoteManager {

    private final AndromedaGames plugin;
    private final Game game; // The game instance this vote manager is tied to
    private final Logger logger;

    private List<VotingHook> currentVoteOptions;
    private Map<UUID, Integer> playerVotes; // Player UUID -> Voted Option Index (0-based)
    private boolean isVoteActive = false;
    private BukkitTask voteTimerTask;
    private int voteDurationSeconds;

    public VoteManager(AndromedaGames plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
        this.logger = plugin.getLogger();
        this.playerVotes = new HashMap<>();
        this.currentVoteOptions = new ArrayList<>();
    }

    /**
     * Starts a new voting session.
     * @param availableHooks A list of VotingHook options to present to players.
     * @param durationSeconds How long the voting period should last.
     * @return True if the vote started successfully, false otherwise (e.g., vote already active, no hooks).
     */
    public boolean startVote(List<VotingHook> availableHooks, int durationSeconds) {
        if (isVoteActive) {
            logger.warning("[" + game.getGameId() + "] Attempted to start a vote while one is already active.");
            return false;
        }
        if (availableHooks == null || availableHooks.isEmpty() || availableHooks.size() < 2) { // Need at least 2 options for a meaningful vote
            logger.warning("[" + game.getGameId() + "] Attempted to start a vote with insufficient options (" + (availableHooks == null ? 0 : availableHooks.size()) + ").");
            return false;
        }
        if (game.getGameState() != GameState.ACTIVE) {
            logger.warning("[" + game.getGameId() + "] Attempted to start a vote but the game is not active.");
            return false;
        }

        this.currentVoteOptions = new ArrayList<>(availableHooks); // Take a copy
        // Optionally shuffle or select a subset if more hooks are provided than can be displayed
        // For now, assume we display all provided (up to a reasonable limit, e.g., 3-5)
        if (this.currentVoteOptions.size() > 5) { // Let's cap at 5 options for display sanity
            Collections.shuffle(this.currentVoteOptions);
            this.currentVoteOptions = this.currentVoteOptions.subList(0, 5);
        }

        this.playerVotes.clear();
        this.isVoteActive = true;
        this.voteDurationSeconds = durationSeconds;

        game.broadcastToGamePlayers(ChatColor.GOLD + "--- VOTE! ---");
        game.broadcastToGamePlayers(ChatColor.YELLOW + "What happens next? You have " + durationSeconds + " seconds to vote!");
        for (int i = 0; i < currentVoteOptions.size(); i++) {
            VotingHook hook = currentVoteOptions.get(i);
            game.broadcastToGamePlayers(ChatColor.AQUA + "" + (i + 1) + ". " + ChatColor.BOLD + hook.getDisplayName() + ChatColor.RESET + ChatColor.GRAY + " - " + hook.getDescription());
        }
        game.broadcastToGamePlayers(ChatColor.YELLOW + "Type " + ChatColor.GREEN + "/vote <number>" + ChatColor.YELLOW + " to cast your vote!");
        playVoteStartSoundToPlayers();


        // Start the timer to end the vote
        AtomicInteger timeLeft = new AtomicInteger(durationSeconds);
        voteTimerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!isVoteActive || game.getGameState() != GameState.ACTIVE) {
                endVote(false); // Game ended or vote cancelled externally
                return;
            }

            if (timeLeft.get() % 10 == 0 && timeLeft.get() > 0 && timeLeft.get() < durationSeconds) { // Announce every 10s, not at start/end
                game.broadcastToGamePlayers(ChatColor.YELLOW + "Vote ends in " + timeLeft.get() + " seconds! Type " + ChatColor.GREEN + "/vote <number>");
            }

            if (timeLeft.decrementAndGet() <= 0) {
                endVote(true); // Time's up, tally votes
            }
        }, 20L, 20L); // Check every second

        logger.info("[" + game.getGameId() + "] Vote started with options: " +
                currentVoteOptions.stream().map(VotingHook::getDisplayName).collect(Collectors.joining(", ")));
        return true;
    }

    /**
     * Allows a player to cast their vote.
     * @param player The player voting.
     * @param optionNumber The 1-based number of the option they are voting for.
     * @return True if the vote was successfully cast, false otherwise.
     */
    public boolean castVote(Player player, int optionNumber) {
        if (!isVoteActive) {
            player.sendMessage(ChatColor.RED + "There is no active vote right now.");
            return false;
        }
        if (!game.isPlayerInGame(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You must be in the game to vote.");
            return false;
        }
        if (optionNumber < 1 || optionNumber > currentVoteOptions.size()) {
            player.sendMessage(ChatColor.RED + "Invalid vote option. Please choose a number between 1 and " + currentVoteOptions.size() + ".");
            return false;
        }

        int optionIndex = optionNumber - 1; // Convert to 0-based index
        if (playerVotes.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "You have changed your vote to: " + ChatColor.AQUA + currentVoteOptions.get(optionIndex).getDisplayName());
        } else {
            player.sendMessage(ChatColor.GREEN + "You voted for: " + ChatColor.AQUA + currentVoteOptions.get(optionIndex).getDisplayName());
        }
        playerVotes.put(player.getUniqueId(), optionIndex);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
        return true;
    }

    /**
     * Ends the current voting session, tallies votes, and applies the winning hook.
     * @param announceWinner Whether to announce the winner and apply the hook.
     */
    public void endVote(boolean announceWinner) {
        if (!isVoteActive) {
            return;
        }
        isVoteActive = false;
        if (voteTimerTask != null && !voteTimerTask.isCancelled()) {
            voteTimerTask.cancel();
        }
        voteTimerTask = null;

        if (!announceWinner || currentVoteOptions.isEmpty()) {
            logger.info("[" + game.getGameId() + "] Vote ended without tallying or options were empty.");
            game.broadcastToGamePlayers(ChatColor.YELLOW + "The vote has ended.");
            currentVoteOptions.clear();
            playerVotes.clear();
            return;
        }

        // Tally votes
        int[] voteCounts = new int[currentVoteOptions.size()];
        for (Integer votedIndex : playerVotes.values()) {
            if (votedIndex >= 0 && votedIndex < voteCounts.length) {
                voteCounts[votedIndex]++;
            }
        }

        int winningIndex = -1;
        int maxVotes = -1;
        List<Integer> tiedIndices = new ArrayList<>();

        for (int i = 0; i < voteCounts.length; i++) {
            if (voteCounts[i] > maxVotes) {
                maxVotes = voteCounts[i];
                winningIndex = i;
                tiedIndices.clear();
                tiedIndices.add(i);
            } else if (voteCounts[i] == maxVotes) {
                tiedIndices.add(i);
            }
        }

        VotingHook winningHook;
        if (winningIndex == -1) { // No votes cast
            game.broadcastToGamePlayers(ChatColor.YELLOW + "No votes were cast! Choosing a random event...");
            winningIndex = (int) (Math.random() * currentVoteOptions.size());
            winningHook = currentVoteOptions.get(winningIndex);
        } else if (tiedIndices.size() > 1) { // Tie occurred
            game.broadcastToGamePlayers(ChatColor.YELLOW + "It's a tie! Choosing randomly among tied options...");
            winningIndex = tiedIndices.get((int) (Math.random() * tiedIndices.size()));
            winningHook = currentVoteOptions.get(winningIndex);
        } else {
            winningHook = currentVoteOptions.get(winningIndex);
        }

        game.broadcastToGamePlayers(ChatColor.GOLD + "Vote ended! Result: " + ChatColor.AQUA + ChatColor.BOLD + winningHook.getDisplayName() + ChatColor.GOLD + " with " + maxVotes + " vote(s)!");
        playVoteEndSoundToPlayers();

        // Gather list of players who voted for the winning hook (optional, for the hook's use)
        List<Player> votersForWinningHook = new ArrayList<>();
        final int finalWinningIndex = winningIndex; // Effectively final for lambda
        playerVotes.entrySet().stream()
                .filter(entry -> entry.getValue() == finalWinningIndex)
                .map(entry -> Bukkit.getPlayer(entry.getKey()))
                .filter(p -> p != null && p.isOnline())
                .forEach(votersForWinningHook::add);

        // Apply the winning hook
        try {
            if (winningHook.canApply(game)) {
                winningHook.apply(game, votersForWinningHook);
                logger.info("[" + game.getGameId() + "] Applied winning voting hook: " + winningHook.getDisplayName());
            } else {
                logger.warning("[" + game.getGameId() + "] Winning hook '" + winningHook.getDisplayName() + "' reported it cannot be applied currently.");
                game.broadcastToGamePlayers(ChatColor.RED + "Unfortunately, " + winningHook.getDisplayName() + " couldn't be activated right now.");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[" + game.getGameId() + "] Error applying voting hook '" + winningHook.getId() + "': " + e.getMessage(), e);
            game.broadcastToGamePlayers(ChatColor.RED + "An error occurred while activating the event.");
        }

        currentVoteOptions.clear();
        playerVotes.clear();
    }

    public boolean isVoteActive() {
        return isVoteActive;
    }

    private void playVoteStartSoundToPlayers() {
        for (UUID uuid : game.getPlayersInGame()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            }
        }
    }
    private void playVoteEndSoundToPlayers() {
        for (UUID uuid : game.getPlayersInGame()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
            }
        }
    }
}