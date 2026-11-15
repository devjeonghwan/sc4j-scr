package org.fogofwarlabs.sc4j.scr.net.lan;

import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.fogofwarlabs.sc4j.scr.content.*;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayBuildCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandCodecs;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTraitFlags;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Verifies that both clients of an in-memory melee LAN game of two Zerg players, whose Hatcheries morph into an added unit type in the same frame,
 * hash every frame alike, with no Sync mismatch, and end with the same added units.
 */
@RequiresInstallation
final class LanAddedUnitGameTest {
    private static final int PLAYERS = 2;
    private static final int HOST_CLIENT = 0;
    private static final int LATENCY = 2;
    private static final int TURNS_IN_TRANSIT = 3;
    private static final int FRAME_MILLISECONDS = 42;
    private static final int VIEWPORT_WIDTH = 640;
    private static final int VIEWPORT_HEIGHT = 480;
    private static final int INSTRUCTION_LIMIT = 100_000;
    private static final int ITERATION_LIMIT = 400_000;
    private static final int STATE_GAME = 9;

    private static final @NotNull String MAP_NAME = "(2)Bottleneck.scm";
    private static final @NotNull String SCENARIO = "staredit\\scenario.chk";

    private static final int MAP_TRANSFER_LIMIT_MILLISECONDS = 20_000;
    private static final int LOBBY_SETTLE_MILLISECONDS = 1000;
    private static final int GAME_START_LIMIT_MILLISECONDS = 20_000;

