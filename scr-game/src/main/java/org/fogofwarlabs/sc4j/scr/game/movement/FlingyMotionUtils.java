package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.engine.entity.MovingEntity;
import org.fogofwarlabs.sc4j.engine.math.IntegerMathUtils;
import org.fogofwarlabs.sc4j.engine.movement.MotionProfileProperties;
import org.fogofwarlabs.sc4j.engine.movement.MotionStep;
import org.fogofwarlabs.sc4j.engine.movement.MovementFlags;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.jetbrains.annotations.NotNull;

/**
 * Applies the flingy motion of the SC:R units and bullets: the direction toward the waypoint, the turn, the speed and the prospective position
 * of the next movement step, over the motion fields of the engine {@link MovingEntity} that the SC:R unit and bullet keep at their native
 * widths (directions, turn rate, movement control and flags in eight bits, the acceleration and the map coordinates of the destination,
 * waypoint and direction target in sixteen bits).
 * <p>
 * Directions have {@code 256} steps per full turn, growing clockwise from up; speeds and exact positions are fixed-point values with eight
 * fraction bits; the destination, waypoint and direction target are sixteen-bit map coordinates, read as signed values. The arithmetic is
 * {@code int} arithmetic and wraps like it.
 * <p>
 * Determinism contract: every operation reads and writes only the moved object and the step it is given, draws no random value and allocates
 * nothing.
 */
public final class FlingyMotionUtils {
    /**
     * The movement control of an object that accelerates, brakes and turns by its flingy type.
     */
    public static final int CONTROL_MOTION_PROFILE = ScrFlingyTypeView.MOVEMENT_CONTROL_FLINGY;
    /**
     * The movement control of an object that moves by its flingy type but stops at once at its destination.
     */
    public static final int CONTROL_INSTANT_STOP = ScrFlingyTypeView.MOVEMENT_CONTROL_PARTIALLY_MOBILE;
    /**
     * The movement control of an object whose speed comes from its iscript (the Walking animation).
     */
    public static final int CONTROL_ANIMATION = ScrFlingyTypeView.MOVEMENT_CONTROL_ISCRIPT;

    /**
     * A thirty-second of a turn in direction steps ({@code 256} per full turn).
     */
    public static final int DIRECTION_THIRTY_SECOND_TURN = 8;
    /**
     * An eighth of a turn in direction steps.
     */
    public static final int DIRECTION_EIGHTH_TURN = 32;
    /**
     * A quarter of a turn in direction steps.
     */
    public static final int DIRECTION_QUARTER_TURN = 64;
    /**
     * Half a turn in direction steps.
     */
    public static final int DIRECTION_HALF_TURN = 128;
    /**
     * A full turn in direction steps.
     */
    public static final int DIRECTION_FULL_TURN = 256;
    /**
     * The mask of a direction.
     */
    public static final int DIRECTION_MASK = DIRECTION_FULL_TURN - 1;

    /**
     * The direction up.
     */
    public static final int DIRECTION_UP = 0;
    /**
     * The direction right, a quarter turn clockwise from up.
     */
    public static final int DIRECTION_RIGHT = DIRECTION_UP + DIRECTION_QUARTER_TURN;
    /**
     * The direction down, half a turn from up.
     */
    public static final int DIRECTION_DOWN = DIRECTION_UP + DIRECTION_HALF_TURN;
    /**
     * The direction left, three quarters of a turn clockwise from up.
     */
    public static final int DIRECTION_LEFT = DIRECTION_DOWN + DIRECTION_QUARTER_TURN;

    /**
     * The quadrant ({@code direction / DIRECTION_QUARTER_TURN}) of the directions from up to right.
     */
    public static final int QUADRANT_UP_RIGHT = 0;
    /**
     * The quadrant of the directions from right to down.
     */
    public static final int QUADRANT_DOWN_RIGHT = 1;
    /**
     * The quadrant of the directions from down to left.
     */
    public static final int QUADRANT_DOWN_LEFT = 2;
    /**
     * The quadrant of the directions from left to up.
     */
    public static final int QUADRANT_UP_LEFT = 3;

