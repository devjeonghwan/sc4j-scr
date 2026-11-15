package org.fogofwarlabs.sc4j.scr.client.ui.console;

import org.fogofwarlabs.sc4j.engine.client.display.DisplayScale;
import org.fogofwarlabs.sc4j.engine.client.display.UiSurface;
import org.fogofwarlabs.sc4j.scr.client.cursor.GameCursor;
import org.fogofwarlabs.sc4j.scr.client.ui.input.WorldInterface;
import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.client.world.GameViewport;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.fogofwarlabs.sc4j.scr.render.ui.InterfaceAssets;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Tests the interface data boundaries: {@code stat_txt} strings, dialog layouts and the console layout mapping.
 */
final class InterfaceDataTest {
    private static final @NotNull String STATUS_BUTTONS = """
                                                          ﻿{
                                                            "type": "DIALOG",
                                                            "name": "StatBtn",
                                                            "rect": [496,354,144,126],
                                                            "style": [],
                                                            "children": [
                                                              {"type": "PUSHBUTTON", "content": "statbtnt-1", "id": 1, "rect": [9,4,36,34], "flags": ["WS_VISIBLE","WS_TABSTOP"], "children": null},
                                                              {"type": "PUSHBUTTON", "content": "statbtnt-9", "id": 9, "rect": [101,84,36,34], "flags": [], "children": null}
                                                            ]
                                                          }
                                                          """;

    /**
     * Control {@code 1} of {@link #STATUS_BUTTONS} in interface coordinates: the dialog origin {@code (496, 354)} plus the
     * control origin {@code (9, 4)}.
     */
    private static final int FIRST_BUTTON_X = 505;
    private static final int FIRST_BUTTON_Y = 358;
    private static final int BUTTON_WIDTH = 36;
    /**
     * The left of control {@code 9} in interface coordinates.
     */
    private static final int NINTH_BUTTON_X = 597;
    private static final int FIRST_BUTTON = 1;
    private static final int NINTH_BUTTON = 9;
    private static final int MISSING_BUTTON = 2;
    /**
     * The control index of a point outside every control.
     */
    private static final int NO_CONTROL = -1;
    /**
     * The controls of {@link #STATUS_BUTTONS}.
     */
    private static final int STATUS_BUTTON_CONTROLS = 2;
    /**
     * The tileset of the installed interface assets (badlands).
     */
    private static final int FIRST_TILESET = 0;
    /**
     * A {@code 1280x960} window: twice the interface space.
     */
    private static final int DOUBLE_WIDTH = 1280;
    private static final int DOUBLE_HEIGHT = 960;
    private static final float DOUBLE_SCALE = 2;
    /**
     * A {@code 960x720} window: one and a half times the interface space.
     */
    private static final int THREE_HALVES_WIDTH = 960;
    private static final int THREE_HALVES_HEIGHT = 720;
    private static final float THREE_HALVES_SCALE = 1.5f;
    /**
     * A {@code 1600x480} window: the interface space centered horizontally, {@code 480} pixels right of the window's left.
     */
    private static final int WIDE_WIDTH = 1600;
    private static final int WIDE_HEIGHT = 480;
    private static final float WIDE_CENTERING = 480;
    private static final double COORDINATE_TOLERANCE = 0.001;
    /**
     * The entries of the parsed {@code stat_txt} fixture: a unit name, a button string (button type {@code 7}, hotkey
     * {@code m}) and a requirement text; strings {@code 0} and past the last are empty.
     */
    private static final int FIXTURE_ENTRIES = 3;
    private static final int FIXTURE_BUTTON_STRING = 2;
    private static final int FIXTURE_REQUIREMENT_STRING = 3;
    private static final int FIXTURE_BUTTON_TYPE = 7;
    private static final int NO_STRING = 0;
    private static final int PAST_LAST_STRING = 4;
    /**
     * The installed English {@code stat_txt}: its entry count, the Move and Pause button strings and the second rank.
     */
    private static final int INSTALLED_ENTRIES = 1551;
    private static final int MOVE_STRING = 664;
    private static final int PAUSE_STRING = 1543;
    private static final int SECOND_RANK = 2;
    private static final int ZERG_RACE = 0;
    private static final int TERRAN_RACE = 1;
    /**
     * The installed left edges and tops of control {@code 1} of the console dialogs.
     */
    private static final int ZERG_COMMAND_CARD_X = 500;
    private static final int TERRAN_COMMAND_CARD_X = 505;
    private static final int TERRAN_MINIMAP_X = 6;
    private static final int TERRAN_MINIMAP_Y = 348;
    private static final int TERRAN_SELECTION_PANEL_X = 168;
    private static final int FIRST_CONTROL = 1;
    /**
     * The installed cursors: eight scroll directions, the frame counts of {@code arrow} and {@code MagG} and the hot spot of
     * {@code ScrollR} (right edge, vertical middle).
     */
    private static final int SCROLL_DIRECTIONS = 8;
    private static final int ARROW_FRAMES = 5;
    private static final int MAGNIFIER_FRAMES = 14;
    private static final float @NotNull [] SCROLL_RIGHT_HOT_SPOT = {1, 0.5f};
    /**
     * The size of the installed SD command button frame of every race.
     */
    private static final int BUTTON_FRAME_WIDTH = 36;
    private static final int BUTTON_FRAME_HEIGHT = 35;
    private static final int RACES = 3;

