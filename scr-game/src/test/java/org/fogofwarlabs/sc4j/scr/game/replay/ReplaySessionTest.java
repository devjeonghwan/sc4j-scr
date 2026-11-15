package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.command.CommandRecorder;
import org.fogofwarlabs.sc4j.engine.command.CommandType;
import org.fogofwarlabs.sc4j.engine.controller.ControllerCommandSource;
import org.fogofwarlabs.sc4j.engine.controller.ControllerSource;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayHeaderBuilder;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.NetworkReplayScenario;
import org.fogofwarlabs.sc4j.scr.game.combat.ProjectilePhase;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.frame.UnitPhase;
import org.fogofwarlabs.sc4j.scr.game.movement.UnitMovement;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.player.ProductionStatistics;
import org.fogofwarlabs.sc4j.scr.game.presentation.InterfaceLink;
import org.fogofwarlabs.sc4j.scr.game.session.OutgoingTurnBuffer;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteEffectPool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.ReplayFileScenario;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.visibility.MapVisibility;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tests replay command dispatch and failure boundaries, the turn exchange of replay playback with the viewer's controls and its Sync commands, the
 * end of playback at a recorded or received Restart Game command, and the executed and pending commands of a replay run.
 */
final class ReplaySessionTest {
    /**
     * The frame of {@link GameTestResources#REPLAY_ZVZ_HEARTBREAK_RIDGE} whose seven chat commands overflow the replay command buffer and run after
     * their loading call, so the seventh does not run.
     */
    private static final int DEFERRED_OVERFLOW_FRAME = 2976;
    /**
     * A frame counter value written over the counter before a step, as an EUD write can.
     */
    private static final int WRITTEN_FRAME = 500;
    /**
     * A Replay Seek target before the end of the fixture replays.
     */
    private static final int SEEK_TARGET_BEFORE_END = 5;
    /**
     * The commands of a network game's startup turn among the fixture's three frame-0 commands.
     */
    private static final int STARTUP_TURN_COMMANDS = 2;
    /**
     * A Select command of one unit that a map queues into the outgoing turn.
     */
    private static final int @NotNull [] QUEUED_COMMAND = {0x09, 0x01, 0x94, 0x06};
    /**
     * A Replay Speed command that a map queues: playing, speed 1 (Slower), factor one.
     */
    private static final int @NotNull [] QUEUED_REPLAY_SPEED = {0x56, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00};
    private static final int SLOWER_SPEED = 1;
    /**
     * The speed setting of a replay's speed configuration at the session start (Fast).
     */
    private static final int START_SPEED = 4;
    /**
     * The largest speed factor, and the frame duration and command interval of Fastest at that factor.
     */
    private static final int MAXIMUM_FACTOR = 16;
    private static final int MAXIMUM_FACTOR_FRAME_DURATION = 2;
    private static final int MAXIMUM_FACTOR_COMMAND_INTERVAL = 10;
    /**
     * The Speed Up presses past the largest factor, the Slow Down presses that bring the factor back to one, and the Slow Down presses past the
     * slowest speed.
     */
    private static final int PRESSES_PAST_MAXIMUM_FACTOR = 6;
    private static final int PRESSES_TO_FACTOR_ONE = 3;
    private static final int PRESSES_PAST_SLOWEST_SPEED = 10;
    /**
     * A silent Latency command that sets the high latency: one extra turn in transit.
     */
    private static final byte @NotNull [] SILENT_HIGH_LATENCY_COMMAND = {0x55, (byte) 0x81};
    private static final byte @NotNull [] PAUSE_COMMAND = {0x10};
    private static final byte @NotNull [] RESTART_GAME_COMMAND = {0x08};
    private static final byte @NotNull [] SELECT_COMMAND = {0x09, 0x01, (byte) 0x94, 0x06};

    /**
     * The frame count of the fixture replays.
     */
    private static final int REPLAY_FRAMES = 10;
    private static final int BROOD_WAR_ENGINE = 1;
    /**
     * The offset of the first player slot's type byte in the replay header: the replay's local player is that human.
     */
    private static final int FIRST_PLAYER_TYPE_OFFSET = ReplayHeaderBuilder.PLAYERS_OFFSET + ReplayHeaderBuilder.PLAYER_TYPE_OFFSET;
    /**
     * The fixture's frame-0 commands: a Keep Alive of each of the players 0, 1 and 2.
     */
    private static final byte @NotNull [] STARTUP_COMMANDS = {0, 0x05, 1, 0x05, 2, 0x05};
    private static final int STARTUP_COMMAND_COUNT = 3;
    /**
     * The frame-0 commands with a Restart Game command of player 0 followed by its Keep Alive.
     */
    private static final byte @NotNull [] RESTART_COMMANDS = {0, 0x05, 1, 0x05, 2, 0x05, 0, 0x08, 0, 0x05};

    /**
     * The seed of the fixture worlds, and the random source a fixture command handler draws from.
     */
    private static final int FIXTURE_SEED = 123;
    private static final int COMMAND_RANDOM_SOURCE = 14;

    /**
     * The fixture terrain: three CV5 records and one VF4 record.
     */
    private static final int TERRAIN_GROUPS_SIZE = 3 * TerrainTileset.CV5_RECORD_SIZE;
    private static final int TERRAIN_MINIATURE_FLAGS_SIZE = 32;
    private static final int MAP_VISIBILITY_CONTROL_COUNT = 5;
    private static final int FRAME_RUNNER_INSTRUCTION_LIMIT = 4096;

