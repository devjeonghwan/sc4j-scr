package org.fogofwarlabs.sc4j.scr.client.ui.menu;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Retains the pending chat recipient and player-list selection while the messaging dialog is open.
 * Accept commits the selected recipient; cancel only closes the dialog.
 * Observers cannot change recipient controls, and ally selection requires alliances to be enabled.
 */
public final class MessagingDialog {
    /**
     * Radio button "Send to player".
     */
    public static final int PLAYER = 1;
    /**
     * Radio button "Send to everyone".
     */
    public static final int EVERYONE = 2;
    /**
     * Radio button "Send to allies".
     */
    public static final int ALLIES = 3;
    /**
     * The player list (COMBOBOX).
     */
    public static final int LIST = 4;
    /**
     * Accept.
     */
    public static final int ACCEPT = -2;
    /**
     * Cancel.
     */
    public static final int CANCEL = -3;
    /**
     * The recipient byte of every player.
     */
    public static final int RECIPIENT_ALL = 8;
    /**
     * The recipient byte of the allies.
     */
    public static final int RECIPIENT_ALLIES = 9;

    private final @NotNull List<VisionDialog.Row> rows;

    private boolean open;
    private int pending;
    private int listIndex;
    private boolean alliesEnabled;
    private boolean observer;

    /**
     * Creates a closed {@link MessagingDialog} instance.
     */
    public MessagingDialog() {
        this.rows = new ArrayList<>();

        this.open          = false;
        this.pending       = 0;
        this.listIndex     = 0;
        this.alliesEnabled = false;
        this.observer      = false;
    }

    /**
     * @return {@code true} if the dialog is open, {@code false} otherwise
     */
    public boolean isOpen() {
        return this.open;
    }

    /**
     * @return the list's entries
     */
    public @NotNull List<VisionDialog.Row> getRows() {
        return this.rows;
    }

    /**
     * @return the selected list index
     */
    public int getListIndex() {
        return this.listIndex;
    }

    /**
     * @param control a radio button
     * @return {@code true} if the radio button is checked, {@code false} otherwise
     */
    public boolean isChecked(int control) {
        return switch (control) {
            case PLAYER -> this.pending < RECIPIENT_ALL;
            case EVERYONE -> this.pending == RECIPIENT_ALL;
            case ALLIES -> this.pending == RECIPIENT_ALLIES;
            default -> false;
        };
    }

    /**
     * @param control a control
     * @return {@code true} if the control takes input, {@code false} otherwise
     */
    public boolean isEnabled(int control) {
        if (control == ACCEPT || control == CANCEL) {
            return true;
        }

        if (this.observer) {
            return false;
        }

        if (control == ALLIES) {
            return this.alliesEnabled;
        }

        return control != PLAYER && control != LIST || !this.rows.isEmpty();
    }


    /**
     * Opens the dialog.
     *
     * @param recipient     the current recipient byte
     * @param players       the list's entries (value in {@link VisionDialog.Row#getPlayer}), in list order
     * @param alliesEnabled whether the game allows alliances
     * @param observer      whether the local player observes
     */
    public void open(int recipient, @NotNull List<VisionDialog.Row> players, boolean alliesEnabled, boolean observer) {
        this.rows.clear();
        this.rows.addAll(players);

        this.pending       = recipient;
        this.alliesEnabled = alliesEnabled;
        this.observer      = observer;

        if (recipient < RECIPIENT_ALL) {
            for (int index = 0; index < this.rows.size(); index++) {
                if (this.rows.get(index).getPlayer() == recipient) {
                    this.listIndex = index;
                }
            }
        }

        this.listIndex = Math.clamp(this.listIndex, 0, Math.max(0, this.rows.size() - 1));
        this.open      = true;
    }

    /**
     * Selects the player, everyone or allies recipient when the control is enabled.
     *
     * @param control the radio button
     */
    public void select(int control) {
        if (!this.isEnabled(control)) {
            return;
        }

        if (control == PLAYER && !this.rows.isEmpty()) {
            this.pending = this.rows.get(this.listIndex).getPlayer();
        } else if (control == EVERYONE) {
            this.pending = RECIPIENT_ALL;
        } else if (control == ALLIES) {
            this.pending = RECIPIENT_ALLIES;
        }
    }

    /**
     * Selects a list entry, which also checks "Send to player".
     *
     * @param index the entry
     */
    public void selectListIndex(int index) {
        if (index < 0 || index >= this.rows.size() || !this.isEnabled(LIST)) {
            return;
        }

        this.listIndex = index;
        this.pending   = this.rows.get(index).getPlayer();
    }

    /**
     * Accepts and closes.
     *
     * @return the new recipient byte
     */
    public int accept() {
        if (this.pending < RECIPIENT_ALL && !this.rows.isEmpty()) {
            this.pending = this.rows.get(this.listIndex).getPlayer();
        }

        this.open = false;

        return this.pending;
    }


    /**
     * Closes without changes.
     */
    public void cancel() {
        this.open = false;
    }
}
