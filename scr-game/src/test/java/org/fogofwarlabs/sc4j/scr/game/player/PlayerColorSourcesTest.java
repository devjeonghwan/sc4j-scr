package org.fogofwarlabs.sc4j.scr.game.player;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Checks the color sources of the player colors: the map's {@code COLR} section and the custom color table.
 */
final class PlayerColorSourcesTest {
    /**
     * The bytes of the unit rows, before the minimap indices.
     */
    private static final int UNIT_ROWS_SIZE = PlayerTable.PLAYER_SLOTS * PlayerColorRows.UNIT_ROW;
    /**
     * The step between the palette indices of two rows in the generated rows.
     */
    private static final int ROW_STEP = 16;
    /**
     * The minimap index of row {@code 0} in the generated rows.
     */
    private static final int FIRST_MINIMAP_INDEX = 200;
    /**
     * A map color past the twelve rows, which the section ignores.
     */
    private static final int COLOR_PAST_ROWS = 12;
    /**
     * The packed {@code 0xRRGGBB} orange of a map color.
     */
    private static final int MAP_ORANGE = 0xFF8000;
    /**
     * The packed {@code 0xRRGGBB} pink network color.
     */
    private static final int NETWORK_PINK = 0xFFC4E4;
    /**
     * The opaque alpha of a decoded custom color.
     */
    private static final int OPAQUE_ALPHA = 0xFF;
    /**
     * A channel whose scaled value {@code 254.745} truncates to {@code 254}.
     */
    private static final float TRUNCATED_CHANNEL = 0.999f;
    private static final int TRUNCATED_CHANNEL_BYTE = 254;
    /**
     * A slot past the playable ones whose custom entry a test fills.
     */
    private static final int NEUTRAL_SLOT_WITH_ENTRY = 9;
    private static final int RED_SHIFT = 24;
    private static final int BLUE_SHIFT = 8;

    /**
     * Creates rows whose row {@code r} holds the palette indices {@code r * 16 + i} and the minimap index {@code 200 + r}.
     */
    private static @NotNull PlayerColorRows createRows() {
        PlayerColorRows rows = new PlayerColorRows();

        for (int row = 0; row < PlayerTable.PLAYER_SLOTS; row++) {
            for (int index = 0; index < PlayerColorRows.UNIT_ROW; index++) {
                rows.writeByte(row * PlayerColorRows.UNIT_ROW + index, row * ROW_STEP + index);
            }

            rows.writeByte(UNIT_ROWS_SIZE + row, FIRST_MINIMAP_INDEX + row);
        }

        return rows;
    }

    @Test
    void map_colors_remap_each_slot_from_the_rows_before_the_section() {
        PlayerColorRows rows = createRows();

        // Slots 0 and 1 swap their rows, slot 2 keeps its own color, slot 3 names a color past the rows, slot 4 takes row 11.
        rows.applyMapColors(new byte[]{1, 0, 2, COLOR_PAST_ROWS, 11, 5, 6, 7});

        Assertions.assertEquals(ROW_STEP, rows.getUnitColor(0, 0), "slot 0 takes row 1");
        Assertions.assertEquals(0, rows.getUnitColor(1, 0), "slot 1 takes row 0 as it was before the section");
        Assertions.assertEquals(2 * ROW_STEP, rows.getUnitColor(2, 0), "slot 2 keeps its row");
        Assertions.assertEquals(3 * ROW_STEP, rows.getUnitColor(3, 0), "a color past the rows changes nothing");
        Assertions.assertEquals(11 * ROW_STEP + 7, rows.getUnitColor(4, 7));
        Assertions.assertEquals(FIRST_MINIMAP_INDEX + 11, rows.getMinimapColor(4), "the minimap index follows the row");
        Assertions.assertEquals(FIRST_MINIMAP_INDEX + 1, rows.getMinimapColor(0));

        Assertions.assertEquals(1, rows.getMapColor(0));
        Assertions.assertEquals(0, rows.getMapColor(1), "map color 0 reads as none, as the native table does");
        Assertions.assertEquals(0, rows.getMapColor(2), "a slot's own color is not a map color");
        Assertions.assertEquals(0, rows.getMapColor(3));
        Assertions.assertEquals(11, rows.getMapColor(4));
    }

    @Test
    void a_map_color_section_of_another_size_changes_nothing() {
        PlayerColorRows rows = createRows();

        rows.applyMapColors(new byte[]{1, 0, 2, 3, 4, 5, 6, 7, 8});

        Assertions.assertEquals(0, rows.getUnitColor(0, 0));
        Assertions.assertEquals(0, rows.getMapColor(0));
    }

    @Test
    void players_show_their_row_color_without_custom_colors() {
        PlayerColorRows rows = createRows();

        rows.applyMapColors(new byte[]{4, 1, 2, 3, 4, 5, 6, 7});
        rows.setCustomColors(new byte[PlayerColorRows.CUSTOM_COLORS_SIZE]);

        Assertions.assertFalse(rows.isCustomColorsActive(), "an empty table is not kept");
        Assertions.assertFalse(rows.hasCustomColor(0));
        Assertions.assertEquals(4 * ROW_STEP, rows.getPlayerColor(0, index -> index), "the palette entry of the remapped row's first byte");
    }

