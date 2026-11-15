package org.fogofwarlabs.sc4j.scr.client.loading;

import org.fogofwarlabs.sc4j.base.log.Logger;
import org.fogofwarlabs.sc4j.engine.client.scene.LoadingTask;
import org.fogofwarlabs.sc4j.scr.render.hd.HdImageSet;
import org.jetbrains.annotations.NotNull;

/**
 * Loads all images of a graphics quality before game start or after a quality change: the installed images and the images the mods add. The load
 * starts with the task's first step, after the tasks queued before it (the client data, whose game data registers the added images). Cancellation
 * empties the set.
 */
public final class HdImageSetLoadTask implements LoadingTask {
    private static final int NANOSECONDS_PER_MILLISECOND = 1_000_000;

    private static final @NotNull Logger LOGGER = Logger.of(HdImageSetLoadTask.class);

    private final @NotNull HdImageSet images;

    private long startedNanoseconds;
    private boolean started;
    private boolean canceled;

    /**
     * Creates an {@link HdImageSetLoadTask} instance; its first step starts the set's load.
     *
     * @param images the image set to load
     */
    public HdImageSetLoadTask(@NotNull HdImageSet images) {
        this.images = images;

        this.startedNanoseconds = 0L;
        this.started            = false;
        this.canceled           = false;
    }

    /**
     * @return the text shown on the loading screen, for example {@code "Loading graphics"}
     */
    @Override
    public @NotNull String getTitle() {
        return "Loading " + this.images.getQuality().name() + " graphics";
    }

    /**
     * @return {@code true} if the task is complete or canceled, {@code false} otherwise
     */
    @Override
    public boolean isFinished() {
        return this.canceled || this.started && !this.images.isLoading();
    }


    /**
     * Does up to about {@code budgetNanoseconds} of work on the render thread (with the graphics context current).
     *
     * @param budgetNanoseconds the time after which the call returns
     * @return the progress from {@code 0} to {@code 1}
     */
    @Override
    public float advance(long budgetNanoseconds) {
        if (this.canceled) {
            return 1;
        }

        if (!this.started) {
            this.started            = true;
            this.startedNanoseconds = System.nanoTime();
            this.images.startLoad();
        }

        float progress = this.images.continueLoad(budgetNanoseconds);

        if (!this.images.isLoading()) {
            // Every .anim file read by the load is garbage now (up to 90 MB each); a collection returns the heap it grew.
            System.gc();
            LOGGER.debug("HD PRELOAD: {0}{1}, {2} images in {3} ms, {4} MB textures (uploads {5} ms, waiting for reads {6} ms)",
                         this.images.getQuality().name(),
                         this.images.isLighting() ? " lit" : "",
                         this.images.getLoadImageCount(),
                         (System.nanoTime() - this.startedNanoseconds) / NANOSECONDS_PER_MILLISECOND,
                         this.images.getTextureBytes() >> HdImageSet.MEBIBYTE_SHIFT,
                         this.images.getLoadUploadNanoseconds() / NANOSECONDS_PER_MILLISECOND,
                         this.images.getLoadWaitNanoseconds() / NANOSECONDS_PER_MILLISECOND);
        }

        return progress;
    }

    /**
     * Stops the task and frees its partial work; afterwards {@link #isFinished()} returns {@code true}.
     */
    @Override
    public void cancel() {
        if (this.canceled) {
            return;
        }

        this.canceled = true;

        if (this.images.isLoading()) {
            this.images.cancelLoad();
        }
    }
}
