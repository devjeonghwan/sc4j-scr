package org.fogofwarlabs.sc4j.scr.client.glue.popup;

import org.fogofwarlabs.sc4j.scr.client.glue.GlueContext;
import org.fogofwarlabs.sc4j.scr.client.glue.GluePopup;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.EditControl;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.InputMethod;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.ScrControl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Shows a text entry popup that rejects the fixed and caller-supplied character sets; OK requires nonempty text. The popup binds its
 * OK button and its text box once at open.
 */
public final class GlueEditPopup extends GluePopup {
    private static final @NotNull String DIALOG = "gluPEdit";
    private static final int OK = 1;
    private static final int PROMPT = 2;
    private static final int CANCEL = 3;
    private static final int EDIT = 4;
    private static final @NotNull String PANEL = "pEPopup.pcx";
    private static final @NotNull String FOLDER_SEPARATOR = "/";
    /**
     * The characters the text box always refuses, before the caller-supplied ones; a field whose text the popup asks for again refuses them too.
     */
    public static final @NotNull String ALWAYS_REJECTED = "*\\/:><|&+^?-";
    private static final @NotNull String EMPTY_TEXT = "";

    private final @NotNull String prompt;
    private final int maximumLength;
    private final @NotNull String rejected;

    private final @NotNull Consumer<@Nullable String> result;

    private @Nullable ScrControl okButton;
    private @Nullable EditControl textBox;

    /**
     * Creates a {@link GlueEditPopup} instance.
     *
     * @param paletteFolder the palette folder of the screen below
     * @param prompt        the prompt
     * @param maximumLength the maximum number of characters
     * @param rejected      the characters refused besides the fixed set
     * @param result        receives the text on Ok, or {@code null} on Cancel
     */
    public GlueEditPopup(@NotNull String paletteFolder,
                         @NotNull String prompt, int maximumLength, @NotNull String rejected,
                         @NotNull Consumer<@Nullable String> result) {
        super(DIALOG, paletteFolder, paletteFolder + FOLDER_SEPARATOR + PANEL);

        this.prompt        = prompt;
        this.maximumLength = maximumLength;
        this.rejected      = ALWAYS_REJECTED + rejected;

        this.result = result;

        this.okButton = null;
        this.textBox  = null;
    }

    /**
     * Called after the dialog is loaded: hide, disable or fill controls here.
     * <p>
     * The popup shows the prompt, empties the text box, disables OK and starts text input.
     *
     * @param glueContext the shared services
     */
    @Override
    protected void onOpen(@NotNull GlueContext glueContext) {
        ScrControl ok = this.bindControl(OK);

        this.bindControl(PROMPT).setReplacedText(this.prompt);
        this.textBox = this.setEditText(EDIT, EMPTY_TEXT, this.maximumLength, this.rejected);
        ok.setEnabled(false);
        this.okButton = ok;

        // Text input starts before the modal dialog runs.
        InputMethod.setTextInput(true);
    }


    /**
     * Called after the text of an {@code EDITTEXT} changed.
     * <p>
     * OK is enabled while the text box holds text.
     *
     * @param control the control id
     */
    @Override
    protected void onEditChanged(int control) {
        ScrControl ok = this.okButton;

        if (ok != null) {
            ok.setEnabled(!this.getTypedText().isEmpty());
        }
    }

    private @NotNull String getTypedText() {
        EditControl box = this.textBox;

        return box == null ? EMPTY_TEXT : box.getEditText().getText();
    }

    /**
     * Called when a control is activated by a click, its hotkey, Esc or Enter.
     *
     * @param control the control id
     */
    @Override
    protected void onActivate(int control) {
        if (control == OK) {
            this.finish();
            this.result.accept(this.getTypedText());
        } else if (control == CANCEL) {
            this.finish();
            this.result.accept(null);
        }
    }


    /**
     * Releases the videos and the popup.
     * <p>
     * Text input ends after the inherited resources are released.
     */
    @Override
    public void close() {
        super.close();

        // Text input ends after the dialog closes; the screen below receives none.
        InputMethod.setTextInput(false);
    }
}
