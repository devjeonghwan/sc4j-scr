package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a single player command of the replay command stream.
 * <p>
 * The payload holds the type-specific argument bytes as recorded in the stream.
 * Structured decoding happens when the simulation consumes the commands.
 */
public final class ReplayCommand {
    private final long frame;
    private final int playerId;
    private final int typeId;

    private final byte @NotNull [] payload;

    /**
     * Creates a {@link ReplayCommand} instance.
     *
     * @param frame    the frame the command was issued in
     * @param playerId the issuing player ID
     * @param typeId   the command type ID
     * @param payload  the type-specific argument bytes
     */
    public ReplayCommand(long frame, int playerId, int typeId, byte @NotNull [] payload) {
        this.frame    = frame;
        this.playerId = playerId;
        this.typeId   = typeId;

        this.payload = payload;
    }

    /**
     * @return the frame the command was issued in
     */
    public long getFrame() {
        return this.frame;
    }

    /**
     * @return the issuing player ID
     */
    public int getPlayerId() {
        return this.playerId;
    }

    /**
     * @return the command type ID
     */
    public int getTypeId() {
        return this.typeId;
    }

    /**
     * @return the type-specific argument bytes
     */
    public byte @NotNull [] getPayload() {
        return this.payload;
    }
}
