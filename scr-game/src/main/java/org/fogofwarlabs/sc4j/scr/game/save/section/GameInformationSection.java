package org.fogofwarlabs.sc4j.scr.game.save.section;

import org.fogofwarlabs.sc4j.scr.game.save.SaveFileReader;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameUtils;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Builds and reads the fixed saved-game information section.
 * <p>
 * Layout: WORD format {@link #FORMAT} ({@code 109}), WORD {@link #VERSION} ({@code 4}), DWORD the random state, four
 * zero bytes, then the {@code 0x8D}-byte game information (the replay header's game information bytes) as the writer
 * leaves it: its first DWORD zero, the save id (the Save command's time DWORD), two bytes with the number of players in
 * the session, a cleared DWORD and the number of computer records among players {@code 0}..{@code 7}; the rest of the
 * section is zero.
 */
public final class GameInformationSection {
    /**
     * The section size.
     */
    public static final int SIZE = 0xB5;
    /**
     * The save format WORD.
     */
    public static final int FORMAT = 109;
    /**
     * The save version WORD the writer stores.
     */
    public static final int VERSION = 4;
    /**
     * The offset of the game information copy in the section.
     */
    public static final int GAME_INFORMATION = 0x0C;
    /**
     * The size of the game information.
     */
    public static final int GAME_INFORMATION_SIZE = 0x8D;
    /**
     * The offset of the game information in the replay header.
     */
    public static final int HEADER_GAME_INFORMATION = 0x14;

    private static final int FORMAT_OFFSET = 0x00;
    private static final int VERSION_OFFSET = 0x02;
    private static final int RANDOM_STATE = 0x04;
    private static final int SAVE_STATE = GAME_INFORMATION;
    private static final int SAVE_GAME_ID = GAME_INFORMATION + 0x1C;
    private static final int SESSION_PLAYERS = GAME_INFORMATION + 0x24;
    private static final int SESSION_PLAYERS_COPY = GAME_INFORMATION + 0x25;
    private static final int CLEARED_DWORD = GAME_INFORMATION + 0x2C;
    private static final int COMPUTER_RECORDS = GAME_INFORMATION + 0x33;
    private static final int WORD_MASK = 0xFFFF;

    /**
     * Builds the section of a running game.
     *
     * @param game           the game
     * @param saveGameId     the save id
     * @param sessionPlayers the number of players in the session
     * @param computers      the number of computer records among players {@code 0}..{@code 7}
     * @return the section bytes
     */
    public static byte @NotNull [] write(@NotNull Game game, int saveGameId, int sessionPlayers, int computers) {
        ByteBuffer section = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
        byte[] header = SaveGameUtils.require(game.getGameContext().getGameHeaderSection(), "game header");

        section.putShort(FORMAT_OFFSET, (short) FORMAT);
        section.putShort(VERSION_OFFSET, (short) VERSION);
        section.putInt(RANDOM_STATE, game.getRandom().getState());
        section.put(GAME_INFORMATION, header, HEADER_GAME_INFORMATION, GAME_INFORMATION_SIZE);
        section.putInt(SAVE_STATE, 0);
        section.putInt(SAVE_GAME_ID, saveGameId);
        section.put(SESSION_PLAYERS, (byte) sessionPlayers);
        section.put(SESSION_PLAYERS_COPY, (byte) sessionPlayers);
        section.putInt(CLEARED_DWORD, 0);
        section.put(COMPUTER_RECORDS, (byte) computers);

        return section.array();
    }

    /**
     * Reads the random state of a section.
     *
     * @param section the section bytes
     * @return the random state at the save
     */
    public static int readRandomState(byte @NotNull [] section) {
        return ByteBuffer.wrap(section).order(ByteOrder.LITTLE_ENDIAN).getInt(RANDOM_STATE);
    }

    /**
     * Reads the save id of a section.
     *
     * @param section the section bytes
     * @return the save id
     */
    public static int readSaveGameId(byte @NotNull [] section) {
        return ByteBuffer.wrap(section).order(ByteOrder.LITTLE_ENDIAN).getInt(SAVE_GAME_ID);
    }

    /**
     * Requires the supported format and exact version.
     *
     * @param section the section bytes
     * @throws UncheckedIOException if the section has another format or version
     */
    public static void requireVersion(byte @NotNull [] section) {
        ByteBuffer buffer = ByteBuffer.wrap(section).order(ByteOrder.LITTLE_ENDIAN);
        int format = buffer.getShort(FORMAT_OFFSET) & WORD_MASK;
        int version = buffer.getShort(VERSION_OFFSET) & WORD_MASK;

        if (format != FORMAT || version != VERSION) {
            throw SaveFileReader.createFailure("The saved game has format {0} version {1}; only {2} version {3} is read.",
                                               format, version,
                                               FORMAT, VERSION);
        }
    }


    private GameInformationSection() {
    }
}
