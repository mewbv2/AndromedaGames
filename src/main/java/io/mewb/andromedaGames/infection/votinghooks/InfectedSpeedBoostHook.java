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

public class InfectedSpeedBoostHook implements VotingHook {

    private static final int BOOST_DURATION_SECONDS = 15;
    private static final int SPEED_AMPLIFIER = 1; // Speed II (0 is Speed I)

    @Override
    public String getId() {
        return "infection_infected_speed_boost";
    }

    @Override
    public String getDisplayName() {
        return "Infected Frenzy!";
    }

    @Override
    public String getDescription() {
        return "All infected get a temporary speed boost!";
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
        // Can apply if there are infected players to boost
        return !infectionGame.getModifiableInfectedPlayers().isEmpty();
    }

    @Override
    public void apply(GameInstance game, List<Player> voters) {
        if (!(game instanceof InfectionGame)) {
            game.plugin.getLogger().warning(getId() + " applied to non-InfectionGame instance: " + game.getInstanceId().toString().substring(0,8));
            return;
        }
        InfectionGame infectionGame = (InfectionGame) game;

        if (infectionGame.getModifiableInfectedPlayers().isEmpty()) {
            infectionGame.broadcastToGamePlayers(ChatColor.YELLOW + getDisplayName() + " could not activate (no infected players).");
            game.plugin.getLogger().info(getId() + " for instance " + game.getInstanceId().toString().substring(0,8) + " had no infected players to target.");
            return;
        }

        infectionGame.broadcastToGamePlayers(ChatColor.RED + "" + ChatColor.BOLD + getDisplayName() + ChatColor.YELLOW + " Infected are faster for " + BOOST_DURATION_SECONDS + " seconds!");

        PotionEffect speedBoost = new PotionEffect(PotionEffectType.SPEED, BOOST_DURATION_SECONDS * 20, SPEED_AMPLIFIER, false, true, true);

        for (UUID infectedUUID : infectionGame.getModifiableInfectedPlayers()) { // Use the correct getter
            Player infected = Bukkit.getPlayer(infectedUUID);
            if (infected != null && infected.isOnline()) {
                infected.addPotionEffect(speedBoost);
                infected.playSound(infected.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, SoundCategory.PLAYERS, 1.0f, 1.3f);
                ParticleUtil.spawnPlayerStatusParticles(infected, Particle.SMOKE, 15, 0.3, 0.5, 0.3, 0.02);
            }
        }
        // PotionEffect will wear off on its own.
        // GameInstance.setActiveVotingHook handles displaying the active hook.
        game.plugin.getLogger().info(getId() + " applied to instance " + game.getInstanceId().toString().substring(0,8) + ", boosting " + infectionGame.getModifiableInfectedPlayers().size() + " infected players.");
    }
}