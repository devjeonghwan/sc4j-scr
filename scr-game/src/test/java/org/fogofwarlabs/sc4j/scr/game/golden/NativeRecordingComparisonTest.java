package org.fogofwarlabs.sc4j.scr.game.golden;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.core.test.TestResourceUtils;
import org.fogofwarlabs.sc4j.scr.core.test.TestResultUtils;
import org.fogofwarlabs.sc4j.scr.game.golden.NativeRecordingCheck.Result;
import org.fogofwarlabs.sc4j.scr.game.test.GameResourcesExtension;
import org.fogofwarlabs.sc4j.scr.game.test.InstallationTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.ReplaySurveyCheck;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Compares every native recording pair {@code <id>.rep} + {@code <id>.screc} below the folder {@code replays/native} with the SC4J simulation
 * ({@link NativeRecordingCheck}) and checks the detection of the same run ({@link ReplaySurveyCheck#of}).
 */
@RequiresInstallation
@ExtendWith(GameResourcesExtension.class)
final class NativeRecordingComparisonTest {
    private static final @NotNull String FOLDER = "replays/native";
    private static final @NotNull String KIND = "native";
    private static final @NotNull String NAME_SEPARATOR = "/";
    private static final @NotNull String KNOWN_DIVERGENCES = FOLDER + "/known-divergences.tsv";
    private static final @NotNull String KNOWN_PROBLEMS = FOLDER + "/known-problems.tsv";
    private static final @NotNull String REPLAY_SUFFIX = ".rep";
    private static final @NotNull String RECORDING_SUFFIX = ".screc";
    private static final @NotNull String COMPARISON_RESULTS_FILE = "native-comparison.tsv";
    private static final @NotNull String DETECTION_RESULTS_FILE = "native-detection.tsv";
    private static final @NotNull String KEY_COLUMN = "id";
    private static final @NotNull List<String> COMPARISON_COLUMNS = List.of("compared_frames", "first_differences", "differing_frames", "detail");
    private static final @NotNull String COMMENT_PREFIX = "#";
    private static final @NotNull String COLUMN_SEPARATOR = "\t";
    private static final int ID_COLUMN = 0;
    private static final int SAMPLE_COLUMN = 1;
    private static final int FRAME_COLUMN = 2;
    private static final int KNOWN_COLUMNS = 3;

    private static @NotNull Map<String, Map<String, Long>> readKnownDivergences() throws IOException {
        Map<String, Map<String, Long>> known = new TreeMap<>();

        for (String line : Files.readAllLines(TestResourceUtils.getPath(KNOWN_DIVERGENCES), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith(COMMENT_PREFIX)) {
                continue;
            }

            String[] columns = line.split(COLUMN_SEPARATOR, -1);

            if (columns.length < KNOWN_COLUMNS ||
                known.computeIfAbsent(columns[ID_COLUMN], id -> new LinkedHashMap<>())
                     .put(columns[SAMPLE_COLUMN], Long.parseLong(columns[FRAME_COLUMN])) != null) {
                throw Exceptions.newIOException("Invalid or repeated known divergence line: {0}", line);
            }
        }

        return known;
    }

    private static void compare(@NotNull InstallationTestResources resources, @NotNull String id, @NotNull Path replay,
                                @NotNull Map<String, Long> listed, @NotNull KnownProblemsAssert known,
                                @NotNull Path comparisonResults, @NotNull Path detectionResults) throws Exception {
        String name = KIND + NAME_SEPARATOR + id;
        Path recording = replay.resolveSibling(id + RECORDING_SUFFIX);

        Assertions.assertTrue(Files.isRegularFile(recording), name + " has no recording " + recording.getFileName() + ".");

        Result result = NativeRecordingCheck.compare(resources, replay, recording);
        ReplaySurveyCheck detection = ReplaySurveyCheck.of(resources.getReplayRun(replay));

        TestResultUtils.appendResultLine(comparisonResults, id,
                                         List.of(Long.toString(result.getComparedFrames()), result.getFirstDifferences().toString(),
                                                 result.getDifferingFrames().toString(), result.getDetail()));
        TestResultUtils.appendResultLine(detectionResults, id, detection.describeReport());

        Assertions.assertAll(
                () -> Assertions.assertEquals(new TreeMap<>(listed), new TreeMap<>(result.getFirstDifferences()),
                                              name + ": first differing frame per sample (" + result.getDetail() + ", differing frames " +
                                              result.getDifferingFrames() + ") does not match the known divergence list " + KNOWN_DIVERGENCES +
                                              "; fix SC4J or update the list with evidence."),
                () -> known.assertClass(id, name, detection.getClassification().getName(), detection.describe())
        );
    }


    /**
     * Creates one test per native recording pair.
     * <p>
     * A pair whose comparison samples all reproduce passes the comparison. A sample that differs fails the test unless the known divergence list
     * {@value #KNOWN_DIVERGENCES} names the pair, the sample and its first differing frame; a listed divergence that no longer occurs, or moved,
     * fails too, so that the list is updated with the change. The detection plays the shared run to the replay's last frame: a pair that is not on
     * the known problem list {@value #KNOWN_PROBLEMS} must be {@code ok}, a listed pair must keep its class, so a replay that fails to play fails
     * even when every recorded frame before the failure matched. A replay without its recording, or a recording without its replay, fails. The pairs
     * are {@code <source>/<id>}, the id is the key of both lists and the test of a pair is named {@code native/<id>}. The results go to
     * {@code <results>/native-comparison.tsv} and {@code <results>/native-detection.tsv}.
     *
     * @param resources the installed game resources
     * @return one test per native recording pair
     * @throws IOException if a recording has no replay or the known divergence list names an id without a pair
     */
    @TestFactory
    @NotNull Stream<DynamicTest> native_recording_comparison(@NotNull InstallationTestResources resources) throws IOException {
        Path folder = TestResourceUtils.getPath(FOLDER);
        Map<String, Map<String, Long>> divergences = readKnownDivergences();
        KnownProblemsAssert known = KnownProblemsAssert.read(KNOWN_PROBLEMS);
        Map<String, Path> replays = TestResourceUtils.listFilesByName(folder, REPLAY_SUFFIX);
        Path comparisonResults = TestResultUtils.createResultTable(COMPARISON_RESULTS_FILE, KEY_COLUMN, COMPARISON_COLUMNS);
        Path detectionResults = TestResultUtils.createResultTable(DETECTION_RESULTS_FILE, KEY_COLUMN, ReplaySurveyCheck.REPORT_COLUMNS);

        for (Path recording : TestResourceUtils.listFilesByName(folder, RECORDING_SUFFIX).values()) {
            String fileName = recording.getFileName().toString();
            String id = fileName.substring(0, fileName.length() - RECORDING_SUFFIX.length());

            if (!Files.isRegularFile(recording.resolveSibling(id + REPLAY_SUFFIX))) {
                throw Exceptions.newIOException("The recording {0} has no replay.", recording);
            }
        }

        for (String id : divergences.keySet()) {
            if (!replays.containsKey(id)) {
                throw Exceptions.newIOException("The known divergence list names {0}, which has no pair.", id);
            }
        }

        known.assertListedKeys(replays.keySet());

        return replays.entrySet()
                      .stream()
                      .map(entry -> DynamicTest.dynamicTest(KIND + NAME_SEPARATOR + entry.getKey(), entry.getValue().toUri(),
                                                            () -> compare(resources, entry.getKey(), entry.getValue(),
                                                                          divergences.getOrDefault(entry.getKey(), Map.of()), known,
                                                                          comparisonResults, detectionResults)));
    }
}
