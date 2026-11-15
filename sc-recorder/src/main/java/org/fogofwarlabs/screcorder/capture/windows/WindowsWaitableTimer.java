package org.fogofwarlabs.screcorder.capture.windows;

import org.fogofwarlabs.screcorder.capture.CaptureTimer;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Owns a reusable high-resolution timer confined to the capture thread.
 */
@SuppressWarnings("restricted")
public final class WindowsWaitableTimer implements CaptureTimer, Closeable {
    private static final int CREATE_WAITABLE_TIMER_HIGH_RESOLUTION = 2;
    private static final int SYNCHRONIZE = 0x100000;
    private static final int TIMER_MODIFY_STATE = 2;

    private static final int WAIT_OBJECT_SIGNALED = 0;
    private static final int WAIT_MARGIN_MILLISECONDS = 1000;

    private static final long NANOSECONDS_PER_TIMER_UNIT = 100;
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000;

    /**
     * Creates a {@link WindowsWaitableTimer} instance confined to the calling thread.
     *
     * @return the created {@link WindowsWaitableTimer} instance
     * @throws IOException if the timer cannot be initialized
     */
    public static @NotNull WindowsWaitableTimer open() throws IOException {
        return new WindowsWaitableTimer();
    }


    private final @NotNull Arena arena;

    private final @NotNull MethodHandle setTimer;
    private final @NotNull MethodHandle waitForTimer;
    private final @NotNull MethodHandle closeHandle;
    private final @NotNull MemorySegment callState;
    private final long errorOffset;
    private final @NotNull MemorySegment dueTime;

    private boolean closed;
    private final @NotNull MemorySegment timer;

    private WindowsWaitableTimer() throws IOException {
        this.arena = Arena.ofShared();

        try {
            SymbolLookup kernel = SymbolLookup.libraryLookup("kernel32.dll", this.arena);
            Linker linker = Linker.nativeLinker();
            MethodHandle createTimer = linker.downcallHandle(kernel.findOrThrow("CreateWaitableTimerExW"),
                                                             FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                                                                                   ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
                                                             Linker.Option.captureCallState("GetLastError"));

            this.setTimer     = linker.downcallHandle(kernel.findOrThrow("SetWaitableTimer"),
                                                      FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                                                                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                                                                            ValueLayout.JAVA_INT),
                                                      Linker.Option.captureCallState("GetLastError"));
            this.waitForTimer = linker.downcallHandle(kernel.findOrThrow("WaitForSingleObject"),
                                                      FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                                                      Linker.Option.captureCallState("GetLastError"));
            this.closeHandle  = linker.downcallHandle(kernel.findOrThrow("CloseHandle"),
                                                      FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            this.callState    = this.arena.allocate(Linker.Option.captureStateLayout());
            this.errorOffset  = Linker.Option.captureStateLayout().byteOffset(MemoryLayout.PathElement.groupElement("GetLastError"));
            this.dueTime      = this.arena.allocate(ValueLayout.JAVA_LONG);

            this.closed = false;
            this.timer  = (MemorySegment) createTimer.invokeExact(this.callState, MemorySegment.NULL, MemorySegment.NULL,
                                                                  CREATE_WAITABLE_TIMER_HIGH_RESOLUTION, SYNCHRONIZE | TIMER_MODIFY_STATE);

            if (this.timer.address() == 0) {
                throw Exceptions.newIOException("Cannot create high-resolution timer: Windows error {0}",
                                                this.callState.get(ValueLayout.JAVA_INT, this.errorOffset));
            }
        } catch (Throwable failure) {
            this.arena.close();

            throw WindowsProcess.convertToIOException(failure, "Cannot initialize capture timer");
        }
    }

    /**
     * Waits for the requested duration.
     * <p>
     * A duration of {@code 0} or less only signals a busy-wait iteration ({@link Thread#onSpinWait()}) instead of a
     * timer wait.
     *
     * @param nanoseconds the duration, with {@code 0} requesting no wait
     * @throws IOException if the timer is closed or the wait fails
     */
    @Override
    public void await(long nanoseconds) throws IOException {
        if (this.closed) {
            throw Exceptions.newIOException("Capture timer is closed");
        }

        if (nanoseconds <= 0) {
            Thread.onSpinWait();

            return;
        }

        long units = nanoseconds / NANOSECONDS_PER_TIMER_UNIT + (nanoseconds % NANOSECONDS_PER_TIMER_UNIT == 0 ? 0 : 1);

        this.dueTime.set(ValueLayout.JAVA_LONG, 0, -units);

        try {
            int scheduled = (int) this.setTimer.invokeExact(this.callState, this.timer, this.dueTime, 0, MemorySegment.NULL, MemorySegment.NULL, 0);

            if (scheduled == 0) {
                throw Exceptions.newIOException("Cannot schedule capture timer: Windows error {0}",
                                                this.callState.get(ValueLayout.JAVA_INT, this.errorOffset));
            }

            int timeoutMilliseconds = (int) (nanoseconds / NANOSECONDS_PER_MILLISECOND + WAIT_MARGIN_MILLISECONDS);
            int status = (int) this.waitForTimer.invokeExact(this.callState, this.timer, timeoutMilliseconds);

            if (status != WAIT_OBJECT_SIGNALED) {
                throw Exceptions.newIOException("Capture timer wait failed: status {0}, Windows error {1}", status,
                                                this.callState.get(ValueLayout.JAVA_INT, this.errorOffset));
            }
        } catch (Throwable failure) {
            throw WindowsProcess.convertToIOException(failure, "Capture timer call failed");
        }
    }

    /**
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     *
     * <p>
     * As noted in {@link AutoCloseable#close()}, cases where the
     * close may fail require careful attention. It is strongly advised
     * to relinquish the underlying resources and to internally
     * <em>mark</em> the {@code Closeable} as closed, prior to throwing
     * the {@code IOException}.
     *
     * @throws IOException if an I/O error occurs
     * @implSpec Closes the timer handle and then the native storage; the instance is marked closed first.
     */
    @Override
    public void close() throws IOException {
        if (this.closed) {
            return;
        }

        this.closed = true;

        try {
            int status = (int) this.closeHandle.invokeExact(this.timer);

            if (status == 0) {
                throw Exceptions.newIOException("Cannot close capture timer");
            }
        } catch (Throwable failure) {
            throw WindowsProcess.convertToIOException(failure, "Cannot close capture timer");
        } finally {
            this.arena.close();
        }
    }
}
