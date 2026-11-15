package org.fogofwarlabs.sc4j.scr.game.combat;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerUnitRecord;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.terrain.RegionPathSearch;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Checks the tile visibility tests of a unit's order target position and of a position, including positions off the map, the attack test of an
 * Arbiter with a computer record, nearest-query self exclusion and candidate evaluation, and the stopping distance of a unit whose squared speed
 * overflows.
 */
final class UnitTargetingTest {
    private static final int MAP_WIDTH = 4;
    private static final int MAP_HEIGHT = 3;
    private static final int TILE_COUNT = MAP_WIDTH * MAP_HEIGHT;

    /**
     * The tile player {@code 0} sees: column zero of row one.
     */
    private static final int VISIBLE_TILE = MAP_WIDTH;
    /**
     * The tile player {@code 0} sees left of {@link #VISIBLE_TILE}: the last column of row zero.
     */
    private static final int VISIBLE_PREVIOUS_TILE = MAP_WIDTH - 1;
    /**
     * The bit of player {@code 0} in the not visible and in the not explored tile flags.
     */
    private static final int FIRST_PLAYER_BIT = 1;
    /**
     * The flags of a tile player {@code 0} sees and explored, while every other player neither sees nor explored it.
     */
    private static final int SEEN_BY_FIRST_PLAYER = MapTileFlags.NOT_VISIBLE_OR_EXPLORED &
                                                    ~(FIRST_PLAYER_BIT | FIRST_PLAYER_BIT << MapTileFlags.NOT_EXPLORED_SHIFT);

    private static final int WORD_MINUS_ONE = 0xFFFF;
    private static final int WORD_MINUS_THIRTY_THREE = 0xFFDF;

    private static final int FIRST_PLAYER = 0;
    private static final int NEUTRAL_PLAYER = 11;

    /**
     * The position of the Arbiter in the running game's map.
     */
    private static final int ARBITER_X = 3600;
    private static final int ARBITER_Y = 2200;
    /**
     * The horizontal distance from the Arbiter to its target, within its weapon range.
     */
    private static final int TARGET_DISTANCE = 64;

    private static final int NEAREST_CREATION_X = 3560;
    private static final int NEAREST_CREATION_Y = 2160;
    private static final int NEAREST_CENTER_X = 3600;
    private static final int NEAREST_CENTER_Y = 2200;
    private static final int NEAREST_SEARCH_RADIUS = 256;
    private static final int NEAREST_CARDINAL_OFFSET = 64;
    private static final int NEAREST_DIAGONAL_OFFSET = 50;

    /**
     * A next speed whose square exceeds the signed DWORD range.
     */
    private static final int OVERFLOWING_SPEED = 50000;
    private static final int OVERFLOWING_ACCELERATION = 1;
    /**
     * The stopping distance at {@link #OVERFLOWING_SPEED}: the squared speed wrapped to a signed DWORD, divided by twice the acceleration.
     */
    private static final int OVERFLOWING_HALT_DISTANCE = -897483648;

    /**
     * Creates a {@link Game} instance whose map hides every tile from every player except {@link #VISIBLE_TILE} and {@link #VISIBLE_PREVIOUS_TILE},
     * which player {@code 0} sees.
     *
     * @return the created {@link Game} instance
     */
    private static @NotNull Game createGame() {
        int[] flags = new int[TILE_COUNT];

        Arrays.fill(flags, MapTileFlags.NOT_VISIBLE_OR_EXPLORED);
        flags[VISIBLE_TILE]          = SEEN_BY_FIRST_PLAYER;
        flags[VISIBLE_PREVIOUS_TILE] = SEEN_BY_FIRST_PLAYER;

        return Game.create(null, new GameMap(MAP_WIDTH, MAP_HEIGHT, 0, new int[TILE_COUNT], flags), new LcgRandom(1));
    }

    /**
     * Creates a {@link Unit} instance of a player without a target unit and with an order target position.
     *
     * @param owner     the player
     * @param positionX the horizontal order target WORD
     * @param positionY the vertical order target WORD
     * @return the created {@link Unit} instance
     */
    private static @NotNull Unit createUnit(int owner, int positionX, int positionY) {
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setOwnerIndex(owner);
        unit.setOrderTargetX(positionX);
        unit.setOrderTargetY(positionY);

        return unit;
    }


