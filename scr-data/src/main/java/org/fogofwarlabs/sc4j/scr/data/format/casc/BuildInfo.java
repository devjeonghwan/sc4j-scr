package org.fogofwarlabs.sc4j.scr.data.format.casc;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Represents the {@code .build.info} file of a CASC installation.
 * <p>
 * The file is a pipe-separated table whose header declares field names with type markers,
 * for example {@code Branch!STRING:0|Active!DEC:1|Build Key!HEX:16|...}.
 */
public final class BuildInfo {
    /**
     * The name of the build information file in the installation directory.
     */
    static final @NotNull String FILE_NAME = ".build.info";

    /**
     * The header line and at least one entry line.
     */
    private static final int MINIMUM_LINE_COUNT = 2;

    /**
     * Reads the active entry of the given {@code .build.info} file.
     *
     * @param buildInfoFile the path to the {@code .build.info} file
     * @return the parsed {@link BuildInfo} instance
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the file has no usable entry
     */
    public static @NotNull BuildInfo readFrom(@NotNull Path buildInfoFile) throws IOException {
        List<String> lines = Files.readAllLines(buildInfoFile, StandardCharsets.UTF_8);

        if (lines.size() < MINIMUM_LINE_COUNT) {
            throw Exceptions.newIllegalArgumentException("The .build.info file has no entries: {0}", buildInfoFile);
        }

        String headerLine = lines.get(0);
        String[] rawFields = headerLine.split("\\|", -1);
        String[] fieldNames = new String[rawFields.length];

        for (int fieldIndex = 0; fieldIndex < rawFields.length; fieldIndex++) {
            String rawField = rawFields[fieldIndex];
            int typeMarkerIndex = rawField.indexOf('!');

            fieldNames[fieldIndex] = typeMarkerIndex >= 0 ? rawField.substring(0, typeMarkerIndex) : rawField;
        }

        int activeFieldIndex = findFieldIndex(fieldNames, "Active");
        int buildKeyFieldIndex = findFieldIndex(fieldNames, "Build Key");
        int versionFieldIndex = findFieldIndex(fieldNames, "Version");
        int branchFieldIndex = findFieldIndex(fieldNames, "Branch");

        if (buildKeyFieldIndex < 0 || versionFieldIndex < 0) {
            throw Exceptions.newIllegalArgumentException("The .build.info file misses required fields: {0}", buildInfoFile);
        }

        String[] selectedValues = null;

        for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex).trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] values = line.split("\\|", -1);

            if (selectedValues == null) {
                selectedValues = values;
            }

            if (activeFieldIndex >= 0 && activeFieldIndex < values.length && "1".equals(values[activeFieldIndex])) {
                selectedValues = values;

                break;
            }
        }

        if (selectedValues == null) {
            throw Exceptions.newIllegalArgumentException("The .build.info file has no entries: {0}", buildInfoFile);
        }

        return new BuildInfo(branchFieldIndex < 0 || branchFieldIndex >= selectedValues.length ? "" : selectedValues[branchFieldIndex],
                             buildKeyFieldIndex >= selectedValues.length ? "" : selectedValues[buildKeyFieldIndex],
                             versionFieldIndex >= selectedValues.length ? "" : selectedValues[versionFieldIndex]);
    }

    private static int findFieldIndex(@NotNull String @NotNull [] fieldNames, @NotNull String fieldName) {
        for (int fieldIndex = 0; fieldIndex < fieldNames.length; fieldIndex++) {
            if (fieldNames[fieldIndex].equalsIgnoreCase(fieldName)) {
                return fieldIndex;
            }
        }

        return -1;
    }


    private final @NotNull String branch;
    private final @NotNull String buildKey;
    private final @NotNull String version;

    /**
     * Creates a {@link BuildInfo} instance.
     *
     * @param branch   the branch name
     * @param buildKey the build configuration key
     * @param version  the build version string
     */
    private BuildInfo(@NotNull String branch, @NotNull String buildKey, @NotNull String version) {
        this.branch   = branch;
        this.buildKey = buildKey;
        this.version  = version;
    }

    /**
     * @return the branch name
     */
    public @NotNull String getBranch() {
        return this.branch;
    }

    /**
     * @return the build configuration key
     */
    public @NotNull String getBuildKey() {
        return this.buildKey;
    }

    /**
     * @return the build version string
     */
    public @NotNull String getVersion() {
        return this.version;
    }
}
