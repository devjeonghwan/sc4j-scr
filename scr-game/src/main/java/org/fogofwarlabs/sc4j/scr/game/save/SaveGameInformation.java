package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.scr.game.session.setup.PlayerSlots;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Holds the save identity, random state and local lobby and record slots used to match a saved game.
 */
public final class SaveGameInformation {
    /**
     * The offset of the lobby slot DWORD in the game structure.
     */
    private static final int LOBBY_SLOT_OFFSET = 0xC0;

    /**
     * Creates a {@link SaveGameInformation} instance from saved-game file contents.
     *
     * @param fileData the file contents
     * @return the created {@link SaveGameInformation} instance
     * @throws IOException if the saved-game file cannot be parsed
     */
    public static @NotNull SaveGameInformation read(byte @NotNull [] fileData) throws IOException {
        return read(SaveGameFile.parse(fileData));
    }

    /**
     * Creates a {@link SaveGameInformation} instance from a saved game.
     *
     * @param file the saved game
     * @return the created {@link SaveGameInformation} instance
     */
    public static @NotNull SaveGameInformation read(@NotNull SaveGameFile file) {
        int lobbySlot = ByteBuffer.wrap(file.getGameStructure()).order(ByteOrder.LITTLE_ENDIAN).getInt(LOBBY_SLOT_OFFSET);

        return new SaveGameInformation(file.getSaveGameId(), file.getRandomState(), lobbySlot, file.getRecordSlot());
    }


    private final int saveGameId;
    private final int randomState;
    private final int lobbySlot;
    private final int recordSlot;

    /**
     * Creates a {@link SaveGameInformation} instance.
     *
     * @param saveGameId  the save id, the Save command's time DWORD
     * @param randomState the generator state at the save
     * @param lobbySlot   the saving machine's lobby slot, or {@link PlayerSlots#NO_SLOT}
     * @param recordSlot  the saving machine's own record slot, or {@link PlayerSlots#NO_SLOT}
     */
    public SaveGameInformation(int saveGameId, int randomState, int lobbySlot, int recordSlot) {
        this.saveGameId  = saveGameId;
        this.randomState = randomState;
        this.lobbySlot   = lobbySlot;
        this.recordSlot  = recordSlot;
    }

    /**
     * @return the save id, the Save command's time DWORD
     */
    public int getSaveGameId() {
        return this.saveGameId;
    }

    /**
     * @return the generator state at the save
     */
    public int getRandomState() {
        return this.randomState;
    }

    /**
     * @return the saving machine's lobby slot, or {@link PlayerSlots#NO_SLOT}
     */
    public int getLobbySlot() {
        return this.lobbySlot;
    }

    /**
     * @return the saving machine's own record slot, or {@link PlayerSlots#NO_SLOT}
     */
    public int getRecordSlot() {
        return this.recordSlot;
    }
}
