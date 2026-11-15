package org.fogofwarlabs.sc4j.scr.game.save.section;

import org.fogofwarlabs.sc4j.scr.game.save.SaveFileReader;
import org.fogofwarlabs.sc4j.scr.game.save.SaveFileWriter;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameContext;
import org.fogofwarlabs.sc4j.scr.game.save.SaveSection;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameContext;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.GameStructureView;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes and restores the game context through {@link GameStructureView}.
 * <p>
 * The writer updates elapsed seconds using the frame duration and {@link GameContext#getElapsedSecondsBase()}.
 * A restore writes the saved bytes through their owners and establishes the saved elapsed-time base.
 */
public final class GameStructureSection implements SaveSection {
    /**
     * The elapsed seconds DWORD.
     */
    private static final int ELAPSED_SECONDS = 0x150;
    private static final int MILLISECONDS_PER_SECOND = 1000;
    private static final int BYTE_MASK = 0xFF;

    /**
     * Stores a saved game structure into the started game through its owners.
     *
     * @param context   the started game
     * @param structure the game structure bytes
     */
    public static void restore(@NotNull SaveGameContext context, byte @NotNull [] structure) {
        Game game = context.getGame();
        ByteBuffer fields = ByteBuffer.wrap(structure).order(ByteOrder.LITTLE_ENDIAN);

        game.getGameContext().markContextSnapshotsTaken();

        GameStructureView view = new GameStructureView(game, game.getTriggerContext());

        for (int offset = 0; offset < structure.length; offset++) {
            view.writeContextByte(offset, structure[offset] & BYTE_MASK);
        }

        game.getGameContext().setElapsedBase(fields.getInt(ELAPSED_SECONDS), fields.getInt(GameStructureView.FRAME_OFFSET));
    }


    /**
     * Creates a {@link GameStructureSection} instance.
     */
    public GameStructureSection() {
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
        long frames = game.getCurrentFrame() - game.getGameContext().getElapsedFrameBase();
        int elapsed = game.getGameContext().getElapsedSecondsBase() +
                      (int) (frames * context.getRuntime().getFrameDuration() / MILLISECONDS_PER_SECOND);
        byte[] structure = new byte[GameContext.CONTEXT_SIZE];

        ByteBuffer.wrap(game.getGameContext().getContextStorage()).order(ByteOrder.LITTLE_ENDIAN).putInt(ELAPSED_SECONDS, elapsed);

        GameStructureView view = new GameStructureView(game, game.getTriggerContext());

        for (int offset = 0; offset < structure.length; offset++) {
            structure[offset] = (byte) view.readContextByte(offset);
        }

        output.writeSection(structure);
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        restore(context, input.readSection(GameContext.CONTEXT_SIZE));
    }
}
