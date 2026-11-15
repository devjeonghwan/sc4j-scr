package org.fogofwarlabs.sc4j.scr.client.ui.console;

import org.fogofwarlabs.sc4j.scr.client.ui.dialog.ScrDialog;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.ScrDialogLayer;
import org.jetbrains.annotations.NotNull;

/**
 * One in-game console dialog ({@code statbtn}, {@code statdata}, {@code statres}, {@code statport}, {@code minimap},
 * {@code stat_f10}) as an open dialog of the shared dialog list: its runtime state and its controls in interface
 * coordinates. The console dialogs are not modal; what none of them takes goes to the game view.
 */
public final class ConsoleDialog implements ScrDialogLayer {
    private final @NotNull DialogLayout layout;

    private final @NotNull ScrDialog dialog;

    /**
     * Creates a {@link ConsoleDialog} instance.
     *
     * @param layout the dialog's controls in interface coordinates
     */
    public ConsoleDialog(@NotNull DialogLayout layout) {
        this.layout = layout;

        this.dialog = new ScrDialog(layout.getDefinition());
    }

    /**
     * @return the dialog's controls in interface coordinates
     */
    public @NotNull DialogLayout getLayout() {
        return this.layout;
    }

    /**
     * @return the dialog's runtime state
     */
    @Override
    public @NotNull ScrDialog getDialog() {
        return this.dialog;
    }

    /**
     * @return the dialog's left edge in interface pixels
     */
    @Override
    public float getLeft() {
        return this.layout.getX();
    }

    /**
     * @return the dialog's top edge in interface pixels
     */
    @Override
    public float getTop() {
        return this.layout.getY();
    }

    /**
     * @return {@code false} because console dialogs allow unclaimed input to reach the game view
     */
    @Override
    public boolean isModal() {
        return false;
    }
}
