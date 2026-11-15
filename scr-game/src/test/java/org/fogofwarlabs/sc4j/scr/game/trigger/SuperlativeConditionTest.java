package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.*;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

/**
 * Checks superlative conditions, ties and unsigned resource comparisons.
 */
final class SuperlativeConditionTest {
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
     * Creates a superlative condition followed by a resource reward action.
     *
     * @param conditionType the superlative condition
     * @param unitType      the unit-type operand
     * @param resourceType  the resource category
     * @return the encoded trigger record
     */
    private static byte @NotNull [] createRecord(int conditionType, int unitType, int resourceType) {
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE];
        ByteBuffer record = TriggerRecordScenario.createRecord(records, 0);

        record.putInt(TriggerRecordCodec.CONDITION_LOCATION_FIELD, 0);
        record.putShort(TriggerRecordCodec.CONDITION_UNIT_FIELD, (short) unitType);
        record.put(TriggerRecordCodec.CONDITION_TYPE_FIELD, (byte) conditionType);
        record.put(TriggerRecordCodec.CONDITION_RESOURCE_FIELD, (byte) resourceType);
        record.putInt(TriggerRecordCodec.ACTIONS_OFFSET + TriggerRecordCodec.ACTION_PLAYER_FIELD, TriggerPlayerSelectors.CURRENT_PLAYER);
        record.putInt(TriggerRecordCodec.ACTIONS_OFFSET + TriggerRecordCodec.ACTION_SECONDARY_FIELD, 50);
        record.put(TriggerRecordCodec.ACTIONS_OFFSET + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) TriggerActionIds.SET_RESOURCES);
        record.put(TriggerRecordCodec.ACTIONS_OFFSET + TriggerRecordCodec.ACTION_MODIFIER_FIELD, (byte) TriggerModifiers.ADD);
        record.put(TriggerRecordCodec.OWNERS_OFFSET + TriggerPlayerSelectors.ALL_PLAYERS, (byte) 1);

        return records;
    }

    /**
     * Advances trigger evaluation through its first execution pass.
     *
     * @param context the trigger evaluation context
     */
    private static void runFirstPass(@NotNull TriggerContext context) {
        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
    }

    /**
     * Creates initialized player records in slot order.
     *
     * @return the initialized player slots
     */
    private static @NotNull Player @NotNull [] createPlayers() {
        Player[] players = new Player[PlayerTable.PLAYABLE_PLAYER_SLOTS];

        for (int index = 0; index < PlayerTable.PLAYABLE_PLAYER_SLOTS; index++) {
            players[index] = new Player(index, index, index < 2 ? PlayerTypes.HUMAN : PlayerTypes.NONE, 0, 0);
        }

        return players;
    }

    /**
     * Creates the player records and evaluation state for the encoded triggers.
     *
     * @param players    the initialized player slots
     * @param accounting the score and death counters
     * @param records    the encoded trigger records
     * @return the initialized trigger context
     */
    private static @NotNull TriggerContext createContext(@NotNull Player @NotNull [] players,
                                                         @NotNull UnitAccounting accounting,
                                                         byte @NotNull [] records) {
        int[] types = new int[PlayerTable.PLAYER_SLOTS];

        for (int index = 0; index < PlayerTable.PLAYABLE_PLAYER_SLOTS; index++) {
            types[index] = players[index].getType();
        }

        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(players);
        context.loadRecords(records);
        context.initializeEvaluation(accounting, new PlayerRelations(types, false, false, false), new int[]{0, 0, 0, 0, REPLAY_OBSERVER});

        return context;
    }


    @Test
    void most_kills_passes_for_leader_and_on_ties() {
        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();
        byte[] records = createRecord(TriggerConditionIds.MOST_KILLS, ScrUnitIds.ZERG_ZERGLING, 0);
        Player[] players = createPlayers();
        TriggerContext context = createContext(players, accounting, records);

        runFirstPass(context);

        Assertions.assertEquals(50, players[0].getMinerals(), "All zero kills tie, so every active player passes.");
        Assertions.assertEquals(50, players[1].getMinerals());
    }

    @Test
    void least_resources_passes_only_for_minimum() {
        byte[] records = createRecord(TriggerConditionIds.LEAST_RESOURCES, 0, TriggerScoreActionsUtils.RESOURCE_MINERALS);
        Player[] players = createPlayers();

        players[0].setMinerals(10);
        players[1].setMinerals(5);

        TriggerContext context = createContext(players, PlayerProgressTestUtils.createUnitAccounting(), records);

        runFirstPass(context);

        Assertions.assertEquals(10, players[0].getMinerals(), "Player 0 has more minerals than player 1.");
        Assertions.assertEquals(55, players[1].getMinerals());
    }

    @Test
    void most_resources_compares_unsigned() {
        byte[] records = createRecord(TriggerConditionIds.MOST_RESOURCES, 0, TriggerScoreActionsUtils.RESOURCE_MINERALS);
        Player[] players = createPlayers();

        players[0].setMinerals(-1);
        players[1].setMinerals(5);

        TriggerContext context = createContext(players, PlayerProgressTestUtils.createUnitAccounting(), records);

        runFirstPass(context);

        Assertions.assertEquals(49, players[0].getMinerals(), "0xFFFFFFFF is the unsigned maximum; the Set Resources add wraps.");
        Assertions.assertEquals(5, players[1].getMinerals());
    }
}
