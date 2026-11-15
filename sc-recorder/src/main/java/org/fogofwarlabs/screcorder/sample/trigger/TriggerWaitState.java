package org.fogofwarlabs.screcorder.sample.trigger;

/**
 * Holds one player's trigger waiting byte and unsigned wait timer.
 */
public final class TriggerWaitState {
    private final int playerIdentifier;
    private final int waiting;
    private final long timer;

    /**
     * Creates a {@link TriggerWaitState} instance.
     *
     * @param playerIdentifier the player slot
     * @param waiting          the unsigned waiting byte
     * @param timer            the unsigned wait timer
     */
    public TriggerWaitState(int playerIdentifier, int waiting, long timer) {
        this.playerIdentifier = playerIdentifier;
        this.waiting          = waiting;
        this.timer            = timer;
    }


    /**
     * @return the player slot
     */
    public int getPlayerIdentifier() {
        return this.playerIdentifier;
    }

    /**
     * @return the unsigned waiting byte
     */
    public int getWaiting() {
        return this.waiting;
    }

    /**
     * @return the unsigned wait timer
     */
    public long getTimer() {
        return this.timer;
    }
}
