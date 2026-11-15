package org.fogofwarlabs.sc4j.scr.client.configuration;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.client.cursor.PointerSpeed;
import org.fogofwarlabs.sc4j.scr.client.ui.observer.ApmAlert;

/**
 * Stores the live client interface options for scrolling, display indicators, portraits, APM alerts and pointer behavior. The settings file
 * initializes them, and the options popup and accelerators update the same values.
 */
public final class InterfaceSettings {
    /**
     * The slowest scroll speed.
     */
    public static final int MINIMUM_SCROLL_SPEED = 0;

    /**
     * The fastest scroll speed.
     */
    public static final int MAXIMUM_SCROLL_SPEED = 6;

    /**
     * The default scroll speed: the middle one.
     */
    public static final int DEFAULT_SCROLL_SPEED = 3;

    /**
     * The portraits mode that shows no portrait.
     */
    public static final int NO_PORTRAITS = 0;

    /**
     * The portraits mode whose portrait holds a still picture.
     */
    public static final int STILL_PORTRAITS = 1;

    /**
     * The portraits mode whose portrait clips play (the default).
     */
    public static final int ANIMATED_PORTRAITS = 2;

    /**
     * The largest APM threshold the options page takes (three digits).
     */
    public static final int MAXIMUM_ACTIONS_PER_MINUTE_ALERT_THRESHOLD = 999;

    /**
     * No Mouse Sensitivity stored: the default follows the system pointer speed.
     */
    public static final int SYSTEM_SENSITIVITY = -1;

    /**
     * The largest Mouse Sensitivity of the options page.
     */
    public static final int MAXIMUM_MOUSE_SENSITIVITY = PointerSpeed.MAXIMUM_SENSITIVITY;

    /**
     * Validates a configured scroll-speed index.
     *
     * @param speed the requested scroll-speed index
     * @return the validated index
     * @throws IllegalArgumentException if the index is outside the supported range
     */
    private static int validateScrollSpeed(int speed) {
        if (speed < MINIMUM_SCROLL_SPEED || speed > MAXIMUM_SCROLL_SPEED) {
            throw Exceptions.newIllegalArgumentException("The scroll speed must be 0 through 6: {0}", speed);
        }

        return speed;
    }

    private int keyScroll;
    private int mouseScroll;

    private boolean frameRateShown;
    private boolean turnRateShown;
    private boolean gameTimer;

    private int tipNumber;
    private boolean tipsShown;

    private boolean colorCycling;
    private int unitPortraits;
    private boolean consoleSplit;

    private boolean actionsPerMinuteShown;
    private boolean actionsPerMinuteAlertEnabled;
    private int actionsPerMinuteAlertThreshold;
    private boolean actionsPerMinuteAlertColorEnabled;
    private boolean actionsPerMinuteAlertSoundEnabled;

    private boolean mouseSensitivityEnabled;
    private int mouseSensitivity;
    private boolean mouseScaling;
    private boolean hardwareCursor;

    /**
     * Creates a {@link InterfaceSettings} instance with the default interface options.
     */
    public InterfaceSettings() {
        this.keyScroll   = DEFAULT_SCROLL_SPEED;
        this.mouseScroll = DEFAULT_SCROLL_SPEED;

        this.frameRateShown = false;
        this.turnRateShown  = false;
        this.gameTimer      = false;

        this.tipNumber = 0;
        this.tipsShown = true;

        this.colorCycling  = true;
        this.unitPortraits = ANIMATED_PORTRAITS;
        this.consoleSplit  = true;

        this.actionsPerMinuteShown             = false;
        this.actionsPerMinuteAlertEnabled      = false;
        this.actionsPerMinuteAlertThreshold    = ApmAlert.DEFAULT_THRESHOLD;
        this.actionsPerMinuteAlertColorEnabled = false;
        this.actionsPerMinuteAlertSoundEnabled = false;

        this.mouseSensitivityEnabled = true;
        this.mouseSensitivity        = SYSTEM_SENSITIVITY;
        this.mouseScaling            = false;
        this.hardwareCursor          = true;
    }

    /**
     * @return the keyboard scroll speed, {@code 0..6}
     */
    public int getKeyScroll() {
        return this.keyScroll;
    }

    /**
     * Sets the keyboard scroll speed.
     *
     * @param keyScroll the speed {@code 0..6}
     * @throws IllegalArgumentException if the speed is outside {@code 0..6}
     */
    public void setKeyScroll(int keyScroll) {
        this.keyScroll = validateScrollSpeed(keyScroll);
    }

    /**
     * @return the mouse (edge) scroll speed, {@code 0..6}
     */
    public int getMouseScroll() {
        return this.mouseScroll;
    }

