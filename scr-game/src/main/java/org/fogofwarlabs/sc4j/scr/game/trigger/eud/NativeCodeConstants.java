package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.ClassicCodeConstantRegion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Holds the read-only classic code constants of Remastered's type-7 EUD regions, read from the installed SC:R
 * 1.23.10.13515 executable.
 * <p>
 * Reads the executable's {@code .rdata} runs of {@code 8}-byte (classic address, DWORD) records. Each {@code 4}-byte type-7 base below
 * occurs there exactly once as the address of a record inside a run, and its DWORD is served as the region's content. No
 * value is embedded here. They are read from the user's executable after checking its file version and the record
 * structure. If the executable is missing, has another version or the structure does not match, loading returns
 * {@code false}. Writes replace the region's stored DWORD ({@link ClassicCodeConstantRegion}).
 */
public final class NativeCodeConstants {
    /**
     * The {@code 4}-byte type-7 bases of the native region table.
     */
    static final int @NotNull [] ADDRESSES = {
            0x0041E0D0, 0x00426918, 0x00427900, 0x00427CDC, 0x0045134C, 0x00451350, 0x0046044C, 0x004887C0,
            0x00489204, 0x00492F90, 0x004A3898, 0x004A389C, 0x004B995C, 0x004BD5EC, 0x004BD5F0, 0x004D68F4,
            0x004D98BC, 0x004F3288, 0x004F3360, 0x004FDFBC, 0x19018F38
    };

    /**
     * The type-7 regions wider than a DWORD of the native region table: {base, size} rows. With the {@code 4}-byte bases of
     * {@link #ADDRESSES} they are the {@code 64} type-7 regions of the runtime table.
     */
    static final int @NotNull [] SNAPSHOT_REGIONS = {
            0x00200000, 0x1000, 0x00201000, 0x6000, 0x004FF900, 0xC, 0x005128F8, 0xA0, 0x0051521C, 0x8, 0x005152A8, 0x54,
            0x00515300, 0x48, 0x00515348, 0x3C, 0x00515388, 0x60, 0x0051A424, 0x16, 0x0051A440, 0x17, 0x0051AAB0, 0x17,
            0x0051AAC8, 0x17, 0x0051AAE0, 0x17, 0x0051AB94, 0x19, 0x0051ABB0, 0x10, 0x0051ABC0, 0x14, 0x0051ABD4, 0x1A,
            0x0051ABF0, 0xC, 0x0051AC10, 0x19, 0x0051AC2C, 0x18, 0x0051AC48, 0x12, 0x0051AC5C, 0x17, 0x0051AC74, 0x10,
            0x0051AD18, 0x16, 0x0051BAF8, 0x100, 0x005973A0, 0x2000, 0x0059C6C0, 0x400, 0x0059CB60, 0x100, 0x00655C58, 0x1B8,
            0x00655E80, 0x1B8, 0x00657AA0, 0x1000, 0x0068C208, 0x18, 0x006CA550, 0xFC, 0x006CA64C, 0x18, 0x006CDFD4, 0x24,
            0x006CE000, 0xC0, 0x006CE320, 0x400, 0x006CE720, 0x400, 0x006CEB20, 0x1A, 0x006D6394, 0x18, 0x190452C8, 0x80,
            0x19046138, 0x100
    };

    private static final int WORD_MASK = 0xFFFF;
    private static final long DWORD_MASK = 0xFFFFFFFFL;
    private static final int RUN_LOOKAHEAD_RECORD_COUNT = 2;

    /**
     * The image base the executable must have.
     */
    private static final long IMAGE_BASE = 0x140000000L;

    /**
     * The executable's file version: major, minor, patch, build.
     */
    private static final int @NotNull [] VERSION = {1, 23, 10, 13515};

    /**
     * The DOS header magic {@code MZ}.
     */
    private static final int DOS_MAGIC = 0x5A4D;

    /**
     * The DOS header field holding the file offset of the PE header.
     */
    private static final int PE_HEADER_POINTER = 0x3C;

    /**
     * The PE signature {@code PE\0\0}.
     */
    private static final int PE_SIGNATURE = 0x00004550;

    /**
     * The PE header field holding the section count WORD.
     */
    private static final int SECTION_COUNT_FIELD = 6;

