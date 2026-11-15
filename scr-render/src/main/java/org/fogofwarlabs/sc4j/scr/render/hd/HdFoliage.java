package org.fogofwarlabs.sc4j.scr.render.hd;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.image.AnimFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.IntUnaryOperator;

/**
 * Places and animates HD foliage with a dedicated xorshift generator seeded from the map seed. Resource footprints block the placed foliage
 * independently of simulation randomness.
 */
final class HdFoliage {
    /**
     * The game time of one frame at the fastest speed, the unit of the foliage clock.
     */
    static final int FRAME_MILLISECONDS = 42;

    /**
     * Record flag: drawn and animated.
     */
    static final int FLAG_VISIBLE = 1;
    /**
     * Record flag: horizontally flipped.
     */
    static final int FLAG_FLIPPED = 8;
    /**
     * Record flag: the anchor of a placed foliage.
     */
    static final int FLAG_PLACED = 2;
    /**
     * Record flag: occupied (covered by a larger foliage, or removed by a resource).
     */
    static final int FLAG_BLOCKED = 4;

    /**
     * Record flag: covered by the foliage anchored at the record's anchor tile.
     */
    private static final int FLAG_COVERED = 16;

    /**
     * The size of one {@code foliage.bin} type record: nine floats followed by six DWORDs.
     */
    private static final int TYPE_SIZE = 60;
    private static final int TYPE_FLOATS = 9;
    private static final int TYPE_INTS = 6;
    private static final int TYPE_INTS_OFFSET = TYPE_FLOATS * Float.BYTES;
    /**
     * The float fields of a type: scale range, flip threshold, offset ranges and the footprint in tiles.
     */
    private static final int SCALE_LOW = 0;
    private static final int SCALE_HIGH = 1;
    private static final int FLIP_THRESHOLD = 2;
    private static final int OFFSET_X_LOW = 3;
    private static final int OFFSET_X_HIGH = 4;
    private static final int OFFSET_Y_LOW = 5;
    private static final int OFFSET_Y_HIGH = 6;
    private static final int TILES_WIDE = 7;
    private static final int TILES_HIGH = 8;
    /**
     * The DWORD fields of a type: frame count, timing (frames per second, then the margin byte), anim entry, first frame and canvas size.
     */
    private static final int FRAME_COUNT = 0;
    private static final int TIMING = 1;
    private static final int ANIMATION_ENTRY = 2;
    private static final int FIRST_FRAME = 3;
    private static final int CANVAS_WIDTH = 4;
    private static final int CANVAS_HEIGHT = 5;
    /**
     * The position of the margin byte in the timing DWORD.
     */
    private static final int MARGIN_SHIFT = 8;
    /**
     * The sides of a type's anchor margin: left and right.
     */
    private static final int MARGIN_SIDES = 2;

    /**
     * The {@code .fol} header: entry count, type count and bit set bytes, then per entry a float chance and the bit set.
     */
    private static final int FOL_ENTRY_COUNT_OFFSET = 0;
    private static final int FOL_TYPE_COUNT_OFFSET = 4;
    private static final int FOL_BIT_SET_BYTES_OFFSET = 8;
    private static final int FOL_HEADER_SIZE = 12;
    private static final float PERCENT = 100.0f;
    /**
     * The type bit sets: the most significant bit of each byte first.
     */
    private static final int BIT_SET_FIRST_BIT = 0x80;
    private static final int BIT_SET_BYTE_SHIFT = 3;
    private static final int BIT_SET_BIT_MASK = 7;

    /**
     * The game pixels of a tile.
     */
    private static final float TILE_SIZE = 32.0f;
    private static final float DISTRIBUTION_LAMBDA = 1.618033f;
    private static final float DISTRIBUTION_THRESHOLD = 0.8f;
    private static final float MILLISECONDS_PER_SECOND = 1000.0f;
    private static final int INITIAL_COVERED_CAPACITY = 64;

    /**
     * The shifts of the foliage xorshift generator.
     */
    private static final int XORSHIFT_FIRST_SHIFT = 13;
    private static final int XORSHIFT_SECOND_SHIFT = 17;
    private static final int XORSHIFT_THIRD_SHIFT = 5;
    /**
     * A draw is mapped to {@code (0, 1]} as {@code (value % UNIT_MODULUS + 1) / UNIT_DIVISOR}.
     */
    private static final long UNIT_MODULUS = 0xFFFFFFFFL;
    private static final double UNIT_DIVISOR = 4294967295.0;

