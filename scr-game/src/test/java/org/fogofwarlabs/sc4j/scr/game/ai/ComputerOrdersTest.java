package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.NetworkReplayScenario;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool.Order;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.CampaignMissionScenario;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Checks computer orders in a running game: transport moves, unburrowing and building requests; the JunkYardDog wander order in a campaign
 * mission; and the repair choice of a recorded LAN game, which skips a building with full hit points and damaged shields.
 */
@RequiresInstallation
final class ComputerOrdersTest {
    private static final int COMPUTER = 0;
    private static final int HUMAN = 1;
    private static final int DROPSHIP_POSITION_X = 3808;
    private static final int DROPSHIP_POSITION_Y = 2300;
    private static final int ZERGLING_POSITION_X = 3300;
    private static final int GATEWAY_POSITION_X = 3800;
    private static final int GROUND_POSITION_Y = 2700;
    private static final int UNKNOWN_REQUEST_KIND = 9;
    /**
     * The priority of the request of the unknown kind.
     */
    private static final int UNKNOWN_REQUEST_PRIORITY = 0x7F;

    /**
     * The campaign mission of the JunkYardDog test, played as Brood War.
     */
    private static final @NotNull String WANDER_MISSION = "bw-protoss-1";
    /**
     * The computer player of the mission that owns the wandering unit.
     */
    private static final int MISSION_COMPUTER = 3;
    /**
     * The turns that run before the JunkYardDog test body starts.
     */
    private static final int START_TURNS = 5;

    private static final int UNIT_POSITION_X = 2048;
    private static final int UNIT_POSITION_Y = 1024;

    /**
     * The turns after which the wandering unit has chosen a wander point.
     */
    private static final int WANDER_TURNS = 12;
    /**
     * The turns after which the wandering unit has left its start position.
     */
    private static final int MOVE_TURNS = 30;
    /**
     * The turns that the wandering unit has to attack the enemy.
     */
    private static final int ATTACK_TURN_LIMIT = 200;
    /**
     * The horizontal distance of the enemy from the wandering unit.
     */
    private static final int ENEMY_OFFSET_X = 40;
    /**
     * The margin of the left, top and right map edges inside which a wander point is clamped.
     */
    private static final int MAP_EDGE_MARGIN = 16;
    /**
     * The margin of the bottom map edge inside which a wander point is clamped.
     */
    private static final int MAP_BOTTOM_MARGIN = 48;
    /**
     * The shortest distance of an unclamped wander point from the start position.
     */
    private static final long MINIMUM_WANDER_DISTANCE = 1000L;
    /**
     * The longest distance of an unclamped wander point from the start position.
     */
    private static final long MAXIMUM_WANDER_DISTANCE = 2060L;
    /**
     * The shift of the wander time BYTE in the cooldown timers.
     */
    private static final int WANDER_TIMER_SHIFT = 24;

    /**
     * The recording client's player of the repair test, player 1 of a LAN game.
     */
    private static final int REPAIR_LOCAL_PLAYER = 1;
    /**
     * The last frame the recording of the repair test runs to.
     */
    private static final int REPAIR_LAST_FRAME = 3886;
    /**
     * The frames of the two resource words of the native host that the repair test compares.
     */
    private static final long FIRST_RESOURCE_WORD_FRAME = 3884L;
    private static final long SECOND_RESOURCE_WORD_FRAME = 3886L;

    private static final int MOVE_ORDER = 6;
    private static final int ATTACK_UNIT_ORDER = 10;
    private static final int MOVE_UNLOAD_ORDER = 112;
    private static final int BURROWED_ORDER = 117;
    private static final int HARASS_MOVE_ORDER = 158;
    private static final int JUNK_YARD_DOG_ORDER = 187;
    private static final int NO_UNIT_TYPE = 228;

    private static @Nullable GameData gameData;
    private static @Nullable ReplayRuntime runtime;

