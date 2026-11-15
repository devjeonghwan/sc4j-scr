package org.fogofwarlabs.sc4j.scr.game.state;

/**
 * Serves game-context bytes by their offset: the whole game context, or the tables a storage of the game context keeps.
 */
public interface GameContextBytes {
    /**
     * Reads one byte of the game context.
     *
     * @param contextOffset a game-context offset this instance serves
     * @return the unsigned byte
     */
    int readContextByte(int contextOffset);

    /**
     * Writes one byte of the game context.
     *
     * @param contextOffset a game-context offset this instance serves
     * @param value         the byte in the low eight bits
     */
    void writeContextByte(int contextOffset, int value);
}
