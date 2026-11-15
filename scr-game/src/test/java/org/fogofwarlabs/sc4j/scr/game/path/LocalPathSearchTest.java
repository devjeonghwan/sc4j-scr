package org.fogofwarlabs.sc4j.scr.game.path;

import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.ClassicHostTableRegion;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.ClassicHostValueRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Checks visited rectangle coverage and band record allocation.
 */
final class LocalPathSearchTest {
    /**
     * The number of captured visited rectangle cases and the rectangles each case inserts.
     */
    private static final int CASE_COUNT = 1024;
    private static final int OPERATIONS_PER_CASE = 64;
    /**
     * The size of the visited area of every case.
     */
    private static final int VISITED_SIZE = 256;
    /**
     * The positions around the corner of each inserted rectangle whose directions a case filters.
     */
    private static final int PROBE_COUNT = 8;
    private static final int PROBE_ROW_LENGTH = 4;
    /**
     * The direction mask with every direction set.
     */
    private static final int ALL_DIRECTIONS = 255;
    /**
     * The size of a digested case record: the insertion result DWORD and one direction BYTE per probe.
     */
    private static final int CASE_RECORD_SIZE = 12;
    /**
     * The ranges of the generated rectangle origins and of the generated rectangle extents.
     */
    private static final int RECTANGLE_ORIGIN_RANGE = 240;
    private static final int RECTANGLE_EXTENT_RANGE = 17;

    /**
     * The shifts of the top, right and bottom WORDs of a packed rectangle.
     */
    private static final int RECTANGLE_TOP_SHIFT = 16;
    private static final int RECTANGLE_RIGHT_SHIFT = 32;
    private static final int RECTANGLE_BOTTOM_SHIFT = 48;
    /**
     * The shift of the vertical WORD in a packed position.
     */
    private static final int POSITION_VERTICAL_SHIFT = 16;

    /**
     * The classic addresses of the visited band count, the visited records, the band boundaries and the band records.
     */
    private static final int VISITED_BAND_COUNT_ADDRESS = 0x6CAF98;
    private static final int VISITED_RECORDS_ADDRESS = 0x6CAFA0;
    private static final int VISITED_BOUNDARIES_ADDRESS = 0x6CD9A0;
    private static final int VISITED_BAND_RECORDS_ADDRESS = 0x6CDBA8;
    /**
     * The size of a visited record.
     */
    private static final int VISITED_RECORD_SIZE = 0x54;
    /**
     * The number of band boundary entries the split leaves: the three bands and the closing boundary.
     */
    private static final int VISITED_BAND_COUNT = 4;
    /**
     * The left, top, right and bottom edges of the rectangle that splits the visited area into three bands.
     */
    private static final long SPLIT_LEFT = 10L;
    private static final long SPLIT_TOP = 0L;
    private static final long SPLIT_RIGHT = 20L;
    private static final long SPLIT_BOTTOM = 5L;
    /**
     * The offset of the third DWORD of a visited record.
     */
    private static final int RECORD_THIRD_DWORD_OFFSET = 2 * Integer.BYTES;
    /**
     * The record that the part after the right edge of the split rectangle takes.
     */
    private static final int RIGHT_PART_RECORD = 2;

    @Test
    void matches_native_visited_rectangle_insertion_cases() throws NoSuchAlgorithmException {
        LocalPathSearch search = new LocalPathSearch();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteBuffer output = ByteBuffer.allocate(CASE_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int caseIndex = 0; caseIndex < CASE_COUNT; caseIndex++) {
            search.resetVisited(VISITED_SIZE);

            for (int operation = 0; operation < OPERATIONS_PER_CASE; operation++) {
                int left = (caseIndex * 17 + operation * 37) % RECTANGLE_ORIGIN_RANGE;
                int top = (caseIndex * 11 + operation * 53) % RECTANGLE_ORIGIN_RANGE;
                int width = (caseIndex + operation * 13) % RECTANGLE_EXTENT_RANGE;
                int height = (caseIndex * 3 + operation * 7) % RECTANGLE_EXTENT_RANGE;
                long rectangle = (long) left | (long) top << RECTANGLE_TOP_SHIFT | (long) (left + width) << RECTANGLE_RIGHT_SHIFT |
                                 (long) (top + height) << RECTANGLE_BOTTOM_SHIFT;

                output.clear();
                output.putInt(search.addVisitedRectangle(rectangle) ? 1 : 0);

                for (int probe = 0; probe < PROBE_COUNT; probe++) {
                    int horizontal = Math.floorMod(left + probe % PROBE_ROW_LENGTH - 1, VISITED_SIZE);
                    int vertical = Math.floorMod(top + probe / PROBE_ROW_LENGTH - 1, VISITED_SIZE);

                    output.put((byte) search.filterDirections(horizontal | vertical << POSITION_VERTICAL_SHIFT, ALL_DIRECTIONS));
                }

                digest.update(output.array());
            }
        }

        Assertions.assertEquals("8f67ba219d97e17014a652847099bc876c8b90ed94167b136c033232fa17b4c0", HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void add_visited_rectangle_should_give_split_bands_the_next_pool_records() {
        LocalPathSearch search = new LocalPathSearch();
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new ClassicHostValueRegion(VISITED_BAND_COUNT_ADDRESS, search::getVisitedBandCount, search::setVisitedBandCount));
        space.add(new ClassicHostTableRegion(VISITED_RECORDS_ADDRESS, search.getVisitedRecords()));
        space.add(new ClassicHostTableRegion(VISITED_BOUNDARIES_ADDRESS, search.getVisitedBoundaries()));
        space.add(new ClassicHostTableRegion(VISITED_BAND_RECORDS_ADDRESS, search.getVisitedBandRecords()));
        search.resetVisited(VISITED_SIZE);

        Assertions.assertEquals(-1, space.readDword(VISITED_BAND_RECORDS_ADDRESS + Integer.BYTES),
                                "Resetting the visited records stores -1 after the first map entry.");

        search.addVisitedRectangle(SPLIT_LEFT | SPLIT_TOP << RECTANGLE_TOP_SHIFT | SPLIT_RIGHT << RECTANGLE_RIGHT_SHIFT |
                                   SPLIT_BOTTOM << RECTANGLE_BOTTOM_SHIFT);

        Assertions.assertEquals(3, space.readDword(VISITED_BAND_COUNT_ADDRESS));

        int[] boundaries = {0, 10, 21, 256};

        // The part before the left edge and the part after the right edge take new records; the covered band keeps record 0.
        int[] bandRecords = {1, 0, 2, -1};

        for (int band = 0; band < VISITED_BAND_COUNT; band++) {
            Assertions.assertEquals(boundaries[band], space.readDword(VISITED_BOUNDARIES_ADDRESS + Integer.BYTES * band));
            Assertions.assertEquals(bandRecords[band], space.readDword(VISITED_BAND_RECORDS_ADDRESS + Integer.BYTES * band));
        }

        Assertions.assertEquals(1, space.readDword(VISITED_RECORDS_ADDRESS));
        Assertions.assertEquals(0, space.readDword(VISITED_RECORDS_ADDRESS + Integer.BYTES));
        Assertions.assertEquals(5, space.readDword(VISITED_RECORDS_ADDRESS + RECORD_THIRD_DWORD_OFFSET));
        Assertions.assertEquals(0, space.readDword(VISITED_RECORDS_ADDRESS + VISITED_RECORD_SIZE), "record 1 copied the empty record");
        Assertions.assertEquals(0, space.readDword(VISITED_RECORDS_ADDRESS + RIGHT_PART_RECORD * VISITED_RECORD_SIZE));
    }
}
