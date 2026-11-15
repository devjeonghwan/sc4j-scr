package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.order.WorkerCommandScenario;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit.ClassicUnitPointerRegion;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit.ClassicUnitTableRegion;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Verifies the unit pool: the native list bookkeeping without inferring unit creation order, the free list links that EUD reads and writes,
 * the move of a unit to the front of another owner list and the generation, death state and sprite checks of a resolved unit handle.
 */
final class UnitPoolTest {
    private static final int UNITS = 0x59CCA8;
    private static final int UNIT_ENTRY = 336;
    private static final int UNIT_FREE_TAIL = 0x62843C;
    /**
     * The offset of the next link after the previous link of a free entry.
     */
    private static final int NEXT_LINK_OFFSET = 0x04;
    private static final int LAST_UNIT_SLOT = 1700;
    private static final int LINKED_UNIT_SLOT = 9;
    private static final int TWO_SLOT_CAPACITY = 2;
    /**
     * The lowest generation bit of a unit handle.
     */
    private static final int LOWEST_GENERATION_BIT = 0x800;
    /**
     * The highest bit of a unit handle.
     */
    private static final int HIGHEST_HANDLE_BIT = 0x80000000;

    /**
     * The capacity of the unit pool.
     */
    private static final int POOL_CAPACITY = 4;
    /**
     * The first unit slot.
     */
    private static final int FIRST_UNIT = 1;
    /**
     * The second unit slot.
     */
    private static final int SECOND_UNIT = 2;
    /**
     * The third unit slot, the last owned one.
     */
    private static final int THIRD_UNIT = 3;
    /**
     * The fourth unit slot, which is in no owner list.
     */
    private static final int FOURTH_UNIT = 4;
    /**
     * The owner that the unit outside the owner lists is moved to.
     */
    private static final int OTHER_OWNER = 1;
    /**
     * The owner of the units before they move to another owner list.
     */
    private static final int FIRST_OWNER = 0;
    /**
     * The owner-list index that ends a list.
     */
    private static final int NO_UNIT = 0;
    /**
     * The neutral player slot.
     */
    private static final int NEUTRAL_PLAYER = 11;

    /**
     * The hidden-to-active transfer cases: the largest pool capacity, the three traversal orders of the hidden units, the list heads and the
     * created-list links of a slot in each snapshot, the expected snapshot count and the digest of the snapshots.
     */
    private static final int MAXIMUM_TRANSFER_CAPACITY = 12;
    private static final int TRAVERSAL_COUNT = 3;
    private static final int REVERSED_TRAVERSAL = 1;
    private static final int INTERLEAVED_TRAVERSAL = 2;
    private static final int LIST_HEAD_COUNT = 4;
    private static final int LINKS_PER_SLOT = 2;
    private static final int TRANSFER_SNAPSHOT_COUNT = 1326;
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";
    private static final @NotNull String TRANSFER_DIGEST = "614b412062644c5ccf924ff33fb99f6767b6a7ac8db89efb674ef58448f9ce75";
    /**
     * The largest five-bit generation, the generation mask and the number of generations.
     */
    private static final int MAXIMUM_GENERATION = 31;
    private static final int GENERATION_MASK = 0x1F;
    private static final int GENERATION_COUNT = 32;
    /**
     * The image offset and the value of a DWORD that the slot storage keeps across recycling.
     */
    private static final int RETAINED_DWORD_OFFSET = 0x150;
    private static final int RETAINED_DWORD = 0x12345678;
    /**
     * The image offsets of the minimum horizontal and the maximum vertical edge index.
     */
    private static final int MINIMUM_HORIZONTAL_EDGE_OFFSET = 0x1D4;
    private static final int MAXIMUM_VERTICAL_EDGE_OFFSET = 0x1E0;
    /**
     * A creation tag and the WORD of it that the pool stores.
     */
    private static final int CREATION_TAG = 0x12345678;
    private static final int STORED_CREATION_TAG = 0x5678;
    /**
     * The first capacity past the largest pool.
     */
    private static final int OVERSIZED_CAPACITY = 8192;
    private static final int SPRITE_POSITION = 100;

    private static int getUnitAddress(int poolIndex) {
        return UNITS + (poolIndex - 1) * UNIT_ENTRY;
    }

