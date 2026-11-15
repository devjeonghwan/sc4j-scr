package org.fogofwarlabs.sc4j.scr.game.sprite.script;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.movement.MovementCheck;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitDiagnosticAssert;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitMemoryScenario;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks the position samples that {@link IscriptMotionPrediction} predicts from a unit's walking script against native captures.
 */
@RequiresInstallation
final class IscriptMotionPredictionTest {
    private static final int LARGE_MAP_DIMENSION = 128;
    private static final int LARGE_MAP_TILE_COUNT = LARGE_MAP_DIMENSION * LARGE_MAP_DIMENSION;
    private static final int PREDICTION_INSTRUCTION_LIMIT = 4096;

    private static final int PREDICTION_CASE_COUNT = 256;
    private static final int PREDICTION_SAMPLE_COUNT = 2;
    /**
     * The captured prediction scratch fields after the expected samples (three WORDs and two BYTEs), which have no observable effect.
     */
    private static final int PREDICTION_SCRATCH_SIZE = 3 * Short.BYTES + 2 * Byte.BYTES;
    /**
     * The SHA-256 digest of the prediction capture.
     */
    private static final @NotNull String PREDICTION_CAPTURE_HASH = "a9d84ed26c09f210638be86572b8a26380e3589ae46911007a0ffbb37eafe72d";
    /**
     * The pool index of the predicted unit.
     */
    private static final int UNIT_POOL_INDEX = 1;

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
    void matches_native_iscript_position_prediction_cases() throws Exception {
        byte[] capture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_ISCRIPT_PREDICTION);

        Assertions.assertEquals(PREDICTION_CAPTURE_HASH, MovementCheck.formatDigest(capture));

        ByteBuffer records = ByteBuffer.wrap(capture).order(ByteOrder.LITTLE_ENDIAN);
        Game game = Game.create(gameData, new GameMap(LARGE_MAP_DIMENSION, LARGE_MAP_DIMENSION, 0, new int[LARGE_MAP_TILE_COUNT]), new LcgRandom(0));
        IscriptMotionPrediction prediction = new IscriptMotionPrediction();

        for (int caseIndex = 0; caseIndex < PREDICTION_CASE_COUNT; caseIndex++) {
            int imageType = records.getInt();
            int scriptIdentifier = records.getInt();
            int walking = records.getInt();
            int frameCount = records.getInt();
            Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);
            byte[] contents = new byte[MovementCheck.UNIT_CAPTURE_SIZE];

            records.get(contents);

            for (int offset = 0; offset < MovementCheck.UNIT_CAPTURE_SIZE; offset++) {
                UnitMemoryScenario.of(unit).writeMemoryByte(offset, contents[offset]);
            }

            Sprite sprite = SpriteScenario.createSprite(0,
                                                        UnitMemoryScenario.of(unit).readMemoryUnsignedWord(MovementCheck.UNIT_POSITION_X_OFFSET),
                                                        UnitMemoryScenario.of(unit).readMemoryUnsignedWord(MovementCheck.UNIT_POSITION_Y_OFFSET),
                                                        0);

            unit.setSprite(sprite);

            SpriteImage image = SpriteScenario.createImage(imageType, 0, 0);

            image.setScriptIdentifier(scriptIdentifier);
            image.setProgramCounter(records.getInt());
            image.setReturnAddress(records.getInt());
            image.setAnimationType(records.getInt());
            image.setWait(records.getInt());
            sprite.setMainImage(image);

            int firstExpected = records.getInt();
            int secondExpected = records.getInt();

            records.position(records.position() + PREDICTION_SCRATCH_SIZE);

            int[] positions = new int[PREDICTION_SAMPLE_COUNT];

            prediction.predict(game, unit, frameCount, positions, PREDICTION_INSTRUCTION_LIMIT);

            Assertions.assertArrayEquals(new int[]{firstExpected, secondExpected}, positions, "Prediction case " + caseIndex);
            UnitDiagnosticAssert.assertDomainEquals(contents, unit, "Prediction unit case " + caseIndex);

            Assertions.assertEquals(walking, image.getProgramCounter(), "Source image case " + caseIndex);
        }

        Assertions.assertFalse(records.hasRemaining());
    }
}