    /**
     * An object with {@link #CONTROL_MOTION_PROFILE} slower than this stops at once at its destination unless it follows a path step or continues
     * through the destination.
     */
    public static final int INSTANT_STOP_SPEED = 192;
    /**
     * An object at least this far in pixels from its waypoint accelerates without the turn test.
     */
    public static final int BRAKING_CHECK_DISTANCE = 32;
    /**
     * The braking distance of the turn test is the frames-to-turn distance times this, halved.
     */
    public static final int BRAKING_MARGIN_FACTOR = 3;
    /**
     * An object that is not animation-controlled turns by its turn rate shifted right by this (half the turn rate).
     */
    public static final int MOTION_PROFILE_TURN_RATE_SHIFT = 1;
    /**
     * The shift of the vertical component (the high half) of a packed direction vector ({@link #calculateDirectionVector}).
     */
    public static final int DIRECTION_VECTOR_VERTICAL_SHIFT = Integer.SIZE;

    /**
     * The mask of an unsigned sixteen-bit map coordinate.
     */
    private static final int COORDINATE_MASK = 0xFFFF;
    /**
     * The mask of the low half of a packed direction vector.
     */
    private static final long LOW_HALF_MASK = 0xFFFFFFFFL;
    /**
     * The fraction bits of the fixed-point speeds, exact positions and direction vector components.
     */
    private static final int FIXED_POINT_SHIFT = 8;
    /**
     * The shift of a slope: the vertical delta times {@code 256} divided by the horizontal delta.
     */
    private static final int SLOPE_SHIFT = 8;
    /**
     * The shift of the vertical coordinate in a packed position.
     */
    private static final int POSITION_VERTICAL_SHIFT = Short.SIZE;
    /**
     * The frames-to-turn estimate of the turn test counts the remaining turn times this, divided by the turn rate.
     */
    private static final int TURN_FRAMES_FACTOR = 2;
    /**
     * The braking distance of the turn test is shifted right by this after the margin factor (halved).
     */
    private static final int BRAKING_MARGIN_SHIFT = 1;
    /**
     * The calculated stopping distance is the squared speed divided by this times the acceleration.
     */
    private static final int STOPPING_DISTANCE_ACCELERATION_FACTOR = 2;
    /**
     * The direction search shifts the sum of its lower and upper bounds right by this to get the middle threshold (half the sum).
     */
    private static final int MIDPOINT_SHIFT = 1;
    /**
     * The entries of one direction in {@link #DIRECTION_COMPONENTS}: the horizontal, then the vertical component.
     */
    private static final int DIRECTION_COMPONENT_STRIDE = 2;
    /**
     * The offset of the vertical component of a direction in {@link #DIRECTION_COMPONENTS}.
     */
    private static final int VERTICAL_COMPONENT = 1;

