package io.mewb.andromedaGames.infection.votinghooks;

import io.mewb.andromedaGames.game.GameInstance;
import io.mewb.andromedaGames.infection.InfectionGame;
import io.mewb.andromedaGames.utils.ParticleUtil;
import io.mewb.andromedaGames.voting.VotingHook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;

public class RevealSurvivorsHook implements VotingHook {

    private static final int REVEAL_DURATION_SECONDS = 7;

    @Override
    public String getId() {
        return "infection_reveal_survivors";
    }

    @Override
    public String getDisplayName() {
        return "Survivor Scan!";
    }

    @Override
    public String getDescription() {
        return "Briefly reveals the locations of all survivors to the infected!";
    }

    @Override
    public int getDurationSeconds() {
        return REVEAL_DURATION_SECONDS;
    }

    @Override
    public boolean canApply(GameInstance game) {
        // Ensure it's an InfectionGame and there are survivors to reveal and infected to reveal to.
        if (!(game instanceof InfectionGame)) {
            return false;
        }
        InfectionGame infectionGame = (InfectionGame) game;
        return !infectionGame.getModifiableSurvivorPlayers().isEmpty() && !infectionGame.getModifiableInfectedPlayers().isEmpty();
    }

    @Override
    public void apply(GameInstance game, List<Player> voters) {
        if (!(game instanceof InfectionGame)) {
            game.plugin.getLogger().warning(getId() + " applied to non-InfectionGame instance: " + game.getInstanceId());
            return;
        }
        InfectionGame infectionGame = (InfectionGame) game;

        if (infectionGame.getModifiableSurvivorPlayers().isEmpty() || infectionGame.getModifiableInfectedPlayers().isEmpty()) {
            infectionGame.broadcastToGamePlayers(ChatColor.YELLOW + getDisplayName() + " could not activate (no survivors or no infected).");
            game.plugin.getLogger().info(getId() + " for instance " + game.getInstanceId().toString().substring(0,8) + " had no targets.");
            return;
        }

        infectionGame.broadcastToGamePlayers(ChatColor.RED + "" + ChatColor.BOLD + getDisplayName() + ChatColor.YELLOW + " Survivors are being revealed to the infected!");

        PotionEffect glowingEffect = new PotionEffect(PotionEffectType.GLOWING, REVEAL_DURATION_SECONDS * 20, 0, false, true, true);

        for (UUID survivorUUID : infectionGame.getModifiableSurvivorPlayers()) { // Use the correct getter
            Player survivor = Bukkit.getPlayer(survivorUUID);
            if (survivor != null && survivor.isOnline()) {
                survivor.addPotionEffect(glowingEffect);

                if (survivor.getLocation().getWorld() != null) {
                    ParticleUtil.spawnLocationEffect(survivor.getLocation().add(0, 1, 0), Particle.WITCH, 20, 0.3, 0.5, 0.3, 0); // Changed particle
                    survivor.getLocation().getWorld().playSound(survivor.getLocation(), Sound.ENTITY_ENDERMAN_STARE, SoundCategory.HOSTILE, 0.7f, 1.5f); // Sound for survivors too
                }
            }
        }

        for (UUID infectedUUID : infectionGame.getModifiableInfectedPlayers()) { // Use the correct getter
            Player infectedPlayer = Bukkit.getPlayer(infectedUUID);
            if (infectedPlayer != null && infectedPlayer.isOnline()) {
                infectedPlayer.sendMessage(ChatColor.RED + "SURVIVOR SCAN ACTIVE! " + ChatColor.YELLOW + "Look for glowing players for " + REVEAL_DURATION_SECONDS + " seconds!");
                infectedPlayer.playSound(infectedPlayer.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.3f);
            }
        }
        // The PotionEffect will wear off on its own.
        // GameInstance.setActiveVotingHook will handle setting the active hook for scoreboard display.
    }
}