package org.fogofwarlabs.sc4j.scr.game.golden;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.test.InstallationTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.ReplayRunScenario;
import org.fogofwarlabs.sc4j.scr.game.test.SharedReplayRunScenario;
import org.fogofwarlabs.screcorder.format.recording.Frame;
import org.fogofwarlabs.screcorder.format.recording.RecordReader;
import org.fogofwarlabs.screcorder.format.recording.SampleDigester;
import org.fogofwarlabs.screcorder.format.recording.observation.CaptureConsistency;
import org.fogofwarlabs.screcorder.format.recording.observation.SampleObservation;
import org.fogofwarlabs.screcorder.format.recording.observation.SampleStatus;
import org.fogofwarlabs.screcorder.format.recording.summary.RecordSummary;
import org.fogofwarlabs.screcorder.sample.Sample;
import org.fogofwarlabs.screcorder.sample.SampleDefinition;
import org.fogofwarlabs.screcorder.sample.SampleDefinitions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Compares one native recording pair {@code <id>.rep} + {@code <id>.screc} (SCRECBIN digest recording) with the SC4J simulation of the replay.
 * <p>
 * The pair is consistent when the replay digest that the recording requested (its metadata) equals the SHA-256 of the replay next to it. The replay
 * is simulated once (the shared run of {@link InstallationTestResources#getReplayRun(Path)}); at every recorded marker-stable frame the comparison
 * samples present in the recording are projected ({@link GameSampleCheck}) and their digests compared. Frames missing from the recording (thinning,
 * missed or inconsistent frames) are not compared, nor is frame {@code 0}, which the native game shows while it is still loading. The replay session
 * values of the {@code triggers} sample are those observed in native replay playback: local and storm player {@code 0}, multiplayer flag {@code 0},
 * replay flag {@code 1}. The result is the first differing frame of every sample that differs; the pseudo-sample {@value #SIMULATION} names the frame
 * where the simulation stopped before the recording did.
 */
final class NativeRecordingCheck {
    /**
     * The pseudo-sample of a simulation that stops (fails or ends) before the last recorded frame.
     */
    static final @NotNull String SIMULATION = "simulation";

    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";
    private static final @NotNull String NONE = "none";
    private static final @NotNull String UNKNOWN_FRAME = "?";

    private static final int MULTIPLAYER_FLAG = 0;
    private static final int REPLAY_FLAG = 1;
    // Observed in every recorded replay session (LAN, computer and all-computer replays): local and storm player 0.
    private static final int REPLAY_LOCAL_PLAYER = 0;

    // Native frame 0 is observed while the game is still loading (no units, players or RNG yet).
    private static final long FIRST_COMPARED_FRAME = 1;
    private static final int EXAMPLE_FRAMES = 8;


    /**
     * The outcome of one comparison.
     */
    static final class Result {
        private final @NotNull Map<String, Long> firstDifferences;
        private final @NotNull Map<String, Long> differingFrames;
        private final long comparedFrames;
        private final @NotNull String detail;

        private Result(@NotNull Map<String, Long> firstDifferences, @NotNull Map<String, Long> differingFrames,
                       long comparedFrames,
                       @NotNull String detail) {
            this.firstDifferences = firstDifferences;
            this.differingFrames  = differingFrames;
            this.comparedFrames   = comparedFrames;
            this.detail           = detail;
        }

        /**
         * @return the first differing frame of every differing sample, in sample order
         */
        @NotNull Map<String, Long> getFirstDifferences() {
            return this.firstDifferences;
        }

        /**
         * @return the number of differing compared frames by differing sample, in sample order
         */
        @NotNull Map<String, Long> getDifferingFrames() {
            return this.differingFrames;
        }

        /**
         * @return the number of compared recorded frames
         */
        long getComparedFrames() {
            return this.comparedFrames;
        }

        /**
         * @return the recording summary and the simulation stop, for reports
         */
        @NotNull String getDetail() {
            return this.detail;
        }
    }

    private static final class RecordedFrame {
        private final long frame;
        private final byte @NotNull [] @Nullable [] digests;

        private RecordedFrame(long frame, byte @NotNull [] @Nullable [] digests) {
            this.frame   = frame;
            this.digests = digests;
        }

        /**
         * @return the recorded frame number
         */
        private long getFrame() {
            return this.frame;
        }

        /**
         * @return the recorded digest of every comparison sample in definition order, {@code null} where the sample was not available
         */
        private byte @NotNull [] @Nullable [] getDigests() {
            return this.digests;
        }
    }


    /**
     * Compares one pair.
     *
     * @param resources the installed game resources
     * @param replay    the replay
     * @param recording the native recording of the replay
     * @return the comparison result
     * @throws Exception if the recording is invalid, does not belong to the replay, or the game cannot be inspected
     */
    static @NotNull Result compare(@NotNull InstallationTestResources resources, @NotNull Path replay, @NotNull Path recording) throws Exception {
        List<SampleDefinition<?>> definitions = new ArrayList<>();
        List<RecordedFrame> frames = new ArrayList<>();
        RecordSummary summary = readRecording(replay, recording, definitions, frames);
        SharedReplayRunScenario run = resources.getReplayRun(replay);
        GameSampleCheck sampleCheck = new GameSampleCheck(REPLAY_LOCAL_PLAYER, REPLAY_LOCAL_PLAYER, MULTIPLAYER_FLAG, REPLAY_FLAG);
        Map<String, Long> first = new LinkedHashMap<>();
        Map<String, Long> differing = new LinkedHashMap<>();
        Map<String, List<Long>> examples = new LinkedHashMap<>();
        long lastFrame = run.getReplay().getHeader().getFrames();
        long compared = 0;
        String stop = NONE;

        for (RecordedFrame recorded : frames) {
            if (recorded.getFrame() < FIRST_COMPARED_FRAME) {
                continue;
            }

            if (recorded.getFrame() > lastFrame) {
                break;
            }

            try {
                run.inspect((int) recorded.getFrame(), game -> {
                    for (int index = 0; index < definitions.size(); index++) {
                        byte[] expected = recorded.getDigests()[index];

                        if (expected == null) {
                            continue;
                        }

                        SampleDefinition<?> definition = definitions.get(index);
                        byte[] actual = sampleCheck.digest(definition, game);

                        if (actual == null || !Arrays.equals(expected, actual)) {
                            first.putIfAbsent(definition.getName(), recorded.getFrame());
                            differing.merge(definition.getName(), 1L, Long::sum);

                            List<Long> listed = examples.computeIfAbsent(definition.getName(), name -> new ArrayList<>());

                            if (listed.size() < EXAMPLE_FRAMES) {
                                listed.add(recorded.getFrame());
                            }
                        }
                    }
                });
            } catch (IllegalStateException exception) {
                first.put(SIMULATION, recorded.getFrame());
                stop = "frame " + recorded.getFrame() + " not reached: " + run.getEndValues().get(ReplayRunScenario.FAILURE_KEY, NONE) +
                       " at frame " + run.getEndValues().get(ReplayRunScenario.FAILURE_FRAME_KEY, UNKNOWN_FRAME);

                break;
            }

            compared++;
        }

        return new Result(first, differing,
                          compared,
                          "recording frames " + summary.getFrameCount() + " (" + summary.getFirstFrame() + ".." + summary.getLastFrame() +
                          "), missing " + summary.getMissingFrameCount() + ", torn " + summary.getTornObservationCount() +
                          "; simulation stop: " + stop + "; first differing frames " + examples);
    }

    private static @NotNull RecordSummary readRecording(@NotNull Path replay, @NotNull Path recording,
                                                        @NotNull List<SampleDefinition<?>> definitions,
                                                        @NotNull List<RecordedFrame> frames) throws IOException, NoSuchAlgorithmException {
        try (RecordReader reader = RecordReader.open(recording)) {
            byte[] requested = reader.getRecord().getMetadata().getRequestedReplayDigest();
            byte[] actual = MessageDigest.getInstance(DIGEST_ALGORITHM).digest(Files.readAllBytes(replay));

            if (requested == null || !Arrays.equals(requested, actual)) {
                throw Exceptions.newIllegalStateException("The recording {0} was not made from the replay {1}: requested {2}, replay {3}.",
                                                          recording.getFileName(), replay.getFileName(),
                                                          requested == null ? NONE : HexFormat.of().formatHex(requested),
                                                          HexFormat.of().formatHex(actual));
            }

            for (SampleDefinition<?> definition : reader.getRecord().getDefinitions()) {
                if (SampleDefinitions.getComparisonDefinitions().contains(definition)) {
                    definitions.add(definition);
                }
            }

            SampleDigester digester = new SampleDigester();
            Frame frame;

            while ((frame = reader.readNextFrame()) != null) {
                if (frame.getConsistency() != CaptureConsistency.MARKER_STABLE) {
                    continue;
                }

                byte[][] digests = new byte[definitions.size()][];

                for (int index = 0; index < definitions.size(); index++) {
                    SampleDefinition<?> definition = definitions.get(index);
                    SampleObservation observation = frame.getSampleObservation(definition.getSampleClass());

                    if (observation.getStatus() == SampleStatus.AVAILABLE) {
                        // A VALUES recording (local diagnosis) is digested here exactly as a DIGEST recording stores it.
                        digests[index] = observation.getDigest() != null ? observation.getDigest() : digestValue(digester, definition, frame);
                    }
                }

                frames.add(new RecordedFrame(frame.getFrameNumber(), digests));
            }

            RecordSummary summary = reader.getSummary();

            if (summary == null) {
                throw Exceptions.newIllegalStateException("The recording {0} has no END summary.", recording.getFileName());
            }

            return summary;
        }
    }

    private static <SampleValue extends Sample> byte @NotNull [] digestValue(@NotNull SampleDigester digester,
                                                                             @NotNull SampleDefinition<SampleValue> definition,
                                                                             @NotNull Frame frame) throws IOException {
        return digester.digest(definition, frame.getSample(definition.getSampleClass()));
    }


    /**
     * Creates a {@link NativeRecordingCheck} instance.
     */
    private NativeRecordingCheck() {
    }
}
