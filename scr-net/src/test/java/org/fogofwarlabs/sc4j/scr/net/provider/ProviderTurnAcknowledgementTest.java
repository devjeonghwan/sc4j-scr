package org.fogofwarlabs.sc4j.scr.net.provider;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the shared turn acknowledgement advances only through turns every eligible participant queued.
 */
final class ProviderTurnAcknowledgementTest {
    @Test
    void calculate_shared_acknowledgement_should_advance_only_through_turns_in_every_queue() {
        ProviderChannel first = new ProviderChannel(0, 40);
        ProviderChannel second = new ProviderChannel(0, 40);

        first.receive(ProviderPacket.createTurn(40, 0, 0, new byte[]{5}));
        first.receive(ProviderPacket.createTurn(41, 0, 0, new byte[]{5}));
        first.receive(ProviderPacket.createTurn(42, 0, 0, new byte[]{5}));
        second.receive(ProviderPacket.createTurn(40, 0, 1, new byte[]{5}));
        second.receive(ProviderPacket.createTurn(42, 0, 1, new byte[]{5}));

        ProviderChannel[] participants = {first, null, second};
        int[] flags = {0, 0, 0};

        Assertions.assertEquals(41, ProviderChannel.calculateSharedAcknowledgement(first, 40, participants, flags));

        second.receive(ProviderPacket.createTurn(41, 0, 1, new byte[]{5}));

        Assertions.assertEquals(43, ProviderChannel.calculateSharedAcknowledgement(second, 41, participants, flags));
        Assertions.assertEquals(3, first.getReceivedCount());
        Assertions.assertEquals(3, second.getReceivedCount());
        Assertions.assertEquals(40, first.getExpectedSequence());
        Assertions.assertEquals(40, second.getExpectedSequence());
    }

    @Test
    void calculate_shared_acknowledgement_should_search_behind_a_distant_head() {
        ProviderChannel channel = new ProviderChannel(0, 0);

        channel.receive(ProviderPacket.createTurn(0, 0, 0, new byte[]{5}));
        channel.receive(ProviderPacket.createTurn(0x7FFF, 0, 0, new byte[]{5}));

        Assertions.assertFalse(channel.hasExpectedPacket());
        Assertions.assertEquals(1, ProviderChannel.calculateSharedAcknowledgement(channel, 0, new ProviderChannel[]{channel}, new int[]{0}));
        Assertions.assertEquals(2, channel.getReceivedCount());
        Assertions.assertEquals(0, channel.getExpectedSequence());
    }

    @Test
    void calculate_shared_acknowledgement_should_keep_the_initial_unsigned_half_range_boundary() {
        int[] expectedSequences = {0x7FFE, 0x7FFF, 0x8000, 0xFFFF};
        int[] expectedAcknowledgements = {0x7FFE, 0, 0, 0};

        for (int sequenceIndex = 0; sequenceIndex < expectedSequences.length; sequenceIndex++) {
            ProviderChannel channel = new ProviderChannel(0, expectedSequences[sequenceIndex]);

            Assertions.assertEquals(expectedAcknowledgements[sequenceIndex],
                                    ProviderChannel.calculateSharedAcknowledgement(channel, 0, new ProviderChannel[]{channel}, new int[]{0}));
        }

        ProviderChannel wrapped = new ProviderChannel(0, 0);

        Assertions.assertEquals(0, ProviderChannel.calculateSharedAcknowledgement(wrapped, 0xFFFF, new ProviderChannel[]{wrapped}, new int[]{0}));
    }

    @Test
    void calculate_shared_acknowledgement_should_include_a_late_joiner_at_its_expected_turn() {
        ProviderChannel first = new ProviderChannel(0, 7);
        ProviderChannel second = new ProviderChannel(0, 8);

        first.receive(ProviderPacket.createTurn(7, 0, 0, new byte[]{5}));
        first.receive(ProviderPacket.createTurn(8, 0, 0, new byte[]{5}));

        ProviderChannel[] participants = {first, second};
        int[] lateSecondFlags = {0, ProviderPeer.FLAG_JOINED_LATE};

        Assertions.assertEquals(7, ProviderChannel.calculateSharedAcknowledgement(first, 7, participants, new int[]{0, 0}));
        Assertions.assertEquals(8, ProviderChannel.calculateSharedAcknowledgement(first, 7, participants, lateSecondFlags));

        second.receive(ProviderPacket.createTurn(8, 0, 1, new byte[]{5}));

        Assertions.assertEquals(9, ProviderChannel.calculateSharedAcknowledgement(second, 8, participants, lateSecondFlags));
    }

