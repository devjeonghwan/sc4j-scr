package org.fogofwarlabs.sc4j.scr.client.glue.campaign;

import org.fogofwarlabs.sc4j.base.log.Logger;
import org.fogofwarlabs.sc4j.scr.client.glue.*;
import org.fogofwarlabs.sc4j.scr.client.glue.popup.GlueSavePopup;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.ListBox;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.ScrControl;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameFile;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameKind;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameSession;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Lists the chosen character's saved games for the selected game edition, with load, delete and campaign return actions.
 */
public final class LoadSavedGameScreen extends GlueScreen {
    private static final @NotNull Logger LOGGER = Logger.of(LoadSavedGameScreen.class);

    private static final @NotNull String DIALOG = "gluLoad";

    private static final int OK = 4;
    private static final int CANCEL = 5;
    private static final int LIST = 6;
    private static final int DELETE = 7;
    /**
     * The slide-in list: panel {@code 1} from the left, the Cancel and Ok panels {@code 2} and {@code 3} from the right, no delay.
     */
    private static final int @NotNull [] SWISH = {1, 0, 2, 2, 3, 2};
    private static final int SWISH_DELAY_MILLISECONDS = 0;
    private static final int FIRST_ROW = 0;

    private static final @NotNull String INVALID_ERROR_KEY = "saveerrorINVALID";
    private static final @NotNull String CONFIRM_DELETE_KEY = "loadCONFIRMDELETE";
    private static final @NotNull String DELETE_ERROR_KEY = "strDELETE_ERR_s";
    private static final @NotNull String NAME_PLACEHOLDER = "%s";

    private @Nullable ListBox list;
    private @NotNull List<String> names;

    private @Nullable Path directory;
    private @NotNull String extension;

    private @Nullable ScrControl okButton;
    private @Nullable ScrControl deleteButton;

    /**
     * Creates a {@link LoadSavedGameScreen} instance.
     */
    public LoadSavedGameScreen() {
        super(DIALOG, SHARED_PALETTE_FOLDER, getPaletteBackground(SHARED_PALETTE_FOLDER));

        this.list  = null;
        this.names = List.of();

        this.directory = null;
        this.extension = SaveGameKind.SINGLE_PLAYER.getExtension();

        this.okButton     = null;
        this.deleteButton = null;
    }

    /**
     * @return the selected save file, or {@code null} when the list or character folder has no selection
     */
    private @Nullable Path getSelectedFile() {
        ListBox files = this.list;
        Path folder = this.directory;

        if (files == null || folder == null) {
            return null;
        }

        int selection = files.getSelection();

        return selection < 0 || selection >= this.names.size() ? null : folder.resolve(this.names.get(selection) + this.extension);
    }


    /**
     * Called after the dialog is loaded: hide, disable or fill controls here.
     * <p>
     * Initializes the saved-game list and the load controls.
     *
     * @param glueContext the shared services
     */
    @Override
    protected void onOpen(@NotNull GlueContext glueContext) {
        this.okButton     = this.bindControl(OK);
        this.deleteButton = this.bindControl(DELETE);

        this.setSwish(SWISH, SWISH_DELAY_MILLISECONDS);

        this.directory = glueContext.getSaveDirectory();
        this.extension = SaveGameKind.of(glueContext.isExpansion(), false).getExtension();

        ListBox files = this.addListBox(LIST);

        // The list shows plain text over the panel art without row bars.
        files.setTransparent(true);
        this.list = files;

        this.fillSavedGames();
    }

    private void fillSavedGames() {
        ListBox files = this.list;
        Path folder = this.directory;

        if (files == null) {
            return;
        }

        this.names = folder == null ? List.of() : GlueSavePopup.listNames(folder, this.extension);
        files.setItems(this.names);
        files.select(this.names.isEmpty() ? ListBox.NO_SELECTION : FIRST_ROW, files.getVisibleRows(this.getContext().getCanvas()));

        this.updateActions();
    }

    /**
     * Enables Ok and Delete while a saved game is selected.
     */
    private void updateActions() {
        boolean selected = this.getSelectedFile() != null;

        requireBound(this.okButton).setEnabled(selected);
        requireBound(this.deleteButton).setEnabled(selected);
    }


    /**
     * Called after the user changed the selection of a list or combo box, or the value of a slider.
     * <p>
     * Updates the load and delete actions after the saved-game selection changes.
     *
     * @param control the control id
     */
    @Override
    protected void onSelectionChanged(int control) {
        if (control == LIST) {
            this.updateActions();
        }
    }

    /**
     * Called when a control is activated by a click, its hotkey, Esc or Enter.
     * <p>
     * Handles the saved-game actions and the cancel navigation.
     *
     * @param control the control id
     */
    @Override
    protected void onActivate(int control) {
        switch (control) {
            case OK -> this.loadSavedGame();
            case CANCEL -> this.leave(() -> this.getContext().getNavigator().show(GlueNavigator.Destination.CAMPAIGN));
            case DELETE -> this.confirmDelete();
            default -> {
            }
        }
    }

    /**
     * Starts the picked saved game, or shows why it does not load.
     */
    private void loadSavedGame() {
        Path file = this.getSelectedFile();

        if (file == null) {
            return;
        }

        GlueContext glueContext = this.getContext();
        MeleeGameSetup setup;

        try {
            setup = SaveGameSession.read(SaveGameFile.parse(Files.readAllBytes(file))).getSetup();
        } catch (IOException | RuntimeException exception) {
            LOGGER.warning("LOAD SAVED GAME: " + file + " does not load: " + exception.getMessage());
            this.showError(glueContext.getGluText().get(INVALID_ERROR_KEY));

            return;
        }

        GlueGameRequest request = GlueGameRequest.createSavedGame(file, glueContext.getPlayerName(), setup.isExpansion(), setup.getCampaignMission());

        this.leave(() -> glueContext.getNavigator().start(request));
    }

    /**
     * Asks {@code loadCONFIRMDELETE} and deletes the picked saved game.
     */
    private void confirmDelete() {
        Path file = this.getSelectedFile();

        if (file == null) {
            return;
        }

        String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
        String question = this.getContext().getGluText().get(CONFIRM_DELETE_KEY);

        this.showPopup(new GlueMessagePopup(GlueMessagePopup.Kind.OK_CANCEL,
                                            this.getPaletteFolder(),
                                            question,
                                            null,
                                            delete -> {
                                                if (delete) {
                                                    this.deleteSavedGame(file, fileName);
                                                }
                                            }));
    }

    /**
     * Deletes a saved game and refills the list, or shows {@code strDELETE_ERR_s} when the file cannot be deleted.
     *
     * @param file     the saved game file
     * @param fileName the file name shown in the error
     */
    private void deleteSavedGame(@NotNull Path file, @NotNull String fileName) {
        try {
            Files.delete(file);
        } catch (IOException exception) {
            LOGGER.warning("LOAD SAVED GAME: " + file + " cannot be deleted: " + exception.getMessage());
            this.showError(this.getContext().getNetworkText().get(DELETE_ERROR_KEY).replace(NAME_PLACEHOLDER, fileName));

            return;
        }

        this.fillSavedGames();
    }
}
