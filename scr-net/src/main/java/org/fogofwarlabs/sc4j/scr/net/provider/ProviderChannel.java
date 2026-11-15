package org.fogofwarlabs.sc4j.scr.net.provider;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * Retains the sequence words, acknowledgement timing and packet queues of one peer and packet class. Consumed turn history is maintained separately
 * from the received and outgoing queues. Tick arithmetic uses wrapping 32-bit values and unsigned comparisons; access requires one owning thread.
 */
public final class ProviderChannel {
    /**
     * The largest unsigned sequence distance that still counts as "behind" (half the 16-bit range).
     */
    static final int SEQUENCE_HALF_RANGE = 0x7FFF;
    /**
     * The largest unsigned tick distance that still counts as "in the past" (half the 32-bit range).
     */
    static final int TICK_HALF_RANGE = 0x7FFFFFFF;

    /**
     * The largest unsigned distance {@code expected - sequence - 1} of a stale packet.
     */
    private static final int STALE_WINDOW = 0x7FFD;
    /**
     * The flag bits that tell an acknowledgement-only packet from a resend request.
     */
    private static final int REQUEST_FLAGS = ProviderPacket.FLAG_ACKNOWLEDGEMENT | ProviderPacket.FLAG_RESEND_REQUEST;
    /**
     * The empty command buffer: the keep-alive command {@link ReplayCommandIds#KEEP_ALIVE} alone.
     */
    private static final byte @NotNull [] EMPTY_COMMAND = {ReplayCommandIds.KEEP_ALIVE};

    /**
     * Describes the established receive outcomes after acknowledgement processing.
     */
    public enum ReceiveResult {
        /**
         * The packet was queued.
         */
        QUEUED,
        /**
         * The packet repeats a received packet.
         */
        DUPLICATE,
        /**
         * The packet precedes the next sequence to consume.
         */
        STALE,
        /**
         * The packet only acknowledged packets.
         */
        ACKNOWLEDGEMENT_ONLY
    }

    /**
     * A sent packet kept until the peer acknowledges it.
     */
    static final class Outgoing {
        private final @NotNull ProviderPacket packet;
        /**
         * The time the retransmission timer counts from.
         */
        private int resendTime;

        private Outgoing(@NotNull ProviderPacket packet, int resendTime) {
            this.packet     = packet;
            this.resendTime = resendTime;
        }

        @NotNull ProviderPacket getPacket() {
            return this.packet;
        }

        int getResendTime() {
            return this.resendTime;
        }

        void setResendTime(int resendTime) {
            this.resendTime = resendTime;
        }
    }

    /**
     * A received packet waiting to be consumed.
     */
    static final class Received {
        private final @NotNull ProviderPacket packet;
        /**
         * The arrival time.
         */
        private final int arrivalTime;
        /**
         * The time of the last resend request for the gap before this packet.
         */
        private int requestTime;

        private Received(@NotNull ProviderPacket packet, int arrivalTime, int requestTime) {
            this.packet      = packet;
            this.arrivalTime = arrivalTime;
            this.requestTime = requestTime;
        }

        @NotNull ProviderPacket getPacket() {
            return this.packet;
        }

        int getArrivalTime() {
            return this.arrivalTime;
        }

        int getRequestTime() {
            return this.requestTime;
        }

        void setRequestTime(int requestTime) {
            this.requestTime = requestTime;
        }
    }

