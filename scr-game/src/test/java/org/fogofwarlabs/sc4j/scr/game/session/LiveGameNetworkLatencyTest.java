package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/**
 * Checks turn-stream latency changes and silent sender notifications.
 */
@RequiresInstallation
final class LiveGameNetworkLatencyTest {
    private static final int MATCHED_PROFILE_SPEED = 4;
    private static final int MAXIMUM_WAIT_ITERATIONS = 20;

    private static final int TURNS_IN_TRANSIT = 2;
    private static final int SILENT_LATENCY_BIT = 0x80;
    /**
     * The Extra High latency setting, which keeps two more turns in transit.
     */
    private static final int EXTRA_HIGH_LATENCY = 2;
    private static final int EXTRA_HIGH_LATENCY_TURNS = 2;
    /**
     * The command type of the Latency command.
     */
    private static final int LATENCY_COMMAND_TYPE = 0x55;

    private static final @NotNull String PLAYER_NAME = "tester";
    private static final int GAME_SEED = 12345;

    @Test
    void latency_command_raises_turns_in_transit_and_is_not_recorded() throws IOException {
        byte[] mapData = LiveSessionScenario.readBottleneckScenario();

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            LiveGame game = new LiveGame(MeleeGameSetup.createUseMapSettings(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, 0, MATCHED_PROFILE_SPEED),
                                         GAME_SEED);
            LiveSessionScenario.Loopback network = new LiveSessionScenario.Loopback();

            game.attachNetwork(network, TURNS_IN_TRANSIT, true);

            ReplayRuntime live = ReplayRuntime.createLiveGame(game.getReplay(), ChkFile.parseTolerant(mapData), gameData,
                                                              game.getLocalPlayer(),
                                                              RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                              RunningGameScenario.INSTRUCTION_LIMIT);

            live.start(0, 0);
            game.startSession(live.getGame(), live.getFrameTiming());

            Assertions.assertEquals(0, game.getLatency(), "every game starts at Low");

            int iteration = LiveSessionScenario.runIterations(game, live, 0, 5);

            Assertions.assertTrue(game.requestLatency(LiveGame.MAXIMUM_LATENCY));

            iteration = this.runUntilLatency(game, live, iteration, LiveGame.MAXIMUM_LATENCY);

            List<int[]> notices = game.takeLatencyNotices();

            Assertions.assertEquals(1, notices.size());
            Assertions.assertEquals(game.getLocalPlayer(), notices.getFirst()[0]);
            Assertions.assertEquals(EXTRA_HIGH_LATENCY, notices.getFirst()[1]);

            iteration = LiveSessionScenario.runIterations(game, live, iteration, 3);

            Assertions.assertEquals(TURNS_IN_TRANSIT + EXTRA_HIGH_LATENCY_TURNS, network.getTurnsInTransit(),
                                    "extra high keeps two more turns in transit");

            game.getOutgoingTurn().queue(new byte[]{ReplayCommandIds.LATENCY, (byte) (SILENT_LATENCY_BIT | 1)});
            this.runUntilLatency(game, live, iteration, 1);

            Assertions.assertTrue(game.takeLatencyNotices().isEmpty(), "a silent command names nobody");
            Assertions.assertTrue(game.getHistory().stream().noneMatch(command -> command.getTypeId() == LATENCY_COMMAND_TYPE),
                                  "Latency is not recorded");
        }
    }

    /**
     * Runs until the session reaches the requested latency.
     *
     * @param game    the live command-stream session
     * @param live    the shared game runtime
     * @param first   the first host iteration
     * @param latency the awaited latency setting
     * @return the next host iteration
     */
    private int runUntilLatency(@NotNull LiveGame game, @NotNull ReplayRuntime live, int first, int latency) {
        int iteration = first;

        while (game.getLatency() != latency) {
            Assertions.assertTrue(iteration < first + MAXIMUM_WAIT_ITERATIONS, "the command arrives with its turn");

            LiveSessionScenario.exchangeTurnAndStep(game, live, iteration++);
        }

        return iteration;
    }
}
