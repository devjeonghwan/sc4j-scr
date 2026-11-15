package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.text.TerminatedStringUtils;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqExploderUtils;
import org.fogofwarlabs.sc4j.scr.data.format.text.LegacyTextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Represents a parsed StarCraft: Brood War replay ({@code .rep}) file.
 * <p>
 * Supports the legacy (before {@code 1.18}), modern ({@code 1.18} to {@code 1.20}) and modern {@code 1.21+} container formats. Legacy
 * sections are decompressed per {@code 0x2000}-byte chunk with the PKWARE DCL codec, modern sections per chunk with zlib when the chunk
 * carries a zlib header.
 */
public final class ReplayFile {
    /**
     * The byte count of a build command payload.
     */
    public static final int BUILD_PAYLOAD = 7;
    /**
     * The message byte count following the sender byte in a chat command.
     */
    public static final int CHAT_MESSAGE_SIZE = 80;
    /**
     * The size of the file name buffer of a Save Game or Load Game command, terminator included: the name takes at most one
     * byte less.
     */
    public static final int SAVED_GAME_NAME_SIZE = 0x1C;

    /**
     * The replay identifier of a file whose identifier is followed by the extension offset DWORD.
     */
    static final @NotNull String EXTENDED_REPLAY_IDENTIFIER = "seRS";
    /**
     * The size of a section chunk before compression.
     */
    static final int CHUNK_SIZE = 0x2000;
    /**
     * The final complement that the original section checksum (CRC flags {@code 3}) omits.
     */
    static final long CHECKSUM_COMPLEMENT = 0xFFFFFFFFL;
    /**
     * The type byte of a recorded command.
     */
    static final int COMMAND_TYPE_SIZE = Byte.BYTES;
    /**
     * The player byte before each recorded command.
     */
    static final int COMMAND_PLAYER_SIZE = Byte.BYTES;

    /**
     * The {@code SKIN} tag of the skin extension section.
     */
    static final int SKIN_TAG = 0x4E494B53;
    /**
     * The {@code LMTS} tag of the limit extension section.
     */
    static final int LIMITS_TAG = 0x53544D4C;
    /**
     * The {@code BFIX} tag of the fix flags extension section.
     */
    static final int FIX_FLAGS_TAG = 0x58494642;
    /**
     * The {@code CCLR} tag of the custom color extension section.
     */
    static final int CUSTOM_COLORS_TAG = 0x524C4343;
    /**
     * The {@code GCFG} tag of the version block extension section.
     */
    static final int VERSION_BLOCK_TAG = 0x47464347;
    /**
     * The {@code XSPD} tag of the speed override extension section, which this client writes for a game with a speed override.
     */
    static final int SPEED_OVERRIDE_TAG = 0x44505358;

    private static final int BYTE_MASK = 0xFF;
    private static final long DWORD_MASK = 0xFFFFFFFFL;
    private static final int RESERVED_HEADER_BYTE_SIZE = Byte.BYTES;

    private static final int REPLAY_IDENTIFIER_SIZE = 4;
    /**
     * The replay identifier of a file without extension sections.
     */
    private static final @NotNull String REPLAY_IDENTIFIER = "reRS";

    private static final int INFLATE_BUFFER_SIZE = 8192;

    /**
     * The header of an extension section: the tag DWORD and the size DWORD.
     */
    private static final int EXTENSION_HEADER_SIZE = 8;
    /**
     * The decoded size of the limit section: seven DWORDs.
     */
    private static final int LIMIT_SECTION_SIZE = ReplayLimits.VALUE_COUNT * Integer.BYTES;
    /**
     * The decoded size of the speed override section: the selector DWORD.
     */
    private static final int SPEED_OVERRIDE_SECTION_SIZE = Integer.BYTES;

    /**
     * The header layout, in the read order of {@link #parseHeader(byte[])}.
     */
    private static final int AFTER_FRAMES_SKIP = 3;
    private static final int AFTER_SEED_SKIP = 12;
    private static final int GAME_TITLE_LENGTH = 28;
    private static final int AFTER_SUBTYPE_SKIP = 8;
    private static final int MAP_TITLE_LENGTH = 26;
    private static final int AFTER_MAP_TITLE_SKIP = 38;
    private static final int SUBTYPE_VALUE_OFFSET = 0x85;
    private static final int VICTORY_CONDITION_OFFSET = 0x89;
    private static final int RESOURCE_TYPE_OFFSET = 0x8A;
    private static final int STANDARD_UNIT_STATS_OFFSET = 0x8B;
    private static final int FOG_OF_WAR_OFFSET = 0x8C;
    private static final int INITIAL_UNITS_OFFSET = 0x8D;
    private static final int FIXED_POSITIONS_OFFSET = 0x8E;
    private static final int RESTRICTION_FLAGS_OFFSET = 0x8F;
    private static final int ALLIES_OFFSET = 0x90;
    private static final int TEAMS_OFFSET = 0x91;
    private static final int CHEATS_OFFSET = 0x92;
    private static final int TOURNAMENT_MODE_OFFSET = 0x93;
    private static final int VICTORY_CONDITION_VALUE_OFFSET = 0x94;
    private static final int STARTING_MINERALS_OFFSET = 0x98;
    private static final int STARTING_GAS_OFFSET = 0x9C;

