package org.fogofwarlabs.sc4j.scr.client.ui.console;

import org.fogofwarlabs.sc4j.scr.client.TextTranslation;
import org.fogofwarlabs.sc4j.scr.data.format.text.LegacyTextUtils;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.IntPredicate;

/**
 * Draws the texts of other players and of the map in the message area in another language. While chat translation is on, a line that shows
 * another player's text asks for the translation of its text at once; while map-text translation is on, so does a line that shows trigger
 * text ({@link MessageLines.TextListener}). The line shows its own bytes until the translation comes; then each rotating line of the message
 * shows the matching piece of the sender prefix and the translated text, wrapped and colored as the message area wraps and colors a written
 * message. Pieces beyond the message's rotating lines are not shown, and a rotating line without a piece shows nothing.
 * <p>
 * While map-text translation is on, a rotating line whose bytes a memory write changed is translated by itself once its bytes stayed the
 * same for {@value #MEMORY_TEXT_SETTLE_MILLISECONDS} milliseconds, and shows its translation only while its bytes stay the same. Lines that
 * are mostly symbols, such as text art, keep their bytes ({@link TextTranslation#isMostlyLetters}).
 * <p>
 * The substitution is for drawing only: the message lines keep their bytes, styles and deadlines. A rotating line whose bytes or deadline
 * differ from the ones written with the message, such as after a later message or a map's write to the line, shows its bytes from then on.
 * A message that takes more pieces than the rotating lines is not translated.
 * <p>
 * Every method runs on the thread that owns the message lines; a translation's future is read there once it is done.
 */
public final class MessageLineTranslations implements MessageLines.TextListener {
    /**
     * The time in milliseconds that the bytes of a line changed by a memory write stay the same before the line is translated.
     */
    public static final int MEMORY_TEXT_SETTLE_MILLISECONDS = 250;

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
     * One translated message: its sender prefix, the answer for its text and the style and color of its first piece.
     */
    private static final class Message {
        private final @NotNull String prefix;
        private final TextTranslation.@NotNull Answer answer;
        private final int style;
        private final int color;

        private @Nullable List<@NotNull Substitute> pieces;

