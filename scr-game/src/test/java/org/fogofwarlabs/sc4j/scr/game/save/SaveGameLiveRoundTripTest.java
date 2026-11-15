package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.CorpusTest;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.LiveMapGameScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/**
 * Checks live-game save round trips.
 */
@CorpusTest({ExternalInput.INSTALLATION, ExternalInput.MAPS})
final class SaveGameLiveRoundTripTest {
    private static final int SEED = 0x6AB9B92E;
    private static final int HUMAN_LOBBY_SLOT = 0;
    private static final @NotNull String PLAYER_NAME = "tester";
    private static final @NotNull String SCENARIO_PATH = "staredit\\scenario.chk";

    private static final @NotNull String MELEE_CASE_NAME = "melee";
    private static final int CONTINUE_FRAMES = 2000;
    private static final int MELEE_SAVE_FRAME = 6000;

    @Test
    void saved_melee_game_against_computers_resaves_and_continues_identically() throws Exception {
        Path installationDirectory = ExternalInput.INSTALLATION.getPath();
        Path mapPath = ExternalInput.MAPS.resolveFile("Download", "BloodBath.scm");

        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO_PATH);
        MeleeGameSetup setup = new MeleeGameSetup(mapData,
                                                  PLAYER_NAME, ScrRaceIds.ZERG, HUMAN_LOBBY_SLOT,
                                                  new int[]{ScrRaceIds.TERRAN, ScrRaceIds.PROTOSS, ScrRaceIds.ZERG},
                                                  GameSpeed.FASTEST);

        SaveGameRoundTripScenario.assumeSelected(MELEE_CASE_NAME);

        try (GameData gameData = GameData.loadFrom(installationDirectory)) {
            LiveGame savedLive = new LiveGame(setup, SEED);
            ReplayRuntime saved = LiveMapGameScenario.startLive(gameData, savedLive, mapPath);

            while (saved.getGame().getCurrentFrame() < MELEE_SAVE_FRAME) {
                LiveMapGameScenario.stepFrame(saved, savedLive);
            }

            SaveGameRoundTripScenario.Result result = SaveGameRoundTripScenario.compare(saved, savedLive, file -> {
                LiveGame loadedLive = SaveGameSession.read(file).createLiveGame();
                ReplayRuntime loaded = LiveMapGameScenario.startLive(gameData, loadedLive, mapPath);

                SaveGameUtils.read(file, new SaveGameContext(loaded, loadedLive));

                return new SaveGameRoundTripScenario.Loaded(loaded, loadedLive);
            }, CONTINUE_FRAMES, LiveMapGameScenario::stepFrame);

            System.out.println("SAVE LIVE ROUND TRIP " + MELEE_CASE_NAME + " @" + MELEE_SAVE_FRAME + ": " + result.describe());

            Assertions.assertTrue(result.isResavedIdentically(), () -> result.describe());
            Assertions.assertEquals(-1, result.getFirstHashMismatchFrame(), () -> result.describe());
        }
    }
}
