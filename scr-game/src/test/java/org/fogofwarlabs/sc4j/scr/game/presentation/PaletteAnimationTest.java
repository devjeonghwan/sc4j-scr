package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks palette updates, unsigned cycle state and input isolation.
 */
final class PaletteAnimationTest {
    /**
     * The cycle records of one capture record.
     */
    private static final int CAPTURE_CYCLE_COUNT = 8;
    /**
     * The bytes of one cycle record.
     */
    private static final int CAPTURE_CYCLE_STRIDE = 24;
    /**
     * The bytes of the cycle records of one capture record.
     */
    private static final int CAPTURE_RECORD_BYTES = 192;

    /**
     * The cycle record offset of the activation flag.
     */
    private static final int ENABLED_OFFSET = 0;
    /**
     * The cycle record offset of the period.
     */
    private static final int PERIOD_OFFSET = 1;
    /**
     * The cycle record offset of the countdown.
     */
    private static final int COUNTDOWN_OFFSET = 2;
    /**
     * The cycle record offset of the first destination palette index.
     */
    private static final int FIRST_COLOR_OFFSET = 3;
    /**
     * The cycle record offset of the source phase.
     */
    private static final int PHASE_OFFSET = 4;
    /**
     * The cycle record offset of the inclusive last destination palette index.
     */
    private static final int LAST_COLOR_OFFSET = 5;
    /**
     * The cycle record offset of the source pointer, whose first byte is non-zero when the cycle has source colors.
     */
    private static final int SOURCE_OFFSET = 8;
    /**
     * The cycle record offset of a source pointer byte that decoding does not read.
     */
    private static final int UNREAD_SOURCE_BYTE_OFFSET = 9;
    /**
     * The cycle record offset of the source cycle length.
     */
    private static final int SOURCE_COLOR_COUNT_OFFSET = 16;

    /**
     * The tileset cases of the static initialization capture.
     */
    private static final int STATIC_TILESET_CASE_COUNT = 6;
    /**
     * The cases of the palette update capture.
     */
    private static final int UPDATE_CASE_COUNT = 512;
    /**
     * The updates applied to each empty-descriptor animation.
     */
    private static final int EMPTY_DESCRIPTOR_UPDATE_COUNT = 24;
    /**
     * The first tileset of the empty-descriptor capture.
     */
    private static final int FIRST_EMPTY_DESCRIPTOR_TILESET = 1;
    /**
     * The last tileset of the empty-descriptor capture.
     */
    private static final int LAST_EMPTY_DESCRIPTOR_TILESET = 2;

    /**
     * The SHA-256 digest of the static tileset initialization capture.
     */
    private static final @NotNull String STATIC_INITIALIZATION_CAPTURE_HASH = "47913c676ebfa037d5fd22a629a722d84e33f31a2786d42a8569d4a889e92783";
    /**
     * The SHA-256 digest of the empty-descriptor initialization capture.
     */
    private static final @NotNull String EMPTY_INITIALIZATION_CAPTURE_HASH = "fce1d96a5c216a6ed09084c090463a97a26868174cce45093eb17674918f058a";
    /**
     * The SHA-256 digest of the palette update capture.
     */
    private static final @NotNull String UPDATE_CAPTURE_HASH = "1add189e1de04167ee0e91fe86eccc716cc4353d58a18009d49ceb834f76370b";

