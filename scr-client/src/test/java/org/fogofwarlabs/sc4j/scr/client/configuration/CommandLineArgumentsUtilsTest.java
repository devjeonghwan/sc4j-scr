package org.fogofwarlabs.sc4j.scr.client.configuration;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

/**
 * Verifies that a Unicode argument replaces a launcher argument only where the ANSI code page could not encode a character.
 */
final class CommandLineArgumentsUtilsTest {
    private static final @NotNull Charset WESTERN = Charset.forName("windows-1252");
    private static final @NotNull String KOREAN_PATH = "D:/maps/(2)\uBCF5\uC11C.scm";

    @Test
    void matches_ansi_should_accept_replacements_only_where_code_page_cannot_encode() {
        Assertions.assertTrue(CommandLineArgumentsUtils.matchesAnsi("D:/maps/(2)??.scm", KOREAN_PATH, WESTERN));
        Assertions.assertTrue(CommandLineArgumentsUtils.matchesAnsi("caf\u00E9", "caf\u00E9", WESTERN));
    }

    @Test
    void matches_ansi_should_reject_arguments_that_differ_elsewhere() {
        Assertions.assertFalse(CommandLineArgumentsUtils.matchesAnsi("D:/maps/(3)??.scm", KOREAN_PATH, WESTERN));
        Assertions.assertFalse(CommandLineArgumentsUtils.matchesAnsi("D:/maps/(2)?.scm", KOREAN_PATH, WESTERN));
        Assertions.assertFalse(CommandLineArgumentsUtils.matchesAnsi("caf?", "caf\u00E9", WESTERN));
    }
}
