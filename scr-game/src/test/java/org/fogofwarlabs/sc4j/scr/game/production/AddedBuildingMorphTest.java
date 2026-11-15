package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.fogofwarlabs.sc4j.scr.content.*;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.format.mpq.MpqArchive;
import org.fogofwarlabs.sc4j.scr.data.format.replay.*;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTraitFlags;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks a Zerg building morph into an added unit type in a live melee game: the Hatchery of the human Zerg player morphs into a test-only copy of
 * the Ultralisk through its own requirement script, is charged once, can be canceled with a refund, and completes as a unit that leaves the
 * Hatchery's supply, depot, creep and Larvae behind and walks away from the Hatchery's place, in a game whose saved replay plays back with equal
 * state hashes.
 */
@RequiresInstallation
final class AddedBuildingMorphTest {
    /**
     * The installed melee map, its scenario file and the seed of the live games.
     */
    private static final @NotNull String MAP_NAME = "(2)Bottleneck.scm";
    private static final @NotNull String SCENARIO_FILE = "staredit\\scenario.chk";
    private static final int GAME_SEED = 0x2F6A1C35;
    private static final @NotNull String PLAYER_NAME = "tester";
    /**
     * The resource cheat the morph game types, and the Unix time of the typed line.
     */
    private static final @NotNull String RESOURCE_CHEAT = "show me the money";
    private static final int TYPED_TIME = 0x6543210F;

    /**
     * The values of the test-only morph result: a cheap and quickly morphed copy of the Ultralisk.
     */
    private static final @NotNull String TITAN_NAME = "Test Titan";
    private static final int TITAN_MINERAL_COST = 20;
    private static final int TITAN_GAS_COST = 0;
    private static final int TITAN_BUILD_TIME = 120;
    private static final int TITAN_BUTTON_POSITION = 8;
    private static final char TITAN_BUTTON_HOTKEY = 't';
    private static final @NotNull String TITAN_BUTTON_TEXT = "Mutate into " + TITAN_NAME;
    /**
     * A second morph result whose requirement script needs a Hive, which the player does not have.
     */
    private static final @NotNull String LOCKED_NAME = "Locked Titan";
    /**
     * A third added type whose extents exceed those of every native type.
     */
    private static final @NotNull String COLOSSAL_NAME = "Colossal Titan";
    private static final int COLOSSAL_EXTENT = 400;

    /**
     * The frame a Drone is sent to build an Extractor and the frame by which it starts it, the frame the commands select the Hatchery and start
     * the morph, the frames until a command runs, the frames until the Drone is gone, the frame the cancel test cancels the morph, and the frames
     * the games run.
     */
    private static final int EXTRACTOR_FRAME = 5;
    private static final int EXTRACTOR_FRAME_LIMIT = 600;
    private static final int MORPH_FRAME = 10;
    private static final int COMMAND_FRAMES = 2;
    private static final int SETTLE_FRAMES = 30;
    private static final int CANCEL_FRAME = 60;
    private static final int CANCEL_GAME_FRAMES = 80;
    private static final int COMPLETION_FRAME_LIMIT = 600;
    private static final int GAME_FRAMES = 3000;
    /**
     * The pixels the morphed unit walks from the Hatchery's place, the pixels it may stop short of the clicked position, and the frame limit of
     * the walk.
     */
    private static final int WALK_DISTANCE = 256;
    private static final int ARRIVAL_DISTANCE = 32;
    private static final int WALK_FRAME_LIMIT = 600;
    /**
     * The quarters of the cost a canceled building morph refunds.
     */
    private static final int REFUND_QUARTERS = 3;
    private static final int QUARTER_DIVISOR = 4;
    /**
     * The tiles around the Hatchery center the creep test counts.
     */
    private static final int CREEP_TILE_RADIUS = 6;

    private static @Nullable GameData gameData;
    private static int titanId;
    private static int lockedId;
    private static int colossalId;

