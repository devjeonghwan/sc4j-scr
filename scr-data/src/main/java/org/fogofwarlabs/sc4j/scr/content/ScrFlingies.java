package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.type.MotionProfile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Holds the flingy type identities of the SC:R mod, one per flingy.dat entry, in flingy.dat order: the entries the game code names
 * are constants, every other entry is named {@code "flingy <entry>"}. The mod registers them in this order, so the index of
 * each registered type is its flingy.dat entry ({@link ScrNativeIds}).
 */
public final class ScrFlingies {
    /**
     * The number of flingy.dat entries: the registered flingy types.
     */
    public static final int COUNT = 209;

    private static final @NotNull MotionProfile @NotNull [] TYPES = new MotionProfile[COUNT];

    private static final @NotNull List<@NotNull MotionProfile> TYPE_LIST;

    static {
        for (int nativeId = 0; nativeId < COUNT; nativeId++) {
            TYPES[nativeId] = new MotionProfile("flingy " + nativeId);
        }

        TYPE_LIST = List.of(TYPES);
    }

    /**
     * Returns the identity of a flingy.dat entry.
     *
     * @param nativeId the flingy.dat entry, from {@code 0} to {@code COUNT - 1}
     * @return the identity
     * @throws IllegalArgumentException if the entry is outside the table
     */
    public static @NotNull MotionProfile get(int nativeId) {
        if (nativeId < 0 || nativeId >= COUNT) {
            throw Exceptions.newIllegalArgumentException("Invalid flingy.dat entry: {0}", nativeId);
        }

        return TYPES[nativeId];
    }

    /**
     * @return every identity in flingy.dat order, as the mod registers them
     */
    public static @NotNull List<@NotNull MotionProfile> getTypes() {
        return TYPE_LIST;
    }


    private ScrFlingies() {
    }
}
