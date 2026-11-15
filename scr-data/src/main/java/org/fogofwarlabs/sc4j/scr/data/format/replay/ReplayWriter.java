package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.text.TerminatedStringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Writes a modern {@code 1.21+} replay container with checksummed, chunked sections and tagged extensions.
 * Each chunk uses zlib compression when it produces fewer bytes than the raw payload.
 */
public final class ReplayWriter {
    /**
     * The size of the player name section.
     */
    public static final int NAMES_SIZE = 0x480;
    /**
     * The size of a name record of the player name section, terminator included.
     */
    public static final int NAME_RECORD_SIZE = 0x60;
    /**
     * The size of the {@code SKIN} extension payload.
     */
    public static final int SKIN_SIZE = 0x15E0;
    /**
     * The size of the {@code CCLR} extension payload.
     */
    public static final int CUSTOM_COLORS_SIZE = 0xC0;
    /**
     * The recording limits written to {@code LMTS}, in resource field order.
     */
    public static final int @NotNull [] CURRENT_LIMITS = ReplayLimits.MAXIMUM_VALUES.clone();
    /**
     * The classic limits: a game whose lobby selects them has a unit capacity of {@code 1700}.
     */
    public static final int @NotNull [] CLASSIC_LIMITS = ReplayLimits.MINIMUM_VALUES.clone();
    /**
     * The recorded {@code BFIX} QWORD.
     */
    public static final long CURRENT_FIX_FLAGS = 4;

    private static final int BYTE_MASK = 0xFF;

    private static final int DEFLATE_BUFFER_MULTIPLIER = 2;
    /**
     * The room of the deflate buffer past twice the chunk size.
     */
    private static final int DEFLATE_BUFFER_MARGIN = 64;
    private static final int MAXIMUM_BLOCK_LENGTH = 0xFF;
    /**
     * The size from which a command (type byte and payload) is not recorded.
     */
    private static final int MAXIMUM_RECORDED_COMMAND_SIZE = 0x100;
    private static final byte @NotNull [] UNRECORDED_TYPES = {
            ReplayCommandIds.SYNC, ReplayCommandIds.KEEP_ALIVE, ReplayCommandIds.SAVE_GAME, ReplayCommandIds.LOAD_GAME, ReplayCommandIds.RESTART_GAME,
            ReplayCommandIds.PAUSE, ReplayCommandIds.RESUME, ReplayCommandIds.LATENCY, ReplayCommandIds.TURN_RATE, ReplayCommandIds.DYNAMIC_TURN_RATE
    };

    /**
     * The version block: the format DWORD, the build, the save time QWORD and the environment bytes.
     */
    private static final int VERSION_BLOCK_FORMAT = 1;
    private static final int VERSION_BLOCK_BUILD_OFFSET = 4;
    private static final int VERSION_BLOCK_TIME_OFFSET = 8;
    private static final int VERSION_BLOCK_ENVIRONMENT_OFFSET = 0x10;
    /**
     * The environment bytes stored in generated version blocks.
     */
    private static final byte @NotNull [] VERSION_BLOCK_ENVIRONMENT = {5, 0, 1, 1};

