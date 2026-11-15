package org.fogofwarlabs.sc4j.scr.client;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.net.beaconlink.BeaconLink;
import org.fogofwarlabs.sc4j.engine.net.beaconlink.LinkAccount;
import org.fogofwarlabs.sc4j.scr.net.beaconlink.BeaconLinkProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Opens the Beacon.Link sessions of the client's multiplayer screens: the run's Beacon.Link, which the runtime gives the SC:R mod with the
 * nodes of the runtime's command line. The sessions play with the run's account; a run without an account (a guest), without a seed source or
 * a client started without the runtime has no Beacon.Link.
 */
public final class BeaconLinkAccess {
    /**
     * The access of a client without Beacon.Link.
     */
    public static final @NotNull BeaconLinkAccess NONE = new BeaconLinkAccess(null);

    private final @Nullable BeaconLink beaconLink;

    /**
     * Creates a {@link BeaconLinkAccess} instance.
     *
     * @param beaconLink the run's Beacon.Link, or {@code null} if the run offers none
     */
    public BeaconLinkAccess(@Nullable BeaconLink beaconLink) {
        this.beaconLink = beaconLink;
    }

    /**
     * @return {@code true} if the run offers Beacon.Link and has an account, {@code false} otherwise
     */
    public boolean isAvailable() {
        return this.getAccount() != null;
    }

    /**
     * @return the run's account, or {@code null} without Beacon.Link or for a run without an account
     */
    public @Nullable LinkAccount getAccount() {
        BeaconLink link = this.beaconLink;

        return link == null ? null : link.getAccount();
    }

    /**
     * Opens a session of the run's account, named with the account name, and the network of the SC:R games on it.
     *
     * @return the network, which owns the session
     * @throws IOException if the run offers no Beacon.Link or has no account, or the key or the socket cannot be opened
     */
    public @NotNull BeaconLinkProvider open() throws IOException {
        BeaconLink link = this.beaconLink;
        LinkAccount account = link == null ? null : link.getAccount();

        if (link == null || account == null) {
            throw Exceptions.newIOException("This run has no account for Beacon.Link: start SC4J from the launcher.");
        }

        return new BeaconLinkProvider(link.open());
    }
}
