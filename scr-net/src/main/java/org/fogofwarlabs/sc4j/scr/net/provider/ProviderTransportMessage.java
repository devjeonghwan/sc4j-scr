package org.fogofwarlabs.sc4j.scr.net.provider;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;

/**
 * Frames a checked provider packet with a four-byte zero prefix.
 * Ordinary messages allow at most {@code 512} packet bytes; parsing retains any bytes beyond the inner encoded length.
 * Nonzero control prefixes are rejected by this entry point.
 */
public final class ProviderTransportMessage {
    /**
     * The largest provider packet in bytes.
     */
    public static final int MAXIMUM_PROVIDER_PACKET_SIZE = 512;

    private static final int PREFIX_SIZE = 4;

    /**
     * Creates a {@link ProviderTransportMessage} instance with the ordinary zero DWORD prefix.
     *
     * @param packet the complete provider envelope to submit
     * @return the created {@link ProviderTransportMessage} instance
     * @throws IllegalArgumentException if the encoded provider packet exceeds {@code 512} bytes
     */
    public static @NotNull ProviderTransportMessage create(@NotNull ProviderPacket packet) {
        byte[] packetBytes = packet.getEncodedBytes();

        if (packetBytes.length > MAXIMUM_PROVIDER_PACKET_SIZE) {
            throw Exceptions.newIllegalArgumentException("The concrete provider network service accepts at most 512 packet bytes.");
        }

        byte[] encoded = new byte[PREFIX_SIZE + packetBytes.length];

        System.arraycopy(packetBytes, 0, encoded, PREFIX_SIZE, packetBytes.length);

        return new ProviderTransportMessage(encoded, packet);
    }

    /**
     * Creates a {@link ProviderTransportMessage} instance while retaining bytes after its encoded inner length.
     * <p>
     * Allows {@code 1..512} bytes after a zero prefix, then validates the inner provider envelope.
     *
     * @param encoded the complete buffer delivered by the network service
     * @return the created {@link ProviderTransportMessage} instance
     * @throws IOException if the outer length, ordinary prefix, or provider envelope is invalid
     */
    public static @NotNull ProviderTransportMessage parse(byte @NotNull [] encoded) throws IOException {
        if (encoded.length <= PREFIX_SIZE || encoded.length > PREFIX_SIZE + MAXIMUM_PROVIDER_PACKET_SIZE) {
            throw Exceptions.newIOException("Provider network message must contain 1 through 512 bytes after its prefix.");
        }

        int prefix = 0;

        for (int index = 0; index < PREFIX_SIZE; index++) {
            prefix |= encoded[index];
        }

        if (prefix != 0) {
            throw Exceptions.newIOException("Nonzero provider network control-message prefixes are unsupported by this entry point.");
        }

        byte[] snapshot = encoded.clone();
        ProviderPacket packet = ProviderPacket.parse(Arrays.copyOfRange(snapshot, PREFIX_SIZE, snapshot.length));

        return new ProviderTransportMessage(snapshot, packet);
    }


    private final byte @NotNull [] encoded;
    private final @NotNull ProviderPacket packet;

    private ProviderTransportMessage(byte @NotNull [] encoded, @NotNull ProviderPacket packet) {
        this.encoded = encoded;
        this.packet  = packet;
    }

    /**
     * @return the immutable checked provider envelope without the outer prefix
     */
    public @NotNull ProviderPacket getPacket() {
        return this.packet;
    }

    /**
     * @return a copy of the complete framed buffer, including any received trailing provider bytes
     */
    public byte @NotNull [] getEncodedBytes() {
        return this.encoded.clone();
    }
}