    @BeforeAll
    static void startGame() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
        runtime  = RunningGameScenario.start(gameData);
    }

    @AfterAll
    static void close() {
        runtime = null;

        if (gameData != null) {
            gameData.close();
        }

        gameData = null;
    }


    @Test
    void move_transport_to_neighbor_should_move_unload_or_return_to_town() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit dropship = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_DROPSHIP, DROPSHIP_POSITION_X, DROPSHIP_POSITION_Y, COMPUTER);
            ComputerPlayerPhase phase = game.getComputerPlayerPhase();

            Assertions.assertNotNull(phase);

            if (dropship.getComputerRecord() == null) {
                int index = phase.getRegions().getIndex(COMPUTER, game.getComputerTerrain().getUnitRegion(dropship));

                phase.getMilitary().addToRegion(phase, index, dropship, true, RunningGameScenario.INSTRUCTION_LIMIT);
            }

            Assertions.assertNotNull(dropship.getComputerRecord());

            game.getComputerOrders().moveTransportToNeighbor(phase, dropship, RunningGameScenario.INSTRUCTION_LIMIT);

            int order = dropship.getPrimaryOrder();

            Assertions.assertTrue(order == MOVE_UNLOAD_ORDER || order == MOVE_ORDER || order == HARASS_MOVE_ORDER,
                                  "The transport move-unloads to a neighbouring region or returns to a town region: " + order);
        });
    }

    @Test
    void unburrow_to_attack_should_unburrow_unit_without_computer_record() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit zergling = RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_ZERGLING, ZERGLING_POSITION_X, GROUND_POSITION_Y, HUMAN);

            Assertions.assertNull(zergling.getComputerRecord());
            zergling.setPrimaryOrder(ScrOrderIds.BURROWED);

            Assertions.assertDoesNotThrow(() -> game.getComputerOrders().unburrowToAttack(zergling, RunningGameScenario.INSTRUCTION_LIMIT));

            Assertions.assertNotEquals(BURROWED_ORDER, zergling.getPrimaryOrder());
        });
    }

    @Test
    void update_building_should_leave_building_alone_for_unknown_request_kind() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            ComputerPlayerPhase phase = game.getComputerPlayerPhase();

            Assertions.assertNotNull(phase);

            Unit gateway = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_GATEWAY, GATEWAY_POSITION_X, GROUND_POSITION_Y, COMPUTER);

            gateway.setComputerRecord(new ComputerUnitRecord(ComputerUnitRecord.KIND_BUILDING, gateway.getIndex()));

            Player buildingPlayer = game.getPlayerTable().getPlayer(COMPUTER);

            Assertions.assertNotNull(buildingPlayer);

            buildingPlayer.setMinerals(0);

            ComputerPlayer player = phase.getPlayer(COMPUTER);

            player.pushRequest(UNKNOWN_REQUEST_PRIORITY, UNKNOWN_REQUEST_KIND, ScrUnitIds.PROTOSS_ZEALOT, null, -1);

            try {
                Assertions.assertEquals(UNKNOWN_REQUEST_KIND, player.getActiveRequestKind());

                int order = gateway.getSecondaryOrder();

                Assertions.assertTrue(game.getComputerOrders().updateBuilding(phase, gateway, RunningGameScenario.INSTRUCTION_LIMIT),
                                      "Other request kinds report the building as handled.");

                Assertions.assertEquals(order, gateway.getSecondaryOrder());
                Assertions.assertEquals(NO_UNIT_TYPE, gateway.getBuildQueueUnitType(gateway.getBuildQueueIndex()));
            } finally {
                gateway.setComputerRecord(null);
            }
        });
    }

    @Test
    void junk_yard_dog_wanders_then_attacks_found_enemy_and_keeps_its_order_queued() throws IOException {
        CampaignMissionScenario.run(ExternalInput.INSTALLATION.getPath(), WANDER_MISSION, true, (game, live) -> {
            Game state = live.getGame();

            CampaignMissionScenario.exchangeTurnsAndStep(game, live, START_TURNS);

            Unit dog = CampaignMissionScenario.createUnit(state, ScrUnitIds.ZERG_ZERGLING, UNIT_POSITION_X, UNIT_POSITION_Y, MISSION_COMPUTER);

            state.getOrders().issueUntargeted(dog, ScrOrderIds.JUNK_YARD_DOG, RunningGameScenario.INSTRUCTION_LIMIT);

            int startX = dog.getPositionX();
            int startY = dog.getPositionY();

            CampaignMissionScenario.exchangeTurnsAndStep(game, live, WANDER_TURNS);

            // State zero chose a wander point and sent the unit there.
            Assertions.assertEquals(JUNK_YARD_DOG_ORDER, dog.getPrimaryOrder());
            Assertions.assertEquals(1, dog.getPrimaryOrderState());

            int targetX = (short) dog.getOrderTargetX();
            int targetY = (short) dog.getOrderTargetY();

            Assertions.assertEquals(targetX, (short) dog.getMovementDestinationX());
            Assertions.assertEquals(targetY, (short) dog.getMovementDestinationY());

            long squared = (long) (targetX - startX) * (targetX - startX) + (long) (targetY - startY) * (targetY - startY);
            int mapWidth = state.getMap().getWidth() * TerrainTileset.TILE_SIZE;
            int mapHeight = state.getMap().getHeight() * TerrainTileset.TILE_SIZE;
            boolean clamped = targetX <= MAP_EDGE_MARGIN || targetX >= mapWidth - MAP_EDGE_MARGIN ||
                              targetY <= MAP_EDGE_MARGIN || targetY >= mapHeight - MAP_BOTTOM_MARGIN;

            Assertions.assertTrue(clamped ||
                                  squared >= MINIMUM_WANDER_DISTANCE * MINIMUM_WANDER_DISTANCE &&
                                  squared <= MAXIMUM_WANDER_DISTANCE * MAXIMUM_WANDER_DISTANCE,
                                  "wander point " + targetX + "," + targetY);
            Assertions.assertNotEquals(0, dog.getCooldownTimers() >>> WANDER_TIMER_SHIFT, "the wander time BYTE +0x73 was not loaded");

            CampaignMissionScenario.exchangeTurnsAndStep(game, live, MOVE_TURNS);

            Assertions.assertTrue(dog.getPositionX() != startX || dog.getPositionY() != startY, "the JunkYardDog does not move");

            Unit enemy = CampaignMissionScenario.createUnit(state,
                                                            ScrUnitIds.PROTOSS_ZEALOT,
                                                            dog.getPositionX() + ENEMY_OFFSET_X, dog.getPositionY(),
                                                            game.getLocalPlayer());
            boolean attacked = false;

            for (int count = 0; count < ATTACK_TURN_LIMIT && !attacked; count++) {
                CampaignMissionScenario.exchangeTurnsAndStep(game, live, 1);

                attacked = dog.getOrderTargetUnit() == enemy && dog.getPrimaryOrder() == ATTACK_UNIT_ORDER;
            }

            Assertions.assertTrue(attacked, "the JunkYardDog never attacked the Zealot");

            Order queued = dog.getLastOrder();

            Assertions.assertNotNull(queued);
            Assertions.assertEquals(JUNK_YARD_DOG_ORDER, queued.getOrderId());
        });
    }

    @Test
    void computer_does_not_repair_building_with_full_hit_points_and_damaged_shields() throws IOException {
        Path map = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, ExternalInput.TEST_MAPS_FOLDER, "eud_com.scx");

        Map<Long, Integer> words = NetworkReplayScenario.collectResourceWords(ExternalInput.INSTALLATION.getPath(), map,
                                                                              GameTestResources.REPLAY_EUD_COMPUTER_SHIELD_REPAIR_DESYNC,
                                                                              REPAIR_LOCAL_PLAYER,
                                                                              REPAIR_LAST_FRAME);

        Assertions.assertEquals(0xBA75, words.get(FIRST_RESOURCE_WORD_FRAME), "The native host's record 15 (frame 3884).");
        Assertions.assertEquals(0x12D9, words.get(SECOND_RESOURCE_WORD_FRAME), "The native host's record 1 (frame 3886).");
    }
}
