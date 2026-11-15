package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;

/**
 * Holds the native race values of the player slots of the SC:R mod as {@code int} constants: the values the SC:R
 * simulation stores and compares, and the values EUD, CHK sections, replays and saved games hold. {@link ScrRaces} holds the
 * engine identities; {@link ScrNativeIds} maps between the two.
 */
public final class ScrRaceIds {
    /**
     * Zerg ({@code 0}).
     */
    public static final int ZERG = 0;
    /**
     * Terran ({@code 1}).
     */
    public static final int TERRAN = 1;
    /**
     * Protoss ({@code 2}).
     */
    public static final int PROTOSS = 2;
    /**
     * Independent ({@code 3}): not a playable race.
     */
    public static final int INDEPENDENT = 3;
    /**
     * Neutral ({@code 4}): not a playable race.
     */
    public static final int NEUTRAL = 4;
    /**
     * User selectable ({@code 5}): the player chooses the race in the lobby.
     */
    public static final int USER_SELECTABLE = 5;
    /**
     * Random ({@code 6}): resolved to a playable race when the game starts.
     */
    public static final int RANDOM = 6;
    /**
     * Inactive ({@code 7}): the slot has no race.
     * <p>
     * In a network lobby the same byte value means a selectable race, which the game start turns into {@link #RANDOM}
     * before it draws the random races.
     */
    public static final int INACTIVE = 7;
    /**
     * The number of playable races, Zerg, Terran and Protoss, the first values.
     */
    public static final int PLAYABLE_COUNT = 3;

    /**
     * The number of values, from {@code 0}.
     */
    private static final int VALUE_COUNT = 8;

    /**
     * @param identifier the value to check
     * @return {@code true} if the value is a race value of a player slot, {@code false} otherwise
     */
    public static boolean isValid(int identifier) {
        return identifier >= 0 && identifier < VALUE_COUNT;
    }

    /**
     * Checks that a value is a race value of a player slot.
     *
     * @param identifier the value to check
     * @return the value
     * @throws IllegalArgumentException if the value is not a race value of a player slot
     */
    public static int requireValid(int identifier) {
        if (!isValid(identifier)) {
            throw Exceptions.newIllegalArgumentException("Invalid race value: {0}", identifier);
        }

        return identifier;
    }


    private ScrRaceIds() {
    }
}
