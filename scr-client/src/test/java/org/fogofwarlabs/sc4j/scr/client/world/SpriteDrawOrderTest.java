package org.fogofwarlabs.sc4j.scr.client.world;

import org.fogofwarlabs.sc4j.scr.client.ClientReplayScenario;
import org.fogofwarlabs.sc4j.scr.client.ClientSession;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.type.constant.SpriteIds;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.visibility.LocalVision;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Checks the sprite order key, the collected tile rows and sprites, and the order in which sprites are drawn.
 * <p>
 * The key test pins the packed key layout, which is the contract: pointer picking and selection replacement compare the packed values, so the layout
 * decides which sprite is drawn later, picked or kept.
 */
final class SpriteDrawOrderTest {
    private static final int MAP_HEIGHT = 256;
    private static final int SMALL_MAP_HEIGHT = 64;
    private static final int VIEW_HEIGHT = 480;
    private static final int TILE_SIZE = 32;
    private static final int GROUND_ELEVATION = 4;
    private static final int CONSTRUCTING_ELEVATION = 5;
    private static final int AIR_ELEVATION = 12;
    private static final int HIGH_ELEVATION = 20;
    private static final int WRAPPED_ELEVATION = 32;
    private static final int NEGATIVE_ELEVATION = -1;
    private static final int NEGATIVE_POSITION = 0x8000;
    private static final int POSITION_SHIFT = 13;
    private static final int FLAG_SHIFT = 5;
    private static final int RESERVED_SHIFT = 9;
    private static final int TURRET_FLAG = 0x10;
    private static final int ALL_PLAYERS_VISION = 0xFF;
    private static final int FIRST_PLAYER_VISION = 0x01;
    private static final int FOURTH_PLAYER_VISION = 0x08;
    private static final int FOURTH_PLAYER = 3;
    /**
     * A {@code sprites.dat} entry outside the ranges a replay always draws.
     */
    private static final int UNIT_SPRITE = SpriteIds.SCV;
    /**
     * The first {@code sprites.dat} entry of the low range a replay always draws.
     */
    private static final int FIRST_LOW_REPLAY_SPRITE = 1;
    /**
     * A {@code sprites.dat} entry inside the low range a replay always draws.
     */
    private static final int INSIDE_LOW_REPLAY_SPRITE = 5;
    /**
     * The last {@code sprites.dat} entry of the low range a replay always draws.
     */
    private static final int LAST_LOW_REPLAY_SPRITE = 0x80;
    /**
     * The first {@code sprites.dat} entry of the high range a replay always draws.
     */
    private static final int FIRST_HIGH_REPLAY_SPRITE = 0x183;
    /**
     * The last {@code sprites.dat} entry of the high range a replay always draws.
     */
    private static final int LAST_HIGH_REPLAY_SPRITE = 0x1DF;
    private static final int SMALL_POOL_CAPACITY = 4;
    private static final int ORDER_POOL_CAPACITY = 16;
    private static final int ROW_POOL_CAPACITY = 8;
    private static final int VISIBILITY_POOL_CAPACITY = 8;
    private static final int REPLAY_RANGE_POOL_CAPACITY = 16;
    private static final int GROWTH_SPRITE_COUNT = 600;

    /**
     * A Terran versus Terran replay whose first two Supply Depots are under construction at this frame.
     */
    private static final long CONSTRUCTION_FRAME = 1440;
    /**
     * The last frame checked: both Supply Depots are complete and their builders have walked off.
     */
    private static final long LAST_CHECKED_FRAME = 2030;

    /**
     * Creates the vision of a game view that shows the players of a comparison mask.
     *
     * @param mask the comparison mask
     * @return the vision of the game view
     */
    private static @NotNull LocalVision createGameVision(int mask) {
        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        game.getLocalVision().setComparisonMask(mask);

        return game.getLocalVision();
    }

    /**
     * Creates the vision of a replay view whose vision dialog accepted the first player and the reveal.
     *
     * @param revealed whether the entire map is revealed
     * @return the vision of the replay view
     */
    private static @NotNull LocalVision createReplayVision(boolean revealed) {
        Game game = Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        game.setReplayPlayback(true);
        game.getLocalVision().acceptDialog(FIRST_PLAYER_VISION, revealed);

        return game.getLocalVision();
    }