    /**
     * The first cycle.
     */
    private static final int FIRST_CYCLE = 0;
    /**
     * The first palette color.
     */
    private static final int FIRST_PALETTE_COLOR = 0;
    /**
     * The second palette color.
     */
    private static final int SECOND_PALETTE_COLOR = 1;
    /**
     * The third palette color.
     */
    private static final int THIRD_PALETTE_COLOR = 2;
    /**
     * The color of a palette entry of the zeroed fixture palette that no update writes.
     */
    private static final int UNWRITTEN_COLOR = 0;
    /**
     * The activation flag of an enabled cycle.
     */
    private static final int ENABLED = 1;
    /**
     * An activation flag other than {@code 1}, which the cycle keeps as it is.
     */
    private static final int ENABLED_MARKER = 17;
    /**
     * The source pointer byte of a cycle without source colors.
     */
    private static final int NO_SOURCE = 0;
    /**
     * The source pointer byte of a cycle that has source colors.
     */
    private static final int SOURCE_PRESENT = 1;
    /**
     * The period of a cycle that updates on every update.
     */
    private static final int EVERY_UPDATE_PERIOD = 1;
    /**
     * The countdown of a cycle that updates on the next update.
     */
    private static final int IMMEDIATE_COUNTDOWN = 1;
    /**
     * The first source color of the copied-input cycle.
     */
    private static final int FIRST_SOURCE_COLOR = 1;
    /**
     * The second source color of the copied-input cycle.
     */
    private static final int SECOND_SOURCE_COLOR = 2;
    /**
     * The source colors of the copied-input cycle.
     */
    private static final int COPIED_SOURCE_COLOR_COUNT = 2;
    /**
     * A source color written into the supplied array after the decoding.
     */
    private static final int CHANGED_SOURCE_COLOR = 77;
    /**
     * The period of the failing cycle.
     */
    private static final int FAILING_PERIOD = 3;
    /**
     * The first and last destination palette index of the failing cycle.
     */
    private static final int FAILING_COLOR = 4;
    /**
     * The source phase of the failing cycle before the update.
     */
    private static final int FAILING_PHASE = 7;
    /**
     * The source phase of the failing cycle after the update.
     */
    private static final int ADVANCED_FAILING_PHASE = 8;
    /**
     * The source cycle length of the failing cycle, which its empty source cannot supply.
     */
    private static final int FAILING_SOURCE_COLOR_COUNT = 10;
    /**
     * The period of the wrapping cycle.
     */
    private static final int WRAPPING_PERIOD = 7;
    /**
     * A non-zero source pointer byte that decoding does not read.
     */
    private static final byte UNREAD_SOURCE_BYTE = (byte) 0xFF;
    /**
     * The countdown that a zero countdown wraps to.
     */
    private static final byte WRAPPED_COUNTDOWN = (byte) 0xFF;
    /**
     * The initial redraw flag of the decoded fixture animations.
     */
    private static final int INITIAL_DIRTY = 19;
    /**
     * The initial redraw flag of the captured animations.
     */
    private static final int CAPTURE_DIRTY = 17;
    /**
     * The redraw flag after an update that writes the palette.
     */
    private static final int SET_DIRTY = 1;
    /**
     * The palette step that repeats a color index in every byte of the color.
     */
    private static final int REPEATED_BYTE_STEP = 0x01010101;

    /**
     * Decodes the fixture cycle records without reading their padding bytes.
     *
     * @param records the cycle records
     * @param palette the current palette
     * @param sources the optional color sources in record order
     * @param dirty   the initial redraw flag
     * @return the decoded animation
     * @throws IllegalArgumentException if the source flag of a record does not match {@code sources}
     */
    private static @NotNull PaletteAnimation decodeAnimation(byte @NotNull [] records,
                                                             int @NotNull [] palette,
                                                             int @NotNull [] @Nullable [] sources,
                                                             int dirty) {
        PaletteAnimation animation = new PaletteAnimation(palette, dirty);

        for (int index = 0; index < CAPTURE_CYCLE_COUNT; index++) {
            int offset = index * CAPTURE_CYCLE_STRIDE;

            if ((records[offset + SOURCE_OFFSET] != NO_SOURCE) != (sources[index] != null)) {
                throw Exceptions.newIllegalArgumentException("Fixture source binding is inconsistent.");
            }

            animation.setCycle(index,
                               records[offset + ENABLED_OFFSET],
                               records[offset + PERIOD_OFFSET], records[offset + COUNTDOWN_OFFSET],
                               records[offset + FIRST_COLOR_OFFSET], records[offset + LAST_COLOR_OFFSET],
                               records[offset + PHASE_OFFSET],
                               records[offset + SOURCE_COLOR_COUNT_OFFSET], sources[index]);
        }

        return animation;
    }

    /**
     * Compares every represented cycle field with the recorded unsigned bytes.
     *
     * @param records   the expected cycle records
     * @param animation the animation to inspect
     */
    private static void assertCycles(byte @NotNull [] records, @NotNull PaletteAnimation animation) {
        for (int index = 0; index < CAPTURE_CYCLE_COUNT; index++) {
            int offset = index * CAPTURE_CYCLE_STRIDE;

            Assertions.assertEquals(Byte.toUnsignedInt(records[offset + ENABLED_OFFSET]), animation.getEnabled(index), "Enabled cycle " + index);
            Assertions.assertEquals(Byte.toUnsignedInt(records[offset + PERIOD_OFFSET]), animation.getPeriod(index), "Period cycle " + index);
            Assertions.assertEquals(Byte.toUnsignedInt(records[offset + COUNTDOWN_OFFSET]), animation.getCountdown(index),
                                    "Countdown cycle " + index);
            Assertions.assertEquals(Byte.toUnsignedInt(records[offset + FIRST_COLOR_OFFSET]), animation.getFirstColor(index),
                                    "FirstColor cycle " + index);
            Assertions.assertEquals(Byte.toUnsignedInt(records[offset + PHASE_OFFSET]), animation.getPhase(index), "Phase cycle " + index);
            Assertions.assertEquals(Byte.toUnsignedInt(records[offset + LAST_COLOR_OFFSET]), animation.getLastColor(index),
                                    "LastColor cycle " + index);
            Assertions.assertEquals(Byte.toUnsignedInt(records[offset + SOURCE_COLOR_COUNT_OFFSET]), animation.getSourceColorCount(index),
                                    "SourceColorCount cycle " + index);
        }
    }


