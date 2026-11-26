package org.fogofwarlabs.sc4j.scr.client;

import org.fogofwarlabs.sc4j.translate.Translation;
import org.fogofwarlabs.sc4j.translate.TranslationRequest;
import org.fogofwarlabs.sc4j.translate.Translator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Asks for the translations of other players' chat texts while the mod setting {@code translateChat} turns chat translation on. The color
 * and control characters of a text (below {@code U+0020}) are protected spans, so they stay as they are and in their places between the
 * translated runs. A translation is for display only: the callers draw it in place of the text and keep the text itself.
 * <p>
 * The translator answers on a worker thread; callers read a future only on their own thread, once it is done.
 */
public final class ChatTranslation {
    /**
     * The chat translation of a run with chat translation off: it asks for nothing.
     */
    public static final @NotNull ChatTranslation OFF = new ChatTranslation(Translator.unavailable(), false);

    /**
     * The first character that is not a control character.
     */
    private static final char FIRST_PRINTABLE_CHARACTER = ' ';

    /**
     * Returns the text a translation shows in place of the original text.
     *
     * @param future the future of the translation, or {@code null} when none was asked
     * @return the translated text once the future is done and the text was translated, or {@code null} otherwise
     */
    public static @Nullable String findTranslatedText(@Nullable CompletableFuture<@NotNull Translation> future) {
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
            return null;
        }

        Translation translation = future.join();

        return translation.isTranslated() ? translation.getTranslatedText() : null;
    }


    private final @NotNull Translator translator;
    private final boolean enabled;

    /**
     * Creates a {@link ChatTranslation} instance.
     *
     * @param translator the translator of the run
     * @param enabled    whether chat translation is on
     */
    public ChatTranslation(@NotNull Translator translator, boolean enabled) {
        this.translator = translator;
        this.enabled    = enabled;
    }

    /**
     * @return {@code true} if chat translation is on, {@code false} otherwise
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * Asks for the translation of a chat text; its start and its control characters stay as they are.
     *
     * @param text            the text
     * @param protectedLength the length of the start of the text that stays as it is, such as the name an emote starts with, or {@code 0}
     * @param target          the language to translate into
     * @return the future of the translation, or {@code null} while chat translation is off
     */
    public @Nullable CompletableFuture<@NotNull Translation> translate(@NotNull String text, int protectedLength, @NotNull Locale target) {
        if (!this.enabled) {
            return null;
        }

        TranslationRequest.Builder request = new TranslationRequest.Builder(text, target);

        if (protectedLength > 0) {
            request.addProtectedSpan(0, protectedLength);
        }

        int index = protectedLength;

        while (index < text.length()) {
            if (text.charAt(index) >= FIRST_PRINTABLE_CHARACTER) {
                index++;

                continue;
            }

            int start = index;

            while (index < text.length() && text.charAt(index) < FIRST_PRINTABLE_CHARACTER) {
                index++;
            }

            request.addProtectedSpan(start, index);
        }

        return this.translator.translate(request.build());
    }
}