    /**
     * The turns a command of the outgoing turn stays in transit at the default latency.
     */
    private static final int LATENCY_TURNS = 1;
    /**
     * The most bytes of an outgoing turn.
     */
    private static final int MAXIMUM_TURN_BYTES = 0x1F0;
    /**
     * The type and the length of the Sync command that starts every outgoing turn.
     */
    private static final int SYNC_COMMAND_TYPE = 0x37;
    private static final int SYNC_COMMAND_LENGTH = 7;
    /**
     * The command types of the Pause and the Replay Speed commands.
     */
    private static final int PAUSE_COMMAND_TYPE = 0x10;
    private static final int REPLAY_SPEED_COMMAND_TYPE = 0x56;
    /**
     * The fastest speed setting.
     */
    private static final int FASTEST_SPEED = 6;
    /**
     * The frame durations of the slowest, slower and fastest speed settings at factor one, in milliseconds.
     */
    private static final int SLOWEST_FRAME_DURATION = 167;
    private static final int SLOWER_FRAME_DURATION = 111;
    private static final int FASTEST_FRAME_DURATION = 42;
    /**
     * The record byte of the queued Sync command after the session start (record 1) and after the first game loop (record 2), whose kind is
     * {@code 0} in replay playback.
     */
    private static final int RECORD_BYTE_OFFSET = 1;
    private static final int FIRST_RECORD_BYTE = 0x10;
    private static final int SECOND_RECORD_BYTE = 0x20;
    /**
     * The Sync command native replay playback holds in the outgoing turn after frames 0, 1 and 2 of
     * {@link GameTestResources#REPLAY_TOP_RIGHT_START} and after frames 0, 2 and 3 of {@link GameTestResources#REPLAY_EUD_WORM_GROWING}.
     */
    private static final @NotNull List<String> TOP_RIGHT_START_SYNC_COMMANDS = List.of("3720b681000000", "373003d1000000", "3740b9c5000000");
    private static final @NotNull List<String> WORM_SYNC_COMMANDS = List.of("3720b5b7000000", "3740bbbf000000", "37506164000000");
    /**
     * The frames whose Sync commands the native samples hold: {@link #WORM_SYNC_COMMANDS} skips the torn sample after frame 1.
     */
    private static final int TOP_RIGHT_START_SYNC_FRAMES = 3;
    private static final int WORM_SYNC_FRAMES = 4;
    private static final int WORM_TORN_SAMPLE_INDEX = 1;

    /**
     * Records how the received turns' Replay Speed and Replay Seek commands change the viewer's playback.
     */
    private static final class RecordingPlaybackControl implements ReplayCommandExecutor.PlaybackControl {
        private final @NotNull List<Integer> seekTargets;

        private boolean paused;

        private RecordingPlaybackControl() {
            this.seekTargets = new ArrayList<>();

            this.paused = false;
        }

        /**
         * @return {@code true} if the viewer's playback is paused, {@code false} otherwise
         */
        @Override
        public boolean isPlaybackPaused() {
            return this.paused;
        }

        /**
         * Sets whether the viewer's playback is paused.
         *
         * @param paused whether playback pauses
         */
        @Override
        public void setPlaybackPaused(boolean paused) {
            this.paused = paused;
        }

        /**
         * @return the frames the playback was sought to, in order
         */
        private @NotNull List<Integer> getSeekTargets() {
            return this.seekTargets;
        }


        /**
         * Seeks the viewer's playback to a frame: a frame before the current one restarts the replay first.
         *
         * @param targetFrame the frame to reach, the replay's frame count at most
         */
        @Override
        public void seekReplay(int targetFrame) {
            this.seekTargets.add(targetFrame);
        }
    }

    /**
     * Creates a {@link Game} instance for the explicit fixture.
     *
     * @param ready whether to establish the world readiness boundary
     * @return the created {@link Game} instance
     */
    private static @NotNull Game createGame(boolean ready) {
        // The map keeps expanded terrain flags, which the Sync records of replay playback hash.
        Game state = Game.create(null, new GameMap(1, 1, 0, new int[1], new int[1]), new LcgRandom(FIXTURE_SEED));

        state.getRandom().setEnabled(0);

        if (ready) {
            state.getUnitSystem().initializeUnitPool(1);
            state.getSpriteSystem().initializeSpritePool(1);
            state.getSpriteSystem().initializeImagePool(1);
            state.initializeExecutionScope();

            UnitPool selectionPool = state.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(selectionPool);

            state.setSelection(new UnitSelection(selectionPool, false, 0, 0));

            ProductionStatistics statistics = new ProductionStatistics(false,
                                                                       new int[ProductionStatistics.VALUE_COUNT],
                                                                       new float[ProductionStatistics.VALUE_COUNT]);

            statistics.initializeSample(0);
            state.getPlayerTable().setProductionStatistics(statistics);
            state.markWorldInitialized();
        }

        return state;
    }

