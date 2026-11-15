package org.fogofwarlabs.sc4j.scr.render;

import java.io.IOException;

/**
 * The game's image overlay offsets (the {@code .lo*} resources selected by its live {@code images.dat}), which renderers read at draw time.
 */
@FunctionalInterface
public interface ImageOverlayOffsets {
    /**
     * Reads one signed coordinate pair from an image overlay resource.
     *
     * @param imageTypeIdentifier the {@code images.dat} entry
     * @param overlayType         the overlay table from attack {@code 0} through shield {@code 5}
     * @param frameIndex          the image frame
     * @param overlayIndex        the coordinate pair within that frame
     * @return the horizontal signed WORD in the low half and the vertical signed WORD in the high half
     * @throws IOException if the resource or position is unavailable
     */
    int getOffset(int imageTypeIdentifier, int overlayType, int frameIndex, int overlayIndex) throws IOException;
}
