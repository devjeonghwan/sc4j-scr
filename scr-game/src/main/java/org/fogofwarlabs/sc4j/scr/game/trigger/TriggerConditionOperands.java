package org.fogofwarlabs.sc4j.scr.game.trigger;

/**
 * Holds the operands of the trigger condition under test: the fields of one {@code TRIG} condition record, read from a decoded definition,
 * from the rows of a node or from the record bytes in a region. The condition loop fills the one instance of its {@link TriggerContext}
 * before each test, so a test allocates nothing; a condition ({@link ScrTriggerCondition}) reads it only during its test.
 */
public final class TriggerConditionOperands {
    private int type;
    private int location;
    private int selector;
    private int amount;
    private int comparison;
    private int unitType;
    private int resourceType;
    private int mask;

    /**
     * Creates a {@link TriggerConditionOperands} instance with zero operands.
     */
    TriggerConditionOperands() {
        this.type         = 0;
        this.location     = 0;
        this.selector     = 0;
        this.amount       = 0;
        this.comparison   = 0;
        this.unitType     = 0;
        this.resourceType = 0;
        this.mask         = 0;
    }

    /**
     * @return the condition type
     */
    public int getType() {
        return this.type;
    }

    /**
     * @return the location field
     */
    public int getLocation() {
        return this.location;
    }

    /**
     * @return the player selector field
     */
    public int getSelector() {
        return this.selector;
    }

    /**
     * @return the amount field
     */
    public int getAmount() {
        return this.amount;
    }

    /**
     * @return the comparison field
     */
    public int getComparison() {
        return this.comparison;
    }

    /**
     * @return the unit type field
     */
    public int getUnitType() {
        return this.unitType;
    }

    /**
     * @return the resource type field (also the switch or score type)
     */
    public int getResourceType() {
        return this.resourceType;
    }

    /**
     * @return the location field of a condition with the {@code 'S','C'} mask marker, else {@code -1}
     */
    public int getMask() {
        return this.mask;
    }

    /**
     * Sets the operands of the next condition under test.
     *
     * @param type         the condition type
     * @param location     the location field
     * @param selector     the player selector field
     * @param amount       the amount field
     * @param comparison   the comparison field
     * @param unitType     the unit type field
     * @param resourceType the resource type field
     * @param mask         the location field of a condition with the {@code 'S','C'} mask marker, else {@code -1}
     */
    void set(int type, int location, int selector, int amount, int comparison, int unitType, int resourceType, int mask) {
        this.type         = type;
        this.location     = location;
        this.selector     = selector;
        this.amount       = amount;
        this.comparison   = comparison;
        this.unitType     = unitType;
        this.resourceType = resourceType;
        this.mask         = mask;
    }
}
