package org.fogofwarlabs.sc4j.scr.game.modifier;

import org.fogofwarlabs.sc4j.scr.content.ScrStats;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * Adds the Protoss Plasma Shields level of the owner to the shield armor ({@link ScrStats#SHIELD_ARMOR}, with eight fraction bits), read through the
 * game's {@link PlayerUpgrades}.
 */
public final class ScrShieldArmorModifier extends ScrStatModifier {
    /**
     * The fraction bits of an armor value.
     */
    private static final int FIXED_POINT_FRACTION_BITS = 8;

    private final @NotNull Game game;

    /**
     * Creates a {@link ScrShieldArmorModifier} instance for one game.
     *
     * @param game the game whose upgrade levels the modifier reads
     */
    public ScrShieldArmorModifier(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Applies this modifier to a value, on the native values.
     *
     * @param player          the player slot index of the owner
     * @param unitTypeIndex   the index of the unit type, its units.dat entry
     * @param weaponTypeIndex unused, {@link org.fogofwarlabs.sc4j.engine.content.type.ContentType#NO_INDEX}
     * @param value           the shield armor before the upgrade, with eight fraction bits
     * @return the shield armor with the upgrade
     * @throws UnsupportedOperationException if the player upgrade levels are missing
     */
    @Override
    public int applyNative(int player, int unitTypeIndex, int weaponTypeIndex, int value) {
        PlayerUpgrades upgrades = this.game.getPlayerTable().requirePlayerUpgrades();

        return value + (upgrades.getLevel(player, ScrUpgradeIds.PROTOSS_PLASMA_SHIELDS) << FIXED_POINT_FRACTION_BITS);
    }
}