    @Test
    void calculate_shared_acknowledgement_should_skip_late_joiners_numerically_across_wrap() {
        ProviderChannel first = new ProviderChannel(0, 0xFFFF);
        ProviderChannel second = new ProviderChannel(0, 0xFFFF);

        first.receive(ProviderPacket.createTurn(0xFFFF, 0, 0, new byte[]{5}));
        first.receive(ProviderPacket.createTurn(0, 0, 0, new byte[]{5}));
        second.receive(ProviderPacket.createTurn(0xFFFF, 0, 1, new byte[]{5}));

        ProviderChannel[] participants = {first, second};
        int[] lateSecondFlags = {0, ProviderPeer.FLAG_JOINED_LATE};

        Assertions.assertEquals(0, ProviderChannel.calculateSharedAcknowledgement(first, 0xFFFF, participants, new int[]{0, 0}));
        Assertions.assertEquals(1, ProviderChannel.calculateSharedAcknowledgement(first, 0xFFFF, participants, lateSecondFlags));
        Assertions.assertEquals(0xFFFF, second.getExpectedSequence());
        Assertions.assertEquals(1, second.getReceivedCount());
    }

    @Test
    void calculate_shared_acknowledgement_should_stop_when_no_participant_is_eligible() {
        ProviderChannel receiver = new ProviderChannel(0, 4);
        ProviderChannel excluded = new ProviderChannel(0, 5);

        Assertions.assertEquals(4, ProviderChannel.calculateSharedAcknowledgement(receiver, 4,
                                                                                  new ProviderChannel[]{null, excluded},
                                                                                  new int[]{0, ProviderPeer.FLAG_JOINED_LATE}));
        Assertions.assertEquals(4, ProviderChannel.calculateSharedAcknowledgement(receiver, 4, new ProviderChannel[0], new int[0]));
    }

    @Test
    void shared_acknowledgement_lets_the_outgoing_turn_remove_only_earlier_pending_packets() throws Exception {
        ProviderChannel sender = new ProviderChannel(0xFFFF, 12);
        ProviderChannel receiver = new ProviderChannel(12, 0xFFFF);
        ProviderPacket first = sender.createOutgoingTurn(12, 0, new byte[0]);
        ProviderPacket second = sender.createOutgoingTurn(12, 0, new byte[0]);

        receiver.receive(ProviderTransportMessage.parse(ProviderTransportMessage.create(first).getEncodedBytes()).getPacket());

        int acknowledgement = ProviderChannel.calculateSharedAcknowledgement(receiver, 0xFFFF, new ProviderChannel[]{receiver}, new int[]{0});
        ProviderPacket response = receiver.createOutgoingTurn(acknowledgement, 1, new byte[0]);

        Assertions.assertEquals(0, acknowledgement);
        Assertions.assertEquals(ProviderChannel.ReceiveResult.QUEUED, sender.receive(response));
        Assertions.assertEquals(1, sender.getPendingOutgoingCount());
        Assertions.assertSame(second, sender.getOldestPendingPacket());
        Assertions.assertEquals(1, receiver.getReceivedCount());
        Assertions.assertEquals(0xFFFF, receiver.getExpectedSequence());
    }

    @Test
    void calculate_shared_acknowledgement_should_reject_invalid_values_and_array_lengths() {
        ProviderChannel channel = new ProviderChannel(0, 0);

        Assertions.assertThrows(IllegalArgumentException.class,
                                () -> ProviderChannel.calculateSharedAcknowledgement(channel, -1, new ProviderChannel[0], new int[0]));
        Assertions.assertThrows(IllegalArgumentException.class,
                                () -> ProviderChannel.calculateSharedAcknowledgement(channel, 0x10000, new ProviderChannel[0], new int[0]));
        Assertions.assertThrows(IllegalArgumentException.class,
                                () -> ProviderChannel.calculateSharedAcknowledgement(channel, 0, new ProviderChannel[]{channel}, new int[0]));
    }
}