    /**
     * The PE header field holding the optional header size WORD.
     */
    private static final int OPTIONAL_HEADER_SIZE_FIELD = 20;

    /**
     * The size of the PE signature and file header, the offset of the optional header.
     */
    private static final int OPTIONAL_HEADER = 24;

    /**
     * The optional header magic of a {@code 64}-bit image.
     */
    private static final int PE32_PLUS_MAGIC = 0x20B;

    /**
     * The optional header field holding the image base QWORD of a {@code 64}-bit image.
     */
    private static final int IMAGE_BASE_FIELD = 24;

    /**
     * The size of a section table entry.
     */
    private static final int SECTION_ENTRY_SIZE = 40;

    /**
     * The size of a section name.
     */
    private static final int SECTION_NAME_SIZE = 8;

    /**
     * The section entry field holding the virtual address DWORD.
     */
    private static final int SECTION_VIRTUAL_ADDRESS = 12;

    /**
     * The section entry field holding the raw data size DWORD.
     */
    private static final int SECTION_RAW_SIZE = 16;

    /**
     * The section entry field holding the raw data file offset DWORD.
     */
    private static final int SECTION_RAW_POINTER = 20;

    /**
     * The signature that starts the fixed file information of the version resource ({@code 0xFEEF04BD}).
     */
    private static final byte @NotNull [] FIXED_FILE_INFO_SIGNATURE = {(byte) 0xBD, 0x04, (byte) 0xEF, (byte) 0xFE};

    /**
     * The fixed file information field holding the most significant file version DWORD.
     */
    private static final int FILE_VERSION_MOST = 8;

    /**
     * The fixed file information field holding the least significant file version DWORD.
     */
    private static final int FILE_VERSION_LEAST = 12;

    /**
     * The start of the virtual address range the code-constant records must lie in.
     */
    private static final long RECORD_AREA_START = 0x140AD1958L;

    /**
     * The end of the virtual address range the code-constant records must lie in.
     */
    private static final long RECORD_AREA_END = 0x140AD9740L;

    /**
     * The size of a code-constant record: the classic address and the DWORD.
     */
    private static final int RECORD_SIZE = 2 * Integer.BYTES;

    /**
     * The fewest records of a run that holds a code constant.
     */
    private static final int MINIMUM_RUN = 4;

    /**
     * The start of the classic image range a record address lies in.
     */
    private static final int CLASSIC_IMAGE_START = 0x400000;

    /**
     * The end of the classic image range a record address lies in.
     */
    private static final int CLASSIC_IMAGE_END = 0x700000;

    /**
     * The start of the classic heap range a record address lies in.
     */
    private static final int CLASSIC_HEAP_START = 0x19000000;

    /**
     * The end of the classic heap range a record address lies in.
     */
    private static final int CLASSIC_HEAP_END = 0x19100000;

    /**
     * The virtual address of the inflated size DWORD of the Storm archive records.
     */
    private static final long STORM_ARCHIVE_SIZE = 0x140DB16E4L;

    /**
     * The virtual address of the zlib stream of the Storm archive records.
     */
    private static final long STORM_ARCHIVE_DATA = 0x140DB16E8L;

    /**
     * The length of the zlib stream of the Storm archive records.
     */
    private static final int STORM_ARCHIVE_STREAM = 0x1CFFF;

    /**
     * The virtual address of the type-7 records.
     */
    private static final long SNAPSHOT_START = 0x140AD1A00L;

    /**
     * The size of the type-7 records.
     */
    private static final int SNAPSHOT_SIZE = 0x7A99;

    /**
     * The address and size bit that marks a record with more than one DWORD.
     */
    private static final int RUN_FLAG = 0x80000000;

    /**
     * Parses the code-constant records of an executable image.
     *
     * @param image the executable image
     * @return the DWORD of each {@code 4}-byte type-7 base, by base
     * @throws IllegalArgumentException if the image is not the expected executable or a record is missing or not unique
     */
    static @NotNull Map<Integer, Integer> parse(byte @NotNull [] image) {
        ByteBuffer buffer = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);

        if (buffer.getShort(0) != DOS_MAGIC) {
            throw Exceptions.newIllegalArgumentException("the executable is not a PE image");
        }

        int header = buffer.getInt(PE_HEADER_POINTER);

