package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.creep.CreepField;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameCheats;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMapScenario;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Checks cache initialization, visibility reset and graphics cache updates across control masks.
 */
final class MapVisibilityTest {
    /**
     * The number of CV5 tile groups of the synthetic tileset.
     */
    private static final int GROUP_COUNT = 37;
    /**
     * The size in bytes of a CV5 tile group record.
     */
    private static final int GROUP_RECORD_SIZE = 52;
    /**
     * The offset of the flags WORD in a CV5 tile group record.
     */
    private static final int GROUP_FLAGS_OFFSET = 2;
    /**
     * The step of the synthetic group flags from one group to the next.
     */
    private static final int GROUP_FLAGS_STEP = 731;
    /**
     * The group flags step of the tileset without group flags.
     */
    private static final int NO_GROUP_FLAGS_STEP = 0;
    /**
     * The offset of the megatile references in a CV5 tile group record.
     */
    private static final int GROUP_MEGATILES_OFFSET = 20;
    /**
     * The number of tiles, and megatile references, of a CV5 tile group.
     */
    private static final int GROUP_TILE_COUNT = 16;
    /**
     * The step of the synthetic megatile references from one group to the next.
     */
    private static final int GROUP_MEGATILE_STEP = 17;
    /**
     * The number of megatiles of the synthetic tileset.
     */
    private static final int MEGATILE_COUNT = 64;
    /**
     * The size in bytes of the VF4 minitile flags of a megatile.
     */
    private static final int MEGATILE_FLAGS_SIZE = 32;
    /**
     * The high bit of a synthetic megatile reference.
     */
    private static final int MEGATILE_HIGH_BIT = 32768;
    /**
     * The period of the megatile references and tile identifiers whose high bit is set.
     */
    private static final int HIGH_BIT_PERIOD = 3;
    /**
     * The VF4 flags of every minitile of the synthetic tileset.
     */
    private static final short MINITILE_FLAGS = 1;
    /**
     * The number of cases of each test.
     */
    private static final int CASE_COUNT = 512;
    /**
     * The width and height of a case map in tiles.
     */
    private static final int MAP_SIZE = 16;
    /**
     * The number of tiles of a case map.
     */
    private static final int TILE_COUNT = MAP_SIZE * MAP_SIZE;
    /**
     * The tileset index of a case map.
     */
    private static final int TILESET_INDEX = 0;
    /**
     * The step of a tile's group from one case to the next.
     */
    private static final int TILE_GROUP_CASE_STEP = 13;
    /**
     * The step of a tile's group from one tile to the next.
     */
    private static final int TILE_GROUP_TILE_STEP = 7;
    /**
     * The variant bits of a tile identifier.
     */
    private static final int TILE_VARIANT_MASK = 15;
    /**
     * The high bit of a tile identifier.
     */
    private static final int TILE_HIGH_BIT = 32768;
    /**
     * The step of the tile flags from one case to the next.
     */
    private static final int FLAGS_CASE_STEP = 0xA1B2C3D5;
    /**
     * The step of the tile flags from one tile to the next.
     */
    private static final int FLAGS_TILE_STEP = 0x1020317;
    /**
     * The number of shared mode byte values the visibility reset cases cycle through.
     */
    private static final int SHARED_MODE_COUNT = 3;
    /**
     * The cheat flags the visibility reset cases cycle through.
     */
    private static final int @NotNull [] CHEAT_CASES = {0, GameCheats.BLACK_SHEEP_WALL, GameCheats.WAR_AINT_WHAT_IT_USED_TO_BE,
                                                        GameCheats.BLACK_SHEEP_WALL | GameCheats.WAR_AINT_WHAT_IT_USED_TO_BE, -1};
    /**
     * The mask of the display BYTE control.
     */
    private static final int BYTE_MASK = 255;
    /**
     * The graphics refresh control every case starts with.
     */
    private static final int GRAPHICS_DIRTY_CONTROL = 7;
    /**
     * The display refresh control every case starts with.
     */
    private static final int DISPLAY_DIRTY_CONTROL = 99;
    /**
     * The number of controls of a map visibility.
     */
    private static final int CONTROL_COUNT = 5;
    /**
     * The index of the graphics refresh control in the control snapshot.
     */
    private static final int GRAPHICS_DIRTY_INDEX = 3;
    /**
     * The index of the display refresh control in the control snapshot.
     */
    private static final int DISPLAY_DIRTY_INDEX = 4;
    /**
     * The number of values each graphics cache case writes after the tiles.
     */
    private static final int TRAILING_VALUE_COUNT = 3;
    /**
     * The size in bytes of the output of one visibility reset case: the controls and the tile flags.
     */
    private static final int RESET_CASE_SIZE = CONTROL_COUNT * Integer.BYTES + TILE_COUNT * Integer.BYTES;
    /**
     * The size in bytes of the output of one graphics cache case: the cache, edge and flags of every tile and the trailing values.
     */
    private static final int CACHE_CASE_SIZE = TILE_COUNT * (Short.BYTES + Byte.BYTES + Integer.BYTES) + TRAILING_VALUE_COUNT * Integer.BYTES;
    /**
     * The step of an overlay tile identifier from one tile to the next.
     */
    private static final int OVERLAY_TILE_STEP = 19;
    /**
     * The mask of an overlay tile identifier WORD.
     */
    private static final int WORD_MASK = 65535;
    /**
     * The period of the tiles whose graphics cache already holds their megatile.
     */
    private static final int CACHE_MATCH_PERIOD = 3;
    /**
     * The megatile bits of a graphics cache WORD.
     */
    private static final int CACHE_MEGATILE_MASK = 32767;
    /**
     * The period of the tiles whose graphics cache has its refresh flag set.
     */
    private static final int CACHE_REFRESH_PERIOD = 5;
    /**
     * The period of the cases that play a replay.
     */
    private static final int REPLAY_CASE_PERIOD = 2;
    /**
     * The replay vision masks the graphics cache cases cycle through.
     */
    private static final int @NotNull [] REPLAY_MASKS = {0, 1, 128, 255};
    /**
     * The comparison masks the graphics cache cases cycle through.
     */
    private static final int @NotNull [] COMPARISON_MASKS = {0, 1, 128, Integer.MIN_VALUE, -1};
    /**
     * The horizontal viewport tile WORDs the graphics cache cases cycle through.
     */
    private static final int @NotNull [] VIEWPORT_HORIZONTALS = {0, 3, 15, 65535};
    /**
     * The vertical viewport tile WORDs the graphics cache cases cycle through.
     */
    private static final int @NotNull [] VIEWPORT_VERTICALS = {0, 2, 15, 65535};
    /**
     * The viewport widths in pixels the graphics cache cases cycle through.
     */
    private static final int @NotNull [] VIEWPORT_WIDTHS = {0, 1, 31, 32, 480, -32, -64, Integer.MAX_VALUE};
    /**
     * The viewport heights in pixels the graphics cache cases cycle through.
     */
    private static final int @NotNull [] VIEWPORT_HEIGHTS = {0, 1, 31, 32, 480, -32, -64, Integer.MIN_VALUE};
    /**
     * The seed of the game random generator.
     */
    private static final int RANDOM_SEED = 0;
    /**
     * The C runtime random state of the creep field.
     */
    private static final int RUNTIME_RANDOM_STATE = 0;
    /**
     * The SHA-256 digest of the created graphics caches of every case.
     */
    private static final @NotNull String GRAPHICS_CACHE_HASH = "81525ba5342a6ce481889d96ad5d70e3afa0dba09b17411b03475db43cd0b5d2";
    /**
     * The SHA-256 digest of the visibility reset outputs of every case.
     */
    private static final @NotNull String VISIBILITY_RESET_HASH = "2325286d2ee1c897fb9ff445d0822f94848a62c7a4a2c47fcb0ee9923e60b92c";
    /**
     * The SHA-256 digest of the graphics cache update outputs of every case.
     */
    private static final @NotNull String CACHE_UPDATE_HASH = "4d4a61dbffbfe87c32d7a6d7917f143f66298151f8db6e1c589684640206d2c3";
    /**
     * The digest algorithm of the case output digests.
     */
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";

