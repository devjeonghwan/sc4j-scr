package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.engine.net.lobby.MapFolders;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayHeader;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderPacket;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderPeer;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderSession;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderTestResources;
import org.fogofwarlabs.sc4j.scr.net.proxy.GameProxySocket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.*;

/**
 * Replays recorded lobby traffic against a joiner's and a host's {@link LanLobby}: each sends the native messages and
 * commands in the native order, the host's lobby rebuilds the recorded game statistics from its slot counts, and the joiner
 * takes the identifier the host assigned and records the host's first command at the native replay frame.
 */
final class LanLobbyTest {
    private static final int CLOCK_BASE = 10_000_000;

    private static final @NotNull InetSocketAddress HOST = new InetSocketAddress("192.168.0.88", GameProxySocket.GAME_PORT);
    private static final @NotNull InetSocketAddress JOINER = new InetSocketAddress("192.168.0.2", GameProxySocket.GAME_PORT);

    private static final @NotNull String GAME_STATISTICS = ",44,12,6,1,f,,1,18,1,,B\rBottleneck\r\rfeedda7a,0,,18,";
    /**
     * The captured game statistics before the lobby counts its slots (four open observer slots) and after the host closed
     * the joiner's slot.
     */
    private static final @NotNull String CREATED_GAME_STATISTICS = ",44,12,6,1,f,,1,18,1,,B\rBottleneck\r\rfeedda7a,,,18,";
    private static final @NotNull String CLOSED_SLOT_GAME_STATISTICS = ",44,12,6,1,f,,1,18,1,,B\rBottleneck\r\rfeedda7a,0,1,18,";
    private static final byte @NotNull [] GAME_DATA = HexFormat.of().parseHex("0f00010001000000010101020200010100010000000000320000000000000000");
    private static final int HOST_MAXIMUM_PLAYERS = 2;

    private static final @NotNull String HOST_NAME = "B";
    private static final @NotNull String JOINER_NAME = "A";

    private static final int HOST_SLOT = 0;
    private static final int JOINER_SLOT = 1;
    private static final int HOST_PLAYER = 0;
    private static final int JOINER_PLAYER = 1;

    /**
     * The seed of the recorded game.
     */
    private static final int SEED = 0x6ABCBEE2;
    /**
     * The first byte of the BattleTag field of the player information message.
     */
    private static final int BATTLE_TAG_OFFSET = 6;
    /**
     * The bits of a packet's sequence number, below its type in a transmission key.
     */
    private static final int SEQUENCE_BITS = Short.SIZE;

    /**
     * The recorded joiner's lobby start time.
     */
    private static final int JOINER_LOBBY_START = 336;
    private static final int JOIN_MILLISECONDS = 2000;
    /**
     * The time a race request is made before the recorded turn that carries it.
     */
    private static final int RACE_REQUEST_LEAD = 60;
    /**
     * The times of the joiner's two race requests (the first Zerg, the second Terran).
     */
    private static final int FIRST_RACE_REQUEST = 8763 - RACE_REQUEST_LEAD;
    private static final int SECOND_RACE_REQUEST = 9828 - RACE_REQUEST_LEAD;
    private static final int BETWEEN_RACE_REQUESTS = 9000;
    /**
     * The time of the host's race request.
     */
    private static final int HOST_RACE_REQUEST = 6842 - RACE_REQUEST_LEAD;
    /**
     * The recorded host start turn.
     */
    private static final int HOST_START_TURN = 200;
    /**
     * The host lobby's time alone before the join: {@code 19} steps of {@code 50} milliseconds.
     */
    private static final int HOST_ALONE_MILLISECONDS = 960;
    private static final int HOST_CREATE_LEAD = 2500;
    /**
     * The longest wait for the host's own slot change to come back in its turns.
     */
    private static final int SLOT_CHANGE_MILLISECONDS = 1000;
    /**
     * The own turns in transit during the game: the recorded joiner sent {@code 322} and {@code 323} at load and one per frame.
     */
    private static final int GAME_TURNS_IN_TRANSIT = 3;

