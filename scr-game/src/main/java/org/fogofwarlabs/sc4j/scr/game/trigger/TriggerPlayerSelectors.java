package org.fogofwarlabs.sc4j.scr.game.trigger;

/**
 * Names the player selectors of trigger conditions, actions and owners; selectors {@code 0}-{@code 11} are the players
 * themselves. The names follow StarEdit.
 */
public final class TriggerPlayerSelectors {
    /**
     * No player.
     */
    public static final int NONE = 12;

    /**
     * The player whose triggers run.
     */
    public static final int CURRENT_PLAYER = 13;

    /**
     * The current player's enemies.
     */
    public static final int FOES = 14;

    /**
     * The current player's allies.
     */
    public static final int ALLIES = 15;

    /**
     * The neutral players of the current player.
     */
    public static final int NEUTRAL_PLAYERS = 16;

    /**
     * Every player.
     */
    public static final int ALL_PLAYERS = 17;

    /**
     * The players of force 1; forces 2 to 4 follow.
     */
    public static final int FORCE_1 = 18;

    /**
     * The players of force 2.
     */
    public static final int FORCE_2 = 19;

    /**
     * The players of force 3.
     */
    public static final int FORCE_3 = 20;

    /**
     * The players of force 4.
     */
    public static final int FORCE_4 = 21;

    /**
     * The players without allied victory.
     */
    public static final int NON_ALLIED_VICTORY_PLAYERS = 26;

    /**
     * The number of selectors; higher values are invalid.
     */
    public static final int COUNT = 27;

    private TriggerPlayerSelectors() {
    }
}
