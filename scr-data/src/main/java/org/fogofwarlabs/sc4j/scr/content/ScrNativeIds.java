package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.engine.content.faction.Faction;
import org.fogofwarlabs.sc4j.engine.content.type.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Maps between the native numbers of the SC:R data (DAT entries, as EUD, CHK, replays and saved games hold them) and the
 * engine content: identities and engine table values.
 * <p>
 * The mod registers every kind in native order ({@code ScrContentImporter} checks it), so the index of a registered type
 * is its native entry. A table reference stores {@link ContentType#NO_INDEX} where the native data holds its "none"
 * entry (the entry count of units, weapons, upgrades, techs and orders); the {@code int} forms convert only that value
 * and pass every other value through unchanged, so a value outside the table written by EUD stays as written and keeps
 * its native overrun behavior. Flingy, sprite, image and sound references have no "none" entry.
 */
public final class ScrNativeIds {
    /**
     * Converts a unit type reference of an engine table to its units.dat entry.
     *
     * @param index the stored index, {@link ContentType#NO_INDEX} for none
     * @return the units.dat entry, {@link ScrUnits#NATIVE_NONE} for none
     */
    public static int toNativeUnit(int index) {
        return index == ContentType.NO_INDEX ? ScrUnits.NATIVE_NONE : index;
    }

    /**
     * Converts a units.dat entry to the stored value of a unit type reference of an engine table.
     *
     * @param nativeId the units.dat entry, {@link ScrUnits#NATIVE_NONE} for none
     * @return the stored index, {@link ContentType#NO_INDEX} for none
     */
    public static int fromNativeUnit(int nativeId) {
        return nativeId == ScrUnits.NATIVE_NONE ? ContentType.NO_INDEX : nativeId;
    }

    /**
     * Converts a weapon type reference of an engine table to its weapons.dat entry: a native weapon's index is its entry, an index after the
     * native weapons is an added weapon's ({@link ScrAddedUnitTypes#FIRST_WEAPON_ID}), whose entry follows the none entry.
     *
     * @param index the stored index, {@link ContentType#NO_INDEX} for none
     * @return the weapons.dat entry, {@link ScrWeapons#NATIVE_NONE} for none
     */
    public static int toNativeWeapon(int index) {
        if (index == ContentType.NO_INDEX) {
            return ScrWeapons.NATIVE_NONE;
        }

        return index >= ScrWeapons.COUNT ? index + ScrAddedUnitTypes.WEAPON_CONTENT_INDEX_SHIFT : index;
    }

    /**
     * Converts a weapons.dat entry to the stored value of a weapon type reference of an engine table, the reverse of {@link #toNativeWeapon}.
     *
     * @param nativeId the weapons.dat entry, {@link ScrWeapons#NATIVE_NONE} for none
     * @return the stored index, {@link ContentType#NO_INDEX} for none
     */
    public static int fromNativeWeapon(int nativeId) {
        if (nativeId == ScrWeapons.NATIVE_NONE) {
            return ContentType.NO_INDEX;
        }

        return nativeId > ScrWeapons.NATIVE_NONE ? nativeId - ScrAddedUnitTypes.WEAPON_CONTENT_INDEX_SHIFT : nativeId;
    }

    /**
     * Converts an upgrade type reference of an engine table to its upgrades.dat entry.
     *
     * @param index the stored index, {@link ContentType#NO_INDEX} for none
     * @return the upgrades.dat entry, {@link ScrUpgrades#NATIVE_NONE} for none
     */
    public static int toNativeUpgrade(int index) {
        return index == ContentType.NO_INDEX ? ScrUpgrades.NATIVE_NONE : index;
    }

    /**
     * Converts an upgrades.dat entry to the stored value of an upgrade type reference of an engine table.
     *
     * @param nativeId the upgrades.dat entry, {@link ScrUpgrades#NATIVE_NONE} for none
     * @return the stored index, {@link ContentType#NO_INDEX} for none
     */
    public static int fromNativeUpgrade(int nativeId) {
        return nativeId == ScrUpgrades.NATIVE_NONE ? ContentType.NO_INDEX : nativeId;
    }

    /**
     * Converts a tech type reference of an engine table to its techdata.dat entry.
     *
     * @param index the stored index, {@link ContentType#NO_INDEX} for none
     * @return the techdata.dat entry, {@link ScrTechs#NATIVE_NONE} for none
     */
    public static int toNativeTech(int index) {
        return index == ContentType.NO_INDEX ? ScrTechs.NATIVE_NONE : index;
    }

    /**
     * Converts a techdata.dat entry to the stored value of a tech type reference of an engine table.
     *
     * @param nativeId the techdata.dat entry, {@link ScrTechs#NATIVE_NONE} for none
     * @return the stored index, {@link ContentType#NO_INDEX} for none
     */
    public static int fromNativeTech(int nativeId) {
        return nativeId == ScrTechs.NATIVE_NONE ? ContentType.NO_INDEX : nativeId;
    }

    /**
     * Converts an order type reference of an engine table to its orders.dat entry.
     *
     * @param index the stored index, {@link ContentType#NO_INDEX} for none
     * @return the orders.dat entry, {@link ScrOrders#NATIVE_NONE} for none
     */
    public static int toNativeOrder(int index) {
        return index == ContentType.NO_INDEX ? ScrOrders.NATIVE_NONE : index;
    }

    /**
     * Converts an orders.dat entry to the stored value of an order type reference of an engine table.
     *
     * @param nativeId the orders.dat entry, {@link ScrOrders#NATIVE_NONE} for none
     * @return the stored index, {@link ContentType#NO_INDEX} for none
     */
    public static int fromNativeOrder(int nativeId) {
        return nativeId == ScrOrders.NATIVE_NONE ? ContentType.NO_INDEX : nativeId;
    }


    /**
     * Finds the unit type of a {@code units.dat} entry.
     *
     * @param nativeId a units.dat entry or any other value
     * @return the unit type of the entry, or {@code null} for the "none" entry and every value outside the table
     */
    public static @Nullable UnitType unitOf(int nativeId) {
        return nativeId >= 0 && nativeId < ScrUnits.COUNT ? ScrUnits.get(nativeId) : null;
    }

    /**
     * Finds the weapon type of a {@code weapons.dat} entry.
     *
     * @param nativeId a weapons.dat entry or any other value
     * @return the weapon type of the entry, or {@code null} for the "none" entry and every value outside the table
     */
    public static @Nullable WeaponType weaponOf(int nativeId) {
        return nativeId >= 0 && nativeId < ScrWeapons.COUNT ? ScrWeapons.get(nativeId) : null;
    }

    /**
     * Finds the upgrade type of an {@code upgrades.dat} entry.
     *
     * @param nativeId an upgrades.dat entry or any other value
     * @return the upgrade type of the entry, or {@code null} for the "none" entry and every value outside the table
     */
    public static @Nullable UpgradeType upgradeOf(int nativeId) {
        return nativeId >= 0 && nativeId < ScrUpgrades.COUNT ? ScrUpgrades.get(nativeId) : null;
    }

    /**
     * Finds the tech type of a {@code techdata.dat} entry.
     *
     * @param nativeId a techdata.dat entry or any other value
     * @return the tech type of the entry, or {@code null} for the "none" entry and every value outside the table
     */
    public static @Nullable TechType techOf(int nativeId) {
        return nativeId >= 0 && nativeId < ScrTechs.COUNT ? ScrTechs.get(nativeId) : null;
    }

    /**
     * Finds the order type of an {@code orders.dat} entry.
     *
     * @param nativeId an orders.dat entry or any other value
     * @return the order type of the entry, or {@code null} for the "none" entry and every value outside the table
     */
    public static @Nullable OrderType orderOf(int nativeId) {
        return nativeId >= 0 && nativeId < ScrOrders.COUNT ? ScrOrders.get(nativeId) : null;
    }

    /**
     * Finds the flingy type of a {@code flingy.dat} entry.
     *
     * @param nativeId a flingy.dat entry or any other value
     * @return the flingy type of the entry, or {@code null} for every value outside the table
     */
    public static @Nullable MotionProfile flingyOf(int nativeId) {
        return nativeId >= 0 && nativeId < ScrFlingies.COUNT ? ScrFlingies.get(nativeId) : null;
    }

    /**
     * Finds the sprite type of a {@code sprites.dat} entry.
     *
     * @param nativeId a sprites.dat entry or any other value
     * @return the sprite type of the entry, or {@code null} for every value outside the table
     */
    public static @Nullable SpriteType spriteOf(int nativeId) {
        return nativeId >= 0 && nativeId < ScrSprites.COUNT ? ScrSprites.get(nativeId) : null;
    }

    /**
     * Finds the image type of an {@code images.dat} entry.
     *
     * @param nativeId an images.dat entry or any other value
     * @return the image type of the entry, or {@code null} for every value outside the table
     */
    public static @Nullable ImageType imageOf(int nativeId) {
        return nativeId >= 0 && nativeId < ScrImages.COUNT ? ScrImages.get(nativeId) : null;
    }

    /**
     * Finds the sound type of a {@code sfxdata.dat} entry.
     *
     * @param nativeId a sfxdata.dat entry or any other value
     * @return the sound type of the entry, or {@code null} for every value outside the table
     */
    public static @Nullable SoundType soundOf(int nativeId) {
        return nativeId >= 0 && nativeId < ScrSounds.COUNT ? ScrSounds.get(nativeId) : null;
    }

    /**
     * Finds the faction of a native race byte.
     *
     * @param nativeId a native race byte or any other value
     * @return the faction of a playable race byte, or {@code null} for every other value (random, none, or outside the races)
     */
    public static @Nullable Faction raceOf(int nativeId) {
        return nativeId >= 0 && nativeId < ScrRaces.COUNT ? ScrRaces.get(nativeId) : null;
    }


    /**
     * Returns the native entry of a registered identity of any kind: its index, since every kind is registered in native
     * order.
     *
     * @param type the identity
     * @return the native entry
     */
    public static int toNative(@NotNull ContentType type) {
        return type.getIndex();
    }


    private ScrNativeIds() {
    }
}