    /**
     * The unsigned slope thresholds (the vertical delta times {@code 256} divided by the horizontal delta) of the directions of a quarter turn, one
     * per direction step; the last entry accepts every slope.
     */
    private static final int @NotNull [] DIRECTION_THRESHOLDS = {
            7, 13, 19, 26, 32, 38, 45, 51, 58, 65, 71, 78, 85, 92, 99, 107,
            114, 122, 129, 137, 146, 154, 163, 172, 181, 190, 200, 211, 221, 233, 244, 256,
            269, 283, 297, 312, 329, 346, 364, 384, 405, 428, 452, 479, 509, 542, 578, 619,
            664, 716, 775, 844, 926, 1023, 1141, 1287, 1476, 1726, 2076, 2600, 3471, 5211, 10429, -1
    };
    /**
     * The direction vector table: horizontal and vertical components of each of the {@code 256} directions as consecutive pairs, with eight
     * fraction bits.
     */
    private static final int @NotNull [] DIRECTION_COMPONENTS = {
            0, -256, 6, -256, 13, -256, 19, -255, 25, -255, 31, -254, 38, -253, 44, -252,
            50, -251, 56, -250, 62, -248, 68, -247, 74, -245, 80, -243, 86, -241, 92, -239,
            98, -237, 104, -234, 109, -231, 115, -229, 121, -226, 126, -223, 132, -220, 137, -216,
            142, -213, 147, -209, 152, -206, 157, -202, 162, -198, 167, -194, 172, -190, 177, -185,
            181, -181, 185, -177, 190, -172, 194, -167, 198, -162, 202, -157, 206, -152, 209, -147,
            213, -142, 216, -137, 220, -132, 223, -126, 226, -121, 229, -115, 231, -109, 234, -104,
            237, -98, 239, -92, 241, -86, 243, -80, 245, -74, 247, -68, 248, -62, 250, -56,
            251, -50, 252, -44, 253, -38, 254, -31, 255, -25, 255, -19, 256, -13, 256, -6,
            256, 0, 256, 6, 256, 13, 255, 19, 255, 25, 254, 31, 253, 38, 252, 44,
            251, 50, 250, 56, 248, 62, 247, 68, 245, 74, 243, 80, 241, 86, 239, 92,
            237, 98, 234, 104, 231, 109, 229, 115, 226, 121, 223, 126, 220, 132, 216, 137,
            213, 142, 209, 147, 206, 152, 202, 157, 198, 162, 194, 167, 190, 172, 185, 177,
            181, 181, 177, 185, 172, 190, 167, 194, 162, 198, 157, 202, 152, 206, 147, 209,
            142, 213, 137, 216, 132, 220, 126, 223, 121, 226, 115, 229, 109, 231, 104, 234,
            98, 237, 92, 239, 86, 241, 80, 243, 74, 245, 68, 247, 62, 248, 56, 250,
            50, 251, 44, 252, 38, 253, 31, 254, 25, 255, 19, 255, 13, 256, 6, 256,
            0, 256, -6, 256, -13, 256, -19, 255, -25, 255, -31, 254, -38, 253, -44, 252,
            -50, 251, -56, 250, -62, 248, -68, 247, -74, 245, -80, 243, -86, 241, -92, 239,
            -98, 237, -104, 234, -109, 231, -115, 229, -121, 226, -126, 223, -132, 220, -137, 216,
            -142, 213, -147, 209, -152, 206, -157, 202, -162, 198, -167, 194, -172, 190, -177, 185,
            -181, 181, -185, 177, -190, 172, -194, 167, -198, 162, -202, 157, -206, 152, -209, 147,
            -213, 142, -216, 137, -220, 132, -223, 126, -226, 121, -229, 115, -231, 109, -234, 104,
            -237, 98, -239, 92, -241, 86, -243, 80, -245, 74, -247, 68, -248, 62, -250, 56,
            -251, 50, -252, 44, -253, 38, -254, 31, -255, 25, -255, 19, -256, 13, -256, 6,
            -256, 0, -256, -6, -256, -13, -255, -19, -255, -25, -254, -31, -253, -38, -252, -44,
            -251, -50, -250, -56, -248, -62, -247, -68, -245, -74, -243, -80, -241, -86, -239, -92,
            -237, -98, -234, -104, -231, -109, -229, -115, -226, -121, -223, -126, -220, -132, -216, -137,
            -213, -142, -209, -147, -206, -152, -202, -157, -198, -162, -194, -167, -190, -172, -185, -177,
            -181, -181, -177, -185, -172, -190, -167, -194, -162, -198, -157, -202, -152, -206, -147, -209,
            -142, -213, -137, -216, -132, -220, -126, -223, -121, -226, -115, -229, -109, -231, -104, -234,
            -98, -237, -92, -239, -86, -241, -80, -243, -74, -245, -68, -247, -62, -248, -56, -250,
            -50, -251, -44, -252, -38, -253, -31, -254, -25, -255, -19, -255, -13, -256, -6, -256
    };

    /**
     * Calculates the direction table vector of a direction scaled by a distance; the products wrap like {@code int} arithmetic.
     *
     * @param direction the direction, read modulo {@code 256}
     * @param distance  the signed multiplier
     * @return the horizontal component in the low half and the vertical component in the high half
     */
    public static long calculateDirectionVector(int direction, int distance) {
        int entry = (direction & DIRECTION_MASK) * DIRECTION_COMPONENT_STRIDE;
        int horizontal = (DIRECTION_COMPONENTS[entry] * distance) >> FIXED_POINT_SHIFT;
        int vertical = (DIRECTION_COMPONENTS[entry + VERTICAL_COMPONENT] * distance) >> FIXED_POINT_SHIFT;

        return (horizontal & LOW_HALF_MASK) | (long) vertical << DIRECTION_VECTOR_VERTICAL_SHIFT;
    }

