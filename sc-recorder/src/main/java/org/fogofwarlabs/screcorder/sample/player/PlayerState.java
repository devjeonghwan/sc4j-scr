package org.fogofwarlabs.screcorder.sample.player;

/**
 * Contains resource and supply values for one player. Supply values retain the engine's doubled-unit representation.
 */
public final class PlayerState {
    private final int identifier;

    private final int minerals;
    private final int gas;
    private final int gatheredMinerals;
    private final int gatheredGas;

    private final int zergSupplyProvided;
    private final int zergSupplyUsed;
    private final int zergSupplyCap;

    private final int terranSupplyProvided;
    private final int terranSupplyUsed;
    private final int terranSupplyCap;

    private final int protossSupplyProvided;
    private final int protossSupplyUsed;
    private final int protossSupplyCap;

    /**
     * Creates a {@link PlayerState} instance.
     *
     * @param identifier            the zero-based player identifier
     * @param minerals              the current minerals
     * @param gas                   the current gas
     * @param gatheredMinerals      the gathered minerals
     * @param gatheredGas           the gathered gas
     * @param zergSupplyProvided    the zerg supply provided value
     * @param zergSupplyUsed        the zerg supply used value
     * @param zergSupplyCap         the zerg supply cap value
     * @param terranSupplyProvided  the terran supply provided value
     * @param terranSupplyUsed      the terran supply used value
     * @param terranSupplyCap       the terran supply cap value
     * @param protossSupplyProvided the protoss supply provided value
     * @param protossSupplyUsed     the protoss supply used value
     * @param protossSupplyCap      the protoss supply cap value
     */
    public PlayerState(int identifier,
                       int minerals, int gas, int gatheredMinerals, int gatheredGas,
                       int zergSupplyProvided, int zergSupplyUsed, int zergSupplyCap,
                       int terranSupplyProvided, int terranSupplyUsed, int terranSupplyCap,
                       int protossSupplyProvided, int protossSupplyUsed, int protossSupplyCap) {
        this.identifier = identifier;

        this.minerals         = minerals;
        this.gas              = gas;
        this.gatheredMinerals = gatheredMinerals;
        this.gatheredGas      = gatheredGas;

        this.zergSupplyProvided = zergSupplyProvided;
        this.zergSupplyUsed     = zergSupplyUsed;
        this.zergSupplyCap      = zergSupplyCap;

        this.terranSupplyProvided = terranSupplyProvided;
        this.terranSupplyUsed     = terranSupplyUsed;
        this.terranSupplyCap      = terranSupplyCap;

        this.protossSupplyProvided = protossSupplyProvided;
        this.protossSupplyUsed     = protossSupplyUsed;
        this.protossSupplyCap      = protossSupplyCap;
    }


    /**
     * @return the zero-based player identifier
     */
    public int getIdentifier() {
        return this.identifier;
    }

    /**
     * @return the current minerals
     */
    public int getMinerals() {
        return this.minerals;
    }

    /**
     * @return the current gas
     */
    public int getGas() {
        return this.gas;
    }

    /**
     * @return the gathered minerals
     */
    public int getGatheredMinerals() {
        return this.gatheredMinerals;
    }

    /**
     * @return the gathered gas
     */
    public int getGatheredGas() {
        return this.gatheredGas;
    }

    /**
     * @return the zerg supply provided value
     */
    public int getZergSupplyProvided() {
        return this.zergSupplyProvided;
    }

    /**
     * @return the zerg supply used value
     */
    public int getZergSupplyUsed() {
        return this.zergSupplyUsed;
    }

    /**
     * @return the zerg supply cap value
     */
    public int getZergSupplyCap() {
        return this.zergSupplyCap;
    }

    /**
     * @return the terran supply provided value
     */
    public int getTerranSupplyProvided() {
        return this.terranSupplyProvided;
    }

    /**
     * @return the terran supply used value
     */
    public int getTerranSupplyUsed() {
        return this.terranSupplyUsed;
    }

    /**
     * @return the terran supply cap value
     */
    public int getTerranSupplyCap() {
        return this.terranSupplyCap;
    }

    /**
     * @return the protoss supply provided value
     */
    public int getProtossSupplyProvided() {
        return this.protossSupplyProvided;
    }

    /**
     * @return the protoss supply used value
     */
    public int getProtossSupplyUsed() {
        return this.protossSupplyUsed;
    }

    /**
     * @return the protoss supply cap value
     */
    public int getProtossSupplyCap() {
        return this.protossSupplyCap;
    }
}
