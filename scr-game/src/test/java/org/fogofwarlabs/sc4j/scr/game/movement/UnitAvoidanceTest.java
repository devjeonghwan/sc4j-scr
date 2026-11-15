package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.scr.data.format.dat.DatSchema;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.FlingyDat;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitDiagnosticAssert;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitMemoryScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks the position prediction of {@link UnitAvoidance} against native captures.
 */
final class UnitAvoidanceTest {
    /**
     * The SHA-256 digest of the native unit position prediction capture.
     */
    private static final @NotNull String UNIT_PREDICTION_CAPTURE_HASH = "5a0d16f27d6491620bcc8c48f9dc2a6daf7498a31e8aa8bee6700c4dedf4595e";

    private static final int CASE_COUNT = 1024;
    private static final int PREDICTION_SAMPLE_COUNT = 3;

    private static final int TURN_RATE = 127;
    private static final int ACCELERATION = 127;

    @Test
    void matches_native_unit_prediction_cases() throws Exception {
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_UNIT_PREDICTION);

        Assertions.assertEquals(UNIT_PREDICTION_CAPTURE_HASH, MovementCheck.formatDigest(capture));

        ByteBuffer records = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);
        DatTable units = new DatSchema().fieldU8("Graphics", 1).parse(new byte[1]);
        DatTable flingy = new DatSchema().fieldU8("Turn Rate", 1).fieldU16("Acceleration", 1).parse(new byte[]{TURN_RATE, ACCELERATION, 0});
        int[] positions = new int[PREDICTION_SAMPLE_COUNT];

        for (int caseIndex = 0; caseIndex < CASE_COUNT; caseIndex++) {
            Unit unit = UnitTestUtils.createUnit(1);

            for (int offset = 0; offset < MovementCheck.UNIT_CAPTURE_SIZE; offset++) {
                UnitMemoryScenario.of(unit).writeMemoryByte(offset, records.get());
            }

            int horizontalPosition = (short) UnitMemoryScenario.of(unit).readMemoryUnsignedWord(MovementCheck.UNIT_POSITION_X_OFFSET);
            int verticalPosition = (short) UnitMemoryScenario.of(unit).readMemoryUnsignedWord(MovementCheck.UNIT_POSITION_Y_OFFSET);

            unit.setSprite(SpriteScenario.createSprite(0, horizontalPosition, verticalPosition, 0));

            int frameCount = records.getInt();

            Assertions.assertEquals(1, records.getInt());

            UnitAvoidance.predict(unit, UnitsDat.createTypeTable(units), FlingyDat.createTypeTable(flingy), frameCount, positions);

            for (int positionIndex = 0; positionIndex < PREDICTION_SAMPLE_COUNT; positionIndex++) {
                Assertions.assertEquals(records.getInt(), positions[positionIndex], "Prediction case " + caseIndex + " sample " + positionIndex);
            }

            byte[] expectedUnit = new byte[MovementCheck.UNIT_CAPTURE_SIZE];

            records.get(expectedUnit);

            UnitDiagnosticAssert.assertDomainEquals(expectedUnit, unit, "Retained unit case " + caseIndex);
        }

        Assertions.assertFalse(records.hasRemaining());
    }
}
