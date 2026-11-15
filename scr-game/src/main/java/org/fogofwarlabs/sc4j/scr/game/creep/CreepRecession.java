package org.fogofwarlabs.sc4j.scr.game.creep;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Retains receding creep records, neighbor-count lists, tile hash chains and the recession countdown.
 * <p>
 * Insertions push to the list front. An update removes a random record from the first nonempty neighbor list, removes
 * its creep tile and returns the record to the free list. A tile-to-record array provides direct lookup.
 * <p>
 * Counter-only instances constructed with nonempty buckets have no records and reject record operations.
 */
public final class CreepRecession {
    /**
     * The number of records.
     */
    public static final int RECORD_COUNT = 1024;
    /**
     * The size of a saved record.
     */
    public static final int SAVED_RECORD_SIZE = 0x14;
    /**
     * The number of tile hash buckets.
     */
    public static final int BUCKET_COUNT = 1024;
    /**
     * The number of saved list heads: the nine neighbor lists and the free list.
     */
    public static final int SAVED_LIST_COUNT = 10;
    /**
     * The number of neighbor-count recession lists, excluding the free list.
     */
    public static final int LIST_COUNT = 9;

    /**
     * The recession period by free record count, one entry per {@code 1 << PERIOD_SHIFT} records.
     */
    private static final int @NotNull [] PERIODS = {1, 3, 5, 6, 7, 8, 9, 9, 9};
    /**
     * The shift from the free record count to its {@link #PERIODS} entry.
     */
    private static final int PERIOD_SHIFT = 7;
    /**
     * The random source of the receding record selection.
     */
    private static final int RECESSION_RANDOM_SOURCE = 27;
    /**
     * The list index BYTE of a free record (the free list is the tenth list).
     */
    private static final int FREE_LIST = 9;
    /**
     * The mask and the row factor of the tile hash ({@code (row * BUCKET_ROW_FACTOR + column) & BUCKET_MASK}).
     */
    private static final int BUCKET_MASK = BUCKET_COUNT - 1;
    private static final int BUCKET_ROW_FACTOR = 17;
    /**
     * The field offsets of a saved record.
     */
    private static final int SAVED_NEXT = 0x00;
    private static final int SAVED_PREVIOUS = 0x04;
    private static final int SAVED_NEXT_HASH = 0x08;
    private static final int SAVED_PREVIOUS_HASH = 0x0C;
    private static final int SAVED_COLUMN = 0x10;
    private static final int SAVED_ROW = 0x11;
    private static final int SAVED_LIST = 0x12;
    /**
     * The steps to the eight neighbors of a tile.
     */
    private static final int @NotNull [] NEIGHBOR_HORIZONTAL = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int @NotNull [] NEIGHBOR_VERTICAL = {-1, -1, -1, 0, 0, 1, 1, 1};
    /**
     * The mask of a count WORD.
     */
    private static final int WORD_MASK = 0xFFFF;
    /**
     * The mask of a column, row or list BYTE.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The multiplier of the state hash.
     */
    private static final int HASH_MULTIPLIER = 31;

    /**
     * Counts neighboring tiles whose tile identifiers belong to the creep group.
     *
     * @param map        the terrain
     * @param horizontal the tile column
     * @param vertical   the tile row
     * @return the number of the eight neighbors whose tile identifier belongs to the creep tile group
     */
    static int countNeighboringCreepTiles(@NotNull GameMap map, int horizontal, int vertical) {
        int count = 0;

        for (int direction = 0; direction < NEIGHBOR_HORIZONTAL.length; direction++) {
            int neighborX = horizontal + NEIGHBOR_HORIZONTAL[direction];
            int neighborY = vertical + NEIGHBOR_VERTICAL[direction];

            if (neighborX >= 0 && neighborX < map.getWidth() && neighborY >= 0 && neighborY < map.getHeight() &&
                TerrainTileset.isCreepTile(map.getTileIdentifier(neighborX, neighborY))) {
                count++;
            }
        }

        return count;
    }