    @Test
    void can_see_target_should_read_the_order_target_tile_of_negative_and_past_edge_positions() {
        UnitTargeting targeting = createGame().getUnitTargeting();

        Assertions.assertTrue(targeting.canSeeTarget(createUnit(FIRST_PLAYER, WORD_MINUS_ONE, TerrainTileset.TILE_SIZE), null),
                              "-1 truncates to column zero of row one.");
        Assertions.assertTrue(targeting.canSeeTarget(createUnit(FIRST_PLAYER, MAP_WIDTH * TerrainTileset.TILE_SIZE, 0), null),
                              "The column past the right edge reads the next row's first tile.");
        Assertions.assertTrue(targeting.canSeeTarget(createUnit(FIRST_PLAYER, WORD_MINUS_THIRTY_THREE, TerrainTileset.TILE_SIZE), null),
                              "Column -1 reads the previous row's last tile.");
        Assertions.assertFalse(targeting.canSeeTarget(createUnit(FIRST_PLAYER, 0, WORD_MINUS_THIRTY_THREE), null),
                               "Row -1 reads before the tile flag array.");
        Assertions.assertFalse(targeting.canSeeTarget(createUnit(FIRST_PLAYER, 0, MAP_HEIGHT * TerrainTileset.TILE_SIZE), null),
                               "The row past the bottom edge reads past the map's tiles.");
    }

    @Test
    void can_see_target_should_test_only_the_not_visible_byte_of_the_order_target_tile() {
        UnitTargeting targeting = createGame().getUnitTargeting();

        Assertions.assertFalse(targeting.canSeeTarget(createUnit(FIRST_PLAYER, 0, 0), null));
        Assertions.assertTrue(targeting.canSeeTarget(createUnit(NEUTRAL_PLAYER, 0, 0), null),
                              "A player past the eighth tests a bit above the not visible byte.");
    }

    @Test
    void is_position_visible_should_read_negative_and_past_edge_positions() {
        UnitTargeting targeting = createGame().getUnitTargeting();

        Assertions.assertTrue(targeting.isPositionVisible(FIRST_PLAYER, WORD_MINUS_ONE, TerrainTileset.TILE_SIZE));
        Assertions.assertTrue(targeting.isPositionVisible(FIRST_PLAYER, MAP_WIDTH * TerrainTileset.TILE_SIZE, 0));
        Assertions.assertTrue(targeting.isPositionVisible(FIRST_PLAYER, WORD_MINUS_THIRTY_THREE, TerrainTileset.TILE_SIZE));
        Assertions.assertFalse(targeting.isPositionVisible(FIRST_PLAYER, 0, WORD_MINUS_THIRTY_THREE));
        Assertions.assertFalse(targeting.isPositionVisible(FIRST_PLAYER, 0, MAP_HEIGHT * TerrainTileset.TILE_SIZE));
    }

    @Test
    void is_position_visible_should_test_the_whole_tile_flag_dword() {
        UnitTargeting targeting = createGame().getUnitTargeting();

        Assertions.assertFalse(targeting.isPositionVisible(NEUTRAL_PLAYER, 0, 0), "A player past the eighth tests a not explored bit.");
    }

