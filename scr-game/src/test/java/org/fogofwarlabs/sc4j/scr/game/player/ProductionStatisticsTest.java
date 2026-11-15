package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Checks that the replay frame advances and the records of the production statistics match every captured native sample and float value.
 */
final class ProductionStatisticsTest {
    /**
     * The replay frame durations of the captured advance cases.
     */
    private static final int @NotNull [] DURATIONS = {42, 48};
    /**
     * The frame of the first sample.
     */
    private static final int INITIAL_SAMPLE_FRAME = -2;
    /**
     * The number of replay frame advances per duration.
     */
    private static final int STEP_COUNT = 170;
    /**
     * The sample field of the frame.
     */
    private static final int FRAME_FIELD = 0;
    /**
     * The sample field of the elapsed time.
     */
    private static final int ELAPSED_TIME_FIELD = 1;
    /**
     * The sample field of the first value.
     */
    private static final int FIRST_VALUE_FIELD = 2;
    /**
     * The byte size of a snapshot: the sample values followed by the accumulators.
     */
    private static final int SNAPSHOT_SIZE = ProductionStatistics.VALUE_COUNT * Integer.BYTES + ProductionStatistics.VALUE_COUNT * Float.BYTES;
    /**
     * The DWORD that fills every initial sample value.
     */
    private static final int FILL_SAMPLE = 0x55555555;
    /**
     * The float that fills every initial accumulator.
     */
    private static final float FILL_ACCUMULATOR = -3.5f;
    /**
     * The recorded owners: the first player, the neutral player, the first index past the player slots and an unsigned maximum.
     */
    private static final int @NotNull [] OWNERS = {0, 11, 12, -1};
    /**
     * The number of recorded categories.
     */
    private static final int CATEGORY_COUNT = 6;
    /**
     * The recorded changes.
     */
    private static final int @NotNull [] CHANGES = {0, 1, Integer.MAX_VALUE, Integer.MIN_VALUE, -1};
    /**
     * The number of recorded cases: both sample states, every owner, category and change.
     */
    private static final int CASE_COUNT = 240;
    /**
     * The owner of the record before the replay frame advances.
     */
    private static final int RECORDED_OWNER = 0;
    /**
     * The category of the record before the replay frame advances.
     */
    private static final int RECORDED_CATEGORY = 0;
    /**
     * The change of the record before the replay frame advances, which the first sample value keeps.
     */
    private static final int RECORDED_CHANGE = 1;
    /**
     * The index of the first sample.
     */
    private static final int FIRST_SAMPLE = 0;
    /**
     * The index of the first sample value and accumulator, the value of {@link #RECORDED_OWNER} and {@link #RECORDED_CATEGORY}.
     */
    private static final int FIRST_VALUE = 0;
    /**
     * The SHA-256 digest of the replay frame advance capture.
     */
    private static final @NotNull String ADVANCE_CAPTURE_HASH = "57fc8ae5f38c38d5339bd6fbf19a7194b5e4c17261b22384a25d85baf4de7c40";
    /**
     * The SHA-256 digest of the snapshots of every recorded case.
     */
    private static final @NotNull String RECORD_CASES_HASH = "86fc3c3dee5a94e949e7fa871db6d35289ccc03fab749dbcc98042f935edad72";
    /**
     * The digest algorithm of the capture and snapshot digests.
     */
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";

    @Test
    void matches_native_advance_replay_frame_cases() throws IOException, NoSuchAlgorithmException {
        byte[] fixture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_ACCOUNTING_ADVANCE);

        Assertions.assertEquals(ADVANCE_CAPTURE_HASH, HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(fixture)));

        ByteBuffer cases = ByteBuffer.wrap(fixture).order(ByteOrder.LITTLE_ENDIAN);

        for (int duration : DURATIONS) {
            ProductionStatistics statistics = new ProductionStatistics(false,
                                                                       new int[ProductionStatistics.VALUE_COUNT],
                                                                       new float[ProductionStatistics.VALUE_COUNT]);

            statistics.initializeSample(INITIAL_SAMPLE_FRAME);
            statistics.record(RECORDED_OWNER, RECORDED_CATEGORY, RECORDED_CHANGE);

            for (int step = 1; step <= STEP_COUNT; step++) {
                Assertions.assertEquals(duration, cases.getInt());
                Assertions.assertEquals(step, cases.getInt());

                statistics.advanceReplayFrame(duration);

                Assertions.assertEquals(cases.getInt(), statistics.getSampleField(statistics.getSampleCount() - 1, FRAME_FIELD));
                Assertions.assertEquals(cases.getInt(), statistics.getSampleField(statistics.getSampleCount() - 1, ELAPSED_TIME_FIELD));
                Assertions.assertEquals(cases.getInt(), statistics.getSampleCount());
                Assertions.assertEquals(cases.getInt(), statistics.getSampleField(FIRST_SAMPLE, FRAME_FIELD));
                Assertions.assertEquals(cases.getInt(), statistics.getSampleField(FIRST_SAMPLE, ELAPSED_TIME_FIELD));
                Assertions.assertEquals(cases.getInt(), Float.floatToRawIntBits(statistics.getAccumulatedValue(FIRST_VALUE)));
                Assertions.assertEquals(RECORDED_CHANGE, statistics.getSampleValue(FIRST_VALUE));
                Assertions.assertEquals(RECORDED_CHANGE, statistics.getSampleField(FIRST_SAMPLE, FIRST_VALUE_FIELD));
            }
        }

        Assertions.assertEquals(0, cases.remaining());
    }

    @Test
    void record_should_convert_unsigned_dwords_to_float_and_write_category_one_secondaries() throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        ByteBuffer snapshot = ByteBuffer.allocate(SNAPSHOT_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        int[] initialSamples = new int[ProductionStatistics.VALUE_COUNT];
        float[] initialAccumulators = new float[ProductionStatistics.VALUE_COUNT];

        Arrays.fill(initialSamples, FILL_SAMPLE);
        Arrays.fill(initialAccumulators, FILL_ACCUMULATOR);

        int caseCount = 0;

        for (boolean samplePresent : new boolean[]{false, true}) {
            for (int owner : OWNERS) {
                for (int category = 0; category < CATEGORY_COUNT; category++) {
                    for (int change : CHANGES) {
                        ProductionStatistics statistics = new ProductionStatistics(samplePresent, initialSamples, initialAccumulators);

                        statistics.record(owner, category, change);

                        snapshot.clear();

                        for (int index = 0; index < ProductionStatistics.VALUE_COUNT; index++) {
                            snapshot.putInt(statistics.getSampleValue(index));
                        }

                        for (int index = 0; index < ProductionStatistics.VALUE_COUNT; index++) {
                            snapshot.putInt(Float.floatToRawIntBits(statistics.getAccumulatedValue(index)));
                        }

                        digest.update(snapshot.array());

                        caseCount++;
                    }
                }
            }
        }

        Assertions.assertEquals(CASE_COUNT, caseCount);
        Assertions.assertEquals(RECORD_CASES_HASH, HexFormat.of().formatHex(digest.digest()));
    }
}
