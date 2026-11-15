package org.fogofwarlabs.sc4j.scr.net.lan;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * Opens the UDP game ports of the LAN tests that run several clients on this computer on free ports the system assigns, so tests of several
 * builds that run at once never need the same port. A port another process takes between the search and the bind is replaced by another free
 * port.
 */
final class LanTestPortUtils {
    /**
     * The number of free ports tried before a bind failure fails the test.
     */
    private static final int BIND_ATTEMPTS = 8;

    /**
     * Opens a game socket on a free port.
     */
    @FunctionalInterface
    interface PortBinding {
        /**
         * Opens the game socket on a port.
         *
         * @param port the local UDP port
         * @throws IOException if the port cannot be bound or the game cannot be opened
         */
        void bind(int port) throws IOException;
    }


    /**
     * Runs a binding on a free UDP port, on another free port as long as the port is taken in between.
     *
     * @param binding the binding that opens the game socket
     * @return the bound port
     * @throws IOException if no free port could be bound or the binding fails otherwise
     */
    static int bindFreePort(@NotNull PortBinding binding) throws IOException {
        BindException failure = new BindException("No free UDP port could be bound.");

        for (int attempt = 0; attempt < BIND_ATTEMPTS; attempt++) {
            int port = findFreePort();

            try {
                binding.bind(port);

                return port;
            } catch (BindException exception) {
                failure = exception;
            }
        }

        throw failure;
    }

    private static int findFreePort() throws SocketException {
        try (DatagramSocket probe = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort();
        }
    }


    private LanTestPortUtils() {
    }
}
