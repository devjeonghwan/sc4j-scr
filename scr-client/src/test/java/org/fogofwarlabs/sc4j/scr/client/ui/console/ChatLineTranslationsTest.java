package org.fogofwarlabs.sc4j.scr.client.ui.console;

import org.fogofwarlabs.sc4j.scr.client.ChatTranslation;
import org.fogofwarlabs.sc4j.scr.client.RecordingTranslator;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.fogofwarlabs.sc4j.translate.ProtectedSpan;
import org.fogofwarlabs.sc4j.translate.TranslationRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.IntPredicate;

/**
 * Checks that the message area draws the translation of another player's text in place of its line while the line keeps its bytes, styles
 * and deadline, and which texts are asked for.
 */
final class ChatLineTranslationsTest {
    /**
     * The layout that keeps every text on one line.
     */
    private static final MessageLines.@NotNull TextLayout SINGLE_LINE = text -> new int[]{text.length};
    /**
     * The piece length of the layout that breaks a text every {@value} bytes.
     */
    private static final int PIECE_LENGTH = 10;
    /**
     * The layout that breaks a text every {@link #PIECE_LENGTH} bytes.
     */
    private static final MessageLines.@NotNull TextLayout FIXED_PIECES = text -> {
        int[] ends = new int[(text.length + PIECE_LENGTH - 1) / PIECE_LENGTH];

        for (int index = 0; index < ends.length; index++) {
            ends[index] = Math.min((index + 1) * PIECE_LENGTH, text.length);
        }

        return ends;
    };

    /**
     * The first message line of a game, then the line the next piece takes.
     */
    private static final int FIRST_LINE = 10;
    private static final int SECOND_LINE = 0;
    private static final int FIRST_PLAYER = 0;
    private static final int SECOND_PLAYER = 1;
    private static final int NAME_COLOR = 0x11223344;
    private static final int HOST_CLOCK = 500;
    private static final int CHAT_TEXT_STYLE = 2;
    private static final @NotNull Locale TARGET = Locale.ENGLISH;
    private static final @NotNull IntPredicate EVERY_SENDER = sender -> true;

    private static @NotNull MessageLines createLines(MessageLines.@NotNull TextLayout layout) {
        MessageLines lines = new MessageLines();

        lines.setLayout(layout);
        lines.setHostClock(() -> HOST_CLOCK);
        lines.setPlayerNames(new byte[][]{"Alpha".getBytes(StandardCharsets.UTF_8), "Bravo".getBytes(StandardCharsets.UTF_8)});
        lines.setNameColors(sender -> NAME_COLOR);
        lines.setLocalPlayer(SECOND_PLAYER);

        return lines;
    }

    private static @NotNull ChatLineTranslations install(@NotNull MessageLines lines, MessageLines.@NotNull TextLayout layout,
                                                         @NotNull ChatTranslation translation, @NotNull IntPredicate translatedSenders) {
        ChatLineTranslations translations = new ChatLineTranslations(lines, layout, translation, TARGET, translatedSenders);

        lines.setOtherPlayerTextListener(translations);

        return translations;
    }

    @Test
    void another_players_chat_shows_its_translation_while_the_line_keeps_its_bytes() {
        MessageLines lines = createLines(SINGLE_LINE);
        RecordingTranslator translator = new RecordingTranslator();
        ChatLineTranslations translations = install(lines, SINGLE_LINE, new ChatTranslation(translator, true), EVERY_SENDER);

        lines.writeChat(FIRST_PLAYER, "안녕".getBytes(StandardCharsets.UTF_8));

        byte[] written = lines.getLine(FIRST_LINE);
        int deadline = lines.getDeadline(FIRST_LINE);
        TranslationRequest request = translator.getRequests().getFirst();

        Assertions.assertEquals(1, translator.getRequests().size());
        Assertions.assertEquals("안녕", request.getText(), "Only the text is asked for, without the sender prefix.");
        Assertions.assertEquals(TARGET, request.getTarget());
        Assertions.assertNull(translations.findSubstitute(FIRST_LINE, written), "The line shows its bytes until the translation comes.");

        translator.answer(0, "hello");

        ChatLineTranslations.Substitute substitute = translations.findSubstitute(FIRST_LINE, lines.getLine(FIRST_LINE));

        Assertions.assertNotNull(substitute);
        Assertions.assertEquals("Alpha:\u0002 hello", substitute.getText());
        Assertions.assertEquals(CHAT_TEXT_STYLE, substitute.getStyle());
        Assertions.assertEquals(NAME_COLOR, substitute.getColor());
        Assertions.assertArrayEquals(written, lines.getLine(FIRST_LINE), "The line keeps its bytes.");
        Assertions.assertEquals(deadline, lines.getDeadline(FIRST_LINE), "The line keeps its deadline.");
        Assertions.assertEquals(CHAT_TEXT_STYLE, lines.getStyle(FIRST_LINE));
        Assertions.assertEquals(NAME_COLOR, lines.getColor(FIRST_LINE));
    }