        private Message(@NotNull String prefix, TextTranslation.@NotNull Answer answer, int style, int color) {
            this.prefix = prefix;
            this.answer = answer;
            this.style  = style;
            this.color  = color;

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

            String translated = this.answer.findTranslatedText();

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

    /**
     * A rotating line whose bytes a memory write changed: its bytes as last drawn, the time they were first drawn, the answer for them once
     * they settled and the substitute once the translation came.
     */
    private static final class MemoryLine {
        private byte @Nullable [] bytes;
        private int changeTime;
        private TextTranslation.@Nullable Answer answer;
        private @Nullable Substitute substitute;

        private MemoryLine() {
            this.bytes      = null;
            this.changeTime = 0;
            this.answer     = null;
            this.substitute = null;
        }
    }


    private final @NotNull MessageLines lines;
    private final MessageLines.@NotNull TextLayout layout;
    private final @NotNull TextTranslation translation;
    private final @NotNull Locale target;
    private final @NotNull IntPredicate translatedSenders;

    private final @Nullable Line @NotNull [] shown;
    private final @Nullable MemoryLine @NotNull [] memoryLines;

    /**
     * Creates a {@link MessageLineTranslations} instance without translated lines.
     *
     * @param lines             the message lines
     * @param layout            the message area's layout, which wraps the translated messages
     * @param translation       the translation of other players' chat texts and of map texts
     * @param target            the language to translate into
     * @param translatedSenders tells whether the chat texts of a sender slot are translated, such as the slots of human players; the
     *                          whispers of players outside the game ({@link MessageLines#NO_SENDER_SLOT}) are always translated
     */
    public MessageLineTranslations(@NotNull MessageLines lines, MessageLines.@NotNull TextLayout layout, @NotNull TextTranslation translation,
                                   @NotNull Locale target, @NotNull IntPredicate translatedSenders) {
        this.lines             = lines;
        this.layout            = layout;
        this.translation       = translation;
        this.target            = target;
        this.translatedSenders = translatedSenders;

        this.shown       = new Line[MessageLines.ROTATING_LINES];
        this.memoryLines = new MemoryLine[MessageLines.ROTATING_LINES];
    }

    /**
     * Finds the text a rotating line shows in place of its bytes. A line whose bytes or deadline changed since its message was written
     * shows its bytes from then on; a line changed by a memory write shows its translation only while its bytes stay the same.
     *
     * @param line     the rotating line
     * @param bytes    the line's bytes up to the first zero ({@link MessageLines#getLine}), not empty
     * @param hostTime the host clock of the drawn frame in milliseconds
     * @return the substitute, or {@code null} when the line shows its bytes
     */
    public @Nullable Substitute findSubstitute(int line, byte @NotNull [] bytes, int hostTime) {
        Line entry = this.shown[line];

        if (entry != null) {
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

        MemoryLine memory = this.memoryLines[line];

        if (memory == null) {
            return null;
        }

        if (!Arrays.equals(bytes, memory.bytes)) {
            memory.bytes      = bytes;
            memory.changeTime = hostTime;
            memory.answer     = null;
            memory.substitute = null;

            return null;
        }

        if (memory.answer == null) {
            if (hostTime - memory.changeTime < MEMORY_TEXT_SETTLE_MILLISECONDS) {
                return null;
            }

            memory.answer = this.translation.translateMapText(LegacyTextUtils.decode(bytes, 0, bytes.length), this.target);

            if (memory.answer == null) {
                return null;
            }
        }

        if (memory.substitute == null) {
            String translated = memory.answer.findTranslatedText();

            memory.substitute = translated == null ? null : new Substitute(translated, this.lines.getStyle(line), this.lines.getColor(line));
        }

        return memory.substitute;
    }

    /**
     * Called when a line with the text of another player or of the map is written.
     *
     * @param source    the kind of the text
     * @param sender    the sender slot, or {@link MessageLines#NO_SENDER_SLOT} for a whisper from a player outside the game and for trigger
     *                  text
     * @param line      the formatted line bytes
     * @param textStart the index of the text in the line, after the sender prefix
     * @param firstLine the rotating line of the first piece; each next piece is on the next rotating line
     * @param lineCount the number of pieces
     * @implSpec Asks for the translation of the text when its kind is translated and remembers the message's rotating lines with their
     * bytes and deadlines.
     */
    @Override
    public void onTextWritten(MessageLines.@NotNull TextSource source, int sender, byte @NotNull [] line, int textStart, int firstLine,
                              int lineCount) {
        if (lineCount > MessageLines.ROTATING_LINES || textStart >= line.length) {
            return;
        }

        String text = LegacyTextUtils.decode(line, textStart, line.length - textStart);
        TextTranslation.Answer answer;

        if (source == MessageLines.TextSource.TRIGGER_TEXT) {
            answer = this.translation.translateMapText(text, this.target);
        } else if (sender == MessageLines.NO_SENDER_SLOT || this.translatedSenders.test(sender)) {
            answer = this.translation.translateChat(text, 0, this.target);
        } else {
            answer = null;
        }

        if (answer == null) {
            return;
        }

        String prefix = LegacyTextUtils.decode(line, 0, textStart);
        Message message = new Message(prefix, answer, this.lines.getStyle(firstLine), this.lines.getColor(firstLine));

        for (int piece = 0; piece < lineCount; piece++) {
            int shownLine = (firstLine + piece) % MessageLines.ROTATING_LINES;

            this.shown[shownLine] = new Line(message, piece, this.lines.getLine(shownLine), this.lines.getDeadline(shownLine));
        }
    }

    /**
     * Called when a piece of any message is written to a rotating line, before {@link #onTextWritten} reports the message.
     *
     * @param line the rotating line
     * @implSpec Forgets what the line showed in place of its bytes.
     */
    @Override
    public void onLineWritten(int line) {
        this.shown[line]       = null;
        this.memoryLines[line] = null;
    }

    /**
     * Called when a memory write changes a byte of a rotating line.
     *
     * @param line the rotating line
     * @implSpec Forgets the message the line showed and, while map-text translation is on, watches the line's bytes from then on.
     */
    @Override
    public void onLineMemoryWritten(int line) {
        this.shown[line] = null;

        if (this.memoryLines[line] == null && this.translation.isMapTextEnabled()) {
            this.memoryLines[line] = new MemoryLine();
        }
    }
}
