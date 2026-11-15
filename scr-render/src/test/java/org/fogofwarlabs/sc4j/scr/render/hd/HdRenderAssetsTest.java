package org.fogofwarlabs.sc4j.scr.render.hd;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.image.AnimFile;
import org.fogofwarlabs.sc4j.scr.data.format.image.GrpFile;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Tests that {@link HdRenderAssets} anchors the Bunker flash at the Bunker.
 */
@RequiresInstallation
final class HdRenderAssetsTest {
    /**
     * The frame units per game pixel of an HD anim.
     */
    private static final int HD_FRAME_SCALE = 4;
    /**
     * The Jungle tileset.
     */
    private static final int JUNGLE_TILESET = 4;
    /**
     * Images: the Bunker flash overlay and an image without an anchored overlay.
     */
    private static final int BUNKER_FLASH = 306;
    private static final int UNANCHORED_IMAGE = 179;
    /**
     * The last checked Bunker flash direction, the frames between directions and the tolerated center differences.
     */
    private static final int LAST_DIRECTION = 4;
    private static final int DIRECTION_FRAME_STEP = 4;
    private static final double HORIZONTAL_TOLERANCE = 3.5;
    private static final double VERTICAL_TOLERANCE = 6.5;
    /**
     * The shift of the vertical offset in a packed anchored overlay offset.
     */
    private static final int VERTICAL_OFFSET_SHIFT = 16;

    @Test
    void hd_bunker_fire_overlay_is_anchored_at_the_bunker() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            HdRenderAssets assets = HdRenderAssets.load(ScrResourceProvider.borrow(storage), JUNGLE_TILESET, HdRenderAssets.Quality.HD);
            AnimFile.Entry hdEntry = AnimFile.parse(storage.readFile("anim/main_" + BUNKER_FLASH + ".anim")).getEntry(0);
            GrpFile classic = GrpFile.parse(storage.readFile("unit/terran/PillBoxT.grp"));

            for (int index = 0; index <= LAST_DIRECTION; index++) {
                int frame = index * DIRECTION_FRAME_STEP;
                int offset = assets.getAnchoredOverlayOffset(BUNKER_FLASH, frame, false);
                GrpFile.Frame classicFrame = classic.decodeFrame(frame);
                // The classic flash is drawn around the sprite (Bunker + LO offset); the HD flash around the Bunker.
                double classicX = (short) offset + classicFrame.getXOffset() + classicFrame.getFrameWidth() / 2.0 - classic.getWidth() / 2.0;
                double classicY = (short) (offset >>> VERTICAL_OFFSET_SHIFT) + classicFrame.getYOffset() + classicFrame.getFrameHeight() / 2.0 -
                                  classic.getHeight() / 2.0;
                double hdX = (hdEntry.getFrameXOffset(frame) + hdEntry.getFrameWidth(frame) / 2.0 - hdEntry.getWidth() / 2.0) / HD_FRAME_SCALE;
                double hdY = (hdEntry.getFrameYOffset(frame) + hdEntry.getFrameHeight(frame) / 2.0 - hdEntry.getHeight() / 2.0) / HD_FRAME_SCALE;

                Assertions.assertEquals(classicX, hdX, HORIZONTAL_TOLERANCE, "direction " + index + " horizontal");
                Assertions.assertEquals(classicY, hdY, VERTICAL_TOLERANCE, "direction " + index + " vertical");
            }

            Assertions.assertEquals(0, assets.getAnchoredOverlayOffset(UNANCHORED_IMAGE, 0, false), "other images keep their position");
        }
    }
}