    /**
     * Calculates the direction of a pixel delta through the direction threshold table: up for a horizontal delta of {@code 0} with a non-positive
     * vertical delta, down for a positive one.
     *
     * @param horizontal the horizontal pixel delta
     * @param vertical   the vertical pixel delta
     * @return the direction, {@code 0..255}
     */
    public static int calculateDirection(int horizontal, int vertical) {
        if (horizontal == 0) {
            return vertical > 0 ? DIRECTION_DOWN : DIRECTION_UP;
        }

        int slope = (vertical << SLOPE_SHIFT) / horizontal;
        int magnitude = Math.abs(slope);
        int lower = 0;
        int upper = DIRECTION_QUARTER_TURN;

        while (lower != upper) {
            int middle = (lower + upper) >>> MIDPOINT_SHIFT;

            if (Integer.compareUnsigned(magnitude, DIRECTION_THRESHOLDS[middle]) <= 0) {
                upper = middle;
            } else {
                lower = middle + 1;
            }
        }

        return ((slope < 0 ? -lower : lower) + (horizontal < 0 ? DIRECTION_LEFT : DIRECTION_RIGHT)) & DIRECTION_MASK;
    }

    /**
     * Calculates the prospective motion of one movement step into a step: turns the object, updates its speed and computes the next position, then
     * restores the movement flags the calculation changed.
     *
     * @param <E>        the object class
     * @param step       receives the prospective values
     * @param object     the moving object
     * @param properties reads the motion profile values of the object
     */
    public static <E extends MovingEntity> void update(@NotNull MotionStep step,
                                                       @NotNull E object,
                                                       @NotNull MotionProfileProperties<? super E> properties) {
        int originalFlags = object.getMovementFlags();

        step.setPreMovementFlags(originalFlags);
        updateDirection(object);
        updateSpeed(object, properties);
        calculateValues(step, object);
        object.setMovementFlags(originalFlags);
    }

    /**
     * Calculates the prospective position with the current direction and speed, then restores the given movement flags.
     *
     * @param step          receives the prospective values
     * @param object        the moving object
     * @param originalFlags the movement flags restored after the calculation
     */
    public static void updateValues(@NotNull MotionStep step, @NotNull MovingEntity object, int originalFlags) {
        step.setPreMovementFlags(originalFlags);
        calculateValues(step, object);
        object.setMovementFlags(originalFlags);
    }

    /**
     * @param object the moving object
     * @return {@code true} if the object stands on its movement destination, {@code false} otherwise
     */
    public static boolean isAtMovementDestination(@NotNull MovingEntity object) {
        return object.getMovementDestinationX() == (object.getPositionX() & COORDINATE_MASK) &&
               object.getMovementDestinationY() == (object.getPositionY() & COORDINATE_MASK);
    }

    /**
     * Updates the accelerating flag and the animation requests of a step without moving the object: an object away from its destination starts to
     * accelerate (an animation-controlled object not while an attack starts) and starts its walking animation, an object at its destination stops
     * accelerating and stops its walking animation; neither request is made while an attack starts.
     *
     * @param step   receives the animation requests
     * @param object the moving object
     */
    public static void updateMovementFlags(@NotNull MotionStep step, @NotNull MovingEntity object) {
        step.setStartingMovement(false);
        step.setStoppingMovement(false);

        int flags = object.getMovementFlags();
        boolean atTarget = isAtMovementDestination(object);

        if (!atTarget) {
            if ((flags & MovementFlags.ACCELERATING) == 0) {
                if (object.getMovementControl() != CONTROL_ANIMATION || (flags & MovementFlags.STARTING_ATTACK) == 0) {
                    object.setMovementFlags(flags | MovementFlags.ACCELERATING);
                }

                if ((flags & MovementFlags.STARTING_ATTACK) == 0) {
                    step.setStartingMovement(true);
                }
            }
        } else if ((flags & MovementFlags.ACCELERATING) != 0) {
            object.setMovementFlags(flags & ~MovementFlags.ACCELERATING);

            if ((flags & MovementFlags.STARTING_ATTACK) == 0) {
                step.setStoppingMovement(true);
            }
        }
    }

