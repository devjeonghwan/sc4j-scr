package org.fogofwarlabs.sc4j.scr.client.glue.lobby;

import org.fogofwarlabs.sc4j.scr.client.glue.GlueContext;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueGameRequest;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * Lists local replay files and starts the chosen replay; game-type and slot controls are hidden.
 */
public final class ReplayListScreen extends FileBrowserScreen {
    private static final int GAME_TYPE_LABEL = 38;
    private static final int GAME_TYPE = 17;
    private static final int SLOT_PANEL = 19;

    /**
     * Creates a {@link ReplayListScreen} instance.
     */
    public ReplayListScreen() {
        super(List.of(REPLAY_EXTENSION));
    }

    /**
     * Called after the dialog is loaded: hide, disable or fill controls here.
     * <p>
     * Opens the replay list and hides the game-type and player-slot controls.
     *
     * @param glueContext the shared services
     */
    @Override
    protected void onOpen(@NotNull GlueContext glueContext) {
        super.onOpen(glueContext);
        this.bindControl(GAME_TYPE_LABEL).setVisible(false);
        this.bindControl(GAME_TYPE).setVisible(false);
        this.bindControl(SLOT_PANEL).setVisible(false);
    }

    /**
     * @param glueContext the shared services
     * @return the root folder of the list
     */
    @Override
    protected @NotNull Path getRoot(@NotNull GlueContext glueContext) {
        return glueContext.getFolders().getReplayDirectory();
    }

    /**
     * Called when Ok is chosen on a file.
     * <p>
     * Starts the chosen replay with the current character name and game edition.
     *
     * @param file the file
     */
    @Override
    protected void onFileChosen(@NotNull Path file) {
        GlueContext glueContext = this.getContext();
        GlueGameRequest request = GlueGameRequest.createReplay(file, glueContext.getPlayerName(), glueContext.isExpansion());

        this.leave(() -> glueContext.getNavigator().start(request));
    }
}
