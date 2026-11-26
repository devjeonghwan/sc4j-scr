package org.fogofwarlabs.sc4j.scr.client.ui.console;

import org.fogofwarlabs.sc4j.scr.client.ChatTranslation;
import org.fogofwarlabs.sc4j.scr.data.format.text.LegacyTextUtils;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.fogofwarlabs.sc4j.translate.Translation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntPredicate;

/**
 * Draws the texts of other players in the message area in another language while chat translation is on. A line that shows another
 * player's text ({@link MessageLines.OtherPlayerTextListener}) asks for the translation of its text at once and shows its own bytes until
 * the translation comes; then each rotating line of the message shows the matching piece of the sender prefix and the translated text,
 * wrapped and colored as the message area wraps and colors a written message. Pieces beyond the message's rotating lines are not shown, and
 * a rotating line without a piece shows nothing.
 * <p>
 * The substitution is for drawing only: the message lines keep their bytes, styles and deadlines. A rotating line whose bytes or deadline
 * differ from the ones written with the message, such as after a later message or a map's write to the line, shows its bytes from then on.
 * A message that takes more pieces than the rotating lines is not translated.
 * <p>
 * Every method runs on the thread that owns the message lines; a translation's future is read there once it is done.
 */
public final class ChatLineTranslations implements MessageLines.OtherPlayerTextListener {
    /**
     * The most text bytes a rotating line keeps, its terminator excluded.
     */
    private static final int LINE_TEXT_LIMIT = MessageLines.LINE_SIZE - 1;
    private static final char TERMINATOR = '\0';

    /**
     * The text a rotating line shows in place of its bytes, with its style code and starting color.
     */
    public static final class Substitute {
        private final @NotNull String text;
        private final int style;
        private final int color;

        /**
         * Creates a {@link Substitute} instance.
         *
         * @param text  the text, empty for a line that shows nothing
         * @param style the style code the text starts with
         * @param color the packed {@code 0xRRGGBBAA} starting color, or {@code 0} when the text starts with its style code
         */
        private Substitute(@NotNull String text, int style, int color) {
            this.text  = text;
            this.style = style;
            this.color = color;
        }

        /**
         * @return the text, empty for a line that shows nothing
         */
        public @NotNull String getText() {
            return this.text;
        }

        /**
         * @return the style code the text starts with
         */
        public int getStyle() {
            return this.style;
        }

        /**
         * @return the packed {@code 0xRRGGBBAA} starting color, or {@code 0} when the text starts with its style code
         */
        public int getColor() {
            return this.color;
        }
    }

    /**
     * One translated message: its sender prefix, the future of its text's translation and the style and color of its first piece.
     */
    private static final class Message {
        private final @NotNull String prefix;
        private final @NotNull CompletableFuture<@NotNull Translation> translation;
        private final int style;
        private final int color;

        private @Nullable List<@NotNull Substitute> pieces;

        private Message(@NotNull String prefix, @NotNull CompletableFuture<@NotNull Translation> translation, int style, int color) {
            this.prefix      = prefix;
            this.translation = translation;
            this.style       = style;
            this.color       = color;

            this.pieces = null;
        }

        /**
         * Returns the pieces of the translated message, wrapped on the first call after the translation came.
         *
         * @param layout the message area's layout
         * @return the pieces, or {@code null} while the translation has not come or when the text was not translated
         */
        private @Nullable List<@NotNull Substitute> findPieces(MessageLines.@NotNull TextLayout layout) {
            if (this.pieces != null) {
                return this.pieces;
            }

            String translated = ChatTranslation.findTranslatedText(this.translation);

            if (translated == null) {
                return null;
            }

            String shown = this.prefix + translated;
            int terminator = shown.indexOf(TERMINATOR);
            byte[] bytes = (terminator < 0 ? shown : shown.substring(0, terminator)).getBytes(StandardCharsets.UTF_8);
            List<Substitute> wrapped = new ArrayList<>();

            if (bytes.length > 0) {
                int pieceStyle = this.style;
                int pieceColor = this.color;
                int start = 0;

                for (int end : layout.breakLines(bytes)) {
                    wrapped.add(new Substitute(LegacyTextUtils.decode(bytes, start, Math.min(end - start, LINE_TEXT_LIMIT)), pieceStyle, pieceColor));

                    int nextStyle = MessageLines.findNextPieceStyle(bytes, start, end, pieceStyle);

                    if (nextStyle != pieceStyle) {
                        pieceColor = 0;
                    }

                    pieceStyle = nextStyle;
                    start      = end;
                }
            }

            this.pieces = wrapped;

            return wrapped;
        }
    }

