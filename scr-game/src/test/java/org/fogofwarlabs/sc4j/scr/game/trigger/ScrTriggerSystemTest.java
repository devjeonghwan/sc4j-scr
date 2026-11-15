package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.game.NetworkReplayScenario;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.*;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting.TriggerSnapshot;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;

/**
 * Checks trigger registration, execution, waits and failure effects, and that a defeat in a multiplayer session releases the classic trigger
 * list of the player, against the synchronization words of a recorded LAN game.
 */
final class ScrTriggerSystemTest {
    private static final int FRAME_DURATION = GameSpeed.FASTEST_DURATION;
    /**
     * The replay mode argument while replaying.
     */
    private static final int REPLAY_GAME = 1;
    /**
     * The game-paused DWORD of a game paused by a trigger.
     */
    private static final int GAME_PAUSED = 1;
    /**
     * The trigger pause BYTE of a game paused by a trigger.
     */
    private static final int TRIGGERS_RUN_WHILE_PAUSED = 1;

    /**
     * The update calls per game second, including the zero countdown call.
     */
    private static final int CALLS_PER_SECOND = 16;
    /**
     * The reload value of the periodic and clock countdowns.
     */
    private static final int COUNTDOWN_RELOAD = 15;
    /**
     * The reload value of the trigger cycle WORD.
     */
    private static final int CYCLE_RELOAD = 30;
    /**
     * The countdown display of a hidden countdown.
     */
    private static final int COUNTDOWN_HIDDEN = -1;
    /**
     * The current-player DWORD after the active-player walk ended.
     */
    private static final int WALK_END_PLAYER = 8;
    /**
     * The execution flags of a disabled record whose actions run.
     */
    private static final int DISABLED_RUNNING_FLAGS = 0x9;
    /**
     * The execution flags of a preserved record.
     */
    private static final int PRESERVE_FLAGS = 0x4;
    /**
     * The execution flags of a preserved record whose actions run.
     */
    private static final int PRESERVE_RUNNING_FLAGS = 0x5;
    /**
     * The action cursor after the last action.
     */
    private static final int FINISHED_ACTION_INDEX = 0x40;
    /**
     * The Set To modifier byte.
     */
    private static final int SET_TO_MODIFIER = 7;

    /**
     * The installed melee trigger resource.
     */
    private static final @NotNull String MELEE_TRIGGERS_PATH = "triggers/Melee.trg";
    /**
     * The SHA-256 digest of the native capture of the melee trigger evaluation.
     */
    private static final @NotNull String EVALUATION_CAPTURE_HASH = "dc6468e0a6efdbf7cf709e73e2fe77276aee84aa8ec99602735201d3c1e742ac";
    /**
     * The cycle, periodic and clock countdowns and the current player that start each captured case.
     */
    private static final int CAPTURE_HEADER_SIZE = 4 * Integer.BYTES;
    /**
     * The captured game-context counters from the counter block to the end of the trigger count cache.
     */
    private static final int CAPTURED_COUNTERS_SIZE = 0x5AC0;
    /**
     * The game-context offset of the counter block that the accounting fixture represents.
     */
    private static final int COUNTERS_OFFSET = 0x2CF4;
    private static final int CAPTURE_CASE_COUNT = 64;
    private static final int FIRST_UPDATE_CASE_COUNT = 512;
    /**
     * The recording client's player of the defeat test.
     */
    private static final int DEFEAT_LOCAL_PLAYER = 0;
    /**
     * The last frame the recording of the defeat test runs to.
     */
    private static final int DEFEAT_LAST_FRAME = 4802;

    private static @NotNull TriggerContext createBoundedContext(byte @NotNull [] resource) {
        int[] playerTypes = {PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                             PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(TriggerPlayerScenario.createPlayers(playerTypes, new byte[PlayerTable.PLAYABLE_PLAYER_SLOTS]));
        context.initializeEvaluation(PlayerProgressTestUtils.createUnitAccounting(), new PlayerRelations(playerTypes, false, false, false),
                                     new int[TriggerContext.CONTROL_COUNT]);
        context.loadAllPlayersResource(resource);
        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, GAME_PAUSED, TRIGGERS_RUN_WHILE_PAUSED);

        return context;
    }


