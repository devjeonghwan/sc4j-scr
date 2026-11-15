package org.fogofwarlabs.sc4j.scr.client.ui.command;

import org.fogofwarlabs.sc4j.scr.client.ClientReplayScenario;
import org.fogofwarlabs.sc4j.scr.client.ClientSession;
import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.client.world.ScrPresentation;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks the production gate failure texts and the mineral shortage against replay state.
 */
@RequiresInstallation
final class ProductionGateTest {
    private static final @NotNull String REPLAY = GameTestResources.REPLAY_TVT_BAEKDUDAEGAN;
    private static final int REPLAY_START_FRAME = 6000;
    private static final int TERRAN = 1;
    private static final int PROTOSS = 2;

    @Test
    void production_gate_reports_mineral_shortage_and_failure_texts() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(REPLAY, REPLAY_START_FRAME)) {
            Game game = session.getGame();
            StatText text = StatText.load(session.getStorage(), "enUS");

            Assertions.assertEquals("Supply limit exceeded.", text.getString(ProductionGate.Failure.SUPPLY_LIMIT.getTextIdentifier(TERRAN)));
            Assertions.assertEquals("Psi limit exceeded.", text.getString(ProductionGate.Failure.SUPPLY_LIMIT.getTextIdentifier(PROTOSS)));
            Assertions.assertEquals("Not enough supplies...build more Supply Depots.",
                                    text.getString(ProductionGate.Failure.SUPPLY_SHORT.getTextIdentifier(TERRAN)));
            Assertions.assertEquals("Not enough minerals...mine more minerals.",
                                    text.getString(ProductionGate.Failure.MINERALS.getTextIdentifier(TERRAN)));
            Assertions.assertEquals("Not enough Vespene gases....harvest more gas.",
                                    text.getString(ProductionGate.Failure.GAS.getTextIdentifier(PROTOSS)));
            Assertions.assertEquals(TERRAN, ProductionGate.Failure.SUPPLY_LIMIT.getSoundRace(TERRAN, PROTOSS));
            Assertions.assertEquals(PROTOSS, ProductionGate.Failure.MINERALS.getSoundRace(TERRAN, PROTOSS));

            GameData gameData = game.getGameData();

            Assertions.assertNotNull(gameData);

            DatTable units = gameData.getUnits();

            Assertions.assertNotNull(units);

            Player player = game.getPlayerTable().getPlayer(0);

            Assertions.assertNotNull(player);

            int minerals = player.getMinerals();
            int costlyUnitTypeIdentifier = -1;

            for (int unitTypeIdentifier = 0; unitTypeIdentifier < ScrUnitIds.NONE && costlyUnitTypeIdentifier < 0; unitTypeIdentifier++) {
                boolean building = (units.get("Special Ability Flags", unitTypeIdentifier) & UnitTypeFlags.BUILDING) != 0;

                if (building && units.get("Supply Required", unitTypeIdentifier) == 0 && units.get("Mineral Cost", unitTypeIdentifier) > minerals) {
                    costlyUnitTypeIdentifier = unitTypeIdentifier;
                }
            }

            Assertions.assertTrue(costlyUnitTypeIdentifier >= 0, "A building costs more than the player's minerals.");

            int events = game.getInterfaceLink().getCommandErrorEvents().getCount();

            Assertions.assertEquals(ProductionGate.Failure.MINERALS,
                                    ProductionGate.check(new ScrGameView(game), new ScrPresentation(game), 0, costlyUnitTypeIdentifier));
            // The shared cost test stored the player's costs; the interface took its error event.
            Assertions.assertEquals(units.get("Mineral Cost", costlyUnitTypeIdentifier), game.getCostTests().getMineralCosts()[0]);
            Assertions.assertEquals(events, game.getInterfaceLink().getCommandErrorEvents().getCount());
        }
    }
}