    /**
     * Reads bytes from a byte array with a cursor.
     */
    private static final class Reader {
        private final byte @NotNull [] data;
        private final int endPosition;

        private int position;

        /**
         * Creates a {@link Reader} instance.
         *
         * @param data the data to read
         */
        private Reader(byte @NotNull [] data) {
            this(data, 0, data.length);
        }

        private Reader(byte @NotNull [] data, int position, int endPosition) {
            this.data        = data;
            this.endPosition = endPosition;

            this.position = position;
        }

        private int getPosition() {
            return this.position;
        }

        private int getRemaining() {
            return this.endPosition - this.position;
        }


        private int readUnsigned8() throws IOException {
            if (this.position >= this.endPosition) {
                throw Exceptions.newIOException("The replay data is truncated.");
            }

            return this.data[this.position++] & BYTE_MASK;
        }

        private int readUnsigned16() throws IOException {
            return this.readUnsigned8() | (this.readUnsigned8() << Byte.SIZE);
        }

        private int readInt32() throws IOException {
            return this.readUnsigned8() | (this.readUnsigned8() << Byte.SIZE) |
                   (this.readUnsigned8() << Short.SIZE) | (this.readUnsigned8() << (Integer.SIZE - Byte.SIZE));
        }

        private long readUnsigned32() throws IOException {
            return this.readInt32() & DWORD_MASK;
        }

        private byte @NotNull [] readBytes(int length) throws IOException {
            if (length < 0 || length > this.getRemaining()) {
                throw Exceptions.newIOException("The replay data is truncated.");
            }

            byte[] result = new byte[length];

            System.arraycopy(this.data, this.position, result, 0, length);
            this.position += length;

            return result;
        }

        private void skip(int length) throws IOException {
            if (length < 0 || length > this.getRemaining()) {
                throw Exceptions.newIOException("The replay data is truncated.");
            }

            this.position += length;
        }
    }

    /**
     * Creates a {@link ReplayFile} instance for a game recorded by this client, as {@link #parse} would read it after
     * {@link ReplayWriter} wrote it.
     * <p>
     * The command list is retained, not copied: a live game appends its executed commands to it, and the replay
     * session consumes them in the same order a recorded replay supplies them.
     *
     * @param header       the {@code 0x279}-byte header section
     * @param commands     the command list, retained and extended by the caller
     * @param mapData      the embedded {@code scenario.chk} bytes
     * @param limits       the seven {@code LMTS} values
     * @param versionBlock the {@code 25}-byte {@code GCFG} block
     * @param fixFlags     the {@code BFIX} QWORD
     * @return the created {@link ReplayFile} instance
     * @throws IOException              if the header section is malformed
     * @throws IllegalArgumentException if the limits tuple or version block has the wrong size
     */
    public static @NotNull ReplayFile create(byte @NotNull [] header,
                                             @NotNull List<ReplayCommand> commands,
                                             byte @NotNull [] mapData,
                                             int @NotNull [] limits,
                                             byte @NotNull [] versionBlock, long fixFlags) throws IOException {
        if (header.length != ReplayHeaderBuilder.HEADER_SIZE) {
            throw Exceptions.newIOException("The replay header section has {0} bytes, not {1}.", header.length, ReplayHeaderBuilder.HEADER_SIZE);
        }

        byte[] flags = new byte[ReplayGameVersion.FLAGS_SIZE];

        LittleEndianUtils.writeInt64(flags, 0, fixFlags);

        ReplayFile file = new ReplayFile(ReplayContainerFormat.MODERN_121,
                                         parseHeader(header),
                                         new ReplayLimits(limits),
                                         ReplayGameVersion.fromSections(versionBlock, flags),
                                         commands, null,
                                         mapData,
                                         0);

        file.headerSection = header.clone();

        return file;
    }

    /**
     * Tests whether content starts like a replay file: its first section decodes to the replay ID {@code reRS} (or {@code seRS} of 1.21 and
     * later). The later sections may still be damaged.
     *
     * @param data the file content
     * @return {@code true} if the content starts with a replay ID section, {@code false} otherwise
     */
    public static boolean isReplay(byte @NotNull [] data) {
        String replayIdentifier;

        try {
            byte[] section = readSection(new Reader(data), ReplayContainerFormat.detect(data), REPLAY_IDENTIFIER_SIZE);

            replayIdentifier = new String(section, StandardCharsets.US_ASCII);
        } catch (IOException | RuntimeException exception) {
            // Content of any other kind fails the section codec somewhere: it is no replay.
            return false;
        }

        return replayIdentifier.equals(REPLAY_IDENTIFIER) || replayIdentifier.equals(EXTENDED_REPLAY_IDENTIFIER);
    }