    /**
     * Calculates a shared turn acknowledgement without consuming packets or storing state. It advances while every included participant
     * contains the candidate and at least one is included. {@code null} channels are excluded; late participants are skipped while the candidate is
     * numerically below their expected sequence. Channels require exclusive access during the calculation.
     *
     * @param receivingChannel        the resolved participant whose new turn triggered the calculation
     * @param previousAcknowledgement its established unsigned 16-bit acknowledgement before this calculation
     * @param participantChannels     all resolved participant channels, with established identifier exclusions set to {@code null}
     * @param participantFlags        the participant state flags corresponding to each slot
     * @return the calculated unsigned 16-bit shared acknowledgement
     * @throws IllegalArgumentException if the previous acknowledgement is out of range or the arrays differ in size
     */
    public static int calculateSharedAcknowledgement(@NotNull ProviderChannel receivingChannel,
                                                     int previousAcknowledgement,
                                                     @Nullable ProviderChannel @NotNull [] participantChannels,
                                                     int @NotNull [] participantFlags) {
        if ((previousAcknowledgement & ~ProviderPacket.WORD_MASK) != 0 || participantChannels.length != participantFlags.length) {
            throw Exceptions.newIllegalArgumentException("Acknowledgement must be unsigned 16-bit and participant arrays must have equal lengths.");
        }

        int candidate = receivingChannel.expectedSequence;

        if (((candidate - previousAcknowledgement) & ProviderPacket.WORD_MASK) >= SEQUENCE_HALF_RANGE) {
            candidate = previousAcknowledgement;
        }

        while (true) {
            boolean foundAny = false;
            boolean foundAll = true;

            for (int participantIndex = 0; participantIndex < participantChannels.length; participantIndex++) {
                ProviderChannel channel = participantChannels[participantIndex];

                if (channel == null ||
                    ((participantFlags[participantIndex] & ProviderPeer.FLAG_JOINED_LATE) != 0 && candidate < channel.expectedSequence)) {
                    continue;
                }

                boolean found = false;

                for (int packetIndex = 0; packetIndex < channel.receivedPackets.size(); packetIndex++) {
                    if (channel.receivedPackets.get(packetIndex).packet.getSequence() == candidate) {
                        found = true;

                        break;
                    }
                }

                foundAny |= found;
                foundAll &= found;
            }

            if (!foundAny || !foundAll) {
                return candidate;
            }

            candidate = (candidate + 1) & ProviderPacket.WORD_MASK;
        }
    }


    private final @NotNull ArrayDeque<Outgoing> outgoingPackets;
    private final @NotNull ArrayList<Received> receivedPackets;
    private final @NotNull ArrayDeque<ProviderPacket> historyPackets;

    private int nextOutgoingSequence;
    private int expectedSequence;

    private int acknowledgement;
    private int sentAcknowledgement;
    private int acknowledgementDueTime;

    /**
     * Creates a {@link ProviderChannel} instance with explicitly established sequence state; the acknowledgement starts at the expected sequence.
     *
     * @param nextOutgoingSequence the participant's next unsigned 16-bit outgoing sequence
     * @param expectedSequence     the next unsigned 16-bit sequence to consume
     * @throws IllegalArgumentException if either sequence is outside the unsigned 16-bit range
     */
    public ProviderChannel(int nextOutgoingSequence, int expectedSequence) {
        if ((nextOutgoingSequence & ~ProviderPacket.WORD_MASK) != 0 || (expectedSequence & ~ProviderPacket.WORD_MASK) != 0) {
            throw Exceptions.newIllegalArgumentException("Turn sequences must be unsigned 16-bit values.");
        }

        this.outgoingPackets = new ArrayDeque<>();
        this.receivedPackets = new ArrayList<>();
        this.historyPackets  = new ArrayDeque<>();

        this.nextOutgoingSequence = nextOutgoingSequence;
        this.expectedSequence     = expectedSequence;

        this.acknowledgement        = expectedSequence;
        this.sentAcknowledgement    = expectedSequence;
        this.acknowledgementDueTime = 0;
    }

    /**
     * @return the next unsigned 16-bit outgoing sequence
     */
    public int getNextOutgoingSequence() {
        return this.nextOutgoingSequence;
    }

    /**
     * Sets the next outgoing sequence; the joiner's game-info handler copies the host's turn.
     *
     * @param sequence the unsigned 16-bit sequence
     */
    void setNextOutgoingSequence(int sequence) {
        this.nextOutgoingSequence = sequence & ProviderPacket.WORD_MASK;
    }

    /**
     * @return the next unsigned 16-bit sequence to consume
     */
    public int getExpectedSequence() {
        return this.expectedSequence;
    }

    /**
     * Sets the next sequence to consume alone, as the turn consumption does for the local record.
     *
     * @param sequence the unsigned 16-bit sequence
     */
    void setExpectedSequence(int sequence) {
        this.expectedSequence = sequence & ProviderPacket.WORD_MASK;
    }

    /**
     * Sets the next sequence to consume and the acknowledgement together.
     *
     * @param sequence the unsigned 16-bit sequence
     */
    void setExpectedAndAcknowledgement(int sequence) {
        this.expectedSequence = sequence & ProviderPacket.WORD_MASK;
        this.acknowledgement  = sequence & ProviderPacket.WORD_MASK;
    }