    private static @NotNull Sprite createLinkedSprite(@NotNull SpritePool pool, int row, int positionY, int elevationLevel, int flags) {
        Sprite sprite = pool.allocate();

        Assertions.assertNotNull(sprite);

        sprite.setY(positionY);
        sprite.setElevationLevel(elevationLevel);
        sprite.setFlags(flags);
        // Hiding a sprite clears its visibility.
        sprite.setVisibilityFlags((flags & Sprite.FLAG_HIDDEN) != 0 ? 0 : Sprite.VISIBLE_TO_ALL_PLAYERS);
        pool.attachToRow(sprite, row);

        return sprite;
    }

    private static @NotNull Sprite createOwnedSprite(@NotNull SpritePool pool, int spriteTypeId, int owner, int visibilityFlags) {
        Sprite sprite = pool.allocate();

        Assertions.assertNotNull(sprite);

        sprite.initializeNativeStorage(spriteTypeId, 0, 0, owner, true);
        sprite.setVisibilityFlags(visibilityFlags);
        pool.attachToRow(sprite, 0);

        return sprite;
    }

    private static boolean isCollected(@NotNull SpriteDrawOrder order, int count, @NotNull Sprite sprite) {
        return IntStream.range(0, count).anyMatch(index -> order.getPoolIndex(index) == sprite.getPoolIndex());
    }

    private static int findDrawPosition(@NotNull SpriteDrawOrder order, int count, @NotNull Sprite sprite) {
        for (int orderIndex = 0; orderIndex < count; orderIndex++) {
            if (order.getPoolIndex(orderIndex) == sprite.getPoolIndex()) {
                return orderIndex;
            }
        }

        Assertions.fail("Sprite " + sprite.getPoolIndex() + " is not collected.");

        return -1;
    }

    @Test
    void compute_key_should_pack_elevation_position_turret_flag_and_reserved_word() {
        int position = 100;
        int reservedWord = 7;
        SpritePool pool = new SpritePool(SMALL_POOL_CAPACITY);
        Sprite sprite = createLinkedSprite(pool, 3, position, GROUND_ELEVATION, Sprite.FLAG_TURRET);

        sprite.setRawDimensions(reservedWord, 0, 0);

        Assertions.assertEquals(((GROUND_ELEVATION << POSITION_SHIFT | position) << FLAG_SHIFT | TURRET_FLAG) << RESERVED_SHIFT | reservedWord,
                                SpriteDrawOrder.computeKey(sprite));

        sprite.setElevationLevel(CONSTRUCTING_ELEVATION);

        Assertions.assertEquals((CONSTRUCTING_ELEVATION << POSITION_SHIFT << FLAG_SHIFT | TURRET_FLAG) << RESERVED_SHIFT | reservedWord,
                                SpriteDrawOrder.computeKey(sprite),
                                "elevations above 4 ignore the vertical position");
    }