    private int countdown;
    private final int @NotNull [] bucketCounts;

    private final boolean recordsRepresented;
    private final int @NotNull [] listHeads;
    /**
     * The next (older) record of each record's list, or {@code -1}.
     */
    private final int @NotNull [] nextRecords;
    /**
     * The previous (newer) record of each record's list, or {@code -1}.
     */
    private final int @NotNull [] previousRecords;
    /**
     * The next record of each record's tile hash bucket, or {@code -1}.
     */
    private final int @NotNull [] nextHashRecords;
    /**
     * The previous record of each record's tile hash bucket, or {@code -1}.
     */
    private final int @NotNull [] previousHashRecords;
    /**
     * The tile column and row BYTEs of each record, kept after the record is freed.
     */
    private final int @NotNull [] recordColumns;
    private final int @NotNull [] recordRows;
    /**
     * The list BYTE of each record: the neighbor count, or {@link #FREE_LIST} for a free record.
     */
    private final int @NotNull [] recordNeighbors;
    private final int @NotNull [] recordTiles;
    /**
     * The tile hash buckets ({@code (row * BUCKET_ROW_FACTOR + column) & BUCKET_MASK}).
     */
    private final int @NotNull [] bucketHeads;
    private int @NotNull [] tileRecords;

    private int freeHead;
    private int freeCount;

    /**
     * Creates a {@link CreepRecession} instance from established recession counters.
     * <p>
     * With empty buckets, {@code freeCount} records form the free list and the remaining records are unused.
     *
     * @param countdown    the recession countdown DWORD
     * @param freeCount    the free-list count WORD
     * @param bucketCounts the active count WORDs of the neighbor-count lists
     * @throws IllegalArgumentException if the free count or the number of lists is out of range
     */
    public CreepRecession(int countdown, int freeCount, int @NotNull [] bucketCounts) {
        if (freeCount < 0 || freeCount > RECORD_COUNT || bucketCounts.length != LIST_COUNT) {
            throw Exceptions.newIllegalArgumentException("Native recession counters require nine buckets and at most 1024 free entries.");
        }

        this.countdown    = countdown;
        this.bucketCounts = bucketCounts.clone();

        this.recordsRepresented  = Arrays.stream(bucketCounts).allMatch(count -> (count & WORD_MASK) == 0);
        this.listHeads           = new int[LIST_COUNT];
        this.nextRecords         = new int[RECORD_COUNT];
        this.previousRecords     = new int[RECORD_COUNT];
        this.nextHashRecords     = new int[RECORD_COUNT];
        this.previousHashRecords = new int[RECORD_COUNT];
        this.recordColumns       = new int[RECORD_COUNT];
        this.recordRows          = new int[RECORD_COUNT];
        this.recordNeighbors     = new int[RECORD_COUNT];
        this.recordTiles         = new int[RECORD_COUNT];
        this.bucketHeads         = new int[BUCKET_COUNT];
        this.tileRecords         = new int[0];

        this.freeHead  = -1;
        this.freeCount = 0;

        Arrays.fill(this.listHeads, -1);
        Arrays.fill(this.nextRecords, -1);
        Arrays.fill(this.previousRecords, -1);
        Arrays.fill(this.nextHashRecords, -1);
        Arrays.fill(this.previousHashRecords, -1);
        Arrays.fill(this.recordTiles, -1);
        Arrays.fill(this.bucketHeads, -1);

        // Push records in ascending order onto the free list, leaving the last record at its head.
        for (int record = 0; record < freeCount; record++) {
            this.release(record);
        }
    }

    /**
     * @return the retained recession countdown DWORD
     */
    public int getCountdown() {
        return this.countdown;
    }

    /**
     * @return the number of free receding-creep records
     */
    public int getFreeCount() {
        return this.freeCount;
    }

    /**
     * @param neighborCount the neighbor-count list from {@code 0} through {@code 8}
     * @return the number of receding tiles in that list
     */
    public int getBucketCount(int neighborCount) {
        return this.bucketCounts[neighborCount] & WORD_MASK;
    }

