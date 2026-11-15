package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

/**
 * Checks cost buffers and resource spending in a running game.
 */
@RequiresInstallation
final class CostTestsTest {
    /**
     * The classic addresses of the mineral, gas and supply costs that the cost tests store, a {@code DWORD} per player.
     */
    private static final int MINERAL_COST_ADDRESS = 0x6CA51C;
    private static final int GAS_COST_ADDRESS = 0x6CA4EC;
    private static final int SUPPLY_COST_ADDRESS = 0x6CA4BC;
    /**
     * The bytes of one player's cost entry.
     */
    private static final int PLAYER_COST_BYTES = 4;

    private static final int START_FRAME = 10;

    private static final int INITIAL_RESOURCES = 1000;
    private static final int SCV_MINERAL_COST = 50;
    private static final int SCV_SUPPLY_COST = 2;
    private static final int MINERALS_AFTER_SCV = 950;
    private static final int SIEGE_TANK_MINERAL_COST = 150;
    private static final int SIEGE_TANK_GAS_COST = 100;

    /**
     * The mineral and gas costs that an EUD write leaves in the cost region, and the resources that the spend leaves.
     */
    private static final int WRITTEN_MINERAL_COST = 7;
    private static final int WRITTEN_GAS_COST = 3;
    private static final int MINERALS_AFTER_WRITTEN_COST = 943;
    private static final int GAS_AFTER_WRITTEN_COST = 997;

    private static final int EMPTY_QUEUE_OWNER = 1;
    private static final int STALE_COST = 5;

    private static @NotNull Game startTerranGame(@NotNull GameData gameData) throws Exception {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN)));
        ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

        runtime.start(0, 0);

        Assertions.assertNull(runtime.getFailure());

        int tick = 0;

        while (runtime.getGame().getCurrentFrame() < START_FRAME) {
            Assertions.assertTrue(runtime.step(GameSpeed.FASTEST_DURATION, tick += GameSpeed.FASTEST_DURATION));
        }

        return runtime.getGame();
    }


    @Test
    void cost_tests_store_their_costs_and_spend_charges_stored_values() throws Exception {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = startTerranGame(gameData);
            int commandCenterType = ScrUnitIds.TERRAN_COMMAND_CENTER;
            UnitPool unitPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(unitPool);

            Unit commandCenter = null;

            for (int index = unitPool.getFirstCreatedIndex(true); index != 0; index = unitPool.getNextCreatedIndex(index)) {
                Unit unit = unitPool.getUnit(index);

                if (unit.getUnitId() == commandCenterType) {
                    commandCenter = unit;

                    break;
                }
            }

            Assertions.assertNotNull(commandCenter, "No unit of type " + commandCenterType + ".");

            int owner = commandCenter.getOwnerIndex();
            Player player = game.getPlayerTable().getPlayer(owner);
            ClassicAddressSpace space = ClassicAddressSpace.create(game);
            CostTests costs = game.getCostTests();

            Assertions.assertNotNull(player);

            player.setMinerals(INITIAL_RESOURCES);
            player.setGas(INITIAL_RESOURCES);

            Assertions.assertTrue(costs.testUnit(game, owner, ScrUnitIds.TERRAN_SCV, true, false));
            Assertions.assertEquals(SCV_MINERAL_COST, space.readDword(MINERAL_COST_ADDRESS + PLAYER_COST_BYTES * owner));
            Assertions.assertEquals(0, space.readDword(GAS_COST_ADDRESS + PLAYER_COST_BYTES * owner));
            Assertions.assertEquals(SCV_SUPPLY_COST, space.readDword(SUPPLY_COST_ADDRESS + PLAYER_COST_BYTES * owner));

            costs.spend(game, owner);

            Assertions.assertEquals(MINERALS_AFTER_SCV, player.getMinerals());

            // The technology test stores a supply of 0; the unit test without a supply test keeps it.
            Assertions.assertTrue(costs.testTechnology(game, owner, ScrTechIds.STIM_PACKS, false));
            Assertions.assertEquals(0, space.readDword(SUPPLY_COST_ADDRESS + PLAYER_COST_BYTES * owner));

            Assertions.assertTrue(costs.testUnit(game, owner, ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE, false, false));
            Assertions.assertEquals(SIEGE_TANK_MINERAL_COST, space.readDword(MINERAL_COST_ADDRESS + PLAYER_COST_BYTES * owner));
            Assertions.assertEquals(SIEGE_TANK_GAS_COST, space.readDword(GAS_COST_ADDRESS + PLAYER_COST_BYTES * owner));
            Assertions.assertEquals(0, space.readDword(SUPPLY_COST_ADDRESS + PLAYER_COST_BYTES * owner));

            // The spend charges what the region holds, as an EUD write left it.
            space.writeDword(MINERAL_COST_ADDRESS + PLAYER_COST_BYTES * owner, WRITTEN_MINERAL_COST);
            space.writeDword(GAS_COST_ADDRESS + PLAYER_COST_BYTES * owner, WRITTEN_GAS_COST);

            costs.spend(game, owner);

            Assertions.assertEquals(MINERALS_AFTER_WRITTEN_COST, player.getMinerals());
            Assertions.assertEquals(GAS_AFTER_WRITTEN_COST, player.getGas());
        }
    }

    @Test
    void cost_tests_store_zero_costs_for_empty_queue_type() throws Exception {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = startTerranGame(gameData);
            CostTests costs = game.getCostTests();

            costs.getMineralCosts()[EMPTY_QUEUE_OWNER] = STALE_COST;
            costs.getSupplyCosts()[EMPTY_QUEUE_OWNER]  = STALE_COST;

            Assertions.assertTrue(costs.testUnit(game, EMPTY_QUEUE_OWNER, ScrUnitIds.NONE, true, true));
            Assertions.assertEquals(0, costs.getMineralCosts()[EMPTY_QUEUE_OWNER]);
            Assertions.assertEquals(0, costs.getSupplyCosts()[EMPTY_QUEUE_OWNER]);
        }
    }
}
