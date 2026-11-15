package org.fogofwarlabs.screcorder.sample;

import org.fogofwarlabs.screcorder.capture.MemoryReader;
import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.starcraft.StarCraftLayout;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.format.recording.Frame;
import org.fogofwarlabs.screcorder.format.recording.RecordReader;
import org.fogofwarlabs.screcorder.format.recording.RecordWriter;
import org.fogofwarlabs.screcorder.format.recording.metadata.RecordMetadata;
import org.fogofwarlabs.screcorder.format.recording.observation.CaptureConsistency;
import org.fogofwarlabs.screcorder.format.recording.observation.StorageMode;
import org.fogofwarlabs.screcorder.format.recording.summary.Termination;
import org.fogofwarlabs.screcorder.sample.frame.FrameStateSample;
import org.fogofwarlabs.screcorder.sample.player.PlayerState;
import org.fogofwarlabs.screcorder.sample.player.PlayersSample;
import org.fogofwarlabs.screcorder.sample.random.RandomSample;
import org.fogofwarlabs.screcorder.sample.trigger.TriggerWaitState;
import org.fogofwarlabs.screcorder.sample.trigger.TriggersSample;
import org.fogofwarlabs.screcorder.sample.unit.UnitState;
import org.fogofwarlabs.screcorder.sample.unit.UnitsSample;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Checks that the players, units, random, frame and trigger samplers read their memory and that their samples round-trip, keep canonical keys and
 * wire widths and validate their payloads.
 */
final class SampleDefinitionsTest {
    private static final int PLAYER_CONTEXT_BYTES = 0x31F4;
    private static final int PLAYER_ROW_BYTES = 53;
    private static final int PLAYER_GAS_OFFSET = 0x30;
    private static final int PLAYER_PROTOSS_SUPPLY_CAP_OFFSET = 0x31D4;
    private static final int UNIT_ACTIVE_OFFSET = 0x18;
    private static final int UNIT_GENERATION_OFFSET = 0xE9;
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    private static final int UNIT_OWNER_OFFSET = 0x68;
    private static final int UNIT_HIT_POINTS_OFFSET = 0x10;
    private static final int UNIT_ENERGY_OFFSET = 0xE6;
    private static final int UNIT_TARGET_POINTER_OFFSET = 0x80;
    private static final int UNIT_TARGET_X_OFFSET = 0x78;
    private static final int UNIT_STATUS_OFFSET = 0x140;
    private static final int RANDOM_BLOCK_BYTES = 16;
    private static final int RANDOM_TOTAL_OFFSET = 8;
    private static final int RANDOM_GATE_OFFSET = 12;
    private static final long MODULE_BASE = 0x140000000L;
    private static final long CONTEXT_ADDRESS = 0x200000000L;
    private static final long POOL_ADDRESS = 0x300000000L;
    private static final int UNIT_POOL_SLOT_COUNT = 2;

    private static final int UNION_WORDS = 0;

    private static final int SIMULATION_COUNTER_BYTES = 0x3C;
    private static final int UNIT_COUNT_OFFSET = 0x38;
    private static final long SIMULATION_COUNTER_OFFSET = 0xE77FC0;
    private static final long GAME_CLOCK_OFFSET = 0xE608;
    private static final long UNIT_CAPACITY_OFFSET = 0xE7E024;
    private static final long TRIGGER_CYCLE_OFFSET = 0x10B0D30;
    private static final long TRIGGER_WAIT_OFFSET = 0x10B0E10;
    private static final long LOCAL_PLAYER_OFFSET = 0xDD5B5C;
    private static final long MULTIPLAYER_FLAG_OFFSET = 0x10908DC;
    private static final long REPLAY_FLAG_OFFSET = 0x1095864;
    private static final long REGION_MODULE_BASE = 0x100000;
    private static final long REGION_CONTEXT_ADDRESS = 0x200000;
    private static final int PLAYER_COUNT = 8;
    private static final int LOCAL_PLAYER_BLOCK_BYTES = 3 * Integer.BYTES;
    private static final int STORM_PLAYER_OFFSET = 2 * Integer.BYTES;
    private static final int FRAME_PAYLOAD_BYTES = 28;
    private static final int TRIGGERS_PAYLOAD_BYTES = 52;

