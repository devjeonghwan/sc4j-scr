package org.fogofwarlabs.sc4j.scr.game.path;

import org.fogofwarlabs.sc4j.engine.movement.MovementFlags;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatSchema;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.FlingyDat;
import org.fogofwarlabs.sc4j.scr.game.movement.FlingyMotionUtils;
import org.fogofwarlabs.sc4j.scr.game.movement.ScrFlingyProperties;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitMemoryScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Checks moving-target prediction, destination updates, movement guards and path timers against captured cases.
 */
final class UnitPathTest {
    /**
     * The SHA-256 digest of the native moving-target prediction capture.
     */
    private static final @NotNull String TARGET_PREDICTION_CAPTURE_HASH = "879ca2fa44853bf74295471f170b643be8caab595e9eb1ed4986bc86ac3323ed";
    /**
     * The SHA-256 digest of the native adjusted moving-target destination capture.
     */
    private static final @NotNull String ADJUSTED_DESTINATION_CAPTURE_HASH = "eff21748651c08865c162e1364567834eb58ba4129d0823fde2ae45ee5d9b3c5";
    /**
     * The SHA-256 digest of the native path movement guard capture.
     */
    private static final @NotNull String MOVEMENT_GUARD_CAPTURE_HASH = "fd782dcfd1be484aa0a2c8aabfa0ac8e867e4d94c3436c0a2f15e2b7454cdddb";
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";
    /**
     * The number of captured target prediction, adjusted destination and movement guard cases.
     */
    private static final int TARGET_PREDICTION_CASE_COUNT = 1024;
    private static final int ADJUSTED_DESTINATION_CASE_COUNT = 1024;
    private static final int MOVEMENT_GUARD_CASE_COUNT = 2048;

    /**
     * The offsets of the unit fields in a unit capture.
     */
    private static final int MOVEMENT_DESTINATION_OFFSET = 0x20;
    private static final int MOVEMENT_DESTINATION_Y_OFFSET = 0x22;
    private static final int MOVEMENT_WAYPOINT_OFFSET = 0x30;
    private static final int MOVEMENT_FLAGS_OFFSET = 0x38;
    private static final int TARGET_HEADING_OFFSET = 0x3B;
    private static final int MOVEMENT_CONTROL_OFFSET = 0x3F;
    private static final int NEXT_SPEED_OFFSET = 0x54;
    private static final int ACCELERATION_OFFSET = 0x60;
    private static final int MOVEMENT_STATE_OFFSET = 0xDB;
    private static final int STATUS_OFFSET = 0x140;

    /**
     * The size of a flingy record of the target prediction tests and the offsets of its fields.
     */
    private static final int FLINGY_RECORD_SIZE = 10;
    private static final int TOP_SPEED_OFFSET = 0;
    private static final int ACCELERATION_FIELD_OFFSET = 4;
    private static final int HALT_DISTANCE_OFFSET = 6;

    /**
     * The captured kinds of the movement target: none, and an accelerating target.
     */
    private static final int NO_TARGET_KIND = 0;
    private static final int ACCELERATING_TARGET_KIND = 2;
    /**
     * The mask of a position WORD and the shift of the vertical WORD in a packed position.
     */
    private static final int WORD_MASK = 0xFFFF;
    private static final int POSITION_VERTICAL_SHIFT = 16;
    /**
     * The ratio of the captured direction multiplier to the captured distance.
     */
    private static final int DISTANCE_MULTIPLIER_RATIO = 3;
    /**
     * The timer step between consecutive movement guard cases.
     */
    private static final int TIMER_CASE_STEP = 17;

    /**
     * Creates a {@link DatSchema} instance of the flingy fields the target prediction reads.
     *
     * @return the created {@link DatSchema} instance
     */
    private static @NotNull DatSchema createFlingySchema() {
        return new DatSchema().fieldU32("Top Speed", 1).fieldU16("Acceleration", 1).fieldU32("Halt Distance", 1);
    }


    @Test
    void matches_native_target_prediction_cases() throws Exception {
        byte[] bytes = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_TARGET_PREDICTION);

