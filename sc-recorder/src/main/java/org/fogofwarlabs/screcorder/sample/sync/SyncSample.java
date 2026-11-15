package org.fogofwarlabs.screcorder.sample.sync;

import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.sample.Sample;
import org.jetbrains.annotations.NotNull;

/**
 * Stores sync state and fixed memory blocks; embedded addresses other than the normalized arena pointer remain process-specific.
 */
public final class SyncSample implements Sample {
    /**
     * Size of the head memory block.
     */
    public static final int HEAD_BYTES = 16;

    /**
     * Size of the status memory block.
     */
    public static final int STATUS_BYTES = 36;

    /**
     * Size of the visibility row block: the visibility bytes of the sprite rows followed by the first drawn sprite row.
     */
    public static final int VISIBILITY_ROWS_BYTES = 260;

    /**
     * Size of the sprite vector block: the sprite pool vector followed by the drawing refresh flag and the visibility fold.
     */
    public static final int SPRITE_VECTOR_BYTES = 32;

    /**
     * Size of the vision memory block.
     */
    public static final int VISION_BYTES = 8;

    /**
     * Size of the players memory block.
     */
    public static final int PLAYERS_BYTES = 16;

    /**
     * Size of the turn memory block.
     */
    public static final int TURN_BYTES = 32;

    /**
     * Size of the ring memory block.
     */
    public static final int RING_BYTES = 4288;

    private final long randomState;
    private final long randomTotal;
    private final long drawingRefreshFlag;
    private final long visibilityFold;
    private final long turnLength;

    private final byte @NotNull [] head;
    private final byte @NotNull [] status;
    private final byte @NotNull [] visibilityRows;
    private final byte @NotNull [] spriteVector;
    private final byte @NotNull [] vision;
    private final byte @NotNull [] players;
    private final byte @NotNull [] turn;
    private final byte @NotNull [] ring;

    /**
     * Creates a {@link SyncSample} instance with detached memory blocks.
     *
     * @param randomState        the unsigned random state
     * @param randomTotal        the unsigned random call total
     * @param drawingRefreshFlag the unsigned flag that requests a recompute of the visibility rows and fold after a turn step
     * @param visibilityFold     the unsigned DWORD whose low byte is the visibility fold of the drawn sprite rows
     * @param turnLength         the unsigned turn length
     * @param head               the sync header memory
     * @param status             the sync status memory
     * @param visibilityRows     the visibility row block
     * @param spriteVector       the sprite vector block; its first QWORD is normalized to an offset from the unit pool
     * @param vision             the vision memory
     * @param players            the sync player memory
     * @param turn               the turn buffer memory
     * @param ring               the sync ring memory
     * @throws IllegalArgumentException if a memory block does not have its fixed size
     */
    public SyncSample(long randomState, long randomTotal,
                      long drawingRefreshFlag, long visibilityFold, long turnLength,
                      byte @NotNull [] head, byte @NotNull [] status,
                      byte @NotNull [] visibilityRows, byte @NotNull [] spriteVector,
                      byte @NotNull [] vision, byte @NotNull [] players, byte @NotNull [] turn, byte @NotNull [] ring) {
        if (head.length != HEAD_BYTES) {
            throw Exceptions.newIllegalArgumentException("Invalid head block size: {0}", head.length);
        }

        if (status.length != STATUS_BYTES) {
            throw Exceptions.newIllegalArgumentException("Invalid status block size: {0}", status.length);
        }

        if (visibilityRows.length != VISIBILITY_ROWS_BYTES) {
            throw Exceptions.newIllegalArgumentException("Invalid blockE77C90 block size: {0}", visibilityRows.length);
        }

        if (spriteVector.length != SPRITE_VECTOR_BYTES) {
            throw Exceptions.newIllegalArgumentException("Invalid blockE76C70 block size: {0}", spriteVector.length);
        }

        if (vision.length != VISION_BYTES) {
            throw Exceptions.newIllegalArgumentException("Invalid vision block size: {0}", vision.length);
        }

        if (players.length != PLAYERS_BYTES) {
            throw Exceptions.newIllegalArgumentException("Invalid players block size: {0}", players.length);
        }

        if (turn.length != TURN_BYTES) {
            throw Exceptions.newIllegalArgumentException("Invalid turn block size: {0}", turn.length);
        }

        if (ring.length != RING_BYTES) {
            throw Exceptions.newIllegalArgumentException("Invalid ring block size: {0}", ring.length);
        }

        this.randomState        = randomState;
        this.randomTotal        = randomTotal;
        this.drawingRefreshFlag = drawingRefreshFlag;
        this.visibilityFold     = visibilityFold;
        this.turnLength         = turnLength;

        this.head           = head.clone();
        this.status         = status.clone();
        this.visibilityRows = visibilityRows.clone();
        this.spriteVector   = spriteVector.clone();
        this.vision         = vision.clone();
        this.players        = players.clone();
        this.turn           = turn.clone();
        this.ring           = ring.clone();
    }


    /**
     * @return the unsigned random state
     */
    public long getRandomState() {
        return this.randomState;
    }

    /**
     * @return the unsigned random call total
     */
    public long getRandomTotal() {
        return this.randomTotal;
    }

    /**
     * @return the unsigned flag that requests a recompute of the visibility rows and fold after a turn step
     */
    public long getDrawingRefreshFlag() {
        return this.drawingRefreshFlag;
    }

    /**
     * @return the unsigned DWORD whose low byte is the visibility fold of the drawn sprite rows
     */
    public long getVisibilityFold() {
        return this.visibilityFold;
    }

    /**
     * @return the unsigned turn length
     */
    public long getTurnLength() {
        return this.turnLength;
    }

    /**
     * @return a detached copy of sync header memory
     */
    public byte @NotNull [] getHead() {
        return this.head.clone();
    }

    /**
     * @return a detached copy of sync status memory
     */
    public byte @NotNull [] getStatus() {
        return this.status.clone();
    }

    /**
     * @return a detached copy of the visibility row block
     */
    public byte @NotNull [] getVisibilityRows() {
        return this.visibilityRows.clone();
    }

    /**
     * @return a detached copy of the sprite vector block; its first QWORD is normalized to an offset from the unit pool
     */
    public byte @NotNull [] getSpriteVector() {
        return this.spriteVector.clone();
    }

    /**
     * @return a detached copy of vision memory
     */
    public byte @NotNull [] getVision() {
        return this.vision.clone();
    }

    /**
     * @return a detached copy of sync player memory
     */
    public byte @NotNull [] getPlayers() {
        return this.players.clone();
    }

    /**
     * @return a detached copy of turn buffer memory
     */
    public byte @NotNull [] getTurn() {
        return this.turn.clone();
    }

    /**
     * @return a detached copy of sync ring memory
     */
    public byte @NotNull [] getRing() {
        return this.ring.clone();
    }
}
