package org.fogofwarlabs.sc4j.scr.net.provider;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Verifies that provider transport messages frame provider packets behind the zero prefix and reject other prefixes and
 * sizes.
 */
final class ProviderTransportMessageTest {
    private static final int PREFIX_SIZE = 4;

    @Test
    void create_should_prefix_the_provider_fixture_with_a_zero_dword() throws Exception {
        byte[] expected = HexFormat.of().parseHex("00000000913b0f003412cdab02000700153412");
        ProviderPacket packet = ProviderPacket.createTurn(0x1234, 0xABCD, 7, new byte[]{0x15, 0x34, 0x12});
        ProviderTransportMessage message = ProviderTransportMessage.create(packet);

        Assertions.assertSame(packet, message.getPacket());
        Assertions.assertArrayEquals(expected, message.getEncodedBytes());

        ProviderPacket parsed = ProviderTransportMessage.parse(expected).getPacket();

        Assertions.assertEquals(0x1234, parsed.getSequence());
        Assertions.assertEquals(0xABCD, parsed.getAcknowledgement());
        Assertions.assertArrayEquals(packet.getEncodedBytes(), parsed.getEncodedBytes());
    }

    @Test
    void transport_message_limits_its_size_below_the_larger_envelope_size() throws Exception {
        ProviderPacket maximum = ProviderPacket.createTurn(0xFFFF, 0xFFFF, 0xFF, new byte[500]);
        byte[] encoded = ProviderTransportMessage.create(maximum).getEncodedBytes();

        Assertions.assertEquals(516, encoded.length);
        Assertions.assertArrayEquals(maximum.getEncodedBytes(), ProviderTransportMessage.parse(encoded).getPacket().getEncodedBytes());

        ProviderPacket largerEnvelope = ProviderPacket.createTurn(0, 0, 0, new byte[501]);

        Assertions.assertThrows(IllegalArgumentException.class, () -> ProviderTransportMessage.create(largerEnvelope));
        Assertions.assertThrows(IOException.class, () -> ProviderTransportMessage.parse(Arrays.copyOf(encoded, 517)));
    }

    @Test
    void parse_should_keep_outer_trailing_bytes_and_a_noncanonical_inner_checksum() throws Exception {
        byte[] encoded = HexFormat.of().parseHex("00000000c9000d00000000000200000027aabbcc");
        byte[] original = encoded.clone();
        ProviderTransportMessage message = ProviderTransportMessage.parse(encoded);

        encoded[PREFIX_SIZE] = 0;

        byte[] returned = message.getEncodedBytes();

        returned[0] = 1;

        Assertions.assertArrayEquals(original, message.getEncodedBytes());
        Assertions.assertArrayEquals(HexFormat.of().parseHex("c9000d00000000000200000027"), message.getPacket().getEncodedBytes());
        Assertions.assertArrayEquals(new byte[]{0x27}, message.getPacket().getPayloadBytes());
    }

    @Test
    void parse_should_reject_control_message_prefixes_and_short_messages() {
        byte[] ordinary = HexFormat.of().parseHex("0000000052990d00000000000200000005");

        for (int prefixIndex = 0; prefixIndex < PREFIX_SIZE; prefixIndex++) {
            byte[] changed = ordinary.clone();

            changed[prefixIndex] = 3;

            Assertions.assertThrows(IOException.class, () -> ProviderTransportMessage.parse(changed));
        }

        Assertions.assertThrows(IOException.class, () -> ProviderTransportMessage.parse(HexFormat.of().parseHex("0300000000000000")));
        Assertions.assertThrows(IOException.class, () -> ProviderTransportMessage.parse(HexFormat.of().parseHex("0600000000000000")));
        Assertions.assertThrows(IOException.class, () -> ProviderTransportMessage.parse(new byte[PREFIX_SIZE]));
        Assertions.assertThrows(IOException.class, () -> ProviderTransportMessage.parse(new byte[5]));
        Assertions.assertThrows(IOException.class, () -> ProviderTransportMessage.parse(new byte[16]));
    }

    @Test
    void framed_turns_reach_the_participant_queue_and_acknowledge_exclusively() throws Exception {
        ProviderChannel local = new ProviderChannel(0xFFFF, 42);
        ProviderChannel remote = new ProviderChannel(42, 0xFFFF);
        ProviderPacket localPacket = local.createOutgoingTurn(42, 7, new byte[0]);
        ProviderPacket delivered = ProviderTransportMessage.parse(ProviderTransportMessage.create(localPacket).getEncodedBytes()).getPacket();

        Assertions.assertEquals(ProviderChannel.ReceiveResult.QUEUED, remote.receive(delivered));

        ProviderPacket remoteTurn = remote.pollExpectedPacket();

        Assertions.assertNotNull(remoteTurn);
        Assertions.assertArrayEquals(new byte[]{5}, remoteTurn.getPayloadBytes());

        ProviderPacket reply = remote.createOutgoingTurn(0, 3, new byte[]{0x55});
        ProviderPacket returned = ProviderTransportMessage.parse(ProviderTransportMessage.create(reply).getEncodedBytes()).getPacket();

        Assertions.assertEquals(ProviderChannel.ReceiveResult.QUEUED, local.receive(returned));
        Assertions.assertEquals(0, local.getPendingOutgoingCount());

        ProviderPacket localTurn = local.pollExpectedPacket();

        Assertions.assertNotNull(localTurn);
        Assertions.assertArrayEquals(new byte[]{0x55}, localTurn.getPayloadBytes());
    }
}
