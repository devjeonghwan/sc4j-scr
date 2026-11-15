package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.presentation.LocalSelection;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Checks fog rectangles, explored visibility, placement-box visibility, position-tile visibility and the visibility loss of a selected unit.
 */
final class SpriteVisibilityTest {
    /**
     * The horizontal pixel position of the created unit.
     */
    private static final int UNIT_X = 3560;
    /**
     * The vertical pixel position of the created unit.
     */
    private static final int UNIT_Y = 2160;
    /**
     * The comparison mask of a player slot that sees no tile of the scenario's map.
     */
    private static final int UNSEEING_PLAYER_MASK = 0x80;
    /**
     * The number of fog rectangle cases.
     */
    private static final int RECTANGLE_CASE_COUNT = 512;
    /**
     * The width and height of a fog rectangle case map in tiles.
     */
    private static final int RECTANGLE_MAP_SIZE = 16;
    /**
     * The number of tiles of a fog rectangle case map.
     */
    private static final int RECTANGLE_TILE_COUNT = RECTANGLE_MAP_SIZE * RECTANGLE_MAP_SIZE;
    /**
     * The tileset index of every case map.
     */
    private static final int TILESET_INDEX = 0;
    /**
     * The step of the tile flags from one case to the next.
     */
    private static final int FLAGS_CASE_STEP = 0xA1B2C3D5;
    /**
     * The step of the tile flags from one tile to the next.
     */
    private static final int FLAGS_TILE_STEP = 0x1020317;
    /**
     * The period of the fog rectangle cases that play a replay.
     */
    private static final int REPLAY_CASE_PERIOD = 2;
    /**
     * The period of the fog rectangle cases that reveal the map.
     */
    private static final int REVEAL_CASE_PERIOD = 7;
    /**
     * The replay vision masks the fog rectangle cases cycle through.
     */
    private static final int @NotNull [] REPLAY_MASKS = {0, 1, 128, 255};
    /**
     * The comparison masks the fog rectangle cases cycle through.
     */
    private static final int @NotNull [] COMPARISON_MASKS = {0, 1, 128, Integer.MIN_VALUE, -1};
    /**
     * The number of first columns, and of first rows, the fog rectangle cases cycle through.
     */
    private static final int RECTANGLE_ORIGIN_COUNT = 13;
    /**
     * The number of column counts, and of row counts, the fog rectangle cases cycle through, starting at {@code -1}.
     */
    private static final int RECTANGLE_SIZE_COUNT = 5;
    /**
     * The storage type of the explored-visibility sprite.
     */
    private static final int EXPLORED_SPRITE_TYPE = 16;
    /**
     * The elevation level of the explored-visibility sprite.
     */
    private static final int EXPLORED_SPRITE_ELEVATION = 4;
    /**
     * The storage type of the images of the explored and placement-box sprites.
     */
    private static final int IMAGE_TYPE = 0;
    /**
     * The pool index of the placement-box unit.
     */
    private static final int UNIT_POOL_INDEX = 1;
    /**
     * The capacity of the sprite pool of the placement-box sprite.
     */
    private static final int SPRITE_POOL_CAPACITY = 1;
    /**
     * The width in tiles of the explored and placement-box case maps.
     */
    private static final int BOX_MAP_WIDTH = 12;
    /**
     * The height in tiles of the explored and placement-box case maps.
     */
    private static final int BOX_MAP_HEIGHT = 9;
    /**
     * The number of tiles of the explored and placement-box case maps.
     */
    private static final int BOX_MAP_TILE_COUNT = BOX_MAP_WIDTH * BOX_MAP_HEIGHT;
    /**
     * The horizontal and vertical sprite positions of the explored and placement-box cases.
     */
    private static final int @NotNull [] COORDINATES = {-65, -32, -1, 0, 16, 31, 32, 383, 416, 32767};
    /**
     * The box widths of the explored and placement-box cases.
     */
    private static final int @NotNull [] BOX_WIDTHS = {0, 1, 31, 32, 64};
    /**
     * The box heights of the explored and placement-box cases.
     */
    private static final int @NotNull [] BOX_HEIGHTS = {0, 32, 64};
    /**
     * The visibilities the sprite of an explored or placement-box case starts with.
     */
    private static final int @NotNull [] OLD_VISIBILITIES = {0, 1, Sprite.VISIBLE_TO_ALL_PLAYERS};
    /**
     * The comparison masks of the explored and placement-box cases.
     */
    private static final int @NotNull [] BOX_COMPARISON_MASKS = {0, 1, Sprite.VISIBLE_TO_ALL_PLAYERS, 0x80000000};
    /**
     * The flags of the second image of the explored and placement-box sprites.
     */
    private static final int SECOND_IMAGE_FLAGS = 0x8000;
    /**
     * The size in bytes of the output of one explored or placement-box case.
     */
    private static final int VECTOR_SIZE = 4 * Integer.BYTES;
    /**
     * The output offset of the visibility loss.
     */
    private static final int LOST_VISIBILITY_OFFSET = 0;
    /**
     * The output offset of the sprite visibility.
     */
    private static final int VISIBILITY_OFFSET = 4;
    /**
     * The output offset of the first image's flags.
     */
    private static final int FIRST_IMAGE_FLAGS_OFFSET = 8;
    /**
     * The output offset of the second image's flags.
     */
    private static final int SECOND_IMAGE_FLAGS_OFFSET = 12;
    /**
     * The number of tile patterns of the explored cases.
     */
    private static final int EXPLORED_PATTERN_COUNT = 8;
    /**
     * The period of the explored patterns whose tiles no player explored.
     */
    private static final int UNEXPLORED_PATTERN_PERIOD = 2;
    /**
     * The tile flags of the explored patterns whose tiles no player explored.
     */
    private static final int UNEXPLORED_TILE_FLAGS = 0xAB00FF73;
    /**
     * The tile flags of the other explored patterns before their explored byte.
     */
    private static final int EXPLORED_TILE_FLAGS = 0xAB000073;
    /**
     * The pattern bits of the explored patterns with full visibility.
     */
    private static final int FULL_VISIBILITY_PATTERN_BITS = 6;
    /**
     * The number of tile patterns of the placement-box cases.
     */
    private static final int PLACEMENT_PATTERN_COUNT = 2;
    /**
     * The tile flags of the first placement-box pattern, whose tiles no player sees.
     */
    private static final int UNSEEN_TILE_FLAGS = 0xAB0000FF;
    /**
     * The tile flags of the second placement-box pattern before its visibility byte.
     */
    private static final int SEEN_TILE_FLAGS = 0xAB000000;
    /**
     * The step of a generated visibility byte from one tile to the next.
     */
    private static final int VISIBILITY_TILE_STEP = 37;
    /**
     * The offset of the generated visibility bytes.
     */
    private static final int VISIBILITY_TILE_OFFSET = 53;
    /**
     * The mask of a generated visibility byte.
     */
    private static final int BYTE_MASK = 255;
    /**
     * The number of explored cases.
     */
    private static final int EXPLORED_CASE_COUNT = 144000;
    /**
     * The number of placement-box cases.
     */
    private static final int PLACEMENT_CASE_COUNT = 36000;
    /**
     * The placement-box width and height of the early-return cases.
     */
    private static final int EARLY_BOX_SIZE = 32;
    /**
     * The width and height in tiles of the single-tile map of the early-return cases.
     */
    private static final int SINGLE_TILE_MAP_SIZE = 1;
    /**
     * The number of tiles of the single-tile map.
     */
    private static final int SINGLE_TILE_COUNT = SINGLE_TILE_MAP_SIZE * SINGLE_TILE_MAP_SIZE;
    /**
     * The comparison mask of the first player.
     */
    private static final int FIRST_PLAYER_MASK = 1;
    /**
     * The comparison mask without a player.
     */
    private static final int NO_PLAYER_MASK = 0;
    /**
     * The visibility the early-return sprite starts with and keeps.
     */
    private static final int KEPT_VISIBILITY = 7;
    /**
     * A horizontal position right of the single-tile map.
     */
    private static final int OFF_MAP_X = 128;
    /**
     * The players that see the tile under the position-tile sprite.
     */
    private static final int SEEING_PLAYERS = 0b101;
    /**
     * The width in tiles of the position-tile map.
     */
    private static final int POSITION_MAP_WIDTH = 4;
    /**
     * The height in tiles of the position-tile map.
     */
    private static final int POSITION_MAP_HEIGHT = 3;
    /**
     * The number of tiles of the position-tile map.
     */
    private static final int POSITION_MAP_TILE_COUNT = POSITION_MAP_WIDTH * POSITION_MAP_HEIGHT;
    /**
     * The storage type of the position-tile sprite.
     */
    private static final int POSITION_SPRITE_TYPE = 0;
    /**
     * The elevation level of the position-tile sprite.
     */
    private static final int POSITION_SPRITE_ELEVATION = 0;
    /**
     * The tile under the position-tile sprite: the second column of the second row.
     */
    private static final int SPRITE_TILE_INDEX = 5;
    /**
     * The horizontal position of the position-tile sprite, inside the second column.
     */
    private static final int SPRITE_X = TerrainTileset.TILE_SIZE + 5;
    /**
     * The vertical position of the position-tile sprite, inside the second row.
     */
    private static final int SPRITE_Y = TerrainTileset.TILE_SIZE + 31;
    /**
     * The vertical WORD position thirty-three pixels above the map.
     */
    private static final int ABOVE_MAP_Y = 0xFFDF;
    /**
     * The sprite flag of a shown solid selection circle.
     */
    private static final int SELECTION_CIRCLE_FLAG = 0x01;
    /**
     * The SHA-256 digest of the results of every fog rectangle case.
     */
    private static final @NotNull String FOG_RECTANGLE_CASES_HASH = "270480642dd9708f36e341e8aa0a532c73b27d96c9ee500dc5f8ea1d9940d982";
    /**
     * The SHA-256 digest of the outputs of every explored case.
     */
    private static final @NotNull String EXPLORED_CASES_HASH = "e4d636e822e697d1792d7d18919684443e268368780cab0cae749b134ab5dfe7";
    /**
     * The SHA-256 digest of the outputs of every placement-box case.
     */
    private static final @NotNull String PLACEMENT_BOX_CASES_HASH = "ffbeeb61fd9ed6edbf3cea53ecea5df59eb57190f6d4ed30ccff5942d379bd02";
    /**
     * The digest algorithm of the case digests.
     */
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";