    /**
     * @return the acknowledgement this side sends: the first sequence not yet received in order
     */
    public int getAcknowledgement() {
        return this.acknowledgement;
    }

    /**
     * Sets the acknowledgement as the shared acknowledgement store of class {@code 2} does.
     *
     * @param acknowledgement the unsigned 16-bit acknowledgement
     */
    void setAcknowledgement(int acknowledgement) {
        this.acknowledgement = acknowledgement & ProviderPacket.WORD_MASK;
    }

    /**
     * @return the number of outgoing packets not removed by an acknowledgement
     */
    public int getPendingOutgoingCount() {
        return this.outgoingPackets.size();
    }

    /**
     * @return the number of queued received packets
     */
    public int getReceivedCount() {
        return this.receivedPackets.size();
    }

    /**
     * @return the oldest pending outgoing packet, or {@code null} if none remains
     */
    public @Nullable ProviderPacket getOldestPendingPacket() {
        Outgoing oldest = this.outgoingPackets.peekFirst();

        return oldest == null ? null : oldest.packet;
    }

    /**
     * @return {@code true} if the first queued packet has the expected sequence, {@code false} otherwise
     */
    public boolean hasExpectedPacket() {
        return !this.receivedPackets.isEmpty() && this.receivedPackets.getFirst().packet.getSequence() == this.expectedSequence;
    }

    int getSentAcknowledgement() {
        return this.sentAcknowledgement;
    }

    int getAcknowledgementDueTime() {
        return this.acknowledgementDueTime;
    }

    @NotNull ArrayDeque<Outgoing> getOutgoingPackets() {
        return this.outgoingPackets;
    }

    @NotNull ArrayList<Received> getReceivedPackets() {
        return this.receivedPackets;
    }

    /**
     * @return the consumed turns kept for resend requests (class {@code 2} only)
     */
    @NotNull ArrayDeque<ProviderPacket> getHistoryPackets() {
        return this.historyPackets;
    }


    /**
     * Creates a {@link ProviderPacket} instance for the next outgoing turn and retains it, advancing its sequence modulo {@code 65536}.
     * <p>
     * An empty command buffer becomes opcode {@code 05}, before packet encoding. No issuer byte is prepended. The caller remains responsible
     * for provider submission and local turn insertion.
     *
     * @param acknowledgement  the already-established shared unsigned 16-bit acknowledgement
     * @param senderIdentifier the local unsigned 8-bit provider sender identifier
     * @param commands         the complete command buffer without a replay issuer prefix
     * @return the created {@link ProviderPacket} instance
     * @throws IllegalArgumentException if the fields or command length exceed the codec's bounds
     */
    public @NotNull ProviderPacket createOutgoingTurn(int acknowledgement, int senderIdentifier, byte @NotNull [] commands) {
        ProviderPacket packet = ProviderPacket.createTurn(this.nextOutgoingSequence, acknowledgement,
                                                          senderIdentifier,
                                                          commands.length == 0 ? EMPTY_COMMAND : commands);

        this.outgoingPackets.addLast(new Outgoing(packet, 0));
        this.nextOutgoingSequence = (this.nextOutgoingSequence + 1) & ProviderPacket.WORD_MASK;

        return packet;
    }

    /**
     * Applies the established acknowledgement and ordering rules to an already-resolved packet.
     * <p>
     * The sender field is not used to resolve a peer. Flag {@code 04} suppresses acknowledgement processing; flag
     * {@code 01} consumes only the acknowledgement. Subtype values and higher flag bits are preserved. Retransmission
     * requests without flag {@code 01} belong to {@link ProviderSession} and fail here before state changes. Stale and
     * duplicate packets still apply their acknowledgement. The acknowledgement and timers of {@link ProviderSession} are not updated.
     *
     * @param packet the immutable envelope already checked by {@link ProviderPacket#parse(byte[])}
     * @return the resulting receive disposition
     * @throws UnsupportedOperationException if the packet requests retransmission
     */
    public @NotNull ReceiveResult receive(@NotNull ProviderPacket packet) {
        int flags = packet.getFlags();

        if ((flags & REQUEST_FLAGS) == ProviderPacket.FLAG_RESEND_REQUEST) {
            throw Exceptions.newUnsupportedOperationException("Provider retransmission requests require the session retry path.");
        }

        if ((flags & ProviderPacket.FLAG_RELAYED) == 0) {
            this.removeAcknowledged(packet.getAcknowledgement(), 0);
        }

        if ((flags & ProviderPacket.FLAG_ACKNOWLEDGEMENT) != 0) {
            return ReceiveResult.ACKNOWLEDGEMENT_ONLY;
        }

        return this.insertReceived(packet, 0, 0);
    }

