package org.fogofwarlabs.sc4j.scr.data.format.mpq;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive.MpqEntry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Deflater;

/**
 * Tests tolerant header, table and sector handling with synthetic protected MPQ archives.
 */
final class MpqArchiveProtectionTest {
    private static final int DEFAULT_ARCHIVE_SIZE = 4096;
    private static final int MULTISECTOR_ARCHIVE_SIZE = 16384;
    private static final int HASH_TABLE_POSITION = 0x100;
    private static final int BLOCK_TABLE_POSITION = 0x200;
    private static final int SCENARIO_DATA_POSITION = 0x300;
    private static final int ATTRIBUTES_DATA_POSITION = 0x400;
    private static final int UNRELATED_ENTRY_SLOT_DISTANCE = 5;
    private static final int NEUTRAL_ENTRY_SLOT_DISTANCE = 2;
    private static final int ZLIB_TEXT_REPEAT_COUNT = 40;
    private static final int IMPLODED_TEXT_REPEAT_COUNT = 400;
    private static final int COMPRESSED_SECTOR_COUNT = 2;
    private static final int STORED_SECTOR_TAIL_SIZE = 32;

    private static final int HASH_ENTRY_DWORD_COUNT = 4;
    private static final int BLOCK_ENTRY_DWORD_COUNT = 4;
    private static final int HASH_ENTRY_SIZE = HASH_ENTRY_DWORD_COUNT * Integer.BYTES;
    private static final int TWO_SECTOR_OFFSET_TABLE_SIZE = 3 * Integer.BYTES;
    private static final int BASE_SECTOR_SIZE = 512;
    private static final int MPQ_MAGIC = 0x1A51504D;
    private static final int HEADER_SIZE = 32;
    private static final int HEADER_SIZE_FIELD = 4;
    private static final int ARCHIVE_SIZE_FIELD = 8;
    private static final int ARCHIVE_VERSION_FIELD = 12;
    private static final int ARCHIVE_VERSION = 1;
    private static final int SECTOR_SHIFT_FIELD = 14;
    private static final int HASH_TABLE_POSITION_FIELD = 16;
    private static final int BLOCK_TABLE_POSITION_FIELD = 20;
    private static final int HASH_TABLE_SIZE_FIELD = 24;
    private static final int BLOCK_TABLE_SIZE_FIELD = 28;
    private static final int SECTOR_SHIFT = 3;
    private static final int SECTOR_SIZE = BASE_SECTOR_SIZE << SECTOR_SHIFT;
    private static final int HASH_ENTRY_COUNT = 16;

    private static final int ATTRIBUTES_VERSION = 100;
    private static final int ATTRIBUTES_FLAGS_OFFSET = 4;
    private static final int CRC32_AND_FILE_TIME_FLAGS = 3;
    private static final int UNKNOWN_ATTRIBUTE_FLAG = 8;

    private static final int CUT_SECTOR_SIZE = 100;
    private static final int MISSING_SECTOR_BYTES = 50;

    private static final byte PKWARE_COMPRESSION = 0x08;
    private static final byte ZLIB_COMPRESSION = 0x02;
    private static final int DEFLATE_BUFFER_MARGIN = 64;
    private static final int COMPRESSION_MASK_SIZE = Byte.BYTES;

    private static final @NotNull String SCENARIO_NAME = "staredit\\scenario.chk";
    private static final @NotNull String SCENARIO_PLAIN_NAME = "scenario.chk";
    private static final byte @NotNull [] SCENARIO = "VER \u0002\u0000\u0000\u0000Í\u0000".getBytes(StandardCharsets.ISO_8859_1);

    private static final int EMPTY = -1;
    private static final int DELETED = -2;
    private static final int KOREAN_LOCALE = 0x412;

    /**
     * The first probed slot of {@link #SCENARIO_NAME} in a {@code 16}-entry hash table.
     */
    private static final int SCENARIO_START_SLOT = 13;

    private static final int PATCH_FILE = 0x02000000;
    private static final int COMPRESSED_FILE = MpqArchive.MPQ_FILE_EXISTS | MpqArchive.MPQ_FILE_COMPRESS;
    /**
     * The block flags left after the map reader clears the single-unit and patch flags: exists and compress.
     */
    private static final int CLEARED_FLAGS = 0x80000200;

    private static byte @NotNull [] createAttributesArchive(int attributeFlags, int attributesSize) {
        byte[] archive = new byte[DEFAULT_ARCHIVE_SIZE];

        putHeader(archive, 0, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 2);

        int[] hashTable = createHashTable(MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, 0));
        int attributesSlot = MpqCryptoUtils.hashTableOffset("(attributes)") & (HASH_ENTRY_COUNT - 1);
        int[] attributesEntry = MpqArchiveScenario.createHashEntry("(attributes)", 0, 1);

