package org.fogofwarlabs.sc4j.scr.client.ui.minimap;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.fogofwarlabs.sc4j.scr.render.OverlayPass;
import org.fogofwarlabs.sc4j.scr.render.ui.InterfaceAssets;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/**
 * Checks map preview size, scale, terrain sampling and bordered start-location dots.
 */
@RequiresInstallation
final class MinimapPreviewTest {
    private static final int TILESET = 0;
    /**
     * Halves a placement box size to its center offset.
     */
    private static final int CENTER_DIVISOR = 2;
    /**
     * A map side without a preview image.
     */
    private static final int UNSUPPORTED_SIDE = 80;
    private static final int SMALL_SIDE = 64;
    private static final int SMALL_MAP_SCALE = 4;
    private static final int SMALL_MAP_TEXTURE_WIDTH = 256;
    private static final int SMALL_MAP_BITMAP_WIDTH = 128;
    /**
     * A tile identifier of an invalid group ({@code 0x7ff}), which converts to {@link #FALLBACK_TILE}.
     */
    private static final short INVALID_GROUP_TILE = (short) 0x7FF0;
    private static final int FALLBACK_TILE = 0x20;
    private static final int INVALID_GROUP_TILE_INDEX = 1;
    /**
     * The sampled minitile and pixel of a tile at the preview's scale of four pixels per tile.
     */
    private static final int SAMPLED_MINITILE = 5;
    private static final int SAMPLED_PIXEL = 55;
    /**
     * The canvas pixel {@code (4 + 1, 1)} of tile {@code 1}.
     */
    private static final int SAMPLED_CANVAS_X = 5;
    private static final int SAMPLED_CANVAS_Y = 1;
    private static final int START_LOCATION_PLAYER = 0;
    /**
     * The start location's tile column and row.
     */
    private static final int START_LOCATION_TILE = 10;
    private static final int TILE_SIZE = 32;
    private static final int HALF_TILE = 16;
    private static final int PLACEMENT_SIZE_MASK = 0xFF;
    /**
     * The bitmap zoom shift and the tile shift of the 128-pixel box of a 64-tile map.
     */
    private static final int BITMAP_ZOOM = 1;
    private static final int TILE_SHIFT = 5;
    /**
     * The texture pixels per bitmap pixel: {@code 4 * 64 / 128}.
     */
    private static final int TEXTURE_PIXELS_PER_BITMAP_PIXEL = 2;
    private static final int BORDER_COLOR = 0x000000FF;
    private static final int RED_SHIFT = 24;
    private static final int GREEN_SHIFT = 16;
    private static final int BLUE_SHIFT = 8;
    private static final int BYTE_MASK = 0xFF;

    /**
     * Reads one packed RGBA color from the minimap texture.
     *
     * @param image the rendered minimap
     * @param x     the texture column
     * @param y     the texture row
     * @return the packed color
     */
    private static int readPixelColor(@NotNull MinimapImage image, int x, int y) {
        byte[] colorBytes = image.getRgba();
        int offset = (y * image.getTextureWidth() + x) * OverlayPass.RGBA_BYTES;

        return (colorBytes[offset] & BYTE_MASK) << RED_SHIFT | (colorBytes[offset + OverlayPass.RGBA_GREEN] & BYTE_MASK) << GREEN_SHIFT |
               (colorBytes[offset + OverlayPass.RGBA_BLUE] & BYTE_MASK) << BLUE_SHIFT | colorBytes[offset + OverlayPass.RGBA_ALPHA] & BYTE_MASK;
    }

    @Test
    void build_preview_should_sample_terrain_and_draw_bordered_start_location_dots() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            DatTable units = UnitsDat.createSchema().parse(storage.readFile("arr/units.dat"));
            InterfaceAssets assets = InterfaceAssets.load(ScrResourceProvider.borrow(storage), TILESET);

            Assertions.assertNull(MinimapImage.buildPreview(UNSUPPORTED_SIDE, UNSUPPORTED_SIDE,
                                                            new short[UNSUPPORTED_SIDE * UNSUPPORTED_SIDE],
                                                            List.of(),
                                                            UnitsDat.createTypeTable(units),
                                                            assets));

            short[] tiles = new short[SMALL_SIDE * SMALL_SIDE];

            // An invalid group (0x7ff) converts to tile 0x20.
            tiles[INVALID_GROUP_TILE_INDEX] = INVALID_GROUP_TILE;

            // A start location (214) of player 0 at tile (10, 10).
            int startLocation = ScrUnitIds.SPECIAL_START_LOCATION;
            int startLocationPosition = START_LOCATION_TILE * TILE_SIZE + HALF_TILE;
            MinimapImage preview = MinimapImage.buildPreview(SMALL_SIDE, SMALL_SIDE,
                                                             tiles,
                                                             List.of(new MinimapPreviewUnit(startLocationPosition, startLocationPosition,
                                                                                            startLocation, START_LOCATION_PLAYER)),
                                                             UnitsDat.createTypeTable(units),
                                                             assets);

            Assertions.assertNotNull(preview);
            Assertions.assertEquals(SMALL_MAP_SCALE, preview.getScale());
            Assertions.assertEquals(SMALL_MAP_TEXTURE_WIDTH, preview.getTextureWidth());
            Assertions.assertEquals(SMALL_MAP_BITMAP_WIDTH, preview.getWidth());

            int megatile = assets.getMegatile(FALLBACK_TILE);
            int expected = assets.getPaletteColor(assets.getMinitilePixel(megatile, SAMPLED_MINITILE, SAMPLED_PIXEL));

            // Tile 1, canvas pixel (4 + 1, 1): minitile 5, pixel 55, no color-cycling remap.
            Assertions.assertEquals(expected, readPixelColor(preview, SAMPLED_CANVAS_X, SAMPLED_CANVAS_Y));

            int playerColor = assets.getPaletteColor(assets.getMinimapPlayerIndex(START_LOCATION_PLAYER));
            // The start location's rectangle in the 128-pixel box, scaled by 4 * 64 / 128 = 2, with a black border of 1.
            int boxX = ((startLocationPosition - (units.get("Placement Box Width", startLocation) & PLACEMENT_SIZE_MASK) / CENTER_DIVISOR) <<
                        BITMAP_ZOOM) >> TILE_SHIFT;
            int boxY = ((startLocationPosition - (units.get("Placement Box Height", startLocation) & PLACEMENT_SIZE_MASK) / CENTER_DIVISOR) <<
                        BITMAP_ZOOM) >> TILE_SHIFT;

            Assertions.assertEquals(playerColor,
                                    readPixelColor(preview, boxX * TEXTURE_PIXELS_PER_BITMAP_PIXEL, boxY * TEXTURE_PIXELS_PER_BITMAP_PIXEL));
            Assertions.assertEquals(BORDER_COLOR,
                                    readPixelColor(preview, boxX * TEXTURE_PIXELS_PER_BITMAP_PIXEL - 1, boxY * TEXTURE_PIXELS_PER_BITMAP_PIXEL));
        }
    }
}
