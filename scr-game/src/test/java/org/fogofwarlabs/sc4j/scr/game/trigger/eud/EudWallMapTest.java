package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.core.test.CorpusTest;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayBuildCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandCodecs;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.presentation.HostButtonSets;
import org.fogofwarlabs.sc4j.scr.game.production.PlacementResult;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.LiveMapGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 * Plays the EUD map "Wall and block a million zerglings 3" headless: its triggers rewrite the requirement scripts, the button sets and the DAT
 * tables at the start, so the SCV warps in Supply Depots as walls (PlaceProtossBuilding), the Engineering Bay upgrades without research time and
 * the SCV trains in the map's build time.
 * <p>
 * The map is a user map: it is read from the client's maps folder ({@link ExternalInput#MAPS}) under its original name.
 */
@CorpusTest({ExternalInput.INSTALLATION, ExternalInput.MAPS})
final class EudWallMapTest {
    private static final @NotNull String MAP_NAME = "eud-wall-million-zergling-3-v0.04.scx";
    private static final @NotNull String SCENARIO_FILE = "staredit\\scenario.chk";
    private static final @NotNull String PLAYER_NAME = "tester";
    private static final int GAME_SEED = 0x1234;
    private static final int HUMAN_LOBBY_SLOT = 0;

    /**
     * The Remastered function of the Protoss build button action, the action the map gives the SCV's Supply Depot button.
     */
    private static final long PROTOSS_BUILD_ACTION = 0x1403A9DE0L;
    /**
     * The frame the map's start triggers have rewritten the game data by.
     */
    private static final int START_FRAME = 20;
    /**
     * The frames a command takes from its submission to its execution at most, and the frames the tests wait for a command's effect.
     */
    private static final int COMMAND_FRAMES = 12;
    private static final int PLACEMENT_FRAMES = 300;
    private static final int COMPLETION_FRAMES = 1000;
    /**
     * The resources the player receives before a command, enough for any cost of the map.
     */
    private static final int MINERALS = 5000;
    /**
     * The pixel distances from the SCV the tests search for a free site, and the angle steps of a search circle.
     */
    private static final int FIRST_SEARCH_RADIUS = 96;
    private static final int LAST_SEARCH_RADIUS = 1200;
    private static final int SEARCH_RADIUS_STEP = 64;
    private static final int SEARCH_ANGLES = 16;
    private static final int SEARCH_TILES = 12;
    private static final int CENTER_DIVISOR = 2;
    /**
     * The size of the single-unit selection payload: the unit count and one handle.
     */
    private static final int SELECTION_SIZE = Byte.BYTES + Integer.BYTES;

    /**
     * A live Use Map Settings game of the human player on the map.
     */
    private static final class WallGame implements AutoCloseable {
        private final @NotNull GameData gameData;
        private final @NotNull LiveGame live;
        private final @NotNull ReplayRuntime runtime;
        private final @NotNull Game game;
        private final int player;

        private WallGame() throws IOException {
            Path mapPath = ExternalInput.MAPS.resolveFile(MAP_NAME);
            byte[] mapData = MpqArchive.open(mapPath).readFile(SCENARIO_FILE);
            MeleeGameSetup setup = MeleeGameSetup.createUseMapSettings(mapData, PLAYER_NAME, ScrRaceIds.TERRAN, HUMAN_LOBBY_SLOT, GameSpeed.FASTEST);

            this.gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
            this.live     = new LiveGame(setup, GAME_SEED);
            this.runtime  = LiveMapGameScenario.startLive(this.gameData, this.live, mapPath);
            this.game     = this.runtime.getGame();
            this.player   = this.live.getLocalPlayer();
            this.runTo(START_FRAME);
        }

        @Override
        public void close() {
            this.gameData.close();
        }

        private void runTo(int frame) {
            while (this.game.getCurrentFrame() < frame) {
                LiveMapGameScenario.stepFrame(this.runtime, this.live);

                Assertions.assertNull(this.runtime.getFailure());
            }
        }

        private int countUnits(int unitType) {
            UnitPool pool = this.game.getUnitSystem().requireUnitPool();
            int count = 0;

            for (int index = pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (unit.getUnitId() == unitType && unit.getOwnerIndex() == this.player) {
                    count++;
                }
            }

            return count;
        }

        private @NotNull Unit requireUnit(int unitType) {
            UnitPool pool = this.game.getUnitSystem().requireUnitPool();

            for (int index = pool.getFirstCreatedIndex(true); index != 0; index = pool.getNextCreatedIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (unit.getUnitId() == unitType && unit.getOwnerIndex() == this.player) {
                    return unit;
                }
            }

            return Assertions.fail("The player has no unit of type " + unitType + ".");
        }

        private void select(@NotNull Unit unit) {
            ByteBuffer selection = ByteBuffer.allocate(SELECTION_SIZE).order(ByteOrder.LITTLE_ENDIAN);

            selection.put((byte) 1).putInt(this.game.getUnitSystem().requireUnitPool().getHandle(unit.getIndex()));

            Assertions.assertTrue(this.live.submit(ReplayCommandIds.SELECT_121, selection.array()));
        }

        private void grantMinerals() {
            this.game.getPlayerTable().getPlayer(this.player).setMinerals(MINERALS);
        }

