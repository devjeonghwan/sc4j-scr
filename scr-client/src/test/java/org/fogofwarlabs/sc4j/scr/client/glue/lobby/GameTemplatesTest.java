package org.fogofwarlabs.sc4j.scr.client.glue.lobby;

import org.fogofwarlabs.sc4j.scr.client.ui.text.GluText;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Checks the localized game type names of the installed string table.
 */
@RequiresInstallation
final class GameTemplatesTest {
    private static final @NotNull String LOCALE = "enUS";
    private static final int MELEE = 2;
    private static final @NotNull String MELEE_NAME = "Melee";
    private static final int TOP_VS_BOTTOM = 15;
    private static final @NotNull String TOP_VS_BOTTOM_NAME = "Top vs Bottom";
    /**
     * Game type ids without an SDK type: one below Melee, and the one between Team Capture The Flag and Top vs Bottom.
     */
    private static final int BELOW_MELEE = 1;
    private static final int UNUSED = 14;

    @Test
    void get_type_name_should_name_top_vs_bottom_and_be_empty_without_sdk_type() throws IOException {
        Path installationPath = ExternalInput.INSTALLATION.getPath();

        Assertions.assertNotNull(installationPath);

        try (CascStorage storage = CascStorage.open(installationPath)) {
            GluText strings = GluText.load(storage, LOCALE);

            Assertions.assertEquals(MELEE_NAME, GameTemplates.getTypeName(strings, MELEE));
            Assertions.assertEquals(TOP_VS_BOTTOM_NAME, GameTemplates.getTypeName(strings, TOP_VS_BOTTOM));
            Assertions.assertEquals("", GameTemplates.getTypeName(strings, BELOW_MELEE));
            Assertions.assertEquals("", GameTemplates.getTypeName(strings, UNUSED));
        }
    }
}
