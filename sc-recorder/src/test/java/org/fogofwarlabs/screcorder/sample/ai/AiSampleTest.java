package org.fogofwarlabs.screcorder.sample.ai;

import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.sample.RangeMemoryScenario;
import org.fogofwarlabs.screcorder.sample.SampleRoundTripCheck;
import org.fogofwarlabs.screcorder.sample.context.ContextSample;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks that the AI sampler captures routes, regions, player memory and unit AI state through the kind four pointer chain and that its reader
 * rejects oversized counts.
 */
final class AiSampleTest {
    private static final long MODULE_BASE = 0x100000000L;
    private static final long CONTEXT_ADDRESS = 0x120000000L;
    private static final int MODULE_BYTES = 0x1100000;

    private static final int AI_CLOCK_OFFSET = 0xE608;
    private static final int REGION_COUNT_POINTER_OFFSET = 0x1097780;
    private static final int REGION_TABLE_OFFSET = 0xEBCE30;
    private static final int ROUTE_CLOCK_OFFSET = 0xEC1C90;
    private static final int ROUTE_SOURCE_OFFSET = 0xEC1CB0;
    private static final int ROUTE_MARKS_OFFSET = 0xEBCE70;
    private static final int AI_PLAYER_RECORD_OFFSET = 0xE7FED0;
    private static final int UNIT_POOL_POINTER_OFFSET = 0xE78060;
    private static final int UNIT_POOL_SLOTS_OFFSET = 0xE78068;
    private static final int UNIT_STRIDE = 0x1E8;
    private static final int UNIT_AI_RECORD_OFFSET = 0x1C8;

    private static final int UNIT_ACTIVE_OFFSET = 0x18;
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    private static final int UNIT_OWNER_OFFSET = 0x68;
    private static final int UNIT_AUTO_TARGET_OFFSET = 0xB8;
    private static final int UNIT_ORDER_TARGET_OFFSET = 0x80;
    private static final int UNIT_AIR_STRENGTH_OFFSET = 0x1D0;
    private static final int UNIT_GROUND_STRENGTH_OFFSET = 0x1D2;
    private static final int UNIT_ORDER_OFFSET = 0x69;
    private static final int UNIT_ORDER_STATE_OFFSET = 0x6A;
    private static final int UNIT_TARGET_X_OFFSET = 0x78;
    private static final int UNIT_TARGET_Y_OFFSET = 0x7A;
    private static final int UNIT_POSITION_X_OFFSET = 0x40;
    private static final int UNIT_POSITION_Y_OFFSET = 0x42;
    private static final int UNIT_SECONDARY_ORDER_OFFSET = 0xEA;
    private static final int AI_PLAYER_RECORD_STRIDE = 0x6E8;
    private static final int UNIT_STATUS_OFFSET = 0x140;
    private static final int AI_RECORD_KIND_OFFSET = 0x10;
    private static final int KIND_WORD_POINTER_OFFSET = 0x20;

    private static final long AUXILIARY_ADDRESS = 0x140000000L;
    private static final long REGION_ADDRESS = 0x160000000L;
    private static final long UNIT_POOL_ADDRESS = 0x180000000L;
    private static final long AI_RECORD_ADDRESS = 0x1A0000000L;
    private static final long KIND_WORD_ADDRESS = 0x1C0000000L;

    private static final int AI_PLAYER = 4;
    private static final int AI_REGION_COUNT = 3;
    private static final int AI_REGION_BYTES = 80;
    private static final int ROUTE_MARKS_BYTES = 2500;
    private static final int UNIT_POOL_SLOT_COUNT = 2;
    private static final int AI_RECORD_BYTES = 0x28;
    private static final int AI_RECORD_KIND = 4;
    private static final int AI_PAYLOAD_PREFIX_BYTES = 4 * Integer.BYTES;