    @Test
    void parse_should_decode_unit_names_hotkeys_and_button_text() throws IOException {
        String json = "[{\"Value\": \"Terran Marine\\\\u0000*\\\\u0000Ground Units\"}," +
                      "{\"Value\": \"m\\\\u0007\\\\u0003M\\\\u0001ove\"}," +
                      "{\"Value\": \"Comsat Station Requires:\\\\n   Academy\"}]";
        StatText text = StatText.parse(json.getBytes(StandardCharsets.UTF_8));

        Assertions.assertEquals(FIXTURE_ENTRIES, text.getCount());
        Assertions.assertEquals("Terran Marine", text.getUnitName(0));
        Assertions.assertEquals('m', text.getHotkey(FIXTURE_BUTTON_STRING));
        Assertions.assertEquals(FIXTURE_BUTTON_TYPE, text.getButtonType(FIXTURE_BUTTON_STRING));
        Assertions.assertEquals("Move", text.getButtonText(FIXTURE_BUTTON_STRING));
        Assertions.assertEquals("Comsat Station Requires:\n   Academy", text.getPlainText(FIXTURE_REQUIREMENT_STRING));
        Assertions.assertEquals("", text.getString(NO_STRING));
        Assertions.assertEquals("", text.getString(PAST_LAST_STRING));
    }

    @Test
    void parse_should_resolve_dialog_controls_to_interface_coordinates() throws IOException {
        DialogLayout dialog = DialogLayout.parse(STATUS_BUTTONS.getBytes(StandardCharsets.UTF_8));

        Assertions.assertEquals("StatBtn", dialog.getName());
        Assertions.assertEquals(STATUS_BUTTON_CONTROLS, dialog.getControls().size());

        DialogLayout.Control first = dialog.getControl(FIRST_BUTTON);

        Assertions.assertEquals(FIRST_BUTTON_X, first.getX());
        Assertions.assertEquals(FIRST_BUTTON_Y, first.getY());
        Assertions.assertEquals(BUTTON_WIDTH, first.getWidth());
        Assertions.assertEquals(Set.of("WS_VISIBLE", "WS_TABSTOP"), first.getFlags());
        Assertions.assertEquals(NINTH_BUTTON_X, dialog.getControl(NINTH_BUTTON).getX());
        Assertions.assertThrows(IllegalArgumentException.class, () -> dialog.getControl(MISSING_BUTTON));
    }

