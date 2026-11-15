package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.game.presentation.HostButtonSets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the classic button set table and its button array regions from executable snapshots.
 * Writes mark the table for conversion to {@link HostButtonSets}. Commit rebuilds the host entries through the address
 * space, clears entries with untranslatable functions and restores defaults if a required read or allocation fails.
 */
public final class ClassicButtonSets {
    /**
     * The mask of the low WORD.
     */
    private static final int WORD_MASK = 0xFFFF;

    /**
     * The classic address of the button set table.
     */
    public static final int TABLE_BASE = 0x5187E8;

    /**
     * The size of a classic table entry: the count DWORD, the button array address and the connected set WORD.
     */
    private static final int ENTRY_SIZE = 12;

    /**
     * The number of DWORDs of a classic table entry.
     */
    private static final int ENTRY_DWORDS = ENTRY_SIZE / Integer.BYTES;

    /**
     * The entry DWORD holding the button array address.
     */
    private static final int ENTRY_ADDRESS = 1;

    /**
     * The entry DWORD holding the connected set WORD.
     */
    private static final int ENTRY_CONNECTED = 2;

    /**
     * The connected set of an entry without a set.
     */
    private static final int NO_SET = 0xFFFF;

    /**
     * The size of a classic button.
     */
    private static final int CLASSIC_BUTTON_SIZE = 20;

    /**
     * The button DWORD holding the position and icon WORDs.
     */
    private static final int BUTTON_POSITION = 0;

    /**
     * The button DWORD holding the condition function address.
     */
    private static final int BUTTON_CONDITION = 1;

    /**
     * The button DWORD holding the action function address.
     */
    private static final int BUTTON_ACTION = 2;

    /**
     * The button DWORD holding the condition value and action value WORDs.
     */
    private static final int BUTTON_VALUES = 3;

    /**
     * The button DWORD holding the enabled and disabled string WORDs.
     */
    private static final int BUTTON_STRINGS = 4;

    /**
     * The largest array size Java allocates, the limit of a converted button array.
     */
    private static final int MAXIMUM_ARRAY_BYTES = Integer.MAX_VALUE - 8;

    /**
     * The number of values of one row of {@link #FUNCTIONS}.
     */
    private static final int FUNCTION_ROW_SIZE = 2;

    /**
     * The classic button arrays: {base, size} rows.
     */
    private static final int @NotNull [] ARRAYS = {
            0x00515BE8, 0x3C, 0x00515C24, 0x3C, 0x00515C60, 0x14, 0x00515C74, 0x14, 0x00515C88, 0x14, 0x00515C9C, 0x28,
            0x00515CC4, 0x14, 0x00515CD8, 0x28, 0x00515D00, 0x14, 0x00515D14, 0x3C, 0x00515D50, 0x14, 0x00515D68, 0x64,
            0x00515DD0, 0x64, 0x00515E38, 0x64, 0x00515EA0, 0x8C, 0x00515F2C, 0x28, 0x00515F58, 0xB4, 0x0051600C, 0x14,
            0x00516020, 0xB4, 0x005160D8, 0x8C, 0x00516168, 0x78, 0x005161E0, 0x8C, 0x00516270, 0xA0, 0x00516310, 0x8C,
            0x005163A0, 0x8C, 0x00516430, 0xB4, 0x005164E8, 0xB4, 0x005165A0, 0x8C, 0x00516630, 0xC8, 0x005166F8, 0xB4,
            0x005167B0, 0x64, 0x00516818, 0xB4, 0x005168D0, 0xA0, 0x00516970, 0x14, 0x00516988, 0x50, 0x005169D8, 0x3C,
            0x00516A14, 0x3C, 0x00516A50, 0x28, 0x00516A78, 0x64, 0x00516AE0, 0x64, 0x00516B48, 0x64, 0x00516BB0, 0x50,
            0x00516C00, 0x3C, 0x00516C3C, 0x3C, 0x00516C78, 0x8C, 0x00516D08, 0xB4, 0x00516DC0, 0xB4, 0x00516E78, 0xA0,
            0x00516F18, 0x8C, 0x00516FA8, 0x8C, 0x00517038, 0x8C, 0x005170C8, 0x8C, 0x00517158, 0x8C, 0x005171E8, 0x78,
            0x00517260, 0x8C, 0x005172F0, 0x64, 0x00517358, 0x78, 0x005173D0, 0x78, 0x00517448, 0x28, 0x00517470, 0x50,
            0x005174C0, 0xA0, 0x00517560, 0x50, 0x005175B0, 0x8C, 0x00517640, 0x64, 0x005176A8, 0x50, 0x005176F8, 0x14,
            0x0051770C, 0x3C, 0x00517748, 0x8C, 0x005177D8, 0x78, 0x00517850, 0x8C, 0x005178E0, 0xB4, 0x00517998, 0xB4,
            0x00517A50, 0x64, 0x00517AB8, 0xB4, 0x00517B70, 0xA0, 0x00517C10, 0x78, 0x00517C88, 0x8C, 0x00517D18, 0x8C,
            0x00517DA8, 0xA0, 0x00517E48, 0x78, 0x00517EC0, 0x78, 0x00517F38, 0xC8, 0x00518000, 0x14, 0x00518014, 0x28,
            0x0051803C, 0x28, 0x00518068, 0xDC, 0x00518148, 0xF0, 0x00518238, 0xF0, 0x00518328, 0x104, 0x00518430, 0x50,
            0x00518480, 0x8C, 0x00518510, 0x78, 0x00518588, 0x50, 0x005185D8, 0x64, 0x00518640, 0x8C, 0x005186D0, 0x78,
            0x00518748, 0x8C, 0x005187D4, 0x14
    };

