package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns palette-cycle timers, color ranges and optional animation color sequences.
 */
public final class PaletteAnimation {
    /**
     * The number of colors in the retained palette.
     */
    public static final int COLOR_COUNT = 256;

    /**
     * The number of palette cycles.
     */
    private static final int CYCLE_COUNT = 8;
    /**
     * The highest first color of a cycle that the first update pass handles.
     */
    private static final int FIRST_PASS_COLOR_LIMIT = 128;
    /**
     * The period and countdown of a tileset cycle at creation.
     */
    private static final int INITIAL_CYCLE_PERIOD = 8;
    /**
     * The activation flag of an enabled cycle.
     */
    private static final int CYCLE_ENABLED = 1;
    /**
     * The number of values of one color range: its first and its last color.
     */
    private static final int RANGE_VALUE_COUNT = 2;
    /**
     * The palette redraw flag after an update.
     */
    private static final int PALETTE_DIRTY = 1;
    /**
     * The multiplier that combines each retained value into the state hash.
     */
    private static final int HASH_MULTIPLIER = 31;

    /**
     * The tileset index of Badlands.
     */
    private static final int BADLANDS_TILESET = 0;
    /**
     * The tileset index of Space Platform.
     */
    private static final int SPACE_PLATFORM_TILESET = 1;
    /**
     * The tileset index of Installation.
     */
    private static final int INSTALLATION_TILESET = 2;
    /**
     * The tileset index of Ash World.
     */
    private static final int ASH_WORLD_TILESET = 3;
    /**
     * The tileset index of Jungle.
     */
    private static final int JUNGLE_TILESET = 4;
    /**
     * The tileset index of Desert.
     */
    private static final int DESERT_TILESET = 5;
    /**
     * The tileset index of Ice.
     */
    private static final int ICE_TILESET = 6;
    /**
     * The tileset index of Twilight.
     */
    private static final int TWILIGHT_TILESET = 7;

    /**
     * Creates a {@link PaletteAnimation} instance for audited static tileset descriptors.
     *
     * @param tileset the original tileset index
     * @param palette the independently established current palette
     * @param dirty   the independently established palette dirty DWORD
     * @return the created {@link PaletteAnimation} instance
     * @throws UnsupportedOperationException if {@code tileset} is not one of the eight original tilesets
     */
    public static @NotNull PaletteAnimation createForTileset(int tileset, int @NotNull [] palette, int dirty) {
        int[] ranges = switch (tileset) {
            case SPACE_PLATFORM_TILESET, INSTALLATION_TILESET -> new int[0];
            case BADLANDS_TILESET, JUNGLE_TILESET -> new int[]{1, 6, 7, 13, 248, 254};
            case ASH_WORLD_TILESET -> new int[]{1, 4, 5, 8, 9, 12};
            case DESERT_TILESET, ICE_TILESET, TWILIGHT_TILESET -> new int[]{1, 13, 248, 254};
            default -> throw Exceptions.newUnsupportedOperationException("Native tileset cycle descriptor producer is not established: {0}", tileset);
        };
        PaletteAnimation animation = new PaletteAnimation(palette, dirty);

        for (int index = 0; index < ranges.length / RANGE_VALUE_COUNT; index++) {
            animation.setCycle(index,
                               CYCLE_ENABLED,
                               INITIAL_CYCLE_PERIOD, INITIAL_CYCLE_PERIOD,
                               ranges[index * RANGE_VALUE_COUNT], ranges[index * RANGE_VALUE_COUNT + 1],
                               0,
                               0, null);
        }

        return animation;
    }

    private final byte @NotNull [] enabled;
    private final byte @NotNull [] periods;
    private final byte @NotNull [] countdowns;
    private final byte @NotNull [] firstColors;
    private final byte @NotNull [] phases;
    private final byte @NotNull [] lastColors;
    private final byte @NotNull [] sourceColorCounts;

    private final int @NotNull [] palette;
    private final int @NotNull [] @Nullable [] frameColors;
    private int dirty;

    /**
     * Creates a {@link PaletteAnimation} instance with initially disabled palette cycles.
     *
     * @param palette the {@link #COLOR_COUNT} current colors, copied on construction
     * @param dirty   the initial redraw flag
     * @throws IllegalArgumentException if {@code palette} does not hold {@link #COLOR_COUNT} colors
     */
    public PaletteAnimation(int @NotNull [] palette, int dirty) {
        if (palette.length != COLOR_COUNT) {
            throw Exceptions.newIllegalArgumentException("Palette cycling requires 256 colors.");
        }

        this.enabled           = new byte[CYCLE_COUNT];
        this.periods           = new byte[CYCLE_COUNT];
        this.countdowns        = new byte[CYCLE_COUNT];
        this.firstColors       = new byte[CYCLE_COUNT];
        this.phases            = new byte[CYCLE_COUNT];
        this.lastColors        = new byte[CYCLE_COUNT];
        this.sourceColorCounts = new byte[CYCLE_COUNT];

        this.palette     = palette.clone();
        this.frameColors = new int[CYCLE_COUNT][];
        this.dirty       = dirty;
    }

    /**
     * @param colorIndex the palette index from {@code 0} through {@code 255}
     * @return the current packed color
     * @throws IndexOutOfBoundsException if the index is outside the palette
     */
    public int getColor(int colorIndex) {
        return this.palette[colorIndex];
    }

    /**
     * @return the palette redraw flag
     */
    public int getDirty() {
        return this.dirty;
    }

    /**
     * @param index the cycle index
     * @return the unsigned cycle activation flag
     */
    int getEnabled(int index) {
        return Byte.toUnsignedInt(this.enabled[index]);
    }

