package org.fogofwarlabs.sc4j.scr.net.provider;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

/**
 * Verifies that a provider channel creates, queues, orders, acknowledges and consumes packets within the native sequence
 * windows.
 */
final class ProviderChannelTest {
    @Test
    void create_outgoing_turn_should_write_the_empty_command_marker_and_wrap_the_sequence() {
        ProviderChannel channel = new ProviderChannel(0xFFFF, 0);
        ProviderPacket first = channel.createOutgoingTurn(0xABCD, 7, new byte[0]);
        ProviderPacket second = channel.createOutgoingTurn(0xABCE, 7, new byte[]{9, 1, 0x26, 0x0E});

        Assertions.assertEquals(0xFFFF, first.getSequence());
        Assertions.assertEquals(0xABCD, first.getAcknowledgement());
        Assertions.assertEquals(7, first.getSenderIdentifier());
        Assertions.assertArrayEquals(new byte[]{5}, first.getPayloadBytes());
        Assertions.assertEquals(0, second.getSequence());
        Assertions.assertArrayEquals(new byte[]{9, 1, 0x26, 0x0E}, second.getPayloadBytes());
        Assertions.assertEquals(1, channel.getNextOutgoingSequence());
        Assertions.assertEquals(2, channel.getPendingOutgoingCount());
        Assertions.assertSame(first, channel.getOldestPendingPacket());
        Assertions.assertNull(channel.pollExpectedPacket());
    }

    @Test
    void receive_should_queue_reordered_turns_and_poll_them_in_order_across_the_wrap() {
        ProviderChannel channel = new ProviderChannel(0, 0xFFFE);
        ProviderPacket first = ProviderPacket.createTurn(0xFFFE, 0, 3, new byte[]{5});
        ProviderPacket second = ProviderPacket.createTurn(0xFFFF, 0, 3, new byte[]{0x55});
        ProviderPacket third = ProviderPacket.createTurn(0, 0, 3, new byte[]{0x56});

        Assertions.assertEquals(ProviderChannel.ReceiveResult.QUEUED, channel.receive(third));
        Assertions.assertEquals(ProviderChannel.ReceiveResult.QUEUED, channel.receive(second));
        Assertions.assertFalse(channel.hasExpectedPacket());
        Assertions.assertNull(channel.pollExpectedPacket());
        Assertions.assertEquals(0xFFFE, channel.getExpectedSequence());
        Assertions.assertEquals(ProviderChannel.ReceiveResult.QUEUED, channel.receive(first));
        Assertions.assertEquals(ProviderChannel.ReceiveResult.DUPLICATE, channel.receive(second));
        Assertions.assertEquals(3, channel.getReceivedCount());
        Assertions.assertSame(first, channel.pollExpectedPacket());
        Assertions.assertSame(second, channel.pollExpectedPacket());
        Assertions.assertSame(third, channel.pollExpectedPacket());
        Assertions.assertEquals(1, channel.getExpectedSequence());
        Assertions.assertEquals(0, channel.getReceivedCount());
        Assertions.assertEquals(ProviderChannel.ReceiveResult.STALE, channel.receive(third));
    }

    @Test
    void receive_should_use_the_native_receive_window_rather_than_signed_short_order() {
        int[] sequences = {0, 1, 0x7FFF, 0x8000, 0x8001, 0x8002, 0xFFFE, 0xFFFF};
        ProviderChannel.ReceiveResult[] expected = {
                ProviderChannel.ReceiveResult.QUEUED, ProviderChannel.ReceiveResult.QUEUED,
                ProviderChannel.ReceiveResult.QUEUED, ProviderChannel.ReceiveResult.QUEUED,
                ProviderChannel.ReceiveResult.QUEUED, ProviderChannel.ReceiveResult.STALE,
                ProviderChannel.ReceiveResult.STALE, ProviderChannel.ReceiveResult.STALE
        };

        for (int sequenceIndex = 0; sequenceIndex < sequences.length; sequenceIndex++) {
            ProviderChannel channel = new ProviderChannel(0, 0);

            Assertions.assertEquals(expected[sequenceIndex],
                                    channel.receive(ProviderPacket.createTurn(sequences[sequenceIndex], 0, 0, new byte[]{5})),
                                    "Sequence " + sequences[sequenceIndex]);
        }

        ProviderChannel channel = new ProviderChannel(0, 0);
        ProviderPacket first = ProviderPacket.createTurn(0, 0, 0, new byte[]{5});

        Assertions.assertEquals(ProviderChannel.ReceiveResult.QUEUED, channel.receive(first));
        Assertions.assertEquals(ProviderChannel.ReceiveResult.QUEUED, channel.receive(ProviderPacket.createTurn(0x7FFF, 0, 0, new byte[]{5})));
        Assertions.assertFalse(channel.hasExpectedPacket());
        Assertions.assertNull(channel.pollExpectedPacket());
        Assertions.assertEquals(2, channel.getReceivedCount());
    }