    /**
     * Returns the tile at a list position in linked-list traversal order.
     *
     * @param neighborCount the neighbor-count list
     * @param position      the zero-based position from the list head
     * @return the row-major tile index
     * @throws IndexOutOfBoundsException if the list has no such position
     */
    public int getRecedingTile(int neighborCount, int position) {
        int record = this.listHeads[neighborCount];

        for (int index = 0; index < position && record >= 0; index++) {
            record = this.nextRecords[record];
        }

        if (record < 0) {
            throw Exceptions.newIndexOutOfBoundsException("Receding list {0} has no position {1}.", neighborCount, position);
        }

        return this.recordTiles[record];
    }


    /**
     * Applies the countdown and verifies that no record removal is due, without a world context.
     *
     * @throws UnsupportedOperationException if a nonempty list would require tile removal
     */
    public void update() {
        if (this.countdown != 0) {
            this.countdown--;

            return;
        }

        this.countdown = PERIODS[this.freeCount >>> PERIOD_SHIFT];

        for (int count : this.bucketCounts) {
            if ((count & WORD_MASK) != 0) {
                throw Exceptions.newUnsupportedOperationException("Native creep recession requires linked tile removal.");
            }
        }
    }

    /**
     * Applies the recession update: the countdown, then removal of one random record of the first nonempty list.
     * <p>
     * The record leaves its list and the tile table, the tile's receding flag is cleared, the creep tile is removed, and
     * the record returns to the free-list front with the free list index.
     *
     * @param game the game whose map, creep field and random generator are used
     * @throws UnsupportedOperationException if removal is due in a counter-only instance or without a creep field
     */
    public void update(@NotNull Game game) {
        if (this.countdown != 0) {
            this.countdown--;

            return;
        }

        this.countdown = PERIODS[this.freeCount >>> PERIOD_SHIFT];

        for (int list = 0; list < LIST_COUNT; list++) {
            int count = this.bucketCounts[list] & WORD_MASK;

            if (count == 0) {
                continue;
            }

            if (!this.recordsRepresented) {
                throw Exceptions.newUnsupportedOperationException("Native creep recession requires linked tile removal.");
            }

            CreepField creep = game.getCreepField();

            if (creep == null) {
                throw Exceptions.newUnsupportedOperationException("Creep recession requires the creep field.");
            }

            int position = game.getRandom().next(RECESSION_RANDOM_SOURCE, 0, count - 1);
            int record = this.listHeads[list];

            for (int index = 0; index < position; index++) {
                record = this.nextRecords[record];
            }

            this.unlink(record, list);
            this.removeHash(record);

            int tileIndex = this.recordTiles[record];

            this.tileRecords[tileIndex] = -1;

            GameMap map = game.getMap();

            map.setCreepReceding(tileIndex, false);
            creep.removeTile(game, tileIndex % map.getWidth(), tileIndex / map.getWidth());
            this.release(record);

            return;
        }
    }

    /**
     * Assigns a free record to a creep tile and marks the tile as receding.
     *
     * @param game       the game whose map is updated
     * @param horizontal the tile column
     * @param vertical   the tile row
     * @return {@code true} if a record was available, {@code false} otherwise
     * @throws UnsupportedOperationException if the instance is counter-only
     */
    public boolean markReceding(@NotNull Game game, int horizontal, int vertical) {
        this.requireRecords();

        if (this.freeHead < 0) {
            return false;
        }

        GameMap map = game.getMap();

        this.ensureTileRecords(map);

        // The free head leaves the free list, takes the tile and joins its neighbor list and the front of its tile hash
        // bucket.
        int record = this.freeHead;

        this.unlinkFree(record);

        int neighbors = countNeighboringCreepTiles(map, horizontal, vertical);
        int tileIndex = vertical * map.getWidth() + horizontal;

        this.recordColumns[record]   = horizontal & BYTE_MASK;
        this.recordRows[record]      = vertical & BYTE_MASK;
        this.recordNeighbors[record] = neighbors;
        this.recordTiles[record]     = tileIndex;
        this.pushFront(record, neighbors);
        this.insertHash(record);
        this.tileRecords[tileIndex] = record;
        map.setCreepReceding(tileIndex, true);

        return true;
    }

