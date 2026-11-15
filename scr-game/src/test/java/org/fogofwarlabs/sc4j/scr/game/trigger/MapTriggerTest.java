package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.type.constant.TriggerUnitGroups;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.*;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

/**
 * Checks owner registration, conditions, action sequencing and retained waits.
 */
final class MapTriggerTest {
    private static final int FRAME_DURATION = GameSpeed.FASTEST_DURATION;
    /**
     * The replay mode argument while replaying.
     */
    private static final int REPLAY_GAME = 1;
    /**
     * The local player of a replay observer, outside the playable slots.
     */
    private static final int REPLAY_OBSERVER = 8;

    /**
     * The game-context offset of the building counts.
     */
    private static final int BUILDING_COUNTS_OFFSET = 0x2E44;
    /**
     * The game-context offset of the all-units type counts.
     */
    private static final int TYPE_COUNTS_OFFSET = 0x3234;
    /**
     * The game-context offset of the completed type counts.
     */
    private static final int COMPLETED_TYPE_COUNTS_OFFSET = 0x5CF4;

    private static final int FIRST_ACTION_FLAGS = TriggerRecordCodec.ACTIONS_OFFSET + TriggerRecordCodec.ACTION_FLAGS_FIELD;

    private static void writeCondition(@NotNull ByteBuffer record, int index,
                                       int type,
                                       int selector,
                                       int amount, int unitType, int comparison,
                                       int resourceType) {
        int base = index * TriggerRecordCodec.CONDITION_SIZE;

        record.putInt(base + TriggerRecordCodec.CONDITION_PLAYER_FIELD, selector);
        record.putInt(base + TriggerRecordCodec.CONDITION_AMOUNT_FIELD, amount);
        record.putShort(base + TriggerRecordCodec.CONDITION_UNIT_FIELD, (short) unitType);
        record.put(base + TriggerRecordCodec.CONDITION_COMPARISON_FIELD, (byte) comparison);
        record.put(base + TriggerRecordCodec.CONDITION_TYPE_FIELD, (byte) type);
        record.put(base + TriggerRecordCodec.CONDITION_RESOURCE_FIELD, (byte) resourceType);
    }

