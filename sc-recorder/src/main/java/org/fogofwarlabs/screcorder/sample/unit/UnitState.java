package org.fogofwarlabs.screcorder.sample.unit;

/**
 * Contains logical unit state. Hit points, shields and energy retain their native fixed-point representation.
 * <p>
 * The class-dependent unit union is decoded by unit type: {@link #UNION_WORDS} keeps its four raw words
 * (resource amount, resource flags, town hall or larva side words), {@link #UNION_UNIT} replaces a unit pointer
 * (worker gather target, nydus exit, silo nuke) by its logical pool slot, and {@link #UNION_SPRITE} records only
 * whether a pointer outside the unit pool (ghost nuke dot, pylon psi field sprite) is set.
 */
public final class UnitState {
    /**
     * The union holds four raw words.
     */
    public static final int UNION_WORDS = 0;

    /**
     * The union holds a unit pointer, stored as a logical pool slot ({@code 0} for none).
     */
    public static final int UNION_UNIT = 1;

    /**
     * The union holds a pointer outside the unit pool, stored as presence ({@code 0} or {@code 1}).
     */
    public static final int UNION_SPRITE = 2;

    /**
     * The value of a {@link #UNION_UNIT} union whose pointer does not address a pool slot (all ones).
     */
    public static final long UNRESOLVED_UNIT = -1;

    private final int identifier;
    private final int generation;
    private final int type;
    private final int owner;

    private final int positionX;
    private final int positionY;
    private final int exactPositionX;
    private final int exactPositionY;

    private final int hitPoints;
    private final int shields;
    private final int energy;

    private final int order;
    private final int orderState;
    private final int orderTimer;

    private final int orderTargetIdentifier;
    private final int orderTargetX;
    private final int orderTargetY;

    private final int secondaryOrder;

    private final int movementState;
    private final int movementFlags;
    private final int heading;

    private final int currentSpeed;
    private final long status;

    private final int unionKind;
    private final long unionValue;
    private final int carryingFlags;
    private final int remainingBuildTime;

    /**
     * Creates a {@link UnitState} instance.
     *
     * @param identifier            the identifier value
     * @param generation            the generation value
     * @param type                  the type value
     * @param owner                 the owner value
     * @param positionX             the position x value
     * @param positionY             the position y value
     * @param exactPositionX        the exact position x value
     * @param exactPositionY        the exact position y value
     * @param hitPoints             the hit points value
     * @param shields               the shields value
     * @param energy                the energy value
     * @param order                 the order value
     * @param orderState            the order state value
     * @param orderTimer            the order timer value
     * @param orderTargetIdentifier the order target identifier value
     * @param orderTargetX          the order target x value
     * @param orderTargetY          the order target y value
     * @param secondaryOrder        the secondary order value
     * @param movementState         the movement state value
     * @param movementFlags         the movement flags value
     * @param heading               the heading value
     * @param currentSpeed          the current speed value
     * @param status                the status value
     * @param unionKind             the union decoding: {@link #UNION_WORDS}, {@link #UNION_UNIT} or {@link #UNION_SPRITE}
     * @param unionValue            the decoded union value
     * @param carryingFlags         the carrying flags value
     * @param remainingBuildTime    the remaining build time value
     */
    public UnitState(int identifier, int generation, int type, int owner,
                     int positionX, int positionY, int exactPositionX, int exactPositionY,
                     int hitPoints, int shields, int energy,
                     int order, int orderState, int orderTimer,
                     int orderTargetIdentifier, int orderTargetX, int orderTargetY,
                     int secondaryOrder,
                     int movementState, int movementFlags, int heading,
                     int currentSpeed, long status,
                     int unionKind, long unionValue, int carryingFlags, int remainingBuildTime) {
        this.identifier = identifier;
        this.generation = generation;
        this.type       = type;
        this.owner      = owner;

        this.positionX      = positionX;
        this.positionY      = positionY;
        this.exactPositionX = exactPositionX;
        this.exactPositionY = exactPositionY;

        this.hitPoints = hitPoints;
        this.shields   = shields;
        this.energy    = energy;

        this.order      = order;
        this.orderState = orderState;
        this.orderTimer = orderTimer;

        this.orderTargetIdentifier = orderTargetIdentifier;
        this.orderTargetX          = orderTargetX;
        this.orderTargetY          = orderTargetY;

        this.secondaryOrder = secondaryOrder;

        this.movementState = movementState;
        this.movementFlags = movementFlags;
        this.heading       = heading;

        this.currentSpeed = currentSpeed;
        this.status       = status;

        this.unionKind          = unionKind;
        this.unionValue         = unionValue;
        this.carryingFlags      = carryingFlags;
        this.remainingBuildTime = remainingBuildTime;
    }


