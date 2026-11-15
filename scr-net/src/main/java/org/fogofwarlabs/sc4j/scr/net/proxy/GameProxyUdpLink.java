package org.fogofwarlabs.sc4j.scr.net.proxy;

import org.fogofwarlabs.sc4j.base.log.Logger;
import org.fogofwarlabs.sc4j.engine.net.DatagramLink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Carries the game socket's datagrams on the local network: a UDP socket bound to a port on every interface, whose dedicated receiver thread
 * queues every received datagram, so that {@link #receive} returns at once and the socket is read even while nobody polls.
 */
public final class GameProxyUdpLink implements DatagramLink<InetSocketAddress> {
    private static final @NotNull Logger LOGGER = Logger.of(GameProxyUdpLink.class);

    private static final int MAXIMUM_DATAGRAM = 2048;

    /**
     * Opens a link bound to a UDP port on every interface.
     *
     * @param port the local port, {@link GameProxySocket#GAME_PORT} for a game, {@code 0} for any free port
     * @return the created {@link GameProxyUdpLink} instance
     * @throws SocketException if the port cannot be bound
     */
    public static @NotNull GameProxyUdpLink open(int port) throws SocketException {
        DatagramSocket socket = new DatagramSocket(null);

        try {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(port));

            return new GameProxyUdpLink(socket);
        } catch (SocketException | RuntimeException exception) {
            socket.close();

            throw exception;
        }
    }


    private final @NotNull DatagramSocket socket;
    private final @NotNull ConcurrentLinkedQueue<DatagramPacket> received;

    private volatile boolean closed;

    private GameProxyUdpLink(@NotNull DatagramSocket socket) {
        this.socket   = socket;
        this.received = new ConcurrentLinkedQueue<>();

        Thread receiver = new Thread(this::receiveDatagrams, "lan-game-socket");

        this.closed = false;

        receiver.setDaemon(true);
        receiver.start();
    }

    /**
     * @return the bound local port
     */
    public int getLocalPort() {
        return this.socket.getLocalPort();
    }


    /**
     * Sends one datagram: the remaining bytes of the buffer.
     *
     * @param datagram the datagram, read from its position to its limit
     * @param target   the receiver's address
     * @throws IOException if the link is closed or the datagram cannot be sent
     */
    @Override
    public void send(@NotNull ByteBuffer datagram, @NotNull InetSocketAddress target) throws IOException {
        byte[] payload = new byte[datagram.remaining()];

        datagram.get(payload);
        this.socket.send(new DatagramPacket(payload, payload.length, target));
    }

    /**
     * Receives the next waiting datagram into the buffer, from its position; a datagram longer than the remaining space is cut. Returns at once
     * when none is waiting.
     *
     * @param buffer the buffer that receives the datagram
     * @return the sender's address, or {@code null} if no datagram is waiting
     * @throws IOException if the link is closed
     */
    @Override
    public @Nullable InetSocketAddress receive(@NotNull ByteBuffer buffer) throws IOException {
        DatagramPacket incoming = this.received.poll();

        if (incoming == null) {
            return null;
        }

        buffer.put(incoming.getData(), 0, Math.min(incoming.getLength(), buffer.remaining()));

        return (InetSocketAddress) incoming.getSocketAddress();
    }

    private void receiveDatagrams() {
        while (!this.closed) {
            DatagramPacket packet = new DatagramPacket(new byte[MAXIMUM_DATAGRAM], MAXIMUM_DATAGRAM);

            try {
                this.socket.receive(packet);
            } catch (SocketException exception) {
                if (!this.closed) {
                    LOGGER.warning("GAME SOCKET RECEIVE STOPPED: " + exception.getMessage());
                }

                return;
            } catch (IOException exception) {
                continue;
            }

            this.received.add(packet);
        }
    }


    /**
     * Closes the link; it receives and sends nothing afterwards.
     *
     * @implSpec Stops receiving and closes the socket.
     */
    @Override
    public void close() {
        this.closed = true;
        this.socket.close();
    }
}
