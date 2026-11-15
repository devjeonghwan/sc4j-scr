package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.map.MapStringSection;
import org.fogofwarlabs.sc4j.scr.game.player.*;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicDynamicRegionsUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks trigger records that execute and rewrite their bytes in the map string section.
 */
final class RecordsInStringSectionTest {
    private static final int FRAME_DURATION = GameSpeed.FASTEST_DURATION;
    /**
     * The replay mode argument while replaying.
     */
    private static final int REPLAY_GAME = 1;
    /**
     * The local player of a replay observer, outside the playable slots.
     */
    private static final int REPLAY_OBSERVER = 8;

    private static final int STRINGS_SIZE = 0x4000;

    /**
     * The first record link offset within the retained string section.
     */
    private static final int FIRST_LINK = 0x100;
    private static final int SECOND_LINK = 0x1000;

    private static final int FIRST_RESULT = 0x3000;
    private static final int SECOND_RESULT = 0x3004;

    /**
     * The previous and next link DWORDs before the record of a list node.
     */
    private static final int NODE_HEADER_SIZE = 2 * Integer.BYTES;
    /**
     * The execution flag of a disabled record.
     */
    private static final int DISABLED_FLAG = 0x8;

    /**
     * @param offset the string-section byte offset
     * @return the classic address of a string section offset
     */
    private static int getAddress(int offset) {
        return ClassicDynamicRegionsUtils.STRING_SECTION_BASE + offset;
    }

    /**
     * Sets the Set Deaths operands of an action so that they address the supplied memory word.
     *
     * @param record   the record view
     * @param index    the action index
     * @param target   the addressed memory word
     * @param modifier the write modifier
     * @param amount   the written amount
     */
    private static void setDeathsAt(@NotNull ByteBuffer record, int index, int target, int modifier, int amount) {
        int base = TriggerRecordCodec.ACTIONS_OFFSET + index * TriggerRecordCodec.ACTION_SIZE;

        record.putInt(base + TriggerRecordCodec.ACTION_PLAYER_FIELD, (target - ClassicAddressSpace.DEATH_TABLE_ADDRESS) / Integer.BYTES);
        record.putInt(base + TriggerRecordCodec.ACTION_SECONDARY_FIELD, amount);
        record.putShort(base + TriggerRecordCodec.ACTION_UNIT_FIELD, (short) 0);
        record.put(base + TriggerRecordCodec.ACTION_TYPE_FIELD, (byte) TriggerActionIds.SET_DEATHS);
        record.put(base + TriggerRecordCodec.ACTION_MODIFIER_FIELD, (byte) modifier);
    }


