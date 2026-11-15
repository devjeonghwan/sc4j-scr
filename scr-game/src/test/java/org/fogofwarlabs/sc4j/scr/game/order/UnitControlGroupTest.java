package org.fogofwarlabs.sc4j.scr.game.order;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks how the hotkey records store, append, recall and prune the unit handles of the control groups of a player, and how the selection-history
 * records are recalled, stamped and overflow into the control-group records.
 */
@RequiresInstallation
final class UnitControlGroupTest {
    private static final int PRIMARY_CONTROL_GROUP = 4;
    private static final int FULL_CONTROL_GROUP = 2;
    private static final int SUCCESSOR_CONTROL_GROUP = FULL_CONTROL_GROUP + 1;
    private static final int EMPTY_CONTROL_GROUP = 9;
    private static final int LAST_HISTORY_INDEX = 7;
    private static final int NEXT_PLAYER_RECORD = 18;
    private static final int FIRST_IGNORED_RECORD = 19;
    private static final int MAXIMUM_RECORD_BYTE = 0xFF;

    private static final int HOTKEY_SET = 0;
    private static final int HOTKEY_RECALL = 1;
    private static final int HOTKEY_APPEND = 2;
    private static final int FIRST_UNKNOWN_OPERATION = 3;
    private static final int UNKNOWN_OPERATION_BYTE = 0xFF;

    /**
     * The owner of the foreign unit of the filter test.
     */
    private static final int FOREIGN_OWNER = 1;

    /**
     * The hotkey record BYTE of the first selection-history record.
     */
    private static final int FIRST_HISTORY_RECORD = 10;
    /**
     * The selection-history records of a player.
     */
    private static final int HISTORY_COUNT = 8;
    private static final int LAST_HISTORY_RECORD = FIRST_HISTORY_RECORD + HISTORY_COUNT - 1;
    private static final int LAST_PLAYER_INDEX = 7;

