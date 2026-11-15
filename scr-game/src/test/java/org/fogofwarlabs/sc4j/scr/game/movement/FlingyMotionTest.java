package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.scr.data.format.dat.DatSchema;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.FlingyDat;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitDiagnosticAssert;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitMemoryScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks the engine motion algorithm ({@link FlingyMotionUtils}) on SC:R units over their {@code flingy.dat} values ({@link ScrFlingyProperties}):
 * the facing and motion updates against native captures, and the stopping distance arithmetic.
 */
final class FlingyMotionTest {
    /**
     * The SHA-256 digest of the native unit facing capture.
     */
    private static final @NotNull String UNIT_FACING_CAPTURE_HASH = "511f6afc3d28fb24552ba10156bcb70369a70a7c5fbb7d865637a6448e5b3849";
    /**
     * The SHA-256 digest of the native unit motion capture.
     */
    private static final @NotNull String UNIT_MOTION_CAPTURE_HASH = "b439ac8d50f99095abab60fccdef32717e17b8d92a7f7804f8fe50249a2bdc77";

    private static final int FACING_CASE_COUNT = 4096;
    private static final int MOTION_CASE_COUNT = 1024;

    private static final int FLINGY_RECORD_SIZE = Integer.BYTES + Short.BYTES + Integer.BYTES;
    private static final int TOP_SPEED = 1024;
    private static final int ACCELERATION = 64;
    private static final int HALT_DISTANCE = 8192;

    /**
     * A next speed below the flingy top speed.
     */
    private static final int SLOW_SPEED = 512;
    /**
     * The stopping distance at {@link #SLOW_SPEED} and the flingy acceleration: the squared speed divided by twice the acceleration.
     */
    private static final int SLOW_HALT_DISTANCE = 2048;
    /**
     * A next speed whose square exceeds the signed DWORD range.
     */
    private static final int OVERFLOWING_SPEED = 50000;
    /**
     * The stopping distance at {@link #OVERFLOWING_SPEED} and an acceleration of {@code 1}: the squared speed wrapped to a signed DWORD, halved.
     */
    private static final int OVERFLOWING_HALT_DISTANCE = -897483648;

    /**
     * Creates the flingy type values of a single flingy type with {@link #TOP_SPEED}, {@link #ACCELERATION} and {@link #HALT_DISTANCE}, without a
     * game content.
     *
     * @return the created flingy type values
     * @throws IOException if the flingy table cannot be parsed
     */
    private static @NotNull ScrFlingyProperties createFlingyTypes() throws IOException {
        ByteBuffer table = ByteBuffer.allocate(FLINGY_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        table.putInt(TOP_SPEED).putShort((short) ACCELERATION).putInt(HALT_DISTANCE);

        DatTable flingy = new DatSchema().fieldU32("Top Speed", 1).fieldU16("Acceleration", 1).fieldU32("Halt Distance", 1).parse(table.array());

        return new ScrFlingyProperties(FlingyDat.createTypeTable(flingy), null);
    }


    @Test
    void matches_native_unit_facing_cases() throws Exception {
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_UNIT_FACING);

        Assertions.assertEquals(UNIT_FACING_CAPTURE_HASH, MovementCheck.formatDigest(capture));

        ByteBuffer records = ByteBuffer.wrap(capture);
        ScrFlingyProperties flingyTypes = createFlingyTypes();

        for (int caseIndex = 0; caseIndex < FACING_CASE_COUNT; caseIndex++) {
            Unit unit = UnitTestUtils.createUnit(1);

            for (int offset = 0; offset < MovementCheck.UNIT_CAPTURE_SIZE; offset++) {
                UnitMemoryScenario.of(unit).writeMemoryByte(offset, records.get());
            }

            byte[] expected = new byte[MovementCheck.UNIT_CAPTURE_SIZE];

            records.get(expected);

            FlingyMotionUtils.updateFacing(unit, flingyTypes);

            UnitDiagnosticAssert.assertDomainEquals(expected, unit, "Facing case " + caseIndex);
        }

        Assertions.assertFalse(records.hasRemaining());
    }

    @Test
    void matches_native_unit_motion_cases() throws Exception {
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_UNIT_MOTION);

        Assertions.assertEquals(UNIT_MOTION_CAPTURE_HASH, MovementCheck.formatDigest(capture));

        ByteBuffer records = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);
        ScrFlingyProperties flingyTypes = createFlingyTypes();

        for (int caseIndex = 0; caseIndex < MOTION_CASE_COUNT; caseIndex++) {
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

            FlingyMotionUtils.update(context, unit, flingyTypes);

            UnitDiagnosticAssert.assertDomainEquals(expectedUnit, unit, "Unit case " + caseIndex);
            MovementCheck.assertState(expectedScratch, context);
        }

        Assertions.assertFalse(records.hasRemaining());
    }

    @Test
    void calculate_stopping_distance_should_divide_in_signed_dword_arithmetic() throws IOException {
        ScrFlingyProperties flingyTypes = createFlingyTypes();
        int flingyControl = ScrFlingyTypeView.MOVEMENT_CONTROL_FLINGY;
        int scriptControl = ScrFlingyTypeView.MOVEMENT_CONTROL_ISCRIPT;

        Assertions.assertEquals(0, calculateStoppingDistance(flingyTypes, flingyControl, 0, ACCELERATION));
        Assertions.assertEquals(0, calculateStoppingDistance(flingyTypes, scriptControl, SLOW_SPEED, ACCELERATION));
        Assertions.assertEquals(HALT_DISTANCE, calculateStoppingDistance(flingyTypes, flingyControl, TOP_SPEED, ACCELERATION));
        Assertions.assertEquals(SLOW_HALT_DISTANCE, calculateStoppingDistance(flingyTypes, flingyControl, SLOW_SPEED, ACCELERATION));
        Assertions.assertEquals(SLOW_HALT_DISTANCE, calculateStoppingDistance(flingyTypes, flingyControl, -SLOW_SPEED, ACCELERATION));
        Assertions.assertEquals(OVERFLOWING_HALT_DISTANCE, calculateStoppingDistance(flingyTypes, flingyControl, OVERFLOWING_SPEED, 1));
    }

    private static int calculateStoppingDistance(@NotNull ScrFlingyProperties flingyTypes, int movementControl, int speed, int acceleration) {
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setFlingyType(0);
        unit.setMovementControl(movementControl);
        unit.setNextSpeed(speed);
        unit.setAcceleration(acceleration);

        return FlingyMotionUtils.calculateStoppingDistance(flingyTypes, unit);
    }
}