    /**
     * Sets the mouse (edge) scroll speed.
     *
     * @param mouseScroll the speed {@code 0..6}
     * @throws IllegalArgumentException if the speed is outside {@code 0..6}
     */
    public void setMouseScroll(int mouseScroll) {
        this.mouseScroll = validateScrollSpeed(mouseScroll);
    }


    /**
     * @return {@code true} if the frame rate shows, {@code false} otherwise
     */
    public boolean isFrameRateShown() {
        return this.frameRateShown;
    }

    /**
     * Sets whether the frame rate shows.
     *
     * @param frameRateShown whether the frame rate is shown
     */
    public void setFrameRateShown(boolean frameRateShown) {
        this.frameRateShown = frameRateShown;
    }

    /**
     * @return {@code true} if the turn rate shows in multiplayer games, {@code false} otherwise
     */
    public boolean isTurnRateShown() {
        return this.turnRateShown;
    }

    /**
     * Sets whether the turn rate shows in multiplayer games.
     *
     * @param turnRateShown whether the turn rate shows
     */
    public void setTurnRateShown(boolean turnRateShown) {
        this.turnRateShown = turnRateShown;
    }

    /**
     * @return {@code true} if the game time shows, {@code false} otherwise
     */
    public boolean isGameTimer() {
        return this.gameTimer;
    }

    /**
     * Sets whether the game time is displayed.
     *
     * @param gameTimer whether the game time is displayed
     */
    public void setGameTimer(boolean gameTimer) {
        this.gameTimer = gameTimer;
    }


    /**
     * @return the last shown StarCraft tip
     */
    public int getTipNumber() {
        return this.tipNumber;
    }

    /**
     * Sets the last shown StarCraft tip.
     *
     * @param tipNumber the tip number
     */
    public void setTipNumber(int tipNumber) {
        this.tipNumber = tipNumber;
    }

    /**
     * @return {@code true} if the tips open at a mission start, {@code false} otherwise
     */
    public boolean isTipsShown() {
        return this.tipsShown;
    }

    /**
     * Sets whether the tips open at a mission start.
     *
     * @param tipsShown whether the tips open at a mission start
     */
    public void setTipsShown(boolean tipsShown) {
        this.tipsShown = tipsShown;
    }


    /**
     * @return {@code true} if the palette cycles, {@code false} otherwise
     */
    public boolean isColorCycling() {
        return this.colorCycling;
    }

    /**
     * Sets whether the palette cycles.
     *
     * @param colorCycling whether the palette cycles
     */
    public void setColorCycling(boolean colorCycling) {
        this.colorCycling = colorCycling;
    }

    /**
     * @return the portraits mode: {@link #NO_PORTRAITS}, {@link #STILL_PORTRAITS} or {@link #ANIMATED_PORTRAITS}
     */
    public int getUnitPortraits() {
        return this.unitPortraits;
    }

    /**
     * Sets the portraits mode.
     *
     * @param unitPortraits {@link #NO_PORTRAITS}, {@link #STILL_PORTRAITS} or {@link #ANIMATED_PORTRAITS}
     * @throws IllegalArgumentException if the mode is not one of them
     */
    public void setUnitPortraits(int unitPortraits) {
        if (unitPortraits < NO_PORTRAITS || unitPortraits > ANIMATED_PORTRAITS) {
            throw Exceptions.newIllegalArgumentException("The portraits mode must be 0 through 2: {0}", unitPortraits);
        }

        this.unitPortraits = unitPortraits;
    }

    /**
     * @return {@code true} if the minimap sits in the left corner of the HD console, {@code false} otherwise (the standard centered console)
     */
    public boolean isConsoleSplit() {
        return this.consoleSplit;
    }

    /**
     * Sets the Mini-Map Position.
     *
     * @param consoleSplit {@code true} for the left corner, {@code false} for the standard console
     */
    public void setConsoleSplit(boolean consoleSplit) {
        this.consoleSplit = consoleSplit;
    }


    /**
     * @return {@code true} if a live game shows actions per minute, {@code false} otherwise
     */
    public boolean isActionsPerMinuteShown() {
        return this.actionsPerMinuteShown;
    }

    /**
     * Sets whether a live game shows actions per minute.
     *
     * @param actionsPerMinuteShown whether actions per minute are shown
     */
    public void setActionsPerMinuteShown(boolean actionsPerMinuteShown) {
        this.actionsPerMinuteShown = actionsPerMinuteShown;
    }

    /**
     * @return {@code true} if "Alert when APM falls below" is on, {@code false} otherwise
     */
    public boolean isActionsPerMinuteAlertEnabled() {
        return this.actionsPerMinuteAlertEnabled;
    }

    /**
     * Sets "Alert when APM falls below".
     *
     * @param actionsPerMinuteAlertEnabled whether the alert is on
     */
    public void setActionsPerMinuteAlertEnabled(boolean actionsPerMinuteAlertEnabled) {
        this.actionsPerMinuteAlertEnabled = actionsPerMinuteAlertEnabled;
    }