    @Test
    void palette_animation_copies_cycle_inputs_and_cycles_source_colors() {
        byte[] records = new byte[CAPTURE_RECORD_BYTES];
        int[][] sources = new int[CAPTURE_CYCLE_COUNT][];

        // Cycle 0 writes its two source colors into palette colors 0 and 1 on every update.
        records[ENABLED_OFFSET]            = ENABLED_MARKER;
        records[PERIOD_OFFSET]             = EVERY_UPDATE_PERIOD;
        records[COUNTDOWN_OFFSET]          = IMMEDIATE_COUNTDOWN;
        records[LAST_COLOR_OFFSET]         = SECOND_PALETTE_COLOR;
        records[SOURCE_OFFSET]             = SOURCE_PRESENT;
        records[SOURCE_COLOR_COUNT_OFFSET] = COPIED_SOURCE_COLOR_COUNT;
        sources[FIRST_CYCLE]               = new int[]{FIRST_SOURCE_COLOR, SECOND_SOURCE_COLOR};

        PaletteAnimation cycle = decodeAnimation(records, new int[PaletteAnimation.COLOR_COUNT], sources, INITIAL_DIRTY);

        records[ENABLED_OFFSET] = 0;
        sources[FIRST_CYCLE][0] = CHANGED_SOURCE_COLOR;

        Assertions.assertEquals(ENABLED_MARKER, cycle.getEnabled(FIRST_CYCLE));
        Assertions.assertEquals(INITIAL_DIRTY, cycle.getDirty());

        cycle.update();

        Assertions.assertEquals(SECOND_SOURCE_COLOR, cycle.getColor(FIRST_PALETTE_COLOR));
        Assertions.assertEquals(FIRST_SOURCE_COLOR, cycle.getColor(SECOND_PALETTE_COLOR), "the cycle keeps a copy of its source colors");
        Assertions.assertEquals(SET_DIRTY, cycle.getDirty());

        cycle.update();

        Assertions.assertEquals(FIRST_SOURCE_COLOR, cycle.getColor(FIRST_PALETTE_COLOR));
        Assertions.assertEquals(SECOND_SOURCE_COLOR, cycle.getColor(SECOND_PALETTE_COLOR));
        Assertions.assertEquals(UNWRITTEN_COLOR, cycle.getColor(THIRD_PALETTE_COLOR));
    }

    @Test
    void update_should_keep_counter_writes_before_unavailable_source_color_failure() {
        byte[] records = new byte[CAPTURE_RECORD_BYTES];

        records[ENABLED_OFFSET]            = ENABLED;
        records[PERIOD_OFFSET]             = FAILING_PERIOD;
        records[COUNTDOWN_OFFSET]          = IMMEDIATE_COUNTDOWN;
        records[FIRST_COLOR_OFFSET]        = FAILING_COLOR;
        records[PHASE_OFFSET]              = FAILING_PHASE;
        records[LAST_COLOR_OFFSET]         = FAILING_COLOR;
        records[SOURCE_OFFSET]             = SOURCE_PRESENT;
        records[SOURCE_COLOR_COUNT_OFFSET] = FAILING_SOURCE_COLOR_COUNT;

        int[][] sources = new int[CAPTURE_CYCLE_COUNT][];

        sources[FIRST_CYCLE] = new int[0];

        PaletteAnimation cycle = decodeAnimation(records, new int[PaletteAnimation.COLOR_COUNT], sources, INITIAL_DIRTY);

        Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, cycle::update);

        records[COUNTDOWN_OFFSET] = FAILING_PERIOD;
        records[PHASE_OFFSET]     = ADVANCED_FAILING_PHASE;

        assertCycles(records, cycle);
        Assertions.assertEquals(INITIAL_DIRTY, cycle.getDirty());

