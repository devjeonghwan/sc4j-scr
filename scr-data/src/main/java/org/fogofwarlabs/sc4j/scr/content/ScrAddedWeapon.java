package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.engine.content.type.WeaponType;
import org.fogofwarlabs.sc4j.scr.content.table.ScrDatKind;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.WeaponsDat;
import org.jetbrains.annotations.NotNull;

/**
 * Describes a weapon a mod adds after the native {@code weapons.dat} entries ({@link ScrAddedUnitTypes#addWeapon}): its engine identity and one
 * value per {@code weapons.dat} field.
 * <p>
 * The values start as a copy of a native base weapon of the installed data; the mod then changes the values it wants, typically the damage, the
 * ranges, the target flags, the splash radii and the projectile ({@link WeaponsDat#FLINGY_TYPE}, a native or added flingy id). A weapon rule of
 * the simulation that names a native weapon does not apply to an added weapon; the values decide its behavior.
 */
public final class ScrAddedWeapon {
    /**
     * Builds {@link ScrAddedWeapon} instances.
     */
    public static final class Builder {
        private final @NotNull WeaponType type;

        private final @NotNull ScrAddedDatValues values;

        /**
         * Creates a {@link Builder} instance whose values are those of a native base weapon.
         *
         * @param type       the engine identity of the added weapon, not registered anywhere yet
         * @param baseWeapon the native {@code weapons.dat} entry whose values are copied
         * @param weapons    the installed {@code weapons.dat} table the values are read from
         * @throws IllegalArgumentException if the base weapon is not a native entry
         */
        public Builder(@NotNull WeaponType type, int baseWeapon, @NotNull DatTable weapons) {
            this.type = type;

            this.values = new ScrAddedDatValues(ScrDatKind.WEAPONS, weapons, baseWeapon, WeaponsDat.WEAPON_COUNT);
        }

        /**
         * Sets the value of a {@code weapons.dat} field.
         *
         * @param field the {@code weapons.dat} field name ({@link WeaponsDat})
         * @param value the value, kept as given
         * @return this instance for chaining
         * @throws IllegalArgumentException if the field does not exist
         */
        public @NotNull Builder setValue(@NotNull String field, int value) {
            this.values.set(field, value);

            return this;
        }

        /**
         * Builds a {@link ScrAddedWeapon} instance with the current values.
         *
         * @return the built {@link ScrAddedWeapon} instance
         */
        public @NotNull ScrAddedWeapon build() {
            return new ScrAddedWeapon(this);
        }
    }

    private final @NotNull WeaponType type;

    private final @NotNull ScrAddedDatValues values;

    private ScrAddedWeapon(@NotNull Builder builder) {
        this.type = builder.type;

        this.values = builder.values.copy();
    }

    /**
     * @return the engine identity of the weapon
     */
    public @NotNull WeaponType getType() {
        return this.type;
    }

    /**
     * Returns the value of a {@code weapons.dat} field.
     *
     * @param field the {@code weapons.dat} field name ({@link WeaponsDat})
     * @return the value
     * @throws IllegalArgumentException if the field does not exist
     */
    public int getValue(@NotNull String field) {
        return this.values.get(field);
    }
}