        System.arraycopy(attributesEntry, 0, hashTable, attributesSlot * HASH_ENTRY_DWORD_COUNT, HASH_ENTRY_DWORD_COUNT);
        putHashTable(archive, HASH_TABLE_POSITION, hashTable);

        int stored = putSectors(archive, SCENARIO_DATA_POSITION, new byte[][]{SCENARIO}, 0);

        LittleEndianUtils.writeInt32(archive, ATTRIBUTES_DATA_POSITION, ATTRIBUTES_VERSION);
        LittleEndianUtils.writeInt32(archive, ATTRIBUTES_DATA_POSITION + ATTRIBUTES_FLAGS_OFFSET, attributeFlags);
        putBlockTable(archive, BLOCK_TABLE_POSITION, new int[]{SCENARIO_DATA_POSITION, stored, SCENARIO.length, COMPRESSED_FILE,
                                                               ATTRIBUTES_DATA_POSITION, attributesSize, attributesSize, MpqArchive.MPQ_FILE_EXISTS});

        return archive;
    }

    private static byte @NotNull [] createCutSectorArchive(int dataPosition, byte @NotNull [] content) {
        int tableLength = TWO_SECTOR_OFFSET_TABLE_SIZE;
        byte[] archive = new byte[dataPosition + tableLength + content.length - MISSING_SECTOR_BYTES];

        putHeader(archive, 0, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 1);
        putHashTable(archive, HASH_TABLE_POSITION, createHashTable(MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, 0)));
        MpqArchiveScenario.putInts(archive, dataPosition, new int[]{tableLength, tableLength + SECTOR_SIZE, tableLength + content.length});
        System.arraycopy(content, 0, archive, dataPosition + tableLength, content.length - MISSING_SECTOR_BYTES);
        putBlockTable(archive, BLOCK_TABLE_POSITION, new int[]{dataPosition, tableLength + content.length, content.length, COMPRESSED_FILE});

        return archive;
    }

    private static void putHeader(byte @NotNull [] archive, int offset, int headerSize,
                                  int hashTablePosition, int blockTablePosition,
                                  int hashTableSize, int blockTableSize) {
        LittleEndianUtils.writeInt32(archive, offset, MPQ_MAGIC);
        LittleEndianUtils.writeInt32(archive, offset + HEADER_SIZE_FIELD, headerSize);
        LittleEndianUtils.writeInt32(archive, offset + ARCHIVE_SIZE_FIELD, Integer.MAX_VALUE);
        archive[offset + ARCHIVE_VERSION_FIELD] = ARCHIVE_VERSION;
        archive[offset + SECTOR_SHIFT_FIELD]    = SECTOR_SHIFT;
        LittleEndianUtils.writeInt32(archive, offset + HASH_TABLE_POSITION_FIELD, hashTablePosition);
        LittleEndianUtils.writeInt32(archive, offset + BLOCK_TABLE_POSITION_FIELD, blockTablePosition);
        LittleEndianUtils.writeInt32(archive, offset + HASH_TABLE_SIZE_FIELD, hashTableSize);
        LittleEndianUtils.writeInt32(archive, offset + BLOCK_TABLE_SIZE_FIELD, blockTableSize);
    }

    private static int @NotNull [] createHashTable(int @NotNull [] entry) {
        int[] hashTable = new int[HASH_ENTRY_COUNT * HASH_ENTRY_DWORD_COUNT];

        Arrays.fill(hashTable, EMPTY);

        int startIndex = MpqCryptoUtils.hashTableOffset(SCENARIO_NAME) & (HASH_ENTRY_COUNT - 1);

        System.arraycopy(entry, 0, hashTable, startIndex * HASH_ENTRY_DWORD_COUNT, HASH_ENTRY_DWORD_COUNT);

        return hashTable;
    }

    private static void putHashTable(byte @NotNull [] archive, int position, int @NotNull [] hashTable) {
        MpqArchiveScenario.putInts(archive, position, MpqArchiveScenario.encrypt(hashTable, MpqCryptoUtils.hashKey("(hash table)")));
    }

    private static void putBlockTable(byte @NotNull [] archive, int position, int @NotNull [] blockTable) {
        MpqArchiveScenario.putInts(archive, position, MpqArchiveScenario.encrypt(blockTable, MpqCryptoUtils.hashKey("(block table)")));
    }

    /**
     * Writes raw sectors after an offset table, encrypting both with the given file key when it is nonzero.
     *
     * @param archive  the archive bytes
     * @param position the position of the offset table
     * @param sectors  the raw sectors
     * @param fileKey  the file key, {@code 0} for plain sectors
     * @return the stored size: the offset table and the sectors
     */
    private static int putSectors(byte @NotNull [] archive, int position, byte @NotNull [] @NotNull [] sectors, int fileKey) {
        int[] offsets = new int[sectors.length + 1];

        offsets[0] = offsets.length * Integer.BYTES;

        for (int sectorIndex = 0; sectorIndex < sectors.length; sectorIndex++) {
            offsets[sectorIndex + 1] = offsets[sectorIndex] + sectors[sectorIndex].length;
        }

        MpqArchiveScenario.putInts(archive, position, fileKey == 0 ? offsets : MpqArchiveScenario.encrypt(offsets, fileKey - 1));

        for (int sectorIndex = 0; sectorIndex < sectors.length; sectorIndex++) {
            byte[] sector = sectors[sectorIndex];

            if (fileKey != 0) {
                sector = encryptBytes(sector, fileKey + sectorIndex);
            }

            System.arraycopy(sector, 0, archive, position + offsets[sectorIndex], sector.length);
        }

        return offsets[sectors.length];
    }

    private static byte @NotNull [] prependCompressionMask(byte mask, byte @NotNull [] data) {
        byte[] prefixed = new byte[data.length + COMPRESSION_MASK_SIZE];

        prefixed[0] = mask;
        System.arraycopy(data, 0, prefixed, COMPRESSION_MASK_SIZE, data.length);

        return prefixed;
    }

    private static byte @NotNull [] deflate(byte @NotNull [] data) {
        byte[] buffer;
        int length;

        try (Deflater deflater = new Deflater()) {
            deflater.setInput(data);
            deflater.finish();

            buffer = new byte[data.length + DEFLATE_BUFFER_MARGIN];
            length = deflater.deflate(buffer);
        }

        return Arrays.copyOf(buffer, length);
    }

    private static byte @NotNull [] encryptBytes(byte @NotNull [] data, int key) {
        int[] values = new int[data.length / Integer.BYTES];

        for (int valueIndex = 0; valueIndex < values.length; valueIndex++) {
            values[valueIndex] = LittleEndianUtils.readInt32(data, valueIndex * Integer.BYTES);
        }

        byte[] encrypted = data.clone();

        MpqArchiveScenario.putInts(encrypted, 0, MpqArchiveScenario.encrypt(values, key));

        return encrypted;
    }


    @Test
    void open_should_search_header_at_sector_boundaries_and_relocate_blocks() throws IOException {
        // A rejected candidate at 0 (header size below 32) and one at 512 (block table above 0x40000) precede the real
        // header at 1024; tables and file data are relative to that header.
        byte[] archive = new byte[DEFAULT_ARCHIVE_SIZE];

        putHeader(archive, 0, 0x10, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 1);
        putHeader(archive, 512, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 0x40001);

        int headerOffset = 1024;

        putHeader(archive, headerOffset, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 1);
        putHashTable(archive, headerOffset + HASH_TABLE_POSITION, createHashTable(MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, 0)));

        int dataPosition = 0x300;
        int stored = putSectors(archive, headerOffset + dataPosition, new byte[][]{SCENARIO}, 0);

        putBlockTable(archive, headerOffset + BLOCK_TABLE_POSITION, new int[]{dataPosition, stored, SCENARIO.length, COMPRESSED_FILE});

        MpqArchive mpqArchive = MpqArchive.open(archive, "relocated");

        Assertions.assertEquals(headerOffset, mpqArchive.getHeaderOffset());
        Assertions.assertArrayEquals(SCENARIO, mpqArchive.readFile(SCENARIO_NAME));

        MpqEntry relocatedEntry = mpqArchive.findEntry(SCENARIO_NAME);

        Assertions.assertNotNull(relocatedEntry);
        Assertions.assertEquals(headerOffset + dataPosition, relocatedEntry.getFilePosition());
    }

    @Test
    void read_file_should_read_signed_table_positions_before_header() throws IOException {
        // The tables sit before the header: the positions are negative 32-bit offsets from the header at 2048.
        byte[] archive = new byte[DEFAULT_ARCHIVE_SIZE];
        int headerOffset = 2048;

        putHeader(archive, headerOffset, HEADER_SIZE, -0x700, -0x600, HASH_ENTRY_COUNT, 1);
        putHashTable(archive, headerOffset - 0x700, createHashTable(MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, 0)));

        int stored = putSectors(archive, headerOffset + 0x100, new byte[][]{SCENARIO}, 0);

        putBlockTable(archive, headerOffset - 0x600, new int[]{0x100, stored, SCENARIO.length, COMPRESSED_FILE});

        Assertions.assertArrayEquals(SCENARIO, MpqArchive.open(archive, "signed").readFile(SCENARIO_NAME));
    }

    @Test
    void read_file_should_mask_hash_table_size_and_ignore_invalid_block_indexes() throws IOException {
        // The header hash size 0x10000010 is masked to 16 entries; an unrelated entry points far past the block table.
        byte[] archive = new byte[DEFAULT_ARCHIVE_SIZE];

        putHeader(archive, 0, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, 0x10000000 | HASH_ENTRY_COUNT, 1);

        int[] hashTable = createHashTable(MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, 0));
        int unrelatedIndex = (MpqCryptoUtils.hashTableOffset(SCENARIO_NAME) + UNRELATED_ENTRY_SLOT_DISTANCE) & (HASH_ENTRY_COUNT - 1);

        System.arraycopy(new int[]{0x12345678, 0x9ABCDEF0, 0, 0x0FFFFFF0}, 0,
                         hashTable, unrelatedIndex * HASH_ENTRY_DWORD_COUNT, HASH_ENTRY_DWORD_COUNT);
        putHashTable(archive, HASH_TABLE_POSITION, hashTable);

        int stored = putSectors(archive, SCENARIO_DATA_POSITION, new byte[][]{SCENARIO}, 0);

        putBlockTable(archive, BLOCK_TABLE_POSITION, new int[]{SCENARIO_DATA_POSITION, stored, SCENARIO.length, COMPRESSED_FILE});

        Assertions.assertArrayEquals(SCENARIO, MpqArchive.open(archive, "masked").readFile(SCENARIO_NAME));
    }

    @Test
    void read_file_should_read_block_table_overlapping_header_and_past_file_end() throws IOException {
        // The eud_com.scx layout: the block table starts at offset 0 and declares more entries than the file holds. The
        // entries past the end are read as zero and decrypted; the scenario uses an entry inside the file.
        byte[] archive = new byte[DEFAULT_ARCHIVE_SIZE];
        int blockCount = 0x3FFF0;
        int scenarioBlock = 0x60;

        putHeader(archive, 0, HEADER_SIZE, 0x800, 0, HASH_ENTRY_COUNT, blockCount);
        putHashTable(archive, 0x800, createHashTable(MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, scenarioBlock)));

        int stored = putSectors(archive, 0xA00, new byte[][]{SCENARIO}, 0);
        int[] blocks = new int[(scenarioBlock + 1) * BLOCK_ENTRY_DWORD_COUNT];

        System.arraycopy(new int[]{0xA00, stored, SCENARIO.length, COMPRESSED_FILE}, 0,
                         blocks, scenarioBlock * BLOCK_ENTRY_DWORD_COUNT, BLOCK_ENTRY_DWORD_COUNT);

        int[] encryptedBlocks = MpqArchiveScenario.encrypt(blocks, MpqCryptoUtils.hashKey("(block table)"));

        // Keep the plain header in place and write only the entries after it. The header words decrypt to garbage, but
        // the cipher state forgets a changed DWORD after 14 DWORDs, so the later entries still decrypt as written.
        for (int valueIndex = HEADER_SIZE / Integer.BYTES; valueIndex < encryptedBlocks.length; valueIndex++) {
            LittleEndianUtils.writeInt32(archive, valueIndex * Integer.BYTES, encryptedBlocks[valueIndex]);
        }

        Assertions.assertArrayEquals(SCENARIO, MpqArchive.open(archive, "overlap").readFile(SCENARIO_NAME));
    }

    @Test
    void read_file_should_read_hash_table_cut_by_file_end() throws IOException {
        // The file ends after 14 of the 16 declared hash entries; the scenario starts probing at slot 13, inside the file.
        // The two missing entries are read as zero and decrypted like the rest.
        int presentEntryCount = 14;
        byte[] archive = new byte[0x800 + presentEntryCount * HASH_ENTRY_SIZE];

        putHeader(archive, 0, HEADER_SIZE, 0x800, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 1);

        int stored = putSectors(archive, SCENARIO_DATA_POSITION, new byte[][]{SCENARIO}, 0);

        putBlockTable(archive, BLOCK_TABLE_POSITION, new int[]{SCENARIO_DATA_POSITION, stored, SCENARIO.length, COMPRESSED_FILE});

        int[] hashTable = createHashTable(MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, 0));
        int[] encrypted = MpqArchiveScenario.encrypt(hashTable, MpqCryptoUtils.hashKey("(hash table)"));

        MpqArchiveScenario.putInts(archive, 0x800, Arrays.copyOf(encrypted, presentEntryCount * HASH_ENTRY_DWORD_COUNT));

        Assertions.assertEquals(SCENARIO_START_SLOT, MpqCryptoUtils.hashTableOffset(SCENARIO_NAME) & (HASH_ENTRY_COUNT - 1));
        Assertions.assertArrayEquals(SCENARIO, MpqArchive.open(archive, "cut").readFile(SCENARIO_NAME));
    }

    @Test
    void read_file_should_skip_deleted_and_localized_decoys_before_neutral_entry() throws IOException {
        // Probe order: a deleted entry, a Korean-locale decoy pointing at garbage, then the neutral scenario.
        byte[] archive = new byte[DEFAULT_ARCHIVE_SIZE];

        putHeader(archive, 0, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 2);

        int[] hashTable = new int[HASH_ENTRY_COUNT * HASH_ENTRY_DWORD_COUNT];

        Arrays.fill(hashTable, EMPTY);

        int startIndex = MpqCryptoUtils.hashTableOffset(SCENARIO_NAME) & (HASH_ENTRY_COUNT - 1);
        int[] deleted = MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, DELETED);
        int[] decoy = MpqArchiveScenario.createHashEntry(SCENARIO_NAME, KOREAN_LOCALE, 1);
        int[] real = MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, 0);

        int decoySlot = (startIndex + 1) & (HASH_ENTRY_COUNT - 1);
        int realSlot = (startIndex + NEUTRAL_ENTRY_SLOT_DISTANCE) & (HASH_ENTRY_COUNT - 1);

        System.arraycopy(deleted, 0, hashTable, startIndex * HASH_ENTRY_DWORD_COUNT, HASH_ENTRY_DWORD_COUNT);
        System.arraycopy(decoy, 0, hashTable, decoySlot * HASH_ENTRY_DWORD_COUNT, HASH_ENTRY_DWORD_COUNT);
        System.arraycopy(real, 0, hashTable, realSlot * HASH_ENTRY_DWORD_COUNT, HASH_ENTRY_DWORD_COUNT);
        putHashTable(archive, HASH_TABLE_POSITION, hashTable);

        int stored = putSectors(archive, SCENARIO_DATA_POSITION, new byte[][]{SCENARIO}, 0);

        putBlockTable(archive, BLOCK_TABLE_POSITION,
                      new int[]{SCENARIO_DATA_POSITION, stored, SCENARIO.length, MpqArchive.MPQ_FILE_EXISTS | MpqArchive.MPQ_FILE_COMPRESS,
                                0x380, 4, 4, MpqArchive.MPQ_FILE_EXISTS});

        Assertions.assertArrayEquals(SCENARIO, MpqArchive.open(archive, "decoys").readFile(SCENARIO_NAME));
    }

    @Test
    void find_entry_should_require_exists_flag_of_selected_block() throws IOException {
        byte[] archive = new byte[DEFAULT_ARCHIVE_SIZE];

        putHeader(archive, 0, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 1);
        putHashTable(archive, HASH_TABLE_POSITION, createHashTable(MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, 0)));

        int stored = putSectors(archive, SCENARIO_DATA_POSITION, new byte[][]{SCENARIO}, 0);

        putBlockTable(archive, BLOCK_TABLE_POSITION, new int[]{SCENARIO_DATA_POSITION, stored, SCENARIO.length, MpqArchive.MPQ_FILE_COMPRESS});

        MpqArchive mpqArchive = MpqArchive.open(archive, "missing");

        Assertions.assertNull(mpqArchive.findEntry(SCENARIO_NAME));
        Assertions.assertThrows(IOException.class, () -> mpqArchive.readFile(SCENARIO_NAME));
    }

    @Test
    void read_file_should_clear_single_unit_and_patch_flags_and_read_sectors() throws IOException {
        // The map reader clears MPQ_FILE_SINGLE_UNIT, so the data needs a sector offset table.
        byte[] archive = new byte[DEFAULT_ARCHIVE_SIZE];

        putHeader(archive, 0, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 1);
        putHashTable(archive, HASH_TABLE_POSITION, createHashTable(MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, 0)));

        int stored = putSectors(archive, SCENARIO_DATA_POSITION, new byte[][]{SCENARIO}, 0);
        int flags = MpqArchive.MPQ_FILE_EXISTS | MpqArchive.MPQ_FILE_COMPRESS | MpqArchive.MPQ_FILE_SINGLE_UNIT | PATCH_FILE;

        putBlockTable(archive, BLOCK_TABLE_POSITION, new int[]{SCENARIO_DATA_POSITION, stored, SCENARIO.length, flags});

        MpqArchive mpqArchive = MpqArchive.open(archive, "single");

        Assertions.assertArrayEquals(SCENARIO, mpqArchive.readFile(SCENARIO_NAME));

        MpqEntry clearedFlagsEntry = mpqArchive.findEntry(SCENARIO_NAME);

        Assertions.assertNotNull(clearedFlagsEntry);
        Assertions.assertEquals(CLEARED_FLAGS, clearedFlagsEntry.getFlags());
    }

    @Test
    void read_file_should_decrypt_fixed_key_file_with_unrelocated_position() throws IOException {
        // Header at 512: the adjusted key uses the block offset relative to the header.
        byte[] archive = new byte[DEFAULT_ARCHIVE_SIZE];
        int headerOffset = 512;

        putHeader(archive, headerOffset, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 1);
        putHashTable(archive, headerOffset + HASH_TABLE_POSITION, createHashTable(MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, 0)));

        int dataPosition = 0x300;
        int flags = MpqArchive.MPQ_FILE_EXISTS | MpqArchive.MPQ_FILE_COMPRESS | MpqArchive.MPQ_FILE_ENCRYPTED | MpqArchive.MPQ_FILE_FIX_KEY;
        int fileKey = (MpqCryptoUtils.hashKey(SCENARIO_PLAIN_NAME) + dataPosition) ^ SCENARIO.length;
        int stored = putSectors(archive, headerOffset + dataPosition, new byte[][]{SCENARIO}, fileKey);

        putBlockTable(archive, headerOffset + BLOCK_TABLE_POSITION, new int[]{dataPosition, stored, SCENARIO.length, flags});

        Assertions.assertArrayEquals(SCENARIO, MpqArchive.open(archive, "fixed key").readFile(SCENARIO_NAME));
    }

    @Test
    void read_file_should_explode_implode_flag_sectors_without_mask_byte() throws IOException {
        byte[] archive = new byte[DEFAULT_ARCHIVE_SIZE];

        putHeader(archive, 0, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 1);
        putHashTable(archive, HASH_TABLE_POSITION, createHashTable(MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, 0)));

        int stored = putSectors(archive, SCENARIO_DATA_POSITION, new byte[][]{MpqTestResources.IMPLODED_STREAM}, 0);

        putBlockTable(archive, BLOCK_TABLE_POSITION,
                      new int[]{SCENARIO_DATA_POSITION, stored, MpqTestResources.IMPLODED_TEXT.length(),
                                MpqArchive.MPQ_FILE_EXISTS | MpqArchive.MPQ_FILE_IMPLODE});

        byte[] content = MpqArchive.open(archive, "imploded").readFile(SCENARIO_NAME);

        Assertions.assertEquals(MpqTestResources.IMPLODED_TEXT, new String(content, StandardCharsets.US_ASCII));
    }

    @Test
    void read_file_should_decompress_masked_sectors_in_fixed_method_order() throws IOException {
        // Three sectors: PKWare (0x08), zlib with its header (0x02), and a stored sector whose raw size reaches the
        // expected size, which is copied without reading a mask byte.
        byte[] zlibText = "zlib sector".repeat(ZLIB_TEXT_REPEAT_COUNT).getBytes(StandardCharsets.US_ASCII);
        byte[] implodedText = MpqTestResources.IMPLODED_TEXT.repeat(IMPLODED_TEXT_REPEAT_COUNT).getBytes(StandardCharsets.US_ASCII);
        byte[] firstSector = Arrays.copyOf(implodedText, SECTOR_SIZE);
        byte[] content = new byte[SECTOR_SIZE * COMPRESSED_SECTOR_COUNT + STORED_SECTOR_TAIL_SIZE];

        System.arraycopy(firstSector, 0, content, 0, SECTOR_SIZE);

        byte[] secondSector = Arrays.copyOf(zlibText, SECTOR_SIZE);

        System.arraycopy(secondSector, 0, content, SECTOR_SIZE, SECTOR_SIZE);
        Arrays.fill(content, SECTOR_SIZE * COMPRESSED_SECTOR_COUNT, content.length, (byte) 0x5A);

        // The PKWare sector decodes only 13 bytes; the remaining sector bytes stay zero.
        byte[] expected = content.clone();

        Arrays.fill(expected, 0, SECTOR_SIZE, (byte) 0);
        System.arraycopy(MpqTestResources.IMPLODED_TEXT.getBytes(StandardCharsets.US_ASCII), 0, expected, 0, MpqTestResources.IMPLODED_TEXT.length());

        byte[] archive = new byte[MULTISECTOR_ARCHIVE_SIZE];

        putHeader(archive, 0, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 1);
        putHashTable(archive, HASH_TABLE_POSITION, createHashTable(MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, 0)));

        byte[][] sectors = {prependCompressionMask(PKWARE_COMPRESSION, MpqTestResources.IMPLODED_STREAM),
                            prependCompressionMask(ZLIB_COMPRESSION, deflate(secondSector)),
                            Arrays.copyOfRange(content, SECTOR_SIZE * COMPRESSED_SECTOR_COUNT, content.length)};
        int stored = putSectors(archive, SCENARIO_DATA_POSITION, sectors, 0);

        putBlockTable(archive, BLOCK_TABLE_POSITION, new int[]{SCENARIO_DATA_POSITION, stored, content.length, COMPRESSED_FILE});

        Assertions.assertArrayEquals(expected, MpqArchive.open(archive, "masked").readFile(SCENARIO_NAME));
    }

    @Test
    void read_file_should_read_decreasing_offset_table_as_empty_content() throws IOException {
        // The map flag turns a decreasing sector offset table into a successful read of nothing.
        byte[] archive = new byte[DEFAULT_ARCHIVE_SIZE];

        putHeader(archive, 0, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 1);
        putHashTable(archive, HASH_TABLE_POSITION, createHashTable(MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, 0)));
        putSectors(archive, SCENARIO_DATA_POSITION, new byte[][]{SCENARIO}, 0);
        LittleEndianUtils.writeInt32(archive, SCENARIO_DATA_POSITION, 0x7FFF);
        putBlockTable(archive, BLOCK_TABLE_POSITION, new int[]{SCENARIO_DATA_POSITION, 64, SCENARIO.length, COMPRESSED_FILE});

        Assertions.assertArrayEquals(new byte[SCENARIO.length], MpqArchive.open(archive, "decreasing").readFile(SCENARIO_NAME));
    }

    @Test
    void read_file_should_count_cut_sector_inside_retry_read_page_as_complete() throws IOException {
        // Two stored sectors; the file ends 50 bytes into the 100-byte second sector. The unbuffered retry read reports
        // whole 4 KiB pages including the page prefix, so the sector is counted as complete: its missing bytes read as zero.
        byte[] content = new byte[SECTOR_SIZE + CUT_SECTOR_SIZE];

        Arrays.fill(content, (byte) 0x11);

        byte[] archive = createCutSectorArchive(SCENARIO_DATA_POSITION, content);

        byte[] expected = new byte[content.length];

        Arrays.fill(expected, 0, SECTOR_SIZE + CUT_SECTOR_SIZE - MISSING_SECTOR_BYTES, (byte) 0x11);

        Assertions.assertArrayEquals(expected, MpqArchive.open(archive, "cut in page").readFile(SCENARIO_NAME));
    }

    @Test
    void read_file_should_drop_cut_sector_that_starts_a_page() throws IOException {
        // The same cut with the second sector starting a 4 KiB page: the retry read reports fewer bytes than the sector
        // needs, so only the first sector is read and the rest stays zero.
        byte[] content = new byte[SECTOR_SIZE + CUT_SECTOR_SIZE];

        Arrays.fill(content, (byte) 0x11);

        int tableLength = TWO_SECTOR_OFFSET_TABLE_SIZE;
        byte[] archive = createCutSectorArchive(0x2000 - SECTOR_SIZE - tableLength, content);

        byte[] expected = new byte[content.length];

        Arrays.fill(expected, 0, SECTOR_SIZE, (byte) 0x11);

        Assertions.assertArrayEquals(expected, MpqArchive.open(archive, "cut at page").readFile(SCENARIO_NAME));
    }

    @Test
    void read_file_should_zero_fill_uncompressed_file_cut_by_file_end() throws IOException {
        int presentByteCount = 6;
        byte[] archive = new byte[SCENARIO_DATA_POSITION + presentByteCount];

        putHeader(archive, 0, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 1);
        putHashTable(archive, HASH_TABLE_POSITION, createHashTable(MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, 0)));
        System.arraycopy(SCENARIO, 0, archive, SCENARIO_DATA_POSITION, presentByteCount);
        putBlockTable(archive, BLOCK_TABLE_POSITION, new int[]{SCENARIO_DATA_POSITION, SCENARIO.length, SCENARIO.length, MpqArchive.MPQ_FILE_EXISTS});

        byte[] expected = Arrays.copyOf(Arrays.copyOf(SCENARIO, presentByteCount), SCENARIO.length);

        Assertions.assertArrayEquals(expected, MpqArchive.open(archive, "cut stored").readFile(SCENARIO_NAME));
    }

    @Test
    void read_file_should_find_files_by_raw_map_string_bytes() throws IOException {
        // A Play WAV path from a Korean map: the CP949 bytes are hashed as stored, and the encryption key uses
        // the bytes after the last backslash.
        byte[] rawName = "staredit\\wav\\.wav".getBytes(StandardCharsets.ISO_8859_1);
        byte[] korean = "\uD55C\uAE00".getBytes(Charset.forName("x-windows-949"));
        byte[] name = new byte[rawName.length + korean.length];
        int prefixLength = "staredit\\wav\\".length();

        System.arraycopy(rawName, 0, name, 0, prefixLength);
        System.arraycopy(korean, 0, name, prefixLength, korean.length);
        System.arraycopy(rawName, prefixLength, name, prefixLength + korean.length, rawName.length - prefixLength);

        byte[] plainName = Arrays.copyOfRange(name, prefixLength, name.length);

        byte[] archive = new byte[DEFAULT_ARCHIVE_SIZE];

        putHeader(archive, 0, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 1);

        int[] hashTable = new int[HASH_ENTRY_COUNT * HASH_ENTRY_DWORD_COUNT];

        Arrays.fill(hashTable, EMPTY);

        int slot = MpqCryptoUtils.hashTableOffset(name) & (HASH_ENTRY_COUNT - 1);

        System.arraycopy(new int[]{MpqCryptoUtils.hashNameA(name), MpqCryptoUtils.hashNameB(name), 0, 0}, 0,
                         hashTable, slot * HASH_ENTRY_DWORD_COUNT, HASH_ENTRY_DWORD_COUNT);
        putHashTable(archive, HASH_TABLE_POSITION, hashTable);

        int stored = putSectors(archive, SCENARIO_DATA_POSITION, new byte[][]{SCENARIO}, MpqCryptoUtils.hashKey(plainName));

        putBlockTable(archive, BLOCK_TABLE_POSITION,
                      new int[]{SCENARIO_DATA_POSITION, stored, SCENARIO.length, COMPRESSED_FILE | MpqArchive.MPQ_FILE_ENCRYPTED});

        MpqArchive mpqArchive = MpqArchive.open(archive, "korean");

        Assertions.assertArrayEquals(SCENARIO, mpqArchive.readFile(name));
        // Decoded text has no byte form above 0xFF: the lookup finds nothing instead of throwing.
        Assertions.assertThrows(IOException.class, () -> mpqArchive.readFile("staredit\\wav\\\uD55C\uAE00.wav"));
        Assertions.assertNull(mpqArchive.findEntry("\uD55C"));
    }

    @Test
    void open_should_ignore_rejected_attributes_file_in_compatibility_mode() throws IOException {
        Assertions.assertThrows(IOException.class, () -> MpqArchive.open(createAttributesArchive(CRC32_AND_FILE_TIME_FLAGS, 44), "native"));

        MpqArchive compatibleArchive = MpqArchive.open(createAttributesArchive(CRC32_AND_FILE_TIME_FLAGS, 44), "compatibility", true);

        Assertions.assertArrayEquals(SCENARIO, compatibleArchive.readFile(SCENARIO_NAME));
    }

    @Test
    void open_should_check_attributes_size_against_block_table() throws IOException {
        // Two blocks with CRC32 and file times need 8 + 2 * 12 bytes; fewer whole entries and unknown flag bits with
        // more bytes are accepted, an extra entry is not.
        Assertions.assertNotNull(MpqArchive.open(createAttributesArchive(CRC32_AND_FILE_TIME_FLAGS, 32), "exact"));
        Assertions.assertNotNull(MpqArchive.open(createAttributesArchive(CRC32_AND_FILE_TIME_FLAGS, 20), "one entry"));
        Assertions.assertNotNull(MpqArchive.open(createAttributesArchive(CRC32_AND_FILE_TIME_FLAGS | UNKNOWN_ATTRIBUTE_FLAG, 60), "unknown flag"));
        Assertions.assertThrows(IOException.class, () -> MpqArchive.open(createAttributesArchive(CRC32_AND_FILE_TIME_FLAGS, 44), "extra entry"));
        Assertions.assertThrows(IOException.class, () -> MpqArchive.open(createAttributesArchive(CRC32_AND_FILE_TIME_FLAGS, 26), "partial entry"));
        Assertions.assertThrows(IOException.class, () -> MpqArchive.open(createAttributesArchive(CRC32_AND_FILE_TIME_FLAGS, 6), "no header"));
    }

    @Test
    void archive_rejects_missing_or_unaligned_header_and_oversized_file() throws IOException {
        Assertions.assertThrows(IOException.class, () -> MpqArchive.open(new byte[600], "no header"));

        byte[] macBinary = new byte[DEFAULT_ARCHIVE_SIZE];

        putHeader(macBinary, 128, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 1);

        Assertions.assertThrows(IOException.class, () -> MpqArchive.open(macBinary, "unaligned header"));

        byte[] archive = new byte[DEFAULT_ARCHIVE_SIZE];

        putHeader(archive, 0, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 1);
        putHashTable(archive, HASH_TABLE_POSITION, createHashTable(MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, 0)));
        putBlockTable(archive, BLOCK_TABLE_POSITION, new int[]{SCENARIO_DATA_POSITION, 16, 0x10000001, COMPRESSED_FILE});

        MpqArchive oversized = MpqArchive.open(archive, "oversized");

        Assertions.assertThrows(IOException.class, () -> oversized.readFile(SCENARIO_NAME));
    }

    @Test
    void get_entries_should_enumerate_unnamed_entries_without_listfile() throws IOException {
        byte[] archive = new byte[DEFAULT_ARCHIVE_SIZE];

        putHeader(archive, 0, HEADER_SIZE, HASH_TABLE_POSITION, BLOCK_TABLE_POSITION, HASH_ENTRY_COUNT, 2);
        putHashTable(archive, HASH_TABLE_POSITION, createHashTable(MpqArchiveScenario.createHashEntry(SCENARIO_NAME, 0, 1)));

        int stored = putSectors(archive, SCENARIO_DATA_POSITION, new byte[][]{SCENARIO}, 0);

        putBlockTable(archive, BLOCK_TABLE_POSITION, new int[]{0, 0, 0, 0, SCENARIO_DATA_POSITION, stored, SCENARIO.length, COMPRESSED_FILE});

        MpqArchive mpqArchive = MpqArchive.open(archive, "unnamed");

        Assertions.assertEquals(1, mpqArchive.getEntries().size());
        Assertions.assertNull(mpqArchive.getEntries().get(0).getFileName());

        MpqEntry unnamedEntry = mpqArchive.findEntry(SCENARIO_NAME);

        Assertions.assertNotNull(unnamedEntry);
        Assertions.assertEquals(SCENARIO.length, unnamedEntry.getFileSize());
    }
}