    @Test
    void hidden_and_dead_transfers_keep_allocation_until_final_recycling() {
        UnitPool pool = new UnitPool(3);
        Unit first = pool.getUnit(UnitPoolScenario.allocate(pool));
        Unit second = pool.getUnit(UnitPoolScenario.allocate(pool));
        Unit third = pool.getUnit(UnitPoolScenario.allocate(pool));

        for (Unit unit : new Unit[]{first, second, third}) {
            unit.setStatus(UnitStatusFlags.GROUNDED_BUILDING);
            pool.prependToOwnerList(unit, 0);
            pool.insertCreatedUnit(unit);
        }

        pool.transferCreatedUnitToHidden(third);

        Assertions.assertEquals(third.getIndex(), pool.getFirstCreatedIndex(false));
        Assertions.assertEquals(first.getIndex(), pool.getPreviousCreatedIndex(second.getIndex()));
        Assertions.assertEquals(second.getIndex(), pool.getNextCreatedIndex(first.getIndex()));

        pool.transferCreatedUnitToActive(third);

        Assertions.assertEquals(0, pool.getFirstCreatedIndex(false));
        Assertions.assertEquals(third.getIndex(), pool.getNextCreatedIndex(first.getIndex()));

        pool.transferUnitToDead(third, false);

        Assertions.assertEquals(third.getIndex(), pool.getFirstDeadIndex());
        Assertions.assertEquals(third.getIndex(), pool.getLastDeadIndex());
        Assertions.assertEquals(3, pool.getAllocatedCount());
        Assertions.assertEquals(second.getIndex(), pool.getFirstOwnerIndex(0));
        Assertions.assertEquals(0, pool.getPreviousOwnerIndex(second.getIndex()));

        pool.unlinkDeadUnit(third);
        pool.recycleFinalized(third.getIndex());

        Assertions.assertEquals(0, pool.getFirstDeadIndex());
        Assertions.assertEquals(0, pool.getLastDeadIndex());
        Assertions.assertEquals(2, pool.getAllocatedCount());
        Assertions.assertFalse(pool.isAllocated(third.getIndex()));
    }

    @Test
    void matches_native_hidden_to_active_transfer_cases() throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        ByteBuffer values = ByteBuffer.allocate((LIST_HEAD_COUNT + MAXIMUM_TRANSFER_CAPACITY * LINKS_PER_SLOT) * Integer.BYTES)
                                      .order(ByteOrder.LITTLE_ENDIAN);
        int snapshots = 0;

        for (int capacity = 1; capacity <= MAXIMUM_TRANSFER_CAPACITY; capacity++) {
            for (int activeCount = 0; activeCount < capacity; activeCount++) {
                for (int traversal = 0; traversal < TRAVERSAL_COUNT; traversal++) {
                    UnitPool pool = new UnitPool(capacity);
                    int[] sequence = new int[capacity];

                    for (int index = 0; index < capacity; index++) {
                        int poolIndex = UnitPoolScenario.allocate(pool);

                        sequence[index] = poolIndex;

                        Unit unit = pool.getUnit(poolIndex);

                        UnitMemoryScenario.of(unit).writeMemoryInteger(UnitMemoryScenario.STATUS_OFFSET,
                                                                       index < activeCount ? UnitStatusFlags.GROUNDED_BUILDING : 0);
                        pool.insertCreatedUnit(unit);
                    }

                    int hiddenCount = capacity - activeCount;

                    for (int transferIndex = -1; transferIndex < hiddenCount; transferIndex++) {
                        if (transferIndex >= 0) {
                            int sourceIndex = switch (traversal) {
                                case REVERSED_TRAVERSAL -> hiddenCount - transferIndex - 1;
                                case INTERLEAVED_TRAVERSAL -> transferIndex < (hiddenCount + 1) / 2
                                                              ? transferIndex * 2
                                                              : (transferIndex - (hiddenCount + 1) / 2) * 2 + 1;
                                default -> transferIndex;
                            };

                            pool.transferCreatedUnitToActive(pool.getUnit(sequence[activeCount + sourceIndex]));
                        }

                        values.clear();
                        values.putInt(pool.getFirstCreatedIndex(true));
                        values.putInt(pool.getLastCreatedIndex(true));
                        values.putInt(pool.getFirstCreatedIndex(false));
                        values.putInt(pool.getLastCreatedIndex(false));

                        for (int poolIndex = 1; poolIndex <= capacity; poolIndex++) {
                            values.putInt(pool.getPreviousCreatedIndex(poolIndex));
                            values.putInt(pool.getNextCreatedIndex(poolIndex));
                        }

                        digest.update(values.array(), 0, values.position());

                        snapshots++;
                    }
                }
            }
        }

