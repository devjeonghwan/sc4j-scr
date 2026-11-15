package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.engine.content.type.MotionProfile;
import org.fogofwarlabs.sc4j.scr.content.table.ScrDatKind;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.FlingyDat;
import org.jetbrains.annotations.NotNull;

/**
 * Describes a flingy entry a mod adds after the native {@code flingy.dat} entries ({@link ScrAddedGraphics}): its engine identity and one value per
 * {@code flingy.dat} field.
 * <p>
 * The values start as a copy of a native base entry of the installed data; the mod then changes the values it wants, typically
 * the sprite ({@link FlingyDat#SPRITE}, a native or added sprite id) and the movement values.
 */
public final class ScrAddedFlingy {
    /**
     * Builds {@link ScrAddedFlingy} instances.
     */
    public static final class Builder {
        private final @NotNull MotionProfile type;
        private final int baseFlingy;

        private final @NotNull ScrAddedDatValues values;

        /**
         * Creates a {@link Builder} instance whose values are those of a native base entry.
         *
         * @param type       the engine identity of the added entry, not registered anywhere yet
         * @param baseFlingy the native {@code flingy.dat} entry whose values are copied
         * @param flingy     the installed {@code flingy.dat} table the values are read from
         * @throws IllegalArgumentException if the base entry is not a native entry
         */
        public Builder(@NotNull MotionProfile type, int baseFlingy, @NotNull DatTable flingy) {
            this.type       = type;
            this.baseFlingy = baseFlingy;

            this.values = new ScrAddedDatValues(ScrDatKind.FLINGY, flingy, baseFlingy, FlingyDat.FLINGY_COUNT);
        }

        /**
         * Sets the value of a {@code flingy.dat} field.
         *
         * @param field the {@code flingy.dat} field name ({@link FlingyDat})
         * @param value the value, kept as given
         * @return this instance for chaining
         * @throws IllegalArgumentException if the field does not exist
         */
        public @NotNull Builder setValue(@NotNull String field, int value) {
            this.values.set(field, value);

            return this;
        }

        /**
         * Builds a {@link ScrAddedFlingy} instance with the current values.
         *
         * @return the built {@link ScrAddedFlingy} instance
         */
        public @NotNull ScrAddedFlingy build() {
            return new ScrAddedFlingy(this);
        }
    }

    private final @NotNull MotionProfile type;
    private final int baseFlingy;

    private final @NotNull ScrAddedDatValues values;

    private ScrAddedFlingy(@NotNull Builder builder) {
        this.type       = builder.type;
        this.baseFlingy = builder.baseFlingy;

        this.values = builder.values.copy();
    }

    /**
     * @return the engine identity of the entry
     */
    public @NotNull MotionProfile getType() {
        return this.type;
    }

    /**
     * @return the native {@code flingy.dat} entry whose values the entry started from
     */
    public int getBaseFlingy() {
        return this.baseFlingy;
    }

    /**
     * Returns the value of a {@code flingy.dat} field.
     *
     * @param field the {@code flingy.dat} field name ({@link FlingyDat})
     * @return the value
     * @throws IllegalArgumentException if the field does not exist
     */
    public int getValue(@NotNull String field) {
        return this.values.get(field);
    }
}