    private static void calculateValues(@NotNull MotionStep step, @NotNull MovingEntity object) {
        updateMovementFlags(step, object);

        int movementFlags = object.getMovementFlags();
        int speed = object.getCurrentSpeed();

        step.setSpeed(speed);

        int horizontal = object.getFixedPositionX();
        int vertical = object.getFixedPositionY();
        int positionX = object.getPositionX() & COORDINATE_MASK;
        int positionY = object.getPositionY() & COORDINATE_MASK;

        if ((movementFlags & MovementFlags.ACCELERATING) != 0 && speed != 0) {
            int targetX = (short) object.getMovementWaypointX() << FIXED_POINT_SHIFT;
            int targetY = (short) object.getMovementWaypointY() << FIXED_POINT_SHIFT;
            int distance = IntegerMathUtils.getApproximateDistance(horizontal, vertical, targetX, targetY);

            if (speed >= distance) {
                horizontal = targetX;
                vertical   = targetY;

                step.setSpeed(distance);
            } else {
                horizontal += object.getVelocityX();
                vertical += object.getVelocityY();
            }

            if (object.getMovementControl() == CONTROL_ANIMATION) {
                setSpeed(object, 0);
            }

            positionX = horizontal >> FIXED_POINT_SHIFT;
            positionY = vertical >> FIXED_POINT_SHIFT;
        }

        step.setPositionX(positionX);
        step.setPositionY(positionY);
        step.setFixedPositionX(horizontal);
        step.setFixedPositionY(vertical);
        step.setPostMovementFlags(object.getMovementFlags());
    }

    /**
     * Turns the desired direction toward the waypoint, or toward the direction target when the object stands on its waypoint, and applies the turn of
     * one frame.
     *
     * @param object the moving object
     */
    public static void updateDirection(@NotNull MovingEntity object) {
        int sourceX = object.getPositionX();
        int sourceY = object.getPositionY();
        int target = object.getMovementWaypointX() | object.getMovementWaypointY() << POSITION_VERTICAL_SHIFT;

        if (target == ((object.getPositionX() & COORDINATE_MASK) | object.getPositionY() << POSITION_VERTICAL_SHIFT)) {
            target = object.getDirectionTargetX() | object.getDirectionTargetY() << POSITION_VERTICAL_SHIFT;
        }

        int horizontal = (short) target - sourceX;
        int vertical = (short) (target >>> POSITION_VERTICAL_SHIFT) - sourceY;
        int direction;

        if (horizontal == 0 && vertical == 0) {
            direction = object.getHeading();
        } else {
            direction = calculateDirection(horizontal, vertical);
        }

        setDesiredDirection(object, direction);
    }

    /**
     * Sets the desired movement direction and turns the movement direction toward it by the turn rate (half the turn rate for an object that is not
     * animation-controlled), refreshing the velocity when it changes.
     *
     * @param object    the moving object
     * @param direction the desired direction
     */
    public static void setDesiredDirection(@NotNull MovingEntity object, int direction) {
        object.setDesiredMovementDirection(direction);

        int heading = object.getTargetHeading();

        if (heading != direction) {
            int turn = object.getTurnRate();

            if (object.getMovementControl() != CONTROL_ANIMATION) {
                turn >>>= MOTION_PROFILE_TURN_RATE_SHIFT;
            }

            int difference = (byte) (direction - heading);

            heading = (heading + Math.max(-turn, Math.min(turn, difference))) & DIRECTION_MASK;
        }

        if (heading != object.getMovementDirection()) {
            object.setMovementDirection(heading);
            writeVelocity(object, object.getCurrentSpeed());
        }
    }