    /**
     * Creates a {@link ReplayFrameRunner} instance for the supplied world and fixture contexts.
     * <p>
     * The deliberately absent frame contexts stop execution before a game phase runs.
     *
     * @param state the fixture world
     * @return the created {@link ReplayFrameRunner} instance, bound to the supplied world and fixture contexts
     * @throws IOException if the terrain fixture cannot be parsed
     */
    private static @NotNull ReplayFrameRunner createFrameRunner(@NotNull Game state) throws IOException {
        UnitPhase.Context units = new UnitPhase.Context(UnitPhase.INITIAL_PRIMARY_REDISTRIBUTION_TIMER,
                                                        UnitPhase.INITIAL_SECONDARY_REDISTRIBUTION_TIMER);
        TerrainTileset terrain = TerrainTileset.parse(new byte[TERRAIN_GROUPS_SIZE], new byte[TERRAIN_MINIATURE_FLAGS_SIZE]);
        GameFrame frame = new GameFrame(units, new UnitMovement.Context(0, 0), new ProjectilePhase.Context(),
                                        new SpriteEffectPool(1), new MapVisibility(terrain, new int[MAP_VISIBILITY_CONTROL_COUNT]));

        return new ReplayFrameRunner(state, frame, units, new TriggerContext(), GameSpeed.createPristineReplay(),
                                     GameSpeed.FASTEST, false, false, false,
                                     new int[]{0, 0, RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT},
                                     FRAME_RUNNER_INSTRUCTION_LIMIT, 1, 0, 0);
    }

    /**
     * Supplies the eight player records with the network ids {@code 0..7}, so that a command executor resolves the issuers of the recorded commands
     * and of the received turns.
     *
     * @param state the fixture world
     */
    private static void preparePlayers(@NotNull Game state) {
        for (int slot = 0; slot < PlayerTable.PLAYABLE_PLAYER_SLOTS; slot++) {
            state.getPlayerTable().setPlayer(slot, new Player(slot, slot, PlayerTypes.HUMAN, 0, 0));
        }
    }

    /**
     * Creates a {@link ReplayFile} instance containing the fixture command block.
     *
     * @param frames the header frame endpoint
     * @return the created {@link ReplayFile} instance
     * @throws IOException if the fixture command block cannot be encoded or parsed
     */
    private static @NotNull ReplayFile createReplay(int frames) throws IOException {
        return createReplay(frames, STARTUP_COMMANDS);
    }

    /**
     * Creates a {@link ReplayFile} instance whose first player is human, containing one frame-0 command block.
     *
     * @param frames   the header frame endpoint
     * @param commands the frame-0 commands, each a player byte, the type byte and the payload
     * @return the created {@link ReplayFile} instance
     * @throws IOException if the command block cannot be encoded or parsed
     */
    private static @NotNull ReplayFile createReplay(int frames, byte @NotNull [] commands) throws IOException {
        byte[] header = new byte[ReplayHeaderBuilder.HEADER_SIZE];

        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                  .put(ReplayHeaderBuilder.ENGINE_OFFSET, (byte) BROOD_WAR_ENGINE).putInt(ReplayHeaderBuilder.FRAMES_OFFSET, frames)
                  .put(FIRST_PLAYER_TYPE_OFFSET, (byte) PlayerTypes.HUMAN);

        byte[] block = ByteBuffer.allocate(Integer.BYTES + 1 + commands.length).order(ByteOrder.LITTLE_ENDIAN)
                                 .putInt(0).put((byte) commands.length).put(commands).array();

        return ReplayFile.parse(ReplayFileScenario.encode(header, block));
    }

    private static boolean isSameCommand(@NotNull ReplayCommand expected, @NotNull ReplayCommand actual) {
        return expected.getPlayerId() == actual.getPlayerId() && expected.getTypeId() == actual.getTypeId() &&
               Arrays.equals(expected.getPayload(), actual.getPayload());
    }

    private static boolean isSameCommands(@NotNull List<ReplayCommand> expected, @NotNull List<ReplayCommand> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }

        for (int index = 0; index < expected.size(); index++) {
            if (!isSameCommand(expected.get(index), actual.get(index))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Presses a viewer control and runs the paused game loops whose turn steps send its command and run it.
     *
     * @param session the replay playback session
     * @param control the control
     */
    private static void pressControl(@NotNull ReplaySession session, @NotNull Runnable control) {
        control.run();
        session.stepPaused();
        session.stepPaused();
    }

    /**
     * Plays a replay with the local interface inputs and code constants a viewer supplies and reads the Sync command of its
     * outgoing turn after each of its first frames.
     *
     * @param gameData     the installed game resources
     * @param installation the installed game
     * @param resource     the replay resource
     * @param frames       the frames to run
     * @return the seven Sync command bytes after each frame, as hexadecimal digits
     * @throws IOException if the replay or a required installed resource cannot be read
     */
    private static @NotNull List<String> readSyncCommands(@NotNull GameData gameData, @NotNull Path installation, @NotNull String resource,
                                                          int frames) throws IOException {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(resource)));
        ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);
        Game state = runtime.getGame();
        List<String> commands = new ArrayList<>();

        NetworkReplayScenario.copyCodeConstants(state, installation);
        NetworkReplayScenario.prepare(state);
        runtime.start(0, 0);

        Assertions.assertNull(runtime.getFailure());

        OutgoingTurnBuffer turn = state.getInterfaceLink().getOutgoingTurn();

