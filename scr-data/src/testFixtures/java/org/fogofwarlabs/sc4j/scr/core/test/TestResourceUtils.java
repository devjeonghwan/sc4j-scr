package org.fogofwarlabs.sc4j.scr.core.test;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Resolves test resources of the repository by their path relative to the class path root, such as
 * {@code replays/focused/stardata/STARDATA_00000001.rep}.
 * <p>
 * The resources must be files in a class path directory, not entries of a jar: games receive the absolute map path, so
 * a resource keeps one stable location. Modules that use the replays, maps and saves of {@code scr-game} put its
 * resource directory on their test runtime class path.
 */
public final class TestResourceUtils {
    private static final @NotNull String FILE_PROTOCOL = "file";
    private static final char EXTENSION_SEPARATOR = '.';

    /**
     * Returns the file of a test resource.
     *
     * @param resource the resource path relative to the class path root, without a leading {@code /}
     * @return the resource file
     * @throws IllegalStateException if the resource is not a file on the test class path
     */
    public static @NotNull Path getPath(@NotNull String resource) {
        URL url = TestResourceUtils.class.getResource("/" + resource);

        if (url == null || !FILE_PROTOCOL.equals(url.getProtocol())) {
            throw Exceptions.newIllegalStateException("The test resource {0} is not a file on the test class path: {1}", resource, url);
        }

        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException exception) {
            throw Exceptions.newIllegalStateException(exception, "Invalid test resource URL {0}.", url);
        }
    }

    /**
     * Reads a test resource.
     *
     * @param resource the resource path relative to the class path root, without a leading {@code /}
     * @return the resource contents
     * @throws IOException           if the resource cannot be read
     * @throws IllegalStateException if the resource is not a file on the test class path
     */
    public static byte @NotNull [] readAllBytes(@NotNull String resource) throws IOException {
        return Files.readAllBytes(getPath(resource));
    }

    /**
     * Lists the files below a folder, recursively, whose lowercase names end with one of the given extensions.
     *
     * @param folder     the folder, such as the path of {@code replays/detection}
     * @param extensions the lowercase file name extensions, such as {@code .rep}
     * @return the files in path order
     * @throws IOException if the folder cannot be read
     */
    public static @NotNull List<Path> listFiles(@NotNull Path folder, @NotNull String @NotNull ... extensions) throws IOException {
        try (Stream<Path> walk = Files.walk(folder)) {
            return walk.filter(path -> Files.isRegularFile(path) &&
                                       Arrays.stream(extensions).anyMatch(path.getFileName().toString().toLowerCase(Locale.ROOT)::endsWith))
                       .sorted()
                       .toList();
        }
    }

    /**
     * Lists the files below a folder like {@link #listFiles}, keyed by their names without the extension, such as the
     * replay ids of a replay folder.
     *
     * @param folder     the folder
     * @param extensions the lowercase file name extensions, such as {@code .rep}
     * @return the files in path order, keyed by name without extension
     * @throws IOException if the folder cannot be read or two files have the same name without extension
     */
    public static @NotNull Map<String, Path> listFilesByName(@NotNull Path folder, @NotNull String @NotNull ... extensions) throws IOException {
        Map<String, Path> files = new LinkedHashMap<>();

        for (Path file : listFiles(folder, extensions)) {
            String fileName = file.getFileName().toString();
            String name = fileName.substring(0, fileName.lastIndexOf(EXTENSION_SEPARATOR));

            if (files.put(name, file) != null) {
                throw Exceptions.newIOException("The name {0} is used twice below {1}.", name, folder);
            }
        }

        return files;
    }


    /**
     * Creates a {@link TestResourceUtils} instance.
     */
    private TestResourceUtils() {
    }
}
