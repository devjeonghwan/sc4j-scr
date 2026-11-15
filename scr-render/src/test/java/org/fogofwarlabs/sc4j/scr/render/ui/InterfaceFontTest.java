package org.fogofwarlabs.sc4j.scr.render.ui;

import org.fogofwarlabs.sc4j.engine.client.text.Font;
import org.fogofwarlabs.sc4j.engine.client.text.TextColorTable;
import org.fogofwarlabs.sc4j.engine.client.text.TextStyle;
import org.fogofwarlabs.sc4j.engine.resource.text.FontFace;
import org.fogofwarlabs.sc4j.engine.resource.text.FontGlyph;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceKeys;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.fogofwarlabs.sc4j.scr.render.HiddenContextScenario;
import org.fogofwarlabs.sc4j.scr.render.OverlayPass;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Checks the text color codes of {@link InterfaceFont}, that a bitmap glyph lands at its offsets with the texels of its
 * atlas rectangle, and that the font as the engine {@link Font} measures and draws as the native paths, in its default style and
 * in a {@link TextStyle} with a code table, the kerning switch, a scale and pixel snapping.
 */
final class InterfaceFontTest {
    /**
     * The system property that enables the GPU check and the message of a skipped check.
     */
    private static final @NotNull String RENDER_SMOKE_PROPERTY = "sc4j.render.smoke";
    private static final @NotNull String RENDER_SMOKE_MESSAGE = "Pass -Psc4j.render.smoke=true to run the GPU check.";
    /**
     * The hidden context, the pen of the drawn glyph and the white color code.
     */
    private static final int SIZE = 64;
    private static final int PEN = 8;
    private static final int WHITE_CODE = 4;
    /**
     * The blending of the white glyph over black may round one step away from the coverage.
     */
    private static final int ROUNDING_TOLERANCE = 1;
    private static final int CHANNEL_MASK = 0xFF;
    private static final @NotNull String @NotNull [] FONTS = {"font8", "font10", "font14", "font16", "font16x"};
    private static final char @NotNull [] CHARACTERS = {'A', 'g', 'W', '1', '.'};
    /**
     * A line with a color code switch and a restore, drawn by the native and the engine path.
     */
    private static final @NotNull String ENGINE_TEXT = "Ag\u0006W\u00011";
    /**
     * The screen pixels per font pixel of the styled draw and the fraction of a pixel the snapped pen is moved by.
     */
    private static final float STYLE_SCALE = 2;
    private static final float PEN_FRACTION = 0.4f;

    /**
     * Creates the game's code colors as the engine's default color table: every interface color code with its color, code {@code 1} restoring.
     */
    private static @NotNull TextColorTable createGameTable() {
        TextColorTable.Builder builder = TextColorTable.builder();

        for (int code = 0; code < InterfaceText.GAME_CODE_COLORS.length; code++) {
            if (InterfaceFont.isColorCode(code)) {
                builder.setColor(code, InterfaceText.GAME_CODE_COLORS[code]);
            }
        }

        return builder.setRestoreCode(1).build();
    }

    private static int @NotNull [] drawText(@NotNull InterfaceRenderer renderer, @NotNull ByteBuffer pixels, @NotNull Runnable draw) {
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        renderer.begin(SIZE, SIZE);
        draw.run();
        renderer.end();
        glFinish();

        return HiddenContextScenario.readColors(pixels, SIZE, SIZE);
    }

