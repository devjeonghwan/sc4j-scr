package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.type.constant.TriggerUnitGroups;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.*;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting.TriggerSnapshot;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Checks trigger operand decoding, record copies and independent execution state.
 */
final class TriggerRecordCodecTest {
    private static final int FRAME_DURATION = GameSpeed.FASTEST_DURATION;
    /**
     * The current-player DWORD after the active-player walk ended.
     */
    private static final int WALK_END_PLAYER = 8;
    /**
     * The replay mode argument while replaying.
     */
    private static final int REPLAY_GAME = 1;

    /**
     * The game-context offset of the building counts.
     */
    private static final int BUILDING_COUNTS_OFFSET = 0x2E44;
    /**
     * The game-context offset of the completed type counts.
     */
    private static final int COMPLETED_TYPE_COUNTS_OFFSET = 0x5CF4;

    /**
     * The last byte of an action, the padding after its marker.
     */
    private static final int ACTION_PADDING_FIELD = TriggerRecordCodec.ACTION_SIZE - 1;

    /**
     * The size of a trigger record.
     */
    private static final int RECORD_SIZE = 0x960;
    /**
     * The record offset of the amount DWORD of the first condition.
     */
    private static final int FIRST_CONDITION_AMOUNT_OFFSET = 8;
    /**
     * The record offset of the unit WORD of the first condition.
     */
    private static final int FIRST_CONDITION_UNIT_OFFSET = 12;
    /**
     * The record offset of the extension WORD of the first action.
     */
    private static final int FIRST_ACTION_EXTENSION_OFFSET = 0x15D;
    /**
     * The execution flags a disabled record gains when its actions run.
     */
    private static final int DISABLED_RUNNING_FLAGS = 0x9;
    /**
     * The action cursor after the last action.
     */
    private static final int FINISHED_ACTION_INDEX = 0x40;

    @Test
    void export_record_should_preserve_modeled_operands_and_exclude_action_padding() {
        byte[] source = new byte[TriggerRecordCodec.RECORD_SIZE];

        for (int offset = 0; offset < source.length; offset++) {
            source[offset] = (byte) (offset * 73 + offset / 251);
        }

        TriggerDefinition definition = TriggerRecordCodec.decodeDefinition(source, 0);
        RunningTrigger trigger = TriggerRecordCodec.createExecution(definition, source, 0);
        byte[] exported = TriggerRecordCodec.exportRecord(trigger);

        for (int offset = 0; offset < source.length; offset++) {
            boolean padding = offset >= TriggerRecordCodec.ACTIONS_OFFSET && offset < TriggerRecordCodec.FLAGS_OFFSET &&
                              (offset - TriggerRecordCodec.ACTIONS_OFFSET) % TriggerRecordCodec.ACTION_SIZE == ACTION_PADDING_FIELD;

            Assertions.assertEquals(padding ? 0 : source[offset], exported[offset]);
        }

        ByteBuffer bytes = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);

        Assertions.assertEquals(bytes.getInt(FIRST_CONDITION_AMOUNT_OFFSET), definition.getCondition(0).getAmount());
        Assertions.assertEquals(Short.toUnsignedInt(bytes.getShort(FIRST_CONDITION_UNIT_OFFSET)), definition.getCondition(0).getUnitType());
        Assertions.assertEquals(Short.toUnsignedInt(bytes.getShort(FIRST_ACTION_EXTENSION_OFFSET)), definition.getAction(0).getExtension());

        source[TriggerRecordCodec.ACTIONS_OFFSET + ACTION_PADDING_FIELD] ^= 127;

        RunningTrigger paddingChanged = TriggerRecordCodec.createExecution(TriggerRecordCodec.decodeDefinition(source, 0), source, 0);

        Assertions.assertArrayEquals(exported, TriggerRecordCodec.exportRecord(paddingChanged));

        source[0] ^= 127;

        Assertions.assertNotEquals(source[0], TriggerRecordCodec.exportRecord(trigger)[0]);

        RunningTrigger operandChanged = TriggerRecordCodec.createExecution(TriggerRecordCodec.decodeDefinition(source, 0), source, 0);

