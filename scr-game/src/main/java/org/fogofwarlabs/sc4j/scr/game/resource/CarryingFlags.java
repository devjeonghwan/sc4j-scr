package org.fogofwarlabs.sc4j.scr.game.resource;

import org.fogofwarlabs.sc4j.scr.game.unit.Unit;

/**
 * Names the bits of a worker's carrying flags ({@link Unit#getCarryingFlags()}).
 * <p>
 * A worker carries gas, minerals or a powerup.
 */
public final class CarryingFlags {
    /**
     * The worker carries gas.
     */
    public static final int GAS = 0x1;
    /**
     * The worker carries minerals.
     */
    public static final int MINERALS = 0x2;
    /**
     * The worker carries a resource: gas or minerals.
     */
    public static final int RESOURCES = GAS | MINERALS;
    /**
     * The worker carries a powerup (a flag or a Khaydarin crystal).
     */
    public static final int POWERUP = 0x4;

    private CarryingFlags() {
    }
}
