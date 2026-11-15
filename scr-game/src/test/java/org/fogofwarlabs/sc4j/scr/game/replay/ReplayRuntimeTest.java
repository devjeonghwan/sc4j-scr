package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.core.test.golden.GoldenValues;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayHeaderBuilder;
import org.fogofwarlabs.sc4j.scr.game.frame.GameExecutionScope;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.ProductionStatistics;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.*;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.LocalView;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.visibility.LocalVision;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks construction bounds, partial startup, independent replay timing, the independence of the simulation from the host tick spacing, the recorded
 * player colors, complete runs of recorded replays, the start screen of replay playback and of an observer and the end of a replay at an EUD
 * failure or at a stale Restart Game command.
 */
final class ReplayRuntimeTest {
    private static final int BROOD_WAR_ENGINE = 1;
    /**
     * The header frame count of the encoded replay with a skipped command block.
     */
    private static final int ENCODED_REPLAY_FRAMES = 10;
    /**
     * An opcode no command block parser knows, so the parser skips its block.
     */
    private static final int UNKNOWN_OPCODE = 0xFF;

    /**
     * The host clock at startup, and an elapsed host tick count at startup that the host-timing diagnostics reject.
     */
    private static final int STARTUP_HOST_TICK = 10000;
    private static final int DIAGNOSTIC_ELAPSED_HOST_TICKS = 7501;
    /**
     * The frame phase index a completed startup leaves.
     */
    private static final int STARTED_FRAME_PHASE_INDEX = 2;
    /**
     * The frame duration of the fast game speed, in milliseconds, which paces the replay.
     */
    private static final int FAST_FRAME_DURATION = 56;
    /**
     * The fastest game speed, which the replay header records.
     */
    private static final int FASTEST_SPEED = 6;
    /**
     * The production statistics sample field the first frame of {@link GameTestResources#REPLAY_ZVP_FIGHTING_SPIRIT} records.
     */
    private static final int FIRST_FRAME_SAMPLE_FIELD = 42;
    /**
     * The least frames and commands a complete run of {@link GameTestResources#REPLAY_ZVP_FIGHTING_SPIRIT} completes.
     */
    private static final int MINIMUM_COMPLETED_FRAMES = 20;
    private static final int MINIMUM_EXECUTED_COMMANDS = 9;

    private static final @NotNull String NO_FAILURE = "none";

    /**
     * The frame at which the civilization defense and the EUD computer replays read an EUD address without a native region.
     */
    private static final int EUD_FAILURE_FRAME = 2;
    /**
     * The value the native EUD error notice shows for the civilization defense access: the bitwise complement of the accessed EUD address.
     */
    private static final int EUD_FAILURE_CODE = 0xD7279F57;
    private static final @NotNull String EUD_FAILURE_END = "native EUD error at frame 2, address 0x28D860A8 (EUD ERROR: 0xD7279F57)";
    /**
     * The value the native EUD error notice shows for the EUD computer access: the bitwise complement of the accessed EUD address.
     */
    private static final int EUD_COMPUTER_FAILURE_CODE = 0xAB8B929F;
    private static final @NotNull String EUD_COMPUTER_FAILURE_END = "native EUD error at frame 2, address 0x54746D60 (EUD ERROR: 0xAB8B929F)";
    /**
     * The frame of the stale Restart Game command that ends the playback of {@link GameTestResources#REPLAY_STALE_RESTART_GAME}, and the random state
     * DWORD and random count the native game keeps after that end.
     */
    private static final int STALE_RESTART_GAME_FRAME = 14988;
    private static final @NotNull String STALE_RESTART_GAME_RANDOM_STATE = "441254477";
    private static final @NotNull String STALE_RESTART_GAME_RANDOM_TOTAL = "54809";
    private static final @NotNull String STALE_RESTART_GAME_END = "native Restart Game at frame 14988 (replay playback ends)";
    /**
     * The character set of the map source path that the replay records.
     */
    private static final @NotNull Charset SOURCE_PATH_CHARSET = Charset.forName("x-windows-949");
    private static final int CHECKPOINT_FRAME_INTERVAL = 500;
    /**
     * The host spacing value that paces the replay by its speed profile.
     */
    private static final int PROFILE_PACING = -1;
    /**
     * A host spacing far above any frame duration.
     */
    private static final int WIDE_HOST_TICKS_PER_FRAME = 5000;
    /**
     * The player colors the replay records for the first eight slots.
     */
    private static final int @NotNull [] RECORDED_COLORS = {3, 7, 1, 5, 4, 6, 2, 0};
    /**
     * A color written into the returned copy of the recorded colors.
     */
    private static final int OVERWRITTEN_COLOR = 15;
    /**
     * The screen positions native replay playback of a 640 x 480 game screen starts on: {@link GameTestResources#REPLAY_TOP_RIGHT_START} at the
     * largest screen X, {@link GameTestResources#REPLAY_EUD_WORM_GROWING} inside the map.
     */
    private static final int TOP_RIGHT_START_SCREEN_X = 3456;
    private static final int TOP_RIGHT_START_SCREEN_Y = 81;
    private static final int WORM_START_SCREEN_X = 1024;
    private static final int WORM_START_SCREEN_Y = 673;
    /**
     * A 1280 x 960 game screen and the screen position {@link GameTestResources#REPLAY_EUD_WORM_GROWING} starts on with it: the start location
     * (1344,864) less half the width and half the 766 play view rows.
     */
    private static final int LARGE_SCREEN_WIDTH = 1280;
    private static final int LARGE_SCREEN_HEIGHT = 960;
    private static final int WORM_LARGE_START_SCREEN_X = 704;
    private static final int WORM_LARGE_START_SCREEN_Y = 481;

