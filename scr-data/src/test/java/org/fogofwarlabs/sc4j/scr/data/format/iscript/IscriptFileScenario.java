package org.fogofwarlabs.sc4j.scr.data.format.iscript;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Creates the contents of small {@code iscript.bin} files with one script for the iscript tests.
 * <p>
 * The file starts with the identifier table offset DWORD, holds the script header at {@link #HEADER_OFFSET} and ends with the identifier table: the
 * script entry and the terminator entry.
 */
final class IscriptFileScenario {
    /**
     * The identifier of the script.
     */
    static final int SCRIPT_ID = 7;
    static final int TABLE_TERMINATOR_ID = 65535;
    /**
     * The size of the identifier table: the script entry and the terminator entry.
     */
    static final int TABLE_SIZE = 8;
    static final int HEADER_OFFSET = 16;
    static final int HEADER_TYPE_POSITION = 20;
    /**
     * The position of the animation offset table, after the header magic and type.
     */
    static final int ANIMATION_TABLE_POSITION = 24;
    /**
     * The offset of the initial animation, the first entry of the animation offset table.
     */
    static final int INIT_BLOCK_OFFSET = 48;

    private static final int MAGIC_SCPE = 0x45504353;

    /**
     * Creates the contents of a file whose script has the header type {@code 1} and its initial animation at {@link #INIT_BLOCK_OFFSET}; the
     * animation bytes and the other animation offsets stay zero.
     *
     * @param tableOffset the offset of the identifier table, which the file ends with
     * @return the file contents, in little-endian order
     */
    static @NotNull ByteBuffer createScript(int tableOffset) {
        ByteBuffer data = ByteBuffer.allocate(tableOffset + TABLE_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        data.putInt(0, tableOffset);
        data.putInt(HEADER_OFFSET, MAGIC_SCPE);
        data.putShort(HEADER_TYPE_POSITION, (short) 1);
        data.putShort(ANIMATION_TABLE_POSITION, (short) INIT_BLOCK_OFFSET);
        data.putShort(tableOffset, (short) SCRIPT_ID);
        data.putShort(tableOffset + Short.BYTES, (short) HEADER_OFFSET);
        data.putShort(tableOffset + 2 * Short.BYTES, (short) TABLE_TERMINATOR_ID);

        return data;
    }


    /**
     * Creates a {@link IscriptFileScenario} instance.
     */
    private IscriptFileScenario() {
    }
}
