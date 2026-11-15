package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.engine.movement.MovementFlags;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatSchema;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.path.PathPoolAssert;
import org.fogofwarlabs.sc4j.scr.game.path.UnitPath;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitCollisionRules;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Checks the movement state machine of {@link UnitMovement} and its movement queries against native captures.
 */
@RequiresInstallation
final class UnitMovementTest {
    /**
     * The SHA-256 digest of the native stationary movement state capture.
     */
    private static final @NotNull String STATIONARY_STATE_CAPTURE_HASH = "5d5c456ce0563291344b66ef63e46afe56af6b32fec05b59f76b8e359d9c373c";
    /**
     * The SHA-256 digest of the native arrival movement state capture.
     */
    private static final @NotNull String ARRIVAL_STATE_CAPTURE_HASH = "479d2aaef204c14c04ead4fc283b41a6f4b37cca3d51c7850add8b5140751b91";
    /**
     * The SHA-256 digest of the native unit motion commit capture.
     */
    private static final @NotNull String MOTION_COMMIT_CAPTURE_HASH = "42fbd05e5982b5948685a6559dbbf3c852005516a68615e0f8403bfa811c83b8";
    /**
     * The SHA-256 digest of the native capture of movement state twelve.
     */
    private static final @NotNull String CLEAR_STATE_TWELVE_CAPTURE_HASH = "6450afdddb88e72e8bff4e590d4d366a17b880c07d38835b93f83949eec49505";

    private static final int MAP_DIMENSION = 32;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int LARGE_MAP_DIMENSION = 128;
    private static final int LARGE_MAP_TILE_COUNT = LARGE_MAP_DIMENSION * LARGE_MAP_DIMENSION;
    private static final int WALKABLE_MAP_DIMENSION = 64;
    /**
     * The pixel height of one sprite row.
     */
    private static final int SPRITE_ROW_HEIGHT = 32;

    private static final int UNIT_POSITION = 512;
    private static final int TRANSITION_LIMIT = 4;
    private static final int PATH_TRANSITION_LIMIT = 8;
    private static final int INSTRUCTION_LIMIT = 64;
    private static final int SINGLE_TRANSITION_LIMIT = 1;

    private static final int SPRITE_REFERENCE_OFFSET = 0x18;
    private static final int SPRITE_REFERENCE_END = 0x20;
    private static final int MOVEMENT_DESTINATION_X_OFFSET = 0x20;
    private static final int MOVEMENT_DESTINATION_Y_OFFSET = 0x22;
    private static final int MOVEMENT_WAYPOINT_X_OFFSET = 0x30;
    private static final int DIRECTION_TARGET_X_OFFSET = 0x34;
    private static final int DIRECTION_TARGET_Y_OFFSET = 0x36;
    private static final int MOVEMENT_FLAGS_OFFSET = 0x38;
    private static final int CURRENT_SPEED_OFFSET = 0x50;
    private static final int PRIMARY_ORDER_OFFSET = 0x69;
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    private static final int MOVEMENT_STATE_OFFSET = 0xDB;
    private static final int PATH_REFERENCE_OFFSET = 0x188;
    private static final int PATH_RECALCULATION_COUNTDOWN_OFFSET = 0x190;
    private static final int REPULSION_CELL_X_OFFSET = 0x1E6;
    private static final int REPULSION_CELL_Y_OFFSET = 0x1E7;
    /**
     * The byte that fills every unit field before a case writes its inputs.
     */
    private static final int FILL_BYTE = 0xA5;

    /**
     * An unnamed user action flag and an unnamed path flag that the updates must keep.
     */
    private static final int RETAINED_USER_ACTION_FLAG = 0x80;
    private static final int RETAINED_PATH_FLAG = 0x80;
    /**
     * The movement destination, the path timer and the random source of the start-path case.
     */
    private static final int START_PATH_DESTINATION_X = 544;
    private static final int START_PATH_TIMER = 99;
    private static final int START_PATH_RANDOM_CALL = 51;
    /**
     * The game random seed of the start-path case.
     */
    private static final int START_PATH_RANDOM_SEED = 64;

    private static final int STATIONARY_CASE_COUNT = 512;
    private static final int ARRIVAL_CASE_COUNT = 256;
    private static final int COMMIT_CASE_COUNT = 512;

    private static final int EDGE_POSITION_X = 32768;
    private static final int EDGE_POSITION_Y = 65535;

    private static final int WORKER_SPRITE_TYPE = 140;
    private static final int WORKER_POSITION = 16;
    private static final int WORKER_ELEVATION = 4;
    private static final int WORKER_PATH_RECALCULATION_COUNTDOWN = 7;
    private static final int WORKER_SOURCE_BYTE = 7;
    private static final int WORKER_RESULT = 0xA5A5A5A5;

