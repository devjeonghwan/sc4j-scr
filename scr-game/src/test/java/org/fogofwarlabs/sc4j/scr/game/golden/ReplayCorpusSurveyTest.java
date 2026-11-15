package org.fogofwarlabs.sc4j.scr.game.golden;

import org.fogofwarlabs.sc4j.scr.core.test.CorpusTest;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.TestResourceUtils;
import org.fogofwarlabs.sc4j.scr.core.test.TestResultUtils;
import org.fogofwarlabs.sc4j.scr.game.test.GameResourcesExtension;
import org.fogofwarlabs.sc4j.scr.game.test.InstallationTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.ReplaySurveyCheck;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;

/**
 * Plays every replay below the corpus folder ({@link ExternalInput#CORPUS}) to its end, classifies it as {@code ok}, {@code suspicious} or
 * {@code error} ({@link ReplaySurveyCheck}) and compares the class with the known problem list {@value #KNOWN_PROBLEMS}.
 */
@CorpusTest({ExternalInput.INSTALLATION, ExternalInput.CORPUS})
@ExtendWith(GameResourcesExtension.class)
final class ReplayCorpusSurveyTest {
    private static final @NotNull String KNOWN_PROBLEMS = "corpus/known-problems.tsv";
    private static final @NotNull String RESULTS_FILE = "replay-survey.tsv";
    private static final @NotNull String KEY_COLUMN = "replay";
    private static final @NotNull String REPLAY_SUFFIX = ".rep";
    private static final @NotNull Duration CASE_TIMEOUT = Duration.ofMinutes(10);
    private static final char PATH_SEPARATOR = '/';

    private static void survey(@NotNull InstallationTestResources resources,
                               @NotNull Path replay,
                               @NotNull String name,
                               @NotNull KnownProblemsAssert known,
                               @NotNull Path results) throws Exception {
        ReplaySurveyCheck survey = Assertions.assertTimeoutPreemptively(CASE_TIMEOUT, () -> ReplaySurveyCheck.run(resources.getGameData(), replay),
                                                                        "The replay did not finish within " + CASE_TIMEOUT.toSeconds() + " seconds.");

        TestResultUtils.appendResultLine(results, name, survey.describeReport());
        known.assertClass(replay.getFileName().toString(), name, survey.getClassification().getName(), survey.describe());
    }


    /**
     * Creates one test per corpus replay.
     * <p>
     * A replay that is not on the known problem list must be {@code ok}; a listed replay must keep its listed class, so a fixed replay is removed
     * from the list. The list names replays by file name, so it serves any corpus folder layout. Every replay appends one line to
     * {@code <results>/replay-survey.tsv}: class, signals with their first frame, run length, failure or native end, cost, per-player command totals,
     * per-window counts ({@link ReplaySurveyCheck#describeWindows()}) and the end values.
     *
     * @param resources the installed game resources
     * @return one test per corpus replay
     * @throws IOException if the known problem list or the corpus folder cannot be read, or the result table cannot be written
     */
    @TestFactory
    @NotNull Stream<DynamicTest> replay_corpus_survey(@NotNull InstallationTestResources resources) throws IOException {
        Path folder = ExternalInput.CORPUS.getPath();
        KnownProblemsAssert known = KnownProblemsAssert.read(KNOWN_PROBLEMS);
        Path results = TestResultUtils.createResultTable(RESULTS_FILE, KEY_COLUMN, ReplaySurveyCheck.REPORT_COLUMNS);

        return TestResourceUtils.listFiles(folder, REPLAY_SUFFIX).stream().map(replay -> {
            String name = folder.relativize(replay).toString().replace(File.separatorChar, PATH_SEPARATOR);

            return DynamicTest.dynamicTest(name, replay.toUri(), () -> survey(resources, replay, name, known, results));
        });
    }
}
