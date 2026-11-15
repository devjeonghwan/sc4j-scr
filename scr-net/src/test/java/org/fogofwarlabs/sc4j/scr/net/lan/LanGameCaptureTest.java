package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplaySession;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderPacket;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderSession;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderTestResources;
import org.fogofwarlabs.sc4j.scr.net.proxy.GameProxySocket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.*;

/**
 * Plays the recorded native LAN game start as its host: the recorded joiner's provider packets go into the host's {@link ProviderSession}, the
 * host's {@link LanLobby} runs the lobby and its countdown, and the host's {@link LiveGame} runs the first in-game turns over a
 * {@link LanTurnTransport} with the game running offscreen. The host's lobby messages, lobby turns and in-game turns (their Sync commands and
 * the host's command) equal the recorded native host's in order, and so do its control packets but one.
 * <p>
 * Not compared: the fields and packets that follow the recording's timing, which the fixture's times (the joiner's clock) do not reproduce on
 * the host: the checksum, the acknowledgement number, the sequence numbers of the control packets, and the acknowledgement and overdue-turn
 * request packets. The one known difference: the native host announced the full game state ({@link ProviderSession#STATE_FULL} with
 * {@link ProviderSession#STATE_ADVERTISED}, control subtype {@code 14}) when the joiner filled the lobby; the host lobby does not send it.
 */
@RequiresInstallation
final class LanGameCaptureTest {
    private static final int CLOCK_BASE = 10_000_000;

    private static final @NotNull InetSocketAddress JOINER = new InetSocketAddress("192.168.0.2", GameProxySocket.GAME_PORT);

    private static final @NotNull String GAME_STATISTICS = ",44,12,6,1,f,,1,18,1,,B\rBottleneck\r\rfeedda7a,0,,18,";
    private static final byte @NotNull [] GAME_DATA = HexFormat.of().parseHex("0f00010001000000010101020200010100010000000000320000000000000000");
    private static final int HOST_MAXIMUM_PLAYERS = 2;
    private static final @NotNull String HOST_NAME = "B";
    private static final int HOST_PLAYER = 0;

    private static final int SEED = 0x6ABCBEE2;
    private static final @NotNull String MAP_NAME = "(2)Bottleneck.scm";
    private static final @NotNull String SCENARIO_FILE = "staredit\\scenario.chk";

    /**
     * The host lobby's time alone before the join: {@code 19} steps of {@code 50} milliseconds.
     */
    private static final int HOST_ALONE_MILLISECONDS = 960;
    private static final int HOST_CREATE_LEAD = 2500;
    /**
     * The recorded host turn that carries the host's race request: the request is made once the turn before it went out.
     */
    private static final int HOST_RACE_TURN = 127;
    /**
     * The recorded host start turn.
     */
    private static final int HOST_START_TURN = 200;
    /**
     * The first in-game turn: the lobby ended with its turns {@code 320} and {@code 321} in transit.
     */
    private static final int FIRST_GAME_TURN = 322;
    /**
     * The own turns in transit during the game.
     */
    private static final int GAME_TURNS_IN_TRANSIT = 3;
    /**
     * The recorded host's camera: its first drawn row {@code 0x40} is the camera's tile row less {@code 4}.
     */
    private static final int HOST_CAMERA_ROW = 0x44;
    /**
     * The game turn whose recorded host turn carries the host's first command after its Sync command.
     */
    private static final int HOST_COMMAND_TURN = 341;
    /**
     * The length of the Sync command that heads every in-game turn of the recorded host.
     */
    private static final int SYNC_COMMAND_LENGTH = 7;
    /**
     * The last recorded host turn: the recording ends before its later turns.
     */
    private static final int LAST_RECORDED_TURN = 356;

    private static final int FRAME_MILLISECONDS = 42;
    private static final int VIEWPORT_WIDTH = 640;
    private static final int VIEWPORT_HEIGHT = 480;
    private static final int INSTRUCTION_LIMIT = 100_000;

    /**
     * The first byte of the BattleTag field of the player information message.
     */
    private static final int BATTLE_TAG_OFFSET = 6;
    /**
     * The control packet of the native host's full game state announcement, which the host lobby does not send.
     */
    private static final @NotNull String FULL_GAME_STATE = "sub=14 payload=06000000";
    /**
     * The bits of a packet's sequence number, below its type in a transmission key.
     */
    private static final int SEQUENCE_BITS = Short.SIZE;

    private static final class Clock {
        private int now;

        private Clock() {
            this.now = 0;
        }
    }