    /**
     * Turns the heading toward the desired movement direction by the turn rate; the turn rate of a motion profile with an accelerating turn
     * grows by one each frame, and the ordered flag clears once the object faces its direction.
     *
     * @param <E>        the object class
     * @param object     the turning object
     * @param properties reads the motion profile values of the object
     */
    public static <E extends MovingEntity> void updateFacing(@NotNull E object, @NotNull MotionProfileProperties<? super E> properties) {
        int movementFlags = object.getMovementFlags();

        if ((movementFlags & MovementFlags.ACCELERATING) != 0 && (movementFlags & MovementFlags.ORDERED) == 0) {
            return;
        }

        int direction = object.getDesiredMovementDirection();
        int heading = object.getHeading();
        int turn = object.getTurnRate();
        int difference = (byte) (direction - heading);

        if (difference > (byte) turn) {
            heading += turn;
        } else if (difference < -(byte) turn) {
            heading -= turn;
        } else {
            heading += difference;
        }

        object.setHeading(heading);

        if (properties.isAcceleratingTurn(object)) {
            object.setTurnRate(turn + 1);
        }

        if (object.getTargetHeading() == direction && (heading & DIRECTION_MASK) == direction) {
            object.setMovementFlags(movementFlags & ~MovementFlags.ORDERED);
        }
    }

    /**
     * Updates the speed of one frame by the movement control: stops at the destination, accelerates toward the top speed, or decelerates when the
     * object must turn first or reaches its stopping distance.
     *
     * @param <E>        the object class
     * @param object     the moving object
     * @param properties reads the motion profile values of the object
     */
    public static <E extends MovingEntity> void updateSpeed(@NotNull E object, @NotNull MotionProfileProperties<? super E> properties) {
        int control = object.getMovementControl();
        boolean atTarget = isAtMovementDestination(object);
        int flags = object.getMovementFlags();

        if ((control == CONTROL_INSTANT_STOP || control == CONTROL_ANIMATION) && atTarget) {
            object.setMovementFlags(flags & ~MovementFlags.BRAKING);
            setSpeed(object, 0);

            return;
        }

        if (control == CONTROL_ANIMATION) {
            int difference = (object.getHeading() - object.getDesiredMovementDirection()) & DIRECTION_MASK;

            if ((flags & MovementFlags.ORDERED) != 0 && Math.min(difference, DIRECTION_FULL_TURN - difference) >= DIRECTION_EIGHTH_TURN) {
                if ((flags & MovementFlags.ACCELERATING) != 0) {
                    object.setMovementFlags(flags & ~(MovementFlags.ACCELERATING | MovementFlags.BRAKING));
                    setSpeed(object, 0);
                }

                return;
            }

            setSpeed(object, object.getNextSpeed());

            return;
        }

        if (control == CONTROL_MOTION_PROFILE && atTarget && object.getNextSpeed() < INSTANT_STOP_SPEED &&
            (flags & (MovementFlags.MOVING | MovementFlags.PASSING_THROUGH)) == 0) {
            object.setMovementFlags(flags & ~MovementFlags.BRAKING);
            setSpeed(object, 0);

            return;
        }

        int nextSpeed = object.getNextSpeed();

        setSpeed(object, nextSpeed);

        int distance = IntegerMathUtils.getApproximateDistance(object.getPositionX(), object.getPositionY(),
                                                               (short) object.getMovementWaypointX(), (short) object.getMovementWaypointY());
        int heading = object.getMovementDirection();
        int direction = object.getDesiredMovementDirection();
        int acceleration = object.getAcceleration();
        boolean decelerate = false;

        if (heading != direction && Integer.compareUnsigned(distance, BRAKING_CHECK_DISTANCE) < 0) {
            int turn = object.getTurnRate();
            int difference = (direction - heading) & DIRECTION_MASK;
            int frames = (turn - 1 + Math.min(difference, DIRECTION_FULL_TURN - difference) * TURN_FRAMES_FACTOR) / turn;
            int threshold = (frames * nextSpeed) >>> FIXED_POINT_SHIFT;

            threshold  = (threshold * BRAKING_MARGIN_FACTOR) >>> BRAKING_MARGIN_SHIFT;
            decelerate = Integer.compareUnsigned(threshold, distance) > 0;
        }

        if (!decelerate && control == CONTROL_MOTION_PROFILE &&
            ((flags & MovementFlags.MOVING) == 0 || (flags & MovementFlags.BRAKE_ON_PATH_STEP) != 0) &&
            ((flags & MovementFlags.PASSING_THROUGH) == 0 ||
             (object.getMovementWaypointX() | object.getMovementWaypointY() << POSITION_VERTICAL_SHIFT) !=
             (object.getMovementDestinationX() | object.getMovementDestinationY() << POSITION_VERTICAL_SHIFT))) {
            int stoppingDistance = calculateStoppingDistance(properties, object);
            int remaining = IntegerMathUtils.getApproximateDistance(object.getFixedPositionX(), object.getFixedPositionY(),
                                                                    (short) object.getMovementWaypointX() << FIXED_POINT_SHIFT,
                                                                    (short) object.getMovementWaypointY() << FIXED_POINT_SHIFT);

            decelerate = Integer.compareUnsigned(stoppingDistance, remaining) >= 0;
        }

        int speed = object.getCurrentSpeed() + (decelerate ? -acceleration : acceleration);

        setSpeed(object, speed > object.getTopSpeed() ? object.getTopSpeed() : Math.max(speed, 0));
    }

