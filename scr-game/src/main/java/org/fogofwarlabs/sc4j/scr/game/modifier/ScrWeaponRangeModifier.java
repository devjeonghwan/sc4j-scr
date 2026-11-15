package org.fogofwarlabs.sc4j.scr.game.modifier;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.modifier.Stat;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.content.ScrWeaponIds;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * Adds the range upgrade of a unit type to a weapon's maximum range ({@link Stat#WEAPON_RANGE}, in pixels): U-238 Shells for a Marine, Grooved
 * Spines for a Hydralisk, Singularity Charge for a Dragoon and Charon Boosters for the Hellfire Missile Pack of a Goliath, each when the owner has
 * researched it.
 */
public final class ScrWeaponRangeModifier extends ScrStatModifier {
    /**
     * The weapon range bonus in pixels of U-238 Shells and Grooved Spines.
     */
    private static final int RANGE_UPGRADE_BONUS = 32;
    /**
     * The weapon range bonus in pixels of Singularity Charge.
     */
    private static final int DRAGOON_RANGE_UPGRADE_BONUS = 64;
    /**
     * The weapon range bonus in pixels of Charon Boosters.
     */
    private static final int GOLIATH_RANGE_UPGRADE_BONUS = 96;

    private final @NotNull Game game;

    /**
     * Creates a {@link ScrWeaponRangeModifier} instance for one game.
     *
     * @param game the game whose upgrade levels the modifier reads
     */
    public ScrWeaponRangeModifier(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Applies this modifier to a value, on the native values.
     *
     * @param player          the player slot index of the owner
     * @param unitTypeIndex   the index of the unit type, its units.dat entry
     * @param weaponTypeIndex the index of the weapon type, its weapons.dat entry
     * @param value           the weapon range before the upgrade, in pixels
     * @return the weapon range with the upgrade bonus
     * @throws UnsupportedOperationException if the unit type has a range upgrade and the player upgrade levels are missing
     */
    @Override
    public int applyNative(int player, int unitTypeIndex, int weaponTypeIndex, int value) {
        int baseType = this.game.getBaseType(unitTypeIndex);
        int upgrade = switch (ScrUnitIds.requireValidOrAdded(baseType)) {
            case ScrUnitIds.TERRAN_MARINE -> ScrUpgradeIds.U_238_SHELLS;
            case ScrUnitIds.ZERG_HYDRALISK -> ScrUpgradeIds.GROOVED_SPINES;
            case ScrUnitIds.PROTOSS_DRAGOON -> ScrUpgradeIds.SINGULARITY_CHARGE;
            case ScrUnitIds.TERRAN_GOLIATH, ScrUnitIds.TERRAN_GOLIATH_TURRET -> weaponTypeIndex == ScrWeaponIds.HELLFIRE_MISSILE_PACK
                                                                                ? ScrUpgradeIds.CHARON_BOOSTERS
                                                                                : -1;
            default -> -1;
        };

        if (upgrade < 0) {
            return value;
        }

        PlayerUpgrades upgrades = this.game.getPlayerTable().getPlayerUpgrades();

        if (upgrades == null) {
            throw Exceptions.newUnsupportedOperationException("Weapon range upgrades require player upgrade levels.");
        }

        if (upgrades.getLevel(player, upgrade) == 0) {
            return value;
        }

        return value + switch (ScrUnitIds.requireValidOrAdded(baseType)) {
            case ScrUnitIds.PROTOSS_DRAGOON -> DRAGOON_RANGE_UPGRADE_BONUS;
            case ScrUnitIds.TERRAN_GOLIATH, ScrUnitIds.TERRAN_GOLIATH_TURRET -> GOLIATH_RANGE_UPGRADE_BONUS;
            default -> RANGE_UPGRADE_BONUS;
        };
    }
}