    /**
     * @return the identifier value
     */
    public int getIdentifier() {
        return this.identifier;
    }

    /**
     * @return the generation value
     */
    public int getGeneration() {
        return this.generation;
    }

    /**
     * @return the type value
     */
    public int getType() {
        return this.type;
    }

    /**
     * @return the owner value
     */
    public int getOwner() {
        return this.owner;
    }

    /**
     * @return the position x value
     */
    public int getPositionX() {
        return this.positionX;
    }

    /**
     * @return the position y value
     */
    public int getPositionY() {
        return this.positionY;
    }

    /**
     * @return the exact position x value
     */
    public int getExactPositionX() {
        return this.exactPositionX;
    }

    /**
     * @return the exact position y value
     */
    public int getExactPositionY() {
        return this.exactPositionY;
    }

    /**
     * @return the hit points value
     */
    public int getHitPoints() {
        return this.hitPoints;
    }

    /**
     * @return the shields value
     */
    public int getShields() {
        return this.shields;
    }

    /**
     * @return the energy value
     */
    public int getEnergy() {
        return this.energy;
    }

    /**
     * @return the order value
     */
    public int getOrder() {
        return this.order;
    }

    /**
     * @return the order state value
     */
    public int getOrderState() {
        return this.orderState;
    }

    /**
     * @return the order timer value
     */
    public int getOrderTimer() {
        return this.orderTimer;
    }

    /**
     * @return the order target identifier value
     */
    public int getOrderTargetIdentifier() {
        return this.orderTargetIdentifier;
    }

    /**
     * @return the order target x value
     */
    public int getOrderTargetX() {
        return this.orderTargetX;
    }

    /**
     * @return the order target y value
     */
    public int getOrderTargetY() {
        return this.orderTargetY;
    }

    /**
     * @return the secondary order value
     */
    public int getSecondaryOrder() {
        return this.secondaryOrder;
    }

    /**
     * @return the movement state value
     */
    public int getMovementState() {
        return this.movementState;
    }

    /**
     * @return the movement flags value
     */
    public int getMovementFlags() {
        return this.movementFlags;
    }

    /**
     * @return the heading value
     */
    public int getHeading() {
        return this.heading;
    }

    /**
     * @return the current speed value
     */
    public int getCurrentSpeed() {
        return this.currentSpeed;
    }

    /**
     * @return the status value
     */
    public long getStatus() {
        return this.status;
    }

    /**
     * @return {@link #UNION_WORDS}, {@link #UNION_UNIT} or {@link #UNION_SPRITE}
     */
    public int getUnionKind() {
        return this.unionKind;
    }

    /**
     * @return the four union words, the logical slot of the union unit ({@link #UNRESOLVED_UNIT} if unresolved), or the union sprite presence
     */
    public long getUnionValue() {
        return this.unionValue;
    }

    /**
     * @return the carrying flags value
     */
    public int getCarryingFlags() {
        return this.carryingFlags;
    }

    /**
     * @return the remaining build time value
     */
    public int getRemainingBuildTime() {
        return this.remainingBuildTime;
    }
}
