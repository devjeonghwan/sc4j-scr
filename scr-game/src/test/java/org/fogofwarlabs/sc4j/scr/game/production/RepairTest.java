package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitGroupFlags;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerRegionScenario;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerUnitRecord;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.presentation.CommandErrorEvents;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainContours;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainRegions;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;

import java.io.IOException;

/**
 * Checks repair startup affordability, charging, the worker's WORD timer and the approach of a computer SCV through completed units in a fresh
 * running game.
 */
@RequiresInstallation
final class RepairTest {
    /**
     * The genuine-map creation point, checked by public creation and completion in each case.
     */
    private static final int CREATION_POSITION_X = 3560;
    private static final int CREATION_POSITION_Y = 2160;
    /**
     * The tiles the worker is parked away before the target is created at the same creation point.
     */
    private static final int PARKING_DISTANCE_TILES = 2;
    /**
     * The tiles between a computer SCV and its distant repair target, which lies beyond the regions next to the SCV.
     */
    private static final int DISTANT_TARGET_TILES = 14;
    /**
     * The divisor of the maximum hit points that leaves a target damaged.
     */
    private static final int DAMAGED_HIT_POINT_DIVISOR = 2;
    /**
     * The minerals or gas of an owner that can afford a repair charge.
     */
    private static final int AVAILABLE_RESOURCES = 1000;
    /**
     * The order states of {@link ScrOrderIds#REPAIR}: started, deciding, following, repairing.
     */
    private static final int REPAIR_STATE_START = 0;
    private static final int REPAIR_STATE_DECIDING = 1;
    private static final int REPAIR_STATE_FOLLOWING = 6;
    private static final int REPAIR_STATE_REPAIRING = 8;
    /**
     * The order identifiers of the repair and of the transport pickup the SCV waits in.
     */
    private static final int REPAIR_ORDER = 34;
    private static final int PICKUP4_ORDER = 96;
    /**
     * The low WORD mask of the unsigned repair timer, its largest value, and the first value above a WORD.
     */
    private static final int WORD_MASK = 0xFFFF;
    private static final int FIRST_VALUE_ABOVE_WORD = 0x10000;

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        GameData loaded = gameData;

        gameData = null;

