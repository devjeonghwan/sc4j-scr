package org.fogofwarlabs.sc4j.scr.data.format.iscript;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Represents a parsed {@code scripts/iscript.bin} file.
 */
public final class IscriptFile {
    /**
     * The highest opcode decoded with operands; higher bytes occupy one byte.
     */
    public static final int MAXIMUM_OPCODE = 0x44;

    private static final int MAGIC_SCPE = 0x45504353;
    private static final int TABLE_TERMINATOR_ID = 65535;
    /**
     * The size of the identifier table offset DWORD at the start of the file.
     */
    private static final int TABLE_OFFSET_SIZE = 4;
    /**
     * One identifier table entry: the script identifier WORD and the header offset WORD.
     */
    private static final int TABLE_ENTRY_SIZE = 4;
    private static final int TABLE_ENTRY_HEADER_OFFSET = 2;
    private static final int HEADER_TYPE_OFFSET = 4;
    /**
     * The offset of the animation offset table inside a header, after the magic and the type.
     */
    private static final int HEADER_ANIMATIONS_OFFSET = 8;
    private static final int ANIMATION_ENTRY_SIZE = 2;
    private static final int MAXIMUM_ANIMATION_INDEX = 0xFF;
    /**
     * The mask that keeps the unsigned value of an opcode byte.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The bytes shown in the message of a block that fails to decode.
     */
    private static final int FAILURE_DUMP_LENGTH = 48;

    /**
     * Creates an {@link IscriptFile} instance from the given {@code iscript.bin} content.
     *
     * @param data the iscript.bin content
     * @return the created {@link IscriptFile} instance
     * @throws IOException if the content is not a valid iscript.bin file
     */
    public static @NotNull IscriptFile parse(byte @NotNull [] data) throws IOException {
        if (data.length < TABLE_OFFSET_SIZE) {
            throw Exceptions.newIOException("The iscript.bin file is too small: {0}", data.length);
        }

        long identifierTableOffset = LittleEndianUtils.readUnsigned32(data, 0);

        if (identifierTableOffset >= data.length) {
            throw Exceptions.newIOException("The iscript.bin file has an invalid identifier table offset: {0}", identifierTableOffset);
        }

        List<int[]> rawEntries = new ArrayList<>();
        TreeSet<Integer> boundaries = new TreeSet<>();

        boundaries.add((int) identifierTableOffset);

        int entryOffset = (int) identifierTableOffset;

        while (entryOffset <= data.length - TABLE_ENTRY_SIZE) {
            int id = LittleEndianUtils.readUnsigned16(data, entryOffset);
            int headerOffset = LittleEndianUtils.readUnsigned16(data, entryOffset + TABLE_ENTRY_HEADER_OFFSET);

            if (id == TABLE_TERMINATOR_ID) {
                break;
            }

            rawEntries.add(new int[]{id, headerOffset});
            boundaries.add(headerOffset);

            entryOffset += TABLE_ENTRY_SIZE;
        }

        // First pass: validate all headers and collect every animation offset for boundaries.
        for (int[] rawEntry : rawEntries) {
            int id = rawEntry[0];
            int headerOffset = rawEntry[1];

            if (headerOffset + HEADER_ANIMATIONS_OFFSET > data.length) {
                throw Exceptions.newIOException("The iscript header is out of bounds: ID {0}", id);
            }

            long magic = LittleEndianUtils.readUnsigned32(data, headerOffset);

            if (magic != MAGIC_SCPE) {
                throw Exceptions.newIOException("The iscript header has an invalid magic: ID {0}", id);
            }

            int type = LittleEndianUtils.readUnsigned16(data, headerOffset + HEADER_TYPE_OFFSET);
            int animationCount = type + 1;

            if (animationCount > (data.length - headerOffset - HEADER_ANIMATIONS_OFFSET) / ANIMATION_ENTRY_SIZE) {
                throw Exceptions.newIOException("The declared iscript animation table is out of bounds: ID {0}", id);
            }

            for (int animationIndex = 0; animationIndex < animationCount; animationIndex++) {
                int animationOffset = readAnimationOffset(data, headerOffset, animationIndex);

                if (animationOffset != 0) {
                    boundaries.add(animationOffset);
                }
            }
        }

        // Second pass: decode the animation blocks.
        List<IscriptHeader> headers = new ArrayList<>(rawEntries.size());

        for (int[] rawEntry : rawEntries) {
            int id = rawEntry[0];
            int headerOffset = rawEntry[1];
            int type = LittleEndianUtils.readUnsigned16(data, headerOffset + HEADER_TYPE_OFFSET);
            int animationCount = type + 1;
            List<IscriptHeader.Animation> animations = new ArrayList<>(animationCount);

            for (int animationIndex = 0; animationIndex < animationCount; animationIndex++) {
                int animationOffset = readAnimationOffset(data, headerOffset, animationIndex);

                if (animationOffset == 0) {
                    continue;
                }

                Integer nextBoundary = boundaries.higher(animationOffset);
                int boundary = nextBoundary != null ? nextBoundary : data.length;
                List<IscriptInstruction> instructions;

                try {
                    instructions = decodeBlock(data, animationOffset, boundary);
                } catch (IOException exception) {
                    StringBuilder hex = new StringBuilder();

                    for (int dumpIndex = animationOffset; dumpIndex < Math.min(animationOffset + FAILURE_DUMP_LENGTH, data.length); dumpIndex++) {
                        hex.append(String.format("%02x ", data[dumpIndex]));
                    }

                    throw Exceptions.newIOException(exception, "Block decode failed: ID {0}, animation {1}, offset {2}, boundary {3}, bytes: {4}",
                                                    id, animationIndex, animationOffset, boundary, hex);
                }

                animations.add(new IscriptHeader.Animation(animationIndex, animationOffset, instructions));
            }

            int[] animationOffsets = new int[animationCount];

            for (int animationIndex = 0; animationIndex < animationCount; animationIndex++) {
                animationOffsets[animationIndex] = readAnimationOffset(data, headerOffset, animationIndex);
            }

            headers.add(new IscriptHeader(id, headerOffset, type, animationOffsets, animations));
        }

        return new IscriptFile(headers, data);
    }

