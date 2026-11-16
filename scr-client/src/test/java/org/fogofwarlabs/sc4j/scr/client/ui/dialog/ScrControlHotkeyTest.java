package org.fogofwarlabs.sc4j.scr.client.ui.dialog;

import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.scr.client.ui.text.GluText;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrControlType;
import org.fogofwarlabs.sc4j.scr.data.resource.dialog.ScrDialogControlUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Checks that a page control shows an installed hotkey string as the pages do: the latency labels of the options' Network tab without their
 * hotkey character, the hotkey letter marked with code {@code 0x04}, in English and Korean.
 */
@RequiresInstallation
final class ScrControlHotkeyTest {
    private static final @NotNull String LOW_KEY = "bnet119_optionsPanel_network_label_lowLat";
    private static final @NotNull String HIGH_KEY = "bnet119_optionsPanel_network_label_highLat";
    private static final @NotNull String EXTRA_HIGH_KEY = "bnet119_optionsPanel_network_label_extraHighLat";
    private static final @NotNull String LATENCY_KEY = "bnet119_optionsPanel_network_subtitle_defaultLatency";
    private static final @NotNull Function<String, String> NO_CONTROL_TEXTS = content -> "";
    private static final int CONTROL_ID = 1;
    private static final int CONTROL_SIZE = 10;

    private static @NotNull ScrControl createPageControl(@NotNull ScrControlType type, @NotNull String text) {
        DialogControl definition = ScrDialogControlUtils.createControl(type, CONTROL_ID, 0, 0, CONTROL_SIZE, CONTROL_SIZE,
                                                                       Set.of("WS_VISIBLE", WebPanelStyleUtils.FLAG), "", List.of());
        ScrControl control = new ScrControl(definition);

        control.setReplacedText(text);

        return control;
    }

    @Test
    void latency_labels_should_drop_the_hotkey_character_and_keep_the_marked_letter() throws IOException {
        Path installationPath = ExternalInput.INSTALLATION.getPath();

        Assertions.assertNotNull(installationPath);

        try (CascStorage storage = CascStorage.open(installationPath)) {
            GluText english = GluText.load(storage, "enUS");
            GluText korean = GluText.load(storage, "koKR");

            Assertions.assertEquals("\u0004L\u0001ow", getDrawnString(english, LOW_KEY));
            Assertions.assertEquals("\u0004H\u0001igh", getDrawnString(english, HIGH_KEY));
            Assertions.assertEquals("\u0004E\u0001xtra High", getDrawnString(english, EXTRA_HIGH_KEY));
            Assertions.assertEquals("낮음 (\u0004L\u0001)", getDrawnString(korean, LOW_KEY));
            Assertions.assertEquals("아주 높음 (\u0004E\u0001)", getDrawnString(korean, EXTRA_HIGH_KEY));

            // The label takes its hotkey on the page; a label without a hotkey string is shown whole and takes none.
            Assertions.assertTrue(createPageControl(ScrControlType.RADIOBUTTON, english.get(EXTRA_HIGH_KEY)).isHotkey('e', NO_CONTROL_TEXTS));
            Assertions.assertEquals(english.get(LATENCY_KEY), getDrawnString(english, LATENCY_KEY));
            Assertions.assertFalse(createPageControl(ScrControlType.LTEXT, english.get(LATENCY_KEY)).isHotkey('l', NO_CONTROL_TEXTS));
        }
    }

    private static @NotNull String getDrawnString(@NotNull GluText strings, @NotNull String key) {
        return createPageControl(ScrControlType.RADIOBUTTON, strings.get(key)).getDrawnString(NO_CONTROL_TEXTS);
    }
}
