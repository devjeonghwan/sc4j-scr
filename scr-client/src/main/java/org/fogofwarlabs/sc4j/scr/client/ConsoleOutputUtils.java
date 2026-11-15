package org.fogofwarlabs.sc4j.scr.client;

import org.fogofwarlabs.sc4j.scr.client.configuration.HostPlatformUtils;
import org.jetbrains.annotations.NotNull;

import java.io.Console;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Installs UTF-8 standard streams on Windows and restores a changed console code page at shutdown. Other systems retain their default streams.
 */
final class ConsoleOutputUtils {
    /**
     * The UTF-8 code page.
     */
    private static final int UTF8_CODE_PAGE = 65001;
    /**
     * The system library of the console code-page functions.
     */
    private static final @NotNull String KERNEL_LIBRARY = "kernel32";
    /**
     * The function that reads the console output code page.
     */
    private static final @NotNull String GET_CODE_PAGE_FUNCTION = "GetConsoleOutputCP";
    /**
     * The function that sets the console output code page.
     */
    private static final @NotNull String SET_CODE_PAGE_FUNCTION = "SetConsoleOutputCP";
    /**
     * The name of the shutdown thread that restores the previous code page.
     */
    private static final @NotNull String RESTORE_THREAD_NAME = "console-code-page";


    /**
     * Installs UTF-8 standard output and error where the console can show it.
     */
    static void install() {
        if (!HostPlatformUtils.isWindows()) {
            return;
        }

        Console console = System.console();

        if (console != null && console.isTerminal() && !useUtf8CodePage()) {
            return;
        }

        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    }

    /**
     * Attempts to install UTF-8 console output and restore its previous code page at shutdown. Native access must be enabled for this module to bind
     * the system code-page functions.
     *
     * @return {@code true} if the console output code page is UTF-8, either already or after a successful switch, {@code false} otherwise
     */
    @SuppressWarnings("restricted")
    private static boolean useUtf8CodePage() {
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel = SymbolLookup.libraryLookup(KERNEL_LIBRARY, Arena.global());
            Optional<MemorySegment> getterSymbol = kernel.find(GET_CODE_PAGE_FUNCTION);
            Optional<MemorySegment> setterSymbol = kernel.find(SET_CODE_PAGE_FUNCTION);

            if (getterSymbol.isEmpty() || setterSymbol.isEmpty()) {
                return false;
            }

            MethodHandle codePageGetter = linker.downcallHandle(getterSymbol.get(), FunctionDescriptor.of(ValueLayout.JAVA_INT));
            MethodHandle codePageSetter = linker.downcallHandle(setterSymbol.get(),
                                                                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            int previous = (int) codePageGetter.invokeExact();

            if (previous == UTF8_CODE_PAGE) {
                return true;
            }

            if ((int) codePageSetter.invokeExact(UTF8_CODE_PAGE) == 0) {
                return false;
            }

            if (previous != 0) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> restore(codePageSetter, previous), RESTORE_THREAD_NAME));
            }

            return true;
        } catch (Throwable exception) {
            return false;
        }
    }

    /**
     * Restores the console code page after flushing the standard streams; a failure is ignored.
     *
     * @param codePageSetter the bound code-page setter
     * @param codePage       the code page to restore
     */
    private static void restore(@NotNull MethodHandle codePageSetter, int codePage) {
        System.out.flush();
        System.err.flush();

        try {
            int ignored = (int) codePageSetter.invokeExact(codePage);
        } catch (Throwable exception) {
            // The console is gone; nothing to restore.
        }
    }

    /**
     * Creates a {@link ConsoleOutputUtils} instance.
     */
    private ConsoleOutputUtils() {
    }
}
