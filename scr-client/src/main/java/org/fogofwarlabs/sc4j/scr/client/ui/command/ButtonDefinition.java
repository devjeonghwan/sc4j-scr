package org.fogofwarlabs.sc4j.scr.client.ui.command;

import org.fogofwarlabs.sc4j.scr.content.ScrAddedUnitType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes one command button of a native button set.
 * <p>
 * The fields follow the 32-byte native button record: position, icon, condition, action, condition value, action value,
 * enabled string and disabled string. String identifiers are one-based {@code stat_txt} indices; zero means none. A button a mod adds
 * ({@link ScrAddedUnitType.TrainButton}) carries its texts instead.
 */
public final class ButtonDefinition {
    /**
     * The button kind character of a unit production button string, the second character after the hotkey.
     */
    public static final char UNIT_PRODUCTION_KIND = 1;

    private final int position;
    private final int icon;

    private final @NotNull ButtonCondition condition;
    private final int conditionValue;

    private final @NotNull ButtonAction action;
    private final int actionValue;

    private final int enabledString;
    private final int disabledString;
    private final ScrAddedUnitType.@Nullable TrainButton addedButton;
    private final @Nullable String enabledText;

    /**
     * Creates a {@link ButtonDefinition} instance whose strings are {@code stat_txt} strings.
     *
     * @param position       the command card position from {@code 1} through {@code 9}
     * @param icon           the {@code cmdicons} frame
     * @param condition      the condition function
     * @param conditionValue the value passed to the condition
     * @param action         the action function
     * @param actionValue    the value passed to the action
     * @param enabledString  the one-based {@code stat_txt} string of the enabled button
     * @param disabledString the one-based {@code stat_txt} string of the disabled button, or {@code 0}
     */
    public ButtonDefinition(int position,
                            int icon,
                            @NotNull ButtonCondition condition, int conditionValue,
                            @NotNull ButtonAction action, int actionValue,
                            int enabledString, int disabledString) {
        this(position, icon, condition, conditionValue, action, actionValue, enabledString, disabledString, null);
    }

    /**
     * Creates a {@link ButtonDefinition} instance of a button a mod adds, whose strings are its texts.
     *
     * @param position       the command card position from {@code 1} through {@code 9}
     * @param icon           the {@code cmdicons} frame
     * @param condition      the condition function
     * @param conditionValue the value passed to the condition
     * @param action         the action function
     * @param actionValue    the value passed to the action
     * @param addedButton    the added button, whose texts the tooltip shows
     */
    public ButtonDefinition(int position,
                            int icon,
                            @NotNull ButtonCondition condition, int conditionValue,
                            @NotNull ButtonAction action, int actionValue,
                            ScrAddedUnitType.@NotNull TrainButton addedButton) {
        this(position, icon, condition, conditionValue, action, actionValue, 0, 0, addedButton);
    }

    private ButtonDefinition(int position,
                             int icon,
                             @NotNull ButtonCondition condition, int conditionValue,
                             @NotNull ButtonAction action, int actionValue,
                             int enabledString, int disabledString, ScrAddedUnitType.@Nullable TrainButton addedButton) {
        this.position = position;
        this.icon     = icon;

        this.condition      = condition;
        this.conditionValue = conditionValue;

        this.action      = action;
        this.actionValue = actionValue;

        this.enabledString  = enabledString;
        this.disabledString = disabledString;
        this.addedButton    = addedButton;
        this.enabledText    = addedButton == null ? null : String.valueOf(addedButton.getHotkey()) + UNIT_PRODUCTION_KIND + addedButton.getText();
    }

    /**
     * @return the command card position from {@code 1} through {@code 9}
     */
    public int getPosition() {
        return this.position;
    }

    /**
     * @return the {@code cmdicons} frame
     */
    public int getIcon() {
        return this.icon;
    }

    /**
     * @return the condition function
     */
    public @NotNull ButtonCondition getCondition() {
        return this.condition;
    }

    /**
     * @return the value passed to the condition
     */
    public int getConditionValue() {
        return this.conditionValue;
    }

    /**
     * @return the action function
     */
    public @NotNull ButtonAction getAction() {
        return this.action;
    }

    /**
     * @return the value passed to the action
     */
    public int getActionValue() {
        return this.actionValue;
    }

    /**
     * @return the one-based {@code stat_txt} string of the enabled button
     */
    public int getEnabledString() {
        return this.enabledString;
    }

    /**
     * @return the one-based {@code stat_txt} string of the disabled button, or {@code 0}
     */
    public int getDisabledString() {
        return this.disabledString;
    }

    /**
     * @return the button a mod adds, whose texts the button shows, or {@code null} for a native button
     */
    public ScrAddedUnitType.@Nullable TrainButton getAddedButton() {
        return this.addedButton;
    }

    /**
     * @return the enabled button string in the {@code stat_txt} button string format (hotkey, button kind, text of the default language), or
     * {@code null} if the button uses its enabled {@code stat_txt} string
     */
    public @Nullable String getEnabledText() {
        return this.enabledText;
    }
}