    /**
     * Runs a replay with the given host spacing and records state checkpoints.
     *
     * @param gameData          the installed game resources
     * @param replay            the parsed replay
     * @param hostTicksPerFrame the host spacing, or a negative value such as {@link #PROFILE_PACING} for profile pacing
     * @return the ordered completed-frame state checkpoints
     * @throws Exception if resources cannot be read or replay execution fails
     */
    private static @NotNull List<String> runReplayAndReadCheckpoints(@NotNull GameData gameData,
                                                                     @NotNull ReplayFile replay,
                                                                     int hostTicksPerFrame) throws Exception {
        List<String> checkpoints = new ArrayList<>();
        ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

        runtime.start(0, 0);

        int hostTick = 0;

        while (true) {
            ReplaySession sessionBeforeStep = runtime.getSession();

            Assertions.assertNotNull(sessionBeforeStep);

            if (sessionBeforeStep.isFinished()) {
                break;
            }

            if (hostTicksPerFrame < 0) {
                runtime.step(0, runtime.getGame().getCurrentFrame() * runtime.getFrameDuration());
            } else {
                hostTick += hostTicksPerFrame;
                runtime.step(hostTicksPerFrame, hostTick);
            }

            ReplaySession sessionAfterStep = runtime.getSession();

            Assertions.assertNotNull(sessionAfterStep);

            long completed = sessionAfterStep.getLastCompletedFrame();

            if (completed % CHECKPOINT_FRAME_INTERVAL == 0 || sessionAfterStep.isFinished()) {
                checkpoints.add(completed + " " + runtime.getGame().getRandom().getState() + "/" +
                                runtime.getGame().getRandom().getTotalRandomCounts() + " " + runtime.getGame().computeStateHash());
            }
        }

        return checkpoints;
    }

