package org.fogofwarlabs.sc4j.scr.game.save.section;

import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.save.*;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes selected unit handles for the playable players and restores them with checked handle resolution
 * ({@link SaveReferences#decodeLiveUnit}).
 */
public final class SelectionSection implements SaveSection {
    private static final int SIZE = PlayerTable.PLAYABLE_PLAYER_SLOTS * UnitSelection.MAXIMUM_SELECTED * Integer.BYTES;

    /**
     * Creates a {@link SelectionSection} instance.
     */
    public SelectionSection() {
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        Game game = context.getGame();
        UnitSelection selection = SaveGameUtils.require(game.getSelection(), "selection");
        UnitPool pool = SaveGameUtils.require(game.getUnitSystem().getUnitPool(), "unit pool");
        ByteBuffer handles = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
            for (int position = 0; position < UnitSelection.MAXIMUM_SELECTED; position++) {
                int poolIndex = selection.getSelectedPoolIndexRaw(player, position);

                handles.putInt(poolIndex == 0 ? 0 : pool.getHandle(poolIndex));
            }
        }

        output.writeSection(handles.array());
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        Game game = context.getGame();
        UnitSelection selection = SaveGameUtils.require(game.getSelection(), "selection");
        SaveReferences references = new SaveReferences(game);
        ByteBuffer handles = ByteBuffer.wrap(input.readSection(SIZE)).order(ByteOrder.LITTLE_ENDIAN);

        for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
            for (int position = 0; position < UnitSelection.MAXIMUM_SELECTED; position++) {
                Unit unit = references.decodeLiveUnit(handles.getInt());

                selection.setSelectedPoolIndexRaw(player, position, unit == null ? 0 : unit.getIndex());
            }
        }
    }
}