    private static void writeAction(@NotNull ByteBuffer record, int index,
                                    int type,
                                    int selector,
                                    int secondary, int unitType,
                                    int modifier,
                                    int duration) {
        int base = TriggerRecordCodec.ACTIONS_OFFSET + index * TriggerRecordCodec.ACTION_SIZE;

        record.putInt(base + TriggerRecordCodec.ACTION_DURATION_FIELD, duration);
        record.putInt(base + TriggerRecordCodec.ACTION_PLAYER_FIELD, selector);
        record.putInt(base + TriggerRecordCodec.ACTION_SECONDARY_FIELD, secondary);
        record.putShort(base + TriggerRecordCodec.ACTION_UNIT_FIELD, (short) unitType);
        record.put(base + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) type);
        record.put(base + TriggerRecordCodec.ACTION_MODIFIER_FIELD, (byte) modifier);
    }

    private static @NotNull Player @NotNull [] createPlayers(int @NotNull [] types, int @NotNull [] forces) {
        Player[] players = new Player[PlayerTable.PLAYABLE_PLAYER_SLOTS];

        for (int index = 0; index < PlayerTable.PLAYABLE_PLAYER_SLOTS; index++) {
            players[index] = new Player(index, index, types[index], 0, forces[index]);
        }

        return players;
    }

    private static @NotNull TriggerContext createContext(@NotNull Player @NotNull [] players,
                                                         @NotNull UnitAccounting accounting,
                                                         int @NotNull [] playerTypes,
                                                         byte @NotNull [] records) {
        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(players);
        context.loadRecords(records);
        context.initializeEvaluation(accounting, new PlayerRelations(playerTypes, false, false, false), new int[]{0, 0, 0, 0, REPLAY_OBSERVER});

        return context;
    }

    private static void runFirstPass(@NotNull TriggerContext context) {
        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
    }


    @Test
    void load_records_should_register_one_copy_per_matching_owner_selector() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.HUMAN, PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.COMPUTER, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        Player[] players = createPlayers(types, new int[]{1, 1, 2, 1, 1, 0, 0, 0});
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE * 2];
        ByteBuffer first = TriggerRecordScenario.createRecord(records, 0);

        first.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);
        first.put(TriggerRecordCodec.OWNERS_OFFSET + TriggerPlayerSelectors.ALL_PLAYERS, (byte) 1);
        first.put(TriggerRecordCodec.OWNERS_OFFSET + TriggerPlayerSelectors.NONE, (byte) 1);

        ByteBuffer second = TriggerRecordScenario.createRecord(records, 1);

        second.put(TriggerRecordCodec.OWNERS_OFFSET + TriggerPlayerSelectors.FORCE_1, (byte) 1);
        second.put(TriggerRecordCodec.OWNERS_OFFSET + 3, (byte) 1);

        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(players);
        context.loadRecords(records);

        Assertions.assertEquals(3, context.getPlayerRecordCount(0), "Player 0 is registered by selectors 0 and 17, then by force 1.");
        Assertions.assertEquals(2, context.getPlayerRecordCount(1));
        Assertions.assertEquals(1, context.getPlayerRecordCount(2), "Force 2 players only receive the all-players copy.");
        Assertions.assertEquals(0, context.getPlayerRecordCount(3), "Inactive players are not registered, even by their own selector.");
        Assertions.assertEquals(2, context.getPlayerRecordCount(4), "Computer players are active.");
    }

    @Test
    void command_condition_evaluates_non_allied_victory_players() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        int[] words = new int[UnitAccountingAssert.CONTEXT_BYTE_COUNT / Integer.BYTES];

        words[BUILDING_COUNTS_OFFSET / Integer.BYTES]     = 3;
        words[BUILDING_COUNTS_OFFSET / Integer.BYTES + 1] = 0;

        UnitAccounting accounting = UnitAccountingAssert.fromCapture(words);
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE];
        ByteBuffer opponentsWithoutBuildings = TriggerRecordScenario.createRecord(records, 0);

        writeCondition(opponentsWithoutBuildings, 0,
                       TriggerConditionIds.COMMAND,
                       TriggerPlayerSelectors.NON_ALLIED_VICTORY_PLAYERS,
                       0, TriggerUnitGroups.BUILDINGS, TriggerModifiers.AT_MOST,
                       0);
        writeAction(opponentsWithoutBuildings, 0,
                    TriggerActionIds.SET_RESOURCES,
                    TriggerPlayerSelectors.CURRENT_PLAYER,
                    5, TriggerScoreActionsUtils.RESOURCE_MINERALS,
                    TriggerModifiers.ADD,
                    0);
        opponentsWithoutBuildings.put(TriggerRecordCodec.OWNERS_OFFSET + TriggerPlayerSelectors.ALL_PLAYERS, (byte) 1);

        Player[] players = createPlayers(types, new int[PlayerTable.PLAYABLE_PLAYER_SLOTS]);
        TriggerContext context = createContext(players, accounting, types, records);

        runFirstPass(context);

        Assertions.assertEquals(5, players[0].getMinerals(), "Player 1 has no buildings.");
        Assertions.assertEquals(0, players[1].getMinerals(), "Player 0 still owns three buildings.");
    }

    @Test
    void command_condition_counts_completed_units_unless_comparison_is_at_most() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        int[] words = new int[UnitAccountingAssert.CONTEXT_BYTE_COUNT / Integer.BYTES];
        int scvCounts = ScrUnitIds.TERRAN_SCV * PlayerTable.PLAYER_SLOTS;

        words[TYPE_COUNTS_OFFSET / Integer.BYTES + scvCounts]           = 2;
        words[COMPLETED_TYPE_COUNTS_OFFSET / Integer.BYTES + scvCounts] = 1;

        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE * 2];
        ByteBuffer exactlyTwoCompleted = TriggerRecordScenario.createRecord(records, 0);

        writeCondition(exactlyTwoCompleted, 0, TriggerConditionIds.COMMAND, 0, 2, ScrUnitIds.TERRAN_SCV, TriggerModifiers.EXACTLY, 0);
        writeAction(exactlyTwoCompleted, 0,
                    TriggerActionIds.SET_RESOURCES,
                    TriggerPlayerSelectors.CURRENT_PLAYER,
                    100, TriggerScoreActionsUtils.RESOURCE_MINERALS,
                    TriggerModifiers.SET_TO,
                    0);
        exactlyTwoCompleted.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        ByteBuffer atMostTwo = TriggerRecordScenario.createRecord(records, 1);

        writeCondition(atMostTwo, 0, TriggerConditionIds.COMMAND, 0, 2, ScrUnitIds.TERRAN_SCV, TriggerModifiers.AT_MOST, 0);
        writeCondition(atMostTwo, 1, TriggerConditionIds.ALWAYS, 0, 0, 0, 0, 0);
        writeAction(atMostTwo, 0,
                    TriggerActionIds.SET_RESOURCES,
                    TriggerPlayerSelectors.CURRENT_PLAYER,
                    5, TriggerScoreActionsUtils.RESOURCE_GAS,
                    TriggerModifiers.ADD,
                    0);
        atMostTwo.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        Player[] players = createPlayers(types, new int[PlayerTable.PLAYABLE_PLAYER_SLOTS]);

        players[0].setGas(7);

        TriggerContext context = createContext(players, UnitAccountingAssert.fromCapture(words), types, records);

        runFirstPass(context);

        Assertions.assertEquals(0, players[0].getMinerals(), "Only one SCV is completed.");
        Assertions.assertEquals(12, players[0].getGas(), "At Most counts incomplete units.");
        Assertions.assertEquals(5, players[0].getGatheredGas());
    }

    @Test
    void elapsed_time_accumulate_switch_and_never_triggers_apply() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE * 3];
        ByteBuffer setSwitch = TriggerRecordScenario.createRecord(records, 0);

        writeCondition(setSwitch, 0, TriggerConditionIds.ELAPSED_TIME, 0, 0, 0, TriggerModifiers.AT_LEAST, 0);
        writeCondition(setSwitch, 1,
                       TriggerConditionIds.ACCUMULATE,
                       TriggerPlayerSelectors.CURRENT_PLAYER,
                       50, 0, TriggerModifiers.AT_LEAST,
                       TriggerScoreActionsUtils.RESOURCE_BOTH);
        writeAction(setSwitch, 0, TriggerActionIds.SET_SWITCH, 0, 200, 0, TriggerModifiers.SET, 0);
        setSwitch.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        ByteBuffer onSwitch = TriggerRecordScenario.createRecord(records, 1);

        writeCondition(onSwitch, 0, TriggerConditionIds.SWITCH, 0, 0, 0, TriggerModifiers.SWITCH_IS_SET, 200);
        writeAction(onSwitch, 0,
                    TriggerActionIds.SET_RESOURCES,
                    TriggerPlayerSelectors.CURRENT_PLAYER,
                    1000, TriggerScoreActionsUtils.RESOURCE_MINERALS,
                    TriggerModifiers.SUBTRACT,
                    0);
        onSwitch.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        ByteBuffer never = TriggerRecordScenario.createRecord(records, 2);

        writeCondition(never, 0, TriggerConditionIds.NEVER, 0, 0, 0, 0, 0);
        writeAction(never, 0, TriggerActionIds.DEFEAT, 0, 0, 0, 0, 0);
        never.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        Player[] players = createPlayers(types, new int[PlayerTable.PLAYABLE_PLAYER_SLOTS]);

        players[0].setMinerals(30);
        players[0].setGas(20);

        TriggerContext context = createContext(players, PlayerProgressTestUtils.createUnitAccounting(), types, records);

        runFirstPass(context);

        Assertions.assertTrue(context.isSwitchSet(200));
        Assertions.assertEquals(0, players[0].getMinerals(), "Subtraction clamps at zero.");
        Assertions.assertEquals(20, players[0].getGas());
        Assertions.assertEquals(0, players[0].getVictoryState());
    }

    @Test
    void wait_action_holds_and_resumes_retained_action() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE];
        ByteBuffer waiting = TriggerRecordScenario.createRecord(records, 0);

        writeCondition(waiting, 0, TriggerConditionIds.ALWAYS, 0, 0, 0, 0, 0);
        writeAction(waiting, 0, TriggerActionIds.WAIT, 0, 0, 0, 0, 84);
        writeAction(waiting, 1,
                    TriggerActionIds.SET_RESOURCES,
                    TriggerPlayerSelectors.CURRENT_PLAYER,
                    9, TriggerScoreActionsUtils.RESOURCE_MINERALS,
                    TriggerModifiers.ADD,
                    0);
        writeAction(waiting, 2, TriggerActionIds.PRESERVE_TRIGGER, 0, 0, 0, 0, 0);
        waiting.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        Player[] players = createPlayers(types, new int[PlayerTable.PLAYABLE_PLAYER_SLOTS]);
        TriggerContext context = createContext(players, PlayerProgressTestUtils.createUnitAccounting(), types, records);

        runFirstPass(context);

        Assertions.assertTrue(context.isWaiting(0));
        Assertions.assertEquals(84, context.getWaitTimer(0));
        Assertions.assertEquals(1, context.getPlayerRecord(0, 0)[FIRST_ACTION_FLAGS] & TriggerFlags.ACTION_STARTED,
                                "The wait marks its action as started.");
        Assertions.assertEquals(0, players[0].getMinerals());

        for (int call = 0; call < 2; call++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        }

        Assertions.assertFalse(context.isWaiting(0));

        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);

        Assertions.assertEquals(9, players[0].getMinerals(), "The resumed wait succeeds and the following actions run.");
        Assertions.assertEquals(0, context.getPlayerRecord(0, 0)[FIRST_ACTION_FLAGS] & TriggerFlags.ACTION_STARTED);
    }

    @Test
    void run_ai_script_runs_only_when_owner_may_start_it() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE * 2];
        ByteBuffer meleeScript = TriggerRecordScenario.createRecord(records, 0);

        writeCondition(meleeScript, 0, TriggerConditionIds.ALWAYS, 0, 0, 0, 0, 0);
        writeAction(meleeScript, 0, TriggerActionIds.RUN_AI_SCRIPT, 0, 'T' | 'M' << 8 | 'C' << 16 | 'x' << 24, 0, 0, 0);
        writeAction(meleeScript, 1,
                    TriggerActionIds.SET_RESOURCES,
                    TriggerPlayerSelectors.CURRENT_PLAYER,
                    4, TriggerScoreActionsUtils.RESOURCE_MINERALS,
                    TriggerModifiers.SET_TO,
                    0);
        meleeScript.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        ByteBuffer visionScript = TriggerRecordScenario.createRecord(records, 1);

        writeCondition(visionScript, 0, TriggerConditionIds.ALWAYS, 0, 0, 0, 0, 0);
        writeAction(visionScript, 0, TriggerActionIds.RUN_AI_SCRIPT, 0, '+' | 'V' << 8 | 'i' << 16 | '1' << 24, 0, 0, 0);
        visionScript.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        Player[] players = createPlayers(types, new int[PlayerTable.PLAYABLE_PLAYER_SLOTS]);
        TriggerContext context = createContext(players, PlayerProgressTestUtils.createUnitAccounting(), types, records);
        UnsupportedOperationException failure = Assertions.assertThrows(UnsupportedOperationException.class, () -> runFirstPass(context));

        Assertions.assertTrue(failure.getMessage().endsWith("+Vi1"), failure.getMessage());
        Assertions.assertEquals(4, players[0].getMinerals(), "A human player's melee script is skipped.");
    }
}