    private static final int TOP_VS_BOTTOM_TYPE = 15;

    private static final int ZERG_RACE = 0;
    private static final int TERRAN_RACE = 1;

    private static final int STATE_GAME = 9;

    private static final int KEEP_ALIVE = 0x05;
    private static final int START_GAME = 0x3C;
    private static final int SAVED_DATA = 0x48;
    private static final int SELECT_121 = 0x63;

    private static final int CLOSED_TYPE = 8;

    private static final class Clock {
        private int now;

        private Clock() {
            this.now = 0;
        }
    }

    private static @NotNull String describeMessage(@NotNull ProviderPacket packet) {
        byte[] payload = packet.getPayloadBytes();

        if (payload.length > 0 && payload[0] == LanLobby.MESSAGE_PLAYER_INFORMATION) {
            // The BattleTag field (6..0x69) and the region are not compared: sc4j has no BattleTag.
            for (int index = BATTLE_TAG_OFFSET; index < LanLobby.PLAYER_INFORMATION_NAME_OFFSET; index++) {
                payload[index] = 0;
            }
        }

        return HexFormat.of().formatHex(payload);
    }

    /**
     * Describes the lobby messages and commands of the packets in their order: each transmitted packet once, without the
     * control packets, the keep-alives and, unless {@code withStart}, the start and saved-data commands.
     *
     * @param packets   the packets one side sent
     * @param withStart whether the start and saved-data commands are described
     * @return one line per message or command
     */
    private static @NotNull List<String> describeLobbyTraffic(@NotNull List<ProviderPacket> packets, boolean withStart) {
        List<String> described = new ArrayList<>();
        Set<Integer> transmitted = new HashSet<>();

        for (ProviderPacket packet : packets) {
            if (packet.getType() == ProviderPacket.CONTROL || packet.getFlags() != 0 ||
                !transmitted.add(packet.getType() << SEQUENCE_BITS | packet.getSequence())) {
                continue;
            }

            byte[] payload = packet.getPayloadBytes();

            if (packet.getType() == ProviderPacket.TURN && (payload.length == 1 && payload[0] == KEEP_ALIVE ||
                                                            !withStart && (payload[0] == START_GAME || payload[0] == SAVED_DATA))) {
                continue;
            }

            described.add((packet.getType() == ProviderPacket.MESSAGE ? "message " : "turn ") + describeMessage(packet));
        }

        return described;
    }

    private static @NotNull List<String> listTrafficKinds(@NotNull List<String> described) {
        return described.stream().map(line -> line.substring(0, line.indexOf(' '))).toList();
    }

    private static @NotNull List<String> describeGameInformation(@NotNull List<ProviderPacket> packets) {
        return packets.stream()
                      .filter(packet -> packet.getType() == ProviderPacket.CONTROL && packet.getSubtype() == ProviderSession.SUBTYPE_GAME_INFORMATION)
                      .map(packet -> HexFormat.of().formatHex(packet.getPayloadBytes()))
                      .distinct()
                      .toList();
    }

    private static int findCommandTurn(@NotNull List<ProviderPacket> packets, int command) {
        for (ProviderPacket packet : packets) {
            byte[] payload = packet.getPayloadBytes();

            if (packet.getType() == ProviderPacket.TURN && packet.getFlags() == 0 && payload.length > 0 && payload[0] == command) {
                return packet.getSequence();
            }
        }

        return -1;
    }


