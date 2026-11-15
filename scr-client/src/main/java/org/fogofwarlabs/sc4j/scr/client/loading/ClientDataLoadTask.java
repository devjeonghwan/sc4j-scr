package org.fogofwarlabs.sc4j.scr.client.loading;

import org.fogofwarlabs.sc4j.base.log.Logger;
import org.fogofwarlabs.sc4j.engine.client.scene.LoadingTask;
import org.fogofwarlabs.sc4j.scr.client.ClientContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Loads locale-independent client data one step at a time during startup: the tables, then the SD frames of the images the mods add. Game start then
 * loads its map and tileset.
 */
public final class ClientDataLoadTask implements LoadingTask {
    private static final @NotNull Logger LOGGER = Logger.of(ClientDataLoadTask.class);

    private final @NotNull ClientContext context;

    private int step;
    private boolean finished;

    /**
     * Creates a {@link ClientDataLoadTask} instance.
     *
     * @param context the client context that keeps the data
     */
    public ClientDataLoadTask(@NotNull ClientContext context) {
        this.context = context;

        this.step     = 0;
        this.finished = false;
    }

    /**
     * @return the text shown on the loading screen, for example {@code "Loading graphics"}
     */
    @Override
    public @NotNull String getTitle() {
        return "Loading data";
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
        long deadlineNanoseconds = System.nanoTime() + budgetNanoseconds;

        while (!this.finished) {
            try {
                this.context.loadClientData(this.step);
            } catch (IOException | RuntimeException exception) {
                LOGGER.warning("Client data " + this.step + " unavailable: " + exception.getMessage());
            }

            this.step++;
            this.finished = this.step >= this.context.getClientDataStepCount();

            if (System.nanoTime() >= deadlineNanoseconds) {
                break;
            }
        }

        return this.finished ? 1 : this.step / (float) this.context.getClientDataStepCount();
    }

    /**
     * Stops the task and frees its partial work; afterwards {@link #isFinished()} returns {@code true}.
     */
    @Override
    public void cancel() {
        this.finished = true;
    }
}
