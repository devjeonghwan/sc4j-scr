package org.fogofwarlabs.sc4j.scr.game.test;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.golden.GoldenDigestUtils;
import org.fogofwarlabs.sc4j.scr.core.test.golden.GoldenValues;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplaySession;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameEnd;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

/**
 * Runs a started {@link ReplayRuntime} to its end and records the end-state values: completed frames, executed commands, the failure or the game's
 * end if any, the final RNG, the observable state digest ({@link StateDigestCheck}) and two traces (the RNG after every frame, the observable digest
 * every {@value #CHECKPOINT_INTERVAL} frames).
 */
public final class ReplayRunScenario {
    /**
     * The frame interval of the observable state checkpoints.
     */
    public static final int CHECKPOINT_INTERVAL = 500;

    /**
     * The end value key of the last completed frame, {@code -1} without a session.
     */
    public static final @NotNull String COMPLETED_FRAMES_KEY = "frames.completed";
    /**
     * The end value key of the failure of the run, {@code none} without a failure.
     */
    public static final @NotNull String FAILURE_KEY = "failure";
    /**
     * The end value key of the frame of the failure, {@code -1} without a failure.
     */
    public static final @NotNull String FAILURE_FRAME_KEY = "failure.frame";
    /**
     * The end value key of the native end state of a game that ended before its last frame.
     */
    public static final @NotNull String END_KEY = "end";
    /**
     * The end value key of the lowercase {@link GameEnd} name of a game that ended before its last frame.
     */
    public static final @NotNull String END_KIND_KEY = "end.kind";
    /**
     * The end value key of the final RNG state.
     */
    public static final @NotNull String RANDOM_STATE_KEY = "rng.state";
    /**
     * The end value key of the final total RNG count.
     */
    public static final @NotNull String RANDOM_TOTAL_KEY = "rng.total";
    /**
     * The end value key of the digest of the RNG after every frame.
     */
    public static final @NotNull String RANDOM_TRACE_KEY = "rng.trace";
    /**
     * The end value key of the digest of the observable state checkpoints.
     */
    public static final @NotNull String OBSERVABLE_TRACE_KEY = "observable.trace";
    /**
     * The end value key of the final observable state digest.
     */
    public static final @NotNull String OBSERVABLE_HASH_KEY = "observable.hash";

    private static final @NotNull String CURRENT_FRAME_KEY = "frames.current";
    private static final @NotNull String EXECUTED_COMMANDS_KEY = "commands.executed";

    private static final @NotNull String NONE = "none";
    private static final @NotNull String CONSTRUCTION_FAILURE = "construction: ";
    /**
     * The end of a game that an EUD failure ended: its last frame, the failing address and the code of the native notice.
     */
    private static final @NotNull String EUD_FAILURE_END_FORMAT = "native EUD error at frame %d, address 0x%08X (EUD ERROR: 0x%08X)";
    /**
     * The end of a game that a Restart Game command ended: its last frame.
     */
    private static final @NotNull String RESTART_END_FORMAT = "native Restart Game at frame %d (replay playback ends)";

    private static final int MAXIMUM_STALLED_STEPS = 10_000;

    /**
     * Describes an exception as a failure value.
     *
     * @param exception the exception
     * @return {@code <simple class name>: <message>}
     */
    public static @NotNull String describeFailure(@NotNull Exception exception) {
        return exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }

    /**
     * Describes an exception of the runtime construction as a failure value.
     *
     * @param exception the exception
     * @return {@code construction: <simple class name>: <message>}
     */
    static @NotNull String describeConstructionFailure(@NotNull RuntimeException exception) {
        return CONSTRUCTION_FAILURE + describeFailure(exception);
    }

    private static @NotNull String digestSafely(@NotNull LongSupplier digest) {
        try {
            return GoldenDigestUtils.format(digest.getAsLong());
        } catch (RuntimeException exception) {
            return "unavailable: " + exception.getClass().getSimpleName();
        }
    }


    private final @NotNull ReplayRuntime runtime;
    private final int hostTicksPerFrame;
    private final @Nullable IntConsumer beforeStep;

    private long randomTrace;
    private long observableTrace;

    private @Nullable String failure;
    private int failureFrame;

    /**
     * Creates a {@link ReplayRunScenario} instance.
     *
     * @param runtime           the runtime after {@link ReplayRuntime#start(int, int)}
     * @param hostTicksPerFrame the host ticks passed per step: {@code 0} for replay playback, the frame duration for a live game
     */
    public ReplayRunScenario(@NotNull ReplayRuntime runtime, int hostTicksPerFrame) {
        this(runtime, hostTicksPerFrame, null);
    }

