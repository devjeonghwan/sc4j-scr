package org.fogofwarlabs.sc4j.scr.data.resource;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads the files a mod ships in its own jar next to the installed data, such as the frame files of its added images ({@link ScrModGraphics}).
 * A path is relative to the jar root, with {@code /} separators and no {@code .} or {@code ..} segment.
 */
@FunctionalInterface
public interface ScrModFiles {
    /**
     * Creates a {@link ScrModFiles} instance over the resources of a class loader, as a mod reads its own jar.
     *
     * @param loader the class loader of the mod
     * @return the created {@link ScrModFiles} instance
     */
    static @NotNull ScrModFiles of(@NotNull ClassLoader loader) {
        return path -> {
            try (InputStream input = loader.getResourceAsStream(checkPath(path))) {
                if (input == null) {
                    throw Exceptions.newIOException("The mod has no file {0}.", path);
                }

                return input.readAllBytes();
            }
        };
    }

    /**
     * Checks a mod file path.
     *
     * @param path the path relative to the jar root
     * @return the path
     * @throws IOException if the path is empty, absolute, has a backslash, a drive or an empty, {@code .} or {@code ..} segment
     */
    static @NotNull String checkPath(@NotNull String path) throws IOException {
        if (path.isEmpty() || path.indexOf('\\') >= 0 || path.indexOf(':') >= 0) {
            throw Exceptions.newIOException("Invalid mod file path: {0}", path);
        }

        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw Exceptions.newIOException("Invalid mod file path: {0}", path);
            }
        }

        return path;
    }

    /**
     * Reads a file.
     *
     * @param path the path relative to the jar root
     * @return the file contents
     * @throws IOException if the path is invalid or the file cannot be read
     */
    byte @NotNull [] readFile(@NotNull String path) throws IOException;
}
