package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.engine.lockstep.*;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.NetworkPeerUtils;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderPacket;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderPeer;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderSession;
import org.fogofwarlabs.sc4j.scr.net.proxy.GameProxySocket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the in-game turn stream over two {@link ProviderSession}s connected in memory: a peer that stops sending turns
 * opens the waiting dialog after {@code 2500} milliseconds with a {@code 45} second countdown; at zero its button drops the stalled peer, and
 * once its departure turn is reached the game continues with the peer's Leave Game command ahead of the turn's commands. The waiting players
 * are the awaited participants of the engine waiting state, and the drop is an engine departure. The started turn stream shortens the
 * keep-alive interval from the lobby's {@code 5} seconds to {@code 2} seconds.
 */
final class LanTurnTransportTest {
    private static final @NotNull InetSocketAddress HOST = new InetSocketAddress("192.168.0.88", GameProxySocket.GAME_PORT);
    private static final @NotNull InetSocketAddress JOINER = new InetSocketAddress("192.168.0.2", GameProxySocket.GAME_PORT);

    private static final int CLOCK_BASE = 10_000_000;
    private static final int HOST_PLAYER = 0;
    private static final int JOINER_PLAYER = 1;
    private static final int MAXIMUM_PLAYERS = 2;
    private static final int JOIN_STEPS = 20;

    /**
     * The silence before the waiting dialog opens.
     */
    private static final int WAIT_DELAY_MILLISECONDS = 2500;
    /**
     * The waiting dialog's countdown.
     */
    private static final int WAIT_COUNTDOWN_SECONDS = 45;
    /**
     * The nominal drop countdown of the engine waiting state once the peer is overdue: the dialog's countdown.
     */
    private static final long WAIT_COUNTDOWN_MILLISECONDS = 45_000L;
    /**
     * A little more than the countdown's second.
     */
    private static final int COUNTDOWN_STEP_MILLISECONDS = 1001;

    private static final int INITIAL_TURNS = 3;
    private static final int FRAME_MILLISECONDS = 42;
    private static final int LEAVE_FRAMES = 10;

    /**
     * The keep-alive interval of a started game.
     */
    private static final int GAME_KEEP_ALIVE_INTERVAL = 2000;
    /**
     * The lobby keep-alive intervals each phase of the keep-alive check runs.
     */
    private static final int KEEP_ALIVE_PERIODS = 3;

    private static final int NO_COUNTDOWN = -1;
    private static final int FLAG_DEPARTED = 8;

    private static final byte LEAVE_GAME = 0x57;
    /**
     * The reason byte of a leaving player's Leave Game command.
     */
    private static final byte LEAVE_REASON = 3;
    /**
     * The reason byte of a dropped player's Leave Game command.
     */
    private static final byte DROPPED_REASON = 6;

    private static final class Datagram {
        private final @NotNull InetSocketAddress source;
        private final @NotNull ProviderPacket packet;

        private Datagram(@NotNull InetSocketAddress source, @NotNull ProviderPacket packet) {
            this.source = source;
            this.packet = packet;
        }
    }

    private static void assertPingIntervals(@NotNull List<@NotNull Integer> pings, int interval) {
        Assertions.assertTrue(pings.size() > 1, "pings: " + pings);

        for (int index = 1; index < pings.size(); index++) {
            Assertions.assertEquals(interval, pings.get(index) - pings.get(index - 1), "pings: " + pings);
        }
    }

    private int now;

    private final @NotNull ArrayDeque<Datagram> toHost;
    private final @NotNull ArrayDeque<Datagram> toJoiner;

    private boolean joinerSilent;

    private final @NotNull List<@NotNull Integer> hostPings;
    private final @NotNull List<@NotNull Integer> joinerPings;

    private final @NotNull ProviderSession host;
    private final @NotNull ProviderSession joiner;

