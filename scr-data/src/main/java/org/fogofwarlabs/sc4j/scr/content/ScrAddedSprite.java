package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.engine.content.type.SpriteType;
import org.fogofwarlabs.sc4j.scr.content.table.ScrDatKind;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.SpritesDat;
import org.jetbrains.annotations.NotNull;

/**
 * Describes a sprite a mod adds after the native {@code sprites.dat} entries ({@link ScrAddedGraphics}): its engine identity and one value per
 * {@code sprites.dat} field.
 * <p>
 * The values start as a copy of a native base entry of the installed data; the mod then changes the values it wants, typically
 * the image ({@link SpritesDat#IMAGE}, a native or added image id), the health bar width and the selection circle. The health bar and
 * selection circle fields have no element for a base sprite below {@code 130}, so they start at {@code 0} for such a base.
 */
public final class ScrAddedSprite {
    /**
     * Builds {@link ScrAddedSprite} instances.
     */
    public static final class Builder {
        private final @NotNull SpriteType type;

        private final @NotNull ScrAddedDatValues values;

        /**
         * Creates a {@link Builder} instance whose values are those of a native base entry.
         *
         * @param type       the engine identity of the added entry, not registered anywhere yet
         * @param baseSprite the native {@code sprites.dat} entry whose values are copied
         * @param sprites    the installed {@code sprites.dat} table the values are read from
         * @throws IllegalArgumentException if the base entry is not a native entry
         */
        public Builder(@NotNull SpriteType type, int baseSprite, @NotNull DatTable sprites) {
            this.type = type;

            this.values = new ScrAddedDatValues(ScrDatKind.SPRITES, sprites, baseSprite, SpritesDat.SPRITE_COUNT);
        }

        /**
         * Sets the value of a {@code sprites.dat} field.
         *
         * @param field the {@code sprites.dat} field name ({@link SpritesDat})
         * @param value the value, kept as given
         * @return this instance for chaining
         * @throws IllegalArgumentException if the field does not exist
         */
        public @NotNull Builder setValue(@NotNull String field, int value) {
            this.values.set(field, value);

            return this;
        }

        /**
         * Builds a {@link ScrAddedSprite} instance with the current values.
         *
         * @return the built {@link ScrAddedSprite} instance
         */
        public @NotNull ScrAddedSprite build() {
            return new ScrAddedSprite(this);
        }
    }

    private final @NotNull SpriteType type;

    private final @NotNull ScrAddedDatValues values;

    private ScrAddedSprite(@NotNull Builder builder) {
        this.type = builder.type;

        this.values = builder.values.copy();
    }

    /**
     * @return the engine identity of the entry
     */
    public @NotNull SpriteType getType() {
        return this.type;
    }

    /**
     * Returns the value of a {@code sprites.dat} field.
     *
     * @param field the {@code sprites.dat} field name ({@link SpritesDat})
     * @return the value
     * @throws IllegalArgumentException if the field does not exist
     */
    public int getValue(@NotNull String field) {
        return this.values.get(field);
    }
}