    @Test
    void ai_sampler_captures_routes_regions_and_the_kind_four_pointer_chain() throws IOException {
        RangeMemoryScenario memory = new RangeMemoryScenario();
        byte[] moduleStorage = memory.allocateRange(MODULE_BASE, MODULE_BYTES);
        byte[] contextStorage = memory.allocateRange(CONTEXT_ADDRESS, ContextSample.MEMORY_BYTES);
        ByteBuffer module = ByteBuffer.wrap(moduleStorage).order(ByteOrder.LITTLE_ENDIAN);

        ByteBuffer.wrap(contextStorage).order(ByteOrder.LITTLE_ENDIAN).putInt(AI_CLOCK_OFFSET, -1);

        long countAddress = AUXILIARY_ADDRESS;
        long regionAddress = REGION_ADDRESS;
        long poolAddress = UNIT_POOL_ADDRESS;
        long recordAddress = AI_RECORD_ADDRESS;
        long kindWordAddress = KIND_WORD_ADDRESS;

        module.putLong(REGION_COUNT_POINTER_OFFSET, countAddress).putLong(REGION_TABLE_OFFSET + AI_PLAYER * Long.BYTES, regionAddress);
        ByteBuffer.wrap(memory.allocateRange(countAddress, Short.BYTES)).order(ByteOrder.LITTLE_ENDIAN).putShort((short) AI_REGION_COUNT);

        byte[] regions = memory.allocateRange(regionAddress, AI_REGION_COUNT * AI_REGION_BYTES);

        regions[(AI_REGION_COUNT - 1) * AI_REGION_BYTES] = 77;
        module.putInt(ROUTE_CLOCK_OFFSET + AI_PLAYER * Integer.BYTES, -1).putInt(ROUTE_SOURCE_OFFSET + AI_PLAYER * Integer.BYTES, -10);
        moduleStorage[ROUTE_MARKS_OFFSET + AI_PLAYER * ROUTE_MARKS_BYTES]            = 9;
        moduleStorage[AI_PLAYER_RECORD_OFFSET + AI_PLAYER * AI_PLAYER_RECORD_STRIDE] = 11;
        module.putLong(UNIT_POOL_POINTER_OFFSET, poolAddress).putLong(UNIT_POOL_SLOTS_OFFSET, UNIT_POOL_SLOT_COUNT);

        ByteBuffer pool = ByteBuffer.wrap(memory.allocateRange(poolAddress, UNIT_POOL_SLOT_COUNT * UNIT_STRIDE)).order(ByteOrder.LITTLE_ENDIAN);

        pool.putLong(UNIT_ACTIVE_OFFSET, 1).putShort(UNIT_TYPE_OFFSET, (short) 65535)
            .put(UNIT_OWNER_OFFSET, (byte) AI_PLAYER).putInt(UNIT_STATUS_OFFSET, -1);
        pool.putLong(UNIT_AUTO_TARGET_OFFSET, poolAddress + UNIT_STRIDE).putLong(UNIT_ORDER_TARGET_OFFSET, poolAddress + UNIT_STRIDE)
            .putLong(UNIT_AI_RECORD_OFFSET, recordAddress);
        pool.putShort(UNIT_AIR_STRENGTH_OFFSET, (short) 10).putShort(UNIT_GROUND_STRENGTH_OFFSET, (short) 20)
            .put(UNIT_ORDER_OFFSET, (byte) 5).put(UNIT_ORDER_STATE_OFFSET, (byte) 6);
        pool.putShort(UNIT_TARGET_X_OFFSET, (short) -4).putShort(UNIT_TARGET_Y_OFFSET, (short) 9)
            .putShort(UNIT_POSITION_X_OFFSET, (short) 500).putShort(UNIT_POSITION_Y_OFFSET, (short) 600)
            .put(UNIT_SECONDARY_ORDER_OFFSET, (byte) 7);

        ByteBuffer record = ByteBuffer.wrap(memory.allocateRange(recordAddress, AI_RECORD_BYTES)).order(ByteOrder.LITTLE_ENDIAN);

        record.put(AI_RECORD_KIND_OFFSET, (byte) AI_RECORD_KIND).putLong(KIND_WORD_POINTER_OFFSET, kindWordAddress);
        ByteBuffer.wrap(memory.allocateRange(kindWordAddress, Short.BYTES)).order(ByteOrder.LITTLE_ENDIAN).putShort((short) 65535);

        AiSampleDefinition definition = new AiSampleDefinition();
        AiSample sample = definition.createSampler(new CaptureContext(memory, MODULE_BASE, CONTEXT_ADDRESS)).sample();
        AiSample decoded = SampleRoundTripCheck.readAfterWrite(definition, sample);

        Assertions.assertEquals(AI_REGION_COUNT, decoded.getRegionCount());
        Assertions.assertEquals(0xFFFFFFFFL, decoded.getClock());
        Assertions.assertEquals(8, decoded.getPlayers().size());
        Assertions.assertEquals(AI_REGION_COUNT, decoded.getPlayers().get(AI_PLAYER).getRegions().size());
        Assertions.assertEquals(77, decoded.getPlayers().get(AI_PLAYER).getRegions().get(AI_REGION_COUNT - 1).getMemory()[0]);
        Assertions.assertEquals(0xFFFFFFFFL, decoded.getPlayers().get(AI_PLAYER).getRouteClock());
        Assertions.assertEquals(-10, decoded.getPlayers().get(AI_PLAYER).getRouteSource());
        Assertions.assertEquals(9, decoded.getPlayers().get(AI_PLAYER).getRouteMarks()[0]);
        Assertions.assertEquals(11, decoded.getPlayers().get(AI_PLAYER).getPlayerMemory()[0]);
        Assertions.assertTrue(decoded.getPlayers().getFirst().getRegions().isEmpty());
        Assertions.assertEquals(1, decoded.getUnits().size());

        AiUnitState unit = decoded.getUnits().getFirst();

        Assertions.assertEquals(1, unit.getIdentifier());
        Assertions.assertEquals(2, unit.getAutoTargetIdentifier());
        Assertions.assertEquals(2, unit.getOrderTargetIdentifier());
        Assertions.assertEquals(recordAddress - MODULE_BASE, unit.getRecordAddress());
        Assertions.assertEquals(AI_RECORD_KIND, unit.getKind());
        Assertions.assertEquals(65535, unit.getKindWord());
        Assertions.assertEquals(-4, unit.getTargetX());
        Assertions.assertEquals(0xFFFFFFFFL, unit.getStatus());

        pool.putLong(UNIT_AUTO_TARGET_OFFSET, poolAddress + 1);
        Assertions.assertThrows(IOException.class, () -> definition.createSampler(new CaptureContext(memory, MODULE_BASE, CONTEXT_ADDRESS)).sample());
    }

    @Test
    void ai_reader_rejects_oversized_player_and_unit_counts() {
        AiSampleDefinition definition = new AiSampleDefinition();
        ByteBuffer invalidPlayers = ByteBuffer.allocate(AI_PAYLOAD_PREFIX_BYTES).order(ByteOrder.LITTLE_ENDIAN)
                                              .putInt(0).putInt(0).putInt(Integer.MAX_VALUE).putInt(0);

        invalidPlayers.flip();
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(invalidPlayers));

        ByteBuffer invalidUnits = ByteBuffer.allocate(AI_PAYLOAD_PREFIX_BYTES).order(ByteOrder.LITTLE_ENDIAN)
                                            .putInt(0).putInt(0).putInt(0).putInt(Integer.MAX_VALUE);

        invalidUnits.flip();
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(invalidUnits));
    }
}