    /**
     * The masks of an unsigned byte and an unsigned WORD.
     */
    private static final int BYTE_MASK = 0xFF;
    private static final int WORD_MASK = 0xFFFF;

    /**
     * Creates an {@link HdFoliage} instance with the foliage of a tileset, or returns {@code null} when the installed data has none for it.
     *
     * @param storage     the borrowed CASC storage
     * @param prefix      the asset quality prefix ({@code ""} or {@code "HD2/"})
     * @param skin        the skin folder, or {@code null}
     * @param tilesetName the tileset file name
     * @return the created {@link HdFoliage} instance, or {@code null} without foliage data or when type counts differ
     * @throws IOException if a foliage resource cannot be read or parsed
     */
    static @Nullable HdFoliage load(@NotNull CascStorage storage, @NotNull String prefix, @Nullable String skin, @NotNull String tilesetName) throws IOException {
        String folder = skin != null && storage.hasFile(skin + "/TileSet/foliage.bin") ? skin + "/TileSet/" : "TileSet/";
        String animationPath = skin != null && storage.hasFile(prefix + skin + "/TileSet/foliage.anim")
                               ? prefix + skin + "/TileSet/foliage.anim"
                               : prefix + "TileSet/foliage.anim";

        if (!storage.hasFile(folder + "foliage.bin") || !storage.hasFile(folder + tilesetName + ".fol") || !storage.hasFile(animationPath)) {
            return null;
        }

        ByteBuffer types = ByteBuffer.wrap(storage.readFile(folder + "foliage.bin")).order(ByteOrder.LITTLE_ENDIAN);
        int typeCount = types.capacity() / TYPE_SIZE;
        float[] typeFloats = new float[typeCount * TYPE_FLOATS];
        int[] typeInts = new int[typeCount * TYPE_INTS];

        for (int type = 0; type < typeCount; type++) {
            for (int field = 0; field < TYPE_FLOATS; field++) {
                typeFloats[type * TYPE_FLOATS + field] = types.getFloat(type * TYPE_SIZE + field * Float.BYTES);
            }

            for (int field = 0; field < TYPE_INTS; field++) {
                typeInts[type * TYPE_INTS + field] = types.getInt(type * TYPE_SIZE + TYPE_INTS_OFFSET + field * Integer.BYTES);
            }
        }

        byte[] contents = storage.readFile(folder + tilesetName + ".fol");
        ByteBuffer foliage = ByteBuffer.wrap(contents).order(ByteOrder.LITTLE_ENDIAN);
        int entryCount = foliage.getInt(FOL_ENTRY_COUNT_OFFSET);

        // The loader rejects a .fol whose type count differs from foliage.bin.
        if (foliage.getInt(FOL_TYPE_COUNT_OFFSET) != typeCount) {
            return null;
        }

        int bitSetBytes = foliage.getInt(FOL_BIT_SET_BYTES_OFFSET);
        float[] chances = new float[entryCount];
        byte[] bitSets = new byte[entryCount * bitSetBytes];

        for (int entry = 0; entry < entryCount; entry++) {
            int offset = FOL_HEADER_SIZE + entry * (bitSetBytes + Float.BYTES);

            if (offset + Float.BYTES + bitSetBytes > contents.length) {
                throw Exceptions.newIOException("Truncated foliage table {0}.fol", tilesetName);
            }

            chances[entry] = foliage.getFloat(offset) / PERCENT;
            System.arraycopy(contents, offset + Float.BYTES, bitSets, entry * bitSetBytes, bitSetBytes);
        }

        return new HdFoliage(typeFloats, typeInts, typeCount, chances, bitSets, bitSetBytes, AnimFile.parse(storage.readFile(animationPath)));
    }

