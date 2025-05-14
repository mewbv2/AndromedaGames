package io.mewb.andromedaGames.game;

public enum GameState {
    WAITING,      // Waiting for players, or in lobby
    STARTING,     // Countdown phase before game begins
    ACTIVE,       // Game is in progress
    ENDING,       // Game has finished, showing scores, before reset
    DISABLED,     // Game is not available (e.g., arena issue, admin disabled)
    RESETTING     // Arena is being reset
}