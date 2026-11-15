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
 * Checks death counters, score modifications and leaderboard actions.
 */
final class MapTriggerScoreTest {
    private static final int FRAME_DURATION = GameSpeed.FASTEST_DURATION;
    /**
     * The replay mode argument while replaying.
     */
    private static final int REPLAY_GAME = 1;
    /**
     * The local player of a replay observer, outside the playable slots.
     */
    private static final int REPLAY_OBSERVER = 8;
    private static final int LEADERBOARD_UPDATE_CALLS = 40;

    /**
     * Writes one condition and its score operands.
     *
     * @param record     the destination record
     * @param index      the condition index
     * @param type       the condition type
     * @param selector   the player selector
     * @param unitType   the unit-type operand
     * @param resource   the resource or score category
     * @param amount     the comparison amount
     * @param comparison the comparison modifier
     */
    private static void writeCondition(@NotNull ByteBuffer record, int index,
                                       int type,
                                       int selector, int unitType, int resource,
                                       int amount, int comparison) {
        int base = index * TriggerRecordCodec.CONDITION_SIZE;

        record.putInt(base + TriggerRecordCodec.CONDITION_PLAYER_FIELD, selector);
        record.putInt(base + TriggerRecordCodec.CONDITION_AMOUNT_FIELD, amount);
        record.putShort(base + TriggerRecordCodec.CONDITION_UNIT_FIELD, (short) unitType);
        record.put(base + TriggerRecordCodec.CONDITION_COMPARISON_FIELD, (byte) comparison);
        record.put(base + TriggerRecordCodec.CONDITION_TYPE_FIELD, (byte) type);
        record.put(base + TriggerRecordCodec.CONDITION_RESOURCE_FIELD, (byte) resource);
    }

