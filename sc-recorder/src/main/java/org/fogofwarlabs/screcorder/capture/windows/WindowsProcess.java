package org.fogofwarlabs.screcorder.capture.windows;

import org.fogofwarlabs.screcorder.binary.LittleEndianUtils;
import org.fogofwarlabs.screcorder.capture.RecordingTarget;
import org.fogofwarlabs.screcorder.capture.starcraft.StarCraftLayout;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reads a Windows x64 process through a reusable native transfer buffer.
 * <p>
 * Instances are confined to the thread that opened them. Remote addresses are passed to the operating system;
 * they are never dereferenced as local memory.
 */
@SuppressWarnings("restricted")
public final class WindowsProcess implements RecordingTarget, Closeable {
    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;
    private static final int PROCESS_VM_READ = 0x10;

    private static final int PROCESS_STILL_ACTIVE = 259;

    private static final int PROCESS_BASIC_INFORMATION_CLASS = 0;
    private static final int PROCESS_INFORMATION_SIZE = 48;
    private static final int PROCESS_INFORMATION_ENVIRONMENT_OFFSET = 8;
    private static final int ENVIRONMENT_IMAGE_OFFSET = 16;

    private static final int INITIAL_BUFFER_SIZE = 1024 * 1024;
    private static final int BUFFER_GROWTH_FACTOR = 2;

    private static final int EXECUTABLE_MINIMUM_SIZE = 0x100;
    private static final int EXECUTABLE_HEADER_SIZE = 0x400;
    private static final int PORTABLE_EXECUTABLE_POINTER_OFFSET = 0x3C;
    private static final int PORTABLE_EXECUTABLE_HEADER_SIZE = 0x58;
    private static final int PORTABLE_EXECUTABLE_SIGNATURE = 0x4550;
    private static final int PORTABLE_EXECUTABLE_MACHINE_OFFSET = 4;
    private static final int PORTABLE_EXECUTABLE_TIMESTAMP_OFFSET = 8;
    private static final int PORTABLE_EXECUTABLE_IMAGE_SIZE_OFFSET = 0x50;

    private static final int FILE_VERSION_HEADER_SIZE = 16;
    private static final int FILE_VERSION_SIGNATURE = 0xFEEF04BD;
    private static final int FILE_VERSION_MAJOR_OFFSET = 8;
    private static final int FILE_VERSION_MAJOR = 0x10017;
    private static final int FILE_VERSION_MINOR_OFFSET = 12;
    private static final int FILE_VERSION_MINOR = 0xA34CB;

    private static final int SNAPSHOT_THREADS = 0x4;
    private static final long INVALID_HANDLE_VALUE = -1;
    private static final int THREAD_ENTRY_SIZE = 28;
    private static final int THREAD_ENTRY_IDENTIFIER_OFFSET = 8;
    private static final int THREAD_ENTRY_OWNER_OFFSET = 12;

    private static final int THREAD_QUERY_LIMITED_INFORMATION = 0x800;
    private static final int THREAD_BASIC_INFORMATION_CLASS = 0;
    private static final int THREAD_INFORMATION_SIZE = 48;
    private static final int THREAD_INFORMATION_ENVIRONMENT_OFFSET = 8;

    private static final int THREAD_TIME_COUNT = 4;
    private static final int THREAD_CREATION_TIME_OFFSET = 0;
    private static final int THREAD_EXIT_TIME_OFFSET = Long.BYTES;
    private static final int THREAD_KERNEL_TIME_OFFSET = 2 * Long.BYTES;
    private static final int THREAD_USER_TIME_OFFSET = 3 * Long.BYTES;


    /**
     * Holds one listed target thread with the values that order the thread list.
     */
    private static final class ListedThread {
        private final long creationTime;
        private final long identifier;
        private final long environmentAddress;

