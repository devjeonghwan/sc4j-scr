package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.engine.command.CommandInput;
import org.fogofwarlabs.sc4j.engine.command.CommandSink;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.player.*;
import org.fogofwarlabs.sc4j.scr.game.session.PauseNotice;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.state.GameEnd;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests recorded command dispatch, selection history, playback controls and rejection boundaries.
 */
@RequiresInstallation
final class ReplayCommandExecutorTest {
    /**
     * The payload sizes of Replay Seek (a frame DWORD), Turn Rate and Dynamic Turn Rate.
     */
    private static final int REPLAY_SEEK_PAYLOAD_SIZE = Integer.BYTES;
    private static final int TURN_RATE_PAYLOAD_SIZE = 1;
    private static final int DYNAMIC_TURN_RATE_PAYLOAD_SIZE = 3;
    /**
     * The replay frame count of the Replay Seek cases, and Replay Seek targets past it, below it and past it as unsigned DWORDs.
     */
    private static final int REPLAY_FRAMES = 10;
    private static final int TARGET_PAST_END = 500;
    private static final int TARGET_BEFORE_END = 5;
    private static final int TARGET_NEGATIVE = -1;

    /**
     * The network id of the fixture's player 0, which the tested commands carry as their issuer, and the network id of a teammate.
     */
    private static final int ISSUER = 7;
    private static final int TEAMMATE_ISSUER = 8;
    /**
     * A recorded id whose low byte is the issuer but whose high bits differ.
     */
    private static final int ALIASED_ISSUER_ID = 0x1000007;
    /**
     * Stored command player indexes with high bits set.
     */
    private static final int HIGH_BIT_COMMAND_PLAYER_INDEX = 0x80000007;
    private static final int OUT_OF_RANGE_COMMAND_PLAYER_INDEX = 0x1000000;
    /**
     * The largest issuer byte.
     */
    private static final int MAXIMUM_ISSUER = 0xFF;
    /**
     * The first player record after the playable ones, and the last playable one.
     */
    private static final int FIRST_EXTENDED_SLOT = PlayerTable.PLAYABLE_PLAYER_SLOTS;
    private static final int LAST_PLAYABLE_SLOT = FIRST_EXTENDED_SLOT - 1;

    /**
     * Command dispatch marker counts left by earlier commands.
     */
    private static final int STALE_MARKER_COUNT = 19;
    private static final int HIGH_BIT_MARKER_COUNT = 0x80000000;

    /**
     * The other unit fields the tests write through the unit memory scenario, besides the shared offsets of {@link UnitMemoryScenario}.
     */
    private static final int PRIMARY_ORDER_STATE_OFFSET = 0x6A;
    private static final int LOCKDOWN_TIMER_OFFSET = 0x1A3;
    private static final int STASIS_TIMER_OFFSET = 0x1A5;
    private static final int MAELSTROM_TIMER_OFFSET = 0x1B4;

    /**
     * The handles of the fixture units in the pool slots 1 to 4 at generation 1.
     */
    private static final int FIRST_UNIT_HANDLE = 0x801;
    private static final int SECOND_UNIT_HANDLE = 0x802;
    private static final int THIRD_UNIT_HANDLE = 0x803;
    private static final int FOURTH_UNIT_HANDLE = 0x804;
    /**
     * The handles of the pool slot 0, of an index outside the pool, of the slots 1 and 3 at another generation and of the slot 1 with high bits set.
     */
    private static final int SLOT_ZERO_HANDLE = 0x800;
    private static final int OUTSIDE_POOL_HANDLE = 0xFFF;
    private static final int FIRST_UNIT_OTHER_GENERATION_HANDLE = 0x1001;
    private static final int THIRD_UNIT_OTHER_GENERATION_HANDLE = 0x1003;
    private static final int FIRST_UNIT_HIGH_BIT_HANDLE = 0x80000801;

    /**
     * The selection history entries of a scope, the newest of them and the largest history timestamp.
     */
    private static final int HISTORY_COUNT = 8;
    private static final int NEWEST_HISTORY = HISTORY_COUNT - 1;
    private static final int MAXIMUM_HISTORY_TIMESTAMP = 0xFFFF;

    /**
     * The sprite and image pool capacity of the startup selection case.
     */
    private static final int STARTUP_SPRITE_CAPACITY = 1700;
    /**
     * The raw bits of the selection scope's float total after one replay frame of the startup selection case.
     */
    private static final int STARTUP_SCOPE_FLOAT_TOTAL_BITS = 0x3F7FC2DD;
    /**
     * The frame of the chat commands.
     */
    private static final int CHAT_FRAME = 12;
    /**
     * The sprite flag of the first team selection circle, which marks a teammate's selection.
     */
    private static final int FIRST_TEAM_SELECTION_CIRCLE_FLAG = 0x02;
    /**
     * The pauses a player has at the start of a game.
     */
    private static final int INITIAL_PAUSES = 3;

    private static @Nullable GameData gameData;

