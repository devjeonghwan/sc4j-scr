package org.fogofwarlabs.sc4j.scr.game.modifier;

import org.fogofwarlabs.sc4j.engine.content.modifier.Stat;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * Adds the armor upgrades to the armor of a unit type ({@link Stat#ARMOR}, with eight fraction bits): Chitinous Plating for an Ultralisk or
 * Torrasque, then the level of the unit type's armor upgrade. The levels are read through the game's {@link PlayerUpgrades}, so an owner past the
 * player slots or an armor upgrade past the upgrade table reads the game-context byte the level layout addresses.
 */
public final class ScrArmorModifier extends ScrStatModifier {
    /**
     * The fraction bits of an armor value.
     */
    private static final int FIXED_POINT_FRACTION_BITS = 8;
    /**
     * The armor the Chitinous Plating upgrade adds to an Ultralisk or Torrasque.
     */
    private static final int CHITINOUS_PLATING_ARMOR = 2;

    private final @NotNull Game game;

    /**
     * Creates a {@link ScrArmorModifier} instance for one game.
     *
     * @param game the game whose upgrade levels and unit types the modifier reads
     */
    public ScrArmorModifier(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Applies this modifier to a value, on the native values.
     *
     * @param player          the player slot index of the owner
     * @param unitTypeIndex   the index of the unit type, its units.dat entry
     * @param weaponTypeIndex unused, {@link org.fogofwarlabs.sc4j.engine.content.type.ContentType#NO_INDEX}
     * @param value           the armor of the unit type with eight fraction bits
     * @return the armor with the upgrades
     * @throws NullPointerException          if the world was built without game data
     * @throws UnsupportedOperationException if the player upgrade levels are missing
     */
    @Override
    public int applyNative(int player, int unitTypeIndex, int weaponTypeIndex, int value) {
        ScrUnitTypeView unitTypes = this.game.requireGameData().getUnitTypes();
        PlayerUpgrades upgrades = this.game.getPlayerTable().requirePlayerUpgrades();
        int baseType = this.game.getBaseType(unitTypeIndex);
        int armor = value;

        if ((baseType == ScrUnitIds.ZERG_ULTRALISK || baseType == ScrUnitIds.HERO_TORRASQUE) &&
            upgrades.getLevel(player, ScrUpgradeIds.CHITINOUS_PLATING) != 0) {
            armor += CHITINOUS_PLATING_ARMOR << FIXED_POINT_FRACTION_BITS;
        }

        armor += upgrades.getLevel(player, unitTypes.getArmorUpgrade(unitTypeIndex)) << FIXED_POINT_FRACTION_BITS;

        return armor;
    }
}
