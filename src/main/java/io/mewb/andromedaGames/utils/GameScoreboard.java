package io.mewb.andromedaGames.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameScoreboard {

    private final Player player;
    private final Scoreboard scoreboard;
    private final Objective objective;

    // To store and update lines without flickering, we use teams for each line
    private final Map<Integer, Team> lineTeams = new HashMap<>();
    private final String[] lineEntryPlaceholders; // Invisible ChatColor sequences

    private static final int MAX_LINES = 15; // Max lines on a sidebar scoreboard (excluding title)

    /**
     * Creates a new scoreboard for a player.
     * @param player The player this scoreboard belongs to.
     * @param title The title of the scoreboard (max 32 chars).
     */
    public GameScoreboard(Player player, String title) {
        this.player = player;
        // Create a new scoreboard for the player to avoid conflicts
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

        // Register the objective for the sidebar
        this.objective = scoreboard.registerNewObjective("gameboard", "dummy", ChatColor.translateAlternateColorCodes('&', title));
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Initialize placeholders for lines
        this.lineEntryPlaceholders = new String[MAX_LINES];
        for (int i = 0; i < MAX_LINES; i++) {
            // Using unique ChatColor sequences as entry names for teams
            this.lineEntryPlaceholders[i] = ChatColor.COLOR_CHAR + "" + (i < 10 ? i : (char) ('a' + (i - 10))) + ChatColor.RESET;
            Team lineTeam = scoreboard.registerNewTeam("line" + i);
            lineTeam.addEntry(this.lineEntryPlaceholders[i]);
            lineTeams.put(i, lineTeam);
        }
    }

    /**
     * Sets or updates a specific line on the scoreboard.
     * Lines are ordered from top (higher score value) to bottom (lower score value).
     * For sidebar, higher score means lower on the list. We'll reverse this for intuitive line numbers.
     *
     * @param lineNumber The line number (0 is the top-most line under the title).
     * @param text The text to display on this line (max 32 chars for prefix/suffix combined effectively).
     */
    public void setLine(int lineNumber, String text) {
        if (lineNumber < 0 || lineNumber >= MAX_LINES) {
            return; // Invalid line number
        }

        Team team = lineTeams.get(lineNumber);
        if (team == null) return; // Should not happen

        // Bukkit scoreboard lines are limited in length.
        // We use prefix and suffix of a team to display longer lines if needed,
        // but for simplicity, we'll aim for text that fits in one part.
        // Max length for prefix/suffix is 16 chars each for older versions,
        // newer versions handle longer strings better but it's safer to be concise.
        // For FAWE/Paper, a single component can be up to 32767, but display is limited.
        // Effective visual limit is around 30-40 characters depending on client.
        String translatedText = ChatColor.translateAlternateColorCodes('&', text);

        // Split text if it's too long (simple split, can be improved)
        if (translatedText.length() > 32) { // A safe general limit for combined prefix/suffix
            if (translatedText.length() > 64) translatedText = translatedText.substring(0, 64); // Hard cap

            // A common technique is to split at 16 for prefix/suffix, but let's try a more direct approach.
            // Newer versions handle longer team prefixes/suffixes better.
            // For this example, we'll just set the prefix.
            // If text is > 16, it might get split by the client or use suffix.
            // Let's assume text is kept under a reasonable length for one line.
            // For simplicity, we'll just use setPrefix.
            // A more robust solution splits into prefix and suffix if text > 16.
            if (translatedText.length() <= 40) { // Paper supports up to 40 for prefix/suffix
                team.setPrefix(translatedText);
                team.setSuffix("");
            } else { // Basic split if really long
                team.setPrefix(translatedText.substring(0, 40));
                team.setSuffix(translatedText.substring(40, Math.min(translatedText.length(), 80)));
            }

        } else {
            team.setPrefix(translatedText);
            team.setSuffix("");
        }

        // Scores determine order: higher score = lower on list.
        // So, line 0 (top) gets score MAX_LINES, line 1 gets MAX_LINES - 1, etc.
        objective.getScore(this.lineEntryPlaceholders[lineNumber]).setScore(MAX_LINES - lineNumber);
    }

    /**
     * Clears a specific line from the scoreboard.
     * @param lineNumber The line number to clear.
     */
    public void clearLine(int lineNumber) {
        if (lineNumber < 0 || lineNumber >= MAX_LINES) {
            return;
        }
        scoreboard.resetScores(this.lineEntryPlaceholders[lineNumber]); // Remove the score
        Team team = lineTeams.get(lineNumber);
        if (team != null) {
            team.setPrefix(""); // Clear text
            team.setSuffix("");
        }
    }

    /**
     * Clears all lines on the scoreboard.
     */
    public void clearAllLines() {
        for (int i = 0; i < MAX_LINES; i++) {
            clearLine(i);
        }
    }

    /**
     * Shows the scoreboard to the player.
     */
    public void show() {
        if (player.isOnline()) {
            player.setScoreboard(this.scoreboard);
        }
    }

    /**
     * Hides the scoreboard from the player by setting their scoreboard to the main server scoreboard.
     */
    public void hide() {
        if (player.isOnline() && player.getScoreboard() == this.scoreboard) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /**
     * Removes the scoreboard and unregisters teams/objectives.
     * Call this when the player leaves the game or the scoreboard is no longer needed.
     */
    public void destroy() {
        hide(); // Ensure it's hidden first
        for (Team team : lineTeams.values()) {
            try {
                team.unregister();
            } catch (IllegalStateException e) { /* Already unregistered */ }
        }
        lineTeams.clear();
        if (objective != null) {
            try {
                objective.unregister();
            } catch (IllegalStateException e) { /* Already unregistered */ }
        }
    }

    /**
     * Updates the title of the scoreboard.
     * @param newTitle The new title (max 32 chars).
     */
    public void updateTitle(String newTitle) {
        objective.setDisplayName(ChatColor.translateAlternateColorCodes('&', newTitle));
    }
}