        Assertions.assertEquals(TRANSFER_SNAPSHOT_COUNT, snapshots);
        Assertions.assertEquals(TRANSFER_DIGEST, HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void slot_storage_survives_recycling_and_commit_follows_the_generation_write() {
        UnitPool pool = new UnitPool(2);
        Unit unit = pool.getUnit(1);

        for (int generation = 0; generation < MAXIMUM_GENERATION; generation++) {
            pool.advanceGeneration(unit);
        }

        UnitMemoryScenario.of(unit).writeMemoryInteger(RETAINED_DWORD_OFFSET, RETAINED_DWORD);

        Assertions.assertEquals(MAXIMUM_GENERATION, pool.getGeneration(1));
        Assertions.assertEquals(0, pool.getAllocatedCount());

        pool.advanceGeneration(unit);
        pool.commitInitializedUnit(unit);

        Assertions.assertEquals(0, pool.getGeneration(1));
        Assertions.assertEquals(2, pool.getFirstFreeIndex());
        Assertions.assertEquals(1, pool.getAllocatedCount());
        Assertions.assertThrows(IllegalStateException.class, () -> pool.commitInitializedUnit(unit));

        pool.recycleFinalized(1);

        Assertions.assertSame(unit, pool.getUnit(1));
        Assertions.assertEquals(RETAINED_DWORD, UnitMemoryScenario.of(pool.getUnit(1)).readMemoryInteger(RETAINED_DWORD_OFFSET));
        Assertions.assertEquals(0, pool.getGeneration(1));
        Assertions.assertFalse(pool.isAllocated(1));
        Assertions.assertThrows(IllegalStateException.class, () -> pool.commitInitializedUnit(UnitTestUtils.createUnit(2)));
    }

    @Test
    void prepend_to_owner_list_should_register_the_owner_before_the_free_list_commit() {
        UnitPool pool = new UnitPool(3);
        Unit first = pool.getUnit(1);
        Unit second = pool.getUnit(3);

        Assertions.assertEquals(0, pool.getFirstOwnerIndex(NEUTRAL_PLAYER));

        pool.prependToOwnerList(first, NEUTRAL_PLAYER);

        Assertions.assertEquals(0, pool.getAllocatedCount());
        Assertions.assertEquals(1, pool.getFirstFreeIndex());
        Assertions.assertEquals(1, pool.getFirstOwnerIndex(NEUTRAL_PLAYER));
        Assertions.assertEquals(-1, UnitMemoryScenario.of(first).readMemoryInteger(MINIMUM_HORIZONTAL_EDGE_OFFSET));
        Assertions.assertEquals(-1, UnitMemoryScenario.of(first).readMemoryInteger(MAXIMUM_VERTICAL_EDGE_OFFSET));

        pool.commitInitializedUnit(first);
        pool.prependToOwnerList(second, NEUTRAL_PLAYER);

        Assertions.assertEquals(3, pool.getFirstOwnerIndex(NEUTRAL_PLAYER));
        Assertions.assertEquals(0, pool.getPreviousOwnerIndex(3));
        Assertions.assertEquals(1, pool.getNextOwnerIndex(3));
        Assertions.assertEquals(3, pool.getPreviousOwnerIndex(1));
        Assertions.assertEquals(0, pool.getNextOwnerIndex(1));
    }

    @Test
    void created_units_enter_separate_active_and_hidden_lists_with_word_tags() {
        UnitPool pool = new UnitPool(4);

        for (int creationIndex = 0; creationIndex < 3; creationIndex++) {
            int poolIndex = UnitPoolScenario.allocate(pool);
            Unit unit = pool.getUnit(poolIndex);

            UnitMemoryScenario.of(unit).writeMemoryInteger(UnitMemoryScenario.STATUS_OFFSET, UnitStatusFlags.GROUNDED_BUILDING);
            pool.insertCreatedUnit(unit);
            pool.setCreationTag(poolIndex, CREATION_TAG);

            Assertions.assertEquals(STORED_CREATION_TAG, pool.getCreationTag(poolIndex));
        }

        Assertions.assertEquals(1, pool.getFirstCreatedIndex(true));
        Assertions.assertEquals(3, pool.getNextCreatedIndex(1));
        Assertions.assertEquals(4, pool.getNextCreatedIndex(3));
        Assertions.assertEquals(3, pool.getPreviousCreatedIndex(4));
        Assertions.assertEquals(0, pool.getNextCreatedIndex(4));
        Assertions.assertEquals(0, pool.getFirstCreatedIndex(false));

        int last = UnitPoolScenario.allocate(pool);

        pool.insertCreatedUnit(pool.getUnit(last));

        Assertions.assertEquals(2, pool.getFirstCreatedIndex(false));
        Assertions.assertEquals(0, pool.getNextCreatedIndex(2));
    }

    @Test
    void allocation_follows_the_initial_free_traversal_for_small_and_both_handle_layouts() {
        for (int capacity : new int[]{0, 1, 2, 5, 1700, 1701, 8191}) {
            UnitPool pool = new UnitPool(capacity);

            Assertions.assertEquals(capacity, pool.getCapacity());

            for (int allocationIndex = 0; allocationIndex < capacity; allocationIndex++) {
                int expectedPoolIndex = allocationIndex == 0 ? 1 : capacity - allocationIndex + 1;

                Assertions.assertEquals(expectedPoolIndex, pool.getFirstFreeIndex());
                Assertions.assertEquals(expectedPoolIndex, UnitPoolScenario.allocate(pool));
                Assertions.assertTrue(pool.isAllocated(expectedPoolIndex));
                Assertions.assertEquals(1, pool.getGeneration(expectedPoolIndex));
                Assertions.assertEquals(expectedPoolIndex, UnitHandle.getPoolIndex(pool.getHandle(expectedPoolIndex), capacity));
                Assertions.assertEquals(1, UnitHandle.getGeneration(pool.getHandle(expectedPoolIndex), capacity));
            }

            Assertions.assertEquals(capacity, pool.getAllocatedCount());
            Assertions.assertEquals(0, pool.getFirstFreeIndex());
            Assertions.assertEquals(0, UnitPoolScenario.allocate(pool));
            Assertions.assertEquals(capacity, pool.getAllocatedCount());
        }
    }

    @Test
    void recycled_slots_insert_after_the_free_head_and_keep_their_generations() {
        UnitPool pool = new UnitPool(6);

        Assertions.assertEquals(1, UnitPoolScenario.allocate(pool));
        Assertions.assertEquals(6, UnitPoolScenario.allocate(pool));
        Assertions.assertEquals(5, UnitPoolScenario.allocate(pool));

        int originalFirstHandle = pool.getHandle(1);

        pool.recycleFinalized(1);
        pool.recycleFinalized(5);

        Assertions.assertEquals(4, pool.getFirstFreeIndex());
        Assertions.assertEquals(1, pool.getAllocatedCount());
        Assertions.assertFalse(pool.isAllocated(1));
        Assertions.assertEquals(originalFirstHandle, pool.getHandle(1));

        for (int expectedPoolIndex : new int[]{4, 5, 1, 3, 2}) {
            Assertions.assertEquals(expectedPoolIndex, UnitPoolScenario.allocate(pool));
        }

        Assertions.assertEquals(2, pool.getGeneration(1));
        Assertions.assertEquals(2, pool.getGeneration(5));
        Assertions.assertEquals(1, pool.getGeneration(6));
        Assertions.assertNotEquals(originalFirstHandle, pool.getHandle(1));
    }

    @Test
    void first_slot_returned_to_an_empty_free_list_stays_its_head() {
        UnitPool pool = new UnitPool(3);

        UnitPoolScenario.allocate(pool);
        UnitPoolScenario.allocate(pool);
        UnitPoolScenario.allocate(pool);

        pool.recycleFinalized(2);
        pool.recycleFinalized(1);
        pool.recycleFinalized(3);

        Assertions.assertEquals(2, UnitPoolScenario.allocate(pool));
        Assertions.assertEquals(3, UnitPoolScenario.allocate(pool));
        Assertions.assertEquals(1, UnitPoolScenario.allocate(pool));
        Assertions.assertEquals(0, UnitPoolScenario.allocate(pool));
    }

    @Test
    void slot_reuse_wraps_to_generation_zero_and_aliases_the_old_handle_after_32_reuses() {
        UnitPool pool = new UnitPool(1);

        Assertions.assertEquals(1, UnitPoolScenario.allocate(pool));

        int initialHandle = pool.getHandle(1);

        for (int reuseCount = 1; reuseCount <= GENERATION_COUNT; reuseCount++) {
            pool.recycleFinalized(1);

            Assertions.assertEquals(1, UnitPoolScenario.allocate(pool));
            Assertions.assertEquals((reuseCount + 1) & GENERATION_MASK, pool.getGeneration(1));

            if (reuseCount < GENERATION_COUNT) {
                Assertions.assertNotEquals(initialHandle, pool.getHandle(1));
            }

            if (reuseCount == MAXIMUM_GENERATION) {
                Assertions.assertEquals(1, pool.getHandle(1));
            }
        }

        Assertions.assertEquals(initialHandle, pool.getHandle(1));
    }

    @Test
    void pool_rejects_misuse_without_changing_its_state() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new UnitPool(-1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new UnitPool(OVERSIZED_CAPACITY));

        UnitPool pool = new UnitPool(2);

        Assertions.assertThrows(IllegalStateException.class, () -> pool.recycleFinalized(1));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> pool.recycleFinalized(0));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> pool.getGeneration(3));
        Assertions.assertEquals(0, pool.getAllocatedCount());
        Assertions.assertEquals(1, UnitPoolScenario.allocate(pool));

        pool.recycleFinalized(1);

        Assertions.assertThrows(IllegalStateException.class, () -> pool.recycleFinalized(1));
        Assertions.assertEquals(2, UnitPoolScenario.allocate(pool));
        Assertions.assertEquals(1, UnitPoolScenario.allocate(pool));
        Assertions.assertEquals(2, pool.getAllocatedCount());
    }

    @Test
    void resolve_handle_should_return_no_unit_when_index_lies_outside_the_pool() {
        int capacity = 2;
        UnitPool pool = new UnitPool(capacity);
        Unit unit = pool.getUnit(UnitPoolScenario.allocate(pool));

        unit.setSprite(SpriteScenario.createSprite(0, SPRITE_POSITION, SPRITE_POSITION, 0));

        Assertions.assertNull(pool.resolveHandle(UnitHandle.encode(0, 1, capacity)), "Index zero addresses the storage before the pool.");
        Assertions.assertNull(pool.resolveHandle(capacity + 1), "An index past the capacity addresses no unit.");
        Assertions.assertSame(unit, pool.resolveHandle(pool.getHandle(unit.getIndex())));
    }

    @Test
    void free_unit_slots_serve_and_store_their_previous_links() {
        UnitPool pool = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new ClassicUnitTableRegion(pool));
        space.add(ClassicUnitPointerRegion.createUnitListRegion(pool));

        Assertions.assertEquals(0, space.readDword(getUnitAddress(1)));
        Assertions.assertEquals(getUnitAddress(1), space.readDword(getUnitAddress(LAST_UNIT_SLOT)));
        Assertions.assertEquals(getUnitAddress(3), space.readDword(getUnitAddress(2)));
        Assertions.assertEquals(getUnitAddress(2), space.readDword(UNIT_FREE_TAIL), "The first insertion after the head made slot 2 the tail.");

        space.writeDword(getUnitAddress(LAST_UNIT_SLOT), getUnitAddress(LINKED_UNIT_SLOT));

        Assertions.assertEquals(LINKED_UNIT_SLOT, pool.getPreviousFreeIndex(LAST_UNIT_SLOT), "An EUD write of an entry start stores its slot.");
        Assertions.assertEquals(getUnitAddress(LINKED_UNIT_SLOT), space.readDword(getUnitAddress(LAST_UNIT_SLOT)));

        space.writeDword(getUnitAddress(LAST_UNIT_SLOT), getUnitAddress(1));
        space.writeDword(getUnitAddress(1), getUnitAddress(LINKED_UNIT_SLOT));

        Unit head = pool.getUnit(1);

        pool.commitInitializedUnit(head);

        Assertions.assertEquals(LAST_UNIT_SLOT, pool.getFirstFreeIndex());
        Assertions.assertEquals(getUnitAddress(LAST_UNIT_SLOT), space.readDword(getUnitAddress(LINKED_UNIT_SLOT) + NEXT_LINK_OFFSET),
                                "Slot 9 now links to the slot after the removed head.");
        Assertions.assertEquals(getUnitAddress(LINKED_UNIT_SLOT), space.readDword(getUnitAddress(LAST_UNIT_SLOT)));

        pool.insertCreatedUnit(head);
        pool.transferCreatedUnitToActive(head);
        pool.transferUnitToDead(head, false);
        pool.unlinkDeadUnit(head);
        pool.recycleFinalized(1);

        Assertions.assertEquals(getUnitAddress(LAST_UNIT_SLOT), space.readDword(getUnitAddress(1)));
        Assertions.assertEquals(getUnitAddress(LAST_UNIT_SLOT - 1), space.readDword(getUnitAddress(1) + NEXT_LINK_OFFSET));
        Assertions.assertEquals(getUnitAddress(1), space.readDword(getUnitAddress(LAST_UNIT_SLOT - 1)));
    }

    @Test
    void unit_free_tail_follows_insertion_and_removal() {
        UnitPool pool = new UnitPool(TWO_SLOT_CAPACITY);

        Assertions.assertEquals(2, pool.getLastFreeIndex());

        pool.commitInitializedUnit(pool.getUnit(1));

        Assertions.assertEquals(2, pool.getLastFreeIndex());

        pool.commitInitializedUnit(pool.getUnit(2));

        Assertions.assertEquals(0, pool.getLastFreeIndex(), "Removing the tail moves it to its previous slot, none.");

        pool.recycleFinalized(2);

        Assertions.assertEquals(2, pool.getLastFreeIndex(), "An empty list takes the slot as head and tail.");

        pool.recycleFinalized(1);

        Assertions.assertEquals(1, pool.getLastFreeIndex(), "Inserting after a head that is the tail makes the slot the tail.");

        pool.setListHeadRaw(UnitPool.ListHead.LAST_FREE, 2);

        Assertions.assertEquals(2, pool.getLastFreeIndex(), "An EUD write of 0x62843C stores the tail.");
    }

    @RequiresInstallation
    @Test
    void resolve_handle_should_check_full_generation_death_state_and_retained_sprite() throws IOException {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = WorkerCommandScenario.createGame(gameData, ScrUnitIds.ZERG_DRONE);
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            Unit worker = pool.getUnit(1);
            int handle = pool.getHandle(1);

            Assertions.assertSame(worker, pool.resolveHandle(handle));
            Assertions.assertNull(pool.resolveHandle(handle ^ LOWEST_GENERATION_BIT));
            Assertions.assertNull(pool.resolveHandle(handle | HIGHEST_HANDLE_BIT));

            worker.setPrimaryOrder(ScrOrderIds.DIE);
            worker.setPrimaryOrderState(Unit.DIE_STATE_DESTROYED);

            Assertions.assertNull(pool.resolveHandle(handle));

            worker.setPrimaryOrderState(0);

            Assertions.assertSame(worker, pool.resolveHandle(handle));

            worker.setSprite(null);

            Assertions.assertNull(pool.resolveHandle(handle));
            Assertions.assertNull(pool.resolveHandle(0));
        }
    }

    @Test
    void move_to_owner_list_should_insert_unit_at_front_of_new_owner_list() {
        UnitPool pool = new UnitPool(POOL_CAPACITY);

        for (int index = FIRST_UNIT; index <= THIRD_UNIT; index++) {
            pool.prependToOwnerList(pool.getUnit(index), FIRST_OWNER);
        }

        for (int index = FIRST_UNIT; index <= THIRD_UNIT; index++) {
            pool.getUnit(index).setOwnerIndex(FIRST_OWNER);
        }

        pool.moveToOwnerList(pool.getUnit(SECOND_UNIT), NEUTRAL_PLAYER);
        pool.getUnit(SECOND_UNIT).setOwnerIndex(NEUTRAL_PLAYER);

        Assertions.assertEquals(THIRD_UNIT, pool.getFirstOwnerIndex(FIRST_OWNER));
        Assertions.assertEquals(FIRST_UNIT, pool.getNextOwnerIndex(THIRD_UNIT));
        Assertions.assertEquals(NO_UNIT, pool.getNextOwnerIndex(FIRST_UNIT));
        Assertions.assertEquals(SECOND_UNIT, pool.getFirstOwnerIndex(NEUTRAL_PLAYER));

        pool.moveToOwnerList(pool.getUnit(THIRD_UNIT), NEUTRAL_PLAYER);
        pool.getUnit(THIRD_UNIT).setOwnerIndex(NEUTRAL_PLAYER);

        Assertions.assertEquals(FIRST_UNIT, pool.getFirstOwnerIndex(FIRST_OWNER));
        Assertions.assertEquals(THIRD_UNIT, pool.getFirstOwnerIndex(NEUTRAL_PLAYER));
        Assertions.assertEquals(SECOND_UNIT, pool.getNextOwnerIndex(THIRD_UNIT));
        Assertions.assertEquals(THIRD_UNIT, pool.getPreviousOwnerIndex(SECOND_UNIT));
        Assertions.assertThrows(IllegalStateException.class, () -> pool.moveToOwnerList(pool.getUnit(FOURTH_UNIT), OTHER_OWNER));
    }
}