    @Test
    void update_should_refresh_leaderboard_and_clock_every_sixteen_ungated_calls() {
        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(TriggerPlayerScenario.createPlayers(new int[PlayerTable.PLAYABLE_PLAYER_SLOTS],
                                                                                   new byte[PlayerTable.PLAYABLE_PLAYER_SLOTS]));

        for (int position = 0; position < PlayerTable.PLAYABLE_PLAYER_SLOTS; position++) {
            Assertions.assertEquals(0, context.getLeaderboardPlayerIndex(position), "The leaderboard pairs start zeroed.");
            Assertions.assertEquals(0, context.getLeaderboardValue(position));
        }

        for (int frame = 1; frame <= 3 * CALLS_PER_SECOND; frame++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);

            Assertions.assertEquals(frame / CALLS_PER_SECOND, context.getElapsedClockTicks());
            Assertions.assertEquals(COUNTDOWN_RELOAD - frame % CALLS_PER_SECOND, context.getClockCountdown());
            Assertions.assertEquals(COUNTDOWN_RELOAD - frame % CALLS_PER_SECOND, context.getPeriodicCountdown());

            for (int position = 0; position < PlayerTable.PLAYABLE_PLAYER_SLOTS; position++) {
                Assertions.assertEquals(frame < CALLS_PER_SECOND ? 0 : -1, context.getLeaderboardPlayerIndex(position));
                Assertions.assertEquals(0, context.getLeaderboardValue(position));
            }
        }

        int clockTicks = context.getElapsedClockTicks();

