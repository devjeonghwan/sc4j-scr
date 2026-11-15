package org.fogofwarlabs.sc4j.scr.game.modifier;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.modifier.Stat;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * Sets the sight range of a unit type with its sight upgrade ({@link Stat#SIGHT_RANGE}, in tiles) to {@code 11}: Ocular Implants for a Ghost,
 * Antennae for an Overlord, Apial Sensors for a Scout and Sensor Array for an Observer, each when the owner has researched it.
 */
public final class ScrSightRangeModifier extends ScrStatModifier {
    /**
     * The sight range of a unit with its sight upgrade, in tiles.
     */
    private static final int UPGRADED_SIGHT_RANGE = 11;

    private final @NotNull Game game;

    /**
     * Creates a {@link ScrSightRangeModifier} instance for one game.
     *
     * @param game the game whose upgrade levels the modifier reads
     */
    public ScrSightRangeModifier(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Applies this modifier to a value, on the native values.
     *
     * @param player          the player slot index of the owner
     * @param unitTypeIndex   the index of the unit type, its units.dat entry
     * @param weaponTypeIndex unused, {@link org.fogofwarlabs.sc4j.engine.content.type.ContentType#NO_INDEX}
     * @param value           the sight range of the unit type, in tiles
     * @return the sight range with the upgrade
     * @throws UnsupportedOperationException if the unit type has a sight upgrade and the player upgrade storage is missing
     */
    @Override
    public int applyNative(int player, int unitTypeIndex, int weaponTypeIndex, int value) {
        int upgrade = switch (ScrUnitIds.requireValidOrAdded(this.game.getBaseType(unitTypeIndex))) {
            case ScrUnitIds.TERRAN_GHOST -> ScrUpgradeIds.OCULAR_IMPLANTS;
            case ScrUnitIds.ZERG_OVERLORD -> ScrUpgradeIds.ANTENNAE;
            case ScrUnitIds.PROTOSS_SCOUT -> ScrUpgradeIds.APIAL_SENSORS;
            case ScrUnitIds.PROTOSS_OBSERVER -> ScrUpgradeIds.SENSOR_ARRAY;
            default -> -1;
        };

        if (upgrade < 0) {
            return value;
        }

        PlayerUpgrades upgrades = this.game.getPlayerTable().getPlayerUpgrades();

        if (upgrades == null) {
            throw Exceptions.newUnsupportedOperationException("Upgraded sight requires initialized player upgrade storage.");
        }

        return upgrades.getLevel(player, upgrade) != 0 ? UPGRADED_SIGHT_RANGE : value;
    }
}