        if (buffer.getInt(header) != PE_SIGNATURE || buffer.getShort(header + OPTIONAL_HEADER) != PE32_PLUS_MAGIC ||
            buffer.getLong(header + OPTIONAL_HEADER + IMAGE_BASE_FIELD) != IMAGE_BASE) {
            throw Exceptions.newIllegalArgumentException("the executable is not the 64-bit image at the expected image base");
        }

        checkVersion(image, buffer);

        int sections = buffer.getShort(header + SECTION_COUNT_FIELD) & WORD_MASK;
        int table = header + OPTIONAL_HEADER + (buffer.getShort(header + OPTIONAL_HEADER_SIZE_FIELD) & WORD_MASK);
        int rawStart = -1;
        int rawSize = 0;
        long virtualStart = 0;

        for (int index = 0; index < sections; index++) {
            int entry = table + index * SECTION_ENTRY_SIZE;
            String name = new String(image, entry, SECTION_NAME_SIZE, StandardCharsets.US_ASCII).trim().replace("\0", "");

            if (name.equals(".rdata")) {
                virtualStart = IMAGE_BASE + (buffer.getInt(entry + SECTION_VIRTUAL_ADDRESS) & DWORD_MASK);
                rawSize      = buffer.getInt(entry + SECTION_RAW_SIZE);
                rawStart     = buffer.getInt(entry + SECTION_RAW_POINTER);
            }
        }

        if (rawStart < 0) {
            throw Exceptions.newIllegalArgumentException("the executable has no .rdata section");
        }

        // One pass over .rdata finds the records of every address: the first offset and whether a second one exists.
        int[] sortedAddresses = ADDRESSES.clone();

        Arrays.sort(sortedAddresses);

        int[] firstOffsets = new int[sortedAddresses.length];
        boolean[] repeated = new boolean[sortedAddresses.length];

        Arrays.fill(firstOffsets, -1);

        for (int offset = rawStart; offset + RECORD_SIZE <= rawStart + rawSize; offset++) {
            int slot = Arrays.binarySearch(sortedAddresses, buffer.getInt(offset));

            if (slot < 0 || countRunRecords(buffer, offset, rawStart, rawStart + rawSize) < MINIMUM_RUN) {
                continue;
            }

            if (firstOffsets[slot] >= 0) {
                repeated[slot] = true;
            } else {
                firstOffsets[slot] = offset;
            }
        }

        Map<Integer, Integer> result = new HashMap<>();

        for (int address : ADDRESSES) {
            int slot = Arrays.binarySearch(sortedAddresses, address);
            int found = firstOffsets[slot];

            if (repeated[slot]) {
                throw Exceptions.newIllegalArgumentException("the record of 0x{0} is not unique", String.format("%08X", address));
            }

            if (found < 0) {
                throw Exceptions.newIllegalArgumentException("no record of 0x{0}", String.format("%08X", address));
            }

            long virtualAddress = virtualStart + (found - rawStart);

            if (virtualAddress < RECORD_AREA_START || virtualAddress + RECORD_SIZE > RECORD_AREA_END) {
                throw Exceptions.newIllegalArgumentException("the record of 0x{0} lies outside the record area", String.format("%08X", address));
            }

            result.put(address, buffer.getInt(found + Integer.BYTES));
        }