    /**
     * Reads a replay resource.
     *
     * @param resource the replay resource
     * @return the parsed replay
     * @throws IOException if the replay cannot be read
     */
    private static @NotNull ReplayFile readReplay(@NotNull String resource) throws IOException {
        return ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(resource)));
    }

    /**
     * Starts a runtime without a display and reads the screen position of its local view.
     *
     * @param runtime the runtime
     * @return the screen {@code {x, y}} after startup
     */
    private static int @NotNull [] readStartScreen(@NotNull ReplayRuntime runtime) {
        runtime.start(0, 0);

        Assertions.assertNull(runtime.getFailure());

        LocalView view = runtime.getGame().getInterfaceLink().getLocalView();

        Assertions.assertTrue(view.isKnown());

        return new int[]{view.getScreenX(), view.getScreenY()};
    }


    @Test
    void replay_runtime_rejects_invalid_execution_bounds_before_reading_dependencies() {
        int[][] invalidControls = {{0, RunningGameScenario.VIEWPORT_HEIGHT, RunningGameScenario.INSTRUCTION_LIMIT},
                                   {RunningGameScenario.VIEWPORT_WIDTH, -1, RunningGameScenario.INSTRUCTION_LIMIT},
                                   {RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT, 0},
                                   {RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT, -1}};

        for (int[] controls : invalidControls) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> new ReplayRuntime(null, null, null, controls[0], controls[1], controls[2]));
        }
    }

    @Test
    void replay_runtime_rejects_skipped_command_blocks_before_reading_map_or_resources() throws IOException {
        byte[] header = new byte[ReplayHeaderBuilder.HEADER_SIZE];

        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                  .put(ReplayHeaderBuilder.ENGINE_OFFSET, (byte) BROOD_WAR_ENGINE).putInt(ReplayHeaderBuilder.FRAMES_OFFSET, ENCODED_REPLAY_FRAMES);

        byte[] unknownCommand = {0, (byte) UNKNOWN_OPCODE};
        byte[] commands = ByteBuffer.allocate(Integer.BYTES + 1 + unknownCommand.length).order(ByteOrder.LITTLE_ENDIAN)
                                    .putInt(0).put((byte) unknownCommand.length).put(unknownCommand).array();
        ReplayFile replay = ReplayFile.parse(ReplayFileScenario.encode(header, commands));

        Assertions.assertEquals(1, replay.getUnknownTypeCount());
        Assertions.assertTrue(replay.getCommands().isEmpty());

        UnsupportedOperationException failure = Assertions.assertThrows(UnsupportedOperationException.class,
                                                                        () -> new ReplayRuntime(replay, null, null,
                                                                                                RunningGameScenario.VIEWPORT_WIDTH,
                                                                                                RunningGameScenario.VIEWPORT_HEIGHT,
                                                                                                RunningGameScenario.INSTRUCTION_LIMIT));

        Assertions.assertTrue(failure.getMessage().contains("skipped unknown command blocks"));
    }

    @RequiresInstallation
    @Test
    void start_should_retain_partial_startup_and_close_random_scope_on_host_diagnostic_failure() throws Exception {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_ZVP_FIGHTING_SPIRIT)));

        try (GameData gameData = GameData.load(CascStorage.open(ExternalInput.INSTALLATION.getPath()))) {
            byte[] mapData = replay.getMapData();

            Assertions.assertNotNull(mapData);

            ReplayRuntime runtime = new ReplayRuntime(replay, ChkFile.parseTolerant(mapData), gameData,
                                                      RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                      RunningGameScenario.INSTRUCTION_LIMIT);
            Game state = runtime.getGame();

            Assertions.assertThrows(IllegalStateException.class, () -> runtime.step(0, STARTUP_HOST_TICK));

            runtime.start(DIAGNOSTIC_ELAPSED_HOST_TICKS, STARTUP_HOST_TICK);

            Assertions.assertInstanceOf(UnsupportedOperationException.class, runtime.getFailure());

            Throwable diagnosticFailure = runtime.getFailure();

            Assertions.assertNotNull(diagnosticFailure);
            Assertions.assertTrue(diagnosticFailure.getMessage().contains("host-timing diagnostics"));
            Assertions.assertEquals("startup game phase 0", runtime.getPhase());
            Assertions.assertSame(state, runtime.getGame());
            Assertions.assertEquals(0, state.getCurrentFrame());

            GameFrame frame = state.getFrame();

            Assertions.assertNotNull(frame);
            Assertions.assertEquals(0, frame.getPhaseIndex());
            Assertions.assertEquals(0, state.getRandom().getEnabled());

            GameExecutionScope scope = state.getExecutionScope();

            Assertions.assertNotNull(scope);
            Assertions.assertEquals(-1, scope.getFramePhase());
            Assertions.assertFalse(scope.isInitializationActive());
            Assertions.assertFalse(state.hasInitializedWorldState());
            Assertions.assertNull(runtime.getSession());

            long interruptedHash = state.computeStateHash();

            Assertions.assertThrows(IllegalStateException.class, () -> runtime.start(0, STARTUP_HOST_TICK + 1));
            Assertions.assertThrows(IllegalStateException.class, () -> runtime.step(0, STARTUP_HOST_TICK + 1));
            Assertions.assertEquals(interruptedHash, state.computeStateHash());
        }
    }

    @RequiresInstallation
    @Test
    void replay_runtime_keeps_profile_pacing_separate_from_recorded_accounting_duration() throws Exception {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_ZVP_FIGHTING_SPIRIT)));

        try (GameData gameData = GameData.load(CascStorage.open(ExternalInput.INSTALLATION.getPath()))) {
            byte[] mapData = replay.getMapData();

            Assertions.assertNotNull(mapData);

            ReplayRuntime runtime = new ReplayRuntime(replay, ChkFile.parseTolerant(mapData), gameData,
                                                      RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                      RunningGameScenario.INSTRUCTION_LIMIT);

            runtime.start(0, STARTUP_HOST_TICK);

            Assertions.assertNull(runtime.getFailure());
            Assertions.assertNotNull(runtime.getSession());

            Game state = runtime.getGame();

            Assertions.assertTrue(state.hasInitializedWorldState());

            GameFrame frame = state.getFrame();

            Assertions.assertNotNull(frame);
            Assertions.assertEquals(STARTED_FRAME_PHASE_INDEX, frame.getPhaseIndex());
            Assertions.assertEquals(0, state.getCurrentFrame());

            ProductionStatistics initialStatistics = state.getPlayerTable().getProductionStatistics();

            Assertions.assertNotNull(initialStatistics);
            Assertions.assertEquals(1, initialStatistics.getSampleCount());
            Assertions.assertEquals(FAST_FRAME_DURATION, runtime.getFrameDuration());
            Assertions.assertEquals(FASTEST_SPEED, replay.getHeader().getSpeed());
            Assertions.assertTrue(runtime.step(0, STARTUP_HOST_TICK + GameSpeed.FAST_DURATION));

            ReplaySession advancedSession = runtime.getSession();

            Assertions.assertNotNull(advancedSession);
            Assertions.assertEquals(1, advancedSession.getLastCompletedFrame());

            ProductionStatistics advancedStatistics = state.getPlayerTable().getProductionStatistics();

            Assertions.assertNotNull(advancedStatistics);
            Assertions.assertEquals(FIRST_FRAME_SAMPLE_FIELD, advancedStatistics.getSampleField(1, 1));
            Assertions.assertEquals(0, state.getRandom().getEnabled());
        }
    }

    /**
     * Completion means that every recorded command ran through the common command path and the session reached the header frame count; it does not
     * claim state equivalence with the original client.
     */
    @RequiresInstallation
    @ExtendWith(GameResourcesExtension.class)
    @Test
    void shared_replay_run_completes_every_frame_and_command(@NotNull InstallationTestResources resources) throws Exception {
        SharedReplayRunScenario run = resources.getReplayRun(GameTestResources.getPath(GameTestResources.REPLAY_PVP_FORTRESS));
        GoldenValues values = run.getEndValues();

        Assertions.assertEquals(NO_FAILURE, values.get("failure"));
        Assertions.assertEquals(Long.toString(run.getReplay().getHeader().getFrames()), values.get("frames.completed"));
        Assertions.assertEquals(Integer.toString(run.getReplay().getCommands().size()), values.get("commands.executed"));
    }

    /**
     * The game keeps the replay file's path; the native game shows its EUD error notice at the failing frame and ends the session there instead of
     * playing on.
     */
    @RequiresInstallation
    @ExtendWith(GameResourcesExtension.class)
    @Test
    void eud_map_replay_ends_at_its_native_eud_failure(@NotNull InstallationTestResources resources) throws Exception {
        Path source = GameTestResources.getPath(GameTestResources.REPLAY_CIVILIZATION_DEFENSE_EUD_FAILURE);
        SharedReplayRunScenario run = resources.getReplayRun(source);

        run.inspect(EUD_FAILURE_FRAME, game -> {
            TriggerContext triggers = game.getTriggerContext();

            Assertions.assertNotNull(triggers);
            Assertions.assertArrayEquals(source.toAbsolutePath().normalize().toString().getBytes(SOURCE_PATH_CHARSET),
                                         game.getGameContext().getMapArchivePath(),
                                         "the game context keeps the replay file's path");
            TriggerEventsAssert.assertEudFailureNotices(triggers.getEvents(), EUD_FAILURE_CODE);
        });

        GoldenValues values = run.getEndValues();

        Assertions.assertEquals(NO_FAILURE, values.get("failure"));
        Assertions.assertEquals(Integer.toString(EUD_FAILURE_FRAME), values.get("frames.completed"));
        Assertions.assertEquals(EUD_FAILURE_END, values.get("end"));
    }

    /**
     * Played back as a replay, a LAN game on the EUD computer map also reads an EUD address without a native region at frame 2: the game shows the
     * EUD error notice there and ends the session.
     */
    @RequiresInstallation
    @ExtendWith(GameResourcesExtension.class)
    @Test
    void eud_computer_map_replay_ends_at_its_native_eud_failure(@NotNull InstallationTestResources resources) throws Exception {
        SharedReplayRunScenario run = resources.getReplayRun(GameTestResources.getPath(GameTestResources.REPLAY_EUD_COMPUTER_SHIELD_REPAIR_DESYNC));

        run.inspect(EUD_FAILURE_FRAME, game -> {
            TriggerContext triggers = game.getTriggerContext();

            Assertions.assertNotNull(triggers);
            TriggerEventsAssert.assertEudFailureNotices(triggers.getEvents(), EUD_COMPUTER_FAILURE_CODE);
        });

        GoldenValues values = run.getEndValues();

        Assertions.assertEquals(NO_FAILURE, values.get("failure"));
        Assertions.assertEquals(Integer.toString(EUD_FAILURE_FRAME), values.get("frames.completed"));
        Assertions.assertEquals(EUD_COMPUTER_FAILURE_END, values.get("end"));
    }

    /**
     * The replay command buffer runs a stale Restart Game command at frame 14988, which ends replay playback without a further frame. The native game
     * also ends the replay session at frame 14988 and keeps the random state 441254477 with 54809 random counts.
     */
    @RequiresInstallation
    @ExtendWith(GameResourcesExtension.class)
    @Test
    void stale_restart_game_replay_ends_at_its_native_restart_frame(@NotNull InstallationTestResources resources) throws Exception {
        GoldenValues values = resources.getReplayRun(GameTestResources.getPath(GameTestResources.REPLAY_STALE_RESTART_GAME)).getEndValues();

        Assertions.assertEquals(NO_FAILURE, values.get("failure"));
        Assertions.assertEquals(Integer.toString(STALE_RESTART_GAME_FRAME), values.get("frames.completed"));
        Assertions.assertEquals(STALE_RESTART_GAME_END, values.get("end"));
        Assertions.assertEquals(STALE_RESTART_GAME_RANDOM_STATE, values.get("rng.state"));
        Assertions.assertEquals(STALE_RESTART_GAME_RANDOM_TOTAL, values.get("rng.total"));
    }

    @RequiresInstallation
    @Test
    void replay_runtime_runs_recorded_replay_and_rejects_a_second_start() throws Exception {
        Path source = GameTestResources.getPath(GameTestResources.REPLAY_ZVP_FIGHTING_SPIRIT);
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(source));

        try (GameData gameData = GameData.load(CascStorage.open(ExternalInput.INSTALLATION.getPath()))) {
            byte[] mapData = replay.getMapData();

            Assertions.assertNotNull(mapData);

            ReplayRuntime runtime = new ReplayRuntime(replay, ChkFile.parseTolerant(mapData), gameData,
                                                      RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                      RunningGameScenario.INSTRUCTION_LIMIT);

            runtime.start(0, 0);

            Game state = runtime.getGame();
            Exception executionFailure = runtime.getFailure();
            ReplaySession session = runtime.getSession();

            if (session != null) {
                try {
                    while (!session.isFinished()) {
                        runtime.step(0, state.getCurrentFrame() * runtime.getFrameDuration());
                    }
                } catch (RuntimeException exception) {
                    executionFailure = exception;
                }
            }

            StringBuilder result = new StringBuilder();

            result.append("replay=").append(source).append('\n');
            result.append("totalFrames=").append(replay.getHeader().getFrames()).append('\n');
            result.append("phase=").append(runtime.getPhase()).append('\n');
            result.append("stateFrame=").append(state.getCurrentFrame()).append('\n');
            result.append("completedFrame=").append(session == null ? -1 : session.getLastCompletedFrame()).append('\n');
            result.append("randomState=").append(state.getRandom().getState()).append('\n');
            result.append("randomCount=").append(state.getRandom().getTotalRandomCounts()).append('\n');
            result.append("units=");

            UnitPool unitPool = state.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(unitPool);

            result.append(unitPool.getAllocatedCount()).append('\n');

            for (int poolIndex = 1; poolIndex <= unitPool.getCapacity(); poolIndex++) {
                if (!unitPool.isAllocated(poolIndex)) {
                    continue;
                }

                Unit unit = unitPool.getUnit(poolIndex);

                if (unit.getOwnerIndex() >= PlayerTable.PLAYABLE_PLAYER_SLOTS) {
                    continue;
                }

                result.append("unit=").append(poolIndex).append(" generation=").append(unitPool.getGeneration(poolIndex))
                      .append(" type=").append(unit.getUnitId()).append(" owner=").append(unit.getOwnerIndex())
                      .append(" path=").append(unit.getPathIndex())
                      .append(" status=").append(Integer.toHexString(unit.getStatus())).append('\n');
            }

            if (session != null) {
                int commandIndex = session.getNextCommandIndex();

                result.append("nextCommandIndex=").append(commandIndex).append('\n');

                if (commandIndex < replay.getCommands().size()) {
                    ReplayCommand command = replay.getCommands().get(commandIndex);

                    result.append("nextCommandFrame=").append(command.getFrame()).append('\n');
                    result.append("nextCommandOpcode=").append(command.getTypeId()).append('\n');
                }
            }

            if (executionFailure != null) {
                StringWriter stackTrace = new StringWriter();

                executionFailure.printStackTrace(new PrintWriter(stackTrace));
                result.append(stackTrace);
            }

            Path report = Path.of("build", "test-output", "native-replay-integration", "replay-startup-current.txt");

            Files.createDirectories(report.getParent());
            Files.writeString(report, result);

            Assertions.assertTrue(state.hasInitializedWorldState(), result.toString());
            Assertions.assertNotNull(session, result.toString());
            Assertions.assertTrue(session.getLastCompletedFrame() >= MINIMUM_COMPLETED_FRAMES, result.toString());
            Assertions.assertTrue(session.getNextCommandIndex() >= MINIMUM_EXECUTED_COMMANDS, result.toString());
            Assertions.assertEquals(0, state.getRandom().getEnabled());
            Assertions.assertThrows(IllegalStateException.class, () -> runtime.start(0, 0));
        }
    }

    @RequiresInstallation
    @Test
    void replay_produces_identical_state_for_any_host_tick_spacing() throws Exception {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_PVP_FORTRESS)));

        try (GameData gameData = GameData.load(CascStorage.open(ExternalInput.INSTALLATION.getPath()))) {
            List<String> profileCheckpoints = runReplayAndReadCheckpoints(gameData, replay, PROFILE_PACING);

            Assertions.assertEquals(profileCheckpoints, runReplayAndReadCheckpoints(gameData, replay, 1));
            Assertions.assertEquals(profileCheckpoints, runReplayAndReadCheckpoints(gameData, replay, WIDE_HOST_TICKS_PER_FRAME));
        }
    }

    @RequiresInstallation
    @Test
    void start_should_place_the_screen_on_the_first_human_start_location_without_a_display() throws IOException {
        try (GameData gameData = GameData.load(CascStorage.open(ExternalInput.INSTALLATION.getPath()))) {
            ReplayRuntime topRight = RunningGameScenario.createRuntime(readReplay(GameTestResources.REPLAY_TOP_RIGHT_START), gameData);
            ReplayRuntime worm = RunningGameScenario.createRuntime(readReplay(GameTestResources.REPLAY_EUD_WORM_GROWING), gameData);

            Assertions.assertArrayEquals(new int[]{TOP_RIGHT_START_SCREEN_X, TOP_RIGHT_START_SCREEN_Y}, readStartScreen(topRight));
            Assertions.assertArrayEquals(new int[]{WORM_START_SCREEN_X, WORM_START_SCREEN_Y}, readStartScreen(worm));
        }
    }

    @RequiresInstallation
    @Test
    void start_should_place_the_screen_of_the_set_screen_size() throws IOException {
        try (GameData gameData = GameData.load(CascStorage.open(ExternalInput.INSTALLATION.getPath()))) {
            ReplayRuntime runtime = RunningGameScenario.createRuntime(readReplay(GameTestResources.REPLAY_EUD_WORM_GROWING), gameData);

            runtime.setScreenSize(LARGE_SCREEN_WIDTH, LARGE_SCREEN_HEIGHT);

            Assertions.assertArrayEquals(new int[]{WORM_LARGE_START_SCREEN_X, WORM_LARGE_START_SCREEN_Y}, readStartScreen(runtime));
            Assertions.assertThrows(IllegalStateException.class, () -> runtime.setScreenSize(LARGE_SCREEN_WIDTH, LARGE_SCREEN_HEIGHT));
        }
    }

    @RequiresInstallation
    @Test
    void observer_starts_on_the_first_human_start_location_with_every_player_vision() throws IOException {
        ReplayFile replay = readReplay(GameTestResources.REPLAY_TOP_RIGHT_START);
        byte[] mapData = replay.getMapData();

        Assertions.assertNotNull(mapData);

        try (GameData gameData = GameData.load(CascStorage.open(ExternalInput.INSTALLATION.getPath()))) {
            ReplayRuntime observer = ReplayRuntime.createLiveGame(replay, ChkFile.parseTolerant(mapData), gameData,
                                                                  PlayerTable.FIRST_OBSERVER_SLOT, true,
                                                                  RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                                  RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertArrayEquals(new int[]{TOP_RIGHT_START_SCREEN_X, TOP_RIGHT_START_SCREEN_Y}, readStartScreen(observer));

            LocalVision vision = observer.getGame().getLocalVision();

            Assertions.assertFalse(observer.getGame().isReplayPlayback());
            Assertions.assertTrue(vision.isReplayOrObserver());
            Assertions.assertEquals(LocalVision.ALL_PLAYERS, vision.getViewMask());
        }
    }

    @RequiresInstallation
    @Test
    void replay_runtime_assigns_recorded_colors_to_first_eight_slots_and_slot_index_to_rest() throws IOException {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_ZVP_FIGHTING_SPIRIT)));
        int[] recorded = replay.getHeader().getColors();

        Assertions.assertArrayEquals(RECORDED_COLORS, recorded);

        recorded[0] = OVERWRITTEN_COLOR;

        Assertions.assertEquals(RECORDED_COLORS[0], replay.getHeader().getColors()[0], "The header returns independent copies.");

        try (GameData gameData = GameData.load(CascStorage.open(ExternalInput.INSTALLATION.getPath()))) {
            ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);
            Game state = runtime.getGame();
            int[] expected = {3, 7, 1, 5, 4, 6, 2, 0, 8, 9, 10, 11};

            for (int slot = 0; slot < PlayerTable.PLAYER_SLOTS; slot++) {
                Player player = state.getPlayerTable().getPlayer(slot);

                Assertions.assertNotNull(player);
                Assertions.assertEquals(expected[slot], player.getColor(), "Player slot " + slot);
            }
        }
    }
}
