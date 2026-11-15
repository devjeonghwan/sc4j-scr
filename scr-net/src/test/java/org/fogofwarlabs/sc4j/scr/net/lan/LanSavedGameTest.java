package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.net.lobby.MapFolders;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.replay.GameSourceFile;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplaySession;
import org.fogofwarlabs.sc4j.scr.game.save.*;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.SyncChecksumUtils;
import org.fogofwarlabs.sc4j.scr.game.session.setup.SavedGamePlayers;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.net.discovery.LanDiscovery;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Tests that a multiplayer saved game reloads into a new LAN lobby and continues in sync, and that the host of a saved
 * game refuses joiners of another saved game or of a record slot another player holds.
 */
@RequiresInstallation
final class LanSavedGameTest {

    private static final long LIMIT_MILLISECONDS = 120_000;
    private static final int JOIN_LIMIT_MILLISECONDS = 3000;
    /**
     * The milliseconds the in-memory lobby gets for the map transfer and for the countdown.
     */
    private static final int LOBBY_LIMIT_MILLISECONDS = 20_000;
    private static final long DISCOVERY_POLL_MILLISECONDS = 10;
    private static final long CLIENT_POLL_MILLISECONDS = 5;

    private static final int SAVE_FRAME = 400;
    private static final int END_FRAME = 900;
    private static final int FRAME_MILLISECONDS = 42;
    private static final int TURNS_IN_TRANSIT = 3;
    private static final int LATENCY = 2;
    private static final int VIEWPORT_WIDTH = 640;
    private static final int VIEWPORT_HEIGHT = 480;
    private static final int INSTRUCTION_LIMIT = 100_000;

    private static final @NotNull String SAVE_NAME = "lansave";
    private static final @NotNull String MAP_NAME = "(4)Blood Bath.scm";
    private static final @NotNull String SCENARIO = "staredit\\scenario.chk";

    /**
     * The base of the Save Game command's time, which becomes the save id.
     */
    private static final int SAVE_TIME_BASE = 0x6A000000;
    /**
     * The save id of the in-memory fixture's saved game.
     */
    private static final int FIXTURE_SAVE_GAME_IDENTIFIER = 1;
    private static final long DWORD_MASK = 0xFFFFFFFFL;

    private static final int FIRST_COMPUTER_SLOT = 2;
    private static final int SECOND_COMPUTER_SLOT = 3;
    private static final int FIRST_PLAYER = 0;
    private static final int SECOND_PLAYER = 1;
    private static final int NOT_SAVED = -1;
    private static final int NO_MISMATCH = -1;

    /**
     * The clients of the in-memory game that writes the saved game: the host and one joiner.
     */
    private static final int SAVED_GAME_CLIENTS = 2;
    /**
     * The clients of the in-memory refusal checks: the host and two joiners.
     */
    private static final int REFUSAL_CLIENTS = 3;
    /**
     * The in-memory client whose saved game has another random state.
     */
    private static final int INVALID_SAVE_JOINER = 1;
    /**
     * The in-memory client that names the host's record slot.
     */
    private static final int OCCUPIED_SLOT_JOINER = 2;
    /**
     * The lobby slot the in-memory joiners name in their saved game identity.
     */
    private static final int JOINER_LOBBY_SLOT = 1;
    /**
     * The record slot the in-memory joiner of another saved game names.
     */
    private static final int JOINER_RECORD_SLOT = 1;

    private static final int STATE_LOBBY = 4;
    private static final int STATE_GAME = 9;
    private static final int HUMAN_TYPE = 2;
    private static final int EITHER_PREFER_COMPUTER_TYPE = 5;
    private static final int REFUSAL_NO_SLOT = 2;
    private static final int REFUSAL_INVALID_SAVE = 5;

    /**
     * One client's running game.
     */
    private static final class Player {
        private final @NotNull LiveGame live;
        private final @NotNull ReplayRuntime runtime;