    @Test
    void joiner_lobby_answers_the_captured_host_like_the_native_joiner(@TempDir @NotNull Path maps) throws Exception {
        // The recorded joiner had the map: a file of its size and checksum in the map folder.
        LanTestResources.writeCapturedMap(maps);

        List<ProviderTestResources.CapturedPacket> capture = ProviderTestResources.readLobbyJoin();
        Clock clock = new Clock();
        List<ProviderPacket> sent = new ArrayList<>();
        ProviderSession session = new ProviderSession((address, packet) -> sent.add(packet), () -> clock.now);
        LanLobby lobby = null;
        int next = 0;

        clock.now = CLOCK_BASE;
        session.beginJoin(HOST, JOINER_NAME, "", "");

        for (int time = 0; time <= capture.getLast().getTime(); time++) {
            clock.now = CLOCK_BASE + time;

            for (; next < capture.size() && capture.get(next).getTime() == time; next++) {
                if (capture.get(next).isFromHost()) {
                    session.receive(HOST, capture.get(next).getPacket().getEncodedBytes());
                }
            }

            session.update();
            session.pollJoin();

            if (time == JOINER_LOBBY_START) {
                lobby = new LanLobby(session, () -> clock.now, null, JOINER_NAME, new byte[LanLobby.PLAYER_BLOCK_SIZE]);
                lobby.setMapFolders(MapFolders.of(List.of(maps), LanMapTransfer.DOWNLOAD_FOLDER));
            }

            if (lobby != null) {
                if (time == FIRST_RACE_REQUEST || time == SECOND_RACE_REQUEST) {
                    lobby.changeRace(time < BETWEEN_RACE_REQUESTS ? ScrRaceIds.ZERG : ScrRaceIds.TERRAN);
                }

                // At state 9 the game takes the turn stream over.
                if (lobby.getState() != STATE_GAME) {
                    lobby.update();
                }
            }
        }

        List<ProviderPacket> nativePackets = new ArrayList<>();

        for (ProviderTestResources.CapturedPacket captured : capture) {
            if (!captured.isFromHost()) {
                nativePackets.add(captured.getPacket());
            }
        }

        List<String> expected = describeLobbyTraffic(nativePackets, false);

        // Joined game (0x40), player information (0x3a), map status (0x4f), download 100 % (0x3d), two race requests.
        Assertions.assertEquals(List.of("turn", "message", "message", "turn", "turn", "turn"), listTrafficKinds(expected));
        Assertions.assertEquals(expected, describeLobbyTraffic(sent, false));
        Assertions.assertNotNull(lobby);
        Assertions.assertEquals(JOINER_SLOT, lobby.getRecord(JOINER_SLOT).getPlayerId());
        Assertions.assertEquals(TERRAN_RACE, lobby.getRecord(JOINER_SLOT).getRace());
        Assertions.assertEquals(HOST_SLOT, lobby.getRecord(HOST_SLOT).getPlayerId());
        Assertions.assertFalse(lobby.isTimedOut());

        // The host's start (0x3c) and saved data (0x48) reached the joiner: its countdown ended with the host's seed.
        Assertions.assertEquals(STATE_GAME, lobby.getState());
        Assertions.assertEquals(SEED, lobby.getSeed());

        // The lobby's last turn was 321 and it consumed through 319 (320 and 321 in transit): the game start step
        // consumes 320 and the first frame 321.
        Assertions.assertEquals(322, session.getSelf().getChannel(ProviderPacket.TURN).getNextOutgoingSequence());
        Assertions.assertEquals(320, session.getSelf().getChannel(ProviderPacket.TURN).getExpectedSequence());
    }