    /**
     * The {@code units.dat} columns of the query fixture: the special ability flags DWORD and the four extent WORDs.
     */
    private static final int UNIT_TYPE_RECORD_SIZE = Integer.BYTES + 4 * Short.BYTES;
    private static final int UNIT_EXTENTS_OFFSET = UnitsDat.UNIT_COUNT * Integer.BYTES;
    private static final int UNIT_EXTENTS_SIZE = 4 * Short.BYTES;
    /**
     * The offsets of the up, right and down extent WORDs in the extents of one unit type.
     */
    private static final int UP_EXTENT_OFFSET = Short.BYTES;
    private static final int RIGHT_EXTENT_OFFSET = 2 * Short.BYTES;
    private static final int DOWN_EXTENT_OFFSET = 3 * Short.BYTES;
    /**
     * The unit count of the query fixture and the origin, the columns and the spacing of its unit grid.
     */
    private static final int QUERY_UNIT_COUNT = 64;
    private static final int QUERY_GRID_ORIGIN = 64;
    private static final int QUERY_GRID_COLUMNS = 8;
    private static final int QUERY_GRID_SPACING = 3;

    private static final int CLEAR_STATE_POSITION_Y = 513;
    private static final int CLEAR_STATE_INVOCATION_COUNT = 7;

    private static final int FIRST_UPDATE_SOURCE_BYTE = 7;

