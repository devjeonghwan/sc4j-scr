package org.fogofwarlabs.sc4j.scr.game.unit;

/**
 * Retains the supplied creation and completion inputs for the active phase.
 */
public final class UnitCreationContext {
    /**
     * The multiplier of the state hash.
     */
    private static final int HASH_MULTIPLIER = 31;


    private final boolean preserveNegativeTypeCounts;
    private final boolean registryPresent;
    private final boolean foliageEnabled;
    private final boolean buildingRefreshEnabled;
    private final boolean completionRefreshSuppressed;
    private final int localPlayer;

    /**
     * Creates a {@link UnitCreationContext} instance with established phase values.
     *
     * @param preserveNegativeTypeCounts  the accounting compatibility flag
     * @param registryPresent             whether the established registry exists
     * @param foliageEnabled              whether the foliage context is enabled
     * @param buildingRefreshEnabled      the building refresh flag
     * @param completionRefreshSuppressed the completion refresh suppression flag
     * @param localPlayer                 the native local player comparison DWORD
     */
    public UnitCreationContext(boolean preserveNegativeTypeCounts,
                               boolean registryPresent,
                               boolean foliageEnabled,
                               boolean buildingRefreshEnabled,
                               boolean completionRefreshSuppressed,
                               int localPlayer) {
        this.preserveNegativeTypeCounts  = preserveNegativeTypeCounts;
        this.registryPresent             = registryPresent;
        this.foliageEnabled              = foliageEnabled;
        this.buildingRefreshEnabled      = buildingRefreshEnabled;
        this.completionRefreshSuppressed = completionRefreshSuppressed;
        this.localPlayer                 = localPlayer;
    }

    /**
     * @return {@code true} if negative accounting type counts are preserved, {@code false} otherwise
     */
    public boolean preservesNegativeTypeCounts() {
        return this.preserveNegativeTypeCounts;
    }

    /**
     * @return {@code true} if the established registry exists, {@code false} otherwise
     */
    public boolean isRegistryPresent() {
        return this.registryPresent;
    }

    /**
     * @return {@code true} if foliage is enabled, {@code false} otherwise
     */
    public boolean isFoliageEnabled() {
        return this.foliageEnabled;
    }

    /**
     * @return {@code true} if building refresh is enabled, {@code false} otherwise
     */
    public boolean isBuildingRefreshEnabled() {
        return this.buildingRefreshEnabled;
    }

    /**
     * @return {@code true} if completion refresh is suppressed, {@code false} otherwise
     */
    public boolean isCompletionRefreshSuppressed() {
        return this.completionRefreshSuppressed;
    }

    /**
     * @return the native local player comparison DWORD
     */
    public int getLocalPlayer() {
        return this.localPlayer;
    }


    /**
     * Includes all retained creation inputs in a state hash.
     *
     * @param hash the preceding state hash
     * @return the hash including all retained creation inputs
     */
    public int computeStateHash(int hash) {
        hash = HASH_MULTIPLIER * hash + (this.preserveNegativeTypeCounts ? 1 : 0);
        hash = HASH_MULTIPLIER * hash + (this.registryPresent ? 1 : 0);
        hash = HASH_MULTIPLIER * hash + (this.foliageEnabled ? 1 : 0);
        hash = HASH_MULTIPLIER * hash + (this.buildingRefreshEnabled ? 1 : 0);
        hash = HASH_MULTIPLIER * hash + (this.completionRefreshSuppressed ? 1 : 0);

        return HASH_MULTIPLIER * hash + this.localPlayer;
    }
}