        return result;
    }

    /**
     * Inflates the Storm archive records of an executable image: chunks of a tag byte, a DWORD length and the bytes, three
     * per archive (tag {@code % 10}: {@code 1} the MPQ header, {@code 2} the block table, {@code 3} the hash table).
     *
     * @param image the executable image
     * @return the inflated records
     * @throws IllegalArgumentException  if no section holds the records, their decoded size is negative or they do not inflate to that size
     * @throws IndexOutOfBoundsException if the referenced image bytes are truncated or outside the array
     */
    static byte @NotNull [] parseStormArchives(byte @NotNull [] image) {
        ByteBuffer buffer = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
        OptionalInt found = findRawOffset(buffer, STORM_ARCHIVE_SIZE, STORM_ARCHIVE_DATA + STORM_ARCHIVE_STREAM - STORM_ARCHIVE_SIZE);

        if (found.isEmpty()) {
            throw Exceptions.newIllegalArgumentException("the executable has no section with the Storm archive records");
        }

        int sizeOffset = found.getAsInt();
        int size = buffer.getInt(sizeOffset);

        if (size < 0) {
            throw Exceptions.newIllegalArgumentException("The Storm archive decoded size is negative: {0}", size);
        }

        byte[] result;

        try (Inflater inflater = new Inflater()) {
            inflater.setInput(image, sizeOffset + Integer.BYTES, STORM_ARCHIVE_STREAM);

            result = new byte[size];

            try {
                int produced = inflater.inflate(result);

                if (produced != size) {
                    throw Exceptions.newIllegalArgumentException("the Storm archive records inflate to {0} bytes, not {1}", produced, size);
                }
            } catch (DataFormatException exception) {
                throw Exceptions.newIllegalArgumentException(exception, "the Storm archive records are not a zlib stream");
            }
        }

        return result;
    }

    /**
     * Parses the type-7 records of an executable image: a record is a (classic address, DWORD) pair, or, with bit {@code 31} of
     * the address set, the address, the size and that many bytes, or, with bit {@code 31} of the size also set, the address, the
     * size, a count and that many bytes, the rest of the size zero.
     *
     * @param image the executable image
     * @return the initial bytes of each record, by base
     * @throws IllegalArgumentException if no section holds the records, a record overruns them or a region has no record
     */
    static @NotNull Map<Integer, byte[]> parseSnapshot(byte @NotNull [] image) {
        ByteBuffer buffer = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
        OptionalInt found = findRawOffset(buffer, SNAPSHOT_START, SNAPSHOT_SIZE);

        if (found.isEmpty()) {
            throw Exceptions.newIllegalArgumentException("the executable has no section with the type-7 records");
        }

        int start = found.getAsInt();
        int end = start + SNAPSHOT_SIZE;
        Map<Integer, byte[]> result = new HashMap<>();
        int position = start;

        while (position + RECORD_SIZE <= end) {
            int address = buffer.getInt(position);
            int value = buffer.getInt(position + Integer.BYTES);

            position += RECORD_SIZE;

            if ((address & RUN_FLAG) == 0) {
                result.put(address, new byte[]{(byte) value, (byte) (value >>> Byte.SIZE),
                                               (byte) (value >>> Short.SIZE), (byte) (value >>> (Integer.SIZE - Byte.SIZE))});

                continue;
            }

            int size = value & ~RUN_FLAG;
            int count = size;

            if ((value & RUN_FLAG) != 0) {
                count = buffer.getInt(position);
                position += Integer.BYTES;
            }

            if (count < 0 || count > size || position + count > end) {
                throw Exceptions.newIllegalArgumentException("the type-7 record of 0x{0} overruns the records",
                                                             String.format("%08X", address & ~RUN_FLAG));
            }

            byte[] bytes = new byte[size];

            System.arraycopy(image, position, bytes, 0, count);

            position += count;

            result.put(address & ~RUN_FLAG, bytes);
        }

        if (position != end) {
            throw Exceptions.newIllegalArgumentException("the type-7 records do not end at their bound 0x7A99");
        }

        for (int region = 0; region < SNAPSHOT_REGIONS.length; region += NativeRegionLayout.REGION_ROW_SIZE) {
            byte[] bytes = result.get(SNAPSHOT_REGIONS[region]);

            if (bytes == null || bytes.length != SNAPSHOT_REGIONS[region + 1]) {
                throw Exceptions.newIllegalArgumentException("no type-7 record of 0x{0}", String.format("%08X", SNAPSHOT_REGIONS[region]));
            }
        }

        return result;
    }

    /**
     * Finds the image file offset of a classic virtual range: the offset in the first section whose raw bytes hold the whole range.
     *
     * @param buffer  the little-endian executable image
     * @param address the virtual address of the range
     * @param length  the length of the range in bytes
     * @return the file offset of the address, or an empty {@link OptionalInt} if no section holds the range
     * @throws IndexOutOfBoundsException if the headers or the section table are truncated
     */
    private static @NotNull OptionalInt findRawOffset(@NotNull ByteBuffer buffer, long address, long length) {
        int header = buffer.getInt(PE_HEADER_POINTER);
        int sections = buffer.getShort(header + SECTION_COUNT_FIELD) & WORD_MASK;
        int table = header + OPTIONAL_HEADER + (buffer.getShort(header + OPTIONAL_HEADER_SIZE_FIELD) & WORD_MASK);

        for (int index = 0; index < sections; index++) {
            int entry = table + index * SECTION_ENTRY_SIZE;
            long virtualStart = IMAGE_BASE + (buffer.getInt(entry + SECTION_VIRTUAL_ADDRESS) & DWORD_MASK);
            int rawSize = buffer.getInt(entry + SECTION_RAW_SIZE);
            int rawStart = buffer.getInt(entry + SECTION_RAW_POINTER);

            if (address < virtualStart || address + length > virtualStart + rawSize) {
                continue;
            }

            return OptionalInt.of((int) (address - virtualStart) + rawStart);
        }

        return OptionalInt.empty();
    }

    private static int countRunRecords(@NotNull ByteBuffer buffer, int offset, int start, int end) {
        int first = offset;

        while (first - RECORD_SIZE >= start && isClassicAddress(buffer.getInt(first - RECORD_SIZE))) {
            first -= RECORD_SIZE;
        }

        int last = offset;

        while (last + RUN_LOOKAHEAD_RECORD_COUNT * RECORD_SIZE <= end && isClassicAddress(buffer.getInt(last + RECORD_SIZE))) {
            last += RECORD_SIZE;
        }

        return (last - first) / RECORD_SIZE + 1;
    }

    private static boolean isClassicAddress(int value) {
        return value >= CLASSIC_IMAGE_START && value < CLASSIC_IMAGE_END || value >= CLASSIC_HEAP_START && value < CLASSIC_HEAP_END;
    }

    private static void checkVersion(byte @NotNull [] image, @NotNull ByteBuffer buffer) {
        byte[] key = "VS_VERSION_INFO".getBytes(StandardCharsets.UTF_16LE);
        int keyOffset = findBytePattern(image, key, 0);
        int fileInformationOffset = keyOffset < 0 ? -1 : findBytePattern(image, FIXED_FILE_INFO_SIGNATURE, keyOffset);

        if (fileInformationOffset < 0) {
            throw Exceptions.newIllegalArgumentException("the executable has no version resource");
        }

        int versionMostSignificant = buffer.getInt(fileInformationOffset + FILE_VERSION_MOST);
        int versionLeastSignificant = buffer.getInt(fileInformationOffset + FILE_VERSION_LEAST);
        int major = versionMostSignificant >>> Short.SIZE;
        int minor = versionMostSignificant & WORD_MASK;
        int patch = versionLeastSignificant >>> Short.SIZE;
        int build = versionLeastSignificant & WORD_MASK;
        int[] version = {major, minor, patch, build};

        if (!Arrays.equals(version, VERSION)) {
            throw Exceptions.newIllegalArgumentException("the executable is version {0}, not 1.23.10.13515",
                                                         String.format("%d.%d.%d.%d", major, minor, patch, build));
        }
    }

    private static int findBytePattern(byte @NotNull [] data, byte @NotNull [] pattern, int from) {
        outer:
        for (int index = from; index + pattern.length <= data.length; index++) {
            for (int inner = 0; inner < pattern.length; inner++) {
                if (data[index + inner] != pattern[inner]) {
                    continue outer;
                }
            }

            return index;
        }

        return -1;
    }


    private @Nullable Map<Integer, Integer> values;

    /**
     * The decompressed Storm archive records (four archives), or {@code null} while unknown.
     */
    private byte @Nullable [] stormArchives;

    /**
     * The classic 1.16 data records of the type-7 regions, by base, or {@code null} while unknown.
     */
    private @Nullable Map<Integer, byte[]> snapshot;

    private @Nullable String reason;

    /**
     * Creates a {@link NativeCodeConstants} instance whose constants are unknown until an executable is loaded.
     */
    public NativeCodeConstants() {
        this.values        = null;
        this.stormArchives = null;
        this.snapshot      = null;
        this.reason        = "no executable was loaded";
    }

    /**
     * Tells whether the Storm archive records the emulation builds its archive list from are known: the zlib stream of the
     * executable inflated to its stored size, chunks of a tag byte, a DWORD length and the bytes, three per archive (tag
     * {@code % 10}: {@code 1} the MPQ header, {@code 2} the block table, {@code 3} the hash table).
     *
     * @return {@code true} if the Storm archive records are known, {@code false} otherwise
     */
    public boolean hasStormArchives() {
        return this.stormArchives != null;
    }

    /**
     * @return the retained Storm archive records, which may precede a failed reload
     * @throws UnsupportedOperationException if no Storm archive array is currently retained
     */
    public byte @NotNull [] getStormArchives() {
        byte[] archives = this.stormArchives;

        if (archives == null) {
            throw Exceptions.newUnsupportedOperationException("The EUD Storm archive list needs the installed executable: {0}.", this.reason);
        }

        return archives;
    }

    /**
     * Returns the initial bytes of a type-7 region. The emulation attaches a buffer to each type-7 region from the
     * type-7 records of the executable (the host of a region is found by base and size); the records are the classic 1.16
     * data at those addresses ({@link #parseSnapshot}).
     *
     * @param address the region base
     * @param size    the region size
     * @return a copy of the retained initial bytes, or {@code null} if no record has that base and size
     * @throws UnsupportedOperationException if no snapshot map is currently retained
     */
    public byte @Nullable [] getSnapshot(int address, int size) {
        Map<Integer, byte[]> current = this.snapshot;

        if (current == null) {
            throw Exceptions.newUnsupportedOperationException("EUD address 0x{0} is a type-7 region whose 1.16 data needs the installed " +
                                                              "executable: {1}.",
                                                              String.format("%08X", address),
                                                              this.reason);
        }

        byte[] bytes = current.get(address);

        return bytes == null || bytes.length != size ? null : bytes.clone();
    }

    /**
     * @return {@code true} if the type-7 records ({@link #getSnapshot}) are known, {@code false} otherwise
     */
    public boolean hasSnapshot() {
        return this.snapshot != null;
    }

    /**
     * @return {@code true} if the constants are known, {@code false} otherwise
     */
    public boolean isKnown() {
        return this.values != null;
    }

    /**
     * Returns the loaded DWORD record of a {@code 4}-byte type-7 base.
     *
     * @param address a {@code 4}-byte type-7 base
     * @return the loaded DWORD record
     * @throws UnsupportedOperationException if the constants are unknown or the base has no record
     */
    public int getValue(int address) {
        Map<Integer, Integer> current = this.values;

        if (current == null) {
            throw Exceptions.newUnsupportedOperationException("EUD address 0x{0} is a code constant whose executable data is unknown: {1}.",
                                                              String.format("%08X", address),
                                                              this.reason);
        }

        Integer value = current.get(address);

        if (value == null) {
            throw Exceptions.newUnsupportedOperationException("EUD address 0x{0} has no native code-constant record.",
                                                              String.format("%08X", address));
        }

        return value;
    }


    /**
     * Loads the constants, archive records and snapshot records from an installed executable in that order.
     * <p>
     * Clears the constants before reading. A failed load keeps completed stages and any previous archive or snapshot
     * values not yet replaced. The reason records the caught failure and may be {@code null} if it has no message.
     *
     * @param executable the {@code x86_64/StarCraft.exe} of the installation
     * @return {@code true} if all three loading stages complete, {@code false} otherwise
     */
    public boolean loadExecutable(@NotNull Path executable) {
        this.values = null;

        try {
            if (!Files.isRegularFile(executable)) {
                this.reason = "the executable " + executable + " is missing";

                return false;
            }

            byte[] image = Files.readAllBytes(executable);

            this.values        = parse(image);
            this.stormArchives = parseStormArchives(image);
            this.snapshot      = parseSnapshot(image);
            this.reason        = "";

            return true;
        } catch (IOException | IllegalArgumentException | IndexOutOfBoundsException exception) {
            this.reason = exception.getMessage();

            return false;
        }
    }

    /**
     * Copies the currently retained constants, Storm archive records, snapshot records and reason without file I/O.
     * <p>
     * Map and array references remain shared without cloning, including unknown values. The reason may be {@code null}.
     *
     * @param source the instance loaded once per session
     */
    public void copyFrom(@NotNull NativeCodeConstants source) {
        this.values        = source.values;
        this.stormArchives = source.stormArchives;
        this.snapshot      = source.snapshot;
        this.reason        = source.reason;
    }
}
