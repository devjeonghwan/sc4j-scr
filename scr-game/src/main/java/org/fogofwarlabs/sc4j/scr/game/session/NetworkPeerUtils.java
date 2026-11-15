package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.lockstep.LockstepPeer;
import org.fogofwarlabs.sc4j.engine.lockstep.LockstepTransport;
import org.jetbrains.annotations.NotNull;

/**
 * Holds the engine {@link LockstepPeer} of every native network id: the senders of the turns a {@link LockstepTransport} of the native turn
 * stream receives, and the participants its waiting state awaits. A network id is a byte (players {@code 0} to {@code 7}, observers from
 * {@code 0x80}); its peer number is the id. The live game maps a network id to the slot it holds through its player slots, as the native
 * records do.
 */
public final class NetworkPeerUtils {
    /**
     * The number of network ids: one byte.
     */
    public static final int NETWORK_ID_COUNT = 0x100;

    private static final @NotNull LockstepPeer @NotNull [] PEERS = new LockstepPeer[NETWORK_ID_COUNT];

    static {
        for (int playerId = 0; playerId < NETWORK_ID_COUNT; playerId++) {
            PEERS[playerId] = new LockstepPeer(playerId);
        }
    }

    /**
     * Returns the peer of a network id, the same instance for every call.
     *
     * @param playerId the network id, {@code 0} to {@code 255}
     * @return the peer whose number is the network id
     * @throws IllegalArgumentException if the id is not a byte
     */
    public static @NotNull LockstepPeer getPeer(int playerId) {
        if (playerId < 0 || playerId >= NETWORK_ID_COUNT) {
            throw Exceptions.newIllegalArgumentException("Invalid network player id: {0}", playerId);
        }

        return PEERS[playerId];
    }

    private NetworkPeerUtils() {
    }
}
