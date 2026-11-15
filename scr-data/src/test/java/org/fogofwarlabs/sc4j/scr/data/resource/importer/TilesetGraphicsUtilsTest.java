package org.fogofwarlabs.sc4j.scr.data.resource.importer;

import org.fogofwarlabs.sc4j.engine.resource.graphics.Palette;
import org.fogofwarlabs.sc4j.engine.resource.graphics.TileGraphics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Tests the import of tileset megatiles (minitile references with the mirror bit) and {@code .wpe} palettes.
 */
final class TilesetGraphicsUtilsTest {
    @Test
    void create_tile_graphics_should_compose_megatiles_from_mirrored_minitiles() throws IOException {
        byte[] minitiles = new byte[2 * 64];
        ByteBuffer megatiles = ByteBuffer.allocate(2 * 64).order(ByteOrder.LITTLE_ENDIAN);

        for (int pixel = 0; pixel < 64; pixel++) {
            minitiles[pixel]      = (byte) pixel;
            minitiles[64 + pixel] = (byte) (100 + pixel);
        }

        // Megatile 0: minitile 0 everywhere, the second one mirrored. Megatile 1: minitile 1.
        for (int mini = 0; mini < 16; mini++) {
            megatiles.putInt(mini == 1 ? 1 : 0);
        }

        for (int mini = 0; mini < 16; mini++) {
            megatiles.putInt(2);
        }

        TileGraphics graphics = TilesetGraphicsUtils.createTileGraphics(megatiles.array(), minitiles);

        Assertions.assertEquals(2, graphics.getTileCount());
        Assertions.assertEquals(32, graphics.getTileWidth());
        Assertions.assertEquals(9, graphics.getPixels().getValue(9 * 32 + 1));
        Assertions.assertEquals(7, graphics.getPixels().getValue(8));
        Assertions.assertEquals(0, graphics.getPixels().getValue(15));
        Assertions.assertEquals(100, graphics.getPixels().getValue(graphics.getTileOffset(1)));
        Assertions.assertThrows(IOException.class, () -> TilesetGraphicsUtils.createTileGraphics(megatiles.array(), new byte[64]));
    }

    @Test
    void create_palette_should_make_opaque_colors_of_the_wpe_entries() throws IOException {
        byte[] contents = new byte[256 * 4];

        contents[4] = 0x12;
        contents[5] = 0x34;
        contents[6] = 0x56;
        contents[7] = 0x78;

        Palette palette = TilesetGraphicsUtils.createPalette(contents);

        Assertions.assertEquals(256, palette.getSize());
        Assertions.assertEquals(0xFF123456, palette.getColor(1));
        Assertions.assertEquals(0xFF000000, palette.getColor(0));
        Assertions.assertThrows(IOException.class, () -> TilesetGraphicsUtils.createPalette(new byte[1020]));
    }
}
