package org.fogofwarlabs.sc4j.scr.data.type.constant;

/**
 * Names interface button sets following the default unit-type button sets.
 */
public final class ButtonSetIds {
    /**
     * Button set {@code 228}: the empty set (no buttons).
     */
    public static final int EMPTY = 228;
    /**
     * Button set {@code 229}: the Cancel button of a targeting command.
     */
    public static final int CANCEL_TARGETING = 229;
    /**
     * Button set {@code 230}: the Cancel button of a building placement.
     */
    public static final int CANCEL_PLACEMENT = 230;
    /**
     * Button set {@code 231}: the Cancel button of a construction.
     */
    public static final int CANCEL_CONSTRUCTION = 231;
    /**
     * Button set {@code 232}: the Set Rally Point and Cancel buttons of a construction.
     */
    public static final int CANCEL_CONSTRUCTION_WITH_RALLY = 232;
    /**
     * Button set {@code 233}: the Cancel button of a building under construction.
     */
    public static final int CANCEL_BUILDING = 233;
    /**
     * Button set {@code 234}: the Set Rally Point and Cancel buttons of a building under construction.
     */
    public static final int CANCEL_BUILDING_WITH_RALLY = 234;
    /**
     * Button set {@code 235}: the Cancel Infestation button.
     */
    public static final int CANCEL_INFESTATION = 235;
    /**
     * Button set {@code 236}: the Select Larva, Set Rally Point and Cancel buttons of a morphing Zerg building.
     */
    public static final int CANCEL_MORPH = 236;
    /**
     * Button set {@code 237}: the Cancel Nuclear Strike button.
     */
    public static final int CANCEL_NUCLEAR_STRIKE = 237;
    /**
     * Button set {@code 238}: the basic structures a Drone mutates into.
     */
    public static final int ZERG_BASIC_STRUCTURES = 238;
    /**
     * Button set {@code 239}: the basic structures an SCV builds.
     */
    public static final int TERRAN_BASIC_STRUCTURES = 239;
    /**
     * Button set {@code 240}: the basic structures a Probe warps in.
     */
    public static final int PROTOSS_BASIC_STRUCTURES = 240;
    /**
     * Button set {@code 241}: the advanced structures a Drone mutates into.
     */
    public static final int ZERG_ADVANCED_STRUCTURES = 241;
    /**
     * Button set {@code 242}: the advanced structures an SCV builds.
     */
    public static final int TERRAN_ADVANCED_STRUCTURES = 242;
    /**
     * Button set {@code 243}: the advanced structures a Probe warps in.
     */
    public static final int PROTOSS_ADVANCED_STRUCTURES = 243;
    /**
     * Button set {@code 244}: the move, stop, attack, patrol and hold buttons of a mixed selection.
     */
    public static final int MIXED_UNITS = 244;
    /**
     * Button set {@code 245}: the move, stop, attack, gather and return cargo buttons of a mixed selection of workers.
     */
    public static final int MIXED_WORKERS = 245;
    /**
     * Button set {@code 246}: the move, stop, attack, patrol, hold, cloak and decloak buttons of a mixed selection of cloakable units.
     */
    public static final int MIXED_CLOAKABLE_UNITS = 246;
    /**
     * Button set {@code 247}: the move, stop, attack, patrol, hold, burrow and unburrow buttons of a mixed selection of Zerg
     * ground units with a burrowable unit.
     */
    public static final int MIXED_BURROWABLE_UNITS = 247;

    /**
     * Button set {@code 248}: the replay controls while playback is paused.
     */
    public static final int REPLAY_PAUSED = 248;
    /**
     * Button set {@code 249}: the replay controls while playback is running.
     */
    public static final int REPLAY_PLAYING = 249;

    private ButtonSetIds() {
    }
}
