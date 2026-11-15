package org.fogofwarlabs.sc4j.scr.game.save.section;

import org.fogofwarlabs.sc4j.scr.game.save.SaveFileReader;
import org.fogofwarlabs.sc4j.scr.game.save.SaveFileWriter;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameContext;
import org.fogofwarlabs.sc4j.scr.game.save.SaveSection;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.LocalView;
import org.jetbrains.annotations.NotNull;

/**
 * Writes the local screen coordinates and restores them with {@link LocalView#restoreSavedScreen}.
 */
public final class ScreenPositionSection implements SaveSection {
    /**
     * Creates a {@link ScreenPositionSection} instance.
     */
    public ScreenPositionSection() {
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        LocalView view = context.getGame().getInterfaceLink().getLocalView();

        output.writeDword(view.getScreenX());
        output.writeDword(view.getScreenY());
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        int screenX = input.readDword();
        int screenY = input.readDword();

        context.getGame().getInterfaceLink().getLocalView().restoreSavedScreen(screenX, screenY);
    }
}