    /**
     * Consumes the first queued packet only when it has the expected sequence.
     * <p>
     * The caller must first check the complete required-participant barrier for turns. A missing packet leaves this
     * channel unchanged. A consumed packet advances the expected sequence modulo {@code 65536}. Returned packet storage
     * remains immutable; this method does not add it to the separate retry history.
     *
     * @return the consumed packet, or {@code null} if the expected packet is unavailable
     */
    public @Nullable ProviderPacket pollExpectedPacket() {
        if (!this.hasExpectedPacket()) {
            return null;
        }

        ProviderPacket packet = this.receivedPackets.removeFirst().packet;

        this.expectedSequence = (this.expectedSequence + 1) & ProviderPacket.WORD_MASK;

        return packet;
    }

    /**
     * Maintains the turn history of one record: queued turns before {@code expected} move to the history, and history
     * turns before {@code oldestNeeded} are released.
     *
     * @param expected     the local player's next turn to consume
     * @param oldestNeeded the oldest turn a peer may still request: the smallest acknowledgement received from any peer
     */
    void pruneTurns(int expected, int oldestNeeded) {
        while (!this.receivedPackets.isEmpty()) {
            int sequence = this.receivedPackets.getFirst().packet.getSequence();

            if (sequence == expected || ((expected - sequence) & ProviderPacket.WORD_MASK) > SEQUENCE_HALF_RANGE) {
                break;
            }

            this.historyPackets.addLast(this.receivedPackets.removeFirst().packet);
        }

        while (!this.historyPackets.isEmpty()) {
            int sequence = this.historyPackets.getFirst().getSequence();

            if (sequence == oldestNeeded || ((oldestNeeded - sequence) & ProviderPacket.WORD_MASK) > SEQUENCE_HALF_RANGE) {
                break;
            }

            this.historyPackets.removeFirst();
        }
    }

    /**
     * Resets every sequence word of the class to {@code sequence} and empties both queues, as the join handshake does for
     * class {@code 0} and the join and player-info handlers do for class {@code 2}.
     *
     * @param sequence the unsigned 16-bit sequence
     */
    void reset(int sequence) {
        this.outgoingPackets.clear();
        this.receivedPackets.clear();

        this.nextOutgoingSequence = sequence;
        this.expectedSequence     = sequence;

        this.acknowledgement        = sequence;
        this.sentAcknowledgement    = sequence;
        this.acknowledgementDueTime = 0;
    }

    /**
     * Consumes the head turn, which must be {@code turn}, into the history and advances the next sequence to consume.
     *
     * @param turn the turn being consumed
     * @return the consumed packet
     */
    @NotNull ProviderPacket consumeTurn(int turn) {
        ProviderPacket packet = this.receivedPackets.removeFirst().packet;

        this.expectedSequence = (turn + 1) & ProviderPacket.WORD_MASK;
        this.historyPackets.addLast(packet);

        return packet;
    }

    /**
     * Marks an acknowledgement due at {@code now} unless one is already due.
     *
     * @param now the current tick
     */
    void markAcknowledgementDue(int now) {
        if (this.acknowledgementDueTime == 0) {
            this.acknowledgementDueTime = now;
        }
    }

    /**
     * Forces the acknowledgement to be sent again: the stale-packet path sets the last sent value to the acknowledgement
     * minus one and the due time to {@code now}.
     *
     * @param now the current tick
     */
    void requestAcknowledgementRepeat(int now) {
        this.sentAcknowledgement    = (this.acknowledgement - 1) & ProviderPacket.WORD_MASK;
        this.acknowledgementDueTime = now;
    }

    /**
     * Records that a packet carrying {@code acknowledgement} was sent: the acknowledgement is no longer due.
     *
     * @param acknowledgement the unsigned 16-bit acknowledgement the packet carried
     */
    void recordSentAcknowledgement(int acknowledgement) {
        this.sentAcknowledgement    = acknowledgement;
        this.acknowledgementDueTime = 0;
    }