    /**
     * Decodes the raw {@code 0x279}-byte header section, for diagnostics of fields the parser does not expose.
     *
     * @param data the replay file content
     * @return the decoded header section
     * @throws IOException if the content is not a valid replay file
     */
    public static byte @NotNull [] readHeaderSection(byte @NotNull [] data) throws IOException {
        ReplayContainerFormat format = ReplayContainerFormat.detect(data);
        Reader reader = new Reader(data);
        byte[] replayIdentifier = readSection(reader, format, REPLAY_IDENTIFIER_SIZE);

        if (new String(replayIdentifier, StandardCharsets.US_ASCII).equals(EXTENDED_REPLAY_IDENTIFIER)) {
            reader.readUnsigned32();
        }

        return readSection(reader, format, ReplayHeaderBuilder.HEADER_SIZE);
    }

    /**
     * Decodes consecutive sections of the replay section codec from the start of a file.
     *
     * @param data   the file contents
     * @param legacy {@code true} for the codec before {@code 1.18} (PKWare chunks), {@code false} for the zlib codec
     * @param sizes  the decoded size of each section, in file order
     * @return the decoded sections, in file order
     * @throws IOException if a section is truncated, malformed or fails its checksum
     */
    public static @NotNull List<byte[]> readSections(byte @NotNull [] data, boolean legacy, int @NotNull ... sizes) throws IOException {
        Reader reader = new Reader(data);
        List<byte[]> sections = new ArrayList<>(sizes.length);

        for (int size : sizes) {
            sections.add(readSection(reader, legacy ? ReplayContainerFormat.LEGACY : ReplayContainerFormat.MODERN, size));
        }

        return sections;
    }

    /**
     * Creates a {@link ReplayFile} instance from replay file content.
     *
     * @param data the replay file content
     * @return the created {@link ReplayFile} instance
     * @throws IOException              if the content is not a valid replay file
     * @throws IllegalArgumentException if a modern commands or map size is negative
     */
    public static @NotNull ReplayFile parse(byte @NotNull [] data) throws IOException {
        ReplayContainerFormat format = ReplayContainerFormat.detect(data);
        Reader reader = new Reader(data);

        // Section 0: replay ID.
        byte[] replayIdentifier = readSection(reader, format, REPLAY_IDENTIFIER_SIZE);
        String replayIdentifierText = new String(replayIdentifier, StandardCharsets.US_ASCII);

        if (!replayIdentifierText.equals(REPLAY_IDENTIFIER) && !replayIdentifierText.equals(EXTENDED_REPLAY_IDENTIFIER)) {
            throw Exceptions.newIOException("The file is not a replay file: invalid replay ID {0}", replayIdentifierText);
        }

        ReplayLimits limits = new ReplayLimits();
        ReplayGameVersion gameVersion = ReplayGameVersion.absent();
        int speedOverrideIndex = 0;

        // The seRS DWORD is an absolute extension offset, outside the encoded sections.
        if (replayIdentifierText.equals(EXTENDED_REPLAY_IDENTIFIER)) {
            long extensionOffset = reader.readUnsigned32();

            if (extensionOffset != 0) {
                if (extensionOffset < reader.getPosition() || extensionOffset > data.length) {
                    throw Exceptions.newIOException("The replay extension offset is outside the file body.");
                }

                limits             = readLimits(new Reader(data, (int) extensionOffset, data.length));
                gameVersion        = readGameVersion(new Reader(data, (int) extensionOffset, data.length));
                speedOverrideIndex = readSpeedOverrideIndex(new Reader(data, (int) extensionOffset, data.length));
                reader             = new Reader(data, reader.getPosition(), (int) extensionOffset);
            }
        }

        // Section 1: header.
        byte[] headerData = readSection(reader, format, ReplayHeaderBuilder.HEADER_SIZE);
        ReplayHeader header = parseHeader(headerData);

        // Section 2: commands (size-prefixed).
        int commandsSize = readInt32Section(reader, format);
        // The native writer omits the command data section when the history is empty.
        byte[] commandsData = commandsSize == 0 && format == ReplayContainerFormat.MODERN_121 ? new byte[0]
                                                                                              : readSection(reader, format, commandsSize);
        List<ReplayCommand> commands = new ArrayList<>();
        List<ReplayCommandBlock> commandBlocks = new ArrayList<>();
        int unknownTypeCount = parseCommands(commandsData, commands, commandBlocks);

        // Section 3: map data (size-prefixed).
        byte[] mapData = null;

        if (reader.getRemaining() > 0) {
            int mapDataSize = readInt32Section(reader, format);

            mapData = readSection(reader, format, mapDataSize);
        }

        // Section 4: player names (modern formats only); without them the header records name the players.
        byte[] nameSection = format != ReplayContainerFormat.LEGACY && reader.getRemaining() > 0 ? readNameSection(reader, format) : null;
        ReplayFile file = new ReplayFile(format, header, limits, gameVersion, commands, List.copyOf(commandBlocks), mapData, unknownTypeCount);

        if (headerData.length == ReplayHeaderBuilder.HEADER_SIZE) {
            file.headerSection = headerData.clone();
        }

        file.speedOverrideIndex = speedOverrideIndex;

        if (nameSection != null) {
            file.playerNames = new byte[ReplayHeaderBuilder.PLAYER_COUNT][];

            for (int index = 0; index < ReplayHeaderBuilder.PLAYER_COUNT; index++) {
                int offset = index * ReplayWriter.NAME_RECORD_SIZE;
                int length = TerminatedStringUtils.getTextLength(nameSection, offset);

                file.playerNames[index] = TerminatedStringUtils.cutText(Arrays.copyOfRange(nameSection, offset, offset + length),
                                                                        ReplayWriter.NAME_RECORD_SIZE);
            }
        }

        return file;
    }

