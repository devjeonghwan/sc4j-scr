package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Preserves the replay {@code GCFG} version block and {@code BFIX} flags. Missing extension sections supply zero values.
 */
public final class ReplayGameVersion {
    /**
     * The size of the {@code GCFG} version block.
     */
    static final int VERSION_BLOCK_SIZE = 25;
    /**
     * The size of the {@code BFIX} flags QWORD.
     */
    static final int FLAGS_SIZE = Long.BYTES;

    private static final int BYTE_MASK = 0xFF;
    private static final int BUILD_OFFSET = 4;
    private static final int KIND_OFFSET = 0x10;

    private static final @NotNull ReplayGameVersion ABSENT = new ReplayGameVersion(new byte[VERSION_BLOCK_SIZE], 0L, false, false);

    /**
     * Returns the shared zero values of a replay without {@code GCFG} and {@code BFIX} sections.
     *
     * @return the zero values of a replay without {@code GCFG} and {@code BFIX} sections
     */
    public static @NotNull ReplayGameVersion absent() {
        return ABSENT;
    }

    /**
     * Creates a {@link ReplayGameVersion} instance with explicit {@code GCFG} and {@code BFIX} values.
     *
     * @param build the build number DWORD at block offset {@code 4}
     * @param kind  the kind byte at block offset {@code 0x10}
     * @param flags the {@code BFIX} flags QWORD
     * @return the created {@link ReplayGameVersion} instance with both sections present
     */
    public static @NotNull ReplayGameVersion of(int build, int kind, long flags) {
        byte[] block = new byte[VERSION_BLOCK_SIZE];

        LittleEndianUtils.writeInt32(block, BUILD_OFFSET, build);
        block[KIND_OFFSET] = (byte) kind;

        return new ReplayGameVersion(block, flags, true, true);
    }

    /**
     * Creates a {@link ReplayGameVersion} instance from decoded extension sections.
     *
     * @param block the decoded {@code GCFG} payload, or {@code null} if the section is absent
     * @param flags the decoded {@code BFIX} payload, or {@code null} if the section is absent
     * @return the created {@link ReplayGameVersion} instance
     */
    static @NotNull ReplayGameVersion fromSections(byte @Nullable [] block, byte @Nullable [] flags) {
        long flagsValue = flags == null ? 0 : LittleEndianUtils.readUnsigned(flags, 0, FLAGS_SIZE);

        return new ReplayGameVersion(block == null ? new byte[VERSION_BLOCK_SIZE] : block, flagsValue, block != null, flags != null);
    }


    private final byte @NotNull [] block;
    private final long flags;

    private final boolean versionSection;
    private final boolean flagsSection;

    private ReplayGameVersion(byte @NotNull [] block, long flags, boolean versionSection, boolean flagsSection) {
        if (block.length != VERSION_BLOCK_SIZE) {
            throw Exceptions.newIllegalArgumentException("The replay version block has 25 bytes.");
        }

        this.block = block.clone();
        this.flags = flags;

        this.versionSection = versionSection;
        this.flagsSection   = flagsSection;
    }

    /**
     * @return the build number DWORD at block offset {@code 4}
     */
    public int getBuild() {
        return LittleEndianUtils.readInt32(this.block, BUILD_OFFSET);
    }

    /**
     * @return the kind byte at block offset {@code 0x10}
     */
    public int getKind() {
        return this.block[KIND_OFFSET] & BYTE_MASK;
    }

    /**
     * @return the {@code BFIX} flags QWORD
     */
    public long getFlags() {
        return this.flags;
    }

    /**
     * @return a copy of the {@code 25}-byte version block
     */
    public byte @NotNull [] getBlock() {
        return this.block.clone();
    }

    /**
     * @return {@code true} if the replay carries a {@code GCFG} section, {@code false} otherwise
     */
    public boolean hasVersionSection() {
        return this.versionSection;
    }

    /**
     * @return {@code true} if the replay carries a {@code BFIX} section, {@code false} otherwise
     */
    public boolean hasFlagsSection() {
        return this.flagsSection;
    }
}