    /**
     * @param index the cycle index
     * @return the unsigned cycle period
     */
    int getPeriod(int index) {
        return Byte.toUnsignedInt(this.periods[index]);
    }

    /**
     * @param index the cycle index
     * @return the unsigned remaining update count
     */
    int getCountdown(int index) {
        return Byte.toUnsignedInt(this.countdowns[index]);
    }

    /**
     * @param index the cycle index
     * @return the first destination palette index
     */
    int getFirstColor(int index) {
        return Byte.toUnsignedInt(this.firstColors[index]);
    }

    /**
     * @param index the cycle index
     * @return the unsigned source phase
     */
    int getPhase(int index) {
        return Byte.toUnsignedInt(this.phases[index]);
    }

    /**
     * @param index the cycle index
     * @return the inclusive last destination palette index
     */
    int getLastColor(int index) {
        return Byte.toUnsignedInt(this.lastColors[index]);
    }

    /**
     * @param index the cycle index
     * @return the unsigned source cycle length
     */
    int getSourceColorCount(int index) {
        return Byte.toUnsignedInt(this.sourceColorCounts[index]);
    }

    /**
     * Sets one cycle, retaining unsigned eight-bit counters and copying its optional source colors. The color range is not validated here; a
     * malformed range fails in {@link #update()}.
     *
     * @param index            the cycle index from {@code 0} through {@code 7}
     * @param enabled          the low eight bits of the activation flag
     * @param period           the unsigned cycle period
     * @param countdown        the remaining update count, including {@code 0} before wraparound
     * @param firstColor       the first destination palette index
     * @param lastColor        the inclusive last destination palette index
     * @param phase            the current source phase
     * @param sourceColorCount the unsigned source cycle length
     * @param sourceColors     the source colors, or {@code null} to rotate the palette range
     */
    public void setCycle(int index,
                         int enabled,
                         int period, int countdown,
                         int firstColor, int lastColor,
                         int phase,
                         int sourceColorCount, int @Nullable [] sourceColors) {
        this.enabled[index]           = (byte) enabled;
        this.periods[index]           = (byte) period;
        this.countdowns[index]        = (byte) countdown;
        this.firstColors[index]       = (byte) firstColor;
        this.phases[index]            = (byte) phase;
        this.lastColors[index]        = (byte) lastColor;
        this.sourceColorCounts[index] = (byte) sourceColorCount;
        this.frameColors[index]       = sourceColors == null ? null : sourceColors.clone();
    }


    /**
     * Updates palette cycles in two color-range passes and unconditionally sets the redraw flag.
     */
    public void update() {
        int index = this.updateRange(0, FIRST_PASS_COLOR_LIMIT);

        if (index < CYCLE_COUNT) {
            this.updateRange(index, COLOR_COUNT);
        }

        this.dirty = PALETTE_DIRTY;
    }

    /**
     * Computes a hash of the retained palette cycle state.
     *
     * @param hash the preceding deterministic state hash
     * @return the hash including palette records, colors, sources and dirty state
     */
    public int computeStateHash(int hash) {
        hash = HASH_MULTIPLIER * hash + this.dirty;

        for (int index = 0; index < CYCLE_COUNT; index++) {
            hash = HASH_MULTIPLIER * hash + Byte.toUnsignedInt(this.enabled[index]);
            hash = HASH_MULTIPLIER * hash + Byte.toUnsignedInt(this.periods[index]);
            hash = HASH_MULTIPLIER * hash + Byte.toUnsignedInt(this.countdowns[index]);
            hash = HASH_MULTIPLIER * hash + Byte.toUnsignedInt(this.firstColors[index]);
            hash = HASH_MULTIPLIER * hash + Byte.toUnsignedInt(this.phases[index]);
            hash = HASH_MULTIPLIER * hash + Byte.toUnsignedInt(this.lastColors[index]);
            hash = HASH_MULTIPLIER * hash + Byte.toUnsignedInt(this.sourceColorCounts[index]);
        }

        for (int value : this.palette) {
            hash = HASH_MULTIPLIER * hash + value;
        }

        for (int[] source : this.frameColors) {
            hash = HASH_MULTIPLIER * hash + (source == null ? 0 : source.length);

            if (source != null) {
                for (int value : source) {
                    hash = HASH_MULTIPLIER * hash + value;
                }
            }
        }

        return hash;
    }

    private int updateRange(int index, int limit) {
        while (index < CYCLE_COUNT) {
            int period = Byte.toUnsignedInt(this.periods[index]);

            if (period == 0 || Byte.toUnsignedInt(this.firstColors[index]) > limit) {
                break;
            }

            if (this.enabled[index] != 0 && --this.countdowns[index] == 0) {
                this.countdowns[index] = (byte) period;

                int firstColor = Byte.toUnsignedInt(this.firstColors[index]);
                int lastColor = Byte.toUnsignedInt(this.lastColors[index]);
                int[] source = this.frameColors[index];

                if (source != null) {
                    int phase = Byte.toUnsignedInt(++this.phases[index]);
                    int count = Byte.toUnsignedInt(this.sourceColorCounts[index]);

                    if (phase == count) {
                        phase              = 0;
                        this.phases[index] = 0;
                    }

                    for (int color = firstColor; color <= lastColor; color++) {
                        this.palette[color] = source[phase];

                        if (++phase == count) {
                            phase = 0;
                        }
                    }
                } else {
                    int lastValue = this.palette[lastColor];

                    System.arraycopy(this.palette, firstColor, this.palette, firstColor + 1, lastColor - firstColor);
                    this.palette[firstColor] = lastValue;
                }
            }

            index++;
        }

        return index;
    }
}
