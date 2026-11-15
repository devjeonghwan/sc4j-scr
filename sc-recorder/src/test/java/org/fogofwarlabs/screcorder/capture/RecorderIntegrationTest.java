package org.fogofwarlabs.screcorder.capture;

import org.fogofwarlabs.screcorder.capture.starcraft.StarCraftLayout;
import org.fogofwarlabs.screcorder.capture.windows.WindowsProcess;
import org.fogofwarlabs.screcorder.capture.windows.WindowsWaitableTimer;
import org.fogofwarlabs.screcorder.format.recording.Frame;
import org.fogofwarlabs.screcorder.format.recording.RecordReader;
import org.fogofwarlabs.screcorder.format.recording.metadata.RecordMetadata;
import org.fogofwarlabs.screcorder.format.recording.observation.StorageMode;
import org.fogofwarlabs.screcorder.format.recording.summary.RecordSummary;
import org.fogofwarlabs.screcorder.format.recording.summary.Termination;
import org.fogofwarlabs.screcorder.sample.SampleDefinitions;
import org.fogofwarlabs.screcorder.sample.player.PlayersSample;
import org.fogofwarlabs.screcorder.sample.random.RandomSample;
import org.fogofwarlabs.screcorder.sample.unit.UnitsSample;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Checks that the recorder captures typed frames from a mock game process and rejects a replay that does not match it.
 */
@EnabledOnOs(OS.WINDOWS)
final class RecorderIntegrationTest {
    private static final int MOCK_START_TIMEOUT_SECONDS = 5;
    private static final int MOCK_POLL_INTERVAL_MILLISECONDS = 5;
    private static final int MODULE_BASE_RADIX = 16;
    private static final int TIMEOUT_SECONDS = 20;
    private static final int PROCESS_EXIT_TIMEOUT_SECONDS = 5;

    @Test
    void recorder_captures_typed_frames_from_a_mock_process_and_rejects_a_wrong_replay(@TempDir @NotNull Path directory) throws Exception {
        String mockPath = System.getProperty("sc.recorder.mock");

        Assumptions.assumeTrue(mockPath != null && !mockPath.isBlank());

        Path baseFile = directory.resolve("base.txt");
        Process mock = new ProcessBuilder(mockPath, baseFile.toString(), "120", "42", "replay")
                .redirectErrorStream(true).redirectOutput(directory.resolve("mock.log").toFile()).start();

        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(MOCK_START_TIMEOUT_SECONDS);

            while (!Files.exists(baseFile) && System.nanoTime() < deadline && mock.isAlive()) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(MOCK_POLL_INTERVAL_MILLISECONDS));
            }

            Assertions.assertTrue(Files.exists(baseFile), "Mock did not publish its image base");

            long moduleBase = Long.parseUnsignedLong(Files.readString(baseFile).trim(), MODULE_BASE_RADIX);
            Path recording = directory.resolve("capture.screc");
            RecordSummary summary;

            try (WindowsProcess target = WindowsProcess.open(mock.pid()); WindowsWaitableTimer timer = WindowsWaitableTimer.open()) {
                target.checkModule(moduleBase);

                Path rejected = directory.resolve("wrong-replay.screc");
                Recorder mismatch = new Recorder(target, moduleBase, true,
                                                 List.of(SampleDefinitions.PLAYERS, SampleDefinitions.UNITS, SampleDefinitions.RANDOM),
                                                 CaptureOptions.createStrict(), TIMEOUT_SECONDS, new AtomicBoolean(false));
                IOException failure = Assertions.assertThrows(IOException.class, () -> mismatch.record(rejected, new RecordMetadata.Builder(),
                                                                                                       StorageMode.VALUES, timer,
                                                                                                       new byte[StarCraftLayout.REPLAY_HEADER_SIZE]));

                Assertions.assertTrue(failure.getMessage().contains("does not match"));
                Assertions.assertFalse(Files.exists(rejected));

                Recorder recorder = new Recorder(target, moduleBase, true,
                                                 List.of(SampleDefinitions.PLAYERS, SampleDefinitions.UNITS, SampleDefinitions.RANDOM),
                                                 CaptureOptions.createStrict(), TIMEOUT_SECONDS, new AtomicBoolean(false));

                summary = recorder.record(recording, new RecordMetadata.Builder().setCoverageRequirements(RecordMetadata.REQUIRE_NO_GAPS),
                                          StorageMode.VALUES, timer, null);
            }

            Assertions.assertEquals(Termination.NORMAL, summary.getTermination());
            Assertions.assertTrue(summary.getFrameCount() >= 100);
            Assertions.assertEquals(120, summary.getLastFrame());
            Assertions.assertEquals(0, summary.getUnavailableSampleCount());
            Assertions.assertTrue(summary.getMissingFrameCount() >= 3);

            try (RecordReader reader = RecordReader.open(recording)) {
                Frame frame;
                Frame lastFrame = null;

                while ((frame = reader.readNextFrame()) != null) {
                    lastFrame = frame;
                }

                Assertions.assertNotNull(lastFrame);
                Assertions.assertEquals(120, lastFrame.getFrameNumber());
                Assertions.assertEquals(8, lastFrame.getSample(PlayersSample.class).getPlayers().size());
                Assertions.assertEquals(52, lastFrame.getSample(UnitsSample.class).getUnits().size());
                Assertions.assertEquals(170, lastFrame.getSample(PlayersSample.class).getPlayers().getFirst().getMinerals());
                Assertions.assertEquals(56320, lastFrame.getSample(UnitsSample.class).getUnits().getFirst().getHitPoints());
                Assertions.assertEquals(360, lastFrame.getSample(RandomSample.class).getTotal());

                RecordSummary recordedSummary = reader.getSummary();

                Assertions.assertNotNull(recordedSummary);
                Assertions.assertEquals(Termination.NORMAL, recordedSummary.getTermination());
            }

            Assertions.assertTrue(mock.waitFor(PROCESS_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            if (mock.isAlive()) {
                mock.destroyForcibly().waitFor(PROCESS_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        }
    }
}
