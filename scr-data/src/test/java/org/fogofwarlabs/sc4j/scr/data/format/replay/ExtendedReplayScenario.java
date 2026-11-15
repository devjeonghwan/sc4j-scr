package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * Builds synthetic {@code seRS} replay files with tagged extension sections for the extension reader tests.
 */
final class ExtendedReplayScenario {
    /**
     * The file offset of the extension offset DWORD of a replay this scenario builds.
     */
    static final int EXTENSION_OFFSET_POSITION = 16;
    /**
     * The offset of the payload size DWORD inside an extension section of {@link #encodeExtension}, after the tag.
     */
    static final int EXTENSION_SIZE_POSITION = 4;

    private static final @NotNull String EXTENDED_REPLAY_IDENTIFIER = "seRS";
    private static final int HEADER_SIZE = 0x279;
    /**
     * The size of the player names written as body padding; it differs from the name section size, so the parser skips it.
     */
    private static final int PADDING_NAMES_SIZE = 0x300;
    private static final long CHECKSUM_COMPLEMENT = 0xFFFFFFFFL;
    private static final int SECTION_PREFIX_SIZE = 2 * Integer.BYTES;

    /**
     * Builds a replay with an empty command block and the given extension sections.
     *
     * @param extensions         the encoded extension sections, placed after the body; none leave the extension offset zero
     * @param includeBodyPadding {@code true} to add a map section, a name-sized section and trailing bytes before the extensions
     * @return the replay file bytes
     * @throws IOException if a section cannot be encoded
     */
    static byte @NotNull [] createReplay(byte @NotNull [] extensions, boolean includeBodyPadding) throws IOException {
        ByteArrayOutputStream replayData = new ByteArrayOutputStream();
        byte[] identifier = EXTENDED_REPLAY_IDENTIFIER.getBytes(StandardCharsets.US_ASCII);

        replayData.write(encodeSection(identifier, identifier));
        replayData.write(new byte[Integer.BYTES]);

        byte[] headerData = new byte[HEADER_SIZE];

        replayData.write(encodeSection(headerData, headerData));

        byte[] commandsData = {1, 0, 0, 0, 0};
        byte[] commandsSize = {5, 0, 0, 0};

        replayData.write(encodeSection(commandsSize, commandsSize));
        replayData.write(encodeSection(commandsData, commandsData));

        if (includeBodyPadding) {
            byte[] mapSize = {1, 0, 0, 0};
            byte[] mapData = {0};
            byte[] playerNames = new byte[PADDING_NAMES_SIZE];

            replayData.write(encodeSection(mapSize, mapSize));
            replayData.write(encodeSection(mapData, mapData));
            replayData.write(encodeSection(playerNames, playerNames));
            replayData.write(new byte[]{7, 6, 5, 4, 3});
        }

        int extensionOffset = extensions.length == 0 ? 0 : replayData.size();

        replayData.write(extensions);

        byte[] result = replayData.toByteArray();

        ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).putInt(EXTENSION_OFFSET_POSITION, extensionOffset);

        return result;
    }

    /**
     * Encodes a checksummed section from its decoded bytes and its stored chunks.
     *
     * @param decodedData the decoded section bytes the checksum covers
     * @param chunks      the stored chunks, raw or compressed
     * @return the encoded section
     * @throws IOException if a chunk cannot be written
     */
    static byte @NotNull [] encodeSection(byte @NotNull [] decodedData, byte @NotNull [] @NotNull ... chunks) throws IOException {
        CRC32 checksum = new CRC32();

        checksum.update(decodedData);

        ByteArrayOutputStream result = new ByteArrayOutputStream();

        result.write(ByteBuffer.allocate(SECTION_PREFIX_SIZE).order(ByteOrder.LITTLE_ENDIAN).putInt((int) (checksum.getValue() ^ CHECKSUM_COMPLEMENT))
                               .putInt(chunks.length).array());

        for (byte[] chunk : chunks) {
            result.write(ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(chunk.length).array());
            result.write(chunk);
        }

        return result.toByteArray();
    }

    /**
     * Encodes a tagged extension section: the tag, the payload size DWORD and the payload.
     *
     * @param identifier the four-character tag
     * @param payload    the payload, usually an encoded section
     * @return the encoded extension section
     * @throws IOException if the payload cannot be written
     */
    static byte @NotNull [] encodeExtension(@NotNull String identifier, byte @NotNull [] payload) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();

        result.write(identifier.getBytes(StandardCharsets.US_ASCII));
        result.write(ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.length).array());
        result.write(payload);

        return result.toByteArray();
    }

    /**
     * Compresses bytes into a zlib stream.
     *
     * @param data the bytes
     * @return the zlib stream
     * @throws IOException if the stream cannot be written
     */
    static byte @NotNull [] compress(byte @NotNull [] data) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();

        try (DeflaterOutputStream compressor = new DeflaterOutputStream(result)) {
            compressor.write(data);
        }

        return result.toByteArray();
    }

    /**
     * Creates a {@link ExtendedReplayScenario} instance.
     */
    private ExtendedReplayScenario() {
    }
}
