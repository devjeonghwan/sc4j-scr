package org.fogofwarlabs.sc4j.scr.net.beaconlink;

import org.fogofwarlabs.sc4j.engine.net.beaconlink.LinkParticipant;
import org.fogofwarlabs.sc4j.engine.net.beaconlink.LinkPlayer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks the addresses of an SC:R game over Beacon.Link: a participant's first address is its key's, a participant whose first address is
 * taken (by a key ground to collide with it, or by the host) gets its next free one, an address is never given to two participants, and the
 * host's list moves a member to the address it names.
 */
final class StormAddressBookTest {
    private static final int SHARED_ADDRESS_FIRST_OCTET = 100;
    private static final int SHARED_ADDRESS_SECOND_OCTET = 0x40;
    private static final int SHARED_ADDRESS_SECOND_OCTET_MASK = 0xC0;

    private static @NotNull LinkParticipant createParticipant(int seed) {
        byte[] key = new byte[LinkPlayer.KEY_LENGTH];

        Arrays.fill(key, (byte) seed);

        return LinkParticipant.of(LinkPlayer.of(key), seed);
    }

    @Test
    void find_free_address_should_give_the_later_participant_of_a_collision_its_next_address() {
        StormAddressBook book = new StormAddressBook();
        LinkParticipant host = createParticipant(1);
        LinkParticipant victim = createParticipant(2);
        LinkParticipant attacker = createParticipant(3);
        InetSocketAddress hostAddress = BeaconLinkProvider.getAddress(host);
        InetSocketAddress victimFirst = StormAddressBook.deriveAddress(victim, 0);

        Assertions.assertEquals(victimFirst, book.findFreeAddress(victim, hostAddress));

        // The attacker holds the victim's first address, as a ground key would: the victim gets another one, not none.
        Assertions.assertTrue(book.put(attacker, victimFirst));

        InetSocketAddress victimAddress = book.findFreeAddress(victim, hostAddress);

        Assertions.assertNotNull(victimAddress);
        Assertions.assertNotEquals(victimFirst, victimAddress);
        Assertions.assertEquals(StormAddressBook.deriveAddress(victim, 1), victimAddress);
        Assertions.assertEquals(SHARED_ADDRESS_FIRST_OCTET, victimAddress.getAddress().getAddress()[0]);
        Assertions.assertEquals(SHARED_ADDRESS_SECOND_OCTET, victimAddress.getAddress().getAddress()[1] & SHARED_ADDRESS_SECOND_OCTET_MASK);
        Assertions.assertTrue(book.put(victim, victimAddress));
        Assertions.assertFalse(book.put(host, victimFirst));
        Assertions.assertEquals(attacker, book.findParticipant(victimFirst));
        Assertions.assertEquals(victimAddress, book.findFreeAddress(victim, hostAddress));

        // A key ground to collide with the host's own address does not get it either.
        LinkParticipant other = createParticipant(4);

        Assertions.assertEquals(StormAddressBook.deriveAddress(other, 1), book.findFreeAddress(other, StormAddressBook.deriveAddress(other, 0)));
    }

    @Test
    void derived_addresses_of_the_attempts_are_distinct_and_put_moves_a_participant() {
        StormAddressBook book = new StormAddressBook();
        LinkParticipant member = createParticipant(5);
        Set<InetSocketAddress> derived = new HashSet<>();

        for (int attempt = 0; attempt < StormAddressBook.MAXIMUM_ATTEMPTS; attempt++) {
            derived.add(StormAddressBook.deriveAddress(member, attempt));
        }

        Assertions.assertEquals(StormAddressBook.MAXIMUM_ATTEMPTS, derived.size());
        Assertions.assertTrue(book.put(member, StormAddressBook.deriveAddress(member, 0)));
        Assertions.assertTrue(book.put(member, StormAddressBook.deriveAddress(member, 2)));
        Assertions.assertNull(book.findParticipant(StormAddressBook.deriveAddress(member, 0)));
        Assertions.assertEquals(member, book.findParticipant(StormAddressBook.deriveAddress(member, 2)));

        book.remove(member);

        Assertions.assertTrue(book.getParticipants().isEmpty());
    }
}