        Assertions.assertEquals(source[0], TriggerRecordCodec.exportRecord(operandChanged)[0]);
    }

    @Test
    void load_all_players_resource_should_keep_registrations_before_unsupported_selector() {
        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(TriggerPlayerScenario.createPlayers(new int[]{PlayerTypes.HUMAN, PlayerTypes.HUMAN,
                                                                                             PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                                                                                             PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE},
                                                                                   new byte[PlayerTable.PLAYABLE_PLAYER_SLOTS]));

        byte[] source = new byte[TriggerRecordCodec.RECORD_SIZE + 11];

        source[TriggerRecordCodec.OWNERS_OFFSET + TriggerPlayerSelectors.ALL_PLAYERS] = 1;
        source[TriggerRecordCodec.OWNERS_OFFSET + TriggerPlayerSelectors.FORCE_1]     = 1;
        source[TriggerRecordCodec.ACTION_INDEX_OFFSET]                                = (byte) 255;

        context.loadAllPlayersResource(source);

        Assertions.assertEquals(1, context.getPlayerRecordCount(0));
        Assertions.assertEquals(1, context.getPlayerRecordCount(1));
        Assertions.assertArrayEquals(Arrays.copyOf(source, RECORD_SIZE), context.getPlayerRecord(0, 0));
        Assertions.assertArrayEquals(context.getPlayerRecord(0, 0), context.getPlayerRecord(1, 0));

        source[0] = 91;

        byte[] detached = context.getPlayerRecord(0, 0);

        detached[1] = 92;

        Assertions.assertEquals(0, context.getPlayerRecord(0, 0)[0]);
        Assertions.assertEquals(0, context.getPlayerRecord(0, 0)[1]);
    }

    @Test
    void player_record_copies_keep_matched_action_failure_and_independent_state() {
        int[] playerTypes = {PlayerTypes.HUMAN, PlayerTypes.HUMAN, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE,
                             PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE, PlayerTypes.NONE};
        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(TriggerPlayerScenario.createPlayers(playerTypes, new byte[PlayerTable.PLAYABLE_PLAYER_SLOTS]));

        byte[] source = new byte[TriggerRecordCodec.RECORD_SIZE];
        ByteBuffer bytes = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);

        bytes.putInt(TriggerRecordCodec.CONDITION_PLAYER_FIELD, TriggerPlayerSelectors.CURRENT_PLAYER);
        bytes.putShort(TriggerRecordCodec.CONDITION_UNIT_FIELD, (short) TriggerUnitGroups.BUILDINGS);
        source[TriggerRecordCodec.CONDITION_COMPARISON_FIELD] = TriggerModifiers.AT_MOST;
        source[TriggerRecordCodec.CONDITION_TYPE_FIELD]       = TriggerConditionIds.COMMAND;
        bytes.putInt(TriggerRecordCodec.FLAGS_OFFSET, 0x12345600);
        source[TriggerRecordCodec.OWNERS_OFFSET + TriggerPlayerSelectors.ALL_PLAYERS] = 1;
        source[TriggerRecordCodec.ACTION_INDEX_OFFSET]                                = (byte) 171;

        context.loadAllPlayersResource(source);
        context.initializeEvaluation(PlayerProgressTestUtils.createUnitAccounting(), new PlayerRelations(playerTypes, false, false, false),
                                     new int[TriggerContext.CONTROL_COUNT]);
        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);

        byte[] expected = source.clone();

        expected[TriggerRecordCodec.FLAGS_OFFSET] |=       DISABLED_RUNNING_FLAGS;
        expected[TriggerRecordCodec.ACTION_INDEX_OFFSET] = FINISHED_ACTION_INDEX;

        Assertions.assertArrayEquals(expected, context.getPlayerRecord(0, 0));
        Assertions.assertArrayEquals(expected, context.getPlayerRecord(1, 0));
        Assertions.assertArrayEquals(source,
                                     TriggerRecordCodec.exportRecord(TriggerRecordCodec.createExecution(
                                             TriggerRecordCodec.decodeDefinition(source, 0), source, 0
                                     )),
                                     "The shared definition is unchanged.");
        Assertions.assertEquals(WALK_END_PLAYER, context.getCurrentPlayerIndex());
        Assertions.assertNotNull(context.getAccountingSnapshot());
    }

    @Test
    void copy_trigger_snapshot_should_copy_represented_counters_after_reset() {
        int[] source = new int[UnitAccountingAssert.CONTEXT_BYTE_COUNT / Integer.BYTES];

        for (int index = 0; index < source.length; index++) {
            source[index] = index * 0x9E3779B9 + 12345;
        }

        UnitAccounting accounting = UnitAccountingAssert.fromCapture(source);

        for (int pass = 0; pass < 2; pass++) {
            if (pass != 0) {
                accounting.initializeForGame(null);
            }

            TriggerSnapshot snapshot = new TriggerSnapshot();

            accounting.copyTriggerSnapshot(snapshot);

            int webCount = ScrUnitIds.SPELL_DISRUPTION_WEB * PlayerTable.PLAYER_SLOTS + PlayerTable.NEUTRAL_PLAYER_ID;

            Assertions.assertEquals(UnitAccountingAssert.readCounter(accounting, BUILDING_COUNTS_OFFSET + 7 * Integer.BYTES),
                                    snapshot.getBuildingCount(7));
            Assertions.assertEquals(UnitAccountingAssert.readCounter(accounting, COMPLETED_TYPE_COUNTS_OFFSET + webCount * Integer.BYTES),
                                    accounting.getCompletedTypeCount(ScrUnitIds.SPELL_DISRUPTION_WEB, PlayerTable.NEUTRAL_PLAYER_ID));
        }
    }
}
