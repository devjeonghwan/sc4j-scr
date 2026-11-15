package org.fogofwarlabs.screcorder.sample.trigger;

import org.fogofwarlabs.screcorder.sample.Sample;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Holds trigger wait state and local session flags.
 */
public final class TriggersSample implements Sample {
    private final int cycle;
    private final @NotNull List<TriggerWaitState> waits;

    private final int localPlayer;
    private final int stormPlayer;

    private final int multiplayerFlag;
    private final int replayFlag;

    /**
     * Creates a {@link TriggersSample} instance.
     *
     * @param cycle           the unsigned trigger cycle
     * @param waits           the eight player wait states
     * @param localPlayer     the signed local player slot
     * @param stormPlayer     the signed network player slot
     * @param multiplayerFlag the unsigned multiplayer byte
     * @param replayFlag      the unsigned replay byte
     */
    public TriggersSample(int cycle, @NotNull List<TriggerWaitState> waits, int localPlayer, int stormPlayer, int multiplayerFlag, int replayFlag) {
        this.cycle = cycle;
        this.waits = List.copyOf(waits);

        this.localPlayer = localPlayer;
        this.stormPlayer = stormPlayer;

        this.multiplayerFlag = multiplayerFlag;
        this.replayFlag      = replayFlag;
    }


    /**
     * @return the unsigned trigger cycle
     */
    public int getCycle() {
        return this.cycle;
    }

    /**
     * @return the immutable player wait states
     */
    public @NotNull List<TriggerWaitState> getWaits() {
        return this.waits;
    }

    /**
     * @return the signed local player slot
     */
    public int getLocalPlayer() {
        return this.localPlayer;
    }

    /**
     * @return the signed network player slot
     */
    public int getStormPlayer() {
        return this.stormPlayer;
    }

    /**
     * @return the unsigned multiplayer byte
     */
    public int getMultiplayerFlag() {
        return this.multiplayerFlag;
    }

    /**
     * @return the unsigned replay byte
     */
    public int getReplayFlag() {
        return this.replayFlag;
    }
}
