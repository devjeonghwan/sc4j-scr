package org.fogofwarlabs.sc4j.scr.game.trigger;

/**
 * Names the flag bits of running triggers and of trigger conditions and actions.
 * <p>
 * Values follow the {@code TRIG}/{@code MBRF} record layout of CHK files: the execution flags of a trigger record and the
 * flag byte of each condition and action.
 */
public final class TriggerFlags {
    /**
     * The conditions passed and the actions are being executed; later evaluations resume the actions.
     */
    public static final int EXECUTION_ACTIONS_RUNNING = 0x1;
    /**
     * Starting the actions sets the current player's result to {@link TriggerPlayerResults#SECURED} (the Draw/Defeat
     * handling).
     */
    public static final int EXECUTION_SETS_RESULT = 0x2;
    /**
     * The trigger is preserved: finishing its actions rewinds it instead of disabling it.
     */
    public static final int EXECUTION_PRESERVE = 0x4;
    /**
     * The trigger is disabled and skipped by evaluation.
     */
    public static final int EXECUTION_DISABLED = 0x8;
    /**
     * Interface actions (wait, pause, transmission, sound, text, center view, ping, portrait) are ignored.
     */
    public static final int EXECUTION_IGNORE_INTERFACE_ACTIONS = 0x10;
    /**
     * The trigger has paused the game.
     */
    public static final int EXECUTION_PAUSED_GAME = 0x20;
    /**
     * Wait skipping is disabled for the trigger.
     */
    public static final int EXECUTION_WAIT_SKIPPING_DISABLED = 0x40;
    /**
     * The execution flags a preserved trigger clears when its finished actions rewind.
     */
    public static final int EXECUTION_CLEARED_ON_REWIND = EXECUTION_ACTIONS_RUNNING | EXECUTION_IGNORE_INTERFACE_ACTIONS |
                                                          EXECUTION_WAIT_SKIPPING_DISABLED;

    /**
     * The condition is disabled.
     */
    public static final int CONDITION_DISABLED = 0x2;

    /**
     * The action started (a Wait set it when it began waiting); the running execution keeps this bit per action.
     */
    public static final int ACTION_STARTED = 0x1;
    /**
     * The action is disabled.
     */
    public static final int ACTION_DISABLED = 0x2;
    /**
     * The action's text is always displayed, regardless of the subtitle option.
     */
    public static final int ACTION_ALWAYS_DISPLAY = 0x4;

    private TriggerFlags() {
    }
}
