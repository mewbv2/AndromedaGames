package io.mewb.andromedaGames.voting;

import io.mewb.andromedaGames.AndromedaGames;
import io.mewb.andromedaGames.game.GameInstance; // Changed from Game to GameInstance
import io.mewb.andromedaGames.game.GameState;   // Assuming GameInstance has getGameState()
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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
    private final GameInstance game; // Changed from Game to GameInstance
    private final Logger logger;

    private List<VotingHook> currentVoteOptions;
    private Map<UUID, Integer> playerVotes; // Player UUID -> Voted Option Index (0-based)
    private boolean isVoteActive = false;
    private BukkitTask voteTimerTask;
    private int voteDurationSeconds;

    public VoteManager(AndromedaGames plugin, GameInstance game) { // Constructor updated
        this.plugin = plugin;
        this.game = game;
        this.logger = plugin.getLogger(); // Assuming plugin.getLogger() is accessible
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
            logger.warning("[" + game.getDefinition().getDefinitionId() + "-Instance:" + game.getInstanceId().toString().substring(0,8) + "] Attempted to start a vote while one is already active.");
            return false;
        }
        if (availableHooks == null || availableHooks.isEmpty() || availableHooks.size() < 2) {
            logger.warning("[" + game.getDefinition().getDefinitionId() + "-Instance:" + game.getInstanceId().toString().substring(0,8) + "] Attempted to start a vote with insufficient options (" + (availableHooks == null ? 0 : availableHooks.size()) + ").");
            return false;
        }
        if (game.getGameState() != GameState.ACTIVE) {
            logger.warning("[" + game.getDefinition().getDefinitionId() + "-Instance:" + game.getInstanceId().toString().substring(0,8) + "] Attempted to start a vote but the game is not active.");
            return false;
        }

        this.currentVoteOptions = new ArrayList<>(availableHooks);
        if (this.currentVoteOptions.size() > 5) {
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


        AtomicInteger timeLeft = new AtomicInteger(durationSeconds);
        voteTimerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!isVoteActive || game.getGameState() != GameState.ACTIVE) {
                endVote(false);
                return;
            }

            if (timeLeft.get() % 10 == 0 && timeLeft.get() > 0 && timeLeft.get() < durationSeconds) {
                game.broadcastToGamePlayers(ChatColor.YELLOW + "Vote ends in " + timeLeft.get() + " seconds! Type " + ChatColor.GREEN + "/vote <number>");
            }

            if (timeLeft.decrementAndGet() <= 0) {
                endVote(true);
            }
        }, 20L, 20L);

        logger.info("[" + game.getDefinition().getDefinitionId() + "-Instance:" + game.getInstanceId().toString().substring(0,8) + "] Vote started with options: " +
                currentVoteOptions.stream().map(VotingHook::getDisplayName).collect(Collectors.joining(", ")));
        return true;
    }

    public boolean castVote(Player player, int optionNumber) {
        if (!isVoteActive) {
            player.sendMessage(ChatColor.RED + "There is no active vote right now.");
            return false;
        }
        // GameInstance should have isPlayerInGame
        if (!game.isPlayerInGame(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You must be in the game to vote.");
            return false;
        }
        if (optionNumber < 1 || optionNumber > currentVoteOptions.size()) {
            player.sendMessage(ChatColor.RED + "Invalid vote option. Please choose a number between 1 and " + currentVoteOptions.size() + ".");
            return false;
        }

        int optionIndex = optionNumber - 1;
        if (playerVotes.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "You have changed your vote to: " + ChatColor.AQUA + currentVoteOptions.get(optionIndex).getDisplayName());
        } else {
            player.sendMessage(ChatColor.GREEN + "You voted for: " + ChatColor.AQUA + currentVoteOptions.get(optionIndex).getDisplayName());
        }
        playerVotes.put(player.getUniqueId(), optionIndex);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.5f);
        return true;
    }

    public void endVote(boolean announceWinner) {
        if (!isVoteActive) {
            return;
        }
        isVoteActive = false;
        if (voteTimerTask != null && !voteTimerTask.isCancelled()) {
            voteTimerTask.cancel();
        }
        voteTimerTask = null;

        String gameInstanceContext = "[" + game.getDefinition().getDefinitionId() + "-Instance:" + game.getInstanceId().toString().substring(0,8) + "]";

        if (!announceWinner || currentVoteOptions.isEmpty()) {
            logger.info(gameInstanceContext + " Vote ended without tallying or options were empty.");
            game.broadcastToGamePlayers(ChatColor.YELLOW + "The vote has ended.");
            currentVoteOptions.clear();
            playerVotes.clear();
            return;
        }

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
        if (winningIndex == -1) {
            game.broadcastToGamePlayers(ChatColor.YELLOW + "No votes were cast! Choosing a random event...");
            winningIndex = (int) (Math.random() * currentVoteOptions.size());
            winningHook = currentVoteOptions.get(winningIndex);
        } else if (tiedIndices.size() > 1) {
            game.broadcastToGamePlayers(ChatColor.YELLOW + "It's a tie! Choosing randomly among tied options...");
            winningIndex = tiedIndices.get((int) (Math.random() * tiedIndices.size()));
            winningHook = currentVoteOptions.get(winningIndex);
        } else {
            winningHook = currentVoteOptions.get(winningIndex);
        }

        game.broadcastToGamePlayers(ChatColor.GOLD + "Vote ended! Result: " + ChatColor.AQUA + ChatColor.BOLD + winningHook.getDisplayName() + ChatColor.GOLD + " with " + maxVotes + " vote(s)!");
        playVoteEndSoundToPlayers();

        List<Player> votersForWinningHook = new ArrayList<>();
        final int finalWinningIndex = winningIndex;
        playerVotes.entrySet().stream()
                .filter(entry -> entry.getValue() == finalWinningIndex)
                .map(entry -> Bukkit.getPlayer(entry.getKey()))
                .filter(p -> p != null && p.isOnline())
                .forEach(votersForWinningHook::add);

        try {
            if (winningHook.canApply(game)) { // Pass GameInstance
                winningHook.apply(game, votersForWinningHook); // Pass GameInstance
                logger.info(gameInstanceContext + " Applied winning voting hook: " + winningHook.getDisplayName());
                game.setActiveVotingHook(winningHook); // Inform the game instance
            } else {
                logger.warning(gameInstanceContext + " Winning hook '" + winningHook.getDisplayName() + "' reported it cannot be applied currently.");
                game.broadcastToGamePlayers(ChatColor.RED + "Unfortunately, " + winningHook.getDisplayName() + " couldn't be activated right now.");
                game.setActiveVotingHook(null); // Ensure no hook is considered active
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, gameInstanceContext + " Error applying voting hook '" + winningHook.getId() + "': " + e.getMessage(), e);
            game.broadcastToGamePlayers(ChatColor.RED + "An error occurred while activating the event.");
            game.setActiveVotingHook(null);
        }

        currentVoteOptions.clear();
        playerVotes.clear();
    }

    public boolean isVoteActive() {
        return isVoteActive;
    }

    private void playVoteStartSoundToPlayers() {
        // GameInstance should have getPlayersInGame()
        for (UUID uuid : game.getPlayersInGame()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 1.0f, 1.2f);
            }
        }
    }
    private void playVoteEndSoundToPlayers() {
        for (UUID uuid : game.getPlayersInGame()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.0f);
            }
        }
    }
}
