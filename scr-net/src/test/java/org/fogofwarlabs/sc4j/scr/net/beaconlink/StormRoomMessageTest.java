package org.fogofwarlabs.sc4j.scr.net.beaconlink;

import org.fogofwarlabs.sc4j.engine.net.beaconlink.LinkParticipant;
import org.fogofwarlabs.sc4j.engine.net.beaconlink.LinkPlayer;
import org.fogofwarlabs.sc4j.scr.net.discovery.LanRecord;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks the room messages of SC:R games over Beacon.Link: a record, a member list with the members' addresses and a channel report read
 * back as written, and every malformed datagram reads as no message.
 */
final class StormRoomMessageTest {
    private static final int OLD_MEMBERS_KIND = 2;
    private static final int MEMBERS_KIND = 3;
    private static final int CHANNELS_KIND = 4;
    private static final int UNKNOWN_KIND = 5;
    private static final int MEMBER_COUNT = 3;
    private static final int IPV4_ADDRESS_SIZE = 4;
    private static final int MEMBER_LENGTH = LinkParticipant.LENGTH + IPV4_ADDRESS_SIZE + Short.BYTES;

    private static @NotNull LinkParticipant createParticipant(int seed) {
        byte[] key = new byte[LinkPlayer.KEY_LENGTH];

        Arrays.fill(key, (byte) seed);

        return LinkParticipant.of(LinkPlayer.of(key), seed);
    }

    private static @NotNull Map<@NotNull LinkParticipant, @NotNull InetSocketAddress> createMembers(int count) {
        Map<LinkParticipant, InetSocketAddress> members = new LinkedHashMap<>();

        for (int index = 0; index < count; index++) {
            LinkParticipant member = createParticipant(index + 1);

            members.put(member, StormAddressBook.deriveAddress(member, index));
        }

        return members;
    }

    @Test
    void parse_should_read_the_written_record_and_members() {
        LanRecord record = LanRecord.createBuilder("s4jgame", 1).add("_type", "2").add("map_name", "Bottleneck").build();
        StormRoomMessage game = StormRoomMessage.parse(StormRoomMessage.encodeGame(record));
        Map<LinkParticipant, InetSocketAddress> members = createMembers(MEMBER_COUNT);
        StormRoomMessage list = StormRoomMessage.parse(StormRoomMessage.encodeMembers(members));

        Assertions.assertNotNull(game);
        Assertions.assertEquals(StormRoomMessage.Kind.GAME, game.getKind());
        Assertions.assertNotNull(game.getRecord());
        Assertions.assertArrayEquals(record.encode(), game.getRecord().encode());
        Assertions.assertNotNull(list);
        Assertions.assertEquals(StormRoomMessage.Kind.MEMBERS, list.getKind());
        Assertions.assertEquals(members, list.getMembers());
        Assertions.assertEquals(List.copyOf(members.keySet()), List.copyOf(list.getMembers().keySet()));

        List<LinkParticipant> connected = List.of(createParticipant(1), createParticipant(2));
        StormRoomMessage report = StormRoomMessage.parse(StormRoomMessage.encodeChannels(connected));

        Assertions.assertNotNull(report);
        Assertions.assertEquals(StormRoomMessage.Kind.CHANNELS, report.getKind());
        Assertions.assertEquals(connected, report.getConnected());
        Assertions.assertTrue(report.getMembers().isEmpty());
    }

    @Test
    void parse_should_reject_malformed_datagrams() {
        byte[] game = StormRoomMessage.encodeGame(LanRecord.createBuilder("s4jgame", 1).add("map_name", "Bottleneck").build());
        byte[] members = StormRoomMessage.encodeMembers(createMembers(2));
        byte[] tooMany = new byte[Byte.BYTES + Byte.BYTES + (StormRoomMessage.MAXIMUM_MEMBERS + 1) * MEMBER_LENGTH];
        byte[] repeated = Arrays.copyOf(members, members.length);
        byte[] oldList = new byte[Byte.BYTES + Byte.BYTES + LinkParticipant.LENGTH];

        tooMany[0] = MEMBERS_KIND;
        tooMany[1] = (byte) (StormRoomMessage.MAXIMUM_MEMBERS + 1);
        System.arraycopy(members, Byte.BYTES + Byte.BYTES, repeated, Byte.BYTES + Byte.BYTES + MEMBER_LENGTH, LinkParticipant.LENGTH);
        oldList[0] = OLD_MEMBERS_KIND;
        oldList[1] = 1;

        Assertions.assertNull(StormRoomMessage.parse(new byte[0]));
        Assertions.assertNull(StormRoomMessage.parse(new byte[]{UNKNOWN_KIND}));
        Assertions.assertNull(StormRoomMessage.parse(new byte[]{MEMBERS_KIND}));
        Assertions.assertNull(StormRoomMessage.parse(Arrays.copyOf(members, members.length - 1)));
        Assertions.assertNull(StormRoomMessage.parse(Arrays.copyOf(members, members.length + 1)));
        Assertions.assertNull(StormRoomMessage.parse(tooMany));
        Assertions.assertNull(StormRoomMessage.parse(repeated));
        Assertions.assertNull(StormRoomMessage.parse(oldList));
        Assertions.assertNull(StormRoomMessage.parse(new byte[]{CHANNELS_KIND, 1}));
        Assertions.assertNull(StormRoomMessage.parse(Arrays.copyOf(game, game.length - 1)));
    }
}
