package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.scr.net.provider.ProviderPeer;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderSession;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Exercises lobby latency bars and round-trip measurements between in-memory clients.
 */
final class LanLatencyTest {
    private static final int TURN_RATE = 24;

    private static final int LATENCY = 30;
    private static final int SLOWER_LATENCY = 70;

    private static final int MEASURE_MILLISECONDS = 12_000;
    private static final int JOIN_LIMIT_MILLISECONDS = 5000;
    private static final int ANSWER_MILLISECONDS = 200;

    private static final int MAXIMUM_LATENCY_BARS = 5;
    private static final int THRESHOLDS = MAXIMUM_LATENCY_BARS - 1;

    private static final int TWO_CLIENTS = 2;
    private static final int THREE_CLIENTS = 3;
    private static final int HOST_CLIENT = 0;
    private static final int JOINER_CLIENT = 1;
    private static final int SECOND_JOINER_CLIENT = 2;

    private static final int JOINER_PLAYER = 1;
    private static final int NO_PLAYER = -1;

    /**
     * A turn rate without a threshold table, the rate of the first table, and a round trip to compare them at.
     */
    private static final int UNTABLED_TURN_RATE = 18;
    private static final int FIRST_TABLE_TURN_RATE = 0;
    private static final int ROUND_TRIP = 300;

    private static @NotNull ProviderPeer requirePeer(@NotNull ProviderSession session, int player) {
        ProviderPeer peer = session.findPeer(player);

        Assertions.assertNotNull(peer, "no peer of player " + player);

        return peer;
    }


    /**
     * Checks that a player whose first ping has no answer yet shows one bar; the answer {@code 60} milliseconds later shows five.
     */
    @Test
    void get_latency_bars_should_return_one_bar_for_a_player_without_an_answer() {
        InMemoryLanScenario network = new InMemoryLanScenario(TWO_CLIENTS);

        network.latency = LATENCY;
        network.host(LanMapTransferTest.createOpenLobbySetup(TWO_CLIENTS, "map.scm", 0, 0), LanGameOptions.MELEE.encode(), null);
        network.sessions[JOINER_CLIENT].beginJoin(network.addresses[HOST_CLIENT], "joiner", "", "");

        Assertions.assertEquals(0, LanClient.getLatencyBars(network.sessions[HOST_CLIENT], JOINER_PLAYER, TURN_RATE), "no player 1 yet");

        network.runUntil(JOIN_LIMIT_MILLISECONDS, lan -> {
            lan.sessions[JOINER_CLIENT].pollJoin();

            return lan.sessions[HOST_CLIENT].findPeer(JOINER_PLAYER) != null;
        });

        Assertions.assertFalse(requirePeer(network.sessions[HOST_CLIENT], JOINER_PLAYER).hasRoundTripTime());
        Assertions.assertEquals(1, LanClient.getLatencyBars(network.sessions[HOST_CLIENT], JOINER_PLAYER, TURN_RATE));

        network.run(ANSWER_MILLISECONDS);

        Assertions.assertEquals(MAXIMUM_LATENCY_BARS, LanClient.getLatencyBars(network.sessions[HOST_CLIENT], JOINER_PLAYER, TURN_RATE));
    }

