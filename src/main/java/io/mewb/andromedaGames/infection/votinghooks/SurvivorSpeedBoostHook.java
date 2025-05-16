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

public class SurvivorSpeedBoostHook implements VotingHook {

    private static final int BOOST_DURATION_SECONDS = 15;
    private static final int SPEED_AMPLIFIER = 1; // Speed II (amplifier 0 is Speed I)

    @Override
    public String getId() {
        return "infection_survivor_speed_boost";
    }

    @Override
    public String getDisplayName() {
        return "Survivor Adrenaline!";
    }

    @Override
    public String getDescription() {
        return "All survivors get a temporary speed boost!";
    }

    @Override
    public int getDurationSeconds() {
        return BOOST_DURATION_SECONDS;
    }

    @Override
    public boolean canApply(GameInstance game) {
        if (!(game instanceof InfectionGame)) {
            return false;
        }
        InfectionGame infectionGame = (InfectionGame) game;
        // Can apply if there are survivors to boost
        return !infectionGame.getModifiableSurvivorPlayers().isEmpty();
    }

    @Override
    public void apply(GameInstance game, List<Player> voters) {
        if (!(game instanceof InfectionGame)) {
            game.plugin.getLogger().warning(getId() + " applied to non-InfectionGame instance: " + game.getInstanceId().toString().substring(0,8));
            return;
        }
        InfectionGame infectionGame = (InfectionGame) game;

        if (infectionGame.getModifiableSurvivorPlayers().isEmpty()) {
            infectionGame.broadcastToGamePlayers(ChatColor.YELLOW + getDisplayName() + " could not activate (no survivors).");
            game.plugin.getLogger().info(getId() + " for instance " + game.getInstanceId().toString().substring(0,8) + " had no survivor players to target.");
            return;
        }

        infectionGame.broadcastToGamePlayers(ChatColor.GREEN + "" + ChatColor.BOLD + getDisplayName() + ChatColor.YELLOW + " Survivors are faster for " + BOOST_DURATION_SECONDS + " seconds!");

        PotionEffect speedBoost = new PotionEffect(PotionEffectType.SPEED, BOOST_DURATION_SECONDS * 20, SPEED_AMPLIFIER, false, true, true);

        for (UUID survivorUUID : infectionGame.getModifiableSurvivorPlayers()) { // Use the correct getter
            Player survivor = Bukkit.getPlayer(survivorUUID);
            if (survivor != null && survivor.isOnline()) {
                survivor.addPotionEffect(speedBoost);
                survivor.playSound(survivor.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 0.8f, 1.2f);
                ParticleUtil.spawnPlayerStatusParticles(survivor, Particle.CLOUD, 15, 0.3, 0.5, 0.3, 0.01);
            }
        }
        game.plugin.getLogger().info(getId() + " applied to instance " + game.getInstanceId().toString().substring(0,8) + ", boosting " + infectionGame.getModifiableSurvivorPlayers().size() + " survivors.");
    }
}