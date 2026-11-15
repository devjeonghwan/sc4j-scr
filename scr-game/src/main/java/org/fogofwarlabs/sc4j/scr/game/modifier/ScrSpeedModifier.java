package org.fogofwarlabs.sc4j.scr.game.modifier;

import org.fogofwarlabs.sc4j.engine.content.modifier.Stat;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * Adds one speed upgrade step ({@link Stat#TOP_SPEED}: the upgrade part of a unit's net speed modifier) when a unit type's speed upgrade applies:
 * the unit type's speed upgrade researched by the owner, the expansion BYTE of the game context for the Torrasque, and always for the heroes and
 * the Lurker with a fixed upgraded speed. The unit keeps the result as its speed upgrade status bit, which then drives its top speed,
 * acceleration and turn rate; that bit stays unit state.
 */
public final class ScrSpeedModifier extends ScrStatModifier {
    /**
     * Returns the speed upgrade of a unit type.
     *
     * @param unitType the unit type, the base type of an added type ({@link Game#getBaseType(int)})
     * @return the upgrade identifier, or {@code -1} if the unit type has none
     * @throws IllegalArgumentException if the unit type is not a units.dat entry
     */
    private static int getSpeedUpgrade(int unitType) {
        return switch (ScrUnitIds.requireValidOrAdded(unitType)) {
            case ScrUnitIds.TERRAN_VULTURE, ScrUnitIds.HERO_JIM_RAYNOR_VULTURE -> ScrUpgradeIds.ION_THRUSTERS;
            case ScrUnitIds.ZERG_OVERLORD -> ScrUpgradeIds.PNEUMATIZED_CARAPACE;
            case ScrUnitIds.ZERG_ZERGLING -> ScrUpgradeIds.METABOLIC_BOOST;
            case ScrUnitIds.ZERG_HYDRALISK -> ScrUpgradeIds.MUSCULAR_AUGMENTS;
            case ScrUnitIds.PROTOSS_ZEALOT -> ScrUpgradeIds.LEG_ENHANCEMENTS;
            case ScrUnitIds.PROTOSS_SCOUT -> ScrUpgradeIds.GRAVITIC_THRUSTERS;
            case ScrUnitIds.PROTOSS_SHUTTLE -> ScrUpgradeIds.GRAVITIC_DRIVE;
            case ScrUnitIds.PROTOSS_OBSERVER -> ScrUpgradeIds.GRAVITIC_BOOSTERS;
            case ScrUnitIds.ZERG_ULTRALISK -> ScrUpgradeIds.ANABOLIC_SYNTHESIS;
            default -> -1;
        };
    }

    /**
     * Checks whether a unit type always moves at its upgraded speed.
     *
     * @param unitType the unit type, the base type of an added type ({@link Game#getBaseType(int)})
     * @return {@code true} if the unit type is a hero or the Lurker with a fixed upgraded speed, {@code false} otherwise
     * @throws IllegalArgumentException if the unit type is not a units.dat entry
     */
    public static boolean isFixedSpeed(int unitType) {
        return switch (ScrUnitIds.requireValidOrAdded(unitType)) {
            case ScrUnitIds.HERO_HUNTER_KILLER, ScrUnitIds.HERO_DEVOURING_ONE, ScrUnitIds.HERO_YGGDRASILL, ScrUnitIds.HERO_FENIX_ZEALOT,
                 ScrUnitIds.HERO_MOJO, ScrUnitIds.HERO_ARTANIS, ScrUnitIds.ZERG_LURKER -> true;
            default -> false;
        };
    }

    /**
     * Checks whether this modifier can add a speed step to a unit type.
     *
     * @param unitType the unit type, the base type of an added type ({@link Game#getBaseType(int)})
     * @return {@code true} if the unit type has a speed upgrade, is the Torrasque or always moves at its upgraded speed, {@code false} otherwise
     * @throws IllegalArgumentException if the unit type is not a units.dat entry
     */
    public static boolean hasSpeedRule(int unitType) {
        return getSpeedUpgrade(unitType) >= 0 || unitType == ScrUnitIds.HERO_TORRASQUE || isFixedSpeed(unitType);
    }

    private final @NotNull Game game;

    /**
     * Creates a {@link ScrSpeedModifier} instance for one game.
     *
     * @param game the game whose upgrade levels the modifier reads
     */
    public ScrSpeedModifier(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Applies this modifier to a value, on the native values. A fixed-speed unit type reads no upgrade storage.
     *
     * @param player          the player slot index of the owner
     * @param unitTypeIndex   the index of the unit type, its units.dat entry
     * @param weaponTypeIndex unused, {@link org.fogofwarlabs.sc4j.engine.content.type.ContentType#NO_INDEX}
     * @param value           the speed upgrade steps before this modifier
     * @return the steps, one more if the speed upgrade applies
     * @throws UnsupportedOperationException if the speed upgrade depends on the upgrade storage and it is missing
     */
    @Override
    public int applyNative(int player, int unitTypeIndex, int weaponTypeIndex, int value) {
        int baseType = this.game.getBaseType(unitTypeIndex);

        if (isFixedSpeed(baseType)) {
            return value + 1;
        }

        int speedUpgrade = getSpeedUpgrade(baseType);

        if (speedUpgrade < 0 && baseType != ScrUnitIds.HERO_TORRASQUE) {
            return value;
        }

        PlayerUpgrades upgrades = this.game.getPlayerTable().requirePlayerUpgrades();
        boolean upgraded = baseType == ScrUnitIds.HERO_TORRASQUE
                           ? upgrades.isExpansionEnabled()
                           : upgrades.getLevel(player, speedUpgrade) != 0;

        return upgraded ? value + 1 : value;
    }
}
