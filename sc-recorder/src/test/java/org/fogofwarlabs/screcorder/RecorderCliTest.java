package org.fogofwarlabs.screcorder;

import org.fogofwarlabs.screcorder.format.recording.RecordWriter;
import org.fogofwarlabs.screcorder.format.recording.metadata.RecordMetadata;
import org.fogofwarlabs.screcorder.format.recording.observation.CaptureConsistency;
import org.fogofwarlabs.screcorder.format.recording.observation.StorageMode;
import org.fogofwarlabs.screcorder.format.recording.summary.Termination;
import org.fogofwarlabs.screcorder.sample.SampleDefinitions;
import org.fogofwarlabs.screcorder.sample.random.RandomSample;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Checks that the command line rejects malformed arguments before opening a target and that the {@code validate} command exits with the status of
 * complete, partial and invalid recordings.
 */
final class RecorderCliTest {
    private static final int FIRST_EXPECTED_FRAME = 0;
    private static final int LAST_EXPECTED_FRAME = 2;
    private static final int RANDOM_GATE = 0;
    private static final int TRUNCATED_BYTE_COUNT = 5;
    private static final int CORRUPTION_MASK = 0x5A;

    private static @NotNull Path writeRecord(@NotNull Path directory, @NotNull String name, long @NotNull ... frames) throws IOException {
        Path path = directory.resolve(name);
        RecordMetadata metadata = new RecordMetadata.Builder().setExpectedFrames(FIRST_EXPECTED_FRAME, LAST_EXPECTED_FRAME)
                                                              .setCoverageRequirements(RecordMetadata.REQUIRE_NO_GAPS |
                                                                                       RecordMetadata.REQUIRE_EXPECTED_BOUNDARIES).build();

        try (RecordWriter writer = RecordWriter.open(path, List.of(SampleDefinitions.RANDOM), metadata, StorageMode.DIGEST)) {
            for (long frame : frames) {
                writer.beginFrame(frame, CaptureConsistency.MARKER_STABLE);
                writer.append(SampleDefinitions.RANDOM, new RandomSample(frame, frame, RANDOM_GATE));
                writer.endFrame();
            }

            writer.finish(Termination.NORMAL);
        }

        return path;
    }

    private static @NotNull IllegalArgumentException assertRunRejects(@NotNull String @NotNull ... arguments) {
        return Assertions.assertThrows(IllegalArgumentException.class, () -> RecorderCli.run(arguments));
    }


    @Test
    void run_should_exit_0_when_validating_a_valid_complete_file(@TempDir @NotNull Path directory) throws IOException {
        Path complete = writeRecord(directory, "complete.screc", 0, 1, 2);

        Assertions.assertEquals(0, RecorderCli.run(new String[]{"validate", complete.toString()}));
    }

    @Test
    void run_should_exit_3_when_validating_a_valid_partial_file(@TempDir @NotNull Path directory) throws IOException {
        Path partial = writeRecord(directory, "partial.screc", 0, 2);

        Assertions.assertEquals(3, RecorderCli.run(new String[]{"validate", partial.toString()}));
    }

    @Test
    void run_should_exit_1_when_validating_an_invalid_file(@TempDir @NotNull Path directory) throws IOException {
        Path complete = writeRecord(directory, "source.screc", 0, 1, 2);
        byte[] bytes = Files.readAllBytes(complete);
        Path truncated = directory.resolve("truncated.screc");

        Files.write(truncated, Arrays.copyOf(bytes, bytes.length - TRUNCATED_BYTE_COUNT));

        Path corrupt = directory.resolve("corrupt.screc");

        bytes[bytes.length / 2] ^= CORRUPTION_MASK;
        Files.write(corrupt, bytes);

        Assertions.assertEquals(1, RecorderCli.run(new String[]{"validate", truncated.toString()}));
        Assertions.assertEquals(1, RecorderCli.run(new String[]{"validate", corrupt.toString()}));
        Assertions.assertEquals(1, RecorderCli.run(new String[]{"validate", directory.resolve("missing.screc").toString()}));
    }

    @Test
    void run_should_validate_samples_and_storage_before_opening_the_target() {
        Assertions.assertTrue(assertRunRejects("record").getMessage().contains("requires at least one --sample"));
        assertRunRejects("validate");
        assertRunRejects("record", "--sample", "pipeline");
        assertRunRejects("record", "--sample", "units", "--sample", "units");
        assertRunRejects("record", "--sample", "players", "--storage", "other");
        assertRunRejects("record", "--profile", "players");
        assertRunRejects("record", "--sample", "players", "--timeout-seconds", "0");
        assertRunRejects("record", "--preset", "other");
        assertRunRejects("record", "--preset", "test", "--playback-speed", "X16");
        assertRunRejects("record", "--preset", "test", "--sound", "loud");
        assertRunRejects("record", "--preset", "test", "--coverage", "loose");
        assertRunRejects("record", "--preset", "test", "--frames", "10");
        assertRunRejects("record", "--preset", "test", "--stride", "0");
    }
}
