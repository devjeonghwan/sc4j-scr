package org.fogofwarlabs.screcorder.sample.player;

import org.fogofwarlabs.screcorder.sample.Sample;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Contains an immutable collection of players observed in one frame.
 */
public final class PlayersSample implements Sample {
    private final @NotNull List<PlayerState> players;

    /**
     * Creates a {@link PlayersSample} instance.
     *
     * @param players the observed players
     */
    public PlayersSample(@NotNull List<PlayerState> players) {
        this.players = List.copyOf(players);
    }


    /**
     * @return the immutable observed players
     */
    public @NotNull List<PlayerState> getPlayers() {
        return this.players;
    }
}
