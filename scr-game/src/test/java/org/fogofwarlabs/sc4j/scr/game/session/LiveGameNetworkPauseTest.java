package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/**
 * Checks turn-stream pause limits and resumed frame execution.
 */
@RequiresInstallation
final class LiveGameNetworkPauseTest {
    private static final int MATCHED_PROFILE_SPEED = 4;
    private static final int MAXIMUM_WAIT_ITERATIONS = 20;

    private static final int TURNS_IN_TRANSIT = 2;
    /**
     * The pauses each player has at the start of a game.
     */
    private static final int INITIAL_PAUSES = 3;
    /**
     * The command types of the Pause and Resume commands.
     */
    private static final int PAUSE_COMMAND_TYPE = 0x10;
    private static final int RESUME_COMMAND_TYPE = 0x11;

    private static final @NotNull String PLAYER_NAME = "tester";
    private static final int GAME_SEED = 12345;

    @Test
    void network_pause_holds_frames_until_resume_and_limits_pauses_per_player() throws IOException {
        byte[] mapData = LiveSessionScenario.readBottleneckScenario();

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            LiveGame game = new LiveGame(MeleeGameSetup.createUseMapSettings(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, 0, MATCHED_PROFILE_SPEED),
                                         GAME_SEED);

            game.attachNetwork(new LiveSessionScenario.Loopback(), TURNS_IN_TRANSIT, true);

            ReplayRuntime live = ReplayRuntime.createLiveGame(game.getReplay(), ChkFile.parseTolerant(mapData), gameData,
                                                              game.getLocalPlayer(),
                                                              RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                              RunningGameScenario.INSTRUCTION_LIMIT);

            live.start(0, 0);

            Game state = live.getGame();

            game.startSession(state, live.getFrameTiming());

            int slot = game.getLocalPlayer();

            Assertions.assertEquals(INITIAL_PAUSES, game.getPausesLeft(slot));

            int iteration = LiveSessionScenario.runIterations(game, live, 0, 10);

            game.requestPause();
            iteration = this.runUntilPaused(game, live, iteration, true);

            List<PauseNotice> notices = game.takePauseNotices();

            Assertions.assertEquals(1, notices.size());
            Assertions.assertTrue(notices.getFirst().isPaused());
            Assertions.assertEquals(slot, notices.getFirst().getSlot());
            Assertions.assertEquals(INITIAL_PAUSES - 1, notices.getFirst().getPausesLeft());

            long heldFrame = state.getCurrentFrame();

            iteration = LiveSessionScenario.runIterations(game, live, iteration, 10);

            Assertions.assertEquals(heldFrame, state.getCurrentFrame(), "the paused game runs no frames while its turns go on");

            game.requestPause();
            iteration = LiveSessionScenario.runIterations(game, live, iteration, 5);

            Assertions.assertTrue(game.takePauseNotices().isEmpty(), "a pause of a paused game does nothing");

            game.requestResume();
            iteration = this.runUntilPaused(game, live, iteration, false);

            Assertions.assertFalse(game.takePauseNotices().getFirst().isPaused());

            LiveSessionScenario.runIterations(game, live, iteration, 3);

            Assertions.assertTrue(state.getCurrentFrame() > heldFrame, "frames run again after the resume");

            for (int pause = 1; pause < INITIAL_PAUSES; pause++) {
                game.requestPause();
                iteration = this.runUntilPaused(game, live, iteration, true);
                game.requestResume();
                iteration = this.runUntilPaused(game, live, iteration, false);
            }

            Assertions.assertEquals(0, game.getPausesLeft(slot));
            Assertions.assertFalse(game.canPause(slot), "three pauses per player");

            game.requestPause();
            LiveSessionScenario.runIterations(game, live, iteration, 5);

            Assertions.assertFalse(game.isPaused());
            Assertions.assertTrue(game.getHistory().stream().noneMatch(command -> command.getTypeId() == PAUSE_COMMAND_TYPE ||
                                                                                  command.getTypeId() == RESUME_COMMAND_TYPE),
                                  "Pause and Resume are not recorded");
        }
    }

    /**
     * Runs until the session reaches the requested pause state.
     *
     * @param game   the live command-stream session
     * @param live   the shared game runtime
     * @param first  the first host iteration
     * @param paused the awaited pause state
     * @return the next host iteration
     */
    private int runUntilPaused(@NotNull LiveGame game, @NotNull ReplayRuntime live, int first, boolean paused) {
        int iteration = first;

        while (game.isPaused() != paused) {
            Assertions.assertTrue(iteration < first + MAXIMUM_WAIT_ITERATIONS, "the command arrives with its turn");

            LiveSessionScenario.exchangeTurnAndStep(game, live, iteration++);
        }

        return iteration;
    }
}
