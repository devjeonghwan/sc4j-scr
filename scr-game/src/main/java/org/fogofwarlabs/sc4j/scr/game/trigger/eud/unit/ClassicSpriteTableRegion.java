package org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit;

import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.jetbrains.annotations.NotNull;

/**
 * Serves the classic sprite table through pool links, identity fields and per-entry storage.
 * Invalid link, owner and sprite-id writes remain in shadows until a supported replacement clears their flags.
 * Identity writes store the selection index first, then any supported owner and sprite id. Index-word writes clear
 * the reserved word and dimensions. Other DWORDs retain their own storage without changing the sprite.
 */
public final class ClassicSpriteTableRegion implements ClassicRegion {
    /**
     * The mask that reads a DWORD as an unsigned value.
     */
    private static final long DWORD_MASK = 0xFFFFFFFFL;

    /**
     * The classic address of the sprite table.
     */
    public static final int BASE = 0x629D98;
    /**
     * The classic entry size.
     */
    public static final int ENTRY_SIZE = 36;
    /**
     * The number of classic entries.
     */
    public static final int ENTRY_COUNT = 2500;

    private static final int PREVIOUS_LINK = 0x00;
    private static final int NEXT_LINK = 0x04;
    private static final int IDENTITY = 0x08;
    private static final int INDEX_WORD = 0x10;
    private static final int LINK_COUNT = 2;
    private static final int WORD_MASK = 0xFFFF;
    private static final int BYTE_MASK = 0xFF;
    private static final int THIRD_BYTE_SHIFT = 16;
    private static final int FOURTH_BYTE_SHIFT = 24;
    /**
     * The exclusive limit of the sprite ids a write stores; a larger id stays in its shadow.
     */
    private static final int STORED_ID_LIMIT = 0xE4;
    private static final int DWORDS_PER_ENTRY = ENTRY_SIZE / Integer.BYTES;


    /**
     * Finds the sprite whose entry starts at a classic address: only the exact start of an entry names one.
     *
     * @param address the classic address
     * @return the sprite index, or {@code -1}
     */
    static int findSpriteIndex(int address) {
        long relative = (address & DWORD_MASK) - BASE;

        if (relative < 0 || relative % ENTRY_SIZE != 0 || relative / ENTRY_SIZE >= ENTRY_COUNT) {
            return -1;
        }

        return (int) (relative / ENTRY_SIZE);
    }


    private final @NotNull SpritePool pool;

    private final int @NotNull [] linkShadows;
    private final boolean @NotNull [] linkShadowSet;
    private final int @NotNull [] storage;
    private final int @NotNull [] ownerShadows;
    private final boolean @NotNull [] ownerShadowSet;
    private final int @NotNull [] idShadows;
    private final boolean @NotNull [] idShadowSet;


    /**
     * Creates a {@link ClassicSpriteTableRegion} instance.
     *
     * @param pool the sprite pool of {@link #ENTRY_COUNT} sprites
     */
    public ClassicSpriteTableRegion(@NotNull SpritePool pool) {
        this.pool = pool;

        this.linkShadows    = new int[ENTRY_COUNT * LINK_COUNT];
        this.linkShadowSet  = new boolean[ENTRY_COUNT * LINK_COUNT];
        this.storage        = new int[ENTRY_COUNT * DWORDS_PER_ENTRY];
        this.ownerShadows   = new int[ENTRY_COUNT];
        this.ownerShadowSet = new boolean[ENTRY_COUNT];
        this.idShadows      = new int[ENTRY_COUNT];
        this.idShadowSet    = new boolean[ENTRY_COUNT];
    }


    /**
     * @return the classic base address
     */
    @Override
    public int getBase() {
        return BASE;
    }

    /**
     * @return the size in bytes
     */
    @Override
    public int getSize() {
        return ENTRY_COUNT * ENTRY_SIZE;
    }


    /**
     * Reads the unsigned byte at an offset.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @return the unsigned byte at the offset
     */
    @Override
    public int readByte(int offset) {
        return this.readByteFromDword(offset);
    }

    /**
     * Stores one byte, changing the domain state that holds it.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @param value  the byte value in the low eight bits
     */
    @Override
    public void writeByte(int offset, int value) {
        this.writeByteToDword(offset, value);
    }