    /**
     * The emulation's button function records: {classic 1.16 function address, Remastered function address} rows.
     */
    private static final long @NotNull [] FUNCTIONS = {
            0x00423180, 0x14014ED40L, 0x004231D0, 0x1403A9AA0L, 0x004231F0, 0x1403AA110L, 0x00423210, 0x1403AA100L,
            0x00423230, 0x1403A9A30L, 0x00423270, 0x1403A9BC0L, 0x00423290, 0x1403A9BB0L, 0x004232B0, 0x1403A9A60L,
            0x004232D0, 0x1403A9A70L, 0x004232F0, 0x1403A9A90L, 0x00423310, 0x1403AA430L, 0x00423330, 0x1403A9A80L,
            0x00423350, 0x1403A9EA0L, 0x00423370, 0x1403A9C30L, 0x00423390, 0x1403A9A50L, 0x004233B0, 0x1403AA3E0L,
            0x004233D0, 0x1403A9AD0L, 0x004233F0, 0x1403AA0E0L, 0x00423410, 0x1403A9C50L, 0x00423430, 0x1403A9C40L,
            0x00423450, 0x1403AA010L, 0x00423470, 0x1403AA120L, 0x00423490, 0x1403AA0F0L, 0x004234B0, 0x1403A9A40L,
            0x004234D0, 0x1403AA020L, 0x00423730, 0x1403A9AE0L, 0x00423760, 0x1403A9E70L, 0x00423790, 0x1403A9C80L,
            0x00423860, 0x1403AA440L, 0x00423930, 0x1403A9EB0L, 0x004239E0, 0x1403A9C20L, 0x00423A40, 0x1403A9CD0L,
            0x00423AA0, 0x1403A9E50L, 0x00423B00, 0x1403AA3F0L, 0x00423B40, 0x1403A9BD0L, 0x00423B70, 0x1403A9C10L,
            0x00423BD0, 0x1403A9E60L, 0x00423C30, 0x1403A9C60L, 0x00423C40, 0x1403A9CE0L, 0x00423C50, 0x1403AA480L,
            0x00423D10, 0x1403A9D60L, 0x00423DD0, 0x1403A9DE0L, 0x00423E90, 0x1403A9DB0L, 0x00423EB0, 0x1403A9D10L,
            0x00423F70, 0x1403AA130L, 0x00424140, 0x1403A9D00L, 0x004241A0, 0x1403A9E40L, 0x00424200, 0x1403A9AB0L,
            0x00424260, 0x1403A9AC0L, 0x004242C0, 0x1403AA3D0L, 0x00424320, 0x1403A9A20L, 0x00424380, 0x1403A99C0L,
            0x00424440, 0x1403A9CC0L, 0x004244A0, 0x1403A9E30L, 0x004282D0, 0x140183EB0L, 0x00428310, 0x1403E5720L,
            0x00428340, 0x1403E5260L, 0x00428360, 0x1403E51E0L, 0x004283C0, 0x1403E4FD0L, 0x004283F0, 0x1403E4E00L,
            0x00428420, 0x1403E4DE0L, 0x00428440, 0x1403E4DC0L, 0x00428480, 0x1403E4850L, 0x004284B0, 0x1403E47F0L,
            0x004284E0, 0x1403E47B0L, 0x00428500, 0x1403E46D0L, 0x00428530, 0x1403E46B0L, 0x00428560, 0x1403E5820L,
            0x004285A0, 0x1403E5780L, 0x004285E0, 0x1403E5750L, 0x00428610, 0x1403E5750L, 0x00428640, 0x1403E5750L,
            0x00428670, 0x1403E5750L, 0x004286A0, 0x1403E5460L, 0x004286E0, 0x1403E53E0L, 0x00428730, 0x1403E5150L,
            0x00428780, 0x1403E50C0L, 0x004287D0, 0x1403E4E30L, 0x00428810, 0x1403E4D90L, 0x00428860, 0x1403E4D40L,
            0x004288E0, 0x1403E47E0L, 0x00428900, 0x1403E47D0L, 0x00428920, 0x1403E46C0L, 0x00428960, 0x1403E5A50L,
            0x00428990, 0x1403E59B0L, 0x00428A10, 0x1403E58C0L, 0x00428AD0, 0x1403E5650L, 0x00428B80, 0x1403E5580L,
            0x00428C30, 0x1403E5340L, 0x00428CB0, 0x1403E5280L, 0x00428D40, 0x1403E4580L, 0x00428DA0, 0x1403E4520L,
            0x00428E00, 0x1403E4480L, 0x00428E60, 0x1403E4400L, 0x00428EA0, 0x1403E4910L, 0x00428ED0, 0x1403E4640L,
            0x00428F30, 0x1403E45D0L, 0x00428FA0, 0x1403E5020L, 0x00428FF0, 0x1403E48B0L, 0x00429070, 0x1403E4F20L,
            0x004290F0, 0x1403E4E80L, 0x00429170, 0x1403E4CE0L, 0x004291C0, 0x1403E4C70L, 0x00429210, 0x1403E4BE0L,
            0x004292C0, 0x1403E4B50L, 0x00429370, 0x1403E4AB0L, 0x004293E0, 0x1403E4A10L, 0x00429450, 0x1403E4A00L,
            0x00429470, 0x1403E4980L, 0x004294E0, 0x1403E4970L, 0x00429500, 0x1403E4960L, 0x00429520, 0x1403E4730L,
            0x00429640, 0x1403E5540L, 0x00429680, 0x1403E54E0L, 0x004296B0, 0x1403E5500L, 0x004296F0, 0x1403E54E0L,
            0x00429720, 0x1403E4FC0L, 0x00429740, 0x1403E4740L, 0x00429770, 0x140304980L, 0x00429780, 0x1403E5A70L,
            0x00459AF0, 0x1403EDE60L, 0x004DEAC0, 0x1403BAE40L, 0x004DEAF0, 0x1403BAD10L, 0x004DEB40, 0x1403BAC60L,
            0x004DF260, 0x1403BACB0L, 0x004DF2C0, 0x1403BAD60L, 0x004DF300, 0x1403BAC00L
    };