    /**
     * Loads the installed game resources.
     *
     * @throws IOException if the installed game resources cannot be read
     */
    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    /**
     * Closes the installed game resources when they were loaded.
     */
    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }

    /**
     * Encodes ordered selection handles using the tested packet width.
     *
     * @param opcode  the command opcode
     * @param handles the ordered native handles
     * @return the command containing the ordered encoded handles
     */
    private static @NotNull ReplayCommand createSelectionCommand(int opcode, int @NotNull ... handles) {
        int width = opcode < ReplayCommandIds.SELECT_121 ? Short.BYTES : Integer.BYTES;
        ByteBuffer payload = ByteBuffer.allocate(1 + handles.length * width).order(ByteOrder.LITTLE_ENDIAN);

        payload.put((byte) handles.length);

        for (int handle : handles) {
            if (width == Short.BYTES) {
                payload.putShort((short) handle);
            } else {
                payload.putInt(handle);
            }
        }

        return new ReplayCommand(0, ISSUER, opcode, payload.array());
    }

    /**
     * Supplies a persistent unit slot with the fixture memory, generation and sprite.
     *
     * @param state     the fixture world
     * @param poolIndex the persistent unit slot
     * @param unitType  the unit type index
     * @param owner     the unit owner
     * @return the supplied persistent unit slot
     */
    private static @NotNull Unit prepareUnit(@NotNull Game state, int poolIndex, int unitType, int owner) {
        UnitPool unitPool = state.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(poolIndex);

        UnitMemoryScenario.of(unit).writeMemoryWord(UnitMemoryScenario.UNIT_TYPE_OFFSET, unitType);
        UnitMemoryScenario.of(unit).writeMemoryByte(UnitMemoryScenario.OWNER_OFFSET, owner);
        UnitMemoryScenario.of(unit).writeMemoryByte(UnitMemoryScenario.PRIMARY_ORDER_OFFSET, ScrOrderIds.GUARD);

        UnitPoolScenario.setFreeGeneration(unitPool, poolIndex, 1);

        // A unit's sprite always has an image, which the team selection circle is linked to.
        Sprite sprite = SpriteScenario.createSprite(0, 0, 0, 0);

        sprite.insertTailImage(SpriteScenario.createImage(0, 0, 0));
        unit.setSprite(sprite);

        return unit;
    }

    /**
     * Creates a Replay Seek command of the fixture's issuer.
     *
     * @param frame the frame DWORD to seek to
     * @return the command
     */
    private static @NotNull ReplayCommand createReplaySeekCommand(int frame) {
        return new ReplayCommand(0, ISSUER, ReplayCommandIds.REPLAY_SEEK,
                                 ByteBuffer.allocate(REPLAY_SEEK_PAYLOAD_SIZE).order(ByteOrder.LITTLE_ENDIAN).putInt(frame).array());
    }

    /**
     * Creates a {@link Game} instance with the requested selection capacity.
     *
     * @param capacity the fixture slot capacity
     * @return the created {@link Game} instance, initialized with the requested selection capacity
     */
    private static @NotNull Game createSelectionGame(int capacity) {
        Game state = ReplayCommandScenario.createGame(gameData);

        state.getPlayerTable().setPlayer(0, new Player(0, ISSUER, PlayerTypes.HUMAN, 0, 1));
        state.getUnitSystem().initializeUnitPool(capacity);
        state.getSpriteSystem().initializeSpritePool(capacity);
        state.getSpriteSystem().initializeImagePool(capacity);

        UnitPool selectionPool = state.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(selectionPool);

        state.setSelection(new UnitSelection(selectionPool, true, 0, 0));
        state.markWorldInitialized();

        return state;
    }

    private static <C> void submitDecoded(@NotNull CommandSink sink,
                                          org.fogofwarlabs.sc4j.engine.player.@NotNull Player player,
                                          @NotNull ScrCommandType<C> type,
                                          byte @NotNull [] payload) throws IOException {
        sink.submit(player, type, type.read(new CommandInput(payload)));
    }

    @Test
    void execute_should_select_unit_with_fresh_replay_startup_context() throws IOException {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_ZVP_FIGHTING_SPIRIT)));
        ReplayInitialization initialization = ReplayInitialization.createFreshReplay(replay.getHeader());
        Game state = ReplayCommandScenario.createGame(gameData);

        state.getPlayerTable().setPlayer(0, new Player(0, ISSUER, PlayerTypes.HUMAN, 0, 1));
        state.getUnitSystem().initializeUnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        state.getSpriteSystem().initializeSpritePool(STARTUP_SPRITE_CAPACITY);
        state.getSpriteSystem().initializeImagePool(STARTUP_SPRITE_CAPACITY);

        UnitPool startupPool = state.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(startupPool);

        UnitSelection selection = initialization.createSelection(startupPool);
        ProductionStatistics statistics = new ProductionStatistics(false,
                                                                   new int[ProductionStatistics.VALUE_COUNT],
                                                                   new float[ProductionStatistics.VALUE_COUNT]);

        statistics.initializeSample(0);
        selection.bindStatistics(statistics);
        state.setSelection(selection);
        state.markWorldInitialized();

        Unit unit = prepareUnit(state, UnitPool.CLASSIC_UNIT_CAPACITY, ScrUnitIds.TERRAN_MARINE, 0);
        int handle = UnitHandle.encode(UnitPool.CLASSIC_UNIT_CAPACITY, 1, UnitPool.CLASSIC_UNIT_CAPACITY);
        ReplayCommand command = new ReplayCommand(0, ISSUER, ReplayCommandIds.SELECT, new byte[]{1, (byte) handle, (byte) (handle >>> Byte.SIZE)});
        ReplayCommandExecutor executor = new ReplayCommandExecutor();

        Assertions.assertTrue(executor.canExecute(state, command));

        executor.execute(state, command);

        Assertions.assertSame(unit, selection.getSelectedUnit(0, 0));
        Assertions.assertEquals(1, selection.getScopeTotal(0));
        Assertions.assertEquals(1, selection.getScopeChanges(0));
        Assertions.assertEquals(1, statistics.getSampleValue(0));

        statistics.advanceReplayFrame(GameSpeed.FASTEST_DURATION);

        Assertions.assertEquals(STARTUP_SCOPE_FLOAT_TOTAL_BITS, Float.floatToRawIntBits(selection.getScopeFloatTotal(0)));
    }

    @Test
    void create_sink_should_run_a_typed_command_as_the_recorded_command_runs() throws IOException {
        Game recorded = ReplayCommandScenario.createGame(gameData);
        Game typed = ReplayCommandScenario.createGame(gameData);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();

        for (Game state : List.of(recorded, typed)) {
            state.getPlayerTable().setPlayer(0, new Player(HIGH_BIT_COMMAND_PLAYER_INDEX, ISSUER, PlayerTypes.NONE, 0, 0));
            state.getRandom().setEnabled(1);
        }

        executor.execute(recorded, new ReplayCommand(0, ISSUER, ReplayCommandIds.KEEP_ALIVE, new byte[0]));

        ScrCommandType<?> type = executor.getCommandTypes().find(ReplayCommandIds.KEEP_ALIVE);
        int record = ReplayCommandIssuer.resolve(typed, ISSUER).getRecordIndex();
        org.fogofwarlabs.sc4j.engine.player.Player issuer = typed.getEngineGame().getPlayer(record);

        Assertions.assertNotNull(type);

        submitDecoded(executor.createSink(typed), issuer, type, new byte[0]);

        Assertions.assertEquals(1, typed.getCommandDispatchMarkerCount());
        Assertions.assertEquals(recorded.getCommandDispatchMarkerCount(), typed.getCommandDispatchMarkerCount());
        Assertions.assertEquals(recorded.computeStateHash(), typed.computeStateHash());
    }

    @Test
    void execute_should_reset_marker_count_for_each_keep_alive_without_consuming_random() {
        Game state = ReplayCommandScenario.createGame(gameData);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();
        ReplayCommand command = new ReplayCommand(0, ISSUER, ReplayCommandIds.KEEP_ALIVE, new byte[0]);

        state.getPlayerTable().setPlayer(0, new Player(HIGH_BIT_COMMAND_PLAYER_INDEX, ISSUER, PlayerTypes.NONE, 0, 0));
        state.getRandom().setEnabled(1);

        long initialHash = state.computeStateHash();

        Assertions.assertTrue(executor.canExecute(state, command));
        Assertions.assertEquals(initialHash, state.computeStateHash());

        executor.execute(state, command);

        Assertions.assertEquals(1, state.getCommandDispatchMarkerCount());

        long firstMarkerHash = state.computeStateHash();

        for (int previousCounter : new int[]{1, 2, Integer.MAX_VALUE, Integer.MIN_VALUE, -1}) {
            state.setCommandDispatchMarkerCount(previousCounter);
            executor.execute(state, command);

            Assertions.assertEquals(firstMarkerHash, state.computeStateHash());
        }

        Assertions.assertEquals(0, state.getCurrentFrame());
        Assertions.assertEquals(1, state.getRandom().getEnabled());
        Assertions.assertEquals(1, state.getRandom().getState());
        Assertions.assertEquals(0, state.getRandom().getTotalRandomCounts());
    }

    @Test
    void execute_should_accept_replay_seek_and_turn_rate_commands_without_a_simulation_effect() {
        Game state = ReplayCommandScenario.createGame(gameData);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();

        state.getPlayerTable().setPlayer(0, new Player(0, ISSUER, PlayerTypes.HUMAN, 1, 0));

        for (ReplayCommand command : new ReplayCommand[]{
                new ReplayCommand(0, ISSUER, ReplayCommandIds.REPLAY_SEEK, new byte[REPLAY_SEEK_PAYLOAD_SIZE]),
                new ReplayCommand(0, ISSUER, ReplayCommandIds.TURN_RATE, new byte[TURN_RATE_PAYLOAD_SIZE]),
                new ReplayCommand(0, ISSUER, ReplayCommandIds.DYNAMIC_TURN_RATE, new byte[DYNAMIC_TURN_RATE_PAYLOAD_SIZE])
        }) {
            state.setCommandDispatchMarkerCount(1);

            Assertions.assertTrue(executor.canExecute(state, command));

            executor.execute(state, command);

            Assertions.assertEquals(0, state.getCommandDispatchMarkerCount());
            Assertions.assertEquals(0, state.getRandom().getTotalRandomCounts());
        }

        ReplayCommand oversizedTurnRate = new ReplayCommand(0, ISSUER, ReplayCommandIds.TURN_RATE, new byte[TURN_RATE_PAYLOAD_SIZE + 1]);

        Assertions.assertFalse(executor.canExecute(state, oversizedTurnRate));
    }

    @Test
    void execute_should_end_the_game_for_restart_game_outside_a_network_session_only() {
        Game state = ReplayCommandScenario.createGame(gameData);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();
        ReplayCommand restart = new ReplayCommand(0, ISSUER, ReplayCommandIds.RESTART_GAME, new byte[0]);

        state.getPlayerTable().setPlayer(0, new Player(0, ISSUER, PlayerTypes.HUMAN, 0, 0));

        Assertions.assertFalse(executor.canExecute(state, restart), "the network session BYTE belongs to the trigger context");

        TriggerContext triggers = new TriggerContext();
        PlayerRelations relations = new PlayerRelations(new int[PlayerTable.PLAYER_SLOTS], false, false, false);
        int[] controls = new int[TriggerContext.CONTROL_COUNT];

        controls[TriggerContext.CONTROL_MULTIPLAYER_SESSION] = 1;
        triggers.initializeEvaluation(PlayerProgressTestUtils.createUnitAccounting(), relations, controls);
        state.setTriggerContext(triggers);
        executor.execute(state, restart);

        Assertions.assertNull(state.getEnd(), "a network session ignores the command");
        Assertions.assertEquals(0, state.getCommandDispatchMarkerCount());

        controls[TriggerContext.CONTROL_MULTIPLAYER_SESSION] = 0;
        triggers.initializeEvaluation(PlayerProgressTestUtils.createUnitAccounting(), relations, controls);
        executor.execute(state, restart);

        Assertions.assertEquals(GameEnd.RESTART, state.getEnd());
        Assertions.assertEquals(0, state.getRandom().getTotalRandomCounts());
    }

    @Test
    void execute_should_seek_the_viewer_playback_to_at_most_the_replay_frames_in_replay_playback() {
        Game state = ReplayCommandScenario.createGame(gameData);
        ReplayCommandExecutor executor = new ReplayCommandExecutor(RunningGameScenario.INSTRUCTION_LIMIT, REPLAY_FRAMES);
        List<Integer> targets = new ArrayList<>();

        state.getPlayerTable().setPlayer(0, new Player(0, ISSUER, PlayerTypes.HUMAN, 0, 0));
        executor.setPlaybackControl(new ReplayCommandExecutor.PlaybackControl() {
            /**
             * @return {@code true} if the viewer's playback is paused, {@code false} otherwise
             */
            @Override
            public boolean isPlaybackPaused() {
                return false;
            }

            /**
             * Sets whether the viewer's playback is paused.
             *
             * @param paused whether playback pauses
             */
            @Override
            public void setPlaybackPaused(boolean paused) {
                Assertions.fail("Replay Seek does not pause.");
            }

            /**
             * Seeks the viewer's playback to a frame: a frame before the current one restarts the replay first.
             *
             * @param targetFrame the frame to reach, the replay's frame count at most
             */
            @Override
            public void seekReplay(int targetFrame) {
                targets.add(targetFrame);
            }
        });

        executor.execute(state, createReplaySeekCommand(TARGET_PAST_END));

        Assertions.assertEquals(List.of(), targets, "a live game does not seek");

        state.setReplayPlayback(true);

        for (int target : new int[]{TARGET_PAST_END, TARGET_BEFORE_END, TARGET_NEGATIVE}) {
            executor.execute(state, createReplaySeekCommand(target));
        }

        Assertions.assertEquals(List.of(REPLAY_FRAMES, TARGET_BEFORE_END, REPLAY_FRAMES), targets);
        Assertions.assertEquals(0, state.getRandom().getTotalRandomCounts());
    }

    @Test
    void execute_should_validate_build_packet_length_before_selection_or_dat_access() {
        Game state = createSelectionGame(1);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();

        for (int length : new int[]{0, ReplayFile.BUILD_PAYLOAD - 1, ReplayFile.BUILD_PAYLOAD + 1}) {
            ReplayCommand command = new ReplayCommand(0, ISSUER, ReplayCommandIds.BUILD, new byte[length]);
            long previousHash = state.computeStateHash();

            Assertions.assertFalse(executor.canExecute(state, command));
            Assertions.assertThrows(UnsupportedOperationException.class, () -> executor.execute(state, command));
            Assertions.assertEquals(previousHash, state.computeStateHash());
        }

        state.setCommandDispatchMarkerCount(STALE_MARKER_COUNT);

        ReplayCommand command = new ReplayCommand(0, ISSUER, ReplayCommandIds.BUILD, new byte[]{31, 107, 0, 89, 0, (byte) 156, 0});

        Assertions.assertTrue(executor.canExecute(state, command));

        executor.execute(state, command);

        Assertions.assertEquals(0, state.getCommandDispatchMarkerCount());
        Assertions.assertEquals(0, state.getRandom().getTotalRandomCounts());
    }

    @Test
    void execute_should_validate_stop_payload_and_consume_empty_selection_without_random() {
        Game state = createSelectionGame(1);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();

        for (byte[] payload : new byte[][]{new byte[0], new byte[]{0, 0}}) {
            ReplayCommand command = new ReplayCommand(0, ISSUER, ReplayCommandIds.STOP, payload);

            Assertions.assertFalse(executor.canExecute(state, command));
            Assertions.assertThrows(UnsupportedOperationException.class, () -> executor.execute(state, command));
        }

        for (byte queue : new byte[]{0, 1, -1}) {
            state.setCommandDispatchMarkerCount(STALE_MARKER_COUNT);

            ReplayCommand command = new ReplayCommand(0, ISSUER, ReplayCommandIds.STOP, new byte[]{queue});

            Assertions.assertTrue(executor.canExecute(state, command));

            executor.execute(state, command);

            Assertions.assertEquals(0, state.getCommandDispatchMarkerCount());
            Assertions.assertEquals(0, state.getRandom().getTotalRandomCounts());
        }
    }

    @Test
    void execute_should_reject_malformed_train_payloads_and_consume_train_without_selection() {
        Game state = createSelectionGame(1);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();

        for (byte[] payload : new byte[][]{new byte[0], new byte[]{64}, new byte[]{64, 0, 0}}) {
            ReplayCommand command = new ReplayCommand(0, ISSUER, ReplayCommandIds.TRAIN, payload);
            long previousHash = state.computeStateHash();

            Assertions.assertFalse(executor.canExecute(state, command));
            Assertions.assertThrows(UnsupportedOperationException.class, () -> executor.execute(state, command));
            Assertions.assertEquals(previousHash, state.computeStateHash());
        }

        state.setCommandDispatchMarkerCount(STALE_MARKER_COUNT);

        ReplayCommand command = new ReplayCommand(0, ISSUER, ReplayCommandIds.TRAIN, new byte[]{64, 0});

        Assertions.assertTrue(executor.canExecute(state, command));

        executor.execute(state, command);

        Assertions.assertEquals(0, state.getCommandDispatchMarkerCount());

        UnitSelection selectionAfterTraining = state.getSelection();

        Assertions.assertNotNull(selectionAfterTraining);
        Assertions.assertNull(selectionAfterTraining.getSelectedUnit(0, 0));
        Assertions.assertEquals(0, state.getRandom().getTotalRandomCounts());

        state.getPlayerTable().setPlayer(0, new Player(OUT_OF_RANGE_COMMAND_PLAYER_INDEX, ISSUER, PlayerTypes.HUMAN, 0, 1));

        Assertions.assertFalse(executor.canExecute(state, command));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> executor.execute(state, command));
    }

    @Test
    void execute_should_preserve_state_for_unsupported_opcodes_payloads_and_issuers() {
        Game state = ReplayCommandScenario.createGame(gameData);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();

        state.getPlayerTable().setPlayer(0, new Player(0, ISSUER, PlayerTypes.HUMAN, 1, 0));
        state.setCommandDispatchMarkerCount(HIGH_BIT_MARKER_COUNT);

        for (ReplayCommand command : new ReplayCommand[]{
                new ReplayCommand(0, ISSUER, ReplayCommandIds.SAVE_GAME, new byte[0]),
                new ReplayCommand(0, ISSUER, ReplayCommandIds.SELECT, new byte[]{0}),
                new ReplayCommand(0, ISSUER, ReplayCommandIds.KEEP_ALIVE, new byte[]{0}),
                new ReplayCommand(0, -1, ReplayCommandIds.KEEP_ALIVE, new byte[0]),
                new ReplayCommand(0, MAXIMUM_ISSUER + 1, ReplayCommandIds.KEEP_ALIVE, new byte[0])
        }) {
            long previousHash = state.computeStateHash();

            Assertions.assertFalse(executor.canExecute(state, command));
            Assertions.assertThrows(UnsupportedOperationException.class, () -> executor.execute(state, command));
            Assertions.assertEquals(previousHash, state.computeStateHash());
        }

        ReplayCommand unmatched = new ReplayCommand(0, MAXIMUM_ISSUER, ReplayCommandIds.KEEP_ALIVE, new byte[0]);
        long previousHash = state.computeStateHash();

        Assertions.assertFalse(executor.canExecute(state, unmatched));
        Assertions.assertDoesNotThrow(() -> executor.execute(state, unmatched));
        Assertions.assertEquals(previousHash, state.computeStateHash());
    }

    @Test
    void can_execute_should_resolve_issuer_without_low_byte_aliasing_or_skipping_missing_records() {
        Game state = ReplayCommandScenario.createGame(gameData);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();
        ReplayCommand command = new ReplayCommand(0, ISSUER, ReplayCommandIds.KEEP_ALIVE, new byte[0]);

        state.getPlayerTable().setPlayer(0, new Player(0, ALIASED_ISSUER_ID, PlayerTypes.HUMAN, 1, 0));
        state.getPlayerTable().setPlayer(FIRST_EXTENDED_SLOT, new Player(FIRST_EXTENDED_SLOT, ISSUER, PlayerTypes.HUMAN, 1, 0));

        Assertions.assertFalse(executor.canExecute(state, command));

        state.getPlayerTable().setPlayer(LAST_PLAYABLE_SLOT, new Player(-1, ISSUER, PlayerTypes.NONE, 0, 0));

        Assertions.assertTrue(executor.canExecute(state, command));

        Game incompleteState = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(1));

        incompleteState.getPlayerTable().setPlayer(1, new Player(1, ISSUER, PlayerTypes.HUMAN, 1, 0));

        Assertions.assertFalse(executor.canExecute(incompleteState, command));
    }

    @Test
    void execute_should_apply_both_selection_widths_in_packet_order_and_update_history() {
        for (int firstOpcode : new int[]{ReplayCommandIds.SELECT, ReplayCommandIds.SELECT_121}) {
            Game state = createSelectionGame(3);
            ReplayCommandExecutor executor = new ReplayCommandExecutor();
            UnitSelection selection = state.getSelection();

            Assertions.assertNotNull(selection);

            RunningGameScenario.advanceTurns(state, 42);

            for (int poolIndex = 1; poolIndex <= 3; poolIndex++) {
                prepareUnit(state, poolIndex, ScrUnitIds.TERRAN_MARINE, 0);
            }

            state.setCommandDispatchMarkerCount(STALE_MARKER_COUNT);

            ReplayCommand replace = createSelectionCommand(firstOpcode, SECOND_UNIT_HANDLE, FIRST_UNIT_HANDLE, SECOND_UNIT_HANDLE);
            long before = state.computeStateHash();

            Assertions.assertTrue(executor.canExecute(state, replace));
            Assertions.assertEquals(before, state.computeStateHash());

            executor.execute(state, replace);

            Assertions.assertEquals(0, state.getCommandDispatchMarkerCount());
            Assertions.assertEquals(2, selection.getSelectedPoolIndex(0, 0));
            Assertions.assertEquals(1, selection.getSelectedPoolIndex(0, 1));
            Assertions.assertNull(selection.getSelectedUnit(0, 2));
            Assertions.assertEquals(SECOND_UNIT_HANDLE, selection.getHistoryHandle(0, NEWEST_HISTORY, 0));
            Assertions.assertEquals(FIRST_UNIT_HANDLE, selection.getHistoryHandle(0, NEWEST_HISTORY, 1));
            Assertions.assertEquals(42, selection.getHistoryTimestamp(0, NEWEST_HISTORY));
            Assertions.assertEquals(1, selection.getScopeTotal(0));
            Assertions.assertEquals(1, selection.getScopeChanges(0));

            executor.execute(state, createSelectionCommand(firstOpcode + 1, THIRD_UNIT_HANDLE));

            Assertions.assertEquals(3, selection.getSelectedPoolIndex(0, 2));
            Assertions.assertEquals(THIRD_UNIT_HANDLE, selection.getHistoryHandle(0, NEWEST_HISTORY - 1, 2));

            executor.execute(state, createSelectionCommand(firstOpcode + 2, FIRST_UNIT_HANDLE));

            Assertions.assertEquals(2, selection.getSelectedPoolIndex(0, 0));
            Assertions.assertEquals(3, selection.getSelectedPoolIndex(0, 1));
            Assertions.assertNull(selection.getSelectedUnit(0, 2));
            Assertions.assertEquals(THIRD_UNIT_HANDLE, selection.getHistoryHandle(0, NEWEST_HISTORY - 2, 1));
            Assertions.assertEquals(3, selection.getScopeTotal(0));
            Assertions.assertEquals(3, selection.getScopeChanges(0));
            Assertions.assertEquals(3.0f, selection.getScopeFloatTotal(0));
            Assertions.assertEquals(3.0f, selection.getScopeFloatChanges(0));
            Assertions.assertEquals(0, state.getRandom().getTotalRandomCounts());
        }
    }

    @Test
    void execute_should_check_selection_add_capacity_before_lookup_or_duplicate_removal() {
        Game state = createSelectionGame(3);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();
        UnitSelection selection = state.getSelection();

        for (int poolIndex = 1; poolIndex <= 3; poolIndex++) {
            prepareUnit(state, poolIndex, ScrUnitIds.TERRAN_MARINE, 0);
        }

        Assertions.assertNotNull(selection);

        RunningGameScenario.advanceTurns(state, 5);
        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_121, FIRST_UNIT_HANDLE, SECOND_UNIT_HANDLE, THIRD_UNIT_HANDLE));

        RunningGameScenario.advanceTurns(state, 4);
        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_ADD_121,
                                                       FIRST_UNIT_HANDLE, FIRST_UNIT_HANDLE, FIRST_UNIT_HANDLE, FIRST_UNIT_HANDLE,
                                                       FIRST_UNIT_HANDLE, FIRST_UNIT_HANDLE, FIRST_UNIT_HANDLE, FIRST_UNIT_HANDLE,
                                                       FIRST_UNIT_HANDLE, OUTSIDE_POOL_HANDLE));

        Assertions.assertEquals(3, selection.getSelectedPoolIndex(0, 2));
        Assertions.assertEquals(2, selection.getScopeTotal(0));
        Assertions.assertEquals(1, selection.getScopeChanges(0));
        Assertions.assertEquals(5, selection.getHistoryTimestamp(0, NEWEST_HISTORY));
        Assertions.assertEquals(0, selection.getHistoryTimestamp(0, NEWEST_HISTORY - 1));
    }

    @Test
    void execute_should_distinguish_removal_lookup_success_from_removing_selected_unit() {
        Game state = createSelectionGame(3);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();
        UnitSelection selection = state.getSelection();

        for (int poolIndex = 1; poolIndex <= 3; poolIndex++) {
            prepareUnit(state, poolIndex, ScrUnitIds.TERRAN_MARINE, 0);
        }

        Assertions.assertNotNull(selection);

        RunningGameScenario.advanceTurns(state, 10);
        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_121, FIRST_UNIT_HANDLE, SECOND_UNIT_HANDLE));

        state.incrementTurnCount();
        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_REMOVE_121, THIRD_UNIT_HANDLE));

        Assertions.assertEquals(2, selection.getScopeChanges(0));
        Assertions.assertEquals(11, selection.getHistoryTimestamp(0, NEWEST_HISTORY - 1));

        state.incrementTurnCount();
        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_REMOVE_121, THIRD_UNIT_OTHER_GENERATION_HANDLE));

        Assertions.assertEquals(3, selection.getScopeTotal(0));
        Assertions.assertEquals(2, selection.getScopeChanges(0));
        Assertions.assertEquals(0, selection.getHistoryTimestamp(0, NEWEST_HISTORY - 2));

        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_121));

        Assertions.assertNull(selection.getSelectedUnit(0, 0));
        Assertions.assertEquals(4, selection.getScopeTotal(0));
        Assertions.assertEquals(2, selection.getScopeChanges(0));
    }

    @Test
    void execute_should_resolve_selected_slots_by_sprite_orders_and_full_generation() {
        Game state = createSelectionGame(4);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();
        UnitSelection selection = state.getSelection();
        Unit live = prepareUnit(state, 1, ScrUnitIds.TERRAN_MARINE, 0);
        Unit dead = prepareUnit(state, 2, ScrUnitIds.TERRAN_MARINE, 0);

        UnitMemoryScenario.of(dead).writeMemoryByte(UnitMemoryScenario.PRIMARY_ORDER_OFFSET, ScrOrderIds.DIE);
        UnitMemoryScenario.of(dead).writeMemoryByte(PRIMARY_ORDER_STATE_OFFSET, 1);

        Unit missingSprite = prepareUnit(state, 3, ScrUnitIds.TERRAN_MARINE, 0);

        missingSprite.setSprite(null);

        Unit fourth = prepareUnit(state, 4, ScrUnitIds.TERRAN_MARINE, 0);

        UnitMemoryScenario.of(fourth).writeMemoryByte(UnitMemoryScenario.PRIMARY_ORDER_OFFSET, ScrOrderIds.DIE);
        UnitMemoryScenario.of(fourth).writeMemoryByte(PRIMARY_ORDER_STATE_OFFSET, 0);

        UnitPool allocationPool = state.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(allocationPool);
        Assertions.assertFalse(allocationPool.isAllocated(1));

        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_121,
                                                       0, SECOND_UNIT_HANDLE, THIRD_UNIT_HANDLE, FIRST_UNIT_OTHER_GENERATION_HANDLE,
                                                       FIRST_UNIT_HIGH_BIT_HANDLE, FIRST_UNIT_HANDLE, FOURTH_UNIT_HANDLE));

        Assertions.assertNotNull(selection);
        Assertions.assertSame(live, selection.getSelectedUnit(0, 0));
        Assertions.assertSame(fourth, selection.getSelectedUnit(0, 1));
        Assertions.assertNull(selection.getSelectedUnit(0, 2));
    }

    @Test
    void execute_should_allow_first_foreign_unit_but_stop_owned_history_prefix() {
        Game state = createSelectionGame(2);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();
        UnitSelection selection = state.getSelection();
        Unit foreign = prepareUnit(state, 1, ScrUnitIds.TERRAN_MARINE, 1);
        Unit own = prepareUnit(state, 2, ScrUnitIds.TERRAN_MARINE, 0);

        Assertions.assertNotNull(selection);

        RunningGameScenario.advanceTurns(state, 3);
        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_121, FIRST_UNIT_HANDLE, SECOND_UNIT_HANDLE));

        Assertions.assertSame(foreign, selection.getSelectedUnit(0, 0));
        Assertions.assertSame(own, selection.getSelectedUnit(0, 1));
        Assertions.assertEquals(3, selection.getHistoryTimestamp(0, NEWEST_HISTORY));
        Assertions.assertEquals(0, selection.getHistoryHandle(0, NEWEST_HISTORY, 0));

        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_121, SECOND_UNIT_HANDLE, FIRST_UNIT_HANDLE));

        Assertions.assertSame(own, selection.getSelectedUnit(0, 0));
        Assertions.assertNull(selection.getSelectedUnit(0, 1));
    }

    @Test
    void execute_should_keep_replace_add_and_multi_selection_filters_distinct() {
        Game state = createSelectionGame(3);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();
        UnitSelection selection = state.getSelection();
        Unit first = prepareUnit(state, 1, ScrUnitIds.TERRAN_MARINE, 0);
        Unit second = prepareUnit(state, 2, ScrUnitIds.TERRAN_MARINE, 0);
        Unit excluded = prepareUnit(state, 3, ScrUnitIds.TERRAN_NUCLEAR_MISSILE, 0);
        Sprite firstSprite = first.getSpriteOrNull();

        Assertions.assertNotNull(firstSprite);

        firstSprite.setFlags(Sprite.FLAG_HIDDEN);
        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_121, FIRST_UNIT_HANDLE, THIRD_UNIT_HANDLE, SECOND_UNIT_HANDLE));

        Assertions.assertNotNull(selection);
        Assertions.assertSame(second, selection.getSelectedUnit(0, 0));
        Assertions.assertNull(selection.getSelectedUnit(0, 1));

        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_121));
        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_ADD_121, THIRD_UNIT_HANDLE));

        Assertions.assertSame(excluded, selection.getSelectedUnit(0, 0));

        firstSprite.setFlags(0);

        for (int offset : new int[]{LOCKDOWN_TIMER_OFFSET, STASIS_TIMER_OFFSET, MAELSTROM_TIMER_OFFSET}) {
            UnitMemoryScenario.of(second).writeMemoryByte(offset, 1);
            executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_121, FIRST_UNIT_HANDLE, SECOND_UNIT_HANDLE));

            Assertions.assertNull(selection.getSelectedUnit(0, 1));

            UnitMemoryScenario.of(second).writeMemoryByte(offset, 0);
        }

        UnitMemoryScenario.of(second).writeMemoryInteger(UnitMemoryScenario.STATUS_OFFSET, UnitStatusFlags.DISABLED);
        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_121, FIRST_UNIT_HANDLE, SECOND_UNIT_HANDLE));

        Assertions.assertNull(selection.getSelectedUnit(0, 1));

        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_121, SECOND_UNIT_HANDLE));

        Assertions.assertSame(second, selection.getSelectedUnit(0, 0));
    }

    @Test
    void execute_should_leave_state_unchanged_for_malformed_selections_and_legacy_overflow() {
        Game state = createSelectionGame(2);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();

        prepareUnit(state, 1, ScrUnitIds.TERRAN_MARINE, 0);
        prepareUnit(state, 2, ScrUnitIds.TERRAN_MARINE, 0);
        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_121, FIRST_UNIT_HANDLE));

        // Handles whose index lies outside the pool resolve to no unit; the command stays executable.
        Assertions.assertTrue(executor.canExecute(state, createSelectionCommand(ReplayCommandIds.SELECT_121, SLOT_ZERO_HANDLE)));
        Assertions.assertTrue(executor.canExecute(state, createSelectionCommand(ReplayCommandIds.SELECT_121, THIRD_UNIT_HANDLE)));

        ReplayCommand[] malformedCommands = {createSelectionCommand(ReplayCommandIds.SELECT, new int[UnitSelection.MAXIMUM_SELECTED + 1]),
                                             new ReplayCommand(0, ISSUER, ReplayCommandIds.SELECT_121, new byte[]{1})};

        for (ReplayCommand command : malformedCommands) {
            long before = state.computeStateHash();

            Assertions.assertFalse(executor.canExecute(state, command));
            Assertions.assertThrows(UnsupportedOperationException.class, () -> executor.execute(state, command));
            Assertions.assertEquals(before, state.computeStateHash());
        }

        state.setCommandDispatchMarkerCount(99);
        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_121, new int[UnitSelection.MAXIMUM_SELECTED + 1]));

        Assertions.assertEquals(0, state.getCommandDispatchMarkerCount());

        UnitSelection selection = state.getSelection();

        Assertions.assertNotNull(selection);
        Assertions.assertEquals(1, selection.getScopeTotal(0));
        Assertions.assertEquals(1, selection.getSelectedPoolIndex(0, 0));

        for (int historyIndex = 0; historyIndex < HISTORY_COUNT; historyIndex++) {
            selection.setHistoryTimestamp(0, historyIndex, MAXIMUM_HISTORY_TIMESTAMP);
        }

        long before = state.computeStateHash();

        Assertions.assertFalse(executor.canExecute(state, createSelectionCommand(ReplayCommandIds.SELECT_ADD_121, SECOND_UNIT_HANDLE)));
        Assertions.assertThrows(UnsupportedOperationException.class,
                                () -> executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_ADD_121, SECOND_UNIT_HANDLE)));
        Assertions.assertEquals(before, state.computeStateHash());
    }

    @Test
    void execute_should_reject_selection_until_world_is_initialized() {
        Game state = ReplayCommandScenario.createGame(gameData);

        state.getPlayerTable().setPlayer(0, new Player(0, ISSUER, PlayerTypes.HUMAN, 0, 1));
        state.getUnitSystem().initializeUnitPool(1);
        state.getSpriteSystem().initializeSpritePool(1);
        state.getSpriteSystem().initializeImagePool(1);

        UnitPool selectionPool = state.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(selectionPool);

        state.setSelection(new UnitSelection(selectionPool, true, 0, 0));
        prepareUnit(state, 1, ScrUnitIds.TERRAN_MARINE, 0);

        ReplayCommandExecutor executor = new ReplayCommandExecutor();
        ReplayCommand command = createSelectionCommand(ReplayCommandIds.SELECT_121, FIRST_UNIT_HANDLE);
        long before = state.computeStateHash();

        Assertions.assertFalse(executor.canExecute(state, command));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> executor.execute(state, command));
        Assertions.assertEquals(before, state.computeStateHash());

        state.markWorldInitialized();

        Assertions.assertTrue(executor.canExecute(state, command));
    }

    @Test
    void can_execute_should_accept_selection_without_a_modeled_recorder() {
        ReplayCommandExecutor executor = new ReplayCommandExecutor();
        ReplayCommand command = createSelectionCommand(ReplayCommandIds.SELECT_121, FIRST_UNIT_HANDLE);
        Game state = ReplayCommandScenario.createGame(gameData);

        state.getPlayerTable().setPlayer(0, new Player(0, ISSUER, PlayerTypes.HUMAN, 0, 1));
        state.getPlayerTable().setPlayer(1, new Player(1, TEAMMATE_ISSUER, PlayerTypes.HUMAN, 0, 1));
        state.getUnitSystem().initializeUnitPool(1);
        state.getSpriteSystem().initializeSpritePool(1);
        state.getSpriteSystem().initializeImagePool(1);

        UnitPool selectionPool = state.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(selectionPool);

        state.setSelection(new UnitSelection(selectionPool, true, 0, 1));
        state.markWorldInitialized();
        prepareUnit(state, 1, ScrUnitIds.TERRAN_MARINE, 0);

        Assertions.assertTrue(executor.canExecute(state, command));
    }

    @Test
    void execute_should_keep_teammate_selections_apart_and_mark_them_for_local_slot() {
        int teamSize = 4;
        int selectionCircleMask = Sprite.FLAG_TEAM_SELECTION_CIRCLE_1 | Sprite.FLAG_TEAM_SELECTION_CIRCLE_2;
        ReplayCommandExecutor executor = new ReplayCommandExecutor();
        Game state = ReplayCommandScenario.createGame(gameData);

        state.getPlayerTable().setPlayer(0, new Player(0, ISSUER, PlayerTypes.HUMAN, 0, 1));
        state.getPlayerTable().setPlayer(1, new Player(0, TEAMMATE_ISSUER, PlayerTypes.HUMAN, 0, 1));
        state.getUnitSystem().initializeUnitPool(2);
        state.getSpriteSystem().initializeSpritePool(2);
        state.getSpriteSystem().initializeImagePool(2);

        UnitPool selectionPool = state.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(selectionPool);

        UnitSelection selection = new UnitSelection(selectionPool, true, teamSize, 0);

        state.setSelection(selection);
        state.markWorldInitialized();

        Unit first = prepareUnit(state, 1, ScrUnitIds.TERRAN_MARINE, 0);
        Unit second = prepareUnit(state, 2, ScrUnitIds.TERRAN_MARINE, 0);
        Sprite firstSprite = first.getSpriteOrNull();
        Sprite secondSprite = second.getSpriteOrNull();
        int firstHandle = UnitHandle.encode(1, 1, 2);
        int secondHandle = UnitHandle.encode(2, 1, 2);

        Assertions.assertNotNull(firstSprite);
        Assertions.assertNotNull(secondSprite);

        executor.execute(state, new ReplayCommand(0, TEAMMATE_ISSUER, ReplayCommandIds.SELECT_121,
                                                  createSelectionCommand(ReplayCommandIds.SELECT_121, firstHandle).getPayload()));

        Assertions.assertEquals(0, selection.getSelectedPoolIndex(0, 0), "The leader's own selection is untouched.");
        Assertions.assertEquals(1, selection.getSelectedPoolIndex(1, 0));
        Assertions.assertEquals(FIRST_TEAM_SELECTION_CIRCLE_FLAG, firstSprite.getFlags() & selectionCircleMask);

        executor.execute(state, new ReplayCommand(0, ISSUER, ReplayCommandIds.SELECT_121,
                                                  createSelectionCommand(ReplayCommandIds.SELECT_121, firstHandle, secondHandle).getPayload()));

        Assertions.assertEquals(1, selection.getSelectedPoolIndex(0, 0));
        Assertions.assertEquals(2, selection.getSelectedPoolIndex(0, 1));
        Assertions.assertEquals(FIRST_TEAM_SELECTION_CIRCLE_FLAG, firstSprite.getFlags() & selectionCircleMask, "The local slot marks nothing.");
        Assertions.assertEquals(0, secondSprite.getFlags() & selectionCircleMask);

        executor.execute(state, new ReplayCommand(0, TEAMMATE_ISSUER, ReplayCommandIds.SELECT_ADD_121,
                                                  createSelectionCommand(ReplayCommandIds.SELECT_ADD_121, secondHandle).getPayload()));

        Assertions.assertEquals(FIRST_TEAM_SELECTION_CIRCLE_FLAG, secondSprite.getFlags() & selectionCircleMask);

        executor.execute(state, new ReplayCommand(0, TEAMMATE_ISSUER, ReplayCommandIds.SELECT_121,
                                                  createSelectionCommand(ReplayCommandIds.SELECT_121, secondHandle).getPayload()));

        Assertions.assertEquals(0, firstSprite.getFlags() & selectionCircleMask, "Replacing the selection unmarks its old units.");
        Assertions.assertEquals(FIRST_TEAM_SELECTION_CIRCLE_FLAG, secondSprite.getFlags() & selectionCircleMask);
        Assertions.assertEquals(1, selection.getSelectedPoolIndex(0, 0), "The leader keeps its own selection.");

        executor.execute(state, new ReplayCommand(0, TEAMMATE_ISSUER, ReplayCommandIds.SELECT_REMOVE_121,
                                                  createSelectionCommand(ReplayCommandIds.SELECT_REMOVE_121, secondHandle).getPayload()));

        Assertions.assertEquals(0, secondSprite.getFlags() & selectionCircleMask);
        Assertions.assertEquals(0, selection.getSelectedPoolIndex(1, 0));
    }

    @Test
    void execute_should_consume_undispatched_commands_without_changing_state() {
        Game state = ReplayCommandScenario.createGame(gameData);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();

        state.getPlayerTable().setPlayer(0, new Player(0, ISSUER, PlayerTypes.HUMAN, 0, 0));

        ReplayCommand[] commands = {
                new ReplayCommand(0, ISSUER, ReplayCommandIds.VOICE_ENABLE, new byte[0]),
                new ReplayCommand(0, ISSUER, ReplayCommandIds.VOICE_SQUELCH, new byte[1]),
                new ReplayCommand(0, ISSUER, ReplayCommandIds.START_GAME, new byte[0]),
                new ReplayCommand(0, ISSUER, ReplayCommandIds.JOINED_GAME, new byte[17]),
                new ReplayCommand(0, ISSUER, ReplayCommandIds.SAVED_DATA, new byte[12]),
                new ReplayCommand(0, ISSUER, ReplayCommandIds.BRIEFING_START, new byte[0]),
                new ReplayCommand(0, ISSUER, ReplayCommandIds.MAKE_GAME_PUBLIC, new byte[0]),
                new ReplayCommand(0, ISSUER, ReplayCommandIds.SAVE_GAME, new byte[]{1, 0, 0, 0, 'a'}),
                new ReplayCommand(0, ISSUER, ReplayCommandIds.LOAD_GAME, new byte[]{0, 0, 0, 0}),
                new ReplayCommand(0, ISSUER, ReplayCommandIds.LATENCY, new byte[]{1}),
                new ReplayCommand(0, ISSUER, ReplayCommandIds.REPLAY_SPEED, new byte[9])
        };

        for (ReplayCommand command : commands) {
            state.setCommandDispatchMarkerCount(0);

            long before = state.computeStateHash();

            Assertions.assertTrue(executor.canExecute(state, command), Integer.toHexString(command.getTypeId()));

            executor.execute(state, command);

            Assertions.assertEquals(before, state.computeStateHash(), Integer.toHexString(command.getTypeId()));
        }

        Assertions.assertFalse(executor.canExecute(state, new ReplayCommand(0, ISSUER, ReplayCommandIds.LATENCY, new byte[0])));
        Assertions.assertFalse(executor.canExecute(state, new ReplayCommand(0, ISSUER, ReplayCommandIds.RESTART_GAME, new byte[1])));
    }

    @Test
    void execute_should_count_sync_as_turn_marker_without_validating_its_checksum() {
        Game state = ReplayCommandScenario.createGame(gameData);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();

        state.getPlayerTable().setPlayer(0, new Player(0, ISSUER, PlayerTypes.HUMAN, 0, 0));

        ReplayCommand sync = new ReplayCommand(0, ISSUER, ReplayCommandIds.SYNC, new byte[]{0x21, 0x34, 0x12, 1, 0, 3});

        state.setCommandDispatchMarkerCount(5);

        Assertions.assertTrue(executor.canExecute(state, sync));

        executor.execute(state, sync);

        Assertions.assertEquals(1, state.getCommandDispatchMarkerCount());
        Assertions.assertFalse(executor.canExecute(state, new ReplayCommand(0, ISSUER, ReplayCommandIds.SYNC, new byte[5])));
        Assertions.assertEquals(0, state.getRandom().getTotalRandomCounts());
    }

    @Test
    void execute_should_pause_game_and_playback_until_recorded_resume() {
        Game state = ReplayCommandScenario.createGame(gameData);

        state.getPlayerTable().setPlayer(0, new Player(0, ISSUER, PlayerTypes.HUMAN, 0, 0));

        ReplayCommandExecutor executor = new ReplayCommandExecutor();
        ReplayCommand pause = new ReplayCommand(0, ISSUER, ReplayCommandIds.PAUSE, new byte[0]);
        ReplayCommand resume = new ReplayCommand(0, ISSUER, ReplayCommandIds.RESUME, new byte[0]);

        Assertions.assertFalse(executor.canExecute(state, pause), "the paused DWORD belongs to the trigger context");

        TriggerContext triggers = new TriggerContext();

        state.setTriggerContext(triggers);

        boolean[] playbackPaused = {false};

        executor.setPlaybackControl(new ReplayCommandExecutor.PlaybackControl() {
            /**
             * @return {@code true} if the viewer's playback is paused, {@code false} otherwise
             */
            @Override
            public boolean isPlaybackPaused() {
                return playbackPaused[0];
            }

            /**
             * Sets whether the viewer's playback is paused.
             *
             * @param paused whether playback pauses
             */
            @Override
            public void setPlaybackPaused(boolean paused) {
                playbackPaused[0] = paused;
            }

            /**
             * Seeks the viewer's playback to a frame: a frame before the current one restarts the replay first.
             *
             * @param targetFrame the frame to reach, the replay's frame count at most
             */
            @Override
            public void seekReplay(int targetFrame) {
                Assertions.fail("Pause and Resume do not seek.");
            }
        });

        executor.execute(state, pause);

        Assertions.assertEquals(1, triggers.getPhaseGate());
        Assertions.assertTrue(playbackPaused[0]);

        List<PauseNotice> notices = executor.takePauseNotices();

        Assertions.assertEquals(1, notices.size());
        Assertions.assertTrue(notices.getFirst().isPaused());
        Assertions.assertEquals(0, notices.getFirst().getSlot());
        Assertions.assertEquals(INITIAL_PAUSES, notices.getFirst().getPausesLeft());

        playbackPaused[0] = false;
        executor.execute(state, pause);

        Assertions.assertFalse(playbackPaused[0]);

        executor.execute(state, resume);

        Assertions.assertEquals(0, triggers.getPhaseGate());
        Assertions.assertFalse(playbackPaused[0]);

        notices = executor.takePauseNotices();

        Assertions.assertEquals(1, notices.size(), "the repeated pause makes no notice");
        Assertions.assertFalse(notices.getFirst().isPaused());

        playbackPaused[0] = true;
        executor.execute(state, pause);
        executor.execute(state, resume);

        Assertions.assertEquals(0, triggers.getPhaseGate());
        Assertions.assertTrue(playbackPaused[0]);

        // Resume without a pause changes nothing.
        playbackPaused[0] = false;
        executor.execute(state, resume);

        Assertions.assertEquals(0, triggers.getPhaseGate());
        Assertions.assertFalse(playbackPaused[0]);
    }

    @Test
    void execute_should_consume_order_nothing_and_validate_its_empty_payload() {
        Game state = createSelectionGame(1);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();

        Assertions.assertFalse(executor.canExecute(state, new ReplayCommand(0, ISSUER, ReplayCommandIds.ORDER_NOTHING, new byte[1])));

        state.setCommandDispatchMarkerCount(STALE_MARKER_COUNT);

        ReplayCommand command = new ReplayCommand(0, ISSUER, ReplayCommandIds.ORDER_NOTHING, new byte[0]);

        Assertions.assertTrue(executor.canExecute(state, command));

        executor.execute(state, command);

        Assertions.assertEquals(0, state.getCommandDispatchMarkerCount());
        Assertions.assertEquals(0, state.getRandom().getTotalRandomCounts());
    }

    @Test
    void execute_should_ignore_build_with_unexpected_order_or_outside_tile() {
        Game state = createSelectionGame(1);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();

        prepareUnit(state, 1, ScrUnitIds.TERRAN_MARINE, 0);
        executor.execute(state, createSelectionCommand(ReplayCommandIds.SELECT_121, FIRST_UNIT_HANDLE));

        UnitSelection selection = state.getSelection();

        Assertions.assertNotNull(selection);
        Assertions.assertNotNull(selection.getSelectedUnit(0, 0));

        for (byte[] payload : new byte[][]{{0, 0, 0, 0, 0, 106, 0}, {(byte) 0xBC, 0, 0, 0, 0, 106, 0}, {30, 1, 0, 0, 0, 106, 0}}) {
            ReplayCommand command = new ReplayCommand(0, ISSUER, ReplayCommandIds.BUILD, payload);
            long before = state.computeStateHash();

            Assertions.assertTrue(executor.canExecute(state, command));
            Assertions.assertDoesNotThrow(() -> executor.execute(state, command));
            Assertions.assertEquals(before, state.computeStateHash());
        }
    }

    @Test
    void execute_should_not_run_cheat_text_from_chat_and_require_full_chat_payload() {
        Game state = createSelectionGame(1);
        ReplayCommandExecutor executor = new ReplayCommandExecutor();
        byte[] payload = new byte[1 + ReplayFile.CHAT_MESSAGE_SIZE];

        payload[0] = 3;

        byte[] text = "operation cwal".getBytes(StandardCharsets.US_ASCII);

        System.arraycopy(text, 0, payload, 1, text.length);

        long before = state.computeStateHash();

        executor.execute(state, new ReplayCommand(CHAT_FRAME, ISSUER, ReplayCommandIds.CHAT, payload));

        Assertions.assertFalse(state.isOperationCwal());
        Assertions.assertEquals(before, state.computeStateHash());

        ReplayCommand shortChat = new ReplayCommand(CHAT_FRAME, ISSUER, ReplayCommandIds.CHAT, new byte[ReplayFile.CHAT_MESSAGE_SIZE]);

        Assertions.assertFalse(executor.canExecute(state, shortChat));

        byte[] fullPayload = new byte[1 + ReplayFile.CHAT_MESSAGE_SIZE];

        Arrays.fill(fullPayload, (byte) 'a');
        fullPayload[0] = 2;
        executor.execute(state, new ReplayCommand(CHAT_FRAME, ISSUER, ReplayCommandIds.CHAT, fullPayload));

        Assertions.assertEquals(before, state.computeStateHash());
    }
}