    @Test
    void host_lobby_answers_the_captured_joiner_like_the_native_host(@TempDir @NotNull Path maps) throws Exception {
        List<ProviderTestResources.CapturedPacket> capture = ProviderTestResources.readLobbyJoin();
        Clock clock = new Clock();
        List<ProviderPacket> sent = new ArrayList<>();
        ProviderSession session = new ProviderSession((address, packet) -> sent.add(packet), () -> clock.now);

        clock.now = CLOCK_BASE - HOST_CREATE_LEAD;
        session.createGame(HOST_NAME, GAME_STATISTICS, "", HOST_MAXIMUM_PLAYERS, 0, 0, 0, GAME_DATA, HOST_NAME, "");

        LanLobby lobby = new LanLobby(session,
                                      () -> clock.now,
                                      LanTestResources.createCapturedLobbySetup(HOST_NAME),
                                      HOST_NAME,
                                      LanTestResources.createCapturedHostBlock());

        lobby.setSeedSource(() -> SEED);

        Assertions.assertTrue(lobby.shareMap(LanTestResources.writeCapturedMap(maps)));

        // Seed the host's recorded turn window before the join.
        for (int time = -HOST_ALONE_MILLISECONDS; time < 0; time++) {
            clock.now = CLOCK_BASE + time;
            session.update();
            lobby.update();
        }

        Assertions.assertEquals(21, session.getSelf().getChannel(ProviderPacket.TURN).getNextOutgoingSequence());

        int next = 0;

        for (int time = 0; time <= capture.getLast().getTime(); time++) {
            clock.now = CLOCK_BASE + time;

            for (; next < capture.size() && capture.get(next).getTime() == time; next++) {
                if (!capture.get(next).isFromHost()) {
                    session.receive(JOINER, capture.get(next).getPacket().getEncodedBytes());
                }
            }

            session.update();

            if (time == HOST_RACE_REQUEST) {
                lobby.changeRace(ScrRaceIds.ZERG);
            }

            // Start at the recorded outgoing-turn boundary.
            if (lobby.getState() == LanLobby.STATE_LOBBY &&
                session.getSelf().getChannel(ProviderPacket.TURN).getNextOutgoingSequence() == HOST_START_TURN) {
                Assertions.assertTrue(lobby.startGame());
            }

            if (lobby.getState() != STATE_GAME) {
                lobby.update();
            }
        }

        List<ProviderPacket> nativePackets = new ArrayList<>();

        for (ProviderTestResources.CapturedPacket captured : capture) {
            if (captured.isFromHost()) {
                nativePackets.add(captured.getPacket());
            }
        }

        List<String> expected = describeLobbyTraffic(nativePackets, true);

        // 0x4a, 0x50, 0x49, 0x5e x3, 0x4f, 0x3a, the joined-game turn, the host's race request, three slot changes, the
        // start (0x3c) and the saved data (0x48).
        Assertions.assertEquals(List.of("message", "message", "message", "message", "message", "message", "message", "message",
                                        "turn", "turn", "turn", "turn", "turn", "turn", "turn"),
                                listTrafficKinds(expected));
        Assertions.assertEquals(expected, describeLobbyTraffic(sent, true));

        // The game information (subtype 8) carries the statistics the lobby rebuilt from its slot counts.
        Assertions.assertEquals(describeGameInformation(nativePackets), describeGameInformation(sent));

        // Compare the recorded start and saved-data turn boundaries.
        Assertions.assertEquals(findCommandTurn(nativePackets, START_GAME), findCommandTurn(sent, START_GAME));
        Assertions.assertEquals(findCommandTurn(nativePackets, SAVED_DATA), findCommandTurn(sent, SAVED_DATA));

        // The countdown ended within the fixture: the game started with the seed of the saved data.
        Assertions.assertEquals(STATE_GAME, lobby.getState());
        Assertions.assertEquals(SEED, lobby.getSeed());

        // The lobby's last turn was 321 and it consumed through 319 (320 and 321 in transit): the game start step
        // consumes 320 and the first frame 321.
        Assertions.assertEquals(322, session.getSelf().getChannel(ProviderPacket.TURN).getNextOutgoingSequence());
        Assertions.assertEquals(320, session.getSelf().getChannel(ProviderPacket.TURN).getExpectedSequence());
        Assertions.assertEquals(JOINER_SLOT, lobby.getRecord(JOINER_SLOT).getPlayerId());
        Assertions.assertEquals(JOINER_NAME, lobby.getRecord(JOINER_SLOT).getName());
        Assertions.assertEquals(100, lobby.getDownloadPercent(JOINER_SLOT));

        // The joiner reported the whole map (status {0, 0, 0, 1, size}): the start check passes.
        Assertions.assertTrue(lobby.isMapTransferComplete());
    }

