package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.fogofwarlabs.sc4j.scr.game.frame.GameExecutionScope;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitMemoryScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
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
 * Compares speed fields with extracted native vectors and checks the initial context lifecycle.
 */
@RequiresInstallation
final class UnitSpeedTest {
    private static final int INSTRUCTION_LIMIT = 512;

    /**
     * The unit memory offsets of the speed fields and of the unit type WORD.
     */
    private static final int TURN_RATE_OFFSET = 0x3A;
    private static final int CURRENT_SPEED_OFFSET = 0x50;
    private static final int NEXT_SPEED_OFFSET = 0x54;
    private static final int ACCELERATION_OFFSET = 0x60;
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    private static final int SPEED_INCREASE_OFFSET = 0x1A1;
    private static final int SPEED_DECREASE_OFFSET = 0x1A2;
    /**
     * The largest speed increase or decrease byte.
     */
    private static final int MAXIMUM_SPEED_MODIFIER = 255;

    /**
     * The speed values of one digest record: the top speed DWORD, the acceleration WORD and the turn rate BYTE.
     */
    private static final int SPEED_RECORD_SIZE = Integer.BYTES + Short.BYTES + Byte.BYTES;
    private static final int RECORD_ACCELERATION_OFFSET = Integer.BYTES;
    private static final int RECORD_TURN_RATE_OFFSET = Integer.BYTES + Short.BYTES;
    /**
     * Speed values that the recalculation must keep.
     */
    private static final int RETAINED_CURRENT_SPEED = 0x12345678;
    private static final int RETAINED_NEXT_SPEED = 0x76543210;
    /**
     * The protected context value that decodes to zero with a zero integrity key.
     */
    private static final int ENCODED_ZERO_CONTEXT_VALUE = 0xD99C5310;

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
    void matches_native_speed_acceleration_and_turn_rate_cases() throws NoSuchAlgorithmException {
        Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        game.initializeExecutionScope();

        Unit unit = UnitTestUtils.createUnit(1);
        MessageDigest digest = MessageDigest.getInstance(MovementCheck.DIGEST_ALGORITHM);
        ByteBuffer values = ByteBuffer.allocate(SPEED_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        int count = 0;

        for (int unitType = 0; unitType < UnitsDat.UNIT_COUNT; unitType++) {
            Assertions.assertNotNull(gameData);

            int flingyType = gameData.getUnits().get("Graphics", unitType);

            if (gameData.getFlingy().get("Movement Control", flingyType) >= ScrFlingyTypeView.MOVEMENT_CONTROL_ISCRIPT) {
                continue;
            }

            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);

            for (int increase : new int[]{0, MAXIMUM_SPEED_MODIFIER}) {
                for (int decrease : new int[]{0, MAXIMUM_SPEED_MODIFIER}) {
                    for (int status : new int[]{0, UnitStatusFlags.SPEED_UPGRADE}) {
                        UnitMemoryScenario.of(unit).writeMemoryByte(SPEED_INCREASE_OFFSET, increase);
                        UnitMemoryScenario.of(unit).writeMemoryByte(SPEED_DECREASE_OFFSET, decrease);
                        UnitMemoryScenario.of(unit).writeMemoryInteger(MovementCheck.UNIT_STATUS_OFFSET, status);
                        UnitMemoryScenario.of(unit).writeMemoryInteger(CURRENT_SPEED_OFFSET, RETAINED_CURRENT_SPEED);
                        UnitMemoryScenario.of(unit).writeMemoryInteger(NEXT_SPEED_OFFSET, RETAINED_NEXT_SPEED);

                        game.getUnitSpeed().recalculate(unit, INSTRUCTION_LIMIT);

                        values.putInt(0, unit.getTopSpeed());
                        values.putShort(RECORD_ACCELERATION_OFFSET, (short) UnitMemoryScenario.of(unit).readMemoryUnsignedWord(ACCELERATION_OFFSET));
                        values.put(RECORD_TURN_RATE_OFFSET, (byte) UnitMemoryScenario.of(unit).readMemoryUnsignedByte(TURN_RATE_OFFSET));
                        digest.update(values.array());

                        Assertions.assertEquals(RETAINED_CURRENT_SPEED, unit.getCurrentSpeed());
                        Assertions.assertEquals(RETAINED_NEXT_SPEED, unit.getNextSpeed());

                        count++;
                    }
                }
            }
        }

        Assertions.assertEquals(624, count);
        Assertions.assertEquals("6a30a59d03a6987b8bf84b6144cb819af48ecbe50525bf9c223c133e617637b6", HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void recalculate_should_throw_outside_frame_phase_without_changing_unit() {
        Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        Unit unit = UnitTestUtils.createUnit(1);
        int unitType = 0;

        Assertions.assertNotNull(gameData);

        while (gameData.getFlingy().get("Movement Control", gameData.getUnits().get("Graphics", unitType)) >=
               ScrFlingyTypeView.MOVEMENT_CONTROL_ISCRIPT) {
            unitType++;
        }

        UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);

        byte[] before = UnitMemoryScenario.of(unit).exportMemoryBytes().clone();

        Assertions.assertThrows(UnsupportedOperationException.class, () -> game.getUnitSpeed().recalculate(unit, INSTRUCTION_LIMIT));

        Assertions.assertArrayEquals(before, UnitMemoryScenario.of(unit).exportMemoryBytes());

        Assertions.assertNull(game.getExecutionScope());

        game.initializeExecutionScope();

        GameExecutionScope executionScope = game.getExecutionScope();

        Assertions.assertNotNull(executionScope);
        Assertions.assertThrows(IllegalStateException.class, game::initializeExecutionScope);
        Assertions.assertFalse(ExecutableIntegrityCheck.matches(0, 0, 0));
        Assertions.assertArrayEquals(before, UnitMemoryScenario.of(unit).exportMemoryBytes());
        Assertions.assertTrue(executionScope.isInitializationActive());

        executionScope.finishInitialization();

        Assertions.assertFalse(executionScope.isInitializationActive());

        Assertions.assertThrows(UnsupportedOperationException.class, () -> game.getUnitSpeed().recalculate(unit, INSTRUCTION_LIMIT));

        Assertions.assertArrayEquals(before, UnitMemoryScenario.of(unit).exportMemoryBytes());
        Assertions.assertTrue(ExecutableIntegrityCheck.matches(0, ENCODED_ZERO_CONTEXT_VALUE, 0));

        executionScope.beginFramePhase(0);

        try {
            game.getUnitSpeed().recalculate(unit, INSTRUCTION_LIMIT);
        } finally {
            executionScope.endFramePhase();
        }

        Assertions.assertEquals(gameData.getFlingy().get("Top Speed", gameData.getUnits().get("Graphics", unitType)), unit.getTopSpeed());
    }
}
