package org.fogofwarlabs.sc4j.scr.client;

import org.fogofwarlabs.sc4j.translate.TranslationRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

/**
 * Checks which map texts are asked for, how their answers are kept and polled, and how digit runs share one template translation.
 */
final class TextTranslationTest {
    private static final @NotNull Locale TARGET = Locale.KOREAN;

    @Test
    void translate_map_text_should_ask_once_per_text_and_answer_once_the_translation_came() {
        RecordingTranslator translator = new RecordingTranslator();
        TextTranslation translation = new TextTranslation(translator, false, true);

        TextTranslation.Answer first = translation.translateMapText("Defend the base", TARGET);
        TextTranslation.Answer second = translation.translateMapText("Defend the base", TARGET);

        Assertions.assertNotNull(first);
        Assertions.assertSame(first, second, "A text shown again finds its answer.");
        Assertions.assertEquals(1, translator.getRequests().size());
        Assertions.assertNull(first.findTranslatedText(), "No text before the translation came.");
        Assertions.assertEquals("Defend the base", translation.resolveMapText("Defend the base", TARGET));

        translator.answer(0, "기지를 지켜라");

        Assertions.assertEquals("기지를 지켜라", first.findTranslatedText());
        Assertions.assertEquals("기지를 지켜라", translation.resolveMapText("Defend the base", TARGET));
        Assertions.assertEquals(1, translator.getRequests().size());
    }

    @Test
    void digit_runs_share_one_template_translation() {
        RecordingTranslator translator = new RecordingTranslator();
        TextTranslation translation = new TextTranslation(translator, false, true);

        TextTranslation.Answer third = translation.translateMapText("Wave 3 - 120 seconds left", TARGET);

        Assertions.assertNotNull(third);
        Assertions.assertEquals("Wave 101 - 102 seconds left", translator.getRequests().getFirst().getText());

        translator.answer(0, "웨이브 101 - 102초 남음");

        Assertions.assertEquals("웨이브 3 - 120초 남음", third.findTranslatedText());
        Assertions.assertEquals("웨이브 4 - 119초 남음", translation.resolveMapText("Wave 4 - 119 seconds left", TARGET),
                                "A text with other numbers takes the translated template at once.");
        Assertions.assertEquals(1, translator.getRequests().size());
    }

    @Test
    void a_template_whose_placeholders_do_not_come_back_asks_for_the_exact_text() {
        RecordingTranslator translator = new RecordingTranslator();
        TextTranslation translation = new TextTranslation(translator, false, true);
        TextTranslation.Answer answer = translation.translateMapText("Wave 3 of 9 incoming", TARGET);

        Assertions.assertNotNull(answer);

        translator.answer(0, "102번째 웨이브가 온다");

        Assertions.assertNull(answer.findTranslatedText());
        Assertions.assertEquals(List.of("Wave 101 of 102 incoming", "Wave 3 of 9 incoming"),
                                translator.getRequests().stream().map(TranslationRequest::getText).toList());

        translator.answer(1, "9개 중 3번째 웨이브가 온다");

        Assertions.assertEquals("9개 중 3번째 웨이브가 온다", answer.findTranslatedText());
    }

    @Test
    void text_art_is_not_asked_for() {
        RecordingTranslator translator = new RecordingTranslator();
        TextTranslation translation = new TextTranslation(translator, false, true);

        Assertions.assertFalse(TextTranslation.isMostlyLetters("|==#==|  ##  |==#==| ok"));
        Assertions.assertFalse(TextTranslation.isMostlyLetters("\u0004 \u0005"));
        Assertions.assertTrue(TextTranslation.isMostlyLetters("\u0004Kill 25 Zerglings!"));
        Assertions.assertEquals("<<==>>", translation.resolveMapText("<<==>>", TARGET));
        Assertions.assertEquals(List.of(), translator.getRequests());
    }

    @Test
    void map_text_translation_off_asks_for_nothing() {
        RecordingTranslator translator = new RecordingTranslator();
        TextTranslation translation = new TextTranslation(translator, true, false);

        Assertions.assertNull(translation.translateMapText("Defend the base", TARGET));
        Assertions.assertEquals("Defend the base", translation.resolveMapText("Defend the base", TARGET));
        Assertions.assertEquals(List.of(), translator.getRequests());
    }
}
