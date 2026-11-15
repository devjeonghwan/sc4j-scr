package org.fogofwarlabs.screcorder.format.replay;

import org.fogofwarlabs.screcorder.binary.LittleEndianUtils;
import org.fogofwarlabs.screcorder.capture.starcraft.StarCraftLayout;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Reads and verifies the header section of modern Remastered replay files.
 */
public final class ReplayHeaderUtils {
    private static final int HEADER_CHUNK_COUNT = 1;
    private static final int STORED_SIZE_FIELD = 2 * Integer.BYTES;
    private static final int INFLATE_OVERFLOW_PROBE_SIZE = Byte.BYTES;

    private static final int PREFIX_SIZE = 32;
    private static final int MODERN_EXTENSION_OFFSET = 16;
    private static final int MODERN_SECTION_OFFSET = 20;
    private static final int EARLY_SECTION_OFFSET = 16;
    private static final int SECTION_HEADER_BYTES = 12;
    private static final byte @NotNull [] MODERN_MAGIC = HexFormat.of().parseHex("c219c293010000000400000073655253");
    private static final byte @NotNull [] EARLY_MAGIC = HexFormat.of().parseHex("a77e7e2b010000000400000072655253");
    private static final long CHECKSUM_MASK = 0xFFFFFFFFL;

    /**
     * Reads the bounded raw or zlib-compressed replay header and checks its checksum.
     *
     * @param path the replay file
     * @return the verified header bytes
     * @throws IOException if the container or header is unsupported, truncated or corrupt
     */
    public static byte @NotNull [] readHeader(@NotNull Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] prefix = input.readNBytes(PREFIX_SIZE);

            if (prefix.length != PREFIX_SIZE) {
                throw Exceptions.newIOException("Replay is truncated");
            }

            int sectionOffset;

            if (Arrays.equals(prefix, 0, MODERN_MAGIC.length, MODERN_MAGIC, 0, MODERN_MAGIC.length)) {
                long extensionOffset = LittleEndianUtils.readUnsigned32(prefix, MODERN_EXTENSION_OFFSET);

                if (extensionOffset != 0 && (extensionOffset < MODERN_SECTION_OFFSET || extensionOffset > Files.size(path))) {
                    throw Exceptions.newIOException("Invalid replay extension offset");
                }

                sectionOffset = MODERN_SECTION_OFFSET;
            } else if (Arrays.equals(prefix, 0, EARLY_MAGIC.length, EARLY_MAGIC, 0, EARLY_MAGIC.length)) {
                sectionOffset = EARLY_SECTION_OFFSET;
            } else {
                throw Exceptions.newIOException("Replay launch supports modern seRS/reRS containers");
            }

            long checksum = LittleEndianUtils.readUnsigned32(prefix, sectionOffset);
            long chunks = LittleEndianUtils.readUnsigned32(prefix, sectionOffset + Integer.BYTES);
            long storedSize = LittleEndianUtils.readUnsigned32(prefix, sectionOffset + STORED_SIZE_FIELD);

            if (chunks != HEADER_CHUNK_COUNT || storedSize <= 0 || storedSize > StarCraftLayout.REPLAY_HEADER_SIZE) {
                throw Exceptions.newIOException("Unsupported replay header section");
            }

            byte[] stored = new byte[(int) storedSize];
            int alreadyRead = PREFIX_SIZE - sectionOffset - SECTION_HEADER_BYTES;
            int prefixPayloadBytes = Math.min(alreadyRead, stored.length);

            System.arraycopy(prefix, sectionOffset + SECTION_HEADER_BYTES, stored, 0, prefixPayloadBytes);

            byte[] remainder = input.readNBytes(stored.length - prefixPayloadBytes);

            if (remainder.length != stored.length - prefixPayloadBytes) {
                throw Exceptions.newIOException("Truncated replay header payload");
            }

            System.arraycopy(remainder, 0, stored, prefixPayloadBytes, remainder.length);

            byte[] header;

            if (stored.length == StarCraftLayout.REPLAY_HEADER_SIZE) {
                header = stored;
            } else {
                try (Inflater inflater = new Inflater()) {
                    try {
                        inflater.setInput(stored);

                        byte[] decoded = new byte[StarCraftLayout.REPLAY_HEADER_SIZE + INFLATE_OVERFLOW_PROBE_SIZE];
                        int length = inflater.inflate(decoded);

                        if (length != StarCraftLayout.REPLAY_HEADER_SIZE || !inflater.finished() || inflater.getRemaining() != 0) {
                            throw Exceptions.newIOException("Invalid compressed replay header");
                        }

                        header = Arrays.copyOf(decoded, StarCraftLayout.REPLAY_HEADER_SIZE);
                    } catch (DataFormatException exception) {
                        throw Exceptions.newIOException(exception, "Corrupt compressed replay header");
                    }
                }
            }

            CRC32 calculator = new CRC32();

            calculator.update(header);

            if ((calculator.getValue() ^ CHECKSUM_MASK) != checksum) {
                throw Exceptions.newIOException("Replay header checksum mismatch");
            }

            return header;
        }
    }


    private ReplayHeaderUtils() {
    }
}