    @Test
    void players_show_their_custom_color_when_the_game_keeps_custom_colors() {
        PlayerColorRows rows = createRows();
        int[] colors = {MAP_ORANGE, NETWORK_PINK, -1, -1, -1, -1, -1, -1};

        rows.setCustomColors(PlayerColorRows.encodeCustomColors(colors));

        Assertions.assertTrue(rows.isCustomColorsActive());
        Assertions.assertTrue(rows.hasCustomColor(0));
        Assertions.assertTrue(rows.hasCustomColor(2), "every playable slot shows the table, an empty entry too");
        Assertions.assertFalse(rows.hasCustomColor(PlayerTable.PLAYABLE_PLAYER_SLOTS), "the slots past the playable ones show their rows");
        Assertions.assertEquals(MAP_ORANGE << Byte.SIZE | OPAQUE_ALPHA, rows.getPlayerColor(0, index -> index));
        Assertions.assertEquals(NETWORK_PINK << Byte.SIZE | OPAQUE_ALPHA, rows.getPlayerColor(1, index -> index));
        Assertions.assertEquals(0, rows.getPlayerColor(2, index -> index));
        Assertions.assertEquals(PlayerTable.PLAYABLE_PLAYER_SLOTS * ROW_STEP, rows.getPlayerColor(PlayerTable.PLAYABLE_PLAYER_SLOTS, index -> index));
    }

    @Test
    void minimap_dots_show_the_minimap_index_of_the_row_without_custom_colors() {
        PlayerColorRows rows = createRows();

        rows.applyMapColors(new byte[]{4, 1, 2, 3, 4, 5, 6, 7});
        rows.setCustomColors(null);

        Assertions.assertEquals(FIRST_MINIMAP_INDEX + 4, rows.getMinimapPlayerColor(0, index -> index), "the minimap index of the remapped row");
        Assertions.assertEquals(FIRST_MINIMAP_INDEX + 1, rows.getMinimapPlayerColor(1, index -> index));
        Assertions.assertNotEquals(rows.getPlayerColor(0, index -> index), rows.getMinimapPlayerColor(0, index -> index),
                                   "the minimap index is not the row's first byte");
    }

    @Test
    void minimap_dots_show_the_custom_color_when_the_game_keeps_custom_colors() {
        PlayerColorRows rows = createRows();
        int[] colors = {MAP_ORANGE, NETWORK_PINK, -1, -1, -1, -1, -1, -1};

        rows.setCustomColors(PlayerColorRows.encodeCustomColors(colors));

        for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
            Assertions.assertEquals(rows.getPlayerColor(player, index -> index), rows.getMinimapPlayerColor(player, index -> index),
                                    "player " + player + " shows its units' color");
        }

        Assertions.assertEquals(NETWORK_PINK << Byte.SIZE | OPAQUE_ALPHA, rows.getMinimapPlayerColor(1, index -> index));
        Assertions.assertEquals(0, rows.getMinimapPlayerColor(PlayerTable.PLAYABLE_PLAYER_SLOTS, index -> index),
                                "a slot past the playable ones takes its empty entry, which draws no dot");
    }

    @Test
    void minimap_dots_of_the_slots_past_the_playable_ones_take_their_custom_entries() {
        PlayerColorRows rows = createRows();
        int[] colors = new int[PlayerTable.PLAYER_SLOTS];

        Arrays.fill(colors, -1);

        colors[0]                       = NETWORK_PINK;
        colors[NEUTRAL_SLOT_WITH_ENTRY] = MAP_ORANGE;

        rows.setCustomColors(PlayerColorRows.encodeCustomColors(colors));

        Assertions.assertTrue(rows.isCustomColorsActive());
        Assertions.assertFalse(rows.hasCustomColor(NEUTRAL_SLOT_WITH_ENTRY), "the units of the slot keep their row");
        Assertions.assertEquals(NEUTRAL_SLOT_WITH_ENTRY * ROW_STEP, rows.getPlayerColor(NEUTRAL_SLOT_WITH_ENTRY, index -> index));
        Assertions.assertEquals(MAP_ORANGE << Byte.SIZE | OPAQUE_ALPHA, rows.getMinimapPlayerColor(NEUTRAL_SLOT_WITH_ENTRY, index -> index));
        Assertions.assertEquals(MAP_ORANGE << Byte.SIZE | OPAQUE_ALPHA, rows.getCustomColor(NEUTRAL_SLOT_WITH_ENTRY));
    }

    @Test
    void only_the_playable_entries_make_the_game_keep_its_custom_colors() {
        PlayerColorRows rows = createRows();
        int[] colors = new int[PlayerTable.PLAYER_SLOTS];

        Arrays.fill(colors, -1);

        colors[NEUTRAL_SLOT_WITH_ENTRY] = MAP_ORANGE;

        rows.setCustomColors(PlayerColorRows.encodeCustomColors(colors));

        Assertions.assertFalse(rows.isCustomColorsActive());
        Assertions.assertEquals(FIRST_MINIMAP_INDEX + NEUTRAL_SLOT_WITH_ENTRY, rows.getMinimapPlayerColor(NEUTRAL_SLOT_WITH_ENTRY, index -> index));
    }

    @Test
    void custom_colors_decode_by_clamping_and_truncating_each_channel() {
        ByteBuffer table = ByteBuffer.allocate(PlayerColorRows.CUSTOM_COLORS_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        table.putFloat(TRUNCATED_CHANNEL).putFloat(-1f).putFloat(2f).putFloat(1f);

        Assertions.assertEquals(TRUNCATED_CHANNEL_BYTE << RED_SHIFT | OPAQUE_ALPHA << BLUE_SHIFT | OPAQUE_ALPHA,
                                PlayerColorRows.decodeCustomColor(table.array(), 0));

        for (int channel = 0; channel <= OPAQUE_ALPHA; channel++) {
            int color = channel << Short.SIZE | channel << Byte.SIZE | channel;

            Assertions.assertEquals(color << Byte.SIZE | OPAQUE_ALPHA,
                                    PlayerColorRows.decodeCustomColor(PlayerColorRows.encodeCustomColors(new int[]{color}), 0),
                                    "an encoded channel " + channel + " decodes unchanged");
        }
    }
}