    @Test
    void color_codes_of_a_text_are_protected_spans() {
        MessageLines lines = createLines(SINGLE_LINE);
        RecordingTranslator translator = new RecordingTranslator();

        install(lines, SINGLE_LINE, new ChatTranslation(translator, true), EVERY_SENDER);

        lines.writeChat(FIRST_PLAYER, "\u0003go \u0004\u0005now".getBytes(StandardCharsets.UTF_8));

        List<ProtectedSpan> spans = translator.getRequests().getFirst().getProtectedSpans();

        Assertions.assertEquals(2, spans.size());
        Assertions.assertEquals(0, spans.get(0).getStart());
        Assertions.assertEquals(1, spans.get(0).getEnd());
        Assertions.assertEquals(4, spans.get(1).getStart());
        Assertions.assertEquals(6, spans.get(1).getEnd());
    }

    @Test
    void overwritten_bytes_stop_the_substitution() {
        MessageLines lines = createLines(SINGLE_LINE);
        RecordingTranslator translator = new RecordingTranslator();
        ChatLineTranslations translations = install(lines, SINGLE_LINE, new ChatTranslation(translator, true), EVERY_SENDER);

        lines.writeChat(FIRST_PLAYER, "gg".getBytes(StandardCharsets.UTF_8));
        translator.answer(0, "good game");

        Assertions.assertNotNull(translations.findSubstitute(FIRST_LINE, lines.getLine(FIRST_LINE)));

        int offset = FIRST_LINE * MessageLines.LINE_SIZE;
        int original = lines.readByte(offset);

        lines.writeByte(offset, 'X');

        Assertions.assertNull(translations.findSubstitute(FIRST_LINE, lines.getLine(FIRST_LINE)), "A line written over shows its bytes.");

        lines.writeByte(offset, original);

        Assertions.assertNull(translations.findSubstitute(FIRST_LINE, lines.getLine(FIRST_LINE)), "The substitution stays stopped.");
    }

    @Test
    void a_translation_takes_the_rotating_lines_of_its_message() {
        MessageLines lines = createLines(FIXED_PIECES);
        RecordingTranslator translator = new RecordingTranslator();
        ChatLineTranslations translations = install(lines, FIXED_PIECES, new ChatTranslation(translator, true), EVERY_SENDER);

        lines.writeChat(FIRST_PLAYER, "abcdefghijkl".getBytes(StandardCharsets.UTF_8));
        translator.answer(0, "xy");

        ChatLineTranslations.Substitute first = translations.findSubstitute(FIRST_LINE, lines.getLine(FIRST_LINE));
        ChatLineTranslations.Substitute second = translations.findSubstitute(SECOND_LINE, lines.getLine(SECOND_LINE));

        Assertions.assertNotNull(first);
        Assertions.assertNotNull(second);
        Assertions.assertEquals("Alpha:\u0002 xy", first.getText());
        Assertions.assertEquals("", second.getText(), "A line without a piece of the translation shows nothing.");
    }

    @Test
    void received_whispers_are_translated() {
        MessageLines lines = createLines(SINGLE_LINE);
        RecordingTranslator translator = new RecordingTranslator();
        ChatLineTranslations translations = install(lines, SINGLE_LINE, new ChatTranslation(translator, true), EVERY_SENDER);

        lines.writeWhisper("<From: %s>", "Zeratul#abcd12", "en taro".getBytes(StandardCharsets.UTF_8), true);
        translator.answer(0, "honor");

        ChatLineTranslations.Substitute substitute = translations.findSubstitute(FIRST_LINE, lines.getLine(FIRST_LINE));

        Assertions.assertEquals("en taro", translator.getRequests().getFirst().getText());
        Assertions.assertNotNull(substitute);
        Assertions.assertEquals("<From: Zeratul#abcd12> honor", substitute.getText());
    }

    @Test
    void own_lines_and_sent_whispers_are_not_translated() {
        MessageLines lines = createLines(SINGLE_LINE);
        RecordingTranslator translator = new RecordingTranslator();
        ChatLineTranslations translations = install(lines, SINGLE_LINE, new ChatTranslation(translator, true), EVERY_SENDER);

        lines.writeChat(SECOND_PLAYER, "mine".getBytes(StandardCharsets.UTF_8));
        lines.writeWhisper("<To: %s>", "Zeratul#abcd12", "sent".getBytes(StandardCharsets.UTF_8), false);

        Assertions.assertEquals(List.of(), translator.getRequests());
        Assertions.assertNull(translations.findSubstitute(FIRST_LINE, lines.getLine(FIRST_LINE)));
    }

    @Test
    void texts_of_senders_outside_the_translated_slots_are_not_asked_for() {
        MessageLines lines = createLines(SINGLE_LINE);
        RecordingTranslator translator = new RecordingTranslator();

        install(lines, SINGLE_LINE, new ChatTranslation(translator, true), sender -> sender != FIRST_PLAYER);

        lines.writeChat(FIRST_PLAYER, "attack".getBytes(StandardCharsets.UTF_8));

        Assertions.assertEquals(List.of(), translator.getRequests());
    }

    @Test
    void chat_translation_off_asks_for_nothing() {
        MessageLines lines = createLines(SINGLE_LINE);
        RecordingTranslator translator = new RecordingTranslator();
        ChatLineTranslations translations = install(lines, SINGLE_LINE, new ChatTranslation(translator, false), EVERY_SENDER);

        lines.writeChat(FIRST_PLAYER, "gg".getBytes(StandardCharsets.UTF_8));

        Assertions.assertEquals(List.of(), translator.getRequests());
        Assertions.assertNull(translations.findSubstitute(FIRST_LINE, lines.getLine(FIRST_LINE)));
    }
}