    private static final int COLLISION_UNIT_COUNT = 2;
    private static final int BLOCKER_POOL_INDEX = 2;
    /**
     * The result an inert-state case starts with.
     */
    private static final int STALE_RESULT = 123;
    /**
     * A frame pulse whose low eight bits are zero.
     */
    private static final int WRAPPED_SOURCE_BYTE = 256;

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }


    @Test
    void update_should_start_iscript_path_after_user_order_delay() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]),
                                new LcgRandom(START_PATH_RANDOM_SEED));

        game.getPathSystem().setPathPool(new PathPool());
        game.getPathSystem().setUnitPath(new UnitPath());

        Unit unit = UnitTestUtils.createUnit(1);

        unit.setSprite(SpriteScenario.createSprite(0, UNIT_POSITION, UNIT_POSITION, 0));
        unit.setMovementDestinationX(START_PATH_DESTINATION_X);
        unit.setMovementDestinationY(UNIT_POSITION);
        unit.setMovementState(MovementStates.START_PATH);
        unit.setMovementControl(ScrFlingyTypeView.MOVEMENT_CONTROL_ISCRIPT);
        unit.setUserActionFlags(Unit.USER_ACTION_ORDER_DONE | Unit.USER_ACTION_COMMANDED | RETAINED_USER_ACTION_FLAG);

        PathPool pathPool = game.getPathSystem().getPathPool();

        Assertions.assertNotNull(pathPool);

        int pathIndex = pathPool.allocate();

        unit.setPathIndex(pathIndex);
        pathPool.setWaypointPosition(pathIndex, START_PATH_DESTINATION_X | UNIT_POSITION << Short.SIZE);
        pathPool.setFlags(pathIndex, 0);
        pathPool.setTimer(pathIndex, START_PATH_TIMER);

        game.getUnitMovement().update(new UnitMovement.Context(0, 0), unit, PATH_TRANSITION_LIMIT);

        Assertions.assertEquals(MovementStates.UI_ORDER_DELAY, unit.getMovementState());
        Assertions.assertEquals(Unit.USER_ACTION_ORDER_DONE | RETAINED_USER_ACTION_FLAG, unit.getUserActionFlags());
        Assertions.assertEquals(1, pathPool.getTimer(pathIndex));
        Assertions.assertEquals(1, game.getRandom().getCallCount(START_PATH_RANDOM_CALL));

        game.getUnitMovement().update(new UnitMovement.Context(0, 0), unit, PATH_TRANSITION_LIMIT);

        Assertions.assertEquals(MovementStates.UI_ORDER_DELAY, unit.getMovementState());
        Assertions.assertEquals(0, pathPool.getTimer(pathIndex));
        Assertions.assertEquals(1, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void update_should_cancel_changed_path_when_new_destination_is_already_reached() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.getPathSystem().setPathPool(new PathPool());
        game.getPathSystem().setUnitPath(new UnitPath());

        Unit unit = UnitTestUtils.createUnit(1);

        unit.setSprite(SpriteScenario.createSprite(0, UNIT_POSITION, UNIT_POSITION, 0));
        unit.setPositionX(UNIT_POSITION);
        unit.setPositionY(UNIT_POSITION);
        unit.setDirectionTargetX(UNIT_POSITION);
        unit.setDirectionTargetY(UNIT_POSITION);
        unit.setMovementDestinationX(UNIT_POSITION);
        unit.setMovementDestinationY(UNIT_POSITION);
        unit.setMovementState(MovementStates.NEW_MOVE_TARGET);

        PathPool pathPool = game.getPathSystem().getPathPool();

        Assertions.assertNotNull(pathPool);

        int pathIndex = pathPool.allocate();

        unit.setPathIndex(pathIndex);
        pathPool.setFlags(pathIndex, PathPool.FLAG_NEW_MOVE_TARGET | RETAINED_PATH_FLAG);

        game.getUnitMovement().update(new UnitMovement.Context(0, 0), unit, PATH_TRANSITION_LIMIT);

        Assertions.assertEquals(MovementStates.DORMANT, unit.getMovementState());
        Assertions.assertEquals(-1, unit.getPathIndex());
        Assertions.assertEquals(pathIndex, pathPool.getFirstFreeIndex());
        Assertions.assertEquals(RETAINED_PATH_FLAG, pathPool.getFlags(pathIndex));
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void update_should_keep_collision_while_order_or_animation_prevents_unstacking() {
        for (int protectedStatus : new int[]{UnitStatusFlags.ORDER_NOT_INTERRUPTIBLE, UnitStatusFlags.ISCRIPT_NO_BREAK, 0}) {
            Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

            game.getUnitSystem().initializeUnitPool(COLLISION_UNIT_COUNT);
            game.initializeUnitSpatialIndex();

            UnitPool unitPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(unitPool);

            Unit unit = unitPool.getUnit(1);

            unit.setUnitId(ScrUnitIds.PROTOSS_PROBE);
            unit.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUND_UNIT | protectedStatus);
            unit.setMovementFlags(protectedStatus == 0 ? MovementFlags.STARTING_ATTACK : 0);
            unit.setMovementState(MovementStates.CHECK_ILLEGAL);
            unit.setPathCollisionFlags(UnitInitializer.GROUND_ELEVATION_PATH_FLAG | UnitInitializer.ILLEGAL_POSITION_PATH_FLAG |
                                       UnitInitializer.STOPPED_MOVING_TO_LEGAL_PATH_FLAG);
            unit.setPositionX(UNIT_POSITION);
            unit.setPositionY(UNIT_POSITION);
            unit.setMovementDestinationX(UNIT_POSITION);
            unit.setMovementDestinationY(UNIT_POSITION);
            unit.setDirectionTargetX(UNIT_POSITION);
            unit.setDirectionTargetY(UNIT_POSITION);
            unit.setSprite(SpriteScenario.createSprite(0, UNIT_POSITION, UNIT_POSITION, 0));

            Unit blocker = unitPool.getUnit(BLOCKER_POOL_INDEX);

            blocker.setUnitId(ScrUnitIds.PROTOSS_NEXUS);
            blocker.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING);
            blocker.setPathCollisionFlags(UnitInitializer.GROUND_ELEVATION_PATH_FLAG);
            blocker.setSprite(SpriteScenario.createSprite(0, UNIT_POSITION, UNIT_POSITION, 0));

            UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

            Assertions.assertNotNull(spatialIndex);
            Assertions.assertNotNull(gameData);

            spatialIndex.insert(unit, gameData.getUnitTypes());
            spatialIndex.insert(blocker, gameData.getUnitTypes());

            game.getUnitMovement().update(new UnitMovement.Context(0, 0), unit, TRANSITION_LIMIT);

            Assertions.assertEquals(MovementStates.CHECK_ILLEGAL, unit.getMovementState());
            Assertions.assertEquals(UnitStatusFlags.COLLISION, unit.getStatus() & UnitStatusFlags.COLLISION);
            Assertions.assertEquals(UnitInitializer.GROUND_ELEVATION_PATH_FLAG, unit.getPathCollisionFlags());
            Assertions.assertEquals(UNIT_POSITION, unit.getMovementDestinationX());
            Assertions.assertEquals(UNIT_POSITION, unit.getMovementDestinationY());
            Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
        }
    }

    @Test
    void matches_native_stationary_state_cases() throws Exception {
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_STATIONARY_STATE);

        Assertions.assertEquals(STATIONARY_STATE_CAPTURE_HASH, MovementCheck.formatDigest(capture));

        ByteBuffer records = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(1);
        game.initializeUnitSpatialIndex();
        game.getSpriteSystem().initializeSpritePool(STATIONARY_CASE_COUNT);

        for (int caseIndex = 0; caseIndex < STATIONARY_CASE_COUNT; caseIndex++) {
            Unit unit = UnitTestUtils.createUnit(1);

            for (int offset = 0; offset < MovementCheck.UNIT_CAPTURE_SIZE; offset++) {
                UnitMemoryScenario.of(unit).writeMemoryByte(offset, records.get());
            }

            UnitMovement.Context context = new UnitMovement.Context(0, 0);
            byte[] inputMotion = new byte[MovementCheck.CONTEXT_CAPTURE_SIZE];

            records.get(inputMotion);
            MovementCheck.loadContext(inputMotion, context);

            byte[] expectedUnit = new byte[MovementCheck.UNIT_CAPTURE_SIZE];

            records.get(expectedUnit);

            byte[] expectedScratch = new byte[MovementCheck.CONTEXT_CAPTURE_SIZE];

            records.get(expectedScratch);

            int expectedResult = records.getInt();
            boolean redispatch = records.getInt() != 0;
            SpritePool spritePool = game.getSpriteSystem().getSpritePool();

            Assertions.assertNotNull(spritePool);

            Sprite sprite = spritePool.allocate();

            Assertions.assertNotNull(sprite);

            sprite.setX(UnitMemoryScenario.of(unit).readMemoryUnsignedWord(MovementCheck.UNIT_POSITION_X_OFFSET));
            sprite.setY(UnitMemoryScenario.of(unit).readMemoryUnsignedWord(MovementCheck.UNIT_POSITION_Y_OFFSET));
            spritePool.attachToRow(sprite, sprite.getY() / SPRITE_ROW_HEIGHT);
            unit.setSprite(sprite);

            if (redispatch) {
                Assertions.assertThrows(IllegalStateException.class,
                                        () -> game.getUnitMovement().update(context, unit, SINGLE_TRANSITION_LIMIT, INSTRUCTION_LIMIT));
            } else {
                game.getUnitMovement().update(context, unit, SINGLE_TRANSITION_LIMIT, INSTRUCTION_LIMIT);
            }

            UnitDiagnosticAssert.assertDomainEquals(expectedUnit, unit, "State9 unit case " + caseIndex);
            MovementCheck.assertState(expectedScratch, context);

            Assertions.assertEquals(expectedResult, context.getResult(), "State9 result case " + caseIndex);
        }

        Assertions.assertFalse(records.hasRemaining());
    }

    @Test
    void matches_native_arrival_state_cases() throws Exception {
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_ARRIVAL_STATE);

        Assertions.assertEquals(ARRIVAL_STATE_CAPTURE_HASH, MovementCheck.formatDigest(capture));

        ByteBuffer records = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);
        Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        for (int caseIndex = 0; caseIndex < ARRIVAL_CASE_COUNT; caseIndex++) {
            Unit unit = UnitTestUtils.createUnit(1);

            for (int offset = 0; offset < MovementCheck.UNIT_CAPTURE_SIZE; offset++) {
                UnitMemoryScenario.of(unit).writeMemoryByte(offset, records.get());
            }

            unit.setPathIndex(UnitMemoryScenario.of(unit).readMemoryInteger(PATH_REFERENCE_OFFSET) == 0 ? -1 : 1);

            PathPool pool = new PathPool();

            Assertions.assertEquals(1, pool.allocate());

            pool.setSourceX(1, 0);
            pool.setSourceY(1, 0);
            pool.setWaypointPosition(1, 0);
            game.getPathSystem().setPathPool(pool);

            byte[] expected = new byte[MovementCheck.UNIT_CAPTURE_SIZE];

            records.get(expected);

            UnitMovement.Context context = new UnitMovement.Context(0, 0);

            Assertions.assertThrows(IllegalStateException.class, () -> game.getUnitMovement().update(context, unit, SINGLE_TRANSITION_LIMIT));

            UnitDiagnosticAssert.assertDomainEquals(expected, unit, "State11 unit case " + caseIndex);

            Assertions.assertEquals(-1, unit.getPathIndex());
            Assertions.assertEquals(records.getInt(), pool.getFirstFreeIndex(), "State11 free head case " + caseIndex);
            Assertions.assertEquals(records.getInt(), PathPoolAssert.readSourcePositionOrFreeLink(pool, 1));
            Assertions.assertEquals(records.getInt(), pool.getWaypointPosition(1));
        }

        Assertions.assertFalse(records.hasRemaining());
    }

    @Test
    void matches_native_motion_commit_cases() throws Exception {
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_MOTION_COMMIT);

        Assertions.assertEquals(MOTION_COMMIT_CAPTURE_HASH, MovementCheck.formatDigest(capture));

        ByteBuffer records = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(1);
        game.initializeUnitSpatialIndex();
        game.getSpriteSystem().initializeSpritePool(COMMIT_CASE_COUNT);

        for (int caseIndex = 0; caseIndex < COMMIT_CASE_COUNT; caseIndex++) {
            Unit unit = UnitTestUtils.createUnit(1);

            for (int offset = 0; offset < MovementCheck.UNIT_CAPTURE_SIZE; offset++) {
                UnitMemoryScenario.of(unit).writeMemoryByte(offset, records.get());
            }

            byte[] inputScratch = new byte[MovementCheck.CONTEXT_CAPTURE_SIZE];

            records.get(inputScratch);

            UnitMovement.Context context = new UnitMovement.Context(0, records.getInt());

            MovementCheck.loadContext(inputScratch, context);

            byte[] expectedUnit = new byte[MovementCheck.UNIT_CAPTURE_SIZE];

            records.get(expectedUnit);

            byte[] expectedScratch = new byte[MovementCheck.CONTEXT_CAPTURE_SIZE];

            records.get(expectedScratch);

            int expectedResult = records.getInt();
            SpritePool sprites = game.getSpriteSystem().getSpritePool();

            Assertions.assertNotNull(sprites);

            Sprite sprite = sprites.allocate();

            Assertions.assertNotNull(sprite);

            sprite.setX(UnitMemoryScenario.of(unit).readMemoryUnsignedWord(MovementCheck.UNIT_POSITION_X_OFFSET));
            sprite.setY(UnitMemoryScenario.of(unit).readMemoryUnsignedWord(MovementCheck.UNIT_POSITION_Y_OFFSET));
            sprites.attachToRow(sprite, sprite.getY() / SPRITE_ROW_HEIGHT);
            unit.setSprite(sprite);

            game.getUnitMovement().commitMotion(context, unit, INSTRUCTION_LIMIT);

            UnitDiagnosticAssert.assertDomainEquals(expectedUnit, unit, "Commit unit case " + caseIndex);
            MovementCheck.assertState(expectedScratch, context);

            Assertions.assertEquals(expectedResult, context.getResult(), "Commit result case " + caseIndex);
        }

        Assertions.assertFalse(records.hasRemaining());
    }

    @Test
    void matches_native_initial_movement_state_cases() throws NoSuchAlgorithmException {
        Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        MessageDigest digest = MessageDigest.getInstance(MovementCheck.DIGEST_ALGORITHM);
        byte[] contents = new byte[MovementCheck.UNIT_CAPTURE_SIZE];
        int count = 0;

        for (int unitType : new int[]{
                ScrUnitIds.TERRAN_MARINE,
                ScrUnitIds.TERRAN_GOLIATH_TURRET,
                ScrUnitIds.ZERG_LARVA,
                ScrUnitIds.ZERG_OVERLORD,
                ScrUnitIds.PROTOSS_PROBE,
                ScrUnitIds.RESOURCE_MINERAL_FIELD
        }) {
            for (int elevation : new int[]{0, 11, 12, 16, 255}) {
                for (int status : new int[]{
                        0,
                        UnitStatusFlags.ISCRIPT_NO_BREAK,
                        UnitStatusFlags.IN_BUNKER,
                        UnitStatusFlags.BURROWED,
                        UnitStatusFlags.CAN_TURN,
                        UnitStatusFlags.CAN_MOVE,
                        UnitStatusFlags.IMMOVABLE,
                        0x170001,
                        0x4140003
                }) {
                    for (int retainedFlags : new int[]{0, 1, 2, 255}) {
                        for (int hidden : new int[]{0, Sprite.FLAG_HIDDEN}) {
                            for (boolean atTarget : new boolean[]{false, true}) {
                                if (hidden != 0 && !atTarget) {
                                    continue;
                                }

                                Unit unit = UnitTestUtils.createUnit(1);

                                for (int offset = 0; offset < contents.length; offset++) {
                                    UnitMemoryScenario.of(unit).writeMemoryByte(offset, FILL_BYTE);
                                }

                                Sprite sprite = SpriteScenario.createSprite(0, EDGE_POSITION_X, EDGE_POSITION_Y, elevation);

                                sprite.setFlags(hidden);
                                unit.setSprite(sprite);
                                UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);
                                UnitMemoryScenario.of(unit).writeMemoryWord(MOVEMENT_DESTINATION_X_OFFSET,
                                                                            atTarget ? EDGE_POSITION_X : EDGE_POSITION_X - 1);
                                UnitMemoryScenario.of(unit).writeMemoryWord(MOVEMENT_DESTINATION_Y_OFFSET, EDGE_POSITION_Y);
                                UnitMemoryScenario.of(unit).writeMemoryInteger(MovementCheck.UNIT_STATUS_OFFSET, status);
                                UnitMemoryScenario.of(unit).writeMemoryByte(MovementCheck.UNIT_PATH_COLLISION_FLAGS_OFFSET, retainedFlags);
                                UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_FLAGS_OFFSET, MovementFlags.ORDERED);
                                UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_OFFSET, ScrOrderIds.GUARD);

                                game.getUnitMovement().initializeState(unit);

                                for (int offset = 0; offset < contents.length; offset++) {
                                    contents[offset] = (byte) (offset >= SPRITE_REFERENCE_OFFSET && offset < SPRITE_REFERENCE_END
                                                               ? 0
                                                               : UnitMemoryScenario.of(unit).readMemoryUnsignedByte(offset));
                                }

                                digest.update(contents);

                                count++;
                            }
                        }
                    }
                }
            }
        }

        Assertions.assertEquals(3240, count);
        Assertions.assertEquals("af383fa33ec41857fd28234a004f812e80f53e3848efa3338e4fc72d3ded73cd", HexFormat.of().formatHex(digest.digest()));
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void update_should_dispatch_new_worker_then_throw_at_unsupported_unstacking() {
        Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setSprite(SpriteScenario.createSprite(WORKER_SPRITE_TYPE, WORKER_POSITION, WORKER_POSITION, WORKER_ELEVATION));
        UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, ScrUnitIds.ZERG_DRONE);
        UnitMemoryScenario.of(unit).writeMemoryWord(MOVEMENT_DESTINATION_X_OFFSET, WORKER_POSITION);
        UnitMemoryScenario.of(unit).writeMemoryWord(MOVEMENT_DESTINATION_Y_OFFSET, WORKER_POSITION);
        UnitMemoryScenario.of(unit).writeMemoryWord(DIRECTION_TARGET_X_OFFSET, WORKER_POSITION);
        UnitMemoryScenario.of(unit).writeMemoryWord(DIRECTION_TARGET_Y_OFFSET, WORKER_POSITION);
        UnitMemoryScenario.of(unit).writeMemoryWord(MovementCheck.UNIT_POSITION_X_OFFSET, WORKER_POSITION);
        UnitMemoryScenario.of(unit).writeMemoryWord(MovementCheck.UNIT_POSITION_Y_OFFSET, WORKER_POSITION);
        UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_FLAGS_OFFSET, MovementFlags.ORDERED);
        UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_OFFSET, ScrOrderIds.GUARD);
        UnitMemoryScenario.of(unit).writeMemoryInteger(MovementCheck.UNIT_STATUS_OFFSET,
                                                       UnitStatusFlags.COMPLETED | UnitStatusFlags.CAN_TURN | UnitStatusFlags.CAN_MOVE |
                                                       UnitStatusFlags.COLLISION | UnitStatusFlags.GROUND_UNIT);
        UnitMemoryScenario.of(unit).writeMemoryByte(PATH_RECALCULATION_COUNTDOWN_OFFSET, WORKER_PATH_RECALCULATION_COUNTDOWN);

        UnitMovement.Context context = new UnitMovement.Context(WORKER_SOURCE_BYTE, WORKER_RESULT);

        game.getUnitMovement().update(context, unit, TRANSITION_LIMIT);

        Assertions.assertEquals(1, context.getResult());
        Assertions.assertEquals(MovementStates.CHECK_ILLEGAL, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(MOVEMENT_STATE_OFFSET));
        Assertions.assertEquals(UnitInitializer.GROUND_ELEVATION_PATH_FLAG,
                                UnitMemoryScenario.of(unit).readMemoryUnsignedByte(MovementCheck.UNIT_PATH_COLLISION_FLAGS_OFFSET));
        Assertions.assertEquals(0, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(PATH_RECALCULATION_COUNTDOWN_OFFSET));

        Assertions.assertThrows(UnsupportedOperationException.class, () -> game.getUnitMovement().update(context, unit, TRANSITION_LIMIT));

        Assertions.assertEquals(MovementStates.CHECK_ILLEGAL, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(MOVEMENT_STATE_OFFSET));
        Assertions.assertEquals(0x130001, UnitMemoryScenario.of(unit).readMemoryInteger(MovementCheck.UNIT_STATUS_OFFSET));
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void update_should_keep_inert_states_and_return_zero_only_for_hidden_or_zero_source() {
        Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        Unit unit = UnitTestUtils.createUnit(1);

        for (int movementState : new int[]{MovementStates.LUMP, MovementStates.HIDDEN, MovementStates.FAKE_FLYER, 36, 255}) {
            for (int sourceByte : new int[]{0, 1, 128, 255}) {
                UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_STATE_OFFSET, movementState);

                UnitMovement.Context context = new UnitMovement.Context(sourceByte, STALE_RESULT);

                game.getUnitMovement().update(context, unit, SINGLE_TRANSITION_LIMIT);

                Assertions.assertEquals(movementState == MovementStates.HIDDEN || sourceByte == 0 ? 0 : 1, context.getResult());
                Assertions.assertEquals(movementState, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(MOVEMENT_STATE_OFFSET));
            }
        }
    }

    @Test
    void matches_native_movement_query_and_blocking_predicate_cases() throws Exception {
        ByteBuffer contents = ByteBuffer.allocate(UnitsDat.UNIT_COUNT * UNIT_TYPE_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int unitType = 0; unitType < UnitsDat.UNIT_COUNT; unitType++) {
            int extents = UNIT_EXTENTS_OFFSET + unitType * UNIT_EXTENTS_SIZE;

            contents.putShort(extents, (short) (2 + unitType % 4));
            contents.putShort(extents + UP_EXTENT_OFFSET, (short) (2 + unitType % 3));
            contents.putShort(extents + RIGHT_EXTENT_OFFSET, (short) (3 + unitType % 5));
            contents.putShort(extents + DOWN_EXTENT_OFFSET, (short) (3 + unitType % 7));
        }

        DatTable units = new DatSchema().fieldU32("Special Ability Flags", UnitsDat.UNIT_COUNT)
                                        .fieldU16x4("Unit Size Left", "Unit Size Up", "Unit Size Right", "Unit Size Down", UnitsDat.UNIT_COUNT)
                                        .parse(contents.array());
        UnitPool pool = new UnitPool(QUERY_UNIT_COUNT);
        UnitSpatialIndex spatialIndex = new UnitSpatialIndex(pool);

        spatialIndex.initializeQueryBounds(UnitsDat.createTypeTable(units));

        for (int unitIndex = 0; unitIndex < QUERY_UNIT_COUNT; unitIndex++) {
            Unit unit = pool.getUnit(unitIndex + 1);

            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitIndex);
            UnitMemoryScenario.of(unit).writeMemoryInteger(MovementCheck.UNIT_STATUS_OFFSET,
                                                           new int[]{0,
                                                                     UnitStatusFlags.GROUNDED_BUILDING,
                                                                     UnitStatusFlags.NO_COLLIDE,
                                                                     UnitStatusFlags.GATHERING,
                                                                     0x800002}[unitIndex % 5]);
            UnitMemoryScenario.of(unit).writeMemoryByte(MovementCheck.UNIT_PATH_COLLISION_FLAGS_OFFSET, new int[]{0, 1, 2, 255}[unitIndex % 4]);
            UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_OFFSET,
                                                        new int[]{ScrOrderIds.DIE,
                                                                  ScrOrderIds.WAIT_FOR_GAS,
                                                                  ScrOrderIds.RETURN_GAS}[unitIndex % 3]);
            unit.setSprite(SpriteScenario.createSprite(0,
                                                       QUERY_GRID_ORIGIN + unitIndex % QUERY_GRID_COLUMNS * QUERY_GRID_SPACING,
                                                       QUERY_GRID_ORIGIN + unitIndex / QUERY_GRID_COLUMNS * QUERY_GRID_SPACING,
                                                       0));
            spatialIndex.insert(unit, UnitsDat.createTypeTable(units));
        }

        Unit incoming = UnitTestUtils.createUnit(QUERY_UNIT_COUNT + 1);
        UnitCollisionRules rules = UnitTestUtils.createCollisionRules();
        MessageDigest queryDigest = MessageDigest.getInstance(MovementCheck.DIGEST_ALGORITHM);
        MessageDigest predicateDigest = MessageDigest.getInstance(MovementCheck.DIGEST_ALGORITHM);
        ByteBuffer result = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        int found = 0;

        for (int unitType : new int[]{ScrUnitIds.TERRAN_MARINE,
                                      ScrUnitIds.ZERG_LARVA,
                                      ScrUnitIds.ZERG_DRONE,
                                      ScrUnitIds.PROTOSS_PROBE}) {
            UnitMemoryScenario.of(incoming).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);

            for (int status : new int[]{0, UnitStatusFlags.GATHERING}) {
                UnitMemoryScenario.of(incoming).writeMemoryInteger(MovementCheck.UNIT_STATUS_OFFSET, status);

                for (int order : new int[]{ScrOrderIds.DIE, ScrOrderIds.RETURN_GAS}) {
                    UnitMemoryScenario.of(incoming).writeMemoryByte(PRIMARY_ORDER_OFFSET, order);

                    for (int candidateIndex = 1; candidateIndex <= QUERY_UNIT_COUNT; candidateIndex++) {
                        Unit candidate = pool.getUnit(candidateIndex);

                        result.putInt(0, rules.isBlockingMovement(candidate, incoming) ? 1 : 0);
                        predicateDigest.update(result.array());

                        Assertions.assertFalse(rules.isBlockingMovement(candidate, candidate));
                    }

                    for (int horizontal : new int[]{60, 64, 69, 75, 80, 85, 90, 100}) {
                        for (int vertical : new int[]{60, 64, 69, 75, 80, 85, 90, 100}) {
                            Unit selected = spatialIndex.findMovementBlocker(rules, incoming, UnitsDat.createTypeTable(units), horizontal, vertical);

                            result.putInt(0, selected == null ? 0 : selected.getIndex());
                            queryDigest.update(result.array());

                            if (selected != null) {
                                found++;
                            }
                        }
                    }
                }
            }
        }

        Assertions.assertEquals(683, found);
        Assertions.assertEquals("e3af1da94399b0af8fbd0ccc739411cf0e32090c5957a0e04bb706f4755b5bac", HexFormat.of().formatHex(queryDigest.digest()));
        Assertions.assertEquals("5a8ef2ca7bffa07c812ca8562ac3f1d40e0dbe2a665101b748b347ff797baa2d",
                                HexFormat.of().formatHex(predicateDigest.digest()));
    }

    @Test
    void matches_native_clear_state_twelve_cases() throws Exception {
        GameMap map = WalkableTerrainScenario.createWalkableMap(WALKABLE_MAP_DIMENSION);
        Game game = Game.create(gameData, map, new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(1);
        game.initializeUnitSpatialIndex();

        UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(spatialIndex);
        Assertions.assertNotNull(gameData);

        spatialIndex.initializeQueryBounds(gameData.getUnitTypes());
        game.getPathSystem().setTerrainContours(WalkableTerrainScenario.createWalkableContours(map));

        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(1);

        unit.setSprite(SpriteScenario.createSprite(0, UNIT_POSITION, CLEAR_STATE_POSITION_Y, 0));

        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_CLEAR_STATE_TWELVE);

        Assertions.assertEquals(CLEAR_STATE_TWELVE_CAPTURE_HASH, MovementCheck.formatDigest(capture));

        ByteBuffer expected = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);
        byte[] expectedUnit = new byte[MovementCheck.UNIT_CAPTURE_SIZE];

        for (int unitType : new int[]{
                ScrUnitIds.TERRAN_MARINE,
                ScrUnitIds.TERRAN_SCV,
                ScrUnitIds.ZERG_LARVA,
                ScrUnitIds.ZERG_DRONE,
                ScrUnitIds.PROTOSS_PROBE,
                ScrUnitIds.RESOURCE_MINERAL_FIELD
        }) {
            for (int retainedFlags : new int[]{0, 1, 7, 255}) {
                for (int currentSpeed : new int[]{0, 127, -1, Integer.MIN_VALUE}) {
                    for (int status : new int[]{0x170001, 0x70001}) {
                        for (int pulse : new int[]{0, 7}) {
                            for (int offset = 0; offset < MovementCheck.UNIT_CAPTURE_SIZE; offset++) {
                                UnitMemoryScenario.of(unit).writeMemoryByte(offset, FILL_BYTE);
                            }

                            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);
                            UnitMemoryScenario.of(unit).writeMemoryInteger(MovementCheck.UNIT_STATUS_OFFSET, status);
                            UnitMemoryScenario.of(unit).writeMemoryByte(MovementCheck.UNIT_PATH_COLLISION_FLAGS_OFFSET, retainedFlags);
                            UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_STATE_OFFSET, MovementStates.CHECK_ILLEGAL);
                            UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_FLAGS_OFFSET, MovementFlags.ORDERED);
                            UnitMemoryScenario.of(unit).writeMemoryInteger(CURRENT_SPEED_OFFSET, currentSpeed);

                            for (int offset : new int[]{MOVEMENT_DESTINATION_X_OFFSET, MOVEMENT_WAYPOINT_X_OFFSET, DIRECTION_TARGET_X_OFFSET,
                                                        MovementCheck.UNIT_POSITION_X_OFFSET}) {
                                UnitMemoryScenario.of(unit).writeMemoryWord(offset, UNIT_POSITION);
                                UnitMemoryScenario.of(unit).writeMemoryWord(offset + Short.BYTES, CLEAR_STATE_POSITION_Y);
                            }

                            UnitMovement.Context context = new UnitMovement.Context(pulse, 0);

                            // Bind the sprite after the raw memory writes, which replace the resolved sprite reference.
                            unit.setSprite(SpriteScenario.createSprite(0, UNIT_POSITION, CLEAR_STATE_POSITION_Y, 0));

                            for (int invocation = 0; invocation < CLEAR_STATE_INVOCATION_COUNT; invocation++) {
                                game.getUnitMovement().update(context, unit, TRANSITION_LIMIT);

                                expected.get(expectedUnit);

                                UnitDiagnosticAssert.assertDomainEquals(expectedUnit, unit, "State twelve invocation " + invocation);

                                Assertions.assertEquals(MovementStates.DORMANT, unit.getMovementState());
                                Assertions.assertEquals(expected.getInt(), context.getResult());
                            }
                        }
                    }
                }
            }
        }

        Assertions.assertFalse(expected.hasRemaining());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void matches_native_first_movement_update_cases() throws IOException, NoSuchAlgorithmException {
        byte[] input = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_FIRST_MOVEMENT_UPDATE_INPUT);
        ByteBuffer source = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN);
        int count = source.getInt();

        Assertions.assertEquals(135, count);

        Game game = Game.create(gameData, new GameMap(LARGE_MAP_DIMENSION, LARGE_MAP_DIMENSION, 0, new int[LARGE_MAP_TILE_COUNT]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        game.initializeUnitSpatialIndex();

        UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(spatialIndex);

        spatialIndex.initializeFlyingUnits();
        game.getSpriteSystem().initializeSpritePool(count);

        MessageDigest digest = MessageDigest.getInstance(MovementCheck.DIGEST_ALGORITHM);
        byte[] contents = new byte[MovementCheck.UNIT_CAPTURE_SIZE];
        UnitMovement.Context context = new UnitMovement.Context(FIRST_UPDATE_SOURCE_BYTE, 0);

        for (int index = 0; index < count; index++) {
            Unit unit = UnitTestUtils.createUnit(source.getInt());

            source.getInt();
            source.getInt();
            source.getInt();

            int positionX = source.getInt();
            int positionY = source.getInt();

            source.get(contents);

            for (int offset = 0; offset < contents.length; offset++) {
                UnitMemoryScenario.of(unit).writeMemoryByte(offset, Byte.toUnsignedInt(contents[offset]));
            }

            SpritePool spritePool = game.getSpriteSystem().getSpritePool();

            Assertions.assertNotNull(spritePool);

            Sprite sprite = spritePool.allocate();

            Assertions.assertNotNull(sprite);

            int spriteType = source.getInt();
            int elevation = source.getInt();
            int flags = source.getInt();
            int owner = source.getInt();

            sprite.initializeNativeStorage(spriteType, positionX, positionY, owner, true);
            sprite.setElevationLevel(elevation);
            sprite.setFlags(flags);
            spritePool.attachToRow(sprite, (short) positionY / SPRITE_ROW_HEIGHT);
            unit.setSprite(sprite);

            if (UnitMemoryScenario.of(unit).readMemoryUnsignedWord(UNIT_TYPE_OFFSET) == ScrUnitIds.ZERG_OVERLORD) {
                int repulsionCellY = UnitMemoryScenario.of(unit).readMemoryUnsignedByte(REPULSION_CELL_Y_OFFSET);
                int repulsionCellX = UnitMemoryScenario.of(unit).readMemoryUnsignedByte(REPULSION_CELL_X_OFFSET);
                int cellIndex = repulsionCellY * UnitSpatialIndex.FLYING_GRID_DIMENSION + repulsionCellX;
                byte[] flyingUnitCounts = spatialIndex.getFlyingUnitCounts();

                Assertions.assertNotNull(flyingUnitCounts);

                flyingUnitCounts[cellIndex] = 1;
            }

            game.getUnitMovement().update(context, unit, TRANSITION_LIMIT);

            for (int offset = 0; offset < contents.length; offset++) {
                contents[offset] = (byte) (offset >= SPRITE_REFERENCE_OFFSET && offset < SPRITE_REFERENCE_END
                                           ? 0
                                           : UnitMemoryScenario.of(unit).readMemoryUnsignedByte(offset));
            }

            digest.update(contents);

            if (UnitMemoryScenario.of(unit).readMemoryUnsignedWord(UNIT_TYPE_OFFSET) == ScrUnitIds.ZERG_OVERLORD) {
                byte[] nativeScratch = HexFormat.of().parseHex("7d0ea5a55101a5a57d0ea5a55101a5a5007d0e00005101000000000000000101");

                MovementCheck.assertState(nativeScratch, context);
            }
        }

        Assertions.assertFalse(source.hasRemaining());
        Assertions.assertEquals("690d5c04ea45f6e2074399945cb347cf655c38e535b562bbeebff3c196d42014", HexFormat.of().formatHex(digest.digest()));
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());

        int retainedPosition = context.getPositionX();
        int retainedFlags = context.getPostMovementFlags();

        context.setSourceByte(WRAPPED_SOURCE_BYTE);

        Assertions.assertEquals(0, context.getSourceByte());
        Assertions.assertEquals(retainedPosition, context.getPositionX());
        Assertions.assertEquals(retainedFlags, context.getPostMovementFlags());
    }
}
