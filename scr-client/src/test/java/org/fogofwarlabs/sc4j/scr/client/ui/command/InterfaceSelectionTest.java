package org.fogofwarlabs.sc4j.scr.client.ui.command;

import org.fogofwarlabs.sc4j.scr.client.ClientReplayScenario;
import org.fogofwarlabs.sc4j.scr.client.ClientSession;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/**
 * Checks that the active unit of an interface selection is the unit of the highest rank, the earlier slot on ties, and that a selection replaces
 * the slots only up to the first empty slot; the engine selection view lists the units without the empty slots.
 */
@RequiresInstallation
final class InterfaceSelectionTest {
    private static final int COMMAND_CENTER = ScrUnitIds.TERRAN_COMMAND_CENTER;
    private static final int TERRAN_WORKER = ScrUnitIds.TERRAN_SCV;
    private static final int INTERACTION_FRAME = 6000;
    private static final int FIRST_SLOT = 0;
    private static final int SECOND_SLOT = 1;
    private static final int THIRD_SLOT = 2;
    private static final int FOURTH_SLOT = 3;

    @Test
    void get_active_unit_should_pick_higher_rank_and_keep_earlier_slot_on_ties() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN, INTERACTION_FRAME)) {
            Game game = session.getGame();
            DatTable units = game.requireGameData().getUnits();
            Unit worker = ClientReplayScenario.findCompletedUnits(game, 0, TERRAN_WORKER).getFirst();
            Unit commandCenter = ClientReplayScenario.findCompletedUnits(game, 0, COMMAND_CENTER).getFirst();
            InterfaceSelection selection = new InterfaceSelection(UnitsDat.createTypeTable(units));
            int workerRank = units.get("Sublabel", TERRAN_WORKER);
            int centerRank = units.get("Sublabel", COMMAND_CENTER);

            selection.set(0, worker, commandCenter);
            Assertions.assertSame(centerRank > workerRank ? commandCenter : worker, selection.getActiveUnit(), "a strictly higher rank wins");
            selection.set(0, commandCenter, worker);
            Assertions.assertSame(workerRank > centerRank ? worker : commandCenter, selection.getActiveUnit(), "ties keep the earlier slot");
            selection.set(0, worker, worker);
            Assertions.assertSame(worker, selection.getActiveUnit());
        }
    }

    @Test
    void select_should_keep_units_after_the_first_empty_slot() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN, INTERACTION_FRAME)) {
            Game game = session.getGame();
            List<Unit> workers = ClientReplayScenario.findCompletedUnits(game, 0, TERRAN_WORKER);
            InterfaceSelection selection = new InterfaceSelection(UnitsDat.createTypeTable(game.requireGameData().getUnits()));

            selection.set(0, workers.get(FIRST_SLOT), workers.get(SECOND_SLOT), workers.get(THIRD_SLOT), workers.get(FOURTH_SLOT));
            selection.setSlot(0, SECOND_SLOT, null);
            selection.select(0, workers.get(SECOND_SLOT));

            Assertions.assertSame(workers.get(SECOND_SLOT), selection.getUnit(FIRST_SLOT));
            Assertions.assertNull(selection.getUnit(SECOND_SLOT));
            Assertions.assertSame(workers.get(THIRD_SLOT), selection.getUnit(THIRD_SLOT));
            Assertions.assertSame(workers.get(FOURTH_SLOT), selection.getUnit(FOURTH_SLOT));
            Assertions.assertEquals(List.of(workers.get(SECOND_SLOT), workers.get(THIRD_SLOT), workers.get(FOURTH_SLOT)), selection.getUnits(),
                                    "the engine view lists the units in slot order without the hole");
            Assertions.assertEquals(FOURTH_SLOT + 1, selection.getCount(), "the native count covers the hole");
            Assertions.assertEquals(FOURTH_SLOT, selection.getSize());
            Assertions.assertFalse(selection.contains(workers.get(FIRST_SLOT)));
            Assertions.assertFalse(selection.isEmpty());
            Assertions.assertFalse(selection.isFull());

            selection.select(0, workers.get(FIRST_SLOT), workers.get(SECOND_SLOT), workers.get(THIRD_SLOT));

            Assertions.assertSame(workers.get(THIRD_SLOT), selection.getUnit(THIRD_SLOT));
            Assertions.assertSame(workers.get(FOURTH_SLOT), selection.getUnit(FOURTH_SLOT));
        }
    }
}
