package org.fogofwarlabs.screcorder.capture;

import org.fogofwarlabs.screcorder.binary.LittleEndianUtils;
import org.fogofwarlabs.screcorder.capture.starcraft.StarCraftLayout;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.format.recording.Frame;
import org.fogofwarlabs.screcorder.format.recording.RecordReader;
import org.fogofwarlabs.screcorder.format.recording.RecordWriter;
import org.fogofwarlabs.screcorder.format.recording.metadata.RecordMetadata;
import org.fogofwarlabs.screcorder.format.recording.observation.CaptureConsistency;
import org.fogofwarlabs.screcorder.format.recording.observation.SampleStatus;
import org.fogofwarlabs.screcorder.format.recording.observation.StorageMode;
import org.fogofwarlabs.screcorder.format.recording.summary.RecordSummary;
import org.fogofwarlabs.screcorder.format.recording.summary.Termination;
import org.fogofwarlabs.screcorder.sample.SampleDefinitions;
import org.fogofwarlabs.screcorder.sample.player.PlayersSample;
import org.fogofwarlabs.screcorder.sample.unit.UnitsSample;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Checks the recorder's capture loop, frame consistency checks and thinning against an in-memory game.
 */
final class RecordingProducerTest {
    private static final long MODULE_BASE = 0x100000;
    private static final int MEMORY_SIZE = 0x1500000;
    private static final int CONTEXT_OFFSET = 0x1450000;
    private static final int REPLAY_FRAME_COUNT_OFFSET = 1;
    private static final int UNIT_POOL_OFFSET = 0x1470000;
    private static final int UNIT_POOL_SLOT_COUNT = 2;
    private static final int UNIT_HIT_POINTS_OFFSET = 0x10;
    private static final int UNIT_ACTIVE_OFFSET = 0x18;
    private static final int UNIT_POSITION_X_OFFSET = 0x40;
    private static final int UNIT_ORDER_TIMER_OFFSET = 0xC9;
    private static final int MAP_WIDTH_OFFSET = 0xE4;
    private static final int MAP_HEIGHT_OFFSET = 0xE6;
    private static final int TILES_POINTER_OFFSET = 0xE7E478;
    private static final int TILES_OFFSET = 0x1480000;
    private static final int THREAD_DATA_SLOT_OFFSET = 0xE09508;
    private static final int EXCEPTION_ACTIONS_OFFSET = 0xC38280;
    private static final int THREAD_ENVIRONMENT_OFFSET = 0x1490000;
    private static final int FIBER_DATA_POINTER_OFFSET = 0x17C8;
    private static final int FIBER_DATA_OFFSET = 0x14A0000;
    private static final int FIBER_FIRST_CHUNK_POINTER_OFFSET = 0x10;
    private static final int FIBER_CHUNK_OFFSET = 0x14B0000;
    private static final int FIBER_FIRST_SLOT_OFFSET = 0x8;
    private static final int THREAD_DATA_OFFSET = 0x14C0000;

    private static final int REPLAY_FRAME_COUNT = 3;
    private static final int LAST_REPLAY_FRAME = 2;
    private static final int TRIGGER_CYCLE_START = 30;
    private static final int INITIAL_HIT_POINTS = 1024;
    private static final int INITIAL_ORDER_TIMER = 9;
    private static final int INITIAL_POSITION_X = 12;
    private static final int INITIAL_MINERALS = 50;
    private static final int SKIPPING_FRAME_STEP = 2;
    private static final int COUNTER_READS_PER_PASS = 2;

    private static final long TEST_POLLING_NANOSECONDS = 1_000_000;
    private static final @NotNull CaptureOptions TEST_OPTIONS = new CaptureOptions(TEST_POLLING_NANOSECONDS,
                                                                                   CaptureSchedule.getEveryFrame(),
                                                                                   false,
                                                                                   0);
    private static final int TIMEOUT_SECONDS = 10;
    private static final int FIRST_EXPECTED_FRAME = 0;
    private static final int LAST_EXPECTED_FRAME = 2;
    private static final int TORN_SPINS_PER_FRAME = 200;
    private static final int TORN_WARM_UP_PASSES = 1;
    private static final int THINNED_DENSE_PREFIX = 1;
    private static final int THINNED_STRIDE = 2;

    private static final class GameScenario implements RecordingTarget, CaptureTimer {
        private final byte @NotNull [] memory;
        private final byte @NotNull [] scalar;
        private final boolean skipFrame;

        private boolean failPlayers;
        private long bytesRead;
        private int frame;
        private int lostFrame;
        private int tornFrame;
        private int spinsPerFrame;
        private int counterReads;
        private int spins;