    /**
     * Creates a {@link ReplayRunScenario} instance that calls a hook before every step, such as a live game's turn exchange.
     *
     * @param runtime           the runtime after {@link ReplayRuntime#start(int, int)}
     * @param hostTicksPerFrame the host ticks passed per step: {@code 0} for replay playback, the frame duration for a live game
     * @param beforeStep        the hook called with the current frame before every step, or {@code null}
     */
    public ReplayRunScenario(@NotNull ReplayRuntime runtime, int hostTicksPerFrame, @Nullable IntConsumer beforeStep) {
        this.runtime           = runtime;
        this.hostTicksPerFrame = hostTicksPerFrame;
        this.beforeStep        = beforeStep;

        this.randomTrace     = GoldenDigestUtils.OFFSET_BASIS;
        this.observableTrace = GoldenDigestUtils.OFFSET_BASIS;

        this.failure      = null;
        this.failureFrame = -1;
    }

    /**
     * Steps the runtime until its session finishes, the game ends, it fails, or {@code lastFrame} completes.
     *
     * @param lastFrame the last frame to run
     */
    public void runTo(int lastFrame) {
        Game game = this.runtime.getGame();
        ReplaySession session = this.runtime.getSession();
        int hostTick = 0;
        int stalledSteps = 0;

        if (this.runtime.getFailure() != null || session == null) {
            this.recordFailure(game, this.runtime.getFailure());

            return;
        }

        try {
            while (!session.isFinished() && !session.isEnded() && game.getCurrentFrame() < lastFrame) {
                int frame = game.getCurrentFrame();

                hostTick += this.hostTicksPerFrame;

                if (this.beforeStep != null) {
                    this.beforeStep.accept(frame);
                }

                this.runtime.step(this.hostTicksPerFrame, this.hostTicksPerFrame == 0 ? frame * this.runtime.getFrameDuration() : hostTick);

                if (game.getCurrentFrame() == frame) {
                    stalledSteps++;

                    if (stalledSteps > MAXIMUM_STALLED_STEPS) {
                        this.failure      = "no frame completed in " + MAXIMUM_STALLED_STEPS + " steps";
                        this.failureFrame = frame;

                        return;
                    }

                    continue;
                }

                stalledSteps = 0;

                this.randomTrace = GoldenDigestUtils.mix(
                        GoldenDigestUtils.mix(GoldenDigestUtils.mix(this.randomTrace, game.getCurrentFrame()), game.getRandom().getState()),
                        game.getRandom().getTotalRandomCounts()
                );

                if (game.getCurrentFrame() % CHECKPOINT_INTERVAL == 0) {
                    this.observableTrace = GoldenDigestUtils.mix(this.observableTrace, (int) StateDigestCheck.digestObservableState(game));
                }
            }
        } catch (RuntimeException exception) {
            this.recordFailure(game, exception);
        }
    }

    /**
     * Puts the end-state values. A game that ended before its last frame also puts {@code end.kind}, the lowercase name of its {@link GameEnd}
     * ({@link ReplaySession#getEnd()}), and {@code end}, which describes the native end state.
     *
     * @param values the values to add to
     */
    public void putValues(@NotNull GoldenValues values) {
        Game game = this.runtime.getGame();
        ReplaySession session = this.runtime.getSession();
        int lastCompletedFrame = session == null ? -1 : session.getLastCompletedFrame();
        GameEnd end = session == null ? null : session.getEnd();
        TriggerContext triggers = game.getTriggerContext();
        LcgRandom random = game.getRandom();

        values.put(COMPLETED_FRAMES_KEY, lastCompletedFrame);
        values.put(CURRENT_FRAME_KEY, game.getCurrentFrame());
        values.put(EXECUTED_COMMANDS_KEY, session == null ? -1 : session.getNextCommandIndex());
        values.put(FAILURE_KEY, this.failure == null ? NONE : this.failure);
        values.put(FAILURE_FRAME_KEY, this.failureFrame);

        if (end != null) {
            values.put(END_KIND_KEY, end.name().toLowerCase(Locale.ROOT));
        }

        if (end == GameEnd.EUD_FAILURE && triggers != null) {
            values.put(END_KEY, String.format(Locale.ROOT, EUD_FAILURE_END_FORMAT, lastCompletedFrame,
                                              triggers.getEudFailureAddress(), ~triggers.getEudFailureAddress()));
        } else if (end == GameEnd.RESTART) {
            values.put(END_KEY, String.format(Locale.ROOT, RESTART_END_FORMAT, lastCompletedFrame));
        }

        values.put(RANDOM_STATE_KEY, Integer.toUnsignedString(random.getState()));
        values.put(RANDOM_TOTAL_KEY, Integer.toUnsignedString(random.getTotalRandomCounts()));
        values.put(RANDOM_TRACE_KEY, GoldenDigestUtils.format(this.randomTrace));
        values.put(OBSERVABLE_TRACE_KEY, GoldenDigestUtils.format(this.observableTrace));
        values.put(OBSERVABLE_HASH_KEY, digestSafely(() -> StateDigestCheck.digestObservableState(game)));
    }

    private void recordFailure(@NotNull Game game, @Nullable Exception exception) {
        if (exception == null && this.runtime.getSession() != null) {
            return;
        }

        this.failure      = exception == null ? "startup produced no session" : describeFailure(exception);
        this.failureFrame = game.getCurrentFrame();
    }
}