        for (int frame = 0; frame < 2 * CALLS_PER_SECOND; frame++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, GAME_PAUSED, TRIGGERS_RUN_WHILE_PAUSED);
        }

        Assertions.assertEquals(clockTicks, context.getElapsedClockTicks());
        Assertions.assertEquals(COUNTDOWN_RELOAD, context.getPeriodicCountdown());
        Assertions.assertEquals(COUNTDOWN_RELOAD, context.getClockCountdown());
    }

    @Test
    void countdown_display_follows_the_second_tick() {
        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(TriggerPlayerScenario.createPlayers(new int[PlayerTable.PLAYABLE_PLAYER_SLOTS],
                                                                                   new byte[PlayerTable.PLAYABLE_PLAYER_SLOTS]));

        Assertions.assertEquals(COUNTDOWN_HIDDEN, context.getCountdownDisplay());

        context.countdownTimer = 2;

        for (int frame = 1; frame < CALLS_PER_SECOND; frame++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);

            Assertions.assertEquals(COUNTDOWN_HIDDEN, context.getCountdownDisplay(), "Set Countdown Timer shows at the next second.");
        }

        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);

        Assertions.assertEquals(1, context.countdownTimer);
        Assertions.assertEquals(1, context.getCountdownDisplay());

        for (int frame = 0; frame < CALLS_PER_SECOND; frame++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        }

        Assertions.assertEquals(0, context.countdownTimer);
        Assertions.assertEquals(0, context.getCountdownDisplay(), "The countdown shows the value it decremented to, so 0:00 shows for a second.");

        for (int frame = 0; frame < CALLS_PER_SECOND; frame++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        }

        Assertions.assertEquals(COUNTDOWN_HIDDEN, context.getCountdownDisplay(), "A stopped countdown is hidden.");

        context.countdownTimer  = 5;
        context.countdownPaused = 1;

        for (int frame = 0; frame < CALLS_PER_SECOND; frame++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        }

        Assertions.assertEquals(5, context.countdownTimer);
        Assertions.assertEquals(5, context.getCountdownDisplay(), "A paused countdown keeps showing its value.");
    }

    @RequiresInstallation
    @Test
    void matches_native_melee_trigger_evaluation_cases() throws Exception {
        byte[] resource;

        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            resource = storage.readFile(MELEE_TRIGGERS_PATH);
        }

        byte[] fixture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_TRIGGER_EVALUATION, EVALUATION_CAPTURE_HASH);

        try (InputStream capture = new ByteArrayInputStream(fixture)) {
            ByteBuffer counters = ByteBuffer.allocate(CAPTURE_HEADER_SIZE + CAPTURED_COUNTERS_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer countBytes = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);

            for (int caseIndex = 0; caseIndex < CAPTURE_CASE_COUNT; caseIndex++) {
                int[] playerTypes = new int[PlayerTable.PLAYER_SLOTS];
                byte[] outcomes = new byte[PlayerTable.PLAYABLE_PLAYER_SLOTS];

                for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
                    playerTypes[player] = player == 0 || player == 3
                                          ? PlayerTypes.HUMAN
                                          : (caseIndex & (1 << player)) != 0 ? PlayerTypes.COMPUTER : PlayerTypes.NONE;
                }

                int[] accountingWords = new int[UnitAccountingAssert.CONTEXT_BYTE_COUNT / Integer.BYTES];

                for (int offset = COUNTERS_OFFSET; offset < UnitAccountingAssert.CONTEXT_BYTE_COUNT; offset += Integer.BYTES) {
                    accountingWords[offset / Integer.BYTES] = 0x12345678 + caseIndex * 0x9E3779B9 + offset * 17;
                }

                UnitAccounting accounting = UnitAccountingAssert.fromCapture(accountingWords);
                PlayerRelations relations = new PlayerRelations(playerTypes, (caseIndex & 1) != 0, false, false);
                TriggerContext context = new TriggerContext();

                context.initializeClassicPlayerRecords(TriggerPlayerScenario.createPlayers(playerTypes, outcomes));
                context.loadAllPlayersResource(resource);
                context.initializeEvaluation(accounting, relations, new int[]{0, 0, (caseIndex >> 1) & 1, 0, 0});
                ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
                ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);

                Assertions.assertEquals(counters.capacity(), capture.readNBytes(counters.array(), 0, counters.capacity()));
                Assertions.assertEquals(counters.getInt(0), context.getCycleCountdown());
                Assertions.assertEquals(counters.getInt(Integer.BYTES), context.getPeriodicCountdown());
                Assertions.assertEquals(counters.getInt(2 * Integer.BYTES), context.getClockCountdown());
                Assertions.assertEquals(counters.getInt(3 * Integer.BYTES), context.getCurrentPlayerIndex());

                ByteBuffer expectedCounters = counters.slice(CAPTURE_HEADER_SIZE, CAPTURED_COUNTERS_SIZE).order(ByteOrder.LITTLE_ENDIAN);
                TriggerSnapshot accountingSnapshot = context.getAccountingSnapshot();

                Assertions.assertNotNull(accountingSnapshot);

                UnitAccountingAssert.assertKnownSnapshot(expectedCounters, accountingSnapshot);

                for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
                    Assertions.assertEquals(Integer.BYTES, capture.readNBytes(countBytes.array(), 0, Integer.BYTES));
                    Assertions.assertEquals(countBytes.getInt(0), context.getPlayerRecordCount(player));

                    for (int recordIndex = 0; recordIndex < context.getPlayerRecordCount(player); recordIndex++) {
                        Assertions.assertArrayEquals(capture.readNBytes(TriggerRecordCodec.RECORD_SIZE),
                                                     context.getPlayerRecord(player, recordIndex));
                    }
                }
            }

            Assertions.assertEquals(-1, capture.read());
        }
    }

    @RequiresInstallation
    @Test
    void matches_native_all_players_record_registration_cases() throws Exception {
        byte[] resource;

        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            resource = storage.readFile(MELEE_TRIGGERS_PATH);
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        Assertions.assertEquals("9f8a8493df1f4be341b8da7922016ad7917db3db43b269194ab6569c5b2c10c4",
                                HexFormat.of().formatHex(digest.digest(resource)));

        ByteBuffer countBytes = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        int[] types = {PlayerTypes.NONE, PlayerTypes.COMPUTER, PlayerTypes.HUMAN, PlayerTypes.RESCUE_PASSIVE, PlayerTypes.RESCUE_ACTIVE,
                       PlayerTypes.NEUTRAL, PlayerTypes.PLAYER_LEFT, PlayerTypes.COMPUTER_LEFT};

        for (int caseIndex = 0; caseIndex < CAPTURE_CASE_COUNT; caseIndex++) {
            TriggerContext context = new TriggerContext();
            int[] playerTypes = new int[PlayerTable.PLAYABLE_PLAYER_SLOTS];
            byte[] outcomes = new byte[PlayerTable.PLAYABLE_PLAYER_SLOTS];

            for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
                playerTypes[player] = types[(caseIndex + player) % types.length];
                outcomes[player]    = (byte) ((caseIndex / 8 + player) % 6);
            }

            context.initializeClassicPlayerRecords(TriggerPlayerScenario.createPlayers(playerTypes, outcomes));
            context.loadAllPlayersResource(resource);

            for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
                countBytes.clear();
                countBytes.putInt(context.getPlayerRecordCount(player));
                digest.update(countBytes.array());

                for (int recordIndex = 0; recordIndex < context.getPlayerRecordCount(player); recordIndex++) {
                    digest.update(context.getPlayerRecord(player, recordIndex));
                }
            }
        }

        Assertions.assertEquals("07c0ab522e74e0f502f22708d77857fb4eac8b0a4800b9e38cc2998219ad5e80", HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void matches_native_first_update_cases() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteBuffer output = ByteBuffer.allocate((3 + 2 * PlayerTable.PLAYABLE_PLAYER_SLOTS) * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        int[] durations = {0, 1, 42, Integer.MAX_VALUE, Integer.MIN_VALUE, -2, -1, 167};
        int[] values = {0, 1, 42, 43, Integer.MAX_VALUE, Integer.MIN_VALUE, -2, -1};

        for (int caseIndex = 0; caseIndex < FIRST_UPDATE_CASE_COUNT; caseIndex++) {
            TriggerContext context = new TriggerContext();

            for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
                if (((caseIndex + player) & 1) != 0) {
                    context.beginWait(player, values[(caseIndex + player) % values.length]);
                }
            }

            ScrTriggerSystem.update(context, durations[caseIndex % durations.length], REPLAY_GAME, (caseIndex >> 3) & 1, (caseIndex >> 4) & 1);
            output.clear();
            output.putInt(context.getCycleCountdown());
            output.putInt(context.getPeriodicCountdown());
            output.putInt(context.getClockCountdown());

            for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
                output.putInt(context.isWaiting(player) ? 1 : 0);
                output.putInt(context.getWaitTimer(player));
            }

            digest.update(output.array());
        }

        Assertions.assertEquals("21791e8ba63c282bc49714b2e22b0e1a38e55a2635a43b0f2cae0335bf666123", HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void update_should_keep_first_replay_call_and_stop_at_actual_evaluation() {
        TriggerContext context = new TriggerContext();

        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);

        Assertions.assertEquals(0, context.getCycleCountdown());
        Assertions.assertEquals(COUNTDOWN_RELOAD - 1, context.getPeriodicCountdown());
        Assertions.assertEquals(COUNTDOWN_RELOAD - 1, context.getClockCountdown());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0));
        Assertions.assertEquals(CYCLE_RELOAD, context.getCycleCountdown());
        Assertions.assertEquals(COUNTDOWN_RELOAD - 2, context.getPeriodicCountdown());
        Assertions.assertEquals(COUNTDOWN_RELOAD - 2, context.getClockCountdown());
    }

    @Test
    void update_should_visit_only_eligible_classic_records_and_keep_final_iterator_value() {
        TriggerContext context = new TriggerContext();
        int[] playerTypes = {PlayerTypes.HUMAN, PlayerTypes.RESCUE_PASSIVE, PlayerTypes.RESCUE_ACTIVE, PlayerTypes.HUMAN, PlayerTypes.NEUTRAL,
                             PlayerTypes.NONE, PlayerTypes.PLAYER_LEFT, PlayerTypes.COMPUTER_LEFT};
        byte[] outcomes = {1, 0, 0, 0, 0, 0, 0, 0};

        context.initializeClassicPlayerRecords(TriggerPlayerScenario.createPlayers(playerTypes, outcomes));
        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);

        Assertions.assertEquals(WALK_END_PLAYER, context.getCurrentPlayerIndex());
        Assertions.assertEquals(CYCLE_RELOAD, context.getCycleCountdown());

        TriggerContext nonemptyContext = new TriggerContext();

        outcomes[0] = 0;
        nonemptyContext.initializeClassicPlayerRecords(TriggerPlayerScenario.createPlayers(playerTypes, outcomes));

        byte[] resource = new byte[TriggerRecordCodec.RECORD_SIZE];

        resource[TriggerRecordCodec.OWNERS_OFFSET + TriggerPlayerSelectors.ALL_PLAYERS] = 1;
        nonemptyContext.loadAllPlayersResource(resource);
        ScrTriggerSystem.update(nonemptyContext, FRAME_DURATION, REPLAY_GAME, 0, 0);

        Assertions.assertThrows(UnsupportedOperationException.class,
                                () -> ScrTriggerSystem.update(nonemptyContext, FRAME_DURATION, REPLAY_GAME, 0, 0));
        Assertions.assertEquals(0, nonemptyContext.getCurrentPlayerIndex());
    }

    @Test
    void update_should_expire_unsigned_waits_without_touching_sentinel_or_gated_clocks() {
        TriggerContext context = new TriggerContext();

        context.beginWait(0, FRAME_DURATION);
        context.beginWait(1, FRAME_DURATION + 1);
        context.beginWait(2, -1);
        context.beginWait(3, Integer.MIN_VALUE);
        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, GAME_PAUSED, TRIGGERS_RUN_WHILE_PAUSED);

        Assertions.assertFalse(context.isWaiting(0));
        Assertions.assertEquals(0, context.getWaitTimer(0));
        Assertions.assertTrue(context.isWaiting(1));
        Assertions.assertEquals(1, context.getWaitTimer(1));
        Assertions.assertEquals(-1, context.getWaitTimer(2));
        Assertions.assertEquals(Integer.MIN_VALUE - FRAME_DURATION, context.getWaitTimer(3));
        Assertions.assertEquals(0, context.getCycleCountdown());
        Assertions.assertEquals(COUNTDOWN_RELOAD, context.getPeriodicCountdown());
        Assertions.assertEquals(COUNTDOWN_RELOAD, context.getClockCountdown());

        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, GAME_PAUSED, 0);

        Assertions.assertEquals(1, context.getWaitTimer(1));
    }

    @Test
    void update_should_retain_default_matched_action_failure_before_dispatch() {
        byte[] resource = new byte[TriggerRecordCodec.RECORD_SIZE];

        resource[TriggerRecordCodec.OWNERS_OFFSET + TriggerPlayerSelectors.ALL_PLAYERS] = 1;

        TriggerContext context = createBoundedContext(resource);

        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, GAME_PAUSED, TRIGGERS_RUN_WHILE_PAUSED);

        ByteBuffer record = ByteBuffer.wrap(context.getPlayerRecord(0, 0)).order(ByteOrder.LITTLE_ENDIAN);

        Assertions.assertEquals(DISABLED_RUNNING_FLAGS, record.getInt(TriggerRecordCodec.FLAGS_OFFSET));
        Assertions.assertEquals(FINISHED_ACTION_INDEX, record.get(TriggerRecordCodec.ACTION_INDEX_OFFSET));
    }

    @Test
    void update_should_dispatch_unsigned_memory_conditions_and_preserve_completed_triggers() {
        byte[] resource = new byte[TriggerRecordCodec.RECORD_SIZE];
        ByteBuffer input = TriggerRecordScenario.createRecord(resource, 0);

        int disabled = TriggerRecordCodec.ACTIONS_OFFSET;
        int preserve = disabled + TriggerRecordCodec.ACTION_SIZE;
        int setDeaths = preserve + TriggerRecordCodec.ACTION_SIZE;

        input.putInt(TriggerRecordCodec.CONDITION_PLAYER_FIELD, -1);
        input.putInt(TriggerRecordCodec.CONDITION_AMOUNT_FIELD, Integer.MIN_VALUE);
        input.put(TriggerRecordCodec.CONDITION_COMPARISON_FIELD, (byte) 0);
        input.put(TriggerRecordCodec.CONDITION_TYPE_FIELD, (byte) TriggerConditionIds.DEATHS);
        input.put(disabled + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) 255);
        input.put(disabled + TriggerRecordCodec.ACTION_FLAGS_FIELD, (byte) TriggerFlags.ACTION_DISABLED);
        input.put(preserve + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) TriggerActionIds.PRESERVE_TRIGGER);
        input.putInt(setDeaths + TriggerRecordCodec.ACTION_PLAYER_FIELD, -1);
        input.putInt(setDeaths + TriggerRecordCodec.ACTION_SECONDARY_FIELD, 1234);
        input.put(setDeaths + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) TriggerActionIds.SET_DEATHS);
        input.put(setDeaths + TriggerRecordCodec.ACTION_MODIFIER_FIELD, (byte) TriggerModifiers.SET_TO);
        input.put(TriggerRecordCodec.OWNERS_OFFSET + TriggerPlayerSelectors.ALL_PLAYERS, (byte) 1);

        TriggerContext context = createBoundedContext(resource);
        int[] counts = new int[2];

        context.setExtendedDeathsOperations(new ScrTriggerSystem.ExtendedDeathsOperations() {
            /**
             * Reads a DWORD selected by already encoded operands.
             *
             * @param playerOperand the encoded address index, not a player selector
             * @param unitOperand   the unsigned unit WORD
             * @return all bits of the selected DWORD
             * @implSpec Checks and counts the encoded read operands, then returns the fixed DWORD value.
             */
            @Override
            public int readExtendedDeaths(int playerOperand, int unitOperand) {
                Assertions.assertEquals(-1, playerOperand);
                Assertions.assertEquals(0, unitOperand);

                counts[0]++;

                return -1;
            }

            /**
             * Sets a DWORD selected by already encoded operands using a supported modifier.
             * Earlier action mutations remain visible if this operation fails.
             *
             * @param playerOperand the encoded address index, not a player selector
             * @param unitOperand   the unsigned unit WORD
             * @param modifier      the encoded action modifier
             * @param amount        the action DWORD
             * @implSpec Checks the encoded write operands and counts the dispatched action.
             */
            @Override
            public void setExtendedDeaths(int playerOperand, int unitOperand, int modifier, int amount) {
                Assertions.assertEquals(-1, playerOperand);
                Assertions.assertEquals(0, unitOperand);
                Assertions.assertEquals(SET_TO_MODIFIER, modifier);
                Assertions.assertEquals(1234, amount);

                counts[1]++;
            }
        });

        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, GAME_PAUSED, TRIGGERS_RUN_WHILE_PAUSED);

        ByteBuffer record = ByteBuffer.wrap(context.getPlayerRecord(0, 0)).order(ByteOrder.LITTLE_ENDIAN);

        Assertions.assertEquals(PRESERVE_FLAGS, record.getInt(TriggerRecordCodec.FLAGS_OFFSET));
        Assertions.assertEquals(0, record.get(TriggerRecordCodec.ACTION_INDEX_OFFSET));

        for (int frame = 0; frame < 31; frame++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, GAME_PAUSED, TRIGGERS_RUN_WHILE_PAUSED);
        }

        Assertions.assertArrayEquals(new int[]{2, 2}, counts);
    }

    @Test
    void update_should_keep_earlier_action_effects_when_provider_fails_and_retry_on_resume() {
        byte[] resource = new byte[TriggerRecordCodec.RECORD_SIZE];
        ByteBuffer input = TriggerRecordScenario.createRecord(resource, 0);

        input.put(TriggerRecordCodec.ACTIONS_OFFSET + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) TriggerActionIds.PRESERVE_TRIGGER);

        for (int action = 1; action < 3; action++) {
            int position = TriggerRecordCodec.ACTIONS_OFFSET + action * TriggerRecordCodec.ACTION_SIZE;

            input.putInt(position + TriggerRecordCodec.ACTION_PLAYER_FIELD, 27);
            input.putInt(position + TriggerRecordCodec.ACTION_SECONDARY_FIELD, action);
            input.put(position + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) TriggerActionIds.SET_DEATHS);
            input.put(position + TriggerRecordCodec.ACTION_MODIFIER_FIELD, (byte) TriggerModifiers.SET_TO);
        }

        input.put(TriggerRecordCodec.OWNERS_OFFSET + TriggerPlayerSelectors.ALL_PLAYERS, (byte) 1);

        TriggerContext context = createBoundedContext(resource);
        int[] stored = new int[1];
        UnsupportedOperationException expected = Exceptions.newUnsupportedOperationException("provider boundary");

        context.setExtendedDeathsOperations(new ScrTriggerSystem.ExtendedDeathsOperations() {
            /**
             * Reads a DWORD selected by already encoded operands.
             *
             * @param playerOperand the encoded address index, not a player selector
             * @param unitOperand   the unsigned unit WORD
             * @return all bits of the selected DWORD
             * @implSpec Rejects unexpected reads from this action-only fixture.
             */
            @Override
            public int readExtendedDeaths(int playerOperand, int unitOperand) {
                throw Exceptions.newAssertionError();
            }

            /**
             * Sets a DWORD selected by already encoded operands using a supported modifier.
             * Earlier action mutations remain visible if this operation fails.
             *
             * @param playerOperand the encoded address index, not a player selector
             * @param unitOperand   the unsigned unit WORD
             * @param modifier      the encoded action modifier
             * @param amount        the action DWORD
             * @implSpec Rejects the designated failing amount and records other amounts.
             */
            @Override
            public void setExtendedDeaths(int playerOperand, int unitOperand, int modifier, int amount) {
                if (amount == 2) {
                    throw expected;
                }

                stored[0] = amount;
            }
        });

        Assertions.assertSame(expected,
                              Assertions.assertThrows(UnsupportedOperationException.class,
                                                      () -> ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME,
                                                                                    GAME_PAUSED, TRIGGERS_RUN_WHILE_PAUSED)));
        Assertions.assertEquals(1, stored[0]);

        ByteBuffer record = ByteBuffer.wrap(context.getPlayerRecord(0, 0)).order(ByteOrder.LITTLE_ENDIAN);

        Assertions.assertEquals(PRESERVE_RUNNING_FLAGS, record.getInt(TriggerRecordCodec.FLAGS_OFFSET));
        Assertions.assertEquals(2, record.get(TriggerRecordCodec.ACTION_INDEX_OFFSET));

        for (int frame = 0; frame < 30; frame++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, GAME_PAUSED, TRIGGERS_RUN_WHILE_PAUSED);
        }

        Assertions.assertSame(expected,
                              Assertions.assertThrows(UnsupportedOperationException.class,
                                                      () -> ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME,
                                                                                    GAME_PAUSED, TRIGGERS_RUN_WHILE_PAUSED)),
                              "Resumption (flag 1) skips the conditions and retries the retained action.");
        Assertions.assertEquals(1, stored[0]);
    }

    @Test
    void initialize_evaluation_should_copy_controls_into_independent_snapshot() {
        int[] playerTypes = {PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                             PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();
        PlayerRelations relations = new PlayerRelations(playerTypes, false, false, false);
        int[] controls = {0x100, Integer.MIN_VALUE, 0x201, 0x12345678, 7};
        TriggerContext context = new TriggerContext();

        context.initializeEvaluation(accounting, relations, controls);

        Arrays.fill(controls, -1);

        Assertions.assertFalse(context.isEvaluationStopped(), "The copied stop byte and session state do not stop the evaluation.");
        Assertions.assertEquals(0x12345678, context.resultFlags);
        Assertions.assertEquals(7, context.getLocalPlayer());
        Assertions.assertEquals(1, context.getMultiplayerSession());

        context.initializeEvaluation(accounting, relations, controls);

        Assertions.assertTrue(context.isEvaluationStopped());
        Assertions.assertEquals(-1, context.resultFlags);
        Assertions.assertEquals(-1, context.getLocalPlayer());
        Assertions.assertEquals(0xFF, context.getMultiplayerSession());
        Assertions.assertThrows(IllegalArgumentException.class,
                                () -> context.initializeEvaluation(accounting, relations, new int[TriggerContext.CONTROL_COUNT - 1]));
        Assertions.assertTrue(context.isEvaluationStopped(), "A rejected control array keeps the previous controls.");
        Assertions.assertEquals(-1, context.resultFlags);
        Assertions.assertEquals(0xFF, context.getMultiplayerSession());
        Assertions.assertEquals(-1, context.getLocalPlayer());
    }

    @RequiresInstallation
    @Test
    void defeat_releases_player_classic_trigger_list() throws IOException {
        Path map = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, ExternalInput.TEST_MAPS_FOLDER, "eud_com.scx");

        Map<Long, Integer> words = NetworkReplayScenario.collectResourceWords(ExternalInput.INSTALLATION.getPath(), map,
                                                                              GameTestResources.REPLAY_EUD_COMPUTER_DEFEAT_DESYNC,
                                                                              DEFEAT_LOCAL_PLAYER,
                                                                              DEFEAT_LAST_FRAME);

        Assertions.assertEquals(0xE24E, words.get(4800L), "The native client's record 3 (frame 4800).");
        Assertions.assertEquals(0x3F2A, words.get(4802L), "The native client's record 5 (frame 4802).");
    }
}
