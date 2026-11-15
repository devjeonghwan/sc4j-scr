package org.fogofwarlabs.screcorder.sample.tile;

import org.fogofwarlabs.screcorder.binary.LittleEndianUtils;
import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.sampling.Sampler;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.sample.SampleDefinition;
import org.fogofwarlabs.screcorder.sample.io.SampleReader;
import org.fogofwarlabs.screcorder.sample.io.SampleWriter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Defines the map tile identifier payload and its bulk memory sampling.
 */
public final class TilesSampleDefinition extends SampleDefinition<TilesSample> {
    private static final int SAMPLE_IDENTIFIER = 15;

    private static final int MAP_SIZE_OFFSET = 0xE4;
    private static final int MAP_HEIGHT_OFFSET = Short.BYTES;
    private static final long TILES_POINTER_OFFSET = 0xE7E478;

    private static final int SIZE_BYTES = 2 * Short.BYTES;
    private static final int MAXIMUM_TILE_COUNT = TilesSample.MAXIMUM_DIMENSION * TilesSample.MAXIMUM_DIMENSION;

    private static final @NotNull SampleReader<TilesSample> READER = TilesSampleDefinition::read;
    private static final @NotNull SampleWriter<TilesSample> WRITER = TilesSampleDefinition::write;

    private static @NotNull TilesSample read(@NotNull ByteBuffer input) throws IOException {
        input.order(ByteOrder.LITTLE_ENDIAN);

        if (input.remaining() < SIZE_BYTES) {
            throw Exceptions.newIOException("Missing map tile dimensions");
        }

        int width = Short.toUnsignedInt(input.getShort());
        int height = Short.toUnsignedInt(input.getShort());

        if (!isValidSize(width, height) || (long) width * height * Short.BYTES != input.remaining()) {
            throw Exceptions.newIOException("Invalid map tile dimensions or payload size: {0} x {1}", width, height);
        }

        short[] tiles = new short[width * height];

        input.asShortBuffer().get(tiles);
        input.position(input.limit());

        return new TilesSample(width, height, tiles);
    }

    private static void write(@NotNull ByteBuffer output, @NotNull TilesSample sample) throws IOException {
        short[] tiles = sample.getTiles();
        int encodedSize = SIZE_BYTES + tiles.length * Short.BYTES;

        if (output.remaining() < encodedSize || output.isReadOnly()) {
            throw Exceptions.newIOException("Insufficient writable map tile buffer: {0}", encodedSize);
        }

        output.order(ByteOrder.LITTLE_ENDIAN).putShort((short) sample.getWidth()).putShort((short) sample.getHeight());

        for (short tile : tiles) {
            output.putShort(tile);
        }
    }

    private static boolean isValidSize(int width, int height) {
        return width >= 1 && width <= TilesSample.MAXIMUM_DIMENSION && height >= 1 && height <= TilesSample.MAXIMUM_DIMENSION;
    }


    /**
     * Creates a {@link TilesSampleDefinition} instance.
     */
    public TilesSampleDefinition() {
        super(SAMPLE_IDENTIFIER, "tiles", TilesSample.class, SIZE_BYTES + MAXIMUM_TILE_COUNT * Short.BYTES);
    }


    /**
     * @return the bounded payload reader
     */
    @Override
    public @NotNull SampleReader<TilesSample> getReader() {
        return READER;
    }

    /**
     * @return the canonical payload writer
     */
    @Override
    public @NotNull SampleWriter<TilesSample> getWriter() {
        return WRITER;
    }


    /**
     * Calculates the exact payload size before reserving reusable output storage.
     *
     * @param sample the sample to encode
     * @return the exact encoded byte count
     */
    @Override
    public int getEncodedSize(@NotNull TilesSample sample) {
        return SIZE_BYTES + sample.getWidth() * sample.getHeight() * Short.BYTES;
    }

    /**
     * Creates a {@link Sampler} instance with session-owned capture buffers.
     * <p>
     * Each sample reads the map width and height from the game context, then {@code width * height} tile identifier words
     * from the start of the tile array that the executable's tile pointer addresses.
     *
     * @param context the capture session
     * @return the created {@link Sampler} instance
     */
    @Override
    public @NotNull Sampler<TilesSample> createSampler(@NotNull CaptureContext context) {
        byte[] sizeStorage = new byte[SIZE_BYTES];
        byte[] pointerStorage = new byte[Long.BYTES];
        byte[] tileStorage = new byte[MAXIMUM_TILE_COUNT * Short.BYTES];
        ShortBuffer tileWords = ByteBuffer.wrap(tileStorage).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();

        return () -> {
            context.getMemory().read(context.getContextAddress() + MAP_SIZE_OFFSET, sizeStorage, 0, sizeStorage.length);

            int width = LittleEndianUtils.readUnsigned16(sizeStorage, 0);
            int height = LittleEndianUtils.readUnsigned16(sizeStorage, MAP_HEIGHT_OFFSET);

            if (!isValidSize(width, height)) {
                throw Exceptions.newIOException("Invalid map dimensions: {0} x {1}", width, height);
            }

            context.getMemory().read(context.getModuleBase() + TILES_POINTER_OFFSET, pointerStorage, 0, pointerStorage.length);

            long tilesAddress = LittleEndianUtils.readUnsigned(pointerStorage, 0, Long.BYTES);

            if (tilesAddress == 0) {
                throw Exceptions.newIOException("Map tile array is not allocated");
            }

            short[] tiles = new short[width * height];

            context.getMemory().read(tilesAddress, tileStorage, 0, tiles.length * Short.BYTES);
            tileWords.get(0, tiles);

            return new TilesSample(width, height, tiles);
        };
    }
}
