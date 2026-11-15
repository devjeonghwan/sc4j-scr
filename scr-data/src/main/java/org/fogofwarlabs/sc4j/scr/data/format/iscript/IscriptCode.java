package org.fogofwarlabs.sc4j.scr.data.format.iscript;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds one block of iscript code in the native instruction encoding ({@link IscriptOpcodes}), with named labels inside the block. A block is
 * placed at an offset of the WORD offset space when a file is assembled ({@link #encode(int)}); a {@code goto} to a label then holds the label's
 * offset. Every other branch operand is an absolute offset, so code may also jump into the installed scripts.
 */
public final class IscriptCode {
    private static final int BYTE_MASK = 0xFF;

    /**
     * Builds {@link IscriptCode} instances.
     */
    public static final class Builder {
        private final @NotNull ByteArrayOutputStream output;
        private final @NotNull Map<String, Integer> labels;
        private final @NotNull List<Integer> fixupPositions;
        private final @NotNull List<String> fixupLabels;

        /**
         * Creates a {@link Builder} instance with an empty block.
         */
        public Builder() {
            this.output         = new ByteArrayOutputStream();
            this.labels         = new HashMap<>();
            this.fixupPositions = new ArrayList<>();
            this.fixupLabels    = new ArrayList<>();
        }

        /**
         * Names the position of the next instruction.
         *
         * @param name the label name, unique in the block
         * @return this instance for chaining
         * @throws IllegalArgumentException if the block already has the label
         */
        public @NotNull Builder label(@NotNull String name) {
            if (this.labels.putIfAbsent(name, this.output.size()) != null) {
                throw Exceptions.newIllegalArgumentException("The iscript label {0} is already defined.", name);
            }

            return this;
        }

        /**
         * Appends one instruction.
         *
         * @param opcode   the opcode ({@link IscriptOpcodes})
         * @param operands the operands in the opcode's layout
         * @return this instance for chaining
         * @throws IllegalArgumentException if the opcode is unknown or the operands do not fit its layout
         */
        public @NotNull Builder instruction(int opcode, int @NotNull ... operands) {
            IscriptOpcodes.encodeInstruction(opcode, operands, this.output);

            return this;
        }

        /**
         * Appends a {@code goto} to a label of the block.
         *
         * @param label the label, defined before or after this instruction
         * @return this instance for chaining
         */
        public @NotNull Builder jump(@NotNull String label) {
            this.output.write(IscriptOpcodes.GOTO);
            this.fixupPositions.add(this.output.size());
            this.fixupLabels.add(label);
            this.output.write(0);
            this.output.write(0);

            return this;
        }

        /**
         * Builds an {@link IscriptCode} instance with the current instructions.
         *
         * @return the built {@link IscriptCode} instance
         * @throws IllegalArgumentException if a {@code goto} names an undefined label or the block is empty
         */
        public @NotNull IscriptCode build() {
            if (this.output.size() == 0) {
                throw Exceptions.newIllegalArgumentException("An iscript code block needs an instruction.");
            }

            int[] fixups = new int[this.fixupPositions.size()];
            int[] targets = new int[fixups.length];

            for (int index = 0; index < fixups.length; index++) {
                Integer target = this.labels.get(this.fixupLabels.get(index));

                if (target == null) {
                    throw Exceptions.newIllegalArgumentException("The iscript label {0} is not defined.", this.fixupLabels.get(index));
                }

                fixups[index]  = this.fixupPositions.get(index);
                targets[index] = target;
            }

            return new IscriptCode(this.output.toByteArray(), new HashMap<>(this.labels), fixups, targets);
        }
    }

    private final byte @NotNull [] bytes;
    private final @NotNull Map<String, Integer> labels;
    private final int @NotNull [] fixups;
    private final int @NotNull [] targets;

    private IscriptCode(byte @NotNull [] bytes, @NotNull Map<String, Integer> labels, int @NotNull [] fixups, int @NotNull [] targets) {
        this.bytes   = bytes;
        this.labels  = labels;
        this.fixups  = fixups;
        this.targets = targets;
    }

    /**
     * @return the number of bytes of the block
     */
    public int getLength() {
        return this.bytes.length;
    }

    /**
     * Returns the position of a label in the block.
     *
     * @param name the label name
     * @return the byte position of the label from the start of the block
     * @throws IllegalArgumentException if the block has no such label
     */
    public int getLabelPosition(@NotNull String name) {
        Integer position = this.labels.get(name);

        if (position == null) {
            throw Exceptions.newIllegalArgumentException("The iscript label {0} is not defined.", name);
        }

        return position;
    }


    /**
     * Encodes the block placed at an offset: every {@code goto} to a label holds the label's offset.
     *
     * @param offset the offset of the first byte of the block
     * @return the encoded bytes
     */
    public byte @NotNull [] encode(int offset) {
        byte[] result = this.bytes.clone();

        for (int index = 0; index < this.fixups.length; index++) {
            int target = offset + this.targets[index];

            result[this.fixups[index]]     = (byte) (target & BYTE_MASK);
            result[this.fixups[index] + 1] = (byte) (target >>> Byte.SIZE);
        }

        return result;
    }
}
