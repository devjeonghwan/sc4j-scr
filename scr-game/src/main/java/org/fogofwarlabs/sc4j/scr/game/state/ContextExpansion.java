package org.fogofwarlabs.sc4j.scr.game.state;

/**
 * Holds the expansion BYTE of the game context: the product selection stores {@code 1} for Brood War and {@code 0} for
 * StarCraft, the replay header copies it, and the simulation tests it for zero wherever Brood War content is gated
 * (upgrade and technology stores, unit speeds, orders, computer scripts). An EUD write stores any byte, which the readers
 * then see.
 */
public final class ContextExpansion {
    /**
     * The mask of a BYTE value.
     */
    private static final int BYTE_MASK = 0xFF;

    private int value;

    /**
     * Creates a {@link ContextExpansion} instance.
     *
     * @param enabled whether the game is played as Brood War
     */
    public ContextExpansion(boolean enabled) {
        this.value = enabled ? 1 : 0;
    }

    /**
     * @return the stored BYTE
     */
    public int getValue() {
        return this.value;
    }

    /**
     * Sets the stored BYTE, as an EUD write does.
     *
     * @param value the new value; only the low byte is stored
     */
    public void setValue(int value) {
        this.value = value & BYTE_MASK;
    }

    /**
     * @return {@code true} if the BYTE is not zero (Brood War), {@code false} otherwise
     */
    public boolean isEnabled() {
        return this.value != 0;
    }
}
