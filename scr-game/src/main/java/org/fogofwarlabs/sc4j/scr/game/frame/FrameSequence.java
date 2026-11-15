package org.fogofwarlabs.sc4j.scr.game.frame;

import org.fogofwarlabs.sc4j.engine.game.frame.PhaseSchedule;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayFrameRunner;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.trigger.ScrTriggerSystem;
import org.jetbrains.annotations.NotNull;

/**
 * Holds the engine frame phases of one SC:R game in the native frame order ({@link Step}), which {@link Game#register}
 * registers as the mod's phase list together with the phase of the trigger system ({@link ScrTriggerSystem}), which runs
 * between {@link Step#FOLIAGE} and {@link Step#DISPLAY_CLOCK}.
 * <p>
 * The SC:R random gate follows the native scopes inside the engine's active range: the frame start restores it, the world
 * step opens and closes it around its phases ({@link GameFrame}), and the trigger system opens and closes it around the
 * trigger evaluation ({@link ReplayFrameRunner#updateTriggers()}). Setup frames run only the world step, as the native startup
 * frames do. The steps after the world step need the established frame caller ({@link Game#requireReplayFrameRunner()}).
 */
public final class FrameSequence {
    /**
     * The steps of a frame in native order.
     */
    public enum Step {
        /**
         * Restores the random gate the frame started with and finishes the advance of the frame counter
         * ({@link ReplayFrameRunner#startFrame()}).
         */
        FRAME_START(PhaseSchedule.RUNNING),
        /**
         * Advances the production statistics by the recorded frame duration
         * ({@link ReplayFrameRunner#advanceProductionStatistics()}).
         */
        PRODUCTION_STATISTICS(PhaseSchedule.RUNNING),
        /**
         * Runs the world phases of the game's {@link FramePhaseList} in one frame scope ({@link GameFrame#run}); setup
         * frames run it too.
         */
        WORLD(PhaseSchedule.RUNNING_AND_SETUP),
        /**
         * Updates the inactive foliage animations when foliage is shown ({@link ReplayFrameRunner#updateFoliage()}).
         */
        FOLIAGE(PhaseSchedule.RUNNING),
        /**
         * Refreshes the display state when the display clock second changes ({@link ReplayFrameRunner#updateDisplayClock()}).
         */
        DISPLAY_CLOCK(PhaseSchedule.RUNNING_AND_PAUSED);

        private final @NotNull PhaseSchedule schedule;

        Step(@NotNull PhaseSchedule schedule) {
            this.schedule = schedule;
        }

        /**
         * @return when the engine frame runs the step
         */
        public @NotNull PhaseSchedule getSchedule() {
            return this.schedule;
        }
    }

    /**
     * Runs one step of the frame sequence of a game.
     */
    private static final class StepPhase implements org.fogofwarlabs.sc4j.engine.game.frame.FramePhase {
        private final @NotNull Game game;
        private final @NotNull Step step;

        private StepPhase(@NotNull Game game, @NotNull Step step) {
            this.game = game;
            this.step = step;
        }

        /**
         * @return the frames this phase runs in: the schedule of its step
         */
        @Override
        public @NotNull PhaseSchedule getSchedule() {
            return this.step.getSchedule();
        }

        /**
         * Runs this phase of the current frame.
         *
         * @param game the game
         */
        @Override
        public void run(org.fogofwarlabs.sc4j.engine.game.@NotNull Game game) {
            switch (this.step) {
                case FRAME_START -> this.game.requireReplayFrameRunner().startFrame();
                case PRODUCTION_STATISTICS -> this.game.requireReplayFrameRunner().advanceProductionStatistics();
                case WORLD -> this.game.requireFrame().run(this.game);
                case FOLIAGE -> this.game.requireReplayFrameRunner().updateFoliage();
                case DISPLAY_CLOCK -> this.game.requireReplayFrameRunner().updateDisplayClock();
            }
        }
    }

    private final @NotNull StepPhase @NotNull [] phases;

    /**
     * Creates a {@link FrameSequence} instance with one phase per step for a game.
     *
     * @param game the game whose frames the phases run
     */
    public FrameSequence(@NotNull Game game) {
        Step[] steps = Step.values();

        this.phases = new StepPhase[steps.length];

        for (Step step : steps) {
            this.phases[step.ordinal()] = new StepPhase(game, step);
        }
    }

    /**
     * Returns the phase of one step.
     *
     * @param step the step
     * @return the phase that runs the step
     */
    public org.fogofwarlabs.sc4j.engine.game.frame.@NotNull FramePhase getPhase(@NotNull Step step) {
        return this.phases[step.ordinal()];
    }
}