    /**
     * Takes the next outgoing sequence for a new packet; the join request (class {@code 0} subtype {@code 1}) always uses
     * {@code 0} and does not advance it.
     *
     * @param packetClass the class of the new packet
     * @param subtype     the subtype of the new packet
     * @return the unsigned 16-bit sequence of the new packet
     */
    int takeOutgoingSequence(int packetClass, int subtype) {
        if (packetClass == ProviderPacket.CONTROL && subtype == ProviderSession.SUBTYPE_JOIN_REQUEST) {
            return 0;
        }

        int sequence = this.nextOutgoingSequence;

        this.nextOutgoingSequence = (sequence + 1) & ProviderPacket.WORD_MASK;

        return sequence;
    }

    /**
     * Keeps a sent packet until it is acknowledged.
     *
     * @param packet     the sent packet
     * @param resendTime the tick the retransmission timer counts from
     */
    void addOutgoing(@NotNull ProviderPacket packet, int resendTime) {
        this.outgoingPackets.addLast(new Outgoing(packet, resendTime));
    }

    /**
     * Removes the outgoing packets the peer acknowledged: every head packet whose sequence precedes {@code acknowledgement} in the unsigned half
     * range. When anything was removed, the new head's resend time restarts at {@code now} unless it lies in the future.
     *
     * @param acknowledgement the received unsigned 16-bit acknowledgement
     * @param now             the current tick
     */
    void removeAcknowledged(int acknowledgement, int now) {
        boolean removed = false;

        while (!this.outgoingPackets.isEmpty()) {
            int distance = (acknowledgement - this.outgoingPackets.getFirst().packet.getSequence()) & ProviderPacket.WORD_MASK;

            if (distance == 0 || distance > SEQUENCE_HALF_RANGE) {
                break;
            }

            this.outgoingPackets.removeFirst();
            removed = true;
        }

        Outgoing head = this.outgoingPackets.peekFirst();

        if (removed && head != null && Integer.compareUnsigned(now - head.resendTime, TICK_HALF_RANGE) < 0) {
            head.resendTime = now;
        }
    }

    /**
     * Queues a received packet in sequence order.
     *
     * @param packet      the packet
     * @param arrivalTime the arrival tick
     * @param requestTime the initial gap-request tick of the node
     * @return {@link ReceiveResult#STALE}, {@link ReceiveResult#DUPLICATE} or {@link ReceiveResult#QUEUED}
     */
    @NotNull ReceiveResult insertReceived(@NotNull ProviderPacket packet, int arrivalTime, int requestTime) {
        int sequence = packet.getSequence();

        if (((this.expectedSequence - sequence - 1) & ProviderPacket.WORD_MASK) <= STALE_WINDOW) {
            return ReceiveResult.STALE;
        }

        int insertionIndex = 0;

        while (insertionIndex < this.receivedPackets.size()) {
            int queuedSequence = this.receivedPackets.get(insertionIndex).packet.getSequence();

            if (((sequence - queuedSequence - 1) & ProviderPacket.WORD_MASK) > STALE_WINDOW) {
                if (sequence == queuedSequence) {
                    return ReceiveResult.DUPLICATE;
                }

                break;
            }

            insertionIndex++;
        }

        this.receivedPackets.add(insertionIndex, new Received(packet, arrivalTime, requestTime));

        return ReceiveResult.QUEUED;
    }

    /**
     * Appends a packet the local player sent to itself: the local record's received list, without ordering or acknowledgement.
     *
     * @param packet      the packet
     * @param arrivalTime the current tick
     */
    void appendReceived(@NotNull ProviderPacket packet, int arrivalTime) {
        this.receivedPackets.add(new Received(packet, arrivalTime, 0));
    }

    /**
     * Advances the acknowledgement of a class other than {@code 2} over the contiguous received packets: it restarts at the
     * next sequence to consume and moves past every queued packet in order.
     *
     * @return {@code true} if the acknowledgement changed, {@code false} otherwise
     */
    boolean advanceAcknowledgement() {
        int previous = this.acknowledgement;
        int candidate = this.expectedSequence;

        for (Received received : this.receivedPackets) {
            if (received.packet.getSequence() != candidate) {
                break;
            }

            candidate = (candidate + 1) & ProviderPacket.WORD_MASK;
        }

        this.acknowledgement = candidate;

        return candidate != previous;
    }
}