    /**
     * Decodes the linear instruction run of an animation block, which ends at the boundary or after an {@code end}, {@code return} or {@code goto}.
     *
     * @param data     the iscript.bin content
     * @param offset   the file offset of the block
     * @param boundary the next block boundary, exclusive
     * @return the decoded instructions in file order
     * @throws IOException if an instruction is invalid or exceeds the boundary
     */
    private static @NotNull List<IscriptInstruction> decodeBlock(byte @NotNull [] data, int offset, int boundary) throws IOException {
        List<IscriptInstruction> instructions = new ArrayList<>();
        int position = offset;

        while (position < boundary) {
            int opcode = data[position] & BYTE_MASK;
            int[] operands = IscriptOpcodes.decodeOperands(opcode, data, position + 1, boundary);

            if (operands == null) {
                throw Exceptions.newIOException("Invalid iscript instruction at offset {0}", position);
            }

            int operandSize = IscriptOpcodes.getOperandSize(opcode, data, position + 1, boundary);

            if (operandSize < 0 || position + 1 + operandSize > boundary) {
                throw Exceptions.newIOException("The iscript instruction exceeds the block boundary at offset {0}", position);
            }

            instructions.add(new IscriptInstruction(position, opcode, operands));

            position += 1 + operandSize;

            // A goto ends the linear run; the target region decodes with its own animation.
            if (opcode == IscriptOpcodes.END || opcode == IscriptOpcodes.RETURN || opcode == IscriptOpcodes.GOTO) {
                break;
            }
        }

        return instructions;
    }

    /**
     * Reads an entry of the animation offset table of a header.
     *
     * @param data           the iscript.bin content
     * @param headerOffset   the file offset of the header
     * @param animationIndex the animation index
     * @return the unsigned animation offset, {@code 0} if unused
     */
    private static int readAnimationOffset(byte @NotNull [] data, int headerOffset, int animationIndex) {
        return LittleEndianUtils.readUnsigned16(data, headerOffset + HEADER_ANIMATIONS_OFFSET + animationIndex * ANIMATION_ENTRY_SIZE);
    }


    private final @NotNull List<IscriptHeader> headers;
    private final byte @NotNull [] data;
    /**
     * The instructions {@link #decodeInstruction} decoded so far, by offset. The file bytes never change, so a decoded instruction stays valid;
     * interpreters decode the same offsets every frame.
     */
    private final @Nullable IscriptInstruction @NotNull [] decodedInstructions;

    /**
     * Creates an {@link IscriptFile} instance.
     *
     * @param headers the iscript headers
     * @param data    the raw iscript.bin content
     */
    private IscriptFile(@NotNull List<IscriptHeader> headers, byte @NotNull [] data) {
        this.headers             = headers;
        this.data                = data;
        this.decodedInstructions = new IscriptInstruction[data.length];
    }

    /**
     * Copies the file contents.
     *
     * @return a copy of the file contents
     */
    public byte @NotNull [] copyData() {
        return this.data.clone();
    }

    /**
     * @return the iscript headers
     */
    public @NotNull List<IscriptHeader> getHeaders() {
        return Collections.unmodifiableList(this.headers);
    }

