package org.fogofwarlabs.sc4j.scr.render;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the health bar widths and rows of {@link HealthBarLayout}.
 */
final class HealthBarLayoutTest {
    /**
     * The {@code sprites.dat} health bar width of the filled width cases.
     */
    private static final int SPRITE_BAR_WIDTH = 31;

    @Test
    void get_filled_width_should_round_to_three_pixel_groups() {
        Assertions.assertEquals(3, HealthBarLayout.getFilledWidth(0, SPRITE_BAR_WIDTH));
        Assertions.assertEquals(3, HealthBarLayout.getFilledWidth(5, SPRITE_BAR_WIDTH));
        // 50% of (31 - 1) = 15, already a multiple of three.
        Assertions.assertEquals(15, HealthBarLayout.getFilledWidth(50, SPRITE_BAR_WIDTH));
        // Filled widths use width - 1: 45% of 30 = 13 rounds down to 12; 55% = 16 rounds down to 15.
        Assertions.assertEquals(12, HealthBarLayout.getFilledWidth(45, SPRITE_BAR_WIDTH));
        Assertions.assertEquals(15, HealthBarLayout.getFilledWidth(55, SPRITE_BAR_WIDTH));
        // 100% of 30 = 30.
        Assertions.assertEquals(30, HealthBarLayout.getFilledWidth(100, SPRITE_BAR_WIDTH));
    }

    @Test
    void emit_should_draw_health_shield_and_energy_rows_with_separators() {
        HealthBarLayout layout = new HealthBarLayout();

        layout.configure(10, 100, -1, -1);

        Assertions.assertEquals(19, layout.getWidth(), "Bars are at least 19 pixels wide.");
        Assertions.assertEquals(5, layout.getHeight());

        layout.configure(33, 50, 97, 40);

        Assertions.assertEquals(31, layout.getWidth(), "Widths are reduced to 3n + 1.");
        Assertions.assertEquals(13, layout.getHeight());
        Assertions.assertEquals(8, layout.getEnergyTop());
        Assertions.assertEquals(4, layout.getHealthColor());

        List<int[]> rectangles = new ArrayList<>();

        layout.emit((left, top, width, height, colorIndex) -> rectangles.add(new int[]{left, top, width, height, colorIndex}));

        // Health rows 0..6 of the with-shield table at 50% use colors {18, 3, 3, 4, 4, 5, 18} over 15 pixels.
        Assertions.assertArrayEquals(new int[]{0, 1, 15, 1, 3}, rectangles.get(2));
        Assertions.assertArrayEquals(new int[]{15, 1, 16, 1, 15}, rectangles.get(3));
        // Shield rows follow and overwrite rows 0..3; 97% of (33 - 1) = 31 rounds down to 30 of 31 pixels.
        Assertions.assertArrayEquals(new int[]{0, 1, 30, 1, 10}, rectangles.get(16));
        Assertions.assertArrayEquals(new int[]{30, 1, 1, 1, 16}, rectangles.get(17));
        // Energy rows start at row 8; 40% of (33 - 1) = 12.
        Assertions.assertArrayEquals(new int[]{0, 9, 12, 1, 12}, rectangles.get(24));

        // Separators every third column over the seven health rows and the five energy rows.
        int[] separator = rectangles.get(rectangles.size() - 2);

        Assertions.assertArrayEquals(new int[]{30, 0, 1, 7, 18}, separator);
        Assertions.assertArrayEquals(new int[]{30, 8, 1, 5, 18}, rectangles.getLast());
    }
}