    @Test
    void host_lobby_rebuilds_the_game_statistics_from_its_slot_counts() {
        Clock clock = new Clock();
        ProviderSession session = new ProviderSession((address, packet) -> {
        }, () -> clock.now);

        clock.now = CLOCK_BASE;
        session.createGame(HOST_NAME, CREATED_GAME_STATISTICS, "", HOST_MAXIMUM_PLAYERS, PlayerTable.OBSERVER_SLOTS, 0, 0, GAME_DATA, HOST_NAME, "");

        LanLobby lobby = new LanLobby(session,
                                      () -> clock.now,
                                      LanTestResources.createCapturedLobbySetup(HOST_NAME),
                                      HOST_NAME,
                                      LanTestResources.createCapturedHostBlock());

        // The captured lobby's observer records are closed: the observer limit becomes 0, as the captured statistics carry.
        Assertions.assertEquals(GAME_STATISTICS, session.getGameStatistics());
        Assertions.assertTrue(lobby.setSlotState(JOINER_SLOT, LanLobby.SLOT_CLOSED));

        for (int time = 0; time < SLOT_CHANGE_MILLISECONDS && lobby.getRecord(JOINER_SLOT).getType() != CLOSED_TYPE; time++) {
            clock.now = CLOCK_BASE + time;
            session.update();
            lobby.update();
        }

        Assertions.assertEquals(CLOSED_TYPE, lobby.getRecord(JOINER_SLOT).getType());
        Assertions.assertEquals(CLOSED_SLOT_GAME_STATISTICS, session.getGameStatistics());
    }

    /**
     * Checks the assigned provider identifier, self and host peers, and corresponding lobby records.
     */
    @Test
    void joiner_identifier_is_the_one_the_captured_host_assigned() throws Exception {
        List<ProviderTestResources.CapturedPacket> capture = ProviderTestResources.readGameStart();
        ProviderPacket information = null;

        for (ProviderTestResources.CapturedPacket packet : capture) {
            if (packet.isFromHost() && packet.getPacket().getType() == ProviderPacket.CONTROL &&
                packet.getPacket().getSubtype() == ProviderSession.SUBTYPE_GAME_INFORMATION && information == null) {
                information = packet.getPacket();
            }
        }

        Assertions.assertNotNull(information);
        Assertions.assertEquals(JOINER_PLAYER, information.getPayloadBytes()[0]);

        Clock clock = new Clock();
        ProviderSession session = new ProviderSession((address, packet) -> {
        }, () -> clock.now);
        LanLobby lobby = null;
        int next = 0;

        clock.now = CLOCK_BASE;
        session.beginJoin(HOST, JOINER_NAME, "", "");

        for (int time = 0; time <= JOIN_MILLISECONDS; time++) {
            clock.now = CLOCK_BASE + time;

            for (; next < capture.size() && capture.get(next).getTime() == time; next++) {
                if (capture.get(next).isFromHost()) {
                    session.receive(HOST, capture.get(next).getPacket().getEncodedBytes());
                }
            }

            session.update();
            session.pollJoin();

            if (time == JOINER_LOBBY_START) {
                lobby = new LanLobby(session, () -> clock.now, null, JOINER_NAME, new byte[LanLobby.PLAYER_BLOCK_SIZE]);
            }

            if (lobby != null) {
                lobby.update();
            }
        }

        Assertions.assertEquals(ProviderSession.JoinState.JOINED, session.getJoinState());
        Assertions.assertEquals(JOINER_PLAYER, session.getLocalIdentifier());
        Assertions.assertEquals(JOINER_PLAYER, session.getSelf().getIdentifier());
        Assertions.assertFalse(session.getSelf().isHost());

        List<ProviderPeer> hosts = session.getPeers().stream().filter(ProviderPeer::isHost).toList();

        Assertions.assertEquals(1, hosts.size());
        Assertions.assertEquals(HOST_PLAYER, hosts.getFirst().getIdentifier());
        Assertions.assertNotNull(lobby);
        Assertions.assertEquals(HOST_PLAYER, lobby.getRecord(HOST_PLAYER).getPlayerId());
        Assertions.assertEquals(JOINER_PLAYER, lobby.getRecord(JOINER_PLAYER).getPlayerId());
        Assertions.assertEquals(JOINER_NAME, lobby.getRecord(JOINER_PLAYER).getName());
    }

