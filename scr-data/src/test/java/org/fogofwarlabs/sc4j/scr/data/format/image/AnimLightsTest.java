package org.fogofwarlabs.sc4j.scr.data.format.image;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainMaskTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Verifies that light tables parse their records, reject invalid content and match the installed anim frames and mask tables.
 */
final class AnimLightsTest {
    private static final int TABLE_OFFSET = 12;
    private static final int LIGHT_RECORD_SIZE = 20;
    private static final int VX4EX_MEGATILE_SIZE = 64;

    @Test
    void parse_should_read_light_records_and_reject_invalid_offsets() throws IOException {
        ByteBuffer contents = ByteBuffer.allocate(TABLE_OFFSET + 3 * 4 + 4 + 2 * LIGHT_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        contents.putInt(3).putInt(0).putInt(0).putInt(0).putInt(24).putInt(0);
        contents.putInt(2);
        contents.put(new byte[LIGHT_RECORD_SIZE]);
        contents.putInt(57).putInt(43).put((byte) 0x98).put((byte) 0xAB).put((byte) 0x8C).put((byte) 0xFF).putInt(28).putInt(76);

        AnimLights lights = AnimLights.parse(contents.array());

        Assertions.assertEquals(3, lights.getImageCount());
        Assertions.assertEquals(0, lights.getFrameCount(0));
        Assertions.assertEquals(2, lights.getFrameCount(1));
        Assertions.assertFalse(lights.hasLight(1, 0));
        Assertions.assertTrue(lights.hasLight(1, 1));
        Assertions.assertEquals(57, lights.getX(1, 1));
        Assertions.assertEquals(43, lights.getY(1, 1));
        Assertions.assertEquals(0x98AB8C, lights.getColor(1, 1));
        Assertions.assertEquals(28, lights.getRadius(1, 1));
        Assertions.assertEquals(76, lights.getIntensity(1, 1));
        Assertions.assertFalse(lights.hasLight(1, 2));
        Assertions.assertFalse(lights.hasLight(7, 0));

        contents.putInt(24, 5);

        Assertions.assertThrows(IOException.class, () -> AnimLights.parse(contents.array()));
    }

    @RequiresInstallation
    @Test
    void installed_lights_and_masks_match_anim_frames_and_record_counts() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            AnimLights lights = AnimLights.parse(storage.readFile("anim/main.lit"));

            Assertions.assertEquals(999, lights.getImageCount());

            ImageRelations relations = ImageRelations.parse(storage.readFile("images.rel"));
            int entryCount = 0;
            int litEntryCount = 0;

            for (int imageIndex = 0; imageIndex < lights.getImageCount(); imageIndex++) {
                if (lights.getFrameCount(imageIndex) == 0) {
                    continue;
                }

                entryCount++;

                boolean anyLight = false;

                for (int frameIndex = 0; frameIndex < lights.getFrameCount(imageIndex); frameIndex++) {
                    anyLight |= lights.hasLight(imageIndex, frameIndex);
                }

                litEntryCount += anyLight ? 1 : 0;

                String path = String.format("HD2/anim/main_%03d.anim", relations.getAnimationImage(imageIndex));

                if (anyLight && storage.hasFile(path)) {
                    Assertions.assertEquals(AnimFile.parse(storage.readFile(path)).getEntry(0).getFrameCount(), lights.getFrameCount(imageIndex),
                                            "main.lit frames of image " + imageIndex);
                }
            }

            Assertions.assertEquals(999, entryCount);
            Assertions.assertEquals(196, litEntryCount);

            int megatileCount = storage.readFile("tileset/jungle.vx4ex").length / VX4EX_MEGATILE_SIZE;
            TerrainMaskTable masks = TerrainMaskTable.parse(storage.readFile("TileSet/Jungle.tmsk"), megatileCount);

            Assertions.assertEquals(629, masks.getRecordCount());
            Assertions.assertFalse(storage.hasFile("TileSet/platform.tmsk"));
        }
    }
}
