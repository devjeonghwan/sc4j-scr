package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerScores;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.ClassicKeyStateRegion;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.LocalKeyStates;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit.ClassicUnitPointerRegion;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit.ClassicUnitTableRegion;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks live game-data, unit-list and key-state regions.
 */
final class ClassicAddressSpaceRegionTest {
    private static final int GAME_DATA = 0x57F0F0;
    private static final int MINERALS = 0x00;
    private static final int GAS = 0x30;
    private static final int DEATH_TABLE = 0x58A364;
    private static final int CODE_CONSTANT = 0x4A3898;

    private static final int UNIT_TABLE = 0x59CCA8;
    private static final int UNIT_ENTRY_SIZE = 336;
    private static final int LAST_ENTRY = UNIT_TABLE + 1699 * UNIT_ENTRY_SIZE;
    private static final int PREVIOUS_LINK = 0x00;
    private static final int NEXT_LINK = 0x04;
    private static final int HIT_POINTS = 0x08;
    private static final int ORDER_TARGET = 0x5C;
    private static final int STATUS = 0xDC;

    private static final int HIDDEN_HEAD = 0x628428;
    private static final int ACTIVE_HEAD = 0x628430;
    private static final int FREE_HEAD = 0x628438;
    private static final int FREE_TAIL = 0x62843C;
    private static final int OWNER_LISTS = 0x6283F8;

    private static final int NUMBER_KEYS = 0x596A48;
    private static final int FUNCTION_KEYS = 0x596A8C;
    private static final int VIRTUAL_KEY_0 = 0x30;
    private static final int VIRTUAL_KEY_1 = 0x31;
    private static final int VIRTUAL_KEY_F6 = 0x75;
    private static final int VIRTUAL_KEY_F8 = 0x77;

