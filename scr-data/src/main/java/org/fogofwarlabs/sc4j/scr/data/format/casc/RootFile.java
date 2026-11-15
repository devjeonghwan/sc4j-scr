package org.fogofwarlabs.sc4j.scr.data.format.casc;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Represents a parsed StarCraft: Remastered root file.
 * <p>
 * The S1 root file is a plain text manifest with one entry per line in the form
 * {@code path[:LOCALE]|hexmd5}. File names are looked up case-insensitively and both
 * slash directions are accepted.
 */
public final class RootFile {
    /**
     * The length of a 16-byte content key written in hexadecimal.
     */
    private static final int CONTENT_KEY_HEX_LENGTH = 32;

    /**
     * Parses the given root file content.
     *
     * @param content the BLTE-decoded root file content
     * @return the parsed {@link RootFile} instance
     * @throws IOException if the content holds no entries
     */
    public static @NotNull RootFile parse(byte @NotNull [] content) throws IOException {
        String text = new String(content, StandardCharsets.UTF_8);
        Map<String, byte[]> contentKeys = new HashMap<>();
        List<String> fileNames = new ArrayList<>();

        for (String line : text.split("\\r?\\n")) {
            if (line.isEmpty()) {
                continue;
            }

            int separatorIndex = line.indexOf('|');

            if (separatorIndex < 0) {
                continue;
            }

            String namePart = line.substring(0, separatorIndex);
            String hashPart = line.substring(separatorIndex + 1).trim();
            int localeIndex = namePart.indexOf(':');

            if (localeIndex >= 0) {
                namePart = namePart.substring(0, localeIndex);
            }

            byte[] contentKey = parseHex(hashPart);

            if (contentKey == null) {
                continue;
            }

            String normalizedName = namePart.replace('/', '\\').toUpperCase(Locale.ROOT);

            if (!contentKeys.containsKey(normalizedName)) {
                fileNames.add(namePart);
            }

            contentKeys.put(normalizedName, contentKey);
        }

        if (contentKeys.isEmpty()) {
            throw Exceptions.newIOException("The root file holds no entries.");
        }

        return new RootFile(contentKeys, fileNames);
    }

    private static byte @Nullable [] parseHex(@NotNull String hexText) {
        if (hexText.length() != CONTENT_KEY_HEX_LENGTH) {
            return null;
        }

        try {
            return HexFormat.of().parseHex(hexText);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }


    private final @NotNull Map<String, byte[]> contentKeys;
    private final @NotNull List<String> fileNames;

    /**
     * Creates a {@link RootFile} instance.
     *
     * @param contentKeys the content keys keyed by the normalized file name
     * @param fileNames   the file names in root file order
     */
    private RootFile(@NotNull Map<String, byte[]> contentKeys, @NotNull List<String> fileNames) {
        this.contentKeys = contentKeys;
        this.fileNames   = fileNames;
    }

    /**
     * @return the file names in root file order
     */
    public @NotNull List<String> getFileNames() {
        return Collections.unmodifiableList(this.fileNames);
    }


    /**
     * Finds the content key of the given file name.
     *
     * @param fileName the file name; both slash directions and any letter case are accepted
     * @return the 16-byte content key, or {@code null} if not found
     */
    public byte @Nullable [] findContentKey(@NotNull String fileName) {
        return this.contentKeys.get(fileName.replace('/', '\\').toUpperCase(Locale.ROOT));
    }
}