        private GameScenario(boolean skipFrame) {
            this.memory    = new byte[MEMORY_SIZE];
            this.scalar    = new byte[Long.BYTES];
            this.skipFrame = skipFrame;

            this.failPlayers   = false;
            this.bytesRead     = 0;
            this.frame         = 0;
            this.lostFrame     = -1;
            this.tornFrame     = -1;
            this.spinsPerFrame = 0;
            this.counterReads  = 0;
            this.spins         = 0;

            this.put(StarCraftLayout.CONTEXT_POINTER, (MODULE_BASE + CONTEXT_OFFSET) ^ StarCraftLayout.CONTEXT_KEY, Long.BYTES);
            this.put(StarCraftLayout.REPLAY_FLAG, 1, Integer.BYTES);
            this.put(StarCraftLayout.REPLAY_HEADER + REPLAY_FRAME_COUNT_OFFSET, REPLAY_FRAME_COUNT, Integer.BYTES);
            this.put(StarCraftLayout.UNIT_POOL_POINTER, MODULE_BASE + UNIT_POOL_OFFSET, Long.BYTES);
            this.put(StarCraftLayout.UNIT_POOL_SLOTS, UNIT_POOL_SLOT_COUNT, Long.BYTES);
            this.put(UNIT_POOL_OFFSET + UNIT_ACTIVE_OFFSET, 1, Long.BYTES);
            this.put(UNIT_POOL_OFFSET + StarCraftLayout.UNIT_STRIDE + UNIT_ACTIVE_OFFSET, 1, Long.BYTES);
            this.put(UNIT_POOL_OFFSET + UNIT_HIT_POINTS_OFFSET, INITIAL_HIT_POINTS, Integer.BYTES);
            this.put(UNIT_POOL_OFFSET + UNIT_ORDER_TIMER_OFFSET, INITIAL_ORDER_TIMER, Byte.BYTES);
            this.put(CONTEXT_OFFSET, INITIAL_MINERALS, Integer.BYTES);
            this.put(CONTEXT_OFFSET + MAP_WIDTH_OFFSET, 1, Short.BYTES);
            this.put(CONTEXT_OFFSET + MAP_HEIGHT_OFFSET, 1, Short.BYTES);
            this.put(TILES_POINTER_OFFSET, MODULE_BASE + TILES_OFFSET, Long.BYTES);
            this.put(THREAD_DATA_SLOT_OFFSET, 0, Integer.BYTES);
            this.put(THREAD_ENVIRONMENT_OFFSET + FIBER_DATA_POINTER_OFFSET, MODULE_BASE + FIBER_DATA_OFFSET, Long.BYTES);
            this.put(FIBER_DATA_OFFSET + FIBER_FIRST_CHUNK_POINTER_OFFSET, MODULE_BASE + FIBER_CHUNK_OFFSET, Long.BYTES);
            this.put(FIBER_CHUNK_OFFSET + FIBER_FIRST_SLOT_OFFSET, MODULE_BASE + THREAD_DATA_OFFSET, Long.BYTES);
            this.put(THREAD_DATA_OFFSET, MODULE_BASE + EXCEPTION_ACTIONS_OFFSET, Long.BYTES);
        }

        /**
         * @return the total number of bytes read from this target
         */
        @Override
        public long getBytesRead() {
            return this.bytesRead;
        }

        /**
         * @return {@code true} if the target is running, {@code false} otherwise
         */
        @Override
        public boolean isAlive() {
            return true;
        }


        /**
         * Reads an unsigned little-endian integer without allocating per read.
         *
         * @param address the target address
         * @param size    the integer width in bytes
         * @return the integer bits
         * @throws IOException if the integer cannot be read
         */
        @Override
        public long readInteger(long address, int size) throws IOException {
            this.read(address, this.scalar, 0, size);

            return LittleEndianUtils.readUnsigned(this.scalar, 0, size);
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
            long relative = address - MODULE_BASE;

            if (this.failPlayers && relative == CONTEXT_OFFSET) {
                throw Exceptions.newIOException("Simulated player read failure");
            }

            if (relative < 0 || relative > this.memory.length - length) {
                throw Exceptions.newIOException("Unmapped test address {0}", address);
            }

            if (relative == CONTEXT_OFFSET + StarCraftLayout.FRAME_OFFSET && this.frame == this.lostFrame &&
                ++this.counterReads == COUNTER_READS_PER_PASS) {
                // The second counter read of the frame closes its sampling pass: the game advanced meanwhile.
                this.advance();
            }

            if (relative == StarCraftLayout.RANDOM_BLOCK && this.frame == this.tornFrame) {
                this.memory[(int) relative]++;
            }

            System.arraycopy(this.memory, (int) relative, destination, offset, length);
            this.bytesRead += length;
        }

