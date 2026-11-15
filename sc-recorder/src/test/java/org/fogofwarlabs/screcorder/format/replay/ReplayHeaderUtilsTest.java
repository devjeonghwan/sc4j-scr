package org.fogofwarlabs.screcorder.format.replay;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Checks raw and compressed replay headers against checksums and truncated containers.
 */
final class ReplayHeaderUtilsTest {
    private static final int FIRST_CONTENT_MARKER_OFFSET = 1;
    private static final byte FIRST_CONTENT_MARKER = 120;
    private static final int SECOND_CONTENT_MARKER_OFFSET = 8;
    private static final byte SECOND_CONTENT_MARKER = 17;

    private static final int HEADER_SIZE = 633;
    private static final int PREFIX_SIZE = 32;
    private static final long CHECKSUM_COMPLEMENT = 0xFFFFFFFFL;
    private static final int HEADER_CHUNK_COUNT = 1;
    private static final int HEADER_CHECKSUM_FIELD = 20;

    @Test
    void read_header_should_verify_raw_and_compressed_headers_and_reject_corruption(@TempDir @NotNull Path directory) throws IOException {
        byte[] header = new byte[HEADER_SIZE];

        header[FIRST_CONTENT_MARKER_OFFSET]  = FIRST_CONTENT_MARKER;
        header[SECOND_CONTENT_MARKER_OFFSET] = SECOND_CONTENT_MARKER;

        CRC32 checksum = new CRC32();

        checksum.update(header);

        byte[] compressed = new byte[HEADER_SIZE];
        int compressedLength;

        try (Deflater compressor = new Deflater()) {
            compressor.setInput(header);
            compressor.finish();
            compressedLength = compressor.deflate(compressed);
        }

        for (byte[] stored : new byte[][]{header, Arrays.copyOf(compressed, compressedLength)}) {
            ByteBuffer replay = ByteBuffer.allocate(PREFIX_SIZE + stored.length).order(ByteOrder.LITTLE_ENDIAN);

            replay.put(HexFormat.of().parseHex("c219c293010000000400000073655253"));
            replay.putInt(0).putInt((int) (checksum.getValue() ^ CHECKSUM_COMPLEMENT)).putInt(HEADER_CHUNK_COUNT).putInt(stored.length).put(stored);

            Path path = directory.resolve("input.rep");

            Files.write(path, replay.array());
            Assertions.assertArrayEquals(header, ReplayHeaderUtils.readHeader(path));

            replay.putInt(HEADER_CHECKSUM_FIELD, 0);
            Files.write(path, replay.array());
            Assertions.assertThrows(IOException.class, () -> ReplayHeaderUtils.readHeader(path));
        }

        Path path = directory.resolve("input.rep");

        Files.write(path, new byte[PREFIX_SIZE - 1]);
        Assertions.assertThrows(IOException.class, () -> ReplayHeaderUtils.readHeader(path));
    }
}