    /**
     * Removes a tile's recession record and clears its receding flag.
     *
     * @param game       the game whose map is updated
     * @param horizontal the tile column
     * @param vertical   the tile row
     * @return {@code true} if the tile had a record, {@code false} otherwise
     * @throws UnsupportedOperationException if the instance is counter-only
     */
    public boolean restore(@NotNull Game game, int horizontal, int vertical) {
        this.requireRecords();

        GameMap map = game.getMap();

        this.ensureTileRecords(map);

        int tileIndex = vertical * map.getWidth() + horizontal;
        int record = this.tileRecords[tileIndex];

        if (record < 0) {
            return false;
        }

        this.tileRecords[tileIndex] = -1;
        this.unlink(record, this.recordNeighbors[record]);
        this.removeHash(record);
        this.release(record);
        map.setCreepReceding(tileIndex, false);

        return true;
    }

    /**
     * Reclassifies the receding neighbors after a tile's creep changed: every neighbor that has creep and is receding moves
     * to the front of the list matching its new neighbor count when that count changed.
     *
     * @param game       the game whose map is inspected
     * @param horizontal the changed tile column
     * @param vertical   the changed tile row
     * @throws IllegalStateException         if a receding neighbor has no record
     * @throws UnsupportedOperationException if a receding neighbor exists in a counter-only instance
     */
    public void updateNeighbors(@NotNull Game game, int horizontal, int vertical) {
        GameMap map = game.getMap();

        for (int direction = 0; direction < NEIGHBOR_HORIZONTAL.length; direction++) {
            int neighborX = horizontal + NEIGHBOR_HORIZONTAL[direction];
            int neighborY = vertical + NEIGHBOR_VERTICAL[direction];

            if (neighborX < 0 || neighborX >= map.getWidth() || neighborY < 0 || neighborY >= map.getHeight()) {
                continue;
            }

            if (!TerrainTileset.isCreepTile(map.getTileIdentifier(neighborX, neighborY)) ||
                (map.getTileFlags(neighborX, neighborY) & MapTileFlags.CREEP_RECEDING) == 0) {
                continue;
            }

            this.requireRecords();
            this.ensureTileRecords(map);

            int record = this.tileRecords[neighborY * map.getWidth() + neighborX];

            if (record < 0) {
                throw Exceptions.newIllegalStateException("A receding creep tile has no recession record.");
            }

            int neighbors = countNeighboringCreepTiles(map, neighborX, neighborY);

            if (neighbors == this.recordNeighbors[record]) {
                continue;
            }

            this.unlink(record, this.recordNeighbors[record]);
            this.recordNeighbors[record] = neighbors;

            this.pushFront(record, neighbors);
        }
    }

    /**
     * Writes the recession into the four sections of the creep recession part: the records (the list links and hash links
     * as one-based indices, then the column, row and list BYTEs), the one-based heads of the tile hash buckets, the
     * one-based heads of the neighbor lists and the free list, and the list counts and the free count WORDs. The countdown
     * is not saved.
     *
     * @param records the record section, little-endian
     * @param buckets the bucket section, little-endian
     * @param lists   the list head section, little-endian
     * @param counts  the count section, little-endian
     */
    public void writeSaveSections(@NotNull ByteBuffer records, @NotNull ByteBuffer buckets, @NotNull ByteBuffer lists, @NotNull ByteBuffer counts) {
        for (int record = 0; record < RECORD_COUNT; record++) {
            int entry = record * SAVED_RECORD_SIZE;

            records.putInt(entry + SAVED_NEXT, this.nextRecords[record] + 1);
            records.putInt(entry + SAVED_PREVIOUS, this.previousRecords[record] + 1);
            records.putInt(entry + SAVED_NEXT_HASH, this.nextHashRecords[record] + 1);
            records.putInt(entry + SAVED_PREVIOUS_HASH, this.previousHashRecords[record] + 1);
            records.put(entry + SAVED_COLUMN, (byte) this.recordColumns[record]);
            records.put(entry + SAVED_ROW, (byte) this.recordRows[record]);
            records.put(entry + SAVED_LIST, (byte) this.recordNeighbors[record]);
        }

        for (int bucket = 0; bucket < BUCKET_COUNT; bucket++) {
            buckets.putInt(bucket * Integer.BYTES, this.bucketHeads[bucket] + 1);
        }

        for (int list = 0; list < LIST_COUNT; list++) {
            lists.putInt(list * Integer.BYTES, this.listHeads[list] + 1);
        }

        lists.putInt(FREE_LIST * Integer.BYTES, this.freeHead + 1);

        for (int list = 0; list < LIST_COUNT; list++) {
            counts.putShort(list * Short.BYTES, (short) this.bucketCounts[list]);
        }

        counts.putShort(FREE_LIST * Short.BYTES, (short) this.freeCount);
    }

