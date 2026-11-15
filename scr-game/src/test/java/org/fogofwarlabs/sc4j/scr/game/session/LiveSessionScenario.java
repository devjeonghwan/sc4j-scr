package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.engine.lockstep.LockstepTransport;
import org.fogofwarlabs.sc4j.engine.lockstep.PeerTurn;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Plays the live sessions of the session tests: reads the installed melee map they play, and runs one-player sessions over a loopback network whose
 * sent turns come back as the local player's own.
 */
final class LiveSessionScenario {
    /**
     * The installed two-player melee map the sessions play.
     */
    private static final @NotNull String MAP_NAME = "(2)Bottleneck.scm";
    /**
     * The scenario file of a map archive.
     */
    private static final @NotNull String SCENARIO_FILE = "staredit\\scenario.chk";

    /**
     * A network of one player whose sent turns come back as its own.
     */
    static final class Loopback implements LockstepTransport {
        private final @NotNull List<byte[]> sent;

        private int received;

        /**
         * Creates a {@link Loopback} instance with an empty ordered turn list.
         */
        Loopback() {
            this.sent = new ArrayList<>();

            this.received = 0;
        }

        /**
         * @return the local turns sent and not yet received back
         */
        @Override
        public int getTurnsInTransit() {
            return this.sent.size() - this.received;
        }


        /**
         * Sends the local player's next turn.
         *
         * @param turn the turn bytes
         */
        @Override
        public void sendTurn(byte @NotNull [] turn) {
            this.sent.add(turn);
        }

        /**
         * Takes the next turn of every participant once all of them arrived.
         * <p>
         * This fixture takes the oldest local turn not yet received.
         *
         * @return the participants' turns in execution order, or {@code null} while a participant's turn is missing
         */
        @Override
        public @Nullable List<@NotNull PeerTurn> receiveTurns() {
            if (this.received >= this.sent.size()) {
                return null;
            }

            return List.of(new PeerTurn(NetworkPeerUtils.getPeer(LiveGame.HUMAN_PLAYER_ID), this.sent.get(this.received++)));
        }
    }

    /**
     * Reads the scenario of the installed melee map, failing the calling test when the installation has no such map.
     *
     * @return the scenario bytes
     * @throws IOException if the installed scenario cannot be read
     */
    static byte @NotNull [] readBottleneckScenario() throws IOException {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP_NAME);

        return MpqArchive.open(mapPath).readFile(SCENARIO_FILE);
    }

    /**
     * Runs the requested host iterations.
     *
     * @param game       the live command-stream session
     * @param live       the shared game runtime
     * @param first      the first host iteration
     * @param iterations the number of iterations
     * @return the next host iteration
     */
    static int runIterations(@NotNull LiveGame game, @NotNull ReplayRuntime live, int first, int iterations) {
        for (int iteration = first; iteration < first + iterations; iteration++) {
            exchangeTurnAndStep(game, live, iteration);
        }

        return first + iterations;
    }

    /**
     * Exchanges a turn before stepping the runtime at the fixture host clock.
     *
     * @param game      the live command-stream session
     * @param live      the shared game runtime
     * @param iteration the host iteration
     */
    static void exchangeTurnAndStep(@NotNull LiveGame game, @NotNull ReplayRuntime live, int iteration) {
        Assertions.assertTrue(game.exchangeTurn(live.getGame().getCurrentFrame()));
        Assertions.assertTrue(live.step(GameSpeed.FASTEST_DURATION, iteration * GameSpeed.FASTEST_DURATION));
    }


    /**
     * Creates a {@link LiveSessionScenario} instance.
     */
    private LiveSessionScenario() {
    }
}
