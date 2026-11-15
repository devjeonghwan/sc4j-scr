package org.fogofwarlabs.sc4j.scr.game.modifier;

import org.fogofwarlabs.sc4j.engine.content.modifier.Stat;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

/**
 * Adds one cooldown upgrade step ({@link Stat#WEAPON_COOLDOWN}: the upgrade part of a unit's attack speed) for the Devouring One and for a
 * Zergling whose owner researched Adrenal Glands. The unit keeps the result as its cooldown upgrade status bit, which halves its weapon cooldown
 * together with its speed modifiers; that bit stays unit state.
 */
public final class ScrWeaponCooldownModifier extends ScrStatModifier {
    private final @NotNull Game game;

    /**
     * Creates a {@link ScrWeaponCooldownModifier} instance.
     *
     * @param game the game whose upgrade levels the modifier reads
     */
    public ScrWeaponCooldownModifier(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Applies this modifier to a value, on the native values. The Devouring One reads no upgrade storage.
     *
     * @param player          the player slot index of the owner
     * @param unitTypeIndex   the index of the unit type, its units.dat entry
     * @param weaponTypeIndex unused, {@link org.fogofwarlabs.sc4j.engine.content.type.ContentType#NO_INDEX}
     * @param value           the cooldown upgrade steps before this modifier
     * @return the steps, one more if the cooldown upgrade applies
     * @throws UnsupportedOperationException if a Zergling's upgrade applies and the upgrade storage is missing
     */
    @Override
    public int applyNative(int player, int unitTypeIndex, int weaponTypeIndex, int value) {
        int baseType = this.game.getBaseType(unitTypeIndex);
        boolean upgraded = baseType == ScrUnitIds.HERO_DEVOURING_ONE ||
                           baseType == ScrUnitIds.ZERG_ZERGLING &&
                           this.game.getPlayerTable().requirePlayerUpgrades().getLevel(player, ScrUpgradeIds.ADRENAL_GLANDS) != 0;

        return upgraded ? value + 1 : value;
    }
}
