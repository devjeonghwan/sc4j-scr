package org.fogofwarlabs.sc4j.scr.client.ui.command;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/**
 * Tracks command-button hotkey presses and mouse holds.
 * Hotkey actions run when the {@code 20} ms release timer expires; pressing an already held key activates it immediately.
 * A second hotkey while another timer is pending stays pressed until pressed again.
 * A card refresh cancels a press when its slot changes or disappears, so releases cannot activate a replacement button.
 * Mouse release activates a button only when its press remains valid and the pointer is still over the button.
 */
public final class HotkeyPress {
    /**
     * The hotkey release delay in milliseconds.
     */
    public static final int RELEASE_DELAY_MILLISECONDS = 20;

    private final boolean @NotNull [] pressed;
    private final int @NotNull [] pressCancellations;

    /**
     * The command button the left mouse button holds pressed, or {@code -1}, and its slot's cancellation count then.
     */
    private int mouseSlot;
    private int mouseCancellations;
    private int timerSlot;
    private long deadline;
    private boolean timerQueued;
    private boolean releasedQueued;

    /**
     * Creates an empty {@link HotkeyPress} instance.
     */
    public HotkeyPress() {
        this.pressed            = new boolean[CommandCard.SLOT_COUNT];
        this.pressCancellations = new int[CommandCard.SLOT_COUNT];

        this.mouseSlot          = -1;
        this.mouseCancellations = 0;
        this.timerSlot          = -1;
        this.deadline           = 0;
        this.timerQueued        = false;
        this.releasedQueued     = false;
    }

    /**
     * @param cancellations the press cancellation count of each slot of the current card
     * @param slot          the slot
     * @return {@code true} if the mouse still holds the slot with an unchanged cancellation count, {@code false} otherwise
     */
    public boolean isMousePressKept(@NotNull IntUnaryOperator cancellations, int slot) {
        return slot >= 0 && slot == this.mouseSlot && cancellations.applyAsInt(slot) == this.mouseCancellations;
    }

    /**
     * @return {@code true} if the last released or re-pressed button was pressed with Shift, {@code false} otherwise
     */
    public boolean isReleasedQueued() {
        return this.releasedQueued;
    }

    /**
     * @param slot the command card slot
     * @return {@code true} if the slot is valid and its button is held by its hotkey, {@code false} otherwise
     */
    public boolean isPressed(int slot) {
        return slot >= 0 && slot < this.pressed.length && this.pressed[slot];
    }


    /**
     * Presses a button by its hotkey.
     *
     * @param cancellations the press cancellation count of each slot of the card ({@link CommandCard#getPressCancellations})
     * @param slot          the matched slot
     * @param queued        whether Shift is held
     * @param now           the millisecond clock
     * @return the slot to activate now (a second press while pressed), or {@code -1}
     */
    public int press(@NotNull IntUnaryOperator cancellations, int slot, boolean queued, long now) {
        this.cancelChanged(cancellations);

        if (this.pressed[slot]) {
            this.pressed[slot] = false;

            if (this.timerSlot == slot) {
                this.timerSlot = -1;
            }

            this.releasedQueued = queued;

            return slot;
        }

        this.pressed[slot]            = true;
        this.pressCancellations[slot] = cancellations.applyAsInt(slot);

        if (this.timerSlot < 0) {
            this.timerSlot   = slot;
            this.deadline    = now + RELEASE_DELAY_MILLISECONDS;
            this.timerQueued = queued;
        }

        return -1;
    }

    /**
     * Runs the release poll.
     *
     * @param cancellations the press cancellation count of each slot of the current card
     * @param now           the millisecond clock
     * @return the released slot to activate, or {@code -1}
     */
    public int poll(@NotNull IntUnaryOperator cancellations, long now) {
        this.cancelChanged(cancellations);

        if (this.timerSlot < 0 || this.deadline - now >= 0) {
            return -1;
        }

        int slot = this.timerSlot;

        this.timerSlot      = -1;
        this.pressed[slot]  = false;
        this.releasedQueued = this.timerQueued;

        return slot;
    }

    /**
     * Clears hotkey presses whose cancellation count changed and cancels their pending release timer.
     */
    private void cancelChanged(@NotNull IntUnaryOperator cancellations) {
        for (int slot = 0; slot < this.pressed.length; slot++) {
            if (!this.pressed[slot] || cancellations.applyAsInt(slot) == this.pressCancellations[slot]) {
                continue;
            }

            this.pressed[slot] = false;

            if (this.timerSlot == slot) {
                this.timerSlot = -1;
            }
        }
    }

    /**
     * Stores the pressed mouse slot and its current cancellation count without activating the button.
     *
     * @param cancellations the press cancellation count of each slot of the card ({@link CommandCard#getPressCancellations})
     * @param slot          the pressed slot
     */
    public void pressMouse(@NotNull IntUnaryOperator cancellations, int slot) {
        this.mouseSlot          = slot;
        this.mouseCancellations = cancellations.applyAsInt(slot);
    }

    /**
     * Releases the mouse hold. The caller activates the slot only when {@link #isMousePressKept} held and the pointer is still over the button.
     */
    public void releaseMouse() {
        this.mouseSlot = -1;
    }


    /**
     * Releases every button without activating it (the command card changed).
     */
    public void clear() {
        Arrays.fill(this.pressed, false);

        this.timerSlot = -1;
    }
}