    @Test
    void game_data_maps_resources_and_deaths_at_classic_offsets() {
        Player[] players = new Player[PlayerTable.PLAYER_SLOTS];

        players[1] = new Player(1, 1, PlayerTypes.EITHER_PREFER_HUMAN, ScrRaceIds.ZERG, 0);

        PlayerScores scores = PlayerProgressTestUtils.createPlayerScores();
        ClassicAddressSpace space = ClassicAddressSpace.create(scores, null, players, new MessageLines());

        space.writeDword(GAME_DATA + MINERALS + Integer.BYTES, 500);

        Assertions.assertEquals(500, players[1].getMinerals());

        space.writeDword(GAME_DATA + GAS + Integer.BYTES, 77);

        Assertions.assertEquals(77, players[1].getGas());

        space.writeDword(DEATH_TABLE + Integer.BYTES * (2 + PlayerTable.PLAYER_SLOTS * ScrUnitIds.ZERG_ZERGLING), 9);

        Assertions.assertEquals(9, space.readExtendedDeaths(2, ScrUnitIds.ZERG_ZERGLING));
        Assertions.assertEquals(9, scores.getDeathTable().get(ScrUnitIds.ZERG_ZERGLING, 2));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> space.readDword(GAME_DATA), "Player 0 has no player object.");
        Assertions.assertThrows(UnsupportedOperationException.class, () -> space.readDword(CODE_CONSTANT), "Type-7 entries stay a boundary.");
    }

    @Test
    void unit_table_translates_fields_and_pointers() {
        UnitPool pool = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        ClassicUnitTableRegion region = new ClassicUnitTableRegion(pool);
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(region);

        Unit first = pool.getUnit(1);
        Unit second = pool.getUnit(2);

        first.setHitPoints(40 << 8);

        Assertions.assertEquals(40 << 8, space.readDword(UNIT_TABLE + HIT_POINTS));

        space.writeDword(UNIT_TABLE + UNIT_ENTRY_SIZE + HIT_POINTS, 25 << 8);

        Assertions.assertEquals(25 << 8, second.getHitPoints(), "Classic slot 1 is pool index 2.");

        first.setOrderTargetUnit(second);

        Assertions.assertEquals(UNIT_TABLE + UNIT_ENTRY_SIZE, space.readDword(UNIT_TABLE + ORDER_TARGET));
        Assertions.assertEquals(0, space.readDword(UNIT_TABLE + UNIT_ENTRY_SIZE + ORDER_TARGET), "No target reads as zero.");

        space.writeDword(UNIT_TABLE + STATUS, 0x12345);

        Assertions.assertEquals(0x12345, first.getStatus(), "Classic +0xDC is native +0x140.");
        Assertions.assertEquals(0x12345, space.readDword(UNIT_TABLE + STATUS));

        space.writeDword(UNIT_TABLE + ORDER_TARGET, 0);

        Assertions.assertNull(first.getOrderTargetUnit(), "Writing 0 stores a null pointer.");
        Assertions.assertEquals(0, space.readDword(UNIT_TABLE + PREVIOUS_LINK), "The free head has no previous free slot.");
        Assertions.assertEquals(UNIT_TABLE, space.readDword(LAST_ENTRY + PREVIOUS_LINK), "Slot 1700 follows the head in the free list.");
    }

    @Test
    void unit_list_heads_and_links_serve_and_store_the_pool_lists() {
        UnitPool pool = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new ClassicUnitTableRegion(pool));
        space.add(ClassicUnitPointerRegion.createUnitListRegion(pool));

        Assertions.assertEquals(UNIT_TABLE, space.readDword(FREE_HEAD), "The free traversal starts at slot 1.");
        Assertions.assertEquals(LAST_ENTRY, space.readDword(UNIT_TABLE + NEXT_LINK), "A free slot links to the next free slot.");
        Assertions.assertEquals(0, space.readDword(UNIT_TABLE + PREVIOUS_LINK), "The free head has no previous free slot.");
        Assertions.assertEquals(UNIT_TABLE + UNIT_ENTRY_SIZE, space.readDword(FREE_TAIL), "Slot 2 is the free tail.");

        Unit first = pool.getUnit(1);

        pool.commitInitializedUnit(first);
        pool.insertCreatedUnit(first);

        Unit second = pool.getUnit(UnitPool.CLASSIC_UNIT_CAPACITY);

        pool.commitInitializedUnit(second);
        pool.insertCreatedUnit(second);

        Assertions.assertEquals(UNIT_TABLE, space.readDword(HIDDEN_HEAD), "Both units start on the hidden list.");
        Assertions.assertEquals(LAST_ENTRY, space.readDword(UNIT_TABLE + NEXT_LINK));
        Assertions.assertEquals(UNIT_TABLE, space.readDword(LAST_ENTRY + PREVIOUS_LINK));
        Assertions.assertEquals(0, space.readDword(ACTIVE_HEAD));

        pool.transferCreatedUnitToActive(first);

        Assertions.assertEquals(UNIT_TABLE, space.readDword(ACTIVE_HEAD));
        Assertions.assertEquals(LAST_ENTRY, space.readDword(HIDDEN_HEAD));
        Assertions.assertEquals(UNIT_TABLE + UNIT_ENTRY_SIZE, space.readDword(FREE_TAIL), "The free tail stays slot 2.");
        Assertions.assertEquals(0, space.readDword(UNIT_TABLE + 1698 * UNIT_ENTRY_SIZE + PREVIOUS_LINK), "The new free head lost its previous link.");
        Assertions.assertEquals(UNIT_TABLE + 1698 * UNIT_ENTRY_SIZE, space.readDword(FREE_HEAD), "The free traversal continues 1700, 1699, ...");

        space.writeDword(ACTIVE_HEAD, LAST_ENTRY);

        Assertions.assertEquals(1700, pool.getFirstCreatedIndex(true), "A write of an entry start stores that unit as the head.");

        space.writeDword(ACTIVE_HEAD, 0x1234);

        Assertions.assertEquals(0x1234, space.readDword(ACTIVE_HEAD), "Other values only change the shadow.");
        Assertions.assertEquals(1700, pool.getFirstCreatedIndex(true));

        space.writeDword(ACTIVE_HEAD, 0);

        Assertions.assertEquals(0, pool.getFirstCreatedIndex(true), "0 stores a null head.");
    }

    @Test
    void owner_list_heads_serve_and_store_the_first_unit_of_each_owner() {
        UnitPool pool = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new ClassicUnitTableRegion(pool));
        space.add(ClassicUnitPointerRegion.createOwnerListRegion(pool));

        Assertions.assertEquals(0, space.readDword(OWNER_LISTS + 7 * Integer.BYTES), "The twelve owner heads start cleared.");

        Unit unit = pool.getUnit(5);

        pool.prependToOwnerList(unit, 7);

        Assertions.assertEquals(UNIT_TABLE + 4 * UNIT_ENTRY_SIZE, space.readDword(OWNER_LISTS + 7 * Integer.BYTES));

        space.writeDword(OWNER_LISTS + 11 * Integer.BYTES, UNIT_TABLE + 9 * UNIT_ENTRY_SIZE);

        Assertions.assertEquals(10, pool.getFirstOwnerIndex(PlayerTable.NEUTRAL_PLAYER_ID),
                                "A write of an entry start stores that unit as the head.");

        space.writeDword(OWNER_LISTS + 7 * Integer.BYTES, 0);

        Assertions.assertEquals(0, pool.getFirstOwnerIndex(7));
    }

    @Test
    void key_states_are_local_input_and_async_offsets_ignore_writes() {
        LocalKeyStates keys = new LocalKeyStates();
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new ClassicKeyStateRegion(keys));

        Assertions.assertThrows(UnsupportedOperationException.class, () -> space.readDword(FUNCTION_KEYS), "Unknown without the local input.");

        keys.setAllReleased();

        Assertions.assertEquals(0, space.readDword(FUNCTION_KEYS));

        keys.setState(VIRTUAL_KEY_F6, 1);

        Assertions.assertEquals(0x100, space.readDword(FUNCTION_KEYS), "Offset 0x74 starts at VK_F5; VK_F6 is the second byte.");

        space.writeDword(FUNCTION_KEYS, 0x01000000);

        Assertions.assertEquals(1, keys.getState(VIRTUAL_KEY_F8));

        keys.setState(VIRTUAL_KEY_1, 1);

        Assertions.assertEquals(0, space.readDword(NUMBER_KEYS), "Offsets 0x30..0x38 read GetAsyncKeyState, not the key bytes.");

        keys.setAsyncPressed(VIRTUAL_KEY_1, true);

        Assertions.assertEquals(0x100, space.readDword(NUMBER_KEYS));

        space.writeDword(NUMBER_KEYS, -1);

        Assertions.assertEquals(0, keys.getState(VIRTUAL_KEY_0), "Writes to offsets 0x30..0x38 are ignored.");
    }
}