        private ListedThread(long creationTime, long identifier, long environmentAddress) {
            this.creationTime       = creationTime;
            this.identifier         = identifier;
            this.environmentAddress = environmentAddress;
        }
    }


    /**
     * Creates a {@link WindowsProcess} instance with query and read access.
     *
     * @param processIdentifier the process identifier
     * @return the created {@link WindowsProcess} instance
     * @throws IOException if the process cannot be opened or the platform is unsupported
     */
    public static @NotNull WindowsProcess open(long processIdentifier) throws IOException {
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows") || !System.getProperty("os.arch").equalsIgnoreCase("amd64")) {
            throw Exceptions.newIOException("Capture requires Windows x64");
        }

        return new WindowsProcess(processIdentifier);
    }

    /**
     * Checks the executable version resource and portable executable identity.
     *
     * @param executable the executable path
     * @throws IOException if the executable is not the supported build
     */
    public static void checkExecutable(@NotNull Path executable) throws IOException {
        byte[] contents = Files.readAllBytes(executable);

        checkImage(contents);

        try (Arena temporary = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup version = SymbolLookup.libraryLookup("version.dll", temporary);
            MethodHandle sizeFunction = linker.downcallHandle(version.findOrThrow("GetFileVersionInfoSizeW"),
                                                              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MethodHandle readFunction = linker.downcallHandle(version.findOrThrow("GetFileVersionInfoW"),
                                                              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                                                                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            MethodHandle queryFunction = linker.downcallHandle(version.findOrThrow("VerQueryValueW"),
                                                               FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                                                                                     ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MemorySegment filename = temporary.allocateFrom(ValueLayout.JAVA_CHAR, (executable.toAbsolutePath() + "\0").toCharArray());
            MemorySegment ignored = temporary.allocate(ValueLayout.JAVA_INT);
            int size = (int) sizeFunction.invokeExact(filename, ignored);

            if (size <= 0 || size > StarCraftLayout.MAXIMUM_READ) {
                throw Exceptions.newIOException("Executable has no readable version resource");
            }

            MemorySegment buffer = temporary.allocate(size);
            int read = (int) readFunction.invokeExact(filename, 0, size, buffer);
            MemorySegment root = temporary.allocateFrom(ValueLayout.JAVA_CHAR, new char[]{'\\', 0});
            MemorySegment pointer = temporary.allocate(ValueLayout.ADDRESS);
            MemorySegment length = temporary.allocate(ValueLayout.JAVA_INT);
            int queried = (int) queryFunction.invokeExact(buffer, root, pointer, length);
            long offset = pointer.get(ValueLayout.ADDRESS, 0).address() - buffer.address();

            if (read == 0 || queried == 0 || length.get(ValueLayout.JAVA_INT, 0) < FILE_VERSION_HEADER_SIZE ||
                offset < 0 || offset > size - FILE_VERSION_HEADER_SIZE ||
                buffer.get(ValueLayout.JAVA_INT, offset) != FILE_VERSION_SIGNATURE ||
                buffer.get(ValueLayout.JAVA_INT, offset + FILE_VERSION_MAJOR_OFFSET) != FILE_VERSION_MAJOR ||
                buffer.get(ValueLayout.JAVA_INT, offset + FILE_VERSION_MINOR_OFFSET) != FILE_VERSION_MINOR) {
                throw Exceptions.newIOException("Unsupported file version; expected {0}", StarCraftLayout.BUILD);
            }
        } catch (Throwable failure) {
            throw convertToIOException(failure, "Cannot inspect executable version");
        }
    }

    private static void checkImage(byte @NotNull [] contents) throws IOException {
        if (contents.length < EXECUTABLE_MINIMUM_SIZE || contents[0] != 'M' || contents[1] != 'Z') {
            throw Exceptions.newIOException("Invalid executable header");
        }

        int headerOffset = LittleEndianUtils.readInt32(contents, PORTABLE_EXECUTABLE_POINTER_OFFSET);

        if (headerOffset < 0 || headerOffset > contents.length - PORTABLE_EXECUTABLE_HEADER_SIZE ||
            LittleEndianUtils.readInt32(contents, headerOffset) != PORTABLE_EXECUTABLE_SIGNATURE ||
            LittleEndianUtils.readUnsigned(contents, headerOffset + PORTABLE_EXECUTABLE_MACHINE_OFFSET, Short.BYTES) !=
            StarCraftLayout.IMAGE_MACHINE ||
            LittleEndianUtils.readInt32(contents, headerOffset + PORTABLE_EXECUTABLE_TIMESTAMP_OFFSET) != StarCraftLayout.IMAGE_TIMESTAMP ||
            LittleEndianUtils.readInt32(contents, headerOffset + PORTABLE_EXECUTABLE_IMAGE_SIZE_OFFSET) != StarCraftLayout.IMAGE_SIZE) {
            throw Exceptions.newIOException("Unsupported executable identity; expected {0} x64", StarCraftLayout.BUILD);
        }
    }

    /**
     * Returns a caught native-call failure as an {@link IOException}: an {@link IOException} is returned unchanged and
     * any other failure is wrapped with the supplied message.
     *
     * @param failure the caught failure
     * @param message the message of a wrapping {@link IOException}
     * @return the {@link IOException} to throw
     */
    static @NotNull IOException convertToIOException(@NotNull Throwable failure, @NotNull String message) {
        if (failure instanceof IOException exception) {
            return exception;
        }

        return Exceptions.newIOException(failure, message);
    }


    private final long processIdentifier;

    private final @NotNull Arena arena;
    private final @NotNull MethodHandle readProcessMemory;
    private final @NotNull MethodHandle closeHandle;
    private final @NotNull MethodHandle queryInformationProcess;
    private final @NotNull MethodHandle getExitCodeProcess;
    private final @NotNull MemorySegment exitStatus;
    private final @NotNull MemorySegment callState;
    private final long errorOffset;
    private final @NotNull MemorySegment transferred;
    private @NotNull MemorySegment transferBuffer;
    private final byte @NotNull [] scalarBuffer;

    private long bytesRead;
    private boolean closed;
    private final @NotNull MemorySegment process;

    private WindowsProcess(long processIdentifier) throws IOException {
        this.processIdentifier = processIdentifier;

        this.arena = Arena.ofShared();

        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel = SymbolLookup.libraryLookup("kernel32.dll", this.arena);
            SymbolLookup system = SymbolLookup.libraryLookup("ntdll.dll", this.arena);
            MethodHandle openProcess = linker.downcallHandle(kernel.findOrThrow("OpenProcess"),
                                                             FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                                                                   ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
                                                             Linker.Option.captureCallState("GetLastError"));

            this.readProcessMemory       = linker.downcallHandle(kernel.findOrThrow("ReadProcessMemory"),
                                                                 FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                                                       ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                                                                                       ValueLayout.JAVA_LONG, ValueLayout.ADDRESS),
                                                                 Linker.Option.captureCallState("GetLastError"));
            this.closeHandle             = linker.downcallHandle(kernel.findOrThrow("CloseHandle"),
                                                                 FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            this.queryInformationProcess = linker.downcallHandle(system.findOrThrow("NtQueryInformationProcess"),
                                                                 FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                                                       ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                                                                       ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                                                                       ValueLayout.ADDRESS));
            this.getExitCodeProcess      = linker.downcallHandle(kernel.findOrThrow("GetExitCodeProcess"),
                                                                 FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                                                       ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                                                                 Linker.Option.captureCallState("GetLastError"));
            this.exitStatus              = this.arena.allocate(ValueLayout.JAVA_INT);
            this.callState               = this.arena.allocate(Linker.Option.captureStateLayout());
            this.errorOffset             = Linker.Option.captureStateLayout().byteOffset(MemoryLayout.PathElement.groupElement("GetLastError"));
            this.transferred             = this.arena.allocate(ValueLayout.JAVA_LONG);
            this.transferBuffer          = this.arena.allocate(INITIAL_BUFFER_SIZE);
            this.scalarBuffer            = new byte[Long.BYTES];

            this.bytesRead = 0;
            this.closed    = false;
            this.process   = (MemorySegment) openProcess.invokeExact(this.callState,
                                                                     PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ, 0,
                                                                     Math.toIntExact(processIdentifier));

            if (this.process.address() == 0) {
                int error = this.callState.get(ValueLayout.JAVA_INT, this.errorOffset);

                throw Exceptions.newIOException("Cannot open process {0}: Windows error {1}", processIdentifier, error);
            }
        } catch (Throwable failure) {
            this.arena.close();

            throw convertToIOException(failure, "Cannot initialize Windows memory access");
        }
    }

    /**
     * @return the total number of bytes read from this target
     */
    @Override
    public long getBytesRead() {
        return this.bytesRead;
    }


    /**
     * Checks termination through the opened process handle.
     *
     * @return {@code true} if the target is running, {@code false} otherwise
     * @throws IOException if the handle is closed or target termination cannot be queried
     */
    @Override
    public boolean isAlive() throws IOException {
        if (this.closed) {
            throw Exceptions.newIOException("Process handle is closed");
        }

        try {
            int success = (int) this.getExitCodeProcess.invokeExact(this.callState, this.process, this.exitStatus);

            if (success == 0) {
                throw Exceptions.newIOException("Cannot query process termination: Windows error {0}",
                                                this.callState.get(ValueLayout.JAVA_INT, this.errorOffset));
            }

            return this.exitStatus.get(ValueLayout.JAVA_INT, 0) == PROCESS_STILL_ACTIVE;
        } catch (Throwable failure) {
            throw convertToIOException(failure, "Cannot query process termination");
        }
    }

    /**
     * Reads the executable image base from process information.
     *
     * @return the executable image base
     * @throws IOException if the handle is closed or process information cannot be read
     */
    public long readModuleBase() throws IOException {
        if (this.closed) {
            throw Exceptions.newIOException("Process handle is closed");
        }

        try (Arena temporary = Arena.ofConfined()) {
            MemorySegment information = temporary.allocate(PROCESS_INFORMATION_SIZE, Long.BYTES);
            int status = (int) this.queryInformationProcess.invokeExact(this.process, PROCESS_BASIC_INFORMATION_CLASS, information,
                                                                        PROCESS_INFORMATION_SIZE, MemorySegment.NULL);

            if (status != 0) {
                throw Exceptions.newIOException("Process information failed: status {0}", Integer.toHexString(status));
            }

            long environment = information.get(ValueLayout.ADDRESS, PROCESS_INFORMATION_ENVIRONMENT_OFFSET).address();

            return this.readInteger(environment + ENVIRONMENT_IMAGE_OFFSET, Long.BYTES);
        } catch (Throwable failure) {
            throw convertToIOException(failure, "Cannot find executable image base");
        }
    }

    /**
     * Lists the thread environment block addresses of the target's threads. An in-memory target has no threads and lists none.
     * <p>
     * The threads come from a system thread snapshot; a thread that exits before it can be opened is left out. Threads are
     * ordered by creation time and then by thread identifier.
     *
     * @return the immutable addresses in thread creation order, the initial thread first
     * @throws IOException if the handle is closed or the threads of the target cannot be listed
     */
    @Override
    public @NotNull List<Long> readThreadEnvironmentAddresses() throws IOException {
        if (this.closed) {
            throw Exceptions.newIOException("Process handle is closed");
        }

        try (Arena temporary = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel = SymbolLookup.libraryLookup("kernel32.dll", temporary);
            SymbolLookup system = SymbolLookup.libraryLookup("ntdll.dll", temporary);
            MethodHandle createSnapshot = linker.downcallHandle(kernel.findOrThrow("CreateToolhelp32Snapshot"),
                                                                FunctionDescriptor.of(ValueLayout.ADDRESS,
                                                                                      ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            MethodHandle findFirstThread = linker.downcallHandle(kernel.findOrThrow("Thread32First"),
                                                                 FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                                                       ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MethodHandle findNextThread = linker.downcallHandle(kernel.findOrThrow("Thread32Next"),
                                                                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                                                      ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MethodHandle openThread = linker.downcallHandle(kernel.findOrThrow("OpenThread"),
                                                            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                                                                  ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            MethodHandle getThreadTimes = linker.downcallHandle(kernel.findOrThrow("GetThreadTimes"),
                                                                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                                                                                      ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                                                                                      ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MethodHandle queryInformationThread = linker.downcallHandle(system.findOrThrow("NtQueryInformationThread"),
                                                                        FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                                                                              ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                                                                              ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                                                                              ValueLayout.ADDRESS));
            MemorySegment entry = temporary.allocate(THREAD_ENTRY_SIZE, Integer.BYTES);
            MemorySegment information = temporary.allocate(THREAD_INFORMATION_SIZE, Long.BYTES);
            MemorySegment times = temporary.allocate((long) THREAD_TIME_COUNT * Long.BYTES, Long.BYTES);
            List<ListedThread> threads = new ArrayList<>();
            MemorySegment snapshot = (MemorySegment) createSnapshot.invokeExact(SNAPSHOT_THREADS, 0);

            if (snapshot.address() == INVALID_HANDLE_VALUE) {
                throw Exceptions.newIOException("Cannot list the threads of process {0}", this.processIdentifier);
            }

            try {
                entry.set(ValueLayout.JAVA_INT, 0, THREAD_ENTRY_SIZE);

                int found = (int) findFirstThread.invokeExact(snapshot, entry);

                for (; found != 0; found = (int) findNextThread.invokeExact(snapshot, entry)) {
                    if (Integer.toUnsignedLong(entry.get(ValueLayout.JAVA_INT, THREAD_ENTRY_OWNER_OFFSET)) != this.processIdentifier) {
                        continue;
                    }

                    int threadIdentifier = entry.get(ValueLayout.JAVA_INT, THREAD_ENTRY_IDENTIFIER_OFFSET);
                    MemorySegment thread = (MemorySegment) openThread.invokeExact(THREAD_QUERY_LIMITED_INFORMATION, 0, threadIdentifier);

                    if (thread.address() == 0) {
                        continue;
                    }

                    try {
                        int status = (int) queryInformationThread.invokeExact(thread, THREAD_BASIC_INFORMATION_CLASS, information,
                                                                              THREAD_INFORMATION_SIZE, MemorySegment.NULL);
                        int timed = (int) getThreadTimes.invokeExact(thread,
                                                                     times.asSlice(THREAD_CREATION_TIME_OFFSET),
                                                                     times.asSlice(THREAD_EXIT_TIME_OFFSET),
                                                                     times.asSlice(THREAD_KERNEL_TIME_OFFSET),
                                                                     times.asSlice(THREAD_USER_TIME_OFFSET));

                        if (status != 0 || timed == 0) {
                            throw Exceptions.newIOException("Cannot query thread {0} of process {1}", Integer.toUnsignedLong(threadIdentifier),
                                                            this.processIdentifier);
                        }

                        threads.add(new ListedThread(times.get(ValueLayout.JAVA_LONG, THREAD_CREATION_TIME_OFFSET),
                                                     Integer.toUnsignedLong(threadIdentifier),
                                                     information.get(ValueLayout.ADDRESS, THREAD_INFORMATION_ENVIRONMENT_OFFSET).address()));
                    } finally {
                        this.closeOwnedHandle(thread);
                    }
                }
            } finally {
                this.closeOwnedHandle(snapshot);
            }

            threads.sort(Comparator.comparingLong((ListedThread thread) -> thread.creationTime).thenComparingLong(thread -> thread.identifier));

            return threads.stream().map(thread -> thread.environmentAddress).toList();
        } catch (Throwable failure) {
            throw convertToIOException(failure, "Cannot list target threads");
        }
    }

    /**
     * Checks the in-memory executable identity.
     *
     * @param moduleBase the executable image base
     * @throws IOException if the image cannot be read or is unsupported
     */
    public void checkModule(long moduleBase) throws IOException {
        byte[] header = new byte[EXECUTABLE_HEADER_SIZE];

        this.read(moduleBase, header, 0, header.length);
        checkImage(header);
    }

    /**
     * Reads an unsigned little-endian integer without allocating per read.
     *
     * @param address the target address
     * @param size    the integer width in bytes
     * @return the integer bits
     * @throws IllegalArgumentException if {@code size} is outside {@code 1} through {@code 8}
     * @throws IOException              if the integer cannot be read
     */
    @Override
    public long readInteger(long address, int size) throws IOException {
        if (size < 1 || size > Long.BYTES) {
            throw Exceptions.newIllegalArgumentException("Invalid integer width: {0}", size);
        }

        this.read(address, this.scalarBuffer, 0, size);

        return LittleEndianUtils.readUnsigned(this.scalarBuffer, 0, size);
    }

    /**
     * Reads the entire requested range.
     *
     * @param address     the target address
     * @param destination the destination storage
     * @param offset      the destination offset
     * @param length      the byte count
     * @throws IOException if the handle is closed or the entire range cannot be read
     */
    @Override
    public void read(long address, byte @NotNull [] destination, int offset, int length) throws IOException {
        if (this.closed) {
            throw Exceptions.newIOException("Process handle is closed");
        }

        if (address == 0 || length <= 0 || length > StarCraftLayout.MAXIMUM_READ || offset < 0 || offset > destination.length - length) {
            throw Exceptions.newIOException("Invalid process read range");
        }

        if (length > this.transferBuffer.byteSize()) {
            long capacity = Math.min(StarCraftLayout.MAXIMUM_READ, Math.max(length, this.transferBuffer.byteSize() * BUFFER_GROWTH_FACTOR));

            this.transferBuffer = this.arena.allocate(capacity);
        }

        try {
            int success = (int) this.readProcessMemory.invokeExact(this.callState, this.process,
                                                                   MemorySegment.ofAddress(address),
                                                                   this.transferBuffer, (long) length, this.transferred);

            if (success == 0 || this.transferred.get(ValueLayout.JAVA_LONG, 0) != length) {
                int error = this.callState.get(ValueLayout.JAVA_INT, this.errorOffset);

                throw Exceptions.newIOException("Read failed at 0x{0}: Windows error {1}", Long.toHexString(address), error);
            }

            MemorySegment.copy(this.transferBuffer, ValueLayout.JAVA_BYTE, 0, destination, offset, length);
            this.bytesRead += length;
        } catch (Throwable failure) {
            throw convertToIOException(failure, "Process memory call failed");
        }
    }

    private void closeOwnedHandle(@NotNull MemorySegment handle) throws Throwable {
        int success = (int) this.closeHandle.invokeExact(handle);

        if (success == 0) {
            throw Exceptions.newIOException("Cannot close a thread or snapshot handle");
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
     * @implSpec Closes the process handle and then the native storage; the instance is marked closed first.
     */
    @Override
    public void close() throws IOException {
        if (this.closed) {
            return;
        }

        this.closed = true;

        try {
            int success = (int) this.closeHandle.invokeExact(this.process);

            if (success == 0) {
                throw Exceptions.newIOException("Cannot close process handle");
            }
        } catch (Throwable failure) {
            throw convertToIOException(failure, "Cannot close process handle");
        } finally {
            this.arena.close();
        }
    }
}
