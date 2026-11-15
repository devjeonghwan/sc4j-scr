package org.fogofwarlabs.sc4j.scr.client.ui.menu;

import org.fogofwarlabs.sc4j.scr.client.ui.text.GluText;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

/**
 * Checks the installed Keystroke Help rows with their blank gap rows and the StarCraft Tips texts loaded by number.
 */
@RequiresInstallation
final class KeystrokeHelpTest {
    private static final int KEYSTROKE_ROWS = 52;

    @Test
    void keystroke_help_has_fifty_two_rows_with_blank_gaps_and_tips_load_by_number() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            GluText table = GluText.load(storage, "enUS", KeystrokeHelp.TABLE);
            List<String> rows = new KeystrokeHelp(table).getRows();

            Assertions.assertEquals(KEYSTROKE_ROWS, rows.size());
            Assertions.assertEquals(table.get("STR_HELP_1"), rows.getFirst());
            Assertions.assertEquals(" ", rows.getLast(), "STR_HELP_LAST is the placeholder too");
            Assertions.assertEquals(" ", rows.get(2), "STR_HELP_1-2 is the placeholder: a gap row");
            Assertions.assertEquals(table.get("STR_HELP_1-3"), rows.get(3));

            GluText tips = GluText.load(storage, "enUS", "tips_txt.json");

            Assertions.assertEquals(tips.get("STR_TIP_TUT1"), tips.getByNumber(GameTips.FIRST_TIP), "tip 1 is the first entry");
            Assertions.assertFalse(tips.getByNumber(GameTips.LAST_EXPANSION_TIP).isEmpty());
        }
    }
}