        /**
         * The frame at which the Save Game command executed, {@code -1} before.
         */
        private int savedFrame;

        private Player(@NotNull LiveGame live, @NotNull ReplayRuntime runtime) {
            this.live    = live;
            this.runtime = runtime;

            this.savedFrame = NOT_SAVED;
        }

        private int getFrame() {
            return this.runtime.getGame().getCurrentFrame();
        }


        /**
         * Runs one frame when the turns allow it.
         */
        private void step() {
            int frame = this.getFrame();

            if (!this.live.exchangeTurn(frame)) {
                return;
            }

            this.runtime.step(FRAME_MILLISECONDS, frame * FRAME_MILLISECONDS);

            Assertions.assertNull(this.runtime.getFailure(), () -> "frame " + this.getFrame() + ": " + this.runtime.getFailure());
        }
    }

    /**
     * Saves a short LAN-style game of two humans for the in-memory checks: the session is played alone (no transport)
     * with a network lobby, which is all a saved game's lobby needs.
     *
     * @param mapPath the map file
     * @param folder  the folder of the saved game
     * @return the saved game file
     * @throws Exception if a file cannot be read or written
     */
    private static @NotNull Path writeSavedGame(@NotNull Path mapPath, @NotNull Path folder) throws Exception {
        byte[] mapFile = Files.readAllBytes(mapPath);
        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO);
        ChkFile scenario = ChkFile.parseTolerant(mapData);
        LanGameOptions options = LanGameOptions.MELEE;
        InMemoryLanScenario network = new InMemoryLanScenario(SAVED_GAME_CLIENTS);

        network.latency = LATENCY;
        network.host(LanLobby.Setup.create(scenario, options, "host", MAP_NAME,
                                           mapFile.length, LanMapTransfer.calculateChecksum(mapFile, mapFile.length)),
                     options.encode(), mapPath);
        network.join(SECOND_PLAYER, mapPath.getParent(), null);
        network.runUntil(LOBBY_LIMIT_MILLISECONDS, lan -> {
            LanLobby host = lan.lobbies[FIRST_PLAYER];

            Assertions.assertNotNull(host);

            return host.isMapTransferComplete();
        });

        LanLobby host = network.lobbies[FIRST_PLAYER];

        Assertions.assertNotNull(host);
        Assertions.assertTrue(host.startGame());

        network.runUntil(LOBBY_LIMIT_MILLISECONDS, lan -> {
            LanLobby currentHost = lan.lobbies[FIRST_PLAYER];

            Assertions.assertNotNull(currentHost);

            return currentHost.getState() == STATE_GAME;
        });

        Path file = folder.resolve(SAVE_NAME + SaveGameKind.MULTIPLAYER.getExtension());

        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            LanLobby startedHost = network.lobbies[FIRST_PLAYER];

            Assertions.assertNotNull(startedHost);

            LiveGame live = new LiveGame(startedHost.createGameSetup(mapData, GameSpeed.FASTEST, true), startedHost.getSeed(), FIRST_PLAYER);
            ReplayRuntime runtime = ReplayRuntime.createLiveGame(live.getReplay(), scenario,
                                                                 data,
                                                                 live.getLocalPlayer(), live.getLocalSlot(), true,
                                                                 live.getTeamSlotRaces(),
                                                                 VIEWPORT_WIDTH, VIEWPORT_HEIGHT,
                                                                 INSTRUCTION_LIMIT);

