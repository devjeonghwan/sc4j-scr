package org.fogofwarlabs.sc4j.scr.game.state;

import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;

/**
 * Names why a game ended before its own end: the session runs no further frame of that game.
 */
public enum GameEnd {
    /**
     * A trigger accessed an EUD address the game does not serve ({@link TriggerContext#hasEudFailure()}): the frame of
     * the access completes and the game takes no further frame; the viewer ends the session once its error notice closes.
     */
    EUD_FAILURE,
    /**
     * A Restart Game command ran outside a network session ({@link Game#end(GameEnd)}): the commands and the turn step of its game
     * loop complete, and the frame does not run. A replay's restart then reloads its scenario from the replay file, which
     * fails, so replay playback ends at that frame and the menus show the scenario load error.
     */
    RESTART
}