    LanTurnTransportTest() {
        this.now = CLOCK_BASE;

        this.toHost   = new ArrayDeque<>();
        this.toJoiner = new ArrayDeque<>();

        this.joinerSilent = false;

        this.hostPings   = new ArrayList<>();
        this.joinerPings = new ArrayList<>();

        this.host   = new ProviderSession((address, packet) -> {
            this.recordPing(this.hostPings, packet);
            this.toJoiner.add(new Datagram(HOST, packet));
        }, () -> this.now);
        this.joiner = new ProviderSession((address, packet) -> {
            if (!this.joinerSilent) {
                this.recordPing(this.joinerPings, packet);
                this.toHost.add(new Datagram(JOINER, packet));
            }
        }, () -> this.now);
    }

    private void recordPing(@NotNull List<@NotNull Integer> pings, @NotNull ProviderPacket packet) {
        if (packet.getType() == ProviderPacket.CONTROL && packet.getSubtype() == ProviderSession.SUBTYPE_PING) {
            pings.add(this.now);
        }
    }

    private void deliver() {
        while (!this.toHost.isEmpty() || !this.toJoiner.isEmpty()) {
            for (Datagram datagram = this.toHost.poll(); datagram != null; datagram = this.toHost.poll()) {
                this.host.receive(datagram.source, datagram.packet.getEncodedBytes());
            }

            for (Datagram datagram = this.toJoiner.poll(); datagram != null; datagram = this.toJoiner.poll()) {
                this.joiner.receive(datagram.source, datagram.packet.getEncodedBytes());
            }
        }

        this.host.update();
        this.joiner.update();
        this.joiner.pollJoin();
    }

    private void joinClients() {
        this.host.createGame("game", "statistics", "", MAXIMUM_PLAYERS, 0, 0, 0, new byte[]{1}, "host", "");
        this.joiner.beginJoin(HOST, "joiner", "", "");

        for (int step = 0; step < JOIN_STEPS; step++) {
            this.now++;
            this.deliver();
        }
    }

    @Test
    void silent_peer_is_waited_for_then_dropped_and_leaves_the_game() {
        this.joinClients();

        Assertions.assertEquals(ProviderSession.JoinState.JOINED, this.joiner.getJoinState());

        LanTurnTransport transport = new LanTurnTransport(this.host, this::deliver);

        // Both players' turns arrive: the turn completes with the players from slot 7 down.
        this.host.sendTurn(new byte[]{ReplayCommandIds.KEEP_ALIVE});
        this.joiner.sendTurn(new byte[]{ReplayCommandIds.KEEP_ALIVE});
        this.deliver();

        List<PeerTurn> turns = transport.receiveTurns();

        Assertions.assertNotNull(turns);
        Assertions.assertEquals(List.of(JOINER_PLAYER, HOST_PLAYER), turns.stream().map(turn -> turn.getPeer().getNumber()).toList());
        Assertions.assertEquals(NO_COUNTDOWN, transport.getWaitingPeers().getCountdownSeconds());

        // Without a client the game names no turn rate; the network's are the provider's caps.
        Assertions.assertEquals(0, transport.getTurnRate().getTurnsPerSecond());
        Assertions.assertEquals(ProviderSession.DEFAULT_TURNS_PER_SECOND, transport.getTurnRate().getNetworkTurnsPerSecond());
        Assertions.assertEquals(ProviderSession.DEFAULT_TURNS_IN_TRANSIT, transport.getTurnRate().getNetworkTurnsInTransit());

        // The joiner goes silent: no dialog before 2500 milliseconds, then 45 seconds counting down once a second.
        this.joinerSilent = true;

        this.host.sendTurn(new byte[]{ReplayCommandIds.KEEP_ALIVE});

        this.now += WAIT_DELAY_MILLISECONDS - 1;

        Assertions.assertNull(transport.receiveTurns());
        Assertions.assertEquals(NO_COUNTDOWN, transport.getWaitingPeers().getCountdownSeconds());

        this.now += 1;

        Assertions.assertNull(transport.receiveTurns());
        Assertions.assertEquals(WAIT_COUNTDOWN_SECONDS, transport.getWaitingPeers().getCountdownSeconds());

        for (int second = WAIT_COUNTDOWN_SECONDS - 1; second >= 1; second--) {
            this.now += COUNTDOWN_STEP_MILLISECONDS;

            Assertions.assertNull(transport.receiveTurns());
            Assertions.assertEquals(second, transport.getWaitingPeers().getCountdownSeconds());
        }

        // At zero the joiner (present, not responding) can be dropped at its expected turn.
        this.now += COUNTDOWN_STEP_MILLISECONDS;

        Assertions.assertNull(transport.receiveTurns());
        Assertions.assertEquals(0, transport.getWaitingPeers().getCountdownSeconds());

        // The dialog's Drop Players button, enabled at zero.
        transport.dropWaitingPeers();

        ProviderPeer dropped = this.host.getPeers().getFirst();

        Assertions.assertEquals(FLAG_DEPARTED, dropped.getFlags() & FLAG_DEPARTED);

        // Its departure is reached: the turn completes without it and starts with its Leave Game command (reason 6).
        turns = transport.receiveTurns();

        Assertions.assertNotNull(turns);
        Assertions.assertEquals(2, turns.size());
        Assertions.assertEquals(JOINER_PLAYER, turns.getFirst().getPeer().getNumber());
        Assertions.assertArrayEquals(new byte[]{LEAVE_GAME, DROPPED_REASON}, turns.getFirst().getTurn());
        Assertions.assertEquals(HOST_PLAYER, turns.get(1).getPeer().getNumber());
        Assertions.assertEquals(NO_COUNTDOWN, transport.getWaitingPeers().getCountdownSeconds());
        Assertions.assertFalse(transport.hasLeft());
    }

