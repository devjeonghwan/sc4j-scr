package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.replay.GameSourceFile;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplaySession;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.SyncChecksumUtils;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderPacket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that every client of an in-memory melee LAN game of two, three or four sc4j clients that issue commands in the
 * same frames records the same commands in the same order and hashes every frame alike, with no Sync mismatch.
 */
@RequiresInstallation
final class LanMeleeSameFrameGameTest {
    private static final int FRAMES = 400;
    private static final int LATENCY = 2;
    private static final int TURNS_IN_TRANSIT = 3;
    private static final int FRAME_MILLISECONDS = 42;
    private static final int VIEWPORT_WIDTH = 640;
    private static final int VIEWPORT_HEIGHT = 480;
    private static final int INSTRUCTION_LIMIT = 100_000;
    private static final int ITERATION_LIMIT = 400_000;
    private static final int HOST = 0;
    /**
     * The longest time of the map transfer and of the countdown, and the time the lobbies need to settle after the transfer.
     */
    private static final int MAP_TRANSFER_LIMIT_MILLISECONDS = 20_000;
    private static final int START_LIMIT_MILLISECONDS = 20_000;
    private static final int SETTLE_MILLISECONDS = 1000;
    /**
     * The joiner that has the installed maps; the joiners after it download the map.
     */
    private static final int MAP_HOLDER = 1;
    /**
     * The most units of one selection.
     */
    private static final int SELECTION_LIMIT = 12;
    /**
     * The right-click command: x, y, target handle, target unit type, queued flag.
     */
    private static final int RIGHT_CLICK_SIZE = 11;
    private static final int FIRST_MOVE_FRAME = 30;
    private static final int SECOND_MOVE_FRAME = 150;
    private static final int MOVE_ORIGIN = 1000;
    private static final int MOVE_PLAYER_SPACING = 100;
    /**
     * The commands of one move: the selection and the order.
     */
    private static final int COMMANDS_PER_MOVE = 2;
    private static final int MOVES = 2;

    private static final int STATE_LOBBY = 4;
    private static final int STATE_GAME = 9;

    private static final class Client {
        private final @NotNull LiveGame live;
        private final @NotNull ReplayRuntime runtime;
        private final @NotNull Game game;

        private Client(@NotNull LiveGame live, @NotNull ReplayRuntime runtime) {
            this.live    = live;
            this.runtime = runtime;
            this.game    = runtime.getGame();
        }

        /**
         * Selects the local player's workers and orders them to a point.
         *
         * @param targetX the target x in pixels
         * @param targetY the target y in pixels
         */
        private void moveWorkers(int targetX, int targetY) {
            UnitPool pool = this.game.getUnitSystem().getUnitPool();
            List<Integer> handles = new ArrayList<>();

            Assertions.assertNotNull(pool);

            for (int index = pool.getFirstCreatedIndex(true);
                 index != 0 && handles.size() < SELECTION_LIMIT;
                 index = pool.getNextCreatedIndex(index)) {
                Unit unit = pool.getUnit(index);
                int type = unit.getUnitId();

                if (unit.getOwnerIndex() == this.live.getLocalPlayer() &&
                    (type == ScrUnitIds.TERRAN_SCV || type == ScrUnitIds.ZERG_DRONE || type == ScrUnitIds.PROTOSS_PROBE)) {
                    handles.add(pool.getHandle(index));
                }
            }

            Assertions.assertFalse(handles.isEmpty(), "the local player has workers");

            ByteBuffer select = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES * handles.size()).order(ByteOrder.LITTLE_ENDIAN);

            select.put((byte) handles.size());

            for (int handle : handles) {
                select.putInt(handle);
            }

            Assertions.assertTrue(this.live.submit(ReplayCommandIds.SELECT_121, select.array()));

            ByteBuffer order = ByteBuffer.allocate(RIGHT_CLICK_SIZE).order(ByteOrder.LITTLE_ENDIAN);

            order.putShort((short) targetX).putShort((short) targetY).putInt(0).putShort((short) ScrUnitIds.NONE).put((byte) 0);