    /**
     * A rotating line that shows a piece of a translated message: the message, the piece's index and the line's bytes and deadline as the
     * message wrote them.
     */
    private static final class Line {
        private final @NotNull Message message;
        private final int piece;
        private final byte @NotNull [] bytes;
        private final int deadline;

        private Line(@NotNull Message message, int piece, byte @NotNull [] bytes, int deadline) {
            this.message  = message;
            this.piece    = piece;
            this.bytes    = bytes;
            this.deadline = deadline;
        }
    }


    private final @NotNull MessageLines lines;
    private final MessageLines.@NotNull TextLayout layout;
    private final @NotNull ChatTranslation translation;
    private final @NotNull Locale target;
    private final @NotNull IntPredicate translatedSenders;

    private final @Nullable Line @NotNull [] shown;

    /**
     * Creates a {@link ChatLineTranslations} instance without translated lines.
     *
     * @param lines             the message lines
     * @param layout            the message area's layout, which wraps the translated messages
     * @param translation       the translation of other players' chat texts
     * @param target            the language to translate into
     * @param translatedSenders tells whether the texts of a sender slot are translated, such as the slots of human players; the whispers of
     *                          players outside the game ({@link MessageLines#NO_SENDER_SLOT}) are always translated
     */
    public ChatLineTranslations(@NotNull MessageLines lines, MessageLines.@NotNull TextLayout layout, @NotNull ChatTranslation translation,
                                @NotNull Locale target, @NotNull IntPredicate translatedSenders) {
        this.lines             = lines;
        this.layout            = layout;
        this.translation       = translation;
        this.target            = target;
        this.translatedSenders = translatedSenders;

        this.shown = new Line[MessageLines.ROTATING_LINES];
    }

    /**
     * Finds the text a rotating line shows in place of its bytes. A line whose bytes or deadline changed since its message was written
     * shows its bytes from then on.
     *
     * @param line  the rotating line
     * @param bytes the line's bytes up to the first zero ({@link MessageLines#getLine})
     * @return the substitute, or {@code null} when the line shows its bytes
     */
    public @Nullable Substitute findSubstitute(int line, byte @NotNull [] bytes) {
        Line entry = this.shown[line];

        if (entry == null) {
            return null;
        }

        if (!Arrays.equals(bytes, entry.bytes) || this.lines.getDeadline(line) != entry.deadline) {
            this.shown[line] = null;

            return null;
        }

        List<Substitute> pieces = entry.message.findPieces(this.layout);

        if (pieces == null) {
            return null;
        }

        return entry.piece < pieces.size() ? pieces.get(entry.piece) : new Substitute("", entry.message.style, 0);
    }

    /**
     * Called when a line with the text of another player is written.
     *
     * @param sender    the sender slot, or {@link MessageLines#NO_SENDER_SLOT} for a whisper from a player outside the game
     * @param line      the formatted line bytes
     * @param textStart the index of the player's text in the line, after the sender prefix
     * @param firstLine the rotating line of the first piece; each next piece is on the next rotating line
     * @param lineCount the number of pieces
     * @implSpec Asks for the translation of the text and remembers the message's rotating lines with their bytes and deadlines.
     */
    @Override
    public void onOtherPlayerTextWritten(int sender, byte @NotNull [] line, int textStart, int firstLine, int lineCount) {
        if ((sender != MessageLines.NO_SENDER_SLOT && !this.translatedSenders.test(sender)) || lineCount > MessageLines.ROTATING_LINES ||
            textStart >= line.length) {
            return;
        }

        String text = LegacyTextUtils.decode(line, textStart, line.length - textStart);
        CompletableFuture<Translation> future = this.translation.translate(text, 0, this.target);

        if (future == null) {
            return;
        }

        String prefix = LegacyTextUtils.decode(line, 0, textStart);
        Message message = new Message(prefix, future, this.lines.getStyle(firstLine), this.lines.getColor(firstLine));

        for (int piece = 0; piece < lineCount; piece++) {
            int shownLine = (firstLine + piece) % MessageLines.ROTATING_LINES;

            this.shown[shownLine] = new Line(message, piece, this.lines.getLine(shownLine), this.lines.getDeadline(shownLine));
        }
    }
}