    /**
     * Reads the recession from the sections {@link #writeSaveSections} wrote; the countdown keeps its value.
     *
     * @param records the record section, little-endian
     * @param buckets the bucket section, little-endian
     * @param lists   the list head section, little-endian
     * @param counts  the count section, little-endian
     * @param map     the terrain the records' tiles belong to
     * @throws UnsupportedOperationException if the instance is counter-only
     */
    public void readSaveSections(@NotNull ByteBuffer records, @NotNull ByteBuffer buckets, @NotNull ByteBuffer lists, @NotNull ByteBuffer counts,
                                 @NotNull GameMap map) {
        this.requireRecords();

        this.tileRecords = new int[map.getTileCount()];

        Arrays.fill(this.tileRecords, -1);

        for (int record = 0; record < RECORD_COUNT; record++) {
            int entry = record * SAVED_RECORD_SIZE;

            this.nextRecords[record]         = records.getInt(entry + SAVED_NEXT) - 1;
            this.previousRecords[record]     = records.getInt(entry + SAVED_PREVIOUS) - 1;
            this.nextHashRecords[record]     = records.getInt(entry + SAVED_NEXT_HASH) - 1;
            this.previousHashRecords[record] = records.getInt(entry + SAVED_PREVIOUS_HASH) - 1;
            this.recordColumns[record]       = records.get(entry + SAVED_COLUMN) & BYTE_MASK;
            this.recordRows[record]          = records.get(entry + SAVED_ROW) & BYTE_MASK;
            this.recordNeighbors[record]     = records.get(entry + SAVED_LIST) & BYTE_MASK;
            this.recordTiles[record]         = -1;

            if (this.recordNeighbors[record] != FREE_LIST) {
                int tileIndex = this.recordRows[record] * map.getWidth() + this.recordColumns[record];

                this.recordTiles[record] = tileIndex;

                if (tileIndex >= 0 && tileIndex < this.tileRecords.length) {
                    this.tileRecords[tileIndex] = record;
                }
            }
        }

        for (int bucket = 0; bucket < BUCKET_COUNT; bucket++) {
            this.bucketHeads[bucket] = buckets.getInt(bucket * Integer.BYTES) - 1;
        }

        for (int list = 0; list < LIST_COUNT; list++) {
            this.listHeads[list] = lists.getInt(list * Integer.BYTES) - 1;
        }

        this.freeHead = lists.getInt(FREE_LIST * Integer.BYTES) - 1;

        for (int list = 0; list < LIST_COUNT; list++) {
            this.bucketCounts[list] = counts.getShort(list * Short.BYTES) & WORD_MASK;
        }

        this.freeCount = counts.getShort(FREE_LIST * Short.BYTES) & WORD_MASK;
    }