    /**
     * Writes one action and its score operands.
     *
     * @param record    the destination record
     * @param index     the action index
     * @param type      the action type
     * @param selector  the player selector
     * @param secondary the secondary amount or score operand
     * @param unitType  the unit-type operand
     * @param modifier  the arithmetic modifier
     */
    private static void writeAction(@NotNull ByteBuffer record, int index, int type, int selector, int secondary, int unitType, int modifier) {
        int base = TriggerRecordCodec.ACTIONS_OFFSET + index * TriggerRecordCodec.ACTION_SIZE;

        record.putInt(base + TriggerRecordCodec.ACTION_PLAYER_FIELD, selector);
        record.putInt(base + TriggerRecordCodec.ACTION_SECONDARY_FIELD, secondary);
        record.putShort(base + TriggerRecordCodec.ACTION_UNIT_FIELD, (short) unitType);
        record.put(base + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) type);
        record.put(base + TriggerRecordCodec.ACTION_MODIFIER_FIELD, (byte) modifier);
    }

    /**
     * Creates initialized player records in slot order.
     *
     * @param types the player types in slot order
     * @return the initialized player slots
     */
    private static @NotNull Player @NotNull [] createPlayers(int @NotNull [] types) {
        Player[] players = new Player[PlayerTable.PLAYABLE_PLAYER_SLOTS];

        for (int index = 0; index < PlayerTable.PLAYABLE_PLAYER_SLOTS; index++) {
            players[index] = new Player(index, index, types[index], 0, 0);
        }

        return players;
    }

    /**
     * Advances trigger evaluation through its first execution pass.
     *
     * @param context the trigger evaluation context
     */
    private static void runFirstPass(@NotNull TriggerContext context) {
        for (int call = 0; call < 2; call++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        }
    }


    @Test
    void set_deaths_feeds_deaths_condition_and_subtraction_clamps_at_zero() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE * 2];
        ByteBuffer set = TriggerRecordScenario.createRecord(records, 0);

        writeCondition(set, 0, TriggerConditionIds.ALWAYS, 0, 0, 0, 0, 0);
        writeAction(set, 0, TriggerActionIds.SET_DEATHS, 1, 3, ScrUnitIds.ZERG_ZERGLING, TriggerModifiers.SET_TO);
        writeAction(set, 1, TriggerActionIds.SET_DEATHS, 1, 2, ScrUnitIds.ZERG_ZERGLING, TriggerModifiers.ADD);
        writeAction(set, 2, TriggerActionIds.SET_DEATHS, 1, 9, ScrUnitIds.ZERG_HYDRALISK, TriggerModifiers.SUBTRACT);
        set.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        ByteBuffer check = TriggerRecordScenario.createRecord(records, 1);

        writeCondition(check, 0, TriggerConditionIds.DEATHS, 1, ScrUnitIds.ZERG_ZERGLING, 0, 5, TriggerModifiers.EXACTLY);
        writeCondition(check, 1, TriggerConditionIds.DEATHS, 1, ScrUnitIds.ZERG_HYDRALISK, 0, 0, TriggerModifiers.EXACTLY);
        writeAction(check, 0,
                    TriggerActionIds.SET_RESOURCES,
                    TriggerPlayerSelectors.CURRENT_PLAYER,
                    7, TriggerScoreActionsUtils.RESOURCE_MINERALS,
                    TriggerModifiers.SET_TO);
        check.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        Player[] players = createPlayers(types);
        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();
        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(players);
        context.loadRecords(records);
        context.initializeEvaluation(accounting, new PlayerRelations(types, false, false, false), new int[]{0, 0, 0, 0, REPLAY_OBSERVER});

        runFirstPass(context);

        Assertions.assertEquals(5, accounting.getScores().getDeaths(1, ScrUnitIds.ZERG_ZERGLING));
        Assertions.assertEquals(0, accounting.getScores().getDeaths(1, ScrUnitIds.ZERG_HYDRALISK));
        Assertions.assertEquals(7, players[0].getMinerals(), "Deaths matched exactly five Zerglings and zero Hydralisks.");
    }

    @Test
    void set_score_splits_totals_and_feeds_score_condition() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE * 2];
        ByteBuffer set = TriggerRecordScenario.createRecord(records, 0);

        writeCondition(set, 0, TriggerConditionIds.ALWAYS, 0, 0, 0, 0, 0);
        writeAction(set, 0,
                    TriggerActionIds.SET_SCORE,
                    TriggerPlayerSelectors.CURRENT_PLAYER,
                    10, TriggerScoreActionsUtils.SCORE_TOTAL,
                    TriggerModifiers.SET_TO);
        writeAction(set, 1,
                    TriggerActionIds.SET_SCORE,
                    TriggerPlayerSelectors.CURRENT_PLAYER,
                    4, TriggerScoreActionsUtils.SCORE_CUSTOM,
                    TriggerModifiers.ADD);
        writeAction(set, 2,
                    TriggerActionIds.SET_SCORE,
                    TriggerPlayerSelectors.CURRENT_PLAYER,
                    3, TriggerScoreActionsUtils.SCORE_KILLS_AND_RAZINGS,
                    TriggerModifiers.SUBTRACT);
        set.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        ByteBuffer check = TriggerRecordScenario.createRecord(records, 1);

        writeCondition(check, 0,
                       TriggerConditionIds.SCORE,
                       TriggerPlayerSelectors.CURRENT_PLAYER, 0, TriggerScoreActionsUtils.SCORE_TOTAL,
                       12, TriggerModifiers.EXACTLY);
        writeCondition(check, 1,
                       TriggerConditionIds.SCORE,
                       TriggerPlayerSelectors.CURRENT_PLAYER, 0, TriggerScoreActionsUtils.SCORE_KILLS,
                       1, TriggerModifiers.EXACTLY);
        writeCondition(check, 2,
                       TriggerConditionIds.KILL,
                       TriggerPlayerSelectors.CURRENT_PLAYER, TriggerUnitGroups.ANY_UNIT, 0,
                       0, TriggerModifiers.EXACTLY);
        writeAction(check, 0,
                    TriggerActionIds.SET_RESOURCES,
                    TriggerPlayerSelectors.CURRENT_PLAYER,
                    7, TriggerScoreActionsUtils.RESOURCE_MINERALS,
                    TriggerModifiers.SET_TO);
        check.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        Player[] players = createPlayers(types);
        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();
        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(players);
        context.loadRecords(records);
        context.initializeEvaluation(accounting, new PlayerRelations(types, false, false, false), new int[]{0, 0, 0, 0, REPLAY_OBSERVER});

        runFirstPass(context);

        // Set total 10: each of the four scores becomes (10 + 3) / 4 = 3; custom +4; kills and razings -2 each -> 1.
        Assertions.assertEquals(3, accounting.getUnitProductionScore(0));
        Assertions.assertEquals(3, accounting.getBuildingProductionScore(0));
        Assertions.assertEquals(1, accounting.getScores().getUnitKillScore(0));
        Assertions.assertEquals(1, accounting.getScores().getRazingScore(0));
        Assertions.assertEquals(4, accounting.getScores().getCustomScore(0));
        Assertions.assertEquals(7, players[0].getMinerals(), "The total score 3 + 3 + 1 + 1 + 4 = 12, kill score 1 and no kills matched.");
    }

    @Test
    void leaderboard_points_action_succeeds_and_next_action_runs() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE];
        ByteBuffer board = TriggerRecordScenario.createRecord(records, 0);

        writeCondition(board, 0, TriggerConditionIds.ALWAYS, 0, 0, 0, 0, 0);
        writeAction(board, 0, TriggerActionIds.LEADERBOARD_POINTS, 0, 0, TriggerScoreActionsUtils.SCORE_TOTAL, 0);
        writeAction(board, 1,
                    TriggerActionIds.SET_RESOURCES,
                    TriggerPlayerSelectors.CURRENT_PLAYER,
                    7, TriggerScoreActionsUtils.RESOURCE_MINERALS,
                    TriggerModifiers.SET_TO);
        board.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        Player[] players = createPlayers(types);
        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(players);
        context.loadRecords(records);
        context.initializeEvaluation(PlayerProgressTestUtils.createUnitAccounting(),
                                     new PlayerRelations(types, false, false, false),
                                     new int[]{0, 0, 0, 0, REPLAY_OBSERVER});

        for (int call = 0; call < LEADERBOARD_UPDATE_CALLS; call++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        }

        Assertions.assertEquals(7, players[0].getMinerals());
    }
}
