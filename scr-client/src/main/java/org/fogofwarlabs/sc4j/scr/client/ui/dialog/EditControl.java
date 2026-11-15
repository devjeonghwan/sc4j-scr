package org.fogofwarlabs.sc4j.scr.client.ui.dialog;

import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.jetbrains.annotations.NotNull;

/**
 * Holds the text of an SC:R {@code EDITTEXT} control: the {@link EditText} its screen types into. A screen attaches the edit control
 * once ({@link ScrDialog#attach}) and gives it a new text each time it sets the control's text, so the control object stays the one
 * the screen bound; the screen's procedure painter draws it, since the text inset, font and colour are the screen's.
 */
public final class EditControl extends ScrControl {
    private @NotNull EditText editText;

    /**
     * Creates a {@link EditControl} instance.
     *
     * @param definition the {@code EDITTEXT} definition
     * @param editText   the text the control edits
     */
    public EditControl(@NotNull DialogControl definition, @NotNull EditText editText) {
        super(definition);

        this.editText = editText;
    }

    /**
     * @return the text the control edits
     */
    public @NotNull EditText getEditText() {
        return this.editText;
    }

    /**
     * Sets the text the control edits.
     *
     * @param editText the new text
     * @return the text the control edited before, which its owner detaches
     */
    public @NotNull EditText setEditText(@NotNull EditText editText) {
        EditText replaced = this.editText;

        this.editText = editText;

        return replaced;
    }
}
