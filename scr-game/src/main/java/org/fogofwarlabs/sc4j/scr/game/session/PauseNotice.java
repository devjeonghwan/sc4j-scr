package org.fogofwarlabs.sc4j.scr.game.session;

/**
 * Reports an executed multiplayer pause or resume for the interface's game messages.
 */
public final class PauseNotice {
    private final int slot;

    private final boolean paused;
    private final int pausesLeft;

    /**
     * Creates a {@link PauseNotice} instance.
     *
     * @param slot       the player slot whose command executed
     * @param paused     whether the command paused the game; {@code false} for a resume
     * @param pausesLeft the pauses the player has left after a pause
     */
    public PauseNotice(int slot, boolean paused, int pausesLeft) {
        this.slot = slot;

        this.paused     = paused;
        this.pausesLeft = pausesLeft;
    }

    /**
     * @return the player slot whose command executed
     */
    public int getSlot() {
        return this.slot;
    }

    /**
     * @return {@code true} if the command paused the game, {@code false} otherwise
     */
    public boolean isPaused() {
        return this.paused;
    }

    /**
     * @return the pauses the player has left after a pause
     */
    public int getPausesLeft() {
        return this.pausesLeft;
    }
}
