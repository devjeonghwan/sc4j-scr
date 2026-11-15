package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

/**
 * Checks the center-view action wait of replay observers.
 */
final class TriggerActionsUtilsTest {
    private static final int FRAME_DURATION = GameSpeed.FASTEST_DURATION;
    /**
     * The replay mode argument while replaying.
     */
    private static final int REPLAY_GAME = 1;
    /**
     * The local player of a replay observer, outside the playable slots.
     */
    private static final int REPLAY_OBSERVER = 8;
    private static final int UPDATE_CALLS = 200;

    @Test
    void center_view_waits_without_expiry_for_replay_observer() {
        int[] types = {PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE];
        ByteBuffer record = TriggerRecordScenario.createRecord(records, 0);

        int centerView = TriggerRecordCodec.ACTIONS_OFFSET;
        int setResources = centerView + TriggerRecordCodec.ACTION_SIZE;

        record.put(TriggerRecordCodec.CONDITION_TYPE_FIELD, (byte) TriggerConditionIds.ALWAYS);
        record.putInt(centerView + TriggerRecordCodec.ACTION_LOCATION_FIELD, 1);
        record.put(centerView + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) TriggerActionIds.CENTER_VIEW);
        record.put(setResources + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) TriggerActionIds.SET_RESOURCES);
        record.put(setResources + TriggerRecordCodec.ACTION_MODIFIER_FIELD, (byte) TriggerModifiers.SET_TO);
        record.putInt(setResources + TriggerRecordCodec.ACTION_PLAYER_FIELD, TriggerPlayerSelectors.CURRENT_PLAYER);
        record.putInt(setResources + TriggerRecordCodec.ACTION_SECONDARY_FIELD, 50);
        record.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        Player[] players = new Player[PlayerTable.PLAYABLE_PLAYER_SLOTS];

        for (int index = 0; index < PlayerTable.PLAYABLE_PLAYER_SLOTS; index++) {
            players[index] = new Player(index, index, types[index], 0, 0);
        }

        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(players);
        context.loadRecords(records);
        context.initializeEvaluation(PlayerProgressTestUtils.createUnitAccounting(),
                                     new PlayerRelations(types, false, false, false),
                                     new int[]{0, 0, 0, 0, REPLAY_OBSERVER});

        for (int call = 0; call < UPDATE_CALLS; call++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        }

        Assertions.assertTrue(context.isWaiting(0));
        Assertions.assertEquals(-1, context.getWaitTimer(0), "The wait never expires for a player other than the local player.");
        Assertions.assertEquals(0, players[0].getMinerals(), "The following action never runs.");
    }
}