    @Test
    void receive_should_remove_only_outgoing_sequences_before_the_exclusive_acknowledgement() {
        ProviderChannel channel = new ProviderChannel(0xFFFE, 10);
        ProviderPacket first = channel.createOutgoingTurn(0, 7, new byte[]{5});
        ProviderPacket second = channel.createOutgoingTurn(0, 7, new byte[]{5});
        ProviderPacket third = channel.createOutgoingTurn(0, 7, new byte[]{5});

        Assertions.assertEquals(ProviderChannel.ReceiveResult.STALE, channel.receive(ProviderPacket.createTurn(9, 0xFFFE, 3, new byte[]{5})));
        Assertions.assertSame(first, channel.getOldestPendingPacket());

        channel.receive(ProviderPacket.createTurn(10, 0xFFFF, 3, new byte[]{5}));

        Assertions.assertSame(second, channel.getOldestPendingPacket());
        Assertions.assertEquals(ProviderChannel.ReceiveResult.DUPLICATE, channel.receive(ProviderPacket.createTurn(10, 0, 3, new byte[]{5})));
        Assertions.assertSame(third, channel.getOldestPendingPacket());
        Assertions.assertEquals(1, channel.getPendingOutgoingCount());

        channel.receive(ProviderPacket.createTurn(10, 1, 3, new byte[]{5}));

        Assertions.assertEquals(0, channel.getPendingOutgoingCount());
        Assertions.assertNull(channel.getOldestPendingPacket());
    }

    @Test
    void receive_should_apply_acknowledgements_only_within_half_the_sequence_range() {
        ProviderChannel channel = new ProviderChannel(0xFFFF, 0);
        ProviderPacket pending = channel.createOutgoingTurn(0, 0, new byte[]{5});

        channel.receive(ProviderPacket.createTurn(0, 0x7FFF, 0, new byte[]{5}));

        Assertions.assertSame(pending, channel.getOldestPendingPacket());

        channel.receive(ProviderPacket.createTurn(0, 0x7FFE, 0, new byte[]{5}));

        Assertions.assertNull(channel.getOldestPendingPacket());
    }

    @Test
    void receive_should_handle_acknowledgement_only_before_sequence_or_subtype() throws Exception {
        ProviderChannel channel = new ProviderChannel(0, 300);

        channel.createOutgoingTurn(0, 0, new byte[]{5});

        ProviderPacket packet = ProviderPacket.parse(HexFormat.of().parseHex("d06b0d000100010002a5070105"));

        Assertions.assertEquals(ProviderChannel.ReceiveResult.ACKNOWLEDGEMENT_ONLY, channel.receive(packet));
        Assertions.assertEquals(0, channel.getPendingOutgoingCount());
        Assertions.assertEquals(0, channel.getReceivedCount());
        Assertions.assertEquals(300, channel.getExpectedSequence());
    }

    @Test
    void receive_should_queue_a_forwarded_turn_without_applying_its_acknowledgement() throws Exception {
        ProviderChannel channel = new ProviderChannel(0, 1);
        ProviderPacket pending = channel.createOutgoingTurn(0, 0, new byte[]{5});
        ProviderPacket packet = ProviderPacket.parse(HexFormat.of().parseHex("ee4a0d000100010002a5070405"));

        Assertions.assertEquals(ProviderChannel.ReceiveResult.QUEUED, channel.receive(packet));
        Assertions.assertSame(pending, channel.getOldestPendingPacket());
        Assertions.assertSame(packet, channel.pollExpectedPacket());
        Assertions.assertEquals(0xA5, packet.getSubtype());
        Assertions.assertEquals(7, packet.getSenderIdentifier());
    }

    @Test
    void receive_should_prefer_the_acknowledgement_only_flag_and_queue_higher_flags() throws Exception {
        ProviderChannel channel = new ProviderChannel(0, 1);

        channel.createOutgoingTurn(0, 0, new byte[]{5});

        ProviderPacket combined = ProviderPacket.parse(HexFormat.of().parseHex("e4550d000100010002a5070305"));

        Assertions.assertEquals(ProviderChannel.ReceiveResult.ACKNOWLEDGEMENT_ONLY, channel.receive(combined));
        Assertions.assertEquals(0, channel.getPendingOutgoingCount());
        Assertions.assertEquals(0, channel.getReceivedCount());

        ProviderPacket higherFlags = ProviderPacket.parse(HexFormat.of().parseHex("cbf00d000100010002a5078005"));

        Assertions.assertEquals(ProviderChannel.ReceiveResult.QUEUED, channel.receive(higherFlags));
        Assertions.assertSame(higherFlags, channel.pollExpectedPacket());
    }

    @Test
    void channel_rejects_unsupported_requests_and_invalid_creation_without_mutation() throws Exception {
        ProviderChannel channel = new ProviderChannel(0, 1);
        ProviderPacket pending = channel.createOutgoingTurn(0, 0, new byte[]{5});
        ProviderPacket request = ProviderPacket.parse(HexFormat.of().parseHex("da600d000100010002a5070205"));

        Assertions.assertThrows(UnsupportedOperationException.class, () -> channel.receive(request));
        Assertions.assertSame(pending, channel.getOldestPendingPacket());
        Assertions.assertEquals(0, channel.getReceivedCount());
        Assertions.assertThrows(IllegalArgumentException.class, () -> channel.createOutgoingTurn(0x10000, 0, new byte[0]));
        Assertions.assertEquals(1, channel.getNextOutgoingSequence());
        Assertions.assertEquals(1, channel.getPendingOutgoingCount());
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ProviderChannel(-1, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ProviderChannel(0, 0x10000));
    }
}