    /**
     * @return the actions-per-minute alert threshold
     */
    public int getActionsPerMinuteAlertThreshold() {
        return this.actionsPerMinuteAlertThreshold;
    }

    /**
     * Sets the actions-per-minute alert threshold.
     *
     * @param actionsPerMinuteAlertThreshold the threshold {@code 0} to {@value #MAXIMUM_ACTIONS_PER_MINUTE_ALERT_THRESHOLD}
     * @throws IllegalArgumentException if the threshold is outside that range
     */
    public void setActionsPerMinuteAlertThreshold(int actionsPerMinuteAlertThreshold) {
        if (actionsPerMinuteAlertThreshold < 0 || actionsPerMinuteAlertThreshold > MAXIMUM_ACTIONS_PER_MINUTE_ALERT_THRESHOLD) {
            throw Exceptions.newIllegalArgumentException("The APM threshold must be 0 through 999: {0}", actionsPerMinuteAlertThreshold);
        }

        this.actionsPerMinuteAlertThreshold = actionsPerMinuteAlertThreshold;
    }

    /**
     * @return {@code true} if the actions-per-minute number takes the alert colors, {@code false} otherwise
     */
    public boolean isActionsPerMinuteAlertColorEnabled() {
        return this.actionsPerMinuteAlertColorEnabled;
    }

    /**
     * Sets "Color Text".
     *
     * @param actionsPerMinuteAlertColorEnabled whether the actions-per-minute number takes the alert colors
     */
    public void setActionsPerMinuteAlertColorEnabled(boolean actionsPerMinuteAlertColorEnabled) {
        this.actionsPerMinuteAlertColorEnabled = actionsPerMinuteAlertColorEnabled;
    }

    /**
     * @return {@code true} if the alert plays its sound, {@code false} otherwise
     */
    public boolean isActionsPerMinuteAlertSoundEnabled() {
        return this.actionsPerMinuteAlertSoundEnabled;
    }

    /**
     * Sets "Play Sound".
     *
     * @param actionsPerMinuteAlertSoundEnabled whether the alert plays its sound
     */
    public void setActionsPerMinuteAlertSoundEnabled(boolean actionsPerMinuteAlertSoundEnabled) {
        this.actionsPerMinuteAlertSoundEnabled = actionsPerMinuteAlertSoundEnabled;
    }


    /**
     * @return {@code true} if the window sets the pointer speed, {@code false} otherwise
     */
    public boolean isMouseSensitivityEnabled() {
        return this.mouseSensitivityEnabled;
    }

    /**
     * Sets whether the window sets the pointer speed.
     *
     * @param mouseSensitivityEnabled whether the window sets the pointer speed
     */
    public void setMouseSensitivityEnabled(boolean mouseSensitivityEnabled) {
        this.mouseSensitivityEnabled = mouseSensitivityEnabled;
    }

    /**
     * @return the Mouse Sensitivity {@code 0} to {@value #MAXIMUM_MOUSE_SENSITIVITY}, or {@link #SYSTEM_SENSITIVITY} while none was chosen
     */
    public int getMouseSensitivity() {
        return this.mouseSensitivity;
    }

    /**
     * Sets the Mouse Sensitivity.
     *
     * @param mouseSensitivity the sensitivity {@code 0} to {@value #MAXIMUM_MOUSE_SENSITIVITY}
     * @throws IllegalArgumentException if the sensitivity is outside that range
     */
    public void setMouseSensitivity(int mouseSensitivity) {
        if (mouseSensitivity < 0 || mouseSensitivity > MAXIMUM_MOUSE_SENSITIVITY) {
            throw Exceptions.newIllegalArgumentException("The mouse sensitivity must be 0 through 100: {0}", mouseSensitivity);
        }

        this.mouseSensitivity = mouseSensitivity;
    }

    /**
     * @return {@code true} if Mouse Scaling is on, {@code false} otherwise
     */
    public boolean isMouseScaling() {
        return this.mouseScaling;
    }

    /**
     * Sets whether Mouse Scaling is on.
     *
     * @param mouseScaling whether Mouse Scaling is on
     */
    public void setMouseScaling(boolean mouseScaling) {
        this.mouseScaling = mouseScaling;
    }

    /**
     * @return {@code true} if the cursor may be the platform pointer, {@code false} otherwise
     */
    public boolean isHardwareCursor() {
        return this.hardwareCursor;
    }

    /**
     * Sets whether the cursor may be the platform pointer.
     *
     * @param hardwareCursor whether the cursor may be the platform pointer
     */
    public void setHardwareCursor(boolean hardwareCursor) {
        this.hardwareCursor = hardwareCursor;
    }
}