    /**
     * Records the units the simulation removes from the local selection.
     */
    private static final class RecordingLocalSelection implements LocalSelection {
        private @Nullable Unit removedUnit;

        /**
         * Creates a {@link RecordingLocalSelection} instance that has removed no unit.
         */
        private RecordingLocalSelection() {
            this.removedUnit = null;
        }

        /**
         * @param position the slot from {@code 0} through {@code 11}
         * @return {@code 0}, because the recording selection holds no selected units
         */
        @Override
        public int getSelectedPoolIndex(int position) {
            return 0;
        }

        /**
         * Accepts a raw slot write without storing it.
         *
         * @param position  the slot from {@code 0} through {@code 11}
         * @param poolIndex the pool index of the unit to store, or {@code 0} for an empty slot
         */
        @Override
        public void setSelectedPoolIndexRaw(int position, int poolIndex) {
        }

        /**
         * Ignores the second offspring of a hatched egg.
         *
         * @param egg    the unit that hatched (the first offspring keeps the egg's unit)
         * @param second the second offspring
         */
        @Override
        public void addHatchedUnit(@NotNull Unit egg, @NotNull Unit second) {
        }

        /**
         * Records the unit the simulation removes from the local selection.
         *
         * @param unit the unit
         */
        @Override
        public void removeUnit(@NotNull Unit unit) {
            this.removedUnit = unit;
        }
    }