        /**
         * Lists the thread environment block addresses of the target's threads: the one thread of the in-memory game.
         *
         * @return the immutable addresses in thread creation order, the initial thread first
         */
        @Override
        public @NotNull List<Long> readThreadEnvironmentAddresses() {
            return List.of(MODULE_BASE + THREAD_ENVIRONMENT_OFFSET);
        }

        /**
         * Waits for the requested duration.
         *
         * @param nanoseconds the duration, with {@code 0} requesting no wait
         */
        @Override
        public void await(long nanoseconds) {
            if (nanoseconds == 0 && (this.spinsPerFrame == 0 || ++this.spins % this.spinsPerFrame != 0)) {
                return;
            }

            this.advance();
        }

        private void advance() {
            this.counterReads = 0;
            this.frame += this.skipFrame && this.frame == 0 ? SKIPPING_FRAME_STEP : 1;

            this.put(CONTEXT_OFFSET + StarCraftLayout.FRAME_OFFSET, this.frame, Integer.BYTES);
            // The trigger pass of every frame changes the cycle WORD; the recorder waits for it.
            this.put(StarCraftLayout.TRIGGER_CYCLE, TRIGGER_CYCLE_START - this.frame, Short.BYTES);
            this.put(CONTEXT_OFFSET, INITIAL_MINERALS + this.frame, Integer.BYTES);
            this.put(UNIT_POOL_OFFSET + UNIT_POSITION_X_OFFSET, INITIAL_POSITION_X + this.frame, Short.BYTES);
            this.put(UNIT_POOL_OFFSET + UNIT_ORDER_TIMER_OFFSET, INITIAL_ORDER_TIMER - this.frame, Byte.BYTES);
            this.put(UNIT_POOL_OFFSET + StarCraftLayout.UNIT_STRIDE + UNIT_ACTIVE_OFFSET, 0, Long.BYTES);

            if (this.frame > LAST_REPLAY_FRAME) {
                this.put(StarCraftLayout.REPLAY_FLAG, 0, Integer.BYTES);
            }
        }

        private void put(long offset, long value, int size) {
            for (int index = 0; index < size; index++) {
                this.memory[Math.toIntExact(offset) + index] = (byte) (value >>> (index * Byte.SIZE));
            }
        }
    }

    private static @NotNull List<Long> readFrames(@NotNull ByteArrayOutputStream output) throws IOException {
        List<Long> frames = new ArrayList<>();

        try (RecordReader reader = new RecordReader(new ByteArrayInputStream(output.toByteArray()))) {
            Frame frame;

            while ((frame = reader.readNextFrame()) != null) {
                frames.add(frame.getFrameNumber());
            }
        }

        return frames;
    }


    @Test
    void record_should_capture_typed_frames_with_independent_snapshots() throws IOException {
        GameScenario target = new GameScenario(false);
        RecordingProducer producer = new Recorder(target, MODULE_BASE, true,
                                                  SampleDefinitions.getAll(), TEST_OPTIONS, TIMEOUT_SECONDS,
                                                  new AtomicBoolean(false));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RecordMetadata metadata = new RecordMetadata.Builder().setExpectedFrames(FIRST_EXPECTED_FRAME, LAST_EXPECTED_FRAME)
                                                              .setCoverageRequirements(RecordMetadata.REQUIRE_NO_GAPS |
                                                                                       RecordMetadata.REQUIRE_AVAILABLE_SAMPLES |
                                                                                       RecordMetadata.REQUIRE_MARKER_STABLE |
                                                                                       RecordMetadata.REQUIRE_EXPECTED_BOUNDARIES).build();

        try (RecordWriter writer = new RecordWriter(output, SampleDefinitions.getAll(), metadata, StorageMode.VALUES)) {
            RecordSummary summary = producer.record(writer, target, null);

            Assertions.assertEquals(Termination.NORMAL, summary.getTermination());
            Assertions.assertEquals(REPLAY_FRAME_COUNT, summary.getFrameCount());
            Assertions.assertEquals(0, summary.getMissingFrameCount());
            Assertions.assertEquals(0, summary.getUnavailableSampleCount());
            Assertions.assertTrue(summary.isRequirementsSatisfied());
        }

        try (RecordReader reader = new RecordReader(new ByteArrayInputStream(output.toByteArray()))) {
            Frame frame;
            PlayersSample firstPlayers = null;
            long expectedFrame = 0;

            while ((frame = reader.readNextFrame()) != null) {
                Assertions.assertEquals(expectedFrame++, frame.getFrameNumber());
                Assertions.assertEquals(CaptureConsistency.MARKER_STABLE, frame.getConsistency());
                Assertions.assertNotNull(frame.getSample(PlayersSample.class));
                Assertions.assertNotNull(frame.getSample(UnitsSample.class));

                if (firstPlayers == null) {
                    firstPlayers = frame.getSample(PlayersSample.class);
                }
            }

            Assertions.assertEquals(REPLAY_FRAME_COUNT, expectedFrame);
            Assertions.assertNotNull(firstPlayers);
            Assertions.assertEquals(INITIAL_MINERALS, firstPlayers.getPlayers().getFirst().getMinerals());

            RecordSummary summary = reader.getSummary();

            Assertions.assertNotNull(summary);
            Assertions.assertEquals(Termination.NORMAL, summary.getTermination());
        }
    }

