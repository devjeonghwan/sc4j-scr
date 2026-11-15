package org.fogofwarlabs.sc4j.scr.game.terrain;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Checks foliage footprint marking, unsigned cell bounds, initialization, independent decoded definitions and the foliage of the installed
 * tilesets.
 */
final class FoliageTest {
    private static final int DEFINITION_SIZE = 60;
    private static final int FOOTPRINT_WIDTH_OFFSET = 0x1C;
    private static final int FOOTPRINT_HEIGHT_OFFSET = 0x20;
    private static final int CELL_COUNT = 65536;
    /**
     * The cell flags of a resource footprint.
     */
    private static final int RESOURCE_FOOTPRINT_FLAG = 4;

    private static final int WIDE_MAP_WIDTH = 128;
    private static final int NARROW_MAP_WIDTH = 4;
    private static final int MEDIUM_MAP_WIDTH = 64;

    /**
     * The first DWORD and the value step per byte offset of the filled definition input.
     */
    private static final int FILL_FIRST_VALUE = 0x12345678;
    private static final int FILL_STEP = 0x1020304;
    /**
     * The bits of a NaN footprint height.
     */
    private static final int NAN_FOOTPRINT_HEIGHT_BITS = 0x7FA12345;
    /**
     * The number of definitions of the mutated definition input and the seed of the state hashes compared across its mutation.
     */
    private static final int MUTATED_DEFINITION_COUNT = 2;
    private static final int STATE_HASH_SEED = 123;
    /**
     * The tilesets, the map width and the blocked foliage flag of the installed foliage cases.
     */
    private static final int TILESET_COUNT = 8;
    private static final int FOLIAGE_MAP_WIDTH = 128;
    private static final int BLOCKED_FOLIAGE_FLAG = 0x4;
    private static final int FOLIAGE_POPULATION_CASES = 32;

    @Test
    void mark_resource_footprint_should_use_zero_stride_on_cleared_grid() {
        byte[] definitions = new byte[DEFINITION_SIZE];

        ByteBuffer.wrap(definitions).order(ByteOrder.LITTLE_ENDIAN).putFloat(FOOTPRINT_WIDTH_OFFSET, 2).putFloat(FOOTPRINT_HEIGHT_OFFSET, 2);

        Foliage foliage = new Foliage(WIDE_MAP_WIDTH, definitions);

        foliage.markResourceFootprint(4, 3, 2, 1);

        for (int tileIndex = 0; tileIndex < CELL_COUNT; tileIndex++) {
            Assertions.assertEquals(tileIndex >= 388 && tileIndex <= 390 ? RESOURCE_FOOTPRINT_FLAG : 0, foliage.getFlags(tileIndex));
        }

        foliage.markResourceFootprint(5, 3, 1, 1);

        Assertions.assertEquals(RESOURCE_FOOTPRINT_FLAG, foliage.getFlags(389));
        Assertions.assertEquals(0, foliage.getFlags(517));
    }

    @Test
    void mark_resource_footprint_should_use_strict_float_extents_and_unsigned_cell_bounds() {
        byte[] definitions = new byte[DEFINITION_SIZE];
        ByteBuffer definition = ByteBuffer.wrap(definitions).order(ByteOrder.LITTLE_ENDIAN);

        definition.putFloat(FOOTPRINT_WIDTH_OFFSET, 1.25F).putFloat(FOOTPRINT_HEIGHT_OFFSET, 0.5F);

        Foliage foliage = new Foliage(NARROW_MAP_WIDTH, definitions);

        foliage.markResourceFootprint(0, 0, 1, 1);

        Assertions.assertEquals(RESOURCE_FOOTPRINT_FLAG, foliage.getFlags(0));
        Assertions.assertEquals(RESOURCE_FOOTPRINT_FLAG, foliage.getFlags(1));
        Assertions.assertEquals(0, foliage.getFlags(2));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> foliage.markResourceFootprint(-1, 0, 1, 1));

        definition.putFloat(FOOTPRINT_HEIGHT_OFFSET, Float.NaN);

        Foliage skipped = new Foliage(NARROW_MAP_WIDTH, definitions);

        skipped.markResourceFootprint(0, 0, 1, 1);

        Assertions.assertEquals(0, skipped.getFlags(0));

