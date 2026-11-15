package org.fogofwarlabs.sc4j.scr.game.session.setup;

import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks which values name a player and the slot each player id maps to after the records changed.
 */
final class PlayerSlotsTest {
    private static final int RECORDS = PlayerTable.PLAYABLE_PLAYER_SLOTS + PlayerTable.OBSERVER_SLOTS;
    private static final int HOST_PLAYER = 0;
    private static final int JOINER_PLAYER = 1;
    private static final int ABSENT_PLAYER = 2;
    private static final int OBSERVER_PLAYER = PlayerTable.FIRST_OBSERVER_SLOT + 1;
    private static final int HOST_SLOT = 0;
    private static final int RELEASED_SLOT = 1;
    private static final int JOINER_SLOT = 2;
    /**
     * The record of the second observer slot, after the eight player records.
     */
    private static final int OBSERVER_SLOT = 9;
    private static final int FIRST_GAME_RECORD_AFTER_PLAYERS = PlayerTable.PLAYABLE_PLAYER_SLOTS;
    private static final @NotNull String HOST_NAME = "host";
    private static final @NotNull String JOINER_NAME = "joiner";
    private static final @NotNull String OBSERVER_NAME = "observer";
    /**
     * The slot of a player id that no record holds.
     */
    private static final int NO_SLOT = -1;

    /**
     * Creates the game records of a lobby whose only player is the host in its slot.
     *
     * @return the created records, empty but for the host's
     */
    private static MeleeGameSetup.@NotNull PlayerRecord @NotNull [] createRecords() {
        MeleeGameSetup.PlayerRecord[] records = new MeleeGameSetup.PlayerRecord[RECORDS];

        for (int index = 0; index < RECORDS; index++) {
            records[index] = new MeleeGameSetup.PlayerRecord(-1, PlayerTypes.NONE, 0, 0, "");
        }

        records[HOST_SLOT] = new MeleeGameSetup.PlayerRecord(HOST_PLAYER, PlayerTypes.HUMAN, 0, 0, HOST_NAME);

        return records;
    }

    @Test
    void update_should_map_a_player_who_joined_again_to_the_slot_it_holds_and_not_the_released_one() {
        MeleeGameSetup.PlayerRecord[] records = createRecords();
        PlayerSlots slots = new PlayerSlots();

        records[RELEASED_SLOT] = new MeleeGameSetup.PlayerRecord(JOINER_PLAYER, PlayerTypes.EITHER_PREFER_HUMAN, 0, 0, JOINER_NAME);
        records[JOINER_SLOT]   = new MeleeGameSetup.PlayerRecord(JOINER_PLAYER, PlayerTypes.HUMAN, 0, 0, JOINER_NAME);
        records[OBSERVER_SLOT] = new MeleeGameSetup.PlayerRecord(OBSERVER_PLAYER, PlayerTypes.OBSERVER, 0, 0, OBSERVER_NAME);

        slots.update(records, RECORDS);

        Assertions.assertEquals(HOST_SLOT, slots.getSlot(HOST_PLAYER));
        Assertions.assertEquals(JOINER_SLOT, slots.getSlot(JOINER_PLAYER));
        Assertions.assertEquals(OBSERVER_SLOT, slots.getSlot(OBSERVER_PLAYER));
        Assertions.assertEquals(NO_SLOT, slots.getSlot(ABSENT_PLAYER));
    }

    @Test
    void update_should_map_a_player_to_its_last_record() {
        MeleeGameSetup.PlayerRecord[] records = createRecords();
        PlayerSlots slots = new PlayerSlots();

        records[JOINER_SLOT]   = new MeleeGameSetup.PlayerRecord(JOINER_PLAYER, PlayerTypes.HUMAN, 0, 0, JOINER_NAME);
        records[OBSERVER_SLOT] = new MeleeGameSetup.PlayerRecord(JOINER_PLAYER, PlayerTypes.OBSERVER, 0, 0, JOINER_NAME);

        slots.update(records, RECORDS);

        Assertions.assertEquals(OBSERVER_SLOT, slots.getSlot(JOINER_PLAYER));
    }

    @Test
    void update_should_scan_only_the_records_that_can_hold_a_player() {
        MeleeGameSetup.PlayerRecord[] records = createRecords();
        PlayerSlots slots = new PlayerSlots();

        records[FIRST_GAME_RECORD_AFTER_PLAYERS] = new MeleeGameSetup.PlayerRecord(JOINER_PLAYER, PlayerTypes.HUMAN, 0, 0, JOINER_NAME);

        slots.update(records, PlayerTable.PLAYABLE_PLAYER_SLOTS);

        Assertions.assertEquals(NO_SLOT, slots.getSlot(JOINER_PLAYER));
        Assertions.assertEquals(HOST_SLOT, slots.getSlot(HOST_PLAYER));
    }

    @Test
    void is_player_identifier_should_accept_only_player_and_observer_ids() {
        int lastObserver = PlayerTable.FIRST_OBSERVER_SLOT + PlayerTable.OBSERVER_SLOTS - 1;

        Assertions.assertTrue(PlayerSlots.isPlayerIdentifier(HOST_PLAYER));
        Assertions.assertTrue(PlayerSlots.isPlayerIdentifier(PlayerTable.PLAYABLE_PLAYER_SLOTS - 1));
        Assertions.assertTrue(PlayerSlots.isPlayerIdentifier(PlayerTable.FIRST_OBSERVER_SLOT));
        Assertions.assertTrue(PlayerSlots.isPlayerIdentifier(lastObserver));

        Assertions.assertFalse(PlayerSlots.isPlayerIdentifier(-1));
        Assertions.assertFalse(PlayerSlots.isPlayerIdentifier(PlayerTable.PLAYABLE_PLAYER_SLOTS), "the ids 8..11 name no one");
        Assertions.assertFalse(PlayerSlots.isPlayerIdentifier(RECORDS - 1));
        Assertions.assertFalse(PlayerSlots.isPlayerIdentifier(lastObserver + 1));
    }

    @Test
    void remove_should_leave_a_departed_player_without_a_slot_until_the_next_update() {
        MeleeGameSetup.PlayerRecord[] records = createRecords();
        PlayerSlots slots = new PlayerSlots();

        records[JOINER_SLOT] = new MeleeGameSetup.PlayerRecord(JOINER_PLAYER, PlayerTypes.HUMAN, 0, 0, JOINER_NAME);

        slots.update(records, RECORDS);
        slots.remove(JOINER_PLAYER);

        Assertions.assertEquals(NO_SLOT, slots.getSlot(JOINER_PLAYER));
        Assertions.assertEquals(HOST_SLOT, slots.getSlot(HOST_PLAYER));
    }
}
