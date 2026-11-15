package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks normalized replay saves and identical continuation after repeated loading.
 */
@RequiresInstallation
final class SaveGameRoundTripTest {
    private static final int CONTINUE_FRAMES = 2000;

    @ParameterizedTest
    @CsvSource({GameTestResources.REPLAY_PVP_FORTRESS + ", 4000", GameTestResources.REPLAY_ZVP_FIGHTING_SPIRIT + ", 7000",
                GameTestResources.REPLAY_UMS_CAPTURE_THE_FLAG + ", 5000", GameTestResources.REPLAY_TERRAN_VS_COMPUTER + ", 5000"})
    void restored_replay_resaves_and_continues_identically(@NotNull String replayName, int saveFrame) throws Exception {
        SaveGameRoundTripScenario.assumeSelected(replayName);

        Path path = GameTestResources.getPath(replayName);
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(path));

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            SaveGameRoundTripScenario.Result result = SaveGameRoundTripScenario.runReplay(gameData, replay, saveFrame, CONTINUE_FRAMES);

            System.out.println("SAVE ROUND TRIP " + replayName + " @" + saveFrame + ": " + result.describe());

            Assertions.assertTrue(result.isResavedIdentically(), () -> result.describe());
            Assertions.assertEquals(-1, result.getFirstHashMismatchFrame(), () -> result.describe());
        }
    }
}