    /**
     * Creates a {@link TerrainTileset} instance of the synthetic tileset: generated megatile references, every third one with its high bit, and the
     * same VF4 flags for every minitile.
     *
     * @param groupFlagsStep the step of the group flags from one group to the next, {@code 0} for groups without flags
     * @return the created {@link TerrainTileset} instance
     * @throws IOException if the generated records cannot be parsed
     */
    private static @NotNull TerrainTileset createTerrain(int groupFlagsStep) throws IOException {
        ByteBuffer groups = ByteBuffer.allocate(GROUP_COUNT * GROUP_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer miniatureFlags = ByteBuffer.allocate(MEGATILE_COUNT * MEGATILE_FLAGS_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int groupIndex = 0; groupIndex < GROUP_COUNT; groupIndex++) {
            groups.putShort(groupIndex * GROUP_RECORD_SIZE + GROUP_FLAGS_OFFSET, (short) (groupIndex * groupFlagsStep));

            for (int variant = 0; variant < GROUP_TILE_COUNT; variant++) {
                int value = (groupIndex * GROUP_MEGATILE_STEP + variant) % MEGATILE_COUNT;

                if ((groupIndex + variant) % HIGH_BIT_PERIOD == 0) {
                    value |= MEGATILE_HIGH_BIT;
                }

                groups.putShort(groupIndex * GROUP_RECORD_SIZE + GROUP_MEGATILES_OFFSET + variant * Short.BYTES, (short) value);
            }
        }

        while (miniatureFlags.hasRemaining()) {
            miniatureFlags.putShort(MINITILE_FLAGS);
        }

        return TerrainTileset.parse(groups.array(), miniatureFlags.array());
    }

    /**
     * Calculates the synthetic tile identifier of a case tile: a generated group and variant, every third one with its high bit.
     *
     * @param caseIndex the case index
     * @param tileIndex the row-major tile index
     * @return the tile identifier
     */
    private static int calculateTileIdentifier(int caseIndex, int tileIndex) {
        int tileIdentifier = ((caseIndex * TILE_GROUP_CASE_STEP + tileIndex * TILE_GROUP_TILE_STEP) % GROUP_COUNT) * GROUP_TILE_COUNT +
                             ((tileIndex + caseIndex) & TILE_VARIANT_MASK);

        if ((caseIndex + tileIndex) % HIGH_BIT_PERIOD == 0) {
            tileIdentifier |= TILE_HIGH_BIT;
        }

        return tileIdentifier;
    }


    @Test
    void matches_native_graphics_cache_and_visibility_reset_cases() throws Exception {
        TerrainTileset terrain = createTerrain(GROUP_FLAGS_STEP);
        MessageDigest cacheDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        MessageDigest visibilityDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        ByteBuffer cacheOutput = ByteBuffer.allocate(TILE_COUNT * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer visibilityOutput = ByteBuffer.allocate(RESET_CASE_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int caseIndex = 0; caseIndex < CASE_COUNT; caseIndex++) {
            int[] tiles = new int[TILE_COUNT];

            for (int tileIndex = 0; tileIndex < tiles.length; tileIndex++) {
                tiles[tileIndex] = calculateTileIdentifier(caseIndex, tileIndex);
            }

            GameMap map = new GameMap(MAP_SIZE, MAP_SIZE, TILESET_INDEX, tiles, new int[TILE_COUNT]);

            cacheOutput.clear();

            for (int value : MapVisibility.createGraphicsCache(map, terrain)) {
                cacheOutput.putShort((short) value);
            }

            cacheDigest.update(cacheOutput.array());

            int[] controls = {
                    caseIndex % SHARED_MODE_COUNT,
                    CHEAT_CASES[caseIndex % CHEAT_CASES.length],
                    caseIndex & BYTE_MASK,
                    GRAPHICS_DIRTY_CONTROL,
                    DISPLAY_DIRTY_CONTROL
            };
            MapVisibility visibility = new MapVisibility(terrain, controls);

            for (int tileIndex = 0; tileIndex < tiles.length; tileIndex++) {
                GameMapScenario.setFlags(map, tileIndex, caseIndex * FLAGS_CASE_STEP + tileIndex * FLAGS_TILE_STEP);
            }

            visibility.resetVisibility(map);

            visibilityOutput.clear();

            for (int value : visibility.getControlSnapshot()) {
                visibilityOutput.putInt(value);
            }

            for (int tileIndex = 0; tileIndex < map.getTileFlagCount(); tileIndex++) {
                visibilityOutput.putInt(map.getTileFlags(tileIndex));
            }

            visibilityDigest.update(visibilityOutput.array());
        }

        Assertions.assertEquals(GRAPHICS_CACHE_HASH, HexFormat.of().formatHex(cacheDigest.digest()));
        Assertions.assertEquals(VISIBILITY_RESET_HASH, HexFormat.of().formatHex(visibilityDigest.digest()));
    }

    @RequiresInstallation
    @Test
    void matches_native_update_graphics_cache_cases() throws Exception {
        TerrainTileset terrain = createTerrain(NO_GROUP_FLAGS_STEP);
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        ByteBuffer output = ByteBuffer.allocate(CACHE_CASE_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        byte[] nativeOutput = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_MAP_CACHE_UPDATE);

        Assertions.assertEquals(CASE_COUNT * output.capacity(), nativeOutput.length);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            for (int caseIndex = 0; caseIndex < CASE_COUNT; caseIndex++) {
                int[] tiles = new int[TILE_COUNT];
                int[] overlay = new int[TILE_COUNT];
                int[] flags = new int[TILE_COUNT];
                int[] cache = new int[TILE_COUNT];

                for (int tileIndex = 0; tileIndex < tiles.length; tileIndex++) {
                    tiles[tileIndex]   = calculateTileIdentifier(caseIndex, tileIndex);
                    overlay[tileIndex] = (tileIndex * OVERLAY_TILE_STEP + caseIndex) & WORD_MASK;
                    cache[tileIndex]   = tileIndex % CACHE_MATCH_PERIOD == 0
                                         ? terrain.getMegatileIdentifier(tiles[tileIndex])
                                         : (tileIndex + caseIndex) & CACHE_MEGATILE_MASK;

                    if (tileIndex % CACHE_REFRESH_PERIOD == 0) {
                        cache[tileIndex] |= CreepField.GRAPHICS_REFRESH_FLAG;
                    }

                    flags[tileIndex] = caseIndex * FLAGS_CASE_STEP + tileIndex * FLAGS_TILE_STEP;
                }

                boolean replay = caseIndex % REPLAY_CASE_PERIOD != 0;
                int replayMask = REPLAY_MASKS[caseIndex % REPLAY_MASKS.length];
                int comparisonMask = COMPARISON_MASKS[caseIndex % COMPARISON_MASKS.length];
                int horizontal = VIEWPORT_HORIZONTALS[caseIndex % VIEWPORT_HORIZONTALS.length];
                int vertical = VIEWPORT_VERTICALS[caseIndex / VIEWPORT_HORIZONTALS.length % VIEWPORT_VERTICALS.length];
                int width = VIEWPORT_WIDTHS[caseIndex % VIEWPORT_WIDTHS.length];
                int height = VIEWPORT_HEIGHTS[caseIndex / VIEWPORT_WIDTHS.length % VIEWPORT_HEIGHTS.length];
                GameMap map = new GameMap(MAP_SIZE, MAP_SIZE, TILESET_INDEX, tiles, flags);

                GameMapScenario.setOverlays(map, overlay);

                Game game = Game.create(gameData, map, new LcgRandom(RANDOM_SEED));
                CreepField creep = new CreepField(map, terrain, cache);

                game.getRuntimeRandom().seed(RUNTIME_RANDOM_STATE);
                game.setReplayPlayback(replay);
                game.getLocalVision().acceptDialog(replayMask, false);
                game.getLocalVision().setComparisonMask(comparisonMask);
                game.setCreepField(creep);

                MapVisibility visibility = new MapVisibility(terrain, new int[]{0, 0, 0, GRAPHICS_DIRTY_CONTROL, DISPLAY_DIRTY_CONTROL});

                visibility.updateGraphicsCache(game, horizontal, vertical, width, height);

                for (int tileIndex = 0; tileIndex < map.getTileCount(); tileIndex++) {
                    Assertions.assertEquals(tiles[tileIndex], map.getTileIdentifier(tileIndex));
                    Assertions.assertEquals(overlay[tileIndex], map.getOverlayTileIdentifier(tileIndex));
                }

                output.clear();

                for (int tileIndex = 0; tileIndex < tiles.length; tileIndex++) {
                    output.putShort((short) creep.getGraphicsCache(tileIndex));
                }

                for (int tileIndex = 0; tileIndex < tiles.length; tileIndex++) {
                    output.put((byte) creep.getEdgeIdentifier(tileIndex));
                }

                for (int tileIndex = 0; tileIndex < map.getTileFlagCount(); tileIndex++) {
                    output.putInt(map.getTileFlags(tileIndex));
                }

                output.putInt(visibility.getControlSnapshot()[GRAPHICS_DIRTY_INDEX]);
                output.putInt(visibility.getControlSnapshot()[DISPLAY_DIRTY_INDEX]);
                output.putInt(creep.hasPendingGraphicsInvalidation() ? 1 : 0);

                digest.update(output.array());

                Assertions.assertArrayEquals(Arrays.copyOfRange(nativeOutput, caseIndex * output.capacity(), (caseIndex + 1) * output.capacity()),
                                             output.array(), "Native cache case " + caseIndex);
                Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
                Assertions.assertEquals(0, game.getRuntimeRandom().getDrawCount());
            }
        }

        Assertions.assertEquals(CACHE_UPDATE_HASH, HexFormat.of().formatHex(digest.digest()));
    }
}