    /**
     * Checks whether a command type and payload length are recorded.
     * <p>
     * Ten command types are excluded; commands of {@code 256} bytes or more are not recorded.
     *
     * @param typeIdentifier the command type byte
     * @param payloadLength  the payload length after the type byte
     * @return {@code true} if the command is recorded, {@code false} otherwise
     */
    public static boolean isRecorded(int typeIdentifier, int payloadLength) {
        if (ReplayFile.COMMAND_TYPE_SIZE + payloadLength >= MAXIMUM_RECORDED_COMMAND_SIZE) {
            return false;
        }

        for (byte excluded : UNRECORDED_TYPES) {
            if ((excluded & BYTE_MASK) == (typeIdentifier & BYTE_MASK)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Encodes commands into frame blocks of the command history.
     * <p>
     * A block is {@code DWORD frame, BYTE length, commands}; each command is its player byte followed by the command bytes.
     * The blocks follow {@link #splitBlocks}.
     *
     * @param commands the commands in execution order, frames non-decreasing
     * @return the command section payload
     */
    public static byte @NotNull [] encodeCommands(@NotNull List<ReplayCommand> commands) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        for (ReplayCommandBlock block : splitBlocks(commands)) {
            int commandEnd = block.getFirstCommandIndex() + block.getCommandCount();

            writeInt(output, (int) block.getFrame());
            output.write(block.getSize());

            for (int commandIndex = block.getFirstCommandIndex(); commandIndex < commandEnd; commandIndex++) {
                ReplayCommand command = commands.get(commandIndex);

                output.write(command.getPlayerId());
                output.write(command.getTypeId());
                output.writeBytes(command.getPayload());
            }
        }

        return output.toByteArray();
    }

    /**
     * Splits commands into the frame blocks of the command history.
     * <p>
     * A new block starts when the frame changes or when the open block plus the next command, with its player byte, would
     * reach {@code 255} bytes.
     *
     * @param commands the commands in execution order, frames non-decreasing
     * @return the blocks in stream order, each a range of {@code commands}
     */
    public static @NotNull List<ReplayCommandBlock> splitBlocks(@NotNull List<ReplayCommand> commands) {
        List<ReplayCommandBlock> blocks = new ArrayList<>();
        int blockLength = 0;
        int firstCommandIndex = 0;
        long blockFrame = -1;

        for (int commandIndex = 0; commandIndex < commands.size(); commandIndex++) {
            ReplayCommand command = commands.get(commandIndex);
            int commandLength = ReplayFile.COMMAND_TYPE_SIZE + command.getPayload().length;

            if (blockFrame >= 0 &&
                (command.getFrame() != blockFrame || blockLength + ReplayFile.COMMAND_PLAYER_SIZE + commandLength >= MAXIMUM_BLOCK_LENGTH)) {
                blocks.add(new ReplayCommandBlock(blockFrame, blockLength, firstCommandIndex, commandIndex - firstCommandIndex));
                blockLength       = 0;
                firstCommandIndex = commandIndex;
            }

            blockFrame = command.getFrame();
            blockLength += ReplayFile.COMMAND_PLAYER_SIZE + commandLength;
        }

        if (blockLength != 0) {
            blocks.add(new ReplayCommandBlock(blockFrame, blockLength, firstCommandIndex, commands.size() - firstCommandIndex));
        }

        return blocks;
    }

    /**
     * Encodes the player name section: twelve {@code 96}-byte records, each the name bytes cut as a {@code 96}-byte buffer holds them
     * ({@link TerminatedStringUtils#putText}: at most {@code 95} bytes and the terminator) and zero padded.
     *
     * @param names the twelve name bytes without terminator
     * @return the {@code 0x480}-byte section payload
     * @throws IllegalArgumentException if the array does not hold twelve names
     */
    public static byte @NotNull [] encodeNames(byte @NotNull [] @NotNull [] names) {
        if (names.length != ReplayHeaderBuilder.PLAYER_COUNT) {
            throw Exceptions.newIllegalArgumentException("The name section holds twelve names.");
        }

        byte[] section = new byte[NAMES_SIZE];

        for (int index = 0; index < names.length; index++) {
            TerminatedStringUtils.putText(names[index], 0, names[index].length, section, index * NAME_RECORD_SIZE, NAME_RECORD_SIZE);
        }

        return section;
    }

    /**
     * Encodes the {@code 25}-byte {@code GCFG} block with its build, save time and environment bytes.
     *
     * @param build       the build number
     * @param unixSeconds the save time in seconds since the Unix epoch
     * @return the {@code 25}-byte block
     */
    public static byte @NotNull [] encodeVersionBlock(int build, long unixSeconds) {
        byte[] block = new byte[ReplayGameVersion.VERSION_BLOCK_SIZE];

        LittleEndianUtils.writeInt32(block, 0, VERSION_BLOCK_FORMAT);
        LittleEndianUtils.writeInt32(block, VERSION_BLOCK_BUILD_OFFSET, build);
        LittleEndianUtils.writeInt64(block, VERSION_BLOCK_TIME_OFFSET, unixSeconds);
        System.arraycopy(VERSION_BLOCK_ENVIRONMENT, 0, block, VERSION_BLOCK_ENVIRONMENT_OFFSET, VERSION_BLOCK_ENVIRONMENT.length);

        return block;
    }

    /**
     * Encodes one checksummed section as compressed or raw chunks.
     *
     * @param payload the section bytes
     * @return the encoded section: checksum, chunk count and chunks
     */
    public static byte @NotNull [] encodeSection(byte @NotNull [] payload) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(payload.length + Long.BYTES);

        writeSection(output, payload);

        return output.toByteArray();
    }

    /**
     * Writes one checksummed section as compressed or raw chunks.
     *
     * @param output  the destination
     * @param payload the decoded section bytes
     */
    static void writeSection(@NotNull ByteArrayOutputStream output, byte @NotNull [] payload) {
        CRC32 checksum = new CRC32();

        checksum.update(payload);
        writeInt(output, (int) (checksum.getValue() ^ ReplayFile.CHECKSUM_COMPLEMENT));

        int chunkCount = (payload.length + ReplayFile.CHUNK_SIZE - 1) / ReplayFile.CHUNK_SIZE;
        byte[] buffer = new byte[ReplayFile.CHUNK_SIZE * DEFLATE_BUFFER_MULTIPLIER + DEFLATE_BUFFER_MARGIN];

        writeInt(output, chunkCount);

        for (int chunk = 0; chunk < chunkCount; chunk++) {
            int offset = chunk * ReplayFile.CHUNK_SIZE;
            int length = Math.min(ReplayFile.CHUNK_SIZE, payload.length - offset);
            int compressedLength;
            boolean compressed;

            try (Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION)) {
                compressedLength = 0;

                deflater.setInput(payload, offset, length);
                deflater.finish();

                while (!deflater.finished() && compressedLength < buffer.length) {
                    compressedLength += deflater.deflate(buffer, compressedLength, buffer.length - compressedLength);
                }

                compressed = deflater.finished() && compressedLength < length;
            }

            if (compressed) {
                writeInt(output, compressedLength);
                output.write(buffer, 0, compressedLength);
            } else {
                writeInt(output, length);
                output.write(payload, offset, length);
            }
        }
    }

    private static void writeExtension(@NotNull ByteArrayOutputStream output, int tag, byte @NotNull [] payload) {
        ByteArrayOutputStream section = new ByteArrayOutputStream();

        writeSection(section, payload);
        writeInt(output, tag);
        writeInt(output, section.size());
        output.writeBytes(section.toByteArray());
    }

    private static void writeInt(@NotNull ByteArrayOutputStream output, int value) {
        output.write(value);
        output.write(value >>> Byte.SIZE);
        output.write(value >>> Short.SIZE);
        output.write(value >>> (Integer.SIZE - Byte.SIZE));
    }


    private final byte @NotNull [] header;
    private final byte @NotNull [] commands;
    private final byte @NotNull [] mapData;
    private final byte @NotNull [] names;

    private final byte @NotNull [] skins;
    private final int @NotNull [] limits;
    private final long fixFlags;
    private final byte @NotNull [] customColors;
    private final byte @NotNull [] versionBlock;
    private final int speedOverrideIndex;

    /**
     * Creates a {@link ReplayWriter} instance from encoded section payloads, for a game without a speed override.
     *
     * @param header       the {@code 0x279}-byte header section
     * @param commands     the command section payload ({@link #encodeCommands})
     * @param mapData      the embedded {@code scenario.chk} bytes
     * @param names        the {@code 0x480}-byte name section ({@link #encodeNames})
     * @param skins        the {@code 0x15e0}-byte {@code SKIN} payload
     * @param limits       the seven {@code LMTS} values
     * @param fixFlags     the {@code BFIX} QWORD
     * @param customColors the {@code 0xc0}-byte {@code CCLR} payload
     * @param versionBlock the {@code 25}-byte {@code GCFG} payload
     * @throws IllegalArgumentException if a section has the wrong size
     */
    public ReplayWriter(byte @NotNull [] header,
                        byte @NotNull [] commands,
                        byte @NotNull [] mapData,
                        byte @NotNull [] names,
                        byte @NotNull [] skins,
                        int @NotNull [] limits,
                        long fixFlags,
                        byte @NotNull [] customColors,
                        byte @NotNull [] versionBlock) {
        this(header, commands, mapData, names, skins, limits, fixFlags, customColors, versionBlock, 0);
    }

    /**
     * Creates a {@link ReplayWriter} instance from encoded section payloads.
     *
     * @param header             the {@code 0x279}-byte header section
     * @param commands           the command section payload ({@link #encodeCommands})
     * @param mapData            the embedded {@code scenario.chk} bytes
     * @param names              the {@code 0x480}-byte name section ({@link #encodeNames})
     * @param skins              the {@code 0x15e0}-byte {@code SKIN} payload
     * @param limits             the seven {@code LMTS} values
     * @param fixFlags           the {@code BFIX} QWORD
     * @param customColors       the {@code 0xc0}-byte {@code CCLR} payload
     * @param versionBlock       the {@code 25}-byte {@code GCFG} payload
     * @param speedOverrideIndex the game's speed override selector, written as the {@code XSPD} section when it is not {@code 0}
     * @throws IllegalArgumentException if a section has the wrong size or the speed override selector is negative
     */
    public ReplayWriter(byte @NotNull [] header,
                        byte @NotNull [] commands,
                        byte @NotNull [] mapData,
                        byte @NotNull [] names,
                        byte @NotNull [] skins,
                        int @NotNull [] limits,
                        long fixFlags,
                        byte @NotNull [] customColors,
                        byte @NotNull [] versionBlock,
                        int speedOverrideIndex) {
        if (header.length != ReplayHeaderBuilder.HEADER_SIZE || names.length != NAMES_SIZE || skins.length != SKIN_SIZE ||
            limits.length != ReplayLimits.VALUE_COUNT || customColors.length != CUSTOM_COLORS_SIZE ||
            versionBlock.length != ReplayGameVersion.VERSION_BLOCK_SIZE) {
            throw Exceptions.newIllegalArgumentException("A replay section has the wrong size.");
        }

        if (speedOverrideIndex < 0) {
            throw Exceptions.newIllegalArgumentException("The speed override selector must not be negative: {0}", speedOverrideIndex);
        }

        this.header   = header.clone();
        this.commands = commands.clone();
        this.mapData  = mapData.clone();
        this.names    = names.clone();

        this.skins              = skins.clone();
        this.limits             = limits.clone();
        this.fixFlags           = fixFlags;
        this.customColors       = customColors.clone();
        this.versionBlock       = versionBlock.clone();
        this.speedOverrideIndex = speedOverrideIndex;
    }

    /**
     * Writes the complete replay file.
     *
     * @return the replay file bytes
     */
    public byte @NotNull [] write() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writeSection(output, ReplayFile.EXTENDED_REPLAY_IDENTIFIER.getBytes(StandardCharsets.US_ASCII));

        int extensionOffsetPosition = output.size();

        writeInt(output, 0);
        writeSection(output, this.header);

        int commandSectionSize = this.commands.length;
        byte[] commandLengthBytes = new byte[Integer.BYTES];

        LittleEndianUtils.writeInt32(commandLengthBytes, 0, commandSectionSize);
        writeSection(output, commandLengthBytes);

        if (this.commands.length != 0) {
            writeSection(output, this.commands);
        }

        int mapSectionSize = this.mapData.length;
        byte[] mapLengthBytes = new byte[Integer.BYTES];

        LittleEndianUtils.writeInt32(mapLengthBytes, 0, mapSectionSize);
        writeSection(output, mapLengthBytes);
        writeSection(output, this.mapData);
        writeSection(output, this.names);

        int extensionOffset = output.size();
        byte[] limitBytes = new byte[ReplayLimits.VALUE_COUNT * Integer.BYTES];
        byte[] flagBytes = new byte[ReplayGameVersion.FLAGS_SIZE];

        writeExtension(output, ReplayFile.SKIN_TAG, this.skins);

        for (int index = 0; index < ReplayLimits.VALUE_COUNT; index++) {
            LittleEndianUtils.writeInt32(limitBytes, index * Integer.BYTES, this.limits[index]);
        }

        writeExtension(output, ReplayFile.LIMITS_TAG, limitBytes);
        LittleEndianUtils.writeInt64(flagBytes, 0, this.fixFlags);
        writeExtension(output, ReplayFile.FIX_FLAGS_TAG, flagBytes);
        writeExtension(output, ReplayFile.CUSTOM_COLORS_TAG, this.customColors);
        writeExtension(output, ReplayFile.VERSION_BLOCK_TAG, this.versionBlock);

        if (this.speedOverrideIndex != 0) {
            byte[] speedOverrideBytes = new byte[Integer.BYTES];

            LittleEndianUtils.writeInt32(speedOverrideBytes, 0, this.speedOverrideIndex);
            writeExtension(output, ReplayFile.SPEED_OVERRIDE_TAG, speedOverrideBytes);
        }

        byte[] file = output.toByteArray();

        LittleEndianUtils.writeInt32(file, extensionOffsetPosition, extensionOffset);

        return file;
    }
}
