package org.fogofwarlabs.sc4j.scr.core.test;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Names the inputs from outside the repository that tests read: one system property per input, forwarded by the build
 * from the Gradle property of the same name.
 * <p>
 * A blank or missing property counts as not configured. Tests declare the inputs they need with
 * {@link RequiresInstallation} or {@link CorpusTest} and read the paths only through {@link #getPath()} and
 * {@link #resolveFile}, which fail with the configuration message, so no test reads the system properties itself.
 */
public enum ExternalInput {
    /**
     * The SC:R installation: CASC data and {@code x86_64/StarCraft.exe}.
     */
    INSTALLATION("sc4j.data", "The SC:R installation"),
    /**
     * The STARDATA replay corpus root with {@code stardata_original_replays}.
     */
    REPLAYS("sc4j.replays", "The replay corpus"),
    /**
     * The client's maps folder with the large EUD user maps under their original names.
     */
    MAPS("sc4j.maps", "The client's maps folder"),
    /**
     * The replay corpus folder whose replays the corpus survey plays, in any folder layout.
     */
    CORPUS("sc4j.corpus", "The replay corpus folder");

    /**
     * The maps folder of {@link #INSTALLATION}, the first name for {@link #resolveFile}.
     */
    public static final @NotNull String MAPS_FOLDER = "Maps";
    /**
     * The folder of test maps inside {@link #MAPS_FOLDER}, the second name for {@link #resolveFile}.
     */
    public static final @NotNull String TEST_MAPS_FOLDER = "test";

    private final @NotNull String propertyName;
    private final @NotNull String description;

    /**
     * Creates an {@link ExternalInput} instance.
     *
     * @param propertyName the system and Gradle property naming the input directory
     * @param description  the input description used in messages
     */
    ExternalInput(@NotNull String propertyName, @NotNull String description) {
        this.propertyName = propertyName;
        this.description  = description;
    }

    /**
     * @return the message that tells how to configure this input
     */
    public @NotNull String getMissingMessage() {
        return Exceptions.format("{0} is required: set the Gradle property {1}, for example -P{1}=<directory>.", this.description, this.propertyName);
    }

    /**
     * @return {@code true} if the property of this input is set and not blank, {@code false} otherwise
     */
    public boolean isConfigured() {
        return !System.getProperty(this.propertyName, "").isBlank();
    }

    /**
     * Returns the configured input directory.
     *
     * @return the input directory
     * @throws IllegalStateException if the property is not configured or names no directory
     * @throws InvalidPathException  if the configured input is not a valid file-system path
     */
    public @NotNull Path getPath() {
        String value = System.getProperty(this.propertyName, "");

        if (value.isBlank()) {
            throw Exceptions.newIllegalStateException(this.getMissingMessage());
        }

        Path path = Path.of(value.strip());

        if (!Files.isDirectory(path)) {
            throw Exceptions.newIllegalStateException("{0} does not exist: {1}={2}", this.description, this.propertyName, path);
        }

        return path;
    }

    /**
     * Returns an existing file or folder below the configured input directory, such as an installed map.
     * <p>
     * The calling test fails, and is not skipped, when the input or the file is missing.
     *
     * @param first the first name of the path relative to the input directory
     * @param more  the further names of the relative path
     * @return the file or folder
     * @throws IllegalStateException if the file or folder does not exist
     */
    public @NotNull Path resolveFile(@NotNull String first, @NotNull String @NotNull ... more) {
        Path path = this.getPath().resolve(first, more);

        if (!Files.exists(path)) {
            throw Exceptions.newIllegalStateException("{0} has no file {1}.", this.description, path);
        }

        return path;
    }
}