    /**
     * Reads an aligned little-endian DWORD; regions whose domain values are DWORDs override it to read them whole.
     *
     * @param offset the byte offset of the DWORD, with four bytes inside the region
     * @return the DWORD
     */
    @Override
    public int readDword(int offset) {
        if ((offset & DWORD_OFFSET_MASK) != 0) {
            return ClassicRegion.super.readDword(offset);
        }

        int entry = offset / ENTRY_SIZE;
        int field = offset % ENTRY_SIZE;

        switch (field) {
            case PREVIOUS_LINK, NEXT_LINK -> {
                int shadow = entry * LINK_COUNT + field / Integer.BYTES;

                if (this.linkShadowSet[shadow]) {
                    return this.linkShadows[shadow];
                }

                int spriteIndex = this.pool.getLink(entry, field == NEXT_LINK);

                return spriteIndex < 0 || spriteIndex >= this.pool.getNativeCapacity() ? 0 : BASE + spriteIndex * ENTRY_SIZE;
            }
            case IDENTITY -> {
                Sprite sprite = this.pool.getSprite(entry);
                int id = this.idShadowSet[entry] ? this.idShadows[entry] : sprite.getSpriteTypeId();
                int owner = this.ownerShadowSet[entry] ? this.ownerShadows[entry] : sprite.getOwner();

                return id & WORD_MASK | (owner & BYTE_MASK) << THIRD_BYTE_SHIFT | (sprite.getSelectionIndex() & BYTE_MASK) << FOURTH_BYTE_SHIFT;
            }
            case INDEX_WORD -> {
                Sprite sprite = this.pool.getSprite(entry);

                return sprite.getReservedWord() & WORD_MASK | (sprite.getWidth() & BYTE_MASK) << THIRD_BYTE_SHIFT |
                       (sprite.getHeight() & BYTE_MASK) << FOURTH_BYTE_SHIFT;
            }
            default -> {
                return this.storage[entry * DWORDS_PER_ENTRY + field / Integer.BYTES];
            }
        }
    }

    /**
     * Stores a little-endian DWORD; regions whose domain values are DWORDs override it to store them whole.
     *
     * @param offset the byte offset of the DWORD, with four bytes inside the region
     * @param value  the DWORD
     */
    @Override
    public void writeDword(int offset, int value) {
        if ((offset & DWORD_OFFSET_MASK) != 0) {
            ClassicRegion.super.writeDword(offset, value);

            return;
        }

        int entry = offset / ENTRY_SIZE;
        int field = offset % ENTRY_SIZE;

        switch (field) {
            case PREVIOUS_LINK, NEXT_LINK -> {
                int shadow = entry * LINK_COUNT + field / Integer.BYTES;
                int target = value == 0 ? -1 : findSpriteIndex(value);

                if (value != 0 && target < 0) {
                    this.linkShadows[shadow]   = value;
                    this.linkShadowSet[shadow] = true;

                    return;
                }

                this.pool.setLink(entry, field == NEXT_LINK, target);

                this.linkShadowSet[shadow] = false;
            }
            case IDENTITY -> {
                Sprite sprite = this.pool.getSprite(entry);

                sprite.setSelectionIndex(value >>> FOURTH_BYTE_SHIFT);

                int owner = value >>> THIRD_BYTE_SHIFT & BYTE_MASK;

                this.ownerShadows[entry]   = owner;
                this.ownerShadowSet[entry] = owner >= PlayerTable.PLAYER_SLOTS;

                if (owner < PlayerTable.PLAYER_SLOTS) {
                    sprite.setOwner(owner);
                }

                int id = value & WORD_MASK;

                this.idShadows[entry]   = id;
                this.idShadowSet[entry] = id >= STORED_ID_LIMIT;

                if (id < STORED_ID_LIMIT) {
                    sprite.setSpriteTypeId(id);
                }
            }
            case INDEX_WORD -> this.pool.getSprite(entry).setRawDimensions(0, 0, 0);
            default -> this.storage[entry * DWORDS_PER_ENTRY + field / Integer.BYTES] = value;
        }
    }
}
