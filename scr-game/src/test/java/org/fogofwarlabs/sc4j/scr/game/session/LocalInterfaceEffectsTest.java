package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkUnit;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatSchema;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPoolScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Checks the local interface effects: attack-alert area marks with their shared finder query state, attack-alert mark decay, and the single-player
 * critter easter egg that kills the critter outside the command stream while the game keeps running and the recorded command stream stays empty.
 */
final class LocalInterfaceEffectsTest {
    private static final int MAP_TILE_DIMENSION = 8;
    private static final int MAP_TILE_COUNT = MAP_TILE_DIMENSION * MAP_TILE_DIMENSION;
    private static final int UNIT_POOL_CAPACITY = 5;
    private static final int OVERFLOWING_ALERT_MARK = 0x1FF;
    private static final int FULL_ALERT_MARK = 255;
    /**
     * The alert mark that {@link #OVERFLOWING_ALERT_MARK} leaves after one decrement: its low byte minus one.
     */
    private static final int DECREMENTED_OVERFLOWING_ALERT_MARK = 254;

    private static final @NotNull String MELEE_MAP_EXTENSION = ".scm";
    private static final @NotNull String SCENARIO_FILE = "staredit\\scenario.chk";
    private static final @NotNull String PLAYER_NAME = "tester";
    private static final int GAME_SEED = 777;
    private static final int CRITTER_STARTUP_FRAMES = 5;
    private static final int FINAL_EFFECT_FRAME = 120;

    private static final int EXTENT_DIMENSION_COUNT = 4;
    private static final int UNIT_RECORD_SIZE = Integer.BYTES + EXTENT_DIMENSION_COUNT * Short.BYTES;
    private static final int EXTENT_COLUMN_OFFSET = UnitsDat.UNIT_COUNT * Integer.BYTES;
    private static final int NORMAL_UNIT_EXTENT = 16;
    private static final int WIDE_UNIT_TYPE = 2;
    private static final int WIDE_UNIT_EXTENT = 600;
    private static final int ATTACK_ALERT_MARK = 100;

    private static final int WORLD_TILE_DIMENSION = 64;
    private static final int WORLD_TILE_COUNT = WORLD_TILE_DIMENSION * WORLD_TILE_DIMENSION;
    /**
     * The order of a dying unit.
     */
    private static final int DIE_ORDER = 0;