    /**
     * Checks the latency thresholds for each turn rate.
     */
    @Test
    void get_latency_bars_should_follow_the_native_thresholds() {
        int[][] cases = {{0, 5}, {93, 5}, {94, 4}, {124, 4}, {125, 3}, {166, 3}, {167, 2}, {207, 2}, {208, 1}, {100_000, 1}, {-1, 1}};
        int[][] rates = {
                {0, 187, 250, 400, 625},
                {8, 281, 375, 500, 625},
                {10, 225, 300, 400, 500},
                {12, 188, 250, 333, 417},
                {14, 161, 214, 286, 357},
                {16, 141, 188, 250, 313},
                {20, 113, 150, 200, 250},
                {24, 94, 125, 167, 208}
        };

        for (int[] latencyCase : cases) {
            Assertions.assertEquals(latencyCase[1], LanClient.getLatencyBars(latencyCase[0], TURN_RATE),
                                    "round trip " + latencyCase[0] + " at rate 24");
        }

        for (int[] rate : rates) {
            for (int threshold = 0; threshold < THRESHOLDS; threshold++) {
                Assertions.assertEquals(MAXIMUM_LATENCY_BARS - threshold,
                                        LanClient.getLatencyBars(rate[1 + threshold] - 1, rate[0]),
                                        "rate " + rate[0] + " below " + rate[1 + threshold]);
                Assertions.assertEquals(THRESHOLDS - threshold,
                                        LanClient.getLatencyBars(rate[1 + threshold], rate[0]),
                                        "rate " + rate[0] + " at " + rate[1 + threshold]);
            }
        }

        // A rate without a table takes the first one (rate 0).
        Assertions.assertEquals(LanClient.getLatencyBars(ROUND_TRIP, FIRST_TABLE_TURN_RATE),
                                LanClient.getLatencyBars(ROUND_TRIP, UNTABLED_TURN_RATE));
    }

    /**
     * Checks three clients with {@code 30} milliseconds between any two: a new peer is pinged at once (its ping time starts at {@code 0}), so
     * every client shows the round trip of {@code 60} milliseconds (five bars at rate {@code 24}) for every other player; none for the own
     * slot. With {@code 70} milliseconds the next keep-alive shows {@code 140} milliseconds: three bars, on every client. A player without an
     * answer yet shows one bar.
     */
    @Test
    void every_client_measures_every_other_player_and_the_bars_follow_the_link() {
        int clients = THREE_CLIENTS;
        InMemoryLanScenario network = new InMemoryLanScenario(clients);
        int[] players = new int[clients];

        network.latency = LATENCY;
        network.host(LanMapTransferTest.createOpenLobbySetup(clients, "map.scm", 0, 0), LanGameOptions.MELEE.encode(), null);
        network.join(JOINER_CLIENT, null, null);
        network.join(SECOND_JOINER_CLIENT, null, null);

        for (int index = 0; index < clients; index++) {
            players[index] = network.sessions[index].getLocalIdentifier();
        }

        network.run(MEASURE_MILLISECONDS);

        for (int index = 0; index < clients; index++) {
            ProviderSession session = network.sessions[index];

            Assertions.assertEquals(0, LanClient.getLatencyBars(session, players[index], TURN_RATE), "no bars for the own slot");
            Assertions.assertEquals(0, LanClient.getLatencyBars(session, NO_PLAYER, TURN_RATE), "no bars without a player");

            for (int other = 0; other < clients; other++) {
                if (other == index) {
                    continue;
                }

                ProviderPeer peer = requirePeer(session, players[other]);

                Assertions.assertTrue(peer.hasRoundTripTime(), "client " + index + " measured player " + players[other]);
                Assertions.assertEquals(2 * LATENCY, peer.getRoundTripTime(), "client " + index + " to player " + players[other]);
                Assertions.assertEquals(MAXIMUM_LATENCY_BARS, LanClient.getLatencyBars(session, players[other], TURN_RATE));
            }
        }

        network.latency = SLOWER_LATENCY;
        network.run(MEASURE_MILLISECONDS);

        for (int index = 0; index < clients; index++) {
            for (int other = 0; other < clients; other++) {
                if (other == index) {
                    continue;
                }

                Assertions.assertEquals(2 * SLOWER_LATENCY,
                                        requirePeer(network.sessions[index], players[other]).getRoundTripTime(),
                                        "client " + index + " to player " + players[other]);
                Assertions.assertEquals(3, LanClient.getLatencyBars(network.sessions[index], players[other], TURN_RATE));
            }
        }
    }
}