    /**
     * Checks that a defeat departure produces a Leave Game command without making the other player wait.
     */
    @Test
    void leaving_player_is_taken_as_left_without_waiting() {
        this.joinClients();

        LanTurnTransport hostTransport = new LanTurnTransport(this.host, this::deliver);
        LanTurnTransport joinerTransport = new LanTurnTransport(this.joiner, this::deliver);

        for (int turn = 0; turn < INITIAL_TURNS; turn++) {
            hostTransport.sendTurn(new byte[]{ReplayCommandIds.KEEP_ALIVE});
            joinerTransport.sendTurn(new byte[]{ReplayCommandIds.KEEP_ALIVE});
        }

        List<PeerTurn> turns = hostTransport.receiveTurns();

        Assertions.assertNotNull(turns);
        Assertions.assertNotNull(joinerTransport.receiveTurns());

        joinerTransport.leave(LiveGame.LEAVE_REASON);

        Assertions.assertTrue(joinerTransport.hasLeft());

        boolean left = false;

        for (int frame = 0; frame < LEAVE_FRAMES && !left; frame++) {
            this.now += FRAME_MILLISECONDS;

            hostTransport.sendTurn(new byte[]{ReplayCommandIds.KEEP_ALIVE});
            turns = hostTransport.receiveTurns();

            Assertions.assertNotNull(turns, "the host never waits for the leaving player");
            left = turns.getFirst().getPeer().getNumber() == JOINER_PLAYER && turns.getFirst().getTurn()[0] == LEAVE_GAME;

            if (left) {
                Assertions.assertArrayEquals(new byte[]{LEAVE_GAME, LEAVE_REASON}, turns.getFirst().getTurn());
            }
        }

        Assertions.assertTrue(left);
        Assertions.assertEquals(NO_COUNTDOWN, hostTransport.getWaitingPeers().getCountdownSeconds());
    }