    /**
     * Sets the current speed and refreshes the velocity when the speed changes.
     *
     * @param object the moving object
     * @param speed  the new current speed, fixed-point
     */
    public static void setSpeed(@NotNull MovingEntity object, int speed) {
        if (object.getCurrentSpeed() != speed) {
            object.setCurrentSpeed(speed);
            writeVelocity(object, speed);
        }
    }

    /**
     * Sets the movement direction and refreshes the velocity when the direction changes.
     *
     * @param object    the moving object
     * @param direction the new movement direction
     */
    public static void setDirection(@NotNull MovingEntity object, int direction) {
        if (object.getMovementDirection() != (direction & DIRECTION_MASK)) {
            object.setMovementDirection(direction);
            writeVelocity(object, object.getCurrentSpeed());
        }
    }

    /**
     * Calculates the stopping distance of an object from its movement control, motion profile, next speed and acceleration
     * ({@link #calculateStoppingDistance(MotionProfileProperties, Object, int, int, int)}).
     *
     * @param <E>        the object class
     * @param properties reads the motion profile values of the object
     * @param object     the object
     * @return the stopping distance with eight fraction bits
     * @throws ArithmeticException if the distance is derived from the speed with an acceleration of {@code 0}
     */
    public static <E extends MovingEntity> int calculateStoppingDistance(@NotNull MotionProfileProperties<? super E> properties, @NotNull E object) {
        return calculateStoppingDistance(properties, object, object.getMovementControl(), object.getNextSpeed(), object.getAcceleration());
    }

    /**
     * Calculates the stopping distance of a moving object: {@code 0} at rest or with a movement control other than
     * {@link #CONTROL_MOTION_PROFILE}, the motion profile's stopping distance at its top speed and acceleration, else the squared speed divided
     * by twice the acceleration. The motion profile values are read only as far as the rule needs them.
     *
     * @param <T>             the class of the moving object
     * @param properties      reads the motion profile values of the object
     * @param object          the moving object
     * @param movementControl the movement control
     * @param speed           the next speed
     * @param acceleration    the acceleration
     * @return the stopping distance with eight fraction bits
     * @throws ArithmeticException if the distance is derived from the speed with an acceleration of {@code 0}
     */
    public static <T> int calculateStoppingDistance(@NotNull MotionProfileProperties<? super T> properties, @NotNull T object,
                                                    int movementControl, int speed, int acceleration) {
        if (speed == 0 || movementControl != CONTROL_MOTION_PROFILE) {
            return 0;
        }

        if (speed == properties.getTopSpeed(object) && acceleration == properties.getAcceleration(object)) {
            return properties.getStoppingDistance(object);
        }

        return (speed * speed) / (acceleration * STOPPING_DISTANCE_ACCELERATION_FACTOR);
    }

    private static void writeVelocity(@NotNull MovingEntity object, int speed) {
        long velocity = calculateDirectionVector(object.getMovementDirection(), speed);

        object.setVelocityX((int) velocity);
        object.setVelocityY((int) (velocity >>> DIRECTION_VECTOR_VERTICAL_SHIFT));
    }


    private FlingyMotionUtils() {
    }
}