        Assertions.assertEquals(TARGET_PREDICTION_CAPTURE_HASH, HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(bytes)));

        ByteBuffer records = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        DatSchema schema = createFlingySchema();
        ByteBuffer table = ByteBuffer.allocate(FLINGY_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        Unit unit = UnitTestUtils.createUnit(1);

        for (int index = 0; index < TARGET_PREDICTION_CASE_COUNT; index++) {
            UnitMemoryScenario.of(unit).writeMemoryInteger(NEXT_SPEED_OFFSET, records.getInt());
            UnitMemoryScenario.of(unit).writeMemoryWord(ACCELERATION_OFFSET, records.getInt());
            UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_CONTROL_OFFSET, records.getInt());
            table.putInt(TOP_SPEED_OFFSET, records.getInt());
            table.putShort(ACCELERATION_FIELD_OFFSET, (short) records.getInt());
            table.putInt(HALT_DISTANCE_OFFSET, records.getInt());

            int direction = records.getInt();
            int distance = records.getInt();
            int multiplier = records.getInt();
            int horizontal = records.getInt();
            int vertical = records.getInt();

            Assertions.assertEquals(distance * DISTANCE_MULTIPLIER_RATIO, multiplier);

            DatTable flingy = schema.parse(table.array());
            long prediction = UnitPath.calculateTargetPrediction(unit, direction, new ScrFlingyProperties(FlingyDat.createTypeTable(flingy), null));

            Assertions.assertEquals(horizontal, (int) prediction, "Horizontal case " + index);
            Assertions.assertEquals(vertical, (int) (prediction >>> Integer.SIZE), "Vertical case " + index);
            Assertions.assertEquals(prediction, FlingyMotionUtils.calculateDirectionVector(direction, multiplier));
        }

        Assertions.assertFalse(records.hasRemaining());
    }

    @Test
    void matches_native_adjusted_destination_cases() throws Exception {
        byte[] bytes = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_ADJUSTED_DESTINATION);

        Assertions.assertEquals(ADJUSTED_DESTINATION_CAPTURE_HASH,
                                HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(bytes)));

        ByteBuffer records = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        DatSchema schema = createFlingySchema();
        ByteBuffer table = ByteBuffer.allocate(FLINGY_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        Unit unit = UnitTestUtils.createUnit(1);
        Unit target = UnitTestUtils.createUnit(2);
        Sprite sprite = SpriteScenario.createSprite(0, 0, 0, 0);

        target.setSprite(sprite);

        for (int index = 0; index < ADJUSTED_DESTINATION_CASE_COUNT; index++) {
            UnitMemoryScenario.of(unit).writeMemoryInteger(NEXT_SPEED_OFFSET, records.getInt());
            UnitMemoryScenario.of(unit).writeMemoryWord(ACCELERATION_OFFSET, records.getInt());
            UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_CONTROL_OFFSET, records.getInt());
            table.putInt(TOP_SPEED_OFFSET, records.getInt());
            table.putShort(ACCELERATION_FIELD_OFFSET, (short) records.getInt());
            table.putInt(HALT_DISTANCE_OFFSET, records.getInt());
            UnitMemoryScenario.of(target).writeMemoryByte(TARGET_HEADING_OFFSET, records.getInt());

            int targetKind = records.getInt();
            int targetFlags = targetKind == ACCELERATING_TARGET_KIND ? MovementFlags.ACCELERATING : 0;

            UnitMemoryScenario.of(target).writeMemoryByte(MOVEMENT_FLAGS_OFFSET, targetFlags);
            unit.setMovementTargetUnit(targetKind == NO_TARGET_KIND ? null : target);
            sprite.setX(records.getInt());
            sprite.setY(records.getInt());

            int width = records.getInt();
            int height = records.getInt();
            int horizontal = records.getInt();
            int vertical = records.getInt();
            int destination = horizontal & WORD_MASK | vertical << POSITION_VERTICAL_SHIFT;

            UnitMemoryScenario.of(unit).writeMemoryInteger(MOVEMENT_DESTINATION_OFFSET, records.getInt());
            UnitMemoryScenario.of(unit).writeMemoryInteger(MOVEMENT_WAYPOINT_OFFSET, records.getInt());
            UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_FLAGS_OFFSET, records.getInt());

            int expectedDestination = records.getInt();
            int expectedSecondary = records.getInt();
            int expectedFlags = records.getInt();
            boolean expectedTarget = records.getInt() != 0;

            ScrFlingyProperties flingyTypes = new ScrFlingyProperties(FlingyDat.createTypeTable(schema.parse(table.array())), null);

            UnitPath.applyAdjustedDestination(unit, flingyTypes, destination, width, height);

            Assertions.assertEquals(expectedDestination, UnitMemoryScenario.of(unit).readMemoryInteger(MOVEMENT_DESTINATION_OFFSET),
                                    "Destination case " + index);
            Assertions.assertEquals(expectedSecondary, UnitMemoryScenario.of(unit).readMemoryInteger(MOVEMENT_WAYPOINT_OFFSET),
                                    "Secondary case " + index);
            Assertions.assertEquals(expectedFlags, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(MOVEMENT_FLAGS_OFFSET), "Flags case " + index);
            Assertions.assertEquals(expectedTarget, unit.getMovementTargetUnit() != null, "Target case " + index);
        }

        Assertions.assertFalse(records.hasRemaining());
    }

    @Test
    void calculate_target_prediction_should_throw_when_acceleration_is_zero() throws Exception {
        Unit unit = UnitTestUtils.createUnit(1);

        UnitMemoryScenario.of(unit).writeMemoryInteger(NEXT_SPEED_OFFSET, 1);

        DatTable flingy = createFlingySchema().parse(new byte[FLINGY_RECORD_SIZE]);
        ScrFlingyProperties flingyTypes = new ScrFlingyProperties(FlingyDat.createTypeTable(flingy), null);

        Assertions.assertThrows(ArithmeticException.class, () -> UnitPath.calculateTargetPrediction(unit, 0, flingyTypes));
    }

    @RequiresInstallation
    @Test
    void matches_native_movement_guard_and_timer_cases() throws Exception {
        byte[] fixture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_MOVEMENT_GUARD);

        Assertions.assertEquals(MOVEMENT_GUARD_CAPTURE_HASH, HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(fixture)));

        ByteBuffer expected = ByteBuffer.wrap(fixture).order(ByteOrder.LITTLE_ENDIAN);

        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = Game.create(data, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

            for (int caseIndex = 0; caseIndex < MOVEMENT_GUARD_CASE_COUNT; caseIndex++) {
                Unit unit = UnitTestUtils.createUnit(1);

                unit.setSprite(SpriteScenario.createSprite(0, expected.getInt(), expected.getInt(), 0));
                UnitMemoryScenario.of(unit).writeMemoryWord(MOVEMENT_DESTINATION_OFFSET, expected.getInt());
                UnitMemoryScenario.of(unit).writeMemoryWord(MOVEMENT_DESTINATION_Y_OFFSET, expected.getInt());
                UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_FLAGS_OFFSET, expected.getInt());
                UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, expected.getInt());

                boolean hasPath = expected.getInt() != 0;
                int dirty = expected.getInt();

                UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_STATE_OFFSET, expected.getInt());

                boolean requirePath = expected.getInt() != 0;
                int timer = expected.getInt();
                PathPool pool = new PathPool();

                Assertions.assertEquals(1, pool.allocate());

                pool.setSourceX(1, 0);
                pool.setSourceY(1, 0);
                pool.setWaypointPosition(1, 0);
                pool.setFlags(1, dirty);
                pool.setTimer(1, timer);
                game.getPathSystem().setPathPool(pool);
                unit.setPathIndex(hasPath ? 1 : -1);

                Assertions.assertEquals(expected.getInt() != 0, UnitPath.updateMovementGuard(game, unit, requirePath), "Guard case " + caseIndex);

                Assertions.assertEquals(expected.getInt(), UnitMemoryScenario.of(unit).readMemoryUnsignedByte(MOVEMENT_STATE_OFFSET),
                                        "State case " + caseIndex);
                Assertions.assertEquals(expected.getInt() != 0, unit.getPathIndex() >= 0, "Path case " + caseIndex);
                Assertions.assertEquals(expected.getInt(), pool.getFirstFreeIndex(), "Head case " + caseIndex);
                Assertions.assertEquals(expected.getInt(), PathPoolAssert.readSourcePositionOrFreeLink(pool, 1), "Link case " + caseIndex);
                Assertions.assertEquals(expected.getInt(), pool.getWaypointPosition(1), "Identifier case " + caseIndex);

                Assertions.assertEquals(expected.getInt() != 0, UnitPath.updateTimer(game, unit), "Timer case " + caseIndex);

                Assertions.assertEquals(expected.getInt(), pool.getTimer(1), "Decrement case " + caseIndex);

                UnitPath.setTimer(game, unit, caseIndex * TIMER_CASE_STEP);

                Assertions.assertEquals(expected.getInt(), pool.getTimer(1), "Stored timer case " + caseIndex);
            }
        }

        Assertions.assertFalse(expected.hasRemaining());
    }
}