    @Test
    @RequiresInstallation
    void update_should_remove_a_selected_unit_that_loses_visibility_from_the_local_selection() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(gameData);
            RecordingLocalSelection selection = new RecordingLocalSelection();

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Unit marine = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, UNIT_X, UNIT_Y, RunningGameScenario.COMPUTER);
                Sprite sprite = marine.getSpriteOrNull();

                Assertions.assertNotNull(sprite);

                game.getInterfaceLink().setLocalSelection(selection);
                sprite.setVisibilityFlags(Sprite.VISIBLE_TO_ALL_PLAYERS);
                sprite.setFlags(sprite.getFlags() | Sprite.FLAG_SELECTED | Sprite.FLAG_SELECTION_CIRCLE);
                game.getSpriteVisibility().update(marine, game.getMap(), UNSEEING_PLAYER_MASK);

                Assertions.assertSame(marine, selection.removedUnit);
                Assertions.assertEquals(0, sprite.getFlags() & SELECTION_CIRCLE_FLAG, "The unseen unit hides its solid circle.");
            });
        }
    }

    @Test
    void matches_native_fog_rectangle_query_cases() throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        ByteBuffer output = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        LocalVision vision = game.getLocalVision();

        for (int caseIndex = 0; caseIndex < RECTANGLE_CASE_COUNT; caseIndex++) {
            int[] flags = new int[RECTANGLE_TILE_COUNT];

            for (int tileIndex = 0; tileIndex < flags.length; tileIndex++) {
                flags[tileIndex] = caseIndex * FLAGS_CASE_STEP + tileIndex * FLAGS_TILE_STEP;
            }

            GameMap map = new GameMap(RECTANGLE_MAP_SIZE, RECTANGLE_MAP_SIZE, TILESET_INDEX, new int[RECTANGLE_TILE_COUNT], flags);

            // The odd cases play a replay with their replay vision mask, and those that are also a multiple of seven reveal the map.
            game.setReplayPlayback(caseIndex % REPLAY_CASE_PERIOD != 0);
            vision.acceptDialog(REPLAY_MASKS[caseIndex % REPLAY_MASKS.length], caseIndex % REVEAL_CASE_PERIOD == 0);
            vision.setComparisonMask(COMPARISON_MASKS[caseIndex % COMPARISON_MASKS.length]);

            boolean result = SpriteVisibility.isFogRectangleHidden(map,
                                                                   caseIndex % RECTANGLE_ORIGIN_COUNT,
                                                                   caseIndex / RECTANGLE_ORIGIN_COUNT % RECTANGLE_ORIGIN_COUNT,
                                                                   caseIndex % RECTANGLE_SIZE_COUNT - 1,
                                                                   caseIndex / RECTANGLE_SIZE_COUNT % RECTANGLE_SIZE_COUNT - 1,
                                                                   vision);

            output.putInt(0, result ? 1 : 0);

            digest.update(output.array());
        }

        Assertions.assertEquals(FOG_RECTANGLE_CASES_HASH, HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void matches_native_update_explored_cases() throws NoSuchAlgorithmException {
        Sprite sprite = SpriteScenario.createSprite(EXPLORED_SPRITE_TYPE, 0, 0, EXPLORED_SPRITE_ELEVATION);
        SpriteImage firstImage = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);
        SpriteImage secondImage = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);

        sprite.insertTailImage(firstImage);
        sprite.insertTailImage(secondImage);

        int[] tiles = new int[BOX_MAP_TILE_COUNT];
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        ByteBuffer vector = ByteBuffer.allocate(VECTOR_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        int cases = 0;

        for (int pattern = 0; pattern < EXPLORED_PATTERN_COUNT; pattern++) {
            for (int tileIndex = 0; tileIndex < tiles.length; tileIndex++) {
                tiles[tileIndex] = pattern % UNEXPLORED_PATTERN_PERIOD == 0
                                   ? UNEXPLORED_TILE_FLAGS
                                   : EXPLORED_TILE_FLAGS | (((tileIndex * VISIBILITY_TILE_STEP + VISIBILITY_TILE_OFFSET) & BYTE_MASK) << Byte.SIZE);
            }

            GameMap map = new GameMap(BOX_MAP_WIDTH, BOX_MAP_HEIGHT, TILESET_INDEX, new int[BOX_MAP_TILE_COUNT], tiles);

            for (int positionX : COORDINATES) {
                for (int positionY : COORDINATES) {
                    sprite.setX(positionX);
                    sprite.setY(positionY);

                    for (int boxWidth : BOX_WIDTHS) {
                        for (int boxHeight : BOX_HEIGHTS) {
                            sprite.setNativeDimensions(boxWidth, boxHeight);

                            for (int oldVisibility : OLD_VISIBILITIES) {
                                for (int mask : BOX_COMPARISON_MASKS) {
                                    sprite.setVisibilityFlags(oldVisibility);
                                    firstImage.setFlags(SpriteImage.FLAG_HAS_DIRECTIONAL_FRAMES);
                                    secondImage.setFlags(SECOND_IMAGE_FLAGS);

                                    boolean fullVisibility = (pattern & FULL_VISIBILITY_PATTERN_BITS) == FULL_VISIBILITY_PATTERN_BITS;
                                    boolean lostVisibility = SpriteVisibility.updateExplored(sprite, map, mask, fullVisibility);

                                    vector.putInt(LOST_VISIBILITY_OFFSET, lostVisibility ? 1 : 0);
                                    vector.putInt(VISIBILITY_OFFSET, sprite.getVisibilityFlags());
                                    vector.putInt(FIRST_IMAGE_FLAGS_OFFSET, firstImage.getFlags());
                                    vector.putInt(SECOND_IMAGE_FLAGS_OFFSET, secondImage.getFlags());

                                    digest.update(vector.array());

                                    cases++;
                                }
                            }
                        }
                    }
                }
            }
        }

        Assertions.assertEquals(EXPLORED_CASE_COUNT, cases);
        Assertions.assertEquals(EXPLORED_CASES_HASH, HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void matches_native_update_placement_box_cases() throws NoSuchAlgorithmException {
        Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);
        Sprite sprite = new SpritePool(SPRITE_POOL_CAPACITY).allocate();

        Assertions.assertNotNull(sprite);

        unit.setSprite(sprite);

        SpriteImage firstImage = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);
        SpriteImage secondImage = SpriteScenario.createImage(IMAGE_TYPE, 0, 0);

        sprite.insertTailImage(firstImage);
        sprite.insertTailImage(secondImage);

        int[] tiles = new int[BOX_MAP_TILE_COUNT];
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        ByteBuffer vector = ByteBuffer.allocate(VECTOR_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        int cases = 0;

        for (int pattern = 0; pattern < PLACEMENT_PATTERN_COUNT; pattern++) {
            for (int tileIndex = 0; tileIndex < tiles.length; tileIndex++) {
                tiles[tileIndex] = pattern == 0
                                   ? UNSEEN_TILE_FLAGS
                                   : SEEN_TILE_FLAGS | ((tileIndex * VISIBILITY_TILE_STEP + VISIBILITY_TILE_OFFSET) & BYTE_MASK);
            }

            GameMap map = new GameMap(BOX_MAP_WIDTH, BOX_MAP_HEIGHT, TILESET_INDEX, new int[BOX_MAP_TILE_COUNT], tiles);

            for (int positionX : COORDINATES) {
                for (int positionY : COORDINATES) {
                    sprite.setX(positionX);
                    sprite.setY(positionY);

                    for (int boxWidth : BOX_WIDTHS) {
                        for (int boxHeight : BOX_HEIGHTS) {
                            for (int oldVisibility : OLD_VISIBILITIES) {
                                for (int mask : BOX_COMPARISON_MASKS) {
                                    sprite.setVisibilityFlags(oldVisibility);
                                    firstImage.setFlags(SpriteImage.FLAG_HAS_DIRECTIONAL_FRAMES);
                                    secondImage.setFlags(SECOND_IMAGE_FLAGS);

                                    boolean lostVisibility = SpriteVisibility.updatePlacementBox(unit, boxWidth, boxHeight, map, mask);

                                    vector.putInt(LOST_VISIBILITY_OFFSET, lostVisibility ? 1 : 0);
                                    vector.putInt(VISIBILITY_OFFSET, sprite.getVisibilityFlags());
                                    vector.putInt(FIRST_IMAGE_FLAGS_OFFSET, firstImage.getFlags());
                                    vector.putInt(SECOND_IMAGE_FLAGS_OFFSET, secondImage.getFlags());

                                    digest.update(vector.array());

                                    cases++;
                                }
                            }
                        }
                    }
                }
            }
        }

        Assertions.assertEquals(PLACEMENT_CASE_COUNT, cases);
        Assertions.assertEquals(PLACEMENT_BOX_CASES_HASH, HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void update_placement_box_should_return_early_without_a_sprite_when_hidden_or_off_map() {
        Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);
        GameMap map = new GameMap(SINGLE_TILE_MAP_SIZE, SINGLE_TILE_MAP_SIZE, TILESET_INDEX, new int[SINGLE_TILE_COUNT], new int[SINGLE_TILE_COUNT]);

        Assertions.assertFalse(SpriteVisibility.updatePlacementBox(unit, EARLY_BOX_SIZE, EARLY_BOX_SIZE, map, FIRST_PLAYER_MASK));

        Sprite sprite = new SpritePool(SPRITE_POOL_CAPACITY).allocate();

        Assertions.assertNotNull(sprite);

        unit.setSprite(sprite);
        sprite.setFlags(Sprite.FLAG_HIDDEN);
        sprite.setVisibilityFlags(KEPT_VISIBILITY);

        Assertions.assertFalse(SpriteVisibility.updatePlacementBox(unit, EARLY_BOX_SIZE, EARLY_BOX_SIZE, map, FIRST_PLAYER_MASK));
        Assertions.assertEquals(KEPT_VISIBILITY, sprite.getVisibilityFlags());

        sprite.setFlags(0);
        sprite.setX(OFF_MAP_X);

        Assertions.assertTrue(SpriteVisibility.updatePlacementBox(unit, EARLY_BOX_SIZE, EARLY_BOX_SIZE, map, NO_PLAYER_MASK));
        Assertions.assertEquals(KEPT_VISIBILITY, sprite.getVisibilityFlags());
    }

    @Test
    void update_position_tile_should_apply_the_tile_under_the_sprite_and_hide_it_off_the_map_tiles() {
        int[] flags = new int[POSITION_MAP_TILE_COUNT];

        flags[SPRITE_TILE_INDEX] = ~SEEING_PLAYERS & MapTileFlags.NOT_VISIBLE;

        GameMap map = new GameMap(POSITION_MAP_WIDTH, POSITION_MAP_HEIGHT, TILESET_INDEX, new int[POSITION_MAP_TILE_COUNT], flags);
        Sprite sprite = SpriteScenario.createSprite(POSITION_SPRITE_TYPE, SPRITE_X, SPRITE_Y, POSITION_SPRITE_ELEVATION);

        SpriteVisibility.updatePositionTile(sprite, map, Sprite.VISIBLE_TO_ALL_PLAYERS);

        Assertions.assertEquals(SEEING_PLAYERS, sprite.getVisibilityFlags());

        sprite.setY(POSITION_MAP_HEIGHT * TerrainTileset.TILE_SIZE);
        SpriteVisibility.updatePositionTile(sprite, map, Sprite.VISIBLE_TO_ALL_PLAYERS);

        Assertions.assertEquals(0, sprite.getVisibilityFlags(), "No player sees a sprite below the map's last row.");

        sprite.setVisibilityFlags(SEEING_PLAYERS);
        sprite.setY(ABOVE_MAP_Y);
        SpriteVisibility.updatePositionTile(sprite, map, Sprite.VISIBLE_TO_ALL_PLAYERS);

        Assertions.assertEquals(0, sprite.getVisibilityFlags(), "No player sees a sprite above the map's first row.");

        sprite.setVisibilityFlags(SEEING_PLAYERS);
        sprite.setFlags(Sprite.FLAG_HIDDEN);
        sprite.setY(TerrainTileset.TILE_SIZE);
        SpriteVisibility.updatePositionTile(sprite, map, Sprite.VISIBLE_TO_ALL_PLAYERS);

        Assertions.assertEquals(SEEING_PLAYERS, sprite.getVisibilityFlags(), "A hidden sprite keeps its visibility.");
    }
}
