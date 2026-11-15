package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.scr.game.unit.Unit;

/**
 * Names the movement states of units ({@link Unit#getMovementState()}), the states of the movement dispatcher
 * {@link UnitMovement#update(UnitMovement.Context, Unit, int, int)}.
 */
public final class MovementStates {
    /**
     * Classifies the unit and selects its first real state.
     */
    public static final int INIT = 0;
    /**
     * Waits until a no-break script section ends, then initializes again.
     */
    public static final int INIT_SEQUENCE = 1;
    /**
     * A unit that does not move.
     */
    public static final int LUMP = 2;
    /**
     * A subunit turret that turns with its target.
     */
    public static final int TURRET = 3;
    /**
     * A unit inside a Bunker, which only turns.
     */
    public static final int BUNKER = 4;
    /**
     * A building that turns but does not move.
     */
    public static final int BUILDING_TURRET = 5;
    /**
     * A hidden unit.
     */
    public static final int HIDDEN = 6;
    /**
     * A flying unit, which moves without paths.
     */
    public static final int FLYER = 7;
    /**
     * The fake flyer state, in which the unit does not move.
     */
    public static final int FAKE_FLYER = 8;
    /**
     * A ground unit at rest that finishes its motion.
     */
    public static final int AT_REST = 9;
    /**
     * A ground unit at rest at its move target.
     */
    public static final int DORMANT = 10;
    /**
     * A ground unit that reached its move target.
     */
    public static final int AT_MOVE_TARGET = 11;
    /**
     * Checks whether the unit stands on a position it cannot hold.
     */
    public static final int CHECK_ILLEGAL = 12;
    /**
     * Moves the unit off an illegal position.
     */
    public static final int MOVE_TO_LEGAL = 13;
    /**
     * An immobile unit that finishes its last motion before becoming a lump.
     */
    public static final int LUMP_WANNABE = 14;
    /**
     * A path search failed; the unit stops and waits.
     */
    public static final int FAILED_PATH = 15;
    /**
     * Retries the path search after a failure.
     */
    public static final int RETRY_PATH = 16;
    /**
     * Starts a path to the move target.
     */
    public static final int START_PATH = 17;
    /**
     * Delays a commanded path start of an iscript-controlled unit.
     */
    public static final int UI_ORDER_DELAY = 18;
    /**
     * Repaths around a collision and turns towards the route.
     */
    public static final int TURN_AND_START = 19;
    /**
     * Turns the unit towards its next waypoint.
     */
    public static final int FACE_TARGET = 20;
    /**
     * Handles a move target that changed while the unit follows a path.
     */
    public static final int NEW_MOVE_TARGET = 21;
    /**
     * Searches another path to the move target.
     */
    public static final int ANOTHER_PATH = 22;
    /**
     * Searches a new path around the last collision.
     */
    public static final int REPATH = 23;
    /**
     * Searches a new path around moving units.
     */
    public static final int REPATH_MOVERS = 24;
    /**
     * Follows the path.
     */
    public static final int FOLLOW_PATH = 25;
    /**
     * Predicts nearby moving units on the path.
     */
    public static final int SCOUT_PATH = 26;
    /**
     * Continues on the path after a scout found an intersection.
     */
    public static final int SCOUT_FREE = 27;
    /**
     * Resolves a collision with a unit.
     */
    public static final int FIX_COLLISION = 28;
    /**
     * Waits for a moving blocker to leave.
     */
    public static final int WAIT_FREE = 29;
    /**
     * Moves on once no unit blocks the next step.
     */
    public static final int GET_FREE = 30;
    /**
     * Prepares a slide along a blocker.
     */
    public static final int SLIDE_PREP = 31;
    /**
     * Slides along a blocker in fixed-point substeps.
     */
    public static final int SLIDE_FREE = 32;
    /**
     * Gives up a blocked slide and repaths.
     */
    public static final int FORCE_MOVE_FREE = 33;
    /**
     * Resolves a collision with the terrain.
     */
    public static final int FIX_TERRAIN = 34;
    /**
     * Slides along the terrain.
     */
    public static final int TERRAIN_SLIDE = 35;

    private MovementStates() {
    }
}
