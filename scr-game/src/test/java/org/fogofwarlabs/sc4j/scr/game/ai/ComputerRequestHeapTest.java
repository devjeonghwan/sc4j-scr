package org.fogofwarlabs.sc4j.scr.game.ai;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks spending-request and town-request insertion, ordering and capacity.
 */
final class ComputerRequestHeapTest {
    private static final int TOWN_REQUEST_KIND = 3;
    private static final int WORKER_REQUEST_KIND = 4;
    private static final int REQUEST_CAPACITY = 63;
    private static final int TOWN_REQUEST_COUNT = 100;
    private static final int TOWN_POSITION_X = 3808;
    private static final int TOWN_POSITION_Y = 2384;

    @Test
    void request_heap_keeps_highest_priority_at_head_and_root_on_equal_priority() {
        ComputerPlayer player = new ComputerPlayer(0, 50, 0);

        player.pushRequest(80, WORKER_REQUEST_KIND, 41, null, -1);
        player.pushRequest(150, TOWN_REQUEST_KIND, 131, null, -1);
        player.pushRequest(80, WORKER_REQUEST_KIND, 42, null, -1);
        player.pushRequest(130, TOWN_REQUEST_KIND, 42, null, -1);

        Assertions.assertEquals(4, player.getRequestCount());
        Assertions.assertEquals(131, player.getActiveRequestType());
        Assertions.assertEquals(TOWN_REQUEST_KIND, player.getActiveRequestKind());

        player.popRequest(false, 0, 0, 0, true);

        Assertions.assertEquals(42, player.getActiveRequestType());
        Assertions.assertEquals(TOWN_REQUEST_KIND, player.getActiveRequestKind());

        player.popRequest(false, 0, 0, 0, true);

        // Equal priorities: the last element replaces the root and stays when its child is not strictly larger.
        Assertions.assertEquals(42, player.getActiveRequestType());
        Assertions.assertEquals(WORKER_REQUEST_KIND, player.getActiveRequestKind());

        player.popRequest(false, 0, 0, 0, true);

        Assertions.assertEquals(41, player.getActiveRequestType());

        player.popRequest(false, 0, 0, 0, true);

        Assertions.assertEquals(0, player.getRequestCount());
        Assertions.assertEquals(0, player.getActiveRequestKind());
    }

    @Test
    void push_request_should_overwrite_last_slot_when_heap_is_full() {
        ComputerPlayer player = new ComputerPlayer(0, 0, 0);

        for (int index = 0; index < REQUEST_CAPACITY; index++) {
            player.pushRequest(10, WORKER_REQUEST_KIND, index, null, -1);
        }

        Assertions.assertEquals(REQUEST_CAPACITY, player.getRequestCount());

        player.pushRequest(200, TOWN_REQUEST_KIND, 200, null, -1);

        Assertions.assertEquals(REQUEST_CAPACITY, player.getRequestCount());
        Assertions.assertEquals(200, player.getActiveRequestType());
    }

    @Test
    void pop_request_should_saturate_reservations_and_charge_budgets_only_on_success() {
        ComputerPlayer player = new ComputerPlayer(0, 0, 0);

        player.beginUpdate(100, 20);
        player.setSupplyBudget(8);
        player.reserve(50, 0, 2);
        player.pushRequest(80, WORKER_REQUEST_KIND, 41, null, -1);
        player.pushRequest(80, WORKER_REQUEST_KIND, 41, null, -1);
        player.popRequest(true, 50, 0, 2, false);

        Assertions.assertEquals(0, player.getReservedMinerals());
        Assertions.assertEquals(50, player.getMineralBudget());
        Assertions.assertEquals(6, player.getSupplyBudget());

        player.popRequest(false, 50, 0, 2, false);

        Assertions.assertEquals(0, player.getReservedMinerals());
        Assertions.assertEquals(50, player.getMineralBudget());
    }

    @Test
    void add_defense_units_should_append_within_twenty_word_list() {
        ComputerPlayer player = new ComputerPlayer(0, 0, 0);

        player.addDefenseUnits(ComputerPlayer.DEFENSE_USE_GROUND_GROUND, 11, 37);

        Assertions.assertEquals(0, player.getDefenseList(ComputerPlayer.DEFENSE_USE_GROUND_GROUND)[0]);

        player.addDefenseUnits(ComputerPlayer.DEFENSE_USE_GROUND_GROUND, 10, 37);
        player.addDefenseUnits(ComputerPlayer.DEFENSE_USE_GROUND_GROUND, 10, 38);
        player.addDefenseUnits(ComputerPlayer.DEFENSE_USE_GROUND_GROUND, 10, 39);

        short[] list = player.getDefenseList(ComputerPlayer.DEFENSE_USE_GROUND_GROUND);

        Assertions.assertEquals(38, list[9]);
        Assertions.assertEquals(39, list[10]);
        Assertions.assertEquals(39, list[19]);
    }

    @Test
    void add_request_should_store_town_request_in_first_empty_entry_with_packed_count() {
        ComputerTown town = new ComputerTown(0);

        town.initialize(0, TOWN_POSITION_X, TOWN_POSITION_Y);

        town.addRequest(0, 1, 41, 150, 0);
        town.addRequest(0, 9, 41, 80, 0);

        Assertions.assertEquals(8, Byte.toUnsignedInt(town.requestFlags[0]));
        Assertions.assertEquals(72, Byte.toUnsignedInt(town.requestFlags[1]));
        Assertions.assertEquals(80, Byte.toUnsignedInt(town.requestPriorities[1]));
        Assertions.assertEquals(41, town.requestTypes[1]);

        for (int index = 2; index < TOWN_REQUEST_COUNT; index++) {
            town.addRequest(0, 1, index, 1, 0);
        }

        town.addRequest(0, 1, 227, 1, 0);

        Assertions.assertEquals(TOWN_REQUEST_COUNT - 1, town.requestTypes[TOWN_REQUEST_COUNT - 1]);
    }
}
