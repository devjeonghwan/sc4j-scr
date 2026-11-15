package org.fogofwarlabs.sc4j.scr.content;

/**
 * Derives the SC:R flingy type traits the engine motion profile table holds as columns: the only place the native flingy sets live. The importer
 * fills the engine {@code acceleratingTurn} column from {@link #isAcceleratingTurn}; code that reads a flingy without a game content reads the
 * same rule.
 */
public final class ScrFlingyTraits {
    /**
     * The first flingy type of the first range whose turn rate grows each frame it turns.
     */
    private static final int ACCELERATING_TURN_FIRST_FLINGY = 0x8D;
    /**
     * The last flingy type of the first range whose turn rate grows each frame it turns.
     */
    private static final int ACCELERATING_TURN_LAST_FLINGY = 0xAB;
    /**
     * The first flingy type of the second range whose turn rate grows each frame it turns.
     */
    private static final int SECOND_ACCELERATING_TURN_FIRST_FLINGY = 0xC9;
    /**
     * The last flingy type of the second range whose turn rate grows each frame it turns.
     */
    private static final int SECOND_ACCELERATING_TURN_LAST_FLINGY = 0xCE;
    /**
     * The mask of the unsigned WORD flingy type of a unit.
     */
    private static final int WORD_MASK = 0xFFFF;

    /**
     * Checks whether the turn rate of a flingy grows by one each frame it turns: the flingy types of the two native ranges.
     *
     * @param flingyType the flingy type ID, read as an unsigned WORD
     * @return {@code true} if the flingy type is in one of the accelerating-turn ranges, {@code false} otherwise
     */
    public static boolean isAcceleratingTurn(int flingyType) {
        return ((flingyType - ACCELERATING_TURN_FIRST_FLINGY) & WORD_MASK) <= ACCELERATING_TURN_LAST_FLINGY - ACCELERATING_TURN_FIRST_FLINGY ||
               ((flingyType - SECOND_ACCELERATING_TURN_FIRST_FLINGY) & WORD_MASK) <=
               SECOND_ACCELERATING_TURN_LAST_FLINGY - SECOND_ACCELERATING_TURN_FIRST_FLINGY;
    }


    private ScrFlingyTraits() {
    }
}
