package org.fogofwarlabs.sc4j.scr.data.resource.dialog;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Names the control types of the SC:R interface files ({@code rez/*.ui.json}), the {@code type} of each control. The
 * engine {@link org.fogofwarlabs.sc4j.engine.resource.dialog.ControlKind} of an imported control is derived from it; the
 * SC:R client reads the native type through {@link ScrDialogControlUtils#getType}.
 */
public enum ScrControlType {
    /**
     * The dialog root.
     */
    DIALOG,
    /**
     * A rectangle.
     */
    RECT,
    /**
     * Left-aligned text.
     */
    LTEXT,
    /**
     * Centered text.
     */
    CTEXT,
    /**
     * Right-aligned text.
     */
    RTEXT,
    /**
     * An animated button.
     */
    FLCBTN,
    /**
     * A push button.
     */
    PUSHBUTTON,
    /**
     * A default push button.
     */
    DEFPUSHBUTTON,
    /**
     * A radio button.
     */
    RADIOBUTTON,
    /**
     * A checkbox.
     */
    CHECKBOX,
    /**
     * A list box.
     */
    LISTBOX,
    /**
     * A combo box.
     */
    COMBOBOX,
    /**
     * An editable text field.
     */
    EDITTEXT,
    /**
     * A scroll bar.
     */
    SCROLLBAR,
    /**
     * A button text element.
     */
    TEXTBOX,
    /**
     * A button hit-test element.
     */
    HOTBOX,
    /**
     * An animation element.
     */
    FLC,
    /**
     * An embedded web page.
     */
    WEBBROWSER,
    /**
     * An unrecognized control type.
     */
    OTHER;

    /**
     * Returns the type of a {@code type} string.
     *
     * @param name the type name of the JSON file
     * @return the type, {@link #OTHER} when unknown
     */
    public static @NotNull ScrControlType of(@NotNull String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return OTHER;
        }
    }
}
