package org.fogofwarlabs.screcorder.capture.sampling;

import org.fogofwarlabs.screcorder.capture.MemoryReader;
import org.fogofwarlabs.screcorder.capture.RecordingTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * Holds the process memory reader and resolved addresses for one capture session.
 * This context and its samplers are confined to the collecting thread.
 */
public final class CaptureContext {
    /**
     * Creates a {@link CaptureContext} instance whose thread environment block addresses the target lists each time a sampler
     * reads them, so that only samplers that need the threads depend on the listing.
     *
     * @param target         the target that serves memory reads and lists its threads
     * @param moduleBase     the executable module base
     * @param contextAddress the resolved game context address
     * @return the created {@link CaptureContext} instance
     */
    public static @NotNull CaptureContext fromTarget(@NotNull RecordingTarget target, long moduleBase, long contextAddress) {
        return new CaptureContext(target, moduleBase, contextAddress, target, List.of());
    }


    private final @NotNull MemoryReader memory;
    private final long moduleBase;
    private final long contextAddress;
    private final @Nullable RecordingTarget threadSource;
    private final @NotNull List<Long> threadEnvironmentAddresses;

    /**
     * Creates a {@link CaptureContext} instance for a target without listed threads.
     *
     * @param memory         the target memory reader
     * @param moduleBase     the executable module base
     * @param contextAddress the resolved game context address
     */
    public CaptureContext(@NotNull MemoryReader memory, long moduleBase, long contextAddress) {
        this(memory, moduleBase, contextAddress, List.of());
    }

    /**
     * Creates a {@link CaptureContext} instance with known thread environment block addresses.
     *
     * @param memory                     the target memory reader
     * @param moduleBase                 the executable module base
     * @param contextAddress             the resolved game context address
     * @param threadEnvironmentAddresses the thread environment block addresses of the target in thread creation order, copied on construction
     */
    public CaptureContext(@NotNull MemoryReader memory, long moduleBase, long contextAddress, @NotNull List<Long> threadEnvironmentAddresses) {
        this(memory, moduleBase, contextAddress, null, List.copyOf(threadEnvironmentAddresses));
    }

    private CaptureContext(@NotNull MemoryReader memory, long moduleBase, long contextAddress,
                           @Nullable RecordingTarget threadSource, @NotNull List<Long> threadEnvironmentAddresses) {
        this.memory                     = memory;
        this.moduleBase                 = moduleBase;
        this.contextAddress             = contextAddress;
        this.threadSource               = threadSource;
        this.threadEnvironmentAddresses = threadEnvironmentAddresses;
    }


    /**
     * @return the target memory reader
     */
    public @NotNull MemoryReader getMemory() {
        return this.memory;
    }

    /**
     * @return the executable module base
     */
    public long getModuleBase() {
        return this.moduleBase;
    }

    /**
     * @return the resolved game context address
     */
    public long getContextAddress() {
        return this.contextAddress;
    }


    /**
     * Reads the thread environment block addresses of the target. A target-backed context lists the threads of the target on
     * every call; any other context returns the addresses it was created with.
     *
     * @return the immutable addresses in thread creation order, the initial thread first
     * @throws IOException if the threads of the target cannot be listed
     */
    public @NotNull List<Long> readThreadEnvironmentAddresses() throws IOException {
        return this.threadSource == null ? this.threadEnvironmentAddresses : this.threadSource.readThreadEnvironmentAddresses();
    }
}