    /**
     * The turn count one past the WORD range, whose history stamp wraps to {@code 1}.
     */
    private static final int TURNS_PAST_WORD = 0x10001;
    private static final int INITIAL_TURNS = 5;
    private static final int ADDITIONAL_TURNS = 4;
    /**
     * The history stamp of a record recalled after {@link #INITIAL_TURNS} and {@link #ADDITIONAL_TURNS} turn steps.
     */
    private static final int RECALLED_HISTORY_STAMP = 9;
    /**
     * The history stamps of the selections of the turn {@link #TURNS_PAST_WORD} and of the next turn.
     */
    private static final int WRAPPED_HISTORY_STAMP = 1;
    private static final int NEXT_TURN_HISTORY_STAMP = 2;

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }

    private static int findHistoryRecord(@NotNull Game game, int poolIndex) {
        UnitSelection selection = game.getSelection();
        UnitPool handlePool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(selection);
        Assertions.assertNotNull(handlePool);

        for (int history = 0; history < HISTORY_COUNT; history++) {
            if (selection.getHistoryHandle(0, history, 0) == handlePool.getHandle(poolIndex)) {
                return history;
            }
        }

        return -1;
    }


    @Test
    void execute_control_group_should_recall_stored_handles_and_prune_dead_members_by_swapping_last() {
        Game game = ControlGroupScenario.createState(gameData);
        UnitSelection selection = game.getSelection();

        Assertions.assertNotNull(selection);

        ControlGroupScenario.selectUnits(game, 1, 2, 3);

        Assertions.assertTrue(selection.executeControlGroup(game, 0, PRIMARY_CONTROL_GROUP, HOTKEY_SET));

        ControlGroupScenario.selectUnits(game, 4);

        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        unitPool.getUnit(1).setPrimaryOrder(ScrOrderIds.DIE);
        unitPool.getUnit(1).setPrimaryOrderState(Unit.DIE_STATE_DESTROYED);

        Assertions.assertTrue(selection.executeControlGroup(game, 0, PRIMARY_CONTROL_GROUP, HOTKEY_RECALL));

        Assertions.assertEquals(3, selection.getSelectedPoolIndex(0, 0));
        Assertions.assertEquals(2, selection.getSelectedPoolIndex(0, 1));
        Assertions.assertEquals(0, selection.getSelectedPoolIndex(0, 2));

        UnitPool handlePool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(handlePool);

        Assertions.assertEquals(handlePool.getHandle(3), selection.getControlGroupHandle(0, PRIMARY_CONTROL_GROUP, 0));
        Assertions.assertEquals(0, selection.getControlGroupHandle(0, PRIMARY_CONTROL_GROUP, 2));
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void remove_unit_should_compact_selection_and_defer_control_group_pruning() {
        Game game = ControlGroupScenario.createState(gameData);
        UnitSelection selection = game.getSelection();

        Assertions.assertNotNull(selection);

        ControlGroupScenario.selectUnits(game, 1, 2, 3);

        selection.executeControlGroup(game, 0, 0, HOTKEY_SET);

        int storedHandle = selection.getControlGroupHandle(0, 0, 1);
        int history = selection.getHistoryHandle(0, LAST_HISTORY_INDEX, 0);
        UnitPool removedPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(removedPool);

        Unit removed = removedPool.getUnit(2);

        selection.removeUnit(game, removed);

        Assertions.assertEquals(1, selection.getSelectedPoolIndex(0, 0));
        Assertions.assertEquals(3, selection.getSelectedPoolIndex(0, 1));
        Assertions.assertEquals(0, selection.getSelectedPoolIndex(0, 2));
        Assertions.assertEquals(storedHandle, selection.getControlGroupHandle(0, 0, 1));
        Assertions.assertEquals(history, selection.getHistoryHandle(0, LAST_HISTORY_INDEX, 0));

        selection.removeUnit(game, removed);

        Assertions.assertEquals(3, selection.getSelectedPoolIndex(0, 1));

        removed.setPrimaryOrder(ScrOrderIds.DIE);
        removed.setPrimaryOrderState(Unit.DIE_STATE_DESTROYED);

        selection.executeControlGroup(game, 0, 0, HOTKEY_RECALL);

        Assertions.assertEquals(1, selection.getSelectedPoolIndex(0, 0));
        Assertions.assertEquals(3, selection.getSelectedPoolIndex(0, 1));
        Assertions.assertEquals(0, selection.getSelectedPoolIndex(0, 2));

        Assertions.assertThrows(IllegalArgumentException.class, () -> selection.removeUnit(game, UnitTestUtils.createUnit(1)));
    }

    @Test
    void execute_control_group_should_append_without_duplicates_and_overflow_into_next_record() {
        Game game = ControlGroupScenario.createState(gameData);
        UnitSelection selection = game.getSelection();

        Assertions.assertNotNull(selection);

        ControlGroupScenario.selectUnits(game, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);

        Assertions.assertTrue(selection.executeControlGroup(game, 0, FULL_CONTROL_GROUP, HOTKEY_SET));

        ControlGroupScenario.selectUnits(game, 1, 13);

        // Appending to a full group stores the handle in the next record's first slot.
        Assertions.assertTrue(selection.executeControlGroup(game, 0, FULL_CONTROL_GROUP, HOTKEY_APPEND));

        UnitPool handlePool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(handlePool);

        Assertions.assertEquals(handlePool.getHandle(13), selection.getControlGroupHandle(0, SUCCESSOR_CONTROL_GROUP, 0));

        Assertions.assertTrue(selection.executeControlGroup(game, 0, SUCCESSOR_CONTROL_GROUP, HOTKEY_RECALL));

        Assertions.assertEquals(13, selection.getSelectedPoolIndex(0, 0));

        Assertions.assertFalse(selection.executeControlGroup(game, 0, EMPTY_CONTROL_GROUP, HOTKEY_RECALL));

        Assertions.assertEquals(13, selection.getSelectedPoolIndex(0, 0));
    }

    @Test
    void execute_control_group_should_filter_hidden_and_foreign_units_and_record_recalled_selection() {
        Game game = ControlGroupScenario.createState(gameData);
        UnitSelection selection = game.getSelection();

        Assertions.assertNotNull(selection);

        ControlGroupScenario.selectUnits(game, 1, 2, 3);

        Assertions.assertTrue(selection.executeControlGroup(game, 0, 0, HOTKEY_SET));

        UnitPool hiddenPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(hiddenPool);

        Sprite spriteToHide = hiddenPool.getUnit(1).getSpriteOrNull();

        Assertions.assertNotNull(spriteToHide);

        spriteToHide.setFlags(Sprite.FLAG_HIDDEN);

        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        unitPool.getUnit(2).setOwnerIndex(FOREIGN_OWNER);

        Assertions.assertTrue(selection.executeControlGroup(game, 0, 0, HOTKEY_RECALL));

        Assertions.assertEquals(3, selection.getSelectedPoolIndex(0, 0));
        Assertions.assertEquals(0, selection.getSelectedPoolIndex(0, 1));

        // Recall replaces the oldest history record, searching from the last record.
        UnitPool handlePool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(handlePool);

        Assertions.assertEquals(handlePool.getHandle(3), selection.getHistoryHandle(0, LAST_HISTORY_INDEX, 0));
        Assertions.assertEquals(0, selection.getHistoryHandle(0, LAST_HISTORY_INDEX, 1));
    }

    /**
     * Checks ignored record and operation bytes and the next-player record boundary.
     */
    @Test
    void execute_control_group_should_ignore_records_above_eighteen_and_unknown_operations() {
        Game game = ControlGroupScenario.createState(gameData);
        UnitSelection selection = game.getSelection();

        Assertions.assertNotNull(selection);

        ControlGroupScenario.selectUnits(game, 1, 2, 3);

        long before = selection.computeStateHash();

        Assertions.assertFalse(selection.executeControlGroup(game, 0, FIRST_IGNORED_RECORD, HOTKEY_SET));
        Assertions.assertFalse(selection.executeControlGroup(game, 0, MAXIMUM_RECORD_BYTE, HOTKEY_RECALL));
        Assertions.assertFalse(selection.executeControlGroup(game, 0, PRIMARY_CONTROL_GROUP, FIRST_UNKNOWN_OPERATION));
        Assertions.assertFalse(selection.executeControlGroup(game, 0, PRIMARY_CONTROL_GROUP, UNKNOWN_OPERATION_BYTE));

        Assertions.assertEquals(before, selection.computeStateHash());

        Assertions.assertTrue(selection.executeControlGroup(game, 0, NEXT_PLAYER_RECORD, HOTKEY_SET));

        UnitPool handlePool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(handlePool);

        Assertions.assertEquals(handlePool.getHandle(1), selection.getControlGroupHandle(1, 0, 0));
    }

    @Test
    void execute_control_group_should_recall_and_stamp_history_record_and_ignore_empty_ones() {
        Game game = ControlGroupScenario.createState(gameData);
        UnitSelection selection = game.getSelection();

        Assertions.assertNotNull(selection);

        RunningGameScenario.advanceTurns(game, INITIAL_TURNS);
        ControlGroupScenario.selectUnits(game, 1, 2, 3);

        int stored = -1;

        for (int history = 0; history < HISTORY_COUNT && stored < 0; history++) {
            if (selection.getHistoryHandle(0, history, 0) != 0) {
                stored = history;
            }
        }

        Assertions.assertTrue(stored >= 0, "A multi-unit selection is recorded in the history.");

        ControlGroupScenario.selectUnits(game, 4);
        RunningGameScenario.advanceTurns(game, ADDITIONAL_TURNS);

        Assertions.assertTrue(selection.executeControlGroup(game, 0, FIRST_HISTORY_RECORD + stored, HOTKEY_RECALL),
                              "Record 10 + h recalls history record h.");

        Assertions.assertEquals(1, selection.getSelectedPoolIndex(0, 0));
        Assertions.assertEquals(2, selection.getSelectedPoolIndex(0, 1));
        Assertions.assertEquals(3, selection.getSelectedPoolIndex(0, 2));
        Assertions.assertEquals(RECALLED_HISTORY_STAMP, selection.getHistoryTimestamp(0, stored), "Recalling a history record stamps it.");

        int empty = stored == 0 ? 1 : 0;

        Assertions.assertFalse(selection.executeControlGroup(game, 0, FIRST_HISTORY_RECORD + empty, HOTKEY_RECALL),
                               "An empty record recalls nothing.");
    }

    @Test
    void selection_history_stamps_the_low_word_of_the_turn_count_and_keeps_turns_apart() {
        Game game = ControlGroupScenario.createState(gameData);
        UnitSelection selection = game.getSelection();

        Assertions.assertNotNull(selection);

        RunningGameScenario.advanceTurns(game, TURNS_PAST_WORD);
        ControlGroupScenario.selectUnits(game, 1, 2, 3);
        game.incrementTurnCount();
        ControlGroupScenario.selectUnits(game, 4, 5);

        int older = findHistoryRecord(game, 1);
        int newer = findHistoryRecord(game, 4);

        Assertions.assertTrue(older >= 0 && newer >= 0, "Both multi-unit selections are recorded in the history.");
        Assertions.assertNotEquals(older, newer, "Selections of different turns take different history records.");
        Assertions.assertEquals(WRAPPED_HISTORY_STAMP, selection.getHistoryTimestamp(0, older), "A stamp is the low WORD of the turn count.");
        Assertions.assertEquals(NEXT_TURN_HISTORY_STAMP, selection.getHistoryTimestamp(0, newer));

        Assertions.assertTrue(selection.executeControlGroup(game, 0, FIRST_HISTORY_RECORD + older, HOTKEY_RECALL));

        Assertions.assertEquals(1, selection.getSelectedPoolIndex(0, 0));
        Assertions.assertEquals(2, selection.getSelectedPoolIndex(0, 1));
        Assertions.assertEquals(3, selection.getSelectedPoolIndex(0, 2));
    }

    @Test
    void execute_control_group_should_set_and_append_history_record_without_duplicates() {
        Game game = ControlGroupScenario.createState(gameData);
        UnitSelection selection = game.getSelection();

        Assertions.assertNotNull(selection);

        ControlGroupScenario.selectUnits(game, 5);

        Assertions.assertTrue(selection.executeControlGroup(game, 0, LAST_HISTORY_RECORD, HOTKEY_SET));

        UnitPool handlePool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(handlePool);

        Assertions.assertEquals(handlePool.getHandle(5), selection.getHistoryHandle(0, LAST_HISTORY_INDEX, 0));

        ControlGroupScenario.selectUnits(game, 6);

        Assertions.assertTrue(selection.executeControlGroup(game, 0, LAST_HISTORY_RECORD, HOTKEY_APPEND));

        Assertions.assertEquals(handlePool.getHandle(6), selection.getHistoryHandle(0, LAST_HISTORY_INDEX, 1));

        Assertions.assertFalse(selection.executeControlGroup(game, 0, LAST_HISTORY_RECORD, HOTKEY_APPEND),
                               "Appending a stored unit again changes nothing.");

        Assertions.assertTrue(selection.executeControlGroup(game, 0, NEXT_PLAYER_RECORD, HOTKEY_SET), "Record 18 of player 0 is player 1's group 0.");

        Assertions.assertEquals(handlePool.getHandle(6), selection.getControlGroupHandle(1, 0, 0));

        Assertions.assertThrows(UnsupportedOperationException.class,
                                () -> selection.executeControlGroup(game, LAST_PLAYER_INDEX, NEXT_PLAYER_RECORD, HOTKEY_RECALL),
                                "Player 7's record 18 lies past the hotkey records (+0x2870).");
    }
}
