package org.fogofwarlabs.screcorder.capture.windows;

import com.sun.management.HotSpotDiagnosticMXBean;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Verifies process memory reads and termination through actual Windows process handles.
 */
@EnabledOnOs(OS.WINDOWS)
final class WindowsProcessTest {
    private static final int PROCESS_EXIT_TIMEOUT_SECONDS = 5;
    private static final int POLICY_FAILURE_EXIT_STATUS = 1;
    private static final int RELEASE_BYTE = 1;
    /**
     * A non-zero address and a byte count that pass the range check, read after the handle is closed.
     */
    private static final long CLOSED_READ_ADDRESS = 1;
    private static final int CLOSED_READ_SIZE = 8;
    private static final @NotNull String CLOSED_MESSAGE = "Process handle is closed";
    /**
     * The offset of the self pointer that every thread environment block holds.
     */
    private static final int ENVIRONMENT_SELF_OFFSET = 0x30;

    /**
     * Keeps a child JVM alive until its parent releases standard input.
     */
    public static final class WaitingProcess {
        /**
         * Waits for the parent's release byte.
         *
         * @param arguments the unused command arguments
         * @throws IOException if the owned temporary storage or standard input cannot be accessed
         */
        public static void main(@NotNull String @NotNull [] arguments) throws IOException {
            Path expected = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
            Path temporary = Files.createTempFile("sc4j-child-", ".tmp");
            boolean valid = false;

            try {
                valid = temporary.toRealPath().getParent().equals(expected) &&
                        ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class).getVMOption("UsePerfData").getValue().equals("false");

                if (valid) {
                    System.in.read();
                }
            } finally {
                Files.deleteIfExists(temporary);
            }

            if (!valid) {
                System.err.println("The child JVM did not inherit the owned startup policy.");
                System.exit(POLICY_FAILURE_EXIT_STATUS);
            }
        }

        private WaitingProcess() {
        }
    }

    @Test
    void read_should_copy_actual_process_memory_and_report_failures() throws IOException {
        byte[] expected = {0, 1, 2, 3, 4, 5, 6, (byte) 255};

        try (Arena memory = Arena.ofConfined(); WindowsProcess process = WindowsProcess.open(ProcessHandle.current().pid())) {
            MemorySegment contents = memory.allocateFrom(ValueLayout.JAVA_BYTE, expected);
            byte[] actual = new byte[expected.length];

            process.read(contents.address(), actual, 0, actual.length);

            Assertions.assertArrayEquals(expected, actual);
            Assertions.assertEquals(expected.length, process.getBytesRead());
            Assertions.assertTrue(process.isAlive());
            Assertions.assertTrue(process.readModuleBase() != 0);
            Assertions.assertThrows(IOException.class, () -> process.read(0, actual, 0, actual.length));
            Assertions.assertThrows(IOException.class, () -> process.read(1, actual, 0, actual.length));
        }
    }

    @Test
    void read_thread_environment_addresses_should_list_the_environment_blocks_of_the_process_threads() throws IOException {
        WindowsProcess process = WindowsProcess.open(ProcessHandle.current().pid());

        try {
            List<Long> environments = process.readThreadEnvironmentAddresses();
            int verifiedCount = 0;

            for (long environment : environments) {
                long self;

                try {
                    self = process.readInteger(environment + ENVIRONMENT_SELF_OFFSET, Long.BYTES);
                } catch (IOException ignored) {
                    // A virtual machine thread that exited after the listing has no readable environment block.
                    continue;
                }

                Assertions.assertEquals(environment, self);
                verifiedCount++;
            }

            Assertions.assertTrue(verifiedCount > 0);
        } finally {
            process.close();
        }

        Assertions.assertThrows(IOException.class, process::readThreadEnvironmentAddresses);
    }

    @Test
    void read_should_report_a_closed_handle() throws IOException {
        WindowsProcess process = WindowsProcess.open(ProcessHandle.current().pid());
        byte[] destination = new byte[CLOSED_READ_SIZE];

        process.close();

        IOException exception = Assertions.assertThrows(IOException.class,
                                                        () -> process.read(CLOSED_READ_ADDRESS, destination, 0, destination.length));

        Assertions.assertEquals(CLOSED_MESSAGE, exception.getMessage());
    }

    @Test
    void is_alive_should_observe_termination_through_the_opened_handle() throws Exception {
        Path executable = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        Path classes = Path.of(WaitingProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Process child = new ProcessBuilder(executable.toString(), "-XX:-UsePerfData",
                                           "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"),
                                           "--class-path", classes.toString(),
                                           WaitingProcess.class.getName()).start();

        try (WindowsProcess target = WindowsProcess.open(child.pid())) {
            Assertions.assertTrue(target.isAlive());

            child.getOutputStream().write(RELEASE_BYTE);
            child.getOutputStream().flush();

            Assertions.assertTrue(child.waitFor(PROCESS_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Assertions.assertEquals(0, child.exitValue());
            Assertions.assertFalse(target.isAlive());
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly().waitFor(PROCESS_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        }
    }
}
