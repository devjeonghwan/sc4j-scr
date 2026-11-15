package org.fogofwarlabs.screcorder.sample.eud;

import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.sample.Sample;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Contains the emulator region hosts ordered by classic address.
 */
public final class EudHostsSample implements Sample {
    private static final long MAXIMUM_UNSIGNED_INTEGER = 0xFFFFFFFFL;

    private final @NotNull List<EudHostRegion> regions;

    /**
     * Creates a {@link EudHostsSample} instance.
     *
     * @param regions the host observations
     * @throws IllegalArgumentException if the region count exceeds the supported maximum, or a region key is duplicate or out of range
     */
    public EudHostsSample(@NotNull List<EudHostRegion> regions) {
        if (regions.size() > EudMemory.MAXIMUM_REGIONS) {
            throw Exceptions.newIllegalArgumentException("Too many emulator host regions");
        }

        List<EudHostRegion> orderedRegions = new ArrayList<>(regions);

        orderedRegions.sort(Comparator.comparingLong(EudHostRegion::getKey));

        long previousKey = -1;

        for (EudHostRegion region : orderedRegions) {
            if (region.getKey() <= previousKey || region.getKey() > MAXIMUM_UNSIGNED_INTEGER) {
                throw Exceptions.newIllegalArgumentException("Invalid or duplicate emulator host key");
            }

            previousKey = region.getKey();
        }

        this.regions = List.copyOf(orderedRegions);
    }


    /**
     * @return immutable host observations in ascending classic address order
     */
    public @NotNull List<EudHostRegion> getRegions() {
        return this.regions;
    }
}