        skipped.markResourceFootprint(-1, -1, 0, 0);
    }

    @Test
    void finish_initialization_should_expire_initial_context() {
        Foliage foliage = new Foliage(WIDE_MAP_WIDTH, new byte[DEFINITION_SIZE]);

        Assertions.assertTrue(foliage.isInitializationActive());

        foliage.finishInitialization();

        Assertions.assertFalse(foliage.isInitializationActive());

        int finishedHash = foliage.computeStateHash(0);

        foliage.markResourceFootprint(0, 0, 0, 0);

        Assertions.assertEquals(finishedHash, foliage.computeStateHash(0));
    }

    @Test
    void decoded_definitions_ignore_later_source_mutation() {
        byte[] definitions = new byte[MUTATED_DEFINITION_COUNT * DEFINITION_SIZE];
        ByteBuffer source = ByteBuffer.wrap(definitions).order(ByteOrder.LITTLE_ENDIAN);

        for (int position = 0; position < definitions.length; position += Integer.BYTES) {
            source.putInt(position, FILL_FIRST_VALUE + position * FILL_STEP);
        }

        source.putFloat(FOOTPRINT_WIDTH_OFFSET, 1.25F);
        source.putInt(FOOTPRINT_HEIGHT_OFFSET, NAN_FOOTPRINT_HEIGHT_BITS);

        Foliage foliage = new Foliage(MEDIUM_MAP_WIDTH, definitions);
        int previousHash = foliage.computeStateHash(STATE_HASH_SEED);

        Arrays.fill(definitions, (byte) 0);
        foliage.markResourceFootprint(0, 0, 1, 1);

        Assertions.assertEquals(0, foliage.getFlags(0));
        Assertions.assertEquals(previousHash, foliage.computeStateHash(STATE_HASH_SEED));
    }

    @RequiresInstallation
    @Test
    void create_foliage_should_return_cleared_context_for_every_installed_tileset() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            for (int tileset = 0; tileset < TILESET_COUNT; tileset++) {
                Foliage foliage = gameData.createFoliage(FOLIAGE_MAP_WIDTH, tileset);

                Assertions.assertTrue(foliage.isInitializationActive());

                foliage.markResourceFootprint(4, 3, 2, 1);

                Assertions.assertEquals(BLOCKED_FOLIAGE_FLAG, foliage.getFlags(388));
                Assertions.assertEquals(BLOCKED_FOLIAGE_FLAG, foliage.getFlags(389));
                Assertions.assertEquals(BLOCKED_FOLIAGE_FLAG, foliage.getFlags(390));
                Assertions.assertEquals(0, foliage.getFlags(516));
            }
        }
    }

    @RequiresInstallation
    @Test
    void matches_native_foliage_population_cases() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            byte[] fixture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_FOLIAGE_POPULATION);
            ByteBuffer cases = ByteBuffer.wrap(fixture).order(ByteOrder.LITTLE_ENDIAN);
            int caseCount = cases.getInt();

            Assertions.assertEquals(FOLIAGE_POPULATION_CASES, caseCount);

            for (int caseIndex = 0; caseIndex < caseCount; caseIndex++) {
                int tileset = cases.getInt();
                int width = cases.getInt();
                int height = cases.getInt();
                int seed = cases.getInt();
                int[] tileIdentifiers = new int[width * height];

                for (int tileIndex = 0; tileIndex < tileIdentifiers.length; tileIndex++) {
                    tileIdentifiers[tileIndex] = Short.toUnsignedInt(cases.getShort());
                }

                Foliage foliage = gameData.createFoliage(width, tileset);

                Assertions.assertFalse(foliage.isGridPopulated());

                foliage.markResourceFootprint(1, 1, 2, 2);

                GameMap populationMap = new GameMap(width, height, tileset, tileIdentifiers);

                foliage.populate(populationMap, seed);

                Assertions.assertTrue(foliage.isGridPopulated());
                Assertions.assertFalse(foliage.hasRenderingState());

                for (int phase = 0; phase < 2; phase++) {
                    if (phase == 1) {
                        foliage.markResourceFootprint(2, 2, 2, 2);
                    }

                    for (int tileIndex = 0; tileIndex < tileIdentifiers.length; tileIndex++) {
                        Assertions.assertEquals(Short.toUnsignedInt(cases.getShort()), foliage.getIdentifier(tileIndex));
                        Assertions.assertEquals(Short.toUnsignedInt(cases.getShort()), foliage.getAnchor(tileIndex));
                        Assertions.assertEquals(Short.toUnsignedInt(cases.getShort()), foliage.getFlags(tileIndex));
                    }

                    for (int tileIndex = tileIdentifiers.length; tileIndex < Foliage.SAVED_CELL_COUNT; tileIndex++) {
                        Assertions.assertEquals(0, foliage.getIdentifier(tileIndex));
                        Assertions.assertEquals(0, foliage.getAnchor(tileIndex));
                        Assertions.assertEquals(0, foliage.getFlags(tileIndex));
                    }
                }

                Assertions.assertThrows(IllegalStateException.class, () -> foliage.populate(populationMap, seed));

                foliage.finishInitialization();

                int[] flagsBeforeLaterMark = new int[tileIdentifiers.length];

                for (int tileIndex = 0; tileIndex < tileIdentifiers.length; tileIndex++) {
                    flagsBeforeLaterMark[tileIndex] = foliage.getFlags(tileIndex);
                }

                foliage.markResourceFootprint(2, 2, 2, 2);

                for (int tileIndex = 0; tileIndex < tileIdentifiers.length; tileIndex++) {
                    Assertions.assertEquals(flagsBeforeLaterMark[tileIndex], foliage.getFlags(tileIndex));
                }
            }

            Assertions.assertEquals(0, cases.remaining());
        }
    }
}
