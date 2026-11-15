package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that the build queue data of a computer unit record moves with the build queue.
 */
final class ComputerUnitRecordTest {
    @RequiresInstallation
    @Test
    void queue_slot_data_moves_with_canceled_build_queue_entry() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = Game.create(data, new GameMap(32, 32, 0, new int[1024]), new LcgRandom(1));

            game.getPlayerTable().setPlayer(0, new Player(0, 0, PlayerTypes.HUMAN, 1, 0));

            Unit barracks = UnitTestUtils.createUnit(1);

            barracks.setOwnerIndex(0);

            ComputerUnitRecord record = new ComputerUnitRecord(ComputerUnitRecord.KIND_BUILDING, 1);

            barracks.setComputerRecord(record);

            ComputerTown first = new ComputerTown(1);
            ComputerTown second = new ComputerTown(2);

            barracks.setBuildQueueIndex(3);

            int[] slots = {ScrUnitIds.TERRAN_GHOST, ScrUnitIds.NONE, ScrUnitIds.NONE,
                           ScrUnitIds.TERRAN_MARINE, ScrUnitIds.TERRAN_FIREBAT};

            barracks.setFirstBuildQueueType(slots[0]);
            barracks.setSecondBuildQueueType(slots[1]);
            barracks.setThirdBuildQueueType(slots[2]);
            barracks.setFourthBuildQueueType(slots[3]);
            barracks.setFifthBuildQueueType(slots[4]);
            record.queueKinds[3]   = 1;
            record.queueTargets[3] = first;
            record.queueKinds[4]   = 2;
            record.queueTargets[4] = second;
            record.queueKinds[0]   = 4;
            record.queueTargets[0] = null;
            record.queueKinds[1]   = 0;

            game.getUnitLifecycle().cancelBuildQueueEntry(barracks, 0, 1000);

            Assertions.assertEquals(32, barracks.getBuildQueueUnitType(3));
            Assertions.assertEquals(1, barracks.getBuildQueueUnitType(4));
            Assertions.assertEquals(228, barracks.getBuildQueueUnitType(0));
            Assertions.assertEquals(2, record.queueKinds[3]);
            Assertions.assertSame(second, record.queueTargets[3]);
            Assertions.assertEquals(4, record.queueKinds[4]);
            Assertions.assertNull(record.queueTargets[4]);
            Assertions.assertEquals(0, record.queueKinds[0], "The slot emptied by the move takes the empty slot's data.");
            Assertions.assertNull(record.queueTargets[0]);
        }
    }
}