    /**
     * Draws one glyph at scale {@code 1} and compares every pixel of the window with the glyph's coverage texel at that place.
     */
    private static void assertGlyph(@NotNull InterfaceRenderer renderer, @NotNull InterfaceFont font, @NotNull FontFace face,
                                    @NotNull FontGlyph glyph, @NotNull ByteBuffer pixels, @NotNull String label) {
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        renderer.begin(SIZE, SIZE);
        font.draw(renderer, String.valueOf((char) glyph.getCodePoint()), PEN, PEN, 1, WHITE_CODE, true);
        renderer.end();
        glFinish();

        int[] colors = HiddenContextScenario.readColors(pixels, SIZE, SIZE);
        int left = PEN + glyph.getOffsetX();
        int top = PEN + glyph.getOffsetY();

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean inside = x >= left && y >= top && x < left + glyph.getWidth() && y < top + glyph.getHeight();
                int expected = inside
                               ? face.getPixels().getValue((glyph.getImageY() + y - top) * face.getImageWidth() + glyph.getImageX() + x - left)
                               : 0;
                int actual = colors[y * SIZE + x] & CHANNEL_MASK;

                Assertions.assertTrue(Math.abs(expected - actual) <= ROUNDING_TOLERANCE,
                                      label + " pixel " + x + "," + y + ": " + actual + " instead of " + expected);
            }
        }
    }

    @Test
    void is_color_code_should_not_accept_alignment_and_control_codes() {
        Assertions.assertFalse(InterfaceFont.isColorCode(0x12));
        Assertions.assertFalse(InterfaceFont.isColorCode(0x13));
        Assertions.assertFalse(InterfaceFont.isColorCode(0x0D));
        Assertions.assertFalse(InterfaceFont.isColorCode(0x1A));
        Assertions.assertTrue(InterfaceFont.isColorCode(0x07));
        Assertions.assertTrue(InterfaceFont.isColorCode(0x1F));
    }

    @RequiresInstallation
    @Test
    void draw_should_place_each_glyph_at_its_offsets_with_the_texels_of_its_atlas_rectangle() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(SIZE, SIZE, "Interface font glyph placement", () -> {
            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); InterfaceRenderer renderer = new InterfaceRenderer()) {
                ScrResourceProvider resources = ScrResourceProvider.borrow(storage);
                ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * OverlayPass.RGBA_BYTES);

                try {
                    for (String name : FONTS) {
                        FontFace face = resources.loadFont(ScrResourceKeys.ofFont(name));
                        InterfaceFont font = InterfaceFont.load(resources, renderer, name);

                        try {
                            for (char character : CHARACTERS) {
                                FontGlyph glyph = face.findGlyph(character);

                                Assertions.assertNotNull(glyph, name + " " + character);
                                assertGlyph(renderer, font, face, glyph, pixels, name + " " + character);
                            }
                        } finally {
                            font.close(renderer);
                        }
                    }
                } finally {
                    MemoryUtil.memFree(pixels);
                }
            }
        });
    }

    @RequiresInstallation
    @Test
    void the_engine_font_should_measure_and_draw_as_the_native_paths() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        HiddenContextScenario.run(SIZE, SIZE, "Interface font as engine font", () -> {
            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); InterfaceRenderer renderer = new InterfaceRenderer()) {
                ScrResourceProvider resources = ScrResourceProvider.borrow(storage);
                ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * OverlayPass.RGBA_BYTES);

                try {
                    for (String name : FONTS) {
                        InterfaceFont font = InterfaceFont.load(resources, renderer, name);
                        Font engineFont = font;

                        try {
                            Assertions.assertEquals(font.measure(ENGINE_TEXT, true), engineFont.measure(ENGINE_TEXT), name);

                            int[] nativePixels = drawText(renderer, pixels, () -> font.draw(renderer, ENGINE_TEXT, PEN, PEN, 1, WHITE_CODE, true));
                            int[] enginePixels = drawText(renderer, pixels, () -> engineFont.draw(renderer, ENGINE_TEXT, PEN, PEN,
                                                                                                  InterfaceFont.getCodeColor(WHITE_CODE)));

                            Assertions.assertArrayEquals(nativePixels, enginePixels, name + ": the engine draw is the native draw");
                        } finally {
                            font.close(renderer);
                        }
                    }
                } finally {
                    MemoryUtil.memFree(pixels);
                }
            }
        });
    }

    @RequiresInstallation
    @Test
    void the_engine_font_should_draw_in_a_style_as_the_native_paths() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        InterfaceCodeColors gameColors = new InterfaceCodeColors(InterfaceText.GAME_CODE_COLORS);
        TextStyle gameStyle = TextStyle.builder().setColors(gameColors).setKerning(false).setScale(STYLE_SCALE).build();
        TextStyle tableStyle = TextStyle.builder(gameStyle).setColors(createGameTable()).build();
        TextStyle snappedStyle = TextStyle.builder().setColors(gameColors).setPixelSnapped(true).build();

        HiddenContextScenario.run(SIZE, SIZE, "Interface font in an engine text style", () -> {
            try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath()); InterfaceRenderer renderer = new InterfaceRenderer()) {
                ScrResourceProvider resources = ScrResourceProvider.borrow(storage);
                ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * OverlayPass.RGBA_BYTES);
                int white = InterfaceFont.getCodeColor(WHITE_CODE);

                try {
                    for (String name : FONTS) {
                        InterfaceFont font = InterfaceFont.load(resources, renderer, name);

                        try {
                            Assertions.assertEquals(font.measure(ENGINE_TEXT, false) * STYLE_SCALE, font.measure(ENGINE_TEXT, gameStyle), name);

                            int[] nativePixels = drawText(renderer, pixels,
                                                          () -> font.draw(renderer, ENGINE_TEXT, PEN, PEN, STYLE_SCALE, WHITE_CODE, false));
                            int[] stylePixels = drawText(renderer, pixels, () -> font.draw(renderer, ENGINE_TEXT, PEN, PEN, white, gameStyle));
                            int[] tablePixels = drawText(renderer, pixels, () -> font.draw(renderer, ENGINE_TEXT, PEN, PEN, white, tableStyle));
                            int[] exactPixels = drawText(renderer, pixels, () -> font.draw(renderer, ENGINE_TEXT, PEN, PEN, white));
                            int[] snappedPixels = drawText(renderer, pixels,
                                                           () -> font.draw(renderer, ENGINE_TEXT, PEN + PEN_FRACTION, PEN - PEN_FRACTION, white, snappedStyle));

                            Assertions.assertArrayEquals(nativePixels, stylePixels, name + ": the styled draw is the native scaled draw without kerning");
                            Assertions.assertArrayEquals(stylePixels, tablePixels, name + ": the engine color table draws as the interface code colors");
                            Assertions.assertArrayEquals(exactPixels, snappedPixels, name + ": snapped glyphs land on the whole pixels of the pen");
                        } finally {
                            font.close(renderer);
                        }
                    }
                } finally {
                    MemoryUtil.memFree(pixels);
                }
            }
        });
    }
}
