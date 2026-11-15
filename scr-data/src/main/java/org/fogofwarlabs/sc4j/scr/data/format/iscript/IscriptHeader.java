package org.fogofwarlabs.sc4j.scr.data.format.iscript;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a single iscript header: an iscript ID with its animation blocks.
 */
public final class IscriptHeader {
    /**
     * The iscript animation names indexed by animation type.
     */
    public static final @NotNull String @NotNull [] ANIMATION_TYPE_NAMES = {
            "Init", "Death", "GndAttkInit", "AirAttkInit", "Unused1", "GndAttkRpt", "AirAttkRpt",
            "CastSpell", "GndAttkToIdle", "AirAttkToIdle", "Unused2", "Walking", "WalkingToIdle",
            "SpecialState1", "SpecialState2", "AlmostBuilt", "Built", "Landing", "LiftOff",
            "IsWorking", "WorkingToIdle", "WarpIn", "Unused3", "StarEditInit", "Disable",
            "Burrow", "UnBurrow", "Enable"
    };

    /**
     * Represents a single animation block.
     */
    public static final class Animation {
        private final int typeIndex;
        private final int offset;
        private final @NotNull List<IscriptInstruction> instructions;

        /**
         * Creates an {@link Animation} instance.
         *
         * @param typeIndex    the animation type index
         * @param offset       the absolute file offset of the block
         * @param instructions the decoded instructions
         */
        public Animation(int typeIndex, int offset, @NotNull List<IscriptInstruction> instructions) {
            this.typeIndex    = typeIndex;
            this.offset       = offset;
            this.instructions = instructions;
        }

        /**
         * @return the animation type index
         */
        public int getTypeIndex() {
            return this.typeIndex;
        }

        /**
         * @return the absolute file offset of the block
         */
        public int getOffset() {
            return this.offset;
        }

        /**
         * @return the decoded instructions
         */
        public @NotNull List<IscriptInstruction> getInstructions() {
            return this.instructions;
        }
    }

    private final int id;
    private final int offset;
    private final int type;
    private final int @NotNull [] animationOffsets;
    private final @NotNull List<Animation> animations;

    /**
     * Creates an {@link IscriptHeader} instance.
     *
     * @param id               the iscript ID
     * @param offset           the file offset of the header
     * @param type             the declared WORD type; the inspection table holds {@code type + 1} offsets
     * @param animationOffsets the animation block offsets per type index; {@code 0} means unused
     * @param animations       the animation blocks
     */
    public IscriptHeader(int id, int offset, int type, int @NotNull [] animationOffsets, @NotNull List<Animation> animations) {
        this.id               = id;
        this.offset           = offset;
        this.type             = type;
        this.animationOffsets = animationOffsets;
        this.animations       = animations;
    }

    /**
     * @return the iscript ID
     */
    public int getId() {
        return this.id;
    }

    /**
     * @return the file offset of the header
     */
    public int getOffset() {
        return this.offset;
    }

    /**
     * @return the header type
     */
    public int getType() {
        return this.type;
    }

    /**
     * Returns the animation block offset within the declared inspection table.
     * <p>
     * Runtime consumers use {@link IscriptFile#getAnimationOffset(int, int)} without this inspection table's declared type bound.
     *
     * @param typeIndex the animation type index
     * @return the absolute file offset of the block, or {@code 0} if unused
     */
    public int getAnimationOffset(int typeIndex) {
        if (typeIndex < 0 || typeIndex >= this.animationOffsets.length) {
            return 0;
        }

        return this.animationOffsets[typeIndex];
    }

    /**
     * @return the animation blocks
     */
    public @NotNull List<Animation> getAnimations() {
        return this.animations;
    }
}
