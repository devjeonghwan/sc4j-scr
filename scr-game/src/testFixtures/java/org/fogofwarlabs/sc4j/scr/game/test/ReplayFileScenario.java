package org.fogofwarlabs.sc4j.scr.game.test;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Encodes replay files of uncompressed sections from a header and data blocks, for the tests that read a replay built from their own inputs.
 */
public final class ReplayFileScenario {
    /**
     * The identifier section that starts a replay file.
     */
    private static final @NotNull String REPLAY_IDENTIFIER = "reRS";
    /**
     * The value whose exclusive or with a section's CRC-32 is the section checksum.
     */
    private static final long CHECKSUM_COMPLEMENT = 0xFFFFFFFFL;
    /**
     * The chunk count of a section with data: one uncompressed chunk.
     */
    private static final int DATA_CHUNK_COUNT = 1;
    /**
     * The checksum, chunk count and data size DWORDs before the data of a section.
     */
    private static final int SECTION_HEADER_SIZE = 3 * Integer.BYTES;
    /**
     * The checksum and chunk count DWORDs of an empty section.
     */
    private static final int EMPTY_SECTION_HEADER_SIZE = 2 * Integer.BYTES;

    /**
     * Encodes a replay file: the identifier section, the header section, then for each block a section that holds the block's byte length as a
     * DWORD followed by the block's own section.
     * <p>
     * A section is stored as its checksum, its chunk count and, when it has data, the data size and the data as one uncompressed chunk; an empty
     * section has no chunk.
     *
     * @param header the replay header
     * @param blocks the data blocks in file order, such as the command block and the map
     * @return the encoded replay file
     */
    public static byte @NotNull [] encode(byte @NotNull [] header, byte @NotNull [] @NotNull ... blocks) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writeSection(output, REPLAY_IDENTIFIER.getBytes(StandardCharsets.US_ASCII));
        writeSection(output, header);

        for (byte[] block : blocks) {
            writeSection(output, ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(block.length).array());
            writeSection(output, block);
        }

        return output.toByteArray();
    }

    private static void writeSection(@NotNull ByteArrayOutputStream output, byte @NotNull [] section) {
        CRC32 checksum = new CRC32();

        checksum.update(section);

        int sectionChecksum = (int) (checksum.getValue() ^ CHECKSUM_COMPLEMENT);

        if (section.length == 0) {
            output.writeBytes(ByteBuffer.allocate(EMPTY_SECTION_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                                        .putInt(sectionChecksum).putInt(0).array());
        } else {
            output.writeBytes(ByteBuffer.allocate(SECTION_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                                        .putInt(sectionChecksum).putInt(DATA_CHUNK_COUNT).putInt(section.length).array());
            output.writeBytes(section);
        }
    }


    /**
     * Creates a {@link ReplayFileScenario} instance.
     */
    private ReplayFileScenario() {
    }
}