    /**
     * Reads the player name section: the {@link ReplayWriter#NAMES_SIZE} bytes of the twelve name records. A section that
     * cannot be read or decodes to another size is not read; the header records then name the players.
     *
     * @param reader the reader at the section
     * @param format the container format
     * @return the section bytes, or {@code null} when the section is not read
     */
    private static byte @Nullable [] readNameSection(@NotNull Reader reader, @NotNull ReplayContainerFormat format) {
        try {
            byte[] section = readSection(reader, format, ReplayWriter.NAMES_SIZE);

            return section.length == ReplayWriter.NAMES_SIZE ? section : null;
        } catch (IOException exception) {
            return null;
        }
    }

    private static int readInt32Section(@NotNull Reader reader, @NotNull ReplayContainerFormat format) throws IOException {
        byte[] sizeData = readSection(reader, format, Integer.BYTES);
        Reader sizeReader = new Reader(sizeData);

        return sizeReader.readInt32();
    }

    private static byte @NotNull [] readSection(@NotNull Reader reader, @NotNull ReplayContainerFormat format, int size) throws IOException {
        long expectedChecksum = reader.readUnsigned32();
        int chunkCount = reader.readInt32();
        byte[] section = format == ReplayContainerFormat.LEGACY ? readLegacySection(reader, size, chunkCount)
                                                                : readModernSection(reader, size, chunkCount);
        CRC32 checksum = new CRC32();

        checksum.update(section);

        // The original section reader uses CRC flags 3, omitting the final complement.
        if ((checksum.getValue() ^ CHECKSUM_COMPLEMENT) != expectedChecksum) {
            throw Exceptions.newIOException("The replay section checksum does not match.");
        }

        return section;
    }

    private static byte @NotNull [] readLegacySection(@NotNull Reader reader, int size, int chunkCount) throws IOException {
        byte[] result = new byte[size];
        int resultOffset = 0;

        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int length = reader.readInt32();
            int rawLength = Math.min(size - chunkIndex * CHUNK_SIZE, CHUNK_SIZE);
            byte[] chunk = reader.readBytes(length);

            if (length == rawLength) {
                System.arraycopy(chunk, 0, result, resultOffset, length);
                resultOffset += length;
            } else {
                byte[] decompressedChunk = MpqExploderUtils.explode(chunk, rawLength);

                System.arraycopy(decompressedChunk, 0, result, resultOffset, rawLength);
                resultOffset += rawLength;
            }
        }

