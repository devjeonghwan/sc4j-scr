package org.fogofwarlabs.sc4j.scr.client.glue.menu;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks the credits entries of the page script ({@link CreditsScreen#parseEntries}) and the client's own credits.
 */
final class CreditsScreenTest {
    private static final int TITLE_STYLE = 1;
    private static final int SECTION_STYLE = 2;
    private static final int ROLE_STYLE = 3;
    private static final int NAME_STYLE = 4;
    private static final int GAP_STYLE = 5;
    private static final int LONG_LITERAL_LENGTH = 1_000_000;
    /**
     * The positions of the client's credits and of the first page entry in the prepend test.
     */
    private static final int CLIENT_HEADING_INDEX = 0;
    private static final int CLIENT_NAME_INDEX = 1;
    private static final int CLIENT_GAPS_START = 2;
    private static final int CLIENT_CREDITS_END = 4;
    private static final int INSTALLED_TITLE_INDEX = 2;
    private static final @NotNull String INSTALLED_PAGE_SCRIPT = "webui/dist/CreditsPanel/CreditsPanel.js";

    @Test
    void parse_entries_should_read_credits_entries_from_page_script() {
        List<String> names = new ArrayList<>();
        List<Integer> styles = new ArrayList<>();

        CreditsScreen.parseEntries("x=[{name:\"\",style:5},{name:\"StarCraft\",style:1},{name:'Say \"hi\"',style:4},{name:\"a\\u00e9\",style:3}];",
                                   names, styles);

        Assertions.assertEquals(List.of("", "StarCraft", "Say \"hi\"", "aé"), names);
        Assertions.assertEquals(List.of(GAP_STYLE, TITLE_STYLE, NAME_STYLE, ROLE_STYLE), styles);
    }

    @Test
    void parse_entries_should_read_quoted_entries_with_escapes_and_skip_malformed() {
        List<String> names = new ArrayList<>();
        List<Integer> styles = new ArrayList<>();

        CreditsScreen.parseEntries("e.exports=[{name:\"StarCraft\",style:1},{name:'Rock \\'n\\' Roll',style:4},{name:\"\\u00e9\",style:3}," +
                                   "{name:\"\",style:5},{name:x,style:2},{name:\"broken\",style:}]",
                                   names, styles);

        Assertions.assertEquals(List.of("StarCraft", "Rock 'n' Roll", "\u00e9", ""), names);
        Assertions.assertEquals(List.of(TITLE_STYLE, NAME_STYLE, ROLE_STYLE, GAP_STYLE), styles);
    }

    /**
     * Checks that a long string literal after an entry start is scanned without recursion and does not overflow the stack.
     */
    @Test
    void parse_entries_should_scan_long_literal_without_recursion() {
        String script = "{name:\"" + "a".repeat(LONG_LITERAL_LENGTH) + "\"};" + "{name:\"Allen Adham\",style:4}";
        List<String> names = new ArrayList<>();
        List<Integer> styles = new ArrayList<>();

        CreditsScreen.parseEntries(script, names, styles);

        Assertions.assertEquals(List.of("Allen Adham"), names);
        Assertions.assertEquals(List.of(NAME_STYLE), styles);
    }

    @Test
    void prepend_client_credits_should_come_before_page_entries() {
        List<String> names = new ArrayList<>(List.of("Thanks to our family and friends.", "", ""));
        List<Integer> styles = new ArrayList<>(List.of(NAME_STYLE, GAP_STYLE, GAP_STYLE));

        CreditsScreen.prependClientCredits(names, styles);

        Assertions.assertEquals("SC4J (Simulation Core for Java) Developer", names.get(CLIENT_HEADING_INDEX));
        Assertions.assertEquals(SECTION_STYLE, styles.get(CLIENT_HEADING_INDEX));
        Assertions.assertEquals("박정환 (Park Jeonghwan)", names.get(CLIENT_NAME_INDEX));
        Assertions.assertEquals(NAME_STYLE, styles.get(CLIENT_NAME_INDEX));
        Assertions.assertEquals(List.of(GAP_STYLE, GAP_STYLE), styles.subList(CLIENT_GAPS_START, CLIENT_CREDITS_END));
        Assertions.assertEquals("Thanks to our family and friends.", names.get(CLIENT_CREDITS_END));
    }

    @RequiresInstallation
    @Test
    void parse_entries_should_read_installed_credits_page() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            String script = new String(storage.readFile(INSTALLED_PAGE_SCRIPT), StandardCharsets.UTF_8);
            List<String> names = new ArrayList<>();
            List<Integer> styles = new ArrayList<>();

            CreditsScreen.parseEntries(script, names, styles);

            Assertions.assertEquals("StarCraft", names.get(INSTALLED_TITLE_INDEX));
            Assertions.assertTrue(names.contains("SPECIAL THANKS"));
            Assertions.assertEquals(GAP_STYLE, styles.getLast());
        }
    }
}
