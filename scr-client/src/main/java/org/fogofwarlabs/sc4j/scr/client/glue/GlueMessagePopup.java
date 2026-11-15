package org.fogofwarlabs.sc4j.scr.client.glue;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Shows a centered message popup over the current screen without dimming its background.
 * <p>
 * Layouts provide OK, OK and Cancel, or a title with a detail line. The selected layout uses the screen palette panel.
 */
public final class GlueMessagePopup extends GluePopup {
    private static final int OK = 1;
    private static final int TEXT = 2;
    private static final int CANCEL = 3;
    private static final int DETAIL = 5;

    /**
     * The message box layouts.
     */
    public enum Kind {
        /**
         * A message with an OK button.
         */
        OK("gluPOk", "pOPopup.pcx"),
        /**
         * A message with OK and Cancel buttons.
         */
        OK_CANCEL("gluPOkCancel", "pDPopup.pcx"),
        /**
         * A title and detail line with an OK button.
         */
        OK_SPLIT("gluPOkSplit", "pOPopup.pcx");

        private final @NotNull String dialogName;
        private final @NotNull String panelName;

        Kind(@NotNull String dialogName, @NotNull String panelName) {
            this.dialogName = dialogName;
            this.panelName  = panelName;
        }
    }

    private final @NotNull Kind kind;

    private final @NotNull String text;
    private final @NotNull String detail;

    private final @NotNull Consumer<Boolean> result;

    /**
     * Creates a {@link GlueMessagePopup} instance.
     *
     * @param kind          the layout
     * @param paletteFolder the palette folder of the screen below, for example {@code glue/palnl}
     * @param text          the message (the title of {@link Kind#OK_SPLIT})
     * @param detail        the detail line of {@link Kind#OK_SPLIT}, or {@code null}
     * @param result        receives {@code true} for Ok and {@code false} for Cancel when the box closes
     */
    public GlueMessagePopup(@NotNull Kind kind,
                            @NotNull String paletteFolder,
                            @NotNull String text,
                            @Nullable String detail,
                            @NotNull Consumer<Boolean> result) {
        super(kind.dialogName, paletteFolder, paletteFolder + "/" + kind.panelName);

        this.kind = kind;

        this.text   = text;
        this.detail = detail == null ? "" : detail;

        this.result = result;
    }

    /**
     * Called after the dialog is loaded: hide, disable or fill controls here.
     *
     * @param glueContext the shared services
     */
    @Override
    protected void onOpen(@NotNull GlueContext glueContext) {
        this.bindControl(TEXT).setReplacedText(this.text);

        if (this.kind == Kind.OK_SPLIT) {
            this.bindControl(DETAIL).setReplacedText(this.detail);
        }
    }

    /**
     * Called when a control is activated by a click, its hotkey, Esc or Enter.
     *
     * @param control the control id
     */
    @Override
    protected void onActivate(int control) {
        if (control != OK && control != CANCEL) {
            return;
        }

        this.finish();
        this.result.accept(control == OK);
    }
}
