package org.fogofwarlabs.screcorder.format.recording;

import org.fogofwarlabs.screcorder.format.recording.metadata.RecordMetadata;
import org.fogofwarlabs.screcorder.format.recording.observation.CaptureConsistency;
import org.fogofwarlabs.screcorder.format.recording.observation.StorageMode;
import org.fogofwarlabs.screcorder.format.recording.summary.Termination;
import org.fogofwarlabs.screcorder.sample.SampleDefinitions;
import org.fogofwarlabs.screcorder.sample.random.RandomSample;
import org.fogofwarlabs.screcorder.sample.unit.UnitState;
import org.fogofwarlabs.screcorder.sample.unit.UnitsSample;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * Checks that the sample digester reproduces the digests a digest recording stores and follows the documented digest input.
 */
final class SampleDigesterTest {
    @Test
    void digest_should_equal_the_digest_stored_by_a_digest_recording() throws Exception {
        RandomSample random = new RandomSample(0x12345678L, 42, 0);
        UnitsSample units = new UnitsSample(List.of(new UnitState(3, 1, 7, 0, 100, 200, 25600, 51200, 40 << 8, 0, 0,
                                                                  2, 0, 9, 0, 0, 0, 23, 0, 0, 0, 0, 0,
                                                                  UnitState.UNION_UNIT, 12, 0, 0)));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RecordMetadata metadata = new RecordMetadata.Builder().setDigestEnabled(true).build();

        try (RecordWriter writer = new RecordWriter(output, List.of(SampleDefinitions.UNITS, SampleDefinitions.RANDOM),
                                                    metadata, StorageMode.DIGEST)) {
            writer.beginFrame(0, CaptureConsistency.MARKER_STABLE);
            writer.append(SampleDefinitions.UNITS, units);
            writer.append(SampleDefinitions.RANDOM, random);
            writer.endFrame();
            writer.finish(Termination.NORMAL);
        }

        SampleDigester digester = new SampleDigester();

        try (RecordReader reader = new RecordReader(new ByteArrayInputStream(output.toByteArray()))) {
            Frame frame = reader.readNextFrame();

            Assertions.assertNotNull(frame);
            Assertions.assertArrayEquals(frame.getSampleObservation(RandomSample.class).getDigest(),
                                         digester.digest(SampleDefinitions.RANDOM, random));
            Assertions.assertArrayEquals(frame.getSampleObservation(UnitsSample.class).getDigest(), digester.digest(SampleDefinitions.UNITS, units));
        }
    }

    @Test
    void digest_should_follow_the_documented_input() throws Exception {
        RandomSample random = new RandomSample(7, 8, 0);
        MessageDigest expected = MessageDigest.getInstance("SHA-256");

        expected.update("SCREC-STATE".getBytes(StandardCharsets.US_ASCII));
        // Format 0.1, the random sample identifier (3), then its canonical VALUES payload (state, total, gate).
        expected.update(HexFormat.of().parseHex("0000" + "0100" + "03000000" + "07000000" + "08000000" + "00000000"));

        Assertions.assertEquals(3, SampleDefinitions.RANDOM.getIdentifier());
        Assertions.assertArrayEquals(expected.digest(), new SampleDigester().digest(SampleDefinitions.RANDOM, random));
    }
}
