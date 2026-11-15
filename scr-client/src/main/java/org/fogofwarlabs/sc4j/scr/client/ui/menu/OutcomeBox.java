package org.fogofwarlabs.sc4j.scr.client.ui.menu;

import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.ScrDialog;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrDialogControlUtils;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrDialogImportUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.Function;

/**
 * Configures victory, defeat and replay-finished dialogs and maps released controls to game actions.
 * Live multiplayer outcomes can show Continue Playing, draw or disconnected text. A replay uses its finished labels.
 * Enter, Escape and the default button request the end-game action.
 */
public final class OutcomeBox {
    /**
     * The default button.
     */
    public static final int DEFAULT_BUTTON = -2;
    /**
     * Continue Playing (victory) / Become Observer (defeat).
     */
    public static final int SECOND_BUTTON = 1;
    /**
     * The {@code network.json} key of the Continue Playing message.
     */
    public static final @NotNull String CONTINUE_WARNING = "strCONTINUEWARNING";
    /**
     * The defeat box closes itself after this time ({@code 20000} ms).
     */
    public static final int DEFEAT_CLOSE_MILLISECONDS = 20000;

    private static final int END_MISSION_TEXT = -10;
    private static final int RESULT_TEXT = -20;
    private static final int DISCONNECTED_TEXT = -21;
    private static final int OK_TEXT = -22;
    private static final int DRAW_TEXT = -23;
    private static final int PENDING_TEXT = -24;
    /**
     * The dialog file of the victory box.
     */
    private static final @NotNull String VICTORY_DIALOG = "wmission";
    /**
     * The dialog file of the defeat box.
     */
    private static final @NotNull String DEFEAT_DIALOG = "lmission";
    /**
     * The {@code gluAll} key of the replay's end text.
     */
    private static final @NotNull String REPLAY_FINISHED_TEXT = "replayEndGame";
    /**
     * The {@code gluAll} key of the replay's end button.
     */
    private static final @NotNull String REPLAY_FINISHED_BUTTON = "replayEndGameButton";

    /**
     * What a pressed control of the box does.
     */
    public enum Action {
        /**
         * Nothing.
         */
        NONE,
        /**
         * The game ends with code {@code 7} (victory) or {@code 8} (defeat) and the score screen follows.
         */
        END_GAME,
        /**
         * The box closes and the game goes on (Continue Playing).
         */
        CONTINUE_PLAYING
    }

    /**
     * Creates a {@link OutcomeBox} instance that opens the box of an outcome.
     *
     * @param storage      the game data
     * @param victory      whether to open the victory box instead of the defeat box
     * @param draw         whether the local result is a draw (result {@code 5})
     * @param multiplayer  whether the game is a multiplayer game
     * @param disconnected whether the game ended after a disconnection
     * @param controlTexts resolves the dialog's content keys ({@code RC3})
     * @return the created {@link OutcomeBox} instance
     * @throws IOException if the dialog definition cannot be read
     */
    public static @NotNull OutcomeBox open(@NotNull CascStorage storage,
                                           boolean victory, boolean draw,
                                           boolean multiplayer, boolean disconnected,
                                           @NotNull Function<String, String> controlTexts) throws IOException {
        ScrDialog dialog = new ScrDialog(ScrDialogImportUtils.load(storage, victory ? VICTORY_DIALOG : DEFEAT_DIALOG));

        apply(dialog, victory, draw, multiplayer, disconnected, controlTexts);

        return new OutcomeBox(dialog, victory);
    }

    /**
     * Creates a {@link OutcomeBox} instance that opens the victory box of a finished replay.
     *
     * @param storage  the game data
     * @param glueText resolves the {@code gluAll} keys
     * @return the created {@link OutcomeBox} instance
     * @throws IOException if the dialog definition cannot be read
     */
    public static @NotNull OutcomeBox openReplayFinished(@NotNull CascStorage storage,
                                                         @NotNull Function<String, String> glueText) throws IOException {
        ScrDialog dialog = new ScrDialog(ScrDialogImportUtils.load(storage, VICTORY_DIALOG));

        applyReplayFinished(dialog, glueText);

        return new OutcomeBox(dialog, true);
    }

    /**
     * Applies the replay branch of the victory box init.
     */
    static void applyReplayFinished(@NotNull ScrDialog dialog, @NotNull Function<String, String> glueText) {
        dialog.requireControl(SECOND_BUTTON).setVisible(false);
        dialog.requireControl(RESULT_TEXT).setVisible(true);
        dialog.requireControl(DISCONNECTED_TEXT).setVisible(false);
        dialog.requireControl(DRAW_TEXT).setVisible(false);
        dialog.requireControl(PENDING_TEXT).setVisible(false);
        dialog.requireControl(RESULT_TEXT).setReplacedText(glueText.apply(REPLAY_FINISHED_TEXT));
        dialog.requireControl(DEFAULT_BUTTON).setReplacedText(glueText.apply(REPLAY_FINISHED_BUTTON));
    }

    /**
     * Updates live outcome controls without replay labels or a server result.
     */
    static void apply(@NotNull ScrDialog dialog,
                      boolean victory, boolean draw,
                      boolean multiplayer, boolean disconnected,
                      @NotNull Function<String, String> controlTexts) {
        boolean disconnectedText = multiplayer && disconnected;

        if (!victory) {
            dialog.requireControl(SECOND_BUTTON).setVisible(false);
            dialog.requireControl(RESULT_TEXT).setVisible(!disconnectedText);
            dialog.requireControl(DISCONNECTED_TEXT).setVisible(disconnectedText);

            return;
        }

        dialog.requireControl(SECOND_BUTTON).setVisible(multiplayer);

        boolean drawText = !disconnectedText && draw;
        boolean resultText = !disconnectedText && !draw;

        dialog.requireControl(RESULT_TEXT).setVisible(resultText);
        dialog.requireControl(DISCONNECTED_TEXT).setVisible(disconnectedText);
        dialog.requireControl(DRAW_TEXT).setVisible(drawText);
        dialog.requireControl(PENDING_TEXT).setVisible(false);

        if (multiplayer) {
            DialogControl control = ScrDialogControlUtils.findControl(dialog.getDefinition(), resultText ? END_MISSION_TEXT : OK_TEXT);

            dialog.requireControl(DEFAULT_BUTTON).setReplacedText(control == null ? "" : dialog.getControlString(control, controlTexts));
        }
    }


    private final @NotNull ScrDialog dialog;
    private final boolean victory;

    private OutcomeBox(@NotNull ScrDialog dialog, boolean victory) {
        this.dialog  = dialog;
        this.victory = victory;
    }

    /**
     * @return the dialog drawn and hit-tested as the game's popup
     */
    public @NotNull ScrDialog getDialog() {
        return this.dialog;
    }

    /**
     * @return {@code true} if this is a victory box, {@code false} otherwise
     */
    public boolean isVictory() {
        return this.victory;
    }


    /**
     * Maps a released control to its action.
     * The default button, Enter and Escape end the game. A visible victory secondary button continues play.
     *
     * @param control the released control or dialog key-request identifier
     * @return the action
     */
    public @NotNull Action press(int control) {
        if (control == DEFAULT_BUTTON || control == ScrDialog.ESCAPE_REQUEST) {
            return Action.END_GAME;
        }

        DialogControl second = ScrDialogControlUtils.findControl(this.dialog.getDefinition(), SECOND_BUTTON);

        if (control == SECOND_BUTTON && this.victory && second != null && this.dialog.isVisible(second)) {
            return Action.CONTINUE_PLAYING;
        }

        return Action.NONE;
    }
}