    @RequiresInstallation
    @Test
    void can_attack_target_should_reject_every_target_of_an_arbiter_with_a_computer_record() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(data);

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Unit arbiter = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_ARBITER,
                                                              ARBITER_X, ARBITER_Y,
                                                              RunningGameScenario.HUMAN);
                Unit marine = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE,
                                                             ARBITER_X + TARGET_DISTANCE, ARBITER_Y,
                                                             RunningGameScenario.COMPUTER);

                Assertions.assertTrue(game.getUnitTargeting().canAttackTarget(arbiter, marine));

                arbiter.setComputerRecord(new ComputerUnitRecord(ComputerUnitRecord.KIND_MILITARY, arbiter.getIndex()));

                Assertions.assertFalse(game.getUnitTargeting().canAttackTarget(arbiter, marine));
            });
        }
    }

    @RequiresInstallation
    @Test
    void find_nearest_unit_should_exclude_the_searching_unit() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(data);

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Unit searcher = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_WRAITH,
                                                               NEAREST_CREATION_X, NEAREST_CREATION_Y,
                                                               RunningGameScenario.HUMAN);

                game.getUnitPositioning().move(searcher, NEAREST_CENTER_X, NEAREST_CENTER_Y);

                Assertions.assertNotNull(searcher.getSpriteOrNull());
                Assertions.assertEquals(NEAREST_CENTER_X, searcher.getSpriteOrNull().getX());
                Assertions.assertEquals(NEAREST_CENTER_Y, searcher.getSpriteOrNull().getY());
                Assertions.assertTrue(searcher.getMinimumHorizontalEdgeIndex() >= 0);
                Assertions.assertTrue(searcher.getMaximumHorizontalEdgeIndex() >= searcher.getMinimumHorizontalEdgeIndex());

                Assertions.assertNull(game.getUnitTargeting().findNearestUnit(searcher,
                                                                              NEAREST_CENTER_X - NEAREST_SEARCH_RADIUS,
                                                                              NEAREST_CENTER_Y - NEAREST_SEARCH_RADIUS,
                                                                              NEAREST_CENTER_X + NEAREST_SEARCH_RADIUS,
                                                                              NEAREST_CENTER_Y + NEAREST_SEARCH_RADIUS,
                                                                              candidate -> candidate == searcher),
                                      "The nearest query must exclude its searching unit before the candidate test.");
            });
        }
    }

    @RequiresInstallation
    @Test
    void find_nearest_unit_should_test_candidates_before_comparing_distance() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(data);

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Unit searcher = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_WRAITH,
                                                               NEAREST_CREATION_X, NEAREST_CREATION_Y,
                                                               RunningGameScenario.HUMAN);

                game.getUnitPositioning().move(searcher, NEAREST_CENTER_X, NEAREST_CENTER_Y);

                Unit nearer = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_WRAITH,
                                                             NEAREST_CREATION_X, NEAREST_CREATION_Y,
                                                             RunningGameScenario.HUMAN);

                game.getUnitPositioning().move(nearer, NEAREST_CENTER_X, NEAREST_CENTER_Y + NEAREST_CARDINAL_OFFSET);

                Unit farther = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_WRAITH,
                                                              NEAREST_CREATION_X, NEAREST_CREATION_Y,
                                                              RunningGameScenario.HUMAN);

                game.getUnitPositioning().move(farther, NEAREST_CENTER_X + NEAREST_DIAGONAL_OFFSET, NEAREST_CENTER_Y + NEAREST_DIAGONAL_OFFSET);

                Assertions.assertNotNull(searcher.getSpriteOrNull());
                Assertions.assertNotNull(nearer.getSpriteOrNull());
                Assertions.assertNotNull(farther.getSpriteOrNull());
                Assertions.assertEquals(NEAREST_CENTER_X, searcher.getSpriteOrNull().getX());
                Assertions.assertEquals(NEAREST_CENTER_Y, searcher.getSpriteOrNull().getY());
                Assertions.assertEquals(NEAREST_CENTER_X, nearer.getSpriteOrNull().getX());
                Assertions.assertEquals(NEAREST_CENTER_Y + NEAREST_CARDINAL_OFFSET, nearer.getSpriteOrNull().getY());
                Assertions.assertEquals(NEAREST_CENTER_X + NEAREST_DIAGONAL_OFFSET, farther.getSpriteOrNull().getX());
                Assertions.assertEquals(NEAREST_CENTER_Y + NEAREST_DIAGONAL_OFFSET, farther.getSpriteOrNull().getY());

                int nearerDistance = RegionPathSearch.calculateDistance(NEAREST_CENTER_X, nearer.getSpriteOrNull().getX(),
                                                                        NEAREST_CENTER_Y, nearer.getSpriteOrNull().getY());
                int fartherDistance = RegionPathSearch.calculateDistance(NEAREST_CENTER_X, farther.getSpriteOrNull().getX(),
                                                                         NEAREST_CENTER_Y, farther.getSpriteOrNull().getY());

                Assertions.assertTrue(fartherDistance > nearerDistance, "The fixture must provide a farther candidate.");
                Assertions.assertTrue(Math.abs(farther.getSpriteOrNull().getX() - NEAREST_CENTER_X) < nearerDistance);
                Assertions.assertTrue(Math.abs(farther.getSpriteOrNull().getY() - NEAREST_CENTER_Y) < nearerDistance);

                List<Unit> initialObservation = new ArrayList<>();

                Assertions.assertNull(game.getUnitTargeting().findNearestUnit(searcher,
                                                                              NEAREST_CENTER_X - NEAREST_SEARCH_RADIUS,
                                                                              NEAREST_CENTER_Y - NEAREST_SEARCH_RADIUS,
                                                                              NEAREST_CENTER_X + NEAREST_SEARCH_RADIUS,
                                                                              NEAREST_CENTER_Y + NEAREST_SEARCH_RADIUS,
                                                                              candidate -> {
                                                                                  if ((candidate == nearer || candidate == farther) &&
                                                                                      !initialObservation.contains(candidate)) {
                                                                                      initialObservation.add(candidate);
                                                                                  }

                                                                                  return false;
                                                                              }));
                Assertions.assertTrue(initialObservation.contains(nearer), "The fixture must expose the nearer candidate.");
                Assertions.assertTrue(initialObservation.contains(farther), "The fixture must expose the farther candidate.");
                Assertions.assertTrue(initialObservation.indexOf(nearer) < initialObservation.indexOf(farther),
                                      "The fixture must visit the nearer candidate before the farther candidate.");

                List<Unit> acceptedObservation = new ArrayList<>();

                Assertions.assertSame(nearer, game.getUnitTargeting().findNearestUnit(searcher,
                                                                                      NEAREST_CENTER_X - NEAREST_SEARCH_RADIUS,
                                                                                      NEAREST_CENTER_Y - NEAREST_SEARCH_RADIUS,
                                                                                      NEAREST_CENTER_X + NEAREST_SEARCH_RADIUS,
                                                                                      NEAREST_CENTER_Y + NEAREST_SEARCH_RADIUS,
                                                                                      candidate -> {
                                                                                          acceptedObservation.add(candidate);

                                                                                          return candidate == nearer;
                                                                                      }));
                Assertions.assertTrue(acceptedObservation.contains(farther),
                                      "A candidate inside the shrinking bounds must be tested even when it cannot improve distance.");
            });
        }
    }

    @RequiresInstallation
    @Test
    void find_nearest_unit_should_return_an_accepted_other_unit_and_no_rejected_unit() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = RunningGameScenario.start(data);

            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                Game game = runtime.getGame();
                Unit searcher = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_WRAITH,
                                                               NEAREST_CREATION_X, NEAREST_CREATION_Y,
                                                               RunningGameScenario.HUMAN);

                game.getUnitPositioning().move(searcher, NEAREST_CENTER_X, NEAREST_CENTER_Y);

                Unit candidate = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_WRAITH,
                                                                NEAREST_CREATION_X, NEAREST_CREATION_Y,
                                                                RunningGameScenario.HUMAN);

                game.getUnitPositioning().move(candidate, NEAREST_CENTER_X + NEAREST_CARDINAL_OFFSET, NEAREST_CENTER_Y);

                Assertions.assertNotNull(candidate.getSpriteOrNull());
                Assertions.assertEquals(NEAREST_CENTER_X + NEAREST_CARDINAL_OFFSET, candidate.getSpriteOrNull().getX());
                Assertions.assertEquals(NEAREST_CENTER_Y, candidate.getSpriteOrNull().getY());
                Assertions.assertSame(candidate, game.getUnitTargeting().findNearestUnit(searcher,
                                                                                         NEAREST_CENTER_X - NEAREST_SEARCH_RADIUS,
                                                                                         NEAREST_CENTER_Y - NEAREST_SEARCH_RADIUS,
                                                                                         NEAREST_CENTER_X + NEAREST_SEARCH_RADIUS,
                                                                                         NEAREST_CENTER_Y + NEAREST_SEARCH_RADIUS,
                                                                                         accepted -> accepted == candidate));
                Assertions.assertNull(game.getUnitTargeting().findNearestUnit(searcher,
                                                                              NEAREST_CENTER_X - NEAREST_SEARCH_RADIUS,
                                                                              NEAREST_CENTER_Y - NEAREST_SEARCH_RADIUS,
                                                                              NEAREST_CENTER_X + NEAREST_SEARCH_RADIUS,
                                                                              NEAREST_CENTER_Y + NEAREST_SEARCH_RADIUS,
                                                                              accepted -> false));
            });
        }
    }

    @RequiresInstallation
    @Test
    void get_halt_distance_should_wrap_the_squared_speed_to_a_signed_dword() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = Game.create(data, new GameMap(MAP_WIDTH, MAP_HEIGHT, 0, new int[TILE_COUNT], new int[TILE_COUNT]), new LcgRandom(1));
            Unit unit = UnitTestUtils.createUnit(1);

            unit.setMovementControl(ScrFlingyTypeView.MOVEMENT_CONTROL_FLINGY);
            unit.setNextSpeed(OVERFLOWING_SPEED);
            unit.setAcceleration(OVERFLOWING_ACCELERATION);

            Assertions.assertEquals(OVERFLOWING_HALT_DISTANCE, game.getUnitTargeting().getHaltDistance(unit));
        }
    }
}