    /**
     * Picks one foliage offset along an axis: between {@code (int) (low * 32)} and {@code tiles * 32 * high - (int) (frameSize * scale)}, at least
     * the low bound.
     *
     * @param draw      the generator state in element {@code 0}, advanced by the draw
     * @param low       the low bound in tiles
     * @param high      the high bound as a fraction of the footprint
     * @param tiles     the footprint along the axis in tiles
     * @param frameSize the frame size along the axis in game pixels
     * @param scale     the placement scale
     * @return the offset in game pixels
     */
    private static short pickRandomOffset(int @NotNull [] draw, float low, float high, float tiles, int frameSize, float scale) {
        int state = draw[0];
        int lowBound = (int) (low * TILE_SIZE);
        float upper = tiles * TILE_SIZE * high - (float) (int) (frameSize * scale);
        float highBound = Math.max((float) lowBound, upper);
        int range = ((short) (int) highBound - (short) lowBound + 1) & WORD_MASK;
        int value = 0;

        if (range != 1) {
            int nextRandomState = computeNextRandomState(state);

            value = ((short) state + (short) nextRandomState) & WORD_MASK;
            state = nextRandomState;

            if (range != 0) {
                value = value % range;
            }
        }

        draw[0] = state;

        return (short) (lowBound + value);
    }

    /**
     * Advances the foliage xorshift generator by one step; the drawn value is the new state plus the old one.
     *
     * @param state the generator state
     * @return the next state
     */
    private static int computeNextRandomState(int state) {
        int value = state << XORSHIFT_FIRST_SHIFT ^ state;

        value ^= value >>> XORSHIFT_SECOND_SHIFT;

        return value ^ value << XORSHIFT_THIRD_SHIFT;
    }

    /**
     * Maps a draw to {@code (0, 1]} as {@code (value % 0xFFFFFFFF + 1) / 4294967295.0}.
     *
     * @param value the drawn value
     * @return the mapped value
     */
    private static float mapRandomValueToUnitInterval(int value) {
        long unsigned = Integer.toUnsignedLong(value);

        return (float) ((double) (unsigned % UNIT_MODULUS + 1) / UNIT_DIVISOR);
    }

    private final float @NotNull [] typeFloats;
    private final int @NotNull [] typeInts;
    private final int typeCount;
    private final float @NotNull [] chances;
    private final byte @NotNull [] bitSets;
    private final int bitSetBytes;
    private final int entryCount;
    private final @NotNull AnimFile animations;

    private int width;
    private int height;

    private short @NotNull [] types;
    private int @NotNull [] anchors;
    private short @NotNull [] flags;
    private byte @NotNull [] variants;
    private short @NotNull [] offsetX;
    private short @NotNull [] offsetY;
    private float @NotNull [] scales;
    private float @NotNull [] delays;
    private int @NotNull [] lastTimes;

    private float maximumWidth;
    private float maximumHeight;
    private boolean placed;

    private HdFoliage(float @NotNull [] typeFloats, int @NotNull [] typeInts, int typeCount,
                      float @NotNull [] chances, byte @NotNull [] bitSets, int bitSetBytes,
                      @NotNull AnimFile animations) {
        this.typeFloats  = typeFloats;
        this.typeInts    = typeInts;
        this.typeCount   = typeCount;
        this.chances     = chances;
        this.bitSets     = bitSets;
        this.bitSetBytes = bitSetBytes;
        this.entryCount  = chances.length;
        this.animations  = animations;

        this.width  = 0;
        this.height = 0;

        this.types     = new short[0];
        this.anchors   = new int[0];
        this.flags     = new short[0];
        this.variants  = new byte[0];
        this.offsetX   = new short[0];
        this.offsetY   = new short[0];
        this.scales    = new float[0];
        this.delays    = new float[0];
        this.lastTimes = new int[0];

        this.maximumWidth  = 0;
        this.maximumHeight = 0;
        this.placed        = false;
    }

    /**
     * @return the foliage art
     */
    @NotNull AnimFile getAnimations() {
        return this.animations;
    }

    /**
     * @return {@code true} if {@link #place} ran for the current map, {@code false} otherwise
     */
    boolean isPlaced() {
        return this.placed;
    }

    /**
     * @return the largest foliage width in tiles
     */
    float getMaximumWidth() {
        return this.maximumWidth;
    }

    /**
     * @return the largest foliage height in tiles
     */
    float getMaximumHeight() {
        return this.maximumHeight;
    }

