package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;

/**
 * Applies the recorded command that cancels a production queue entry.
 * <p>
 * A single selected, completed unit of the issuer may cancel an entry unless it is a Larva, Mutalisk, Hydralisk, Hatchery, Lair, Spire or Creep
 * Colony. Slot {@code 254} searches the last occupied entry and slot {@code 255} is ignored; other slots are passed to the queue cancellation
 * handler.
 */
public final class ProductionQueueCommands {
    /**
     * The recorded slot that addresses the last occupied queue entry.
     */
    private static final int LAST_OCCUPIED_SLOT = 254;
    /**
     * The recorded slot that cancels nothing.
     */
    private static final int IGNORED_SLOT = 255;
    /**
     * The mask of the recorded unsigned WORD slot.
     */
    private static final int WORD_MASK = 0xFFFF;

    private final @NotNull Game game;

    /**
     * Creates a {@link ProductionQueueCommands} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public ProductionQueueCommands(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Cancels a production entry of the issuer's single selected unit.
     *
     * @param owner            the issuing player
     * @param slot             the recorded unsigned WORD slot
     * @param instructionLimit the order script budget
     * @return {@code true} if an entry was canceled, {@code false} otherwise
     * @throws UnsupportedOperationException if selection storage is absent
     */
    public boolean cancelEntry(int owner, int slot, int instructionLimit) {
        UnitSelection selection = this.game.getSelection();

        if (selection == null) {
            throw Exceptions.newUnsupportedOperationException("Queue cancellation requires initialized selection storage.");
        }

        Unit unit = selection.getSelectedUnit(owner, 0);

        if (unit == null || selection.getSelectedUnit(owner, 1) != null || unit.getOwnerIndex() != owner ||
            (unit.getStatus() & UnitStatusFlags.COMPLETED) == 0) {
            return false;
        }

        switch (ScrUnitIds.requireValidOrAdded(this.game.getBaseType(unit))) {
            case ScrUnitIds.ZERG_LARVA, ScrUnitIds.ZERG_MUTALISK, ScrUnitIds.ZERG_HYDRALISK, ScrUnitIds.ZERG_HATCHERY, ScrUnitIds.ZERG_LAIR,
                 ScrUnitIds.ZERG_SPIRE, ScrUnitIds.ZERG_CREEP_COLONY -> {
                return false;
            }
            default -> {
            }
        }

        slot &= WORD_MASK;

        if (slot == IGNORED_SLOT) {
            return false;
        }

        int index = slot;

        if (slot == LAST_OCCUPIED_SLOT) {
            // The last occupied circular slot, searched from the last entry down.
            index = Unit.BUILD_QUEUE_SLOTS - 1;

            while (index >= 0 && unit.getBuildQueueUnitType((unit.getBuildQueueIndex() + index) % Unit.BUILD_QUEUE_SLOTS) == ScrUnitIds.NONE) {
                index--;
            }

            if (index < 0) {
                return false;
            }
        }

        this.game.getUnitLifecycle().cancelBuildQueueEntry(unit, index, instructionLimit);

        return true;
    }
}
