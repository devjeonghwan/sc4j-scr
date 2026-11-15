package org.fogofwarlabs.sc4j.scr.game.golden;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.golden.GoldenValues;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.game.NetworkReplayScenario;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.replay.GameSourceFile;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.test.InstallationTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.ReplayRunScenario;
import org.fogofwarlabs.sc4j.scr.game.test.ReplaySurveyCheck.Classification;
import org.fogofwarlabs.sc4j.scr.game.test.ReplaySurveyCheck.Signal;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * Starts a game on a map as the client does and runs it without commands for {@value #FRAMES} frames, or until the game ends or fails, then
 * classifies the run like the replay detection ({@link Classification}).
 * <p>
 * The game is created with the code constants of the installed executable, the map archive as the game's source file (both only for a map archive,
 * not for a raw scenario) and a fixed seed, as a melee game against one Zerg computer or as a Use Map Settings game. The run is an {@code error} when
 * the game cannot be set up, fails (an exception or a stall), ends at an EUD access that the native game does not serve either, or completes fewer
 * than {@value #FRAMES} frames; it is {@code ok} otherwise.
 */
final class MapDetectionCheck {
    /**
     * The frames every map runs.
     */
    static final int FRAMES = 480;
    /**
     * The column names of {@link #describeReport()}, in order.
     */
    static final @NotNull List<String> REPORT_COLUMNS = List.of("mode", "class", "signals",
                                                                "frames.completed", "failure", "failure.frame", "end",
                                                                "rng.state", "rng.total", "observable.hash");

    private static final @NotNull String MODE_MELEE = "melee";
    private static final @NotNull String MODE_USE_MAP_SETTINGS = "ums";
    private static final @NotNull String NONE = "none";
    private static final @NotNull String SIGNAL_FRAME_SEPARATOR = "@";
    private static final @NotNull String SETUP_FAILURE = "setup: ";
    private static final int NO_FRAME = -1;

    private static final @NotNull String HUMAN_NAME = "tester";
    private static final int INITIALIZATION_SEED = 0x6AB9B92E;

    /**
     * Creates a {@link MapDetectionCheck} instance that starts a game on a map and runs it.
     *
     * @param resources      the installed game resources
     * @param map            the map file, which the game reads as its source file when it is an archive
     * @param rawScenario    {@code true} if the map is a raw scenario ({@code .chk}), {@code false} for an archive
     * @param scenario       the map's scenario
     * @param chk            the parsed scenario
     * @param useMapSettings {@code true} for a Use Map Settings game, {@code false} for a melee game
     * @return the created {@link MapDetectionCheck} instance
     * @throws Exception if the installed resources cannot be read
     */
    static @NotNull MapDetectionCheck run(@NotNull InstallationTestResources resources,
                                          @NotNull Path map, boolean rawScenario, byte @NotNull [] scenario, @NotNull ChkFile chk,
                                          boolean useMapSettings) throws Exception {
        GoldenValues values = new GoldenValues();
        LiveGame game;
        ReplayRuntime runtime;

        try {
            MeleeGameSetup setup = useMapSettings
                                   ? MeleeGameSetup.createUseMapSettings(scenario, HUMAN_NAME, ScrRaceIds.TERRAN, 0, GameSpeed.FASTEST)
                                   : new MeleeGameSetup(scenario, HUMAN_NAME, ScrRaceIds.TERRAN, 0,
                                                        new int[]{ScrRaceIds.ZERG}, GameSpeed.FASTEST);

            game    = new LiveGame(setup, INITIALIZATION_SEED);
            runtime = ReplayRuntime.createLiveGame(game.getReplay(), chk, resources.getGameData(),
                                                   game.getLocalPlayer(),
                                                   RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                   RunningGameScenario.INSTRUCTION_LIMIT);
        } catch (RuntimeException exception) {
            values.put(ReplayRunScenario.FAILURE_KEY, SETUP_FAILURE + ReplayRunScenario.describeFailure(exception));
            values.put(ReplayRunScenario.FAILURE_FRAME_KEY, 0);

            return new MapDetectionCheck(useMapSettings, values);
        }

        Game state = runtime.getGame();

        if (!rawScenario) {
            NetworkReplayScenario.copyCodeConstants(state, resources.getGamePath());
            NetworkReplayScenario.prepare(state);
            runtime.setSourceFile(GameSourceFile.read(map));
        }

        runtime.start(0, 0);

        ReplayRunScenario run = new ReplayRunScenario(runtime, GameSpeed.FASTEST_DURATION, frame -> game.exchangeTurn(state.getCurrentFrame()));

        run.runTo(FRAMES);
        run.putValues(values);

        return new MapDetectionCheck(useMapSettings, values);
    }


    private final boolean useMapSettings;
    private final @NotNull GoldenValues endValues;

    private final int completedFrames;
    private final @NotNull String failure;
    private final int failureFrame;
    private final @NotNull String end;

    private MapDetectionCheck(boolean useMapSettings, @NotNull GoldenValues endValues) {
        this.useMapSettings = useMapSettings;
        this.endValues      = endValues;

        this.completedFrames = Integer.parseInt(endValues.get(ReplayRunScenario.COMPLETED_FRAMES_KEY, Integer.toString(NO_FRAME)));
        this.failure         = endValues.get(ReplayRunScenario.FAILURE_KEY, NONE);
        this.failureFrame    = Integer.parseInt(endValues.get(ReplayRunScenario.FAILURE_FRAME_KEY, Integer.toString(NO_FRAME)));
        this.end             = endValues.get(ReplayRunScenario.END_KEY, NONE);
    }

    /**
     * @return the classification: {@code error} for a failure, an EUD end or a short run, {@code ok} otherwise
     */
    @NotNull Classification getClassification() {
        return NONE.equals(this.describeSignals()) ? Classification.OK : Classification.ERROR;
    }

    /**
     * Describes the signal of the run, the first that applies: {@code failure@<frame>}, {@code eud_failure@<last frame>} or
     * {@code length@<last frame>}.
     *
     * @return the signal, or {@code none}
     */
    @NotNull String describeSignals() {
        if (!NONE.equals(this.failure)) {
            return Signal.FAILURE.getName() + SIGNAL_FRAME_SEPARATOR + this.failureFrame;
        }

        if (!NONE.equals(this.end)) {
            return Signal.EUD_FAILURE.getName() + SIGNAL_FRAME_SEPARATOR + this.completedFrames;
        }

        return this.completedFrames < FRAMES ? Signal.LENGTH.getName() + SIGNAL_FRAME_SEPARATOR + this.completedFrames : NONE;
    }

    /**
     * Describes the outcome for failure messages.
     *
     * @return {@code <class> (<signals>, failure: <failure>, end: <end>)}
     */
    @NotNull String describe() {
        return this.getClassification().getName() + " (" + this.describeSignals() + ", failure: " + this.failure + ", end: " + this.end + ")";
    }

    /**
     * Describes the run as the values of the {@link #REPORT_COLUMNS} of a result table.
     *
     * @return the values in column order
     */
    @NotNull List<String> describeReport() {
        return List.of(this.useMapSettings ? MODE_USE_MAP_SETTINGS : MODE_MELEE, this.getClassification().getName(), this.describeSignals(),
                       Integer.toString(this.completedFrames), this.failure, Integer.toString(this.failureFrame), this.end,
                       this.endValues.get(ReplayRunScenario.RANDOM_STATE_KEY, NONE), this.endValues.get(ReplayRunScenario.RANDOM_TOTAL_KEY, NONE),
                       this.endValues.get(ReplayRunScenario.OBSERVABLE_HASH_KEY, NONE));
    }
}