    @Test
    void silent_peer_is_awaited_by_the_waiting_state_and_its_drop_is_a_departure() {
        this.joinClients();

        LanTurnTransport transport = new LanTurnTransport(this.host, this::deliver);
        WaitingPeers waiting = transport.getWaitingPeers();
        LockstepPeer joinerPeer = NetworkPeerUtils.getPeer(JOINER_PLAYER);

        this.host.sendTurn(new byte[]{ReplayCommandIds.KEEP_ALIVE});
        this.joiner.sendTurn(new byte[]{ReplayCommandIds.KEEP_ALIVE});
        this.deliver();

        Assertions.assertNotNull(transport.receiveTurns());
        Assertions.assertFalse(waiting.isWaiting());

        // The silent joiner is awaited from the last complete receive and overdue after 2500 milliseconds.
        this.joinerSilent = true;

        this.host.sendTurn(new byte[]{ReplayCommandIds.KEEP_ALIVE});

        this.now += WAIT_DELAY_MILLISECONDS - 1;

        Assertions.assertNull(transport.receiveTurns());
        Assertions.assertEquals(List.of(joinerPeer), waiting.getAwaitedPeers());
        Assertions.assertEquals(List.of(), waiting.getOverduePeers());

        this.now += 1;

        Assertions.assertNull(transport.receiveTurns());
        Assertions.assertEquals(List.of(joinerPeer), waiting.getOverduePeers());
        Assertions.assertEquals(WAIT_COUNTDOWN_MILLISECONDS, waiting.getCountdownMillis());
        Assertions.assertEquals(WAIT_COUNTDOWN_SECONDS, transport.getWaitingPeers().getCountdownSeconds());

        for (int second = WAIT_COUNTDOWN_SECONDS - 1; second >= 0; second--) {
            this.now += COUNTDOWN_STEP_MILLISECONDS;

            Assertions.assertNull(transport.receiveTurns());
        }

        // The waiting state never drops by itself: only the dialog's button does, and the departure comes with the receive without the peer.
        Assertions.assertEquals(0, transport.getWaitingPeers().getCountdownSeconds());
        Assertions.assertEquals(List.of(), transport.takeDepartures());

        transport.dropWaitingPeers();

        Assertions.assertNotNull(transport.receiveTurns());
        Assertions.assertFalse(waiting.isWaiting());
        Assertions.assertEquals(List.of(), waiting.getAwaitedPeers());

        List<PeerDeparture> departures = transport.takeDepartures();

        Assertions.assertEquals(1, departures.size());
        Assertions.assertSame(joinerPeer, departures.getFirst().getPeer());
        Assertions.assertEquals(DepartureKind.DROPPED, departures.getFirst().getKind());
        Assertions.assertEquals(LanLobby.DROP_REASON, departures.getFirst().getReason());
        Assertions.assertEquals(List.of(), transport.takeDepartures());
    }

    /**
     * Checks that both players ping every {@code 5} seconds in the lobby and every {@code 2} seconds once the game's turn stream starts.
     */
    @Test
    void game_start_shortens_the_keep_alive_interval() {
        this.joinClients();

        this.hostPings.clear();
        this.joinerPings.clear();

        int lobbyEnd = this.now + KEEP_ALIVE_PERIODS * ProviderSession.DEFAULT_KEEP_ALIVE_INTERVAL;

        while (this.now != lobbyEnd) {
            this.now++;
            this.deliver();
        }

        assertPingIntervals(this.hostPings, ProviderSession.DEFAULT_KEEP_ALIVE_INTERVAL);
        assertPingIntervals(this.joinerPings, ProviderSession.DEFAULT_KEEP_ALIVE_INTERVAL);

        LanTurnTransport hostTransport = new LanTurnTransport(this.host, this::deliver);
        LanTurnTransport joinerTransport = new LanTurnTransport(this.joiner, this::deliver);

        this.hostPings.clear();
        this.joinerPings.clear();

        for (int turn = 0; turn < INITIAL_TURNS; turn++) {
            hostTransport.sendTurn(new byte[]{ReplayCommandIds.KEEP_ALIVE});
            joinerTransport.sendTurn(new byte[]{ReplayCommandIds.KEEP_ALIVE});
        }

        int gameStart = this.now;
        int gameEnd = gameStart + KEEP_ALIVE_PERIODS * ProviderSession.DEFAULT_KEEP_ALIVE_INTERVAL;

        while (this.now != gameEnd) {
            this.now++;
            this.deliver();

            if ((this.now - gameStart) % FRAME_MILLISECONDS == 0) {
                hostTransport.sendTurn(new byte[]{ReplayCommandIds.KEEP_ALIVE});
                joinerTransport.sendTurn(new byte[]{ReplayCommandIds.KEEP_ALIVE});

                Assertions.assertNotNull(hostTransport.receiveTurns());
                Assertions.assertNotNull(joinerTransport.receiveTurns());
            }
        }

        assertPingIntervals(this.hostPings, GAME_KEEP_ALIVE_INTERVAL);
        assertPingIntervals(this.joinerPings, GAME_KEEP_ALIVE_INTERVAL);
    }
}
