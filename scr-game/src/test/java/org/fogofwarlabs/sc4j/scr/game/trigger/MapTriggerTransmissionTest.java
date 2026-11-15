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
 * Checks transmission waits with and without the evaluation override.
 */
final class MapTriggerTransmissionTest {
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
     * Creates a {@link TriggerContext} instance with the transmission wait fixture.
     *
     * @param evaluationOverride the transmission evaluation control
     * @param players            the fully initialized player slots
     * @return the created {@link TriggerContext} instance
     */
    private static @NotNull TriggerContext createContext(int evaluationOverride, @NotNull Player @NotNull [] players) {
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE];
        ByteBuffer record = TriggerRecordScenario.createRecord(records, 0);

        record.put(TriggerRecordCodec.CONDITION_TYPE_FIELD, (byte) TriggerConditionIds.ALWAYS);

        int base = TriggerRecordCodec.ACTIONS_OFFSET;

        record.putInt(base + TriggerRecordCodec.ACTION_LOCATION_FIELD, 1);
        record.putInt(base + TriggerRecordCodec.ACTION_DURATION_FIELD, 5000);
        record.putInt(base + TriggerRecordCodec.ACTION_SECONDARY_FIELD, 100);
        record.put(base + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) TriggerActionIds.TRANSMISSION);
        record.put(base + TriggerRecordCodec.ACTION_MODIFIER_FIELD, (byte) TriggerModifiers.SET_TO);

        base += TriggerRecordCodec.ACTION_SIZE;

        record.putInt(base + TriggerRecordCodec.ACTION_PLAYER_FIELD, TriggerPlayerSelectors.CURRENT_PLAYER);
        record.putInt(base + TriggerRecordCodec.ACTION_SECONDARY_FIELD, 7);
        record.put(base + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) TriggerActionIds.SET_RESOURCES);
        record.put(base + TriggerRecordCodec.ACTION_MODIFIER_FIELD, (byte) TriggerModifiers.SET_TO);
        record.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        int[] types = {PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                       PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(players);
        context.loadRecords(records);
        context.initializeEvaluation(PlayerProgressTestUtils.createUnitAccounting(),
                                     new PlayerRelations(types, false, false, false),
                                     new int[]{0, 0, evaluationOverride, 0, REPLAY_OBSERVER});

        return context;
    }

    /**
     * Creates the fully initialized player slots for the transmission fixture.
     *
     * @return the created player slots
     */
    private static @NotNull Player @NotNull [] createPlayers() {
        Player[] players = new Player[PlayerTable.PLAYABLE_PLAYER_SLOTS];

        players[0] = new Player(0, 0, PlayerTypes.HUMAN, 0, 0);

        for (int index = 1; index < PlayerTable.PLAYABLE_PLAYER_SLOTS; index++) {
            players[index] = new Player(index, index, PlayerTypes.NONE, 0, 0);
        }

        return players;
    }


    @Test
    void transmission_waits_for_set_duration_with_evaluation_override() {
        Player[] players = createPlayers();
        TriggerContext context = createContext(1, players);

        for (int call = 0; call < 10; call++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        }

        Assertions.assertEquals(7, players[0].getMinerals(), "Duration 100 (set by modifier 7) expired and the next action ran.");
    }

    @Test
    void transmission_waits_without_expiry_without_evaluation_override() {
        Player[] players = createPlayers();
        TriggerContext context = createContext(0, players);

        for (int call = 0; call < 200; call++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        }

        Assertions.assertEquals(0, players[0].getMinerals(), "Without the evaluation override the wait never expires in replays.");
    }
}
