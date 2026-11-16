package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.CorpusTest;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.LiveMapGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Plays an EUD map headless whose start triggers run Set Alliance Status, Set Mission Objectives and Defeat with an observer slot id as the
 * current player, and checks that the run does not fail.
 * <p>
 * The map is read from the client's maps folder ({@link ExternalInput#MAPS}).
 */
@CorpusTest({ExternalInput.INSTALLATION, ExternalInput.MAPS})
final class EudAllianceMapTest {
    private static final @NotNull String MAP_NAME = "록맨X_반역의바바_4.9.scx";
    private static final @NotNull String SCENARIO_FILE = "staredit\\scenario.chk";
    private static final @NotNull String PLAYER_NAME = "tester";
    private static final int GAME_SEED = 0x6AFB3E18;
    /**
     * The lobby slot of the local player.
     */
    private static final int HUMAN_LOBBY_SLOT = 4;
    /**
     * The frame the run reaches.
     */
    private static final int LAST_FRAME = 3000;

    @Test
    void map_runs_past_set_alliance_status_of_an_out_of_range_current_player() throws IOException {
        Path mapPath = ExternalInput.MAPS.resolveFile(MAP_NAME);
        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO_FILE);
        MeleeGameSetup setup = MeleeGameSetup.createUseMapSettings(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, HUMAN_LOBBY_SLOT, GameSpeed.FASTEST);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            LiveGame live = new LiveGame(setup, GAME_SEED);
            ReplayRuntime runtime = LiveMapGameScenario.startLive(gameData, live, mapPath);
            Game game = runtime.getGame();
            TriggerContext triggers = game.getTriggerContext();

            Assertions.assertNotNull(triggers);

            while (game.getCurrentFrame() < LAST_FRAME) {
                LiveMapGameScenario.stepFrame(runtime, live);

                Assertions.assertNull(runtime.getFailure(), () -> "frame " + game.getCurrentFrame());
                Assertions.assertFalse(triggers.hasEudFailure(), () -> "frame " + game.getCurrentFrame());
            }
        }
    }
}