    /**
     * A live melee game of the human Zerg player against a Zerg computer on the installed Bottleneck.
     */
    private static final class ZergGame {
        private final @NotNull LiveGame live;
        private final @NotNull ReplayRuntime runtime;
        private final @NotNull Game game;
        private final int player;

        private ZergGame() throws IOException {
            byte[] mapData = MpqArchive.open(ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, MAP_NAME)).readFile(SCENARIO_FILE);
            MeleeGameSetup setup = new MeleeGameSetup(mapData, PLAYER_NAME, ScrRaceIds.ZERG, 0, new int[]{ScrRaceIds.ZERG}, GameSpeed.FASTEST);

            this.live    = new LiveGame(setup, GAME_SEED);
            this.runtime = ReplayRuntime.createLiveGame(this.live.getReplay(), ChkFile.parseTolerant(mapData), requireGameData(),
                                                        this.live.getLocalPlayer(),
                                                        RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                        RunningGameScenario.INSTRUCTION_LIMIT);
            this.runtime.start(0, 0);
            this.game   = this.runtime.getGame();
            this.player = this.live.getLocalPlayer();

            Assertions.assertNull(this.runtime.getFailure());
        }

        /**
         * Exchanges the turn and runs one frame.
         */
        private void step() {
            int frame = this.game.getCurrentFrame();

            this.live.exchangeTurn(frame);

            Assertions.assertTrue(this.runtime.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION));
            Assertions.assertNull(this.runtime.getFailure());
        }

        private void runTo(int frame) {
            while (this.game.getCurrentFrame() < frame) {
                this.step();
            }
        }

        /**
         * Submits the selection of one unit.
         */
        private void select(@NotNull Unit unit) {
            int handle = this.game.getUnitSystem().requireUnitPool().getHandle(unit.getIndex());
            ByteBuffer selection = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);

            selection.put((byte) 1).putInt(handle);

            Assertions.assertTrue(this.live.submit(ReplayCommandIds.SELECT_121, selection.array()));
        }

        /**
         * Submits the resource cheat, which the game of a single human player accepts.
         */
        private void grantResources() {
            GameFrame frame = this.game.getFrame();

            Assertions.assertNotNull(frame);
            Assertions.assertEquals(LiveGame.TEXT_CHEAT, this.live.submitText(RESOURCE_CHEAT, frame.getVisibility().getCheatFlags(), true,
                                                                              this.game.getRuntimeRandom(), TYPED_TIME));
        }

        /**
         * Submits the selection of a Drone and its Extractor on the Vespene Geyser nearest to a building, so the player keeps a building and is not
         * defeated once the building morphs into a unit.
         */
        private void buildExtractor(@NotNull Unit drone, @NotNull Unit building) {
            UnitPool pool = this.game.getUnitSystem().requireUnitPool();
            ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();
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

            int tileX = (geyser.getPositionX() - units.getPlacementWidth(ScrUnitIds.ZERG_EXTRACTOR) / 2) / TerrainTileset.TILE_SIZE;
            int tileY = (geyser.getPositionY() - units.getPlacementHeight(ScrUnitIds.ZERG_EXTRACTOR) / 2) / TerrainTileset.TILE_SIZE;
            ReplayBuildCommand build = new ReplayBuildCommand(ScrOrderIds.DRONE_START_BUILD, tileX, tileY, ScrUnitIds.ZERG_EXTRACTOR);

            this.select(drone);

            Assertions.assertTrue(this.live.submit(ReplayCommandIds.BUILD, ReplayCommandCodecs.encode(ReplayCommandCodecs.BUILD, build)));
        }

        /**
         * Submits a right click of the selected units on the ground.
         */
        private void rightClick(int positionX, int positionY) {
            ReplayTargetCommand command = ReplayTargetCommand.createRightClick(positionX, positionY, 0, Integer.BYTES, ScrUnitIds.NONE, 0);

            Assertions.assertTrue(this.live.submit(ReplayCommandIds.RIGHT_CLICK_121,
                                                   ReplayCommandCodecs.encode(ReplayCommandCodecs.RIGHT_CLICK_121, command)));
        }

        /**
         * Submits the building morph of the selected building into a type.
         */
        private void morphBuilding(int unitType) {
            ByteBuffer morph = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);

            morph.putShort((short) unitType);

            Assertions.assertTrue(this.live.submit(ReplayCommandIds.BUILDING_MORPH, morph.array()));
        }

        private @NotNull Player requirePlayer() {
            Player owner = this.game.getPlayerTable().getPlayer(this.player);

            Assertions.assertNotNull(owner);

            return owner;
        }

        private @NotNull Unit requireUnit(int unitType) {
            List<Unit> found = this.findUnits(unitType);

            Assertions.assertEquals(1, found.size(), "one unit of type " + unitType);

            return found.getFirst();
        }

        private @NotNull List<Unit> findUnits(int unitType) {
            UnitPool pool = this.game.getUnitSystem().requireUnitPool();
            List<Unit> found = new ArrayList<>();

            for (int index = pool.getFirstOwnerIndex(this.player); index != 0; index = pool.getNextOwnerIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (unit.getUnitId() == unitType && !unit.isDestroyed() && unit.getSpriteOrNull() != null) {
                    found.add(unit);
                }
            }

            return found;
        }

        /**
         * Counts the creep tiles in the square of {@link #CREEP_TILE_RADIUS} tiles around a position.
         */
        private int countCreepTiles(int positionX, int positionY) {
            GameMap map = this.game.getMap();
            int centerX = positionX / TerrainTileset.TILE_SIZE;
            int centerY = positionY / TerrainTileset.TILE_SIZE;
            int count = 0;

            for (int tileY = Math.max(0, centerY - CREEP_TILE_RADIUS); tileY <= Math.min(map.getHeight() - 1, centerY + CREEP_TILE_RADIUS); tileY++) {
                for (int tileX = Math.max(0, centerX - CREEP_TILE_RADIUS); tileX <= Math.min(map.getWidth() - 1, centerX + CREEP_TILE_RADIUS); tileX++) {
                    if ((map.getTileFlags(tileX, tileY) & MapTileFlags.HAS_CREEP) != 0) {
                        count++;
                    }
                }
            }

            return count;
        }
    }


    @BeforeAll
    static void loadGameData() throws IOException {
        GameData installedData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
        ScrAddedUnitType titan = new ScrAddedUnitType.Builder(new UnitType(TITAN_NAME), TITAN_NAME, ScrUnits.ZERG_ULTRALISK, installedData.getUnits())
                .setValue(UnitsDat.MINERAL_COST, TITAN_MINERAL_COST)
                .setValue(UnitsDat.GAS_COST, TITAN_GAS_COST)
                .setValue(UnitsDat.BUILD_TIME, TITAN_BUILD_TIME)
                .setTrait(ScrUnitTypeView.TraitColumn.TRAITS, UnitTraitFlags.BUILDING_MORPH_RESULT)
                .setRequirementScript(new ScrRequirementScript.Builder().requireProducer(ScrUnits.ZERG_HATCHERY).requireNotBusy().build())
                .setTrainButton(new ScrAddedUnitType.TrainButton(ScrUnits.ZERG_HATCHERY, TITAN_BUTTON_POSITION,
                                                                 ScrAddedUnitType.ProductionAction.BUILDING_MORPH,
                                                                 TITAN_BUTTON_HOTKEY, TITAN_BUTTON_TEXT))
                .build();
        ScrAddedUnitType locked = new ScrAddedUnitType.Builder(new UnitType(LOCKED_NAME), LOCKED_NAME, ScrUnits.ZERG_ULTRALISK,
                                                               installedData.getUnits())
                .setTrait(ScrUnitTypeView.TraitColumn.TRAITS, UnitTraitFlags.BUILDING_MORPH_RESULT)
                .setRequirementScript(new ScrRequirementScript.Builder().requireProducer(ScrUnits.ZERG_HATCHERY)
                                                                        .requireCompleted(ScrUnits.ZERG_HIVE)
                                                                        .build())
                .build();
        ScrAddedUnitType colossal = new ScrAddedUnitType.Builder(new UnitType(COLOSSAL_NAME), COLOSSAL_NAME, ScrUnits.ZERG_ULTRALISK,
                                                                 installedData.getUnits())
                .setValue(UnitsDat.DIMENSION_LEFT, COLOSSAL_EXTENT)
                .setValue(UnitsDat.DIMENSION_UP, COLOSSAL_EXTENT)
                .setValue(UnitsDat.DIMENSION_RIGHT, COLOSSAL_EXTENT)
                .setValue(UnitsDat.DIMENSION_DOWN, COLOSSAL_EXTENT)
                .build();

        gameData   = installedData;
        titanId    = installedData.getAddedUnitTypes().add(titan);
        lockedId   = installedData.getAddedUnitTypes().add(locked);
        colossalId = installedData.getAddedUnitTypes().add(colossal);
    }

    @AfterAll
    static void closeGameData() {
        GameData installedData = gameData;

        gameData = null;

        if (installedData != null) {
            installedData.close();
        }
    }


    private static @NotNull GameData requireGameData() {
        Assertions.assertNotNull(gameData);

        return gameData;
    }

    /**
     * Plays a saved replay to its end and returns the state hash of every frame.
     */
    private static @NotNull List<Long> playBack(@NotNull ReplayFile replay, int player) throws IOException {
        byte[] mapData = replay.getMapData();

        Assertions.assertNotNull(mapData);

        ReplayRuntime playback = new ReplayRuntime(replay, ChkFile.parseTolerant(mapData), requireGameData(),
                                                   RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                   RunningGameScenario.INSTRUCTION_LIMIT);
        List<Long> hashes = new ArrayList<>();

        playback.start(0, 0);

        Game game = playback.getGame();

        for (int frame = 0; playback.step(GameSpeed.FASTEST_DURATION, frame * GameSpeed.FASTEST_DURATION); frame++) {
            Assertions.assertNull(playback.getFailure());

            hashes.add(game.computeStateHash());
        }

        UnitAccounting accounting = game.getPlayerTable().requireUnitAccounting();

        Assertions.assertEquals(0, accounting.getTypeCount(ScrUnitIds.ZERG_HATCHERY, player));
        Assertions.assertEquals(1, accounting.getCompletedTypeCount(titanId, player));

        return hashes;
    }


    @Test
    void hatchery_morphs_into_the_added_unit_and_leaves_its_base_behind() throws IOException {
        ZergGame zerg = new ZergGame();
        ScrUnitTypeView units = zerg.game.requireGameData().getUnitTypes();
        UnitAccounting accounting = zerg.game.getPlayerTable().requireUnitAccounting();
        Unit hatchery = zerg.requireUnit(ScrUnitIds.ZERG_HATCHERY);
        int hatcheryX = hatchery.getPositionX();
        int hatcheryY = hatchery.getPositionY();
        int creepTiles = zerg.countCreepTiles(hatcheryX, hatcheryY);

        Assertions.assertFalse(zerg.findUnits(ScrUnitIds.ZERG_LARVA).isEmpty());
        Assertions.assertTrue(creepTiles > 0);

        // The player keeps a building, the Extractor, so the morph does not defeat it.
        zerg.runTo(EXTRACTOR_FRAME);
        zerg.grantResources();
        zerg.buildExtractor(zerg.findUnits(ScrUnitIds.ZERG_DRONE).getFirst(), hatchery);

        while (zerg.findUnits(ScrUnitIds.ZERG_EXTRACTOR).isEmpty()) {
            Assertions.assertTrue(zerg.game.getCurrentFrame() < EXTRACTOR_FRAME_LIMIT, "the Drone starts the Extractor");

            zerg.step();
        }

        // The Drone that became the Extractor leaves the supply count.
        zerg.runTo(zerg.game.getCurrentFrame() + SETTLE_FRAMES);

        int minerals = zerg.requirePlayer().getMinerals();
        int supplyProvided = accounting.getSupplyProvided(zerg.player);
        int supplyUsed = accounting.getSupplyUsed(zerg.player);
        int morphFrame = zerg.game.getCurrentFrame();

        zerg.select(hatchery);
        zerg.morphBuilding(titanId);
        zerg.runTo(morphFrame + COMMAND_FRAMES);

        // The morph is charged once, when it starts; the building stays a Hatchery and shows the added type.
        Assertions.assertEquals(minerals - TITAN_MINERAL_COST, zerg.requirePlayer().getMinerals());
        Assertions.assertEquals(ScrUnitIds.ZERG_HATCHERY, hatchery.getUnitId());
        Assertions.assertEquals(0, hatchery.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertTrue(UnitQueries.isBuildingMorph(units, hatchery));
        Assertions.assertEquals(titanId, UnitQueries.getDisplayedType(units, hatchery));
        Assertions.assertEquals(1, accounting.getCompletedTypeCount(ScrUnitIds.ZERG_HATCHERY, zerg.player));

        for (int frame = 0; frame < COMPLETION_FRAME_LIMIT && hatchery.getUnitId() != titanId; frame++) {
            zerg.step();
        }

        Assertions.assertEquals(titanId, hatchery.getUnitId());
        Assertions.assertEquals(ScrUnitIds.ZERG_HATCHERY, hatchery.getPreviousUnitType());
        Assertions.assertNotEquals(0, hatchery.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertEquals(0, hatchery.getStatus() & UnitStatusFlags.GROUNDED_BUILDING);
        Assertions.assertFalse(UnitQueries.isProducing(hatchery));
        Assertions.assertEquals(units.getHitPoints(titanId), hatchery.getHitPoints());
        Assertions.assertEquals(hatcheryX, hatchery.getPositionX());
        Assertions.assertEquals(hatcheryY, hatchery.getPositionY());
        Assertions.assertEquals(0, units.getFlags(hatchery.getUnitId()) & UnitTypeFlags.RESOURCE_DEPOT);

        // The Hatchery's counts and supply are gone, the unit's are there.
        Assertions.assertEquals(0, accounting.getTypeCount(ScrUnitIds.ZERG_HATCHERY, zerg.player));
        Assertions.assertEquals(0, accounting.getCompletedTypeCount(ScrUnitIds.ZERG_HATCHERY, zerg.player));
        Assertions.assertEquals(1, accounting.getTypeCount(titanId, zerg.player));
        Assertions.assertEquals(1, accounting.getCompletedTypeCount(titanId, zerg.player));
        Assertions.assertEquals(supplyProvided - units.getSupplyProvided(ScrUnitIds.ZERG_HATCHERY), accounting.getSupplyProvided(zerg.player));
        Assertions.assertEquals(supplyUsed + units.getSupplyRequired(titanId), accounting.getSupplyUsed(zerg.player));

        // The Larvae lose their parent; once the creep under them recedes they die.
        for (Unit larva : zerg.findUnits(ScrUnitIds.ZERG_LARVA)) {
            Assertions.assertNull(larva.getParentUnit());
        }

        zerg.runTo(GAME_FRAMES);

        Assertions.assertTrue(zerg.findUnits(ScrUnitIds.ZERG_LARVA).isEmpty(), "the Larvae die off creep");
        Assertions.assertTrue(zerg.countCreepTiles(hatcheryX, hatcheryY) < creepTiles, "the creep recedes");
        Assertions.assertSame(hatchery, zerg.requireUnit(titanId));

        // The saved replay plays back twice with equal state hashes and ends as the live game did.
        ReplayFile saved = ReplayFile.parse(zerg.live.writeReplay(zerg.game.getCurrentFrame(), 0));
        List<Long> first = playBack(saved, zerg.player);
        List<Long> second = playBack(saved, zerg.player);

        Assertions.assertEquals(GAME_FRAMES, first.size());
        Assertions.assertEquals(first, second);
    }

    @Test
    void unit_morphed_from_the_hatchery_walks_to_a_clicked_position() throws IOException {
        ZergGame zerg = new ZergGame();
        Unit hatchery = zerg.requireUnit(ScrUnitIds.ZERG_HATCHERY);

        // The player keeps a building, the Extractor, so the morph does not defeat it.
        zerg.runTo(EXTRACTOR_FRAME);
        zerg.grantResources();
        zerg.buildExtractor(zerg.findUnits(ScrUnitIds.ZERG_DRONE).getFirst(), hatchery);

        while (zerg.findUnits(ScrUnitIds.ZERG_EXTRACTOR).isEmpty()) {
            Assertions.assertTrue(zerg.game.getCurrentFrame() < EXTRACTOR_FRAME_LIMIT, "the Drone starts the Extractor");

            zerg.step();
        }

        zerg.runTo(zerg.game.getCurrentFrame() + SETTLE_FRAMES);
        zerg.select(hatchery);
        zerg.morphBuilding(titanId);

        for (int frame = 0; frame < COMPLETION_FRAME_LIMIT && hatchery.getUnitId() != titanId; frame++) {
            zerg.step();
        }

        Assertions.assertEquals(titanId, hatchery.getUnitId());

        int targetX = hatchery.getPositionX() - WALK_DISTANCE;
        int targetY = hatchery.getPositionY();
        int idleOrder = zerg.game.requireGameData().getUnitTypes().getReturnToIdleOrder(titanId);

        zerg.select(hatchery);
        zerg.rightClick(targetX, targetY);
        zerg.runTo(zerg.game.getCurrentFrame() + COMMAND_FRAMES);

        for (int frame = 0; hatchery.getPrimaryOrder() != idleOrder; frame++) {
            Assertions.assertTrue(frame < WALK_FRAME_LIMIT, "the unit stops at the clicked position");

            zerg.step();
        }

        Assertions.assertTrue(Math.abs(hatchery.getPositionX() - targetX) <= ARRIVAL_DISTANCE &&
                              Math.abs(hatchery.getPositionY() - targetY) <= ARRIVAL_DISTANCE,
                              () -> "the unit reaches " + targetX + "," + targetY + ", is at " + hatchery.getPositionX() + "," +
                                    hatchery.getPositionY());
    }

    @Test
    void canceled_morph_restores_the_hatchery_with_a_refund() throws IOException {
        ZergGame zerg = new ZergGame();
        UnitAccounting accounting = zerg.game.getPlayerTable().requireUnitAccounting();
        Unit hatchery = zerg.requireUnit(ScrUnitIds.ZERG_HATCHERY);
        int minerals = zerg.requirePlayer().getMinerals();

        zerg.runTo(MORPH_FRAME);
        zerg.select(hatchery);
        zerg.morphBuilding(titanId);
        zerg.runTo(CANCEL_FRAME);

        Assertions.assertEquals(0, hatchery.getStatus() & UnitStatusFlags.COMPLETED);

        Assertions.assertTrue(zerg.live.submit(ReplayCommandIds.CANCEL_MORPH, new byte[0]));
        zerg.runTo(CANCEL_GAME_FRAMES);

        Assertions.assertEquals(ScrUnitIds.ZERG_HATCHERY, hatchery.getUnitId());
        Assertions.assertNotEquals(0, hatchery.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertEquals(minerals - TITAN_MINERAL_COST + TITAN_MINERAL_COST * REFUND_QUARTERS / QUARTER_DIVISOR,
                                zerg.requirePlayer().getMinerals());
        Assertions.assertEquals(1, accounting.getCompletedTypeCount(ScrUnitIds.ZERG_HATCHERY, zerg.player));
        Assertions.assertEquals(0, accounting.getTypeCount(titanId, zerg.player));
        Assertions.assertFalse(zerg.findUnits(ScrUnitIds.ZERG_LARVA).isEmpty());
    }

    @Test
    void own_requirement_script_decides_the_producer_and_its_requirements() throws IOException {
        ZergGame zerg = new ZergGame();
        ScrRequirementSystem requirements = zerg.game.getRequirements();
        Unit hatchery = zerg.requireUnit(ScrUnitIds.ZERG_HATCHERY);
        Unit drone = zerg.findUnits(ScrUnitIds.ZERG_DRONE).getFirst();
        int minerals = zerg.requirePlayer().getMinerals();

        Assertions.assertTrue(ScrRequirementSystem.isAccepted(requirements.checkBuild(hatchery, titanId, zerg.player)));
        Assertions.assertFalse(ScrRequirementSystem.isAccepted(requirements.checkBuild(drone, titanId, zerg.player)));
        Assertions.assertFalse(ScrRequirementSystem.isAccepted(requirements.checkBuild(hatchery, lockedId, zerg.player)));
        Assertions.assertTrue(ScrRequirementSystem.isDisabled(requirements.checkBuild(hatchery, lockedId, zerg.player)));

        // The native types keep their installed scripts: a Lair needs a Spawning Pool.
        Assertions.assertFalse(ScrRequirementSystem.isAccepted(requirements.checkBuild(hatchery, ScrUnitIds.ZERG_LAIR, zerg.player)));

        zerg.runTo(MORPH_FRAME);
        zerg.select(hatchery);
        zerg.morphBuilding(lockedId);
        zerg.runTo(MORPH_FRAME + COMMAND_FRAMES);

        Assertions.assertEquals(minerals, zerg.requirePlayer().getMinerals());
        Assertions.assertNotEquals(0, hatchery.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertEquals(ScrUnitIds.NONE, UnitQueries.getQueuedType(hatchery));
    }

    @Test
    void added_type_larger_than_every_native_type_widens_the_unit_finder_queries() throws IOException {
        ZergGame zerg = new ZergGame();
        ScrUnitTypeView units = zerg.game.requireGameData().getUnitTypes();
        UnitSpatialIndex spatial = zerg.game.getUnitSystem().getUnitSpatialIndex();
        int nativeWidth = 0;
        int nativeHeight = 0;

        for (int unitType = 0; unitType < UnitsDat.UNIT_COUNT; unitType++) {
            nativeWidth  = Math.max(nativeWidth, (short) units.getDimensionLeft(unitType) + 1 + (short) units.getDimensionRight(unitType));
            nativeHeight = Math.max(nativeHeight, (short) units.getDimensionUp(unitType) + 1 + (short) units.getDimensionDown(unitType));
        }

        int width = units.getDimensionLeft(colossalId) + 1 + units.getDimensionRight(colossalId);
        int height = units.getDimensionUp(colossalId) + 1 + units.getDimensionDown(colossalId);

        Assertions.assertNotNull(spatial);
        Assertions.assertTrue(width > nativeWidth && height > nativeHeight);
        Assertions.assertEquals(width, spatial.getEdges().getMinimumQueryDimension(true));
        Assertions.assertEquals(height, spatial.getEdges().getMinimumQueryDimension(false));
    }

    @Test
    void building_morph_button_needs_a_building_morph_result() {
        GameData installedData = requireGameData();
        ScrAddedUnitType.Builder builder = new ScrAddedUnitType.Builder(new UnitType("Plain Titan"), "Plain Titan", ScrUnits.ZERG_ULTRALISK,
                                                                        installedData.getUnits())
                .setTrainButton(new ScrAddedUnitType.TrainButton(ScrUnits.ZERG_HATCHERY, TITAN_BUTTON_POSITION,
                                                                 ScrAddedUnitType.ProductionAction.BUILDING_MORPH,
                                                                 TITAN_BUTTON_HOTKEY, TITAN_BUTTON_TEXT));

        Assertions.assertThrows(IllegalStateException.class, builder::build);
        Assertions.assertThrows(IllegalStateException.class, () -> new ScrRequirementScript.Builder().or());
        Assertions.assertThrows(IllegalStateException.class, () -> new ScrRequirementScript.Builder().requireNotBusy().or().build());
    }
}
