package io.mewb.andromedaGames.player;

import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;

public class PlayerState {
    private final ItemStack[] inventory;
    private final ItemStack[] armor;
    private final Collection<PotionEffect> effects;
    private final double health;
    private final double maxHealth; // Added to store max health
    private final int foodLevel;
    private final float saturation;
    private final float exhaustion;
    private final int level;
    private final float exp;
    private final GameMode gameMode;
    private final boolean isFlying;
    private final boolean allowFlight;


    public PlayerState(ItemStack[] inventory, ItemStack[] armor, Collection<PotionEffect> effects,
                       double health, double maxHealth, int foodLevel, float saturation, float exhaustion,
                       int level, float exp, GameMode gameMode, boolean isFlying, boolean allowFlight) {
        this.inventory = inventory;
        this.armor = armor;
        this.effects = effects;
        this.health = health;
        this.maxHealth = maxHealth;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.exhaustion = exhaustion;
        this.level = level;
        this.exp = exp;
        this.gameMode = gameMode;
        this.isFlying = isFlying;
        this.allowFlight = allowFlight;
    }

    // Getters for all fields
    public ItemStack[] getInventory() { return inventory; }
    public ItemStack[] getArmor() { return armor; }
    public Collection<PotionEffect> getEffects() { return effects; }
    public double getHealth() { return health; }
    public double getMaxHealth() { return maxHealth; }
    public int getFoodLevel() { return foodLevel; }
    public float getSaturation() { return saturation; }
    public float getExhaustion() { return exhaustion; }
    public int getLevel() { return level; }
    public float getExp() { return exp; }
    public GameMode getGameMode() { return gameMode; }
    public boolean isFlying() { return isFlying; }
    public boolean isAllowFlight() { return allowFlight; }
}
