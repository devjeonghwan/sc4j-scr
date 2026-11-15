package org.fogofwarlabs.screcorder.sample.ai;

/**
 * Stores immutable unit AI state with logical target slots and process-specific AI record addresses.
 */
public final class AiUnitState {
    private final int identifier;
    private final int type;
    private final int owner;
    private final long status;
    private final int autoTargetIdentifier;
    private final long recordAddress;
    private final int kind;
    private final int kindWord;
    private final int airStrength;
    private final int groundStrength;
    private final int order;
    private final int orderState;
    private final int orderTargetIdentifier;
    private final int targetX;
    private final int targetY;
    private final int positionX;
    private final int positionY;
    private final int secondaryOrder;

    /**
     * Creates a {@link AiUnitState} instance.
     *
     * @param identifier            the one-based unit pool slot
     * @param type                  the unsigned unit type
     * @param owner                 the unsigned unit owner
     * @param status                the unsigned unit status bits
     * @param autoTargetIdentifier  the logical auto-target slot, or {@code 0}
     * @param recordAddress         the AI record address as an offset from the executable image base, or {@code 0}
     * @param kind                  the unsigned AI record kind, or {@code -1} without a record
     * @param kindWord              the unsigned word read for AI record kind {@code 4}, or {@code -1} when absent
     * @param airStrength           the unsigned air strength of the unit
     * @param groundStrength        the unsigned ground strength of the unit
     * @param order                 the unsigned primary order
     * @param orderState            the unsigned primary order state
     * @param orderTargetIdentifier the logical order-target slot, or {@code 0}
     * @param targetX               the signed order target horizontal position
     * @param targetY               the signed order target vertical position
     * @param positionX             the unsigned unit horizontal position
     * @param positionY             the unsigned unit vertical position
     * @param secondaryOrder        the unsigned secondary order
     */
    public AiUnitState(int identifier, int type, int owner, long status,
                       int autoTargetIdentifier, long recordAddress, int kind, int kindWord,
                       int airStrength, int groundStrength,
                       int order, int orderState, int orderTargetIdentifier, int targetX, int targetY,
                       int positionX, int positionY, int secondaryOrder) {
        this.identifier            = identifier;
        this.type                  = type;
        this.owner                 = owner;
        this.status                = status;
        this.autoTargetIdentifier  = autoTargetIdentifier;
        this.recordAddress         = recordAddress;
        this.kind                  = kind;
        this.kindWord              = kindWord;
        this.airStrength           = airStrength;
        this.groundStrength        = groundStrength;
        this.order                 = order;
        this.orderState            = orderState;
        this.orderTargetIdentifier = orderTargetIdentifier;
        this.targetX               = targetX;
        this.targetY               = targetY;
        this.positionX             = positionX;
        this.positionY             = positionY;
        this.secondaryOrder        = secondaryOrder;
    }


    /**
     * @return the one-based unit pool slot
     */
    public int getIdentifier() {
        return this.identifier;
    }

    /**
     * @return the unsigned unit type
     */
    public int getType() {
        return this.type;
    }

    /**
     * @return the unsigned unit owner
     */
    public int getOwner() {
        return this.owner;
    }

    /**
     * @return the unsigned unit status bits
     */
    public long getStatus() {
        return this.status;
    }

    /**
     * @return the logical auto-target slot, or {@code 0}
     */
    public int getAutoTargetIdentifier() {
        return this.autoTargetIdentifier;
    }

    /**
     * @return the AI record address as an offset from the executable image base, or {@code 0}
     */
    public long getRecordAddress() {
        return this.recordAddress;
    }

    /**
     * @return the unsigned AI record kind, or {@code -1} without a record
     */
    public int getKind() {
        return this.kind;
    }

    /**
     * @return the unsigned word read for AI record kind {@code 4}, or {@code -1} when absent
     */
    public int getKindWord() {
        return this.kindWord;
    }

    /**
     * @return the unsigned air strength of the unit
     */
    public int getAirStrength() {
        return this.airStrength;
    }

    /**
     * @return the unsigned ground strength of the unit
     */
    public int getGroundStrength() {
        return this.groundStrength;
    }

    /**
     * @return the unsigned primary order
     */
    public int getOrder() {
        return this.order;
    }

    /**
     * @return the unsigned primary order state
     */
    public int getOrderState() {
        return this.orderState;
    }

    /**
     * @return the logical order-target slot, or {@code 0}
     */
    public int getOrderTargetIdentifier() {
        return this.orderTargetIdentifier;
    }

    /**
     * @return the signed order target horizontal position
     */
    public int getTargetX() {
        return this.targetX;
    }

    /**
     * @return the signed order target vertical position
     */
    public int getTargetY() {
        return this.targetY;
    }

    /**
     * @return the unsigned unit horizontal position
     */
    public int getPositionX() {
        return this.positionX;
    }

    /**
     * @return the unsigned unit vertical position
     */
    public int getPositionY() {
        return this.positionY;
    }

    /**
     * @return the unsigned secondary order
     */
    public int getSecondaryOrder() {
        return this.secondaryOrder;
    }
}
