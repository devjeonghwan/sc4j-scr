package org.fogofwarlabs.sc4j.scr.benchmark;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplaySession;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Measures the simulation speed: plays a replay headless in replay mode and reports milliseconds per {@value #FRAMES_PER_OPERATION} frames.
 * <p>
 * One operation steps {@value #FRAMES_PER_OPERATION} frames. Replay parsing and game creation run in the setup and are not measured; when fewer than
 * {@value #FRAMES_PER_OPERATION} frames of the replay remain, the setup restarts the replay. The installation comes from the system property
 * {@value #DATA_PROPERTY}; the {@code replay} parameter names the replay files.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ReplayPlaybackBenchmark {
    /**
     * The system property naming the SC:R installation.
     */
    public static final @NotNull String DATA_PROPERTY = "sc4j.data";

    private static final int FRAMES_PER_OPERATION = 1000;
    private static final int VIEWPORT_WIDTH = 640;
    private static final int VIEWPORT_HEIGHT = 480;
    private static final int INSTRUCTION_LIMIT = 100_000;
    private static final int MAXIMUM_STALLED_STEPS = 10_000;

    /**
     * The replay file to play; set by JMH from the {@code replay} parameter.
     */
    @Param("")
    public @Nullable String replay;

    private @Nullable GameData gameData;
    private @Nullable ReplayFile replayFile;
    private @Nullable ChkFile mapFile;
    private long replayFrames;

    private @Nullable ReplayRuntime runtime;
    private @Nullable Game game;
    private @Nullable ReplaySession session;

    /**
     * Creates a {@link ReplayPlaybackBenchmark} instance; JMH sets the parameters and calls the setup methods.
     */
    public ReplayPlaybackBenchmark() {
        this.replay = null;

        this.gameData     = null;
        this.replayFile   = null;
        this.mapFile      = null;
        this.replayFrames = 0L;

        this.runtime = null;
        this.game    = null;
        this.session = null;
    }

    /**
     * Loads the installed game data and parses the replay.
     *
     * @throws IOException           if the installation or the replay cannot be read
     * @throws IllegalStateException if the installation property or the replay parameter is not set, or the replay holds no map
     */
    @Setup(Level.Trial)
    public void loadInputs() throws IOException {
        String installationPath = System.getProperty(DATA_PROPERTY, "");
        String replayPath = this.replay;
        byte[] mapData;

        if (installationPath.isBlank()) {
            throw Exceptions.newIllegalStateException("The SC:R installation is required: set the system property {0}.", DATA_PROPERTY);
        }

        if (replayPath == null || replayPath.isBlank()) {
            throw Exceptions.newIllegalStateException("The replay parameter names no file.");
        }

        this.gameData     = GameData.loadFrom(Path.of(installationPath));
        this.replayFile   = ReplayFile.parse(Files.readAllBytes(Path.of(replayPath)));
        this.replayFrames = this.replayFile.getHeader().getFrames();
        mapData           = this.replayFile.getMapData();

        if (mapData == null) {
            throw Exceptions.newIllegalStateException("The replay {0} holds no map.", replayPath);
        }

        this.mapFile = ChkFile.parseTolerant(mapData);
    }

    /**
     * Starts the replay from its first frame when fewer than {@value #FRAMES_PER_OPERATION} frames remain.
     *
     * @throws IOException           if a required installed resource cannot be loaded
     * @throws IllegalStateException if the inputs are not loaded or the replay starts no replay session
     */
    @Setup(Level.Invocation)
    public void prepareFrames() throws IOException {
        Game currentGame = this.game;
        ReplaySession currentSession = this.session;

        if (currentGame != null && currentSession != null && !currentSession.isFinished() &&
            currentGame.getCurrentFrame() + FRAMES_PER_OPERATION <= this.replayFrames) {
            return;
        }

        ReplayRuntime started = new ReplayRuntime(this.requireReplayFile(),
                                                  this.requireMapFile(),
                                                  this.requireGameData(),
                                                  VIEWPORT_WIDTH, VIEWPORT_HEIGHT,
                                                  INSTRUCTION_LIMIT);

        started.start(0, 0);

        this.runtime = started;
        this.game    = started.getGame();
        this.session = started.getSession();

        if (this.session == null) {
            throw Exceptions.newIllegalStateException("The replay {0} started no replay session.", this.replay);
        }
    }

    private @NotNull GameData requireGameData() {
        GameData loaded = this.gameData;

        if (loaded == null) {
            throw Exceptions.newIllegalStateException("The game data is not loaded.");
        }

        return loaded;
    }

    private @NotNull ReplayFile requireReplayFile() {
        ReplayFile loaded = this.replayFile;

        if (loaded == null) {
            throw Exceptions.newIllegalStateException("The replay is not loaded.");
        }

        return loaded;
    }

    private @NotNull ChkFile requireMapFile() {
        ChkFile loaded = this.mapFile;

        if (loaded == null) {
            throw Exceptions.newIllegalStateException("The replay map is not loaded.");
        }

        return loaded;
    }

    /**
     * Steps {@value #FRAMES_PER_OPERATION} frames of the replay.
     *
     * @return the current frame, consumed by JMH
     * @throws IllegalStateException if the replay is not started or stops advancing
     */
    @Benchmark
    public int stepFrames() {
        ReplayRuntime currentRuntime = this.runtime;
        Game currentGame = this.game;
        int lastFrame;
        int stalledSteps = 0;

        if (currentRuntime == null || currentGame == null) {
            throw Exceptions.newIllegalStateException("The replay is not started.");
        }

        lastFrame = currentGame.getCurrentFrame() + FRAMES_PER_OPERATION;

        while (currentGame.getCurrentFrame() < lastFrame && stalledSteps <= MAXIMUM_STALLED_STEPS) {
            int frame = currentGame.getCurrentFrame();

            currentRuntime.step(0, frame * currentRuntime.getFrameDuration());
            stalledSteps = currentGame.getCurrentFrame() == frame ? stalledSteps + 1 : 0;
        }

        if (stalledSteps > MAXIMUM_STALLED_STEPS) {
            throw Exceptions.newIllegalStateException("The replay {0} stopped advancing at frame {1}.", this.replay, currentGame.getCurrentFrame());
        }

        return currentGame.getCurrentFrame();
    }

    /**
     * Closes the installed game data.
     */
    @TearDown(Level.Trial)
    public void closeInputs() {
        GameData loaded = this.gameData;

        this.session  = null;
        this.game     = null;
        this.runtime  = null;
        this.gameData = null;

        if (loaded != null) {
            loaded.close();
        }
    }
}
