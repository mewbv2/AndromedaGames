package io.mewb.andromedaGames.capturetheshard;

import org.bukkit.ChatColor;

public enum TeamColor {
    RED(ChatColor.RED, "&cRed Team"),
    BLUE(ChatColor.BLUE, "&9Blue Team");
    // We can add GREEN, YELLOW, etc., later if we support more than 2 teams.

    private final ChatColor chatColor;
    private final String defaultDisplayName;

    TeamColor(ChatColor chatColor, String defaultDisplayName) {
        this.chatColor = chatColor;
        this.defaultDisplayName = defaultDisplayName;
    }

    public ChatColor getChatColor() {
        return chatColor;
    }

    public String getDefaultDisplayName() {
        return defaultDisplayName;
    }

    /**
     * Gets the display name with color codes translated.
     * @return The colored display name.
     */
    public String getFormattedDisplayName() {
        return ChatColor.translateAlternateColorCodes('&', this.defaultDisplayName);
    }

    /**
     * Gets the opposing team's color.
     * Assumes a two-team setup (RED vs BLUE).
     * @return The opposing TeamColor, or null if not applicable (e.g. more than 2 teams without specific logic).
     */
    public TeamColor getOpposite() {
        if (this == RED) {
            return BLUE;
        } else if (this == BLUE) {
            return RED;
        }
        return null; // Should not happen in a 2-team game
    }
}