    /**
     * @param tile the row-major tile index
     * @return the record flags of the tile ({@link #FLAG_VISIBLE}, {@link #FLAG_FLIPPED} and the placement flags)
     */
    int getFlags(int tile) {
        return this.flags[tile];
    }

    /**
     * @param tile the row-major tile index
     * @return the foliage type anchored at the tile
     */
    int getType(int tile) {
        return this.types[tile];
    }

    /**
     * @param tile the row-major tile index
     * @return the current animation frame offset of the tile's foliage
     */
    int getVariant(int tile) {
        return this.variants[tile] & BYTE_MASK;
    }

    /**
     * @param tile the row-major tile index
     * @return the horizontal placement offset in game pixels
     */
    int getOffsetX(int tile) {
        return this.offsetX[tile];
    }

    /**
     * @param tile the row-major tile index
     * @return the vertical placement offset in game pixels
     */
    int getOffsetY(int tile) {
        return this.offsetY[tile];
    }

    /**
     * @param tile the row-major tile index
     * @return the placement scale
     */
    float getScale(int tile) {
        return this.scales[tile];
    }

    /**
     * @param type the foliage type
     * @return the anim entry of the type
     */
    int getAnimationEntry(int type) {
        return this.typeInts[type * TYPE_INTS + ANIMATION_ENTRY];
    }

    /**
     * @param type the foliage type
     * @return the first anim frame of the type
     */
    int getFirstFrame(int type) {
        return this.typeInts[type * TYPE_INTS + FIRST_FRAME];
    }

    /**
     * @param type the foliage type
     * @return the canvas width of the type in scale-{@code 4} pixels
     */
    int getCanvasWidth(int type) {
        return this.typeInts[type * TYPE_INTS + CANVAS_WIDTH];
    }

    /**
     * @param type the foliage type
     * @return the canvas height of the type in scale-{@code 4} pixels
     */
    int getCanvasHeight(int type) {
        return this.typeInts[type * TYPE_INTS + CANVAS_HEIGHT];
    }

    /**
     * @return the map width in tiles of the last placement
     */
    int getWidth() {
        return this.width;
    }

    /**
     * @return the map height in tiles of the last placement
     */
    int getHeight() {
        return this.height;
    }