    /**
     * Describes a packet without its checksum and, for an unflagged data packet, without its acknowledgement. The BattleTag field of the
     * player information message is not compared: sc4j has no BattleTag.
     *
     * @param packet the packet
     * @return the description
     */
    private static @NotNull String describePacket(@NotNull ProviderPacket packet) {
        boolean acknowledged = packet.getType() == ProviderPacket.CONTROL || packet.getFlags() != 0;
        byte[] payload = packet.getPayloadBytes();

        if (packet.getType() == ProviderPacket.MESSAGE && payload.length > 0 && payload[0] == LanLobby.MESSAGE_PLAYER_INFORMATION) {
            Arrays.fill(payload, BATTLE_TAG_OFFSET, LanLobby.PLAYER_INFORMATION_NAME_OFFSET, (byte) 0);
        }

        return "class=" + packet.getType() + " seq=" + packet.getSequence() + (acknowledged ? " ack=" + packet.getAcknowledgement() : "") +
               " sub=" + packet.getSubtype() + " sender=" + packet.getSenderIdentifier() + " flags=" + packet.getFlags() +
               " payload=" + HexFormat.of().formatHex(payload);
    }

    /**
     * Describes the first transmission of every unflagged data packet in order.
     *
     * @param packets the packets the host sent
     * @return one line per data packet
     */
    private static @NotNull List<String> describeDataPackets(@NotNull List<ProviderPacket> packets) {
        List<String> described = new ArrayList<>();
        Set<Integer> transmitted = new HashSet<>();

        for (ProviderPacket packet : packets) {
            if (packet.getType() != ProviderPacket.CONTROL && packet.getFlags() == 0 &&
                transmitted.add(packet.getType() << SEQUENCE_BITS | packet.getSequence())) {
                described.add(describePacket(packet));
            }
        }

        return described;
    }

    /**
     * Describes the first transmission of every unflagged control packet in its order, without its sequence and acknowledgement numbers.
     *
     * @param packets the packets the host sent
     * @return one line per control packet
     */
    private static @NotNull List<String> describeControlPackets(@NotNull List<ProviderPacket> packets) {
        List<String> described = new ArrayList<>();
        Set<Integer> transmitted = new HashSet<>();

        for (ProviderPacket packet : packets) {
            if (packet.getType() == ProviderPacket.CONTROL && packet.getFlags() == 0 && transmitted.add(packet.getSequence())) {
                described.add("sub=" + packet.getSubtype() + " payload=" + HexFormat.of().formatHex(packet.getPayloadBytes()));
            }
        }

        return described;
    }

    /**
     * Maps each recorded host turn of the game to the recording time of its first transmission.
     *
     * @param capture the recorded packets
     * @return the times by turn
     */
    private static @NotNull Map<Integer, Integer> findGameTurnTimes(@NotNull List<ProviderTestResources.CapturedPacket> capture) {
        Map<Integer, Integer> times = new HashMap<>();

        for (ProviderTestResources.CapturedPacket captured : capture) {
            ProviderPacket packet = captured.getPacket();

            if (captured.isFromHost() && packet.getType() == ProviderPacket.TURN && packet.getFlags() == 0 &&
                packet.getSequence() >= FIRST_GAME_TURN) {
                times.putIfAbsent(packet.getSequence(), captured.getTime());
            }
        }

        return times;
    }

    private static byte @Nullable [] findRecordedTurn(@NotNull List<ProviderTestResources.CapturedPacket> capture, int turn) {
        for (ProviderTestResources.CapturedPacket captured : capture) {
            ProviderPacket packet = captured.getPacket();

            if (captured.isFromHost() && packet.getType() == ProviderPacket.TURN && packet.getFlags() == 0 && packet.getSequence() == turn) {
                return packet.getPayloadBytes();
            }
        }

        return null;
    }


