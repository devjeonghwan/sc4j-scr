package org.fogofwarlabs.sc4j.scr.client.ui.observer;

import org.fogofwarlabs.sc4j.scr.client.ui.text.GluText;
import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.presentation.TimerTextUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Rebuilds the replay command-card progress, elapsed time, speed or seek target, and paused text.
 * Elapsed time uses the recorded game speed; speed text uses the current playback setting and multiplier.
 * The caller supplies the paused blink state.
 */
public final class ReplayPanel {
    private static final int @NotNull [] FRAME_MILLISECONDS = {GameSpeed.SLOWEST_DURATION, GameSpeed.SLOWER_DURATION,
                                                               GameSpeed.SLOW_DURATION, GameSpeed.NORMAL_DURATION,
                                                               GameSpeed.FAST_DURATION, GameSpeed.FASTER_DURATION,
                                                               GameSpeed.FASTEST_DURATION};

    private static final int TITLE_CONTROL = -45;
    private static final int TIME_CONTROL = -46;
    private static final int SPEED_CONTROL = -47;
    private static final int PAUSED_CONTROL = -48;

    private static final int TITLE_STRING = 1545;
    private static final int PAUSED_STRING = 1546;
    private static final int SEEK_STRING = 1548;

    private static final int PERCENT_SCALE = 100;
    private static final int FIRST_UNSCALED_SPEED = 3;

    private final @NotNull StatText text;
    private final @NotNull GluText glueText;

    private final @NotNull Map<Integer, String> texts;
    private int progress;

    /**
     * Creates a {@link ReplayPanel} instance.
     *
     * @param text     the {@code stat_txt} strings
     * @param glueText the {@code gluAll} strings
     */
    public ReplayPanel(@NotNull StatText text, @NotNull GluText glueText) {
        this.text     = text;
        this.glueText = glueText;

        this.texts    = new HashMap<>();
        this.progress = 0;
    }

    /**
     * @return the progress bar percent of control {@code 10}
     */
    public int getProgress() {
        return this.progress;
    }

    /**
     * @return the texts of the shown controls ({@code -45} to {@code -48}) by id
     */
    public @NotNull Map<Integer, String> getTexts() {
        return this.texts;
    }


    /**
     * Recomputes the panel.
     *
     * @param frame         the current frame
     * @param endFrame      the last frame of the replay
     * @param recordedSpeed the replay's recorded game speed index ({@code 0..6})
     * @param speedIndex    the playback speed index ({@code 0..6})
     * @param multiplier    the playback multiplier
     * @param paused        whether playback is paused
     * @param blink         the blink state of the "Paused" line
     * @param seekTarget    the frame being sought, or a negative value
     */
    public void update(long frame, long endFrame, int recordedSpeed, int speedIndex, int multiplier, boolean paused, boolean blink, long seekTarget) {
        this.texts.clear();

        this.progress = endFrame > 0 ? (int) (frame * PERCENT_SCALE / endFrame) : 0;

        this.texts.put(TITLE_CONTROL, "\u0003" + this.text.getString(TITLE_STRING));

        int milliseconds = FRAME_MILLISECONDS[Math.clamp(recordedSpeed, 0, GameSpeed.FASTEST)];
        String time = TimerTextUtils.formatElapsedTime(TimerTextUtils.calculateElapsedSeconds((int) frame, milliseconds));

        this.texts.put(TIME_CONTROL, String.format(Locale.ROOT, this.glueText.get("scoreTIME_s").replace("%S", "%s"), time));

        if (seekTarget > frame) {
            String seekTime = TimerTextUtils.formatElapsedTime(TimerTextUtils.calculateElapsedSeconds((int) seekTarget, milliseconds));

            this.texts.put(SPEED_CONTROL, String.format(Locale.ROOT, this.text.getString(SEEK_STRING).replace("%S", "%s"), seekTime));
        } else {
            int index = Math.clamp(speedIndex, 0, GameSpeed.FASTEST);
            String suffix = multiplier > 1 ? String.format(Locale.ROOT, " %s%d", index < FIRST_UNSCALED_SPEED ? "/" : "x", multiplier) : "";

            this.texts.put(SPEED_CONTROL, this.glueText.get("gameSPEED") + " " + this.glueText.get(GluText.GAME_SPEED_KEYS.get(index)) + suffix);
        }

        if (paused) {
            this.texts.put(PAUSED_CONTROL, blink ? this.text.getString(PAUSED_STRING) : "");
        }
    }
}
