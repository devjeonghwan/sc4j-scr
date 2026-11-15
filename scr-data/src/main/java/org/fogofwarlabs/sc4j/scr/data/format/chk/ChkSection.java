package org.fogofwarlabs.sc4j.scr.data.format.chk;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Preserves one CHK header encountered during signed-offset traversal.
 * <p>
 * A header remains visible to tag-presence inspection even when its declared payload is unavailable.
 */
public final class ChkSection {
    private final @NotNull String name;
    private final int headerOffset;
    private final int declaredSize;
    private final byte @Nullable [] payload;

    /**
     * Creates a {@link ChkSection} instance.
     *
     * @param name         the four raw tag bytes represented by ISO-8859-1 characters
     * @param headerOffset the header offset in the supplied CHK buffer
     * @param declaredSize the unmodified signed length DWORD
     * @param payload      the payload, or {@code null} if it cannot be read from the supplied buffer
     */
    ChkSection(@NotNull String name, int headerOffset, int declaredSize, byte @Nullable [] payload) {
        this.name         = name;
        this.headerOffset = headerOffset;
        this.declaredSize = declaredSize;
        this.payload      = payload;
    }

    /**
     * @return the four raw tag bytes represented by ISO-8859-1 characters
     */
    public @NotNull String getName() {
        return this.name;
    }

    /**
     * @return the header offset in the original supplied CHK buffer
     */
    public int getHeaderOffset() {
        return this.headerOffset;
    }

    /**
     * @return the payload offset in the original supplied CHK buffer
     */
    public int getPayloadOffset() {
        return this.headerOffset + ChkFile.SECTION_HEADER_SIZE;
    }

    /**
     * @return the unmodified signed length DWORD
     */
    public int getDeclaredSize() {
        return this.declaredSize;
    }

    /**
     * @return {@code true} if the declared nonnegative payload is entirely supplied, {@code false} otherwise
     */
    public boolean hasCompletePayload() {
        return this.payload != null;
    }

    /**
     * Returns the supplied payload without repairing the declared length.
     *
     * @return the payload bytes
     * @throws IllegalStateException if the declared payload is unavailable in the supplied buffer
     */
    public byte @NotNull [] getPayload() {
        if (this.payload == null) {
            throw Exceptions.newIllegalStateException("The CHK {0} payload at offset {1} cannot be read from the supplied buffer; declared size {2}.",
                                                      this.name, this.getPayloadOffset(), this.declaredSize);
        }

        return this.payload;
    }
}
