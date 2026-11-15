package org.fogofwarlabs.sc4j.scr.game.trigger;

/**
 * Names the trigger action types of CHK {@code TRIG}/{@code MBRF} records ({@code TriggerDefinition.Action.getType()}).
 * <p>
 * The names follow StarEdit's action list.
 */
public final class TriggerActionIds {
    /**
     * No action (ends the action list).
     */
    public static final int NO_ACTION = 0;
    /**
     * Victory.
     */
    public static final int VICTORY = 1;
    /**
     * Defeat.
     */
    public static final int DEFEAT = 2;
    /**
     * Preserve Trigger.
     */
    public static final int PRESERVE_TRIGGER = 3;
    /**
     * Wait.
     */
    public static final int WAIT = 4;
    /**
     * Pause Game.
     */
    public static final int PAUSE_GAME = 5;
    /**
     * Unpause Game.
     */
    public static final int UNPAUSE_GAME = 6;
    /**
     * Transmission.
     */
    public static final int TRANSMISSION = 7;
    /**
     * Play WAV.
     */
    public static final int PLAY_WAV = 8;
    /**
     * Display Text Message.
     */
    public static final int DISPLAY_TEXT_MESSAGE = 9;
    /**
     * Center View.
     */
    public static final int CENTER_VIEW = 10;
    /**
     * Create Unit with Properties.
     */
    public static final int CREATE_UNIT_WITH_PROPERTIES = 11;
    /**
     * Set Mission Objectives.
     */
    public static final int SET_MISSION_OBJECTIVES = 12;
    /**
     * Set Switch.
     */
    public static final int SET_SWITCH = 13;
    /**
     * Set Countdown Timer.
     */
    public static final int SET_COUNTDOWN_TIMER = 14;
    /**
     * Run AI Script.
     */
    public static final int RUN_AI_SCRIPT = 15;
    /**
     * Run AI Script At Location.
     */
    public static final int RUN_AI_SCRIPT_AT_LOCATION = 16;
    /**
     * Leader Board (Control).
     */
    public static final int LEADERBOARD_CONTROL = 17;
    /**
     * Leader Board (Control At Location).
     */
    public static final int LEADERBOARD_CONTROL_AT_LOCATION = 18;
    /**
     * Leader Board (Resources).
     */
    public static final int LEADERBOARD_RESOURCES = 19;
    /**
     * Leader Board (Kills).
     */
    public static final int LEADERBOARD_KILLS = 20;
    /**
     * Leader Board (Points).
     */
    public static final int LEADERBOARD_POINTS = 21;
    /**
     * Kill Unit.
     */
    public static final int KILL_UNIT = 22;
    /**
     * Kill Unit At Location.
     */
    public static final int KILL_UNIT_AT_LOCATION = 23;
    /**
     * Remove Unit.
     */
    public static final int REMOVE_UNIT = 24;
    /**
     * Remove Unit At Location.
     */
    public static final int REMOVE_UNIT_AT_LOCATION = 25;
    /**
     * Set Resources.
     */
    public static final int SET_RESOURCES = 26;
    /**
     * Set Score.
     */
    public static final int SET_SCORE = 27;
    /**
     * Minimap Ping.
     */
    public static final int MINIMAP_PING = 28;
    /**
     * Talking Portrait.
     */
    public static final int TALKING_PORTRAIT = 29;
    /**
     * Mute Unit Speech.
     */
    public static final int MUTE_UNIT_SPEECH = 30;
    /**
     * Unmute Unit Speech.
     */
    public static final int UNMUTE_UNIT_SPEECH = 31;
    /**
     * Leaderboard Computer Players.
     */
    public static final int LEADERBOARD_COMPUTER_PLAYERS = 32;
    /**
     * Leader Board Goal (Control).
     */
    public static final int LEADERBOARD_GOAL_CONTROL = 33;
    /**
     * Leader Board Goal (Control At Location).
     */
    public static final int LEADERBOARD_GOAL_CONTROL_AT_LOCATION = 34;
    /**
     * Leader Board Goal (Resources).
     */
    public static final int LEADERBOARD_GOAL_RESOURCES = 35;
    /**
     * Leader Board Goal (Kills).
     */
    public static final int LEADERBOARD_GOAL_KILLS = 36;
    /**
     * Leader Board Goal (Points).
     */
    public static final int LEADERBOARD_GOAL_POINTS = 37;
    /**
     * Move Location.
     */
    public static final int MOVE_LOCATION = 38;
    /**
     * Move Unit.
     */
    public static final int MOVE_UNIT = 39;
    /**
     * Leader Board (Greed).
     */
    public static final int LEADERBOARD_GREED = 40;
    /**
     * Set Next Scenario.
     */
    public static final int SET_NEXT_SCENARIO = 41;
    /**
     * Set Doodad State.
     */
    public static final int SET_DOODAD_STATE = 42;
    /**
     * Set Invincibility.
     */
    public static final int SET_INVINCIBILITY = 43;
    /**
     * Create Unit.
     */
    public static final int CREATE_UNIT = 44;
    /**
     * Set Deaths.
     */
    public static final int SET_DEATHS = 45;
    /**
     * Order.
     */
    public static final int ORDER = 46;
    /**
     * Comment.
     */
    public static final int COMMENT = 47;
    /**
     * Give Units to Player.
     */
    public static final int GIVE_UNITS_TO_PLAYER = 48;
    /**
     * Modify Unit Hit Points.
     */
    public static final int MODIFY_UNIT_HIT_POINTS = 49;
    /**
     * Modify Unit Energy.
     */
    public static final int MODIFY_UNIT_ENERGY = 50;
    /**
     * Modify Unit Shield Points.
     */
    public static final int MODIFY_UNIT_SHIELD_POINTS = 51;
    /**
     * Modify Unit Resource Amount.
     */
    public static final int MODIFY_UNIT_RESOURCE_AMOUNT = 52;
    /**
     * Modify Unit Hangar Count.
     */
    public static final int MODIFY_UNIT_HANGAR_COUNT = 53;
    /**
     * Pause Timer.
     */
    public static final int PAUSE_TIMER = 54;
    /**
     * Unpause Timer.
     */
    public static final int UNPAUSE_TIMER = 55;
    /**
     * Draw.
     */
    public static final int DRAW = 56;
    /**
     * Set Alliance Status.
     */
    public static final int SET_ALLIANCE_STATUS = 57;
    /**
     * Disable Debug Mode.
     */
    public static final int DISABLE_DEBUG_MODE = 58;
    /**
     * Enable Debug Mode.
     */
    public static final int ENABLE_DEBUG_MODE = 59;
    /**
     * The first action type without a handler: such actions succeed without effect.
     */
    public static final int FIRST_WITHOUT_HANDLER = 60;

    private TriggerActionIds() {
    }
}