    @Test
    void console_layout_maps_interface_space_independently_of_resolution() throws IOException {
        DialogLayout buttons = DialogLayout.parse(STATUS_BUTTONS.getBytes(StandardCharsets.UTF_8));
        ConsoleLayout layout = new ConsoleLayout(buttons, buttons, buttons, buttons, buttons, buttons);
        GameViewport viewport = new GameViewport();

        // The same framebuffer at one pixel per logical unit and at display scales 2 and 1.5 (640x480 logical units): the console is placed
        // and hit in framebuffer pixels alike.
        UiSurface[] doubleSurfaces = {UiSurface.of(DOUBLE_WIDTH, DOUBLE_HEIGHT, DisplayScale.ONE),
                                      UiSurface.of(DOUBLE_WIDTH, DOUBLE_HEIGHT, DisplayScale.of(DOUBLE_SCALE, 1))};

        for (UiSurface surface : doubleSurfaces) {
            viewport.setSurface(surface);
            layout.setViewport(viewport);

            Assertions.assertEquals(DOUBLE_SCALE, layout.getScale(), surface.toString());
            Assertions.assertEquals(FIRST_BUTTON_X * DOUBLE_SCALE, layout.toScreenX(FIRST_BUTTON_X), surface.toString());
            Assertions.assertEquals(FIRST_BUTTON,
                                    layout.findControl(layout.getDialog(buttons).getDialog(),
                                                       buttons,
                                                       FIRST_BUTTON_X * DOUBLE_SCALE + 1,
                                                       FIRST_BUTTON_Y * DOUBLE_SCALE + 1),
                                    surface.toString());
        }

        viewport.setSurface(UiSurface.of(THREE_HALVES_WIDTH, THREE_HALVES_HEIGHT, DisplayScale.of(THREE_HALVES_SCALE, 1)));
        layout.setViewport(viewport);

        Assertions.assertEquals(THREE_HALVES_SCALE, layout.getScale());
        Assertions.assertEquals(FIRST_BUTTON,
                                layout.findControl(layout.getDialog(buttons).getDialog(),
                                                   buttons,
                                                   FIRST_BUTTON_X * THREE_HALVES_SCALE + 1,
                                                   FIRST_BUTTON_Y * THREE_HALVES_SCALE + 1));

        viewport.setSurface(UiSurface.of(WIDE_WIDTH, WIDE_HEIGHT, DisplayScale.ONE));
        layout.setViewport(viewport);

        Assertions.assertEquals(1, layout.getScale());
        Assertions.assertEquals(WIDE_CENTERING + FIRST_BUTTON_X, layout.toScreenX(FIRST_BUTTON_X));
        Assertions.assertEquals(FIRST_BUTTON_Y, layout.toScreenY(FIRST_BUTTON_Y));
        Assertions.assertEquals(FIRST_BUTTON_X, layout.toInterfaceX(layout.toScreenX(FIRST_BUTTON_X)), COORDINATE_TOLERANCE);
        Assertions.assertEquals(NO_CONTROL, layout.findControl(layout.getDialog(buttons).getDialog(), buttons, 0, 0));
        Assertions.assertTrue(layout.isOnConsole(layout.toScreenY(ConsoleLayout.CONSOLE_TOP)));
    }

    @RequiresInstallation
    @Test
    void installed_interface_definitions_load_stat_text_and_console_layouts() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            StatText text = StatText.load(storage, "enUS");

            Assertions.assertEquals(INSTALLED_ENTRIES, text.getCount());
            Assertions.assertEquals("Move", text.getButtonText(MOVE_STRING));
            Assertions.assertEquals("Pause", text.getButtonText(PAUSE_STRING));
            Assertions.assertEquals("Private", text.getRank(SECOND_RANK));

            ConsoleLayout zerg = ConsoleLayout.load(storage, ZERG_RACE);

            Assertions.assertEquals(ZERG_COMMAND_CARD_X, zerg.getCommandButtons().getControl(FIRST_CONTROL).getX());

            ConsoleLayout terran = ConsoleLayout.load(storage, TERRAN_RACE);

            Assertions.assertEquals(TERRAN_COMMAND_CARD_X, terran.getCommandButtons().getControl(FIRST_CONTROL).getX());
            Assertions.assertEquals(TERRAN_MINIMAP_X, terran.getMinimap().getControl(FIRST_CONTROL).getX());
            Assertions.assertEquals(TERRAN_MINIMAP_Y, terran.getMinimap().getControl(FIRST_CONTROL).getY());
            Assertions.assertEquals(TERRAN_SELECTION_PANEL_X, terran.getSelectionPanel().getControl(FIRST_CONTROL).getX());
        }
    }

    @RequiresInstallation
    @Test
    void interface_assets_load_cursors_and_button_frames_for_every_state() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            InterfaceAssets assets = InterfaceAssets.load(ScrResourceProvider.borrow(storage), FIRST_TILESET);

            for (WorldInterface.Cursor cursor : WorldInterface.Cursor.values()) {
                for (int direction = 0; direction < (cursor == WorldInterface.Cursor.SCROLL ? SCROLL_DIRECTIONS : 1); direction++) {
                    String name = ConsoleView.getCursorName(cursor, direction);
                    int frames = assets.getCursorFrameCount(name);

                    Assertions.assertTrue(frames > 0, name);

                    for (int frame = 0; frame < frames; frame++) {
                        Assertions.assertTrue(assets.decodeCursor(name, frame).getWidth() > 0, name);
                    }
                }
            }

            Assertions.assertEquals(ARROW_FRAMES, assets.getCursorFrameCount("arrow"));
            Assertions.assertEquals(MAGNIFIER_FRAMES, assets.getCursorFrameCount("MagG"));
            Assertions.assertArrayEquals(SCROLL_RIGHT_HOT_SPOT, GameCursor.getHotSpot("ScrollR"));

            for (int race = 0; race < RACES; race++) {
                Assertions.assertEquals(BUTTON_FRAME_WIDTH, assets.decodeButtonFrame(race, InterfaceAssets.BUTTON_UP).getWidth());
                Assertions.assertEquals(BUTTON_FRAME_HEIGHT, assets.decodeButtonFrame(race, InterfaceAssets.BUTTON_UP).getHeight());
            }
        }
    }
}
