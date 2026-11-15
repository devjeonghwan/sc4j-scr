package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.type.constant.TriggerUnitGroups;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Creates {@link UnitAccounting} instances from captured native accounting storage, and reads and compares their counters through the
 * game-context DWORD view of {@link UnitAccounting}.
 */
public final class UnitAccountingAssert {
    /**
     * The byte length of the original accounting snapshot.
     */
    public static final int CONTEXT_BYTE_COUNT = 0xDD34;

    /**
     * The game-context offset of the first per-player counter group, DWORD {@code 0} of the captured trigger snapshot.
     */
    static final int COUNTERS_OFFSET = 0x2CF4;
    /**
     * The game-context offset of the first type counter.
     */
    static final int TYPE_COUNTS_OFFSET = 0x3234;
    /**
     * The game-context offset of the first completed type counter.
     */
    static final int COMPLETED_TYPE_COUNTS_OFFSET = 0x5CF4;

    /**
     * The DWORD index of the first type counter in the captured trigger snapshot.
     */
    private static final int SNAPSHOT_TYPE_COUNTS_INDEX = 3072;
    /**
     * The DWORD index of the first completed type counter in the captured trigger snapshot.
     */
    private static final int SNAPSHOT_COMPLETED_TYPE_COUNTS_INDEX = 336;
    /**
     * The offset bits below a DWORD boundary.
     */
    private static final int DWORD_ALIGNMENT_MASK = Integer.BYTES - 1;

    /**
     * Creates a {@link UnitAccounting} instance from an original snapshot: every game-context DWORD the accounting owns
     * ({@link UnitAccounting#hasContextDword}) is written through {@link UnitAccounting#writeContextDword}.
     *
     * @param words the original accounting words
     * @return the created {@link UnitAccounting} instance
     * @throws IllegalArgumentException if the snapshot has the wrong length
     */
    public static @NotNull UnitAccounting fromCapture(int @NotNull [] words) {
        if (words.length != CONTEXT_BYTE_COUNT / Integer.BYTES) {
            throw Exceptions.newIllegalArgumentException("The native unit accounting context has the wrong size.");
        }

        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

        for (int offset = 0; offset < CONTEXT_BYTE_COUNT; offset += Integer.BYTES) {
            if (accounting.hasContextDword(offset)) {
                accounting.writeContextDword(offset, words[offset / Integer.BYTES]);
            }
        }

        return accounting;
    }

    /**
     * Reads a represented counter by its original snapshot offset.
     *
     * @param accounting the accounting counters
     * @param offset     the original byte offset
     * @return the counter value
     * @throws IllegalArgumentException if the offset is unsupported or has no represented counter
     */
    public static int readCounter(@NotNull UnitAccounting accounting, int offset) {
        if (offset < 0 || offset >= CONTEXT_BYTE_COUNT || (offset & DWORD_ALIGNMENT_MASK) != 0) {
            throw Exceptions.newIllegalArgumentException("Unsupported native counter offset: {0}", offset);
        }

        if (!accounting.hasContextDword(offset)) {
            throw Exceptions.newIllegalArgumentException("The capture offset has no represented counter: {0}", offset);
        }

        return accounting.readContextDword(offset);
    }

    /**
     * Compares every represented accounting counter with an original snapshot.
     *
     * @param expected   the expected snapshot bytes
     * @param accounting the accounting counters
     */
    public static void assertKnownCounters(@NotNull ByteBuffer expected, @NotNull UnitAccounting accounting) {
        for (int offset = 0; offset < CONTEXT_BYTE_COUNT; offset += Integer.BYTES) {
            if (accounting.hasContextDword(offset)) {
                Assertions.assertEquals(expected.getInt(offset), accounting.readContextDword(offset), describeOffset(offset));
            }
        }
    }

    /**
     * Compares every represented accounting counter with an original snapshot of DWORDs.
     *
     * @param expected   the expected snapshot DWORDs
     * @param accounting the accounting counters
     */
    static void assertKnownCounters(int @NotNull [] expected, @NotNull UnitAccounting accounting) {
        for (int offset = 0; offset < CONTEXT_BYTE_COUNT; offset += Integer.BYTES) {
            if (accounting.hasContextDword(offset)) {
                Assertions.assertEquals(expected[offset / Integer.BYTES], accounting.readContextDword(offset), describeOffset(offset));
            }
        }
    }

    /**
     * Compares the counters a trigger reads from its snapshot ({@link UnitAccounting.TriggerSnapshot#getBuildingCount} and
     * {@link UnitAccounting.TriggerSnapshot#getCommandCount} for every player, unit type and unit group) with an original snapshot. The captured
     * snapshot holds the per-player counters from DWORD {@code 0} in game-context order and the type counters at their own indices.
     *
     * @param expected the expected snapshot bytes
     * @param snapshot the trigger accounting snapshot
     */
    public static void assertKnownSnapshot(@NotNull ByteBuffer expected, @NotNull UnitAccounting.TriggerSnapshot snapshot) {
        UnitAccounting expectedAccounting = PlayerProgressTestUtils.createUnitAccounting();
        UnitAccounting.TriggerSnapshot expectedSnapshot = new UnitAccounting.TriggerSnapshot();

        for (int offset = COUNTERS_OFFSET; offset < CONTEXT_BYTE_COUNT; offset += Integer.BYTES) {
            if (expectedAccounting.hasContextDword(offset)) {
                int source = offset >= COMPLETED_TYPE_COUNTS_OFFSET
                             ? SNAPSHOT_COMPLETED_TYPE_COUNTS_INDEX + (offset - COMPLETED_TYPE_COUNTS_OFFSET) / Integer.BYTES
                             : offset >= TYPE_COUNTS_OFFSET
                               ? SNAPSHOT_TYPE_COUNTS_INDEX + (offset - TYPE_COUNTS_OFFSET) / Integer.BYTES
                               : (offset - COUNTERS_OFFSET) / Integer.BYTES;

                expectedAccounting.writeContextDword(offset, expected.getInt(source * Integer.BYTES));
            }
        }

        expectedAccounting.copyTriggerSnapshot(expectedSnapshot);

        for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
            Assertions.assertEquals(expectedSnapshot.getBuildingCount(player), snapshot.getBuildingCount(player), "buildings of player " + player);

            for (int unitType = 0; unitType <= TriggerUnitGroups.FACTORIES; unitType++) {
                if (unitType == TriggerUnitGroups.NONE) {
                    continue;
                }

                for (boolean completed : new boolean[]{false, true}) {
                    Assertions.assertEquals(expectedSnapshot.getCommandCount(player, unitType, completed),
                                            snapshot.getCommandCount(player, unitType, completed),
                                            "player " + player + ", type " + unitType + (completed ? ", completed" : ""));
                }
            }
        }
    }

    private static @NotNull String describeOffset(int offset) {
        return "context offset 0x" + Integer.toHexString(offset).toUpperCase(Locale.ROOT);
    }


    private UnitAccountingAssert() {
    }
}
