package org.fogofwarlabs.sc4j.scr.game.test;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.core.test.golden.GoldenValues;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.replay.GameSourceFile;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayCommandObserver;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plays one replay in replay mode and shares the run between the tests of one test JVM, so that a replay that several tests read is simulated once.
 * <p>
 * {@link InstallationTestResources#getReplayRun(Path)} keeps one instance per replay. The run only moves forward: {@link #inspect(int, Inspection)}
 * advances it to a frame and hands the live {@link Game} to a check that must only read it. The check fails when the frame, the RNG or the observable
 * state ({@link StateDigestCheck}) changed while it ran, and the run is then discarded. A request for a frame the run has passed starts a new run
 * from frame {@code 0}. When the run reaches the replay's last frame it records its end values (those of {@link ReplayRunScenario} and
 * {@link CommandAcceptanceCheck}) and keeps the command validity counts of the run ({@link CommandValidityCheck}). Both stay available after
 * {@link InstallationTestResources} released the live run, so that the detection of the replay ({@link ReplaySurveyCheck#of}) reads the same run.
 */
public final class SharedReplayRunScenario {
    private static final @NotNull String MISSING_END_VALUES = "The replay run stopped before its last frame without end values.";

    /**
     * Reads the live game of a shared run at a frame.
     */
    public interface Inspection {
        /**
         * Checks the game; the game must not be changed.
         *
         * @param game the live game at the requested frame
         * @throws Exception if the check fails
         */
        void check(@NotNull Game game) throws Exception;
    }

    /**
     * Passes every recorded command to the acceptance and the validity observers of one run.
     */
    private static final class RunObserver implements ReplayCommandObserver {
        private final @NotNull CommandAcceptanceCheck acceptance;
        private final @NotNull CommandValidityCheck validity;

        private RunObserver(@NotNull CommandAcceptanceCheck acceptance, @NotNull CommandValidityCheck validity) {
            this.acceptance = acceptance;
            this.validity   = validity;
        }

        /**
         * Called before the command executes.
         *
         * @param game    the simulation state
         * @param command the recorded command
         */
        @Override
        public void onBeforeCommand(@NotNull Game game, @NotNull ReplayCommand command) {
            this.acceptance.onBeforeCommand(game, command);
            this.validity.onBeforeCommand(game, command);
        }

        /**
         * Called after the command executed without an exception.
         *
         * @param game    the simulation state
         * @param command the recorded command
         */
        @Override
        public void onAfterCommand(@NotNull Game game, @NotNull ReplayCommand command) {
            this.acceptance.onAfterCommand(game, command);
            this.validity.onAfterCommand(game, command);
        }
    }

    private final @NotNull GameData gameData;
    private final @NotNull GameSourceFile sourceFile;
    private final @NotNull ReplayFile replay;
    private final int lastFrame;

    private @Nullable ReplayRuntime runtime;
    private @Nullable CommandAcceptanceCheck acceptance;
    private @Nullable CommandValidityCheck validity;
    private @Nullable ReplayRunScenario run;

    private @Nullable GoldenValues endValues;
    private @Nullable CommandValidityCheck endValidity;

    private int startCount;
    private long elapsedNanoseconds;

    /**
     * Creates a {@link SharedReplayRunScenario} instance that starts its run on first use.
     *
     * @param gameData the installed game data, which must stay open while the run is used
     * @param replay   the replay file
     * @throws IOException if the replay cannot be read
     */
    SharedReplayRunScenario(@NotNull GameData gameData, @NotNull Path replay) throws IOException {
        byte[] contents = Files.readAllBytes(replay);

        this.gameData   = gameData;
        this.sourceFile = GameSourceFile.create(replay, contents);
        this.replay     = ReplayFile.parse(contents);
        this.lastFrame  = (int) Math.min(Integer.MAX_VALUE, this.replay.getHeader().getFrames());

        this.runtime    = null;
        this.acceptance = null;
        this.validity   = null;
        this.run        = null;

        this.endValues   = null;
        this.endValidity = null;

        this.startCount         = 0;
        this.elapsedNanoseconds = 0L;
    }

    /**
     * @return the parsed replay
     */
    public @NotNull ReplayFile getReplay() {
        return this.replay;
    }

    /**
     * @return the replay's last frame, the frame count of its header
     */
    public int getLastFrame() {
        return this.lastFrame;
    }

    /**
     * @return how often the replay was started from frame {@code 0} in this JVM
     */
    public int getStartCount() {
        return this.startCount;
    }

    /**
     * @return the nanoseconds spent constructing and running the runs of this replay in this JVM
     */
    public long getElapsedNanoseconds() {
        return this.elapsedNanoseconds;
    }

    /**
     * Returns the end values of the run: completed frames, executed commands, the failure or the game's end if any, the final RNG, the observable
     * state digests and traces, and the per-command acceptance. A replay whose runtime cannot be constructed yields only {@code failure}.
     *
     * @return a copy of the end values
     * @throws IOException           if the installed resources of the run cannot be read
     * @throws IllegalStateException if the run stops before the replay's last frame without end values
     */
    public @NotNull GoldenValues getEndValues() throws IOException {
        if (this.endValues == null) {
            this.advance(this.lastFrame);
        }

        if (this.endValues == null) {
            throw Exceptions.newIllegalStateException(MISSING_END_VALUES);
        }

        return new GoldenValues().putAll(this.endValues, "");
    }

    /**
     * Returns the command validity counts of the run that recorded the end values, advancing the run to the replay's last frame first. A replay whose
     * runtime cannot be constructed yields no counts.
     *
     * @return the command validity counts, which must only be read
     * @throws IOException           if the installed resources of the run cannot be read
     * @throws IllegalStateException if the run stops before the replay's last frame without end values
     */
    public @NotNull CommandValidityCheck getEndValidity() throws IOException {
        if (this.endValidity == null) {
            this.advance(this.lastFrame);
        }

        if (this.endValidity == null) {
            throw Exceptions.newIllegalStateException(MISSING_END_VALUES);
        }

        return this.endValidity;
    }

    /**
     * Advances the run to a frame and lets a check read the live game.
     *
     * @param frame      the frame, at most the replay's last frame
     * @param inspection the check, which must not change the game
     * @throws Exception if the frame is after the last frame, the run cannot start or stops before the frame, or the check fails or changes the game
     */
    public void inspect(int frame, @NotNull Inspection inspection) throws Exception {
        if (frame > this.lastFrame) {
            throw Exceptions.newIllegalArgumentException("Frame {0} is after the replay's last frame {1}.", frame, this.lastFrame);
        }

        this.advance(frame);

        if (this.runtime == null) {
            throw Exceptions.newIllegalStateException("The replay runtime could not be constructed: {0}", this.endValues);
        }

        Game game = this.runtime.getGame();

        if (game.getCurrentFrame() != frame) {
            throw Exceptions.newIllegalStateException("The replay run stopped at frame {0} before frame {1}: {2}",
                                                      game.getCurrentFrame(), frame, this.endValues);
        }

        long digest = StateDigestCheck.digestObservableState(game);
        int randomState = game.getRandom().getState();
        int randomTotal = game.getRandom().getTotalRandomCounts();

        inspection.check(game);

        if (game.getCurrentFrame() != frame || StateDigestCheck.digestObservableState(game) != digest ||
            game.getRandom().getState() != randomState || game.getRandom().getTotalRandomCounts() != randomTotal) {
            this.release();

            throw Exceptions.newIllegalStateException("The inspection changed the shared replay state at frame {0}; " +
                                                      "inspections must only read the game.", frame);
        }
    }

    /**
     * Releases the live run; the end values and their validity counts stay available.
     */
    void release() {
        this.run        = null;
        this.validity   = null;
        this.acceptance = null;
        this.runtime    = null;
    }

    private void advance(int frame) throws IOException {
        long start = System.nanoTime();

        if (this.runtime == null || this.runtime.getGame().getCurrentFrame() > frame) {
            this.start();
        }

        if (this.run != null && this.runtime != null && this.acceptance != null) {
            this.run.runTo(frame);

            // A run that stops before the requested frame has ended (finished session, failure or stall) like one at its end.
            if (this.endValues == null && (frame >= this.lastFrame || this.runtime.getGame().getCurrentFrame() < frame)) {
                GoldenValues values = new GoldenValues();

                this.run.putValues(values);
                this.acceptance.putValues(values);

                this.endValues   = values;
                this.endValidity = this.validity;
            }
        }

        this.elapsedNanoseconds += System.nanoTime() - start;
    }

    private void start() throws IOException {
        this.release();
        this.startCount++;

        try {
            this.runtime = RunningGameScenario.createRuntime(this.replay, this.gameData);
        } catch (RuntimeException exception) {
            this.endValues   = new GoldenValues().put(ReplayRunScenario.FAILURE_KEY, ReplayRunScenario.describeConstructionFailure(exception));
            this.endValidity = new CommandValidityCheck();

            return;
        }

        this.acceptance = new CommandAcceptanceCheck();
        this.validity   = new CommandValidityCheck();

        this.runtime.setSourceFile(this.sourceFile);
        this.runtime.setCommandObserver(new RunObserver(this.acceptance, this.validity));
        this.runtime.start(0, 0);

        this.run = new ReplayRunScenario(this.runtime, 0);
    }
}