    /**
     * Folds every retained counter and the receding tiles in list order into a diagnostic hash.
     *
     * @param hash the preceding diagnostic hash
     * @return the hash including every retained counter and the receding tiles in list order
     */
    public int computeStateHash(int hash) {
        hash = HASH_MULTIPLIER * hash + this.countdown;
        hash = HASH_MULTIPLIER * hash + this.freeCount;

        for (int count : this.bucketCounts) {
            hash = HASH_MULTIPLIER * hash + (count & WORD_MASK);
        }

        for (int list = 0; list < LIST_COUNT; list++) {
            for (int record = this.listHeads[list]; record >= 0; record = this.nextRecords[record]) {
                hash = HASH_MULTIPLIER * hash + this.recordTiles[record];
            }
        }

        return hash;
    }

    private void requireRecords() {
        if (!this.recordsRepresented) {
            throw Exceptions.newUnsupportedOperationException("Counter-only creep recession has no linked records.");
        }
    }

    private void ensureTileRecords(@NotNull GameMap map) {
        if (this.tileRecords.length != map.getTileCount()) {
            if (this.tileRecords.length != 0) {
                throw Exceptions.newIllegalStateException("Creep recession records belong to another map.");
            }

            this.tileRecords = new int[map.getTileCount()];
            Arrays.fill(this.tileRecords, -1);
        }
    }

    private void pushFront(int record, int list) {
        int head = this.listHeads[list];

        this.nextRecords[record]     = head;
        this.previousRecords[record] = -1;

        if (head >= 0) {
            this.previousRecords[head] = record;
        }

        this.listHeads[list]    = record;
        this.bucketCounts[list] = (this.bucketCounts[list] + 1) & WORD_MASK;
    }

    private void unlink(int record, int list) {
        int next = this.nextRecords[record];
        int previous = this.previousRecords[record];

        if (this.listHeads[list] == record) {
            this.listHeads[list] = next;
        }

        if (next >= 0) {
            this.previousRecords[next] = previous;
        }

        if (previous >= 0) {
            this.nextRecords[previous] = next;
        }

        this.nextRecords[record]     = -1;
        this.previousRecords[record] = -1;
        this.bucketCounts[list]      = (this.bucketCounts[list] - 1) & WORD_MASK;
    }

    /**
     * Pushes a record onto the free list; it keeps its tile column and row.
     */
    private void release(int record) {
        int head = this.freeHead;

        this.recordNeighbors[record] = FREE_LIST;
        this.recordTiles[record]     = -1;
        this.nextRecords[record]     = head;
        this.previousRecords[record] = -1;

        if (head >= 0) {
            this.previousRecords[head] = record;
        }

        this.freeHead = record;
        this.freeCount++;
    }

    /**
     * Takes a record off the free list.
     */
    private void unlinkFree(int record) {
        int next = this.nextRecords[record];
        int previous = this.previousRecords[record];

        if (this.freeHead == record) {
            this.freeHead = next;
        }

        if (next >= 0) {
            this.previousRecords[next] = previous;
        }

        if (previous >= 0) {
            this.nextRecords[previous] = next;
        }

        this.nextRecords[record]     = -1;
        this.previousRecords[record] = -1;
        this.freeCount--;
    }

    private void insertHash(int record) {
        int bucket = this.recordRows[record] * BUCKET_ROW_FACTOR + this.recordColumns[record] & BUCKET_MASK;
        int head = this.bucketHeads[bucket];

        this.nextHashRecords[record]     = head;
        this.previousHashRecords[record] = -1;

        if (head >= 0) {
            this.previousHashRecords[head] = record;
        }

        this.bucketHeads[bucket] = record;
    }

    private void removeHash(int record) {
        int bucket = this.recordRows[record] * BUCKET_ROW_FACTOR + this.recordColumns[record] & BUCKET_MASK;
        int next = this.nextHashRecords[record];
        int previous = this.previousHashRecords[record];

        if (this.bucketHeads[bucket] == record) {
            this.bucketHeads[bucket] = next;
        }

        if (next >= 0) {
            this.previousHashRecords[next] = previous;
        }

        if (previous >= 0) {
            this.nextHashRecords[previous] = next;
        }

        this.nextHashRecords[record]     = -1;
        this.previousHashRecords[record] = -1;
    }
}