    private static final @NotNull Map<Integer, Long> FUNCTION_MAP = new HashMap<>();

    /**
     * The table region: its 1.16 bytes, a write marks it modified.
     */
    private static final class Table implements ClassicRegion {
        private final byte @NotNull [] bytes;

        private boolean modified;

        Table(byte @NotNull [] bytes) {
            this.bytes = bytes;

            this.modified = false;
        }

        /**
         * @return the classic base address
         */
        @Override
        public int getBase() {
            return TABLE_BASE;
        }

        /**
         * @return the size in bytes
         */
        @Override
        public int getSize() {
            return this.bytes.length;
        }


        /**
         * Reads the unsigned byte at an offset.
         *
         * @param offset the byte offset, below {@link #getSize()}
         * @return the unsigned byte at the offset
         */
        @Override
        public int readByte(int offset) {
            return this.bytes[offset] & BYTE_MASK;
        }

        /**
         * Stores one byte, changing the domain state that holds it.
         *
         * @param offset the byte offset, below {@link #getSize()}
         * @param value  the byte value in the low eight bits
         */
        @Override
        public void writeByte(int offset, int value) {
            this.bytes[offset] = (byte) value;
            this.modified      = true;
        }
    }

    /**
     * A button array region: its 1.16 bytes, a write marks the table modified.
     */
    private final class ButtonArray implements ClassicRegion {
        private final int base;