    private static final class PoolMemoryScenario implements MemoryReader {
        private final byte @NotNull [] context;
        private final byte @NotNull [] pool;
        private final byte @NotNull [] metadata;
        private final byte @NotNull [] random;
        private final @NotNull List<Long> requests;

        private PoolMemoryScenario() {
            this.context  = new byte[PLAYER_CONTEXT_BYTES];
            this.pool     = new byte[UNIT_POOL_SLOT_COUNT * StarCraftLayout.UNIT_STRIDE];
            this.metadata = new byte[2 * Long.BYTES];
            this.random   = new byte[RANDOM_BLOCK_BYTES];
            this.requests = new ArrayList<>();

            ByteBuffer.wrap(this.metadata).order(ByteOrder.LITTLE_ENDIAN).putLong(POOL_ADDRESS).putLong(UNIT_POOL_SLOT_COUNT);
        }


        /**
         * Reads the entire requested range.
         *
         * @param address     the target address
         * @param destination the destination storage
         * @param offset      the destination offset
         * @param length      the byte count
         * @throws IOException if the entire range cannot be read
         */
        @Override
        public void read(long address, byte @NotNull [] destination, int offset, int length) throws IOException {
            this.requests.add(address);

            byte[] source;

            if (address == CONTEXT_ADDRESS) {
                source = this.context;
            } else if (address == POOL_ADDRESS) {
                source = this.pool;
            } else if (address == MODULE_BASE + StarCraftLayout.UNIT_POOL_POINTER) {
                source = this.metadata;
            } else if (address == MODULE_BASE + StarCraftLayout.RANDOM_BLOCK) {
                source = this.random;
            } else {
                throw Exceptions.newIOException("Unexpected fixture address: {0}", address);
            }

            System.arraycopy(source, 0, destination, offset, length);
        }
    }

    private static final class RegionMemoryScenario implements MemoryReader {
        private final @NotNull Map<Long, byte[]> regions;

        private RegionMemoryScenario(@NotNull Map<Long, byte[]> regions) {
            this.regions = regions;
        }


        /**
         * Reads the entire requested range.
         *
         * @param address     the target address
         * @param destination the destination storage
         * @param offset      the destination offset
         * @param length      the byte count
         * @throws IOException if the entire range cannot be read
         */
        @Override
        public void read(long address, byte @NotNull [] destination, int offset, int length) throws IOException {
            byte[] region = this.regions.get(address);

            if (region == null || region.length != length) {
                throw Exceptions.newIOException("Unexpected native fixture read at {0}", address);
            }

            System.arraycopy(region, 0, destination, offset, length);
        }
    }


    @Test
    void samplers_read_bulk_memory_and_convert_targets_to_stable_slots() throws IOException {
        PoolMemoryScenario scenario = new PoolMemoryScenario();
        ByteBuffer playerMemory = ByteBuffer.wrap(scenario.context).order(ByteOrder.LITTLE_ENDIAN);

        playerMemory.putInt(0, 50).putInt(PLAYER_GAS_OFFSET, 25).putInt(PLAYER_PROTOSS_SUPPLY_CAP_OFFSET, 400);

        ByteBuffer unitMemory = ByteBuffer.wrap(scenario.pool).order(ByteOrder.LITTLE_ENDIAN);

        unitMemory.putLong(UNIT_ACTIVE_OFFSET, 1).put(UNIT_GENERATION_OFFSET, (byte) 7);
        unitMemory.putShort(UNIT_TYPE_OFFSET, (short) 3).put(UNIT_OWNER_OFFSET, (byte) 2);
        unitMemory.putInt(UNIT_HIT_POINTS_OFFSET, 5120).putShort(UNIT_ENERGY_OFFSET, (short) 768);
        unitMemory.putLong(UNIT_TARGET_POINTER_OFFSET, POOL_ADDRESS + StarCraftLayout.UNIT_STRIDE);
        unitMemory.putShort(UNIT_TARGET_X_OFFSET, (short) -4).putInt(UNIT_STATUS_OFFSET, 0xFFFFFFFF);

        ByteBuffer.wrap(scenario.random).order(ByteOrder.LITTLE_ENDIAN).putInt(0, -1).putInt(RANDOM_TOTAL_OFFSET, 123).putInt(RANDOM_GATE_OFFSET, 1);

        CaptureContext context = new CaptureContext(scenario, MODULE_BASE, CONTEXT_ADDRESS);

        PlayersSample players = SampleDefinitions.PLAYERS.createSampler(context).sample();
        UnitsSample units = SampleDefinitions.UNITS.createSampler(context).sample();
        RandomSample random = SampleDefinitions.RANDOM.createSampler(context).sample();

        Assertions.assertEquals(8, players.getPlayers().size());
        Assertions.assertEquals(50, players.getPlayers().getFirst().getMinerals());
        Assertions.assertEquals(25, players.getPlayers().getFirst().getGas());
        Assertions.assertEquals(400, players.getPlayers().getFirst().getProtossSupplyCap());
        Assertions.assertEquals(1, units.getUnits().size());

        UnitState unit = units.getUnits().getFirst();

        Assertions.assertEquals(1, unit.getIdentifier());
        Assertions.assertEquals(7, unit.getGeneration());
        Assertions.assertEquals(2, unit.getOwner());
        Assertions.assertEquals(5120, unit.getHitPoints());
        Assertions.assertEquals(768, unit.getEnergy());
        Assertions.assertEquals(2, unit.getOrderTargetIdentifier());
        Assertions.assertEquals(-4, unit.getOrderTargetX());
        Assertions.assertEquals(0xFFFFFFFFL, unit.getStatus());
        Assertions.assertEquals(0xFFFFFFFFL, random.getState());
        Assertions.assertEquals(123, random.getTotal());
        Assertions.assertEquals(1, random.getGate());
        Assertions.assertEquals(4, scenario.requests.size());

        playerMemory.putInt(0, 999);

        Assertions.assertEquals(50, players.getPlayers().getFirst().getMinerals());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> players.getPlayers().clear());

