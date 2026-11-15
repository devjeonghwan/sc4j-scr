package org.fogofwarlabs.sc4j.scr.client.glue.popup;

import org.fogofwarlabs.sc4j.base.log.Logger;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueContext;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.ListBox;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.ScrControl;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lists saved games from a source folder and loads a validated selection, reporting load or cancel to its owner.
 */
public final class GlueLoadPopup extends GlueFramedPopup {
    private static final @NotNull Logger LOGGER = Logger.of(GlueLoadPopup.class);

    private static final @NotNull String DIALOG = "loadgame";
    private static final int LIST = 1;
    private static final int LOAD = -2;
    private static final int CANCEL = -3;
    private static final int FIRST_ROW = 0;
    private static final @NotNull String LOAD_ERROR_KEY = "strERROR_LOADING_SAVE_FILE";

    /**
     * What the dialog loads from.
     */
    public interface Source {
        /**
         * @return the folder the dialog lists
         */
        @NotNull Path getDirectory();

        /**
         * @return the file name extension with its dot
         */
        @NotNull String getExtension();

        /**
         * Loads a saved game: sends the Load Game command in a game, or starts the game from the glue.
         *
         * @param file the picked file, in {@link #getDirectory()}
         */
        void load(@NotNull Path file);
    }


    /**
     * Tells whether a file is a saved game this client loads.
     *
     * @param file the file
     * @return {@code true} if the file parses as a saved game of this client, {@code false} otherwise
     */
    static boolean isLoadable(@NotNull Path file) {
        try {
            SaveGameFile.parse(Files.readAllBytes(file));

            return true;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warning("LOAD DIALOG: " + file + " does not load: " + exception.getMessage());

            return false;
        }
    }

    private final @NotNull Source source;
    private final @NotNull Consumer<Boolean> result;

    private @Nullable ListBox list;
    private @NotNull List<String> names;

    private @Nullable ScrControl loadButton;

    /**
     * Creates a {@link GlueLoadPopup} instance.
     *
     * @param paletteFolder the palette folder of the screen below
     * @param consoleRace   the console race of the game below, or {@code -1} over a glue screen
     * @param source        what the dialog loads from
     * @param result        receives {@code true} once a saved game was picked and loaded, {@code false} on Cancel
     */
    public GlueLoadPopup(@NotNull String paletteFolder, int consoleRace, @NotNull Source source, @NotNull Consumer<Boolean> result) {
        super(DIALOG, paletteFolder, consoleRace);

        this.source = source;
        this.result = result;

        this.list  = null;
        this.names = List.of();

        this.loadButton = null;
    }

    /**
     * Called after the dialog is loaded: hide, disable or fill controls here.
     * <p>
     * The popup lists the source's saved games, selects the first one and enables Load while the list is not empty.
     *
     * @param glueContext the shared services
     * @throws IOException if an asset cannot be read
     */
    @Override
    protected void onOpen(@NotNull GlueContext glueContext) throws IOException {
        this.loadButton = this.bindControl(LOAD);

        super.onOpen(glueContext);

        this.names = GlueSavePopup.listNames(this.source.getDirectory(), this.source.getExtension());

        ListBox files = this.addListBox(LIST);

        files.setItems(this.names);
        this.list = files;
        files.select(this.names.isEmpty() ? ListBox.NO_SELECTION : FIRST_ROW, files.getVisibleRows(glueContext.getCanvas()));
        requireBound(this.loadButton).setEnabled(!this.names.isEmpty());
    }

    /**
     * Called after the user changed the selection of a list or combo box, or the value of a slider.
     * <p>
     * Load is enabled while the list selects a saved game.
     *
     * @param control the control id
     */
    @Override
    protected void onSelectionChanged(int control) {
        ListBox files = this.list;

        if (control == LIST && files != null) {
            requireBound(this.loadButton).setEnabled(files.getSelection() >= 0 && files.getSelection() < this.names.size());
        }
    }

    /**
     * Called when a control is activated by a click, its hotkey, Esc or Enter.
     * <p>
     * Load loads the selected saved game when it parses, and shows the load error otherwise; Cancel closes the popup.
     *
     * @param control the control id
     */
    @Override
    protected void onActivate(int control) {
        if (control == CANCEL) {
            this.finish();
            this.result.accept(false);

            return;
        }

        ListBox files = this.list;

        if (control != LOAD || files == null) {
            return;
        }

        int selection = files.getSelection();

        if (selection < 0 || selection >= this.names.size()) {
            return;
        }

        Path file = this.source.getDirectory().resolve(this.names.get(selection) + this.source.getExtension());

        if (!isLoadable(file)) {
            this.showError(this.getContext().getNetworkText().get(LOAD_ERROR_KEY));

            return;
        }

        LOGGER.info("LOAD DIALOG: " + file);
        this.finish();
        this.source.load(file);
        this.result.accept(true);
    }
}