        private final byte @NotNull [] bytes;

        ButtonArray(int base, byte @NotNull [] bytes) {
            this.base  = base;
            this.bytes = bytes;
        }

        /**
         * @return the classic base address
         */
        @Override
        public int getBase() {
            return this.base;
        }

        /**
         * @return the size in bytes
         */
        @Override
        public int getSize() {
            return this.bytes.length;
        }


        /**
         * Reads the unsigned byte at an offset.
         *
         * @param offset the byte offset, below {@link #getSize()}
         * @return the unsigned byte at the offset
         */
        @Override
        public int readByte(int offset) {
            return this.bytes[offset] & BYTE_MASK;
        }

        /**
         * Stores one byte, changing the domain state that holds it.
         *
         * @param offset the byte offset, below {@link #getSize()}
         * @param value  the byte value in the low eight bits
         */
        @Override
        public void writeByte(int offset, int value) {
            this.bytes[offset]                    = (byte) value;
            ClassicButtonSets.this.table.modified = true;
        }
    }

    static {
        for (int index = 0; index < FUNCTIONS.length; index += FUNCTION_ROW_SIZE) {
            FUNCTION_MAP.put((int) FUNCTIONS[index], FUNCTIONS[index + 1]);
        }
    }

    /**
     * Creates a {@link ClassicButtonSets} instance with the table and button array regions from their 1.16 records.
     *
     * @param constants the loaded code constants
     * @param host      the game's button set table the commit rebuilds
     * @return the created {@link ClassicButtonSets} instance, or {@code null} when a record is missing
     * @throws UnsupportedOperationException if the executable was not loaded
     */
    public static @Nullable ClassicButtonSets create(@NotNull NativeCodeConstants constants, @NotNull HostButtonSets host) {
        byte[] tableBytes = constants.getSnapshot(TABLE_BASE, HostButtonSets.SET_COUNT * ENTRY_SIZE);

        if (tableBytes == null) {
            return null;
        }

        ClassicButtonSets sets = new ClassicButtonSets(host, tableBytes);

        for (int index = 0; index < ARRAYS.length; index += NativeRegionLayout.REGION_ROW_SIZE) {
            byte[] bytes = constants.getSnapshot(ARRAYS[index], ARRAYS[index + 1]);

            if (bytes == null) {
                return null;
            }

            sets.regions.add(sets.new ButtonArray(ARRAYS[index], bytes));
        }

        return sets;
    }

    /**
     * Converts the 20-byte classic buttons to host buttons.
     *
     * @param words the classic buttons as DWORDs
     * @param count the number of buttons
     * @return the buttons, or {@code null} when a function has no record
     */
    private static HostButtonSets.@NotNull Button @Nullable [] convert(int @NotNull [] words, int count) {
        HostButtonSets.Button[] converted = new HostButtonSets.Button[count];

        for (int index = 0; index < count; index++) {
            int base = index * CLASSIC_BUTTON_SIZE / Integer.BYTES;
            long condition = translate(words[base + BUTTON_CONDITION]);
            long action = translate(words[base + BUTTON_ACTION]);

            if (condition < 0 || action < 0) {
                return null;
            }

            converted[index] = new HostButtonSets.Button(words[base + BUTTON_POSITION],
                                                         words[base + BUTTON_POSITION] >>> Short.SIZE,
                                                         condition, words[base + BUTTON_VALUES],
                                                         action, words[base + BUTTON_VALUES] >>> Short.SIZE,
                                                         words[base + BUTTON_STRINGS], words[base + BUTTON_STRINGS] >>> Short.SIZE);
        }

        return converted;
    }