    /**
     * Places the foliage of a map.
     *
     * @param seed      the game seed
     * @param width     the map width in tiles
     * @param height    the map height in tiles
     * @param megatiles the VX4 megatile of each row-major tile index (the CV5 lookup), or a negative value outside the CV5 table
     */
    void place(int seed, int width, int height, @NotNull IntUnaryOperator megatiles) {
        int tileCount = width * height;

        this.width         = width;
        this.height        = height;
        this.types         = new short[tileCount];
        this.anchors       = new int[tileCount];
        this.flags         = new short[tileCount];
        this.variants      = new byte[tileCount];
        this.offsetX       = new short[tileCount];
        this.offsetY       = new short[tileCount];
        this.scales        = new float[tileCount];
        this.delays        = new float[tileCount];
        this.lastTimes     = new int[tileCount];
        this.maximumWidth  = 0;
        this.maximumHeight = 0;

        int state = seed == 0 ? 1 : seed;
        int[] candidates = new int[this.typeCount];
        int[] covered = new int[INITIAL_COVERED_CAPACITY];

        for (int tile = 0; tile < tileCount; tile++) {
            int megatile = megatiles.applyAsInt(tile);

            if (megatile < 0 || megatile >= this.entryCount || !this.hasAnyType(megatile) || (this.flags[tile] & FLAG_PLACED) != 0) {
                continue;
            }

            int nextRandomState = computeNextRandomState(state);
            float chance = mapRandomValueToUnitInterval(nextRandomState + state);

            state = nextRandomState;

            if (chance > this.chances[megatile]) {
                continue;
            }

            nextRandomState = computeNextRandomState(state);

            float delayDraw = mapRandomValueToUnitInterval(nextRandomState + state);

            state = nextRandomState;

            float delay = -((float) Math.log(1.0f - delayDraw) / DISTRIBUTION_LAMBDA);
            int candidateCount = 0;

            for (int type = 0; type < this.typeCount; type++) {
                if (this.hasType(megatile, type) && this.typeInts[type * TYPE_INTS + FRAME_COUNT] != 0 &&
                    this.collectCoveredTiles(megatiles, type, tile, null) >= 0) {
                    candidates[candidateCount++] = type;
                }
            }

            if (candidateCount == 0) {
                continue;
            }

            int pick = 0;

            if (candidateCount != 1) {
                nextRandomState = computeNextRandomState(state);
                pick            = ((nextRandomState + state) & WORD_MASK) % candidateCount;
                state           = nextRandomState;
            }

            int type = candidates[pick];

            int footprint = Math.max(1,
                                     (int) this.typeFloats[type * TYPE_FLOATS + TILES_WIDE] * (int) this.typeFloats[type * TYPE_FLOATS + TILES_HIGH]);

            if (covered.length < footprint) {
                covered = new int[footprint];
            }

            int coveredCount = this.collectCoveredTiles(megatiles, type, tile, covered);

            if (coveredCount < 0) {
                continue;
            }

            this.delays[tile]    = delay;
            this.types[tile]     = (short) type;
            this.anchors[tile]   = tile;
            this.lastTimes[tile] = 0;

            float scaleLow = this.typeFloats[type * TYPE_FLOATS + SCALE_LOW];
            float scaleHigh = this.typeFloats[type * TYPE_FLOATS + SCALE_HIGH];

            nextRandomState   = computeNextRandomState(state);
            this.scales[tile] = (scaleHigh - scaleLow) * mapRandomValueToUnitInterval(nextRandomState + state) + scaleLow;
            state             = nextRandomState;
            nextRandomState   = computeNextRandomState(state);

            float flipDraw = mapRandomValueToUnitInterval(nextRandomState + state);

            state = nextRandomState;

            if (this.typeFloats[type * TYPE_FLOATS + FLIP_THRESHOLD] < flipDraw) {
                this.flags[tile] &= (short) ~FLAG_FLIPPED;
            } else {
                this.flags[tile] |= FLAG_FLIPPED;
            }

            int variantCount = this.typeInts[type * TYPE_INTS + FRAME_COUNT] & WORD_MASK;
            int variant = 0;

            if (variantCount != 1) {
                nextRandomState = computeNextRandomState(state);
                variant         = nextRandomState + state;
                state           = nextRandomState;

                if (variantCount != 0) {
                    variant = (variant & WORD_MASK) % variantCount;
                }
            }

            this.variants[tile] = (byte) variant;

            AnimFile.Entry entry = this.animations.getEntry(this.typeInts[type * TYPE_INTS + ANIMATION_ENTRY]);
            int frame = this.typeInts[type * TYPE_INTS + FIRST_FRAME];
            int scaleDivisor = this.animations.getScale() == 0 ? HdFrameGeometry.UNIT_SCALE : this.animations.getScale();
            int frameWidth = entry.getFrameWidth(frame) / scaleDivisor;
            int frameHeight = entry.getFrameHeight(frame) / scaleDivisor;
            float scale = this.scales[tile];
            int[] draw = {state, 0};
            int typeOffset = type * TYPE_FLOATS;

            this.offsetX[tile] = pickRandomOffset(draw,
                                                  this.typeFloats[typeOffset + OFFSET_X_LOW],
                                                  this.typeFloats[typeOffset + OFFSET_X_HIGH],
                                                  this.typeFloats[typeOffset + TILES_WIDE],
                                                  frameWidth,
                                                  scale);
            this.offsetY[tile] = pickRandomOffset(draw,
                                                  this.typeFloats[typeOffset + OFFSET_Y_LOW],
                                                  this.typeFloats[typeOffset + OFFSET_Y_HIGH],
                                                  this.typeFloats[typeOffset + TILES_HIGH],
                                                  frameHeight,
                                                  scale);
            state              = draw[0];

            float tilesWide = this.typeFloats[typeOffset + TILES_WIDE];
            float tilesHigh = this.typeFloats[typeOffset + TILES_HIGH];

            for (int row = 0; row < tilesHigh; row++) {
                for (int column = 0; column < tilesWide; column++) {
                    int index = (int) (row * tilesWide) + column;

                    if (index != 0 && index < coveredCount) {
                        int record = covered[index];

                        this.anchors[record] = tile;
                        this.flags[record]   = (short) ((this.flags[record] | FLAG_COVERED) & ~FLAG_PLACED | FLAG_BLOCKED);
                    }
                }
            }

                                this.maximumWidth  = Math.max(this.maximumWidth, tilesWide);
                                this.maximumHeight = Math.max(this.maximumHeight, tilesHigh);
            this.flags[tile] |= FLAG_PLACED;
        }

        this.updateVisibility();
        this.placed = true;
    }

