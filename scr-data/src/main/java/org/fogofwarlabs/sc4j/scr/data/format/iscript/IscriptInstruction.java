package org.fogofwarlabs.sc4j.scr.data.format.iscript;

import org.jetbrains.annotations.NotNull;

/**
 * Retains a decoded iscript opcode, its file offset and ordered operand values.
 */
public final class IscriptInstruction {
    private final int offset;
    private final int opcode;
    private final int @NotNull [] operands;

    /**
     * Creates an {@link IscriptInstruction} instance.
     *
     * @param offset   the absolute file offset of the instruction
     * @param opcode   the opcode
     * @param operands the operand values
     */
    public IscriptInstruction(int offset, int opcode, int @NotNull [] operands) {
        this.offset   = offset;
        this.opcode   = opcode;
        this.operands = operands;
    }

    /**
     * @return the absolute file offset of the instruction
     */
    public int getOffset() {
        return this.offset;
    }

    /**
     * @return the opcode
     */
    public int getOpcode() {
        return this.opcode;
    }

    /**
     * @return the operand values
     */
    public int @NotNull [] getOperands() {
        return this.operands;
    }

    /**
     * @return the opcode name, or {@code unknown_} followed by the hexadecimal opcode if the opcode has no name
     */
    public @NotNull String getName() {
        String name = IscriptOpcodes.getName(this.opcode);

        return name != null ? name : "unknown_" + Integer.toHexString(this.opcode);
    }
}