        if (loaded != null) {
            loaded.close();
        }
    }


    private @Nullable ReplayRuntime runtime;

    RepairTest() {
        this.runtime = null;
    }

    @BeforeEach
    void startRunningGame() throws IOException {
        Assertions.assertNotNull(gameData);

        this.runtime = RunningGameScenario.start(gameData);
    }

    @AfterEach
    void releaseRunningGame() {
        this.runtime = null;
    }


    private @NotNull Unit createWorker(@NotNull Game game) {
        Unit worker = RunningGameScenario.createUnit(game,
                                                     ScrUnitIds.TERRAN_SCV,
                                                     CREATION_POSITION_X, CREATION_POSITION_Y,
                                                     RunningGameScenario.HUMAN);
        Sprite sprite = worker.getSpriteOrNull();
        ScrUnitTypeView units = game.requireGameData().getUnitTypes();

        Assertions.assertNotNull(sprite);
        Assertions.assertNotNull(sprite.getMainImage());
        Assertions.assertEquals(RunningGameScenario.HUMAN, worker.getOwnerIndex());
        Assertions.assertNull(worker.getComputerRecord());
        Assertions.assertNotEquals(0, units.getFlags(worker.getUnitId()) & UnitTypeFlags.WORKER);
        Assertions.assertNotEquals(0, worker.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertEquals(0, worker.getStatus() & UnitStatusFlags.LOADED);

        game.getUnitPositioning().move(worker, sprite.getX() + PARKING_DISTANCE_TILES * TerrainTileset.TILE_SIZE, sprite.getY());

        return worker;
    }

    private @NotNull Unit createDamagedTarget(@NotNull Game game, @NotNull Unit worker) {
        Unit target = RunningGameScenario.createUnit(game,
                                                     ScrUnitIds.TERRAN_VULTURE,
                                                     CREATION_POSITION_X, CREATION_POSITION_Y,
                                                     RunningGameScenario.HUMAN);
        ScrUnitTypeView units = game.requireGameData().getUnitTypes();
        Sprite sprite = target.getSpriteOrNull();
        int type = target.getUnitId();
        int maximumHitPoints = units.getHitPoints(type);

        Assertions.assertNotNull(sprite);
        Assertions.assertNotNull(sprite.getMainImage());
        Assertions.assertNotEquals(0, units.getFlags(type) & UnitTypeFlags.MECHANICAL);
        Assertions.assertNotEquals(0, units.getGroupFlags(type) & UnitGroupFlags.TERRAN);
        Assertions.assertNotEquals(0, target.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertEquals(0, target.getStatus() & (UnitStatusFlags.LOADED | UnitStatusFlags.FLYING));
        Assertions.assertEquals(0, target.getStasisTimer());
        Assertions.assertEquals(worker.getOwnerIndex(), target.getOwnerIndex());
        Assertions.assertTrue(units.getMineralCost(type) > 0);
        Assertions.assertEquals(0, units.getGasCost(type));

        int increment = game.getUnitBuildTime().getHitPointIncrement(target);

        Assertions.assertTrue(increment > 0);

        target.setHitPoints(maximumHitPoints / DAMAGED_HIT_POINT_DIVISOR);

        Assertions.assertTrue(target.getHitPoints() + increment < maximumHitPoints);

        game.getUnitPositioning().move(worker,
                                       sprite.getX() + units.getDimensionRight(type) + units.getDimensionLeft(worker.getUnitId()) + 1, sprite.getY());

        worker.setPrimaryOrder(ScrOrderIds.REPAIR);
        worker.setOrderTargetUnit(target);
        game.getInterfaceLink().getCommandErrorEvents().clear();

        return target;
    }

    /**
     * Sets the target's type to cost gas only: its mineral cost moves to its gas cost.
     *
     * @param game   the running game
     * @param target the repair target
     * @throws NullPointerException if the world was built without game data
     */
    private void setGasOnlyCost(@NotNull Game game, @NotNull Unit target) {
        // The game works on its own copy of the tables, so the class-level game data keeps the installed costs.
        DatTable units = game.requireGameData().getUnits();
        int type = target.getUnitId();
        int mineralCost = units.get(UnitsDat.MINERAL_COST, type);

        units.set(UnitsDat.GAS_COST, type, mineralCost);
        units.set(UnitsDat.MINERAL_COST, type, 0);

        Assertions.assertEquals(0, game.requireGameData().getUnitTypes().getMineralCost(type));
        Assertions.assertEquals(mineralCost, game.requireGameData().getUnitTypes().getGasCost(type));
    }

    private void checkNonzeroTimer(int timer) {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit worker = this.createWorker(game);
            Unit target = this.createDamagedTarget(game, worker);
            Player player = game.getPlayerTable().getPlayer(worker.getOwnerIndex());

            Assertions.assertNotNull(player);

            player.setMinerals(0);
            player.setGas(0);
            worker.setPrimaryOrderState(REPAIR_STATE_REPAIRING);
            worker.setRepairTimer(timer);

            int hitPoints = target.getHitPoints();
            int increment = game.getUnitBuildTime().getHitPointIncrement(target);

            game.getUnitTerranConstruction().updateRepair(worker, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(timer - 1, worker.getRepairTimer());
            Assertions.assertEquals(hitPoints + increment, target.getHitPoints());
            Assertions.assertEquals(0, game.getInterfaceLink().getCommandErrorEvents().getCount());
            Assertions.assertEquals(0, player.getMinerals());
            Assertions.assertEquals(0, player.getGas());
        });
    }

    @Test
    void update_repair_should_check_unaffordable_startup_without_charging_or_finishing() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit worker = this.createWorker(game);
            Unit target = this.createDamagedTarget(game, worker);
            Player player = game.getPlayerTable().getPlayer(worker.getOwnerIndex());

            Assertions.assertNotNull(player);

            player.setMinerals(0);
            player.setGas(0);
            worker.setPrimaryOrderState(REPAIR_STATE_START);

            int hitPoints = target.getHitPoints();

            game.getUnitTerranConstruction().updateRepair(worker, RunningGameScenario.INSTRUCTION_LIMIT);

            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();

            Assertions.assertEquals(1, errors.getCount(), "Repair startup checks affordability even though it ignores a failed check.");
            Assertions.assertEquals(CommandErrorEvents.Sound.INSUFFICIENT_MINERALS, errors.get(0).getSound());
            Assertions.assertEquals(worker.getOwnerIndex(), errors.get(0).getPlayer());
            Assertions.assertEquals(REPAIR_ORDER, worker.getPrimaryOrder());
            Assertions.assertEquals(REPAIR_STATE_FOLLOWING, worker.getPrimaryOrderState());
            Assertions.assertSame(target, worker.getMovementTargetUnit());
            Assertions.assertEquals(0, worker.getRepairTimer());
            Assertions.assertEquals(hitPoints, target.getHitPoints());
            Assertions.assertEquals(0, player.getMinerals());
            Assertions.assertEquals(0, player.getGas());
        });
    }

    @Test
    void update_repair_should_check_affordable_startup_without_charging() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit worker = this.createWorker(game);
            Unit target = this.createDamagedTarget(game, worker);
            Player player = game.getPlayerTable().getPlayer(worker.getOwnerIndex());

            Assertions.assertNotNull(player);

            player.setMinerals(AVAILABLE_RESOURCES);
            player.setGas(AVAILABLE_RESOURCES);
            worker.setPrimaryOrderState(REPAIR_STATE_START);

            int hitPoints = target.getHitPoints();

            game.getUnitTerranConstruction().updateRepair(worker, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(0, game.getInterfaceLink().getCommandErrorEvents().getCount());
            Assertions.assertEquals(AVAILABLE_RESOURCES, player.getMinerals());
            Assertions.assertEquals(AVAILABLE_RESOURCES, player.getGas());
            Assertions.assertEquals(hitPoints, target.getHitPoints());
            Assertions.assertEquals(REPAIR_STATE_FOLLOWING, worker.getPrimaryOrderState());
            Assertions.assertEquals(0, worker.getRepairTimer());
        });
    }

    @Test
    void update_repair_should_wrap_zero_timer_before_a_failed_charge() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit worker = this.createWorker(game);
            Unit target = this.createDamagedTarget(game, worker);
            Player player = game.getPlayerTable().getPlayer(worker.getOwnerIndex());

            Assertions.assertNotNull(player);

            player.setMinerals(0);
            player.setGas(0);
            worker.setPrimaryOrderState(REPAIR_STATE_REPAIRING);
            worker.setRepairTimer(0);

            int hitPoints = target.getHitPoints();

            game.getUnitTerranConstruction().updateRepair(worker, RunningGameScenario.INSTRUCTION_LIMIT);

            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();

            Assertions.assertEquals(1, errors.getCount());
            Assertions.assertEquals(CommandErrorEvents.Sound.INSUFFICIENT_MINERALS, errors.get(0).getSound());
            Assertions.assertEquals(worker.getOwnerIndex(), errors.get(0).getPlayer());
            Assertions.assertEquals(hitPoints, target.getHitPoints());
            Assertions.assertEquals(0, player.getMinerals());
            Assertions.assertEquals(0, player.getGas());
            Assertions.assertNotEquals(REPAIR_ORDER, worker.getPrimaryOrder());
            Assertions.assertEquals(WORD_MASK, worker.getRepairTimer(), "The old zero timer is decremented and stored before the failed charge.");
        });
    }

    @Test
    void update_repair_should_decrement_timer_one_without_charging() {
        this.checkNonzeroTimer(1);
    }

    @Test
    void update_repair_should_decrement_maximum_word_timer_without_charging() {
        this.checkNonzeroTimer(WORD_MASK);
    }

    @Test
    void update_repair_should_charge_and_restore_hit_points_when_zero_timer_is_affordable() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit worker = this.createWorker(game);
            Unit target = this.createDamagedTarget(game, worker);
            Player player = game.getPlayerTable().getPlayer(worker.getOwnerIndex());

            Assertions.assertNotNull(player);

            player.setMinerals(AVAILABLE_RESOURCES);
            player.setGas(AVAILABLE_RESOURCES);
            worker.setPrimaryOrderState(REPAIR_STATE_REPAIRING);
            worker.setRepairTimer(0);

            int hitPoints = target.getHitPoints();
            int increment = game.getUnitBuildTime().getHitPointIncrement(target);

            game.getUnitTerranConstruction().updateRepair(worker, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertTrue(player.getMinerals() < AVAILABLE_RESOURCES);
            Assertions.assertEquals(AVAILABLE_RESOURCES, player.getGas());
            Assertions.assertEquals(hitPoints + increment, target.getHitPoints());
            Assertions.assertEquals(0, game.getInterfaceLink().getCommandErrorEvents().getCount());
            Assertions.assertTrue(worker.getRepairTimer() > 0 && worker.getRepairTimer() <= WORD_MASK);
        });
    }

    @Test
    void update_repair_should_charge_one_gas_for_a_type_without_mineral_cost() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit worker = this.createWorker(game);
            Unit target = this.createDamagedTarget(game, worker);
            Player player = game.getPlayerTable().getPlayer(worker.getOwnerIndex());

            Assertions.assertNotNull(player);

            this.setGasOnlyCost(game, target);
            player.setMinerals(0);
            player.setGas(AVAILABLE_RESOURCES);
            worker.setPrimaryOrderState(REPAIR_STATE_REPAIRING);
            worker.setRepairTimer(0);

            int hitPoints = target.getHitPoints();
            int increment = game.getUnitBuildTime().getHitPointIncrement(target);

            game.getUnitTerranConstruction().updateRepair(worker, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(0, game.getInterfaceLink().getCommandErrorEvents().getCount());
            Assertions.assertEquals(0, player.getMinerals());
            Assertions.assertEquals(AVAILABLE_RESOURCES - 1, player.getGas(), "Gas, the only nonzero price, costs one per charge.");
            Assertions.assertEquals(hitPoints + increment, target.getHitPoints());
            Assertions.assertTrue(worker.getRepairTimer() > 0 && worker.getRepairTimer() <= WORD_MASK);
        });
    }

    @Test
    void update_repair_should_finish_with_the_gas_error_for_a_type_without_mineral_cost_and_no_gas() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit worker = this.createWorker(game);
            Unit target = this.createDamagedTarget(game, worker);
            Player player = game.getPlayerTable().getPlayer(worker.getOwnerIndex());

            Assertions.assertNotNull(player);

            this.setGasOnlyCost(game, target);
            player.setMinerals(AVAILABLE_RESOURCES);
            player.setGas(0);
            worker.setPrimaryOrderState(REPAIR_STATE_REPAIRING);
            worker.setRepairTimer(0);

            int hitPoints = target.getHitPoints();

            game.getUnitTerranConstruction().updateRepair(worker, RunningGameScenario.INSTRUCTION_LIMIT);

            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();

            Assertions.assertEquals(1, errors.getCount());
            Assertions.assertEquals(CommandErrorEvents.Sound.INSUFFICIENT_GAS, errors.get(0).getSound());
            Assertions.assertEquals(worker.getOwnerIndex(), errors.get(0).getPlayer());
            Assertions.assertEquals(hitPoints, target.getHitPoints());
            Assertions.assertEquals(AVAILABLE_RESOURCES, player.getMinerals());
            Assertions.assertEquals(0, player.getGas());
            Assertions.assertNotEquals(REPAIR_ORDER, worker.getPrimaryOrder());
            Assertions.assertEquals(WORD_MASK, worker.getRepairTimer());
        });
    }

    /**
     * Places an enemy ground target in every region next to the SCV, so the route to the distant target is unsafe and the SCV waits for its own
     * Dropship instead of following the target.
     */
    @Test
    void update_repair_should_face_the_target_and_keep_the_pickup_order_when_a_transport_pickup_starts() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Game game = this.runtime.getGame();
            Unit worker = RunningGameScenario.createUnit(game,
                                                         ScrUnitIds.TERRAN_SCV,
                                                         CREATION_POSITION_X, CREATION_POSITION_Y,
                                                         RunningGameScenario.COMPUTER);
            Unit target = RunningGameScenario.createUnit(game,
                                                         ScrUnitIds.TERRAN_VULTURE,
                                                         CREATION_POSITION_X, CREATION_POSITION_Y,
                                                         RunningGameScenario.COMPUTER);
            Unit transport = RunningGameScenario.createUnit(game,
                                                            ScrUnitIds.TERRAN_DROPSHIP,
                                                            CREATION_POSITION_X, CREATION_POSITION_Y,
                                                            RunningGameScenario.COMPUTER);
            Unit threat = RunningGameScenario.createUnit(game,
                                                         ScrUnitIds.TERRAN_MARINE,
                                                         CREATION_POSITION_X, CREATION_POSITION_Y,
                                                         RunningGameScenario.HUMAN);
            ScrUnitTypeView units = game.requireGameData().getUnitTypes();
            TerrainContours contours = game.getPathSystem().getTerrainContours();
            Sprite targetSprite = target.getSpriteOrNull();

            Assertions.assertNotNull(contours);
            Assertions.assertNotNull(targetSprite);
            Assertions.assertNotEquals(0, transport.getStatus() & UnitStatusFlags.COMPLETED);

            game.getUnitPositioning().move(target, targetSprite.getX() + DISTANT_TARGET_TILES * TerrainTileset.TILE_SIZE, targetSprite.getY());

            TerrainRegions regions = contours.getRegions();
            int source = game.getComputerTerrain().getUnitRegion(worker);
            int destination = game.getComputerTerrain().getRegion(targetSprite.getX(), targetSprite.getY());

            Assertions.assertNotEquals(source, destination);
            Assertions.assertEquals(regions.getComponent(source), regions.getComponent(destination));

            for (int neighborIndex = 0; neighborIndex < regions.getNeighborCount(source); neighborIndex++) {
                int neighbor = regions.getNeighborRegion(source, neighborIndex);

                Assertions.assertNotEquals(destination, neighbor, "The distant target lies beyond the regions next to the SCV.");

                ComputerRegionScenario.setGroundTarget(game, RunningGameScenario.COMPUTER, neighbor, threat);
            }

            if (worker.getComputerRecord() == null) {
                worker.setComputerRecord(new ComputerUnitRecord(ComputerUnitRecord.KIND_GUARD, worker.getIndex()));
            }

            target.setHitPoints(units.getHitPoints(target.getUnitId()) / DAMAGED_HIT_POINT_DIVISOR);
            target.setOrderTargetUnit(worker);
            worker.setPrimaryOrder(ScrOrderIds.REPAIR);
            worker.setOrderTargetUnit(target);
            worker.setPrimaryOrderState(REPAIR_STATE_DECIDING);

            game.getUnitTerranConstruction().updateRepair(worker, RunningGameScenario.INSTRUCTION_LIMIT);

            OrderPool.Order queued = worker.getFirstOrder();

            Assertions.assertEquals(PICKUP4_ORDER, worker.getPrimaryOrder(), "The SCV waits for its transport.");
            Assertions.assertEquals(REPAIR_STATE_START, worker.getPrimaryOrderState(), "The repair order does not enter its following state.");
            Assertions.assertNotNull(queued);
            Assertions.assertEquals(REPAIR_ORDER, queued.getOrderId());
            Assertions.assertSame(target, queued.getTargetUnit());
            Assertions.assertNotSame(target, worker.getMovementTargetUnit());
            Assertions.assertEquals(targetSprite.getX(), worker.getDirectionTargetX(), "The SCV faces its target even though the pickup starts.");
            Assertions.assertEquals(targetSprite.getY(), worker.getDirectionTargetY());
        });
    }

    @Test
    void set_repair_timer_should_retain_only_the_low_word() {
        RunningGameScenario.runInCommandDispatch(this.runtime, () -> {
            Assertions.assertNotNull(this.runtime);

            Unit worker = this.createWorker(this.runtime.getGame());

            worker.setRepairTimer(-1);

            Assertions.assertEquals(WORD_MASK, worker.getRepairTimer(), "The repair timer has unsigned WORD storage.");

            worker.setRepairTimer(FIRST_VALUE_ABOVE_WORD);

            Assertions.assertEquals(0, worker.getRepairTimer());
        });
    }
}