    /**
     * Collects the tiles a foliage type covers with its anchor at a tile, row-major (the anchor first), and tests whether the type fits there.
     *
     * @param megatiles the VX4 megatile of each row-major tile index
     * @param type      the foliage type
     * @param tile      the anchor tile
     * @param covered   the array receiving the covered tiles, or {@code null}
     * @return the number of covered tiles, or {@code -1} if the type does not fit
     */
    private int collectCoveredTiles(@NotNull IntUnaryOperator megatiles, int type, int tile, int @Nullable [] covered) {
        int tilesHigh = (int) this.typeFloats[type * TYPE_FLOATS + TILES_HIGH];
        int tilesWide = (int) this.typeFloats[type * TYPE_FLOATS + TILES_WIDE];
        int column = tile % this.width;
        int row = tile / this.width;
        int coveredCount = 0;

        for (int rowOffset = 0; rowOffset < tilesHigh; rowOffset++) {
            int tileRow = row + rowOffset;

            if (tileRow >= this.height) {
                return -1;
            }

            for (int columnOffset = 0; columnOffset < tilesWide; columnOffset++) {
                int tileColumn = column + columnOffset;

                if (tileColumn >= this.width) {
                    return -1;
                }

                int index = tileRow * this.width + tileColumn;

                if ((this.flags[index] & (FLAG_PLACED | FLAG_BLOCKED)) != 0) {
                    return -1;
                }

                int megatile = megatiles.applyAsInt(index);

                if (megatile < 0 || megatile >= this.entryCount || !this.hasType(megatile, type)) {
                    return -1;
                }

                if (covered != null) {
                    covered[coveredCount] = index;
                }

                coveredCount++;
            }
        }

        // The same type may not be anchored within the margin above or to the sides.
        int margin = (this.typeInts[type * TYPE_INTS + TIMING] >>> MARGIN_SHIFT) & BYTE_MASK;
        int firstColumn = margin <= column ? column - margin : 0;
        int endColumn = (int) this.typeFloats[type * TYPE_FLOATS + TILES_WIDE] + 1 + firstColumn + margin * MARGIN_SIDES;

        for (int tileRow = margin <= row ? row - margin : 0; tileRow < row + 1; tileRow++) {
            if (tileRow >= this.height || firstColumn >= endColumn) {
                continue;
            }

            for (int tileColumn = firstColumn; tileColumn < endColumn; tileColumn++) {
                if (tileColumn < this.width) {
                    int index = tileRow * this.width + tileColumn;

                    if ((this.flags[index] & FLAG_PLACED) != 0 && this.types[index] == type) {
                        return -1;
                    }
                }
            }
        }

        return coveredCount;
    }

    /**
     * Marks the placed, unblocked foliage whose exponential draw reaches {@link #DISTRIBUTION_THRESHOLD} visible and resets the animation clocks.
     */
    private void updateVisibility() {
        for (int tile = 0; tile < this.flags.length; tile++) {
            int flag = this.flags[tile];

            if ((flag & FLAG_BLOCKED) == 0 && (flag & FLAG_PLACED) != 0 && this.delays[tile] >= DISTRIBUTION_THRESHOLD) {
                flag |= FLAG_VISIBLE;
            } else {
                flag &= ~FLAG_VISIBLE;
            }

            this.flags[tile]     = (short) flag;
            this.lastTimes[tile] = 0;
        }
    }

