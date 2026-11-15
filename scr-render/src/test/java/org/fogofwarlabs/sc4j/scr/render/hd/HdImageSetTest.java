package org.fogofwarlabs.sc4j.scr.render.hd;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.dat.ImagesDat;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.fogofwarlabs.sc4j.scr.render.HiddenContextScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Tests that {@link HdImageSet} keeps streamed textures of both its lit and its diffuse tables within the texture budget.
 */
@RequiresInstallation
final class HdImageSetTest {
    /**
     * The system property that enables the GPU checks and the message of a skipped check.
     */
    private static final @NotNull String RENDER_SMOKE_PROPERTY = "sc4j.render.smoke";
    private static final @NotNull String RENDER_SMOKE_MESSAGE = "Pass -Psc4j.render.smoke=true to run the GPU check.";
    /**
     * The width and height of the hidden window in pixels.
     */
    private static final int SIZE = 64;
    /**
     * The texture budget of streamed HD2 images in megabytes, and the shift that converts megabytes to bytes.
     */
    private static final long HD2_TEXTURE_BUDGET_MEGABYTES = 384L;
    private static final int MEGABYTE_SHIFT = 20;

    @Test
    void streamed_lit_and_diffuse_textures_stay_within_the_texture_budget() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(SIZE, SIZE, "HD texture budget verification", () -> {
            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath());
                 HdImageSet images = new HdImageSet(HdAnimations.load(ScrResourceProvider.borrow(storage), HdRenderAssets.Quality.HD2, null), true)) {
                long budget = HD2_TEXTURE_BUDGET_MEGABYTES << MEGABYTE_SHIFT;
                long frame = 0;
                long largestResidentBytes = 0;
                Runnable beforeUpload = () -> {
                };

                // One image per frame, its lit layers and then its diffuse layer: textures of older frames make room for the new ones.
                for (int image = 0; image < ImagesDat.IMAGE_COUNT; image++) {
                    frame++;

                    try {
                        images.acquire(image, false, 0, frame, beforeUpload);
                        images.acquireDiffuse(image, false, 0, frame, beforeUpload);
                    } catch (IOException exception) {
                        // An image without an HD anim uploads nothing.
                        continue;
                    }

                    long residentBytes = images.getTextureBytes();

                    largestResidentBytes = Math.max(largestResidentBytes, residentBytes);
                    Assertions.assertTrue(residentBytes <= budget, "image " + image + ": " + (residentBytes >> MEGABYTE_SHIFT) + " MB resident");
                }

                Assertions.assertTrue(largestResidentBytes > budget / 2,
                                      "the images fill the budget: " + (largestResidentBytes >> MEGABYTE_SHIFT) + " MB");
            }
        });
    }
}
