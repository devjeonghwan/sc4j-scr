package org.fogofwarlabs.sc4j.scr.game.player;

/**
 * A run of game-context DWORDs addressed by a native linear index from its start.
 * <p>
 * The per-player count tables lie back to back in the game context. A {@link PlayerUnitCounts} index past the end of its table addresses the DWORDs
 * that follow the table, which another table provides through this interface.
 */
public interface ContextDwordTable {
    /**
     * Returns a DWORD by its native linear index.
     *
     * @param index the DWORD index from the start of the table
     * @return the DWORD
     */
    int getByNativeIndex(int index);

    /**
     * Sets a DWORD by its native linear index.
     *
     * @param index the DWORD index from the start of the table
     * @param value the new DWORD
     */
    void setByNativeIndex(int index, int value);
}
