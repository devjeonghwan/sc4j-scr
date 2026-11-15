package org.fogofwarlabs.sc4j.scr.data.format.mpq;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive.MpqEntry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Tests locale selection and neutral-decoy handling with synthetic MPQ archives.
 */
final class MpqArchiveLocaleTest {
    private static final @NotNull String SCENARIO_NAME = "staredit\\scenario.chk";
    private static final byte @NotNull [] SCENARIO = "VER \u0002\u0000\u0000\u0000Í\u0000".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte @NotNull [] OTHER_SCENARIO = "VER \u0002\u0000\u0000\u0000?\u0000".getBytes(StandardCharsets.ISO_8859_1);

    private static final int ARCHIVE_SIZE = 4096;
    private static final int MPQ_MAGIC = 0x1A51504D;
    private static final int HEADER_SIZE = 32;
    private static final int HEADER_SIZE_FIELD = 4;
    private static final int ARCHIVE_SIZE_FIELD = 8;
    private static final int SECTOR_SHIFT_FIELD = 14;
    private static final int HASH_TABLE_POSITION_FIELD = 16;
    private static final int BLOCK_TABLE_POSITION_FIELD = 20;
    private static final int HASH_TABLE_SIZE_FIELD = 24;
    private static final int BLOCK_TABLE_SIZE_FIELD = 28;
    private static final int HASH_TABLE_POSITION = 0x100;
    private static final int BLOCK_TABLE_POSITION = 0x200;
    private static final int FIRST_DATA_POSITION = 0x300;
    private static final int SECOND_DATA_POSITION = 0x400;
    private static final int SECTOR_SHIFT = 3;

    private static final int HASH_ENTRY_COUNT = 16;
    private static final int HASH_ENTRY_DWORD_COUNT = 4;
    private static final int EMPTY = -1;

    private static final int NEUTRAL_LOCALE = 0;
    private static final int GERMAN_LOCALE = 0x407;
    /**
     * The English Storm locale, the default locale of the archive lookups.
     */
    private static final int ENGLISH_LOCALE = 0x409;

    private static final int COMPRESSED_FILE = MpqArchive.MPQ_FILE_EXISTS | MpqArchive.MPQ_FILE_COMPRESS;

    private static byte @NotNull [] createDecoyArchive() {
        byte[] archive = new byte[ARCHIVE_SIZE];

        putHeader(archive, 2);

        int[] hashTable = createEmptyHashTable();

        putProbedEntry(hashTable, 0, MpqArchiveScenario.createHashEntry(SCENARIO_NAME, NEUTRAL_LOCALE, 0));
        putProbedEntry(hashTable, 1, MpqArchiveScenario.createHashEntry(SCENARIO_NAME, ENGLISH_LOCALE, 1));
        MpqArchiveScenario.putInts(archive, HASH_TABLE_POSITION, MpqArchiveScenario.encrypt(hashTable, MpqCryptoUtils.hashKey("(hash table)")));

        int stored = putStoredFile(archive, FIRST_DATA_POSITION, SCENARIO);
        int decoyFlags = COMPRESSED_FILE | MpqArchive.MPQ_FILE_ENCRYPTED;
        int[] blockTable = {0x20, 0, 0, decoyFlags, FIRST_DATA_POSITION, stored, SCENARIO.length, COMPRESSED_FILE};

        MpqArchiveScenario.putInts(archive, BLOCK_TABLE_POSITION, MpqArchiveScenario.encrypt(blockTable, MpqCryptoUtils.hashKey("(block table)")));

        return archive;
    }

    private static void putHeader(byte @NotNull [] archive, int blockTableSize) {
        LittleEndianUtils.writeInt32(archive, 0, MPQ_MAGIC);
        LittleEndianUtils.writeInt32(archive, HEADER_SIZE_FIELD, HEADER_SIZE);
        LittleEndianUtils.writeInt32(archive, ARCHIVE_SIZE_FIELD, archive.length);
        archive[SECTOR_SHIFT_FIELD] = SECTOR_SHIFT;
        LittleEndianUtils.writeInt32(archive, HASH_TABLE_POSITION_FIELD, HASH_TABLE_POSITION);
        LittleEndianUtils.writeInt32(archive, BLOCK_TABLE_POSITION_FIELD, BLOCK_TABLE_POSITION);
        LittleEndianUtils.writeInt32(archive, HASH_TABLE_SIZE_FIELD, HASH_ENTRY_COUNT);
        LittleEndianUtils.writeInt32(archive, BLOCK_TABLE_SIZE_FIELD, blockTableSize);
    }

    private static int @NotNull [] createEmptyHashTable() {
        int[] hashTable = new int[HASH_ENTRY_COUNT * HASH_ENTRY_DWORD_COUNT];

        Arrays.fill(hashTable, EMPTY);

        return hashTable;
    }

    private static void putProbedEntry(int @NotNull [] hashTable, int probeStep, int @NotNull [] entry) {
        int slot = (MpqCryptoUtils.hashTableOffset(SCENARIO_NAME) + probeStep) & (HASH_ENTRY_COUNT - 1);

        System.arraycopy(entry, 0, hashTable, slot * HASH_ENTRY_DWORD_COUNT, HASH_ENTRY_DWORD_COUNT);
    }