    @Test
    void host_game_start_sends_the_packets_of_the_captured_native_host() throws Exception {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP_NAME);

        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO_FILE);
        List<ProviderTestResources.CapturedPacket> capture = ProviderTestResources.readGameStart();
        Map<Integer, Integer> turnTimes = findGameTurnTimes(capture);
        byte[] commandTurn = findRecordedTurn(capture, HOST_COMMAND_TURN);
        Clock clock = new Clock();
        List<ProviderPacket> sent = new ArrayList<>();
        ProviderSession session = new ProviderSession((address, packet) -> sent.add(packet), () -> clock.now);

        Assertions.assertNotNull(commandTurn);

        clock.now = CLOCK_BASE - HOST_CREATE_LEAD;
        session.createGame(HOST_NAME, GAME_STATISTICS, "", HOST_MAXIMUM_PLAYERS, 0, 0, 0, GAME_DATA, HOST_NAME, "");

        LanLobby lobby = new LanLobby(session,
                                      () -> clock.now,
                                      LanTestResources.createCapturedLobbySetup(HOST_NAME),
                                      HOST_NAME,
                                      LanTestResources.createCapturedHostBlock());

        lobby.setSeedSource(() -> SEED);

        Assertions.assertTrue(lobby.shareMap(mapPath));

        // The host's recorded turn window before the join.
        for (int time = -HOST_ALONE_MILLISECONDS; time < 0; time++) {
            clock.now = CLOCK_BASE + time;
            session.update();
            lobby.update();
        }

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            LiveGame live = null;
            ReplayRuntime runtime = null;
            boolean raceRequested = false;
            boolean started = false;
            boolean commandSubmitted = false;
            boolean waiting = false;
            int next = 0;

            for (int time = 0; time <= capture.getLast().getTime(); time++) {
                clock.now = CLOCK_BASE + time;

                for (; next < capture.size() && capture.get(next).getTime() == time; next++) {
                    if (!capture.get(next).isFromHost()) {
                        session.receive(JOINER, capture.get(next).getPacket().getEncodedBytes());
                    }
                }

                session.update();

                if (lobby.getState() != LanLobby.STATE_GAME) {
                    int nextLobbyTurn = session.getSelf().getChannel(ProviderPacket.TURN).getNextOutgoingSequence();

                    if (!raceRequested && nextLobbyTurn == HOST_RACE_TURN) {
                        lobby.changeRace(ScrRaceIds.ZERG);

                        raceRequested = true;
                    }

                    // Start once at the recorded outgoing-turn boundary.
                    if (!started && lobby.getState() == LanLobby.STATE_LOBBY && nextLobbyTurn == HOST_START_TURN) {
                        Assertions.assertTrue(lobby.startGame());

                        started = true;
                    }

                    lobby.update();

                    continue;
                }

                if (live == null) {
                    live = new LiveGame(lobby.createGameSetup(mapData, GameSpeed.FASTEST, true), lobby.getSeed(), HOST_PLAYER);
                    live.attachNetwork(new LanTurnTransport(session, () -> {
                    }), GAME_TURNS_IN_TRANSIT, false);
                    runtime = ReplayRuntime.createLiveGame(live.getReplay(), ChkFile.parseTolerant(mapData),
                                                           gameData,
                                                           live.getLocalPlayer(),
                                                           VIEWPORT_WIDTH, VIEWPORT_HEIGHT,
                                                           INSTRUCTION_LIMIT);
                    runtime.start(0, 0);

                    Game game = runtime.getGame();
                    ReplaySession replaySession = runtime.getSession();

                    Assertions.assertNotNull(replaySession);

                    live.setSyncSource(game, () -> HOST_CAMERA_ROW);
                    live.attachReplaySession(replaySession);
                }

                // The recorded host ran a turn step when it sent its next turn, and retried a waiting one at every loop.
                int nextTurn = session.getSelf().getChannel(ProviderPacket.TURN).getNextOutgoingSequence();
                Integer turnTime = turnTimes.get(nextTurn);

                while (waiting || turnTime != null && turnTime <= time) {
                    if (!commandSubmitted && nextTurn == HOST_COMMAND_TURN) {
                        // The host's command after the Sync command of its turn 341 (its Select).
                        byte[] command = Arrays.copyOfRange(commandTurn, SYNC_COMMAND_LENGTH, commandTurn.length);

                        Assertions.assertTrue(live.submit(command[0], Arrays.copyOfRange(command, 1, command.length)));

                        commandSubmitted = true;
                    }

                    waiting = !live.exchangeTurn(runtime.getGame().getCurrentFrame());

                    if (waiting) {
                        break;
                    }

                    runtime.step(FRAME_MILLISECONDS, 0);

                    nextTurn = session.getSelf().getChannel(ProviderPacket.TURN).getNextOutgoingSequence();
                    turnTime = turnTimes.get(nextTurn);
                }
            }

            Assertions.assertNotNull(live);
            Assertions.assertEquals(0, live.getSyncMismatches());
        }

        List<ProviderPacket> nativePackets = new ArrayList<>();

        for (ProviderTestResources.CapturedPacket captured : capture) {
            if (captured.isFromHost()) {
                nativePackets.add(captured.getPacket());
            }
        }

        Assertions.assertEquals(LAST_RECORDED_TURN + 1, session.getSelf().getChannel(ProviderPacket.TURN).getNextOutgoingSequence());
        Assertions.assertEquals(describeDataPackets(nativePackets), describeDataPackets(sent), "lobby messages and turns, in order");

        List<String> nativeControls = describeControlPackets(nativePackets);

        Assertions.assertTrue(nativeControls.remove(FULL_GAME_STATE), "the native full game state announcement");
        Assertions.assertEquals(nativeControls, describeControlPackets(sent), "control packets");
    }
}