    @Test
    void record_should_report_missing_frames_in_the_binary_summary() throws IOException {
        GameScenario target = new GameScenario(true);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RecordMetadata metadata = new RecordMetadata.Builder().setExpectedFrames(FIRST_EXPECTED_FRAME, LAST_EXPECTED_FRAME)
                                                              .setCoverageRequirements(RecordMetadata.REQUIRE_NO_GAPS).build();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.RANDOM), metadata, StorageMode.DIGEST)) {
            Recorder recorder = new Recorder(target, MODULE_BASE, true,
                                             List.of(SampleDefinitions.RANDOM), TEST_OPTIONS, TIMEOUT_SECONDS,
                                             new AtomicBoolean(false));
            RecordSummary summary = recorder.record(writer, target, null);

            Assertions.assertEquals(1, summary.getMissingFrameCount());
            Assertions.assertEquals(2, summary.getFrameCount());
        }

        try (RecordReader reader = new RecordReader(new ByteArrayInputStream(output.toByteArray()))) {
            while (reader.readNextFrame() != null) {
            }

            RecordSummary summary = reader.getSummary();

            Assertions.assertNotNull(summary);
            Assertions.assertEquals(1, summary.getMissingFrameCount());
        }
    }

    @Test
    void record_should_reject_a_mismatched_replay_before_capturing_any_frame() throws IOException {
        GameScenario target = new GameScenario(false);
        Recorder recorder = new Recorder(target, MODULE_BASE, true,
                                         List.of(SampleDefinitions.RANDOM), TEST_OPTIONS, TIMEOUT_SECONDS,
                                         new AtomicBoolean(false));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.RANDOM),
                                                    new RecordMetadata.Builder().build(), StorageMode.VALUES)) {
            Assertions.assertThrows(IOException.class, () -> recorder.record(writer, target, new byte[StarCraftLayout.REPLAY_HEADER_SIZE]));
        }

        try (RecordReader reader = new RecordReader(new ByteArrayInputStream(output.toByteArray()))) {
            Assertions.assertThrows(IOException.class, reader::readNextFrame);
        }
    }

    @Test
    void record_should_mark_a_failed_sample_read_without_inventing_values() throws IOException {
        GameScenario target = new GameScenario(false);

        target.failPlayers = true;

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RecordMetadata metadata = new RecordMetadata.Builder().setCoverageRequirements(RecordMetadata.REQUIRE_AVAILABLE_SAMPLES).build();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.PLAYERS, SampleDefinitions.UNITS, SampleDefinitions.RANDOM),
                                                    metadata, StorageMode.VALUES)) {
            Recorder recorder = new Recorder(target, MODULE_BASE, true,
                                             List.of(SampleDefinitions.PLAYERS, SampleDefinitions.UNITS, SampleDefinitions.RANDOM),
                                             TEST_OPTIONS, TIMEOUT_SECONDS, new AtomicBoolean(false));
            RecordSummary summary = recorder.record(writer, target, null);

            Assertions.assertEquals(Termination.NORMAL, summary.getTermination());
            Assertions.assertEquals(3, summary.getUnavailableSampleCount());
            Assertions.assertFalse(summary.isRequirementsSatisfied());
        }

        try (RecordReader reader = new RecordReader(new ByteArrayInputStream(output.toByteArray()))) {
            Frame frame;

            while ((frame = reader.readNextFrame()) != null) {
                Assertions.assertEquals(SampleStatus.READ_FAILED, frame.getSampleObservation(PlayersSample.class).getStatus());

                Frame observedFrame = frame;

                Assertions.assertThrows(IllegalStateException.class, () -> observedFrame.getSample(PlayersSample.class));
                Assertions.assertNotNull(frame.getSample(UnitsSample.class));
            }
        }
    }

    @Test
    void record_should_write_canceled_termination_for_an_interrupted_session() throws IOException {
        GameScenario target = new GameScenario(false);
        AtomicBoolean canceled = new AtomicBoolean(false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.RANDOM),
                                                    new RecordMetadata.Builder().build(), StorageMode.VALUES)) {
            Recorder recorder = new Recorder(target, MODULE_BASE, true, List.of(SampleDefinitions.RANDOM), TEST_OPTIONS, TIMEOUT_SECONDS, canceled);
            RecordSummary summary = recorder.record(writer, nanoseconds -> {
                if (nanoseconds > 0) {
                    canceled.set(true);
                }
            }, null);

            Assertions.assertEquals(Termination.CANCELLED, summary.getTermination());
            Assertions.assertEquals(1, summary.getFrameCount());
        }

        try (RecordReader reader = new RecordReader(new ByteArrayInputStream(output.toByteArray()))) {
            while (reader.readNextFrame() != null) {
            }

            RecordSummary summary = reader.getSummary();

            Assertions.assertNotNull(summary);
            Assertions.assertEquals(Termination.CANCELLED, summary.getTermination());
        }
    }

    @Test
    void record_should_drop_a_frame_whose_counter_changed_while_it_was_sampled() throws IOException {
        GameScenario target = new GameScenario(false);

        target.lostFrame = 1;

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RecordMetadata metadata = new RecordMetadata.Builder().setExpectedFrames(FIRST_EXPECTED_FRAME, LAST_EXPECTED_FRAME).build();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.RANDOM), metadata, StorageMode.DIGEST)) {
            Recorder recorder = new Recorder(target, MODULE_BASE, true,
                                             List.of(SampleDefinitions.RANDOM), TEST_OPTIONS, TIMEOUT_SECONDS,
                                             new AtomicBoolean(false));
            RecordSummary summary = recorder.record(writer, target, null);

            Assertions.assertEquals(1, recorder.getLostFrameCount());
            Assertions.assertEquals(1, summary.getMissingFrameCount());
            Assertions.assertEquals(List.of(0L, 2L), readFrames(output));
        }
    }

    @Test
    void record_should_write_torn_frames_in_strict_mode_and_drop_them_in_tolerant_mode() throws IOException {
        for (boolean tolerant : new boolean[]{false, true}) {
            GameScenario target = new GameScenario(false);

            target.tornFrame     = 1;
            target.spinsPerFrame = TORN_SPINS_PER_FRAME;

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            CaptureOptions options = new CaptureOptions(0, CaptureSchedule.getEveryFrame(), tolerant, TORN_WARM_UP_PASSES);
            RecordMetadata metadata = new RecordMetadata.Builder().setExpectedFrames(FIRST_EXPECTED_FRAME, LAST_EXPECTED_FRAME).build();

            try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.RANDOM), metadata, StorageMode.DIGEST)) {
                Recorder recorder = new Recorder(target, MODULE_BASE, true,
                                                 List.of(SampleDefinitions.RANDOM), options, TIMEOUT_SECONDS,
                                                 new AtomicBoolean(false));
                RecordSummary summary = recorder.record(writer, target, null);

                Assertions.assertEquals(tolerant ? 0 : 1, summary.getTornObservationCount());
                Assertions.assertEquals(tolerant ? 1 : 0, recorder.getDroppedFrameCount());
                Assertions.assertEquals(tolerant ? List.of(0L, 2L) : List.of(0L, 1L, 2L), readFrames(output));
            }
        }
    }

    @Test
    void record_should_record_the_dense_prefix_and_one_frame_per_stride_when_thinned() throws IOException {
        GameScenario target = new GameScenario(false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CaptureSchedule schedule = new CaptureSchedule(0, Long.MAX_VALUE, THINNED_DENSE_PREFIX, THINNED_STRIDE);
        CaptureOptions options = new CaptureOptions(TEST_POLLING_NANOSECONDS, schedule, true, 0);
        RecordMetadata metadata = new RecordMetadata.Builder().setExpectedFrames(FIRST_EXPECTED_FRAME, LAST_EXPECTED_FRAME).build();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.RANDOM), metadata, StorageMode.DIGEST)) {
            Recorder recorder = new Recorder(target, MODULE_BASE, true,
                                             List.of(SampleDefinitions.RANDOM), options, TIMEOUT_SECONDS,
                                             new AtomicBoolean(false));

            recorder.record(writer, target, null);
        }

        // Frame 0 is the dense prefix, frame 2 the first frame of the next stride period (and the final frame).
        Assertions.assertEquals(List.of(0L, 2L), readFrames(output));
    }
}
