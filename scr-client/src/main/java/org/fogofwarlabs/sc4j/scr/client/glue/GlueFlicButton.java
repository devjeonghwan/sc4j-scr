package org.fogofwarlabs.sc4j.scr.client.glue;

import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.ScrControl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Holds the runtime state of one glue {@code FLCBTN}: its videos of both definition modes and its hover level, which its hover timer
 * moves toward full while the pointer is on the button. The screen attaches it at open and its procedure painter draws it.
 */
final class GlueFlicButton extends ScrControl {
    private static final int HIGHLIGHT_STEP = 15;
    private static final int HIGHLIGHT_MAXIMUM = 100;

    private final @NotNull List<GlueVideo> videos;
    private int level;

    /**
     * Creates a {@link GlueFlicButton} instance at hover level {@code 0}.
     *
     * @param definition the {@code FLCBTN} definition
     * @param videos     the videos of its {@code FLC} children with content, in child order
     */
    GlueFlicButton(@NotNull DialogControl definition, @NotNull List<GlueVideo> videos) {
        super(definition);

        this.videos = videos;
        this.level  = 0;
    }

    /**
     * @return the videos of the {@code FLC} children with content, in child order
     */
    @NotNull List<GlueVideo> getVideos() {
        return this.videos;
    }

    /**
     * @return the hover level from {@code 0} through {@code 1}
     */
    float getHoverLevel() {
        return this.level / (float) HIGHLIGHT_MAXIMUM;
    }

    /**
     * Advances the hover timer: the hover level moves {@code 15} toward {@code 100} while the button is hovered, else toward
     * {@code 0}, and the fading videos follow.
     */
    void tick() {
        boolean hovered = this.isHovered();
        int step = hovered ? HIGHLIGHT_STEP : -HIGHLIGHT_STEP;

        this.level = Math.clamp(this.level + step, 0, HIGHLIGHT_MAXIMUM);

        for (GlueVideo video : this.videos) {
            video.tick(hovered);
        }
    }
}
