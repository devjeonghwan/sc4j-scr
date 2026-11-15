package org.fogofwarlabs.sc4j.scr.data.format.terrain;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * One record of {@code tileset/foliage.bin}: the placement, scale and animation of a foliage definition.
 */
public final class FoliageDefinition {
    /**
     * The size of one record in bytes.
     */
    public static final int RECORD_SIZE = 60;

    private static final int MINIMUM_SCALE_OFFSET = 0x0;
    private static final int MAXIMUM_SCALE_OFFSET = 0x4;
    private static final int FLAG_PROBABILITY_OFFSET = 0x8;
    private static final int MINIMUM_HORIZONTAL_POSITION_OFFSET = 0xC;
    private static final int MAXIMUM_HORIZONTAL_FACTOR_OFFSET = 0x10;
    private static final int MINIMUM_VERTICAL_POSITION_OFFSET = 0x14;
    private static final int MAXIMUM_VERTICAL_FACTOR_OFFSET = 0x18;
    private static final int FOOTPRINT_WIDTH_OFFSET = 0x1C;
    private static final int FOOTPRINT_HEIGHT_OFFSET = 0x20;
    private static final int RANDOM_SELECTION_RANGE_OFFSET = 0x24;
    private static final int EXCLUSION_RADIUS_OFFSET = 0x29;
    private static final int ANIMATION_INDEX_OFFSET = 0x2C;
    private static final int FRAME_INDEX_OFFSET = 0x30;

    /**
     * Decodes every record of a definition file.
     *
     * @param data the file contents
     * @return the definitions in file order
     * @throws IllegalArgumentException if the data is empty or not whole records
     */
    public static @NotNull FoliageDefinition @NotNull [] parseAll(byte @NotNull [] data) {
        if (data.length < RECORD_SIZE || data.length % RECORD_SIZE != 0) {
            throw Exceptions.newIllegalArgumentException("The foliage definitions require complete 60-byte records.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        FoliageDefinition[] definitions = new FoliageDefinition[data.length / RECORD_SIZE];

        for (int index = 0; index < definitions.length; index++) {
            definitions[index] = new FoliageDefinition(buffer, index * RECORD_SIZE);
        }

        return definitions;
    }


    private final float minimumScale;
    private final float maximumScale;
    private final float flagProbability;

    private final float minimumHorizontalPosition;
    private final float maximumHorizontalFactor;
    private final float minimumVerticalPosition;
    private final float maximumVerticalFactor;
    private final float footprintWidth;
    private final float footprintHeight;

    private final int randomSelectionRange;
    private final int exclusionRadius;

    private final int animationIndex;
    private final int frameIndex;

    private FoliageDefinition(@NotNull ByteBuffer buffer, int recordOffset) {
        this.minimumScale    = buffer.getFloat(recordOffset + MINIMUM_SCALE_OFFSET);
        this.maximumScale    = buffer.getFloat(recordOffset + MAXIMUM_SCALE_OFFSET);
        this.flagProbability = buffer.getFloat(recordOffset + FLAG_PROBABILITY_OFFSET);

        this.minimumHorizontalPosition = buffer.getFloat(recordOffset + MINIMUM_HORIZONTAL_POSITION_OFFSET);
        this.maximumHorizontalFactor   = buffer.getFloat(recordOffset + MAXIMUM_HORIZONTAL_FACTOR_OFFSET);
        this.minimumVerticalPosition   = buffer.getFloat(recordOffset + MINIMUM_VERTICAL_POSITION_OFFSET);
        this.maximumVerticalFactor     = buffer.getFloat(recordOffset + MAXIMUM_VERTICAL_FACTOR_OFFSET);
        this.footprintWidth            = buffer.getFloat(recordOffset + FOOTPRINT_WIDTH_OFFSET);
        this.footprintHeight           = buffer.getFloat(recordOffset + FOOTPRINT_HEIGHT_OFFSET);

        this.randomSelectionRange = buffer.getInt(recordOffset + RANDOM_SELECTION_RANGE_OFFSET);
        this.exclusionRadius      = Byte.toUnsignedInt(buffer.get(recordOffset + EXCLUSION_RADIUS_OFFSET));

        this.animationIndex = buffer.getInt(recordOffset + ANIMATION_INDEX_OFFSET);
        this.frameIndex     = buffer.getInt(recordOffset + FRAME_INDEX_OFFSET);
    }

    /**
     * @return the smallest scale factor
     */
    public float getMinimumScale() {
        return this.minimumScale;
    }

    /**
     * @return the largest scale factor
     */
    public float getMaximumScale() {
        return this.maximumScale;
    }

    /**
     * @return the probability of the retained tile flag bit {@code 8}
     */
    public float getFlagProbability() {
        return this.flagProbability;
    }

    /**
     * @return the smallest horizontal position, in tiles
     */
    public float getMinimumHorizontalPosition() {
        return this.minimumHorizontalPosition;
    }

    /**
     * @return the factor of the footprint width bounding the horizontal position
     */
    public float getMaximumHorizontalFactor() {
        return this.maximumHorizontalFactor;
    }

    /**
     * @return the smallest vertical position, in tiles
     */
    public float getMinimumVerticalPosition() {
        return this.minimumVerticalPosition;
    }

    /**
     * @return the factor of the footprint height bounding the vertical position
     */
    public float getMaximumVerticalFactor() {
        return this.maximumVerticalFactor;
    }

    /**
     * @return the footprint width, in tiles
     */
    public float getFootprintWidth() {
        return this.footprintWidth;
    }

    /**
     * @return the footprint height, in tiles
     */
    public float getFootprintHeight() {
        return this.footprintHeight;
    }

    /**
     * @return the DWORD range of the random selection draw ({@code 0} excludes the definition)
     */
    public int getRandomSelectionRange() {
        return this.randomSelectionRange;
    }

    /**
     * @return the unsigned radius, in tiles, within which the same definition is not repeated
     */
    public int getExclusionRadius() {
        return this.exclusionRadius;
    }

    /**
     * @return the animation index in {@code tileset/foliage.anim}
     */
    public int getAnimationIndex() {
        return this.animationIndex;
    }

    /**
     * @return the frame index within the animation
     */
    public int getFrameIndex() {
        return this.frameIndex;
    }
}