        ByteBuffer encodedUnit = ByteBuffer.allocate(SampleDefinitions.UNITS.getEncodedSize(units));

        SampleDefinitions.UNITS.getWriter().write(encodedUnit, units);
        encodedUnit.flip();

        UnitState decoded = SampleDefinitions.UNITS.getReader().read(encodedUnit).getUnits().getFirst();

        Assertions.assertEquals(2, decoded.getOrderTargetIdentifier());
        Assertions.assertEquals(-4, decoded.getOrderTargetX());
        Assertions.assertEquals(0xFFFFFFFFL, decoded.getStatus());

        ByteBuffer invalidUnitIdentifier = ByteBuffer.wrap(encodedUnit.array().clone()).order(ByteOrder.LITTLE_ENDIAN);

        invalidUnitIdentifier.putShort(Integer.BYTES, (short) 0);
        Assertions.assertThrows(IOException.class, () -> SampleDefinitions.UNITS.getReader().read(invalidUnitIdentifier));

        invalidUnitIdentifier.putShort(Integer.BYTES, (short) (StarCraftLayout.MAXIMUM_UNITS + 1));
        invalidUnitIdentifier.rewind();
        Assertions.assertThrows(IOException.class, () -> SampleDefinitions.UNITS.getReader().read(invalidUnitIdentifier));
    }

    @Test
    void units_sampler_rejects_unresolvable_pointers_and_wide_pool_counts() {
        PoolMemoryScenario scenario = new PoolMemoryScenario();

        ByteBuffer.wrap(scenario.pool).order(ByteOrder.LITTLE_ENDIAN)
                  .putLong(UNIT_ACTIVE_OFFSET, 1).putLong(UNIT_TARGET_POINTER_OFFSET, POOL_ADDRESS + 1);

        CaptureContext context = new CaptureContext(scenario, MODULE_BASE, CONTEXT_ADDRESS);

        Assertions.assertThrows(IOException.class, () -> SampleDefinitions.UNITS.createSampler(context).sample());

        ByteBuffer.wrap(scenario.metadata).order(ByteOrder.LITTLE_ENDIAN).putLong(Long.BYTES, 0x100000000L);
        Assertions.assertThrows(IOException.class, () -> SampleDefinitions.UNITS.createSampler(context).sample());

        ByteBuffer.wrap(scenario.metadata).order(ByteOrder.LITTLE_ENDIAN).putLong(Long.BYTES, -1);
        Assertions.assertThrows(IOException.class, () -> SampleDefinitions.UNITS.createSampler(context).sample());
    }

    @Test
    void players_sample_orders_canonical_keys_and_rejects_malformed_tables() throws IOException {
        PlayerState first = new PlayerState(0, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        PlayerState second = new PlayerState(1, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        PlayersSample unordered = new PlayersSample(List.of(second, first));
        SampleDefinition<PlayersSample> definition = SampleDefinitions.PLAYERS;
        ByteBuffer output = ByteBuffer.allocate(definition.getEncodedSize(unordered));

        definition.getWriter().write(output, unordered);

        byte[] canonical = output.array().clone();

        output.flip();

        PlayersSample decoded = definition.getReader().read(output);

        Assertions.assertEquals(0, decoded.getPlayers().getFirst().getIdentifier());
        Assertions.assertEquals(10, decoded.getPlayers().getFirst().getMinerals());

        ByteBuffer duplicate = ByteBuffer.wrap(canonical.clone());

        duplicate.put(Integer.BYTES + PLAYER_ROW_BYTES, (byte) 0);
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(duplicate));

        ByteBuffer invalidIdentifier = ByteBuffer.wrap(canonical.clone());

        invalidIdentifier.put(Integer.BYTES, (byte) 8);
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(invalidIdentifier));

        ByteBuffer enormousCount = ByteBuffer.wrap(canonical.clone()).order(ByteOrder.LITTLE_ENDIAN);

        enormousCount.putInt(0, Integer.MAX_VALUE);
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(enormousCount));

        ByteBuffer truncated = ByteBuffer.wrap(canonical.clone());

        truncated.limit(truncated.limit() - 1);
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(truncated));
        Assertions.assertThrows(IOException.class, () -> definition.getEncodedSize(new PlayersSample(List.of(first, first))));
        Assertions.assertThrows(IOException.class, () -> definition.getWriter().write(ByteBuffer.allocate(1), unordered));
        Assertions.assertThrows(IOException.class,
                                () -> definition.getWriter().write(ByteBuffer.allocate(canonical.length).asReadOnlyBuffer(), unordered));
    }

    @Test
    void units_sample_keeps_wire_widths_and_signed_extremes() throws IOException {
        byte[] expected = HexFormat.of().parseHex(
                "01000000480dffffffffffff008000000080ffffff7fffffffff80000000ffffff8001480d0080ffffff7f0000ff804020eb32a4f8ffffffff00ffffffffffffffffffffff"
        );
        SampleDefinition<UnitsSample> definition = SampleDefinitions.UNITS;
        UnitsSample decoded = definition.getReader().read(ByteBuffer.wrap(expected));
        UnitState unit = decoded.getUnits().getFirst();

        Assertions.assertEquals(3400, unit.getIdentifier());
        Assertions.assertEquals(255, unit.getGeneration());
        Assertions.assertEquals(65535, unit.getType());
        Assertions.assertEquals(65535, unit.getPositionX());
        Assertions.assertEquals(32768, unit.getPositionY());
        Assertions.assertEquals(Integer.MIN_VALUE, unit.getExactPositionX());
        Assertions.assertEquals(Integer.MAX_VALUE, unit.getExactPositionY());
        Assertions.assertEquals(-1, unit.getHitPoints());
        Assertions.assertEquals(65535, unit.getEnergy());
        Assertions.assertEquals(3400, unit.getOrderTargetIdentifier());
        Assertions.assertEquals(-32768, unit.getOrderTargetX());
        Assertions.assertEquals(32767, unit.getOrderTargetY());
        Assertions.assertEquals(-123456789, unit.getCurrentSpeed());
        Assertions.assertEquals(0xFFFFFFFFL, unit.getStatus());
        Assertions.assertEquals(65535, unit.getRemainingBuildTime());
        Assertions.assertEquals(UNION_WORDS, unit.getUnionKind());
        Assertions.assertEquals(-1L, unit.getUnionValue());

        ByteBuffer output = ByteBuffer.allocate(definition.getEncodedSize(decoded));

        definition.getWriter().write(output, decoded);
        Assertions.assertArrayEquals(expected, output.array());
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(ByteBuffer.wrap(new byte[3])));
        Assertions.assertThrows(IOException.class, () -> definition.getWriter().write(ByteBuffer.allocate(expected.length - 1), decoded));
    }

    @Test
    void random_sample_keeps_unsigned_values_and_definitions_resolve_by_identifier_and_name() throws IOException {
        SampleDefinition<RandomSample> definition = SampleDefinitions.RANDOM;
        RandomSample sample = new RandomSample(0xFFFFFFFFL, 0x80000000L, 1);
        ByteBuffer output = ByteBuffer.allocate(definition.getEncodedSize(sample));

        definition.getWriter().write(output, sample);
        Assertions.assertArrayEquals(new byte[]{-1, -1, -1, -1, 0, 0, 0, -128, 1, 0, 0, 0}, output.array());
        output.flip();
        Assertions.assertEquals(sample.getState(), definition.getReader().read(output).getState());
        Assertions.assertThrows(IOException.class, () -> definition.getEncodedSize(new RandomSample(0x100000000L, 0, 0)));

        for (SampleDefinition<?> supportedDefinition : SampleDefinitions.getAll()) {
            Assertions.assertSame(supportedDefinition, SampleDefinitions.findByIdentifier(supportedDefinition.getIdentifier()));
            Assertions.assertSame(supportedDefinition, SampleDefinitions.findByName(supportedDefinition.getName()));
        }
    }

    @Test
    void frame_and_trigger_samplers_read_simulation_and_trigger_state_from_memory() throws IOException {
        byte[] random = new byte[RANDOM_BLOCK_BYTES];

        ByteBuffer.wrap(random).order(ByteOrder.LITTLE_ENDIAN).putInt(0, -1).putInt(RANDOM_TOTAL_OFFSET, Integer.MIN_VALUE);

        byte[] counters = new byte[SIMULATION_COUNTER_BYTES];

        ByteBuffer.wrap(counters).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 17).putInt(Integer.BYTES, 23).putInt(UNIT_COUNT_OFFSET, 42);

        byte[] clock = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(123).array();
        byte[] capacity = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(3400).array();
        byte[] cycle = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN).putShort((short) 0xFFFE).array();
        byte[] waits = new byte[PLAYER_COUNT * (Byte.BYTES + Integer.BYTES)];

        waits[PLAYER_COUNT - 1] = (byte) 0xFF;
        ByteBuffer.wrap(waits).order(ByteOrder.LITTLE_ENDIAN).putInt(PLAYER_COUNT + (PLAYER_COUNT - 1) * Integer.BYTES, -1);

        byte[] local = new byte[LOCAL_PLAYER_BLOCK_BYTES];

        ByteBuffer.wrap(local).order(ByteOrder.LITTLE_ENDIAN).putInt(0, -1).putInt(STORM_PLAYER_OFFSET, 2);

        RegionMemoryScenario memory = new RegionMemoryScenario(Map.of(
                REGION_MODULE_BASE + StarCraftLayout.RANDOM_BLOCK, random,
                REGION_MODULE_BASE + SIMULATION_COUNTER_OFFSET, counters,
                REGION_CONTEXT_ADDRESS + GAME_CLOCK_OFFSET, clock,
                REGION_MODULE_BASE + UNIT_CAPACITY_OFFSET, capacity,
                REGION_MODULE_BASE + TRIGGER_CYCLE_OFFSET, cycle,
                REGION_MODULE_BASE + TRIGGER_WAIT_OFFSET, waits,
                REGION_MODULE_BASE + LOCAL_PLAYER_OFFSET, local,
                REGION_MODULE_BASE + MULTIPLAYER_FLAG_OFFSET, new byte[]{1},
                REGION_MODULE_BASE + REPLAY_FLAG_OFFSET, new byte[]{1}
        ));
        CaptureContext context = new CaptureContext(memory, REGION_MODULE_BASE, REGION_CONTEXT_ADDRESS);
        FrameStateSample frameState = SampleDefinitions.FRAME.createSampler(context).sample();
        TriggersSample triggerState = SampleDefinitions.TRIGGERS.createSampler(context).sample();

        Assertions.assertEquals(0xFFFFFFFFL, frameState.getRandomState());
        Assertions.assertEquals(0x80000000L, frameState.getRandomTotal());
        Assertions.assertEquals(123, frameState.getSeconds());
        Assertions.assertEquals(42, frameState.getUnitCount());
        Assertions.assertEquals(17, frameState.getOrderTimerCounter());
        Assertions.assertEquals(23, frameState.getSecondaryTimerCounter());
        Assertions.assertEquals(3400, frameState.getUnitCapacity());
        Assertions.assertEquals(0xFFFE, triggerState.getCycle());
        Assertions.assertEquals(-1, triggerState.getLocalPlayer());
        Assertions.assertEquals(2, triggerState.getStormPlayer());
        Assertions.assertEquals(1, triggerState.getMultiplayerFlag());
        Assertions.assertEquals(1, triggerState.getReplayFlag());
        Assertions.assertEquals(0xFF, triggerState.getWaits().getLast().getWaiting());
        Assertions.assertEquals(0xFFFFFFFFL, triggerState.getWaits().getLast().getTimer());

        waits[PLAYER_COUNT - 1] = 0;

        Assertions.assertEquals(0xFF, triggerState.getWaits().getLast().getWaiting());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> triggerState.getWaits().clear());
    }

    @Test
    void frame_and_trigger_samples_round_trip_and_order_waits_by_player() throws IOException {
        FrameStateSample frameState = new FrameStateSample(1, 2, 3, 4, 5, 6, 7);
        List<TriggerWaitState> waits = new ArrayList<>();

        for (int playerIdentifier = PLAYER_COUNT - 1; playerIdentifier >= 0; playerIdentifier--) {
            waits.add(new TriggerWaitState(playerIdentifier, playerIdentifier, 0xFFFFFFFFL - playerIdentifier));
        }

        TriggersSample triggerState = new TriggersSample(30, waits, -1, 2, 1, 0);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.TRIGGERS, SampleDefinitions.FRAME),
                                                    new RecordMetadata.Builder().build(), StorageMode.VALUES)) {
            writer.beginFrame(0, 0, CaptureConsistency.UNVERIFIED);
            writer.append(SampleDefinitions.TRIGGERS, triggerState);
            writer.append(SampleDefinitions.FRAME, frameState);
            writer.endFrame();
            writer.finish(Termination.NORMAL);
        }

        try (RecordReader reader = new RecordReader(new ByteArrayInputStream(output.toByteArray()))) {
            Frame frame = reader.readNextFrame();

            Assertions.assertNotNull(frame);
            Assertions.assertEquals(7, frame.getSample(FrameStateSample.class).getUnitCapacity());

            TriggersSample decoded = frame.getSample(TriggersSample.class);

            for (int playerIdentifier = 0; playerIdentifier < PLAYER_COUNT; playerIdentifier++) {
                Assertions.assertEquals(playerIdentifier, decoded.getWaits().get(playerIdentifier).getPlayerIdentifier());
                Assertions.assertEquals(0xFFFFFFFFL - playerIdentifier, decoded.getWaits().get(playerIdentifier).getTimer());
            }

            Assertions.assertNull(reader.readNextFrame());
            Assertions.assertNotNull(reader.getSummary());
        }
    }

    @Test
    void frame_and_trigger_samples_reject_invalid_counters_waits_and_payload_bounds() {
        Assertions.assertThrows(IOException.class,
                                () -> SampleDefinitions.FRAME.getEncodedSize(new FrameStateSample(0x100000000L, 0, 0, 0, 0, 0, 0)));
        Assertions.assertThrows(IOException.class, () -> SampleDefinitions.FRAME.getReader().read(ByteBuffer.allocate(FRAME_PAYLOAD_BYTES - 1)));
        Assertions.assertThrows(IOException.class,
                                () -> SampleDefinitions.TRIGGERS.getReader().read(ByteBuffer.allocate(TRIGGERS_PAYLOAD_BYTES - 1)));

        List<TriggerWaitState> duplicateWaits = new ArrayList<>();

        for (int playerIdentifier = 0; playerIdentifier < PLAYER_COUNT; playerIdentifier++) {
            duplicateWaits.add(new TriggerWaitState(0, 0, 0));
        }

        Assertions.assertThrows(IOException.class,
                                () -> SampleDefinitions.TRIGGERS.getEncodedSize(new TriggersSample(0, duplicateWaits, 0, 0, 0, 0)));
        Assertions.assertThrows(IOException.class,
                                () -> SampleDefinitions.TRIGGERS.getEncodedSize(new TriggersSample(0x10000, List.of(), 0, 0, 0, 0)));
        Assertions.assertThrows(IOException.class,
                                () -> SampleDefinitions.FRAME.getWriter().write(ByteBuffer.allocate(FRAME_PAYLOAD_BYTES).asReadOnlyBuffer(),
                                                                                new FrameStateSample(0, 0, 0, 0, 0, 0, 0)));
    }
}
