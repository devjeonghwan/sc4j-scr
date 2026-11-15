package org.fogofwarlabs.sc4j.scr.game.modifier;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.modifier.Stat;
import org.fogofwarlabs.sc4j.scr.data.type.ScrWeaponTypeView;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * Adds the damage bonus of each level of the weapon's damage upgrade to its damage ({@link Stat#WEAPON_DAMAGE}, without fraction bits). The level
 * is read through the game's {@link PlayerUpgrades}, so a damage upgrade past the upgrade table reads the game-context byte the level layout
 * addresses.
 */
public final class ScrWeaponDamageModifier extends ScrStatModifier {
    private final @NotNull Game game;

    /**
     * Creates a {@link ScrWeaponDamageModifier} instance for one game.
     *
     * @param game the game whose upgrade levels and weapon types the modifier reads
     */
    public ScrWeaponDamageModifier(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Applies this modifier to a value, on the native values.
     *
     * @param player          the player slot index of the attacking player
     * @param unitTypeIndex   unused, {@link org.fogofwarlabs.sc4j.engine.content.type.ContentType#NO_INDEX}
     * @param weaponTypeIndex the index of the weapon type, its weapons.dat entry
     * @param value           the damage amount of the weapon type
     * @return the damage with the upgrade bonus
     * @throws NullPointerException          if the world was built without game data
     * @throws UnsupportedOperationException if the player upgrade levels are missing
     */
    @Override
    public int applyNative(int player, int unitTypeIndex, int weaponTypeIndex, int value) {
        ScrWeaponTypeView weapons = this.game.requireGameData().getWeaponTypes();
        PlayerUpgrades upgrades = this.game.getPlayerTable().getPlayerUpgrades();

        if (upgrades == null) {
            throw Exceptions.newUnsupportedOperationException("Weapon damage requires player upgrade levels.");
        }

        int level = upgrades.getLevel(player, weapons.getDamageUpgrade(weaponTypeIndex));

        return value + weapons.getDamageBonus(weaponTypeIndex) * level;
    }
}
