package io.mewb.andromedaGames.voting;

import io.mewb.andromedaGames.game.GameInstance;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Represents a votable event or modifier that can occur during a game.
 */
public interface VotingHook {

    /**
     * A unique identifier for this voting hook (e.g., "tnt_drop", "low_gravity").
     * Used internally and potentially in configurations.
     * @return The unique ID string.
     */
    String getId();

    /**
     * A short, user-friendly name for this hook, displayed to players during voting.
     * @return The display name.
     */
    String getDisplayName();

    /**
     * A brief description of what this hook does, also for player display.
     * @return The description.
     */
    String getDescription();

    /**
     * Applies the effect of this voting hook to the specified game.
     * @param game The game instance to which the hook's effect should be applied.
     * @param voters (Optional) A list of players who voted for this option, if needed by the hook.
     */
    void apply(GameInstance game, List<Player> voters);

    /**
     * (Optional) Checks if this voting hook can currently be applied to the given game.
     * For instance, some hooks might not make sense if the game is almost over,
     * or if a similar hook is already active.
     * @param game The game instance to check against.
     * @return True if the hook can be applied, false otherwise. Defaults to true.
     */
    default boolean canApply(GameInstance game) {
        return true;
    }

    /**
     * (Optional) Specifies the duration of the hook's effect in seconds.
     * A value of 0 or less might indicate an instantaneous event or one that modifies game rules permanently
     * until another hook changes them or the game ends.
     * @return Duration in seconds. Defaults to 0 (instantaneous or permanent).
     */
    default int getDurationSeconds() {
        return 0;
    }
}
