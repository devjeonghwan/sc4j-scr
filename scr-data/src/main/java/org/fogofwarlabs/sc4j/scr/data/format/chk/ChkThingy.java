package org.fogofwarlabs.sc4j.scr.data.format.chk;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;

/**
 * Represents a ten-byte THG2 record and its unit or sprite creation-routing inputs.
 * <p>
 * Routing reports a request; this class does not construct units or sprites.
 */
public final class ChkThingy {
    /**
     * The size of one THG2 record in bytes.
     */
    public static final int ENTRY_SIZE = 10;

    /**
     * The flag that selects the standalone sprite constructor.
     */
    private static final int SPRITE_FLAG = 0x1000;
    /**
     * The flag that requests the post-creation effect after a successful unit constructor.
     */
    private static final int POST_CREATION_EFFECT_FLAG = 0x8000;
    /**
     * The neutral player, the owner of the door units.
     */
    private static final int NEUTRAL_OWNER = 11;

    private final int sourceOffset;
    private final int typeIdentifier;
    private final int horizontal;
    private final int vertical;
    private final int owner;
    private final int flags;

    /**
     * Creates a {@link ChkThingy} instance.
     *
     * @param sourceOffset   the record offset in the supplied CHK buffer
     * @param typeIdentifier the unsigned unit or sprite type WORD
     * @param horizontal     the unsigned horizontal position WORD
     * @param vertical       the unsigned vertical position WORD
     * @param owner          the unmodified owner byte
     * @param flags          the unmodified flags WORD
     */
    ChkThingy(int sourceOffset, int typeIdentifier, int horizontal, int vertical, int owner, int flags) {
        this.sourceOffset   = sourceOffset;
        this.typeIdentifier = typeIdentifier;
        this.horizontal     = horizontal;
        this.vertical       = vertical;
        this.owner          = owner;
        this.flags          = flags;
    }

    /**
     * @return the record offset in the original supplied CHK buffer
     */
    public int getSourceOffset() {
        return this.sourceOffset;
    }

    /**
     * @return the unsigned unit or sprite type WORD
     */
    public int getTypeIdentifier() {
        return this.typeIdentifier;
    }

    /**
     * @return the unsigned horizontal position WORD passed to the constructor
     */
    public int getHorizontal() {
        return this.horizontal;
    }

    /**
     * @return the unsigned vertical position WORD passed to the constructor
     */
    public int getVertical() {
        return this.vertical;
    }

    /**
     * @return the unmodified owner byte
     */
    public int getOwner() {
        return this.owner;
    }

    /**
     * @return the unmodified flags WORD
     */
    public int getFlags() {
        return this.flags;
    }

    /**
     * @return the owner passed to the constructor: the neutral player for the door unit types {@code 205..208}, otherwise the record owner
     */
    public int getCreationOwner() {
        return !this.isSprite() && this.typeIdentifier >= ScrUnitIds.SPECIAL_UPPER_LEVEL_DOOR &&
               this.typeIdentifier <= ScrUnitIds.SPECIAL_RIGHT_PIT_DOOR
               ? NEUTRAL_OWNER
               : this.owner;
    }

    /**
     * @return {@code true} if flag {@code 0x1000} selects the standalone sprite constructor, {@code false} otherwise
     */
    public boolean isSprite() {
        return (this.flags & SPRITE_FLAG) != 0;
    }

    /**
     * @return {@code true} if a non-sprite record requests a post-creation effect (flag {@code 0x8000}), {@code false} otherwise
     */
    public boolean hasPostCreationEffect() {
        return !this.isSprite() && (this.flags & POST_CREATION_EFFECT_FLAG) != 0;
    }


    /**
     * Checks whether the record requests unit creation.
     *
     * @param useMapSettings whether the game uses map settings
     * @return {@code true} if unit creation is requested, {@code false} otherwise
     */
    public boolean requestsUnitCreation(boolean useMapSettings) {
        return !this.isSprite() && (useMapSettings || this.getCreationOwner() == NEUTRAL_OWNER);
    }
}
