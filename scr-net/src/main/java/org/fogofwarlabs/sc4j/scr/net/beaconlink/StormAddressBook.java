package org.fogofwarlabs.sc4j.scr.net.beaconlink;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.net.beaconlink.LinkParticipant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the addresses of the participants of one SC:R game over Beacon.Link: the IPv4 socket addresses the Storm session uses for them,
 * one per participant and never one for two.
 * <p>
 * A participant's address derives from its wire form (its player key and connection number, so that two connections of one account get two
 * addresses): SHA-256 of a domain text, the wire form and, after the first, an attempt number; the first four bytes give the host bits of
 * {@code 100.64.0.0/10}, the next two the port above the well-known ports. The host of a game assigns every
 * joiner the first derived address that is free and not the host's own, and its member list is the whole truth for the joiners. So a joiner
 * cannot take another participant's address by grinding a key whose first address collides with it: the later participant of a collision
 * only gets its next address, whoever chose the key.
 */
final class StormAddressBook {
    /**
     * The most derived addresses tried for one participant: one more than the other members and the host of a game can hold, so a free one
     * is always found (unless two attempts derive the same address).
     */
    static final int MAXIMUM_ATTEMPTS = StormRoomMessage.MAXIMUM_MEMBERS + 1;

    private static final @NotNull String ADDRESS_DOMAIN = "SC4J SC:R Storm address 1";
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";
    private static final int ADDRESS_NETWORK = 0x64400000;
    private static final int ADDRESS_HOST_MASK = 0x003FFFFF;
    private static final int IPV4_ADDRESS_SIZE = 4;
    private static final int PORT_OFFSET = 4;
    private static final int FIRST_PORT = 1024;
    private static final int PORT_COUNT = 0x10000 - FIRST_PORT;
    private static final int UNSIGNED_SHORT_MASK = 0xFFFF;

    /**
     * Derives an address of a participant.
     *
     * @param participant the participant
     * @param attempt     the attempt, {@code 0} for the participant's first address
     * @return the address
     */
    static @NotNull InetSocketAddress deriveAddress(@NotNull LinkParticipant participant, int attempt) {
        byte[] digest;

        try {
            MessageDigest hash = MessageDigest.getInstance(DIGEST_ALGORITHM);

            hash.update(ADDRESS_DOMAIN.getBytes(StandardCharsets.UTF_8));
            hash.update(participant.getBytes());

            if (attempt > 0) {
                hash.update((byte) attempt);
            }

            digest = hash.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw Exceptions.newIllegalStateException(exception, "The JDK has no {0}.", DIGEST_ALGORITHM);
        }

        ByteBuffer values = ByteBuffer.wrap(digest);
        int host = ADDRESS_NETWORK | values.getInt(0) & ADDRESS_HOST_MASK;
        int port = FIRST_PORT + (values.getShort(PORT_OFFSET) & UNSIGNED_SHORT_MASK) % PORT_COUNT;

        try {
            return new InetSocketAddress(InetAddress.getByAddress(ByteBuffer.allocate(IPV4_ADDRESS_SIZE).putInt(host).array()), port);
        } catch (UnknownHostException exception) {
            throw Exceptions.newIllegalStateException(exception, "Four bytes are an IPv4 address.");
        }
    }


    private final @NotNull Map<@NotNull InetSocketAddress, @NotNull LinkParticipant> participants;
    private final @NotNull Map<@NotNull LinkParticipant, @NotNull InetSocketAddress> addresses;

    /**
     * Creates an empty {@link StormAddressBook} instance.
     */
    StormAddressBook() {
        this.participants = new LinkedHashMap<>();
        this.addresses    = new LinkedHashMap<>();
    }

    /**
     * @return the participants with an address, in the order they got it
     */
    @NotNull List<@NotNull LinkParticipant> getParticipants() {
        return List.copyOf(this.addresses.keySet());
    }

    /**
     * Finds the participant with an address.
     *
     * @param address the address
     * @return the participant with the address, or {@code null} if no participant has it
     */
    @Nullable LinkParticipant findParticipant(@NotNull InetSocketAddress address) {
        return this.participants.get(address);
    }

    /**
     * Finds a participant's address.
     *
     * @param participant the participant
     * @return the participant's address, or {@code null} if it has none
     */
    @Nullable InetSocketAddress findAddress(@NotNull LinkParticipant participant) {
        return this.addresses.get(participant);
    }


    /**
     * Finds the address the host assigns a participant: the address it has, or its first derived address that no other participant has and
     * that is not the reserved one.
     *
     * @param participant the participant
     * @param reserved    the address no participant gets, the host's own
     * @return the address, or {@code null} if every attempt is taken
     */
    @Nullable InetSocketAddress findFreeAddress(@NotNull LinkParticipant participant, @NotNull InetSocketAddress reserved) {
        InetSocketAddress current = this.addresses.get(participant);

        if (current != null) {
            return current;
        }

        for (int attempt = 0; attempt < MAXIMUM_ATTEMPTS; attempt++) {
            InetSocketAddress address = deriveAddress(participant, attempt);

            if (!address.equals(reserved) && !this.participants.containsKey(address)) {
                return address;
            }
        }

        return null;
    }

    /**
     * Gives a participant an address, replacing the address it had.
     *
     * @param participant the participant
     * @param address     the address
     * @return {@code true} if the participant has the address now, {@code false} otherwise (another participant has it)
     */
    boolean put(@NotNull LinkParticipant participant, @NotNull InetSocketAddress address) {
        LinkParticipant holder = this.participants.get(address);

        if (holder != null && !holder.equals(participant)) {
            return false;
        }

        this.remove(participant);
        this.participants.put(address, participant);
        this.addresses.put(participant, address);

        return true;
    }

    /**
     * Removes a participant's address; a participant without one is ignored.
     *
     * @param participant the participant
     */
    void remove(@NotNull LinkParticipant participant) {
        InetSocketAddress address = this.addresses.remove(participant);

        if (address != null) {
            this.participants.remove(address);
        }
    }
}
