package org.fogofwarlabs.screcorder.sample.eud;

import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.sample.Sample;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Contains the emulator mode, native object bytes and regions ordered by classic address.
 */
public final class EudSample implements Sample {
    static final int OBJECT_BYTES = 144;
    private static final long MAXIMUM_UNSIGNED_INTEGER = 0xFFFFFFFFL;

    private final long mode;
    private final long emulator;
    private final byte @NotNull [] object;
    private final byte @Nullable [] root;
    private final byte @Nullable [] left;
    private final byte @Nullable [] right;
    private final @NotNull List<EudRegion> regions;

    /**
     * Creates a {@link EudSample} instance.
     *
     * @param mode     the unsigned emulator mode
     * @param emulator the normalized emulator pointer (image tag plus offset, other-pointer tag, or {@code 0})
     * @param object   the {@code 144} emulator object bytes, or empty when the emulator is absent
     * @param root     the {@code 48} root node bytes, or {@code null}
     * @param left     the {@code 48} left child bytes, or {@code null}
     * @param right    the {@code 48} right child bytes, or {@code null}
     * @param regions  the region descriptors
     * @throws IllegalArgumentException if the mode, a byte array or the region count is invalid, or a region key is duplicate or out of range
     */
    public EudSample(long mode, long emulator, byte @NotNull [] object,
                     byte @Nullable [] root, byte @Nullable [] left, byte @Nullable [] right,
                     @NotNull List<EudRegion> regions) {
        if (mode < 0 || mode > MAXIMUM_UNSIGNED_INTEGER || object.length != (emulator == 0 ? 0 : OBJECT_BYTES) ||
            emulator == 0 && (root != null || left != null || right != null || !regions.isEmpty()) ||
            root != null && root.length != EudMemory.NODE_BYTES ||
            left != null && left.length != EudMemory.NODE_BYTES ||
            right != null && right.length != EudMemory.NODE_BYTES ||
            regions.size() > EudMemory.MAXIMUM_REGIONS) {
            throw Exceptions.newIllegalArgumentException("Invalid emulator sample bounds");
        }

        List<EudRegion> orderedRegions = new ArrayList<>(regions);

        orderedRegions.sort(Comparator.comparingLong(EudRegion::getKey));

        long previousKey = -1;

        for (EudRegion region : orderedRegions) {
            if (region.getKey() <= previousKey || region.getKey() > MAXIMUM_UNSIGNED_INTEGER) {
                throw Exceptions.newIllegalArgumentException("Invalid or duplicate emulator region key");
            }

            previousKey = region.getKey();
        }

        this.mode     = mode;
        this.emulator = emulator;
        this.object   = object.clone();
        this.root     = root == null ? null : root.clone();
        this.left     = left == null ? null : left.clone();
        this.right    = right == null ? null : right.clone();
        this.regions  = List.copyOf(orderedRegions);
    }


    /**
     * @return the unsigned emulator mode
     */
    public long getMode() {
        return this.mode;
    }

    /**
     * @return the normalized emulator pointer
     */
    public long getEmulator() {
        return this.emulator;
    }

    /**
     * @return a copy of the emulator object bytes, or empty when the emulator is absent
     */
    public byte @NotNull [] getObject() {
        return this.object.clone();
    }

    /**
     * @return a copy of the root node bytes, or {@code null}
     */
    public byte @Nullable [] getRoot() {
        return this.root == null ? null : this.root.clone();
    }

    /**
     * @return a copy of the left child bytes, or {@code null}
     */
    public byte @Nullable [] getLeft() {
        return this.left == null ? null : this.left.clone();
    }

    /**
     * @return a copy of the right child bytes, or {@code null}
     */
    public byte @Nullable [] getRight() {
        return this.right == null ? null : this.right.clone();
    }

    /**
     * @return immutable region descriptors in ascending classic address order
     */
    public @NotNull List<EudRegion> getRegions() {
        return this.regions;
    }
}
