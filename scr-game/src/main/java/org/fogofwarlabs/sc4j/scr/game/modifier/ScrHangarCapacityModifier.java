package org.fogofwarlabs.sc4j.scr.game.modifier;

import org.fogofwarlabs.sc4j.scr.content.ScrStats;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.jetbrains.annotations.NotNull;

/**
 * Doubles the hangar capacity ({@link ScrStats#HANGAR_CAPACITY}) of a Carrier type to {@link UnitQueries#UPGRADED_CARRIER_CAPACITY} and of a Reaver
 * type to {@link UnitQueries#UPGRADED_REAVER_CAPACITY} for a hero, or when the owner researched Carrier Capacity or Reaver Capacity.
 */
public final class ScrHangarCapacityModifier extends ScrStatModifier {
    private final @NotNull Game game;

    /**
     * Creates a {@link ScrHangarCapacityModifier} instance.
     *
     * @param game the game whose unit types and upgrade levels the modifier reads
     */
    public ScrHangarCapacityModifier(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Applies this modifier to a value, on the native values. A hero reads no upgrade storage.
     *
     * @param player          the player slot index of the owner
     * @param unitTypeIndex   the index of the unit type, its units.dat entry
     * @param weaponTypeIndex unused, {@link org.fogofwarlabs.sc4j.engine.content.type.ContentType#NO_INDEX}
     * @param value           the hangar capacity before this modifier
     * @return the capacity, upgraded for a Carrier or Reaver type that is a hero or whose upgrade the owner has
     * @throws UnsupportedOperationException if the upgrade applies to a type that is not a hero and the upgrade storage is missing
     * @throws NullPointerException          if a Carrier or Reaver type is tested and the world was built without game data
     */
    @Override
    public int applyNative(int player, int unitTypeIndex, int weaponTypeIndex, int value) {
        boolean carrier = this.game.requireGameData().getUnitTypes().isCarrier(unitTypeIndex);
        boolean reaver = this.game.requireGameData().getUnitTypes().isReaver(unitTypeIndex);

        if (!carrier && !reaver) {
            return value;
        }

        boolean hero = (this.game.requireGameData().getUnitTypes().getFlags(unitTypeIndex) & UnitTypeFlags.HERO) != 0;
        int upgrade = carrier ? ScrUpgradeIds.CARRIER_CAPACITY : ScrUpgradeIds.REAVER_CAPACITY;

        if (hero || this.game.getPlayerTable().requirePlayerUpgrades().getLevel(player, upgrade) != 0) {
            return carrier ? UnitQueries.UPGRADED_CARRIER_CAPACITY : UnitQueries.UPGRADED_REAVER_CAPACITY;
        }

        return value;
    }
}
