package org.fogofwarlabs.sc4j.scr.game.trigger;

/**
 * Names the action types of CHK {@code MBRF} (mission briefing) records.
 * <p>
 * The values index the briefing action table; the names follow StarEdit's briefing action list.
 */
public final class BriefingActionIds {
    /**
     * No action (ends the action list); its table entry is never reached.
     */
    public static final int NO_ACTION = 0;
    /**
     * Wait.
     */
    public static final int WAIT = 1;
    /**
     * Play WAV.
     */
    public static final int PLAY_WAV = 2;
    /**
     * Text Message.
     */
    public static final int TEXT_MESSAGE = 3;
    /**
     * Mission Objectives.
     */
    public static final int MISSION_OBJECTIVES = 4;
    /**
     * Show Portrait.
     */
    public static final int SHOW_PORTRAIT = 5;
    /**
     * Hide Portrait.
     */
    public static final int HIDE_PORTRAIT = 6;
    /**
     * Display Speaking Portrait.
     */
    public static final int DISPLAY_SPEAKING_PORTRAIT = 7;
    /**
     * Transmission.
     */
    public static final int TRANSMISSION = 8;
    /**
     * Skip Tutorial Enabled.
     */
    public static final int SKIP_TUTORIAL_ENABLED = 9;
    /**
     * The number of briefing action types.
     */
    public static final int COUNT = 10;

    private BriefingActionIds() {
    }
}
