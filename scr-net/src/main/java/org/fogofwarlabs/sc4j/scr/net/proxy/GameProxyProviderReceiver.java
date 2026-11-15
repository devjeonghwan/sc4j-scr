package org.fogofwarlabs.sc4j.scr.net.proxy;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.net.provider.ProviderPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Applies the datagram hash gate, answers probes and maps inbound endpoints before provider delivery.
 * IPv4 integers store the first dotted octet in the low byte. Routes replace the endpoint only
 * when both destination components are nonzero. Callback and route state require one owning thread.
 */
public final class GameProxyProviderReceiver {
    /**
     * The supported private and loopback IPv4 networks in the native DWORD layout: {@code 10/8}, {@code 127/8},
     * {@code 172.16/12} and {@code 192.168/16}.
     */
    private static final int FIRST_OCTET_MASK = 0xFF;
    private static final int PRIVATE_NETWORK_10 = 10;
    private static final int LOOPBACK_NETWORK = 127;
    private static final int PRIVATE_NETWORK_172_MASK = 0xF0FF;
    private static final int PRIVATE_NETWORK_172 = 0x10AC;
    private static final int PRIVATE_NETWORK_192_MASK = 0xFFFF;
    private static final int PRIVATE_NETWORK_192 = 0xA8C0;

    /**
     * The bits of a route key or value that hold the port; the address is above them.
     */
    private static final int PORT_BITS = 16;
    private static final int PORT_MASK = 0xFFFF;

    /**
     * Sends the datagrams a receive answers with.
     */
    @FunctionalInterface
    public interface DatagramSender {
        /**
         * Sends a datagram to an endpoint.
         *
         * @param address  the destination native IPv4 DWORD
         * @param port     the destination unsigned 16-bit port
         * @param datagram the datagram to send
         * @throws IOException if the datagram cannot be sent
         */
        void send(int address, int port, @NotNull GameProxyDatagram datagram) throws IOException;
    }

    /**
     * Receives an ordinary provider packet after endpoint mapping and envelope checks.
     */
    @FunctionalInterface
    public interface PacketReceiver {
        /**
         * Delivers a packet to the caller's established participant resolution boundary.
         *
         * @param address the mapped native IPv4 DWORD
         * @param port    the mapped unsigned 16-bit port
         * @param packet  the checked ordinary provider packet
         * @throws IOException if participant resolution or delivery fails
         */
        void receive(int address, int port, @NotNull ProviderPacket packet) throws IOException;
    }

    /**
     * Checks the supported private and loopback IPv4 ranges.
     *
     * @param address the native IPv4 DWORD
     * @return {@code true} if the address is in {@code 10/8}, {@code 127/8}, {@code 172.16/12} or {@code 192.168/16}, {@code false} otherwise
     */
    static boolean isPrivateAddress(int address) {
        return (address & FIRST_OCTET_MASK) == PRIVATE_NETWORK_10 || (address & FIRST_OCTET_MASK) == LOOPBACK_NETWORK ||
               (address & PRIVATE_NETWORK_172_MASK) == PRIVATE_NETWORK_172 || (address & PRIVATE_NETWORK_192_MASK) == PRIVATE_NETWORK_192;
    }

    private static long calculateRouteKey(int address, int port) {
        return (Integer.toUnsignedLong(address) << PORT_BITS) | port;
    }


    private final @Nullable LongSupplier hashContext;

    private final @NotNull Map<Long, Long> inboundRoutes;

    /**
     * Creates a {@link GameProxyProviderReceiver} instance with an optional hash callback and an empty inbound route map.
     * Applicable receives invoke the callback twice, with envelope hashing between them.
     *
     * @param hashContext the caller-established hash callback, or {@code null} when absent
     */
    public GameProxyProviderReceiver(@Nullable LongSupplier hashContext) {
        this.hashContext = hashContext;

        this.inboundRoutes = new HashMap<>();
    }