        return result;
    }

    private static byte @NotNull [] readModernSection(@NotNull Reader reader, int size, int chunkCount) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream(size);

        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int length = reader.readInt32();
            byte[] chunk = reader.readBytes(length);
            int rawLength = Math.min(size - chunkIndex * CHUNK_SIZE, CHUNK_SIZE);

            // The section reader copies a block whose stored length is its raw length and inflates any other; the writer
            // stores a block raw whenever deflate does not shrink it, so a raw block may start with bytes that look like
            // a zlib header.
            if (length != rawLength) {
                byte[] decompressedChunk = inflate(chunk, Integer.MAX_VALUE);

                result.write(decompressedChunk, 0, decompressedChunk.length);
            } else {
                result.write(chunk, 0, chunk.length);
            }
        }

        return result.toByteArray();
    }

    private static byte @NotNull [] inflate(byte @NotNull [] compressedData, int maximumSize) throws IOException {
        ByteArrayOutputStream decompressedData;

        try (Inflater inflater = new Inflater()) {
            decompressedData = new ByteArrayOutputStream();
            byte[] buffer = new byte[INFLATE_BUFFER_SIZE];

            inflater.setInput(compressedData);

            try {
                while (!inflater.finished()) {
                    int producedSize = inflater.inflate(buffer);

                    if (producedSize == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                        throw Exceptions.newIOException("The zlib stream is truncated.");
                    }

                    if (producedSize > maximumSize - decompressedData.size()) {
                        throw Exceptions.newIOException("The inflated data exceeds the output capacity.");
                    }

                    decompressedData.write(buffer, 0, producedSize);
                }
            } catch (DataFormatException exception) {
                throw Exceptions.newIOException(exception, "The zlib stream is malformed.");
            }
        }

        return decompressedData.toByteArray();
    }

    private static @NotNull ReplayLimits readLimits(@NotNull Reader reader) throws IOException {
        ReplayLimits limits = new ReplayLimits();

        while (reader.getRemaining() >= EXTENSION_HEADER_SIZE) {
            int sectionIdentifier = reader.readInt32();
            int sectionSize = reader.readInt32();

            if (sectionSize < 0) {
                throw Exceptions.newIOException("Negative replay extension lengths are not supported.");
            }

            if (sectionIdentifier == LIMITS_TAG) {
                if (sectionSize > reader.getRemaining()) {
                    throw Exceptions.newIOException("The replay limit section is truncated.");
                }

                Reader sectionReader = new Reader(reader.data, reader.getPosition(), reader.getPosition() + sectionSize);
                Reader decodedReader = new Reader(readFixedSection(sectionReader, LIMIT_SECTION_SIZE));
                int[] requestedValues = new int[ReplayLimits.VALUE_COUNT];

                for (int valueIndex = 0; valueIndex < requestedValues.length; valueIndex++) {
                    requestedValues[valueIndex] = decodedReader.readInt32();
                }

                limits = new ReplayLimits(requestedValues);
            }

            if (sectionSize > reader.getRemaining()) {
                break;
            }

            reader.skip(sectionSize);
        }

        return limits;
    }

    /**
     * Reads the tagged {@code GCFG} version block and {@code BFIX} flags extension sections.
     *
     * @param reader the reader at the extension sections
     * @return the decoded version values
     * @throws IOException if a section is malformed
     */
    private static @NotNull ReplayGameVersion readGameVersion(@NotNull Reader reader) throws IOException {
        byte[] block = null;
        byte[] flags = null;

        while (reader.getRemaining() >= EXTENSION_HEADER_SIZE) {
            int sectionIdentifier = reader.readInt32();
            int sectionSize = reader.readInt32();

            if (sectionSize < 0 || sectionSize > reader.getRemaining()) {
                break;
            }

            if (sectionIdentifier == VERSION_BLOCK_TAG || sectionIdentifier == FIX_FLAGS_TAG) {
                Reader sectionReader = new Reader(reader.data, reader.getPosition(), reader.getPosition() + sectionSize);
                byte[] payload = readFixedSection(sectionReader, sectionIdentifier == VERSION_BLOCK_TAG ? ReplayGameVersion.VERSION_BLOCK_SIZE
                                                                                                        : ReplayGameVersion.FLAGS_SIZE);

                if (sectionIdentifier == VERSION_BLOCK_TAG) {
                    block = payload;
                } else {
                    flags = payload;
                }
            }

            reader.skip(sectionSize);
        }

        return ReplayGameVersion.fromSections(block, flags);
    }

    /**
     * Reads the speed override selector of the tagged {@code XSPD} extension section.
     *
     * @param reader the reader at the extension sections
     * @return the selector, {@code 0} when the section is absent
     * @throws IOException if the section is malformed or holds a negative selector
     */
    private static int readSpeedOverrideIndex(@NotNull Reader reader) throws IOException {
        int speedOverrideIndex = 0;

        while (reader.getRemaining() >= EXTENSION_HEADER_SIZE) {
            int sectionIdentifier = reader.readInt32();
            int sectionSize = reader.readInt32();

            if (sectionSize < 0 || sectionSize > reader.getRemaining()) {
                break;
            }

            if (sectionIdentifier == SPEED_OVERRIDE_TAG) {
                Reader sectionReader = new Reader(reader.data, reader.getPosition(), reader.getPosition() + sectionSize);

                speedOverrideIndex = LittleEndianUtils.readInt32(readFixedSection(sectionReader, SPEED_OVERRIDE_SECTION_SIZE), 0);
            }

            reader.skip(sectionSize);
        }

        if (speedOverrideIndex < 0) {
            throw Exceptions.newIOException("The replay speed override selector is negative: {0}", speedOverrideIndex);
        }

        return speedOverrideIndex;
    }

    private static byte @NotNull [] readFixedSection(@NotNull Reader reader, int size) throws IOException {
        long expectedChecksum = reader.readUnsigned32();
        int chunkCount = reader.readInt32();
        byte[] result = new byte[size];
        int resultOffset = 0;

        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int length = reader.readInt32();

            if (length < 0 || length > result.length - resultOffset) {
                throw Exceptions.newIOException("The replay limit chunk exceeds the remaining output capacity.");
            }

            byte[] chunk = reader.readBytes(length);
            int rawLength = chunkIndex == 0 ? result.length : CHUNK_SIZE;

            if (length != rawLength) {
                try {
                    chunk = inflate(chunk, CHUNK_SIZE);
                } catch (IOException exception) {
                    chunk = MpqExploderUtils.explodeUpTo(chunk, CHUNK_SIZE);
                }

                if (chunk.length == 0 || chunk.length > result.length - resultOffset) {
                    throw Exceptions.newIOException("The replay limit chunk has an invalid decoded size.");
                }
            }

            System.arraycopy(chunk, 0, result, resultOffset, chunk.length);
            resultOffset += chunk.length;
        }

        if (resultOffset != result.length) {
            if (size == LIMIT_SECTION_SIZE) {
                throw Exceptions.newIOException("The replay limit section must decode to 28 bytes.");
            }

            throw Exceptions.newIOException("The replay extension section must decode to {0} bytes.", size);
        }

        CRC32 checksum = new CRC32();

        checksum.update(result);

        if ((checksum.getValue() ^ CHECKSUM_COMPLEMENT) != expectedChecksum) {
            throw Exceptions.newIOException("The replay section checksum does not match.");
        }

        return result;
    }

    private static @NotNull ReplayHeader parseHeader(byte @NotNull [] data) throws IOException {
        Reader reader = new Reader(data);
        int engine = reader.readUnsigned8();
        long frames = reader.readUnsigned32();

        reader.skip(AFTER_FRAMES_SKIP);

        long randomSeed = reader.readUnsigned32();

        reader.skip(AFTER_SEED_SKIP);

        String title = readText(reader, GAME_TITLE_LENGTH);
        int mapWidth = reader.readUnsigned16();
        int mapHeight = reader.readUnsigned16();

        reader.skip(RESERVED_HEADER_BYTE_SIZE);

        int availableSlotsCount = reader.readUnsigned8();
        int speed = reader.readUnsigned8();

        reader.skip(RESERVED_HEADER_BYTE_SIZE);

        int type = reader.readUnsigned16();
        int subType = reader.readUnsigned16();

        reader.skip(AFTER_SUBTYPE_SKIP);

        String host = readText(reader, ReplayHeaderBuilder.HOST_NAME_SIZE);

        reader.skip(RESERVED_HEADER_BYTE_SIZE);

        String map = readText(reader, MAP_TITLE_LENGTH);

        reader.skip(AFTER_MAP_TITLE_SKIP);

        List<ReplayHeader.ReplayPlayer> players = new ArrayList<>(ReplayHeaderBuilder.PLAYER_COUNT);

        for (int playerIndex = 0; playerIndex < ReplayHeaderBuilder.PLAYER_COUNT; playerIndex++) {
            int commandPlayerIndex = reader.readInt32();
            int recordedIdentifier = reader.readInt32();
            int playerType = reader.readUnsigned8();
            int race = reader.readUnsigned8();
            int team = reader.readUnsigned8();
            String name = readText(reader, ReplayHeaderBuilder.PLAYER_NAME_SIZE);

            players.add(new ReplayHeader.ReplayPlayer(commandPlayerIndex, recordedIdentifier, playerType, race, team, name));
        }

        int[] colors = new int[ReplayHeaderBuilder.COLOR_COUNT];

        for (int colorIndex = 0; colorIndex < ReplayHeaderBuilder.COLOR_COUNT; colorIndex++) {
            colors[colorIndex] = reader.readInt32();
        }

        boolean[] createMeleeUnitsForPlayer = new boolean[ReplayHeaderBuilder.MELEE_UNIT_FLAG_COUNT];

        for (int playerIndex = 0; playerIndex < createMeleeUnitsForPlayer.length; playerIndex++) {
            createMeleeUnitsForPlayer[playerIndex] = reader.readUnsigned8() != 0;
        }

        ReplaySettings settings = new ReplaySettings(LittleEndianUtils.readUnsigned32(data, SUBTYPE_VALUE_OFFSET),
                                                     data[VICTORY_CONDITION_OFFSET] & BYTE_MASK,
                                                     data[RESOURCE_TYPE_OFFSET] & BYTE_MASK,
                                                     data[STANDARD_UNIT_STATS_OFFSET] & BYTE_MASK,
                                                     data[FOG_OF_WAR_OFFSET] & BYTE_MASK,
                                                     data[INITIAL_UNITS_OFFSET] & BYTE_MASK,
                                                     data[FIXED_POSITIONS_OFFSET] & BYTE_MASK,
                                                     data[RESTRICTION_FLAGS_OFFSET] & BYTE_MASK,
                                                     data[ALLIES_OFFSET] & BYTE_MASK, data[TEAMS_OFFSET] & BYTE_MASK,
                                                     data[CHEATS_OFFSET] & BYTE_MASK,
                                                     data[TOURNAMENT_MODE_OFFSET] & BYTE_MASK,
                                                     LittleEndianUtils.readUnsigned32(data, VICTORY_CONDITION_VALUE_OFFSET),
                                                     LittleEndianUtils.readUnsigned32(data, STARTING_MINERALS_OFFSET),
                                                     LittleEndianUtils.readUnsigned32(data, STARTING_GAS_OFFSET),
                                                     createMeleeUnitsForPlayer);

        int campaignMission = LittleEndianUtils.readUnsigned16(data, ReplayHeaderBuilder.CAMPAIGN_MISSION_OFFSET);

        return new ReplayHeader(engine,
                                frames, randomSeed,
                                title,
                                mapWidth, mapHeight,
                                availableSlotsCount,
                                speed,
                                type, subType,
                                host,
                                map,
                                players, colors,
                                settings,
                                campaignMission);
    }

    private static @NotNull String readText(@NotNull Reader reader, int length) throws IOException {
        byte[] text = reader.readBytes(length);

        return LegacyTextUtils.decode(text, 0, TerminatedStringUtils.getTextLength(text));
    }

    /**
     * Splits the command section into blocks and commands as the replay loader does: each block's commands follow its
     * header until the commands' bytes, counted with their player bytes in a BYTE, reach the block size. The loader's
     * cursor continues after the last command, not at the end the block size names.
     * <p>
     * A command type without a stored length skips the rest of its block and is counted.
     *
     * @param data     the command section payload
     * @param commands receives the commands in stream order
     * @param blocks   receives the command blocks in stream order
     * @return the number of skipped command types
     */
    private static int parseCommands(byte @NotNull [] data, @NotNull List<ReplayCommand> commands, @NotNull List<ReplayCommandBlock> blocks) throws IOException {
        Reader reader = new Reader(data);
        int unknownTypeCount = 0;

        while (reader.getRemaining() > 0) {
            long frame = reader.readUnsigned32();
            int blockSize = reader.readUnsigned8();
            int blockEnd = Math.min(reader.getPosition() + blockSize, data.length);
            int firstCommandIndex = commands.size();
            int consumedSize = 0;

            while (consumedSize < blockSize) {
                int playerIdentifier = reader.readUnsigned8();
                int typeOffset = reader.getPosition();
                int typeIdentifier = reader.readUnsigned8();
                int length = ReplayCommandUtils.getStoredLength(data, typeOffset);

                if (length < 0) {
                    // Unknown command type: skip to the end of the command block.
                    unknownTypeCount++;
                    reader.position = blockEnd;

                    break;
                }

                byte[] payload = reader.readBytes(length - COMMAND_TYPE_SIZE);

                commands.add(new ReplayCommand(frame, playerIdentifier, typeIdentifier, payload));
                consumedSize = (consumedSize + length + COMMAND_PLAYER_SIZE) & BYTE_MASK;
            }

            blocks.add(new ReplayCommandBlock(frame, blockSize, firstCommandIndex, commands.size() - firstCommandIndex));
        }

        return unknownTypeCount;
    }


    private final @NotNull ReplayContainerFormat format;
    private final @NotNull ReplayHeader header;
    private final @NotNull ReplayLimits limits;
    private final @NotNull ReplayGameVersion gameVersion;
    private final @NotNull List<ReplayCommand> commands;
    /**
     * The command blocks of the parsed command section, or {@code null} for a recording created in memory, whose blocks
     * follow its growing command list ({@link ReplayWriter#splitBlocks}).
     */
    private final @Nullable List<ReplayCommandBlock> commandBlocks;
    private final byte @Nullable [] mapData;
    private final int unknownTypeCount;

    /**
     * The raw {@code 0x279}-byte header section, or {@code null} for a legacy file whose section has another size.
     */
    private byte @Nullable [] headerSection;
    /**
     * The game's name table: the name bytes of the replay's name section or of a live game, each without terminator, or
     * {@code null} when only the header records' names are known.
     */
    private byte @Nullable [] @NotNull [] playerNames;
    /**
     * Whether this in-memory recording starts from a saved game instead of initializing a new map.
     */
    private boolean savedGameStart;
    /**
     * The game's speed override selector: the {@code XSPD} section's or a live game's, {@code 0} for none.
     */
    private int speedOverrideIndex;

    /**
     * Creates a {@link ReplayFile} instance.
     *
     * @param format           the replay container format
     * @param header           the replay header
     * @param limits           the requested and effective resource limits
     * @param gameVersion      the {@code GCFG} version block and {@code BFIX} flags
     * @param commands         the player commands
     * @param commandBlocks    the command blocks of the parsed command section, or {@code null} for a recording created in memory
     * @param mapData          the raw map data section, or {@code null} if absent
     * @param unknownTypeCount the number of skipped unknown command types
     */
    private ReplayFile(@NotNull ReplayContainerFormat format,
                       @NotNull ReplayHeader header,
                       @NotNull ReplayLimits limits,
                       @NotNull ReplayGameVersion gameVersion,
                       @NotNull List<ReplayCommand> commands, @Nullable List<ReplayCommandBlock> commandBlocks,
                       byte @Nullable [] mapData,
                       int unknownTypeCount) {
        this.format           = format;
        this.header           = header;
        this.limits           = limits;
        this.gameVersion      = gameVersion;
        this.commands         = commands;
        this.commandBlocks    = commandBlocks;
        this.mapData          = mapData;
        this.unknownTypeCount = unknownTypeCount;

        this.headerSection      = null;
        this.playerNames        = null;
        this.savedGameStart     = false;
        this.speedOverrideIndex = 0;
    }

    /**
     * @return the replay container format
     */
    public @NotNull ReplayContainerFormat getFormat() {
        return this.format;
    }

    /**
     * @return the replay header
     */
    public @NotNull ReplayHeader getHeader() {
        return this.header;
    }

    /**
     * @return the requested and effective original replay resource limits
     */
    public @NotNull ReplayLimits getLimits() {
        return this.limits;
    }

    /**
     * @return the {@code GCFG} version block and {@code BFIX} flags, zero when absent
     */
    public @NotNull ReplayGameVersion getGameVersion() {
        return this.gameVersion;
    }

    /**
     * @return the player commands in stream order
     */
    public @NotNull List<ReplayCommand> getCommands() {
        return this.commands;
    }

    /**
     * Returns the blocks of the command stream, each a range of {@link #getCommands()}: the parsed section's blocks, or
     * for a recording created in memory the blocks its commands are written in.
     *
     * @return the command blocks in stream order
     */
    public @NotNull List<ReplayCommandBlock> getCommandBlocks() {
        return this.commandBlocks != null ? this.commandBlocks : ReplayWriter.splitBlocks(this.commands);
    }

    /**
     * @return the raw map data section, or {@code null} if absent
     */
    public byte @Nullable [] getMapData() {
        return this.mapData;
    }

    /**
     * @return the number of skipped unknown command types
     */
    public int getUnknownTypeCount() {
        return this.unknownTypeCount;
    }

    /**
     * @return a copy of the raw {@code 0x279}-byte header section, or {@code null} when it is not available
     */
    public byte @Nullable [] getHeaderSection() {
        return this.headerSection == null ? null : this.headerSection.clone();
    }

    /**
     * Returns the players' display names: the text of the name table's bytes ({@link #getPlayerNameBytes}).
     *
     * @return twelve names in record order
     */
    public @NotNull List<String> getPlayerNames() {
        byte[][] nameBytes = this.getPlayerNameBytes();
        List<String> names = new ArrayList<>(nameBytes.length);

        for (byte[] name : nameBytes) {
            names.add(TerminatedStringUtils.readText(name));
        }

        return names;
    }

    /**
     * Returns the game's name table, the names the game's messages name the players with: the names of the replay's name
     * section or of a live game ({@link #setPlayerNames}), else the header records' names in UTF-8, each cut to the
     * {@code 0x5f} bytes a name record holds.
     *
     * @return twelve name byte arrays in record order, without terminator
     */
    public byte @NotNull [] @NotNull [] getPlayerNameBytes() {
        byte[][] names = new byte[ReplayHeaderBuilder.PLAYER_COUNT][];

        for (int index = 0; index < ReplayHeaderBuilder.PLAYER_COUNT; index++) {
            if (this.playerNames != null) {
                names[index] = this.playerNames[index].clone();
            } else {
                String name = index < this.header.getPlayers().size() ? this.header.getPlayers().get(index).getName() : "";

                names[index] = TerminatedStringUtils.cutText(name, ReplayWriter.NAME_RECORD_SIZE);
            }
        }

        return names;
    }

    /**
     * Sets the game's name table of a live game: each name cut to the {@code 0x5f} bytes a name record holds.
     *
     * @param playerNames the twelve name byte arrays in record order, without terminator
     * @throws IllegalArgumentException if there are not twelve names; the existing name table is retained
     */
    public void setPlayerNames(byte @NotNull [] @NotNull [] playerNames) {
        if (playerNames.length != ReplayHeaderBuilder.PLAYER_COUNT) {
            throw Exceptions.newIllegalArgumentException("The name table takes {0} names; {1} were given.",
                                                         ReplayHeaderBuilder.PLAYER_COUNT, playerNames.length);
        }

        byte[][] names = new byte[ReplayHeaderBuilder.PLAYER_COUNT][];

        for (int index = 0; index < ReplayHeaderBuilder.PLAYER_COUNT; index++) {
            names[index] = TerminatedStringUtils.cutText(playerNames[index], ReplayWriter.NAME_RECORD_SIZE);
        }

        this.playerNames = names;
    }

    /**
     * @return {@code true} if the game starts from a saved game, {@code false} otherwise
     */
    public boolean isSavedGameStart() {
        return this.savedGameStart;
    }

    /**
     * @return the game's speed override selector: the {@code XSPD} section's, or the one a live game set ({@link #setSpeedOverrideIndex}),
     * {@code 0} for none
     */
    public int getSpeedOverrideIndex() {
        return this.speedOverrideIndex;
    }

    /**
     * Sets the speed override selector of a live game, which its game start and its written replay take.
     *
     * @param speedOverrideIndex the selector, {@code 0} for none
     * @throws IllegalArgumentException if the selector is negative
     */
    public void setSpeedOverrideIndex(int speedOverrideIndex) {
        if (speedOverrideIndex < 0) {
            throw Exceptions.newIllegalArgumentException("The speed override selector must not be negative: {0}", speedOverrideIndex);
        }

        this.speedOverrideIndex = speedOverrideIndex;
    }


    /**
     * Marks the game this recording runs as one that starts from a saved game.
     */
    public void markSavedGameStart() {
        this.savedGameStart = true;
    }
}
