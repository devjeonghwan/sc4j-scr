package org.fogofwarlabs.screcorder.capture;

import org.fogofwarlabs.screcorder.binary.LittleEndianUtils;
import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.sampling.Sampler;
import org.fogofwarlabs.screcorder.capture.starcraft.StarCraftLayout;
import org.fogofwarlabs.screcorder.capture.windows.WindowsWaitableTimer;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.format.recording.RecordWriter;
import org.fogofwarlabs.screcorder.format.recording.metadata.LoadedReplayEvidence;
import org.fogofwarlabs.screcorder.format.recording.metadata.RecordMetadata;
import org.fogofwarlabs.screcorder.format.recording.observation.CaptureConsistency;
import org.fogofwarlabs.screcorder.format.recording.observation.SampleStatus;
import org.fogofwarlabs.screcorder.format.recording.observation.StorageMode;
import org.fogofwarlabs.screcorder.format.recording.summary.RecordSummary;
import org.fogofwarlabs.screcorder.format.recording.summary.Termination;
import org.fogofwarlabs.screcorder.sample.Sample;
import org.fogofwarlabs.screcorder.sample.SampleDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Captures observed game frames from a caller-owned read-only target.
 * <p>
 * The capture loop reads the frame counter until it changes ({@link CaptureOptions#getPollingIntervalNanoseconds()},
 * a busy wait by default) and samples a selected frame at once, without a settling delay. Each pass is bracketed by
 * two counter reads and two reads of the random block: the frame is consistent when both counter reads are equal, the
 * random block did not change and its gate is clear. A frame whose counter changed during the pass is lost; a frame
 * whose random bracket stays unstable is torn (strict mode) or dropped (gap-tolerant mode).
 */
public final class Recorder implements RecordingProducer {
    /**
     * The longest startup and capture deadline in seconds that a recorder accepts: one day.
     */
    public static final long MAXIMUM_TIMEOUT_SECONDS = 86400;

    private static final int MAXIMUM_ATTEMPTS = 40;

    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000;

    private static final long STARTUP_TIMEOUT_NANOSECONDS = 60 * NANOSECONDS_PER_SECOND;
    private static final long POLL_WAIT_NANOSECONDS = 1_000_000;
    private static final long FRAME_END_WAIT_NANOSECONDS = 8_000_000;

    private static final int REPLAY_HEADER_FRAME_COUNT_OFFSET = 1;

    private static final int REPLAY_FLAG_GAME = 0;
    private static final int REPLAY_FLAG_REPLAY = 1;


    private static final class Session {
        private final long contextAddress;
        private final byte @NotNull [] replayHeader;
        private final long deadline;

        private Session(long contextAddress, byte @NotNull [] replayHeader, long deadline) {
            this.contextAddress = contextAddress;
            this.replayHeader   = replayHeader;
            this.deadline       = deadline;
        }
    }


    private static void sampleAll(@NotNull Sampler<?> @NotNull [] samplers,
                                  @Nullable Sample @NotNull [] samples, @NotNull SampleStatus @NotNull [] statuses) {
        for (int index = 0; index < samplers.length; index++) {
            samples[index] = null;

            try {
                samples[index]  = samplers[index].sample();
                statuses[index] = SampleStatus.AVAILABLE;
            } catch (IOException failure) {
                statuses[index] = SampleStatus.READ_FAILED;
            }
        }
    }


    private final @NotNull RecordingTarget target;
    private final long moduleBase;
    private final boolean replayMode;
    private final @NotNull List<SampleDefinition<?>> definitions;
    private final @NotNull CaptureOptions options;
    private final long timeoutNanoseconds;
    private final @NotNull AtomicBoolean canceled;

    private long lostFrameCount;
    private long droppedFrameCount;

    /**
     * Creates a {@link Recorder} instance without taking ownership of its target.
     *
     * @param target         the opened target
     * @param moduleBase     the supported executable image base
     * @param replayMode     whether to capture a replay instead of a live game
     * @param definitions    the selected sample definitions
     * @param options        the polling, frame selection, gap and warm-up options
     * @param timeoutSeconds the total startup and capture deadline
     * @param canceled       the cancellation signal
     * @throws IllegalArgumentException if no sample definition is selected or {@code timeoutSeconds} is not positive or exceeds the supported maximum
     */
    public Recorder(@NotNull RecordingTarget target, long moduleBase, boolean replayMode,
                    @NotNull List<SampleDefinition<?>> definitions, @NotNull CaptureOptions options, long timeoutSeconds,
                    @NotNull AtomicBoolean canceled) {
        if (definitions.isEmpty() || timeoutSeconds <= 0 || timeoutSeconds > MAXIMUM_TIMEOUT_SECONDS) {
            throw Exceptions.newIllegalArgumentException("Invalid sample selection or capture timing");
        }

        this.target             = target;
        this.moduleBase         = moduleBase;
        this.replayMode         = replayMode;
        this.definitions        = List.copyOf(definitions);
        this.options            = options;
        this.timeoutNanoseconds = timeoutSeconds * NANOSECONDS_PER_SECOND;
        this.canceled           = canceled;

        this.lostFrameCount    = 0;
        this.droppedFrameCount = 0;
    }

    /**
     * @return the number of selected frames whose counter changed while they were sampled, in the last capture
     */
    public long getLostFrameCount() {
        return this.lostFrameCount;
    }

    /**
     * @return the number of selected frames dropped as inconsistent in gap-tolerant mode, in the last capture
     */
    public long getDroppedFrameCount() {
        return this.droppedFrameCount;
    }

    /**
     * Records a session using an owned Windows observation timer.
     *
     * @param output               the new recording path
     * @param metadata             the provenance builder
     * @param storageMode          the sample storage mode
     * @param expectedReplayHeader the paired header, or {@code null} for an unpaired attached session
     * @return the completed capture summary
     * @throws IOException if startup, target reads or output fail
     */
    public @NotNull RecordSummary record(@NotNull Path output, @NotNull RecordMetadata.Builder metadata,
                                         @NotNull StorageMode storageMode, byte @Nullable [] expectedReplayHeader) throws IOException {
        try (WindowsWaitableTimer timer = WindowsWaitableTimer.open()) {
            return this.record(output, metadata, storageMode, timer, expectedReplayHeader);
        }
    }

    /**
     * Records a session into a newly created file after checking its loaded replay header.
     *
     * @param output               the new recording path
     * @param metadata             the provenance builder
     * @param storageMode          the sample storage mode
     * @param timer                the caller-owned observation timer
     * @param expectedReplayHeader the paired header, or {@code null} for an unpaired attached session
     * @return the completed capture summary
     * @throws IOException if startup, target reads or output fail
     */
    public @NotNull RecordSummary record(@NotNull Path output, @NotNull RecordMetadata.Builder metadata,
                                         @NotNull StorageMode storageMode, @NotNull CaptureTimer timer,
                                         byte @Nullable [] expectedReplayHeader) throws IOException {
        Session session = this.waitForSession(expectedReplayHeader, timer);

        metadata.setLoadedReplayEvidence(expectedReplayHeader == null ? LoadedReplayEvidence.UNAVAILABLE : LoadedReplayEvidence.HEADER_MATCHED);

        if (this.replayMode) {
            long frameCount = LittleEndianUtils.readUnsigned32(session.replayHeader, REPLAY_HEADER_FRAME_COUNT_OFFSET);
            CaptureSchedule schedule = this.options.getSchedule();

            if (frameCount > schedule.getFirstFrame()) {
                metadata.setExpectedFrames(schedule.getFirstFrame(), Math.min(frameCount - 1, schedule.getLastFrame()));
            }
        }

        try (RecordWriter writer = RecordWriter.open(output, this.definitions, metadata.build(), storageMode)) {
            return this.capture(session, writer, timer);
        }
    }

    /**
     * Records one session after verifying its optional paired replay header. The writer and timer remain owned by the caller.
     *
     * @param writer               the recording destination
     * @param timer                the observation timer
     * @param expectedReplayHeader the paired header, or {@code null} for an attached session
     * @return the completed capture summary
     * @throws IOException if startup, target reads or recording output fail
     */
    @Override
    public @NotNull RecordSummary record(@NotNull RecordWriter writer, @NotNull CaptureTimer timer, byte @Nullable [] expectedReplayHeader) throws IOException {
        return this.capture(this.waitForSession(expectedReplayHeader, timer), writer, timer);
    }

    private @NotNull Session waitForSession(byte @Nullable [] expectedReplayHeader, @NotNull CaptureTimer timer) throws IOException {
        long started = System.nanoTime();
        long deadline = started + this.timeoutNanoseconds;
        long startupDeadline = started + Math.min(this.timeoutNanoseconds, STARTUP_TIMEOUT_NANOSECONDS);
        long contextAddress = 0;
        String startupFailure = "";
        byte[] replayHeader = new byte[StarCraftLayout.REPLAY_HEADER_SIZE];

        while (!this.canceled.get() && System.nanoTime() < startupDeadline) {
            if (!this.target.isAlive()) {
                throw Exceptions.newIOException("Target exited before a capture session began");
            }

            boolean headerMismatch = false;

            try {
                long encoded = this.target.readInteger(this.moduleBase + StarCraftLayout.CONTEXT_POINTER, Long.BYTES);
                long replayFlag = this.target.readInteger(this.moduleBase + StarCraftLayout.REPLAY_FLAG, Integer.BYTES);

                if (encoded != 0 && this.isSessionModeFlag(replayFlag)) {
                    long candidate = encoded ^ StarCraftLayout.CONTEXT_KEY;

                    this.target.readInteger(candidate + StarCraftLayout.FRAME_OFFSET, Integer.BYTES);
                    this.target.read(this.moduleBase + StarCraftLayout.REPLAY_HEADER, replayHeader, 0, replayHeader.length);

                    if (expectedReplayHeader == null || Arrays.equals(expectedReplayHeader, replayHeader)) {
                        contextAddress = candidate;

                        break;
                    }

                    headerMismatch = LittleEndianUtils.readUnsigned32(replayHeader, REPLAY_HEADER_FRAME_COUNT_OFFSET) != 0;
                    startupFailure = "Waiting for the replay header to be published";
                }
            } catch (IOException failure) {
                startupFailure = String.valueOf(failure.getMessage());
            }

            if (headerMismatch) {
                throw Exceptions.newIOException("Loaded replay header does not match the paired input");
            }

            timer.await(POLL_WAIT_NANOSECONDS);
        }

        if (contextAddress == 0) {
            throw Exceptions.newIOException("No session began before cancellation or timeout. Last startup read: {0}", startupFailure);
        }

        this.target.read(this.moduleBase + StarCraftLayout.REPLAY_HEADER, replayHeader, 0, replayHeader.length);

        if (expectedReplayHeader != null && !Arrays.equals(expectedReplayHeader, replayHeader)) {
            throw Exceptions.newIOException("Loaded replay header does not match the paired input");
        }

        return new Session(contextAddress, replayHeader, deadline);
    }

    private @NotNull RecordSummary capture(@NotNull Session session, @NotNull RecordWriter writer, @NotNull CaptureTimer timer) throws IOException {
        CaptureContext context = CaptureContext.fromTarget(this.target, this.moduleBase, session.contextAddress);
        Sampler<?>[] samplers = new Sampler<?>[this.definitions.size()];
        Sample[] samples = new Sample[this.definitions.size()];
        SampleStatus[] statuses = new SampleStatus[this.definitions.size()];

        for (int index = 0; index < samplers.length; index++) {
            samplers[index] = this.definitions.get(index).createSampler(context);
        }

        long frameCount = this.replayMode ? LittleEndianUtils.readUnsigned32(session.replayHeader, REPLAY_HEADER_FRAME_COUNT_OFFSET) : 0;
        long finalFrame = frameCount > 0 ? frameCount - 1 : -1;
        CaptureSchedule schedule = this.options.getSchedule();
        long pollingInterval = this.options.getPollingIntervalNanoseconds();
        long lastFrame = -1;
        long lastCycle = -1;
        long writtenFrame = -1;
        byte[] randomBefore = new byte[StarCraftLayout.RANDOM_BLOCK_SIZE];
        byte[] randomAfter = new byte[StarCraftLayout.RANDOM_BLOCK_SIZE];
        Termination termination = Termination.FAILED;

        this.lostFrameCount    = 0;
        this.droppedFrameCount = 0;

        try {
            this.warmUp(samplers);

            long started = System.nanoTime();

            while (!this.canceled.get() && System.nanoTime() < session.deadline) {
                if (!this.target.isAlive()) {
                    break;
                }

                long encoded = this.target.readInteger(this.moduleBase + StarCraftLayout.CONTEXT_POINTER, Long.BYTES);
                long replayFlag = this.target.readInteger(this.moduleBase + StarCraftLayout.REPLAY_FLAG, Integer.BYTES);

                if (encoded == 0 || (encoded ^ StarCraftLayout.CONTEXT_KEY) != session.contextAddress || !this.isSessionModeFlag(replayFlag)) {
                    termination = Termination.NORMAL;

                    break;
                }

                long frameNumber = this.readFrame(session);
                long cycle = this.readCycle();

                if (frameNumber == lastFrame) {
                    lastCycle = cycle;
                    timer.await(pollingInterval);

                    continue;
                }

                if (frameNumber < lastFrame) {
                    break;
                }

                long previousCycle = frameNumber == lastFrame + 1 ? lastCycle : -1;

                lastFrame = frameNumber;
                lastCycle = cycle;

                if (schedule.isPastWindow(frameNumber)) {
                    termination = Termination.NORMAL;

                    break;
                }

                if (!schedule.isSelected(frameNumber, finalFrame, writtenFrame)) {
                    continue;
                }

                if (!this.awaitFrameEnd(session, frameNumber, previousCycle, cycle, timer, pollingInterval)) {
                    this.lostFrameCount++;

                    continue;
                }

                long elapsedNanoseconds = System.nanoTime() - started;
                boolean advanced = false;
                boolean torn = true;

                for (int attempt = 0; attempt < MAXIMUM_ATTEMPTS; attempt++) {
                    if (attempt > 0 && this.readFrame(session) != frameNumber) {
                        advanced = true;

                        break;
                    }

                    this.target.read(this.moduleBase + StarCraftLayout.RANDOM_BLOCK, randomBefore, 0, randomBefore.length);
                    sampleAll(samplers, samples, statuses);
                    long frameNumberAfter = this.readFrame(session);
                    this.target.read(this.moduleBase + StarCraftLayout.RANDOM_BLOCK, randomAfter, 0, randomAfter.length);

                    if (frameNumberAfter != frameNumber) {
                        advanced = true;

                        break;
                    }

                    torn = !Arrays.equals(randomBefore, randomAfter) ||
                           LittleEndianUtils.readInt32(randomBefore, StarCraftLayout.RANDOM_GATE_OFFSET) != 0 ||
                           LittleEndianUtils.readInt32(randomAfter, StarCraftLayout.RANDOM_GATE_OFFSET) != 0;

                    if (!torn) {
                        break;
                    }

                    timer.await(pollingInterval == 0 ? 0 : POLL_WAIT_NANOSECONDS);
                }

                if (advanced) {
                    this.lostFrameCount++;

                    continue;
                }

                if (torn && this.options.isGapTolerant()) {
                    this.droppedFrameCount++;

                    continue;
                }

                this.writeFrame(writer, frameNumber, elapsedNanoseconds, torn, samples, statuses);
                writtenFrame = frameNumber;
            }

            if (this.canceled.get()) {
                termination = Termination.CANCELLED;
            } else if (termination != Termination.NORMAL && System.nanoTime() >= session.deadline) {
                termination = Termination.TIMEOUT;
            }
        } catch (IOException | RuntimeException failure) {
            try {
                writer.finish(Termination.FAILED);
            } catch (IOException | RuntimeException finalizationFailure) {
                failure.addSuppressed(finalizationFailure);
            }

            throw failure;
        }

        return writer.finish(termination);
    }

    /**
     * Waits until the frame's processing has reached its trigger pass. The game increments the frame counter before it
     * runs the frame, and its trigger pass decrements the trigger cycle WORD every frame, so the state is complete only
     * once the cycle WORD changed after the counter did. Without a known previous cycle (the previous frame was not
     * observed) the wait is bounded.
     *
     * @param session         the capture session
     * @param frameNumber     the observed frame number
     * @param previousCycle   the trigger cycle of the previous frame, or {@code -1} when it was not observed
     * @param cycle           the trigger cycle read with {@code frameNumber}
     * @param timer           the observation timer
     * @param pollingInterval the wait between unchanged counter reads; {@code 0} busy-waits
     * @return {@code true} if the counter stayed on {@code frameNumber} until the frame ended or the bounded wait elapsed, {@code false} otherwise
     * @throws IOException if the frame counter or the trigger cycle cannot be read, or the wait fails
     */
    private boolean awaitFrameEnd(@NotNull Session session, long frameNumber, long previousCycle, long cycle,
                                  @NotNull CaptureTimer timer, long pollingInterval) throws IOException {
        if (previousCycle < 0 || cycle != previousCycle) {
            return true;
        }

        long deadline = System.nanoTime() + FRAME_END_WAIT_NANOSECONDS;

        while (System.nanoTime() < deadline) {
            timer.await(pollingInterval == 0 ? 0 : Math.min(pollingInterval, POLL_WAIT_NANOSECONDS));

            if (this.readFrame(session) != frameNumber) {
                return false;
            }

            if (this.readCycle() != previousCycle) {
                return true;
            }
        }

        return true;
    }

    private boolean isSessionModeFlag(long replayFlag) {
        return this.replayMode ? replayFlag == REPLAY_FLAG_REPLAY : replayFlag == REPLAY_FLAG_GAME;
    }

    private long readCycle() throws IOException {
        return this.target.readInteger(this.moduleBase + StarCraftLayout.TRIGGER_CYCLE, Short.BYTES);
    }

    private long readFrame(@NotNull Session session) throws IOException {
        return this.target.readInteger(session.contextAddress + StarCraftLayout.FRAME_OFFSET, Integer.BYTES);
    }

    private void warmUp(@NotNull Sampler<?> @NotNull [] samplers) {
        for (int pass = 0; pass < this.options.getWarmUpPasses(); pass++) {
            for (Sampler<?> sampler : samplers) {
                try {
                    sampler.sample();
                } catch (IOException ignored) {
                    // A warm-up pass only exercises the sampling code; its result is discarded.
                }
            }
        }
    }

    private void writeFrame(@NotNull RecordWriter writer, long frameNumber, long elapsedNanoseconds, boolean torn,
                            @Nullable Sample @NotNull [] samples, @NotNull SampleStatus @NotNull [] statuses) throws IOException {
        writer.beginFrame(frameNumber, elapsedNanoseconds, torn ? CaptureConsistency.TORN : CaptureConsistency.MARKER_STABLE);

        boolean committed = false;

        try {
            for (int index = 0; index < this.definitions.size(); index++) {
                SampleDefinition<?> definition = this.definitions.get(index);
                Sample sample = samples[index];

                if (torn) {
                    writer.appendUnavailable(definition, SampleStatus.TORN);
                } else if (statuses[index] != SampleStatus.AVAILABLE || sample == null) {
                    writer.appendUnavailable(definition, statuses[index]);
                } else {
                    this.appendSample(writer, definition, sample);
                }
            }

            writer.endFrame();
            committed = true;
        } finally {
            if (!committed) {
                writer.abortFrame();
            }
        }
    }

    private <SampleValue extends Sample> void appendSample(@NotNull RecordWriter writer,
                                                           @NotNull SampleDefinition<SampleValue> definition, @NotNull Sample sample) throws IOException {
        writer.append(definition, definition.getSampleClass().cast(sample));
    }
}