    /**
     * The test-only morph result: a free and quickly morphed copy of the Ultralisk that a Hatchery morphs into.
     */
    private static final @NotNull String TITAN_NAME = "Test Titan";
    private static final int TITAN_MINERAL_COST = 0;
    private static final int TITAN_GAS_COST = 0;
    private static final int TITAN_BUILD_TIME = 120;
    /**
     * The frame each client sends a Drone to build an Extractor, which keeps the player from being defeated once its Hatchery is a unit, the frame
     * each client morphs its Hatchery, and the frames the game runs.
     */
    private static final int EXTRACTOR_FRAME = 5;
    private static final int MORPH_FRAME = 300;
    private static final int FRAMES = 700;
    /**
     * The selection command of one unit: the unit count, then the handle.
     */
    private static final int SELECTION_SIZE = Byte.BYTES + Integer.BYTES;

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
         * Submits the commands of the local player at a frame: the Extractor, then the morph of the Hatchery into the added type.
         *
         * @param frame   the current frame
         * @param titanId the added type
         */
        private void command(long frame, int titanId) {
            if (frame == EXTRACTOR_FRAME) {
                Unit drone = this.findUnit(ScrUnitIds.ZERG_DRONE);
                Unit geyser = this.findNearestGeyser(this.findUnit(ScrUnitIds.ZERG_HATCHERY));
                ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
                int tileX = (geyser.getPositionX() - units.getPlacementWidth(ScrUnitIds.ZERG_EXTRACTOR) / 2) / TerrainTileset.TILE_SIZE;
                int tileY = (geyser.getPositionY() - units.getPlacementHeight(ScrUnitIds.ZERG_EXTRACTOR) / 2) / TerrainTileset.TILE_SIZE;
                ReplayBuildCommand build = new ReplayBuildCommand(ScrOrderIds.DRONE_START_BUILD, tileX, tileY, ScrUnitIds.ZERG_EXTRACTOR);

                this.select(drone);

                Assertions.assertTrue(this.live.submit(ReplayCommandIds.BUILD, ReplayCommandCodecs.encode(ReplayCommandCodecs.BUILD, build)));
            }

            if (frame == MORPH_FRAME) {
                ByteBuffer morph = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);

                morph.putShort((short) titanId);
                this.select(this.findUnit(ScrUnitIds.ZERG_HATCHERY));

                Assertions.assertTrue(this.live.submit(ReplayCommandIds.BUILDING_MORPH, morph.array()));
            }
        }

        private void select(@NotNull Unit unit) {
            ByteBuffer selection = ByteBuffer.allocate(SELECTION_SIZE).order(ByteOrder.LITTLE_ENDIAN);

            selection.put((byte) 1).putInt(this.game.getUnitSystem().requireUnitPool().getHandle(unit.getIndex()));

            Assertions.assertTrue(this.live.submit(ReplayCommandIds.SELECT_121, selection.array()));
        }

        private @NotNull Unit findUnit(int unitType) {
            UnitPool pool = this.game.getUnitSystem().requireUnitPool();
            int player = this.live.getLocalPlayer();

            for (int index = pool.getFirstOwnerIndex(player); index != 0; index = pool.getNextOwnerIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (unit.getUnitId() == unitType && !unit.isDestroyed()) {
                    return unit;
                }
            }

            return Assertions.fail("client of player " + player + " has no unit of type " + unitType);
        }

        private @NotNull Unit findNearestGeyser(@NotNull Unit building) {
            UnitPool pool = this.game.getUnitSystem().requireUnitPool();
            Unit geyser = null;
            long nearest = Long.MAX_VALUE;

            for (int index = 1; index <= pool.getCapacity(); index++) {
                Unit candidate = pool.getUnit(index);

                if (!pool.isAllocated(index) || candidate.getUnitId() != ScrUnitIds.RESOURCE_VESPENE_GEYSER) {
                    continue;
                }

                long differenceX = candidate.getPositionX() - building.getPositionX();
                long differenceY = candidate.getPositionY() - building.getPositionY();

                if (differenceX * differenceX + differenceY * differenceY < nearest) {
                    nearest = differenceX * differenceX + differenceY * differenceY;
                    geyser  = candidate;
                }
            }

            Assertions.assertNotNull(geyser);

            return geyser;
        }
    }

    @Test
    void both_clients_hash_alike_when_their_hatcheries_morph_into_an_added_unit_type() throws Exception {
        Path mapPath = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP_NAME);

        byte[] mapFile = Files.readAllBytes(mapPath);
        byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO);
        ChkFile scenario = ChkFile.parseTolerant(mapData);
        LanGameOptions options = LanGameOptions.MELEE;
        InMemoryLanScenario network = new InMemoryLanScenario(PLAYERS);
        LanLobby.Setup setup = LanLobby.Setup.create(scenario, options,
                                                     "host",
                                                     MAP_NAME, mapFile.length, LanMapTransfer.calculateChecksum(mapFile, mapFile.length));
        LanLobby[] lobbies = network.lobbies;

        network.latency = LATENCY;
        network.host(setup, options.encode(), mapPath);
        network.join(1, mapPath.getParent(), mapPath.getParent().resolve("Download"));
        network.runUntil(MAP_TRANSFER_LIMIT_MILLISECONDS, lan -> {
            LanLobby host = lan.lobbies[HOST_CLIENT];

            Assertions.assertNotNull(host);

            return host.isMapTransferComplete();
        });
        network.run(LOBBY_SETTLE_MILLISECONDS);

        for (LanLobby lobby : lobbies) {
            Assertions.assertNotNull(lobby);

            lobby.changeRace(ScrRaceIds.ZERG);
        }

        network.run(LOBBY_SETTLE_MILLISECONDS);

        LanLobby host = lobbies[HOST_CLIENT];

        Assertions.assertNotNull(host);
        Assertions.assertTrue(host.startGame());

        network.runUntil(GAME_START_LIMIT_MILLISECONDS, lan -> {
            for (LanLobby lobby : lan.lobbies) {
                Assertions.assertNotNull(lobby);

                if (lobby.getState() != STATE_GAME) {
                    return false;
                }
            }

            return true;
        });

        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            int titanId = data.getAddedUnitTypes().add(
                    new ScrAddedUnitType.Builder(new UnitType(TITAN_NAME), TITAN_NAME, ScrUnits.ZERG_ULTRALISK, data.getUnits())
                            .setValue(UnitsDat.MINERAL_COST, TITAN_MINERAL_COST)
                            .setValue(UnitsDat.GAS_COST, TITAN_GAS_COST)
                            .setValue(UnitsDat.BUILD_TIME, TITAN_BUILD_TIME)
                            .setTrait(ScrUnitTypeView.TraitColumn.TRAITS, UnitTraitFlags.BUILDING_MORPH_RESULT)
                            .setRequirementScript(new ScrRequirementScript.Builder().requireProducer(ScrUnits.ZERG_HATCHERY).requireNotBusy().build())
                            .build()
            );
            Client[] clients = new Client[PLAYERS];

            for (int index = 0; index < PLAYERS; index++) {
                LanLobby lobby = lobbies[index];

                Assertions.assertNotNull(lobby);

                MeleeGameSetup gameSetup = lobby.createGameSetup(mapData, GameSpeed.FASTEST, true);
                LiveGame live = new LiveGame(gameSetup, lobby.getSeed(), network.sessions[index].getLocalIdentifier());

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

            long[] commanded = {-1, -1};

            for (int iteration = 0; iteration < ITERATION_LIMIT; iteration++) {
                boolean done = true;

                network.now++;

                for (int index = 0; index < PLAYERS; index++) {
                    Client client = clients[index];
                    long frame = client.game.getCurrentFrame();

                    if (frame >= FRAMES) {
                        continue;
                    }

                    done = false;

                    if (commanded[index] != frame) {
                        client.command(frame, titanId);
                        commanded[index] = frame;
                    }

                    if (!client.live.exchangeTurn(frame)) {
                        continue;
                    }

                    client.runtime.step(FRAME_MILLISECONDS, network.now);

                    Assertions.assertNull(client.runtime.getFailure(),
                                          () -> "frame " + client.game.getCurrentFrame() + ": " + client.runtime.getFailure());
                }

                long frame = clients[HOST_CLIENT].game.getCurrentFrame();

                if (clients[1].game.getCurrentFrame() == frame) {
                    Assertions.assertEquals(SyncChecksumUtils.calculateUnitHash(clients[HOST_CLIENT].game),
                                            SyncChecksumUtils.calculateUnitHash(clients[1].game),
                                            "unit hash at frame " + frame);
                }

                if (done) {
                    break;
                }
            }

            for (Client client : clients) {
                UnitAccounting accounting = client.game.getPlayerTable().requireUnitAccounting();

                Assertions.assertEquals(FRAMES, client.game.getCurrentFrame());
                Assertions.assertEquals(0, client.live.getSyncMismatches());

                for (Client owner : clients) {
                    int player = owner.live.getLocalPlayer();

                    Assertions.assertEquals(1, accounting.getCompletedTypeCount(titanId, player), "player " + player + " has the added unit");
                    Assertions.assertEquals(0, accounting.getTypeCount(ScrUnitIds.ZERG_HATCHERY, player));
                }
            }
        }
    }
}
