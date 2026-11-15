package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.production.ScrRequirementSystem;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * A generic host region over a Remastered global table of DWORDs or WORDs: reads and writes copy the host bytes, so an
 * EUD write changes the table the game reads.
 */
public final class ClassicHostTableRegion implements ClassicRegion {
    /**
     * The mask of the low WORD.
     */
    private static final int WORD_MASK = 0xFFFF;

    /**
     * The classic address of the flee distances.
     */
    public static final int FLEE_DISTANCES = 0x51ABFC;
    /**
     * The classic addresses of the requirement scripts, in the order of {@link ScrRequirementSystem#copyScripts()}: orders,
     * technology use, research, upgrades, production.
     */
    public static final int @NotNull [] REQUIREMENT_SCRIPTS = {0x514CF8, 0x514A48, 0x514908, 0x5145C0, 0x514178};
    /**
     * The classic address of the per-speed timeouts (one DWORD per game speed, write-ignored). Outside a multiplayer
     * session every entry is {@code 1000}; in one they come from the network provider's latency and the speed's frame
     * duration, at least {@code 1000}.
     */
    public static final int SPEED_TIMEOUTS = 0x51CE70;

    /**
     * The single-player value of every per-speed timeout.
     */
    private static final int SINGLE_PLAYER_TIMEOUT = GameSpeed.MINIMUM_TIMEOUT;
    /**
     * The sizes of the classic requirement script regions, in the order of {@link #REQUIREMENT_SCRIPTS}.
     */
    private static final int @NotNull [] REQUIREMENT_SCRIPT_SIZES = {0x524, 0x2B0, 0x13E, 0x346, 0x442};


    /**
     * Creates a {@link ClassicHostTableRegion} instance over a write-ignored live DWORD table.
     *
     * @param base  the classic base
     * @param table the live host table, one DWORD per element
     * @return the created {@link ClassicHostTableRegion} instance
     */
    public static @NotNull ClassicHostTableRegion createWriteIgnoredRegion(int base, int @NotNull [] table) {
        return new ClassicHostTableRegion(base, table, Integer.BYTES, table.length * Integer.BYTES, true);
    }

    /**
     * Creates a {@link ClassicHostTableRegion} instance for the write-ignored per-speed timeouts {@link #SPEED_TIMEOUTS} of a game outside a
     * multiplayer session.
     *
     * @return the created {@link ClassicHostTableRegion} instance, every entry {@code 1000}
     */
    public static @NotNull ClassicHostTableRegion createSinglePlayerSpeedTimeoutRegion() {
        int[] timeouts = new int[GameSpeed.SPEED_COUNT];

        Arrays.fill(timeouts, SINGLE_PLAYER_TIMEOUT);

        return new ClassicHostTableRegion(SPEED_TIMEOUTS, timeouts, Integer.BYTES, GameSpeed.SPEED_COUNT * Integer.BYTES, true);
    }

    /**
     * Creates {@link ClassicHostTableRegion} instances for the WORD regions of the game's requirement scripts.
     *
     * @param scripts the game's requirement scripts ({@link ScrRequirementSystem#getScripts()})
     * @return the five WORD regions of the scripts
     * @throws IllegalStateException if a script does not have the classic region's size
     */
    public static @NotNull ClassicHostTableRegion @NotNull [] createRequirementScriptRegions(int @NotNull [] @NotNull [] scripts) {
        ClassicHostTableRegion[] regions = new ClassicHostTableRegion[REQUIREMENT_SCRIPTS.length];

        for (int index = 0; index < regions.length; index++) {
            if (scripts[index].length * Short.BYTES != REQUIREMENT_SCRIPT_SIZES[index]) {
                throw Exceptions.newIllegalStateException("The requirement script at {0} has {1} words, the classic region {2} bytes.",
                                                          String.format("0x%08X", REQUIREMENT_SCRIPTS[index]),
                                                          scripts[index].length,
                                                          String.format("0x%X", REQUIREMENT_SCRIPT_SIZES[index]));
            }

            regions[index] = new ClassicHostTableRegion(REQUIREMENT_SCRIPTS[index],
                                                        scripts[index],
                                                        Short.BYTES,
                                                        REQUIREMENT_SCRIPT_SIZES[index],
                                                        false);
        }

        return regions;
    }


    private final int base;
    private final int @NotNull [] table;
    private final int elementSize;
    private final int size;
    private final boolean writeIgnored;


    /**
     * Creates a {@link ClassicHostTableRegion} instance over a live host table of DWORDs whose writes change the table.
     *
     * @param base  the classic base
     * @param table the live host table, one DWORD per element
     */
    public ClassicHostTableRegion(int base, int @NotNull [] table) {
        this(base, table, Integer.BYTES, table.length * Integer.BYTES, false);
    }

    private ClassicHostTableRegion(int base, int @NotNull [] table, int elementSize, int size, boolean writeIgnored) {
        this.base         = base;
        this.table        = table;
        this.elementSize  = elementSize;
        this.size         = size;
        this.writeIgnored = writeIgnored;
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
        return this.size;
    }

    /**
     * Tells whether the address space accepts writes without calling the region or changing its state.
     *
     * @return {@code true} if writes are ignored, {@code false} otherwise
     */
    @Override
    public boolean isWriteIgnored() {
        return this.writeIgnored;
    }


    /**
     * Reads the unsigned byte at an offset.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @return the unsigned byte at the offset
     */
    @Override
    public int readByte(int offset) {
        return this.table[offset / this.elementSize] >>> (offset % this.elementSize) * Byte.SIZE & BYTE_MASK;
    }

    /**
     * Stores one byte, changing the domain state that holds it.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @param value  the byte value in the low eight bits
     */
    @Override
    public void writeByte(int offset, int value) {
        int shift = (offset % this.elementSize) * Byte.SIZE;
        int index = offset / this.elementSize;
        int merged = LittleEndianUtils.replaceByte(this.table[index], shift, value);

        this.table[index] = this.elementSize == Integer.BYTES ? merged : merged & WORD_MASK;
    }

    /**
     * Reads an aligned little-endian DWORD; regions whose domain values are DWORDs override it to read them whole.
     *
     * @param offset the byte offset of the DWORD, with four bytes inside the region
     * @return the DWORD
     */
    @Override
    public int readDword(int offset) {
        return this.elementSize == Integer.BYTES && (offset & DWORD_OFFSET_MASK) == 0
               ? this.table[offset / Integer.BYTES]
               : ClassicRegion.super.readDword(offset);
    }

    /**
     * Stores a little-endian DWORD; regions whose domain values are DWORDs override it to store them whole.
     *
     * @param offset the byte offset of the DWORD, with four bytes inside the region
     * @param value  the DWORD
     */
    @Override
    public void writeDword(int offset, int value) {
        if (this.elementSize == Integer.BYTES && (offset & DWORD_OFFSET_MASK) == 0) {
            this.table[offset / Integer.BYTES] = value;
        } else {
            ClassicRegion.super.writeDword(offset, value);
        }
    }
}