    @Test
    void string_section_records_run_in_place_and_see_their_own_rewrites() {
        byte[] strings = new byte[STRINGS_SIZE];
        ByteBuffer section = ByteBuffer.wrap(strings).order(ByteOrder.LITTLE_ENDIAN);

        // The first record sets the first result and links to the second; it is not preserved, so it runs once.
        section.putInt(FIRST_LINK + Integer.BYTES, getAddress(SECOND_LINK));

        ByteBuffer first = TriggerRecordScenario.createRecordAt(strings, FIRST_LINK + NODE_HEADER_SIZE);

        first.put(TriggerRecordCodec.CONDITION_TYPE_FIELD, (byte) TriggerConditionIds.ALWAYS);
        setDeathsAt(first, 0, getAddress(FIRST_RESULT), TriggerModifiers.SET_TO, 5);

        // The second record adds one to the amount of its own second action, which then sets the second result: the
        // run sees its own rewrite. It is preserved and ends the walk (next link 0).
        section.putInt(SECOND_LINK + Integer.BYTES, 0);

        ByteBuffer second = TriggerRecordScenario.createRecordAt(strings, SECOND_LINK + NODE_HEADER_SIZE);

        second.put(TriggerRecordCodec.CONDITION_TYPE_FIELD, (byte) TriggerConditionIds.ALWAYS);

        int secondAmount = SECOND_LINK + NODE_HEADER_SIZE + TriggerRecordCodec.ACTIONS_OFFSET + TriggerRecordCodec.ACTION_SIZE +
                           TriggerRecordCodec.ACTION_SECONDARY_FIELD;

        setDeathsAt(second, 0, getAddress(secondAmount), TriggerModifiers.ADD, 1);
        setDeathsAt(second, 1, getAddress(SECOND_RESULT), TriggerModifiers.SET_TO, 10);
        second.put(TriggerRecordCodec.ACTIONS_OFFSET + 2 * TriggerRecordCodec.ACTION_SIZE + TriggerRecordCodec.ACTION_TYPE_FIELD,
                   (byte) TriggerActionIds.PRESERVE_TRIGGER);

        MapStringSection stringSection = new MapStringSection(strings, false);

        // The node of player 0 links its list to the first record.
        byte[] records = new byte[TriggerRecordCodec.RECORD_SIZE];
        ByteBuffer node = TriggerRecordScenario.createRecord(records, 0);

        node.put(TriggerRecordCodec.CONDITION_TYPE_FIELD, (byte) TriggerConditionIds.ALWAYS);

        int firstNode = ClassicDynamicRegionsUtils.getFirstTriggerNodeBase(STRINGS_SIZE);

        setDeathsAt(node, 0, firstNode + Integer.BYTES, TriggerModifiers.SET_TO, getAddress(FIRST_LINK));
        node.put(TriggerRecordCodec.OWNERS_OFFSET, (byte) 1);

        Player[] players = new Player[PlayerTable.PLAYABLE_PLAYER_SLOTS];

        for (int index = 0; index < PlayerTable.PLAYABLE_PLAYER_SLOTS; index++) {
            players[index] = new Player(index, index, index == 0 ? PlayerTypes.HUMAN : PlayerTypes.NONE, 0, 0);
        }

        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();
        TriggerContext context = new TriggerContext();

        context.initializeClassicPlayerRecords(players);
        context.loadRecords(records);

        int[] types = new int[PlayerTable.PLAYER_SLOTS];

        for (int index = 0; index < PlayerTable.PLAYABLE_PLAYER_SLOTS; index++) {
            types[index] = players[index].getType();
        }

        context.initializeEvaluation(accounting, new PlayerRelations(types, false, false, false), new int[]{0, 0, 0, 0, REPLAY_OBSERVER});

        ClassicAddressSpace space = ClassicAddressSpace.create(context, accounting.getScores(), accounting, null, new MessageLines());

        space.addAll(ClassicDynamicRegionsUtils.create(stringSection));
        space.addAll(context.getClassicTriggerRegions(firstNode));
        context.setExtendedDeathsOperations(space);

        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);

        Assertions.assertEquals(5, stringSection.readDword(FIRST_RESULT));
        Assertions.assertEquals(11, stringSection.readDword(SECOND_RESULT), "The second action runs with the amount the first just wrote.");
        Assertions.assertEquals(11, stringSection.readDword(secondAmount));

        stringSection.writeDword(FIRST_RESULT, 0);

        // The cycle countdown reloads with 30 and reaches zero on the next pass.
        for (int frame = 0; frame <= TriggerContext.CYCLE_COUNTDOWN_RELOAD; frame++) {
            ScrTriggerSystem.update(context, FRAME_DURATION, REPLAY_GAME, 0, 0);
        }

        Assertions.assertEquals(0, stringSection.readDword(FIRST_RESULT), "A record that is not preserved is disabled after its run.");
        Assertions.assertEquals(12, stringSection.readDword(SECOND_RESULT));
        Assertions.assertEquals(DISABLED_FLAG,
                                stringSection.readByte(FIRST_LINK + NODE_HEADER_SIZE + TriggerRecordCodec.FLAGS_OFFSET) &
                                TriggerFlags.EXECUTION_DISABLED,
                                "The first record's flags hold the disabled bit.");
    }
}
