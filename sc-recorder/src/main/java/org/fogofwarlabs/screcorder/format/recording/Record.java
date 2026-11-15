package org.fogofwarlabs.screcorder.format.recording;

import org.fogofwarlabs.screcorder.format.recording.metadata.RecordMetadata;
import org.fogofwarlabs.screcorder.sample.SampleDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Describes one recorded session without materializing its frames.
 */
public final class Record {
    private final @NotNull List<SampleDefinition<?>> definitions;
    private final @NotNull RecordMetadata metadata;

    Record(@NotNull List<SampleDefinition<?>> definitions, @NotNull RecordMetadata metadata) {
        this.definitions = List.copyOf(definitions);
        this.metadata    = metadata;
    }

    /**
     * @return the selected definitions in ascending sample identifier order
     */
    public @NotNull List<SampleDefinition<?>> getDefinitions() {
        return this.definitions;
    }

    /**
     * @return the immutable provenance and collection settings
     */
    public @NotNull RecordMetadata getMetadata() {
        return this.metadata;
    }
}
