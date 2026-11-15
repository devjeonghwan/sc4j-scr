package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.game.player.Player;

/**
 * Names the pending trigger result bytes of the players ({@link TriggerContext#getPlayerResult}), which become the
 * players' victory states when the results propagate.
 * <p>
 * A result is written unchanged as the player's victory state, so the results that reach a player share the victory
 * state values ({@link #DEFEAT} is {@link Player#VICTORY_STATE_DEFEATED}); {@link #SECURED} only exists while the
 * triggers run.
 */
public final class TriggerPlayerResults {
    /**
     * No result.
     */
    public static final int NONE = 0;

    /**
     * Defeat: the Defeat action and the defeat of the players left when another wins; the defeated victory state.
     */
    public static final int DEFEAT = Player.VICTORY_STATE_DEFEATED;

    /**
     * Victory; results from this one up count as a win when results propagate.
     */
    public static final int VICTORY = 3;

    /**
     * The result a trigger with {@link TriggerFlags#EXECUTION_SETS_RESULT} secures when its actions start: Defeat and
     * Draw fail against it, it counts as a win, and a multiplayer session keeps the player's triggers.
     */
    public static final int SECURED = 4;

    /**
     * Draw, and every initially active player when all of them won.
     */
    public static final int DRAW = 5;

    private TriggerPlayerResults() {
    }
}
