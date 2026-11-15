package org.fogofwarlabs.sc4j.scr.core.test;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the result tables of folder-driven tests: one tab-separated line per case, such as
 * {@code replay-detection.tsv}, for comparison between runs.
 * <p>
 * The build names the result directory of each test task with the system property {@value #RESULTS_PROPERTY}
 * ({@code build/golden-results/<task>} of the module); without it the tables go to {@value #DEFAULT_RESULTS_DIRECTORY}.
 */
public final class TestResultUtils {
    /**
     * The system property naming the result directory of the test task.
     */
    public static final @NotNull String RESULTS_PROPERTY = "sc4j.golden.results";

    private static final @NotNull String DEFAULT_RESULTS_DIRECTORY = "build/golden-results";
    private static final @NotNull String COLUMN_SEPARATOR = "\t";
    private static final @NotNull String LINE_SEPARATOR = "\n";

    /**
     * Returns a file of the result directory.
     *
     * @param fileName the file name
     * @return the result file, which may not exist yet
     */
    public static @NotNull Path getResultPath(@NotNull String fileName) {
        return Path.of(System.getProperty(RESULTS_PROPERTY, DEFAULT_RESULTS_DIRECTORY)).resolve(fileName);
    }

    /**
     * Creates or truncates a result table and writes its header line.
     *
     * @param fileName  the file name in the result directory
     * @param keyColumn the name of the first column, which names the case
     * @param columns   the names of the value columns
     * @return the result table
     * @throws IOException if the table cannot be written
     */
    public static @NotNull Path createResultTable(@NotNull String fileName, @NotNull String keyColumn, @NotNull List<String> columns) throws IOException {
        Path table = getResultPath(fileName);

        Files.createDirectories(table.toAbsolutePath().getParent());
        Files.writeString(table, formatLine(keyColumn, columns), StandardCharsets.UTF_8);

        return table;
    }

    /**
     * Appends one case line to a result table; tabs and line breaks inside values are replaced by spaces.
     *
     * @param table  the result table
     * @param key    the case name
     * @param values the values in column order
     * @throws IOException if the table cannot be written
     */
    public static void appendResultLine(@NotNull Path table, @NotNull String key, @NotNull List<String> values) throws IOException {
        Files.writeString(table, formatLine(key, values), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static @NotNull String formatLine(@NotNull String key, @NotNull List<String> values) {
        List<String> columns = new ArrayList<>();

        columns.add(key);

        for (String value : values) {
            columns.add(value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' '));
        }

        return String.join(COLUMN_SEPARATOR, columns) + LINE_SEPARATOR;
    }


    /**
     * Creates a {@link TestResultUtils} instance.
     */
    private TestResultUtils() {
    }
}