    /**
     * Writes a one-sector file whose raw sector equals its content (a stored sector) after its offset table.
     *
     * @param archive  the archive bytes
     * @param position the position of the offset table
     * @param content  the file content
     * @return the stored size: the offset table and the sector
     */
    private static int putStoredFile(byte @NotNull [] archive, int position, byte @NotNull [] content) {
        int tableLength = 2 * Integer.BYTES;

        MpqArchiveScenario.putInts(archive, position, new int[]{tableLength, tableLength + content.length});
        System.arraycopy(content, 0, archive, position + tableLength, content.length);

        return tableLength + content.length;
    }


    @AfterEach
    void restoreLocale() {
        MpqArchive.setLocale(ENGLISH_LOCALE);
    }

    @Test
    void read_file_should_read_english_scenario_behind_neutral_empty_decoy() throws IOException {
        // The first probed entry is a neutral scenario on block 0, an existing, encrypted, compressed block of size 0 at
        // offset 0x20; the next entry is the real scenario under 0x409.
        byte[] archive = createDecoyArchive();

        Assertions.assertEquals(ENGLISH_LOCALE, MpqArchive.getLocale());
        Assertions.assertArrayEquals(SCENARIO, MpqArchive.open(archive, "english").readFile(SCENARIO_NAME));

        // Locale 0 matches the neutral decoy at once, which reads as an empty scenario.
        MpqArchive.setLocale(NEUTRAL_LOCALE);

        Assertions.assertEquals(0, MpqArchive.open(archive, "neutral").readFile(SCENARIO_NAME).length);

        // A German game has no exact entry, so the last neutral candidate, the decoy, wins.
        MpqArchive.setLocale(MpqArchive.toStormLocale("deDE"));

        Assertions.assertEquals(0, MpqArchive.open(archive, "german").readFile(SCENARIO_NAME).length);
    }

    @Test
    void find_entry_should_choose_last_neutral_entry_without_exact_locale() throws IOException {
        // Probe order: a neutral scenario, a German one and a second neutral one; the English lookup takes the last
        // neutral candidate, and the German lookup returns its exact entry at once.
        byte[] archive = new byte[ARCHIVE_SIZE];

        putHeader(archive, 3);

        int[] hashTable = createEmptyHashTable();

        putProbedEntry(hashTable, 0, MpqArchiveScenario.createHashEntry(SCENARIO_NAME, NEUTRAL_LOCALE, 0));
        putProbedEntry(hashTable, 1, MpqArchiveScenario.createHashEntry(SCENARIO_NAME, GERMAN_LOCALE, 1));
        putProbedEntry(hashTable, 2, MpqArchiveScenario.createHashEntry(SCENARIO_NAME, NEUTRAL_LOCALE, 2));
        MpqArchiveScenario.putInts(archive, HASH_TABLE_POSITION, MpqArchiveScenario.encrypt(hashTable, MpqCryptoUtils.hashKey("(hash table)")));

        int stored = putStoredFile(archive, FIRST_DATA_POSITION, OTHER_SCENARIO);

        putStoredFile(archive, SECOND_DATA_POSITION, SCENARIO);

        int[] blockTable = {FIRST_DATA_POSITION, stored, OTHER_SCENARIO.length, COMPRESSED_FILE,
                            SECOND_DATA_POSITION, stored, SCENARIO.length, COMPRESSED_FILE,
                            SECOND_DATA_POSITION, stored, SCENARIO.length, COMPRESSED_FILE};

        MpqArchiveScenario.putInts(archive, BLOCK_TABLE_POSITION, MpqArchiveScenario.encrypt(blockTable, MpqCryptoUtils.hashKey("(block table)")));

        Assertions.assertArrayEquals(SCENARIO, MpqArchive.open(archive, "last neutral").readFile(SCENARIO_NAME));

        MpqEntry neutralEntry = MpqArchive.open(archive, "last neutral").findEntry(SCENARIO_NAME);

        Assertions.assertNotNull(neutralEntry);
        Assertions.assertEquals(SECOND_DATA_POSITION, neutralEntry.getFilePosition());

        MpqArchive.setLocale(GERMAN_LOCALE);

        MpqEntry germanEntry = MpqArchive.open(archive, "german").findEntry(SCENARIO_NAME);

        Assertions.assertNotNull(germanEntry);
        Assertions.assertEquals(SECOND_DATA_POSITION, germanEntry.getFilePosition());

        MpqArchive.setLocale(NEUTRAL_LOCALE);

        Assertions.assertArrayEquals(OTHER_SCENARIO, MpqArchive.open(archive, "first neutral").readFile(SCENARIO_NAME));
    }

    @Test
    void to_storm_locale_should_map_only_five_game_languages_and_default_to_english() {
        Assertions.assertEquals(0x409, MpqArchive.toStormLocale("enUS"));
        Assertions.assertEquals(0x407, MpqArchive.toStormLocale("deDE"));
        Assertions.assertEquals(0x40A, MpqArchive.toStormLocale("esES"));
        Assertions.assertEquals(0x40C, MpqArchive.toStormLocale("frFR"));
        Assertions.assertEquals(0x410, MpqArchive.toStormLocale("itIT"));

        // 0x412, 0x80A, 0x411, 0x404, 0x416 and 0x419 are not among the five, so the setter passes 0x409.
        for (String language : new String[]{"koKR", "esMX", "jaJP", "zhTW", "ptBR", "ruRU", "plPL", "enSG", "KOKR", ""}) {
            Assertions.assertEquals(ENGLISH_LOCALE, MpqArchive.toStormLocale(language), language);
        }
    }
}
