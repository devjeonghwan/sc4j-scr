package org.fogofwarlabs.screcorder.sample.location;

import org.fogofwarlabs.screcorder.sample.Sample;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Contains immutable location rectangles.
 */
public final class LocationsSample implements Sample {
    private final @NotNull List<LocationState> locations;

    /**
     * Creates a {@link LocationsSample} instance.
     *
     * @param locations the location values to copy
     */
    public LocationsSample(@NotNull List<LocationState> locations) {
        this.locations = List.copyOf(locations);
    }


    /**
     * @return the immutable location values
     */
    public @NotNull List<LocationState> getLocations() {
        return this.locations;
    }
}
