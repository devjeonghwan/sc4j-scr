package org.fogofwarlabs.sc4j.scr.data.format.chk;

/**
 * Represents a single preplaced unit of the CHK {@code UNIT} section.
 */
public final class ChkUnit {
    /**
     * The size of one UNIT record in bytes.
     */
    public static final int ENTRY_SIZE = 36;

    /*
     * State bits: a bit of the state flags applies when the same bit of the valid properties flags is set.
     */
    /**
     * The state bit of a cloaked unit.
     */
    public static final int STATE_CLOAKED = 0x1;
    /**
     * The state bit of a burrowed unit.
     */
    public static final int STATE_BURROWED = 0x2;
    /**
     * The state bit of a building in transit (lifted off).
     */
    public static final int STATE_IN_TRANSIT = 0x4;
    /**
     * The state bit of a hallucinated unit.
     */
    public static final int STATE_HALLUCINATED = 0x8;
    /**
     * The state bit of an invincible unit.
     */
    public static final int STATE_INVINCIBLE = 0x10;

    /**
     * The valid elements bit of the hit points percentage.
     */
    public static final int ELEMENT_HIT_POINTS = 0x2;
    /**
     * The valid elements bit of the shield points percentage.
     */
    public static final int ELEMENT_SHIELD_POINTS = 0x4;
    /**
     * The valid elements bit of the energy percentage.
     */
    public static final int ELEMENT_ENERGY = 0x8;
    /**
     * The valid elements bit of the resource amount.
     */
    public static final int ELEMENT_RESOURCE_AMOUNT = 0x10;
    /**
     * The valid elements bit of the hangar count.
     */
    public static final int ELEMENT_HANGAR_COUNT = 0x20;

    private final long serial;

    private final int x;
    private final int y;

    private final int unitId;

    /**
     * The relation type WORD, kept with the {@code UNIT} record layout although no SC4J code reads it.
     */
    private final int relationType;

    private final int validPropertiesFlags;
    private final int validElementsFlags;

    private final int owner;

    private final int hitPointsPercent;
    private final int shieldPointsPercent;
    private final int energyPercent;

    private final long resourceAmount;

    private final int hangarCount;

    private final int stateFlags;

    private final long linkedSerial;

    /**
     * Creates a {@link ChkUnit} instance.
     *
     * @param serial               the serial number
     * @param x                    the x coordinate in pixels
     * @param y                    the y coordinate in pixels
     * @param unitId               the unit type ID
     * @param relationType         the relation type WORD
     * @param validPropertiesFlags the valid properties flags
     * @param validElementsFlags   the valid elements flags
     * @param owner                the owner player
     * @param hitPointsPercent     the hit points percentage
     * @param shieldPointsPercent  the shield points percentage
     * @param energyPercent        the energy percentage
     * @param resourceAmount       the resource amount
     * @param hangarCount          the hangar count
     * @param stateFlags           the state flags
     * @param linkedSerial         the linked unit serial
     */
    public ChkUnit(long serial,
                   int x, int y,
                   int unitId,
                   int relationType,
                   int validPropertiesFlags, int validElementsFlags,
                   int owner,
                   int hitPointsPercent, int shieldPointsPercent, int energyPercent,
                   long resourceAmount,
                   int hangarCount,
                   int stateFlags,
                   long linkedSerial) {
        this.serial = serial;

        this.x = x;
        this.y = y;

        this.unitId = unitId;

        this.relationType = relationType;

        this.validPropertiesFlags = validPropertiesFlags;
        this.validElementsFlags   = validElementsFlags;

        this.owner = owner;

        this.hitPointsPercent    = hitPointsPercent;
        this.shieldPointsPercent = shieldPointsPercent;
        this.energyPercent       = energyPercent;

        this.resourceAmount = resourceAmount;

        this.hangarCount = hangarCount;

        this.stateFlags = stateFlags;

        this.linkedSerial = linkedSerial;
    }

    /**
     * @return the serial number
     */
    public long getSerial() {
        return this.serial;
    }

    /**
     * @return the x coordinate in pixels
     */
    public int getX() {
        return this.x;
    }

    /**
     * @return the y coordinate in pixels
     */
    public int getY() {
        return this.y;
    }

    /**
     * @return the unit type ID
     */
    public int getUnitId() {
        return this.unitId;
    }

    /**
     * @return the valid properties flags
     */
    public int getValidPropertiesFlags() {
        return this.validPropertiesFlags;
    }

    /**
     * @return the valid elements flags
     */
    public int getValidElementsFlags() {
        return this.validElementsFlags;
    }

    /**
     * @return the owner player
     */
    public int getOwner() {
        return this.owner;
    }

    /**
     * @return the hit points percentage
     */
    public int getHitPointsPercent() {
        return this.hitPointsPercent;
    }

    /**
     * @return the shield points percentage
     */
    public int getShieldPointsPercent() {
        return this.shieldPointsPercent;
    }

    /**
     * @return the energy percentage
     */
    public int getEnergyPercent() {
        return this.energyPercent;
    }

    /**
     * @return the resource amount
     */
    public long getResourceAmount() {
        return this.resourceAmount;
    }

    /**
     * @return the unsigned hangar count WORD
     */
    public int getHangarCount() {
        return this.hangarCount;
    }

    /**
     * @return the state flags
     */
    public int getStateFlags() {
        return this.stateFlags;
    }

    /**
     * @return the related unit serial DWORD at record offset {@code 0x20} (Nydus exit or add-on partner)
     */
    public long getLinkedSerial() {
        return this.linkedSerial;
    }
}