    /**
     * Removes the foliage over a resource's tiles: every tile of the rectangle resolves to its anchor and the anchored type's footprint is hidden and
     * blocked (a tile without foliage uses the footprint of type {@code 0}).
     *
     * @param column  the first tile column
     * @param row     the first tile row
     * @param columns the width in tiles
     * @param rows    the height in tiles
     */
    void block(int column, int row, int columns, int rows) {
        if (!this.placed) {
            return;
        }

        for (int rowOffset = 0; rowOffset < rows; rowOffset++) {
            for (int columnOffset = 0; columnOffset < columns; columnOffset++) {
                int tileColumn = column + columnOffset;
                int tileRow = row + rowOffset;

                if (tileColumn < 0 || tileRow < 0 || tileColumn >= this.width || tileRow >= this.height) {
                    continue;
                }

                int anchor = tileRow * this.width + tileColumn;

                if ((this.flags[anchor] & FLAG_COVERED) != 0) {
                    anchor = this.anchors[anchor];
                }

                int type = this.types[anchor];
                float tilesWide = this.typeFloats[type * TYPE_FLOATS + TILES_WIDE];
                float tilesHigh = this.typeFloats[type * TYPE_FLOATS + TILES_HIGH];

                // The footprint has no column bound: past the right edge it runs on in the flat tile array, into the next row's first tiles.
                for (int footprintRow = 0; footprintRow < tilesHigh; footprintRow++) {
                    for (int footprintColumn = 0; footprintColumn < tilesWide; footprintColumn++) {
                        int index = anchor + this.width * footprintRow + footprintColumn;

                        if (index < this.flags.length) {
                            this.flags[index] = (short) (this.flags[index] & ~FLAG_VISIBLE | FLAG_BLOCKED);
                        }
                    }
                }
            }
        }
    }

    /**
     * Advances the animations around the view: a visible foliage of {@code n} frames at {@code f} frames per second (the type's timing byte) steps
     * {@code (time - last) / (int) (1000 / f)} frames once that period elapsed.
     *
     * @param time        the foliage clock, {@code frame * 42}
     * @param firstColumn the view's first tile column
     * @param firstRow    the view's first tile row
     * @param columns     the view width in tiles, {@code (pixels + 31) / 32}
     * @param rows        the view height in tiles
     */
    void update(int time, int firstColumn, int firstRow, int columns, int rows) {
        int marginColumns = Math.max(0, (int) this.maximumWidth - 1);
        int marginRows = Math.max(0, (int) this.maximumHeight - 1);

        for (int row = -marginRows; row < rows + 1 + marginRows; row++) {
            int tileRow = firstRow + row;

            if (tileRow < 0 || tileRow >= this.height) {
                continue;
            }

            for (int column = -marginColumns; column < columns + 1 + marginColumns; column++) {
                int tileColumn = firstColumn + column;

                if (tileColumn < 0 || tileColumn >= this.width) {
                    continue;
                }

                int tile = tileRow * this.width + tileColumn;

                if ((this.flags[tile] & FLAG_VISIBLE) == 0) {
                    continue;
                }

                int type = this.types[tile];
                int period = (int) (MILLISECONDS_PER_SECOND / (float) (this.typeInts[type * TYPE_INTS + TIMING] & BYTE_MASK));
                int last = this.lastTimes[tile];

                if (Integer.compareUnsigned(time, last + period) <= 0) {
                    continue;
                }

                int frame = (this.variants[tile] & BYTE_MASK) + Integer.divideUnsigned(time - last, period) & BYTE_MASK;
                int frameCount = this.typeInts[type * TYPE_INTS + FRAME_COUNT];

                if (Integer.compareUnsigned(frame, frameCount) >= 0) {
                    frame = Integer.remainderUnsigned(frame, frameCount);
                }

                this.variants[tile]  = (byte) frame;
                this.lastTimes[tile] = time;
            }
        }
    }

    private boolean hasAnyType(int megatile) {
        for (int index = 0; index < this.bitSetBytes; index++) {
            if (this.bitSets[megatile * this.bitSetBytes + index] != 0) {
                return true;
            }
        }

        return false;
    }

    private boolean hasType(int megatile, int type) {
        return (this.bitSets[megatile * this.bitSetBytes + (type >>> BIT_SET_BYTE_SHIFT)] & (BIT_SET_FIRST_BIT >>> (type & BIT_SET_BIT_MASK))) != 0;
    }
}
