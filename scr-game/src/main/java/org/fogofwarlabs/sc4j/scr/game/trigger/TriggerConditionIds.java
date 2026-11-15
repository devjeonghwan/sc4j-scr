package org.fogofwarlabs.sc4j.scr.game.trigger;

/**
 * Names the trigger condition types of CHK {@code TRIG}/{@code MBRF} records ({@code TriggerDefinition.Condition.getType()}).
 * <p>
 * The names follow StarEdit's condition list.
 */
public final class TriggerConditionIds {
    /**
     * No condition (ends the condition list).
     */
    public static final int NO_CONDITION = 0;
    /**
     * Countdown Timer.
     */
    public static final int COUNTDOWN_TIMER = 1;
    /**
     * Command.
     */
    public static final int COMMAND = 2;
    /**
     * Bring.
     */
    public static final int BRING = 3;
    /**
     * Accumulate.
     */
    public static final int ACCUMULATE = 4;
    /**
     * Kill.
     */
    public static final int KILL = 5;
    /**
     * Command the Most.
     */
    public static final int COMMAND_THE_MOST = 6;
    /**
     * Commands the Most At.
     */
    public static final int COMMAND_THE_MOST_AT = 7;
    /**
     * Most Kills.
     */
    public static final int MOST_KILLS = 8;
    /**
     * Highest Score.
     */
    public static final int HIGHEST_SCORE = 9;
    /**
     * Most Resources.
     */
    public static final int MOST_RESOURCES = 10;
    /**
     * Switch.
     */
    public static final int SWITCH = 11;
    /**
     * Elapsed Time.
     */
    public static final int ELAPSED_TIME = 12;
    /**
     * Mission Briefing (only in briefing triggers).
     */
    public static final int MISSION_BRIEFING = 13;
    /**
     * Opponents.
     */
    public static final int OPPONENTS = 14;
    /**
     * Deaths.
     */
    public static final int DEATHS = 15;
    /**
     * Command the Least.
     */
    public static final int COMMAND_THE_LEAST = 16;
    /**
     * Command the Least At.
     */
    public static final int COMMAND_THE_LEAST_AT = 17;
    /**
     * Least Kills.
     */
    public static final int LEAST_KILLS = 18;
    /**
     * Lowest Score.
     */
    public static final int LOWEST_SCORE = 19;
    /**
     * Least Resources.
     */
    public static final int LEAST_RESOURCES = 20;
    /**
     * Score.
     */
    public static final int SCORE = 21;
    /**
     * Always.
     */
    public static final int ALWAYS = 22;
    /**
     * Never.
     */
    public static final int NEVER = 23;
    /**
     * The number of condition types; higher types are invalid.
     */
    public static final int COUNT = 24;

    private TriggerConditionIds() {
    }
}
