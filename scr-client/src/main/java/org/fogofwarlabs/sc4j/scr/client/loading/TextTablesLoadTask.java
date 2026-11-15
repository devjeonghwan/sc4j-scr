package org.fogofwarlabs.sc4j.scr.client.loading;

import org.fogofwarlabs.sc4j.base.log.Logger;
import org.fogofwarlabs.sc4j.engine.client.scene.LoadingTask;
import org.fogofwarlabs.sc4j.scr.client.ClientContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Loads the localized text tables of the client ({@link ClientContext#getTextTables}) as one loading step: at the client start and after a locale
 * change.
 */
public final class TextTablesLoadTask implements LoadingTask {
    private static final @NotNull Logger LOGGER = Logger.of(TextTablesLoadTask.class);

    private final @NotNull ClientContext context;
    private final @NotNull String locale;

    private boolean finished;

    /**
     * Creates a {@link TextTablesLoadTask} instance.
     *
     * @param context the client context that keeps the tables
     * @param locale  the locale such as {@code koKR}
     */
    public TextTablesLoadTask(@NotNull ClientContext context, @NotNull String locale) {
        this.context = context;
        this.locale  = locale;

        this.finished = false;
    }

    /**
     * @return the text shown on the loading screen, for example {@code "Loading graphics"}
     */
    @Override
    public @NotNull String getTitle() {
        return "Loading text";
    }

    /**
     * @return {@code true} if the task is complete or canceled, {@code false} otherwise
     */
    @Override
    public boolean isFinished() {
        return this.finished;
    }


    /**
     * Does up to about {@code budgetNanoseconds} of work on the render thread (with the graphics context current).
     *
     * @param budgetNanoseconds the time after which the call returns
     * @return the progress from {@code 0} to {@code 1}
     */
    @Override
    public float advance(long budgetNanoseconds) {
        if (!this.finished) {
            try {
                this.context.getTextTables(this.locale);
            } catch (IOException exception) {
                LOGGER.warning("Text tables unavailable for " + this.locale + ": " + exception.getMessage());
            }

            this.finished = true;
        }

        return 1;
    }

    /**
     * Stops the task and frees its partial work; afterwards {@link #isFinished()} returns {@code true}.
     */
    @Override
    public void cancel() {
        this.finished = true;
    }
}