    @RequiresInstallation
    @Test
    void host_first_command_is_recorded_at_the_native_replay_frame() throws Exception {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, "(2)Bottleneck.scm");

        byte[] mapData = MpqArchive.open(mapPath).readFile("staredit\\scenario.chk");
        List<ProviderTestResources.CapturedPacket> capture = ProviderTestResources.readGameStart();
        Clock clock = new Clock();
        ProviderSession session = new ProviderSession((address, packet) -> {
        }, () -> clock.now);
        LanLobby lobby = null;
        LiveGame game = null;
        long frame = 0;
        int next = 0;

        clock.now = CLOCK_BASE;
        session.beginJoin(HOST, JOINER_NAME, "", "");

        for (int time = 0; time <= capture.getLast().getTime(); time++) {
            clock.now = CLOCK_BASE + time;

            for (; next < capture.size() && capture.get(next).getTime() == time; next++) {
                if (capture.get(next).isFromHost()) {
                    session.receive(HOST, capture.get(next).getPacket().getEncodedBytes());
                }
            }

            session.update();
            session.pollJoin();

            if (time == JOINER_LOBBY_START) {
                lobby = new LanLobby(session, () -> clock.now, null, JOINER_NAME, new byte[LanLobby.PLAYER_BLOCK_SIZE]);
            }

            if (lobby == null) {
                continue;
            }

            if (lobby.getState() != STATE_GAME) {
                if (time == FIRST_RACE_REQUEST || time == SECOND_RACE_REQUEST) {
                    lobby.changeRace(time < BETWEEN_RACE_REQUESTS ? ScrRaceIds.ZERG : ScrRaceIds.TERRAN);
                }

                lobby.update();

                continue;
            }

            if (game == null) {
                // The joiner's own lobby table resolves with the host's seed; it plays its own record (identifier 1).
                game = new LiveGame(lobby.createGameSetup(mapData, GameSpeed.FASTEST, true), lobby.getSeed(), session.getLocalIdentifier());
                game.attachNetwork(new LanTurnTransport(session, () -> {
                }), GAME_TURNS_IN_TRANSIT, true);
            }

            if (game.exchangeTurn(frame)) {
                frame++;
            }
        }

        Assertions.assertNotNull(game);

        // Game 1's replay: the joiner (Terran) in slot 1 of force 2, the host (Zerg) in slot 0, colors [7,3,1,5,6,2,4,0].
        ReplayHeader header = game.getReplay().getHeader();

        Assertions.assertEquals(JOINER_PLAYER, game.getLocalPlayer());
        Assertions.assertEquals(0x6ABCBEE2L, header.getRandomSeed());
        Assertions.assertEquals(TOP_VS_BOTTOM_TYPE, header.getType());
        Assertions.assertArrayEquals(new int[]{7, 3, 1, 5, 6, 2, 4, 0}, header.getColors());
        Assertions.assertEquals(ZERG_RACE, header.getPlayers().get(HOST_PLAYER).getRace());
        Assertions.assertEquals(TERRAN_RACE, header.getPlayers().get(JOINER_PLAYER).getRace());
        Assertions.assertEquals(2, header.getPlayers().get(JOINER_PLAYER).getTeam());

        // The host's turns reached 356 within the fixture: frames 0..35 ran.
        Assertions.assertEquals(36, frame);

        List<ReplayCommand> history = game.getHistory();

        Assertions.assertFalse(history.isEmpty());

        ReplayCommand first = history.getFirst();

        Assertions.assertEquals(20, first.getFrame());
        Assertions.assertEquals(HOST_PLAYER, first.getPlayerId());
        Assertions.assertEquals(SELECT_121, first.getTypeId());
        Assertions.assertEquals("07052d0000062d0000072d0000042d00000b2d00000a2d0000082d0000", HexFormat.of().formatHex(first.getPayload()));
    }
}