    /**
     * Tests whether the scenario contains a native critter type.
     *
     * @param map the parsed scenario
     * @return {@code true} if a critter placement is present, {@code false} otherwise
     */
    private static boolean hasCritter(@NotNull ChkFile map) {
        for (ChkUnit placement : map.getUnits()) {
            if (isCritter(placement.getUnitId())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Tests whether a unit type is one of the native critter types.
     *
     * @param unitType the unit type identifier
     * @return {@code true} if the type is a critter, {@code false} otherwise
     */
    private static boolean isCritter(int unitType) {
        return unitType == ScrUnitIds.CRITTER_RHYNADON || unitType == ScrUnitIds.CRITTER_BENGALAAS ||
               unitType >= ScrUnitIds.CRITTER_SCANTID && unitType <= ScrUnitIds.CRITTER_URSADON;
    }

    /**
     * Creates a {@link Game} instance with six allocated units and fixture unit extents.
     *
     * @param positions the sprite positions
     * @param types     the fixture unit types: {@code 0} has 16-pixel extents, {@code 1} has none, and {@code 2} has 600-pixel extents
     * @return the created {@link Game} instance
     * @throws IOException if the fixture DAT table cannot be parsed
     */
    private static @NotNull Game createWorld(int @NotNull [] @NotNull [] positions, int @NotNull [] types) throws IOException {
        DatSchema schema = new DatSchema().fieldU32("Special Ability Flags", UnitsDat.UNIT_COUNT)
                                          .fieldU16x4("Unit Size Left", "Unit Size Up", "Unit Size Right", "Unit Size Down", UnitsDat.UNIT_COUNT);
        ByteBuffer contents = ByteBuffer.allocate(UnitsDat.UNIT_COUNT * UNIT_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int dimension = 0; dimension < EXTENT_DIMENSION_COUNT; dimension++) {
            contents.putShort(EXTENT_COLUMN_OFFSET + dimension * Short.BYTES, (short) NORMAL_UNIT_EXTENT);
            contents.putShort(EXTENT_COLUMN_OFFSET + WIDE_UNIT_TYPE * EXTENT_DIMENSION_COUNT * Short.BYTES + dimension * Short.BYTES,
                              (short) WIDE_UNIT_EXTENT);
        }

        ScrUnitTypeView unitTypes = UnitsDat.createTypeTable(schema.parse(contents.array()));
        GameMap map = new GameMap(WORLD_TILE_DIMENSION, WORLD_TILE_DIMENSION, 0, new int[WORLD_TILE_COUNT], new int[WORLD_TILE_COUNT]);
        Game game = Game.create(null, map, new LcgRandom(1));

        game.getUnitSystem().initializeUnitPool(positions.length);
        game.getUnitSystem().initializeUnitSpatialIndex(unitTypes);

        UnitPool pool = game.getUnitSystem().getUnitPool();
        UnitSpatialIndex finder = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(pool);
        Assertions.assertNotNull(finder);

        for (int index = 0; index < positions.length; index++) {
            Unit unit = pool.getUnit(UnitPoolScenario.allocate(pool));

            unit.setUnitId(types[index]);
            unit.setSprite(SpriteScenario.createSprite(0, positions[index][0], positions[index][1], 0));
            finder.insert(unit, unitTypes);
        }

        return game;
    }


    @Test
    void decrement_attack_alert_marks_should_touch_only_nonzero_active_list_bytes() {
        GameMap map = new GameMap(MAP_TILE_DIMENSION, MAP_TILE_DIMENSION, 0, new int[MAP_TILE_COUNT], new int[MAP_TILE_COUNT]);
        Game game = Game.create(null, map, new LcgRandom(1));

        game.getUnitSystem().initializeUnitPool(UNIT_POOL_CAPACITY);

        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit active = pool.getUnit(UnitPoolScenario.allocate(pool));
        Unit zero = pool.getUnit(UnitPoolScenario.allocate(pool));
        Unit hidden = pool.getUnit(UnitPoolScenario.allocate(pool));
        Unit unlisted = pool.getUnit(UnitPoolScenario.allocate(pool));
        Unit free = pool.getUnit(pool.getFirstFreeIndex());

        active.setStatus(UnitStatusFlags.GROUNDED_BUILDING);
        zero.setStatus(UnitStatusFlags.GROUNDED_BUILDING);
        pool.insertCreatedUnit(active);
        pool.insertCreatedUnit(zero);
        pool.insertCreatedUnit(hidden);
        active.setAlertMark(OVERFLOWING_ALERT_MARK);
        hidden.setAlertMark(FULL_ALERT_MARK);
        unlisted.setAlertMark(FULL_ALERT_MARK);
        free.setAlertMark(FULL_ALERT_MARK);

        game.getLocalInterfaceEffects().decrementAttackAlertMarks();

        Assertions.assertEquals(DECREMENTED_OVERFLOWING_ALERT_MARK, active.getAlertMark());
        Assertions.assertEquals(0, zero.getAlertMark());
        Assertions.assertEquals(FULL_ALERT_MARK, hidden.getAlertMark());
        Assertions.assertEquals(FULL_ALERT_MARK, unlisted.getAlertMark());
        Assertions.assertEquals(FULL_ALERT_MARK, free.getAlertMark());

        active.setAlertMark(1);

        game.getLocalInterfaceEffects().decrementAttackAlertMarks();
        game.getLocalInterfaceEffects().decrementAttackAlertMarks();

        Assertions.assertEquals(0, active.getAlertMark());
        Assertions.assertEquals(0, zero.getAlertMark());
    }

    @Test
    void attack_alert_effects_accept_an_uninitialized_world() {
        GameMap map = new GameMap(MAP_TILE_DIMENSION, MAP_TILE_DIMENSION, 0, new int[MAP_TILE_COUNT], new int[MAP_TILE_COUNT]);
        Game game = Game.create(null, map, new LcgRandom(1));

        Assertions.assertDoesNotThrow(() -> game.getLocalInterfaceEffects().decrementAttackAlertMarks());
        Assertions.assertDoesNotThrow(() -> game.getLocalInterfaceEffects().markAttackAlertArea(0, 0));
    }

    @RequiresInstallation
    @Test
    void kill_critter_should_kill_without_random_draws_or_a_recorded_command() throws IOException {
        Path mapPath = null;

        try (Stream<Path> maps = Files.list(ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER))) {
            for (Path candidate : maps.filter(path -> path.toString().endsWith(MELEE_MAP_EXTENSION)).sorted().toList()) {
                if (hasCritter(ChkFile.parseTolerant(MpqArchive.open(candidate).readFile(SCENARIO_FILE)))) {
                    mapPath = candidate;

                    break;
                }
            }
        }

        Assertions.assertNotNull(mapPath, "An installed melee map with a critter is required.");

        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO_FILE);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            MeleeGameSetup setup = new MeleeGameSetup(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, 0, new int[]{ScrRaceIds.ZERG}, GameSpeed.FASTEST);
            LiveGame game = new LiveGame(setup, GAME_SEED);
            ReplayRuntime live = ReplayRuntime.createLiveGame(game.getReplay(), ChkFile.parseTolerant(mapData), gameData,
                                                              game.getLocalPlayer(),
                                                              RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                              RunningGameScenario.INSTRUCTION_LIMIT);

            live.start(0, 0);

            Assertions.assertNull(live.getFailure());

            Game state = live.getGame();

            for (int frame = 0; frame < CRITTER_STARTUP_FRAMES; frame++) {
                game.exchangeTurn(state.getCurrentFrame());

                Assertions.assertTrue(live.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));
            }

            UnitPool pool = state.getUnitSystem().getUnitPool();
            int critter = 0;

            Assertions.assertNotNull(pool);

            for (int index = 1; index <= pool.getCapacity() && critter == 0; index++) {
                int unitType = pool.getUnit(index).getUnitId();

                if (pool.isAllocated(index) && pool.getUnit(index).getSpriteOrNull() != null && isCritter(unitType)) {
                    critter = index;
                }
            }

            Assertions.assertNotEquals(0, critter);

            Unit unit = pool.getUnit(critter);
            int randomCount = state.getRandom().getTotalRandomCounts();

            state.getLocalInterfaceEffects().killCritter(unit, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(randomCount, state.getRandom().getTotalRandomCounts(), "The interface runs with the generator disabled.");

            for (int frame = CRITTER_STARTUP_FRAMES; frame < FINAL_EFFECT_FRAME; frame++) {
                game.exchangeTurn(state.getCurrentFrame());

                Assertions.assertTrue(live.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));
            }

            Assertions.assertTrue(unit.getSpriteOrNull() == null || !pool.isAllocated(critter) ||
                                  unit.getUnitId() != pool.getUnit(critter).getUnitId() || unit.getPrimaryOrder() == DIE_ORDER,
                                  "The critter must be dead or dying.");
            Assertions.assertTrue(game.getHistory().isEmpty(), "The easter egg is not a command.");
        }
    }

    @Test
    void mark_attack_alert_area_should_use_unexpanded_edges_and_stamp_before_vertical_filter() throws IOException {
        Game game = createWorld(new int[][]{{1000, 1000}, {1352, 1000}, {1353, 1000}, {1000, 1400}, {1600, 1000}, {1000, 1000}},
                                new int[]{0, 1, 0, 1, 2, 2});
        UnitPool pool = game.getUnitSystem().getUnitPool();
        UnitSpatialIndex finder = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(pool);
        Assertions.assertNotNull(finder);

        int[] allocationOrder = {1, 6, 5, 4, 3, 2};

        game.getLocalInterfaceEffects().markAttackAlertArea(1000, 1000);

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, game.getUnitSystem().getUnitFinderSearchCounter().getValue()),
                () -> Assertions.assertEquals(ATTACK_ALERT_MARK, pool.getUnit(allocationOrder[0]).getAlertMark()),
                () -> Assertions.assertEquals(0, pool.getUnit(allocationOrder[1]).getAlertMark(), "The right boundary is excluded."),
                () -> Assertions.assertEquals(ATTACK_ALERT_MARK, pool.getUnit(allocationOrder[2]).getAlertMark(),
                                              "An edge inside is sufficient even when the center is outside."),
                () -> Assertions.assertEquals(0, pool.getUnit(allocationOrder[3]).getAlertMark()),
                () -> Assertions.assertEquals(1, finder.getQueryMarkers()[allocationOrder[3]],
                                              "Horizontal visitation stamps a vertically rejected unit."),
                () -> Assertions.assertEquals(ATTACK_ALERT_MARK, pool.getUnit(allocationOrder[4]).getAlertMark()),
                () -> Assertions.assertEquals(0, pool.getUnit(allocationOrder[5]).getAlertMark(),
                                              "A box surrounding the query has no horizontal edge inside."),
                () -> Assertions.assertEquals(0, finder.getQueryMarkers()[allocationOrder[5]])
        );

        game.getLocalInterfaceEffects().markAttackAlertArea(1000, 1000);

        Assertions.assertEquals(2, game.getUnitSystem().getUnitFinderSearchCounter().getValue());
        Assertions.assertEquals(2, finder.getQueryMarkers()[allocationOrder[3]]);
    }

    @Test
    void mark_attack_alert_area_should_truncate_bounds_to_signed_words_and_count_empty_queries() throws IOException {
        Game game = createWorld(new int[][]{{10, 20}, {0, 0}, {1352, 1352}, {1351, 1351}, {648, 648}, {647, 647}}, new int[]{1, 1, 1, 1, 1, 1});
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        game.getLocalInterfaceEffects().markAttackAlertArea(65546, 65556);

        Assertions.assertEquals(ATTACK_ALERT_MARK, pool.getUnit(1).getAlertMark(), "The alert coordinates use their low WORDs.");
        Assertions.assertEquals(1, game.getUnitSystem().getUnitFinderSearchCounter().getValue());

        game.getLocalInterfaceEffects().markAttackAlertArea(32767, 32767);

        Assertions.assertEquals(2, game.getUnitSystem().getUnitFinderSearchCounter().getValue(),
                                "A wrapped empty rectangle still advances the counter.");

        game.getLocalInterfaceEffects().markAttackAlertArea(1000, 1000);

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, pool.getUnit(5).getAlertMark(), "Right and lower bounds are excluded."),
                () -> Assertions.assertEquals(ATTACK_ALERT_MARK, pool.getUnit(4).getAlertMark()),
                () -> Assertions.assertEquals(ATTACK_ALERT_MARK, pool.getUnit(3).getAlertMark(), "Left and upper bounds are included."),
                () -> Assertions.assertEquals(0, pool.getUnit(2).getAlertMark())
        );
    }

    @Test
    void mark_attack_alert_area_should_require_unit_finder_once_unit_pool_exists() {
        GameMap map = new GameMap(MAP_TILE_DIMENSION, MAP_TILE_DIMENSION, 0, new int[MAP_TILE_COUNT], new int[MAP_TILE_COUNT]);
        Game game = Game.create(null, map, new LcgRandom(1));

        game.getUnitSystem().initializeUnitPool(1);

        Assertions.assertThrows(IllegalStateException.class, () -> game.getLocalInterfaceEffects().markAttackAlertArea(0, 0));
    }
}