    /**
     * Replaces an inbound route, retaining zero destination components.
     *
     * @param sourceAddress      the received native IPv4 DWORD used as the lookup key
     * @param sourcePort         the received unsigned 16-bit port used as the lookup key
     * @param destinationAddress the native IPv4 DWORD to deliver to the provider
     * @param destinationPort    the unsigned 16-bit port to deliver to the provider
     * @throws IllegalArgumentException if a port is outside the unsigned 16-bit range
     */
    public void recordInboundRoute(int sourceAddress, int sourcePort, int destinationAddress, int destinationPort) {
        if ((sourcePort & ~PORT_MASK) != 0 || (destinationPort & ~PORT_MASK) != 0) {
            throw Exceptions.newIllegalArgumentException("Game proxy route ports must be unsigned 16-bit values.");
        }

        this.inboundRoutes.put(calculateRouteKey(sourceAddress, sourcePort), calculateRouteKey(destinationAddress, destinationPort));
    }

    /**
     * Applies the hash gate, answers a probe, then delivers an ordinary provider packet.
     * <p>
     * For private sources with a callback and nonzero hash field, XOR equality with the second callback value drops the
     * datagram. A remaining datagram with a nonzero probe timestamp that is not a probe answer is answered to its source
     * through {@code sender}, whatever its type. Relayed probes and probe answers carry no provider packet and end there.
     * A retained route is applied before provider decoding and callback delivery.
     *
     * @param datagram      the parsed outer envelope
     * @param sourceAddress the received native IPv4 DWORD
     * @param sourcePort    the received unsigned 16-bit port
     * @param sender        the caller's datagram send callback for the probe answer
     * @param receiver      the caller's participant resolution and provider receive callback
     * @return {@code true} if the packet was delivered, {@code false} otherwise
     * @throws IllegalArgumentException      if the source port is outside the unsigned 16-bit range
     * @throws IOException                   if address information or the provider envelope is invalid, or the answer cannot be sent
     * @throws UnsupportedOperationException if a control type or relay branch is required
     */
    public boolean receive(@NotNull GameProxyDatagram datagram,
                           int sourceAddress, int sourcePort,
                           @NotNull DatagramSender sender, @NotNull PacketReceiver receiver) throws IOException {
        if ((sourcePort & ~PORT_MASK) != 0) {
            throw Exceptions.newIllegalArgumentException("Game proxy source port must be unsigned 16-bit.");
        }

        if (isPrivateAddress(sourceAddress) && this.hashContext != null && datagram.getScalarField(GameProxyDatagram.PAYLOAD_HASH_FIELD) != 0) {
            long firstValue = this.hashContext.getAsLong();
            long envelopeHash = datagram.calculateReceiveHash();
            long secondValue = this.hashContext.getAsLong();

            if ((envelopeHash ^ firstValue) == secondValue) {
                return false;
            }
        }

        int messageType = datagram.getMessageType();

        if (datagram.getScalarField(GameProxyDatagram.PROBE_TIMESTAMP_FIELD) != 0 && messageType != GameProxyDatagram.TYPE_PROBE_ANSWER) {
            sender.send(sourceAddress, sourcePort, GameProxyDatagram.createProbeAnswer(datagram));
        }

        if (datagram.isRelayed()) {
            throw Exceptions.newUnsupportedOperationException("Game proxy relay receive branches are not implemented.");
        }

        if (messageType == GameProxyDatagram.TYPE_RELAYED_PROBE || messageType == GameProxyDatagram.TYPE_PROBE_ANSWER) {
            return false;
        }

        if (messageType != GameProxyDatagram.TYPE_DATA) {
            throw Exceptions.newUnsupportedOperationException("Game proxy control receive branches are not implemented.");
        }

        Long route = this.inboundRoutes.get(calculateRouteKey(sourceAddress, sourcePort));
        int address = sourceAddress;
        int port = sourcePort;

        if (route != null && (route >>> PORT_BITS) != 0 && (route & PORT_MASK) != 0) {
            address = (int) (route >>> PORT_BITS);
            port    = (int) (route & PORT_MASK);
        }

        receiver.receive(address, port, datagram.parseProviderMessage().getPacket());

        return true;
    }
}
