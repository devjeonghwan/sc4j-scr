package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayBuildCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandCodecs;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.RequirementOpcodes;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.production.PlacementResult;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.ClassicHostTableRegion;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks that the build command tests a construction order with its {@code orders.dat} requirement script: an SCV may place a building as a Probe
 * does once an EUD write names the SCV in the PlaceProtossBuilding script, as the triggers of EUD maps do for walls an SCV warps in.
 */
@RequiresInstallation
final class EudOrderRequirementBuildTest {
    /**
     * The installed melee map, its scenario file, the seed of the live games and the human player's name.
     */
    private static final @NotNull String MAP_NAME = "(2)Bottleneck.scm";
    private static final @NotNull String SCENARIO_FILE = "staredit\\scenario.chk";
    private static final int GAME_SEED = 0x51D7A3E9;
    private static final @NotNull String PLAYER_NAME = "tester";

    /**
     * The minerals the player receives before the command, enough for any Supply Depot.
     */
    private static final int MINERALS = 1000;
    /**
     * The frame the command is sent and the frames the game runs until the Supply Depot is checked.
     */
    private static final int COMMAND_FRAME = 10;
    private static final int PLACEMENT_FRAMES = 300;
    /**
     * The tile distance around the SCV the test searches for a free Supply Depot site.
     */
    private static final int SEARCH_TILES = 12;
    /**
     * The divisor of a placement size that gives the offset of the footprint center.
     */
    private static final int CENTER_DIVISOR = 2;
    private static final int WORD_MASK = 0xFFFF;
    /**
     * The size of the single-unit selection payload: the unit count and one handle.
     */
    private static final int SELECTION_SIZE = Byte.BYTES + Integer.BYTES;

    private static @Nullable GameData gameData;

    /**
     * A live melee game of the human Terran player against a Terran computer on the installed Bottleneck.
     */
    private static final class TerranGame {
        private final @NotNull LiveGame live;
        private final @NotNull ReplayRuntime runtime;
        private final @NotNull Game game;
        private final int player;

        private TerranGame() throws IOException {
            byte[] mapData = MpqArchive.open(ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP_NAME)).readFile(SCENARIO_FILE);
            MeleeGameSetup setup = new MeleeGameSetup(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, 0, new int[]{ScrRaceIds.TERRAN}, GameSpeed.FASTEST);

            Assertions.assertNotNull(gameData);

            this.live    = new LiveGame(setup, GAME_SEED);
            this.runtime = ReplayRuntime.createLiveGame(this.live.getReplay(), ChkFile.parseTolerant(mapData), gameData, this.live.getLocalPlayer(),
                                                        RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                        RunningGameScenario.INSTRUCTION_LIMIT);
            this.runtime.start(0, 0);
            this.game   = this.runtime.getGame();
            this.player = this.live.getLocalPlayer();

            Assertions.assertNull(this.runtime.getFailure());
        }

        private void runTo(int frame) {
            while (this.game.getCurrentFrame() < frame) {
                int current = this.game.getCurrentFrame();

                this.live.exchangeTurn(current);

                Assertions.assertTrue(this.runtime.step(GameSpeed.FASTEST_DURATION, current * GameSpeed.FASTEST_DURATION));
                Assertions.assertNull(this.runtime.getFailure());
            }
        }

        private @Nullable Unit findUnit(int unitType) {
            UnitPool pool = this.game.getUnitSystem().requireUnitPool();

            for (int index = pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (unit.getUnitId() == unitType && unit.getOwnerIndex() == this.player) {
                    return unit;
                }
            }

            return null;
        }

        /**
         * Selects the player's first SCV and sends a building command of it for a Supply Depot at the nearest free site.
         *
         * @param orderId the construction order
         */
        private void buildSupplyDepot(int orderId) {
            Unit scv = this.findUnit(ScrUnitIds.TERRAN_SCV);

            Assertions.assertNotNull(scv);

            ByteBuffer selection = ByteBuffer.allocate(SELECTION_SIZE).order(ByteOrder.LITTLE_ENDIAN);

            selection.put((byte) 1).putInt(this.game.getUnitSystem().requireUnitPool().getHandle(scv.getIndex()));

            Assertions.assertTrue(this.live.submit(ReplayCommandIds.SELECT_121, selection.array()));

            int[] tile = this.findFreeTile(scv, ScrUnitIds.TERRAN_SUPPLY_DEPOT);
            ReplayBuildCommand build = new ReplayBuildCommand(orderId, tile[0], tile[1], ScrUnitIds.TERRAN_SUPPLY_DEPOT);

            this.game.getPlayerTable().getPlayer(this.player).setMinerals(MINERALS);

            Assertions.assertTrue(this.live.submit(ReplayCommandIds.BUILD, ReplayCommandCodecs.encode(ReplayCommandCodecs.BUILD, build)));
        }

