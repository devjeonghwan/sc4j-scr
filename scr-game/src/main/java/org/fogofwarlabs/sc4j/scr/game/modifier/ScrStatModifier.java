package org.fogofwarlabs.sc4j.scr.game.modifier;

import org.fogofwarlabs.sc4j.engine.content.modifier.StatModifier;
import org.fogofwarlabs.sc4j.engine.content.table.GameContent;
import org.fogofwarlabs.sc4j.engine.content.type.ContentType;
import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.fogofwarlabs.sc4j.engine.content.type.WeaponType;
import org.fogofwarlabs.sc4j.engine.player.Player;
import org.fogofwarlabs.sc4j.scr.content.ScrNativeIds;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Applies one SC:R upgrade effect to a stat. The effect works on the native values ({@link #applyNative}): the player DWORD of the owner, the
 * units.dat entry of the unit type and the weapons.dat entry of the weapon, any of which may lie past its table (values EUD wrote), where the
 * SC:R storages read the bytes the native layout addresses. The SC:R simulation applies its modifiers on those native values
 * ({@link org.fogofwarlabs.sc4j.scr.game.state.Game#applyModifiers}); the engine form ({@link #apply}) converts the identities to their native
 * entries and applies the same effect, so engine callers and the SC:R simulation agree.
 */
public abstract class ScrStatModifier implements StatModifier {
    /**
     * Creates a {@link ScrStatModifier} instance.
     */
    protected ScrStatModifier() {
        // The subclasses hold the game they read.
    }

    /**
     * Applies this modifier to a value: the native form on the slot index of the player and the native entries of the types.
     *
     * @param content    the content of the game
     * @param player     the owner
     * @param unitType   the unit type, or {@code null} for a weapon stat without a unit
     * @param weaponType the weapon type for a weapon stat, otherwise {@code null}
     * @param value      the value before this modifier
     * @return the value after this modifier
     */
    @Override
    public final int apply(@NotNull GameContent content, @NotNull Player player, @Nullable UnitType unitType, @Nullable WeaponType weaponType,
                           int value) {
        return this.applyNative(player.getIndex(), unitType != null ? ScrNativeIds.toNative(unitType) : ContentType.NO_INDEX,
                                weaponType != null ? ScrNativeIds.toNative(weaponType) : ContentType.NO_INDEX, value);
    }

    /**
     * Applies this modifier to a value, on the native values.
     *
     * @param player          the player DWORD of the owner, its slot index or any value EUD wrote
     * @param unitTypeIndex   the units.dat entry of the unit type, or {@link ContentType#NO_INDEX} for a weapon stat without a unit
     * @param weaponTypeIndex the weapons.dat entry of the weapon for a weapon stat, otherwise {@link ContentType#NO_INDEX}
     * @param value           the value before this modifier
     * @return the value after this modifier
     */
    public abstract int applyNative(int player, int unitTypeIndex, int weaponTypeIndex, int value);
}
