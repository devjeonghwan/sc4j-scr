package org.fogofwarlabs.sc4j.scr.client;

/**
 * Holds the fixed inputs of the seeded live client sessions that tests start, so that their games are deterministic.
 */
public final class ClientSessionScenario {
    /**
     * The random seed of a live client session that a test starts.
     */
    public static final int GAME_SEED = 0x1234;


    /**
     * Creates a {@link ClientSessionScenario} instance.
     */
    private ClientSessionScenario() {
    }
}
