package org.fogofwarlabs.sc4j.scr.client.ui.console;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that the resource row keeps its right edge and text size and moves wide items left without overlap or clipping.
 */
final class ResourceRowLayoutTest {
    /**
     * The lefts and the width of the first three {@code statres} slots, from the right edge leftwards: supply, gas and
     * minerals of a one-race player.
     */
    private static final float @NotNull [] SLOT_LEFTS = {352, 284, 216};
    private static final float SLOT_WIDTH = 66;
    /**
     * The right edge of the bar, the right of the rightmost slot.
     */
    private static final float BAR_RIGHT = 418;
    private static final float ICON_WIDTH = 14;
    private static final float TEXT_GAP = 2;
    /**
     * The advance of every character at the unchanged font size, and the pixel past the text that the inclusive clip right needs.
     */
    private static final float CHARACTER_ADVANCE = 6;
    private static final float CLIP_PIXEL = 1;
    /**
     * The first printable character; color codes advance nothing.
     */
    private static final char FIRST_PRINTABLE = 0x20;

    /**
     * Measures an item's content at the unchanged font size: the icon, the gap and the printable characters of the text, plus
     * the clip pixel.
     *
     * @param text the item's text with color codes
     * @return the content width
     */
    private static float calculateContentWidth(@NotNull String text) {
        long printable = text.chars().filter(character -> character >= FIRST_PRINTABLE).count();

        return ICON_WIDTH + TEXT_GAP + printable * CHARACTER_ADVANCE + CLIP_PIXEL;
    }

    /**
     * Places the texts from the right edge leftwards and checks the row rules: the rightmost item ends at the bar's right
     * edge, every item has room for its whole text at the unchanged character advance, and no item overlaps the item on
     * its right.
     *
     * @param texts the texts from the right edge leftwards
     * @return the item lefts
     */
    private static float @NotNull [] placeItems(@NotNull String @NotNull [] texts) {
        ResourceRowLayout layout = new ResourceRowLayout();
        float[] lefts = new float[texts.length];

        layout.reset();

        for (int item = 0; item < texts.length; item++) {
            float contentWidth = calculateContentWidth(texts[item]);

            lefts[item] = layout.placeItem(SLOT_LEFTS[item], SLOT_LEFTS[item] + SLOT_WIDTH, contentWidth);

            Assertions.assertEquals(lefts[item], layout.getItemLeft());
            Assertions.assertTrue(layout.getItemRight() - lefts[item] >= contentWidth, texts[item]);

            if (item == 0) {
                Assertions.assertEquals(BAR_RIGHT, layout.getItemRight());
            } else {
                Assertions.assertTrue(lefts[item] + contentWidth <= lefts[item - 1], texts[item]);
            }
        }

        return lefts;
    }


    @Test
    void place_item_should_keep_items_in_their_slots_while_their_texts_fit() {
        float[] lefts = placeItems(new String[]{"\u0007200\u0001/200", "1000", "50000"});

        Assertions.assertArrayEquals(SLOT_LEFTS, lefts);
    }

    @Test
    void place_item_should_move_a_wide_mineral_item_left_without_overlap_or_clipping() {
        String minerals = "-2147483648";
        float[] lefts = placeItems(new String[]{"\u0007200\u0001/200", "1000", minerals});

        Assertions.assertEquals(SLOT_LEFTS[0], lefts[0]);
        Assertions.assertEquals(SLOT_LEFTS[1], lefts[1]);
        Assertions.assertEquals(SLOT_LEFTS[2] + SLOT_WIDTH - calculateContentWidth(minerals), lefts[2]);
    }

    @Test
    void place_item_should_move_the_items_on_the_left_of_a_wide_item_only_past_their_free_room() {
        String supply = "\u000732768\u0001/32768";
        float shift = calculateContentWidth(supply) - SLOT_WIDTH;
        float[] narrowGas = placeItems(new String[]{supply, "1000", "50000"});
        float[] wideGas = placeItems(new String[]{supply, "123456789", "50000"});

        Assertions.assertEquals(SLOT_LEFTS[0] - shift, narrowGas[0]);
        Assertions.assertEquals(SLOT_LEFTS[1], narrowGas[1], "The gas slot's free room takes the shift.");
        Assertions.assertEquals(SLOT_LEFTS[2], narrowGas[2]);
        Assertions.assertTrue(wideGas[1] < SLOT_LEFTS[1], "The wide gas text moves left.");
    }
}