        for (int colorIndex = 0; colorIndex < PaletteAnimation.COLOR_COUNT; colorIndex++) {
            Assertions.assertEquals(UNWRITTEN_COLOR, cycle.getColor(colorIndex));
        }
    }

    @Test
    void update_should_wrap_countdown_as_unsigned_byte() {
        byte[] records = new byte[CAPTURE_RECORD_BYTES];

        records[ENABLED_OFFSET]            = ENABLED;
        records[PERIOD_OFFSET]             = WRAPPING_PERIOD;
        records[UNREAD_SOURCE_BYTE_OFFSET] = UNREAD_SOURCE_BYTE;

        PaletteAnimation cycle = decodeAnimation(records, new int[PaletteAnimation.COLOR_COUNT], new int[CAPTURE_CYCLE_COUNT][], INITIAL_DIRTY);

        cycle.update();

        records[COUNTDOWN_OFFSET] = WRAPPED_COUNTDOWN;

        assertCycles(records, cycle);
        Assertions.assertEquals(SET_DIRTY, cycle.getDirty());
    }

    @Test
    void matches_native_static_tileset_cycle_initialization_cases() throws Exception {
        byte[] bytes = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_PALETTE_STATIC_INITIALIZATION,
                                                           STATIC_INITIALIZATION_CAPTURE_HASH);
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        for (int index = 0; index < STATIC_TILESET_CASE_COUNT; index++) {
            int tileset = input.getInt();
            byte[] expected = new byte[CAPTURE_RECORD_BYTES];

            input.get(expected);

            PaletteAnimation cycle = PaletteAnimation.createForTileset(tileset, new int[PaletteAnimation.COLOR_COUNT], CAPTURE_DIRTY);

            assertCycles(expected, cycle);
            Assertions.assertEquals(CAPTURE_DIRTY, cycle.getDirty());
        }

        Assertions.assertFalse(input.hasRemaining());
    }

    @Test
    void matches_native_empty_descriptor_cycle_initialization_cases() throws Exception {
        byte[] bytes = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_PALETTE_EMPTY_INITIALIZATION, EMPTY_INITIALIZATION_CAPTURE_HASH);
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int[] palette = new int[PaletteAnimation.COLOR_COUNT];

        for (int index = 0; index < palette.length; index++) {
            palette[index] = index * REPEATED_BYTE_STEP;
        }

        for (int expectedTileset = FIRST_EMPTY_DESCRIPTOR_TILESET; expectedTileset <= LAST_EMPTY_DESCRIPTOR_TILESET; expectedTileset++) {
            int tileset = input.getInt();

            Assertions.assertEquals(expectedTileset, tileset);

            byte[] expected = new byte[CAPTURE_RECORD_BYTES];

            input.get(expected);

            PaletteAnimation animation = PaletteAnimation.createForTileset(tileset, palette, CAPTURE_DIRTY);

            assertCycles(expected, animation);
            Assertions.assertEquals(CAPTURE_DIRTY, animation.getDirty());

            for (int update = 0; update < EMPTY_DESCRIPTOR_UPDATE_COUNT; update++) {
                animation.update();
            }

            assertCycles(expected, animation);
            Assertions.assertEquals(SET_DIRTY, animation.getDirty());

            for (int index = 0; index < palette.length; index++) {
                Assertions.assertEquals(palette[index], animation.getColor(index));
            }
        }

        Assertions.assertFalse(input.hasRemaining());
    }

    @Test
    void matches_native_palette_update_cases() throws Exception {
        byte[] bytes = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_PALETTE_CYCLE_UPDATE, UPDATE_CAPTURE_HASH);
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        for (int index = 0; index < UPDATE_CASE_COUNT; index++) {
            byte[] records = new byte[CAPTURE_RECORD_BYTES];

            input.get(records);

            int[] palette = new int[PaletteAnimation.COLOR_COUNT];

            for (int color = 0; color < PaletteAnimation.COLOR_COUNT; color++) {
                palette[color] = input.getInt();
            }

            int[][] sources = new int[CAPTURE_CYCLE_COUNT][];

            for (int sourceIndex = 0; sourceIndex < CAPTURE_CYCLE_COUNT; sourceIndex++) {
                int[] source = new int[PaletteAnimation.COLOR_COUNT];

                for (int color = 0; color < PaletteAnimation.COLOR_COUNT; color++) {
                    source[color] = input.getInt();
                }

                if (records[sourceIndex * CAPTURE_CYCLE_STRIDE + SOURCE_OFFSET] != NO_SOURCE) {
                    sources[sourceIndex] = source;
                }
            }

            PaletteAnimation cycle = decodeAnimation(records, palette, sources, index);

            cycle.update();

            byte[] expectedRecords = new byte[CAPTURE_RECORD_BYTES];

            input.get(expectedRecords);

            assertCycles(expectedRecords, cycle);

            for (int color = 0; color < PaletteAnimation.COLOR_COUNT; color++) {
                Assertions.assertEquals(input.getInt(), cycle.getColor(color), "Palette case " + index + " color " + color);
            }

            Assertions.assertEquals(input.getInt(), cycle.getDirty());
        }

        Assertions.assertFalse(input.hasRemaining());
    }
}
