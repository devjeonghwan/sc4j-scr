package org.fogofwarlabs.sc4j.scr.render.ui;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Checks the alignment, wrapping and end-of-text rules of {@link InterfaceText}.
 */
final class InterfaceTextTest {
    /**
     * The first printable character and the advance of every printable character.
     */
    private static final char FIRST_PRINTABLE = 0x20;
    private static final int CHARACTER_WIDTH = 10;
    /**
     * The message area row: the pen and the inclusive right edge {@code W - 10} of the {@code 640}-wide interface.
     */
    private static final int MESSAGE_ROW_PEN = 10;
    private static final int MESSAGE_ROW_RIGHT = 630;
    /**
     * The inclusive right edge of the wrapping cases.
     */
    private static final int WRAP_RIGHT = 55;
    /**
     * Ten pixels per printable character; control codes advance nothing.
     */
    private static final @NotNull ToDoubleFunction<String> MEASURE = text -> {
        int width = 0;

        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) >= FIRST_PRINTABLE) {
                width += CHARACTER_WIDTH;
            }
        }

        return width;
    };

    @Test
    void compute_alignment_shifts_should_be_empty_without_alignment_codes() {
        Assertions.assertArrayEquals(new int[0], InterfaceText.computeAlignmentShifts("\u0004plain", MESSAGE_ROW_PEN, MESSAGE_ROW_RIGHT, MEASURE));
    }

    @Test
    void compute_alignment_shifts_should_center_the_rest_between_pen_and_right() {
        // The message area row: pen 10, inclusive right W - 10 = 630; "\x13\x07" then 50 px of text:
        // shift = (630 - (int) (10 + 50)) >> 1 = 285, so the text spans 295 .. 345 around the row center 320.
        Assertions.assertArrayEquals(new int[]{285},
                                     InterfaceText.computeAlignmentShifts("\u0013\u000712345", MESSAGE_ROW_PEN, MESSAGE_ROW_RIGHT, MEASURE));
    }

    @Test
    void compute_alignment_shifts_should_end_the_rest_at_the_inclusive_right_for_the_right_code() {
        // "ab" stays at the pen, "\x12" moves the rest "cd" to end at 630: shift = 630 - (int) (10 + 20 + 20) = 580.
        Assertions.assertArrayEquals(new int[]{580}, InterfaceText.computeAlignmentShifts("ab\u0012cd", MESSAGE_ROW_PEN, MESSAGE_ROW_RIGHT, MEASURE));
    }

    @Test
    void compute_alignment_shifts_should_measure_a_later_code_from_the_moved_pen() {
        // First "\x13": rest 40 px, shift = (100 - 40) >> 1 = 30, pen 30; then "ab" moves the pen to 50 and "\x12" puts
        // "cd" at the right: 100 - (int) (50 + 20) = 30.
        Assertions.assertArrayEquals(new int[]{30, 30}, InterfaceText.computeAlignmentShifts("\u0013ab\u0012cd", 0, 100, MEASURE));
    }

    @Test
    void compute_alignment_shifts_should_truncate_fractional_pens() {
        // (int) 100 - (int) (0.5 + 25.25 + 20) = 55, halved by the arithmetic shift to 27.
        ToDoubleFunction<String> fractional = text -> text.equals("ab") ? 25.25 : text.isEmpty() ? 0 : 20;

        Assertions.assertArrayEquals(new int[]{27}, InterfaceText.computeAlignmentShifts("ab\u0013cd", 0.5f, 100, fractional));
    }

    @Test
    void find_wrap_pen_should_measure_the_rest_only_up_to_the_word_wrap_point() {
        // From pen 0 with right 55: "aaa" ends at 30, the spaces at 50, "bbb" would end at 80 > 55, so the pass stops
        // before it with the spaces included.
        Assertions.assertEquals(50f, InterfaceText.findWrapPen("aaa  bbb", 0, 0, WRAP_RIGHT, MEASURE));
        // Spaces that already pass the right edge end the pass with them.
        Assertions.assertEquals(60f, InterfaceText.findWrapPen("aaa   bbb", 0, 0, WRAP_RIGHT, MEASURE));
        // A line feed ends it where it is; color codes and 0x12/0x13 advance nothing and do not start a word.
        Assertions.assertEquals(30f, InterfaceText.findWrapPen("\u0013\u0004aaa\nbbb", 0, 0, 100, MEASURE));
        // 0x0B belongs to its word.
        Assertions.assertEquals(0f, InterfaceText.findWrapPen("aa\u000Bbbbbbbbbb", 0, 0, WRAP_RIGHT, MEASURE));
        // The floored pen is compared: a word ending at 55.9 still fits right 55.
        Assertions.assertEquals(55.9f, InterfaceText.findWrapPen("ab", 0, 35.9f, WRAP_RIGHT, MEASURE));
    }

    @Test
    void compute_alignment_shifts_should_center_on_the_wrap_point_when_the_rest_overflows() {
        // "\x13" at pen 0, right 55: the rest "aaa  bbb" stops at 50 (see above): shift = (55 - 50) >> 1 = 2.
        Assertions.assertArrayEquals(new int[]{2}, InterfaceText.computeAlignmentShifts("\u0013aaa  bbb", 0, WRAP_RIGHT, MEASURE));
    }

    @Test
    void truncate_lines_should_stop_at_an_unknown_control_byte_and_fill_with_empty_lines() {
        List<String> lines = List.of("ab\u000Bcd", "ef");

        Assertions.assertEquals(List.of("ab", "", ""), InterfaceText.truncateLines("ab\u000Bcd\nef", lines, 2));
        Assertions.assertEquals(List.of("ab"), InterfaceText.truncateLines("ab\u0014cd\nef", List.of("ab\u0014cd", "ef"), 0));

        // Color, alignment, tab, carriage return and 0x1A codes do not end drawing.
        List<String> kept = List.of("\u0004a\u0012b\tc\rd\u001Ae");

        Assertions.assertEquals(kept, InterfaceText.truncateLines(kept.getFirst(), kept, 3));
    }

    @Test
    void truncate_lines_should_end_the_layout_after_the_form_feed_line() {
        Assertions.assertEquals(List.of("ab"), InterfaceText.truncateLines("ab\fcd", List.of("ab", "cd"), 3));
        Assertions.assertEquals(List.of("ab", "cd"), InterfaceText.truncateLines("ab\ncd", List.of("ab", "cd"), 3));
    }

    @Test
    void find_line_end_should_end_between_words_and_keep_the_start_on_a_long_first_word() {
        // Right 55: "aaa bbb" would end at 70, so the piece ends at the word start 4 (the space stays on it).
        Assertions.assertEquals(4, InterfaceText.findLineEnd("aaa bbb", 0, 0, WRAP_RIGHT, InterfaceText.WrapMode.WORD, MEASURE));
        // A line feed ends the piece after itself.
        Assertions.assertEquals(3, InterfaceText.findLineEnd("ab\ncd", 0, 0, WRAP_RIGHT, InterfaceText.WrapMode.WORD, MEASURE));
        // The first word does not fit, so the starting offset is retained.
        Assertions.assertEquals(0, InterfaceText.findLineEnd("abcdefgh ij", 0, 0, WRAP_RIGHT, InterfaceText.WrapMode.WORD, MEASURE));
        // Leading spaces are taken before a first word that does not fit.
        Assertions.assertEquals(2, InterfaceText.findLineEnd("  abcdefgh", 0, 0, WRAP_RIGHT, InterfaceText.WrapMode.WORD, MEASURE));
    }

    @Test
    void find_line_end_should_stop_before_the_character_that_passes_the_right_edge_in_character_mode() {
        // Right 55: the sixth character ends at 60 > 55; spaces advance untested, so the second space (to 60) stays.
        Assertions.assertEquals(5, InterfaceText.findLineEnd("abcdefgh", 0, 0, WRAP_RIGHT, InterfaceText.WrapMode.CHARACTER, MEASURE));
        Assertions.assertEquals(6, InterfaceText.findLineEnd("ab cd efgh", 0, 0, WRAP_RIGHT, InterfaceText.WrapMode.CHARACTER, MEASURE));
        // No floor: "b" ending at 55.5 does not fit right 55.
        Assertions.assertEquals(1, InterfaceText.findLineEnd("ab", 0, 35.5f, WRAP_RIGHT, InterfaceText.WrapMode.CHARACTER, MEASURE));
        Assertions.assertEquals(3, InterfaceText.findLineEnd("ab\ncd", 0, 0, WRAP_RIGHT, InterfaceText.WrapMode.CHARACTER, MEASURE));
    }

    @Test
    void remove_caret_glyph_should_drop_only_the_edit_caret_glyph() {
        // The caret string 04 A6 01 keeps its color codes but loses the glyph for measuring and wrapping.
        Assertions.assertEquals("ab\u0004\u0001cd", InterfaceText.removeCaretGlyph("ab\u0004¦\u0001cd"));
        // A U+00A6 not preceded by code 0x04 is an ordinary glyph.
        Assertions.assertEquals("a¦b", InterfaceText.removeCaretGlyph("a¦b"));
    }
}