        while (state.getCurrentFrame() < frames) {
            Assertions.assertTrue(runtime.step(0, state.getCurrentFrame() * runtime.getFrameDuration()), () -> String.valueOf(runtime.getFailure()));

            StringBuilder command = new StringBuilder();

            for (int offset = 0; offset < SYNC_COMMAND_LENGTH; offset++) {
                command.append(HexFormat.of().toHexDigits((byte) turn.readByte(offset)));
            }

            commands.add(command.toString());
        }

        return commands;
    }


    @Test
    void replay_session_requires_a_frame_runner_for_the_same_world() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game state = createGame(false);

        Assertions.assertThrows(NullPointerException.class, () -> new ReplaySession(state, replay, (currentState, command) -> {
        }, null));
        Assertions.assertThrows(IllegalArgumentException.class,
                                () -> new ReplaySession(state, replay, (currentState, command) -> {
                                }, createFrameRunner(createGame(false))));
    }

    @Test
    void step_should_reject_unready_world_before_consuming_commands() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game state = createGame(false);
        ReplaySession session = new ReplaySession(state,
                                                  replay,
                                                  (currentState, command) -> Assertions.fail("Unready world dispatched."),
                                                  createFrameRunner(state));
        long before = state.computeStateHash();

        Assertions.assertThrows(UnsupportedOperationException.class, () -> session.step(GameSpeed.FASTEST_DURATION));
        Assertions.assertEquals(0, session.getNextCommandIndex());
        Assertions.assertEquals(before, state.computeStateHash());
    }

    @Test
    void step_should_deliver_same_frame_commands_before_the_frame_failure() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game state = createGame(true);
        List<ReplayCommand> delivered = new ArrayList<>();
        ReplaySession session = new ReplaySession(state,
                                                  replay,
                                                  (currentState, command) -> {
                                                      Assertions.assertEquals(0, currentState.getCurrentFrame());
                                                      Assertions.assertEquals(1, currentState.getRandom().getEnabled());

                                                      currentState.getRandom().next(COMMAND_RANDOM_SOURCE);
                                                      delivered.add(command);
                                                  },
                                                  createFrameRunner(state));

        Assertions.assertThrows(UnsupportedOperationException.class, () -> session.step(GameSpeed.FASTEST_DURATION));
        Assertions.assertEquals(replay.getCommands(), delivered);
        Assertions.assertEquals(STARTUP_COMMAND_COUNT, session.getNextCommandIndex());
        Assertions.assertEquals(STARTUP_COMMAND_COUNT, state.getRandom().getTotalRandomCounts());
        Assertions.assertEquals(0, state.getRandom().getEnabled());
        Assertions.assertEquals(1, state.getCurrentFrame());

        ProductionStatistics statisticsAfterFrameFailure = state.getPlayerTable().getProductionStatistics();

        Assertions.assertNotNull(statisticsAfterFrameFailure);
        Assertions.assertEquals(2, statisticsAfterFrameFailure.getSampleCount());

        long interruptedHash = state.computeStateHash();

        Assertions.assertThrows(IllegalStateException.class, () -> session.step(GameSpeed.FASTEST_DURATION));
        Assertions.assertEquals(interruptedHash, state.computeStateHash());
    }

    @Test
    void step_should_keep_successful_commands_and_retry_only_the_failing_command() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game state = createGame(true);
        List<ReplayCommand> attempts = new ArrayList<>();
        IllegalStateException failure = Exceptions.newIllegalStateException("Command boundary fixture");
        ReplaySession session = new ReplaySession(state,
                                                  replay,
                                                  (currentState, command) -> {
                                                      Assertions.assertEquals(1, currentState.getRandom().getEnabled());

                                                      attempts.add(command);
                                                      currentState.getRandom().next(COMMAND_RANDOM_SOURCE);

                                                      if (command == replay.getCommands().get(1)) {
                                                          throw failure;
                                                      }
                                                  },
                                                  createFrameRunner(state));

        for (int attempt = 0; attempt < 2; attempt++) {
            Assertions.assertSame(failure, Assertions.assertThrows(IllegalStateException.class, () -> session.step(GameSpeed.FASTEST_DURATION)));
            Assertions.assertEquals(1, session.getNextCommandIndex());
            Assertions.assertEquals(0, state.getCurrentFrame());
            Assertions.assertEquals(0, state.getRandom().getEnabled());
            Assertions.assertEquals(2 + attempt, state.getRandom().getTotalRandomCounts());

            ProductionStatistics statisticsAfterCommandFailure = state.getPlayerTable().getProductionStatistics();

            Assertions.assertNotNull(statisticsAfterCommandFailure);
            Assertions.assertEquals(1, statisticsAfterCommandFailure.getSampleCount());
        }

        Assertions.assertEquals(List.of(replay.getCommands().get(0), replay.getCommands().get(1), replay.getCommands().get(1)), attempts);
    }

    @Test
    void step_should_count_a_turn_after_its_commands_independently_of_the_frame_counter() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game state = createGame(true);
        List<Integer> commandTurns = new ArrayList<>();

        state.setReplayPlayback(true);

        Assertions.assertEquals(0, state.getTurnCount());

        ReplaySession session = new ReplaySession(state,
                                                  replay,
                                                  (currentState, command) -> commandTurns.add(currentState.getTurnCount()),
                                                  createFrameRunner(state));

        Assertions.assertEquals(0, state.getTurnCount(), "The first step runs the startup turn step.");

        state.storeCurrentFrame(WRITTEN_FRAME);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> session.step(GameSpeed.FASTEST_DURATION));
        Assertions.assertEquals(List.of(1, 1, 1), commandTurns, "The commands of a step see the turns counted before it.");
        Assertions.assertEquals(2, state.getTurnCount(), "The step counts its turn after the commands, before the frame runs.");
    }

    @Test
    void step_should_run_the_startup_turn_commands_before_the_startup_turn_step_counts() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game state = createGame(true);
        List<Integer> commandTurns = new ArrayList<>();
        ReplaySession session = new ReplaySession(state,
                                                  replay,
                                                  (currentState, command) -> commandTurns.add(currentState.getTurnCount()),
                                                  createFrameRunner(state));

        session.setStartupTurnCommandCount(() -> STARTUP_TURN_COMMANDS);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> session.step(GameSpeed.FASTEST_DURATION));
        Assertions.assertEquals(List.of(0, 0, 1), commandTurns, "The startup turn's two commands see 0, the frame's command 1.");
        Assertions.assertEquals(2, state.getTurnCount(), "The startup turn step and the frame's turn step.");
        Assertions.assertEquals(replay.getCommands(), session.getExecutedCommands());
    }

    @Test
    void replay_playback_sends_the_outgoing_turn_in_each_turn_step() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game state = createGame(true);
        List<ReplayCommand> delivered = new ArrayList<>();

        state.setReplayPlayback(true);

        ReplaySession session = new ReplaySession(state, replay, (currentState, command) -> delivered.add(command), createFrameRunner(state));
        InterfaceLink link = state.getInterfaceLink();
        OutgoingTurnBuffer turn = link.getOutgoingTurn();

        Assertions.assertEquals(LATENCY_TURNS, link.getLatencyTurns());
        Assertions.assertEquals(MAXIMUM_TURN_BYTES, link.getMaximumTurnBytes());
        Assertions.assertEquals(SYNC_COMMAND_TYPE, turn.readByte(0));
        Assertions.assertEquals(SYNC_COMMAND_LENGTH, turn.getLength());

        // A map queues a command as EUD writes do: the bytes at the length, then the raised length.
        for (int index = 0; index < QUEUED_COMMAND.length; index++) {
            turn.writeByte(OutgoingTurnBuffer.SYNC_COMMAND_LENGTH + index, QUEUED_COMMAND[index]);
        }

        turn.setLength(OutgoingTurnBuffer.SYNC_COMMAND_LENGTH + QUEUED_COMMAND.length);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> session.step(GameSpeed.FASTEST_DURATION));
        Assertions.assertTrue(isSameCommands(replay.getCommands(), delivered), "The sent turn's command does not run in replay playback.");
        Assertions.assertEquals(SYNC_COMMAND_LENGTH, turn.getLength(), "The turn step sent the turn and queued the Sync command.");
        Assertions.assertEquals(SYNC_COMMAND_TYPE, turn.readByte(0));
        Assertions.assertEquals(QUEUED_COMMAND[0], turn.readByte(OutgoingTurnBuffer.SYNC_COMMAND_LENGTH), "Bytes past the length remain.");
    }

    @Test
    void replay_playback_queues_the_sync_record_made_before_the_last_turn_step() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game state = createGame(true);

        state.setReplayPlayback(true);

        ReplaySession session = new ReplaySession(state, replay, (currentState, command) -> {
        }, createFrameRunner(state));
        OutgoingTurnBuffer turn = state.getInterfaceLink().getOutgoingTurn();

        Assertions.assertEquals(FIRST_RECORD_BYTE, turn.readByte(RECORD_BYTE_OFFSET), "The session start queues record 1.");

        // The first game loop runs the startup turn step (record 2) and the frame's turn step (record 3); the frame itself fails in this world.
        Assertions.assertThrows(UnsupportedOperationException.class, () -> session.step(GameSpeed.FASTEST_DURATION));
        Assertions.assertEquals(SECOND_RECORD_BYTE, turn.readByte(RECORD_BYTE_OFFSET), "The frame's turn step queued the record before its own.");
    }

    @Test
    void replay_playback_runs_the_replay_speed_command_of_the_received_turn() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game state = createGame(true);
        List<ReplayCommand> delivered = new ArrayList<>();

        state.setReplayPlayback(true);

        ReplayFrameRunner runner = createFrameRunner(state);
        ReplaySession session = new ReplaySession(state, replay, (currentState, command) -> delivered.add(command), runner);
        OutgoingTurnBuffer turn = state.getInterfaceLink().getOutgoingTurn();

        // A map queues the command as EUD writes do: the bytes at the length, then the raised length.
        for (int index = 0; index < QUEUED_REPLAY_SPEED.length; index++) {
            turn.writeByte(OutgoingTurnBuffer.SYNC_COMMAND_LENGTH + index, QUEUED_REPLAY_SPEED[index]);
        }

        turn.setLength(OutgoingTurnBuffer.SYNC_COMMAND_LENGTH + QUEUED_REPLAY_SPEED.length);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> session.step(GameSpeed.FASTEST_DURATION));
        Assertions.assertEquals(SLOWER_SPEED, runner.getFrameTiming().getSpeedIndex(),
                                "The startup turn step sent the command and the step's turn step ran it.");
        Assertions.assertEquals(SLOWER_FRAME_DURATION, runner.getFrameTiming().getFrameDuration());
        Assertions.assertTrue(isSameCommands(replay.getCommands(), delivered), "The command handler receives the replay's commands only.");
    }

    @Test
    void viewer_controls_take_effect_when_their_command_returns_in_a_paused_game_loop() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game state = createGame(true);
        RecordingPlaybackControl playback = new RecordingPlaybackControl();

        state.setReplayPlayback(true);

        ReplayFrameRunner runner = createFrameRunner(state);
        ReplaySession session = new ReplaySession(state, replay, (currentState, command) -> {
        }, runner);
        GameSpeed timing = runner.getFrameTiming();
        OutgoingTurnBuffer turn = state.getInterfaceLink().getOutgoingTurn();

        session.setPlaybackControl(playback);
        session.increaseSpeed();

        Assertions.assertEquals(REPLAY_SPEED_COMMAND_TYPE, turn.readByte(OutgoingTurnBuffer.SYNC_COMMAND_LENGTH),
                                "The command waits in the outgoing turn, where EUD reads see it.");
        Assertions.assertEquals(START_SPEED, timing.getSpeedIndex());
        Assertions.assertTrue(session.stepPaused());
        Assertions.assertEquals(START_SPEED + 1, timing.getSpeedIndex());
        Assertions.assertEquals(0, state.getCurrentFrame(), "A paused game loop runs no frame.");
        Assertions.assertEquals(2, state.getTurnCount(), "It runs the startup turn step and its own turn step.");

        session.playOrPause();
        session.stepPaused();

        Assertions.assertFalse(playback.isPlaybackPaused(), "The command is in transit for one turn step.");

        session.stepPaused();

        Assertions.assertTrue(playback.isPlaybackPaused());
        Assertions.assertEquals(1, timing.getMode());
    }

    @Test
    void speed_controls_climb_settings_then_factor_and_back() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game state = createGame(true);

        state.setReplayPlayback(true);

        ReplayFrameRunner runner = createFrameRunner(state);
        ReplaySession session = new ReplaySession(state, replay, (currentState, command) -> {
        }, runner);
        GameSpeed timing = runner.getFrameTiming();

        pressControl(session, session::increaseSpeed);
        pressControl(session, session::increaseSpeed);

        Assertions.assertEquals(FASTEST_SPEED, timing.getSpeedIndex());
        Assertions.assertEquals(FASTEST_FRAME_DURATION, timing.getFrameDuration());

        for (int press = 0; press < PRESSES_PAST_MAXIMUM_FACTOR; press++) {
            pressControl(session, session::increaseSpeed);
        }

        Assertions.assertEquals(MAXIMUM_FACTOR, timing.getFactor(), "The factor stops at 16.");
        Assertions.assertEquals(MAXIMUM_FACTOR_FRAME_DURATION, timing.getFrameDuration());
        Assertions.assertEquals(MAXIMUM_FACTOR_COMMAND_INTERVAL, timing.getCommandInterval());

        pressControl(session, session::decreaseSpeed);

        Assertions.assertEquals(MAXIMUM_FACTOR / 2, timing.getFactor(), "Slow Down first halves the factor at Fastest.");

        for (int press = 0; press < PRESSES_TO_FACTOR_ONE; press++) {
            pressControl(session, session::decreaseSpeed);
        }

        Assertions.assertEquals(1, timing.getFactor());
        Assertions.assertEquals(FASTEST_SPEED, timing.getSpeedIndex());

        for (int press = 0; press < PRESSES_PAST_SLOWEST_SPEED; press++) {
            pressControl(session, session::decreaseSpeed);
        }

        Assertions.assertEquals(0, timing.getSpeedIndex());
        Assertions.assertEquals(1, timing.getFactor());
        Assertions.assertEquals(SLOWEST_FRAME_DURATION, timing.getFrameDuration());
    }

    @Test
    void replay_seek_command_seeks_the_viewer_playback_to_at_most_the_frame_count() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game state = createGame(true);
        RecordingPlaybackControl playback = new RecordingPlaybackControl();
        ReplayCommandExecutor executor = new ReplayCommandExecutor(RunningGameScenario.INSTRUCTION_LIMIT, REPLAY_FRAMES);

        preparePlayers(state);
        state.setReplayPlayback(true);
        executor.setPlaybackControl(playback);

        ReplaySession session = new ReplaySession(state, replay, executor, createFrameRunner(state));

        session.setPlaybackControl(playback);
        pressControl(session, () -> session.seek(WRITTEN_FRAME));
        pressControl(session, () -> session.seek(SEEK_TARGET_BEFORE_END));

        Assertions.assertEquals(List.of(REPLAY_FRAMES, SEEK_TARGET_BEFORE_END), playback.getSeekTargets());
    }

    @Test
    void received_turn_runs_its_leading_replay_turn_commands_only() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game state = createGame(true);
        List<ReplayCommand> delivered = new ArrayList<>();

        state.setReplayPlayback(true);

        ReplayFrameRunner runner = createFrameRunner(state);
        ReplaySession session = new ReplaySession(state, replay, (currentState, command) -> delivered.add(command), runner);
        OutgoingTurnBuffer turn = state.getInterfaceLink().getOutgoingTurn();

        turn.queue(PAUSE_COMMAND);
        turn.queue(SELECT_COMMAND);
        session.increaseSpeed();
        session.stepPaused();

        Assertions.assertEquals(replay.getCommands().size() + 1, delivered.size());
        Assertions.assertEquals(PAUSE_COMMAND_TYPE, delivered.getLast().getTypeId(), "The Pause command before the Select command runs.");
        Assertions.assertEquals(START_SPEED, runner.getFrameTiming().getSpeedIndex(), "The Replay Speed command after it does not.");
    }

    @Test
    void latency_command_keeps_one_more_turn_in_transit() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game state = createGame(true);

        state.setReplayPlayback(true);

        ReplayFrameRunner runner = createFrameRunner(state);
        ReplaySession session = new ReplaySession(state, replay, (currentState, command) -> {
        }, runner);

        // The first loop's startup turn step sends the command and its own turn step runs it; the second loop sends two turns.
        state.getInterfaceLink().getOutgoingTurn().queue(SILENT_HIGH_LATENCY_COMMAND);
        session.stepPaused();
        session.stepPaused();
        session.increaseSpeed();
        session.stepPaused();
        session.stepPaused();

        Assertions.assertEquals(START_SPEED, runner.getFrameTiming().getSpeedIndex(), "Two turns are in transit.");

        session.stepPaused();

        Assertions.assertEquals(START_SPEED + 1, runner.getFrameTiming().getSpeedIndex());
    }

    @Test
    void received_restart_game_command_ends_replay_playback() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game state = createGame(true);

        preparePlayers(state);
        state.setReplayPlayback(true);

        ReplaySession session = new ReplaySession(state, replay, new ReplayCommandExecutor(), createFrameRunner(state));

        state.getInterfaceLink().getOutgoingTurn().queue(RESTART_GAME_COMMAND);

        Assertions.assertFalse(session.stepPaused(), "The startup turn step sends the command and the loop's turn step runs it.");
        Assertions.assertTrue(session.isEnded());
        Assertions.assertFalse(session.isFinished());
        Assertions.assertFalse(session.step(GameSpeed.FASTEST_DURATION), "The ended game runs no frame.");
        Assertions.assertFalse(session.stepPaused());
        Assertions.assertEquals(0, state.getCurrentFrame());
        Assertions.assertEquals(0, session.getLastCompletedFrame());
    }

    @Test
    void recorded_restart_game_command_ends_replay_playback_at_its_frame() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES, RESTART_COMMANDS);
        Game state = createGame(true);

        preparePlayers(state);
        state.setReplayPlayback(true);

        ReplaySession session = new ReplaySession(state, replay, new ReplayCommandExecutor(), createFrameRunner(state));

        Assertions.assertFalse(session.step(GameSpeed.FASTEST_DURATION), "The command's game loop runs no frame.");
        Assertions.assertTrue(session.isEnded());
        Assertions.assertFalse(session.isFinished());
        Assertions.assertEquals(1, state.getCommandDispatchMarkerCount(), "The Keep Alive after the command still runs.");
        Assertions.assertEquals(replay.getCommands().size(), session.getNextCommandIndex());
        Assertions.assertEquals(2, state.getTurnCount(), "The startup turn step and the loop's turn step still count.");
        Assertions.assertFalse(session.step(GameSpeed.FASTEST_DURATION), "The ended game runs no further game loop.");
        Assertions.assertEquals(2, state.getTurnCount());
        Assertions.assertEquals(0, state.getCurrentFrame());
        Assertions.assertEquals(0, session.getLastCompletedFrame());
    }

    @Test
    void step_should_not_report_completion_when_the_final_frame_fails() throws IOException {
        ReplayFile replay = createReplay(1);
        Game state = createGame(true);
        ReplaySession session = new ReplaySession(state, replay, (currentState, command) -> {
        }, createFrameRunner(state));

        Assertions.assertThrows(UnsupportedOperationException.class, () -> session.step(GameSpeed.FASTEST_DURATION));
        Assertions.assertEquals(1, state.getCurrentFrame());
        Assertions.assertEquals(0, session.getLastCompletedFrame());
        Assertions.assertFalse(session.isFinished());
        Assertions.assertThrows(IllegalStateException.class, () -> session.step(GameSpeed.FASTEST_DURATION));
    }

    @RequiresInstallation
    @Test
    void executed_and_pending_commands_follow_the_command_buffer_through_a_deferred_overflow_frame() throws IOException {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_ZVZ_HEARTBREAK_RIDGE)));
        Map<Long, List<ReplayCommand>> recordedCommands = replay.getCommands().stream().collect(Collectors.groupingBy(ReplayCommand::getFrame));
        List<ReplayCommand> handledCommands = new ArrayList<>();
        List<Integer> differingFrames = new ArrayList<>();
        byte[] mapData = replay.getMapData();

        Assertions.assertNotNull(mapData);

        try (GameData gameData = GameData.load(CascStorage.open(ExternalInput.INSTALLATION.getPath()))) {
            ReplayRuntime runtime = new ReplayRuntime(replay,
                                                      ChkFile.parseTolerant(mapData),
                                                      gameData,
                                                      RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                      RunningGameScenario.INSTRUCTION_LIMIT);

            runtime.setCommandObserver(new ReplayCommandObserver() {
                /**
                 * Called before the command executes.
                 *
                 * @param game    the simulation state
                 * @param command the recorded command
                 */
                @Override
                public void onBeforeCommand(@NotNull Game game, @NotNull ReplayCommand command) {
                    handledCommands.add(command);
                }

                /**
                 * Called after the command executed without an exception.
                 *
                 * @param game    the simulation state
                 * @param command the recorded command
                 */
                @Override
                public void onAfterCommand(@NotNull Game game, @NotNull ReplayCommand command) {
                }
            });
            runtime.start(0, 0);

            ReplaySession session = runtime.getSession();

            Assertions.assertNotNull(session);

            while (runtime.getGame().getCurrentFrame() <= DEFERRED_OVERFLOW_FRAME) {
                int frame = runtime.getGame().getCurrentFrame();
                ReplayCommand pendingCommand = session.getPendingCommand();

                handledCommands.clear();
                Assertions.assertTrue(runtime.step(0, frame * runtime.getFrameDuration()), "frame " + frame);

                List<ReplayCommand> executedCommands = session.getExecutedCommands();

                Assertions.assertEquals(handledCommands, executedCommands, "frame " + frame);

                if (!executedCommands.isEmpty()) {
                    Assertions.assertNotNull(pendingCommand, "frame " + frame);
                    Assertions.assertTrue(isSameCommand(executedCommands.getFirst(), pendingCommand), "frame " + frame);
                }

                if (!isSameCommands(recordedCommands.getOrDefault((long) frame, List.of()), executedCommands)) {
                    differingFrames.add(frame);
                }
            }
        }

        Assertions.assertEquals(List.of(DEFERRED_OVERFLOW_FRAME), differingFrames);
    }

    @RequiresInstallation
    @Test
    void replay_playback_queues_the_sync_commands_of_native_replay_playback() throws IOException {
        Path installation = ExternalInput.INSTALLATION.getPath();

        try (GameData gameData = GameData.load(CascStorage.open(installation))) {
            List<String> worm = new ArrayList<>(readSyncCommands(gameData, installation, GameTestResources.REPLAY_EUD_WORM_GROWING,
                                                                 WORM_SYNC_FRAMES));

            worm.remove(WORM_TORN_SAMPLE_INDEX);

            Assertions.assertEquals(TOP_RIGHT_START_SYNC_COMMANDS,
                                    readSyncCommands(gameData, installation, GameTestResources.REPLAY_TOP_RIGHT_START, TOP_RIGHT_START_SYNC_FRAMES));
            Assertions.assertEquals(WORM_SYNC_COMMANDS, worm);
        }
    }

    @Test
    void get_controller_should_name_the_source_of_the_recorded_stream() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game live = createGame(true);
        Game playback = createGame(true);

        playback.setReplayPlayback(true);

        ReplaySession liveSession = new ReplaySession(live, replay, (currentState, command) -> {
        }, createFrameRunner(live));
        ReplaySession playbackSession = new ReplaySession(playback, replay, (currentState, command) -> {
        }, createFrameRunner(playback));

        Assertions.assertEquals(ControllerSource.HUMAN, liveSession.getController().getSource());
        Assertions.assertEquals(ControllerSource.REPLAY, playbackSession.getController().getSource());

        liveSession.setControllerSource(ControllerSource.NETWORK);

        Assertions.assertEquals(ControllerSource.NETWORK, liveSession.getController().getSource());
    }

    @Test
    void set_command_source_should_hand_every_record_over_once_through_the_given_source() throws IOException {
        ReplayFile replay = createReplay(REPLAY_FRAMES);
        Game state = createGame(true);
        List<ReplayCommand> delivered = new ArrayList<>();
        List<Object> recorded = new ArrayList<>();
        ReplaySession session = new ReplaySession(state, replay, (currentState, command) -> delivered.add(command), createFrameRunner(state));
        ControllerCommandSource source = new ControllerCommandSource(List.of(session.getController()));

        source.setRecorder(new CommandRecorder() {
            /**
             * Called at the command point of a game loop, before the loop's commands.
             *
             * @param game the game, in its state after the previous loop
             */
            @Override
            public void startLoop(org.fogofwarlabs.sc4j.engine.game.@NotNull Game game) {
            }

            /**
             * Submits one command of a player.
             *
             * @param <C>     the command class
             * @param player  the issuing player
             * @param type    the command type
             * @param command the command
             */
            @Override
            public <C> void submit(org.fogofwarlabs.sc4j.engine.player.@NotNull Player player, @NotNull CommandType<C> type, @NotNull C command) {
                recorded.add(command);
            }
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> session.setCommandSource(new ControllerCommandSource(List.of())));

        session.setCommandSource(source);

        Assertions.assertSame(source, session.getCommandSource());
        Assertions.assertNull(state.getEngineGame().getFrameRunner().getCommandSource(), "the engine frame asks no command source");
        Assertions.assertThrows(UnsupportedOperationException.class, () -> session.step(GameSpeed.FASTEST_DURATION));
        Assertions.assertEquals(replay.getCommands(), delivered);
        Assertions.assertEquals(replay.getCommands(), recorded, "every record went through the given source once");
    }
}
