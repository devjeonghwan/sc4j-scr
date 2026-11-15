package org.fogofwarlabs.sc4j.scr.game.player;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that a count index past its table addresses the game-context table that follows it, which the tests reach past the last row and by linear
 * DWORD index because the EUD-visible layout of {@link PlayerUnitCounts#NATIVE_COUNT} DWORDs (unit type rows of player columns) before the next table
 * is the contract.
 */
final class PlayerUnitCountsTest {
    /**
     * The unit type after the last unit type, whose row is past the table.
     */
    private static final int EMPTY_TYPE = ScrUnitIds.NONE;
    /**
     * The player of the counts.
     */
    private static final int PLAYER = 3;
    /**
     * The count in the first row of the following table.
     */
    private static final int FOLLOWING_COUNT = 41;
    /**
     * The count added to the second row past the table.
     */
    private static final int ADDED_COUNT = 5;
    /**
     * The first row of the following table, which the row of {@link #EMPTY_TYPE} reaches.
     */
    private static final int FIRST_FOLLOWING_ROW = 0;
    /**
     * The second row of the following table, which the row after {@link #EMPTY_TYPE} reaches.
     */
    private static final int SECOND_FOLLOWING_ROW = 1;

    @Test
    void row_228_reads_and_writes_the_first_row_of_the_following_table() {
        PlayerUnitCounts counts = PlayerProgressTestUtils.createPlayerUnitCounts();
        PlayerUnitCounts following = PlayerProgressTestUtils.createPlayerUnitCounts();

        counts.setSuccessor(following);
        following.set(FIRST_FOLLOWING_ROW, PLAYER, FOLLOWING_COUNT);

        Assertions.assertEquals(FOLLOWING_COUNT, counts.get(EMPTY_TYPE, PLAYER));

        counts.increment(EMPTY_TYPE, PLAYER);
        counts.add(EMPTY_TYPE + 1, PLAYER, ADDED_COUNT);

        Assertions.assertEquals(FOLLOWING_COUNT + 1, following.get(FIRST_FOLLOWING_ROW, PLAYER));
        Assertions.assertEquals(ADDED_COUNT, following.get(SECOND_FOLLOWING_ROW, PLAYER));
        Assertions.assertEquals(FOLLOWING_COUNT + 1, counts.getByNativeIndex(PlayerUnitCounts.NATIVE_COUNT + PLAYER));
    }

    @Test
    void a_row_past_the_table_without_a_following_table_throws() {
        PlayerUnitCounts counts = PlayerProgressTestUtils.createPlayerUnitCounts();

        Assertions.assertThrows(UnsupportedOperationException.class, () -> counts.get(EMPTY_TYPE, PLAYER));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> counts.increment(EMPTY_TYPE, PLAYER));
    }
}