    @Test
    void collect_should_order_visible_sprites_by_elevation_position_turret_and_index() {
        SpritePool pool = new SpritePool(ORDER_POOL_CAPACITY);
        Sprite lowerGround = createLinkedSprite(pool, 3, 120, GROUND_ELEVATION, 0);
        Sprite upperGround = createLinkedSprite(pool, 1, 40, GROUND_ELEVATION, 0);
        Sprite air = createLinkedSprite(pool, 0, 10, AIR_ELEVATION, 0);
        Sprite projectile = createLinkedSprite(pool, 3, 127, CONSTRUCTING_ELEVATION, 0);
        Sprite turret = createLinkedSprite(pool, 1, 40, GROUND_ELEVATION, Sprite.FLAG_TURRET);
        Sprite hidden = createLinkedSprite(pool, 2, 70, GROUND_ELEVATION, Sprite.FLAG_HIDDEN);
        Sprite doodad = createLinkedSprite(pool, 1, 40, GROUND_ELEVATION, 0);
        Sprite unlinked = pool.allocate();

        Assertions.assertNotNull(unlinked);

        unlinked.setY(50);
        unlinked.setElevationLevel(GROUND_ELEVATION);

        SpriteDrawOrder order = new SpriteDrawOrder();

        Assertions.assertEquals(6, order.collect(pool, 0, VIEW_HEIGHT, MAP_HEIGHT, createGameVision(ALL_PLAYERS_VISION)));

        int firstTie = Math.min(upperGround.getPoolIndex(), doodad.getPoolIndex());
        int secondTie = Math.max(upperGround.getPoolIndex(), doodad.getPoolIndex());
        int[] expected = {firstTie, secondTie, turret.getPoolIndex(), lowerGround.getPoolIndex(), projectile.getPoolIndex(), air.getPoolIndex()};

        for (int orderIndex = 0; orderIndex < expected.length; orderIndex++) {
            Assertions.assertEquals(expected[orderIndex], order.getPoolIndex(orderIndex), "Draw position " + orderIndex);
        }

        Assertions.assertFalse(IntStream.range(0, 6).anyMatch(index -> order.getPoolIndex(index) == hidden.getPoolIndex()));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> order.getPoolIndex(6));
    }

    @Test
    void collect_should_wrap_elevation_to_five_bits() {
        SpritePool pool = new SpritePool(SMALL_POOL_CAPACITY);
        Sprite ground = createLinkedSprite(pool, 0, 10, GROUND_ELEVATION, 0);
        Sprite wrapped = createLinkedSprite(pool, 15, 500, WRAPPED_ELEVATION, 0);
        SpriteDrawOrder order = new SpriteDrawOrder();

        Assertions.assertEquals(2, order.collect(pool, 0, VIEW_HEIGHT, MAP_HEIGHT, createGameVision(ALL_PLAYERS_VISION)));
        Assertions.assertEquals(wrapped.getPoolIndex(), order.getPoolIndex(0), "elevation 32 sorts as elevation 0");
        Assertions.assertEquals(ground.getPoolIndex(), order.getPoolIndex(1));
    }

    @Test
    void collect_should_break_ties_by_reserved_word() {
        SpritePool pool = new SpritePool(SMALL_POOL_CAPACITY);
        Sprite first = createLinkedSprite(pool, 2, 70, GROUND_ELEVATION, 0);
        Sprite second = createLinkedSprite(pool, 2, 70, GROUND_ELEVATION, 0);
        Sprite lowerIndex = first.getPoolIndex() < second.getPoolIndex() ? first : second;
        Sprite higherIndex = lowerIndex == first ? second : first;
        SpriteDrawOrder order = new SpriteDrawOrder();

        lowerIndex.setRawDimensions(higherIndex.getPoolIndex(), 0, 0);
        higherIndex.setRawDimensions(lowerIndex.getPoolIndex(), 0, 0);

        Assertions.assertEquals(2, order.collect(pool, 0, VIEW_HEIGHT, MAP_HEIGHT, createGameVision(ALL_PLAYERS_VISION)));
        Assertions.assertEquals(higherIndex.getPoolIndex(), order.getPoolIndex(0), "the lower reserved WORD is drawn first");
        Assertions.assertEquals(lowerIndex.getPoolIndex(), order.getPoolIndex(1));
    }

    @Test
    void collect_should_sign_extend_vertical_position() {
        SpritePool pool = new SpritePool(SMALL_POOL_CAPACITY);
        Sprite negative = createLinkedSprite(pool, 0, NEGATIVE_POSITION, GROUND_ELEVATION, 0);
        Sprite high = createLinkedSprite(pool, 1, 40, HIGH_ELEVATION, 0);
        SpriteDrawOrder order = new SpriteDrawOrder();

        Assertions.assertEquals(2, order.collect(pool, 0, VIEW_HEIGHT, MAP_HEIGHT, createGameVision(ALL_PLAYERS_VISION)));
        Assertions.assertEquals(high.getPoolIndex(), order.getPoolIndex(0));
        Assertions.assertEquals(negative.getPoolIndex(), order.getPoolIndex(1), "a negative position fills the high key bits");
    }

    @Test
    void collect_should_ignore_position_of_negative_elevation() {
        SpritePool pool = new SpritePool(SMALL_POOL_CAPACITY);
        Sprite upper = createLinkedSprite(pool, 0, 10, NEGATIVE_ELEVATION, 0);
        Sprite lower = createLinkedSprite(pool, 15, 500, NEGATIVE_ELEVATION, 0);
        Sprite air = createLinkedSprite(pool, 1, 40, AIR_ELEVATION, 0);
        SpriteDrawOrder order = new SpriteDrawOrder();

        upper.setRawDimensions(2, 0, 0);
        lower.setRawDimensions(1, 0, 0);

        Assertions.assertEquals(3, order.collect(pool, 0, VIEW_HEIGHT, MAP_HEIGHT, createGameVision(ALL_PLAYERS_VISION)));
        Assertions.assertEquals(air.getPoolIndex(), order.getPoolIndex(0), "elevation -1 is stored as 255 and sorts above the air");
        Assertions.assertEquals(lower.getPoolIndex(), order.getPoolIndex(1), "elevation 255 ignores the position");
        Assertions.assertEquals(upper.getPoolIndex(), order.getPoolIndex(2));
    }

    @Test
    void collect_should_drain_equal_keys_in_heap_order() {
        SpritePool pool = new SpritePool(ORDER_POOL_CAPACITY);
        Sprite first = createLinkedSprite(pool, 1, 70, GROUND_ELEVATION, 0);
        Sprite second = createLinkedSprite(pool, 2, 70, GROUND_ELEVATION, 0);
        Sprite third = createLinkedSprite(pool, 3, 70, GROUND_ELEVATION, 0);
        SpriteDrawOrder order = new SpriteDrawOrder();

        first.setRawDimensions(0, 0, 0);
        second.setRawDimensions(0, 0, 0);
        third.setRawDimensions(0, 0, 0);

        Assertions.assertEquals(3, order.collect(pool, 0, VIEW_HEIGHT, MAP_HEIGHT, createGameVision(ALL_PLAYERS_VISION)));
        Assertions.assertEquals(second.getPoolIndex(), order.getPoolIndex(0));
        Assertions.assertEquals(third.getPoolIndex(), order.getPoolIndex(1));
        Assertions.assertEquals(first.getPoolIndex(), order.getPoolIndex(2));
    }

    @Test
    void collect_should_start_four_rows_above_the_camera_row() {
        SpritePool pool = new SpritePool(ROW_POOL_CAPACITY);
        // Row 5 is five rows above the camera row.
        createLinkedSprite(pool, 5, 5 * TILE_SIZE + 16, GROUND_ELEVATION, 0);

        Sprite firstRow = createLinkedSprite(pool, 6, 6 * TILE_SIZE + 16, GROUND_ELEVATION, 0);
        Sprite lastRow = createLinkedSprite(pool, 63, 63 * TILE_SIZE + 16, GROUND_ELEVATION, 0);
        SpriteDrawOrder order = new SpriteDrawOrder();

        Assertions.assertEquals(2, order.collect(pool, 10, VIEW_HEIGHT, SMALL_MAP_HEIGHT, createGameVision(ALL_PLAYERS_VISION)));
        Assertions.assertEquals(firstRow.getPoolIndex(), order.getPoolIndex(0));
        Assertions.assertEquals(lastRow.getPoolIndex(), order.getPoolIndex(1), "the window reaches the last map row");
    }

    @Test
    void collect_should_test_visibility_but_not_the_hidden_flag() {
        SpritePool pool = new SpritePool(VISIBILITY_POOL_CAPACITY);
        Sprite hidden = createOwnedSprite(pool, UNIT_SPRITE, FOURTH_PLAYER, FIRST_PLAYER_VISION);
        Sprite unseen = createOwnedSprite(pool, UNIT_SPRITE, FOURTH_PLAYER, FOURTH_PLAYER_VISION);
        Sprite seen = createOwnedSprite(pool, UNIT_SPRITE, FOURTH_PLAYER, FIRST_PLAYER_VISION | FOURTH_PLAYER_VISION);
        SpriteDrawOrder order = new SpriteDrawOrder();

        hidden.setFlags(Sprite.FLAG_HIDDEN);

        int count = order.collect(pool, 0, VIEW_HEIGHT, MAP_HEIGHT, createGameVision(FIRST_PLAYER_VISION));

        Assertions.assertEquals(2, count);
        Assertions.assertTrue(isCollected(order, count, hidden), "a hidden sprite the view sees is drawn");
        Assertions.assertFalse(isCollected(order, count, unseen));
        Assertions.assertTrue(isCollected(order, count, seen));
    }

    @Test
    void collect_should_draw_the_replay_sprite_ranges_whatever_their_visibility() {
        SpritePool pool = new SpritePool(REPLAY_RANGE_POOL_CAPACITY);
        Sprite[] drawn = {createOwnedSprite(pool, FIRST_LOW_REPLAY_SPRITE, FOURTH_PLAYER, 0),
                          createOwnedSprite(pool, INSIDE_LOW_REPLAY_SPRITE, FOURTH_PLAYER, FOURTH_PLAYER_VISION),
                          createOwnedSprite(pool, LAST_LOW_REPLAY_SPRITE, FOURTH_PLAYER, 0),
                          createOwnedSprite(pool, FIRST_HIGH_REPLAY_SPRITE, FOURTH_PLAYER, 0),
                          createOwnedSprite(pool, LAST_HIGH_REPLAY_SPRITE, FOURTH_PLAYER, 0)};
        Sprite[] skipped = {createOwnedSprite(pool, FIRST_LOW_REPLAY_SPRITE - 1, FOURTH_PLAYER, 0),
                            createOwnedSprite(pool, LAST_LOW_REPLAY_SPRITE + 1, FOURTH_PLAYER, 0),
                            createOwnedSprite(pool, FIRST_HIGH_REPLAY_SPRITE - 1, FOURTH_PLAYER, 0),
                            createOwnedSprite(pool, LAST_HIGH_REPLAY_SPRITE + 1, FOURTH_PLAYER, FOURTH_PLAYER_VISION)};
        SpriteDrawOrder order = new SpriteDrawOrder();

        int count = order.collect(pool, 0, VIEW_HEIGHT, MAP_HEIGHT, createReplayVision(false));

        Assertions.assertEquals(drawn.length, count);

        for (Sprite sprite : drawn) {
            Assertions.assertTrue(isCollected(order, count, sprite), "sprites.dat entry " + sprite.getSpriteTypeId());
        }

        for (Sprite sprite : skipped) {
            Assertions.assertFalse(isCollected(order, count, sprite), "sprites.dat entry " + sprite.getSpriteTypeId());
        }

        Assertions.assertEquals(0,
                                order.collect(pool, 0, VIEW_HEIGHT, MAP_HEIGHT, createGameVision(FIRST_PLAYER_VISION)),
                                "a game view draws only the sprites its vision sees");
    }

    @Test
    void collect_should_draw_every_neutral_sprite_while_the_replay_reveals_the_map() {
        SpritePool pool = new SpritePool(VISIBILITY_POOL_CAPACITY);
        Sprite neutral = createOwnedSprite(pool, UNIT_SPRITE, PlayerTable.NEUTRAL_PLAYER_ID, 0);
        Sprite player = createOwnedSprite(pool, UNIT_SPRITE, FOURTH_PLAYER, 0);
        SpriteDrawOrder order = new SpriteDrawOrder();

        Assertions.assertEquals(1, order.collect(pool, 0, VIEW_HEIGHT, MAP_HEIGHT, createReplayVision(true)));
        Assertions.assertEquals(neutral.getPoolIndex(), order.getPoolIndex(0));
        Assertions.assertFalse(isCollected(order, 1, player));
        Assertions.assertEquals(0, order.collect(pool, 0, VIEW_HEIGHT, MAP_HEIGHT, createReplayVision(false)));
    }

    @Test
    void collect_should_follow_row_relocation_and_grow_reusable_storage() {
        SpritePool pool = new SpritePool(GROWTH_SPRITE_COUNT);

        for (int index = 0; index < GROWTH_SPRITE_COUNT; index++) {
            createLinkedSprite(pool, index % SMALL_MAP_HEIGHT, (index % SMALL_MAP_HEIGHT) * TILE_SIZE + 5, GROUND_ELEVATION, 0);
        }

        SpriteDrawOrder order = new SpriteDrawOrder();

        Assertions.assertEquals(GROWTH_SPRITE_COUNT, order.collect(pool, 0, VIEW_HEIGHT, SMALL_MAP_HEIGHT, createGameVision(ALL_PLAYERS_VISION)));

        int previousKey = 0;

        for (int orderIndex = 0; orderIndex < GROWTH_SPRITE_COUNT; orderIndex++) {
            int key = SpriteDrawOrder.computeKey(pool.getSprite(order.getPoolIndex(orderIndex)));

            Assertions.assertTrue(Integer.compareUnsigned(key, previousKey) >= 0);

            previousKey = key;
        }

        Sprite moved = pool.getSprite(order.getPoolIndex(0));

        pool.relocate(moved, moved.getX(), 63 * TILE_SIZE + 30, SMALL_MAP_HEIGHT);
        order.collect(pool, 0, VIEW_HEIGHT, SMALL_MAP_HEIGHT, createGameVision(ALL_PLAYERS_VISION));

        Assertions.assertEquals(moved.getPoolIndex(),
                                order.getPoolIndex(GROWTH_SPRITE_COUNT - 1),
                                "A relocated sprite is ordered by its new position.");
    }

    @RequiresInstallation
    @Test
    void constructing_scv_is_drawn_after_its_building_and_by_position_after_completion() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(GameTestResources.REPLAY_TVT_PYTHON, CONSTRUCTION_FRAME)) {
            Map<Unit, Unit> builders = new HashMap<>();
            SpriteDrawOrder order = new SpriteDrawOrder();
            int constructingChecks = 0;
            int completedChecks = 0;

            do {
                Game game = session.getGame();
                UnitPool units = game.getUnitSystem().getUnitPool();

                Assertions.assertNotNull(units);

                for (int index = 1; index <= units.getCapacity(); index++) {
                    Unit unit = units.isAllocated(index) ? units.getUnit(index) : null;

                    if (unit != null && unit.getUnitId() == ScrUnitIds.TERRAN_SCV &&
                        unit.getPrimaryOrder() == ScrOrderIds.CONSTRUCTING_BUILDING && unit.getOrderTargetUnit() != null) {
                        builders.put(unit.getOrderTargetUnit(), unit);
                    }
                }

                SpritePool sprites = game.getSpriteSystem().getSpritePool();

                Assertions.assertNotNull(sprites);

                int count = order.collect(sprites, 0, VIEW_HEIGHT, game.getMap().getHeight(), game.getLocalVision());

                for (Map.Entry<Unit, Unit> entry : builders.entrySet()) {
                    Sprite building = entry.getKey().getSpriteOrNull();
                    Sprite builder = entry.getValue().getSpriteOrNull();

                    if (building == null || builder == null) {
                        continue;
                    }

                    boolean builderAfter = findDrawPosition(order, count, builder) > findDrawPosition(order, count, building);

                    if ((entry.getKey().getStatus() & UnitStatusFlags.COMPLETED) == 0) {
                        Assertions.assertEquals(CONSTRUCTING_ELEVATION, builder.getElevationLevel());
                        Assertions.assertTrue(builderAfter, "frame " + game.getCurrentFrame() + ": the constructing SCV is drawn after the building");

                        constructingChecks++;
                    } else if (builder.getElevationLevel() == building.getElevationLevel() && builder.getY() != building.getY()) {
                        Assertions.assertEquals(builder.getY() > building.getY(),
                                                builderAfter,
                                                "frame " + game.getCurrentFrame() + ": a finished SCV is drawn by its vertical position");

                        completedChecks++;
                    }
                }
            } while (session.getLastCompletedFrame() < LAST_CHECKED_FRAME && session.stepFrame(false));

            Assertions.assertTrue(constructingChecks > 0 && completedChecks > 0, "the replay constructs and completes Supply Depots");
        }
    }
}