            runtime.start(0, network.now);
            Files.createDirectories(folder);
            Files.write(file,
                        SaveGameUtils.write(new SaveGameContext(runtime, live),
                                            createSavedGameDescription(),
                                            FIXTURE_SAVE_GAME_IDENTIFIER).toByteArray());
        }

        return file;
    }

    /**
     * Joins a client of the in-memory network with a saved game identity and runs until its lobby is refused or holds a
     * slot.
     *
     * @param network     the in-memory LAN
     * @param index       the client
     * @param information the client's saved game identity
     */
    private static void joinSavedGame(@NotNull InMemoryLanScenario network, int index, @NotNull SaveGameInformation information) {
        LanLobby lobby = network.openLobby(index, information);

        network.runUntil(JOIN_LIMIT_MILLISECONDS, lan -> lobby.getRefusal() != LanLobby.NO_REFUSAL);
    }

    /**
     * Creates a {@link SaveGameDescription} instance for the multiplayer fixture.
     *
     * @return the created {@link SaveGameDescription} instance
     */
    private static @NotNull SaveGameDescription createSavedGameDescription() {
        return new SaveGameDescription("test", true, SAVE_NAME.getBytes(StandardCharsets.UTF_8), List.of("playera", "playerb"),
                                       SaveGameDescription.CUSTOM_GAME_TYPE, "", "", 0);
    }

    private static LanDiscovery.@Nullable Game discover(@NotNull LanClient host,
                                                        @NotNull LanClient joiner,
                                                        @NotNull String name, int port,
                                                        long deadline) throws InterruptedException {
        while (System.currentTimeMillis() < deadline) {
            host.update();

            for (LanDiscovery.Game game : joiner.getGames()) {
                // A game of the same name on another port is the game of another test run on this computer.
                if (game.getInformation().getName().equals(name) && game.getAddress().getPort() == port) {
                    return game;
                }
            }

            Thread.sleep(DISCOVERY_POLL_MILLISECONDS);
        }

        return null;
    }

    private static @NotNull LanLobby requireLobby(@NotNull LanClient client) {
        LanLobby lobby = client.getLobby();

        Assertions.assertNotNull(lobby);

        return lobby;
    }

    private static @NotNull LanLobby joinLobby(@NotNull LanClient host, @NotNull LanClient joiner, long deadline) throws InterruptedException {
        runUntil(host, joiner, deadline, () -> joiner.getState() != LanClient.State.JOINING);

        Assertions.assertEquals(LanClient.State.LOBBY, joiner.getState(),
                                () -> "the join failed: " + joiner.getJoinFailure() + ", saved game missing " + joiner.isSavedGameMissing());

        LanLobby lobby = requireLobby(joiner);

        runUntil(host, joiner, deadline, () -> lobby.getState() >= STATE_LOBBY && lobby.getRecord(SECOND_PLAYER).getPlayerId() >= 0);

        return lobby;
    }

    private static void runUntil(@NotNull LanClient first, @NotNull LanClient second, long deadline, @NotNull BooleanSupplier condition) throws InterruptedException {
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            first.update();
            second.update();
            Thread.sleep(CLIENT_POLL_MILLISECONDS);
        }

        Assertions.assertTrue(condition.getAsBoolean(), "timed out");
    }

    /**
     * Starts a client's new game from its lobby, writing its saved games into its folder.
     *
     * @param data     the installed game resources
     * @param client   the client
     * @param lobby    the client's lobby
     * @param scenario the map's scenario
     * @param mapPath  the map file
     * @param mapFile  the map file bytes
     * @param saves    the folder of the client's saved games
     * @return the started game
     * @throws IOException if the map cannot be read
     */
    private static @NotNull Player startNewGame(@NotNull GameData data,
                                                @NotNull LanClient client, @NotNull LanLobby lobby,
                                                @NotNull ChkFile scenario,
                                                @NotNull Path mapPath, byte @NotNull [] mapFile,
                                                @NotNull Path saves) throws IOException {
        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO);
        ProviderSession session = client.getSession();

        Assertions.assertNotNull(session);

        LiveGame live = new LiveGame(lobby.createGameSetup(mapData, GameSpeed.FASTEST, true), lobby.getSeed(), session.getLocalIdentifier());
        Player player = startGame(data, client, live, scenario, mapPath, mapFile);

        live.setSavedGameHandler(new LiveGame.SavedGameHandler() {
            /**
             * Writes the running game to a saved game on every machine executing the Save Game command.
             *
             * @param time the command's time DWORD, {@code time(0)} of the issuing machine
             * @param name the command's name bytes without terminator, as received: the file name without extension
             * @throws UncheckedIOException if the saved game cannot be written
             */
            @Override
            public void saveGame(int time, byte @NotNull [] name) {
                player.savedFrame = player.getFrame();

                try {
                    Files.createDirectories(saves);
                    Files.write(saves.resolve(new String(name, StandardCharsets.UTF_8) + SaveGameKind.MULTIPLAYER.getExtension()),
                                SaveGameUtils.write(new SaveGameContext(player.runtime, live), createSavedGameDescription(), time).toByteArray());
                } catch (IOException exception) {
                    throw Exceptions.newUncheckedIOException(exception);
                }
            }

            /**
             * Ends the running single-player game and starts the saved game.
             *
             * @param time the command's time DWORD
             * @param name the command's name bytes without terminator, as received: the file name without extension
             * @implSpec This fixture ignores load requests.
             */
            @Override
            public void loadGame(int time, byte @NotNull [] name) {
            }
        });

        return player;
    }

    /**
     * Starts a client's game from its own saved game file with the new lobby's players, as the client loads it.
     *
     * @param data    the installed game resources
     * @param client  the client
     * @param lobby   the client's lobby
     * @param mapPath the map file
     * @param mapFile the map file bytes
     * @return the started game
     * @throws IOException if the saved game cannot be read
     */
    private static @NotNull Player startSavedGame(@NotNull GameData data,
                                                  @NotNull LanClient client, @NotNull LanLobby lobby,
                                                  @NotNull Path mapPath, byte @NotNull [] mapFile) throws IOException {
        LanSavedGame saved = client.getSavedGame();
        ProviderSession session = client.getSession();

        Assertions.assertNotNull(saved);
        Assertions.assertNotNull(session);

        SaveGameFile file = SaveGameFile.parse(Files.readAllBytes(saved.getFile()));
        LiveGame live = SaveGameSession.read(file).createLiveGame(lobby.createSavedGamePlayers(), session.getLocalIdentifier());
        Player player = startGame(data, client, live, ChkFile.parseTolerant(live.getSetup().getMapData()), mapPath, mapFile);

        SaveGameUtils.read(file, new SaveGameContext(player.runtime, live));

        return player;
    }

    private static @NotNull Player startGame(@NotNull GameData data,
                                             @NotNull LanClient client, @NotNull LiveGame live,
                                             @NotNull ChkFile scenario,
                                             @NotNull Path mapPath, byte @NotNull [] mapFile) throws IOException {
        client.startNetworkThread();
        live.attachNetwork(client.createTurnTransport(), TURNS_IN_TRANSIT, false);

        ReplayRuntime runtime = ReplayRuntime.createLiveGame(live.getReplay(), scenario,
                                                             data,
                                                             live.getLocalPlayer(), live.getLocalSlot(), true,
                                                             live.getTeamSlotRaces(),
                                                             VIEWPORT_WIDTH, VIEWPORT_HEIGHT,
                                                             INSTRUCTION_LIMIT);
        Game game = runtime.getGame();

        runtime.setSourceFile(GameSourceFile.create(mapPath, mapFile));
        live.startSession(game, runtime.getFrameTiming());
        live.setSyncSource(game, () -> 0);
        runtime.start(0, 0);

        Assertions.assertNull(runtime.getFailure());

        ReplaySession session = runtime.getSession();

        Assertions.assertNotNull(session);

        live.attachReplaySession(session);

        return new Player(live, runtime);
    }


    /**
     * Plays a multiplayer saved game loaded into a new LAN lobby, between two {@link LanClient}s on this computer over the
     * real network stack (multicast discovery, UDP game ports {@code 6116} and {@code 6117}, never {@code 6112}).
     * <p>
     * Client A hosts a melee game on {@code (4)Blood Bath.scm}, client B joins, two computers fill the other slots. At
     * frame {@link #SAVE_FRAME} A (the first player) sends the Save Game command, which every machine executes and writes
     * its own {@code .mlx}; the game runs on to {@link #END_FRAME} and its unit hashes are kept. Then B hosts the saved game
     * from its own file (with the save id); A finds the game with the advertised save id, joins with its own file of that
     * id and the host matches it (same random state, its saved record slot). After the countdown every machine loads its
     * own file with the new network ids (A was player {@code 0} and is now {@code 1}) and the game continues: both clients
     * reach {@link #END_FRAME} with the same unit hash on every frame and no Sync mismatch. Divergence from the original
     * continuation is reported.
     * <p>
     * Fails without the installed game and its melee maps; skipped when the network does not deliver the discovery in
     * time.
     *
     * @param folders the temporary folder of the clients' saved games
     */
    @Test
    void saved_game_reloads_into_a_new_lobby_and_continues_in_sync(@TempDir @NotNull Path folders) throws Exception {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP_NAME);

        byte[] mapFile = Files.readAllBytes(mapPath);
        ChkFile scenario = ChkFile.parseTolerant(MpqArchive.open(mapPath).readFile(SCENARIO));
        Path savesA = folders.resolve("a");
        Path savesB = folders.resolve("b");
        long deadline = System.currentTimeMillis() + LIMIT_MILLISECONDS;
        Map<Integer, Integer> originalHashes = new HashMap<>();

        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
             LanClient clientA = LanClient.open("playera", MapFolders.of(List.of(mapPath.getParent()), LanMapTransfer.DOWNLOAD_FOLDER));
             LanClient clientB = LanClient.open("playerb", MapFolders.of(List.of(mapPath.getParent()), LanMapTransfer.DOWNLOAD_FOLDER))) {
            clientA.setSaveDirectory(savesA, SaveGameKind.MULTIPLAYER);
            clientB.setSaveDirectory(savesB, SaveGameKind.MULTIPLAYER);

            // The original game: A hosts, B joins, two computers.
            int originalPort = LanTestPortUtils.bindFreePort(port -> clientA.host("s4joriginal",
                                                                                  LanClient.HostedMap.read(mapPath, scenario, "Blood Bath"),
                                                                                  LanGameOptions.MELEE, GameSpeed.FASTEST, port));
            LanDiscovery.Game listed = discover(clientA, clientB, "s4joriginal", originalPort, deadline);

            Assumptions.assumeTrue(listed != null, "The discovery did not see the game in time.");

            LanTestPortUtils.bindFreePort(port -> clientB.join(listed, port));

            LanLobby lobbyA = requireLobby(clientA);
            LanLobby lobbyB = joinLobby(clientA, clientB, deadline);

            Assertions.assertTrue(lobbyA.setSlotState(FIRST_COMPUTER_SLOT, LanLobby.SLOT_COMPUTER));
            Assertions.assertTrue(lobbyA.setSlotState(SECOND_COMPUTER_SLOT, LanLobby.SLOT_COMPUTER));

            runUntil(clientA, clientB,
                     deadline,
                     () -> lobbyB.getRecord(SECOND_COMPUTER_SLOT).getType() == EITHER_PREFER_COMPUTER_TYPE && lobbyA.isMapTransferComplete());

            Assertions.assertTrue(lobbyA.startGame());

            runUntil(clientA, clientB, deadline, () -> lobbyA.getState() == STATE_GAME && lobbyB.getState() == STATE_GAME);

            Assertions.assertEquals(STATE_GAME, lobbyB.getState());

            Player originalA = startNewGame(data, clientA, lobbyA, scenario, mapPath, mapFile, savesA);
            Player originalB = startNewGame(data, clientB, lobbyB, scenario, mapPath, mapFile, savesB);
            boolean saveSent = false;

            Assertions.assertEquals(FIRST_PLAYER, originalA.live.getLocalPlayerId());
            Assertions.assertEquals(SECOND_PLAYER, originalB.live.getLocalPlayerId());

            while ((originalA.getFrame() < END_FRAME || originalB.getFrame() < END_FRAME) && System.currentTimeMillis() < deadline) {
                if (!saveSent && originalA.getFrame() == SAVE_FRAME) {
                    // The save dialog's Save of the first player: the command's time is the save id.
                    originalA.live.requestSaveGame(SAVE_TIME_BASE + SAVE_FRAME, SAVE_NAME.getBytes(StandardCharsets.UTF_8));
                    saveSent = true;
                }

                if (originalA.getFrame() < END_FRAME) {
                    originalA.step();
                }

                if (originalB.getFrame() < END_FRAME) {
                    originalB.step();
                }

                if (originalA.getFrame() > SAVE_FRAME) {
                    originalHashes.put(originalA.getFrame(), SyncChecksumUtils.calculateUnitHash(originalA.runtime.getGame()));
                }

                Thread.sleep(0);
            }

            Assertions.assertEquals(END_FRAME, originalA.getFrame());
            Assertions.assertEquals(END_FRAME, originalB.getFrame());
            Assertions.assertEquals(0, originalA.live.getSyncMismatches());
            Assertions.assertEquals(0, originalB.live.getSyncMismatches());

            Path fileA = savesA.resolve(SAVE_NAME + SaveGameKind.MULTIPLAYER.getExtension());
            Path fileB = savesB.resolve(SAVE_NAME + SaveGameKind.MULTIPLAYER.getExtension());

            Assertions.assertTrue(Files.isRegularFile(fileA), "A wrote its saved game");
            Assertions.assertTrue(Files.isRegularFile(fileB), "B wrote its saved game");

            SaveGameInformation informationA = SaveGameInformation.read(Files.readAllBytes(fileA));
            SaveGameInformation informationB = SaveGameInformation.read(Files.readAllBytes(fileB));

            Assertions.assertNotNull(informationA);
            Assertions.assertNotNull(informationB);
            Assertions.assertEquals(informationA.getSaveGameId(), informationB.getSaveGameId(), "one save id on every machine");
            Assertions.assertEquals(informationA.getRandomState(), informationB.getRandomState(), "one random state on every machine");
            Assertions.assertEquals(FIRST_PLAYER, informationA.getLobbySlot());
            Assertions.assertEquals(SECOND_PLAYER, informationB.getLobbySlot());
            Assertions.assertEquals(originalA.live.getLocalSlot(), informationA.getRecordSlot());
            Assertions.assertEquals(originalB.live.getLocalSlot(), informationB.getRecordSlot());

            int savedFrame = originalA.savedFrame;

            Assertions.assertTrue(savedFrame > SAVE_FRAME, "the command executed a few turns after it was sent");
            Assertions.assertEquals(savedFrame, originalB.savedFrame, "every machine saved at the same frame");

            clientA.leave();
            clientB.leave();

            // The saved game: B hosts it from its file, A joins with its own file of the advertised save id.
            LanSavedGame savedB = LanSavedGame.read(fileB);

            int savedPort = LanTestPortUtils.bindFreePort(port -> clientB.hostSaved("s4jsaved", savedB, port));
            LanDiscovery.Game savedListing = discover(clientB, clientA, "s4jsaved", savedPort, deadline);

            Assumptions.assumeTrue(savedListing != null, "The discovery did not see the saved game in time.");

            Assertions.assertEquals(informationB.getSaveGameId() & DWORD_MASK, savedListing.getInformation().getSaveGameId(),
                                    "the advertised save id");

            LanTestPortUtils.bindFreePort(port -> clientA.join(savedListing, port));

            LanLobby hostLobby = requireLobby(clientB);
            LanLobby joinerLobby = joinLobby(clientB, clientA, deadline);
            LanSavedGame savedA = clientA.getSavedGame();

            Assertions.assertNotNull(savedA, "A found its own file of the save id");
            Assertions.assertEquals(fileA, savedA.getFile());
            Assertions.assertEquals(HUMAN_TYPE, hostLobby.getRecord(FIRST_PLAYER).getType(), "the host takes the first open slot");
            Assertions.assertEquals(informationB.getRecordSlot(), hostLobby.getSavedSlot(FIRST_PLAYER));
            Assertions.assertEquals(SECOND_PLAYER, hostLobby.getRecord(SECOND_PLAYER).getPlayerId(), "A takes the next open slot");
            Assertions.assertEquals(informationA.getRecordSlot(), hostLobby.getSavedSlot(SECOND_PLAYER));
            Assertions.assertEquals(EITHER_PREFER_COMPUTER_TYPE, hostLobby.getRecord(FIRST_COMPUTER_SLOT).getType(),
                                    "the computers follow the open slots");
            Assertions.assertFalse(joinerLobby.isRaceChangeAllowed(SECOND_PLAYER, ScrRaceIds.ZERG, SECOND_PLAYER), "a saved game keeps its races");
            Assertions.assertTrue(hostLobby.startGame());

            runUntil(clientA, clientB, deadline, () -> hostLobby.getState() == STATE_GAME && joinerLobby.getState() == STATE_GAME);

            Assertions.assertEquals(STATE_GAME, joinerLobby.getState());

            SavedGamePlayers playersHost = hostLobby.createSavedGamePlayers();
            SavedGamePlayers playersJoiner = joinerLobby.createSavedGamePlayers();

            Assertions.assertEquals(playersHost.toString(), playersJoiner.toString(), "every machine moves the players alike");
            Assertions.assertEquals(FIRST_PLAYER, playersHost.getPlayerId(informationB.getRecordSlot()));
            Assertions.assertEquals(SECOND_PLAYER, playersHost.getPlayerId(informationA.getRecordSlot()));

            Player loadedB = startSavedGame(data, clientB, hostLobby, mapPath, mapFile);
            Player loadedA = startSavedGame(data, clientA, joinerLobby, mapPath, mapFile);

            Assertions.assertEquals(savedFrame, loadedA.getFrame());
            Assertions.assertEquals(savedFrame, loadedB.getFrame());
            Assertions.assertEquals(originalA.live.getLocalPlayer(), loadedA.live.getLocalPlayer(), "A plays its saved slot");
            Assertions.assertEquals(originalB.live.getLocalPlayer(), loadedB.live.getLocalPlayer(), "B plays its saved slot");
            Assertions.assertEquals(SECOND_PLAYER, loadedA.live.getLocalPlayerId());
            Assertions.assertEquals(FIRST_PLAYER, loadedB.live.getLocalPlayerId());

            List<Map<Integer, Integer>> loadedHashes = List.of(new HashMap<>(), new HashMap<>());

            while ((loadedA.getFrame() < END_FRAME || loadedB.getFrame() < END_FRAME) && System.currentTimeMillis() < deadline) {
                if (loadedA.getFrame() < END_FRAME) {
                    loadedA.step();
                }

                if (loadedB.getFrame() < END_FRAME) {
                    loadedB.step();
                }

                List<Player> loaded = List.of(loadedA, loadedB);

                for (int index = 0; index < loaded.size(); index++) {
                    Player player = loaded.get(index);

                    if (player.getFrame() > savedFrame) {
                        loadedHashes.get(index).putIfAbsent(player.getFrame(), SyncChecksumUtils.calculateUnitHash(player.runtime.getGame()));
                    }
                }

                Thread.sleep(0);
            }

            Assertions.assertEquals(END_FRAME, loadedA.getFrame());
            Assertions.assertEquals(END_FRAME, loadedB.getFrame());

            int originalMismatch = NO_MISMATCH;

            for (int frame = savedFrame + 1; frame <= END_FRAME; frame++) {
                Integer hashA = loadedHashes.get(0).get(frame);

                Assertions.assertNotNull(hashA, "A's unit hash at frame " + frame);
                Assertions.assertEquals(hashA, loadedHashes.get(1).get(frame), "the loaded games' unit hashes at frame " + frame);

                if (originalMismatch < 0 && !hashA.equals(originalHashes.get(frame))) {
                    originalMismatch = frame;
                }
            }

            System.out.println("LAN SAVED GAME: the loaded game leaves the original continuation's unit hashes at frame " + originalMismatch);

            Assertions.assertNotEquals(loadedHashes.get(0).get(savedFrame + 1), loadedHashes.get(0).get(END_FRAME),
                                       "the units changed after the load");
            Assertions.assertEquals(0, loadedA.live.getSyncMismatches(), "no Sync mismatch after the load");
            Assertions.assertEquals(0, loadedB.live.getSyncMismatches(), "no Sync mismatch after the load");
            Assertions.assertEquals(SyncChecksumUtils.calculateUnitHash(loadedA.runtime.getGame()),
                                    SyncChecksumUtils.calculateUnitHash(loadedB.runtime.getGame()));
            Assertions.assertEquals(SyncChecksumUtils.calculateResourceHash(loadedA.runtime.getGame()),
                                    SyncChecksumUtils.calculateResourceHash(loadedB.runtime.getGame()));
        }
    }

    /**
     * Exercises the host's checks of a saved game's joiners in memory: a joiner whose file has another random state is refused with {@code 5}
     * ({@code chatINVALIDSAVE}), a joiner that names a record slot another player holds with {@code 2}, and nobody changes a race or a color.
     *
     * @param folders the temporary folder of the saved game
     */
    @Test
    void host_refuses_joiners_of_another_saved_game_or_of_its_own_slot(@TempDir @NotNull Path folders) throws Exception {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP_NAME);

        Path file = writeSavedGame(mapPath, folders);
        LanSavedGame saved = LanSavedGame.read(file);
        SaveGameInformation information = saved.getInformation();
        LanLobby.Setup setup = saved.createLobbySetup("host", MAP_NAME, 0, 0);
        InMemoryLanScenario network = new InMemoryLanScenario(REFUSAL_CLIENTS);

        network.latency = LATENCY;
        network.host(setup, saved.getOptions().encode(), null);

        LanLobby host = network.lobbies[FIRST_PLAYER];

        Assertions.assertNotNull(host);
        Assertions.assertEquals(information.getRecordSlot(), host.getSavedSlot(FIRST_PLAYER), "the host's own record slot");

        // A joiner of another saved game: refusal 5.
        joinSavedGame(network, INVALID_SAVE_JOINER,
                      new SaveGameInformation(information.getSaveGameId(), information.getRandomState() + 1, JOINER_LOBBY_SLOT, JOINER_RECORD_SLOT));

        LanLobby invalidJoiner = network.lobbies[INVALID_SAVE_JOINER];

        Assertions.assertNotNull(invalidJoiner);
        Assertions.assertEquals(REFUSAL_INVALID_SAVE, invalidJoiner.getRefusal());

        // A joiner naming the host's record slot: refusal 2.
        joinSavedGame(network, OCCUPIED_SLOT_JOINER,
                      new SaveGameInformation(information.getSaveGameId(), information.getRandomState(),
                                              JOINER_LOBBY_SLOT, information.getRecordSlot()));

        LanLobby occupiedSlotJoiner = network.lobbies[OCCUPIED_SLOT_JOINER];

        Assertions.assertNotNull(occupiedSlotJoiner);
        Assertions.assertEquals(REFUSAL_NO_SLOT, occupiedSlotJoiner.getRefusal());
        Assertions.assertFalse(host.isRaceChangeAllowed(FIRST_PLAYER, ScrRaceIds.ZERG, FIRST_PLAYER), "the host keeps its saved race");
    }
}
