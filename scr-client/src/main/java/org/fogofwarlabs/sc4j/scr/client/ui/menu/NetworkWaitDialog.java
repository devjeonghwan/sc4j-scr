package org.fogofwarlabs.sc4j.scr.client.ui.menu;

import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogDefinition;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.ScrDialog;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrDialogImportUtils;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.presentation.TimerTextUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.LocalInterfaceState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * Updates the waiting-for-players dialog from the transport countdown and overdue player names.
 * While open it publishes the countdown to local interface state. Closing after a wait records the supplied host clock.
 * Drop Players is enabled at zero; the caller handles its action.
 */
public final class NetworkWaitDialog {
    /**
     * The dialog file.
     */
    public static final @NotNull String FILE = "timeout";
    /**
     * Drop Players.
     */
    public static final int DROP = 2;
    private static final int COUNTDOWN = -30;
    private static final int FIRST_NAME = -10;
    private static final int NAME_LINES = PlayerTable.PLAYER_SLOTS;

    private final @NotNull ScrDialog dialog;
    private boolean open;

    /**
     * Creates a {@link NetworkWaitDialog} instance.
     *
     * @param storage the game storage
     * @throws IOException if the dialog cannot be read
     */
    public NetworkWaitDialog(@NotNull CascStorage storage) throws IOException {
        this(ScrDialogImportUtils.load(storage, FILE));
    }

    /**
     * Creates a {@link NetworkWaitDialog} instance over a loaded definition.
     *
     * @param definition the {@code timeout} dialog
     */
    NetworkWaitDialog(@NotNull DialogDefinition definition) {
        this.dialog = new ScrDialog(definition);
        this.open   = false;
    }

    /**
     * @return the dialog while it is open, else {@code null}
     */
    public @Nullable ScrDialog getOpenDialog() {
        return this.open ? this.dialog : null;
    }

    /**
     * Follows the transport's waiting state once per frame.
     *
     * @param countdown the countdown in seconds, {@code -1} while turns arrive
     * @param players   the names of the players waited for
     * @param local     the game's local interface state
     * @param hostClock the host clock in milliseconds
     */
    public void update(int countdown, @NotNull List<String> players, @NotNull LocalInterfaceState local, int hostClock) {
        if (countdown < 0) {
            if (this.open) {
                local.setStallStamp(hostClock);
            }

            this.open = false;

            return;
        }

        this.open = true;

        local.setStallCountdown(countdown);

        for (int line = 0; line < NAME_LINES; line++) {
            this.dialog.requireControl(FIRST_NAME - line).setReplacedText(line < players.size() ? players.get(line) : "");
        }

        this.dialog.requireControl(COUNTDOWN).setReplacedText(TimerTextUtils.formatCountdown(countdown));
        this.dialog.requireControl(COUNTDOWN).setVisible(countdown != 0);
        this.dialog.requireControl(DROP).setEnabled(countdown == 0);
    }
}
