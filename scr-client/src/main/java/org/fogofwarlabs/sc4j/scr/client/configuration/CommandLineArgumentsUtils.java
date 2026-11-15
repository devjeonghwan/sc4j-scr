package org.fogofwarlabs.sc4j.scr.client.configuration;

import org.jetbrains.annotations.NotNull;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Recovers the client's launch arguments as Unicode on Windows. The {@code java} launcher receives its arguments in the ANSI code page, so characters
 * that code page cannot hold, for example Korean in a map or replay path, arrive as {@code ?}. The process's real command line
 * ({@code GetCommandLineW}) is split the same way the runtime does ({@code CommandLineToArgvW}); its last entries are the client's arguments, after
 * the launcher, its options and the main class. Each recovered argument replaces the given one only when the two differ solely where the ANSI code
 * page could not represent the character, so launcher expansions (argument files, environment options) never change an argument.
 */
public final class CommandLineArgumentsUtils {
    /**
     * What the ANSI conversion writes for a character it cannot represent.
     */
    private static final char REPLACEMENT = '?';

    private static final @NotNull String JNU_ENCODING_PROPERTY = "sun.jnu.encoding";
    private static final @NotNull String NATIVE_ENCODING_PROPERTY = "native.encoding";

    private static final @NotNull String KERNEL_LIBRARY = "kernel32";
    private static final @NotNull String SHELL_LIBRARY = "shell32";
    private static final @NotNull String GET_COMMAND_LINE_FUNCTION = "GetCommandLineW";
    private static final @NotNull String LOCAL_FREE_FUNCTION = "LocalFree";
    private static final @NotNull String SPLIT_COMMAND_LINE_FUNCTION = "CommandLineToArgvW";

    /**
     * Returns the launch arguments with ANSI replacements undone where the Unicode command line has the real text.
     *
     * @param arguments the arguments the launcher passed to {@code main}
     * @return the recovered arguments, or {@code arguments} itself when nothing needs or can be recovered
     */
    public static @NotNull String @NotNull [] recover(@NotNull String @NotNull [] arguments) {
        if (!HostPlatformUtils.isWindows()) {
            return arguments;
        }

        if (Arrays.stream(arguments).noneMatch(argument -> argument.indexOf(REPLACEMENT) >= 0)) {
            return arguments;
        }

        List<String> commandLine = readCommandLine();

        if (commandLine.size() < arguments.length) {
            return arguments;
        }

        Charset ansiCharset = getAnsiCharset();
        String[] recovered = arguments.clone();
        int offset = commandLine.size() - arguments.length;

        for (int index = 0; index < arguments.length; index++) {
            String unicodeArgument = commandLine.get(offset + index);

            if (matchesAnsi(arguments[index], unicodeArgument, ansiCharset)) {
                recovered[index] = unicodeArgument;
            }
        }

        return recovered;
    }

    /**
     * Checks equal UTF-16 lengths and accepts differences only where the ANSI encoder cannot represent the Unicode character.
     *
     * @param launcherArgument the launcher argument
     * @param unicodeArgument  the Unicode argument
     * @param ansiCharset      the ANSI character set
     * @return {@code true} if every difference is an ANSI replacement, {@code false} otherwise
     */
    static boolean matchesAnsi(@NotNull String launcherArgument, @NotNull String unicodeArgument, @NotNull Charset ansiCharset) {
        if (launcherArgument.length() != unicodeArgument.length()) {
            return false;
        }

        CharsetEncoder encoder = ansiCharset.newEncoder();

        for (int index = 0; index < launcherArgument.length(); index++) {
            char original = unicodeArgument.charAt(index);

            if (launcherArgument.charAt(index) != original && encoder.canEncode(original)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Finds the launcher character set, falling back when the configured character set is unavailable.
     *
     * @return the ANSI character set used to compare argument recovery
     */
    private static @NotNull Charset getAnsiCharset() {
        try {
            return Charset.forName(System.getProperty(JNU_ENCODING_PROPERTY, System.getProperty(NATIVE_ENCODING_PROPERTY, "")));
        } catch (IllegalArgumentException exception) {
            return StandardCharsets.US_ASCII;
        }
    }

    /**
     * Reads the Windows Unicode argument vector and releases its native allocation. The client starts with native access enabled for library lookup,
     * downcall bindings and segment reinterpretation.
     *
     * @return the split arguments, or an empty list when native recovery fails
     */
    @SuppressWarnings("restricted")
    private static @NotNull List<String> readCommandLine() {
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel = SymbolLookup.libraryLookup(KERNEL_LIBRARY, arena);
            SymbolLookup shell = SymbolLookup.libraryLookup(SHELL_LIBRARY, arena);
            Optional<MemorySegment> getCommandLine = kernel.find(GET_COMMAND_LINE_FUNCTION);
            Optional<MemorySegment> localFree = kernel.find(LOCAL_FREE_FUNCTION);
            Optional<MemorySegment> split = shell.find(SPLIT_COMMAND_LINE_FUNCTION);

            if (getCommandLine.isEmpty() || localFree.isEmpty() || split.isEmpty()) {
                return List.of();
            }

            MethodHandle getCommandLineHandle = linker.downcallHandle(getCommandLine.get(), FunctionDescriptor.of(ValueLayout.ADDRESS));
            MethodHandle splitHandle = linker.downcallHandle(split.get(),
                                                             FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MethodHandle freeHandle = linker.downcallHandle(localFree.get(), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

            MemorySegment line = (MemorySegment) getCommandLineHandle.invokeExact();
            MemorySegment count = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment vector = (MemorySegment) splitHandle.invokeExact(line, count);

            if (vector.equals(MemorySegment.NULL)) {
                return List.of();
            }

            try {
                int size = count.get(ValueLayout.JAVA_INT, 0);
                MemorySegment entries = vector.reinterpret(size * ValueLayout.ADDRESS.byteSize());
                String[] result = new String[size];

                for (int index = 0; index < size; index++) {
                    MemorySegment entry = entries.getAtIndex(ValueLayout.ADDRESS, index).reinterpret(Long.MAX_VALUE);

                    result[index] = entry.getString(0, StandardCharsets.UTF_16LE);
                }

                return List.of(result);
            } finally {
                MemorySegment ignored = (MemorySegment) freeHandle.invokeExact(vector);
            }
        } catch (Throwable exception) {
            return List.of();
        }
    }

    /**
     * Creates a {@link CommandLineArgumentsUtils} instance.
     */
    private CommandLineArgumentsUtils() {
    }
}
