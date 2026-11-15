package org.fogofwarlabs.sc4j.scr.game.modifier;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.modifier.Stat;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * Adds the bonus of a unit type's range upgrade to its target acquisition range ({@link Stat#TARGET_ACQUISITION_RANGE}, in tiles): U-238 Shells,
 * Charon Boosters, Grooved Spines and Singularity Charge when the owner has researched them, and for the heroes Alan Schezar and Fenix (Dragoon)
 * when the expansion BYTE of the game context is set.
 */
public final class ScrTargetAcquisitionRangeModifier extends ScrStatModifier {
    /**
     * The target acquisition range bonus of a Goliath with Charon Boosters and of Alan Schezar, in tiles.
     */
    private static final int CHARON_BOOSTERS_RANGE_BONUS = 3;
    /**
     * The target acquisition range bonus of a Dragoon with Singularity Charge and of Fenix, in tiles.
     */
    private static final int SINGULARITY_CHARGE_RANGE_BONUS = 2;
    /**
     * The target acquisition range bonus of the other range upgrades (U-238 Shells, Grooved Spines), in tiles.
     */
    private static final int RANGE_UPGRADE_BONUS = 1;

    private final @NotNull Game game;

    /**
     * Creates a {@link ScrTargetAcquisitionRangeModifier} instance for one game.
     *
     * @param game the game whose upgrade levels the modifier reads
     */
    public ScrTargetAcquisitionRangeModifier(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Applies this modifier to a value, on the native values.
     *
     * @param player          the player slot index of the owner
     * @param unitTypeIndex   the index of the unit type, its units.dat entry
     * @param weaponTypeIndex unused, {@link org.fogofwarlabs.sc4j.engine.content.type.ContentType#NO_INDEX}
     * @param value           the target acquisition range of the unit type, in tiles
     * @return the range with the upgrade bonus
     * @throws UnsupportedOperationException if a range upgrade applies and the player upgrade levels are missing
     */
    @Override
    public int applyNative(int player, int unitTypeIndex, int weaponTypeIndex, int value) {
        int baseType = this.game.getBaseType(unitTypeIndex);
        int upgrade = switch (ScrUnitIds.requireValidOrAdded(baseType)) {
            case ScrUnitIds.TERRAN_MARINE -> ScrUpgradeIds.U_238_SHELLS;
            case ScrUnitIds.TERRAN_GOLIATH, ScrUnitIds.TERRAN_GOLIATH_TURRET -> ScrUpgradeIds.CHARON_BOOSTERS;
            case ScrUnitIds.ZERG_HYDRALISK -> ScrUpgradeIds.GROOVED_SPINES;
            case ScrUnitIds.PROTOSS_DRAGOON -> ScrUpgradeIds.SINGULARITY_CHARGE;
            default -> -1;
        };

        if (upgrade < 0 && baseType != ScrUnitIds.HERO_ALAN_SCHEZAR && baseType != ScrUnitIds.HERO_ALAN_SCHEZAR_TURRET &&
            baseType != ScrUnitIds.HERO_FENIX_DRAGOON) {
            return value;
        }

        PlayerUpgrades upgrades = this.game.getPlayerTable().getPlayerUpgrades();

        if (upgrades == null) {
            throw Exceptions.newUnsupportedOperationException("Native acquisition range requires initialized player upgrade storage.");
        }

        boolean increased = upgrade >= 0 ? upgrades.getLevel(player, upgrade) != 0 : upgrades.isExpansionEnabled();

        if (!increased) {
            return value;
        }

        return value + switch (ScrUnitIds.requireValidOrAdded(baseType)) {
            case ScrUnitIds.TERRAN_GOLIATH, ScrUnitIds.TERRAN_GOLIATH_TURRET, ScrUnitIds.HERO_ALAN_SCHEZAR,
                 ScrUnitIds.HERO_ALAN_SCHEZAR_TURRET -> CHARON_BOOSTERS_RANGE_BONUS;
            case ScrUnitIds.PROTOSS_DRAGOON, ScrUnitIds.HERO_FENIX_DRAGOON -> SINGULARITY_CHARGE_RANGE_BONUS;
            default -> RANGE_UPGRADE_BONUS;
        };
    }
}