    /**
     * Translates a classic function address through the function records.
     *
     * @param classic the classic function address
     * @return the Remastered function of a classic function address, {@code 0} for {@code 0}, {@code -1} without a record
     */
    private static long translate(int classic) {
        if (classic == 0) {
            return 0;
        }

        Long function = FUNCTION_MAP.get(classic);

        return function == null ? -1 : function;
    }

    /**
     * Reads DWORDs through the emulation ({@link ClassicAddressSpace#readQuietly}): an unaligned DWORD combines the two
     * aligned DWORDs it spans, and every read must succeed.
     *
     * @param space       the address space
     * @param address     the classic address of the first DWORD
     * @param destination receives the DWORDs
     * @return {@code true} if every read succeeded, {@code false} otherwise
     */
    private static boolean readDwords(@NotNull ClassicAddressSpace space, int address, int @NotNull [] destination) {
        int[] word = new int[1];

        for (int index = 0; index < destination.length; index++) {
            if (!space.readQuietly(address + index * Integer.BYTES, word)) {
                return false;
            }

            destination[index] = word[0];
        }

        return true;
    }


    private final @NotNull HostButtonSets host;

    private final @NotNull Table table;

    private final @NotNull List<ClassicRegion> regions;

    private ClassicButtonSets(@NotNull HostButtonSets host, byte @NotNull [] tableBytes) {
        this.host    = host;
        this.table   = new Table(tableBytes);
        this.regions = new ArrayList<>();

        this.regions.add(this.table);
    }

    /**
     * @return the table region followed by the button array regions
     */
    public @NotNull List<ClassicRegion> getRegions() {
        return this.regions;
    }


    /**
     * Commits a written table; does nothing without a write.
     *
     * @param space the address space the commit reads through
     */
    public void commit(@NotNull ClassicAddressSpace space) {
        if (!this.table.modified) {
            return;
        }

        this.table.modified = false;

        int[] classic = new int[HostButtonSets.SET_COUNT * ENTRY_SIZE / Integer.BYTES];

        if (!readDwords(space, TABLE_BASE, classic)) {
            this.host.restoreDefaults();

            return;
        }

        int[] counts = new int[HostButtonSets.SET_COUNT];
        int[] connected = new int[HostButtonSets.SET_COUNT];
        HostButtonSets.Button[][] arrays = new HostButtonSets.Button[HostButtonSets.SET_COUNT][];

        for (int set = 0; set < HostButtonSets.SET_COUNT; set++) {
            int count = classic[set * ENTRY_DWORDS];
            int address = classic[set * ENTRY_DWORDS + ENTRY_ADDRESS];

            counts[set]    = count & WORD_MASK;
            connected[set] = classic[set * ENTRY_DWORDS + ENTRY_CONNECTED] & WORD_MASK;

            if (address == 0 || count == 0) {
                continue;
            }

            long bytes = Integer.toUnsignedLong(count) * CLASSIC_BUTTON_SIZE;

            // Restore defaults when the converted button array exceeds the allocation limit.
            if (bytes > MAXIMUM_ARRAY_BYTES) {
                this.host.restoreDefaults();

                return;
            }

            int[] buttons = new int[(int) bytes / Integer.BYTES];

            if (!readDwords(space, address, buttons)) {
                this.host.restoreDefaults();

                return;
            }

            HostButtonSets.Button[] converted = convert(buttons, count);

            if (converted == null) {
                counts[set]    = 0;
                connected[set] = NO_SET;

                continue;
            }

            arrays[set] = converted;
        }

        for (int set = 0; set < HostButtonSets.SET_COUNT; set++) {
            this.host.replace(set, counts[set], arrays[set], connected[set]);
        }
    }
}
