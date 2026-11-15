package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a selection operation and ordered unit identities independently of their input source. The request does not validate unit
 * existence or apply selection rules.
 */
public class SelectionRequest {
    /**
     * Identifies the requested selection operation.
     */
    public enum Operation {
        /**
         * Replaces the selection.
         */
        REPLACE,
        /**
         * Adds handles to the selection.
         */
        ADD,
        /**
         * Removes handles from the selection.
         */
        REMOVE
    }

    private final @NotNull Operation operation;

    private final int handleWidthBytes;
    private final long @NotNull [] handles;

    /**
     * Creates a {@link SelectionRequest} instance using the supplied handle storage.
     * <p>
     * The caller transfers ownership of the array and must not modify it afterward. Handles retain their width, order and duplicates
     * without selection validation.
     *
     * @param operation        the requested selection operation
     * @param handleWidthBytes the unit identity width in bytes, either {@code 2} or {@code 4}
     * @param handles          the owned unsigned unit handles
     */
    public SelectionRequest(@NotNull Operation operation, int handleWidthBytes, long @NotNull [] handles) {
        this.operation = operation;

        this.handleWidthBytes = handleWidthBytes;
        this.handles          = handles;
    }

    /**
     * @return the requested selection operation
     */
    public final @NotNull Operation getOperation() {
        return this.operation;
    }

    /**
     * @return the number of supplied raw handles in the request
     */
    public final int getHandleCount() {
        return this.handles.length;
    }

    /**
     * @return the unit identity width in bytes, either {@code 2} or {@code 4}
     */
    public final int getHandleWidthBytes() {
        return this.handleWidthBytes;
    }

    /**
     * Returns a raw unsigned handle in its supplied position.
     *
     * @param index the zero-based handle position
     * @return the unsigned handle value, from {@code 0} through {@code 0xFFFFFFFFL}
     * @throws IndexOutOfBoundsException if the position is outside the request
     */
    public final long getHandle(int index) {
        return this.handles[index];
    }
}
