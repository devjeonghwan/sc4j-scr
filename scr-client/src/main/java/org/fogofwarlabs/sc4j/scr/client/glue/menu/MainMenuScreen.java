package org.fogofwarlabs.sc4j.scr.client.glue.menu;

import org.fogofwarlabs.sc4j.scr.client.glue.*;
import org.fogofwarlabs.sc4j.scr.client.glue.popup.OptionsPopup;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.ScrControl;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Shows animated game-choice and exit buttons together with the available menu actions.
 * <p>
 * Single Player and Multiplayer ask for Original or Expansion before opening the campaigns or the connection choice. Options, Hotkeys,
 * Credits and Intro open their corresponding scenes. The top right names the run's player ({@code Name#tag}, or the guest name) in place of
 * the login button, which is hidden; the unavailable collection action is disabled. Runtime information lines are cleared, and the build
 * line shows version, locale and platform.
 */
public final class MainMenuScreen extends GlueScreen {
    private static final @NotNull String DIALOG = "gluMain";
    private static final @NotNull String PALETTE_FOLDER = "glue/palmm";
    private static final int EXIT = 2;
    private static final int SINGLE_PLAYER = 3;
    private static final int MULTIPLAYER = 4;
    private static final int VIEW_INTRO = 8;
    private static final int LOGIN = 10;
    private static final int PLAYER_NAME = 11;
    private static final int OPTIONS = 12;
    private static final int LOCK = 13;
    private static final int HOTKEYS = 15;
    private static final int CREDITS = 16;
    private static final int SINGLE_PLAYER_UNAVAILABLE = 21;
    private static final int COLLECTIONS = 28;
    /**
     * The information controls whose runtime text is cleared when the menu opens.
     */
    private static final int @NotNull [] RUNTIME_TEXTS = {9, 24, 25, 26};
    /**
     * Identifies the right-aligned build line at the bottom of the main menu.
     * The text joins the build version, locale and platform with the configured separator.
     */
    private static final int BUILD_LINE = 27;
    private static final @NotNull String BUILD_LINE_SEPARATOR = " | ";
    private static final @NotNull String PLATFORM = "64-bit";

    /**
     * Shows the Original or Expansion choice over the main menu; Cancel leaves the menu open.
     */
    static final class GameModePopup extends GluePopup {
        private static final int ORIGINAL = 6;
        private static final int TITLE_CONTROL = 2;
        private static final int CANCEL = 3;
        private static final int EXPANSION = 7;
        private static final @NotNull String GAME_MODE_DIALOG = "gluGameMode";
        private static final @NotNull String PANEL = "glue/palmm/Retail_Ex.DDS";
        private static final @NotNull String TITLE = "gluSELECTTYPE";

        private final @NotNull Consumer<Boolean> choice;

        GameModePopup(@NotNull Consumer<Boolean> choice) {
            super(GAME_MODE_DIALOG, PALETTE_FOLDER, PANEL);

            this.choice = choice;
        }

        /**
         * Called after the dialog is loaded: hide, disable or fill controls here.
         *
         * @param glueContext the shared services
         */
        @Override
        protected void onOpen(@NotNull GlueContext glueContext) {
            ScrControl title = this.bindControl(TITLE_CONTROL);

            title.setReplacedText(glueContext.getGluText().get(TITLE));
            title.setTextFormat(GlueCanvas.FORMAT_CENTER_ONE_LINE);
        }

        /**
         * Called when a control is activated by a click, its hotkey, Esc or Enter.
         *
         * @param control the control id
         */
        @Override
        protected void onActivate(int control) {
            switch (control) {
                case ORIGINAL -> {
                    this.finish();
                    this.choice.accept(false);
                }
                case EXPANSION -> {
                    this.finish();
                    this.choice.accept(true);
                }
                case CANCEL -> this.finish();
                default -> {
                }
            }
        }
    }


    /**
     * Creates a {@link MainMenuScreen} instance.
     */
    public MainMenuScreen() {
        super(DIALOG, PALETTE_FOLDER, getPaletteBackground(PALETTE_FOLDER));
    }

    /**
     * Called after the dialog is loaded: hide, disable or fill controls here.
     *
     * @param glueContext the shared services
     */
    @Override
    protected void onOpen(@NotNull GlueContext glueContext) {
        this.bindControl(SINGLE_PLAYER_UNAVAILABLE).setVisible(false);
        this.bindControl(LOCK).setVisible(false);
        this.bindControl(COLLECTIONS).setEnabled(false);
        this.showPlayerName(LOGIN, PLAYER_NAME);

        for (int control : RUNTIME_TEXTS) {
            this.bindControl(control).setReplacedText("");
        }

        GlueCanvas canvas = glueContext.getCanvas();

        String buildLine = canvas.getStorage().getBuildVersion() + BUILD_LINE_SEPARATOR + canvas.getLocale() + BUILD_LINE_SEPARATOR + PLATFORM;

        this.bindControl(BUILD_LINE).setReplacedText(buildLine);
    }

    /**
     * Called when a control is activated by a click, its hotkey, Esc or Enter.
     *
     * @param control the control id
     */
    @Override
    protected void onActivate(int control) {
        switch (control) {
            case EXIT -> this.getContext().getNavigator().exit();
            case OPTIONS -> this.showPopup(new OptionsPopup(this.getPaletteFolder()));
            case VIEW_INTRO -> this.getContext().getNavigator().show(GlueNavigator.Destination.INTRO);
            case CREDITS -> this.getContext().getNavigator().show(GlueNavigator.Destination.CREDITS);
            case HOTKEYS -> this.getContext().getNavigator().show(GlueNavigator.Destination.HOTKEYS);
            case SINGLE_PLAYER -> this.chooseGameMode(false, GlueNavigator.Destination.CAMPAIGN);
            case MULTIPLAYER -> this.chooseGameMode(true, GlueNavigator.Destination.CONNECTION);
            default -> {
            }
        }
    }

    /**
     * Shows the Original or Expansion choice; a choice sets the game mode and shows the next destination.
     *
     * @param multiplayer whether the chosen game is a multiplayer game
     * @param next        the destination shown after the choice
     */
    private void chooseGameMode(boolean multiplayer, GlueNavigator.@NotNull Destination next) {
        this.showPopup(new GameModePopup(expansion -> {
            this.getContext().setExpansion(expansion);
            this.getContext().setMultiplayer(multiplayer);
            this.getContext().getNavigator().show(next);
        }));
    }
}