        /**
         * Creates a completed building of the player at the first free site on circles around the SCV.
         *
         * @param unitType the building type
         * @return the building
         */
        private @NotNull Unit createBuilding(int unitType) {
            Unit scv = this.requireUnit(ScrUnitIds.TERRAN_SCV);

            for (int radius = FIRST_SEARCH_RADIUS; radius < LAST_SEARCH_RADIUS; radius += SEARCH_RADIUS_STEP) {
                for (int step = 0; step < SEARCH_ANGLES; step++) {
                    double angle = 2 * Math.PI * step / SEARCH_ANGLES;
                    Unit building = this.game.getTriggerUnitActions().createUnit(unitType,
                                                                                 scv.getPositionX() + (int) (radius * Math.cos(angle)),
                                                                                 scv.getPositionY() + (int) (radius * Math.sin(angle)),
                                                                                 this.player,
                                                                                 Integer.MAX_VALUE);

                    if (building != null) {
                        return building;
                    }
                }
            }

            return Assertions.fail("No free site for type " + unitType + " lies near the SCV.");
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

            return Assertions.fail("No free site for type " + unitType + " lies near the SCV.");
        }
    }

    private static boolean hasButtonAction(@NotNull HostButtonSets sets, long action, int actionValue) {
        for (int set = 0; set < HostButtonSets.SET_COUNT; set++) {
            HostButtonSets.Button[] buttons = sets.getButtons(set);

            if (buttons == null) {
                continue;
            }

            for (HostButtonSets.Button button : buttons) {
                if (button.getAction() == action && button.getActionValue() == actionValue) {
                    return true;
                }
            }
        }

        return false;
    }

    @Test
    void scv_warps_in_a_supply_depot_wall() throws IOException {
        try (WallGame wall = new WallGame()) {
            Assertions.assertTrue(hasButtonAction(wall.game.getGameContext().getHostButtonSets(), PROTOSS_BUILD_ACTION, ScrUnitIds.TERRAN_SUPPLY_DEPOT),
                                  "the map's SCV places the Supply Depot with the Protoss build action");

            Unit scv = wall.requireUnit(ScrUnitIds.TERRAN_SCV);
            int[] tile = wall.findFreeTile(scv, ScrUnitIds.TERRAN_SUPPLY_DEPOT);
            ReplayBuildCommand build = new ReplayBuildCommand(ScrOrderIds.PLACE_PROTOSS_BUILDING, tile[0], tile[1], ScrUnitIds.TERRAN_SUPPLY_DEPOT);

            wall.grantMinerals();
            wall.select(scv);

            Assertions.assertTrue(wall.live.submit(ReplayCommandIds.BUILD, ReplayCommandCodecs.encode(ReplayCommandCodecs.BUILD, build)));

            wall.runTo(wall.game.getCurrentFrame() + PLACEMENT_FRAMES);

            Unit depot = wall.requireUnit(ScrUnitIds.TERRAN_SUPPLY_DEPOT);
            int completionLimit = wall.game.getCurrentFrame() + COMPLETION_FRAMES;

            Assertions.assertNotEquals(ScrOrderIds.CONSTRUCTING_BUILDING, scv.getPrimaryOrder(), "the wall warps in without its builder");

            while ((depot.getStatus() & UnitStatusFlags.COMPLETED) == 0) {
                Assertions.assertTrue(wall.game.getCurrentFrame() < completionLimit, "the wall completes");

                wall.runTo(wall.game.getCurrentFrame() + 1);
            }
        }
    }

    @Test
    void engineering_bay_raises_the_infantry_weapons_level() throws IOException {
        try (WallGame wall = new WallGame()) {
            Unit engineeringBay = wall.createBuilding(ScrUnitIds.TERRAN_ENGINEERING_BAY);
            int level = wall.game.getPlayerTable().requirePlayerUpgrades().getLevel(wall.player, ScrUpgradeIds.TERRAN_INFANTRY_WEAPONS);

            wall.grantMinerals();
            wall.select(engineeringBay);

            Assertions.assertTrue(wall.live.submit(ReplayCommandIds.UPGRADE, new byte[]{(byte) ScrUpgradeIds.TERRAN_INFANTRY_WEAPONS}));

            wall.runTo(wall.game.getCurrentFrame() + COMMAND_FRAMES);

            Assertions.assertEquals(level + 1,
                                    wall.game.getPlayerTable().requirePlayerUpgrades().getLevel(wall.player, ScrUpgradeIds.TERRAN_INFANTRY_WEAPONS));
        }
    }

    @Test
    void command_center_trains_an_scv_in_the_map_build_time() throws IOException {
        try (WallGame wall = new WallGame()) {
            int buildTime = wall.game.requireGameData().getUnitTypes().getBuildTime(ScrUnitIds.TERRAN_SCV);

            Assertions.assertNotEquals(wall.gameData.getUnitTypes().getBuildTime(ScrUnitIds.TERRAN_SCV), buildTime, "the map rewrites the build time");

            Unit commandCenter = wall.createBuilding(ScrUnitIds.TERRAN_COMMAND_CENTER);
            int scvs = wall.countUnits(ScrUnitIds.TERRAN_SCV);
            int submitted = wall.game.getCurrentFrame();

            wall.grantMinerals();
            wall.select(commandCenter);

            Assertions.assertTrue(wall.live.submit(ReplayCommandIds.TRAIN, new byte[]{(byte) ScrUnitIds.TERRAN_SCV, 0}));

            while (wall.countUnits(ScrUnitIds.TERRAN_SCV) == scvs) {
                Assertions.assertTrue(wall.game.getCurrentFrame() <= submitted + buildTime + COMMAND_FRAMES, "the SCV trains in the map's build time");

                wall.runTo(wall.game.getCurrentFrame() + 1);
            }

            Assertions.assertTrue(wall.game.getCurrentFrame() - submitted >= buildTime);
        }
    }
}
