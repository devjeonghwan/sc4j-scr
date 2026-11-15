package org.fogofwarlabs.sc4j.scr.game.sprite.script.load;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptFile;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptHeader;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptOpcodes;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.BitSet;

/**
 * Maps the WORD offsets of {@code iscript.bin} to the positions of the SC:R iscript program and back. The map is a
 * bijection over every WORD offset, so an image may hold any offset (a saved stale one, one inside an instruction) and
 * keep it.
 * <p>
 * The code that the animations reach comes first, in ascending offset order, so that the instructions images run lie next
 * to each other: every entry the native header table gives for the 28 animation kinds (also past the declared count),
 * followed through every instruction and branch target up to an {@code end}, {@code goto} or {@code return}. The other
 * offsets follow in ascending order. The end of the program, after the last position, maps to the offset after the last
 * WORD offset.
 */
public final class ScrIscriptOffsets {
    /**
     * The number of WORD offsets, and of program positions.
     */
    public static final int OFFSET_COUNT = 0x10000;

    /**
     * The number of iscript animation kinds whose header entries the reachable code starts at.
     */
    private static final int KIND_COUNT = 28;

    /**
     * Creates the offset map of an iscript.
     *
     * @param iscript the parsed {@code iscript.bin}
     * @return the created {@link ScrIscriptOffsets} instance
     */
    public static @NotNull ScrIscriptOffsets create(@NotNull IscriptFile iscript) {
        BitSet reachable = findReachableOffsets(iscript);
        int[] positionToOffset = new int[OFFSET_COUNT + 1];
        int[] offsetToPosition = new int[OFFSET_COUNT];
        int position = 0;

        for (int offset = reachable.nextSetBit(0); offset >= 0; offset = reachable.nextSetBit(offset + 1)) {
            positionToOffset[position] = offset;
            offsetToPosition[offset]   = position++;
        }

        int reachableCount = position;

        for (int offset = reachable.nextClearBit(0); offset < OFFSET_COUNT; offset = reachable.nextClearBit(offset + 1)) {
            positionToOffset[position] = offset;
            offsetToPosition[offset]   = position++;
        }

        positionToOffset[OFFSET_COUNT] = OFFSET_COUNT;

        return new ScrIscriptOffsets(offsetToPosition, positionToOffset, reachableCount);
    }

    /**
     * Returns the offset a branch opcode may continue at.
     *
     * @param decoded the decoded instruction
     * @return the target offset, or {@code -1} if the opcode does not branch
     */
    public static int findBranchTarget(@NotNull IscriptInstruction decoded) {
        int[] operands = decoded.getOperands();

        return switch (decoded.getOpcode()) {
            case IscriptOpcodes.GOTO, IscriptOpcodes.CALL, IscriptOpcodes.PWRUPCONDJMP, IscriptOpcodes.LIFTOFFCONDJMP -> operands[0];
            case IscriptOpcodes.RANDCONDJMP, IscriptOpcodes.TRGTRANGECONDJMP -> operands[1];
            case IscriptOpcodes.TRGTARCCONDJMP, IscriptOpcodes.CURDIRECTCONDJMP -> operands[2];
            default -> -1;
        };
    }

    /**
     * Finds the offsets of the instructions the header entries reach.
     *
     * @param iscript the parsed {@code iscript.bin}
     * @return the reached offsets
     */
    private static @NotNull BitSet findReachableOffsets(@NotNull IscriptFile iscript) {
        BitSet reachable = new BitSet(OFFSET_COUNT);
        ArrayDeque<Integer> entries = new ArrayDeque<>();

        for (IscriptHeader header : iscript.getHeaders()) {
            for (int kindIndex = 0; kindIndex < KIND_COUNT; kindIndex++) {
                try {
                    int offset = iscript.getAnimationOffset(header.getId(), kindIndex);

                    if (offset != 0) {
                        entries.add(offset);
                    }
                } catch (IOException exception) {
                    throw Exceptions.newIllegalStateException(exception, "The iscript header {0} has no animation table.", header.getId());
                }
            }
        }

        while (!entries.isEmpty()) {
            int offset = entries.poll();

            while (offset < OFFSET_COUNT && !reachable.get(offset)) {
                IscriptInstruction decoded;
                int size;

                try {
                    decoded = iscript.decodeInstruction(offset);
                    size    = iscript.getInstructionSize(offset);
                } catch (IOException exception) {
                    break;
                }

                reachable.set(offset);

                int target = findBranchTarget(decoded);
                int opcode = decoded.getOpcode();

                if (target >= 0) {
                    entries.add(target);
                }

                if (opcode == IscriptOpcodes.END || opcode == IscriptOpcodes.GOTO || opcode == IscriptOpcodes.RETURN) {
                    break;
                }

                offset += size;
            }
        }

        return reachable;
    }


    private final int @NotNull [] offsetToPosition;
    private final int @NotNull [] positionToOffset;
    private final int reachableCount;

    /**
     * Creates a {@link ScrIscriptOffsets} instance.
     *
     * @param offsetToPosition the position of every WORD offset
     * @param positionToOffset the offset of every position and of the end
     * @param reachableCount   the number of reachable offsets, which take the first positions
     */
    private ScrIscriptOffsets(int @NotNull [] offsetToPosition, int @NotNull [] positionToOffset, int reachableCount) {
        this.offsetToPosition = offsetToPosition;
        this.positionToOffset = positionToOffset;
        this.reachableCount   = reachableCount;
    }

    /**
     * @return the number of offsets the header entries reach; they take positions {@code 0} up to this count minus one
     */
    public int getReachableCount() {
        return this.reachableCount;
    }

    /**
     * Returns the program position of an offset.
     *
     * @param offset the WORD offset, {@code 0} through {@code 0xFFFF}
     * @return the position
     */
    public int getPosition(int offset) {
        return this.offsetToPosition[offset];
    }

    /**
     * Returns the offset of a program position.
     *
     * @param position the position, {@code 0} up to {@link #OFFSET_COUNT}; {@link #OFFSET_COUNT} is the end of the program
     * @return the offset; the end of the program gives {@link #OFFSET_COUNT}
     */
    public int getOffset(int position) {
        return this.positionToOffset[position];
    }
}
