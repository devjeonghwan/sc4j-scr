package org.fogofwarlabs.screcorder.capture;

import org.fogofwarlabs.screcorder.RecorderCli;
import org.fogofwarlabs.screcorder.binary.LittleEndianUtils;
import org.fogofwarlabs.screcorder.format.recording.Frame;
import org.fogofwarlabs.screcorder.format.recording.RecordReader;
import org.fogofwarlabs.screcorder.format.recording.metadata.LoadedReplayEvidence;
import org.fogofwarlabs.screcorder.format.recording.observation.CaptureConsistency;
import org.fogofwarlabs.screcorder.format.recording.observation.SampleStatus;
import org.fogofwarlabs.screcorder.format.recording.summary.RecordSummary;
import org.fogofwarlabs.screcorder.format.recording.summary.Termination;
import org.fogofwarlabs.screcorder.format.replay.ReplayHeaderUtils;
import org.fogofwarlabs.screcorder.sample.SampleDefinition;
import org.fogofwarlabs.screcorder.sample.player.PlayersSample;
import org.fogofwarlabs.screcorder.sample.random.RandomSample;
import org.fogofwarlabs.screcorder.sample.unit.UnitsSample;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Checks that the {@code record} command launches the paired replay in the real game, captures its frames and closes the game.
 */
@EnabledOnOs(OS.WINDOWS)
final class ReplayLaunchIntegrationTest {
    private static final int REPLAY_FRAME_COUNT_OFFSET = 1;
    private static final int COMPLETE_EXIT_STATUS = 0;
    private static final int PARTIAL_EXIT_STATUS = 3;

    private static @NotNull String hashFile(@NotNull Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }

        return HexFormat.of().formatHex(digest.digest());
    }


    @Test
    void record_command_launches_the_paired_replay_captures_frames_and_closes_the_owned_game() throws Exception {
        String gamePath = System.getProperty("sc.recorder.game", "");
        String replayPath = System.getProperty("sc.recorder.replay", "");

        Assumptions.assumeTrue(!gamePath.isBlank() && !replayPath.isBlank(), "Real-game launch requires explicit executable and replay properties");

        Path replay = Path.of(replayPath);
        byte[] header = ReplayHeaderUtils.readHeader(replay);
        int expectedLastFrame = Math.toIntExact(LittleEndianUtils.readUnsigned32(header, REPLAY_FRAME_COUNT_OFFSET));
        Path buildDirectory = Path.of("build", "live-integration");

        Files.createDirectories(buildDirectory);

        Path output = Files.createTempDirectory(buildDirectory, "recordings with spaces ").toAbsolutePath();
        int status = RecorderCli.run(new String[]{"record", "--replay", replay.toString(), "--executable", gamePath,
                                                  "--sample", "players", "--sample", "units", "--sample", "random",
                                                  "--sample", "frame", "--sample", "random-counters", "--sample", "locations",
                                                  "--sample", "lists", "--sample", "context", "--sample", "triggers",
                                                  "--sample", "eud", "--sample", "eud-hosts", "--sample", "ai",
                                                  "--sample", "camera", "--sample", "sync",
                                                  "--out", output.toString(), "--timeout-seconds", "120"});

        Assertions.assertTrue(status == COMPLETE_EXIT_STATUS || status == PARTIAL_EXIT_STATUS, "Replay must terminate normally");

        List<Path> sessions;

        try (Stream<Path> paths = Files.list(output)) {
            sessions = paths.filter(Files::isDirectory).toList();
        }

        Assertions.assertEquals(1, sessions.size());

        Path session = sessions.getFirst();
        Path pairedReplay = session.resolve("input.rep");

        Assertions.assertEquals(-1L, Files.mismatch(replay, pairedReplay));

        List<Path> recordings;

        try (Stream<Path> paths = Files.list(session)) {
            recordings = paths.filter(path -> path.getFileName().toString().endsWith(".screc") ||
                                              path.getFileName().toString().endsWith(".partial")).toList();
        }

        Assertions.assertEquals(1, recordings.size());

        Path recording = recordings.getFirst();

        try (RecordReader reader = RecordReader.open(recording)) {
            Assertions.assertEquals(LoadedReplayEvidence.HEADER_MATCHED, reader.getRecord().getMetadata().getLoadedReplayEvidence());

            List<SampleDefinition<?>> definitions = reader.getRecord().getDefinitions();

            Assertions.assertEquals(14, definitions.size());

            int[] availableSamples = new int[definitions.size()];
            Frame frame;
            Frame lastFrame = null;
            long frameCount = 0;

            while ((frame = reader.readNextFrame()) != null) {
                Assertions.assertTrue(lastFrame == null || frame.getFrameNumber() > lastFrame.getFrameNumber());

                lastFrame = frame;
                frameCount++;

                for (int sampleIndex = 0; sampleIndex < definitions.size(); sampleIndex++) {
                    if (frame.getSampleObservation(definitions.get(sampleIndex).getSampleClass()).getStatus() == SampleStatus.AVAILABLE) {
                        availableSamples[sampleIndex]++;
                    }
                }

                if (status == COMPLETE_EXIT_STATUS) {
                    Assertions.assertEquals(CaptureConsistency.MARKER_STABLE, frame.getConsistency());
                    Assertions.assertEquals(frameCount - 1, frame.getFrameNumber());
                    Assertions.assertNotNull(frame.getSample(PlayersSample.class));
                    Assertions.assertNotNull(frame.getSample(UnitsSample.class));
                    Assertions.assertNotNull(frame.getSample(RandomSample.class));
                }
            }

            Assertions.assertNotNull(lastFrame);

            for (int sampleIndex = 0; sampleIndex < definitions.size(); sampleIndex++) {
                Assertions.assertTrue(availableSamples[sampleIndex] > 0, "No available observations for " + definitions.get(sampleIndex).getName());
            }

            Assertions.assertTrue(lastFrame.getFrameNumber() >= expectedLastFrame - 1);

            RecordSummary summary = reader.getSummary();

            Assertions.assertNotNull(summary);
            Assertions.assertEquals(Termination.NORMAL, summary.getTermination());
            Assertions.assertEquals(status == COMPLETE_EXIT_STATUS, summary.isRequirementsSatisfied());
        }

        String metadata = Files.readString(recording.resolveSibling(recording.getFileName() + ".json"));
        String quality = status == COMPLETE_EXIT_STATUS ? "clean-observed-coverage" : "partial-or-inconsistent";

        Assertions.assertTrue(metadata.contains("\"quality\": \"" + quality + "\""));
        Assertions.assertTrue(metadata.contains("\"termination\": \"NORMAL\""));
        Assertions.assertTrue(metadata.contains("\"recording_sha256\": \"" + hashFile(recording) + "\""));
        Assertions.assertTrue(metadata.contains("\"replay_sha256\": \"" + hashFile(pairedReplay) + "\""));
        Assertions.assertTrue(metadata.contains("\"process_exit_code\":"));

        String identifierPrefix = "\"process_id\": \"";
        int identifierStart = metadata.indexOf(identifierPrefix) + identifierPrefix.length();
        long processIdentifier = Long.parseLong(metadata.substring(identifierStart, metadata.indexOf('"', identifierStart)));

        try (Stream<ProcessHandle> processes = ProcessHandle.allProcesses()) {
            Assertions.assertFalse(processes.anyMatch(process -> process.pid() == processIdentifier));
        }
    }
}
