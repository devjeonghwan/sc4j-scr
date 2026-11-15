package org.fogofwarlabs.sc4j.scr.data.format.casc;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.zip.Deflater;

/**
 * Checks chunk-table decoding, embedded containers and malformed decoded sizes.
 */
final class BlteUtilsTest {
    private static final int STORED_MODE_PERIOD = 2;
    private static final int CONTENT_PATTERN_PERIOD = 97;
    private static final int RANDOM_CONTENT_LENGTH = 40;
    private static final int UNSIGNED_BYTE_VALUE_COUNT = 256;
    private static final int CONTENT_RUN_DIVISOR = 3;
    private static final int SMALL_CONTENT_SIZE = 100_000;
    private static final int SMALL_CHUNK_SIZE = 16_384;
    private static final int LARGE_CONTENT_SIZE = 9 << 20;
    private static final int LARGE_CHUNK_SIZE = 256 << 10;
    private static final int EMBEDDED_CONTENT_SIZE = 50_000;
    private static final int EMBEDDED_CHUNK_SIZE = 8_192;
    private static final int RECORD_HEADER_SIZE = 30;
    private static final int CHUNK_DECODED_SIZE_FIELD = 4;

    private static final int HEADER_FIXED_SIZE = 12;
    private static final int CHUNK_INFO_SIZE = 24;
    private static final int CHUNK_CHECKSUM_SIZE = 16;
    private static final int CHUNK_TABLE_FLAGS = 0x0F;
    private static final int DEFLATE_BUFFER_SIZE = 8192;

    private static final byte @NotNull [] MAGIC = {'B', 'L', 'T', 'E'};
    private static final char ZLIB_MODE = 'Z';
    private static final char STORED_MODE = 'N';

    /**
     * Builds a BLTE container with a chunk table: chunks alternate zlib ({@code Z}) and stored ({@code N}).
     */
    private static byte @NotNull [] createContainer(byte @NotNull [] content, int chunkSize) {
        int chunkCount = (content.length + chunkSize - 1) / chunkSize;
        byte[][] chunks = new byte[chunkCount][];

        for (int chunk = 0; chunk < chunkCount; chunk++) {
            int start = chunk * chunkSize;
            int length = Math.min(chunkSize, content.length - start);
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();

            if (chunk % STORED_MODE_PERIOD == 0) {
                try (Deflater deflater = new Deflater()) {
                    byte[] buffer = new byte[DEFLATE_BUFFER_SIZE];

                    encoded.write(ZLIB_MODE);
                    deflater.setInput(content, start, length);
                    deflater.finish();

                    while (!deflater.finished()) {
                        encoded.write(buffer, 0, deflater.deflate(buffer));
                    }
                }
            } else {
                encoded.write(STORED_MODE);
                encoded.write(content, start, length);
            }

            chunks[chunk] = encoded.toByteArray();
        }

        int headerSize = HEADER_FIXED_SIZE + chunkCount * CHUNK_INFO_SIZE;
        int totalSize = headerSize;

        for (byte[] chunk : chunks) {
            totalSize += chunk.length;
        }

        ByteBuffer container = ByteBuffer.allocate(totalSize);

        container.put(MAGIC).putInt(headerSize).put((byte) CHUNK_TABLE_FLAGS).put((byte) 0).putShort((short) chunkCount);

        for (int chunk = 0; chunk < chunkCount; chunk++) {
            container.putInt(chunks[chunk].length).putInt(Math.min(chunkSize, content.length - chunk * chunkSize)).put(new byte[CHUNK_CHECKSUM_SIZE]);
        }

        for (byte[] chunk : chunks) {
            container.put(chunk);
        }

        return container.array();
    }

    private static byte @NotNull [] createContent(int size) {
        byte[] content = new byte[size];
        Random random = new Random(size);

        for (int index = 0; index < size; index++) {
            content[index] = (byte) (index % CONTENT_PATTERN_PERIOD < RANDOM_CONTENT_LENGTH
                                     ? random.nextInt(UNSIGNED_BYTE_VALUE_COUNT)
                                     : index / CONTENT_RUN_DIVISOR);
        }

        return content;
    }


    @Test
    void decode_should_restore_content_of_small_chunk_table() throws IOException {
        byte[] content = createContent(SMALL_CONTENT_SIZE);

        Assertions.assertArrayEquals(content, BlteUtils.decode(createContainer(content, SMALL_CHUNK_SIZE)));
    }

    @Test
    void decode_should_restore_content_of_large_chunk_table_above_parallel_threshold() throws IOException {
        // Above the parallel threshold: the chunks inflate on the common pool into one array.
        byte[] content = createContent(LARGE_CONTENT_SIZE);

        Assertions.assertArrayEquals(content, BlteUtils.decode(createContainer(content, LARGE_CHUNK_SIZE)));
    }

    @Test
    void decode_should_read_container_inside_larger_array() throws IOException {
        byte[] content = createContent(EMBEDDED_CONTENT_SIZE);
        byte[] container = createContainer(content, EMBEDDED_CHUNK_SIZE);
        byte[] record = new byte[container.length + RECORD_HEADER_SIZE];

        System.arraycopy(container, 0, record, RECORD_HEADER_SIZE, container.length);

        Assertions.assertArrayEquals(content, BlteUtils.decode(record, RECORD_HEADER_SIZE, container.length));
    }

    @Test
    void decode_should_reject_chunk_shorter_than_declared_size() {
        byte[] content = createContent(LARGE_CONTENT_SIZE);
        byte[] container = createContainer(content, LARGE_CHUNK_SIZE);

        // The first chunk declares more decoded bytes than its zlib stream holds.
        ByteBuffer.wrap(container).putInt(HEADER_FIXED_SIZE + CHUNK_DECODED_SIZE_FIELD, LARGE_CHUNK_SIZE + 1);

        Assertions.assertThrows(IOException.class, () -> BlteUtils.decode(container));
    }
}
