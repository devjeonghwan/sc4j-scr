package org.fogofwarlabs.screcorder.sample.tile;

import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.sampling.Sampler;
import org.fogofwarlabs.screcorder.sample.RangeMemoryScenario;
import org.fogofwarlabs.screcorder.sample.SampleRoundTripCheck;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks that the tiles sampler reads the map tile words through the tile pointer as detached snapshots and that the tiles
 * sample validates its dimensions and payload.
 */
final class TilesSampleTest {
    private static final long MODULE_BASE = 0x140000000L;
    private static final long CONTEXT_ADDRESS = 0x200000000L;
    private static final long TILES_ADDRESS = 0x300000000L;
    private static final int CONTEXT_BYTES = 0x100;
    private static final int MAP_WIDTH_OFFSET = 0xE4;
    private static final int MAP_HEIGHT_OFFSET = 0xE6;
    private static final int TILES_POINTER_OFFSET = 0xE7E478;
    private static final int TILE_ARRAY_BYTES = 0x20000;

    private static final int MAP_WIDTH = 3;
    private static final int MAP_HEIGHT = 2;
    private static final int LAST_TILE_INDEX = 5;
    private static final int FIRST_TILE = 0x10;
    private static final int MAXIMUM_TILE = 0xFFFF;
    private static final int CHANGED_TILE = 0x16;
    private static final int OUTSIDE_MAP_TILE = 0x1234;
    private static final int SIZE_BYTES = 4;
    private static final int OVERSIZED_DIMENSION = 257;

    private static final class TileMemoryScenario {
        private final @NotNull RangeMemoryScenario memory;
        private final @NotNull ByteBuffer context;
        private final @NotNull ByteBuffer pointer;
        private final @NotNull ByteBuffer tiles;

        private TileMemoryScenario() {
            this.memory  = new RangeMemoryScenario();
            this.context = ByteBuffer.wrap(this.memory.allocateRange(CONTEXT_ADDRESS, CONTEXT_BYTES)).order(ByteOrder.LITTLE_ENDIAN);
            this.pointer = ByteBuffer.wrap(this.memory.allocateRange(MODULE_BASE + TILES_POINTER_OFFSET, Long.BYTES)).order(ByteOrder.LITTLE_ENDIAN);
            this.tiles   = ByteBuffer.wrap(this.memory.allocateRange(TILES_ADDRESS, TILE_ARRAY_BYTES)).order(ByteOrder.LITTLE_ENDIAN);

            this.context.putShort(MAP_WIDTH_OFFSET, (short) MAP_WIDTH).putShort(MAP_HEIGHT_OFFSET, (short) MAP_HEIGHT);
            this.pointer.putLong(0, TILES_ADDRESS);
        }
    }


    @Test
    void tiles_sampler_reads_the_map_tile_words_and_keeps_snapshots_detached() throws IOException {
        TileMemoryScenario scenario = new TileMemoryScenario();

        scenario.tiles.putShort(0, (short) FIRST_TILE);
        scenario.tiles.putShort(LAST_TILE_INDEX * Short.BYTES, (short) MAXIMUM_TILE);
        scenario.tiles.putShort((LAST_TILE_INDEX + 1) * Short.BYTES, (short) OUTSIDE_MAP_TILE);

        TilesSampleDefinition definition = new TilesSampleDefinition();
        Sampler<TilesSample> sampler = definition.createSampler(new CaptureContext(scenario.memory, MODULE_BASE, CONTEXT_ADDRESS));
        TilesSample sample = sampler.sample();

        scenario.tiles.putShort(0, (short) CHANGED_TILE);

        TilesSample decoded = SampleRoundTripCheck.readAfterWrite(definition, sample);

        Assertions.assertEquals(MAP_WIDTH, decoded.getWidth());
        Assertions.assertEquals(MAP_HEIGHT, decoded.getHeight());
        Assertions.assertEquals(MAP_WIDTH * MAP_HEIGHT, decoded.getTiles().length);
        Assertions.assertEquals(FIRST_TILE, Short.toUnsignedInt(decoded.getTiles()[0]));
        Assertions.assertEquals(MAXIMUM_TILE, Short.toUnsignedInt(decoded.getTiles()[LAST_TILE_INDEX]));
        Assertions.assertEquals(CHANGED_TILE, Short.toUnsignedInt(sampler.sample().getTiles()[0]));

        short[] detached = decoded.getTiles();

        detached[0] = 0;

        Assertions.assertEquals(FIRST_TILE, Short.toUnsignedInt(decoded.getTiles()[0]));
    }

    @Test
    void tiles_sampler_rejects_invalid_dimensions_and_a_missing_tile_array() {
        TileMemoryScenario scenario = new TileMemoryScenario();
        Sampler<TilesSample> sampler = new TilesSampleDefinition().createSampler(new CaptureContext(scenario.memory, MODULE_BASE, CONTEXT_ADDRESS));

        scenario.pointer.putLong(0, 0);
        Assertions.assertThrows(IOException.class, sampler::sample);

        scenario.pointer.putLong(0, TILES_ADDRESS);
        scenario.context.putShort(MAP_WIDTH_OFFSET, (short) 0);
        Assertions.assertThrows(IOException.class, sampler::sample);

        scenario.context.putShort(MAP_WIDTH_OFFSET, (short) OVERSIZED_DIMENSION);
        Assertions.assertThrows(IOException.class, sampler::sample);
    }

    @Test
    void tiles_sample_validates_dimensions_and_payload_lengths() throws IOException {
        TilesSampleDefinition definition = new TilesSampleDefinition();
        TilesSample sample = new TilesSample(MAP_WIDTH, MAP_HEIGHT, new short[MAP_WIDTH * MAP_HEIGHT]);
        ByteBuffer output = ByteBuffer.allocate(definition.getEncodedSize(sample));

        definition.getWriter().write(output, sample);

        byte[] canonical = output.array().clone();
        ByteBuffer truncated = ByteBuffer.wrap(canonical.clone());

        truncated.limit(truncated.limit() - 1);
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(truncated));

        ByteBuffer zeroWidth = ByteBuffer.wrap(canonical.clone()).order(ByteOrder.LITTLE_ENDIAN);

        zeroWidth.putShort(0, (short) 0);
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(zeroWidth));

        ByteBuffer widerMap = ByteBuffer.wrap(canonical.clone()).order(ByteOrder.LITTLE_ENDIAN);

        widerMap.putShort(0, (short) (MAP_WIDTH + 1));
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(widerMap));
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(ByteBuffer.allocate(SIZE_BYTES - 1)));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new TilesSample(MAP_WIDTH, MAP_HEIGHT, new short[MAP_WIDTH]));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new TilesSample(OVERSIZED_DIMENSION, 1, new short[OVERSIZED_DIMENSION]));
        Assertions.assertThrows(IOException.class,
                                () -> definition.getWriter().write(ByteBuffer.allocate(canonical.length).asReadOnlyBuffer(), sample));
    }
}
