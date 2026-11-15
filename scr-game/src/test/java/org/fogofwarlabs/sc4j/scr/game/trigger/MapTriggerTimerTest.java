package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

/**
 * Checks countdown conditions, related player selectors and draw results.
 */
final class MapTriggerTimerTest {
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
     * The update calls per game second, including the zero countdown call.
     */
    private static final int CALLS_PER_SECOND = 16;
    private static final int COUNTDOWN_SECONDS = 3;
    /**
     * The further update calls after the countdown expiry that let the expiry trigger run.
     */
    private static final int SETTLE_CALLS = 40;
    /**
     * The draw result.
     */
    private static final int DRAW_RESULT = 5;

    /**
     * Writes one condition and its timer comparison.
     *
     * @param record     the destination record
     * @param index      the condition index
     * @param type       the condition type
     * @param amount     the comparison amount
     * @param comparison the comparison modifier
     */
    private static void writeCondition(@NotNull ByteBuffer record, int index, int type, int amount, int comparison) {
        int base = index * TriggerRecordCodec.CONDITION_SIZE;

        record.putInt(base + TriggerRecordCodec.CONDITION_AMOUNT_FIELD, amount);
        record.put(base + TriggerRecordCodec.CONDITION_COMPARISON_FIELD, (byte) comparison);
        record.put(base + TriggerRecordCodec.CONDITION_TYPE_FIELD, (byte) type);
    }

    /**
     * Writes one action and its timer or resource operands.
     *
     * @param record    the destination record
     * @param index     the action index
     * @param type      the action type
     * @param selector  the player selector
     * @param secondary the secondary amount
     * @param modifier  the arithmetic modifier
     * @param duration  the timer duration
     */
    private static void writeAction(@NotNull ByteBuffer record, int index, int type, int selector, int secondary, int modifier, int duration) {
        int base = TriggerRecordCodec.ACTIONS_OFFSET + index * TriggerRecordCodec.ACTION_SIZE;

        record.putInt(base + TriggerRecordCodec.ACTION_DURATION_FIELD, duration);
        record.putInt(base + TriggerRecordCodec.ACTION_PLAYER_FIELD, selector);
        record.putInt(base + TriggerRecordCodec.ACTION_SECONDARY_FIELD, secondary);
        record.put(base + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) type);
        record.put(base + TriggerRecordCodec.ACTION_MODIFIER_FIELD, (byte) modifier);
    }

    /**
     * Creates the player records and evaluation state for the encoded triggers.
     *
     * @param relations the player alliances
     * @param records   the encoded trigger records
     * @param players   the initialized player slots
     * @return the initialized trigger context
     */
    private static @NotNull TriggerContext createContext(@NotNull PlayerRelations relations,
                                                         byte @NotNull [] records,
                                                         @NotNull Player @NotNull [] players) {
        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(players);
        context.loadRecords(records);
        context.initializeEvaluation(PlayerProgressTestUtils.createUnitAccounting(), relations, new int[]{0, 0, 0, 0, REPLAY_OBSERVER});

        return context;
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


    @Test
    void countdown_timer_counts_down_and_satisfies_timer_condition() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE * 2];
        ByteBuffer start = TriggerRecordScenario.createRecord(records, 0);

        writeCondition(start, 0, TriggerConditionIds.ALWAYS, 0, 0);
        writeAction(start, 0, TriggerActionIds.SET_COUNTDOWN_TIMER, 0, 0, TriggerModifiers.SET_TO, COUNTDOWN_SECONDS);
        start.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        ByteBuffer expired = TriggerRecordScenario.createRecord(records, 1);

        writeCondition(expired, 0, TriggerConditionIds.COUNTDOWN_TIMER, 0, TriggerModifiers.EXACTLY);
        writeCondition(expired, 1, TriggerConditionIds.ELAPSED_TIME, 1, TriggerModifiers.AT_LEAST);
        writeAction(expired, 0, TriggerActionIds.SET_RESOURCES, TriggerPlayerSelectors.CURRENT_PLAYER, 7, TriggerModifiers.SET_TO, 0);
        expired.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        Player[] players = createPlayers(types);
        TriggerContext context = createContext(new PlayerRelations(types, false, false, false), records, players);

        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);

        Assertions.assertEquals(0, players[0].getMinerals());

        for (int call = 0; call < CALLS_PER_SECOND * COUNTDOWN_SECONDS; call++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        }

        for (int call = 0; call < SETTLE_CALLS; call++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        }

        Assertions.assertEquals(7, players[0].getMinerals(), "The countdown reached zero after three game seconds.");
    }

    @Test
    void foes_allies_and_neutral_selectors_choose_matching_players_except_current() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.HUMAN, PlayerTypes.HUMAN, PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        PlayerRelations relations = new PlayerRelations(types, false, false, false);

        relations.setAlliances(0, new int[]{1, 0, 2, 1, 0, 0, 0, 0, 0, 0, 0, 1});

        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE];
        ByteBuffer selectors = TriggerRecordScenario.createRecord(records, 0);

        writeCondition(selectors, 0, TriggerConditionIds.ALWAYS, 0, 0);
        writeAction(selectors, 0, TriggerActionIds.SET_RESOURCES, TriggerPlayerSelectors.FOES, 1, TriggerModifiers.ADD, 0);
        writeAction(selectors, 1, TriggerActionIds.SET_RESOURCES, TriggerPlayerSelectors.ALLIES, 10, TriggerModifiers.ADD, 0);
        writeAction(selectors, 2, TriggerActionIds.SET_RESOURCES, TriggerPlayerSelectors.NEUTRAL_PLAYERS, 100, TriggerModifiers.ADD, 0);
        selectors.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        Player[] players = createPlayers(types);
        TriggerContext context = createContext(relations, records, players);

        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);

        Assertions.assertEquals(0, players[0].getMinerals(), "The current player is never selected.");
        Assertions.assertEquals(1, players[1].getMinerals());
        Assertions.assertEquals(10, players[2].getMinerals());
        Assertions.assertEquals(100, players[3].getMinerals());
    }

    @Test
    void draw_stores_result_five_for_the_player() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE];
        ByteBuffer draw = TriggerRecordScenario.createRecord(records, 0);

        writeCondition(draw, 0, TriggerConditionIds.ALWAYS, 0, 0);
        writeAction(draw, 0, TriggerActionIds.DRAW, 0, 0, 0, 0);
        draw.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        Player[] players = createPlayers(types);
        TriggerContext context = createContext(new PlayerRelations(types, false, false, false), records, players);

        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);

        Assertions.assertEquals(DRAW_RESULT, context.getPlayerResult(0), "Draw stores result 5.");
        Assertions.assertEquals(DRAW_RESULT, players[0].getVictoryState());
    }
}