            Assertions.assertTrue(this.live.submit(ReplayCommandIds.RIGHT_CLICK_121, order.array()));
        }
    }

    @Test
    void two_humans_command_in_the_same_frames_and_both_clients_hash_alike(@TempDir @NotNull Path folder) throws Exception {
        this.play("(3)Holy Ground.scm", 2, folder);
    }

    @Test
    void three_humans_mesh_and_command_in_the_same_frames(@TempDir @NotNull Path folder) throws Exception {
        this.play("(3)Holy Ground.scm", 3, folder);
    }

    @Test
    void four_humans_mesh_and_command_in_the_same_frames(@TempDir @NotNull Path folder) throws Exception {
        this.play("(4)Blood Bath.scm", 4, folder);
    }

    /**
     * Plays a melee LAN game of the given players on the installed map and compares their recorded commands and hashes.
     * <p>
     * The host shares the map, the first joiner has it in its map folder and the others download it (from the host or
     * from a joiner that has it, over the joiners' direct links), and every client passes the lobby and the countdown.
     * The joiners reach each other through the host's player information (subtype {@code 6}) with each player's provider
     * address ({@code ProviderSession.encodeAddress}). The executor runs the players' turns from player {@code 7} down to
     * {@code 0}, so the commands and hashes must match whichever player id or start slot each client got.
     *
     * @param mapName the installed map's file name
     * @param players the number of human clients
     * @param folder  the folder of the clients' map folders
     * @throws Exception if the map cannot be read or the game fails
     */
    private void play(@NotNull String mapName, int players, @NotNull Path folder) throws Exception {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, mapName);

        byte[] mapFile = Files.readAllBytes(mapPath);
        byte[] mapData = MpqArchive.open(mapPath).readFile("staredit\\scenario.chk");
        ChkFile scenario = ChkFile.parseTolerant(mapData);
        LanGameOptions options = LanGameOptions.MELEE;
        InMemoryLanScenario network = new InMemoryLanScenario(players);
        LanLobby.Setup setup = LanLobby.Setup.create(scenario, options,
                                                     "host",
                                                     mapName,
                                                     mapFile.length, LanMapTransfer.calculateChecksum(mapFile, mapFile.length));
        LanLobby[] lobbies = network.lobbies;

        network.latency = LATENCY;
        network.host(setup, options.encode(), mapPath);

        for (int joiner = 1; joiner < players; joiner++) {
            // The first joiner has the installed maps; the others start with empty map folders.
            Path maps = joiner == MAP_HOLDER ? mapPath.getParent() : Files.createDirectories(folder.resolve("joiner" + joiner));

            network.join(joiner, maps, folder.resolve("joiner" + joiner).resolve("Download"));
        }

        network.runUntil(MAP_TRANSFER_LIMIT_MILLISECONDS, lan -> {
            LanLobby host = lan.lobbies[HOST];

            Assertions.assertNotNull(host);

            return host.isMapTransferComplete();
        });
        network.run(SETTLE_MILLISECONDS);

        for (int index = 0; index < players; index++) {
            LanLobby lobby = lobbies[index];

            Assertions.assertNotNull(lobby);
            Assertions.assertEquals(STATE_LOBBY, lobby.getState(), "every client reached the lobby state");

            Path local = lobby.getMapFile();

            Assertions.assertNotNull(local, "client " + index + " holds the map");
            Assertions.assertArrayEquals(mapFile, Files.readAllBytes(local), "client " + index + "'s map is the host's");

            if (index > MAP_HOLDER) {
                Assertions.assertEquals(folder.resolve("joiner" + index).resolve("Download").resolve(mapName), local,
                                        "client " + index + " downloaded the map");
            }

            // Every client knows every other player: the joiners reached each other through the host's subtype 6.
            for (int other = 0; other < players; other++) {
                if (other == index) {
                    continue;
                }

                int identifier = network.sessions[other].getLocalIdentifier();
                boolean known = network.sessions[index].findPeer(identifier) != null;

                Assertions.assertTrue(known, "client " + index + " knows player " + identifier);
            }

            for (int slot = 0; slot < players; slot++) {
                boolean visible = lobby.getRecord(slot).getPlayerId() >= 0;

                if (!visible) {
                    LanLobby host = lobbies[HOST];

                    Assertions.assertNotNull(host);

                    visible = host.getRecord(slot).getPlayerId() < 0;
                }

                Assertions.assertTrue(visible, "client " + index + " sees slot " + slot);
            }
        }

        LanLobby host = lobbies[HOST];

        Assertions.assertNotNull(host);
        Assertions.assertTrue(host.isMapTransferComplete(), "every joiner reported the map");
        Assertions.assertTrue(host.startGame());

        network.runUntil(START_LIMIT_MILLISECONDS, lan -> {
            for (LanLobby lobby : lan.lobbies) {
                Assertions.assertNotNull(lobby);

                if (lobby.getState() != STATE_GAME) {
                    return false;
                }
            }

            return true;
        });

        int lobbyEnd = network.sessions[HOST].getSelf().getChannel(ProviderPacket.TURN).getExpectedSequence();

        for (int index = 0; index < lobbies.length; index++) {
            LanLobby lobby = lobbies[index];

            Assertions.assertNotNull(lobby);
            Assertions.assertEquals(STATE_GAME, lobby.getState());
            Assertions.assertEquals(lobbyEnd, network.sessions[index].getSelf().getChannel(ProviderPacket.TURN).getExpectedSequence(),
                                    "every lobby ends on the same turn");
        }

        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Client[] clients = new Client[players];

            for (int index = 0; index < clients.length; index++) {
                LanLobby lobby = lobbies[index];

                Assertions.assertNotNull(lobby);

                MeleeGameSetup game = lobby.createGameSetup(mapData, GameSpeed.FASTEST, true);
                LiveGame live = new LiveGame(game, lobby.getSeed(), network.sessions[index].getLocalIdentifier());

                live.attachNetwork(new LanTurnTransport(network.sessions[index], network::deliver), TURNS_IN_TRANSIT, false);

                ReplayRuntime runtime = ReplayRuntime.createLiveGame(live.getReplay(), scenario,
                                                                     data,
                                                                     live.getLocalPlayer(), true,
                                                                     VIEWPORT_WIDTH, VIEWPORT_HEIGHT,
                                                                     INSTRUCTION_LIMIT);

                runtime.setSourceFile(GameSourceFile.create(mapPath, mapFile));
                live.setSyncSource(runtime.getGame(), () -> 0);
                runtime.start(0, network.now);

                Assertions.assertNull(runtime.getFailure());

                ReplaySession session = runtime.getSession();

                Assertions.assertNotNull(session);

                live.attachReplaySession(session);
                clients[index] = new Client(live, runtime);
            }

            for (int iteration = 0; iteration < ITERATION_LIMIT; iteration++) {
                boolean done = true;

                network.now++;

                for (Client client : clients) {
                    long frame = client.game.getCurrentFrame();

                    if (frame >= FRAMES) {
                        continue;
                    }

                    done = false;

                    // Both clients command in the same frames, so one turn step carries the commands of both players.
                    if (frame == FIRST_MOVE_FRAME || frame == SECOND_MOVE_FRAME) {
                        client.moveWorkers(MOVE_ORIGIN + MOVE_PLAYER_SPACING * client.live.getLocalPlayer(), MOVE_ORIGIN + (int) frame);
                    }

                    if (!client.live.exchangeTurn(frame)) {
                        continue;
                    }

                    client.runtime.step(FRAME_MILLISECONDS, network.now);

                    Assertions.assertNull(client.runtime.getFailure(),
                                          () -> "frame " + client.game.getCurrentFrame() + ": " + client.runtime.getFailure());
                }

                long frame = clients[HOST].game.getCurrentFrame();

                for (int index = 1; index < players; index++) {
                    if (clients[index].game.getCurrentFrame() == frame) {
                        Assertions.assertEquals(SyncChecksumUtils.calculateUnitHash(clients[HOST].game),
                                                SyncChecksumUtils.calculateUnitHash(clients[index].game),
                                                "unit hash at frame " + frame);
                    }
                }

                if (done) {
                    break;
                }
            }

            List<ReplayCommand> reference = clients[HOST].live.getHistory();

            Assertions.assertEquals(MOVES * COMMANDS_PER_MOVE * players, reference.size(), "two moves of every player, select and order each");

            for (Client client : clients) {
                List<ReplayCommand> history = client.live.getHistory();

                Assertions.assertEquals(FRAMES, client.game.getCurrentFrame());
                Assertions.assertEquals(reference.size(), history.size());

                for (int index = 0; index < history.size(); index++) {
                    Assertions.assertEquals(reference.get(index).getFrame(), history.get(index).getFrame());
                    Assertions.assertEquals(reference.get(index).getPlayerId(), history.get(index).getPlayerId());
                    Assertions.assertEquals(reference.get(index).getTypeId(), history.get(index).getTypeId());
                }

                Assertions.assertEquals(0, client.live.getSyncMismatches());
            }

            // One frame's commands run from the highest player id down (the executor's order).
            long first = reference.getFirst().getFrame();
            List<Integer> order = reference.stream().filter(command -> command.getFrame() == first)
                                           .map(ReplayCommand::getPlayerId).distinct().toList();
            List<Integer> descending = new ArrayList<>();

            for (int player = players - 1; player >= 0; player--) {
                descending.add(player);
            }

            Assertions.assertEquals(descending, order);
        }
    }
}