        private int @NotNull [] findFreeTile(@NotNull Unit builder, int unitType) {
            ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
            int width = units.getPlacementWidth(unitType);
            int height = units.getPlacementHeight(unitType);
            int builderTileX = builder.getPositionX() / TerrainTileset.TILE_SIZE;
            int builderTileY = builder.getPositionY() / TerrainTileset.TILE_SIZE;

            for (int distance = 1; distance <= SEARCH_TILES; distance++) {
                for (int offsetY = -distance; offsetY <= distance; offsetY++) {
                    for (int offsetX = -distance; offsetX <= distance; offsetX++) {
                        int tileX = builderTileX + offsetX;
                        int tileY = builderTileY + offsetY;
                        int code = this.game.getUnitPlacement().getPlacementCode(builder, builder, this.player, unitType,
                                                                                tileX * TerrainTileset.TILE_SIZE + width / CENTER_DIVISOR,
                                                                                tileY * TerrainTileset.TILE_SIZE + height / CENTER_DIVISOR,
                                                                                false, false);

                        if (tileX >= 0 && tileY >= 0 && code == PlacementResult.VALID) {
                            return new int[]{tileX, tileY};
                        }
                    }
                }
            }

            return Assertions.fail("No free Supply Depot site lies near the SCV.");
        }

        /**
         * Writes, through the EUD address space, the SCV as the unit the PlaceProtossBuilding requirement script names in place of the Probe.
         */
        private void nameScvInProtossPlacementScript() {
            int[] script = this.game.getRequirements().getScripts()[0];
            ClassicAddressSpace space = ClassicAddressSpace.create(this.game);

            for (int position = 1; position + 2 < script.length; position++) {
                if (script[position - 1] == RequirementOpcodes.END && script[position] == ScrOrderIds.PLACE_PROTOSS_BUILDING &&
                    script[position + 1] == RequirementOpcodes.CURRENT_UNIT && script[position + 2] == ScrUnitIds.PROTOSS_PROBE) {
                    int address = ClassicHostTableRegion.REQUIREMENT_SCRIPTS[0] + (position + 2) * Short.BYTES;
                    int aligned = address & -Integer.BYTES;
                    int shift = (address - aligned) * Byte.SIZE;

                    space.writeDword(aligned, space.readDword(aligned) & ~(WORD_MASK << shift) | ScrUnitIds.TERRAN_SCV << shift);

                    return;
                }
            }

            Assertions.fail("The installed order script has no PlaceProtossBuilding entry naming the Probe.");
        }
    }

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }

    @Test
    void scv_cannot_place_a_building_as_a_probe_with_the_installed_order_script() throws IOException {
        TerranGame terran = new TerranGame();

        terran.runTo(COMMAND_FRAME);
        terran.buildSupplyDepot(ScrOrderIds.PLACE_PROTOSS_BUILDING);
        terran.runTo(COMMAND_FRAME + PLACEMENT_FRAMES);

        Assertions.assertNull(terran.findUnit(ScrUnitIds.TERRAN_SUPPLY_DEPOT));
    }

    @Test
    void scv_warps_in_a_building_once_an_eud_write_names_it_in_the_order_script() throws IOException {
        TerranGame terran = new TerranGame();

        terran.runTo(COMMAND_FRAME);
        terran.nameScvInProtossPlacementScript();
        terran.buildSupplyDepot(ScrOrderIds.PLACE_PROTOSS_BUILDING);
        terran.runTo(COMMAND_FRAME + PLACEMENT_FRAMES);

        Unit depot = terran.findUnit(ScrUnitIds.TERRAN_SUPPLY_DEPOT);
        Unit scv = terran.findUnit(ScrUnitIds.TERRAN_SCV);

        Assertions.assertNotNull(depot, "the SCV places the Supply Depot");
        Assertions.assertNotNull(scv);
        Assertions.assertEquals(ScrOrderIds.INCOMPLETE_WARPING, depot.getPrimaryOrder(), "the Supply Depot warps in as a Protoss building");
        Assertions.assertNotEquals(ScrOrderIds.CONSTRUCTING_BUILDING, scv.getPrimaryOrder(), "the SCV leaves the building");
    }

    @Test
    void scv_places_a_building_with_the_installed_order_script() throws IOException {
        TerranGame terran = new TerranGame();

        terran.runTo(COMMAND_FRAME);
        terran.buildSupplyDepot(ScrOrderIds.PLACE_BUILDING);
        terran.runTo(COMMAND_FRAME + PLACEMENT_FRAMES);

        Assertions.assertNotNull(terran.findUnit(ScrUnitIds.TERRAN_SUPPLY_DEPOT));
    }
}