    /**
     * Reads an unsigned animation entry directly from the file's byte-indexed table. The declared inspection-table size does not bound this lookup.
     *
     * @param scriptIdentifier the complete script identifier compared with unsigned table words
     * @param animationIndex   the unsigned animation byte
     * @return the unsigned instruction offset, including {@code 0} when stored
     * @throws IllegalArgumentException if the animation index is outside the unsigned byte range
     * @throws IOException              if the identifier is absent or the lookup exceeds the file
     */
    public int getAnimationOffset(int scriptIdentifier, int animationIndex) throws IOException {
        if (animationIndex < 0 || animationIndex > MAXIMUM_ANIMATION_INDEX) {
            throw Exceptions.newIllegalArgumentException("Animation index is outside the unsigned byte range: {0}", animationIndex);
        }

        long tableOffset = LittleEndianUtils.readUnsigned32(this.data, 0);

        if (tableOffset >= this.data.length) {
            throw Exceptions.newIOException("The iscript identifier table is outside the file.");
        }

        for (int entryOffset = (int) tableOffset; entryOffset <= this.data.length - TABLE_ENTRY_SIZE; entryOffset += TABLE_ENTRY_SIZE) {
            int identifier = LittleEndianUtils.readUnsigned16(this.data, entryOffset);

            if (identifier == scriptIdentifier) {
                int animationOffset = LittleEndianUtils.readUnsigned16(this.data, entryOffset + TABLE_ENTRY_HEADER_OFFSET) +
                                      HEADER_ANIMATIONS_OFFSET + animationIndex * ANIMATION_ENTRY_SIZE;

                if (animationOffset > this.data.length - ANIMATION_ENTRY_SIZE) {
                    throw Exceptions.newIOException("The indexed iscript animation entry is outside the file.");
                }

                return LittleEndianUtils.readUnsigned16(this.data, animationOffset);
            }

            if (identifier == TABLE_TERMINATOR_ID) {
                break;
            }
        }

        throw Exceptions.newIOException("The iscript identifier does not exist: {0}", Integer.toUnsignedString(scriptIdentifier));
    }

    /**
     * Returns the raw size of the instruction at the given offset, one byte for a skipped opcode above {@code 0x44}.
     *
     * @param offset the absolute file offset
     * @return the size in bytes including the opcode
     * @throws IOException if the offset is outside the file or the operands extend past it
     */
    public int getInstructionSize(int offset) throws IOException {
        if (offset < 0 || offset >= this.data.length) {
            throw Exceptions.newIOException("The iscript instruction offset is out of bounds: {0}", offset);
        }

        int opcode = this.data[offset] & BYTE_MASK;

        if (opcode > MAXIMUM_OPCODE) {
            return 1;
        }

        int operandSize = IscriptOpcodes.getOperandSize(opcode, this.data, offset + 1, this.data.length);

        if (operandSize < 0) {
            throw Exceptions.newIOException("Invalid iscript instruction at offset {0}", offset);
        }

        return 1 + operandSize;
    }


    /**
     * Finds the header of the given iscript ID.
     *
     * @param iscriptId the iscript ID
     * @return the matching header, or {@code null} if not found
     */
    public @Nullable IscriptHeader findHeader(int iscriptId) {
        for (IscriptHeader header : this.headers) {
            if (header.getId() == iscriptId) {
                return header;
            }
        }

        return null;
    }

    /**
     * Finds the header at a file offset, as a saved image keeps its script by the header offset.
     *
     * @param offset the file offset of the header
     * @return the matching header, or {@code null} if no header starts there
     */
    public @Nullable IscriptHeader findHeaderAt(int offset) {
        for (IscriptHeader header : this.headers) {
            if (header.getOffset() == offset) {
                return header;
            }
        }

        return null;
    }

    /**
     * Decodes a single instruction at the given absolute offset.
     * <p>
     * Offset {@code 0} is executable, and bytes above opcode {@code 0x44} decode as one-byte instructions without operands.
     *
     * @param offset the absolute file offset
     * @return the decoded instruction
     * @throws IOException if the offset is outside the file or the operands extend past it
     */
    public @NotNull IscriptInstruction decodeInstruction(int offset) throws IOException {
        if (offset < 0 || offset >= this.data.length) {
            throw Exceptions.newIOException("The iscript instruction offset is out of bounds: {0}", offset);
        }

        IscriptInstruction decoded = this.decodedInstructions[offset];

        if (decoded != null) {
            return decoded;
        }

        int opcode = this.data[offset] & BYTE_MASK;

        if (opcode > MAXIMUM_OPCODE) {
            decoded = new IscriptInstruction(offset, opcode, new int[0]);
        } else {
            int[] operands = IscriptOpcodes.decodeOperands(opcode, this.data, offset + 1, this.data.length);

            if (operands == null) {
                throw Exceptions.newIOException("Invalid iscript instruction at offset {0}", offset);
            }

            decoded = new IscriptInstruction(offset, opcode, operands);
        }

        this.decodedInstructions[offset] = decoded;

        return decoded;
    }
}
